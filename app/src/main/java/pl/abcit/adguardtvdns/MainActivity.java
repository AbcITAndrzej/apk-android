package pl.abcit.adguardtvdns;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_VPN = 42;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppRow> appRows = new ArrayList<>();

    private EditText dns1Input;
    private EditText dns2Input;
    private EditText searchInput;
    private CheckBox allAppsCheckbox;
    private TextView statusBadge;
    private TextView appCounterText;
    private TextView debugStatsText;
    private TextView debugLogText;
    private LinearLayout appListLayout;
    private LinearLayout appsPanel;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshDebugPanel();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        loadSettings();
        loadApps();
        filterApps("");
        DebugLog.log(this, "Panel otwarty");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDebugPanel();
        handler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        saveSettings();
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    private View buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackground(makeBackground(0xFF07111F, 0xFF111827, 0));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(34), dp(28), dp(34), dp(34));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, matchWrap());

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView title = text("AdGuard TV DNS Pro", 30, true, 0xFFFFFFFF);
        titleBox.addView(title);

        TextView subtitle = text("Lekki DNS przez lokalny VPN • Android TV • wybór aplikacji • debug", 15, false, 0xFFB6C2D6);
        subtitle.setPadding(0, dp(3), 0, 0);
        titleBox.addView(subtitle);

        statusBadge = text("STATUS", 18, true, 0xFFFFFFFF);
        statusBadge.setGravity(Gravity.CENTER);
        statusBadge.setPadding(dp(18), dp(10), dp(18), dp(10));
        header.addView(statusBadge, new LinearLayout.LayoutParams(dp(210), dp(54)));

        root.addView(spacer(1, 18));
        root.addView(buildDnsCard());
        root.addView(spacer(1, 18));
        root.addView(buildAppsCard());
        root.addView(spacer(1, 18));
        root.addView(buildDebugCard());

        return scrollView;
    }

    private View buildDnsCard() {
        LinearLayout card = card(0xFF111C2E, 0xFF24324A);

        TextView section = text("1. Połączenie", 22, true, 0xFFFFFFFF);
        card.addView(section);

        TextView hint = text("Domyślnie używa AdGuard DNS: 94.140.14.14 oraz 94.140.15.15. Możesz wpisać własne DNS IPv4.", 15, false, 0xFFB6C2D6);
        hint.setPadding(0, dp(4), 0, dp(14));
        card.addView(hint);

        LinearLayout dnsRow = new LinearLayout(this);
        dnsRow.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(dnsRow, matchWrap());

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        dnsRow.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        left.addView(label("DNS 1"));
        dns1Input = editText("94.140.14.14");
        left.addView(dns1Input);

        dnsRow.addView(spacer(14, 1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        dnsRow.addView(right, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        right.addView(label("DNS 2"));
        dns2Input = editText("94.140.15.15");
        right.addView(dns2Input);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(18), 0, 0);
        card.addView(actions, matchWrap());

        Button connect = primaryButton("CONNECT");
        connect.setOnClickListener(v -> startDnsVpn());
        actions.addView(connect, new LinearLayout.LayoutParams(0, dp(62), 1));

        actions.addView(spacer(12, 1));

        Button stop = dangerButton("STOP");
        stop.setOnClickListener(v -> stopDnsVpn());
        actions.addView(stop, new LinearLayout.LayoutParams(0, dp(62), 1));

        actions.addView(spacer(12, 1));

        Button defaults = neutralButton("AdGuard default");
        defaults.setOnClickListener(v -> {
            dns1Input.setText("94.140.14.14");
            dns2Input.setText("94.140.15.15");
            saveSettings();
            toast("Ustawiono domyślne DNS AdGuard");
        });
        actions.addView(defaults, new LinearLayout.LayoutParams(0, dp(62), 1));

        return card;
    }

    private View buildAppsCard() {
        appsPanel = card(0xFF101827, 0xFF283448);

        TextView section = text("2. Aplikacje", 22, true, 0xFFFFFFFF);
        appsPanel.addView(section);

        TextView hint = text("Wybierz, czy DNS ma działać dla całego Android TV, czy tylko dla wskazanych aplikacji.", 15, false, 0xFFB6C2D6);
        hint.setPadding(0, dp(4), 0, dp(12));
        appsPanel.addView(hint);

        allAppsCheckbox = new CheckBox(this);
        allAppsCheckbox.setText("Wszystkie aplikacje korzystają z tego DNS");
        allAppsCheckbox.setTextColor(Color.WHITE);
        allAppsCheckbox.setTextSize(19);
        allAppsCheckbox.setButtonTintList(null);
        allAppsCheckbox.setFocusable(true);
        allAppsCheckbox.setPadding(0, dp(6), 0, dp(10));
        allAppsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateAppListEnabled();
            saveSettings();
        });
        appsPanel.addView(allAppsCheckbox, matchWrap());

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        appsPanel.addView(searchRow, matchWrap());

        searchInput = editText("Szukaj aplikacji, np. chrome, firefox, youtube...");
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterApps(String.valueOf(s)); }
            @Override public void afterTextChanged(Editable s) { }
        });
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, dp(58), 1));

        searchRow.addView(spacer(12, 1));

        Button clearSearch = neutralButton("Wyczyść");
        clearSearch.setOnClickListener(v -> searchInput.setText(""));
        searchRow.addView(clearSearch, new LinearLayout.LayoutParams(dp(150), dp(58)));

        LinearLayout selectRow = new LinearLayout(this);
        selectRow.setOrientation(LinearLayout.HORIZONTAL);
        selectRow.setPadding(0, dp(12), 0, dp(10));
        appsPanel.addView(selectRow, matchWrap());

        Button selectVisible = neutralButton("Zaznacz widoczne");
        selectVisible.setOnClickListener(v -> setVisibleRowsChecked(true));
        selectRow.addView(selectVisible, new LinearLayout.LayoutParams(0, dp(56), 1));

        selectRow.addView(spacer(10, 1));

        Button clearVisible = neutralButton("Odznacz widoczne");
        clearVisible.setOnClickListener(v -> setVisibleRowsChecked(false));
        selectRow.addView(clearVisible, new LinearLayout.LayoutParams(0, dp(56), 1));

        selectRow.addView(spacer(10, 1));

        Button selectAll = neutralButton("Zaznacz wszystkie");
        selectAll.setOnClickListener(v -> setAllRowsChecked(true));
        selectRow.addView(selectAll, new LinearLayout.LayoutParams(0, dp(56), 1));

        selectRow.addView(spacer(10, 1));

        Button clearAll = neutralButton("Odznacz wszystkie");
        clearAll.setOnClickListener(v -> setAllRowsChecked(false));
        selectRow.addView(clearAll, new LinearLayout.LayoutParams(0, dp(56), 1));

        appCounterText = text("Ładowanie aplikacji...", 15, false, 0xFFB6C2D6);
        appCounterText.setPadding(0, dp(4), 0, dp(8));
        appsPanel.addView(appCounterText);

        appListLayout = new LinearLayout(this);
        appListLayout.setOrientation(LinearLayout.VERTICAL);
        appsPanel.addView(appListLayout, matchWrap());

        return appsPanel;
    }

    private View buildDebugCard() {
        LinearLayout card = card(0xFF0D1624, 0xFF263143);

        TextView section = text("3. Debug", 22, true, 0xFFFFFFFF);
        card.addView(section);

        TextView hint = text("Tu widać status VPN, liczniki DNS i ostatnie zdarzenia. Przy problemie zrób screen tego panelu.", 15, false, 0xFFB6C2D6);
        hint.setPadding(0, dp(4), 0, dp(12));
        card.addView(hint);

        debugStatsText = text("Status: ...", 16, false, 0xFFFFFFFF);
        debugStatsText.setTypeface(Typeface.MONOSPACE);
        debugStatsText.setPadding(dp(14), dp(12), dp(14), dp(12));
        debugStatsText.setBackground(makeSolid(0xFF050B14, 14, 0xFF1E2A3B));
        card.addView(debugStatsText, matchWrap());

        LinearLayout logActions = new LinearLayout(this);
        logActions.setOrientation(LinearLayout.HORIZONTAL);
        logActions.setPadding(0, dp(12), 0, dp(10));
        card.addView(logActions, matchWrap());

        Button refresh = neutralButton("Odśwież");
        refresh.setOnClickListener(v -> refreshDebugPanel());
        logActions.addView(refresh, new LinearLayout.LayoutParams(0, dp(54), 1));

        logActions.addView(spacer(10, 1));

        Button copy = neutralButton("Kopiuj log");
        copy.setOnClickListener(v -> copyLogToClipboard());
        logActions.addView(copy, new LinearLayout.LayoutParams(0, dp(54), 1));

        logActions.addView(spacer(10, 1));

        Button clear = dangerButton("Wyczyść log");
        clear.setOnClickListener(v -> {
            DebugLog.clear(this);
            refreshDebugPanel();
            toast("Log wyczyszczony");
        });
        logActions.addView(clear, new LinearLayout.LayoutParams(0, dp(54), 1));

        debugLogText = text("", 14, false, 0xFFE5E7EB);
        debugLogText.setTypeface(Typeface.MONOSPACE);
        debugLogText.setPadding(dp(14), dp(12), dp(14), dp(12));
        debugLogText.setBackground(makeSolid(0xFF050B14, 14, 0xFF1E2A3B));
        card.addView(debugLogText, matchWrap());

        return card;
    }

    private void startDnsVpn() {
        saveSettings();
        if (!allAppsCheckbox.isChecked() && selectedCount() == 0) {
            toast("Najpierw zaznacz aplikacje albo włącz tryb: wszystkie aplikacje");
            DebugLog.log(this, "Start zablokowany: brak wybranych aplikacji");
            refreshDebugPanel();
            return;
        }

        if (!looksLikeIpv4(cleanDns(dns1Input.getText().toString(), "")) || !looksLikeIpv4(cleanDns(dns2Input.getText().toString(), ""))) {
            toast("Wpisz DNS IPv4, np. 94.140.14.14");
            DebugLog.log(this, "Start zablokowany: DNS wygląda niepoprawnie");
            refreshDebugPanel();
            return;
        }

        Intent prepareIntent = VpnService.prepare(this);
        if (prepareIntent != null) {
            DebugLog.log(this, "Android wymaga zgody na VPN");
            startActivityForResult(prepareIntent, REQ_VPN);
        } else {
            startServiceNow();
        }
    }

    private void stopDnsVpn() {
        Intent intent = new Intent(this, DnsVpnService.class);
        intent.setAction(DnsVpnService.ACTION_STOP);
        startService(intent);
        toast("DNS zatrzymywany");
        refreshDebugPanel();
    }

    private void startServiceNow() {
        Intent intent = new Intent(this, DnsVpnService.class);
        intent.setAction(DnsVpnService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        toast("Łączenie DNS...");
        refreshDebugPanel();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) {
            DebugLog.log(this, "Zgoda VPN zaakceptowana");
            startServiceNow();
        } else if (requestCode == REQ_VPN) {
            DebugLog.log(this, "Zgoda VPN odrzucona");
            toast("Bez zgody VPN aplikacja nie zmieni DNS");
            refreshDebugPanel();
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
        if (dns1Input == null || dns2Input == null || allAppsCheckbox == null) return;
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
        updateCountersOnly();
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

            String label;
            try {
                label = String.valueOf(pm.getApplicationLabel(info));
            } catch (Exception e) {
                label = info.packageName;
            }
            launchable.add(new AppItem(label, info.packageName));
        }

        Collections.sort(launchable, Comparator.comparing(item -> item.label.toLowerCase(Locale.US)));

        if (launchable.isEmpty()) {
            TextView empty = text("Nie znaleziono aplikacji do wyboru.", 16, false, 0xFFE5E7EB);
            empty.setPadding(0, dp(12), 0, dp(12));
            appListLayout.addView(empty);
            updateCountersOnly();
            return;
        }

        for (AppItem item : launchable) {
            LinearLayout rowView = new LinearLayout(this);
            rowView.setOrientation(LinearLayout.VERTICAL);
            rowView.setPadding(dp(12), dp(8), dp(12), dp(8));
            rowView.setBackground(makeSolid(0xFF162235, 12, 0xFF29384F));

            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(item.label + "\n" + item.packageName);
            checkBox.setTextColor(Color.WHITE);
            checkBox.setTextSize(17);
            checkBox.setFocusable(true);
            checkBox.setPadding(0, dp(5), 0, dp(5));
            checkBox.setChecked(selected != null && selected.contains(item.packageName));
            checkBox.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> saveSettings());

            rowView.addView(checkBox, matchWrap());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, dp(8));
            appListLayout.addView(rowView, params);
            appRows.add(new AppRow(item.label, item.packageName, rowView, checkBox));
        }

        updateAppListEnabled();
        updateCountersOnly();
    }

    private void filterApps(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
        int visible = 0;
        for (AppRow row : appRows) {
            boolean show = q.isEmpty()
                    || row.label.toLowerCase(Locale.US).contains(q)
                    || row.packageName.toLowerCase(Locale.US).contains(q);
            row.rowView.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) visible++;
        }
        updateCountersOnly(visible);
    }

    private void setVisibleRowsChecked(boolean checked) {
        for (AppRow row : appRows) {
            if (row.rowView.getVisibility() == View.VISIBLE) {
                row.checkBox.setChecked(checked);
            }
        }
        saveSettings();
        updateCountersOnly();
    }

    private void setAllRowsChecked(boolean checked) {
        for (AppRow row : appRows) {
            row.checkBox.setChecked(checked);
        }
        saveSettings();
        updateCountersOnly();
    }

    private void updateAppListEnabled() {
        boolean enabled = allAppsCheckbox == null || !allAppsCheckbox.isChecked();
        for (AppRow row : appRows) {
            row.checkBox.setEnabled(enabled);
            row.rowView.setAlpha(enabled ? 1.0f : 0.45f);
        }
        if (searchInput != null) searchInput.setEnabled(enabled);
        updateCountersOnly();
    }

    private void refreshDebugPanel() {
        SharedPreferences prefs = getSharedPreferences(DnsVpnService.PREFS, MODE_PRIVATE);
        boolean running = prefs.getBoolean(DnsVpnService.PREF_RUNNING, false);
        String lastError = prefs.getString(DnsVpnService.PREF_LAST_ERROR, "");
        String lastEvent = prefs.getString(DnsVpnService.PREF_LAST_EVENT, "");
        String activeDns = prefs.getString(DnsVpnService.PREF_ACTIVE_DNS, dns1Input.getText() + ", " + dns2Input.getText());
        String activeMode = prefs.getString(DnsVpnService.PREF_ACTIVE_MODE, allAppsCheckbox.isChecked() ? "Wszystkie aplikacje" : "Wybrane aplikacje: " + selectedCount());
        String lastDomain = prefs.getString(DnsVpnService.PREF_LAST_DOMAIN, "-");
        long startedAt = prefs.getLong(DnsVpnService.PREF_STARTED_AT, 0);
        long packets = prefs.getLong(DnsVpnService.PREF_PACKETS, 0);
        long queries = prefs.getLong(DnsVpnService.PREF_DNS_QUERIES, 0);
        long responses = prefs.getLong(DnsVpnService.PREF_DNS_RESPONSES, 0);
        long failures = prefs.getLong(DnsVpnService.PREF_DNS_FAILURES, 0);

        statusBadge.setText(running ? "ONLINE" : "OFFLINE");
        statusBadge.setBackground(makeSolid(running ? 0xFF00A86B : 0xFF374151, 28, running ? 0xFF34D399 : 0xFF6B7280));

        String started = startedAt == 0 ? "-" : new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(startedAt));
        StringBuilder stats = new StringBuilder();
        stats.append("running       : ").append(running ? "YES" : "NO").append('\n');
        stats.append("event         : ").append(emptyDash(lastEvent)).append('\n');
        stats.append("mode          : ").append(activeMode).append('\n');
        stats.append("dns upstream  : ").append(activeDns).append('\n');
        stats.append("started at    : ").append(started).append('\n');
        stats.append("packets       : ").append(packets).append('\n');
        stats.append("dns queries   : ").append(queries).append('\n');
        stats.append("dns responses : ").append(responses).append('\n');
        stats.append("dns failures  : ").append(failures).append('\n');
        stats.append("last domain   : ").append(emptyDash(lastDomain)).append('\n');
        stats.append("last error    : ").append(emptyDash(lastError));
        debugStatsText.setText(stats.toString());

        String log = DebugLog.read(this);
        if (log.length() > 5000) {
            log = log.substring(log.length() - 5000);
        }
        debugLogText.setText(log.trim().isEmpty() ? "Brak logów." : log);
        updateCountersOnly();
    }

    private void updateCountersOnly() {
        int visible = 0;
        for (AppRow row : appRows) {
            if (row.rowView.getVisibility() == View.VISIBLE) visible++;
        }
        updateCountersOnly(visible);
    }

    private void updateCountersOnly(int visible) {
        if (appCounterText == null) return;
        int selected = selectedCount();
        String mode = allAppsCheckbox != null && allAppsCheckbox.isChecked() ? "tryb: wszystkie aplikacje" : "tryb: wybrane aplikacje";
        appCounterText.setText("Aplikacje: " + appRows.size() + " • widoczne: " + visible + " • zaznaczone: " + selected + " • " + mode);
    }

    private void copyLogToClipboard() {
        String all = debugStatsText.getText().toString() + "\n\n" + DebugLog.read(this);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("AdGuard TV DNS debug", all));
            toast("Skopiowano debug do schowka");
        }
    }

    private int selectedCount() {
        int count = 0;
        for (AppRow row : appRows) {
            if (row.checkBox.isChecked()) count++;
        }
        return count;
    }

    private boolean looksLikeIpv4(String value) {
        if (value == null) return false;
        String[] parts = value.trim().split("\\.");
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                int v = Integer.parseInt(part);
                if (v < 0 || v > 255) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private String cleanDns(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private String emptyDash(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private LinearLayout card(int color, int stroke) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(20), dp(22), dp(22));
        card.setBackground(makeSolid(color, 18, stroke));
        card.setFocusable(false);
        return card;
    }

    private TextView label(String value) {
        TextView label = text(value, 15, true, 0xFFE5E7EB);
        label.setPadding(0, 0, 0, dp(5));
        return label;
    }

    private EditText editText(String hint) {
        EditText editText = new EditText(this);
        editText.setTextColor(Color.WHITE);
        editText.setHintTextColor(0xFF8EA0B8);
        editText.setHint(hint);
        editText.setTextSize(18);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        editText.setPadding(dp(14), 0, dp(14), 0);
        editText.setFocusable(true);
        editText.setSelectAllOnFocus(true);
        editText.setBackground(makeSolid(0xFF07111F, 14, 0xFF334155));
        return editText;
    }

    private Button primaryButton(String value) {
        Button button = baseButton(value);
        button.setBackground(makeSolid(0xFF00A86B, 14, 0xFF34D399));
        button.setTextColor(Color.WHITE);
        return button;
    }

    private Button dangerButton(String value) {
        Button button = baseButton(value);
        button.setBackground(makeSolid(0xFFB42318, 14, 0xFFF97066));
        button.setTextColor(Color.WHITE);
        return button;
    }

    private Button neutralButton(String value) {
        Button button = baseButton(value);
        button.setBackground(makeSolid(0xFF24324A, 14, 0xFF475569));
        button.setTextColor(Color.WHITE);
        return button;
    }

    private Button baseButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(17);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextColor(color);
        textView.setTextSize(sp);
        if (bold) textView.setTypeface(textView.getTypeface(), Typeface.BOLD);
        return textView;
    }

    private View spacer(int widthDp, int heightDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)));
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private GradientDrawable makeSolid(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private GradientDrawable makeBackground(int startColor, int endColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{startColor, endColor}
        );
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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
        final String label;
        final String packageName;
        final View rowView;
        final CheckBox checkBox;

        AppRow(String label, String packageName, View rowView, CheckBox checkBox) {
            this.label = label;
            this.packageName = packageName;
            this.rowView = rowView;
            this.checkBox = checkBox;
        }
    }
}
