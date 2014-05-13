/*_##########################################################################
  _##
  _##  Copyright (C) 2012-2014  Kaito Yamada
  _##
  _##########################################################################
*/

package org.pcap4j.packet;

/**
 * @author Kaito Yamada
 * @since pcap4j 0.9.11
 */
public final class IcmpV4InformationReplyPacket extends IcmpIdentifiablePacket {

  /**
   *
   */
  private static final long serialVersionUID = -9187969821832140340L;

  private final IcmpV4InformationReplyHeader header;

  /**
   *
   * @param rawData
   * @return a new IcmpV4InformationReplyPacket object.
   * @throws IllegalRawDataException
   * @throws NullPointerException if the rawData argument is null.
   * @throws IllegalArgumentException if the rawData argument is empty.
   */
  public static IcmpV4InformationReplyPacket newPacket(
    byte[] rawData
  ) throws IllegalRawDataException {
    if (rawData == null) {
      throw new NullPointerException("rawData must not be null.");
    }
    if (rawData.length == 0) {
      throw new IllegalArgumentException("rawData is empty.");
    }
    return new IcmpV4InformationReplyPacket(rawData);
  }

  private IcmpV4InformationReplyPacket(byte[] rawData) throws IllegalRawDataException {
    this.header = new IcmpV4InformationReplyHeader(rawData);
  }

  private IcmpV4InformationReplyPacket(Builder builder) {
    super(builder);
    this.header = new IcmpV4InformationReplyHeader(builder);
  }

  @Override
  public IcmpV4InformationReplyHeader getHeader() { return header; }

  @Override
  public Builder getBuilder() {
    return new Builder(this);
  }

  /**
   * @author Kaito Yamada
   * @since pcap4j 0.9.11
   */
  public static
  final class Builder extends org.pcap4j.packet.IcmpIdentifiablePacket.Builder {

    /**
     *
     */
    public Builder() {}

    @Override
    public Builder identifier(short identifier) {
      super.identifier(identifier);
      return this;
    }

    @Override
    public Builder sequenceNumber(short sequenceNumber) {
      super.sequenceNumber(sequenceNumber);
      return this;
    }

    private Builder(IcmpV4InformationReplyPacket packet) { super(packet); }

    @Override
    public IcmpV4InformationReplyPacket build() {
      return new IcmpV4InformationReplyPacket(this);
    }

  }

  /**
   * @author Kaito Yamada
   * @since pcap4j 0.9.11
   */
  public static final class IcmpV4InformationReplyHeader extends IcmpIdentifiableHeader {

    /*
     *  0                            15
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |         Identifier            |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |       Sequence Number         |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     */

    /**
     *
     */
    private static final long serialVersionUID = -2093444994122929555L;

    private IcmpV4InformationReplyHeader(byte[] rawData) throws IllegalRawDataException {
      super(rawData);
    }

    private IcmpV4InformationReplyHeader(Builder builder) { super(builder); }

    @Override
    protected String getHeaderName() {
      return "ICMPv4 Information Reply Header";
    }

  }

}
