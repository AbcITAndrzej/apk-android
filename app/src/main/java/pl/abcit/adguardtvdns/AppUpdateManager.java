package pl.abcit.adguardtvdns;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AppUpdateManager {
    private static final String REPO = "AbcITAndrzej/apk-android";
    private static final String LATEST_RELEASE_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String RELEASES_PAGE_URL = "https://github.com/" + REPO + "/releases";
    static final String PREF_STARTUP_UPDATE_DISABLED = "startup_update_disabled";
    static final String PREF_STARTUP_UPDATE_SNOOZE_UNTIL = "startup_update_snooze_until";
    private static final long FOURTEEN_DAYS_MS = 14L * 24L * 60L * 60L * 1000L;

    private AppUpdateManager() {}

    static void checkForUpdateOnStartup(Activity activity) {
        if (activity == null) return;
        SharedPreferences prefs = activity.getSharedPreferences(DnsVpnService.PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_STARTUP_UPDATE_DISABLED, false)) return;
        long snoozeUntil = prefs.getLong(PREF_STARTUP_UPDATE_SNOOZE_UNTIL, 0L);
        if (snoozeUntil > System.currentTimeMillis()) return;
        showStartupUpdateCheckDialog(activity, prefs);
    }

    private static void showStartupUpdateCheckDialog(Activity activity, SharedPreferences prefs) {
        int pad = dp(activity, 18);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(activity, 8), pad, 0);

        TextView message = new TextView(activity);
        message.setText(activity.getString(R.string.startup_update_checking_message));
        message.setTextSize(16);
        message.setGravity(Gravity.START);
        box.addView(message, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        CheckBox snooze = new CheckBox(activity);
        snooze.setText(activity.getString(R.string.startup_update_snooze_14_days));
        snooze.setPadding(0, dp(activity, 12), 0, 0);
        box.addView(snooze, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        CheckBox disable = new CheckBox(activity);
        disable.setText(activity.getString(R.string.startup_update_disable));
        disable.setPadding(0, dp(activity, 4), 0, 0);
        box.addView(disable, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        snooze.setOnCheckedChangeListener((buttonView, checked) -> {
            SharedPreferences.Editor editor = prefs.edit();
            if (checked) editor.putLong(PREF_STARTUP_UPDATE_SNOOZE_UNTIL, System.currentTimeMillis() + FOURTEEN_DAYS_MS);
            else editor.remove(PREF_STARTUP_UPDATE_SNOOZE_UNTIL);
            editor.apply();
        });
        disable.setOnCheckedChangeListener((buttonView, checked) -> prefs.edit().putBoolean(PREF_STARTUP_UPDATE_DISABLED, checked).apply());

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.startup_update_title))
                .setView(box)
                .setNegativeButton(activity.getString(R.string.cancel), null)
                .show();

        new Thread(() -> {
            try {
                ReleaseInfo info = fetchLatestRelease();
                VersionInfo current = getCurrentVersion(activity);
                boolean newer = isNewer(current, info);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    if (newer) {
                        try { if (dialog.isShowing()) dialog.dismiss(); } catch (Exception ignored) {}
                        showUpdateDialog(activity, info, current.displayName());
                    } else if (dialog.isShowing()) {
                        message.setText(activity.getString(R.string.startup_update_latest_message, current.displayName()));
                        try { dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText("OK"); } catch (Exception ignored) {}
                    }
                });
            } catch (Exception e) {
                DebugLog.log(activity, "SYSTEM", "Startup update check failed: " + e.getClass().getSimpleName() + ": " + safeMsg(e));
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    if (dialog.isShowing()) {
                        message.setText(activity.getString(R.string.startup_update_failed_message, safeMsg(e)));
                        try { dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText("OK"); } catch (Exception ignored) {}
                    }
                });
            }
        }, "StartupUpdateCheck").start();
    }

    static void checkForUpdate(Activity activity) {
        if (activity == null) return;
        Toast.makeText(activity, activity.getString(R.string.checking_update), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                ReleaseInfo info = fetchLatestRelease();
                VersionInfo current = getCurrentVersion(activity);
                boolean newer = isNewer(current, info);
                activity.runOnUiThread(() -> {
                    if (!newer) {
                        showNoUpdateDialog(activity, info, current);
                    } else {
                        showUpdateDialog(activity, info, current.displayName());
                    }
                });
            } catch (Exception e) {
                DebugLog.log(activity, "SYSTEM", "Update check failed: " + e.getClass().getSimpleName() + ": " + safeMsg(e));
                activity.runOnUiThread(() -> showUpdateErrorDialog(activity, e));
            }
        }, "AppUpdateCheck").start();
    }

    static void testReleaseSource(Activity activity) {
        if (activity == null) return;
        Toast.makeText(activity, activity.getString(R.string.testing_update_source), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                ReleaseInfo info = fetchLatestRelease();
                VersionInfo current = getCurrentVersion(activity);
                String msg = activity.getString(R.string.update_source_ok_message,
                        REPO,
                        current.displayName(),
                        info.displayName(),
                        String.valueOf(info.versionCode),
                        info.apkName == null ? "-" : info.apkName,
                        info.downloadUrl == null ? "-" : info.downloadUrl);
                activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                        .setTitle(activity.getString(R.string.update_source_ok))
                        .setMessage(msg)
                        .setPositiveButton(activity.getString(R.string.download_update_test), (d, w) -> downloadAndInstall(activity, info))
                        .setNegativeButton("OK", null)
                        .setNeutralButton(activity.getString(R.string.open_releases_page), (d, w) -> openReleasesPage(activity))
                        .show());
            } catch (Exception e) {
                DebugLog.log(activity, "SYSTEM", "Update source test failed: " + e.getClass().getSimpleName() + ": " + safeMsg(e));
                activity.runOnUiThread(() -> showUpdateErrorDialog(activity, e));
            }
        }, "AppUpdateSourceTest").start();
    }

    private static void showUpdateDialog(Activity activity, ReleaseInfo info, String current) {
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.update_available))
                .setMessage(activity.getString(R.string.update_available_message, current, info.displayName()))
                .setPositiveButton(activity.getString(R.string.download_update), (d, w) -> downloadAndInstall(activity, info))
                .setNegativeButton(activity.getString(R.string.cancel), null)
                .setNeutralButton(activity.getString(R.string.open_releases_page), (d, w) -> openReleasesPage(activity))
                .show();
    }

    private static void showNoUpdateDialog(Activity activity, ReleaseInfo info, VersionInfo current) {
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.no_update_available))
                .setMessage(activity.getString(R.string.no_update_details,
                        current.displayName(),
                        info.displayName(),
                        String.valueOf(info.versionCode),
                        info.apkName == null ? "-" : info.apkName))
                .setPositiveButton("OK", null)
                .setNeutralButton(activity.getString(R.string.open_releases_page), (d, w) -> openReleasesPage(activity))
                .show();
    }

    private static void showUpdateErrorDialog(Activity activity, Exception e) {
        String msg = activity.getString(R.string.update_check_failed_details, REPO, safeMsg(e));
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.update_check_failed_title))
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .setNeutralButton(activity.getString(R.string.open_releases_page), (d, w) -> openReleasesPage(activity))
                .show();
    }

    private static ReleaseInfo fetchLatestRelease() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(LATEST_RELEASE_URL).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "AdGuard-TV-DNS-Pro-Updater");
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setRequestProperty("Pragma", "no-cache");
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(stream);
        if (code == 404) throw new Exception("HTTP 404: no public GitHub Release found. Create a tag like v3.5.2 and wait for Actions to publish the release.");
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + trim(body, 260));

        JSONObject json = new JSONObject(body);
        ReleaseInfo info = new ReleaseInfo();
        info.tagName = json.optString("tag_name", "");
        info.name = json.optString("name", "");
        info.body = json.optString("body", "");
        info.versionCode = parseVersionCode(info.body);
        if (info.versionCode <= 0) info.versionCode = parseVersionCode(info.tagName + "\n" + info.name);
        info.versionName = parseVersionName(info.body);
        if (info.versionName == null || info.versionName.length() == 0) info.versionName = parseVersionName(info.tagName + "\n" + info.name);

        JSONArray assets = json.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String assetName = asset.optString("name", "");
                String url = asset.optString("browser_download_url", "");
                String low = assetName.toLowerCase();
                if (low.endsWith(".apk") && !url.isEmpty()) {
                    info.apkName = assetName;
                    info.downloadUrl = url;
                    break;
                }
            }
        }
        if (info.downloadUrl == null || info.downloadUrl.length() == 0) {
            throw new Exception("Latest release exists, but it has no APK asset. Check Actions > Publish GitHub Release on tag.");
        }
        return info;
    }

    private static int parseVersionCode(String text) {
        if (text == null) return -1;
        Matcher m = Pattern.compile("versionCode\\s*[:=]\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        }
        return -1;
    }

    private static String parseVersionName(String text) {
        if (text == null) return "";
        Matcher m = Pattern.compile("versionName\\s*[:=]\\s*['\"]?([^\\s'\"]+)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) return m.group(1).trim();
        Matcher tag = Pattern.compile("v?(\\d+(?:\\.\\d+){1,3}(?:[-_][A-Za-z0-9._-]+)?)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (tag.find()) return tag.group(1).trim();
        return "";
    }

    private static void downloadAndInstall(Activity activity, ReleaseInfo info) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity)
                        .setTitle(activity.getString(R.string.install_permission_needed))
                        .setMessage(activity.getString(R.string.install_permission_message))
                        .setPositiveButton(activity.getString(R.string.open_settings), (d, w) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()));
                            activity.startActivity(intent);
                        })
                        .setNegativeButton(activity.getString(R.string.cancel), null)
                        .show();
                return;
            }

            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) throw new Exception("DownloadManager missing");
            String filename = safeFileName(info.apkName == null || info.apkName.isEmpty() ? "adguard-tv-dns-update.apk" : info.apkName);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(info.downloadUrl));
            request.setTitle("AdGuard TV DNS Pro " + info.displayName());
            request.setDescription(activity.getString(R.string.downloading_update));
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, filename);
            long id = dm.enqueue(request);
            Toast.makeText(activity, activity.getString(R.string.downloading_update), Toast.LENGTH_LONG).show();

            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    long completed = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (completed != id) return;
                    try {
                        Uri apkUri = dm.getUriForDownloadedFile(id);
                        if (apkUri == null) throw new Exception("No APK URI");
                        Intent install = new Intent(Intent.ACTION_VIEW);
                        install.setDataAndType(apkUri, "application/vnd.android.package-archive");
                        install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        context.startActivity(install);
                        DebugLog.log(context, "SYSTEM", "Update installer opened: " + info.displayName());
                    } catch (Exception e) {
                        DebugLog.log(context, "SYSTEM", "Open installer failed: " + e.getClass().getSimpleName());
                        Toast.makeText(context, context.getString(R.string.update_downloaded_open_failed), Toast.LENGTH_LONG).show();
                    } finally {
                        try { context.unregisterReceiver(this); } catch (Exception ignored) {}
                    }
                }
            };
            if (Build.VERSION.SDK_INT >= 33) {
                activity.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED);
            } else {
                activity.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
        } catch (Exception e) {
            DebugLog.log(activity, "SYSTEM", "Update download failed: " + e.getClass().getSimpleName() + ": " + safeMsg(e));
            Toast.makeText(activity, activity.getString(R.string.update_download_failed), Toast.LENGTH_LONG).show();
        }
    }

    private static VersionInfo getCurrentVersion(Context context) {
        VersionInfo out = new VersionInfo();
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            out.name = pi.versionName == null ? "unknown" : pi.versionName;
            if (Build.VERSION.SDK_INT >= 28) out.code = (int) pi.getLongVersionCode(); else out.code = pi.versionCode;
        } catch (Exception e) {
            out.name = "unknown";
            out.code = -1;
        }
        return out;
    }

    private static boolean isNewer(VersionInfo current, ReleaseInfo latest) {
        // Najpewniejsze jest porównanie versionCode. VersionName może mieć dopiski typu "test"/"debug".
        if (latest.versionCode > 0 && current.code > 0) return latest.versionCode > current.code;

        int[] currentNumbers = extractVersionNumbers(current.name);
        int[] latestNumbers = extractVersionNumbers(firstNonEmpty(latest.versionName, latest.tagName, latest.name));
        if (currentNumbers != null && latestNumbers != null) return compareVersions(latestNumbers, currentNumbers) > 0;

        return isDifferentVersion(current.name, latest.tagName, latest.name);
    }

    private static String firstNonEmpty(String a, String b, String c) {
        if (a != null && a.trim().length() > 0) return a;
        if (b != null && b.trim().length() > 0) return b;
        if (c != null && c.trim().length() > 0) return c;
        return "";
    }

    private static int[] extractVersionNumbers(String value) {
        if (value == null) return null;
        Matcher m = Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:\\.(\\d+))?").matcher(value);
        if (!m.find()) return null;
        int[] out = new int[]{0, 0, 0, 0};
        for (int i = 1; i <= 4; i++) {
            String part = m.group(i);
            if (part == null || part.length() == 0) continue;
            try { out[i - 1] = Integer.parseInt(part); } catch (Exception ignored) {}
        }
        return out;
    }

    private static int compareVersions(int[] a, int[] b) {
        for (int i = 0; i < 4; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) return av > bv ? 1 : -1;
        }
        return 0;
    }

    private static boolean isDifferentVersion(String current, String tag, String name) {
        String normalizedCurrent = normalize(current);
        String normalizedTag = normalize(tag);
        String normalizedName = normalize(name);
        if (normalizedTag.length() > 0 && normalizedCurrent.contains(normalizedTag)) return false;
        if (normalizedName.length() > 0 && normalizedCurrent.contains(normalizedName)) return false;
        return normalizedTag.length() > 0 || normalizedName.length() > 0;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String v = value.toLowerCase().trim();
        if (v.startsWith("v")) v = v.substring(1);
        return v.replace("release", "").replace("updater", "").replace("debug", "").replace("test", "").replace("_", "-");
    }

    private static void openReleasesPage(Activity activity) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE_URL)));
        } catch (Exception e) {
            Toast.makeText(activity, activity.getString(R.string.open_settings_failed), Toast.LENGTH_LONG).show();
        }
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        return sb.toString();
    }

    private static String safeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String safeMsg(Exception e) {
        if (e == null) return "unknown";
        String msg = e.getMessage();
        return msg == null || msg.trim().isEmpty() ? e.getClass().getSimpleName() : msg;
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private static final class VersionInfo {
        String name;
        int code;
        String displayName() { return name + " (" + code + ")"; }
    }

    private static final class ReleaseInfo {
        String tagName;
        String name;
        String body;
        String versionName;
        String apkName;
        String downloadUrl;
        int versionCode = -1;
        String displayName() {
            if (versionName != null && !versionName.isEmpty()) {
                if (tagName != null && !tagName.isEmpty() && !versionName.equals(tagName)) return versionName + " (" + tagName + ")";
                return versionName;
            }
            if (tagName != null && !tagName.isEmpty()) return tagName;
            if (name != null && !name.isEmpty()) return name;
            return "latest";
        }
    }
}
