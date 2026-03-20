package net.skds.wpo.environmental.network;

/**
 * Shared data holder for the Environmental debug overlay.
 * Lives in the main source set so both server (packet sending) and client (packet receiving) can access it.
 * The client-side EnvClientEvents reads from this and passes to EnvDebugOverlay.
 */
public class EnvDebugData {

    private static EnvDebugPacket lastPacket = null;
    private static long lastReceiveTime = 0;

    public static void receive(EnvDebugPacket packet) {
        lastPacket = packet;
        lastReceiveTime = System.currentTimeMillis();
    }

    public static EnvDebugPacket getPacket() {
        return lastPacket;
    }

    public static boolean hasData() {
        return lastPacket != null;
    }

    public static long getLastReceiveTime() {
        return lastReceiveTime;
    }
}
