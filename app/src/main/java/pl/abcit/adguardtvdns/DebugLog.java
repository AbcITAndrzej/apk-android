package pl.abcit.adguardtvdns;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DebugLog {
    private static final String PREFS = "dns_debug_log";
    private static final String KEY_LINES = "lines";
    private static final int MAX_LINES = 280;
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private DebugLog() {}

    static synchronized void log(Context context, String message) {
        log(context, "SYSTEM", message);
    }

    static synchronized void log(Context context, String group, String message) {
        if (context == null || message == null) return;
        String safeGroup = sanitize(group == null || group.trim().isEmpty() ? "SYSTEM" : group.trim());
        String safeMessage = sanitize(message);
        String line = FORMAT.format(new Date()) + " | " + safeGroup + " | " + safeMessage;

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<String> lines = readLinesInternal(prefs);
        lines.add(line);
        while (lines.size() > MAX_LINES) {
            lines.remove(0);
        }
        prefs.edit().putString(KEY_LINES, join(lines)).apply();
    }

    static synchronized List<String> readLines(Context context) {
        if (context == null) return new ArrayList<>();
        return readLinesInternal(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    static synchronized String read(Context context) {
        return join(readLines(context));
    }

    static synchronized Map<String, List<String>> readGrouped(Context context) {
        List<String> lines = readLines(context);
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        Collections.reverse(lines);
        for (String line : lines) {
            String group = "SYSTEM";
            String rest = line;
            String[] parts = line.split(" \\| ", 3);
            if (parts.length == 3) {
                group = parts[1];
                rest = parts[0] + "  " + parts[2];
            }
            List<String> list = grouped.get(group);
            if (list == null) {
                list = new ArrayList<>();
                grouped.put(group, list);
            }
            if (list.size() < 18) list.add(rest);
        }
        return grouped;
    }

    static synchronized void clear(Context context) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LINES).apply();
    }

    private static List<String> readLinesInternal(SharedPreferences prefs) {
        String raw = prefs.getString(KEY_LINES, "");
        List<String> result = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return result;
        String[] split = raw.split("\\n");
        for (String s : split) {
            if (s != null && !s.trim().isEmpty()) result.add(s);
        }
        return result;
    }

    private static String join(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) builder.append('\n');
            builder.append(lines.get(i));
        }
        return builder.toString();
    }

    private static String sanitize(String s) {
        return s.replace('\n', ' ').replace('\r', ' ').replace('|', '/');
    }
}
