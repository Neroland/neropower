package za.co.neroland.neropower.backend;

/**
 * Pure arithmetic of the Dry Cask and the Vitrifier (no Minecraft types, unit-tested).
 */
public final class CaskMath {

    /** Slots in one Dry Cask. */
    public static final int CASK_SLOTS = 27;

    /** Sand consumed per vitrification. */
    public static final int SAND_PER_OP = 2;

    private CaskMath() {
    }

    /**
     * Trace heat a cask adds once per {@code caskHeatIntervalTicks}: 1 for every {@code slotsPerHeat}
     * slots holding radioactive waste (spent rods, fission residue). Vitrified waste is inert and is
     * not counted.
     */
    public static int traceHeat(int radioactiveSlots, int slotsPerHeat) {
        return Math.max(0, radioactiveSlots) / Math.max(1, slotsPerHeat);
    }

    /** Whether a cask emits radiation (it holds at least one spent rod or residue). */
    public static boolean emitting(int radioactiveSlots) {
        return radioactiveSlots > 0;
    }

    /** Total NE one vitrification costs. */
    public static long vitrifyCost(int nePerTick, int ticksPerOp) {
        return (long) Math.max(0, nePerTick) * Math.max(0, ticksPerOp);
    }

    /**
     * Whether the Vitrifier may start or continue an operation: one waste item, at least
     * {@link #SAND_PER_OP} sand, and room for one more Vitrified Waste in the output.
     */
    public static boolean canVitrify(int waste, int sand, int outputCount, int outputMax) {
        return waste >= 1 && sand >= SAND_PER_OP && outputCount < outputMax;
    }
}
