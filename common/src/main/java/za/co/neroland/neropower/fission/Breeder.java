package za.co.neroland.neropower.fission;

/**
 * The breeder's clock, pure so the rule is testable: while the core runs (either mode, not stalled,
 * not SCRAMmed) and the blanket input holds a Pallurite Pellet with room in the output, progress gains
 * {@link FissionMath#breedStep} milli-ticks per tick; every {@code ticksPerPellet x 1000} one pellet is
 * bred. Nothing accumulates while the core is idle or there is nothing to breed, so a pellet never
 * appears "for free" when an input is finally loaded. Saved as {@code BreedProgress} (milli-ticks).
 */
public final class Breeder {

    /** Render buckets of the synced {@code breedStep} (0..4). */
    public static final int STEPS = 5;

    private int progress;

    /** Saved progress, milli-ticks. */
    public int progress() {
        return this.progress;
    }

    /** Restore saved progress (clamped to non-negative). */
    public void load(int saved) {
        this.progress = Math.max(0, saved);
    }

    /**
     * One tick.
     *
     * @param running        the core ran this tick (fuel, not stalled, not SCRAMmed)
     * @param canBreed       an input pellet is loaded and the output slot has room
     * @param step           {@link FissionMath#breedStep} for this tick
     * @param ticksPerPellet {@code breederTicksPerPellet}
     * @return true when a pellet was bred this tick (the caller moves the items)
     */
    public boolean tick(boolean running, boolean canBreed, int step, int ticksPerPellet) {
        if (!running || !canBreed || step <= 0) {
            return false;
        }
        long threshold = threshold(ticksPerPellet);
        long next = Math.min(threshold, (long) this.progress + step);
        if (next >= threshold) {
            this.progress = 0;
            return true;
        }
        this.progress = (int) next;
        return false;
    }

    /** The render bucket 0..4 of the current progress. */
    public int step(int ticksPerPellet) {
        long threshold = threshold(ticksPerPellet);
        return (int) Math.max(0L, Math.min(STEPS - 1L, this.progress * (long) STEPS / threshold));
    }

    /** Progress as permille of one pellet. */
    public int permille(int ticksPerPellet) {
        long threshold = threshold(ticksPerPellet);
        return (int) Math.max(0L, Math.min(1000L, this.progress * 1000L / threshold));
    }

    private static long threshold(int ticksPerPellet) {
        return Math.max(1L, (long) Math.max(1, ticksPerPellet) * 1000L);
    }
}
