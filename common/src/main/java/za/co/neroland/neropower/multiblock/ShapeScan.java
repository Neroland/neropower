package za.co.neroland.neropower.multiblock;

/**
 * Shared limits of every NeroPower multiblock scan. A scan touches at most {@link #MAX_POSITIONS}
 * positions and never loads a chunk; validation runs on change (see {@link MultiblockIndex}), never on a
 * periodic cadence.
 */
public final class ShapeScan {

    /** Hard cap on the positions one evaluation may read (a 5x5x16 turbine is exactly this). */
    public static final int MAX_POSITIONS = 400;

    private ShapeScan() {
    }

    /** Whether a box is small enough to scan. */
    public static boolean withinBudget(Bounds bounds) {
        return bounds.volume() <= MAX_POSITIONS;
    }
}
