package za.co.neroland.neropower.turbine;

/**
 * The contract's synced {@code readout} codes (0-5) and the update-tag keys the turbine family writes
 * them under. Render inputs only; never saved to disk.
 */
public final class TurbineReadout {

    public static final int REST = 0;
    public static final int WORK = 1;
    public static final int STALL = 2;
    public static final int HOT = 3;
    public static final int TRIP = 4;
    public static final int FAIL = 5;

    /** Update-tag keys (render extras). */
    public static final String KEY_READOUT = "NpReadout";
    public static final String KEY_STALL = "NpStall";

    private TurbineReadout() {
    }
}
