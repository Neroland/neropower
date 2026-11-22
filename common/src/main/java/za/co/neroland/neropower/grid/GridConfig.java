package za.co.neroland.neropower.grid;

import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.neropower.config.NeroPowerConfig;

/**
 * Grid balance keys (0.4.0) for the Circuit Breaker and the Load Bank, declared on
 * NeroPower's shared {@code neropower} schema. {@code NeroPowerCommon.init()} must class-load this
 * ({@link #init()}) <b>before</b> {@code NeroPowerConfig.load()}. Server-authoritative; no player data.
 */
public final class GridConfig {

    private static final ConfigValue<Integer> BREAKER_DWELL = NeroPowerConfig.schema().intRange(
            "breakerMinDwellTicks", 10, 0, 200, true, "ticks a Circuit Breaker holds each warning stage before "
            + "it climbs to the next; it trips once its load reaches its limit (100%) for that long");
    private static final ConfigValue<Integer> LOAD_BANK_MAX_RATE = NeroPowerConfig.schema().intRange(
            "loadBankMaxRate", 20_000, 1, 1_000_000, true, "highest rate (NE/t) a Load Bank can be set to burn");
    private static final ConfigValue<Integer> LOAD_BANK_NE_PER_HEAT = NeroPowerConfig.schema().intRange(
            "loadBankNePerHeat", 100, 1, 1_000_000, true, "NE a Load Bank burns per unit of heat it makes; "
            + "raised automatically so a Stirling Generator on it always returns less than half the power burned");

    private GridConfig() {
    }

    /** Class-load trigger — call before {@code NeroPowerConfig.load()}. */
    public static void init() {
    }

    public static int breakerMinDwellTicks() {
        return BREAKER_DWELL.get();
    }

    public static int loadBankMaxRate() {
        return LOAD_BANK_MAX_RATE.get();
    }

    public static int loadBankNePerHeat() {
        return LOAD_BANK_NE_PER_HEAT.get();
    }
}
