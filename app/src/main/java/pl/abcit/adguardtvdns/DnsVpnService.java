package pl.abcit.adguardtvdns;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class DnsVpnService extends VpnService {
    public static final String ACTION_START = "pl.abcit.adguardtvdns.START";
    public static final String ACTION_STOP = "pl.abcit.adguardtvdns.STOP";

    public static final String PREFS = "dns_settings";
    public static final String PREF_DNS_1 = "dns_1";
    public static final String PREF_DNS_2 = "dns_2";
    public static final String PREF_ALL_APPS = "all_apps";
    public static final String PREF_SELECTED_APPS = "selected_apps";

    public static final String PREF_RUNNING = "running";
    public static final String PREF_LAST_ERROR = "last_error";
    public static final String PREF_LAST_EVENT = "last_event";
    public static final String PREF_ACTIVE_DNS = "active_dns";
    public static final String PREF_ACTIVE_MODE = "active_mode";
    public static final String PREF_STARTED_AT = "started_at";
    public static final String PREF_PACKETS = "packets";
    public static final String PREF_DNS_QUERIES = "dns_queries";
    public static final String PREF_DNS_RESPONSES = "dns_responses";
    public static final String PREF_DNS_FAILURES = "dns_failures";
    public static final String PREF_LAST_DOMAIN = "last_domain";

    private static final String CHANNEL_ID = "dns_vpn_status";
    private static final int NOTIFICATION_ID = 1001;

    private final AtomicLong packets = new AtomicLong();
    private final AtomicLong dnsQueries = new AtomicLong();
    private final AtomicLong dnsResponses = new AtomicLong();
    private final AtomicLong dnsFailures = new AtomicLong();

    private volatile boolean running;
    private volatile long lastStatsWriteMs;
    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private FileOutputStream tunOutput;
    private ExecutorService executor;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            DebugLog.log(this, "STOP kliknięty");
            stopVpn("Zatrzymane ręcznie");
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("Łączenie..."));
        DebugLog.log(this, "START kliknięty");
        startVpn();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopVpn("Usługa zamknięta");
        super.onDestroy();
    }

    private void startVpn() {
        if (running) {
            DebugLog.log(this, "VPN już działa - pomijam drugi start");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean allApps = prefs.getBoolean(PREF_ALL_APPS, true);
        Set<String> selectedApps = prefs.getStringSet(PREF_SELECTED_APPS, new LinkedHashSet<String>());
        String[] upstreamServers = getUpstreamServers();

        packets.set(0);
        dnsQueries.set(0);
        dnsResponses.set(0);
        dnsFailures.set(0);
        writeStatus(false, "", "Przygotowanie VPN");

        if (!allApps && (selectedApps == null || selectedApps.isEmpty())) {
            String error = "Tryb wybranych aplikacji, ale lista jest pusta";
            DebugLog.log(this, "BŁĄD: " + error);
            writeStatus(false, error, "Start anulowany");
            stopSelf();
            return;
        }

        try {
            Builder builder = new Builder()
                    .setSession("AdGuard TV DNS Pro")
                    .setMtu(1500)
                    .addAddress(DnsPacketUtils.VPN_ADDRESS, 32)
                    .addRoute(DnsPacketUtils.VIRTUAL_DNS, 32)
                    .addDnsServer(DnsPacketUtils.VIRTUAL_DNS);

            int allowed = 0;
            if (!allApps && selectedApps != null) {
                for (String packageName : selectedApps) {
                    try {
                        builder.addAllowedApplication(packageName);
                        allowed++;
                        DebugLog.log(this, "Dołączona aplikacja: " + packageName);
                    } catch (Exception e) {
                        DebugLog.log(this, "Pomijam aplikację " + packageName + ": " + e.getClass().getSimpleName());
                    }
                }

                if (allowed == 0) {
                    String error = "Nie udało się dołączyć żadnej wybranej aplikacji";
                    DebugLog.log(this, "BŁĄD: " + error);
                    writeStatus(false, error, "Start anulowany");
                    stopSelf();
                    return;
                }
            }

            DebugLog.log(this, "DNS upstream: " + upstreamServers[0] + ", " + upstreamServers[1]);
            DebugLog.log(this, allApps ? "Tryb: wszystkie aplikacje" : "Tryb: wybrane aplikacje: " + allowed);

            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                String error = "Brak zgody Androida na VPN albo establish() zwróciło null";
                DebugLog.log(this, "BŁĄD: " + error);
                writeStatus(false, error, "VPN nie wystartował");
                stopSelf();
                return;
            }

            running = true;
            executor = Executors.newFixedThreadPool(4);
            writeStatus(true, "", "VPN aktywny");
            DebugLog.log(this, "VPN aktywny. Wirtualny DNS: " + DnsPacketUtils.VIRTUAL_DNS);
            updateNotification("DNS aktywny");

            vpnThread = new Thread(() -> vpnLoop(upstreamServers), "AdGuardTvDnsLoop");
            vpnThread.start();
        } catch (Exception e) {
            String error = e.getClass().getSimpleName() + ": " + safeMessage(e);
            DebugLog.log(this, "BŁĄD startu VPN: " + error);
            stopVpn(error);
            stopSelf();
        }
    }

    private void vpnLoop(String[] upstreamServers) {
        try (FileInputStream input = new FileInputStream(vpnInterface.getFileDescriptor());
             FileOutputStream output = new FileOutputStream(vpnInterface.getFileDescriptor())) {
            tunOutput = output;
            byte[] packet = new byte[32767];

            while (running) {
                int length = input.read(packet);
                if (length <= 0) continue;
                packets.incrementAndGet();

                byte[] copy = Arrays.copyOf(packet, length);
                DnsPacketUtils.DnsRequest request = DnsPacketUtils.parseUdpDnsQuery(copy, length);
                if (request == null) {
                    saveStats(false);
                    continue;
                }

                dnsQueries.incrementAndGet();
                String domain = DnsPacketUtils.extractQuestionName(request.dnsPayload);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_LAST_DOMAIN, domain).apply();

                long currentQuery = dnsQueries.get();
                if (currentQuery <= 20 || currentQuery % 25 == 0) {
                    DebugLog.log(this, "DNS query #" + currentQuery + ": " + domain);
                }

                executor.execute(() -> handleDnsRequest(request, upstreamServers, domain));
                saveStats(false);
            }
        } catch (Exception e) {
            if (running) {
                String error = e.getClass().getSimpleName() + ": " + safeMessage(e);
                DebugLog.log(this, "BŁĄD pętli VPN: " + error);
                writeStatus(false, error, "Pętla VPN zatrzymana");
            } else {
                DebugLog.log(this, "Pętla VPN zakończona");
            }
        } finally {
            running = false;
            saveStats(true);
        }
    }

    private void handleDnsRequest(DnsPacketUtils.DnsRequest request, String[] upstreamServers, String domain) {
        byte[] dnsResponse = queryUpstream(request.dnsPayload, upstreamServers, domain);
        if (dnsResponse == null || dnsResponse.length == 0) {
            dnsFailures.incrementAndGet();
            saveStats(true);
            return;
        }

        byte[] responsePacket = DnsPacketUtils.buildUdpDnsResponse(request, dnsResponse);
        try {
            synchronized (this) {
                if (tunOutput != null && running) {
                    tunOutput.write(responsePacket);
                    tunOutput.flush();
                    dnsResponses.incrementAndGet();
                }
            }
        } catch (Exception e) {
            dnsFailures.incrementAndGet();
            DebugLog.log(this, "BŁĄD odpowiedzi dla " + domain + ": " + e.getClass().getSimpleName());
        }
        saveStats(false);
    }

    private byte[] queryUpstream(byte[] dnsPayload, String[] upstreamServers, String domain) {
        for (String server : upstreamServers) {
            if (server == null || server.trim().isEmpty()) continue;

            try (DatagramSocket socket = new DatagramSocket()) {
                protect(socket);
                socket.setSoTimeout(5000);

                InetAddress address = InetAddress.getByName(server.trim());
                DatagramPacket request = new DatagramPacket(dnsPayload, dnsPayload.length, address, 53);
                socket.send(request);

                byte[] buffer = new byte[4096];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                socket.receive(response);
                return Arrays.copyOf(response.getData(), response.getLength());
            } catch (Exception e) {
                DebugLog.log(this, "Upstream " + server + " nie odpowiedział dla " + domain + ": " + e.getClass().getSimpleName());
            }
        }
        return null;
    }

    private String[] getUpstreamServers() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String dns1 = cleanDns(prefs.getString(PREF_DNS_1, "94.140.14.14"), "94.140.14.14");
        String dns2 = cleanDns(prefs.getString(PREF_DNS_2, "94.140.15.15"), "94.140.15.15");
        return new String[]{dns1, dns2};
    }

    private String cleanDns(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private void stopVpn(String reason) {
        boolean wasRunning = running;
        running = false;

        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }

        try {
            if (vpnInterface != null) vpnInterface.close();
        } catch (Exception ignored) {
        }
        vpnInterface = null;
        tunOutput = null;

        try {
            stopForeground(true);
        } catch (Exception ignored) {
        }

        writeStatus(false, reason == null ? "" : reason, wasRunning ? "VPN zatrzymany" : "VPN nieaktywny");
        saveStats(true);
        if (reason != null && !reason.trim().isEmpty()) {
            DebugLog.log(this, "Status: " + reason);
        }
    }

    private void writeStatus(boolean isRunning, String lastError, String lastEvent) {
        String[] dns = getUpstreamServers();
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean allApps = prefs.getBoolean(PREF_ALL_APPS, true);
        Set<String> selectedApps = prefs.getStringSet(PREF_SELECTED_APPS, new LinkedHashSet<String>());

        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean(PREF_RUNNING, isRunning)
                .putString(PREF_LAST_ERROR, lastError == null ? "" : lastError)
                .putString(PREF_LAST_EVENT, lastEvent == null ? "" : lastEvent)
                .putString(PREF_ACTIVE_DNS, dns[0] + ", " + dns[1])
                .putString(PREF_ACTIVE_MODE, allApps ? "Wszystkie aplikacje" : "Wybrane aplikacje: " + (selectedApps == null ? 0 : selectedApps.size()));
        if (isRunning) {
            editor.putLong(PREF_STARTED_AT, System.currentTimeMillis());
        }
        editor.apply();
        saveStats(true);
    }

    private void saveStats(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastStatsWriteMs < 1000) return;
        lastStatsWriteMs = now;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong(PREF_PACKETS, packets.get())
                .putLong(PREF_DNS_QUERIES, dnsQueries.get())
                .putLong(PREF_DNS_RESPONSES, dnsResponses.get())
                .putLong(PREF_DNS_FAILURES, dnsFailures.get())
                .apply();
    }

    private Notification buildNotification(String text) {
        createNotificationChannel();

        Intent openIntent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, openIntent, flags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("AdGuard TV DNS Pro")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "AdGuard TV DNS Pro",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Status lokalnego DNS przez VPN");
        manager.createNotificationChannel(channel);
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? "brak szczegółów" : message;
    }
}
