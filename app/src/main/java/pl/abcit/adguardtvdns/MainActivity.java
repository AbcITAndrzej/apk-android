package pl.abcit.adguardtvdns;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

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
    private static final int REQ_EXPORT_SETTINGS = 70;
    private static final int REQ_IMPORT_SETTINGS = 71;
    private static final int REQ_EXPORT_LOGS = 72;
    private static final int APP_FILTER_ALL = 0;
    private static final int APP_FILTER_DNS = 1;
    private static final int APP_FILTER_NO_DNS = 2;
    private static final String PREF_PROFILES_JSON = "profiles_json";
    private static final int SCREEN_HOME = 0;
    private static final int SCREEN_APPS = 1;
    private static final int SCREEN_SERVERS = 2;
    private static final int SCREEN_LOGS = 3;
    private static final int SCREEN_SETTINGS = 4;
    private static final String PREF_LANGUAGE = "ui_language";
    private static final String PREF_THEME = "ui_theme";
    private static final String THEME_SYSTEM = "system";
    private static final String THEME_DARK = "dark";
    private static final String THEME_LIGHT = "light";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppItem> apps = new ArrayList<>();
    private final Map<String, String> appLabels = new LinkedHashMap<>();
    private final List<ServerPreset> presets = new ArrayList<>();

    private FrameLayout contentFrame;
    private TextView titleView;
    private TextView statusBadge;
    private Button starPowerButton;
    private Button topLeftButton;
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
    private int appListFilterMode = APP_FILTER_ALL;

    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            refreshStatusViews();
            if (currentScreen == SCREEN_LOGS) renderLogsOnly();
            handler.postDelayed(this, 1200);
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(applyLocale(newBase));
    }

    private static Context applyLocale(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(DnsVpnService.PREFS, MODE_PRIVATE);
            String code = prefs.getString(PREF_LANGUAGE, "");
            if (code == null || code.trim().isEmpty() || "system".equals(code)) return context;
            Locale locale = new Locale(code);
            Locale.setDefault(locale);
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.setLocale(locale);
            return context.createConfigurationContext(config);
        } catch (Exception ignored) {
            return context;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initPresets();
        ensureDefaultSettings();
        loadApps();
        setContentView(buildRoot());
        showScreen(SCREEN_HOME);
        DebugLog.log(this, "SYSTEM", "UI opened: v3.5.2 updater UI fix");
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
        root.setBackground(makeGradient(bgTop(), bgBottom()));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(22), dp(14), dp(22), dp(12));
        top.setBackground(makeSolid(topBarColor(), 0, topBarColor(), 0));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70)));

        topLeftButton = miniButton("⚙");
        topLeftButton.setTextSize(28);
        topLeftButton.setOnClickListener(v -> { if (currentScreen == SCREEN_HOME) showScreen(SCREEN_SETTINGS); else showScreen(SCREEN_HOME); });
        top.addView(topLeftButton, new LinearLayout.LayoutParams(dp(58), dp(50)));

        titleView = text(getString(R.string.app_name), 24, true, 0xFFFFFFFF);
        titleView.setPadding(dp(14), 0, 0, 0);
        top.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        statusBadge = text("...", 14, true, 0xFFFFFFFF);
        statusBadge.setGravity(Gravity.CENTER);
        statusBadge.setPadding(dp(12), dp(8), dp(12), dp(8));
        top.addView(statusBadge, new LinearLayout.LayoutParams(dp(132), dp(42)));

        contentFrame = new FrameLayout(this);
        root.addView(contentFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(12), dp(8), dp(12), dp(8));
        bottom.setBackground(makeSolid(bottomBarColor(), 0, borderColor(), 1));
        root.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(84)));

        tabHome = navButton("⌂\n" + getString(R.string.home));
        tabApps = navButton("▦\n" + getString(R.string.apps));
        tabServers = navButton("▤\n" + getString(R.string.servers));
        tabLogs = navButton("◎\n" + getString(R.string.logs));
        tabHome.setOnClickListener(v -> showScreen(SCREEN_HOME));
        tabApps.setOnClickListener(v -> showScreen(SCREEN_APPS));
        tabServers.setOnClickListener(v -> showScreen(SCREEN_SERVERS));
        tabLogs.setOnClickListener(v -> showScreen(SCREEN_LOGS));
        bottom.addView(tabHome, new LinearLayout.LayoutParams(0, dp(64), 1));
        bottom.addView(gap(8, 1));
        bottom.addView(tabApps, new LinearLayout.LayoutParams(0, dp(64), 1));
        bottom.addView(gap(8, 1));
        bottom.addView(tabServers, new LinearLayout.LayoutParams(0, dp(64), 1));
        bottom.addView(gap(8, 1));
        bottom.addView(tabLogs, new LinearLayout.LayoutParams(0, dp(64), 1));

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
        } else if (screen == SCREEN_LOGS) {
            titleView.setText(getString(R.string.logs));
            contentFrame.addView(scroll(buildLogsScreen()));
        } else {
            titleView.setText(getString(R.string.settings));
            contentFrame.addView(scroll(buildSettingsScreen()));
        }
        refreshStatusViews();
        if (screen == SCREEN_HOME) requestHomeDefaultFocus();
    }

    private View buildHomeScreen() {
        LinearLayout root = screenRoot();

        LinearLayout hero = card(0xFF172435, 0xFF34445C);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(hero, matchWrap());

        starPowerButton = baseButton("✦", 0xFF263548, 0xFF3C95F4, 0xFFD9DEE7, 54);
        starPowerButton.setContentDescription(getString(R.string.power_button_hint));
        starPowerButton.setOnFocusChangeListener((v, hasFocus) -> refreshStatusViews());
        starPowerButton.setOnClickListener(v -> toggleDnsVpn());
        hero.addView(starPowerButton, new LinearLayout.LayoutParams(dp(138), dp(138)));

        hero.addView(space(1, 18));
        TextView serverLabel = text(getString(R.string.current_server) + ":", 16, false, 0xFFB8C4D8);
        serverLabel.setGravity(Gravity.CENTER);
        hero.addView(serverLabel, matchWrap());

        TextView serverName = text(getPrefs().getString(DnsVpnService.PREF_SERVER_NAME, "AdGuard DNS") + "  →", 26, true, 0xFFFFFFFF);
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
        shortcuts.addView(appsButton, new LinearLayout.LayoutParams(0, dp(96), 1));
        shortcuts.addView(gap(14, 1));

        Button serversButton = bigIconButton("▤\n" + getString(R.string.servers));
        serversButton.setOnClickListener(v -> showScreen(SCREEN_SERVERS));
        shortcuts.addView(serversButton, new LinearLayout.LayoutParams(0, dp(96), 1));
        shortcuts.addView(gap(14, 1));

        Button logsButton = bigIconButton("◎\n" + getString(R.string.logs));
        logsButton.setOnClickListener(v -> showScreen(SCREEN_LOGS));
        shortcuts.addView(logsButton, new LinearLayout.LayoutParams(0, dp(96), 1));
        shortcuts.addView(gap(14, 1));

        Button settingsButton = bigIconButton("⚙\n" + getString(R.string.settings));
        settingsButton.setOnClickListener(v -> showScreen(SCREEN_SETTINGS));
        shortcuts.addView(settingsButton, new LinearLayout.LayoutParams(0, dp(96), 1));

        hero.addView(space(1, 24));
        LinearLayout connectRow = new LinearLayout(this);
        connectRow.setOrientation(LinearLayout.HORIZONTAL);
        connectRow.setGravity(Gravity.CENTER);
        hero.addView(connectRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));

        Button off = segmentedButton(getString(R.string.off), false);
        off.setOnClickListener(v -> stopDnsVpn());
        connectRow.addView(off, new LinearLayout.LayoutParams(0, dp(54), 1));
        connectRow.addView(gap(10, 1));
        Button on = segmentedButton(getString(R.string.on), true);
        on.setOnClickListener(v -> startDnsVpn());
        connectRow.addView(on, new LinearLayout.LayoutParams(0, dp(54), 1));

        root.addView(space(1, 16));
        root.addView(buildProfilesCard(), matchWrap());

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

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(0, dp(10), 0, 0);
        tools.addView(filterRow, matchWrap());
        Button filterAll = modeButton(getString(R.string.filter_all_apps), appListFilterMode == APP_FILTER_ALL);
        filterAll.setOnClickListener(v -> { appListFilterMode = APP_FILTER_ALL; showScreen(SCREEN_APPS); });
        filterRow.addView(filterAll, new LinearLayout.LayoutParams(0, dp(54), 1));
        filterRow.addView(gap(10, 1));
        Button filterDns = modeButton(getString(R.string.filter_dns_apps), appListFilterMode == APP_FILTER_DNS);
        filterDns.setOnClickListener(v -> { appListFilterMode = APP_FILTER_DNS; showScreen(SCREEN_APPS); });
        filterRow.addView(filterDns, new LinearLayout.LayoutParams(0, dp(54), 1));
        filterRow.addView(gap(10, 1));
        Button filterNoDns = modeButton(getString(R.string.filter_no_dns_apps), appListFilterMode == APP_FILTER_NO_DNS);
        filterNoDns.setOnClickListener(v -> { appListFilterMode = APP_FILTER_NO_DNS; showScreen(SCREEN_APPS); });
        filterRow.addView(filterNoDns, new LinearLayout.LayoutParams(0, dp(54), 1));

        LinearLayout profileRow = new LinearLayout(this);
        profileRow.setOrientation(LinearLayout.HORIZONTAL);
        profileRow.setPadding(0, dp(10), 0, 0);
        tools.addView(profileRow, matchWrap());
        Button saveProfile = primaryButton(getString(R.string.save_current_profile));
        saveProfile.setOnClickListener(v -> promptSaveProfile());
        profileRow.addView(saveProfile, new LinearLayout.LayoutParams(0, dp(56), 1));
        profileRow.addView(gap(10, 1));
        Button quickSelected = neutralButton(getString(R.string.quick_selected_shortcuts));
        quickSelected.setOnClickListener(v -> { toast(getString(R.string.quick_profiles_hint)); showScreen(SCREEN_HOME); });
        profileRow.addView(quickSelected, new LinearLayout.LayoutParams(0, dp(56), 1));

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
        logStatsText = text("", 15, false, isLightTheme() ? 0xFF102033 : 0xFFE8EEF8);
        logStatsText.setTypeface(Typeface.MONOSPACE);
        logStatsText.setPadding(dp(14), dp(12), dp(14), dp(12));
        logStatsText.setBackground(makeSolid(isLightTheme() ? 0xFFFFFFFF : 0xFF08111D, 16, isLightTheme() ? 0xFFCAD7E8 : 0xFF263143, 1));
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

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(10), 0, 0);
        actions.addView(row2, matchWrap());
        Button cache = neutralButton(getString(R.string.clear_cache));
        cache.setOnClickListener(v -> clearLocalCache());
        row2.addView(cache, new LinearLayout.LayoutParams(0, dp(56), 1));

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setPadding(0, dp(10), 0, 0);
        actions.addView(row3, matchWrap());
        Button exportSettings = neutralButton(getString(R.string.export_settings));
        exportSettings.setOnClickListener(v -> exportSettingsToFile());
        row3.addView(exportSettings, new LinearLayout.LayoutParams(0, dp(56), 1));
        row3.addView(gap(10, 1));
        Button importSettings = neutralButton(getString(R.string.import_settings));
        importSettings.setOnClickListener(v -> importSettingsFromFile());
        row3.addView(importSettings, new LinearLayout.LayoutParams(0, dp(56), 1));

        root.addView(space(1, 16));
        groupedLogsLayout = vertical();
        root.addView(groupedLogsLayout, matchWrap());
        renderLogsOnly();
        return root;
    }

    private View buildSettingsScreen() {
        LinearLayout root = screenRoot();

        LinearLayout settingsCard = card(0xFF172435, 0xFF34445C);
        root.addView(settingsCard, matchWrap());
        settingsCard.addView(text(getString(R.string.settings_title), 22, true, 0xFFFFFFFF));
        TextView hint = text(getString(R.string.settings_hint), 15, false, 0xFFD5DEEC);
        hint.setPadding(0, dp(6), 0, dp(12));
        settingsCard.addView(hint);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        settingsCard.addView(row1, matchWrap());
        Button export = primaryButton(getString(R.string.save_all_settings));
        export.setOnClickListener(v -> exportSettingsToFile());
        row1.addView(export, new LinearLayout.LayoutParams(0, dp(60), 1));
        row1.addView(gap(12, 1));
        Button importBtn = neutralButton(getString(R.string.load_all_settings));
        importBtn.setOnClickListener(v -> importSettingsFromFile());
        row1.addView(importBtn, new LinearLayout.LayoutParams(0, dp(60), 1));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(12), 0, 0);
        settingsCard.addView(row2, matchWrap());
        Button clear = dangerButton(getString(R.string.clear_cache));
        clear.setOnClickListener(v -> clearLocalCache());
        row2.addView(clear, new LinearLayout.LayoutParams(0, dp(58), 1));
        row2.addView(gap(12, 1));
        Button cloud = neutralButton(getString(R.string.cloud_beta));
        cloud.setOnClickListener(v -> toast(getString(R.string.cloud_beta_not_ready)));
        row2.addView(cloud, new LinearLayout.LayoutParams(0, dp(58), 1));

        TextView cloudHint = text(getString(R.string.cloud_beta_hint), 13, false, 0xFFB8C4D8);
        cloudHint.setPadding(0, dp(10), 0, 0);
        settingsCard.addView(cloudHint);

        root.addView(space(1, 16));
        root.addView(buildThemeCard(), matchWrap());

        root.addView(space(1, 16));
        root.addView(buildStartupCard(), matchWrap());

        root.addView(space(1, 16));
        root.addView(buildNetworkToolsCard(), matchWrap());

        root.addView(space(1, 16));
        root.addView(buildAboutCard(), matchWrap());

        root.addView(space(1, 16));
        LinearLayout langCard = card(0xFF101A29, 0xFF303D51);
        root.addView(langCard, matchWrap());
        langCard.addView(text(getString(R.string.language), 22, true, 0xFFFFFFFF));
        TextView langHint = text(getString(R.string.language_hint), 14, false, 0xFFB8C4D8);
        langHint.setPadding(0, dp(4), 0, dp(12));
        langCard.addView(langHint);

        String active = getPrefs().getString(PREF_LANGUAGE, "");
        List<LanguageOption> languages = getLanguageOptions();
        LinearLayout row = null;
        for (int i = 0; i < languages.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, i == 0 ? 0 : dp(10), 0, 0);
                langCard.addView(row, matchWrap());
            }
            LanguageOption lang = languages.get(i);
            boolean selected = (active == null || active.isEmpty()) ? lang.code.equals("system") : active.equals(lang.code);
            Button b = modeButton((selected ? "✓  " : "") + lang.label, selected);
            b.setTextSize(13);
            b.setOnClickListener(v -> setLanguage(lang.code));
            row.addView(b, new LinearLayout.LayoutParams(0, dp(56), 1));
            if (i % 2 == 0) row.addView(gap(10, 1));
        }

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

        List<AppItem> display = getAppsSortedForList(selected, allApps);
        for (AppItem item : display) {
            boolean effectiveDns = allApps || selected.contains(item.packageName);
            boolean show = q.isEmpty() || item.label.toLowerCase(Locale.US).contains(q) || item.packageName.toLowerCase(Locale.US).contains(q);
            if (show && appListFilterMode == APP_FILTER_DNS) show = effectiveDns;
            if (show && appListFilterMode == APP_FILTER_NO_DNS) show = !effectiveDns;
            if (!show) continue;
            visible++;
            appListLayout.addView(appRow(item, allApps, selected.contains(item.packageName), logging.contains(item.packageName)));
        }

        if (appCounterText != null) {
            String mode = allApps ? getString(R.string.all_apps) : getString(R.string.selected_apps_count, selectedCount);
            String filter = appListFilterMode == APP_FILTER_DNS ? getString(R.string.filter_dns_apps) : (appListFilterMode == APP_FILTER_NO_DNS ? getString(R.string.filter_no_dns_apps) : getString(R.string.filter_all_apps));
            appCounterText.setText(getString(R.string.installed_apps) + ": " + apps.size() + " / visible: " + visible + " / " + mode + " / " + filter);
        }
        if (visible == 0) {
            TextView empty = text(getString(R.string.no_apps_found), 16, false, 0xFFFFFFFF);
            empty.setPadding(dp(12), dp(18), dp(12), dp(18));
            appListLayout.addView(empty, matchWrap());
        }
    }

    private List<AppItem> getAppsSortedForList(Set<String> selected, boolean allApps) {
        List<AppItem> display = new ArrayList<>(apps);
        Collections.sort(display, (a, b) -> {
            boolean aOn = allApps || selected.contains(a.packageName);
            boolean bOn = allApps || selected.contains(b.packageName);
            if (aOn != bOn) return aOn ? -1 : 1;
            return a.label.toLowerCase(Locale.US).compareTo(b.label.toLowerCase(Locale.US));
        });
        return display;
    }

    private View appRow(AppItem item, boolean allApps, boolean selected, boolean logging) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setFocusable(true);
        row.setClickable(true);
        applyFocus(row, 0xFF172435, 0xFF243A58, 18);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(top, matchWrap());

        ImageView icon = new ImageView(this);
        if (item.icon != null) icon.setImageDrawable(item.icon);
        top.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout labels = vertical();
        labels.setPadding(dp(12), 0, dp(4), 0);
        TextView name = text(item.label, 17, true, 0xFFFFFFFF);
        name.setSingleLine(false);
        TextView pkg = text(item.packageName, 11, false, 0xFF8FA3BD);
        pkg.setSingleLine(true);
        labels.addView(name);
        labels.addView(pkg);
        top.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView state = text(allApps || selected ? "●" : "○", 28, true, allApps || selected ? 0xFF6FF2B2 : 0xFF7B8DA7);
        state.setGravity(Gravity.CENTER);
        top.addView(state, new LinearLayout.LayoutParams(dp(38), dp(46)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(58), dp(10), 0, 0);
        row.addView(actions, matchWrap());

        Button dns = smallStateButton(allApps || selected ? getString(R.string.dns_enabled) : getString(R.string.dns_disabled), allApps || selected);
        dns.setOnClickListener(v -> toggleAppSelected(item.packageName));
        actions.addView(dns, new LinearLayout.LayoutParams(0, dp(50), 1));
        actions.addView(gap(10, 1));

        Button log = smallStateButton(logging ? getString(R.string.log_enabled) : getString(R.string.log_disabled), logging);
        log.setOnClickListener(v -> toggleAppLogging(item.packageName));
        actions.addView(log, new LinearLayout.LayoutParams(0, dp(50), 1));

        row.setOnClickListener(v -> toggleAppSelected(item.packageName));

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
            empty.addView(text(getString(R.string.log_empty), 16, false, isLightTheme() ? 0xFF102033 : 0xFFFFFFFF));
            groupedLogsLayout.addView(empty, matchWrap());
            return;
        }

        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            LinearLayout groupCard = card(0xFF101A29, 0xFF303D51);
            String title = displayGroupName(entry.getKey());
            groupCard.addView(text(title, 20, true, 0xFFFFFFFF));
            TextView body = text(join(entry.getValue()), 13, false, isLightTheme() ? 0xFF102033 : 0xFFE8EEF8);
            body.setTypeface(Typeface.MONOSPACE);
            body.setPadding(dp(12), dp(10), dp(12), dp(10));
            body.setBackground(makeSolid(isLightTheme() ? 0xFFFFFFFF : 0xFF08111D, 14, isLightTheme() ? 0xFFCAD7E8 : 0xFF263143, 1));
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

    private void toggleDnsVpn() {
        if (getPrefs().getBoolean(DnsVpnService.PREF_RUNNING, false)) {
            stopDnsVpn();
        } else {
            startDnsVpn();
        }
    }

    private void clearLocalCache() {
        deleteDirContent(getCacheDir());
        DebugLog.clear(this);
        getPrefs().edit()
                .putLong(DnsVpnService.PREF_PACKETS, 0)
                .putLong(DnsVpnService.PREF_DNS_QUERIES, 0)
                .putLong(DnsVpnService.PREF_DNS_RESPONSES, 0)
                .putLong(DnsVpnService.PREF_DNS_FAILURES, 0)
                .putLong(DnsVpnService.PREF_DNS_DROPPED, 0)
                .putString(DnsVpnService.PREF_LAST_DOMAIN, "-")
                .putString(DnsVpnService.PREF_LAST_ERROR, "")
                .apply();
        refreshStatusViews();
        renderLogsOnly();
        toast(getString(R.string.cache_cleared));
    }

    private void deleteDirContent(java.io.File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        java.io.File[] files = dir.listFiles();
        if (files == null) return;
        for (java.io.File f : files) {
            if (f.isDirectory()) deleteDirContent(f);
            try { f.delete(); } catch (Exception ignored) {}
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) {
            DebugLog.log(this, "SYSTEM", "VPN permission accepted");
            startServiceNow();
        } else if (requestCode == REQ_VPN) {
            DebugLog.log(this, "SYSTEM", "VPN permission denied");
            toast(getString(R.string.vpn_permission_denied));
        } else if (requestCode == REQ_EXPORT_SETTINGS && resultCode == RESULT_OK && data != null && data.getData() != null) {
            writeSettingsExport(data.getData());
        } else if (requestCode == REQ_IMPORT_SETTINGS && resultCode == RESULT_OK && data != null && data.getData() != null) {
            readSettingsImport(data.getData());
        } else if (requestCode == REQ_EXPORT_LOGS && resultCode == RESULT_OK && data != null && data.getData() != null) {
            writeLogsExport(data.getData());
        }
    }

    private LinearLayout buildProfilesCard() {
        LinearLayout card = card(0xFF101A29, 0xFF303D51);
        card.addView(text(getString(R.string.profiles), 21, true, 0xFFFFFFFF));
        TextView hint = text(getString(R.string.profiles_hint), 14, false, 0xFFB8C4D8);
        hint.setPadding(0, dp(4), 0, dp(12));
        card.addView(hint);

        List<Profile> profiles = readProfiles();
        if (!profiles.isEmpty()) {
            LinearLayout row = null;
            for (int i = 0; i < profiles.size(); i++) {
                if (i % 2 == 0) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setPadding(0, i == 0 ? 0 : dp(10), 0, 0);
                    card.addView(row, matchWrap());
                }
                Profile profile = profiles.get(i);
                Button b = primaryButton(profile.name);
                b.setText(profile.name + "\n" + profile.serverName);
                b.setOnClickListener(v -> applyProfileAndConnect(profile));
                row.addView(b, new LinearLayout.LayoutParams(0, dp(72), 1));
                if (i % 2 == 0) row.addView(gap(10, 1));
            }
        }

        Set<String> selected = getSelectedApps();
        if (!selected.isEmpty()) {
            TextView quick = text(getString(R.string.quick_app_profiles), 16, true, 0xFFFFFFFF);
            quick.setPadding(0, dp(14), 0, dp(8));
            card.addView(quick);
            LinearLayout row = null;
            int shown = 0;
            for (String pkg : selected) {
                String label = appLabels.get(pkg);
                if (label == null) continue;
                if (shown >= 8) break;
                if (shown % 2 == 0) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setPadding(0, shown == 0 ? 0 : dp(10), 0, 0);
                    card.addView(row, matchWrap());
                }
                Button b = neutralButton(label);
                b.setText(label + "\n" + getPrefs().getString(DnsVpnService.PREF_SERVER_NAME, "AdGuard DNS"));
                final String packageName = pkg;
                final String appLabel = label;
                b.setOnClickListener(v -> applySingleAppAndConnect(packageName, appLabel));
                row.addView(b, new LinearLayout.LayoutParams(0, dp(72), 1));
                if (shown % 2 == 0) row.addView(gap(10, 1));
                shown++;
            }
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(14), 0, 0);
        card.addView(actions, matchWrap());
        Button save = primaryButton(getString(R.string.save_current_profile));
        save.setOnClickListener(v -> promptSaveProfile());
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(56), 1));
        actions.addView(gap(10, 1));
        Button clear = dangerButton(getString(R.string.clear_profiles));
        clear.setOnClickListener(v -> { getPrefs().edit().remove(PREF_PROFILES_JSON).apply(); toast(getString(R.string.cleared)); showScreen(SCREEN_HOME); });
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(56), 1));
        return card;
    }

    private void promptSaveProfile() {
        final EditText input = editText("");
        input.setText(guessProfileName());
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.save_current_profile))
                .setMessage(getString(R.string.profile_name_hint))
                .setView(input)
                .setPositiveButton(getString(R.string.save), (dialog, which) -> saveCurrentProfile(input.getText().toString().trim()))
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
        input.requestFocus();
        showKeyboard(input);
    }

    private String guessProfileName() {
        if (!isAllAppsMode() && getSelectedApps().size() == 1) {
            String pkg = getSelectedApps().iterator().next();
            String label = appLabels.get(pkg);
            if (label != null && !label.trim().isEmpty()) return label;
        }
        return getPrefs().getString(DnsVpnService.PREF_SERVER_NAME, "AdGuard DNS") + " " + new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
    }

    private void saveCurrentProfile(String name) {
        if (name == null || name.trim().isEmpty()) name = guessProfileName();
        Profile profile = new Profile();
        profile.name = name.trim();
        profile.serverName = getPrefs().getString(DnsVpnService.PREF_SERVER_NAME, "AdGuard DNS");
        profile.dns1 = getPrefs().getString(DnsVpnService.PREF_DNS_1, "94.140.14.14");
        profile.dns2 = getPrefs().getString(DnsVpnService.PREF_DNS_2, "94.140.15.15");
        profile.allApps = isAllAppsMode();
        profile.packages = getSelectedApps();

        List<Profile> profiles = readProfiles();
        List<Profile> next = new ArrayList<>();
        for (Profile existing : profiles) {
            if (!existing.name.equalsIgnoreCase(profile.name)) next.add(existing);
        }
        next.add(0, profile);
        while (next.size() > 12) next.remove(next.size() - 1);
        writeProfiles(next);
        DebugLog.log(this, "SYSTEM", "Profile saved: " + profile.name);
        toast(getString(R.string.profile_saved));
        showScreen(SCREEN_HOME);
    }

    private void applyProfileAndConnect(Profile profile) {
        if (profile == null) return;
        getPrefs().edit()
                .putString(DnsVpnService.PREF_SERVER_NAME, profile.serverName)
                .putString(DnsVpnService.PREF_DNS_1, profile.dns1)
                .putString(DnsVpnService.PREF_DNS_2, profile.dns2)
                .putBoolean(DnsVpnService.PREF_ALL_APPS, profile.allApps)
                .putStringSet(DnsVpnService.PREF_SELECTED_APPS, profile.packages == null ? new LinkedHashSet<String>() : profile.packages)
                .apply();
        DebugLog.log(this, "SYSTEM", "Profile applied: " + profile.name);
        startDnsVpn();
    }

    private void applySingleAppAndConnect(String packageName, String label) {
        Set<String> one = new LinkedHashSet<>();
        one.add(packageName);
        getPrefs().edit()
                .putBoolean(DnsVpnService.PREF_ALL_APPS, false)
                .putStringSet(DnsVpnService.PREF_SELECTED_APPS, one)
                .apply();
        DebugLog.log(this, packageName, "Quick single-app profile started: " + label);
        startDnsVpn();
    }

    private List<Profile> readProfiles() {
        List<Profile> out = new ArrayList<>();
        String json = getPrefs().getString(PREF_PROFILES_JSON, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Profile p = new Profile();
                p.name = o.optString("name", "Profile " + (i + 1));
                p.serverName = o.optString("serverName", "AdGuard DNS");
                p.dns1 = o.optString("dns1", "94.140.14.14");
                p.dns2 = o.optString("dns2", "94.140.15.15");
                p.allApps = o.optBoolean("allApps", false);
                p.packages = new LinkedHashSet<>();
                JSONArray pkgs = o.optJSONArray("packages");
                if (pkgs != null) for (int j = 0; j < pkgs.length(); j++) p.packages.add(pkgs.optString(j));
                out.add(p);
            }
        } catch (Exception e) {
            DebugLog.log(this, "SYSTEM", "Profile read error: " + e.getClass().getSimpleName());
        }
        return out;
    }

    private void writeProfiles(List<Profile> profiles) {
        try {
            JSONArray arr = new JSONArray();
            for (Profile p : profiles) {
                JSONObject o = new JSONObject();
                o.put("name", p.name);
                o.put("serverName", p.serverName);
                o.put("dns1", p.dns1);
                o.put("dns2", p.dns2);
                o.put("allApps", p.allApps);
                JSONArray pkgs = new JSONArray();
                if (p.packages != null) for (String pkg : p.packages) pkgs.put(pkg);
                o.put("packages", pkgs);
                arr.put(o);
            }
            getPrefs().edit().putString(PREF_PROFILES_JSON, arr.toString()).apply();
        } catch (Exception e) {
            DebugLog.log(this, "SYSTEM", "Profile write error: " + e.getClass().getSimpleName());
        }
    }

    private void exportSettingsToFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "adguard-tv-dns-settings.json");
            startActivityForResult(intent, REQ_EXPORT_SETTINGS);
        } catch (Exception e) {
            copySettingsToClipboard();
        }
    }

    private void importSettingsFromFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent, REQ_IMPORT_SETTINGS);
        } catch (Exception e) {
            toast(getString(R.string.import_not_available));
        }
    }

    private void writeSettingsExport(Uri uri) {
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new Exception("No output stream");
            out.write(buildSettingsJson().getBytes(StandardCharsets.UTF_8));
            toast(getString(R.string.settings_exported));
        } catch (Exception e) {
            toast(getString(R.string.export_failed));
            DebugLog.log(this, "SYSTEM", "Export failed: " + e.getClass().getSimpleName());
        }
    }

    private void readSettingsImport(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("No input stream");
            byte[] buffer = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(buffer)) > 0) sb.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
            applySettingsJson(sb.toString());
            toast(getString(R.string.settings_imported));
            showScreen(SCREEN_HOME);
        } catch (Exception e) {
            toast(getString(R.string.import_failed));
            DebugLog.log(this, "SYSTEM", "Import failed: " + e.getClass().getSimpleName());
        }
    }

    private void copySettingsToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("adguard-tv-dns-settings.json", buildSettingsJson()));
            toast(getString(R.string.settings_copied));
        }
    }

    private String buildSettingsJson() {
        try {
            SharedPreferences p = getPrefs();
            JSONObject o = new JSONObject();
            o.put("format", "adguard-tv-dns-pro");
            o.put("version", 37);
            o.put("serverName", p.getString(DnsVpnService.PREF_SERVER_NAME, "AdGuard DNS"));
            o.put("dns1", p.getString(DnsVpnService.PREF_DNS_1, "94.140.14.14"));
            o.put("dns2", p.getString(DnsVpnService.PREF_DNS_2, "94.140.15.15"));
            o.put("allApps", p.getBoolean(DnsVpnService.PREF_ALL_APPS, true));
            o.put("batterySaver", p.getBoolean(DnsVpnService.PREF_BATTERY_SAVER, true));
            o.put("language", p.getString(PREF_LANGUAGE, "system"));
            o.put("theme", p.getString(PREF_THEME, THEME_SYSTEM));
            o.put("ipv6Dns", p.getBoolean(DnsVpnService.PREF_IPV6_DNS, false));
            o.put("tcpFallback", p.getBoolean(DnsVpnService.PREF_TCP_FALLBACK, true));
            o.put("autostart", p.getBoolean(DnsVpnService.PREF_AUTOSTART, false));
            o.put("autostartProfile", p.getString(DnsVpnService.PREF_AUTOSTART_PROFILE, BootReceiver.PROFILE_CURRENT));
            JSONArray selected = new JSONArray();
            for (String pkg : getSelectedApps()) selected.put(pkg);
            o.put("selectedApps", selected);
            JSONArray logging = new JSONArray();
            for (String pkg : getLoggingApps()) logging.put(pkg);
            o.put("loggingApps", logging);
            o.put("profiles", new JSONArray(p.getString(PREF_PROFILES_JSON, "[]")));
            return o.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void applySettingsJson(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        Set<String> selected = new LinkedHashSet<>();
        JSONArray selectedArr = o.optJSONArray("selectedApps");
        if (selectedArr != null) for (int i = 0; i < selectedArr.length(); i++) selected.add(selectedArr.optString(i));
        Set<String> logging = new LinkedHashSet<>();
        JSONArray loggingArr = o.optJSONArray("loggingApps");
        if (loggingArr != null) for (int i = 0; i < loggingArr.length(); i++) logging.add(loggingArr.optString(i));
        JSONArray profiles = o.optJSONArray("profiles");
        getPrefs().edit()
                .putString(DnsVpnService.PREF_SERVER_NAME, o.optString("serverName", "AdGuard DNS"))
                .putString(DnsVpnService.PREF_DNS_1, o.optString("dns1", "94.140.14.14"))
                .putString(DnsVpnService.PREF_DNS_2, o.optString("dns2", "94.140.15.15"))
                .putBoolean(DnsVpnService.PREF_ALL_APPS, o.optBoolean("allApps", true))
                .putBoolean(DnsVpnService.PREF_BATTERY_SAVER, o.optBoolean("batterySaver", true))
                .putString(PREF_LANGUAGE, o.optString("language", "system"))
                .putString(PREF_THEME, o.optString("theme", THEME_SYSTEM))
                .putBoolean(DnsVpnService.PREF_IPV6_DNS, o.optBoolean("ipv6Dns", false))
                .putBoolean(DnsVpnService.PREF_TCP_FALLBACK, o.optBoolean("tcpFallback", true))
                .putBoolean(DnsVpnService.PREF_AUTOSTART, o.optBoolean("autostart", false))
                .putString(DnsVpnService.PREF_AUTOSTART_PROFILE, o.optString("autostartProfile", BootReceiver.PROFILE_CURRENT))
                .putStringSet(DnsVpnService.PREF_SELECTED_APPS, selected)
                .putStringSet(DnsVpnService.PREF_LOGGING_APPS, logging)
                .putString(PREF_PROFILES_JSON, profiles == null ? "[]" : profiles.toString())
                .apply();
        DebugLog.log(this, "SYSTEM", "Settings imported from file");
    }

    private void refreshStatusViews() {
        SharedPreferences p = getPrefs();
        boolean running = p.getBoolean(DnsVpnService.PREF_RUNNING, false);
        if (statusBadge != null) {
            statusBadge.setText(running ? getString(R.string.active) : getString(R.string.inactive));
            statusBadge.setBackground(makeSolid(running ? 0xFF1D8B5A : 0xFF7D3240, 22, running ? 0xFF8BFFD0 : 0xFFFF9DA8, 2));
        }
        if (starPowerButton != null) {
            boolean focused = starPowerButton.hasFocus();
            starPowerButton.setText(running ? "★" : "✦");
            starPowerButton.setTextColor(running ? 0xFFFFD66B : 0xFFD9DEE7);
            int bg = focused ? (running ? 0xFF4A3A18 : 0xFF2E7DD1) : (running ? 0xFF3A3014 : 0xFF263548);
            int border = focused ? 0xFFBDE3FF : (running ? 0xFFFFD66B : 0xFF6EADEB);
            starPowerButton.setBackground(makeSolid(bg, 160, border, focused ? 5 : (running ? 4 : 2)));
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
                    .putBoolean(DnsVpnService.PREF_TCP_FALLBACK, true)
                    .putBoolean(DnsVpnService.PREF_IPV6_DNS, false)
                    .putBoolean(DnsVpnService.PREF_AUTOSTART, false)
                    .putString(DnsVpnService.PREF_AUTOSTART_PROFILE, BootReceiver.PROFILE_CURRENT)
                    .putString(PREF_THEME, THEME_SYSTEM)
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
        updateTopLeftButton();
    }

    private void requestHomeDefaultFocus() {
        handler.postDelayed(() -> {
            if (currentScreen == SCREEN_HOME && starPowerButton != null) {
                starPowerButton.requestFocus();
                refreshStatusViews();
            }
        }, 180);
    }

    private void updateTopLeftButton() {
        if (topLeftButton == null) return;
        if (currentScreen == SCREEN_HOME) {
            topLeftButton.setText("⚙");
            topLeftButton.setTextSize(28);
            topLeftButton.setContentDescription(getString(R.string.settings));
        } else {
            topLeftButton.setText("‹");
            topLeftButton.setTextSize(34);
            topLeftButton.setContentDescription(getString(R.string.home));
        }
    }

    private List<LanguageOption> getLanguageOptions() {
        List<LanguageOption> out = new ArrayList<>();
        out.add(new LanguageOption("system", getString(R.string.language_system)));
        out.add(new LanguageOption("en", "English"));
        out.add(new LanguageOption("pl", "Polski"));
        out.add(new LanguageOption("de", "Deutsch"));
        out.add(new LanguageOption("fr", "Français"));
        out.add(new LanguageOption("nl", "Nederlands"));
        out.add(new LanguageOption("es", "Español beta"));
        out.add(new LanguageOption("it", "Italiano beta"));
        out.add(new LanguageOption("pt", "Português beta"));
        out.add(new LanguageOption("cs", "Čeština beta"));
        out.add(new LanguageOption("sk", "Slovenčina beta"));
        out.add(new LanguageOption("hu", "Magyar beta"));
        out.add(new LanguageOption("ro", "Română beta"));
        out.add(new LanguageOption("bg", "Български beta"));
        out.add(new LanguageOption("el", "Ελληνικά beta"));
        out.add(new LanguageOption("hr", "Hrvatski beta"));
        out.add(new LanguageOption("sl", "Slovenščina beta"));
        out.add(new LanguageOption("sr", "Srpski beta"));
        out.add(new LanguageOption("sq", "Shqip beta"));
        out.add(new LanguageOption("da", "Dansk beta"));
        out.add(new LanguageOption("sv", "Svenska beta"));
        out.add(new LanguageOption("fi", "Suomi beta"));
        out.add(new LanguageOption("et", "Eesti beta"));
        out.add(new LanguageOption("lv", "Latviešu beta"));
        out.add(new LanguageOption("lt", "Lietuvių beta"));
        out.add(new LanguageOption("ga", "Gaeilge beta"));
        out.add(new LanguageOption("mt", "Malti beta"));
        out.add(new LanguageOption("is", "Íslenska beta"));
        out.add(new LanguageOption("no", "Norsk beta"));
        out.add(new LanguageOption("uk", "Українська beta"));
        out.add(new LanguageOption("ru", "Русский beta"));
        out.add(new LanguageOption("tr", "Türkçe beta"));
        return out;
    }

    private void setLanguage(String code) {
        if (code == null || code.trim().isEmpty() || "system".equals(code)) {
            getPrefs().edit().remove(PREF_LANGUAGE).apply();
        } else {
            getPrefs().edit().putString(PREF_LANGUAGE, code).apply();
        }
        toast(getString(R.string.language_changed));
        recreate();
    }


    private View buildThemeCard() {
        LinearLayout card = card(0xFF101A29, 0xFF303D51);
        card.addView(text(getString(R.string.theme), 22, true, 0xFFFFFFFF));
        TextView hint = text(getString(R.string.theme_hint), 14, false, 0xFFB8C4D8);
        hint.setPadding(0, dp(4), 0, dp(12));
        card.addView(hint);
        String theme = getPrefs().getString(PREF_THEME, THEME_SYSTEM);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row, matchWrap());
        Button system = modeButton(getString(R.string.theme_system), THEME_SYSTEM.equals(theme));
        system.setOnClickListener(v -> setThemeMode(THEME_SYSTEM));
        row.addView(system, new LinearLayout.LayoutParams(0, dp(58), 1));
        row.addView(gap(10, 1));
        Button dark = modeButton(getString(R.string.theme_dark), THEME_DARK.equals(theme));
        dark.setOnClickListener(v -> setThemeMode(THEME_DARK));
        row.addView(dark, new LinearLayout.LayoutParams(0, dp(58), 1));
        row.addView(gap(10, 1));
        Button light = modeButton(getString(R.string.theme_light), THEME_LIGHT.equals(theme));
        light.setOnClickListener(v -> setThemeMode(THEME_LIGHT));
        row.addView(light, new LinearLayout.LayoutParams(0, dp(58), 1));
        return card;
    }

    private View buildStartupCard() {
        LinearLayout card = card(0xFF101A29, 0xFF303D51);
        card.addView(text(getString(R.string.startup), 22, true, 0xFFFFFFFF));
        TextView hint = text(getString(R.string.startup_hint), 14, false, 0xFFB8C4D8);
        hint.setPadding(0, dp(4), 0, dp(12));
        card.addView(hint);
        boolean enabled = getPrefs().getBoolean(DnsVpnService.PREF_AUTOSTART, false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row, matchWrap());
        Button autostart = modeButton(enabled ? getString(R.string.autostart_on) : getString(R.string.autostart_off), enabled);
        autostart.setOnClickListener(v -> { getPrefs().edit().putBoolean(DnsVpnService.PREF_AUTOSTART, !enabled).apply(); showScreen(SCREEN_SETTINGS); });
        row.addView(autostart, new LinearLayout.LayoutParams(0, dp(58), 1));
        row.addView(gap(10, 1));
        Button current = modeButton(getString(R.string.autostart_current), BootReceiver.PROFILE_CURRENT.equals(getPrefs().getString(DnsVpnService.PREF_AUTOSTART_PROFILE, BootReceiver.PROFILE_CURRENT)));
        current.setOnClickListener(v -> { getPrefs().edit().putString(DnsVpnService.PREF_AUTOSTART_PROFILE, BootReceiver.PROFILE_CURRENT).apply(); showScreen(SCREEN_SETTINGS); });
        row.addView(current, new LinearLayout.LayoutParams(0, dp(58), 1));

        List<Profile> profiles = readProfiles();
        if (!profiles.isEmpty()) {
            TextView pHint = text(getString(R.string.autostart_profile_hint), 14, false, 0xFFB8C4D8);
            pHint.setPadding(0, dp(12), 0, dp(8));
            card.addView(pHint);
            LinearLayout pRow = null;
            String active = getPrefs().getString(DnsVpnService.PREF_AUTOSTART_PROFILE, BootReceiver.PROFILE_CURRENT);
            for (int i = 0; i < profiles.size(); i++) {
                if (i >= 6) break;
                if (i % 2 == 0) { pRow = new LinearLayout(this); pRow.setOrientation(LinearLayout.HORIZONTAL); pRow.setPadding(0, i == 0 ? 0 : dp(8), 0, 0); card.addView(pRow, matchWrap()); }
                Profile profile = profiles.get(i);
                Button b = modeButton((profile.name.equals(active) ? "✓  " : "") + profile.name, profile.name.equals(active));
                b.setTextSize(13);
                b.setOnClickListener(v -> { getPrefs().edit().putString(DnsVpnService.PREF_AUTOSTART_PROFILE, profile.name).apply(); showScreen(SCREEN_SETTINGS); });
                pRow.addView(b, new LinearLayout.LayoutParams(0, dp(54), 1));
                if (i % 2 == 0) pRow.addView(gap(10, 1));
            }
        }
        return card;
    }

    private View buildNetworkToolsCard() {
        LinearLayout card = card(0xFF101A29, 0xFF303D51);
        card.addView(text(getString(R.string.network_tools), 22, true, 0xFFFFFFFF));
        TextView hint = text(getString(R.string.network_tools_hint), 14, false, 0xFFB8C4D8);
        hint.setPadding(0, dp(4), 0, dp(12));
        card.addView(hint);
        boolean ipv6 = getPrefs().getBoolean(DnsVpnService.PREF_IPV6_DNS, false);
        boolean tcp = getPrefs().getBoolean(DnsVpnService.PREF_TCP_FALLBACK, true);
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row1, matchWrap());
        Button ipv6Btn = modeButton(ipv6 ? getString(R.string.ipv6_dns_on) : getString(R.string.ipv6_dns_off), ipv6);
        ipv6Btn.setOnClickListener(v -> { getPrefs().edit().putBoolean(DnsVpnService.PREF_IPV6_DNS, !ipv6).apply(); showScreen(SCREEN_SETTINGS); });
        row1.addView(ipv6Btn, new LinearLayout.LayoutParams(0, dp(58), 1));
        row1.addView(gap(10, 1));
        Button tcpBtn = modeButton(tcp ? getString(R.string.tcp_fallback_on) : getString(R.string.tcp_fallback_off), tcp);
        tcpBtn.setOnClickListener(v -> { getPrefs().edit().putBoolean(DnsVpnService.PREF_TCP_FALLBACK, !tcp).apply(); showScreen(SCREEN_SETTINGS); });
        row1.addView(tcpBtn, new LinearLayout.LayoutParams(0, dp(58), 1));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(10), 0, 0);
        card.addView(row2, matchWrap());
        Button test = primaryButton(getString(R.string.test_dns));
        test.setOnClickListener(v -> runDnsTest());
        row2.addView(test, new LinearLayout.LayoutParams(0, dp(58), 1));
        row2.addView(gap(10, 1));
        Button vpnSettings = neutralButton(getString(R.string.open_vpn_settings));
        vpnSettings.setOnClickListener(v -> openVpnSettings());
        row2.addView(vpnSettings, new LinearLayout.LayoutParams(0, dp(58), 1));
        return card;
    }

    private View buildAboutCard() {
        LinearLayout card = card(0xFF172435, 0xFF34445C);
        card.addView(text(getString(R.string.about_app), 22, true, 0xFFFFFFFF));
        TextView version = text(getString(R.string.version_label) + ": " + getVersionNameSafe() + " (" + getVersionCodeSafe() + ")", 15, false, 0xFFD5DEEC);
        version.setPadding(0, dp(4), 0, dp(12));
        card.addView(version);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row, matchWrap());
        Button update = primaryButton(getString(R.string.check_update));
        update.setOnClickListener(v -> AppUpdateManager.checkForUpdate(this));
        row.addView(update, new LinearLayout.LayoutParams(0, dp(58), 1));
        row.addView(gap(10, 1));
        Button logs = neutralButton(getString(R.string.export_diagnostic_logs));
        logs.setOnClickListener(v -> exportLogsToFile());
        row.addView(logs, new LinearLayout.LayoutParams(0, dp(58), 1));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(10), 0, 0);
        card.addView(row2, matchWrap());
        Button testSource = neutralButton(getString(R.string.test_update_source));
        testSource.setOnClickListener(v -> AppUpdateManager.testReleaseSource(this));
        row2.addView(testSource, new LinearLayout.LayoutParams(0, dp(58), 1));

        TextView always = text(getString(R.string.always_on_hint), 13, false, 0xFFB8C4D8);
        always.setPadding(0, dp(10), 0, 0);
        card.addView(always);
        return card;
    }

    private void setThemeMode(String theme) {
        getPrefs().edit().putString(PREF_THEME, theme == null ? THEME_SYSTEM : theme).apply();
        recreate();
    }

    private void runDnsTest() {
        toast(getString(R.string.testing_dns));
        SharedPreferences p = getPrefs();
        String dns1 = p.getString(DnsVpnService.PREF_DNS_1, "94.140.14.14");
        String dns2 = p.getString(DnsVpnService.PREF_DNS_2, "94.140.15.15");
        boolean tcp = p.getBoolean(DnsVpnService.PREF_TCP_FALLBACK, true);
        new Thread(() -> {
            DnsDiagnostics.Result result = DnsDiagnostics.test(dns1, dns2, tcp);
            DebugLog.log(this, "SYSTEM", "DNS test: " + result.detail + " / " + result.server + " / " + result.millis + " ms");
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle(result.ok ? getString(R.string.dns_test_ok) : getString(R.string.dns_test_failed))
                    .setMessage(result.detail + "\n" + getString(R.string.server) + ": " + result.server + "\n" + result.millis + " ms")
                    .setPositiveButton("OK", null)
                    .show());
        }, "DnsManualTest").start();
    }

    private void openVpnSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_VPN_SETTINGS));
        } catch (Exception e) {
            toast(getString(R.string.open_settings_failed));
        }
    }

    private void exportLogsToFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, "adguard-tv-dns-diagnostic-logs.txt");
            startActivityForResult(intent, REQ_EXPORT_LOGS);
        } catch (Exception e) {
            copyLogToClipboard();
        }
    }

    private void writeLogsExport(Uri uri) {
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new Exception("No output stream");
            String content = "AdGuard TV DNS Pro " + getVersionNameSafe() + "\n" + buildStatsText() + "\n\n" + DebugLog.read(this);
            out.write(content.getBytes(StandardCharsets.UTF_8));
            toast(getString(R.string.logs_exported));
        } catch (Exception e) {
            toast(getString(R.string.export_failed));
            DebugLog.log(this, "SYSTEM", "Log export failed: " + e.getClass().getSimpleName());
        }
    }

    private String getVersionNameSafe() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return getString(R.string.build_flavor); }
    }

    private int getVersionCodeSafe() {
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= 28) return (int) pi.getLongVersionCode();
            return pi.versionCode;
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean isLightTheme() {
        String theme = getPrefs().getString(PREF_THEME, THEME_SYSTEM);
        if (THEME_LIGHT.equals(theme)) return true;
        if (THEME_DARK.equals(theme)) return false;
        int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night != Configuration.UI_MODE_NIGHT_YES;
    }

    private int bgTop() { return isLightTheme() ? 0xFFEAF2FD : 0xFF121D2B; }
    private int bgBottom() { return isLightTheme() ? 0xFFDDE9F8 : 0xFF0A111D; }
    private int topBarColor() { return isLightTheme() ? 0xFFF7FAFF : 0xFF1D2A3A; }
    private int bottomBarColor() { return isLightTheme() ? 0xFFF2F7FF : 0xFF111B28; }
    private int borderColor() { return isLightTheme() ? 0xFFD1DCEC : 0xFF263143; }
    private int adaptTextColor(int color) {
        if (!isLightTheme()) return color;
        if (color == 0xFFFFFFFF || color == 0xFFE8EEF8 || color == 0xFFD5DEEC) return 0xFF102033;
        if (color == 0xFFB8C4D8 || color == 0xFF8FA3BD || color == 0xFF7D8EA8) return 0xFF526271;
        return color;
    }

    private boolean validateDns(String dns) {
        if (dns == null) return false;
        String value = dns.trim();
        if (value.isEmpty()) return false;
        if (value.contains(":")) {
            try {
                return java.net.InetAddress.getByName(value).getAddress().length == 16;
            } catch (Exception e) {
                return false;
            }
        }
        if (!value.matches("[0-9.]+")) return false;
        String[] parts = value.split("\\.");
        if (parts.length != 4) return false;
        try {
            for (String part : parts) {
                if (part.length() == 0) return false;
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
        int c = isLightTheme() ? 0xFFF7FAFF : color;
        int b = isLightTheme() ? 0xFFD6E2F2 : border;
        card.setBackground(makeSolid(c, 22, b, 1));
        return card;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(value == null ? "" : value);
        t.setTextSize(uiSp(sp));
        t.setTextColor(adaptTextColor(color));
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
        e.setTextColor(isLightTheme() ? 0xFF102033 : Color.WHITE);
        e.setHintTextColor(isLightTheme() ? 0xFF65758A : 0xFF7D8EA8);
        e.setTextSize(uiSp(15));
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        if (hintOrValue != null && validateDns(hintOrValue)) {
            e.setText(hintOrValue);
        } else {
            e.setHint(hintOrValue);
        }
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setSelectAllOnFocus(false);
        e.setFocusable(true);
        e.setFocusableInTouchMode(true);
        e.setBackground(makeSolid(isLightTheme() ? 0xFFFFFFFF : 0xFF08111D, 14, isLightTheme() ? 0xFF9AB6D8 : 0xFF476D9B, 2));
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
        b.setTextSize(uiSp(sp));
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
        int normal = selected ? 0xFF2E7DD1 : 0xFF1D2A3A;
        int focus = selected ? 0xFF49A4FF : 0xFF36567E;
        applyFocus(b, normal, focus, 18);
        if (b.hasFocus()) {
            b.setBackground(makeSolid(focus, 18, 0xFFBDE3FF, 4));
        }
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

    private int uiSp(int sp) {
        int out = sp >= 18 ? sp - 1 : sp;
        int sw = getResources().getConfiguration().screenWidthDp;
        if (sw > 0 && sw < 600) out -= 1;
        return Math.max(11, out);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void showKeyboard(EditText editText) {
        editText.postDelayed(() -> {
            editText.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(editText, InputMethodManager.SHOW_FORCED);
                editText.postDelayed(() -> imm.showSoftInput(editText, InputMethodManager.SHOW_FORCED), 250);
            }
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

    private static class Profile {
        String name;
        String serverName;
        String dns1;
        String dns2;
        boolean allApps;
        Set<String> packages = new LinkedHashSet<>();
    }

    private static class LanguageOption {
        final String code;
        final String label;
        LanguageOption(String code, String label) { this.code = code; this.label = label; }
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
