/*_##########################################################################
  _##
  _##  Copyright (C) 2012-2017  Pcap4J.org
  _##
  _##########################################################################
*/

package org.pcap4j.core;

import org.pcap4j.packet.Packet;

/**
 * @author Kaito Yamada
 * @since pcap4j 0.9.8
 */
@FunctionalInterface
public interface PacketListener {

  /**
   *
   * @param packet packet
   */
  public void gotPacket(Packet packet);

}
