package za.co.neroland.neropower.turbine;

import za.co.neroland.nerolandcore.sideconfig.SideMode;

/**
 * The pure rules of the pipe-less fluid movement: when a port, or the Steam Condenser, hands
 * fluid to the block it touches and which faces may do it. Pure JVM (Core's {@link SideMode} only).
 *
 * <ul>
 *   <li>The exchange runs every {@code portTransferInterval} ticks (spread by a per-block phase), never
 *       per tick.</li>
 *   <li>A face pushes when the player set it to PUSH, or when it lets fluid out (OUTPUT / I/O) and the
 *       block's FLUID auto-eject toggle is on (the default preset is I/O with auto-eject on).</li>
 *   <li>A face pulls when it takes input automatically (INPUT / I/O) and auto-input is on.</li>
 *   <li><b>Guarded pushes.</b> Steam and exhaust steam are pushed on an automatic face only into a
 *       neighbour that would not also take water (a turbine inlet, a condenser), so a reactor port or an
 *       exhaust port never fills an empty relay tank that a condenser is feeding water through. A PUSH face
 *       always pushes.</li>
 * </ul>
 */
public final class PortTransfer {

    /** Default and bounds of {@code portTransferInterval} (ticks). */
    public static final int DEFAULT_INTERVAL = 10;
    public static final int MIN_INTERVAL = 1;
    public static final int MAX_INTERVAL = 200;

    /** mB per face per tick of interval: one exchange moves at most {@code interval x} this. */
    public static final long FACE_MB_PER_TICK = 2_000L;

    private PortTransfer() {
    }

    /** {@code raw} clamped to the interval bounds. */
    public static int clampInterval(int raw) {
        return Math.max(MIN_INTERVAL, Math.min(MAX_INTERVAL, raw));
    }

    /** A stable phase in {@code [0, interval)} from any per-block seed. */
    public static int phase(int seed, int interval) {
        return Math.floorMod(seed * 31, clampInterval(interval));
    }

    /** Whether an exchange is due this tick. */
    public static boolean due(long gameTime, int phase, int interval) {
        return Math.floorMod(gameTime + phase, (long) clampInterval(interval)) == 0L;
    }

    /** mB one face may move in one exchange. */
    public static long budget(int interval) {
        return clampInterval(interval) * FACE_MB_PER_TICK;
    }

    /**
     * Whether a face pushes.
     *
     * @param guarded            this push is steam / exhaust steam (guarded, see the class note)
     * @param neighbourTakesWater the neighbour would accept water through this face (simulated)
     */
    public static boolean pushes(SideMode mode, boolean autoEject, boolean guarded, boolean neighbourTakesWater) {
        if (mode == null) {
            return false;
        }
        if (mode == SideMode.PUSH) {
            return true;
        }
        if (!autoEject || !mode.autoEjects()) {
            return false;
        }
        return !guarded || !neighbourTakesWater;
    }

    /** Whether a face pulls. */
    public static boolean pulls(SideMode mode, boolean autoInput) {
        return mode != null && autoInput && mode.autoInputs();
    }
}
