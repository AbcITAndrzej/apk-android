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
import android.graphics.drawable.Drawable;
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
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_VPN = 42;
    private static final int SCREEN_HOME = 0;
    private static final int SCREEN_APPS = 1;
    private static final int SCREEN_SERVERS = 2;
    private static final int SCREEN_LOGS = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppItem> apps = new ArrayList<>();
    private final Map<String, String> appLabels = new LinkedHashMap<>();
    private final List<ServerPreset> presets = new ArrayList<>();

    private FrameLayout contentFrame;
    private TextView titleView;
    private TextView statusBadge;
    private Button tabHome;
    private Button tabApps;
    private Button tabServers;
    private Button tabLogs;

    private EditText appSearchInput;
    private LinearLayout appListLayout;
    private TextView appCounterText;
    private EditText customDns1;
    private EditText customDns2;
    private TextView homeStatsText;
    private TextView logStatsText;
    private LinearLayout groupedLogsLayout;

    private int currentScreen = SCREEN_HOME;
    private String appFilter = "";

    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            refreshStatusViews();
            if (currentScreen == SCREEN_LOGS) renderLogsOnly();
            handler.postDelayed(this, 1200);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initPresets();
        ensureDefaultSettings();
        loadApps();
        setContentView(buildRoot());
        showScreen(SCREEN_HOME);
        DebugLog.log(this, "SYSTEM", "UI opened: v3.0 pro debug");
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatusViews();
        handler.removeCallbacks(refreshRunnable);
        handler.post(refreshRunnable);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(makeGradient(0xFF121D2B, 0xFF0A111D));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(22), dp(14), dp(22), dp(12));
        top.setBackground(makeSolid(0xFF1D2A3A, 0, 0xFF1D2A3A, 0));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)));

        Button back = miniButton("‹");
        back.setTextSize(34);
        back.setOnClickListener(v -> showScreen(SCREEN_HOME));
        top.addView(back, new LinearLayout.LayoutParams(dp(64), dp(54)));

        titleView = text(getString(R.string.app_name), 24, true, 0xFFFFFFFF);
        titleView.setPadding(dp(14), 0, 0, 0);
        top.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        statusBadge = text("...", 14, true, 0xFFFFFFFF);
        statusBadge.setGravity(Gravity.CENTER);
        statusBadge.setPadding(dp(12), dp(8), dp(12), dp(8));
        top.addView(statusBadge, new LinearLayout.LayoutParams(dp(145), dp(46)));

        contentFrame = new FrameLayout(this);
        root.addView(contentFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(12), dp(10), dp(12), dp(10));
        bottom.setBackground(makeSolid(0xFF111B28, 0, 0xFF263143, 1));
        root.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92)));

        tabHome = navButton("⌂\n" + getString(R.string.home));
        tabApps = navButton("▦\n" + getString(R.string.apps));
        tabServers = navButton("▤\n" + getString(R.string.servers));
        tabLogs = navButton("◎\n" + getString(R.string.logs));
        tabHome.setOnClickListener(v -> showScreen(SCREEN_HOME));
        tabApps.setOnClickListener(v -> showScreen(SCREEN_APPS));
        tabServers.setOnClickListener(v -> showScreen(SCREEN_SERVERS));
        tabLogs.setOnClickListener(v -> showScreen(SCREEN_LOGS));
        bottom.addView(tabHome, new LinearLayout.LayoutParams(0, dp(72), 1));
        bottom.addView(gap(8, 1));
        bottom.addView(tabApps, new LinearLayout.LayoutParams(0, dp(72), 1));
        bottom.addView(gap(8, 1));
        bottom.addView(tabServers, new LinearLayout.LayoutParams(0, dp(72), 1));
        bottom.addView(gap(8, 1));
        bottom.addView(tabLogs, new LinearLayout.LayoutParams(0, dp(72), 1));

        return root;
    }

    private void showScreen(int screen) {
        currentScreen = screen;
        contentFrame.removeAllViews();
        updateNavSelected();
        if (screen == SCREEN_HOME) {
            titleView.setText(getString(R.string.app_name));
            contentFrame.addView(scroll(buildHomeScreen()));
        } else if (screen == SCREEN_APPS) {
            titleView.setText(getString(R.string.apps));
            contentFrame.addView(scroll(buildAppsScreen()));
        } else if (screen == SCREEN_SERVERS) {
            titleView.setText(getString(R.string.servers));
            contentFrame.addView(scroll(buildServersScreen()));
        } else {
            titleView.setText(getString(R.string.logs));
            contentFrame.addView(scroll(buildLogsScreen()));
        }
        refreshStatusViews();
    }

    private View buildHomeScreen() {
        LinearLayout root = screenRoot();

        LinearLayout hero = card(0xFF172435, 0xFF34445C);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(hero, matchWrap());

        TextView logo = text("✦", 74, true, 0xFFD9DEE7);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(makeSolid(0xFF263548, 160, 0xFF6EADEB, 2));
        hero.addView(logo, new LinearLayout.LayoutParams(dp(154), dp(154)));

        hero.addView(space(1, 18));
        TextView serverLabel = text(getString(R.string.current_server) + ":", 16, false, 0xFFB8C4D8);
        serverLabel.setGravity(Gravity.CENTER);
        hero.addView(serverLabel, matchWrap());

        TextView serverName = text(getPrefs().getString(DnsVpnService.PREF_SERVER_NAME, "AdGuard DNS") + "  →", 30, true, 0xFFFFFFFF);
        serverName.setGravity(Gravity.CENTER);
        serverName.setPadding(0, dp(4), 0, dp(10));
        serverName.setFocusable(true);
        serverName.setOnClickListener(v -> showScreen(SCREEN_SERVERS));
        applyFocus(serverName, 0x00000000, 0xFF1E3655, 18);
        hero.addView(serverName, matchWrap());

        LinearLayout shortcuts = new LinearLayout(this);
        shortcuts.setOrientation(LinearLayout.HORIZONTAL);
        shortcuts.setGravity(Gravity.CENTER);
        hero.addView(shortcuts, matchWrap());

        Button appsButton = bigIconButton("▦\n" + getString(R.string.apps));
        appsButton.setOnClickListener(v -> showScreen(SCREEN_APPS));
        shortcuts.addView(appsButton, new LinearLayout.LayoutParams(0, dp(110), 1));
        shortcuts.addView(gap(14, 1));

        Button serversButton = bigIconButton("▤\n" + getString(R.string.servers));
        serversButton.setOnClickListener(v -> showScreen(SCREEN_SERVERS));
        shortcuts.addView(serversButton, new LinearLayout.LayoutParams(0, dp(110), 1));
        shortcuts.addView(gap(14, 1));

        Button logsButton = bigIconButton("◎\n" + getString(R.string.logs));
        logsButton.setOnClickListener(v -> showScreen(SCREEN_LOGS));
        shortcuts.addView(logsButton, new LinearLayout.LayoutParams(0, dp(110), 1));

        hero.addView(space(1, 24));
        LinearLayout connectRow = new LinearLayout(this);
        connectRow.setOrientation(LinearLayout.HORIZONTAL);
        connectRow.setGravity(Gravity.CENTER);
        hero.addView(connectRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(74)));

        Button off = segmentedButton(getString(R.string.off), false);
        off.setOnClickListener(v -> stopDnsVpn());
        connectRow.addView(off, new LinearLayout.LayoutParams(0, dp(62), 1));
        connectRow.addView(gap(10, 1));
        Button on = segmentedButton(getString(R.string.on), true);
        on.setOnClickListener(v -> startDnsVpn());
        connectRow.addView(on, new LinearLayout.LayoutParams(0, dp(62), 1));

        root.addView(space(1, 16));
        LinearLayout statsCard = card(0xFF111A28, 0xFF303D51);
        root.addView(statsCard, matchWrap());
        TextView header = text(getString(R.string.stats), 20, true, 0xFFFFFFFF);
        statsCard.addView(header);
        homeStatsText = text("", 15, false, 0xFFE8EEF8);
        homeStatsText.setTypeface(Typeface.MONOSPACE);
        homeStatsText.setPadding(dp(14), dp(12), dp(14), dp(12));
        homeStatsText.setBackground(makeSolid(0xFF08111D, 16, 0xFF263143, 1));
        statsCard.addView(homeStatsText, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(14), 0, 0);
        statsCard.addView(actions, matchWrap());
        Button connect = primaryButton(getString(R.string.connect));
        connect.setOnClickListener(v -> startDnsVpn());
        actions.addView(connect, new LinearLayout.LayoutParams(0, dp(58), 1));
        actions.addView(gap(12, 1));
        Button stop = dangerButton(getString(R.string.stop));
        stop.setOnClickListener(v -> stopDnsVpn());
        actions.addView(stop, new LinearLayout.LayoutParams(0, dp(58), 1));

        return root;
    }

    private View buildServersScreen() {
        LinearLayout root = screenRoot();

        LinearLayout custom = card(0xFF152235, 0xFF34445C);
        root.addView(custom, matchWrap());
        custom.addView(text(getString(R.string.custom_server), 21, true, 0xFFFFFFFF));
        TextView hint = text(getString(R.string.focus_hint), 14, false, 0xFFB8C4D8);
        hint.setPadding(0, dp(4), 0, dp(12));
        custom.addView(hint);

        LinearLayout dnsRow = new LinearLayout(this);
        dnsRow.setOrientation(LinearLayout.HORIZONTAL);
        custom.addView(dnsRow, matchWrap());
        LinearLayout left = vertical();
        left.addView(label(getString(R.string.dns_1)));
        customDns1 = editText(getPrefs().getString(DnsVpnService.PREF_DNS_1, "94.140.14.14"));
        left.addView(customDns1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        dnsRow.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        dnsRow.addView(gap(12, 1));
        LinearLayout right = vertical();
        right.addView(label(getString(R.string.dns_2)));
        customDns2 = editText(getPrefs().getString(DnsVpnService.PREF_DNS_2, "94.140.15.15"));
        right.addView(customDns2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        dnsRow.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button save = primaryButton("＋  " + getString(R.string.save_custom));
        save.setOnClickListener(v -> saveCustomServer());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        saveParams.setMargins(0, dp(12), 0, 0);
        custom.addView(save, saveParams);

        root.addView(space(1, 16));
        LinearLayout listCard = card(0xFF101A29, 0xFF303D51);
        root.addView(listCard, matchWrap());
        listCard.addView(text(getString(R.string.server_presets), 21, true, 0xFFFFFFFF));
        listCard.addView(space(1, 10));

        for (ServerPreset preset : presets) {
            listCard.addView(serverRow(preset));
        }
        return root;
    }

    private View buildAppsScreen() {
        LinearLayout root = screenRoot();

        LinearLayout intro = card(0xFF172435, 0xFF34445C);
        root.addView(intro, matchWrap());
        intro.addView(text(getString(R.string.intro_title), 21, true, 0xFFFFFFFF));
        TextView hint = text(getString(R.string.choose_apps_hint), 15, false, 0xFFD5DEEC);
        hint.setPadding(0, dp(6), 0, dp(10));
        intro.addView(hint);

        LinearLayout mode = new LinearLayout(this);
        mode.setOrientation(LinearLayout.HORIZONTAL);
        intro.addView(mode, matchWrap());
        Button all = modeButton(getString(R.string.all_apps), isAllAppsMode());
        all.setOnClickListener(v -> { setAllAppsMode(true); showScreen(SCREEN_APPS); });
        mode.addView(all, new LinearLayout.LayoutParams(0, dp(58), 1));
        mode.addView(gap(12, 1));
        Button selected = modeButton(getString(R.string.selected_apps), !isAllAppsMode());
        selected.setOnClickListener(v -> { setAllAppsMode(false); showScreen(SCREEN_APPS); });
        mode.addView(selected, new LinearLayout.LayoutParams(0, dp(58), 1));

        root.addView(space(1, 16));
        LinearLayout tools = card(0xFF101A29, 0xFF303D51);
        root.addView(tools, matchWrap());

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        tools.addView(searchRow, matchWrap());
        appSearchInput = editText(getString(R.string.search_apps));
        appSearchInput.setSingleLine(true);
        appSearchInput.setText(appFilter);
        appSearchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        appSearchInput.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) showKeyboard(appSearchInput); });
        appSearchInput.setOnClickListener(v -> showKeyboard(appSearchInput));
        appSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean searchAction = actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (searchAction) {
                appFilter = v.getText().toString();
                renderAppList();
                hideKeyboard(v);
                return true;
            }
            return false;
        });
        appSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { appFilter = String.valueOf(s); renderAppList(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchRow.addView(appSearchInput, new LinearLayout.LayoutParams(0, dp(58), 1));
        searchRow.addView(gap(10, 1));
        Button keyboard = neutralButton(getString(R.string.show_keyboard));
        keyboard.setOnClickListener(v -> { appSearchInput.requestFocus(); showKeyboard(appSearchInput); });
        searchRow.addView(keyboard, new LinearLayout.LayoutParams(dp(150), dp(58)));

        LinearLayout bulkRow1 = new LinearLayout(this);
        bulkRow1.setOrientation(LinearLayout.HORIZONTAL);
        bulkRow1.setPadding(0, dp(12), 0, 0);
        tools.addView(bulkRow1, matchWrap());
        Button selectVisible = neutralButton(getString(R.string.select_visible));
        selectVisible.setOnClickListener(v -> setVisibleSelected(true));
        bulkRow1.addView(selectVisible, new LinearLayout.LayoutParams(0, dp(54), 1));
        bulkRow1.addView(gap(10, 1));
        Button clearVisible = neutralButton(getString(R.string.clear_visible));
        clearVisible.setOnClickListener(v -> setVisibleSelected(false));
        bulkRow1.addView(clearVisible, new LinearLayout.LayoutParams(0, dp(54), 1));

        LinearLayout bulkRow2 = new LinearLayout(this);
        bulkRow2.setOrientation(LinearLayout.HORIZONTAL);
        bulkRow2.setPadding(0, dp(10), 0, 0);
        tools.addView(bulkRow2, matchWrap());
        Button selectAll = neutralButton(getString(R.string.select_all));
        selectAll.setOnClickListener(v -> setAllSelected(true));
        bulkRow2.addView(selectAll, new LinearLayout.LayoutParams(0, dp(54), 1));
        bulkRow2.addView(gap(10, 1));
        Button clearAll = neutralButton(getString(R.string.clear_all));
        clearAll.setOnClickListener(v -> setAllSelected(false));
        bulkRow2.addView(clearAll, new LinearLayout.LayoutParams(0, dp(54), 1));

        root.addView(space(1, 16));
        LinearLayout listCard = card(0xFF101A29, 0xFF303D51);
        root.addView(listCard, matchWrap());
        appCounterText = text("", 15, false, 0xFFB8C4D8);
        listCard.addView(appCounterText, matchWrap());
        appListLayout = vertical();
        appListLayout.setPadding(0, dp(10), 0, 0);
        listCard.addView(appListLayout, matchWrap());
        renderAppList();
        return root;
    }

    private View buildLogsScreen() {
        LinearLayout root = screenRoot();
        LinearLayout stats = card(0xFF101A29, 0xFF303D51);
        root.addView(stats, matchWrap());
        stats.addView(text(getString(R.string.stats), 21, true, 0xFFFFFFFF));
        logStatsText = text("", 15, false, 0xFFE8EEF8);
        logStatsText.setTypeface(Typeface.MONOSPACE);
        logStatsText.setPadding(dp(14), dp(12), dp(14), dp(12));
        logStatsText.setBackground(makeSolid(0xFF08111D, 16, 0xFF263143, 1));
        stats.addView(logStatsText, matchWrap());

        LinearLayout prefs = new LinearLayout(this);
        prefs.setOrientation(LinearLayout.HORIZONTAL);
        prefs.setPadding(0, dp(12), 0, 0);
        stats.addView(prefs, matchWrap());
        Button saver = modeButton(getString(R.string.battery_saver), getPrefs().getBoolean(DnsVpnService.PREF_BATTERY_SAVER, true));
        saver.setOnClickListener(v -> {
            boolean next = !getPrefs().getBoolean(DnsVpnService.PREF_BATTERY_SAVER, true);
            getPrefs().edit().putBoolean(DnsVpnService.PREF_BATTERY_SAVER, next).apply();
            showScreen(SCREEN_LOGS);
        });
        prefs.addView(saver, new LinearLayout.LayoutParams(0, dp(58), 1));

        root.addView(space(1, 16));
        LinearLayout actions = card(0xFF172435, 0xFF34445C);
        root.addView(actions, matchWrap());
        TextView saverHint = text(getString(R.string.battery_saver_hint), 14, false, 0xFFB8C4D8);
        saverHint.setPadding(0, 0, 0, dp(10));
        actions.addView(saverHint);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(row, matchWrap());
        Button refresh = neutralButton(getString(R.string.refresh));
        refresh.setOnClickListener(v -> { refreshStatusViews(); renderLogsOnly(); });
        row.addView(refresh, new LinearLayout.LayoutParams(0, dp(56), 1));
        row.addView(gap(10, 1));
        Button copy = neutralButton(getString(R.string.copy_logs));
        copy.setOnClickListener(v -> copyLogToClipboard());
        row.addView(copy, new LinearLayout.LayoutParams(0, dp(56), 1));
        row.addView(gap(10, 1));
        Button clear = dangerButton(getString(R.string.clear_logs));
        clear.setOnClickListener(v -> { DebugLog.clear(this); renderLogsOnly(); toast(getString(R.string.cleared)); });
        row.addView(clear, new LinearLayout.LayoutParams(0, dp(56), 1));

        root.addView(space(1, 16));
        groupedLogsLayout = vertical();
        root.addView(groupedLogsLayout, matchWrap());
        renderLogsOnly();
        return root;
    }

    private void renderAppList() {
        if (appListLayout == null) return;
        appListLayout.removeAllViews();
        Set<String> selected = getSelectedApps();
        Set<String> logging = getLoggingApps();
        boolean allApps = isAllAppsMode();
        String q = appFilter == null ? "" : appFilter.trim().toLowerCase(Locale.US);
        int visible = 0;
        int selectedCount = selected.size();

        for (AppItem item : apps) {
            boolean show = q.isEmpty() || item.label.toLowerCase(Locale.US).contains(q) || item.packageName.toLowerCase(Locale.US).contains(q);
            if (!show) continue;
            visible++;
            appListLayout.addView(appRow(item, allApps, selected.contains(item.packageName), logging.contains(item.packageName)));
        }

        if (appCounterText != null) {
            String mode = allApps ? getString(R.string.all_apps) : getString(R.string.selected_apps_count, selectedCount);
            appCounterText.setText(getString(R.string.installed_apps) + ": " + apps.size() + " / visible: " + visible + " / " + mode);
        }
        if (visible == 0) {
            TextView empty = text(getString(R.string.no_apps_found), 16, false, 0xFFFFFFFF);
            empty.setPadding(dp(12), dp(18), dp(12), dp(18));
            appListLayout.addView(empty, matchWrap());
        }
    }

    private View appRow(AppItem item, boolean allApps, boolean selected, boolean logging) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setFocusable(true);
        applyFocus(row, 0xFF172435, 0xFF243A58, 18);

        ImageView icon = new ImageView(this);
        if (item.icon != null) icon.setImageDrawable(item.icon);
        row.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout labels = vertical();
        labels.setPadding(dp(14), 0, dp(8), 0);
        TextView name = text(item.label, 18, true, 0xFFFFFFFF);
        TextView pkg = text(item.packageName, 12, false, 0xFF8FA3BD);
        labels.addView(name);
        labels.addView(pkg);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button dns = smallStateButton(allApps || selected ? getString(R.string.dns_enabled) : getString(R.string.dns_disabled), allApps || selected);
        dns.setOnClickListener(v -> toggleAppSelected(item.packageName));
        row.addView(dns, new LinearLayout.LayoutParams(dp(118), dp(54)));
        row.addView(gap(8, 1));

        Button log = smallStateButton(logging ? getString(R.string.log_enabled) : getString(R.string.log_disabled), logging);
        log.setOnClickListener(v -> toggleAppLogging(item.packageName));
        row.addView(log, new LinearLayout.LayoutParams(dp(112), dp(54)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(lp);
        return row;
    }

    private View serverRow(ServerPreset preset) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setFocusable(true);
        applyFocus(row, 0xFF172435, 0xFF243A58, 18);
        row.setOnClickListener(v -> selectServer(preset));

        boolean current = getPrefs().getString(DnsVpnService.PREF_SERVER_NAME, "AdGuard DNS").equals(preset.name);
        TextView radio = text(current ? "◉" : "○", 31, true, current ? 0xFF7AB8F5 : 0xFF7B8DA7);
        radio.setGravity(Gravity.CENTER);
        row.addView(radio, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout labels = vertical();
        labels.setPadding(dp(10), 0, 0, 0);
        labels.addView(text(preset.name, 20, true, 0xFFFFFFFF));
        labels.addView(text(preset.kind + "  •  " + preset.dns1 + " / " + preset.dns2, 14, false, 0xFF8FA3BD));
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView arrow = text("⌄", 24, true, 0xFFB8C4D8);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(44), dp(56)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(lp);
        return row;
    }

    private void renderLogsOnly() {
        if (groupedLogsLayout == null) return;
        groupedLogsLayout.removeAllViews();
        Map<String, List<String>> grouped = DebugLog.readGrouped(this);
        if (grouped.isEmpty()) {
            LinearLayout empty = card(0xFF101A29, 0xFF303D51);
            empty.addView(text(getString(R.string.log_empty), 16, false, 0xFFFFFFFF));
            groupedLogsLayout.addView(empty, matchWrap());
            return;
        }

        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            LinearLayout groupCard = card(0xFF101A29, 0xFF303D51);
            String title = displayGroupName(entry.getKey());
            groupCard.addView(text(title, 20, true, 0xFFFFFFFF));
            TextView body = text(join(entry.getValue()), 13, false, 0xFFE8EEF8);
            body.setTypeface(Typeface.MONOSPACE);
            body.setPadding(dp(12), dp(10), dp(12), dp(10));
            body.setBackground(makeSolid(0xFF08111D, 14, 0xFF263143, 1));
            groupCard.addView(body, matchWrap());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(12));
            groupedLogsLayout.addView(groupCard, lp);
        }
    }

    private void startDnsVpn() {
        if (!validateDns(getPrefs().getString(DnsVpnService.PREF_DNS_1, "94.140.14.14")) || !validateDns(getPrefs().getString(DnsVpnService.PREF_DNS_2, "94.140.15.15"))) {
            toast(getString(R.string.invalid_dns));
            return;
        }
        if (!isAllAppsMode() && getSelectedApps().isEmpty()) {
            toast(getString(R.string.no_selected_apps));
            return;
        }
        Intent prepareIntent = VpnService.prepare(this);
        if (prepareIntent != null) {
            DebugLog.log(this, "SYSTEM", getString(R.string.vpn_permission_needed));
            startActivityForResult(prepareIntent, REQ_VPN);
        } else {
            startServiceNow();
        }
    }

    private void stopDnsVpn() {
        Intent intent = new Intent(this, DnsVpnService.class);
        intent.setAction(DnsVpnService.ACTION_STOP);
        startService(intent);
        toast(getString(R.string.stopping));
        refreshStatusViews();
    }

    private void startServiceNow() {
        Intent intent = new Intent(this, DnsVpnService.class);
        intent.setAction(DnsVpnService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent); else startService(intent);
        toast(getString(R.string.connecting));
        refreshStatusViews();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) {
            DebugLog.log(this, "SYSTEM", "VPN permission accepted");
            startServiceNow();
        } else if (requestCode == REQ_VPN) {
            DebugLog.log(this, "SYSTEM", "VPN permission denied");
            toast(getString(R.string.vpn_permission_denied));
        }
    }

    private void refreshStatusViews() {
        SharedPreferences p = getPrefs();
        boolean running = p.getBoolean(DnsVpnService.PREF_RUNNING, false);
        if (statusBadge != null) {
            statusBadge.setText(running ? getString(R.string.active) : getString(R.string.inactive));
            statusBadge.setBackground(makeSolid(running ? 0xFF1D8B5A : 0xFF7D3240, 22, running ? 0xFF8BFFD0 : 0xFFFF9DA8, 2));
        }
        String stats = buildStatsText();
        if (homeStatsText != null) homeStatsText.setText(stats);
        if (logStatsText != null) logStatsText.setText(stats);
    }

    private String buildStatsText() {
        SharedPreferences p = getPrefs();
        boolean running = p.getBoolean(DnsVpnService.PREF_RUNNING, false);
        long started = p.getLong(DnsVpnService.PREF_STARTED_AT, 0);
        String uptime = started <= 0 ? "-" : humanTime(System.currentTimeMillis() - started);
        return "status: " + (running ? "ACTIVE" : "INACTIVE") + "\n" +
                "server: " + p.getString(DnsVpnService.PREF_ACTIVE_SERVER, p.getString(DnsVpnService.PREF_SERVER_NAME, "AdGuard DNS")) + "\n" +
                "dns: " + p.getString(DnsVpnService.PREF_ACTIVE_DNS, p.getString(DnsVpnService.PREF_DNS_1, "94.140.14.14") + ", " + p.getString(DnsVpnService.PREF_DNS_2, "94.140.15.15")) + "\n" +
                "mode: " + (isAllAppsMode() ? getString(R.string.all_apps) : getString(R.string.selected_apps_count, getSelectedApps().size())) + "\n" +
                "uptime: " + uptime + "\n" +
                "packets: " + p.getLong(DnsVpnService.PREF_PACKETS, 0) + "\n" +
                "queries: " + p.getLong(DnsVpnService.PREF_DNS_QUERIES, 0) + " / responses: " + p.getLong(DnsVpnService.PREF_DNS_RESPONSES, 0) + " / failures: " + p.getLong(DnsVpnService.PREF_DNS_FAILURES, 0) + "\n" +
                "dropped: " + p.getLong(DnsVpnService.PREF_DNS_DROPPED, 0) + "\n" +
                "last domain: " + p.getString(DnsVpnService.PREF_LAST_DOMAIN, "-") + "\n" +
                "last error: " + p.getString(DnsVpnService.PREF_LAST_ERROR, "");
    }

    private void initPresets() {
        presets.clear();
        presets.add(new ServerPreset("AdGuard DNS", "Public", "94.140.14.14", "94.140.15.15"));
        presets.add(new ServerPreset("AdGuard DNS + Family Protection", "Public", "94.140.14.15", "94.140.15.16"));
        presets.add(new ServerPreset("Cloudflare", "Public", "1.1.1.1", "1.0.0.1"));
        presets.add(new ServerPreset("Google DNS", "Public", "8.8.8.8", "8.8.4.4"));
        presets.add(new ServerPreset("Quad9", "Security", "9.9.9.9", "149.112.112.112"));
        presets.add(new ServerPreset("CleanBrowsing", "Security", "185.228.168.9", "185.228.169.9"));
        presets.add(new ServerPreset("CleanBrowsing + Family Protection", "Family", "185.228.168.168", "185.228.169.168"));
        presets.add(new ServerPreset("OpenDNS", "Public", "208.67.222.222", "208.67.220.220"));
        presets.add(new ServerPreset("CONTROL D + Ads & Tracking", "Public", "76.76.2.2", "76.76.10.2"));
        presets.add(new ServerPreset("AlternateDNS", "Public", "76.76.19.19", "76.223.122.150"));
    }

    private void ensureDefaultSettings() {
        SharedPreferences p = getPrefs();
        if (!p.contains(DnsVpnService.PREF_DNS_1)) {
            p.edit()
                    .putString(DnsVpnService.PREF_SERVER_NAME, "AdGuard DNS")
                    .putString(DnsVpnService.PREF_DNS_1, "94.140.14.14")
                    .putString(DnsVpnService.PREF_DNS_2, "94.140.15.15")
                    .putBoolean(DnsVpnService.PREF_ALL_APPS, true)
                    .putBoolean(DnsVpnService.PREF_BATTERY_SAVER, true)
                    .apply();
        }
    }

    private void loadApps() {
        apps.clear();
        appLabels.clear();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installed = pm.getInstalledApplications(0);
        for (ApplicationInfo info : installed) {
            if (info == null || info.packageName == null) continue;
            if (info.packageName.equals(getPackageName())) continue;
            Intent launch = pm.getLaunchIntentForPackage(info.packageName);
            if (launch == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                launch = pm.getLeanbackLaunchIntentForPackage(info.packageName);
            }
            if (launch == null) continue;
            String label;
            try { label = String.valueOf(pm.getApplicationLabel(info)); } catch (Exception e) { label = info.packageName; }
            Drawable icon = null;
            try { icon = pm.getApplicationIcon(info.packageName); } catch (Exception ignored) {}
            apps.add(new AppItem(label, info.packageName, icon));
            appLabels.put(info.packageName, label);
        }
        Collections.sort(apps, Comparator.comparing(a -> a.label.toLowerCase(Locale.US)));
    }

    private void selectServer(ServerPreset preset) {
        getPrefs().edit()
                .putString(DnsVpnService.PREF_SERVER_NAME, preset.name)
                .putString(DnsVpnService.PREF_DNS_1, preset.dns1)
                .putString(DnsVpnService.PREF_DNS_2, preset.dns2)
                .apply();
        DebugLog.log(this, "SYSTEM", "Server selected: " + preset.name);
        toast(getString(R.string.server_selected));
        showScreen(SCREEN_SERVERS);
    }

    private void saveCustomServer() {
        String dns1 = customDns1 == null ? "" : customDns1.getText().toString().trim();
        String dns2 = customDns2 == null ? "" : customDns2.getText().toString().trim();
        if (!validateDns(dns1) || !validateDns(dns2)) {
            toast(getString(R.string.invalid_dns));
            return;
        }
        getPrefs().edit()
                .putString(DnsVpnService.PREF_SERVER_NAME, "Custom DNS")
                .putString(DnsVpnService.PREF_DNS_1, dns1)
                .putString(DnsVpnService.PREF_DNS_2, dns2)
                .apply();
        DebugLog.log(this, "SYSTEM", "Custom server saved: " + dns1 + ", " + dns2);
        toast(getString(R.string.server_saved));
        showScreen(SCREEN_SERVERS);
    }

    private void toggleAppSelected(String pkg) {
        Set<String> selected = getSelectedApps();
        if (selected.contains(pkg)) selected.remove(pkg); else selected.add(pkg);
        getPrefs().edit().putBoolean(DnsVpnService.PREF_ALL_APPS, false).putStringSet(DnsVpnService.PREF_SELECTED_APPS, selected).apply();
        renderAppList();
    }

    private void toggleAppLogging(String pkg) {
        Set<String> logging = getLoggingApps();
        if (logging.contains(pkg)) logging.remove(pkg); else logging.add(pkg);
        getPrefs().edit().putStringSet(DnsVpnService.PREF_LOGGING_APPS, logging).apply();
        renderAppList();
    }

    private void setVisibleSelected(boolean checked) {
        Set<String> selected = getSelectedApps();
        String q = appFilter == null ? "" : appFilter.trim().toLowerCase(Locale.US);
        for (AppItem item : apps) {
            boolean show = q.isEmpty() || item.label.toLowerCase(Locale.US).contains(q) || item.packageName.toLowerCase(Locale.US).contains(q);
            if (show) {
                if (checked) selected.add(item.packageName); else selected.remove(item.packageName);
            }
        }
        getPrefs().edit().putBoolean(DnsVpnService.PREF_ALL_APPS, false).putStringSet(DnsVpnService.PREF_SELECTED_APPS, selected).apply();
        renderAppList();
    }

    private void setAllSelected(boolean checked) {
        Set<String> selected = new LinkedHashSet<>();
        if (checked) for (AppItem item : apps) selected.add(item.packageName);
        getPrefs().edit().putBoolean(DnsVpnService.PREF_ALL_APPS, false).putStringSet(DnsVpnService.PREF_SELECTED_APPS, selected).apply();
        renderAppList();
    }

    private void setAllAppsMode(boolean all) {
        getPrefs().edit().putBoolean(DnsVpnService.PREF_ALL_APPS, all).apply();
    }

    private boolean isAllAppsMode() { return getPrefs().getBoolean(DnsVpnService.PREF_ALL_APPS, true); }

    private Set<String> getSelectedApps() {
        return new LinkedHashSet<>(getPrefs().getStringSet(DnsVpnService.PREF_SELECTED_APPS, new LinkedHashSet<String>()));
    }

    private Set<String> getLoggingApps() {
        return new LinkedHashSet<>(getPrefs().getStringSet(DnsVpnService.PREF_LOGGING_APPS, new LinkedHashSet<String>()));
    }

    private SharedPreferences getPrefs() { return getSharedPreferences(DnsVpnService.PREFS, MODE_PRIVATE); }

    private String displayGroupName(String key) {
        if (key == null) return "SYSTEM";
        if (key.equals("ALL_APPS")) return getString(R.string.global_log_group);
        if (key.startsWith("SELECTED_APPS_")) return getString(R.string.multi_app_log_group) + " (" + key.replace("SELECTED_APPS_", "") + ")";
        String label = appLabels.get(key);
        return label == null ? key : label;
    }

    private void copyLogToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("AdGuard TV DNS Pro logs", DebugLog.read(this)));
            toast(getString(R.string.copied));
        }
    }

    private void updateNavSelected() {
        styleNav(tabHome, currentScreen == SCREEN_HOME);
        styleNav(tabApps, currentScreen == SCREEN_APPS);
        styleNav(tabServers, currentScreen == SCREEN_SERVERS);
        styleNav(tabLogs, currentScreen == SCREEN_LOGS);
    }

    private boolean validateDns(String dns) {
        if (dns == null) return false;
        String[] parts = dns.trim().split("\\.");
        if (parts.length != 4) return false;
        try {
            for (String part : parts) {
                int n = Integer.parseInt(part);
                if (n < 0 || n > 255) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private ScrollView scroll(View child) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(child, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private LinearLayout screenRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(22));
        return root;
    }

    private LinearLayout card(int color, int border) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(makeSolid(color, 22, border, 1));
        return card;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(value == null ? "" : value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setIncludeFontPadding(true);
        return t;
    }

    private TextView label(String value) {
        TextView t = text(value, 13, true, 0xFF8FA3BD);
        t.setPadding(0, 0, 0, dp(5));
        return t;
    }

    private EditText editText(String hintOrValue) {
        EditText e = new EditText(this);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(0xFF7D8EA8);
        e.setTextSize(16);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        if (hintOrValue != null && (hintOrValue.contains(".") && validateMaybeIp(hintOrValue))) {
            e.setText(hintOrValue);
        } else {
            e.setHint(hintOrValue);
        }
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setSelectAllOnFocus(false);
        e.setFocusable(true);
        e.setFocusableInTouchMode(true);
        e.setBackground(makeSolid(0xFF08111D, 14, 0xFF476D9B, 2));
        e.setOnClickListener(v -> showKeyboard(e));
        return e;
    }

    private boolean validateMaybeIp(String value) { return value.matches("[0-9.]+") || value.length() < 16; }

    private Button navButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setGravity(Gravity.CENTER);
        b.setAllCaps(false);
        b.setFocusable(true);
        return b;
    }

    private Button bigIconButton(String value) { return baseButton(value, 0xFF172435, 0xFF253C5D, 0xFFFFFFFF, 18); }
    private Button primaryButton(String value) { return baseButton(value, 0xFF2E7DD1, 0xFF3C95F4, 0xFFFFFFFF, 16); }
    private Button dangerButton(String value) { return baseButton(value, 0xFF813443, 0xFFB64254, 0xFFFFFFFF, 16); }
    private Button neutralButton(String value) { return baseButton(value, 0xFF253348, 0xFF334A6D, 0xFFFFFFFF, 15); }
    private Button miniButton(String value) { return baseButton(value, 0xFF263548, 0xFF36567E, 0xFFFFFFFF, 20); }
    private Button segmentedButton(String value, boolean on) { return baseButton(value, on ? 0xFF2E7DD1 : 0xFF303A49, on ? 0xFF3C95F4 : 0xFF48576A, 0xFFFFFFFF, 17); }
    private Button modeButton(String value, boolean selected) { return baseButton(value, selected ? 0xFF2E7DD1 : 0xFF253348, selected ? 0xFF8DCAFF : 0xFF334A6D, 0xFFFFFFFF, 15); }
    private Button smallStateButton(String value, boolean selected) { return baseButton(value, selected ? 0xFF1D8B5A : 0xFF314054, selected ? 0xFF8BFFD0 : 0xFF6D7C92, 0xFFFFFFFF, 12); }

    private Button baseButton(String value, int normal, int focus, int textColor, int sp) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(textColor);
        b.setTextSize(sp);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setFocusable(true);
        applyFocus(b, normal, focus, 18);
        return b;
    }

    private void styleNav(Button b, boolean selected) {
        if (b == null) return;
        b.setBackground(makeSolid(selected ? 0xFF2E7DD1 : 0xFF1D2A3A, 18, selected ? 0xFF8DCAFF : 0xFF2B3A4F, selected ? 2 : 1));
    }

    private void applyFocus(View view, int normal, int focus, int radius) {
        view.setBackground(makeSolid(normal, radius, 0xFF33465F, 1));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) view.setDefaultFocusHighlightEnabled(false);
        view.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackground(makeSolid(hasFocus ? focus : normal, radius, hasFocus ? 0xFF9FD2FF : 0xFF33465F, hasFocus ? 4 : 1));
            v.setScaleX(hasFocus ? 1.025f : 1f);
            v.setScaleY(hasFocus ? 1.025f : 1f);
        });
    }

    private GradientDrawable makeSolid(int color, int radius, int border, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if (stroke > 0) g.setStroke(dp(stroke), border);
        return g;
    }

    private GradientDrawable makeGradient(int top, int bottom) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        return g;
    }

    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private Space gap(int w, int h) { Space s = new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(dp(w), dp(h))); return s; }
    private Space space(int w, int h) { return gap(w, h); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void showKeyboard(EditText editText) {
        editText.postDelayed(() -> {
            editText.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }, 120);
    }

    private void hideKeyboard(TextView view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }

    private String humanTime(long millis) {
        long sec = millis / 1000;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private String join(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String s : lines) sb.append(s).append('\n');
        return sb.toString();
    }

    private static class AppItem {
        final String label;
        final String packageName;
        final Drawable icon;
        AppItem(String label, String packageName, Drawable icon) { this.label = label; this.packageName = packageName; this.icon = icon; }
    }

    private static class ServerPreset {
        final String name;
        final String kind;
        final String dns1;
        final String dns2;
        ServerPreset(String name, String kind, String dns1, String dns2) { this.name = name; this.kind = kind; this.dns1 = dns1; this.dns2 = dns2; }
    }
}
