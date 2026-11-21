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
            "neutron poison gained per tick while the core runs hot: full output, every usable rod slot "
            + "loaded and the control-rod factor above fissionPoisonRodThresholdPermille (0 disables poisoning)");
    private static final ConfigValue<Integer> POISON_ROD_THRESHOLD_PERMILLE = NeroPowerConfig.schema().intRange(
            "fissionPoisonRodThresholdPermille", FissionMath.DEFAULT_POISON_ROD_THRESHOLD, 0, 1_000, true,
            "poison only builds while the effective control-rod factor (permille) is ABOVE this line - "
            + "i.e. the core runs hot; with the defaults one Control Rod Assembly (850) keeps it steady");
    private static final ConfigValue<Integer> POISON_DECAY_PER_TICK = NeroPowerConfig.schema().intRange(
            "fissionPoisonDecayPerTick", 3, 0, 1_000, true,
            "neutron poison shed per tick whenever the core is not accumulating it (idle, throttled, "
            + "part-loaded, control-rodded at or below the threshold, or stalled)");
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

    // --- 0.4.0: moderators, europium rods, breeder, steam mode, braided rod, economizer -----------

    private static final ConfigValue<Integer> MODERATOR_PER_CONTACT = NeroPowerConfig.schema().intRange(
            "fissionModeratorPermillePerContact", 10, 0, 100, true,
            "output AND heat bonus (permille) per moderator fuel-channel contact: each interior moderator "
            + "counts its face-adjacent interior air blocks, weighted 1 (graphite) or 2 (moderator tank)");
    private static final ConfigValue<Integer> MODERATOR_CAP = NeroPowerConfig.schema().intRange(
            "fissionModeratorCapPermille", 400, 0, 1_000, true,
            "largest output AND heat bonus (permille) moderators can give one reactor");
    private static final ConfigValue<Integer> EUROPIUM_ROD_PERMILLE = NeroPowerConfig.schema().intRange(
            "fissionEuropiumControlRodPermille", 250, 0, 1_000, true,
            "output AND heat reduction (permille) per Europium Control Rod Assembly; shares the 15% floor");
    private static final ConfigValue<Integer> BREEDER_TICKS_PER_PELLET = NeroPowerConfig.schema().intRange(
            "breederTicksPerPellet", 36_000, 1_200, 1_000_000, true,
            "running ticks one Breeder Blanket at 100% output factor needs to breed one Kindled Pellet");
    private static final ConfigValue<Integer> BREEDER_MAX_BLANKETS = NeroPowerConfig.schema().intRange(
            "breederMaxBlankets", 8, 1, 64, true,
            "Breeder Blankets counted for the breeding rate (more still cost reactivity)");
    private static final ConfigValue<Integer> BREEDER_COST_PERMILLE = NeroPowerConfig.schema().intRange(
            "breederReactivityCostPermille", 50, 0, 500, true,
            "output AND heat reduction (permille) per Breeder Blanket; shares the 15% floor");
    private static final ConfigValue<Integer> STEAM_PER_HEAT = NeroPowerConfig.schema().intRange(
            "fissionSteamPerHeat", 4, 1, 64, true,
            "mB of steam per heat unit a core in steam mode raises (consuming the same mB of water)");
    private static final ConfigValue<Integer> CRYO_HEAT_PER_MB = NeroPowerConfig.schema().intRange(
            "fissionCryoHeatPerMb", 6, 1, 100, true,
            "heat removed per mB of cryo coolant a Reactor Steam Port feeds the core (either mode)");
    private static final ConfigValue<Integer> CRYO_SAFE_BAND = NeroPowerConfig.schema().intRange(
            "fissionCryoSafeBandPermille", 150, 0, 300, true,
            "how far (permille of heat capacity) WARNING and UNSTABLE move up while cryo coolant is fed");
    private static final ConfigValue<Integer> PORT_TANK_MB = NeroPowerConfig.schema().intRange(
            "reactorSteamPortTankMb", 16_000, 1_000, 256_000, true,
            "capacity (mB) of each Reactor Steam Port tank, coolant and steam alike");
    private static final ConfigValue<Integer> BRAIDED_OUTPUT = NeroPowerConfig.schema().intRange(
            "fissionBraidedOutputPermille", 1_400, 100, 5_000, true,
            "a Braided Fuel Rod's output AND heat, permille of a Fuel Rod at the same burn-up");
    private static final ConfigValue<Integer> BRAIDED_BURNUP = NeroPowerConfig.schema().intRange(
            "fissionBraidedBurnupPermille", 800, 100, 5_000, true,
            "a Braided Fuel Rod's burn-up rate, permille of a Fuel Rod's (800 = lasts 1.25 times as long)");
    private static final ConfigValue<Integer> BRAIDED_HEAVY_WATER = NeroPowerConfig.schema().intRange(
            "fissionBraidedHeavyWaterContacts", 8, 0, 300, true,
            "moderator-tank contact score (contacts x 2) a reactor needs before Braided Fuel Rods fission");
    private static final ConfigValue<Integer> ECONOMIZER_BURNUP = NeroPowerConfig.schema().intRange(
            "fissionEconomizerBurnupPermille", 750, 100, 1_000, true,
            "burn-up rate (permille) of every rod while a Fuel Economizer is fitted");
    private static final ConfigValue<Integer> ECONOMIZER_OUTPUT = NeroPowerConfig.schema().intRange(
            "fissionEconomizerOutputPermille", 900, 100, 1_000, true,
            "output (permille; steam in steam mode) while a Fuel Economizer is fitted");

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

    /** Control-rod factor (permille) poison must exceed to build ({@link FissionMath#poisonAccumulates}). */
    public static int poisonRodThresholdPermille() {
        return POISON_ROD_THRESHOLD_PERMILLE.get();
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

    public static int moderatorPermillePerContact() {
        return MODERATOR_PER_CONTACT.get();
    }

    public static int moderatorCapPermille() {
        return MODERATOR_CAP.get();
    }

    public static int europiumControlRodPermille() {
        return EUROPIUM_ROD_PERMILLE.get();
    }

    public static int breederTicksPerPellet() {
        return BREEDER_TICKS_PER_PELLET.get();
    }

    public static int breederMaxBlankets() {
        return BREEDER_MAX_BLANKETS.get();
    }

    public static int breederReactivityCostPermille() {
        return BREEDER_COST_PERMILLE.get();
    }

    public static int steamPerHeat() {
        return STEAM_PER_HEAT.get();
    }

    public static int cryoHeatPerMb() {
        return CRYO_HEAT_PER_MB.get();
    }

    public static int cryoSafeBandPermille() {
        return CRYO_SAFE_BAND.get();
    }

    public static int steamPortTankMb() {
        return PORT_TANK_MB.get();
    }

    public static int braidedOutputPermille() {
        return BRAIDED_OUTPUT.get();
    }

    public static int braidedBurnupPermille() {
        return BRAIDED_BURNUP.get();
    }

    public static int braidedHeavyWaterContacts() {
        return BRAIDED_HEAVY_WATER.get();
    }

    public static int economizerBurnupPermille() {
        return ECONOMIZER_BURNUP.get();
    }

    public static int economizerOutputPermille() {
        return ECONOMIZER_OUTPUT.get();
    }
}
