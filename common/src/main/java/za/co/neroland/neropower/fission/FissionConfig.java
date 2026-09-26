package za.co.neroland.neropower.fission;

import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.neropower.config.NeroPowerConfig;

/**
 * The Stage 4 fission reactor's balance keys, declared on NeroPower's shared {@code neropower}
 * schema ({@link NeroPowerConfig#schema()}) so they live in the same {@code neropower.properties}
 * file as the failure ladder. This class must be class-loaded before {@code NeroPowerConfig.load()}
 * ({@code NeroPowerCommon.init()} calls {@link #init()} first), otherwise the keys are not part of
 * the schema Core registers.
 *
 * <p>Every value here is gameplay balance and therefore <b>server-authoritative</b>. None of them
 * carries player data.
 */
public final class FissionConfig {

    private static final ConfigValue<Integer> NE_PER_ROD = NeroPowerConfig.schema().intRange(
            "fissionNePerRod", 120, 1, 100_000, true,
            "nominal NE/tick one fresh fuel rod contributes at 100% of the burn-up curve; the core sums "
            + "every loaded rod, then scales by control rods, preset, upgrades and the failure ladder");
    private static final ConfigValue<Integer> HEAT_PER_ROD_PER_TICK = NeroPowerConfig.schema().intRange(
            "fissionHeatPerRodPerTick", 6, 0, 10_000, true,
            "heat one rod adds per tick at 100% of its heat factor (fresh rods run hotter: heat factor = "
            + "output factor + 200 permille); control rods reduce it in step with output");
    private static final ConfigValue<Integer> BURNUP_PER_TICK = NeroPowerConfig.schema().intRange(
            "fissionBurnupPerTick", 8, 1, 1_000_000, true,
            "burn-up a loaded rod gains per tick, in permille x1000 (8 = a rod lasts ~125,000 ticks / "
            + "~104 minutes at Balanced; Overdrive and Speed modules burn faster)");
    private static final ConfigValue<String> BURNUP_CURVE = NeroPowerConfig.schema().string(
            "fissionBurnupCurve", FissionMath.DEFAULT_CURVE, true,
            "output factor (permille of nominal) by rod burn-up (permille), as burnup=factor knots "
            + "interpolated linearly; fewer than two valid knots falls back to the default");
    private static final ConfigValue<Integer> CONTROL_ROD_PERMILLE = NeroPowerConfig.schema().intRange(
            "fissionControlRodPermille", 150, 0, 1_000, true,
            "output AND heat reduction (permille) per Control Rod Assembly inside the shell; the factor "
            + "never drops below 15% however many assemblies are fitted");
    private static final ConfigValue<Integer> POISON_PER_TICK = NeroPowerConfig.schema().intRange(
            "fissionPoisonPerTick", 2, 0, 1_000, true,
            "neutron poison gained per tick while the core runs at full output with every usable rod "
            + "slot loaded (0 disables poisoning)");
    private static final ConfigValue<Integer> POISON_DECAY_PER_TICK = NeroPowerConfig.schema().intRange(
            "fissionPoisonDecayPerTick", 3, 0, 1_000, true,
            "neutron poison shed per tick whenever the core is not accumulating it (idle, throttled, "
            + "part-loaded or stalled)");
    private static final ConfigValue<Integer> POISON_STALL_PERMILLE = NeroPowerConfig.schema().intRange(
            "fissionPoisonStallPermille", 900, 1, 1_000, true,
            "poison level (permille) at which the core stalls (no output, no burn-up) until it has "
            + "decayed 300 below this line - the xenon pit");
    private static final ConfigValue<Boolean> SCORCH_ENABLED = NeroPowerConfig.schema().bool(
            "fissionScorchEnabled", false, true,
            "true: a fission FAILURE also leaves a scorch zone that hurts living entities for "
            + "fissionScorchDays real days; false (default): the blast alone");
    private static final ConfigValue<Integer> SCORCH_DAYS = NeroPowerConfig.schema().intRange(
            "fissionScorchDays", 3, 1, 365, true,
            "real (calendar, UTC) days a scorch zone persists after a fission failure");
    private static final ConfigValue<Integer> SCORCH_RADIUS = NeroPowerConfig.schema().intRange(
            "fissionScorchRadius", 6, 1, 32, true,
            "half-edge (blocks) of the cubic scorch zone centred on the failed reactor");

    private FissionConfig() {
    }

    /** Class-load trigger: called by {@code NeroPowerCommon.init()} before {@code NeroPowerConfig.load()}. */
    public static void init() {
    }

    public static int nePerRod() {
        return NE_PER_ROD.get();
    }

    public static int heatPerRodPerTick() {
        return HEAT_PER_ROD_PER_TICK.get();
    }

    /** Burn-up gained per rod per tick, in permille x1000. */
    public static int burnupPerTick() {
        return BURNUP_PER_TICK.get();
    }

    /** The raw burn-up curve string; parse it with {@link FissionMath#parseCurve(String)}. */
    public static String burnupCurve() {
        String raw = BURNUP_CURVE.get();
        return raw == null ? FissionMath.DEFAULT_CURVE : raw;
    }

    public static int controlRodPermille() {
        return CONTROL_ROD_PERMILLE.get();
    }

    public static int poisonPerTick() {
        return POISON_PER_TICK.get();
    }

    public static int poisonDecayPerTick() {
        return POISON_DECAY_PER_TICK.get();
    }

    public static int poisonStallPermille() {
        return POISON_STALL_PERMILLE.get();
    }

    public static boolean scorchEnabled() {
        return SCORCH_ENABLED.get();
    }

    public static int scorchDays() {
        return SCORCH_DAYS.get();
    }

    public static int scorchRadius() {
        return SCORCH_RADIUS.get();
    }
}
