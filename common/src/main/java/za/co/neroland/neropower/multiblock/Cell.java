package za.co.neroland.neropower.multiblock;

/**
 * What a multiblock shape sees at one position. A {@link Probe} maps block states to cells, so the
 * shapes themselves are pure and testable without a world. Names follow the registry ids.
 */
public enum Cell {
    /** Not loaded: a shape never loads a chunk, it reports unformed instead. */
    UNLOADED,
    AIR,
    /** Anything a shape does not know. */
    OTHER,
    // --- fission ---
    FISSION_CASING,
    FISSION_CORE,
    CONTROL_ROD,
    EUROPIUM_CONTROL_ROD,
    GRAPHITE_MODERATOR,
    MODERATOR_TANK,
    BREEDER_BLANKET,
    REACTOR_STEAM_PORT,
    REACTOR_CONTROL_COMPUTER,
    // --- turbine ---
    TURBINE_HELM,
    TURBINE_SHROUD,
    TURBINE_SPINDLE,
    TURBINE_STATOR,
    TURBINE_STEAM_PORT,
    // --- storage ring ---
    SMES_CONTROLLER,
    SMES_COIL;

    /** Whether this cell may sit inside a fission shell. */
    public boolean reactorInterior() {
        return switch (this) {
            case CONTROL_ROD, EUROPIUM_CONTROL_ROD, GRAPHITE_MODERATOR, MODERATOR_TANK, BREEDER_BLANKET -> true;
            default -> false;
        };
    }
}
