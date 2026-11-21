package za.co.neroland.neropower.radiation;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import za.co.neroland.neropower.backend.BackEndHooks;
import za.co.neroland.neropower.backend.RadiationEmitter;

/**
 * The in-memory, per-dimension set of loaded radiation sources (never saved) and the one-off bursts.
 * Fills {@link BackEndHooks}' emitter registry and burst sink; the fission package calls
 * {@link #reactorFailure(ServerLevel, BlockPos)} when a core enters FAILURE. Every write goes through
 * {@link #contaminate} and is a no-op while radiation is off. World data only: positions and levels.
 */
public final class RadiationSources implements BackEndHooks.EmitterRegistry, BackEndHooks.BurstSink {

    public static final RadiationSources INSTANCE = new RadiationSources();

    private final Map<ResourceKey<Level>, Set<RadiationEmitter>> emitters = new ConcurrentHashMap<>();

    private RadiationSources() {
    }

    /** Install this registry and burst sink into the back-end hooks (call once from common init). */
    public static void install() {
        BackEndHooks.setEmitters(INSTANCE);
        BackEndHooks.setBursts(INSTANCE);
    }

    @Override
    public void add(Level level, RadiationEmitter emitter) {
        if (level.isClientSide()) {
            return;
        }
        this.emitters.computeIfAbsent(level.dimension(),
                k -> Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()))).add(emitter);
    }

    @Override
    public void remove(Level level, RadiationEmitter emitter) {
        Set<RadiationEmitter> set = this.emitters.get(level.dimension());
        if (set != null) {
            set.remove(emitter);
        }
    }

    @Override
    public void poolFailure(ServerLevel level, BlockPos pos) {
        contaminate(level, RadiationMath.chunkKeyOfBlock(pos.getX(), pos.getZ()), RadiationConfig.poolFailureBurst());
    }

    /** A Fission Core at {@code pos} entered FAILURE: the burst on its chunk, a third on each neighbour. */
    public static void reactorFailure(ServerLevel level, BlockPos pos) {
        int burst = RadiationConfig.failureBurst();
        int side = RadiationMath.neighbourBurst(burst);
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                contaminate(level, RadiationMath.chunkKey(cx + dx, cz + dz), dx == 0 && dz == 0 ? burst : side);
            }
        }
    }

    /** Add contamination to a chunk (capped store); nothing while radiation is off or for 0. */
    public static void contaminate(ServerLevel level, long chunk, int amount) {
        if (amount <= 0 || !RadiationConfig.enabled(level.getServer().isDedicatedServer())) {
            return;
        }
        ContaminationState.get(level).add(chunk, amount, RadiationConfig.maxChunksPerDimension());
    }

    /** A copy of the loaded sources of a dimension (iteration-safe). */
    public RadiationEmitter[] sources(Level level) {
        Set<RadiationEmitter> set = this.emitters.get(level.dimension());
        if (set == null) {
            return new RadiationEmitter[0];
        }
        synchronized (set) {
            return set.toArray(new RadiationEmitter[0]);
        }
    }

    /** Emitting sources within {@code range} blocks of {@code centre} (capped for the Geiger). */
    public int countNear(Level level, BlockPos centre, int range, int cap) {
        long r2 = (long) range * range;
        int n = 0;
        for (RadiationEmitter emitter : sources(level)) {
            if (emitter.emitting() && emitter.emitterPos().distSqr(centre) <= r2) {
                n++;
                if (n >= cap) {
                    return cap;
                }
            }
        }
        return n;
    }

    /** One emission pass for a dimension: every emitting source adds per open face to its chunk. */
    void emit(ServerLevel level, int perFace) {
        for (RadiationEmitter emitter : sources(level)) {
            int amount = RadiationMath.emission(emitter.emitting(), emitter.unshieldedFaces(), perFace);
            if (amount > 0) {
                BlockPos pos = emitter.emitterPos();
                contaminate(level, RadiationMath.chunkKeyOfBlock(pos.getX(), pos.getZ()), amount);
            }
        }
    }

    /** Forget every source (server stop). */
    public void clear() {
        this.emitters.clear();
    }
}
