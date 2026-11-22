package za.co.neroland.neropower.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import za.co.neroland.neropower.beam.BeamEndpointBlockEntity;
import za.co.neroland.neropower.beam.BeamTransmitterBlockEntity;
import za.co.neroland.neropower.fission.FissionCoreBlockEntity;
import za.co.neroland.neropower.fission.ScorchZones;
import za.co.neroland.neropower.link.LinkScope;
import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;

/** The live source: block entities in chunks that are already loaded, and the scorch-zone store. */
final class ServerMapSource implements NeroPowerMapSource {

    @Override
    public Collection<Machine> machines(@Nullable MinecraftServer server, ResourceKey<Level> dimension,
            BlockPos around, int radius) {
        ServerLevel level = server == null ? null : server.getLevel(dimension);
        if (level == null) {
            return List.of();
        }
        List<Machine> out = new ArrayList<>();
        int chunks = (radius + 15) >> 4;
        int cx = around.getX() >> 4;
        int cz = around.getZ() >> 4;
        long r2 = (long) radius * radius;
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz); // never loads
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) { // server thread, read-only
                    Machine machine = be.isRemoved() ? null : machine(be);
                    if (machine == null) {
                        continue;
                    }
                    long ex = (long) machine.pos().getX() - around.getX();
                    long ez = (long) machine.pos().getZ() - around.getZ();
                    if (ex * ex + ez * ez <= r2) {
                        out.add(machine);
                    }
                }
            }
        }
        return out;
    }

    @Nullable
    private static Machine machine(BlockEntity be) {
        if (be instanceof FissionCoreBlockEntity core) {
            return new Machine(Kind.REACTOR, core.getBlockPos(), ownerOf(core));
        }
        if (be instanceof BeamEndpointBlockEntity endpoint) {
            return new Machine(Kind.BEAM, endpoint.getBlockPos(), ownerOf(endpoint));
        }
        return null;
    }

    /**
     * The recorded owner, as the link module reads it: a transmitter's (or relay's) link owner, otherwise the
     * NeroTech machine owner (only set when per-player attribution was on at placement).
     */
    private static Optional<UUID> ownerOf(NeroPowerMachineBlockEntity machine) {
        return LinkScope.ownerOf(machine instanceof BeamTransmitterBlockEntity transmitter
                ? transmitter.linkOwner() : Optional.empty(), machine.owner());
    }

    @Override
    public Collection<ScorchZones.Zone> scorchZones(@Nullable MinecraftServer server) {
        return server == null ? List.of() : ScorchZones.get(server).zones();
    }

    @Override
    public long today() {
        return ScorchZones.todayEpochDay();
    }
}
