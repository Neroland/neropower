package za.co.neroland.neropower.map;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import za.co.neroland.neropower.fission.ScorchZones;

/**
 * Where {@link NeroPowerMapLayers} reads its data. The provider owns every visibility decision; a source hands
 * over what is already in memory, so the rules can be tested against a seeded fake. Every method answers an
 * empty collection when {@code server} is {@code null}. Never loads or generates a chunk.
 */
public interface NeroPowerMapSource {

    /**
     * Fission cores and beam endpoints in already-loaded chunks within {@code radius} blocks (horizontal) of
     * {@code around} in {@code dimension}.
     */
    Collection<Machine> machines(@Nullable MinecraftServer server, ResourceKey<Level> dimension, BlockPos around,
            int radius);

    /** Every live scorch zone (expiry is checked by the provider against {@link #today()}). */
    Collection<ScorchZones.Zone> scorchZones(@Nullable MinecraftServer server);

    /** Today's UTC epoch day, the clock scorch-zone expiry is measured against. */
    long today();

    /** What a map marker needs from a machine. {@code owner} is compared, never shown. */
    record Machine(Kind kind, BlockPos pos, Optional<UUID> owner) {
    }

    /** The two machine kinds the power layers draw. */
    enum Kind {
        REACTOR, BEAM
    }
}
