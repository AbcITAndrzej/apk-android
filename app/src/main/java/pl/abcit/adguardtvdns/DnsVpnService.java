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

public class DnsVpnService extends VpnService {
    public static final String ACTION_START = "pl.abcit.adguardtvdns.START";
    public static final String ACTION_STOP = "pl.abcit.adguardtvdns.STOP";
    public static final String PREFS = "dns_settings";
    public static final String PREF_DNS_1 = "dns_1";
    public static final String PREF_DNS_2 = "dns_2";
    public static final String PREF_ALL_APPS = "all_apps";
    public static final String PREF_SELECTED_APPS = "selected_apps";

    private static final String CHANNEL_ID = "dns_vpn_status";
    private static final int NOTIFICATION_ID = 1001;

    private volatile boolean running;
    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private FileOutputStream tunOutput;
    private ExecutorService executor;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopVpn();
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("DNS działa"));
        startVpn();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }

    private void startVpn() {
        if (running) return;

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean allApps = prefs.getBoolean(PREF_ALL_APPS, true);
        Set<String> selectedApps = prefs.getStringSet(PREF_SELECTED_APPS, new LinkedHashSet<String>());

        try {
            Builder builder = new Builder()
                    .setSession("AdGuard TV DNS")
                    .setMtu(1500)
                    .addAddress(DnsPacketUtils.VPN_ADDRESS, 32)
                    .addRoute(DnsPacketUtils.VIRTUAL_DNS, 32)
                    .addDnsServer(DnsPacketUtils.VIRTUAL_DNS);

            if (!allApps && selectedApps != null && !selectedApps.isEmpty()) {
                for (String packageName : selectedApps) {
                    try {
                        builder.addAllowedApplication(packageName);
                    } catch (Exception ignored) {
                        // App mogła zostać usunięta. Pomijamy ją.
                    }
                }
            }

            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                stopSelf();
                return;
            }

            running = true;
            executor = Executors.newFixedThreadPool(4);
            vpnThread = new Thread(this::vpnLoop, "AdGuardTvDnsLoop");
            vpnThread.start();
        } catch (Exception e) {
            stopVpn();
            stopSelf();
        }
    }

    private void vpnLoop() {
        String[] upstreamServers = getUpstreamServers();

        try (FileInputStream input = new FileInputStream(vpnInterface.getFileDescriptor());
             FileOutputStream output = new FileOutputStream(vpnInterface.getFileDescriptor())) {
            tunOutput = output;
            byte[] packet = new byte[32767];

            while (running) {
                int length = input.read(packet);
                if (length <= 0) continue;

                byte[] copy = Arrays.copyOf(packet, length);
                DnsPacketUtils.DnsRequest request = DnsPacketUtils.parseUdpDnsQuery(copy, length);
                if (request == null) continue;

                executor.execute(() -> handleDnsRequest(request, upstreamServers));
            }
        } catch (Exception ignored) {
            // Zamknięcie VPN podczas stopu też trafi tutaj.
        } finally {
            running = false;
        }
    }

    private void handleDnsRequest(DnsPacketUtils.DnsRequest request, String[] upstreamServers) {
        byte[] dnsResponse = queryUpstream(request.dnsPayload, upstreamServers);
        if (dnsResponse == null || dnsResponse.length == 0) return;

        byte[] responsePacket = DnsPacketUtils.buildUdpDnsResponse(request, dnsResponse);
        try {
            synchronized (this) {
                if (tunOutput != null && running) {
                    tunOutput.write(responsePacket);
                    tunOutput.flush();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private byte[] queryUpstream(byte[] dnsPayload, String[] upstreamServers) {
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
            } catch (Exception ignored) {
                // Spróbuj następnego serwera.
            }
        }
        return null;
    }

    private String[] getUpstreamServers() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String dns1 = prefs.getString(PREF_DNS_1, "94.140.14.14");
        String dns2 = prefs.getString(PREF_DNS_2, "94.140.15.15");
        return new String[]{dns1, dns2};
    }

    private void stopVpn() {
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
                .setContentTitle("AdGuard TV DNS")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "AdGuard TV DNS",
                NotificationManager.IMPORTANCE_LOW
        );
        manager.createNotificationChannel(channel);
    }
}
