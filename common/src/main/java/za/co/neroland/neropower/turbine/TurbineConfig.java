package za.co.neroland.neropower.turbine;

import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.neropower.config.NeroPowerConfig;
import za.co.neroland.neropower.fission.FissionConfig;
import za.co.neroland.neropower.fission.FissionMath;

/**
 * The steam turbine's and Steam Condenser's balance keys, declared on NeroPower's shared
 * {@code neropower} schema ({@link NeroPowerConfig#schema()}). Like the other family config classes it
 * must be class-loaded before {@code NeroPowerConfig.load()} ({@link #init()}), otherwise the keys are
 * not part of the schema Core registers.
 *
 * <p>Every value is gameplay balance and therefore server-authoritative. None carries player data.
 */
public final class TurbineConfig {

    /** What an overspeed FAILURE does. */
    public enum FailureMode {
        /** Inlets close, the trip latches, the rotor coasts (default; never destructive). */
        TRIP,
        /** As {@link #TRIP}, and every mounted vane set is destroyed. */
        SHED_VANES,
        /** As {@link #TRIP}, and the shroud bursts ({@code Explode}, capped, terrain-policy aware). */
        BLAST;

        /** Parse a config value; anything unknown reads as {@link #TRIP}. */
        public static FailureMode parse(String raw) {
            if (raw == null) {
                return TRIP;
            }
            return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "shed_blades", "shed_vanes" -> SHED_VANES;
                case "blast" -> BLAST;
                default -> TRIP;
            };
        }
    }

    private static final ConfigValue<Integer> NE_PER_STEAM_MB = NeroPowerConfig.schema().intRange(
            "turbineNePerSteamMb", 8, 1, 100, true,
            "NE one mB of steam yields at rated speed, 1000 permille vane efficiency and a condensed exhaust");
    private static final ConfigValue<Integer> VANE_FLOW_STEEL = NeroPowerConfig.schema().intRange(
            "turbineVaneFlowSteel", 40, 1, 10_000, true, "mB/t of steam one steel vane slice passes");
    private static final ConfigValue<Integer> VANE_FLOW_CINDERSTEEL = NeroPowerConfig.schema().intRange(
            "turbineVaneFlowCindersteel", 70, 1, 10_000, true, "mB/t of steam one cindersteel vane slice passes");
    private static final ConfigValue<Integer> VANE_FLOW_SUPERCONDUCTING = NeroPowerConfig.schema().intRange(
            "turbineVaneFlowSuperconducting", 110, 1, 10_000, true,
            "mB/t of steam one superconducting vane slice passes");
    private static final ConfigValue<Integer> VANE_EFF_STEEL = NeroPowerConfig.schema().intRange(
            "turbineVaneEffSteel", 550, 1, 1_000, true, "steel vane efficiency (permille)");
    private static final ConfigValue<Integer> VANE_EFF_CINDERSTEEL = NeroPowerConfig.schema().intRange(
            "turbineVaneEffCindersteel", 650, 1, 1_000, true, "cindersteel vane efficiency (permille)");
    private static final ConfigValue<Integer> VANE_EFF_SUPERCONDUCTING = NeroPowerConfig.schema().intRange(
            "turbineVaneEffSuperconducting", 750, 1, 1_000, true, "superconducting vane efficiency (permille)");
    private static final ConfigValue<Integer> STATOR_CEILING_TIER1 = NeroPowerConfig.schema().intRange(
            "turbineStatorCeilingTier1", 15, 1, 100_000, true,
            "NE/t one tier 1 wound Turbine Stator can convert (8 per stator slice)");
    private static final ConfigValue<Integer> STATOR_CEILING_TIER2 = NeroPowerConfig.schema().intRange(
            "turbineStatorCeilingTier2", 40, 1, 100_000, true, "NE/t one tier 2 wound Turbine Stator can convert");
    private static final ConfigValue<Integer> STATOR_CEILING_TIER3 = NeroPowerConfig.schema().intRange(
            "turbineStatorCeilingTier3", 100, 1, 100_000, true, "NE/t one tier 3 wound Turbine Stator can convert");
    private static final ConfigValue<Integer> SPIN_UP = NeroPowerConfig.schema().intRange(
            "turbineSpinUpRpmPerTick", 3, 1, 100, true, "rpm the rotor gains per tick at most (3 = 0 to 1800 in 30 s)");
    private static final ConfigValue<Integer> COAST_DOWN = NeroPowerConfig.schema().intRange(
            "turbineCoastDownRpmPerTick", 1, 1, 100, true, "rpm the rotor loses per tick at most (1 = 1800 to 0 in 90 s)");
    private static final ConfigValue<Integer> TRIP_RPM = NeroPowerConfig.schema().intRange(
            "turbineTripRpm", 2_100, 1_900, 3_000, true,
            "rpm at which overspeed stress reaches 1000 permille on the failure ladder (rated speed is 1800)");
    private static final ConfigValue<String> FAILURE_ACTION = NeroPowerConfig.schema().string(
            "turbineFailureAction", "trip", true,
            "what an overspeed FAILURE does: trip (inlets close, rotor coasts), shed_blades (trip and the vane "
            + "sets are destroyed) or blast (trip and the shroud bursts); with overloadEnabled=false always trip");
    private static final ConfigValue<Integer> VENT_EFFICIENCY = NeroPowerConfig.schema().intRange(
            "turbineVentEfficiencyPermille", 600, 0, 1_000, true,
            "share of the yield kept for exhaust vented to the air through a vent port instead of a condenser");
    private static final ConfigValue<Integer> PORT_TANK_MB = NeroPowerConfig.schema().intRange(
            "turbineSteamPortTankMb", 16_000, 1_000, 256_000, true, "tank size (mB) of a Turbine Steam Port");
    private static final ConfigValue<Integer> PORT_TRANSFER_INTERVAL = NeroPowerConfig.schema().intRange(
            "portTransferInterval", PortTransfer.DEFAULT_INTERVAL, PortTransfer.MIN_INTERVAL, PortTransfer.MAX_INTERVAL,
            true, "ticks between the pipe-less fluid exchanges of Reactor Steam Ports, Turbine Steam Ports and "
            + "Steam Condensers with the blocks they touch (never per tick)");
    private static final ConfigValue<Integer> CONDENSER_MB_PER_TICK = NeroPowerConfig.schema().intRange(
            "condenserMbPerTick", 200, 1, 100_000, true,
            "mB/t of exhaust steam an air-cooled Steam Condenser condenses at atmosphere factor 1000");
    private static final ConfigValue<String> CONDENSER_ATMOSPHERE_FACTORS = NeroPowerConfig.schema().string(
            "condenserAtmosphereFactors", TurbineMath.DEFAULT_ATMOSPHERE_FACTORS, true,
            "air-cooling factor (permille, 0-2000) by the air NeroTech reports at the condenser; unknown = 1000");
    private static final ConfigValue<Integer> CONDENSER_RADIATOR_MB = NeroPowerConfig.schema().intRange(
            "condenserRadiatorMbPerTick", 30, 0, 10_000, true,
            "mB/t each face-adjacent NeroTech Radiator adds to a Steam Condenser (works in vacuum)");
    private static final ConfigValue<Integer> CONDENSER_STEAM_PER_CRYO = NeroPowerConfig.schema().intRange(
            "condenserSteamPerCryoMb", 20, 1, 1_000, true,
            "mB of exhaust steam one mB of cryo coolant condenses (works in vacuum)");
    private static final ConfigValue<Integer> CONDENSER_TANK_MB = NeroPowerConfig.schema().intRange(
            "condenserTankMb", 16_000, 1_000, 256_000, true,
            "size (mB) of each of a Steam Condenser's three tanks (exhaust in, water out, cryo coolant)");

    private TurbineConfig() {
    }

    /** Class-load trigger: called before {@code NeroPowerConfig.load()}. */
    public static void init() {
    }

    /**
     * The NE per mB the turbine actually uses: {@code turbineNePerSteamMb}, capped so steam mode can never
     * yield more than twice direct mode per rod at any config ({@code FissionMath.steamNePerMbCap}). The defaults sit exactly on the cap (8), so it only binds on extreme configs.
     */
    public static int nePerSteamMb() {
        int configured = NE_PER_STEAM_MB.get();
        String curve = FissionConfig.burnupCurve();
        int nePerRod = FissionConfig.nePerRod();
        int heatPerRod = FissionConfig.heatPerRodPerTick();
        int steamPerHeat = FissionConfig.steamPerHeat();
        CapCache cache = capCache;
        if (cache == null || !cache.matches(curve, nePerRod, heatPerRod, steamPerHeat)) {
            cache = new CapCache(curve, nePerRod, heatPerRod, steamPerHeat,
                    FissionMath.steamNePerMbCap(FissionMath.parseCurve(curve), nePerRod, heatPerRod, steamPerHeat));
            capCache = cache;
        }
        return Math.max(0, Math.min(configured, cache.cap()));
    }

    /** {@code turbineNePerSteamMb} as configured, before the steam-to-direct cap ({@link #nePerSteamMb()}). */
    public static int configuredNePerSteamMb() {
        return NE_PER_STEAM_MB.get();
    }

    /** The last computed cap, keyed by the fission keys it depends on (recomputed after a config reload). */
    private record CapCache(String curve, int nePerRod, int heatPerRod, int steamPerHeat, int cap) {
        boolean matches(String c, int ne, int heat, int steam) {
            return this.curve.equals(c) && this.nePerRod == ne && this.heatPerRod == heat && this.steamPerHeat == steam;
        }
    }

    private static volatile CapCache capCache;

    /** mB/t one vane slice of {@code tier} passes (0 for none). */
    public static int vaneFlow(VaneTier tier) {
        return switch (tier) {
            case NONE -> 0;
            case STEEL -> VANE_FLOW_STEEL.get();
            case CINDERSTEEL -> VANE_FLOW_CINDERSTEEL.get();
            case SUPERCONDUCTING -> VANE_FLOW_SUPERCONDUCTING.get();
        };
    }

    /** Vane efficiency permille of {@code tier} (0 for none). */
    public static int vaneEfficiency(VaneTier tier) {
        return switch (tier) {
            case NONE -> 0;
            case STEEL -> VANE_EFF_STEEL.get();
            case CINDERSTEEL -> VANE_EFF_CINDERSTEEL.get();
            case SUPERCONDUCTING -> VANE_EFF_SUPERCONDUCTING.get();
        };
    }

    /** NE/t one stator wound to {@code tier} converts (0 when bare). */
    public static int statorCeiling(WindingTier tier) {
        return switch (tier) {
            case NONE -> 0;
            case TIER1 -> STATOR_CEILING_TIER1.get();
            case TIER2 -> STATOR_CEILING_TIER2.get();
            case TIER3 -> STATOR_CEILING_TIER3.get();
        };
    }

    public static int spinUpRpmPerTick() {
        return SPIN_UP.get();
    }

    public static int coastDownRpmPerTick() {
        return COAST_DOWN.get();
    }

    public static int tripRpm() {
        return TRIP_RPM.get();
    }

    public static FailureMode failureAction() {
        return FailureMode.parse(FAILURE_ACTION.get());
    }

    public static int ventEfficiencyPermille() {
        return VENT_EFFICIENCY.get();
    }

    public static int portTankMb() {
        return PORT_TANK_MB.get();
    }

    /** Ticks between adjacent fluid exchanges of ports and condensers, clamped to its bounds. */
    public static int portTransferInterval() {
        return PortTransfer.clampInterval(PORT_TRANSFER_INTERVAL.get());
    }

    public static int condenserMbPerTick() {
        return CONDENSER_MB_PER_TICK.get();
    }

    public static String condenserAtmosphereFactors() {
        String raw = CONDENSER_ATMOSPHERE_FACTORS.get();
        return raw == null ? TurbineMath.DEFAULT_ATMOSPHERE_FACTORS : raw;
    }

    public static int condenserRadiatorMbPerTick() {
        return CONDENSER_RADIATOR_MB.get();
    }

    public static int condenserSteamPerCryoMb() {
        return CONDENSER_STEAM_PER_CRYO.get();
    }

    public static int condenserTankMb() {
        return CONDENSER_TANK_MB.get();
    }
}
