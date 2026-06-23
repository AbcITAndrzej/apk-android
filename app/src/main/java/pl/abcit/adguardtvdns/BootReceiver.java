package pl.abcit.adguardtvdns;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;

public class BootReceiver extends BroadcastReceiver {
    public static final String PROFILE_CURRENT = "__CURRENT__";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) return;
        String action = intent == null ? "" : intent.getAction();
        SharedPreferences prefs = context.getSharedPreferences(DnsVpnService.PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(DnsVpnService.PREF_AUTOSTART, false)) return;

        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                Thread.sleep(15000);
                String profileName = prefs.getString(DnsVpnService.PREF_AUTOSTART_PROFILE, PROFILE_CURRENT);
                if (profileName != null && !PROFILE_CURRENT.equals(profileName)) {
                    applyProfileByName(context, prefs, profileName);
                }
                Intent prepare = VpnService.prepare(context);
                if (prepare != null) {
                    DebugLog.log(context, "SYSTEM", "Autostart skipped after " + action + ": VPN permission is not granted yet");
                    return;
                }
                Intent service = new Intent(context, DnsVpnService.class);
                service.setAction(DnsVpnService.ACTION_START);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service); else context.startService(service);
                DebugLog.log(context, "SYSTEM", "Autostart started after " + action + " using profile: " + profileName);
            } catch (Exception e) {
                DebugLog.log(context, "SYSTEM", "Autostart failed: " + e.getClass().getSimpleName());
            } finally {
                try { pending.finish(); } catch (Exception ignored) {}
            }
        }, "AdGuardTvDnsBoot").start();
    }

    private void applyProfileByName(Context context, SharedPreferences prefs, String name) throws Exception {
        String json = prefs.getString("profiles_json", "[]");
        JSONArray arr = new JSONArray(json);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            if (!name.equals(o.optString("name", ""))) continue;
            Set<String> packages = new LinkedHashSet<>();
            JSONArray pkgs = o.optJSONArray("packages");
            if (pkgs != null) for (int j = 0; j < pkgs.length(); j++) packages.add(pkgs.optString(j));
            prefs.edit()
                    .putString(DnsVpnService.PREF_SERVER_NAME, o.optString("serverName", "AdGuard DNS"))
                    .putString(DnsVpnService.PREF_DNS_1, o.optString("dns1", "94.140.14.14"))
                    .putString(DnsVpnService.PREF_DNS_2, o.optString("dns2", "94.140.15.15"))
                    .putBoolean(DnsVpnService.PREF_ALL_APPS, o.optBoolean("allApps", true))
                    .putStringSet(DnsVpnService.PREF_SELECTED_APPS, packages)
                    .apply();
            DebugLog.log(context, "SYSTEM", "Autostart profile applied: " + name);
            return;
        }
        DebugLog.log(context, "SYSTEM", "Autostart profile not found: " + name);
    }
}
