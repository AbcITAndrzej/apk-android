package pl.abcit.adguardtvdns;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class AppUpdateManager {
    private static final String REPO = "AbcITAndrzej/apk-android";
    private static final String LATEST_RELEASE_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";

    private AppUpdateManager() {}

    static void checkForUpdate(Activity activity) {
        if (activity == null) return;
        Toast.makeText(activity, activity.getString(R.string.checking_update), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                ReleaseInfo info = fetchLatestRelease();
                String current = getCurrentVersionName(activity);
                boolean newer = isDifferentVersion(current, info.tagName, info.name);
                activity.runOnUiThread(() -> {
                    if (!newer) {
                        Toast.makeText(activity, activity.getString(R.string.no_update_available), Toast.LENGTH_LONG).show();
                    } else {
                        showUpdateDialog(activity, info, current);
                    }
                });
            } catch (Exception e) {
                DebugLog.log(activity, "SYSTEM", "Update check failed: " + e.getClass().getSimpleName());
                activity.runOnUiThread(() -> Toast.makeText(activity, activity.getString(R.string.update_check_failed), Toast.LENGTH_LONG).show());
            }
        }, "AppUpdateCheck").start();
    }

    private static void showUpdateDialog(Activity activity, ReleaseInfo info, String current) {
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.update_available))
                .setMessage(activity.getString(R.string.update_available_message, current, info.displayName()))
                .setPositiveButton(activity.getString(R.string.download_update), (d, w) -> downloadAndInstall(activity, info))
                .setNegativeButton(activity.getString(R.string.cancel), null)
                .show();
    }

    private static ReleaseInfo fetchLatestRelease() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(LATEST_RELEASE_URL).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "AdGuard-TV-DNS-Pro-Updater");
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(stream);
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + body);
        JSONObject json = new JSONObject(body);
        ReleaseInfo info = new ReleaseInfo();
        info.tagName = json.optString("tag_name", "");
        info.name = json.optString("name", "");
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
        if (info.downloadUrl == null || info.downloadUrl.length() == 0) throw new Exception("No APK asset in latest release");
        return info;
    }

    private static void downloadAndInstall(Activity activity, ReleaseInfo info) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
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
            activity.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        } catch (Exception e) {
            DebugLog.log(activity, "SYSTEM", "Update download failed: " + e.getClass().getSimpleName());
            Toast.makeText(activity, activity.getString(R.string.update_download_failed), Toast.LENGTH_LONG).show();
        }
    }

    private static String getCurrentVersionName(Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pi.versionName == null ? "unknown" : pi.versionName;
        } catch (Exception e) {
            return "unknown";
        }
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
        return v.replace("release", "").replace("updater", "").replace("debug", "").replace("_", "-");
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

    private static final class ReleaseInfo {
        String tagName;
        String name;
        String apkName;
        String downloadUrl;
        String displayName() {
            if (tagName != null && !tagName.isEmpty()) return tagName;
            if (name != null && !name.isEmpty()) return name;
            return "latest";
        }
    }
}
