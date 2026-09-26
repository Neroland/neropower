package za.co.neroland.neropower.beam;

import java.util.Locale;

/**
 * What a beam endpoint is doing right now — one word for the GUI status line. Transmitters and
 * relays report the sending states; receivers report {@link #RECEIVING} / {@link #IDLE}. The
 * ordinal rides the menu's synced {@code ContainerData}, so the order is wire format — append only.
 */
public enum BeamStatus {

    /** Nothing to do: transmission disabled, or no energy to send / none arriving. */
    IDLE,
    /** No target linked (transmitter / relay). */
    UNLINKED,
    /** Energy moved to the target this pass. */
    TRANSMITTING,
    /** Something solid sits in the beam — no transfer. */
    BLOCKED,
    /** The target is further than {@code beamRange}. */
    OUT_OF_RANGE,
    /** The target block is gone or is not a beam endpoint any more (the link was dropped). */
    TARGET_MISSING,
    /** The target's chunk is not loaded — pass skipped, never force-loaded. */
    TARGET_ASLEEP,
    /** The target's buffer accepted nothing. */
    TARGET_FULL,
    /** Cross-dimension beam refused: {@code beamCrossDimension} is off or the target is not orbital. */
    DENIED,
    /** A relay chain came back on itself, or exceeded {@code beamMaxHops}. */
    LOOP,
    /** Energy arrived by beam this pass (receiver / relay). */
    RECEIVING;

    /** Stable view for ordinal decoding — never call {@code values()} per frame. */
    public static final BeamStatus[] VALUES = values();

    /** Decode a synced ordinal, clamping unknown values to {@link #IDLE}. */
    public static BeamStatus byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : IDLE;
    }

    /** {@code neropower.beam.status.<lowercase name>} (see lang parts). */
    public String translationKey() {
        return "neropower.beam.status." + name().toLowerCase(Locale.ROOT);
    }
}
