package net.ossrs.sea.rtmp.io;

import java.io.EOFException;
import java.io.InputStream;
import android.util.Log;
import net.ossrs.sea.rtmp.packets.RtmpPacket;

/**
 * RTMPConnection's read thread
 * 
 * @author francois, leo
 */
public class ReadThread extends Thread {

    private static final String TAG = "ReadThread";

    private RtmpDecoder rtmpDecoder;
    private InputStream in;
    private PacketRxHandler packetRxHandler;
    private ThreadController threadController;

    public ReadThread(RtmpSessionInfo rtmpSessionInfo, InputStream in, PacketRxHandler packetRxHandler, ThreadController threadController) {
        super("RtmpReadThread");
        this.in = in;
        this.packetRxHandler = packetRxHandler;
        this.rtmpDecoder = new RtmpDecoder(rtmpSessionInfo);
        this.threadController = threadController;
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {
                RtmpPacket rtmpPacket = rtmpDecoder.readPacket(in);
                packetRxHandler.handleRxPacket(rtmpPacket);
            } catch (EOFException eof) {
                // The handler thread will wait until be invoked.
                packetRxHandler.handleRxPacket(null);
//            } catch (WindowAckRequired war) {
//                Log.i(TAG, "Window Acknowledgment required, notifying packet handler...");
//                packetRxHandler.notifyWindowAckRequired(war.getBytesRead());
//                if (war.getRtmpPacket() != null) {
//                    // Pass to handler
//                    packetRxHandler.handleRxPacket(war.getRtmpPacket());
//                }
            } catch (Exception ex) {
                if (!this.isInterrupted()) {
                    Log.e(TAG, "Caught exception while reading/decoding packet, shutting down...", ex);
                    this.interrupt();
                }
            }
        }
        // Close inputstream
        try {
            in.close();
        } catch (Exception ex) {
            Log.w(TAG, "Failed to close inputstream", ex);
        }
        Log.i(TAG, "exiting");
        if (threadController != null) {
            threadController.threadHasExited(this);
        }
    }

    public void shutdown() {
        Log.d(TAG, "Stopping read thread...");
        this.interrupt();
    }
}
