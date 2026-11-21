package za.co.neroland.neropower.fission;

/**
 * How a fission core turns heat into something useful. {@link #DIRECT} (0.3.x behaviour) makes NE
 * itself; {@link #STEAM} makes no NE and boils water at its Reactor Steam Ports instead. The operator
 * asks for a mode in the core screen; the effective mode is steam only while at least one Reactor
 * Steam Port sits in the shell, so breaking the last port falls back to direct rather than trapping
 * the heat. Saved as the string {@code Mode} ({@code direct} / {@code steam}; absent = direct).
 */
public enum ReactorMode {
    DIRECT("direct"),
    STEAM("steam");

    private final String id;

    ReactorMode(String id) {
        this.id = id;
    }

    /** The saved string. */
    public String id() {
        return this.id;
    }

    /** The synced render code (0 direct, 1 steam). */
    public int code() {
        return ordinal();
    }

    /** Parse a saved string; anything unknown (or absent) reads as {@link #DIRECT}. */
    public static ReactorMode byId(String id) {
        return "steam".equals(id) ? STEAM : DIRECT;
    }

    /** The mode for a synced code. */
    public static ReactorMode byCode(int code) {
        return code == 1 ? STEAM : DIRECT;
    }

    /** The mode the core actually runs in, given what was asked and the steam ports in its shell. */
    public static ReactorMode effective(ReactorMode requested, int steamPorts) {
        return requested == STEAM && steamPorts > 0 ? STEAM : DIRECT;
    }

    /** Whether the operator may switch to {@code target} now: steam needs a port, direct is always allowed. */
    public static boolean canRequest(ReactorMode target, int steamPorts) {
        return target == DIRECT || steamPorts > 0;
    }

    public String translationKey() {
        return "gui.neropower.fission_core.mode." + this.id;
    }
}
