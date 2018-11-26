package org.pcap4j.packet;

import static org.junit.Assert.*;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pcap4j.packet.EthernetPacket;
import org.pcap4j.packet.IcmpV6CommonPacket;
import org.pcap4j.packet.IcmpV6MobilePrefixSolicitationPacket.IcmpV6MobilePrefixSolicitationHeader;
import org.pcap4j.packet.IllegalRawDataException;
import org.pcap4j.packet.IpV6Packet;
import org.pcap4j.packet.IpV6SimpleFlowLabel;
import org.pcap4j.packet.IpV6SimpleTrafficClass;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.SimpleBuilder;
import org.pcap4j.packet.UnknownPacket;
import org.pcap4j.packet.namednumber.EtherType;
import org.pcap4j.packet.namednumber.IcmpV6Code;
import org.pcap4j.packet.namednumber.IcmpV6Type;
import org.pcap4j.packet.namednumber.IpNumber;
import org.pcap4j.packet.namednumber.IpVersion;
import org.pcap4j.util.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("javadoc")
public class IcmpV6MobilePrefixSolicitationPacketTest {

    private static final Logger logger = LoggerFactory
            .getLogger(IcmpV6HomeAgentAddressDiscoveryRequestPacketTest.class);
    private final IcmpV6MobilePrefixSolicitationPacket packet;
    private final short identifier;
    private final short reserved;

    public IcmpV6MobilePrefixSolicitationPacketTest() {
        this.identifier = (short) 1234;
        this.reserved = (short) 12345;

        IcmpV6MobilePrefixSolicitationPacket.Builder b = new IcmpV6MobilePrefixSolicitationPacket.Builder();
        b.identifier(identifier)
                .reserved(reserved);
        this.packet = b.build();
    }

    public Packet getPacket() {
        return packet;
    }

    protected Packet getWholePacket() {
        Inet6Address srcAddr;
        Inet6Address dstAddr;
        try {
            srcAddr = (Inet6Address) InetAddress.getByName("2001:db8::3:2:2");
            dstAddr = (Inet6Address) InetAddress.getByName("2001:db8::3:2:1");
        } catch (UnknownHostException e) {
            throw new AssertionError();
        }
        IcmpV6CommonPacket.Builder icmpV6b = new IcmpV6CommonPacket.Builder();
        icmpV6b.type(IcmpV6Type.MOBILE_PREFIX_SOLICITATION)
                .code(IcmpV6Code.NO_CODE)
                .srcAddr(srcAddr)
                .dstAddr(dstAddr)
                .payloadBuilder(new SimpleBuilder(packet))
                .correctChecksumAtBuild(true);

        IpV6Packet.Builder ipv6b = new IpV6Packet.Builder();
        ipv6b.version(IpVersion.IPV6)
                .trafficClass(IpV6SimpleTrafficClass.newInstance((byte) 0x12))
                .flowLabel(IpV6SimpleFlowLabel.newInstance(0x12345))
                .nextHeader(IpNumber.ICMPV6)
                .hopLimit((byte) 100)
                .srcAddr(srcAddr)
                .dstAddr(dstAddr)
                .correctLengthAtBuild(true)
                .payloadBuilder(icmpV6b);

        EthernetPacket.Builder eb = new EthernetPacket.Builder();
        eb.dstAddr(MacAddress.getByName("fe:00:00:00:00:02"))
                .srcAddr(MacAddress.getByName("fe:00:00:00:00:01"))
                .type(EtherType.IPV6)
                .payloadBuilder(ipv6b)
                .paddingAtBuild(true);
        return eb.build();

    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        logger.info(
                "########## " + IcmpV6MobilePrefixSolicitationPacketTest.class.getSimpleName() + " START ##########");
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {}

    @Test
    public void testNewPacket() {
        IcmpV6MobilePrefixSolicitationPacket p;
        try {
            p = IcmpV6MobilePrefixSolicitationPacket.newPacket(packet.getRawData(), 0, packet.getRawData().length);
        } catch (IllegalRawDataException e) {
            throw new AssertionError(e);
        }

    }

    @Test
    public void testGetHeader() {
        IcmpV6MobilePrefixSolicitationHeader h = packet.getHeader();
        assertEquals(identifier, h.getIdentifier());
        assertEquals(reserved, h.getReserved());
    }

    @Test
    public void testGetWholePacket() {
        System.out.println(getWholePacket().toString());
    }
}
