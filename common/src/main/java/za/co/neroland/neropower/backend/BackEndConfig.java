package za.co.neroland.neropower.backend;

import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.neropower.config.NeroPowerConfig;

/**
 * Waste back-end balance keys — the Spent Fuel Rack's decay heat and cooling, the Vitrifier's cost and
 * the Dry Cask's trace heat — declared on NeroPower's shared {@code neropower} schema (see
 * {@link NeroPowerConfig#schema()}). {@code NeroPowerCommon.init()} touches {@link #init()} before
 * {@link NeroPowerConfig#load()} so these values join the schema Core registers. Every key is
 * server-authoritative gameplay balance; none of them concerns a player.
 */
public final class BackEndConfig {

    // --- Spent Fuel Rack ------------------------------------------------------------------------------
    private static final ConfigValue<Integer> POOL_ROD_HEAT_PER_TICK = NeroPowerConfig.schema().intRange(
            "poolRodHeatPerTick", 4, 0, 1_000, true, "decay heat per tick each Hot Spent Fuel Rod adds to a "
            + "Spent Fuel Rack");
    private static final ConfigValue<Integer> POOL_COOLING_PER_CONTACT = NeroPowerConfig.schema().intRange(
            "poolCoolingPerContact", 4, 0, 1_000, true, "heat per tick a Spent Fuel Rack loses for each water "
            + "source block touching one of its five non-bottom faces (0-5 contacts)");
    private static final ConfigValue<Integer> POOL_COOL_TICKS = NeroPowerConfig.schema().intRange(
            "poolCoolTicks", 20_000, 20, 10_000_000, true, "ticks a Hot Spent Fuel Rod must sit in a calm "
            + "Spent Fuel Rack (below the warning stage) before it becomes a Spent Fuel Rod");

    // --- Vitrifier --------------------------------------------------------------------------------------
    private static final ConfigValue<Integer> VITRIFIER_NE_PER_TICK = NeroPowerConfig.schema().intRange(
            "vitrifierNePerTick", 40, 1, 100_000, true, "NE per tick a Vitrifier draws while it works");
    private static final ConfigValue<Integer> VITRIFIER_TICKS_PER_OP = NeroPowerConfig.schema().intRange(
            "vitrifierTicksPerOp", 200, 1, 100_000, true, "ticks one vitrification takes (one waste item and "
            + "two sand become one Vitrified Waste)");

    // --- Dry Cask ---------------------------------------------------------------------------------------
    private static final ConfigValue<Integer> CASK_HEAT_INTERVAL_TICKS = NeroPowerConfig.schema().intRange(
            "caskHeatIntervalTicks", 20, 1, 12_000, true, "how often a Dry Cask adds its trace heat");
    private static final ConfigValue<Integer> CASK_SLOTS_PER_HEAT = NeroPowerConfig.schema().intRange(
            "caskSlotsPerHeat", 9, 1, 27, true, "a Dry Cask adds 1 heat per caskHeatIntervalTicks for every "
            + "this many slots holding spent rods or fission residue (vitrified waste is inert)");

    private BackEndConfig() {
    }

    /** Class-load trigger so the keys above join the schema before {@link NeroPowerConfig#load()}. */
    public static void init() {
    }

    public static int poolRodHeatPerTick() {
        return POOL_ROD_HEAT_PER_TICK.get();
    }

    public static int poolCoolingPerContact() {
        return POOL_COOLING_PER_CONTACT.get();
    }

    public static int poolCoolTicks() {
        return POOL_COOL_TICKS.get();
    }

    public static int vitrifierNePerTick() {
        return VITRIFIER_NE_PER_TICK.get();
    }

    public static int vitrifierTicksPerOp() {
        return VITRIFIER_TICKS_PER_OP.get();
    }

    public static int caskHeatIntervalTicks() {
        return CASK_HEAT_INTERVAL_TICKS.get();
    }

    public static int caskSlotsPerHeat() {
        return CASK_SLOTS_PER_HEAT.get();
    }
}
