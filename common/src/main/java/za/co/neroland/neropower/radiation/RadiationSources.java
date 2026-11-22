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
import za.co.neroland.neropower.protection.Protection;
import za.co.neroland.neropower.protection.ProtectionCheck;

/**
 * The in-memory, per-dimension set of loaded radiation sources (never saved) and the one-off bursts.
 * Fills {@link BackEndHooks}' emitter registry and burst sink; the fission package calls
 * {@link #reactorFailure(ServerLevel, BlockPos)} when a core enters FAILURE. Every write goes through
 * {@link #contaminate} and is a no-op while radiation is off, and for a chunk whose centre column the
 * active {@link ProtectionCheck} reports protected. World data only: positions and levels.
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
        if (za.co.neroland.neropower.machine.Exhibits.at(level, pos)) {
            return; // gallery exhibits never contaminate
        }
        contaminate(level, RadiationMath.chunkKeyOfBlock(pos.getX(), pos.getZ()), RadiationConfig.poolFailureBurst());
    }

    /** A Fission Core at {@code pos} entered FAILURE: the burst on its chunk, a third on each neighbour. */
    public static void reactorFailure(ServerLevel level, BlockPos pos) {
        if (za.co.neroland.neropower.machine.Exhibits.at(level, pos)) {
            return; // gallery exhibits never contaminate
        }
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

    /**
     * Add contamination to a chunk (capped store); nothing while radiation is off, for 0, or for a
     * protected chunk (dropped rods, casks and bursts never reach protected ground).
     */
    public static void contaminate(ServerLevel level, long chunk, int amount) {
        if (amount <= 0 || !RadiationConfig.enabled(level.getServer().isDedicatedServer())) {
            return;
        }
        if (chunkProtected(Protection.get(), level, chunk, level.getSeaLevel())) {
            return;
        }
        ContaminationState.get(level).add(chunk, amount, RadiationConfig.maxChunksPerDimension());
    }

    /** Whether the centre column of {@code chunk} (at height {@code y}) is protected ground. */
    static boolean chunkProtected(ProtectionCheck check, ServerLevel level, long chunk, int y) {
        BlockPos centre = new BlockPos((RadiationMath.chunkX(chunk) << 4) + 8, y, (RadiationMath.chunkZ(chunk) << 4) + 8);
        return check.isProtected(level, centre);
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

    /** Whether a source shows on a Geiger: it emits and has at least one unshielded face. Pure. */
    public static boolean counts(boolean emitting, int unshieldedFaces) {
        return emitting && unshieldedFaces > 0;
    }

    /**
     * Sources that actually leak within {@code range} blocks of {@code centre} (capped for the Geiger): emitting
     * <i>and</i> with at least one face not covered by shielding. A fully enclosed cask emits nothing, so the
     * Geiger does not count it either (no hidden-vault detector).
     */
    public int countNear(Level level, BlockPos centre, int range, int cap) {
        long r2 = (long) range * range;
        int n = 0;
        for (RadiationEmitter emitter : sources(level)) {
            if (counts(emitter.emitting(), emitter.unshieldedFaces())
                    && !za.co.neroland.neropower.machine.Exhibits.is(emitter)
                    && emitter.emitterPos().distSqr(centre) <= r2) {
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
            if (za.co.neroland.neropower.machine.Exhibits.is(emitter)) {
                continue; // gallery exhibits never contaminate
            }
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
