package za.co.neroland.neropower.beam;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.platform.EnergyLookup;

import za.co.neroland.neropower.config.NeroPowerConfig;

/**
 * One same-dimension beam shot from a multi-target source (Beam Splitter, Phased Array Emitter) to one
 * {@link BeamTarget}: the transmitter's rules applied once — loaded target only (never force-loaded), a
 * beam endpoint that accepts beams, range, line of sight ({@link BeamPath} / {@link BeamMath#clear}),
 * per-block loss, the entity hazard, and the source charged {@link BeamMath#sourceCost} for what the
 * target actually kept. Cross-dimension shots are not offered by these sources.
 */
public final class BeamShot {

    /** The outcome of one shot. {@code accepted} is what the target kept; {@code status} the reason. */
    public record Result(BeamStatus status, long accepted, int distance, int lossPermille) {

        static Result of(BeamStatus status, int distance, int lossPermille) {
            return new Result(status, 0L, distance, lossPermille);
        }

        public boolean delivered() {
            return this.accepted > 0L;
        }

        public boolean blocked() {
            return this.status == BeamStatus.BLOCKED;
        }

        public boolean outOfRange() {
            return this.status == BeamStatus.OUT_OF_RANGE;
        }

        /** The link points at nothing that can take a beam any more and should be dropped. */
        public boolean dead() {
            return this.status == BeamStatus.TARGET_MISSING;
        }
    }

    private BeamShot() {
    }

    /**
     * Measure the shot without moving energy (range, distance, loss, sight) — for the screen when a link
     * is not tried this pass. Never loads a chunk.
     */
    public static Result survey(Level level, BlockPos from, BeamTarget aim, int range, int lossPerBlock) {
        return fire(level, from, aim, null, 0L, range, lossPerBlock, Set.of(), 0, false);
    }

    /**
     * Fire up to {@code budget} NE (before loss) from {@code source} at {@code aim}.
     *
     * @param visited endpoints that already sent this pass (passed on to a relay)
     * @param hops    chain depth of this shot
     */
    public static Result fire(Level level, BlockPos from, BeamTarget aim, NeroEnergyStorage source, long budget,
            int range, int lossPerBlock, Set<BlockPos> visited, int hops) {
        return fire(level, from, aim, source, budget, range, lossPerBlock, visited, hops, true);
    }

    private static Result fire(Level level, BlockPos from, BeamTarget aim, NeroEnergyStorage source, long budget,
            int range, int lossPerBlock, Set<BlockPos> visited, int hops, boolean live) {
        if (!aim.sameDimension(level)) {
            return Result.of(BeamStatus.DENIED, 0, BeamMath.PERMILLE);
        }
        BlockPos to = aim.pos();
        if (!BeamMath.withinRange(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(), range)) {
            return Result.of(BeamStatus.OUT_OF_RANGE, 0, BeamMath.PERMILLE);
        }
        double blocks = BeamMath.distance(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
        int distance = (int) Math.round(blocks);
        if (!level.hasChunkAt(to)) {
            return Result.of(BeamStatus.TARGET_ASLEEP, distance, 0);
        }
        if (!(level.getBlockEntity(to) instanceof BeamEndpointBlockEntity endpoint) || !endpoint.acceptsBeam()) {
            return Result.of(BeamStatus.TARGET_MISSING, distance, BeamMath.PERMILLE);
        }
        if (!pathClear(level, from, to)) {
            return Result.of(BeamStatus.BLOCKED, distance, BeamMath.PERMILLE);
        }
        double factor = BeamMath.lossFactor(blocks, lossPerBlock);
        int lossPermille = BeamMath.lossPermille(factor);
        if (!live) {
            return Result.of(BeamStatus.IDLE, distance, lossPermille);
        }
        if (!NeroPowerConfig.transmissionEnabled() || visited.contains(to) || hops >= BeamConfig.beamMaxHops()) {
            return Result.of(visited.contains(to) ? BeamStatus.LOOP : BeamStatus.IDLE, distance, lossPermille);
        }
        hurtEntitiesInBeam(level, from, to);
        if (source == null) {
            return Result.of(BeamStatus.IDLE, distance, lossPermille);
        }
        long offer = source.extract(Math.max(0L, budget), true);
        long deliverable = BeamMath.delivered(offer, factor);
        if (deliverable <= 0L) {
            return Result.of(BeamStatus.IDLE, distance, lossPermille);
        }
        NeroEnergyStorage sink = EnergyLookup.INSTANCE.find(level, to, null);
        if (sink == null) {
            sink = endpoint.getEnergy();
        }
        long accepted = sink.insert(deliverable, false);
        if (accepted <= 0L) {
            return Result.of(BeamStatus.TARGET_FULL, distance, lossPermille);
        }
        source.extract(BeamMath.sourceCost(accepted, factor, offer), false);
        endpoint.onBeamReceived(accepted, visited, hops + 1);
        return new Result(BeamStatus.TRANSMITTING, accepted, distance, lossPermille);
    }

    /** Every loaded cell strictly between the ends is air or a beam endpoint (unloaded cells never block). */
    static boolean pathClear(Level level, BlockPos from, BlockPos to) {
        List<BeamPath.Cell> cells = BeamPath.cellsBetween(from.getX(), from.getY(), from.getZ(),
                to.getX(), to.getY(), to.getZ());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        return BeamMath.clear(cells, cell -> {
            cursor.set(cell.x(), cell.y(), cell.z());
            boolean loaded = level.hasChunkAt(cursor);
            boolean air = loaded && level.getBlockState(cursor).isAir();
            boolean endpoint = loaded && !air && level.getBlockEntity(cursor) instanceof BeamEndpointBlockEntity;
            return BeamMath.blocks(loaded, air, endpoint);
        });
    }

    /**
     * The beam hazard, shared by every beam source: {@code beamDamage} magic damage to each living
     * entity whose box the line between the two block centres passes through (thickened by
     * {@link BeamMath#BEAM_RADIUS}). Only called once the path is clear, so a wall between the ends
     * already stops the beam. The segment is walked in {@link BeamMath#HAZARD_STEP}-block steps with a
     * thin box per step, entities are hurt at most once a pass, and no more than
     * {@link BeamMath#HAZARD_CAP} per shot. {@code 0} damage disables it.
     */
    public static void hurtEntitiesInBeam(Level level, BlockPos from, BlockPos to) {
        double damage = BeamConfig.beamDamage();
        if (damage <= 0.0 || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double ax = from.getX() + 0.5;
        double ay = from.getY() + 0.5;
        double az = from.getZ() + 0.5;
        double bx = to.getX() + 0.5;
        double by = to.getY() + 0.5;
        double bz = to.getZ() + 0.5;
        double length = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
        int steps = BeamMath.hazardSteps(length);
        Set<Integer> hit = new HashSet<>();
        double pad = BeamMath.BEAM_RADIUS + 0.5;
        for (int s = 0; s < steps && hit.size() < BeamMath.HAZARD_CAP; s++) {
            double t0 = (double) s / steps;
            double t1 = (double) (s + 1) / steps;
            double x0 = ax + (bx - ax) * t0;
            double y0 = ay + (by - ay) * t0;
            double z0 = az + (bz - az) * t0;
            double x1 = ax + (bx - ax) * t1;
            double y1 = ay + (by - ay) * t1;
            double z1 = az + (bz - az) * t1;
            AABB step = new AABB(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1),
                    Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1)).inflate(pad);
            for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, step, LivingEntity::isAlive)) {
                if (hit.size() >= BeamMath.HAZARD_CAP || hit.contains(entity.getId())) {
                    continue;
                }
                AABB b = entity.getBoundingBox();
                if (!BeamMath.segmentHitsBox(ax, ay, az, bx, by, bz, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ,
                        BeamMath.BEAM_RADIUS)) {
                    continue;
                }
                hit.add(entity.getId());
                //? if >=1.21.2 {
                entity.hurtServer(serverLevel, serverLevel.damageSources().magic(), (float) damage);
                //?} else {
                /*entity.hurt(serverLevel.damageSources().magic(), (float) damage);
                *///?}
            }
        }
    }
}
