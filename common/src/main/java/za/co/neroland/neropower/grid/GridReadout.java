package za.co.neroland.neropower.grid;

/**
 * The contract's {@code readout} (0..5) and {@code stallCause} (0..15, screen gauge-kit table) codes the
 * grid and new beam blocks use.
 */
public final class GridReadout {

    public static final int REST = 0;
    public static final int WORK = 1;
    public static final int STALL = 2;
    public static final int HOT = 3;
    public static final int TRIP = 4;
    public static final int FAIL = 5;

    public static final int CAUSE_NONE = 0;
    public static final int CAUSE_NO_POWER = 9;
    public static final int CAUSE_NO_LENS = 10;
    public static final int CAUSE_NO_LINK = 11;
    public static final int CAUSE_BEAM_BLOCKED = 12;
    public static final int CAUSE_NOTHING_TO_DO = 14;

    private GridReadout() {
    }

    public static int clampReadout(int code) {
        return Math.max(REST, Math.min(FAIL, code));
    }

    public static int clampCause(int cause) {
        return Math.max(0, Math.min(15, cause));
    }
}
