package za.co.neroland.neropower.radiation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Server tick for contamination: one decay step per dimension, within the per-tick budget. Does nothing
 * while radiation is off (existing data is kept, frozen). Called from NeroPower's existing per-loader
 * server tick ({@code ScorchTicker}), so no loader wiring changes.
 */
public final class ContaminationTicker {

    private ContaminationTicker() {
    }

    public static void tick(MinecraftServer server) {
        if (!RadiationConfig.enabled(server.isDedicatedServer())) {
            return;
        }
        int permille = RadiationConfig.decayPermillePerMinute();
        int budget = RadiationConfig.decayBudgetPerTick();
        for (ServerLevel level : server.getAllLevels()) {
            ContaminationState.get(level).decayTick(level.getGameTime(), permille, budget);
        }
    }
}
