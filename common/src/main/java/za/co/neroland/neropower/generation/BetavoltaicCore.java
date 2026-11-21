package za.co.neroland.neropower.generation;

/**
 * The Betavoltaic Cell's rules, free of Minecraft types so they are unit-tested. A live Beta Capsule
 * gives a flat {@code nePerTick} for {@code capsuleTicks} ticks of output, then turns into a Spent Beta
 * Capsule in its slot (nothing is voided). The capsule only ages on ticks it actually pays out, so a
 * full buffer pauses it rather than wasting it; without a live capsule there is no output.
 */
public final class BetavoltaicCore {

    public static final int STATE_NONE = 0;
    public static final int STATE_LIVE = 1;
    public static final int STATE_SPENT = 2;

    /** Ticks of output the current capsule has given. */
    private long used;

    /** What one tick asks the block entity to do. */
    public record Step(int generate, boolean spendCapsule, boolean working, int cause) {
    }

    /**
     * One tick.
     *
     * @param state      what the slot holds ({@link #STATE_NONE} / {@link #STATE_LIVE} / {@link #STATE_SPENT})
     * @param bufferRoom free NE in the energy buffer
     */
    public Step tick(int state, long bufferRoom, int nePerTick, int capsuleTicks) {
        if (state != STATE_LIVE) {
            this.used = 0L;
            return new Step(0, false, false, GenerationReadout.CAUSE_NO_FUEL);
        }
        long life = Math.max(1, capsuleTicks);
        if (this.used >= life) {
            this.used = 0L;
            return new Step(0, true, false, GenerationReadout.CAUSE_NO_FUEL);
        }
        int rate = Math.max(0, nePerTick);
        if (bufferRoom < rate || bufferRoom <= 0L) {
            return new Step(0, false, false, GenerationReadout.CAUSE_BUFFER_FULL);
        }
        this.used++;
        boolean spent = this.used >= life;
        if (spent) {
            this.used = 0L;
        }
        return new Step(rate, spent, true, GenerationReadout.CAUSE_NONE);
    }

    /** Ticks of output the live capsule has given. */
    public long used() {
        return this.used;
    }

    /** Ticks of output left in a capsule of {@code capsuleTicks} life. */
    public long left(int capsuleTicks) {
        return Math.max(0L, capsuleTicks - this.used);
    }

    /** A fresh capsule (or a changed slot) starts its clock again. */
    public void reset() {
        this.used = 0L;
    }

    public void load(long used) {
        this.used = Math.max(0L, used);
    }
}
