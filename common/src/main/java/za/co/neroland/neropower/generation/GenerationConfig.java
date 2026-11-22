package za.co.neroland.neropower.generation;

import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.neropower.config.NeroPowerConfig;

/**
 * Small-generator balance keys (Hydrogen Fuel Cell and Betavoltaic Cell) on NeroPower's shared
 * {@code neropower} schema. {@code NeroPowerCommon.init()} touches {@link #init()} before
 * {@link NeroPowerConfig#load()} so the keys join the schema Core registers. Every key is
 * server-authoritative gameplay balance; none of them concerns a player.
 */
public final class GenerationConfig {

    // --- Hydrogen Fuel Cell ---------------------------------------------------------------------------
    private static final ConfigValue<Integer> FUEL_CELL_TICKS_PER_UNIT = NeroPowerConfig.schema().intRange(
            "fuelCellTicksPerUnit", 20, 1, 12_000, true, "ticks a Hydrogen Fuel Cell spends on one unit of "
            + "hydrogen with oxygen fed (twice as long when it breathes the air instead)");
    private static final ConfigValue<Integer> FUEL_CELL_NE_PER_UNIT = NeroPowerConfig.schema().intRange(
            "fuelCellNePerHydrogenUnit", 60, 1, 70, true, "NE one unit of hydrogen gives in a Hydrogen "
            + "Fuel Cell, spread evenly over the unit's ticks (on air the same NE over twice the time). Capped "
            + "at 70 so electrolysing water and burning the hydrogen always loses energy, even with the "
            + "cheapest upgraded Electrolyzer");
    private static final ConfigValue<Integer> FUEL_CELL_WATER_PER_UNIT = NeroPowerConfig.schema().intRange(
            "fuelCellWaterPerUnitMb", 50, 0, 1_000, true, "mB of water a Hydrogen Fuel Cell puts out per unit "
            + "of hydrogen");
    private static final ConfigValue<Integer> FUEL_CELL_TANK_MB = NeroPowerConfig.schema().intRange(
            "fuelCellTankMb", 4_000, 200, 1_000_000, true, "capacity (mB) of each of a Hydrogen Fuel Cell's "
            + "three tanks (hydrogen in, oxygen in, water out)");

    // --- Betavoltaic Cell -----------------------------------------------------------------------------
    private static final ConfigValue<Integer> BETAVOLTAIC_NE_PER_TICK = NeroPowerConfig.schema().intRange(
            "betavoltaicNePerTick", 4, 1, 10_000, true, "flat NE per tick a Betavoltaic Cell makes while its "
            + "Beta Capsule is live (it does not fade; it stops)");
    private static final ConfigValue<Integer> BETAVOLTAIC_CAPSULE_TICKS = NeroPowerConfig.schema().intRange(
            "betavoltaicCapsuleTicks", 720_000, 20, 100_000_000, true, "ticks of output one Beta Capsule "
            + "gives before it becomes a Spent Beta Capsule (720000 = 30 in-game days); a full buffer pauses it");

    private GenerationConfig() {
    }

    /** Class-load trigger so the keys above join the schema before {@link NeroPowerConfig#load()}. */
    public static void init() {
    }

    public static int fuelCellTicksPerUnit() {
        return FUEL_CELL_TICKS_PER_UNIT.get();
    }

    public static int fuelCellNePerUnit() {
        return FUEL_CELL_NE_PER_UNIT.get();
    }

    public static int fuelCellWaterPerUnitMb() {
        return FUEL_CELL_WATER_PER_UNIT.get();
    }

    public static int fuelCellTankMb() {
        return FUEL_CELL_TANK_MB.get();
    }

    public static int betavoltaicNePerTick() {
        return BETAVOLTAIC_NE_PER_TICK.get();
    }

    public static int betavoltaicCapsuleTicks() {
        return BETAVOLTAIC_CAPSULE_TICKS.get();
    }
}
