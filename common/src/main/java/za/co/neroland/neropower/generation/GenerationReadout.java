package za.co.neroland.neropower.generation;

/**
 * The synced {@code readout} (0–5) and {@code stallCause} (0–15) codes the small generators report
 * (block-contract render inputs; meanings from the shared screen gauge-kit table). No Minecraft types.
 */
public final class GenerationReadout {

    public static final int REST = 0;
    public static final int WORK = 1;
    public static final int STALL = 2;

    public static final int CAUSE_NONE = 0;
    /** Fuel cell: no hydrogen. Betavoltaic: no capsule, or the capsule is spent. */
    public static final int CAUSE_NO_FUEL = 2;
    /** Fuel cell: water tank full. */
    public static final int CAUSE_OUTPUT_FULL = 7;
    /** Energy buffer full. */
    public static final int CAUSE_BUFFER_FULL = 8;
    /** Fuel cell: no oxygen in the tank and no breathable air here (screen rewords "no coolant"). */
    public static final int CAUSE_NO_OXYGEN = 13;

    /** Update-tag keys, shared with the other 0.4.0 packages. */
    public static final String KEY_READOUT = "NpReadout";
    public static final String KEY_STALL = "NpStall";

    private GenerationReadout() {
    }

    /** Readout from a tick's result: working beats stall beats rest. */
    public static int readout(boolean working, int cause) {
        if (working) {
            return WORK;
        }
        return cause != CAUSE_NONE ? STALL : REST;
    }
}
