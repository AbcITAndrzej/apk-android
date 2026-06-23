package pl.abcit.adguardtvdns;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Random;

final class DnsDiagnostics {
    private DnsDiagnostics() {}

    static Result test(String dns1, String dns2, boolean tcpFallback) {
        String[] servers = new String[]{clean(dns1), clean(dns2)};
        byte[] query = buildDnsQuery("adguard.com");
        for (String server : servers) {
            if (server == null || server.length() == 0) continue;
            long start = System.currentTimeMillis();
            try {
                byte[] response = udp(server, query);
                if (response != null && response.length >= 12 && !DnsPacketUtils.isTruncatedDnsResponse(response)) {
                    return new Result(true, server, "UDP OK", System.currentTimeMillis() - start);
                }
                if (tcpFallback) {
                    response = tcp(server, query);
                    if (response != null && response.length >= 12) {
                        return new Result(true, server, "TCP fallback OK", System.currentTimeMillis() - start);
                    }
                }
            } catch (Exception e) {
                if (tcpFallback) {
                    try {
                        byte[] response = tcp(server, query);
                        if (response != null && response.length >= 12) {
                            return new Result(true, server, "TCP fallback OK", System.currentTimeMillis() - start);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return new Result(false, "-", "No DNS response", 0);
    }

    private static byte[] udp(String server, byte[] query) throws Exception {
        InetAddress address = InetAddress.getByName(server);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(3500);
            DatagramPacket request = new DatagramPacket(query, query.length, address, 53);
            socket.send(request);
            byte[] buffer = new byte[4096];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            return Arrays.copyOf(response.getData(), response.getLength());
        }
    }

    private static byte[] tcp(String server, byte[] query) throws Exception {
        InetAddress address = InetAddress.getByName(server);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, 53), 3500);
            socket.setSoTimeout(4500);
            OutputStream out = socket.getOutputStream();
            out.write((query.length >> 8) & 0xFF);
            out.write(query.length & 0xFF);
            out.write(query);
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
            return data;
        }
    }

    private static byte[] buildDnsQuery(String name) {
        byte[] qname = encodeName(name);
        byte[] packet = new byte[12 + qname.length + 4];
        int id = new Random().nextInt(0xFFFF);
        packet[0] = (byte) ((id >> 8) & 0xFF);
        packet[1] = (byte) (id & 0xFF);
        packet[2] = 0x01; // recursion desired
        packet[3] = 0x00;
        packet[4] = 0x00;
        packet[5] = 0x01; // QDCOUNT
        System.arraycopy(qname, 0, packet, 12, qname.length);
        int pos = 12 + qname.length;
        packet[pos] = 0x00;
        packet[pos + 1] = 0x01; // A
        packet[pos + 2] = 0x00;
        packet[pos + 3] = 0x01; // IN
        return packet;
    }

    private static byte[] encodeName(String name) {
        String[] parts = name.split("\\.");
        int len = 1;
        for (String p : parts) len += 1 + p.length();
        byte[] out = new byte[len];
        int pos = 0;
        for (String p : parts) {
            out[pos++] = (byte) p.length();
            for (int i = 0; i < p.length(); i++) out[pos++] = (byte) p.charAt(i);
        }
        out[pos] = 0;
        return out;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    static final class Result {
        final boolean ok;
        final String server;
        final String detail;
        final long millis;
        Result(boolean ok, String server, String detail, long millis) {
            this.ok = ok;
            this.server = server;
            this.detail = detail;
            this.millis = millis;
        }
    }
}
