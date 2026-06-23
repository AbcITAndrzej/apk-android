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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DnsVpnService extends VpnService {
    public static final String ACTION_START = "pl.abcit.adguardtvdns.START";
    public static final String ACTION_STOP = "pl.abcit.adguardtvdns.STOP";

    public static final String PREFS = "dns_settings";
    public static final String PREF_DNS_1 = "dns_1";
    public static final String PREF_DNS_2 = "dns_2";
    public static final String PREF_SERVER_NAME = "server_name";
    public static final String PREF_ALL_APPS = "all_apps";
    public static final String PREF_SELECTED_APPS = "selected_apps";
    public static final String PREF_LOGGING_APPS = "logging_apps";
    public static final String PREF_BATTERY_SAVER = "battery_saver";
    public static final String PREF_IPV6_DNS = "ipv6_dns";
    public static final String PREF_TCP_FALLBACK = "tcp_fallback";
    public static final String PREF_AUTOSTART = "autostart";
    public static final String PREF_AUTOSTART_PROFILE = "autostart_profile";

    public static final String PREF_RUNNING = "running";
    public static final String PREF_LAST_ERROR = "last_error";
    public static final String PREF_LAST_EVENT = "last_event";
    public static final String PREF_ACTIVE_DNS = "active_dns";
    public static final String PREF_ACTIVE_SERVER = "active_server";
    public static final String PREF_ACTIVE_MODE = "active_mode";
    public static final String PREF_ACTIVE_GROUP = "active_group";
    public static final String PREF_STARTED_AT = "started_at";
    public static final String PREF_PACKETS = "packets";
    public static final String PREF_DNS_QUERIES = "dns_queries";
    public static final String PREF_DNS_RESPONSES = "dns_responses";
    public static final String PREF_DNS_FAILURES = "dns_failures";
    public static final String PREF_DNS_DROPPED = "dns_dropped";
    public static final String PREF_LAST_DOMAIN = "last_domain";

    private static final String CHANNEL_ID = "dns_vpn_status";
    private static final int NOTIFICATION_ID = 1001;

    private final AtomicLong packets = new AtomicLong();
    private final AtomicLong dnsQueries = new AtomicLong();
    private final AtomicLong dnsResponses = new AtomicLong();
    private final AtomicLong dnsFailures = new AtomicLong();
    private final AtomicLong droppedQueries = new AtomicLong();

    private volatile boolean running;
    private volatile long lastStatsWriteMs;
    private volatile long lastDomainWriteMs;
    private volatile String logGroup = "SYSTEM";
    private volatile boolean domainLoggingEnabled;

    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private FileOutputStream tunOutput;
    private ThreadPoolExecutor executor;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            DebugLog.log(this, "SYSTEM", "STOP requested");
            stopVpn("Stopped manually");
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("Connecting…"));
        DebugLog.log(this, "SYSTEM", "CONNECT requested");
        startVpn();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopVpn("Service destroyed");
        super.onDestroy();
    }

    private void startVpn() {
        if (running) {
            DebugLog.log(this, "SYSTEM", "VPN already running - duplicate start ignored");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean allApps = prefs.getBoolean(PREF_ALL_APPS, true);
        boolean batterySaver = prefs.getBoolean(PREF_BATTERY_SAVER, true);
        boolean ipv6Dns = prefs.getBoolean(PREF_IPV6_DNS, false);
        boolean tcpFallback = prefs.getBoolean(PREF_TCP_FALLBACK, true);
        Set<String> selectedApps = prefs.getStringSet(PREF_SELECTED_APPS, new LinkedHashSet<String>());
        Set<String> loggingApps = prefs.getStringSet(PREF_LOGGING_APPS, new LinkedHashSet<String>());
        String[] upstreamServers = getUpstreamServers();
        String serverName = prefs.getString(PREF_SERVER_NAME, "AdGuard DNS");

        packets.set(0);
        dnsQueries.set(0);
        dnsResponses.set(0);
        dnsFailures.set(0);
        droppedQueries.set(0);
        lastStatsWriteMs = 0;
        lastDomainWriteMs = 0;

        logGroup = resolveLogGroup(allApps, selectedApps);
        domainLoggingEnabled = shouldLogDomains(allApps, selectedApps, loggingApps);
        writeStatus(false, "", "Preparing VPN");

        if (!allApps && (selectedApps == null || selectedApps.isEmpty())) {
            String error = "Selected-app mode is enabled but no app is selected";
            DebugLog.log(this, "SYSTEM", "ERROR: " + error);
            writeStatus(false, error, "Start cancelled");
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

            if (ipv6Dns) {
                try {
                    builder.addAddress(DnsPacketUtils.VPN_ADDRESS_V6, 128)
                            .addRoute(DnsPacketUtils.VIRTUAL_DNS_V6, 128)
                            .addDnsServer(DnsPacketUtils.VIRTUAL_DNS_V6);
                    DebugLog.log(this, "SYSTEM", "IPv6 virtual DNS enabled: " + DnsPacketUtils.VIRTUAL_DNS_V6);
                } catch (Exception e) {
                    DebugLog.log(this, "SYSTEM", "IPv6 virtual DNS could not be enabled: " + e.getClass().getSimpleName());
                }
            }

            int allowed = 0;
            if (allApps) {
                try {
                    builder.addDisallowedApplication(getPackageName());
                    DebugLog.log(this, "SYSTEM", "Own app excluded from VPN scope to prevent DNS loop");
                } catch (Exception e) {
                    DebugLog.log(this, "SYSTEM", "Could not exclude own app from VPN scope: " + e.getClass().getSimpleName());
                }
            }
            if (!allApps && selectedApps != null) {
                for (String packageName : selectedApps) {
                    if (getPackageName().equals(packageName)) {
                        DebugLog.log(this, "SYSTEM", "Skipping own app in selected-app mode to prevent DNS loop");
                        continue;
                    }
                    try {
                        builder.addAllowedApplication(packageName);
                        allowed++;
                        DebugLog.log(this, packageName, "App included in VPN scope");
                    } catch (Exception e) {
                        DebugLog.log(this, "SYSTEM", "Skipping app " + packageName + ": " + e.getClass().getSimpleName());
                    }
                }

                if (allowed == 0) {
                    String error = "No selected application could be added to VPN scope";
                    DebugLog.log(this, "SYSTEM", "ERROR: " + error);
                    writeStatus(false, error, "Start cancelled");
                    stopSelf();
                    return;
                }
            }

            DebugLog.log(this, "SYSTEM", "Server: " + serverName + " / " + upstreamServers[0] + ", " + upstreamServers[1]);
            DebugLog.log(this, "SYSTEM", allApps ? "Mode: all apps" : "Mode: selected apps: " + allowed);
            DebugLog.log(this, "SYSTEM", "Advanced domain logging: " + (domainLoggingEnabled ? "ON" : "OFF") + ", battery saver: " + (batterySaver ? "ON" : "OFF") + ", IPv6 DNS: " + (ipv6Dns ? "ON" : "OFF") + ", TCP fallback: " + (tcpFallback ? "ON" : "OFF"));

            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                String error = "VPN permission missing or establish() returned null";
                DebugLog.log(this, "SYSTEM", "ERROR: " + error);
                writeStatus(false, error, "VPN did not start");
                stopSelf();
                return;
            }

            running = true;
            int coreWorkers = batterySaver ? 1 : 2;
            int maxWorkers = batterySaver ? 2 : 4;
            executor = new ThreadPoolExecutor(
                    coreWorkers,
                    maxWorkers,
                    20L,
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<Runnable>(batterySaver ? 96 : 256),
                    new ThreadPoolExecutor.DiscardPolicy()
            );
            writeStatus(true, "", "VPN active");
            DebugLog.log(this, "SYSTEM", "VPN active. Virtual DNS: " + DnsPacketUtils.VIRTUAL_DNS);
            updateNotification("DNS active: " + serverName);

            vpnThread = new Thread(() -> vpnLoop(upstreamServers, batterySaver, tcpFallback), "AdGuardTvDnsLoop");
            vpnThread.start();
        } catch (Exception e) {
            String error = e.getClass().getSimpleName() + ": " + safeMessage(e);
            DebugLog.log(this, "SYSTEM", "VPN start error: " + error);
            stopVpn(error);
            stopSelf();
        }
    }

    private void vpnLoop(String[] upstreamServers, boolean batterySaver, boolean tcpFallback) {
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
                maybeWriteLastDomain(domain, batterySaver);
                maybeLogDomain(domain, batterySaver);

                if (executor != null && !executor.isShutdown()) {
                    int beforeQueue = executor.getQueue().size();
                    executor.execute(() -> handleDnsRequest(request, upstreamServers, domain, tcpFallback));
                    if (beforeQueue >= (batterySaver ? 95 : 255)) {
                        droppedQueries.incrementAndGet();
                    }
                } else {
                    dnsFailures.incrementAndGet();
                }
                saveStats(false);
            }
        } catch (Exception e) {
            if (running) {
                String error = e.getClass().getSimpleName() + ": " + safeMessage(e);
                DebugLog.log(this, "SYSTEM", "VPN loop error: " + error);
                writeStatus(false, error, "VPN loop stopped");
            } else {
                DebugLog.log(this, "SYSTEM", "VPN loop finished");
            }
        } finally {
            running = false;
            saveStats(true);
        }
    }

    private void handleDnsRequest(DnsPacketUtils.DnsRequest request, String[] upstreamServers, String domain, boolean tcpFallback) {
        byte[] dnsResponse = queryUpstream(request.dnsPayload, upstreamServers, domain, tcpFallback);
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
                    dnsResponses.incrementAndGet();
                }
            }
        } catch (Exception e) {
            dnsFailures.incrementAndGet();
            DebugLog.log(this, logGroup, "Response write error for " + domain + ": " + e.getClass().getSimpleName());
        }
        saveStats(false);
    }

    private byte[] queryUpstream(byte[] dnsPayload, String[] upstreamServers, String domain, boolean tcpFallback) {
        for (String server : upstreamServers) {
            if (server == null || server.trim().isEmpty()) continue;

            try (DatagramSocket socket = new DatagramSocket()) {
                boolean protectedSocket = protect(socket);
                if (!protectedSocket) {
                    DebugLog.log(this, "SYSTEM", "ERROR: protect(socket) returned false for upstream " + server);
                    continue;
                }
                socket.setSoTimeout(3500);

                InetAddress address = InetAddress.getByName(server.trim());
                socket.connect(address, 53);
                DatagramPacket request = new DatagramPacket(dnsPayload, dnsPayload.length);
                socket.send(request);

                byte[] buffer = new byte[4096];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                socket.receive(response);
                byte[] udpResponse = Arrays.copyOf(response.getData(), response.getLength());
                if (tcpFallback && DnsPacketUtils.isTruncatedDnsResponse(udpResponse)) {
                    byte[] tcpResponse = queryUpstreamTcp(dnsPayload, server.trim(), domain);
                    if (tcpResponse != null && tcpResponse.length > 0) return tcpResponse;
                }
                return udpResponse;
            } catch (Exception e) {
                if (tcpFallback) {
                    byte[] tcpResponse = queryUpstreamTcp(dnsPayload, server.trim(), domain);
                    if (tcpResponse != null && tcpResponse.length > 0) return tcpResponse;
                }
                long failures = dnsFailures.get();
                if (failures < 10 || failures % 50 == 0) {
                    DebugLog.log(this, logGroup, "Upstream " + server + " failed for " + domain + ": " + e.getClass().getSimpleName());
                }
            }
        }
        return null;
    }


    private byte[] queryUpstreamTcp(byte[] dnsPayload, String server, String domain) {
        Socket socket = null;
        try {
            InetAddress address = InetAddress.getByName(server);
            socket = new Socket();
            boolean protectedSocket = protect(socket);
            if (!protectedSocket) {
                DebugLog.log(this, "SYSTEM", "ERROR: protect(tcp socket) returned false for upstream " + server);
                closeQuietly(socket);
                return null;
            }
            socket.connect(new InetSocketAddress(address, 53), 3500);
            socket.setSoTimeout(4500);
            OutputStream out = socket.getOutputStream();
            out.write((dnsPayload.length >> 8) & 0xFF);
            out.write(dnsPayload.length & 0xFF);
            out.write(dnsPayload);
            out.flush();

            InputStream in = socket.getInputStream();
            int hi = in.read();
            int lo = in.read();
            if (hi < 0 || lo < 0) return null;
            int len = ((hi & 0xFF) << 8) | (lo & 0xFF);
            if (len <= 0 || len > 65535) return null;
            byte[] data = new byte[len];
            int pos = 0;
            while (pos < len) {
                int n = in.read(data, pos, len - pos);
                if (n < 0) return null;
                pos += n;
            }
            DebugLog.log(this, logGroup, "TCP fallback OK for " + domain + " via " + server);
            return data;
        } catch (Exception e) {
            long failures = dnsFailures.get();
            if (failures < 10 || failures % 50 == 0) {
                DebugLog.log(this, logGroup, "TCP upstream " + server + " failed for " + domain + ": " + e.getClass().getSimpleName());
            }
            return null;
        } finally {
            closeQuietly(socket);
        }
    }

    private void stopVpn(String reason) {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        closeQuietly(vpnInterface);
        vpnInterface = null;
        tunOutput = null;
        writeStatus(false, reason == null ? "" : reason, reason == null ? "Stopped" : reason);
        updateNotification("DNS stopped");
        stopForeground(true);
        DebugLog.log(this, "SYSTEM", "VPN stopped: " + (reason == null ? "no reason" : reason));
    }

    private String[] getUpstreamServers() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String dns1 = cleanDns(prefs.getString(PREF_DNS_1, "94.140.14.14"), "94.140.14.14");
        String dns2 = cleanDns(prefs.getString(PREF_DNS_2, "94.140.15.15"), "94.140.15.15");
        return new String[]{dns1, dns2};
    }

    private String cleanDns(String value, String fallback) {
        if (value == null) return fallback;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private boolean shouldLogDomains(boolean allApps, Set<String> selectedApps, Set<String> loggingApps) {
        if (loggingApps == null || loggingApps.isEmpty()) return false;
        if (allApps) return loggingApps.contains("__ALL__");
        if (selectedApps == null || selectedApps.isEmpty()) return false;
        for (String pkg : selectedApps) {
            if (loggingApps.contains(pkg)) return true;
        }
        return false;
    }

    private String resolveLogGroup(boolean allApps, Set<String> selectedApps) {
        if (allApps) return "ALL_APPS";
        if (selectedApps != null && selectedApps.size() == 1) {
            return selectedApps.iterator().next();
        }
        int count = selectedApps == null ? 0 : selectedApps.size();
        return "SELECTED_APPS_" + count;
    }

    private void maybeWriteLastDomain(String domain, boolean batterySaver) {
        long now = System.currentTimeMillis();
        long interval = batterySaver ? 2000 : 650;
        if (now - lastDomainWriteMs < interval) return;
        lastDomainWriteMs = now;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_LAST_DOMAIN, domain).apply();
    }

    private void maybeLogDomain(String domain, boolean batterySaver) {
        if (!domainLoggingEnabled) return;
        long n = dnsQueries.get();
        if (batterySaver && n % 10 != 1) return;
        if (!batterySaver && n > 80 && n % 5 != 0) return;
        DebugLog.log(this, logGroup, "DNS query #" + n + ": " + domain);
    }

    private void writeStatus(boolean isRunning, String lastError, String lastEvent) {
        String[] dns = getUpstreamServers();
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean allApps = prefs.getBoolean(PREF_ALL_APPS, true);
        Set<String> selectedApps = prefs.getStringSet(PREF_SELECTED_APPS, new LinkedHashSet<String>());
        String serverName = prefs.getString(PREF_SERVER_NAME, "AdGuard DNS");

        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean(PREF_RUNNING, isRunning)
                .putString(PREF_LAST_ERROR, lastError == null ? "" : lastError)
                .putString(PREF_LAST_EVENT, lastEvent == null ? "" : lastEvent)
                .putString(PREF_ACTIVE_DNS, dns[0] + ", " + dns[1])
                .putString(PREF_ACTIVE_SERVER, serverName)
                .putString(PREF_ACTIVE_GROUP, logGroup)
                .putString(PREF_ACTIVE_MODE, allApps ? "All apps" : "Selected apps: " + (selectedApps == null ? 0 : selectedApps.size()));
        if (isRunning) {
            editor.putLong(PREF_STARTED_AT, System.currentTimeMillis());
        }
        editor.apply();
        saveStats(true);
    }

    private void saveStats(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastStatsWriteMs < 1500) return;
        lastStatsWriteMs = now;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong(PREF_PACKETS, packets.get())
                .putLong(PREF_DNS_QUERIES, dnsQueries.get())
                .putLong(PREF_DNS_RESPONSES, dnsResponses.get())
                .putLong(PREF_DNS_FAILURES, dnsFailures.get())
                .putLong(PREF_DNS_DROPPED, droppedQueries.get())
                .apply();
    }

    private Notification buildNotification(String text) {
        createNotificationChannel();

        Intent openIntent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
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
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "AdGuard TV DNS Pro", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Local DNS over VPN status");
        manager.createNotificationChannel(channel);
    }

    private void closeQuietly(Socket socket) {
        if (socket == null) return;
        try { socket.close(); } catch (Exception ignored) {}
    }

    private void closeQuietly(ParcelFileDescriptor pfd) {
        if (pfd == null) return;
        try {
            pfd.close();
        } catch (Exception ignored) {
        }
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? "no details" : message;
    }
}
