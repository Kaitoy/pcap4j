/*_##########################################################################
  _##
  _##  Copyright (C) 2012-2016  Pcap4J.org
  _##
  _##########################################################################
*/

package org.pcap4j.packet;

import java.util.Arrays;

import org.pcap4j.packet.IpV4Packet.IpV4Option;
import org.pcap4j.packet.namednumber.IpV4OptionType;
import org.pcap4j.util.ByteArrays;


/**
 * @author Kaito Yamada
 * @since pcap4j 0.9.11
 */
public final class IllegalIpV4Option implements IpV4Option, IllegalRawDataHolder {

  /**
   *
   */
  private static final long serialVersionUID = -4509427228608202960L;

  private final IpV4OptionType type;
  private final byte[] rawData;
  private final IllegalRawDataException cause;

  /**
   * A static factory method.
   * This method validates the arguments by {@link ByteArrays#validateBounds(byte[], int, int)},
   * which may throw exceptions undocumented here.
   *
   * @param rawData rawData
   * @param offset offset
   * @param length length
   * @param cause cause
   * @return a new IllegalIpV4Option object.
   */
  public static IllegalIpV4Option newInstance(
    byte[] rawData, int offset, int length, IllegalRawDataException cause
  ) {
    if (cause == null) {
      throw new NullPointerException("cause is null.");
    }
    ByteArrays.validateBounds(rawData, offset, length);
    return new IllegalIpV4Option(rawData, offset, length, cause);
  }

  private IllegalIpV4Option(byte[] rawData, int offset, int length, IllegalRawDataException cause) {
    this.type = IpV4OptionType.getInstance(rawData[offset]);
    this.rawData = new byte[length];
    System.arraycopy(rawData, offset, this.rawData, 0, length);
    this.cause = cause;
  }

  @Override
  public IpV4OptionType getType() { return type; }

  @Override
  public int length() { return rawData.length; }

  @Override
  public byte[] getRawData() {
    byte[] copy = new byte[rawData.length];
    System.arraycopy(rawData, 0, copy, 0, copy.length);
    return copy;
  }

  @Override
  public IllegalRawDataException getCause() {
    return cause;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[option-type: ")
      .append(type)
      .append("] [Illegal Raw Data: 0x")
      .append(ByteArrays.toHexString(rawData, ""))
      .append("] [cause: ")
      .append(cause)
      .append("]");
    return sb.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + cause.hashCode();
    result = prime * result + Arrays.hashCode(rawData);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    IllegalIpV4Option other = (IllegalIpV4Option) obj;
    if (!cause.equals(other.cause)) {
      return false;
    }
    if (!Arrays.equals(rawData, other.rawData)) {
      return false;
    }
    return true;
  }

}
