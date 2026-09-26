package za.co.neroland.neropower.environmental;

import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.neropower.config.NeroPowerConfig;

/**
 * Stage 7 balance keys — the Radioisotope Generator's decay curve and the Stirling Generator's
 * heat-to-power exchange — declared on NeroPower's shared {@code neropower} schema (see
 * {@link NeroPowerConfig#schema()}). {@code NeroPowerCommon.init()} touches {@link #init()} before
 * {@link NeroPowerConfig#load()} so these values are part of the schema Core registers.
 *
 * <p>Every key here is server-authoritative gameplay balance. RTG decay is the same on every world;
 * the Stirling's cold-face bonus scales with the local ambient when {@code planetEfficiencyEnabled}
 * is on ({@link StirlingMath#coldFaceBonusPermille}).
 */
public final class EnvironmentalConfig {

    /** Ticks in one in-game day — the unit {@code rtgHalfLifeDays} is expressed in. */
    public static final int TICKS_PER_DAY = 24_000;

    // --- Radioisotope Generator ----------------------------------------------------
    private static final ConfigValue<Integer> RTG_NE_PER_TICK = NeroPowerConfig.schema().intRange(
            "rtgNePerTick", 30, 1, 10_000, true, "NE per tick a Radioisotope Generator produces from a "
            + "fresh isotope pellet; the output halves every rtgHalfLifeDays until it drops below "
            + "rtgCutoffPermille of this value");
    private static final ConfigValue<Integer> RTG_HALF_LIFE_DAYS = NeroPowerConfig.schema().intRange(
            "rtgHalfLifeDays", 20, 1, 365, true, "half-life of an isotope pellet in in-game days "
            + "(1 day = 24000 ticks): output halves every this many days");
    private static final ConfigValue<Integer> RTG_CUTOFF_PERMILLE = NeroPowerConfig.schema().intRange(
            "rtgCutoffPermille", 50, 1, 999, true, "a pellet is spent once its output falls below this "
            + "permille of rtgNePerTick (50 = 5%, about 4.3 half-lives); the RTG then ejects a spent pellet");

    private static final ConfigValue<Integer> RTG_SPENT_PELLET_POLLUTION = NeroPowerConfig.schema().intRange(
            "rtgSpentPelletPollution", 200, 0, 100_000, true, "pollution burst (NeroTech's regional "
            + "pollution, no player attribution) recorded per Spent Isotope Pellet destroyed as a dropped "
            + "item - burnt, in lava, on a cactus, in an explosion (0 disables)");

    // --- Stirling Generator -----------------------------------------------------------
    private static final ConfigValue<Integer> STIRLING_NE_PER_HEAT_UNIT = NeroPowerConfig.schema().intRange(
            "stirlingNePerHeatUnit", 15, 1, 1_000, true, "NE a Stirling Generator produces per unit of heat "
            + "it draws from its hottest adjacent machine");
    private static final ConfigValue<Integer> STIRLING_MAX_DRAW_PER_OP = NeroPowerConfig.schema().intRange(
            "stirlingMaxDrawPerOp", 8, 1, 1_000, true, "hard cap on the heat a Stirling Generator draws "
            + "from a neighbour in one tick, whatever the gradient");
    private static final ConfigValue<Integer> STIRLING_DRAW_PERMILLE = NeroPowerConfig.schema().intRange(
            "stirlingDrawPermille", 100, 1, 1_000, true, "share (permille) of the hot neighbour's gradient "
            + "above ambient a Stirling Generator draws per tick (100 = 10%)");
    private static final ConfigValue<Integer> STIRLING_COLD_FACE_BONUS_PERMILLE = NeroPowerConfig.schema().intRange(
            "stirlingColdFaceBonusPermille", 500, 0, 1_000, true, "output bonus (permille) while a Stirling "
            + "Generator touches a cold sink — water, ice, snow or a NeroTech Radiator (500 = +50%); "
            + "without one the usable gradient is also halved");

    private EnvironmentalConfig() {
    }

    /** Class-load trigger so the keys above join the schema before {@link NeroPowerConfig#load()}. */
    public static void init() {
    }

    public static int rtgNePerTick() {
        return RTG_NE_PER_TICK.get();
    }

    public static int rtgHalfLifeDays() {
        return RTG_HALF_LIFE_DAYS.get();
    }

    /** {@link #rtgHalfLifeDays()} in ticks. */
    public static long rtgHalfLifeTicks() {
        return (long) rtgHalfLifeDays() * TICKS_PER_DAY;
    }

    public static int rtgCutoffPermille() {
        return RTG_CUTOFF_PERMILLE.get();
    }

    /** Pollution recorded per destroyed Spent Isotope Pellet ({@link SpentIsotopePelletItem}). */
    public static int rtgSpentPelletPollution() {
        return RTG_SPENT_PELLET_POLLUTION.get();
    }

    public static int stirlingNePerHeatUnit() {
        return STIRLING_NE_PER_HEAT_UNIT.get();
    }

    public static int stirlingMaxDrawPerOp() {
        return STIRLING_MAX_DRAW_PER_OP.get();
    }

    public static int stirlingDrawPermille() {
        return STIRLING_DRAW_PERMILLE.get();
    }

    public static int stirlingColdFaceBonusPermille() {
        return STIRLING_COLD_FACE_BONUS_PERMILLE.get();
    }
}
