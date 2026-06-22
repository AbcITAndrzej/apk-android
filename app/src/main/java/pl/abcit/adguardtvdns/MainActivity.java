package pl.abcit.adguardtvdns;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_VPN = 42;

    private EditText dns1Input;
    private EditText dns2Input;
    private CheckBox allAppsCheckbox;
    private LinearLayout appListLayout;
    private final List<AppRow> appRows = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        loadSettings();
        loadApps();
    }

    @Override
    protected void onPause() {
        saveSettings();
        super.onPause();
    }

    private View buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(16, 16, 16));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(36), dp(28), dp(36), dp(28));
        scrollView.addView(root);

        TextView title = text("AdGuard TV DNS", 30, true);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView description = text("Prosty DNS przez lokalny VPN. Domyślnie używa AdGuard DNS i nie ma reklam ani śledzenia w aplikacji.", 16, false);
        description.setPadding(0, 0, 0, dp(20));
        root.addView(description);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.LEFT);
        buttonRow.setPadding(0, 0, 0, dp(20));
        root.addView(buttonRow);

        Button startButton = button("START");
        startButton.setOnClickListener(v -> startDnsVpn());
        buttonRow.addView(startButton, new LinearLayout.LayoutParams(dp(180), dp(64)));

        SpaceView space = new SpaceView(this, dp(16), 1);
        buttonRow.addView(space);

        Button stopButton = button("STOP");
        stopButton.setOnClickListener(v -> stopDnsVpn());
        buttonRow.addView(stopButton, new LinearLayout.LayoutParams(dp(180), dp(64)));

        root.addView(label("DNS 1"));
        dns1Input = editText();
        root.addView(dns1Input);

        root.addView(label("DNS 2"));
        dns2Input = editText();
        root.addView(dns2Input);

        allAppsCheckbox = new CheckBox(this);
        allAppsCheckbox.setText("Działaj dla wszystkich aplikacji");
        allAppsCheckbox.setTextColor(Color.WHITE);
        allAppsCheckbox.setTextSize(20);
        allAppsCheckbox.setPadding(0, dp(20), 0, dp(10));
        allAppsCheckbox.setFocusable(true);
        allAppsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> updateAppListEnabled());
        root.addView(allAppsCheckbox);

        TextView appHeader = text("Aplikacje — zaznacz tylko wtedy, gdy nie używasz trybu wszystkich aplikacji:", 18, true);
        appHeader.setPadding(0, dp(10), 0, dp(8));
        root.addView(appHeader);

        appListLayout = new LinearLayout(this);
        appListLayout.setOrientation(LinearLayout.VERTICAL);
        root.addView(appListLayout);

        return scrollView;
    }

    private void startDnsVpn() {
        saveSettings();
        Intent prepareIntent = VpnService.prepare(this);
        if (prepareIntent != null) {
            startActivityForResult(prepareIntent, REQ_VPN);
        } else {
            startServiceNow();
        }
    }

    private void stopDnsVpn() {
        Intent intent = new Intent(this, DnsVpnService.class);
        intent.setAction(DnsVpnService.ACTION_STOP);
        startService(intent);
        Toast.makeText(this, "DNS zatrzymany", Toast.LENGTH_SHORT).show();
    }

    private void startServiceNow() {
        Intent intent = new Intent(this, DnsVpnService.class);
        intent.setAction(DnsVpnService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Toast.makeText(this, "DNS uruchomiony", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) {
            startServiceNow();
        }
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(DnsVpnService.PREFS, MODE_PRIVATE);
        dns1Input.setText(prefs.getString(DnsVpnService.PREF_DNS_1, "94.140.14.14"));
        dns2Input.setText(prefs.getString(DnsVpnService.PREF_DNS_2, "94.140.15.15"));
        allAppsCheckbox.setChecked(prefs.getBoolean(DnsVpnService.PREF_ALL_APPS, true));
        updateAppListEnabled();
    }

    private void saveSettings() {
        Set<String> selected = new LinkedHashSet<>();
        for (AppRow row : appRows) {
            if (row.checkBox.isChecked()) {
                selected.add(row.packageName);
            }
        }

        getSharedPreferences(DnsVpnService.PREFS, MODE_PRIVATE)
                .edit()
                .putString(DnsVpnService.PREF_DNS_1, cleanDns(dns1Input.getText().toString(), "94.140.14.14"))
                .putString(DnsVpnService.PREF_DNS_2, cleanDns(dns2Input.getText().toString(), "94.140.15.15"))
                .putBoolean(DnsVpnService.PREF_ALL_APPS, allAppsCheckbox.isChecked())
                .putStringSet(DnsVpnService.PREF_SELECTED_APPS, selected)
                .apply();
    }

    private String cleanDns(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private void loadApps() {
        appListLayout.removeAllViews();
        appRows.clear();

        SharedPreferences prefs = getSharedPreferences(DnsVpnService.PREFS, MODE_PRIVATE);
        Set<String> selected = prefs.getStringSet(DnsVpnService.PREF_SELECTED_APPS, new LinkedHashSet<String>());

        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<AppItem> launchable = new ArrayList<>();

        for (ApplicationInfo info : apps) {
            if (info == null || info.packageName == null) continue;
            if (info.packageName.equals(getPackageName())) continue;
            if (pm.getLaunchIntentForPackage(info.packageName) == null) continue;

            String label = String.valueOf(pm.getApplicationLabel(info));
            launchable.add(new AppItem(label, info.packageName));
        }

        Collections.sort(launchable, Comparator.comparing(item -> item.label.toLowerCase()));

        if (launchable.isEmpty()) {
            TextView empty = text("Nie znaleziono aplikacji do wyboru.", 16, false);
            appListLayout.addView(empty);
            return;
        }

        for (AppItem item : launchable) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(item.label + "\n" + item.packageName);
            checkBox.setTextColor(Color.WHITE);
            checkBox.setTextSize(18);
            checkBox.setPadding(0, dp(8), 0, dp(8));
            checkBox.setFocusable(true);
            checkBox.setChecked(selected != null && selected.contains(item.packageName));
            checkBox.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> saveSettings());

            appListLayout.addView(checkBox);
            appRows.add(new AppRow(item.packageName, checkBox));
        }

        updateAppListEnabled();
    }

    private void updateAppListEnabled() {
        boolean enabled = allAppsCheckbox == null || !allAppsCheckbox.isChecked();
        for (AppRow row : appRows) {
            row.checkBox.setEnabled(enabled);
            row.checkBox.setAlpha(enabled ? 1.0f : 0.45f);
        }
    }

    private TextView label(String value) {
        TextView label = text(value, 16, true);
        label.setPadding(0, dp(10), 0, dp(4));
        return label;
    }

    private EditText editText() {
        EditText editText = new EditText(this);
        editText.setTextColor(Color.WHITE);
        editText.setHintTextColor(Color.GRAY);
        editText.setTextSize(20);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        editText.setPadding(dp(14), 0, dp(14), 0);
        editText.setFocusable(true);
        editText.setSelectAllOnFocus(true);
        editText.setBackgroundColor(Color.rgb(38, 38, 38));
        editText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));
        return editText;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(20);
        button.setAllCaps(false);
        button.setFocusable(true);
        return button;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(sp);
        if (bold) textView.setTypeface(textView.getTypeface(), android.graphics.Typeface.BOLD);
        return textView;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class AppItem {
        final String label;
        final String packageName;

        AppItem(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }

    private static class AppRow {
        final String packageName;
        final CheckBox checkBox;

        AppRow(String packageName, CheckBox checkBox) {
            this.packageName = packageName;
            this.checkBox = checkBox;
        }
    }

    private static class SpaceView extends View {
        SpaceView(Context context, int width, int height) {
            super(context);
            setLayoutParams(new LinearLayout.LayoutParams(width, height));
        }
    }
}
