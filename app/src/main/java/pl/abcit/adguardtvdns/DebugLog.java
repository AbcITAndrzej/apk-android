package pl.abcit.adguardtvdns;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class DebugLog {
    static final String PREF_LOGS = "debug_logs";
    private static final String TAG = "AdGuardTvDns";
    private static final int MAX_CHARS = 24000;

    private DebugLog() {
    }

    static synchronized void log(Context context, String message) {
        if (context == null || message == null) return;
        String line = now() + "  " + message + "\n";
        SharedPreferences prefs = context.getSharedPreferences(DnsVpnService.PREFS, Context.MODE_PRIVATE);
        String old = prefs.getString(PREF_LOGS, "");
        String merged = old + line;
        if (merged.length() > MAX_CHARS) {
            merged = merged.substring(merged.length() - MAX_CHARS);
            int firstNewLine = merged.indexOf('\n');
            if (firstNewLine >= 0 && firstNewLine + 1 < merged.length()) {
                merged = merged.substring(firstNewLine + 1);
            }
        }
        prefs.edit().putString(PREF_LOGS, merged).apply();
        Log.d(TAG, message);
    }

    static synchronized String read(Context context) {
        if (context == null) return "";
        return context.getSharedPreferences(DnsVpnService.PREFS, Context.MODE_PRIVATE)
                .getString(PREF_LOGS, "");
    }

    static synchronized void clear(Context context) {
        if (context == null) return;
        context.getSharedPreferences(DnsVpnService.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_LOGS, now() + "  Log wyczyszczony\n")
                .apply();
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }
}
