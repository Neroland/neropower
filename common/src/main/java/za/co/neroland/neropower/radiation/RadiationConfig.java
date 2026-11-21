package za.co.neroland.neropower.radiation;

import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.neropower.config.NeroPowerConfig;
import za.co.neroland.neropower.failure.TerrainPolicy;

/**
 * Radiation config (shared {@code neropower} schema, server-authoritative). These are the store
 * keys; the effect and source keys arrive with the radiation blocks and tools.
 */
public final class RadiationConfig {

    private static final ConfigValue<String> RADIATION_MODE = NeroPowerConfig.schema().string("radiationMode",
            "auto", true, "whether radiation is simulated: on / off / auto (auto = on in singleplayer and LAN, "
            + "off on a dedicated server). Off writes no contamination and freezes what exists");
    private static final ConfigValue<Integer> DECAY_PERMILLE = NeroPowerConfig.schema().intRange(
            "radiationDecayPermillePerMinute", 5, 0, 1_000, true, "contamination each chunk loses per minute "
            + "of game time, permille of its level (at least 1 while above 0; 0 disables decay)");
    private static final ConfigValue<Integer> MAX_CHUNKS = NeroPowerConfig.schema().intRange(
            "radiationMaxChunksPerDimension", 4_096, 64, 65_536, true, "hard cap on contaminated chunks per "
            + "dimension; past it the lowest levels are pruned first");
    private static final ConfigValue<Integer> DECAY_BUDGET = NeroPowerConfig.schema().intRange(
            "radiationDecayBudgetPerTick", 256, 16, 4_096, true, "contaminated chunks a decay pass visits per "
            + "tick per dimension (round-robin)");

    private RadiationConfig() {
    }

    public static void init() {
    }

    /** Raw mode string. */
    public static String radiationMode() {
        return RADIATION_MODE.get();
    }

    /** Whether radiation runs on this server ({@code auto}: off on a dedicated server). */
    public static boolean enabled(boolean dedicatedServer) {
        return TerrainPolicy.resolve(RADIATION_MODE.get(), dedicatedServer);
    }

    public static int decayPermillePerMinute() {
        return DECAY_PERMILLE.get();
    }

    public static int maxChunksPerDimension() {
        return MAX_CHUNKS.get();
    }

    public static int decayBudgetPerTick() {
        return DECAY_BUDGET.get();
    }
}
