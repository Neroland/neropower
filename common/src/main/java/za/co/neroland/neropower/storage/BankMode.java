package za.co.neroland.neropower.storage;

/**
 * How a Battery Bank Controller discharges its pool into the world.
 * <ul>
 *   <li>{@link #BUFFER} (default) — a plain reservoir: every tick it pushes into any neighbour that
 *       will take power, like NeroTech's Battery Bank.</li>
 *   <li>{@link #PRIORITY_SOURCE} — a backup: it pushes into a neighbour only while that neighbour's
 *       own buffer is below {@code bankPriorityThresholdPermille}, so generators feeding the same
 *       machines drain first and the bank tops up shortfalls.</li>
 * </ul>
 * Cycled by sneak-right-clicking the controller with an empty hand; persisted as the ordinal.
 */
public enum BankMode {

    BUFFER("buffer"),
    PRIORITY_SOURCE("priority_source");

    private final String key;

    BankMode(String key) {
        this.key = key;
    }

    /** Translation key: {@code neropower.bank_mode.<key>}. */
    public String translationKey() {
        return "neropower.bank_mode." + this.key;
    }

    public BankMode next() {
        BankMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** Ordinal lookup that tolerates a bad save / packet (falls back to {@link #BUFFER}). */
    public static BankMode byOrdinal(int ordinal) {
        BankMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : BUFFER;
    }
}
