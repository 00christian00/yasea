package net.ossrs.sea.rtmp.io;

import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import android.util.Log;
import net.ossrs.sea.rtmp.packets.Command;
import net.ossrs.sea.rtmp.packets.RtmpPacket;
import net.ossrs.sea.rtmp.packets.Video;

/**
 * RTMPConnection's write thread
 * 
 * @author francois, leo
 */
public class WriteThread extends Thread {

    private static final String TAG = "WriteThread";

    private RtmpSessionInfo rtmpSessionInfo;
    private OutputStream out;
    private ConcurrentLinkedQueue<RtmpPacket> writeQueue = new ConcurrentLinkedQueue<RtmpPacket>();
    private final Object txPacketLock = new Object();
    private volatile boolean active = true;
    private AtomicInteger videoFrameCacheNumber;

    public WriteThread(RtmpSessionInfo rtmpSessionInfo, OutputStream out, AtomicInteger count) {
        super("RtmpWriteThread");
        this.rtmpSessionInfo = rtmpSessionInfo;
        this.out = out;
        this.videoFrameCacheNumber = count;
    }

    @Override
    public void run() {

        while (active) {
            try {
                while (!writeQueue.isEmpty()) {
                    RtmpPacket rtmpPacket = writeQueue.poll();
                    ChunkStreamInfo chunkStreamInfo = rtmpSessionInfo.getChunkStreamInfo(rtmpPacket.getHeader().getChunkStreamId());
                    chunkStreamInfo.setPrevHeaderTx(rtmpPacket.getHeader());
                    rtmpPacket.writeTo(out, rtmpSessionInfo.getTxChunkSize(), chunkStreamInfo);
                    Log.d(TAG, "WriteThread: wrote packet: " + rtmpPacket + ", size: " + rtmpPacket.getHeader().getPacketLength());
                    if (rtmpPacket instanceof Command) {
                        rtmpSessionInfo.addInvokedCommand(((Command) rtmpPacket).getTransactionId(), ((Command) rtmpPacket).getCommandName());
                    }
                    if (rtmpPacket instanceof Video) {
                        videoFrameCacheNumber.getAndDecrement();
                    }
                }
                out.flush();
            } catch (SocketException se) {
                Log.e(TAG, "WriteThread: Caught SocketException during write loop, shutting down", se);
                Thread.getDefaultUncaughtExceptionHandler().uncaughtException(this, se);
                active = false;
                continue;
            } catch (IOException ioe) {
                Log.e(TAG, "WriteThread: Caught IOException during write loop, shutting down", ioe);
                Thread.getDefaultUncaughtExceptionHandler().uncaughtException(this, ioe);
                active = false;
                continue;
            }

            // Waiting for next packet
            Log.d(TAG, "WriteThread: waiting...");
            synchronized (txPacketLock) {
                try {
                    // isEmpty() may take some time, so time out should be set to wait next offer
                    txPacketLock.wait(500);
                } catch (InterruptedException ex) {
                    Log.w(TAG, "Interrupted", ex);
                    this.interrupt();
                }
            }
        }

        Log.d(TAG, "exit");
    }

    /** Transmit the specified RTMP packet (thread-safe) */
    public void send(RtmpPacket rtmpPacket) {
        if (rtmpPacket != null) {
            writeQueue.add(rtmpPacket);
        }
        synchronized (txPacketLock) {
            txPacketLock.notify();
        }
    }

    public void shutdown() {
        Log.d(TAG, "Stopping");
        writeQueue.clear();
        active = false;
        synchronized (txPacketLock) {
            txPacketLock.notify();
        }
    }
}
