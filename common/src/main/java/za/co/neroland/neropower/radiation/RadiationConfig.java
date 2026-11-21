package za.co.neroland.neropower.radiation;

import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.neropower.config.NeroPowerConfig;
import za.co.neroland.neropower.failure.TerrainPolicy;

/**
 * Radiation config (shared {@code neropower} schema, server-authoritative): the store keys, the
 * effect pass, the sources and the decontaminant. Every key here is world tuning; none names a player.
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
    private static final ConfigValue<Integer> EFFECT_INTERVAL = NeroPowerConfig.schema().intRange(
            "radiationEffectIntervalTicks", 40, 20, 200, true, "ticks between radiation effect passes");
    private static final ConfigValue<Integer> EFFECT_CHUNKS = NeroPowerConfig.schema().intRange(
            "radiationEffectChunksPerPass", 64, 1, 1_024, true, "contaminated loaded chunks one effect pass "
            + "visits per dimension (round-robin)");
    private static final ConfigValue<Integer> FAILURE_BURST = NeroPowerConfig.schema().intRange(
            "radiationFailureBurst", 600, 0, 1_000, true, "contamination added to a failed reactor's chunk "
            + "(a third of it to each of the 8 neighbouring chunks)");
    private static final ConfigValue<Integer> POOL_FAILURE_BURST = NeroPowerConfig.schema().intRange(
            "radiationPoolFailureBurst", 300, 0, 1_000, true, "contamination added to a Spent Fuel Rack's "
            + "chunk when it reaches FAILURE");
    private static final ConfigValue<Integer> CASK_PER_FACE = NeroPowerConfig.schema().intRange(
            "radiationCaskPerFace", 1, 0, 100, true, "contamination per unshielded face of a loaded source "
            + "(dry cask with spent rods or residue, failed rack) per minute");
    private static final ConfigValue<Integer> DROPPED_HOT = NeroPowerConfig.schema().intRange(
            "radiationDroppedHotRod", 4, 0, 100, true, "contamination per dropped hot spent fuel rod per 100 ticks");
    private static final ConfigValue<Integer> DROPPED_SPENT = NeroPowerConfig.schema().intRange(
            "radiationDroppedSpent", 1, 0, 100, true, "contamination per dropped spent fuel rod or fission "
            + "residue per 100 ticks");
    private static final ConfigValue<Integer> DECONTAMINANT = NeroPowerConfig.schema().intRange(
            "decontaminantStrength", 250, 1, 1_000, true, "contamination one Decontaminant removes from the "
            + "clicked chunk");

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

    public static int effectIntervalTicks() {
        return EFFECT_INTERVAL.get();
    }

    public static int effectChunksPerPass() {
        return EFFECT_CHUNKS.get();
    }

    public static int failureBurst() {
        return FAILURE_BURST.get();
    }

    public static int poolFailureBurst() {
        return POOL_FAILURE_BURST.get();
    }

    public static int caskPerFace() {
        return CASK_PER_FACE.get();
    }

    public static int droppedHotRod() {
        return DROPPED_HOT.get();
    }

    public static int droppedSpent() {
        return DROPPED_SPENT.get();
    }

    public static int decontaminantStrength() {
        return DECONTAMINANT.get();
    }
}
