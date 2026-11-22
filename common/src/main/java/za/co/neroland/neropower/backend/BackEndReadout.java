package za.co.neroland.neropower.backend;

/**
 * The synced {@code readout} (0–5) and {@code stallCause} (0–15) codes the back-end blocks report
 * (the frozen render inputs; meanings from the shared screen gauge kit), and the pure rules that
 * pick them. No Minecraft types, unit-tested.
 */
public final class BackEndReadout {

    // readout
    public static final int REST = 0;
    public static final int WORK = 1;
    public static final int STALL = 2;
    public static final int HOT = 3;
    public static final int TRIP = 4;
    public static final int FAIL = 5;

    // stallCause (gauge kit table)
    public static final int CAUSE_NONE = 0;
    public static final int CAUSE_NO_WATER = 4;
    public static final int CAUSE_OUTPUT_FULL = 7;
    public static final int CAUSE_NO_POWER = 9;
    public static final int CAUSE_NOTHING_TO_DO = 14;

    /** Lang suffixes for {@code gui.neropower.stall.<key>}, by code (0..15). */
    private static final String[] CAUSE_KEYS = {
        "none", "not_formed", "no_fuel", "no_steam", "no_water", "exhaust_blocked", "no_heat_sink",
        "output_full", "buffer_full", "no_power", "no_lens", "no_link", "beam_blocked", "no_coolant",
        "nothing_to_do", "locked_out"};

    private BackEndReadout() {
    }

    /** The {@code gui.neropower.stall.<key>} suffix for a cause code (unknown codes read as {@code none}). */
    public static String causeKey(int cause) {
        return cause >= 0 && cause < CAUSE_KEYS.length ? CAUSE_KEYS[cause] : CAUSE_KEYS[0];
    }

    /**
     * Readout from the state, worst first: fail, hot, stall (any cause), work, rest.
     *
     * @param failing a failure ladder at FAILURE
     * @param hot     WARNING / UNSTABLE, or over the throttle threshold
     * @param cause   the stall cause (0 none)
     * @param working whether the block is doing its job this tick
     */
    public static int readout(boolean failing, boolean hot, int cause, boolean working) {
        if (failing) {
            return FAIL;
        }
        if (hot) {
            return HOT;
        }
        if (cause != CAUSE_NONE) {
            return STALL;
        }
        return working ? WORK : REST;
    }

    /** Spent Fuel Rack stall cause: hot rods with no water contact. */
    public static int poolCause(int hotRods, int contacts) {
        return hotRods > 0 && contacts <= 0 ? CAUSE_NO_WATER : CAUSE_NONE;
    }

    /**
     * Vitrifier stall cause. Both input slots empty is rest (no cause); a half-loaded machine has
     * nothing to do; a full output, then a lack of power, stall a loaded machine.
     */
    public static int vitrifierCause(int waste, int sand, boolean outputFull, boolean powered) {
        if (waste <= 0 && sand <= 0) {
            return CAUSE_NONE;
        }
        if (waste < 1 || sand < CaskMath.SAND_PER_OP) {
            return CAUSE_NOTHING_TO_DO;
        }
        if (outputFull) {
            return CAUSE_OUTPUT_FULL;
        }
        return powered ? CAUSE_NONE : CAUSE_NO_POWER;
    }
}
