/*_##########################################################################
  _##
  _##  Copyright (C) 2011-2014  Kaito Yamada
  _##
  _##########################################################################
*/

package org.pcap4j.core;

import java.io.EOFException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.pcap4j.core.BpfProgram.BpfCompileMode;
import org.pcap4j.core.NativeMappings.PcapErrbuf;
import org.pcap4j.core.NativeMappings.PcapLibrary;
import org.pcap4j.core.NativeMappings.bpf_program;
import org.pcap4j.core.NativeMappings.pcap_pkthdr;
import org.pcap4j.core.NativeMappings.pcap_stat;
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode;
import org.pcap4j.packet.IllegalRawDataException;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.factory.PacketFactories;
import org.pcap4j.packet.namednumber.DataLinkType;
import org.pcap4j.util.ByteArrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * A wrapper class for struct pcap_t.
 *
 * @author Kaito Yamada
 * @since pcap4j 0.9.1
 */
public final class PcapHandle {

  private static final Logger logger = LoggerFactory.getLogger(PcapHandle.class);

  private volatile DataLinkType dlt;
  private final Pointer handle;
  private final ThreadLocal<Long> timestampsInts
    = new ThreadLocal<Long>();
  private final ThreadLocal<Integer> timestampsMicros
    = new ThreadLocal<Integer>();
  private final ReentrantReadWriteLock handleLock = new ReentrantReadWriteLock(true);

  private volatile boolean open = true;
  private volatile String filteringExpression = "";

  private static final Inet4Address WILDCARD_MASK;

  static {
    try {
      WILDCARD_MASK = (Inet4Address)InetAddress.getByName("0.0.0.0");
    } catch (UnknownHostException e) {
      throw new AssertionError("never get here");
    }
  }

  PcapHandle(Pointer handle) {
    this.handle = handle;
    this.dlt = getDltByNative();
  }

  private PcapHandle(Builder builder) throws PcapNativeException {
    PcapErrbuf errbuf = new PcapErrbuf();
    this.handle
      = NativeMappings.pcap_create(
          builder.deviceName,
          errbuf
        );
    if (handle == null || errbuf.length() != 0) {
      throw new PcapNativeException(errbuf.toString());
    }

    try {
      if (builder.isSnaplenSet) {
        int rc = NativeMappings.pcap_set_snaplen(handle, builder.snaplen);
        if (rc != 0) {
          throw new PcapNativeException(getError(), rc);
        }
      }
      if (builder.promiscuousMode != null) {
        int rc = NativeMappings.pcap_set_promisc(handle, builder.promiscuousMode.getValue());
        if (rc != 0) {
          throw new PcapNativeException(getError(), rc);
        }
      }
      if (builder.isRfmonSet) {
        try {
          int rc = PcapLibrary.INSTANCE.pcap_set_rfmon(handle, builder.rfmon ? 1 : 0);
          if (rc != 0) {
            throw new PcapNativeException(getError(), rc);
          }
        } catch (UnsatisfiedLinkError e) {
          logger.error("Failed to instantiate PcapHandle object.", e);
          throw new PcapNativeException("Monitor mode is not supported on this platform.");
        }
      }
      if (builder.isTimeoutMillisSet) {
        int rc = NativeMappings.pcap_set_timeout(handle, builder.timeoutMillis);
        if (rc != 0) {
          throw new PcapNativeException(getError(), rc);
        }
      }
      if (builder.isBufferSizeSet) {
        int rc = NativeMappings.pcap_set_buffer_size(handle, builder.bufferSize);
        if (rc != 0) {
          throw new PcapNativeException(getError(), rc);
        }
      }

      int rc = NativeMappings.pcap_activate(handle);
      if (rc < 0) {
        throw new PcapNativeException(getError(), rc);
      }
    } catch (NotOpenException e) {
      throw new AssertionError("Never get here.");
    }

    this.dlt = getDltByNative();
  }

  private DataLinkType getDltByNative() {
    return DataLinkType.getInstance(
             NativeMappings.pcap_datalink(handle)
           );
  }

  /**
   *
   * @return the Data Link Type of this PcapHandle
   */
  public DataLinkType getDlt() { return dlt; }

  /**
   * @param dlt a {@link org.pcap4j.packet.namednumber.DataLinkType DataLinkType}
   *        object to set
   * @throws PcapNativeException
   * @throws NotOpenException
   */
  public void setDlt(DataLinkType dlt) throws PcapNativeException, NotOpenException {
    if (dlt == null) {
      throw new NullPointerException("dlt must not be null.");
    }
    if (!open) {
      throw new NotOpenException();
    }

    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }

      int rc = NativeMappings.pcap_set_datalink(handle, dlt.value());
      if (rc < 0) {
        throw new PcapNativeException(getError(), rc);
      }
    } finally {
      handleLock.readLock().unlock();
    }

    this.dlt = dlt;
  }

  /**
   *
   * @return true if this PcapHandle object is open (i.e. not yet closed by {@link #close() close()});
   *         false otherwise.
   */
  public boolean isOpen() { return open; }

  /**
   *
   * @return the filtering expression of this PcapHandle
   */
  public String getFilteringExpression() {return filteringExpression; }

  /**
   *
   * @return an integer part of a timestamp of a packet captured in a current thread.
   */
  public Long getTimestampInts() { return timestampsInts.get(); }

  /**
   *
   * @return a fraction part of a timestamp of a packet captured in a current thread.
   *         The value represents the number of microseconds.
   */
  public Integer getTimestampMicros() { return timestampsMicros.get(); }

  /**
   *
   * @return the dimension of the packet portion (in bytes) that is delivered to the application.
   * @throws NotOpenException
   */
  public int getSnapshot() throws NotOpenException {
    if (!open) {
      throw new NotOpenException();
    }

    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }
      return NativeMappings.pcap_snapshot(handle);
    } finally {
      handleLock.readLock().unlock();
    }
  }

  /**
   *
   * @return a {@link org.pcap4j.core.PcapHandle.SwappedType SwappedType} object.
   * @throws NotOpenException
   */
  public SwappedType isSwapped() throws NotOpenException {
    if (!open) {
      throw new NotOpenException();
    }

    int rc;
    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }
      rc = NativeMappings.pcap_is_swapped(handle);
    } finally {
      handleLock.readLock().unlock();
    }

    switch (rc) {
      case 0:
        return SwappedType.NOT_SWAPPED;
      case 1:
        return SwappedType.SWAPPED;
      case 2:
        return SwappedType.MAYBE_SWAPPED;
      default:
        logger.warn("pcap_snapshot returned an unexpected code: " + rc);
        return SwappedType.MAYBE_SWAPPED;
    }
  }

  /**
   *
   * @return the major version number of the pcap library used to write the savefile.
   * @throws NotOpenException
   */
  public int getMajorVersion() throws NotOpenException {
    if (!open) {
      throw new NotOpenException();
    }

    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }
      return NativeMappings.pcap_major_version(handle);
    } finally {
      handleLock.readLock().unlock();
    }
  }

  /**
   *
   * @return the minor version number of the pcap library used to write the savefile.
   * @throws NotOpenException
   */
  public int getMinorVersion() throws NotOpenException {
    if (!open) {
      throw new NotOpenException();
    }

    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }
      return NativeMappings.pcap_minor_version(handle);
    } finally {
      handleLock.readLock().unlock();
    }
  }

  /**
   *
   * @param bpfExpression
   * @param mode
   * @param netmask
   * @return a {@link org.pcap4j.core.BpfProgram BpfProgram} object.
   * @throws PcapNativeException
   * @throws NotOpenException
   */
  public BpfProgram compileFilter(
    String bpfExpression, BpfCompileMode mode, Inet4Address netmask
  ) throws PcapNativeException, NotOpenException {
    if (
         bpfExpression == null
      || mode == null
      || netmask == null
    ) {
      StringBuilder sb = new StringBuilder();
      sb.append("bpfExpression: ").append(bpfExpression)
        .append(" mode: ").append(mode)
        .append(" netmask: ").append(netmask);
      throw new NullPointerException(sb.toString());
    }
    if (!open) {
      throw new NotOpenException();
    }

    bpf_program prog;
    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }

      prog = new bpf_program();
      int rc = NativeMappings.pcap_compile(
                 handle, prog, bpfExpression, mode.getValue(),
                 ByteArrays.getInt(ByteArrays.toByteArray(netmask), 0)
               );
      if (rc < 0) {
        throw new PcapNativeException(getError(), rc);
      }
    } finally {
      handleLock.readLock().unlock();
    }

    return new BpfProgram(prog, bpfExpression);
  }

  /**
   *
   * @param bpfExpression
   * @param mode
   * @param netmask
   * @throws PcapNativeException
   * @throws NotOpenException
   * @throws NullPointerException
   */
  public void setFilter(
    String bpfExpression, BpfCompileMode mode, Inet4Address netmask
  ) throws PcapNativeException, NotOpenException {
    if (
         bpfExpression == null
      || mode == null
      || netmask == null
    ) {
      StringBuilder sb = new StringBuilder();
      sb.append("bpfExpression: ").append(bpfExpression)
        .append(" mode: ").append(mode)
        .append(" netmask: ").append(netmask);
      throw new NullPointerException(sb.toString());
    }
    if (!open) {
      throw new NotOpenException();
    }

    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }

      bpf_program prog = new bpf_program();
      try {
        int mask = ByteArrays.getInt(ByteArrays.toByteArray(netmask), 0);
        int rc = NativeMappings.pcap_compile(
                   handle, prog, bpfExpression, mode.getValue(), mask
                 );
        if (rc < 0) {
          throw new PcapNativeException(
                      "Error occured in pcap_compile: " + getError(),
                      rc
                    );
        }

        rc = NativeMappings.pcap_setfilter(handle, prog);
        if (rc < 0) {
          throw new PcapNativeException(
                      "Error occured in pcap_setfilger: " + getError(),
                      rc
                    );
        }

        this.filteringExpression = bpfExpression;
      } finally {
        NativeMappings.pcap_freecode(prog);
      }
    } finally {
      handleLock.readLock().unlock();
    }
  }

  /**
   *
   * @param bpfExpression
   * @param mode
   * @throws PcapNativeException
   * @throws NotOpenException
   * @throws NullPointerException
   */
  public void setFilter(
    String bpfExpression, BpfCompileMode mode
  ) throws PcapNativeException, NotOpenException {
    setFilter(bpfExpression, mode, WILDCARD_MASK);
  }

  /**
   *
   * @param prog
   * @throws PcapNativeException
   * @throws NotOpenException
   */
  public void setFilter(BpfProgram prog) throws PcapNativeException, NotOpenException {
    if (prog == null) {
      StringBuilder sb = new StringBuilder();
      sb.append("prog: ").append(prog);
      throw new NullPointerException(sb.toString());
    }
    if (!open) {
      throw new NotOpenException();
    }

    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }

      int rc = NativeMappings.pcap_setfilter(handle, prog.getProgram());
      if (rc < 0) {
        throw new PcapNativeException("Failed to set filter: " + getError(), rc);
      }
    } finally {
      handleLock.readLock().unlock();
    }

    this.filteringExpression = prog.getExpression();
  }

  /**
   *
   * @param mode
   * @throws PcapNativeException
   * @throws NotOpenException
   */
  public void setBlockingMode(BlockingMode mode) throws PcapNativeException, NotOpenException {
    if (mode == null) {
      StringBuilder sb = new StringBuilder();
      sb.append(" mode: ").append(mode);
      throw new NullPointerException(sb.toString());
    }
    if (!open) {
      throw new NotOpenException();
    }

    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }

      PcapErrbuf errbuf = new PcapErrbuf();
      int rc = NativeMappings.pcap_setnonblock(handle, mode.getValue(), errbuf);
      if (rc < 0) {
        throw new PcapNativeException(errbuf.toString(), rc);
      }
    } finally {
      handleLock.readLock().unlock();
    }
  }

  /**
   *
   * @return blocking mode
   * @throws PcapNativeException
   * @throws NotOpenException
   */
  public BlockingMode getBlockingMode() throws PcapNativeException, NotOpenException {
    if (!open) {
      throw new NotOpenException();
    }

    PcapErrbuf errbuf = new PcapErrbuf();
    int rc;
    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }
      rc = NativeMappings.pcap_getnonblock(handle, errbuf);
    } finally {
      handleLock.readLock().unlock();
    }

    if (rc == 0) {
      return BlockingMode.BLOCKING;
    }
    else if (rc > 0) {
      return BlockingMode.NONBLOCKING;
    }
    else {
      throw new PcapNativeException(errbuf.toString(), rc);
    }
  }

  /**
   *
   * @return a captured packet.
   * @throws NotOpenException
   */
  public Packet getNextPacket() throws NotOpenException, IllegalRawDataException {
    if (!open) {
      throw new NotOpenException();
    }

    pcap_pkthdr header = new pcap_pkthdr();
    header.setAutoSynch(false);
    Pointer packet;
    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }
      packet = NativeMappings.pcap_next(handle, header);
    } finally {
      handleLock.readLock().unlock();
    }

    if (packet != null) {
      Pointer headerP = header.getPointer();
      timestampsInts.set(pcap_pkthdr.getTvSec(headerP).longValue());
      timestampsMicros.set(pcap_pkthdr.getTvUsec(headerP).intValue());
      byte[] ba = packet.getByteArray(0, pcap_pkthdr.getCaplen(headerP));
      return PacketFactories.getFactory(Packet.class, DataLinkType.class)
               .newInstance(ba, 0, ba.length, dlt);
    }
    else {
      return null;
    }
  }

  /**
   *
   * @return a captured packet.
   * @throws PcapNativeException
   * @throws EOFException
   * @throws TimeoutException
   * @throws NotOpenException
   * @throws IllegalRawDataException
   */
  public Packet getNextPacketEx()
  throws PcapNativeException, EOFException, TimeoutException, NotOpenException, IllegalRawDataException {
    if (!open) {
      throw new NotOpenException();
    }


    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }

      PointerByReference headerPP = new PointerByReference();
      PointerByReference dataPP = new PointerByReference();
      int rc = NativeMappings.pcap_next_ex(handle, headerPP, dataPP);
      switch (rc) {
        case 0:
          throw new TimeoutException();
        case 1:
          Pointer headerP = headerPP.getValue();
          Pointer dataP = dataPP.getValue();
          if (headerP == null || dataP == null) {
            throw new PcapNativeException(
                        "Failed to get packet. *header: "
                          + headerP + " *data: " + dataP
                      );
          }

          timestampsInts.set(pcap_pkthdr.getTvSec(headerP).longValue());
          timestampsMicros.set(pcap_pkthdr.getTvUsec(headerP).intValue());
          byte[] ba = dataP.getByteArray(0, pcap_pkthdr.getCaplen(headerP));
          return PacketFactories.getFactory(Packet.class, DataLinkType.class)
                   .newInstance(ba, 0, ba.length, dlt);
        case -1:
          throw new PcapNativeException(
                  "Error occured in pcap_next_ex(): " + getError(), rc
                );
        case -2:
          throw new EOFException();
        default:
          throw new PcapNativeException(
                  "Unexpected error occured: " + getError(), rc
                );
      }
    } finally {
      handleLock.readLock().unlock();
    }
  }

  /**
   * A wrapper method for <code>int pcap_loop(pcap_t *, int, pcap_handler, u_char *)</code>.
   * When a packet is captured, <code>listener.gotPacket(Packet)</code> is called in
   * the thread which called the <code>loop()</code>. And then this PcapHandle waits for
   * the thread to return from the <code>gotPacket()</code> before it retrieves the next
   * packet from the pcap capture buffer.
   *
   * @param packetCount
   * @param listener
   * @throws PcapNativeException
   * @throws InterruptedException
   * @throws NotOpenException
   */
  public void loop(
    int packetCount, PacketListener listener
  ) throws PcapNativeException, InterruptedException, NotOpenException {
    loop(
      packetCount,
      listener,
      SimpleExecutor.getInstance()
    );
  }

  /**
   * A wrapper method for <code>int pcap_loop(pcap_t *, int, pcap_handler, u_char *)</code>.
   * When a packet is captured, the
   * {@link java.util.concurrent.Executor#execute(Runnable) executor.execute()} is called
   * with a Runnable object in the thread which called the <code>loop()</code>.
   * Then, the Runnable object calls <code>listener.gotPacket(Packet)</code>.
   * If <code>listener.gotPacket(Packet)</code> is expected to take a long time to
   * process a packet, this method should be used with a proper executor instead of
   * {@link #loop(int, PacketListener)} in order to prevent the pcap capture buffer from overflowing.
   *
   * @param packetCount
   * @param listener
   * @param executor
   * @throws PcapNativeException
   * @throws InterruptedException
   * @throws NotOpenException
   */
  public void loop(
    int packetCount, PacketListener listener, Executor executor
  ) throws PcapNativeException, InterruptedException, NotOpenException {
    if (listener == null || executor == null) {
      StringBuilder sb = new StringBuilder();
      sb.append("listener: ").append(listener)
        .append(" executor: ").append(executor);
      throw new NullPointerException(sb.toString());
    }
    if (!open) {
      throw new NotOpenException();
    }

    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }

      logger.info("Starting loop.");
      int rc = NativeMappings.pcap_loop(
                 handle,
                 packetCount,
                 new GotPacketFuncExecutor(listener, dlt, executor),
                 null
               );
      switch (rc) {
        case  0:
          logger.info("Finished loop.");
          break;
        case -1:
          throw new PcapNativeException(
                  "Error occured: " + getError(), rc
                );
        case -2:
          logger.info("Broken.");
          throw new InterruptedException();
        default:
          throw new PcapNativeException(
                  "Unexpected error occured: " + getError(), rc
                );
      }
    } finally {
      handleLock.readLock().unlock();
    }
  }

  /**
   *
   * @param packetCount
   * @param listener
   * @return the number of captured packets.
   * @throws PcapNativeException
   * @throws InterruptedException
   * @throws NotOpenException
   */
  public int dispatch(
    int packetCount, PacketListener listener
  ) throws PcapNativeException, InterruptedException, NotOpenException {
    return dispatch(
             packetCount,
             listener,
             SimpleExecutor.getInstance()
           );
  }

  /**
   *
   * @param packetCount
   * @param listener
   * @param executor
   * @return the number of captured packets.
   * @throws PcapNativeException
   * @throws InterruptedException
   * @throws NotOpenException
   */
  public int dispatch(
    int packetCount, PacketListener listener, Executor executor
  ) throws PcapNativeException, InterruptedException, NotOpenException {
    if (listener == null || executor == null) {
      StringBuilder sb = new StringBuilder();
      sb.append("listener: ").append(listener)
        .append(" executor: ").append(executor);
      throw new NullPointerException(sb.toString());
    }
    if (!open) {
      throw new NotOpenException();
    }

    int rc;
    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }

      logger.info("Starting dispatch.");
      rc = NativeMappings.pcap_dispatch(
             handle,
             packetCount,
             new GotPacketFuncExecutor(listener, dlt, executor),
             null
           );
      if (rc < 0) {
        switch (rc) {
          case -1:
            throw new PcapNativeException(
                    "Error occured: " + getError(),
                    rc
                  );
          case -2:
            logger.info("Broken.");
            throw new InterruptedException();
          default:
            throw new PcapNativeException(
                    "Unexpected error occured: " + getError(),
                    rc
                  );
        }
      }
    } finally {
      handleLock.readLock().unlock();
    }

    logger.info("Finish dispatch.");
    return rc;
  }

  /**
   *
   * @param filePath "-" means stdout.
   *        The dlt of the PcapHandle which captured the packets you want to dump
   *        must be the same as this dlt.
   * @return an opened PcapDumper.
   * @throws PcapNativeException
   * @throws NotOpenException
   */
  public PcapDumper dumpOpen(String filePath) throws PcapNativeException, NotOpenException {
    if (filePath == null) {
      throw new NullPointerException("filePath must not be null.");
    }
    if (!open) {
      throw new NotOpenException();
    }

    Pointer dumper;
    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }

      dumper = NativeMappings.pcap_dump_open(handle, filePath);
      if (dumper == null) {
        throw new PcapNativeException(getError());
      }
    } finally {
      handleLock.readLock().unlock();
    }

    return new PcapDumper(dumper);
  }

  /**
   *
   * @param packetCount
   * @param dumper
   * @throws PcapNativeException
   * @throws InterruptedException
   * @throws NotOpenException
   */
  public
  void loop(int packetCount, PcapDumper dumper)
  throws PcapNativeException, InterruptedException, NotOpenException {
    if (dumper == null) {
      throw new NullPointerException("dumper must not be null.");
    }
    if (!open) {
      throw new NotOpenException();
    }

    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }

      logger.info("Starting dump loop.");
      int rc = NativeMappings.pcap_loop(
                 handle,
                 packetCount,
                 NativeMappings.PCAP_DUMP,
                 dumper.getDumper()
               );

      switch (rc) {
        case  0:
          logger.info("Finished dump loop.");
          break;
        case -1:
          throw new PcapNativeException(
                  "Error occured: " + getError(), rc
                );
        case -2:
          logger.info("Broken.");
          throw new InterruptedException();
        default:
          throw new PcapNativeException(
                  "Unexpected error occured: " + getError(), rc
                );
      }
    } finally {
      handleLock.readLock().unlock();
    }
  }

  /**
   * Breaks a loop which this handle is working on.
   *
   * The loop may not be broken immediately on some OSes
   * because of buffering or something.
   * As a workaround, letting this capture some bogus packets
   * after calling this method may work.
   * @throws NotOpenException
   */
  public void breakLoop() throws NotOpenException {
    if (!open) {
      throw new NotOpenException();
    }

    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }

      logger.info("Break loop.");
      NativeMappings.pcap_breakloop(handle);
    } finally {
      handleLock.readLock().unlock();
    }
  }

  /**
   *
   * @param packet
   * @throws PcapNativeException
   * @throws NotOpenException
   * @throws NullPointerException
   */
  public void sendPacket(Packet packet) throws PcapNativeException, NotOpenException {
    if (packet == null) {
      throw new NullPointerException("packet may not be null");
    }
    if (!open) {
      throw new NotOpenException();
    }

    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }

      int rc = NativeMappings.pcap_sendpacket(
                 handle, packet.getRawData(), packet.length()
               );
      if (rc < 0) {
        throw new PcapNativeException(
                "Error occured in pcap_sendpacket(): " + getError(),
                rc
              );
      }
    } finally {
      handleLock.readLock().unlock();
    }
  }

  /**
   *
   * @return a {@link org.pcap4j.core.PcapStat PcapStat} object.
   * @throws PcapNativeException
   * @throws NotOpenException
   */
  public PcapStat getStats() throws PcapNativeException, NotOpenException {
    if (!open) {
      throw new NotOpenException();
    }

    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }

      if (Platform.isWindows()) {
        IntByReference pcapStatSize = new IntByReference();
        Pointer psp = PcapLibrary.INSTANCE.win_pcap_stats_ex(handle, pcapStatSize);

        if (pcapStatSize.getValue() != 24) {
          throw new PcapNativeException(getError());
        }
        if (psp == null) {
          throw new PcapNativeException(getError());
        }

        return new PcapStat(psp, true);
      }
      else {
        pcap_stat ps = new pcap_stat();
        ps.setAutoSynch(false);
        int rc = NativeMappings.pcap_stats(handle, ps);
        if (rc < 0) {
          throw new PcapNativeException(getError(), rc);
        }

        return new PcapStat(ps.getPointer(), false);
      }
    } finally {
      handleLock.readLock().unlock();
    }
  }

//  /**
//   *
//   * @return a {@link org.pcap4j.core.PcapStatEx PcapStatEx} object.
//   * @throws PcapNativeException
//   * @throws NotOpenException
//   */
//  public PcapStatEx getStatsEx() throws PcapNativeException, NotOpenException {
//    if (!Platform.isWindows()) {
//      throw new UnsupportedOperationException("This method is only for Windows.");
//    }
//
//    pcap_stat_ex ps = new pcap_stat_ex();
//    int rc = PcapLibrary.INSTANCE.dos_pcap_stats_ex(handle, ps);
//    if (rc < 0) {
//      throw new PcapNativeException(getError(), rc);
//    }
//
//    return new PcapStatEx(ps);
//  }

  /**
   * @return a list of {@link org.pcap4j.packet.namednumber.DataLinkType DataLinkType}
   * @throws PcapNativeException
   * @throws NotOpenException
   */
  public List<DataLinkType> listDatalinks()
  throws PcapNativeException, NotOpenException {
    if (!open) {
      throw new NotOpenException();
    }

    List<DataLinkType> list;
    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }

      PointerByReference dltBufPP = new PointerByReference();
      int rc = NativeMappings.pcap_list_datalinks(handle, dltBufPP);
      if (rc < 0) {
        throw new PcapNativeException(getError(), rc);
      }

      Pointer dltBufP = dltBufPP.getValue();
      list = new ArrayList<DataLinkType>(rc);
      for (int i: dltBufP.getIntArray(0, rc)) {
        list.add(DataLinkType.getInstance(i));
      }
      NativeMappings.pcap_free_datalinks(dltBufP);
    } finally {
      handleLock.readLock().unlock();
    }

    return list;
  }

  /**
   *
   * @return an error message.
   * @throws NotOpenException
   */
  public String getError() throws NotOpenException {
    if (!open) {
      throw new NotOpenException();
    }

    if (!handleLock.readLock().tryLock()) {
      throw new NotOpenException();
    }
    try {
      if (!open) {
        throw new NotOpenException();
      }
      return NativeMappings.pcap_geterr(handle).getString(0);
    } finally {
      handleLock.readLock().unlock();
    }
  }

  /**
   * Closes this PcapHandle.
   */
  public void close() {
    if (!open) {
      logger.warn("Already closed.");
      return;
    }

    handleLock.writeLock().lock();
    try {
      if (!open) {
        logger.warn("Already closed.");
        return;
      }
      open = false;
    } finally {
      handleLock.writeLock().unlock();
    }

    NativeMappings.pcap_close(handle);
    logger.info("Closed.");
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(60);

    sb.append("Link type: [").append(dlt)
      .append("] handle: [").append(handle)
      .append("] Open: [").append(open)
      .append("] Filtering Expression: [").append(filteringExpression)
      .append("]");

    return sb.toString();
  }

  private static final class SimpleExecutor implements Executor {

    private SimpleExecutor() {}

    private static final SimpleExecutor INSTANCE = new SimpleExecutor();

    public static SimpleExecutor getInstance() { return INSTANCE; }

    @Override
    public void execute(Runnable command) {
      command.run();
    }

  }

  private final class GotPacketFuncExecutor implements NativeMappings.pcap_handler {

    private final DataLinkType dlt;
    private final PacketListener listener;
    private final Executor executor;

    public GotPacketFuncExecutor(
      PacketListener listener, DataLinkType dlt, Executor executor
    ) {
      this.dlt = dlt;
      this.listener = listener;
      this.executor = executor;
    }

    @Override
    public void got_packet(
      Pointer args, Pointer header, final Pointer packet
    ) {
      final long tvs = pcap_pkthdr.getTvSec(header).longValue();
      final int tvus = pcap_pkthdr.getTvUsec(header).intValue();
      final byte[] ba = packet.getByteArray(0, pcap_pkthdr.getCaplen(header));

      executor.execute(
        new Runnable() {
          @Override
          public void run() {
            timestampsInts.set(tvs);
            timestampsMicros.set(tvus);
            try {
                listener.gotPacket(
                  PacketFactories.getFactory(Packet.class, DataLinkType.class)
                    .newInstance(ba, 0, ba.length, dlt)
                );
            } catch (IllegalRawDataException e) {
            }
          }
        }
      );
    }

  }

  /**
   * @author Kaito Yamada
   * @since pcap4j 1.2.0
   */
  public static final class Builder {

    private final String deviceName;
    private int snaplen;
    private boolean isSnaplenSet = false;
    private PromiscuousMode promiscuousMode = null;
    private boolean rfmon;
    private boolean isRfmonSet = false;
    private int timeoutMillis;
    private boolean isTimeoutMillisSet = false;
    private int bufferSize;
    private boolean isBufferSizeSet = false;

    /**
     *
     * @param deviceName A value {@link PcapNetworkInterface#getName()} returns.
     */
    public Builder(String deviceName) {
      if (deviceName == null || deviceName.length() == 0) {
        throw new IllegalArgumentException("deviceName: " + deviceName);
      }
      this.deviceName = deviceName;
    }

    /**
     * @param snaplen Snapshot length, which is the number of bytes captured for each packet.
     *                If this method isn't called, the platform's default snaplen will be applied
     *                at {@link #build()}.
     * @return this Builder object for method chaining.
     */
    public Builder snaplen(int snaplen) {
      this.snaplen = snaplen;
      this.isSnaplenSet = true;
      return this;
    }

    /**
     * @param promiscuousMode Promiscuous mode.
     *                        If this method isn't called,
     *                        the platform's default mode will be used
     *                        at {@link #build()}.
     * @return this Builder object for method chaining.
     */
    public Builder promiscuousMode(PromiscuousMode promiscuousMode) {
      this.promiscuousMode = promiscuousMode;
      return this;
    }

    /**
     * @param rfmon Whether monitor mode should be set on a PcapHandle
     *              when it is built. If true, monitor mode will be set,
     *              otherwise it will not be set.
     *              Some platforms don't support setting monitor mode.
     *              Calling this method on such platforms may cause PcapNativeException
     *              at {@link #build()}.
     *              If this method isn't called, the platform's default mode will be applied
     *              at {@link #build()} (if supported).
     * @return this Builder object for method chaining.
     */
    public Builder rfmon(boolean rfmon) {
      this.rfmon = rfmon;
      this.isRfmonSet = true;
      return this;
    }

    /**
     * @param timeoutMillis Read timeout. Most OSs buffer packets.
     *                      The OSs pass the packets to Pcap4j after the buffer gets full
     *                      or the read timeout expires.
     *                      Must be non-negative. May be ignored by some OSs.
     *                      0 means disable buffering on Solaris.
     *                      0 means infinite on the other OSs.
     *                      1 through 9 means infinite on Solaris.
     *                      If this method isn't called, the platform's default timeout will be applied
     *                      at {@link #build()}.
     * @return this Builder object for method chaining.
     */
    public Builder timeoutMillis(int timeoutMillis) {
      this.timeoutMillis = timeoutMillis;
      this.isTimeoutMillisSet = true;
      return this;
    }

    /**
     * @param bufferSize The buffer size, which is in units of bytes.
     *                   If this method isn't called,
     *                   the platform's default buffer size will be applied
     *                   at {@link #build()}.
     * @return this Builder object for method chaining.
     */
    public Builder bufferSize(int bufferSize) {
      this.bufferSize = bufferSize;
      this.isBufferSizeSet = true;
      return this;
    }

    /**
     * @return a new PcapHandle object.
     * @throws PcapNativeException
     */
    public PcapHandle build() throws PcapNativeException {
      return new PcapHandle(this);
    }

  }

  /**
   *
   * @author Kaito Yamada
   * @version pcap4j 0.9.16
   */
  public static enum SwappedType {

    /**
     *
     */
    NOT_SWAPPED(0),

    /**
     *
     */
    SWAPPED(1),

    /**
     *
     */
    MAYBE_SWAPPED(2);

    private final int value;

    private SwappedType(int value) {
      this.value = value;
    }

    /**
     *
     * @return value
     */
    public int getValue() {
      return value;
    }
  }

  /**
   *
   * @author Kaito Yamada
   * @version pcap4j 0.9.15
   */
  public static enum BlockingMode {

    /**
     *
     */
    BLOCKING(0),

    /**
     *
     */
    NONBLOCKING(1);

    private final int value;

    private BlockingMode(int value) {
      this.value = value;
    }

    /**
     *
     * @return value
     */
    public int getValue() {
      return value;
    }
  }

}
