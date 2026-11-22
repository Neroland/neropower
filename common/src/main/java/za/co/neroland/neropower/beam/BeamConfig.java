package za.co.neroland.neropower.beam;

import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.neropower.config.NeroPowerConfig;

/**
 * Beamed-power balance keys (Stage 6), declared on NeroPower's shared {@code neropower} schema.
 * {@code NeroPowerCommon.init()} must class-load this ({@link #init()}) <b>before</b>
 * {@code NeroPowerConfig.load()} so the keys are part of the schema Core registers. All
 * server-authoritative — a connected client uses the server's values.
 */
public final class BeamConfig {

    private static final ConfigValue<Integer> RANGE = NeroPowerConfig.schema().intRange("beamRange",
            128, 8, 512, true, "maximum straight-line distance (blocks) a transmitter or relay may beam "
            + "power to its target; a link further than this idles with status OUT_OF_RANGE");
    private static final ConfigValue<Integer> LOSS_PERMILLE_PER_BLOCK = NeroPowerConfig.schema().intRange(
            "beamLossPermillePerBlock", 3, 0, 100, true, "beam loss per block of distance (permille): "
            + "delivered = sent * (1 - loss/1000)^distance; 3 = about 68% delivered at 128 blocks, 0 = lossless");
    private static final ConfigValue<Integer> TRANSFER_PER_PASS = NeroPowerConfig.schema().intRange(
            "beamTransferPerPass", 2_000, 1, 1_000_000, true, "NE a transmitter or relay sends per pass "
            + "(before loss); passes run every beamCheckIntervalTicks");
    private static final ConfigValue<Integer> CHECK_INTERVAL_TICKS = NeroPowerConfig.schema().intRange(
            "beamCheckIntervalTicks", 5, 1, 40, true, "ticks between beam passes: each pass re-checks line "
            + "of sight, moves energy and damages anything standing in the beam (5 = four passes a second)");
    private static final ConfigValue<Double> DAMAGE = NeroPowerConfig.schema().doubleRange("beamDamage",
            0.5, 0.0, 10.0, true, "damage (half-hearts) dealt every pass to living entities standing in an "
            + "active beam; 0 disables the hazard");
    private static final ConfigValue<Integer> MAX_HOPS = NeroPowerConfig.schema().intRange("beamMaxHops",
            8, 1, 32, true, "longest relay chain a single pass will follow before it stops (loop guard)");
    private static final ConfigValue<Boolean> CROSS_DIMENSION = NeroPowerConfig.schema().bool(
            "beamCrossDimension", false, true, "true: a transmitter may target an Orbital Receiver in a "
            + "space dimension (Core's neroland:space/dimensions tag) from another dimension, paying "
            + "beamOrbitalHopLossPermille instead of distance loss; false: every link is same-dimension");
    private static final ConfigValue<Integer> ORBITAL_HOP_LOSS_PERMILLE = NeroPowerConfig.schema().intRange(
            "beamOrbitalHopLossPermille", 300, 0, 1_000, true, "fixed loss (permille) of a cross-dimension "
            + "orbital hop, in place of distance loss (300 = 70% delivered)");

    // --- 0.4.0: Beam Splitter and Phased Array Emitter ----------------------
    private static final ConfigValue<Integer> SPLITTER_MAX_LINKS = NeroPowerConfig.schema().intRange(
            "beamSplitterMaxLinks", 4, 1, 8, true, "outgoing links a Beam Splitter may hold; each gets a share "
            + "(permille) of what arrives after the lens, and the shares never add up to more than 100%");
    private static final ConfigValue<String> SPLITTER_LENS_LOSS = NeroPowerConfig.schema().string(
            "beamSplitterLensLoss", SplitMath.DEFAULT_LENS_LOSS, true, "Beam Splitter loss (permille of what "
            + "arrives) by the focus lens in its slot, as lens=permille pairs (quartz, glacite, ember, void); "
            + "with no lens the splitter accepts no beam");
    private static final ConfigValue<Integer> PHASED_RANGE = NeroPowerConfig.schema().intRange(
            "phasedArrayRange", 384, 8, 1_024, true, "maximum straight-line distance (blocks) from a Phased "
            + "Array Emitter to each of its receivers");
    private static final ConfigValue<Integer> PHASED_LOSS = NeroPowerConfig.schema().intRange(
            "phasedArrayLossPermillePerBlock", 1, 0, 100, true, "Phased Array Emitter loss per block of "
            + "distance (permille), compounded like beamLossPermillePerBlock");
    private static final ConfigValue<Integer> PHASED_TRANSFER = NeroPowerConfig.schema().intRange(
            "phasedArrayTransferPerPass", 8_000, 1, 1_000_000, true, "NE a Phased Array Emitter sends per pass "
            + "(before loss) to the one receiver it serves that pass");
    private static final ConfigValue<Integer> PHASED_MAX_TARGETS = NeroPowerConfig.schema().intRange(
            "phasedArrayMaxTargets", 4, 1, 8, true, "receivers a Phased Array Emitter may serve, one per pass "
            + "in list order (round-robin)");

    private BeamConfig() {
    }

    /** Class-load trigger — call before {@code NeroPowerConfig.load()}. */
    public static void init() {
    }

    /** Maximum beam length in blocks (8..512, default 128). */
    public static int beamRange() {
        return RANGE.get();
    }

    /** Loss per block in permille (0..100, default 3). */
    public static int beamLossPermillePerBlock() {
        return LOSS_PERMILLE_PER_BLOCK.get();
    }

    /** NE sent per pass before loss (default 2000). */
    public static int beamTransferPerPass() {
        return TRANSFER_PER_PASS.get();
    }

    /** Ticks between passes (1..40, default 5). */
    public static int beamCheckIntervalTicks() {
        return CHECK_INTERVAL_TICKS.get();
    }

    /** Damage per pass to entities in the beam (0..10, default 0.5; 0 disables). */
    public static double beamDamage() {
        // Core (1.20.1+) clamps a hand-edited "NaN" or infinity to the range, so the value is always finite.
        return DAMAGE.get();
    }

    /** Relay chain depth guard (1..32, default 8). */
    public static int beamMaxHops() {
        return MAX_HOPS.get();
    }

    /** Whether cross-dimension orbital links are allowed (default false). */
    public static boolean beamCrossDimension() {
        return CROSS_DIMENSION.get();
    }

    /** Fixed orbital-hop loss in permille (0..1000, default 300). */
    public static int beamOrbitalHopLossPermille() {
        return ORBITAL_HOP_LOSS_PERMILLE.get();
    }

    /** Outgoing links per Beam Splitter (1..8, default 4). */
    public static int beamSplitterMaxLinks() {
        return SPLITTER_MAX_LINKS.get();
    }

    /** Lens loss permille by {@link SplitMath.Lens#ordinal()} (index 0, no lens, reads 1000). */
    public static int[] beamSplitterLensLoss() {
        return SplitMath.parseLensLoss(SPLITTER_LENS_LOSS.get());
    }

    /** Phased Array range in blocks (8..1024, default 384). */
    public static int phasedArrayRange() {
        return PHASED_RANGE.get();
    }

    /** Phased Array loss per block in permille (0..100, default 1). */
    public static int phasedArrayLossPermillePerBlock() {
        return PHASED_LOSS.get();
    }

    /** NE the Phased Array sends per pass before loss (default 8000). */
    public static int phasedArrayTransferPerPass() {
        return PHASED_TRANSFER.get();
    }

    /** Receivers per Phased Array (1..8, default 4). */
    public static int phasedArrayMaxTargets() {
        return PHASED_MAX_TARGETS.get();
    }
}
