package pl.abcit.adguardtvdns;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

final class DnsPacketUtils {
    static final String VPN_ADDRESS = "10.10.10.2";
    static final String VIRTUAL_DNS = "10.10.10.10";
    static final String VPN_ADDRESS_V6 = "fd00:ad:guard::2";
    static final String VIRTUAL_DNS_V6 = "fd00:ad:guard::53";

    private static final byte[] VIRTUAL_DNS_BYTES = new byte[]{10, 10, 10, 10};
    private static final byte[] VIRTUAL_DNS_V6_BYTES = parseAddress(VIRTUAL_DNS_V6, 16);
    private static final AtomicInteger NEXT_IP_ID = new AtomicInteger(1);

    private DnsPacketUtils() {}

    static DnsRequest parseUdpDnsQuery(byte[] packet, int length) {
        if (packet == null || length < 28) return null;
        int version = (packet[0] >> 4) & 0x0F;
        if (version == 4) return parseIpv4UdpDnsQuery(packet, length);
        if (version == 6) return parseIpv6UdpDnsQuery(packet, length);
        return null;
    }

    private static DnsRequest parseIpv4UdpDnsQuery(byte[] packet, int length) {
        int ihl = (packet[0] & 0x0F) * 4;
        if (ihl < 20 || length < ihl + 8) return null;

        int totalLength = readUnsignedShort(packet, 2);
        if (totalLength <= 0 || totalLength > length) totalLength = length;

        int protocol = packet[9] & 0xFF;
        if (protocol != 17) return null;

        int flagsAndFragment = readUnsignedShort(packet, 6);
        if ((flagsAndFragment & 0x1FFF) != 0) return null;
        if (!matchesVirtualDns(packet, 16)) return null;

        int udpStart = ihl;
        int srcPort = readUnsignedShort(packet, udpStart);
        int dstPort = readUnsignedShort(packet, udpStart + 2);
        if (dstPort != 53) return null;

        int udpLength = readUnsignedShort(packet, udpStart + 4);
        if (udpLength < 8 || udpStart + udpLength > totalLength) return null;

        int dnsStart = udpStart + 8;
        int dnsLength = udpLength - 8;
        if (dnsLength <= 0) return null;

        DnsRequest request = new DnsRequest();
        request.ipv6 = false;
        request.srcIp = Arrays.copyOfRange(packet, 12, 16);
        request.dstIp = Arrays.copyOfRange(packet, 16, 20);
        request.srcPort = srcPort;
        request.dstPort = dstPort;
        request.dnsPayload = Arrays.copyOfRange(packet, dnsStart, dnsStart + dnsLength);
        return request;
    }

    private static DnsRequest parseIpv6UdpDnsQuery(byte[] packet, int length) {
        if (length < 48) return null;
        int payloadLength = readUnsignedShort(packet, 4);
        int nextHeader = packet[6] & 0xFF;
        if (nextHeader != 17) return null; // no IPv6 extension header parsing here
        int totalLength = 40 + payloadLength;
        if (payloadLength <= 0 || totalLength > length) totalLength = length;
        if (!matchesVirtualDnsV6(packet, 24)) return null;

        int udpStart = 40;
        int srcPort = readUnsignedShort(packet, udpStart);
        int dstPort = readUnsignedShort(packet, udpStart + 2);
        if (dstPort != 53) return null;

        int udpLength = readUnsignedShort(packet, udpStart + 4);
        if (udpLength < 8 || udpStart + udpLength > totalLength) return null;

        int dnsStart = udpStart + 8;
        int dnsLength = udpLength - 8;
        if (dnsLength <= 0) return null;

        DnsRequest request = new DnsRequest();
        request.ipv6 = true;
        request.srcIp = Arrays.copyOfRange(packet, 8, 24);
        request.dstIp = Arrays.copyOfRange(packet, 24, 40);
        request.srcPort = srcPort;
        request.dstPort = dstPort;
        request.dnsPayload = Arrays.copyOfRange(packet, dnsStart, dnsStart + dnsLength);
        return request;
    }

    static byte[] buildUdpDnsResponse(DnsRequest request, byte[] dnsPayload) {
        if (request == null || dnsPayload == null) return new byte[0];
        return request.ipv6 ? buildIpv6UdpDnsResponse(request, dnsPayload) : buildIpv4UdpDnsResponse(request, dnsPayload);
    }

    private static byte[] buildIpv4UdpDnsResponse(DnsRequest request, byte[] dnsPayload) {
        int ipHeaderLength = 20;
        int udpHeaderLength = 8;
        int totalLength = ipHeaderLength + udpHeaderLength + dnsPayload.length;
        byte[] packet = new byte[totalLength];

        packet[0] = 0x45;
        packet[1] = 0;
        writeShort(packet, 2, totalLength);
        writeShort(packet, 4, NEXT_IP_ID.getAndIncrement() & 0xFFFF);
        writeShort(packet, 6, 0);
        packet[8] = 64;
        packet[9] = 17;
        packet[10] = 0;
        packet[11] = 0;

        System.arraycopy(request.dstIp, 0, packet, 12, 4);
        System.arraycopy(request.srcIp, 0, packet, 16, 4);

        int ipChecksum = checksum(packet, 0, ipHeaderLength);
        writeShort(packet, 10, ipChecksum);

        int udpStart = ipHeaderLength;
        writeShort(packet, udpStart, request.dstPort);
        writeShort(packet, udpStart + 2, request.srcPort);
        writeShort(packet, udpStart + 4, udpHeaderLength + dnsPayload.length);
        writeShort(packet, udpStart + 6, 0);
        System.arraycopy(dnsPayload, 0, packet, udpStart + udpHeaderLength, dnsPayload.length);

        int udpChecksum = udpChecksumIpv4(packet, udpStart, udpHeaderLength + dnsPayload.length, request.dstIp, request.srcIp);
        writeShort(packet, udpStart + 6, udpChecksum);
        return packet;
    }

    private static byte[] buildIpv6UdpDnsResponse(DnsRequest request, byte[] dnsPayload) {
        int ipHeaderLength = 40;
        int udpHeaderLength = 8;
        int udpLength = udpHeaderLength + dnsPayload.length;
        byte[] packet = new byte[ipHeaderLength + udpLength];

        packet[0] = 0x60;
        writeShort(packet, 4, udpLength);
        packet[6] = 17;
        packet[7] = 64;
        System.arraycopy(request.dstIp, 0, packet, 8, 16);
        System.arraycopy(request.srcIp, 0, packet, 24, 16);

        int udpStart = ipHeaderLength;
        writeShort(packet, udpStart, request.dstPort);
        writeShort(packet, udpStart + 2, request.srcPort);
        writeShort(packet, udpStart + 4, udpLength);
        writeShort(packet, udpStart + 6, 0);
        System.arraycopy(dnsPayload, 0, packet, udpStart + udpHeaderLength, dnsPayload.length);

        int udpChecksum = udpChecksumIpv6(packet, udpStart, udpLength, request.dstIp, request.srcIp);
        writeShort(packet, udpStart + 6, udpChecksum);
        return packet;
    }

    static String extractQuestionName(byte[] dnsPayload) {
        if (dnsPayload == null || dnsPayload.length < 13) return "?";
        StringBuilder name = new StringBuilder();
        int index = 12;
        int guard = 0;

        while (index < dnsPayload.length && guard++ < 80) {
            int len = dnsPayload[index] & 0xFF;
            if (len == 0) return name.length() == 0 ? "." : name.toString();
            if ((len & 0xC0) == 0xC0) return name.length() == 0 ? "?" : name.toString();
            index++;
            if (len > 63 || index + len > dnsPayload.length) return "?";
            if (name.length() > 0) name.append('.');
            for (int i = 0; i < len; i++) {
                int c = dnsPayload[index + i] & 0xFF;
                name.append(c >= 32 && c <= 126 ? (char) c : '?');
            }
            index += len;
        }
        return name.length() == 0 ? "?" : name.toString();
    }

    static boolean isTruncatedDnsResponse(byte[] dnsPayload) {
        return dnsPayload != null && dnsPayload.length > 3 && (dnsPayload[2] & 0x02) != 0;
    }

    private static boolean matchesVirtualDns(byte[] packet, int offset) {
        return packet[offset] == VIRTUAL_DNS_BYTES[0]
                && packet[offset + 1] == VIRTUAL_DNS_BYTES[1]
                && packet[offset + 2] == VIRTUAL_DNS_BYTES[2]
                && packet[offset + 3] == VIRTUAL_DNS_BYTES[3];
    }

    private static boolean matchesVirtualDnsV6(byte[] packet, int offset) {
        if (VIRTUAL_DNS_V6_BYTES == null) return false;
        for (int i = 0; i < 16; i++) if (packet[offset + i] != VIRTUAL_DNS_V6_BYTES[i]) return false;
        return true;
    }

    private static int udpChecksumIpv4(byte[] packet, int udpStart, int udpLength, byte[] srcIp, byte[] dstIp) {
        long sum = 0;
        sum += readUnsignedShort(srcIp, 0);
        sum += readUnsignedShort(srcIp, 2);
        sum += readUnsignedShort(dstIp, 0);
        sum += readUnsignedShort(dstIp, 2);
        sum += 17;
        sum += udpLength;
        return finishChecksum(sum + payloadSum(packet, udpStart, udpLength));
    }

    private static int udpChecksumIpv6(byte[] packet, int udpStart, int udpLength, byte[] srcIp, byte[] dstIp) {
        long sum = 0;
        for (int i = 0; i < 16; i += 2) sum += readUnsignedShort(srcIp, i);
        for (int i = 0; i < 16; i += 2) sum += readUnsignedShort(dstIp, i);
        sum += (udpLength >>> 16) & 0xFFFF;
        sum += udpLength & 0xFFFF;
        sum += 17;
        return finishChecksum(sum + payloadSum(packet, udpStart, udpLength));
    }

    private static long payloadSum(byte[] data, int start, int length) {
        long sum = 0;
        int end = start + length;
        for (int i = start; i + 1 < end; i += 2) sum += readUnsignedShort(data, i);
        if ((length & 1) == 1) sum += (data[end - 1] & 0xFF) << 8;
        return sum;
    }

    private static int checksum(byte[] data, int start, int length) {
        return finishChecksum(payloadSum(data, start, length));
    }

    private static int finishChecksum(long sum) {
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        int result = (int) (~sum) & 0xFFFF;
        return result == 0 ? 0xFFFF : result;
    }

    private static int readUnsignedShort(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static void writeShort(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >> 8) & 0xFF);
        data[offset + 1] = (byte) (value & 0xFF);
    }

    private static byte[] parseAddress(String value, int expectedLength) {
        try {
            byte[] raw = InetAddress.getByName(value).getAddress();
            return raw.length == expectedLength ? raw : null;
        } catch (Exception e) {
            return null;
        }
    }

    static class DnsRequest {
        boolean ipv6;
        byte[] srcIp;
        byte[] dstIp;
        int srcPort;
        int dstPort;
        byte[] dnsPayload;
    }
}
