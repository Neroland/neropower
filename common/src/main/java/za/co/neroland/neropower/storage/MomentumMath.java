package za.co.neroland.neropower.storage;

/**
 * The Momentum Wheel's rules, Minecraft-free: bearing friction takes
 * {@code lossPermillePerSecond} of the stored energy every second (at least 1 NE while any is stored and
 * the loss is on, so a wheel always spins down to rest), and the renderer reads speed as a 0..20 bucket of
 * the fill. Energy only ever leaves through the loss or an extract; nothing here adds any.
 */
public final class MomentumMath {

    /** Ticks between friction steps. */
    public static final int LOSS_INTERVAL = 20;
    /** Charge buckets the renderer sees (rim speed): 0 (still) .. {@value} (full speed). */
    public static final int CHARGE_BUCKETS = 20;

    private MomentumMath() {
    }

    /** NE lost to friction in one second; 0 with nothing stored or the loss off; never more than stored. */
    public static long lossPerSecond(long stored, int lossPermillePerSecond) {
        if (stored <= 0 || lossPermillePerSecond <= 0) {
            return 0L;
        }
        long loss = Math.max(1L, stored * lossPermillePerSecond / 1000L);
        return Math.min(stored, loss);
    }

    /** Fill in permille (0..1000). */
    public static int permille(long stored, long capacity) {
        if (stored <= 0 || capacity <= 0) {
            return 0;
        }
        return (int) Math.min(1000L, stored * 1000L / capacity);
    }

    /** The synced charge bucket (contract field chargeBucket): fill permille / 50, so 0..{@value #CHARGE_BUCKETS}. */
    public static int chargeBucket(long stored, long capacity) {
        return Math.min(CHARGE_BUCKETS, permille(stored, capacity) / 50);
    }
}
