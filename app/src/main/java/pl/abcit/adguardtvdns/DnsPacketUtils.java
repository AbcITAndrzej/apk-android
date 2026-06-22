package pl.abcit.adguardtvdns;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

final class DnsPacketUtils {
    static final String VPN_ADDRESS = "10.111.222.1";
    static final String VIRTUAL_DNS = "10.111.222.2";

    private static final byte[] VIRTUAL_DNS_BYTES = new byte[]{10, 111, (byte) 222, 2};
    private static final AtomicInteger NEXT_IP_ID = new AtomicInteger(1);

    private DnsPacketUtils() {
    }

    static DnsRequest parseUdpDnsQuery(byte[] packet, int length) {
        if (packet == null || length < 28) return null;

        int version = (packet[0] >> 4) & 0x0F;
        int ihl = (packet[0] & 0x0F) * 4;
        if (version != 4 || ihl < 20 || length < ihl + 8) return null;

        int totalLength = readUnsignedShort(packet, 2);
        if (totalLength <= 0 || totalLength > length) totalLength = length;

        int protocol = packet[9] & 0xFF;
        if (protocol != 17) return null; // UDP only

        int flagsAndFragment = readUnsignedShort(packet, 6);
        if ((flagsAndFragment & 0x1FFF) != 0) return null; // ignore fragments

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
        request.srcIp = Arrays.copyOfRange(packet, 12, 16);
        request.dstIp = Arrays.copyOfRange(packet, 16, 20);
        request.srcPort = srcPort;
        request.dstPort = dstPort;
        request.dnsPayload = Arrays.copyOfRange(packet, dnsStart, dnsStart + dnsLength);
        return request;
    }

    static byte[] buildUdpDnsResponse(DnsRequest request, byte[] dnsPayload) {
        int ipHeaderLength = 20;
        int udpHeaderLength = 8;
        int totalLength = ipHeaderLength + udpHeaderLength + dnsPayload.length;
        byte[] packet = new byte[totalLength];

        packet[0] = 0x45;
        packet[1] = 0;
        writeShort(packet, 2, totalLength);
        writeShort(packet, 4, NEXT_IP_ID.getAndIncrement() & 0xFFFF);
        writeShort(packet, 6, 0); // flags + fragment offset
        packet[8] = 64;
        packet[9] = 17; // UDP
        packet[10] = 0;
        packet[11] = 0;

        System.arraycopy(request.dstIp, 0, packet, 12, 4); // source IP: virtual DNS
        System.arraycopy(request.srcIp, 0, packet, 16, 4); // destination IP: app/VPN address

        int ipChecksum = checksum(packet, 0, ipHeaderLength);
        writeShort(packet, 10, ipChecksum);

        int udpStart = ipHeaderLength;
        writeShort(packet, udpStart, request.dstPort); // source port 53
        writeShort(packet, udpStart + 2, request.srcPort);
        writeShort(packet, udpStart + 4, udpHeaderLength + dnsPayload.length);
        writeShort(packet, udpStart + 6, 0);
        System.arraycopy(dnsPayload, 0, packet, udpStart + udpHeaderLength, dnsPayload.length);

        int udpChecksum = udpChecksum(packet, udpStart, udpHeaderLength + dnsPayload.length,
                request.dstIp, request.srcIp);
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
            if (len == 0) {
                return name.length() == 0 ? "." : name.toString();
            }
            if ((len & 0xC0) == 0xC0) {
                // Pytanie DNS normalnie nie powinno tu używać kompresji, ale log nie może rozwalić usługi.
                return name.length() == 0 ? "?" : name.toString();
            }
            index++;
            if (len > 63 || index + len > dnsPayload.length) return "?";
            if (name.length() > 0) name.append('.');
            for (int i = 0; i < len; i++) {
                int c = dnsPayload[index + i] & 0xFF;
                if (c >= 32 && c <= 126) {
                    name.append((char) c);
                } else {
                    name.append('?');
                }
            }
            index += len;
        }
        return name.length() == 0 ? "?" : name.toString();
    }


    private static boolean matchesVirtualDns(byte[] packet, int offset) {
        return packet[offset] == VIRTUAL_DNS_BYTES[0]
                && packet[offset + 1] == VIRTUAL_DNS_BYTES[1]
                && packet[offset + 2] == VIRTUAL_DNS_BYTES[2]
                && packet[offset + 3] == VIRTUAL_DNS_BYTES[3];
    }

    private static int udpChecksum(byte[] packet, int udpStart, int udpLength, byte[] srcIp, byte[] dstIp) {
        long sum = 0;

        sum += readUnsignedShort(srcIp, 0);
        sum += readUnsignedShort(srcIp, 2);
        sum += readUnsignedShort(dstIp, 0);
        sum += readUnsignedShort(dstIp, 2);
        sum += 17;
        sum += udpLength;

        int end = udpStart + udpLength;
        for (int i = udpStart; i + 1 < end; i += 2) {
            sum += readUnsignedShort(packet, i);
        }
        if ((udpLength & 1) == 1) {
            sum += (packet[end - 1] & 0xFF) << 8;
        }

        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        int result = (int) (~sum) & 0xFFFF;
        return result == 0 ? 0xFFFF : result;
    }

    private static int checksum(byte[] data, int start, int length) {
        long sum = 0;
        int end = start + length;
        for (int i = start; i + 1 < end; i += 2) {
            sum += readUnsignedShort(data, i);
        }
        if ((length & 1) == 1) {
            sum += (data[end - 1] & 0xFF) << 8;
        }
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (int) (~sum) & 0xFFFF;
    }

    private static int readUnsignedShort(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static void writeShort(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >> 8) & 0xFF);
        data[offset + 1] = (byte) (value & 0xFF);
    }

    static class DnsRequest {
        byte[] srcIp;
        byte[] dstIp;
        int srcPort;
        int dstPort;
        byte[] dnsPayload;
    }
}
