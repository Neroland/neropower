package za.co.neroland.neropower.client.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerotech.config.NeroTechConfig;
import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.beam.BeamConfig;
import za.co.neroland.neropower.beam.BeamEndpointBlockEntity;
import za.co.neroland.neropower.beam.BeamPath;
import za.co.neroland.neropower.beam.BeamStatus;
import za.co.neroland.neropower.beam.BeamTarget;
import za.co.neroland.neropower.beam.BeamTransmitterBlockEntity;

/**
 * Beam endpoint BER — one renderer for all four beam blocks (transmitter, relay, receiver, orbital
 * receiver). A {@code beam_dish} disc sits on the pedestal neck (the block model tops out at y=13,
 * leaving the space for exactly this), tilted toward the linked target when there is one in this
 * dimension (straight up for an orbital hop, toward the block's facing when unlinked) and spinning
 * while energy moves. A transmitter or relay that is linked and actually TRANSMITTING draws a beam
 * from its dish centre toward the target's, same dimension only; one whose line of sight is BLOCKED
 * draws a dimmer beam that ends in a small flare at the block in the way, and nothing beyond it.
 *
 * <p>The beam itself ({@link Ray}) is shared with the Phased Array Emitter: a crossed pair of
 * {@code beam_ray} strips with a striated outer sheath. It is cut to 64 blocks from the emitter and
 * to the view distance, the sheath is dropped once the camera is more than 32 blocks from the beam,
 * and a beam with both drawn ends more than 64 blocks from the camera is not drawn at all. A beam is
 * never more than seven quads. The frustum box covers the same clipped segment, so a long beam is
 * culled when its near part is off screen.
 *
 * <p>Sync discipline: extraction reads only the synced surface — the transmitter's
 * {@code target()} ({@code Target*} rides the update tag), {@code renderStatus()} and
 * {@code linked()} — plus the client's own loaded blocks for the blocked end point (looked up once
 * every ten ticks). {@code renderAnimationsEnabled=false} parks the dish and freezes the beam.</p>
 */
public class BeamEndpointRenderer
        implements BlockEntityRenderer<BeamEndpointBlockEntity, BeamEndpointRenderer.State> {

    private static final Identifier DISH_TEX = PowerRenderHelper.texture("beam_dish");
    private static final Identifier RAY_TEX = PowerRenderHelper.texture("beam_ray");
    private static final Identifier SHEATH_TEX = PowerRenderHelper.texture("beam_sheath");
    private static final Identifier FLARE_TEX = PowerRenderHelper.texture("beam_flare");

    /** Dish centre height above the block origin (neck ends at y=13). */
    private static final float DISH_Y = 14.5F / 16.0F;
    /** Dish half-size (a 12×12 px disc). */
    private static final float DISH_R = 6.0F / 16.0F;
    /** Dish spin speed (degrees per tick) while energy moves; a slow idle turn otherwise. */
    private static final float SPIN_ACTIVE = 4.0F;
    private static final float SPIN_IDLE = 0.5F;
    /** Facing tilt for an unlinked dish: mostly up, leaning toward the front. */
    private static final float IDLE_LEAN = 0.6F;

    /** No beam is drawn past this many blocks from its emitter, or with both ends this far from the camera. */
    public static final float MAX_DRAWN = 64.0F;
    /** The sheath is drawn only while the camera is within this many blocks of the beam. */
    public static final float SHEATH_RANGE = 32.0F;
    /** Ray and sheath half-widths (blocks). */
    private static final float RAY_HALF = 1.5F / 16.0F;
    private static final float SHEATH_HALF = 3.5F / 16.0F;
    private static final float FLARE_HALF = 0.3F;
    /** The ray strip is eight 32×32 frames; one quad shows up to seven of them end to end. */
    private static final int RAY_FRAMES = 8;
    private static final int RAY_REPEATS = 7;
    /** Longest single quad along a beam (blocks): a 64-block beam is two. */
    private static final float SEGMENT = 32.0F;
    /** Pattern travel, in tiles per tick. */
    private static final float RAY_SCROLL = 0.1F;
    private static final long TIME_WRAP = 2400L;
    /** How often a blocked beam's end point is looked up again (ticks). */
    private static final long SIGHT_TICKS = 10L;
    private static final float BLOCKED_GAIN = 0.6F;

    /**
     * One beam's share of a render state: origin (block-relative), unit direction, drawn length and
     * colours. Filled by {@link #rayExtract}, drawn by {@link #raySubmit}.
     */
    public static final class Ray {
        boolean on;
        float ox;
        float oy;
        float oz;
        float dx;
        float dy;
        float dz;
        float len;
        boolean sheath;
        boolean flare;
        float scroll;
        /** Brightness 0..1 (a fade, or a blocked beam's dimming). */
        public float gain = 1.0F;
        public int coreRgb = PowerRenderHelper.powerColor(0.7F);
        public int sheathRgb = PowerRenderHelper.powerColor(0.45F);

        /** Whether anything will be drawn. */
        public boolean on() {
            return this.on;
        }

        public void off() {
            this.on = false;
        }
    }

    /** Render state: everything extracted from the BE + clocks, nothing read at submit time. */
    public static class State extends BlockEntityRenderState {
        /** Dish normal (unit) in block-local space. */
        float nx = 0.0F;
        float ny = 1.0F;
        float nz = 0.0F;
        float spin;
        int tint = 0xFFFFFF;
        final Ray ray = new Ray();
        final BeamScopeOverlay.Scope scope = new BeamScopeOverlay.Scope();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /**
     * Cover the beam when linked, cut to {@link #MAX_DRAWN} blocks from this block; a one-block pad
     * otherwise. NeoForge-only frustum hook, inert non-override on Fabric/Forge (NeroTech recipe).
     */
    public AABB getRenderBoundingBox(BeamEndpointBlockEntity endpoint) {
        BlockPos pos = endpoint.getBlockPos();
        if (endpoint instanceof BeamTransmitterBlockEntity transmitter) {
            BeamTarget target = transmitter.target();
            Level level = endpoint.getLevel();
            if (target != null && level != null && target.sameDimension(level)) {
                BlockPos to = target.pos();
                return segmentBox(pos, to.getX() - pos.getX(), to.getY() - pos.getY(), to.getZ() - pos.getZ(),
                        MAX_DRAWN);
            }
        }
        return new AABB(pos).inflate(1.0);
    }

    /** The box round a segment from {@code pos} along {@code (dx,dy,dz)}, cut to {@code max} blocks, padded by one. */
    public static AABB segmentBox(BlockPos pos, double dx, double dy, double dz, double max) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double scale = len > max && len > 0.0 ? max / len : 1.0;
        double ex = dx * scale;
        double ey = dy * scale;
        double ez = dz * scale;
        return new AABB(
                pos.getX() + Math.min(0.0, ex) - 1.0, pos.getY() + Math.min(0.0, ey) - 1.0,
                pos.getZ() + Math.min(0.0, ez) - 1.0, pos.getX() + Math.max(0.0, ex) + 2.0,
                pos.getY() + Math.max(0.0, ey) + 2.0, pos.getZ() + Math.max(0.0, ez) + 2.0);
    }

    @Override
    public void extractRenderState(BeamEndpointBlockEntity endpoint, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(endpoint, state, partialTick, cameraPos, breakProgress);
        Level level = endpoint.getLevel();
        Direction facing = endpoint.getBlockState().getValue(NeroTechMachineBlock.FACING);
        BeamStatus status = endpoint.renderStatus();
        boolean moving = status == BeamStatus.TRANSMITTING || status == BeamStatus.RECEIVING;
        boolean animate = NeroTechConfig.renderAnimationsEnabled();
        state.ray.off();
        state.scope.off();

        // Dish aim: the linked target (this dimension), straight up for an orbital hop, else the facing.
        float dx = facing.getStepX() * IDLE_LEAN;
        float dy = 1.0F;
        float dz = facing.getStepZ() * IDLE_LEAN;
        // Explicit null check (not folded into a flag) so ecj's null-flow analysis can track it.
        if (level == null) {
            setNormal(state, dx, dy, dz);
            state.spin = 0.0F;
            state.tint = 0xFFFFFF;
            return;
        }
        if (endpoint instanceof BeamTransmitterBlockEntity transmitter) {
            BeamTarget target = transmitter.target();
            if (target != null) {
                if (target.sameDimension(level)) {
                    BlockPos from = endpoint.getBlockPos();
                    BlockPos to = target.pos();
                    dx = to.getX() - from.getX();
                    dy = to.getY() - from.getY();
                    dz = to.getZ() - from.getZ();
                    float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    boolean aimed = BeamScopeOverlay.aimed(from);
                    if (status == BeamStatus.TRANSMITTING) {
                        // Energy is crossing: the whole link, out to the configured range.
                        state.ray.gain = 1.0F;
                        rayExtract(state.ray, from, 0.5F, DISH_Y, 0.5F, dx, dy, dz,
                                Math.min(len, BeamConfig.beamRange()), false, cameraPos, level, partialTick, animate);
                    } else if (status == BeamStatus.BLOCKED) {
                        // Something is in the way: the beam stops there with a flare.
                        float blockedAt = cachedSight(endpoint, level, from, to);
                        if (blockedAt > 0.0F) {
                            state.ray.gain = BLOCKED_GAIN;
                            rayExtract(state.ray, from, 0.5F, DISH_Y, 0.5F, dx, dy, dz, blockedAt, true,
                                    cameraPos, level, partialTick, animate);
                        }
                    }
                    if (aimed && len > 0.0F) {
                        BlockPos.MutableBlockPos hit = new BlockPos.MutableBlockPos();
                        float blockedAt = sight(level, from, to.getX(), to.getY(), to.getZ(), hit);
                        BeamScopeOverlay.extract(state.scope, from, 0.5F, DISH_Y, 0.5F, dx, dy, dz, len,
                                blockedAt, blockedAt >= 0.0F ? hit : null);
                    }
                } else {
                    dx = 0.0F;
                    dy = 1.0F;
                    dz = 0.0F; // orbital: aim at the sky; the hop itself is never drawn
                }
            }
        } else if (endpoint.orbital()) {
            dx = 0.0F;
            dy = 1.0F;
            dz = 0.0F;
        }
        setNormal(state, dx, dy, dz);
        state.tint = moving ? PowerRenderHelper.powerColor(0.55F) : 0xFFFFFF;

        if (animate) {
            float now = level.getGameTime() + partialTick;
            state.spin = (now * (moving ? SPIN_ACTIVE : SPIN_IDLE)) % 360.0F;
        } else {
            state.spin = 0.0F; // static parked frame
        }
    }

    /** The blocked end point of a transmitter's beam, looked up again every {@link #SIGHT_TICKS} ticks. */
    private static float cachedSight(BeamEndpointBlockEntity endpoint, Level level, BlockPos from, BlockPos to) {
        long tick = level.getGameTime();
        if (!endpoint.displayInit || tick < endpoint.displayLastTick
                || tick - endpoint.displayLastTick >= SIGHT_TICKS) {
            endpoint.displayInit = true;
            endpoint.displayLastTick = tick;
            endpoint.displayPos = sight(level, from, to.getX(), to.getY(), to.getZ(), new BlockPos.MutableBlockPos());
        }
        return (float) endpoint.displayPos;
    }

    /**
     * Walks the block cells strictly between {@code from} and the block at {@code (tx,ty,tz)} on the
     * client and returns the distance (blocks, centre to near face) to the first loaded cell that is
     * neither air nor another beam endpoint, leaving that cell in {@code hit}; {@code -1} when the way
     * is clear. Unloaded cells never block, as on the server.
     */
    public static float sight(Level level, BlockPos from, int tx, int ty, int tz, BlockPos.MutableBlockPos hit) {
        List<BeamPath.Cell> cells = BeamPath.cellsBetween(from.getX(), from.getY(), from.getZ(), tx, ty, tz);
        for (int i = 0; i < cells.size(); i++) {
            BeamPath.Cell cell = cells.get(i);
            hit.set(cell.x(), cell.y(), cell.z());
            if (!level.hasChunkAt(hit) || level.getBlockState(hit).isAir()
                    || level.getBlockEntity(hit) instanceof BeamEndpointBlockEntity) {
                continue;
            }
            double ddx = cell.x() - from.getX();
            double ddy = cell.y() - from.getY();
            double ddz = cell.z() - from.getZ();
            return (float) Math.max(0.25, Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz) - 0.5);
        }
        return -1.0F;
    }

    /**
     * Fill {@code ray} for a beam leaving {@code pos} at the block-relative point {@code (ox,oy,oz)}
     * along {@code (dx,dy,dz)} (any length) for {@code length} blocks. Applies every cut: 64 blocks
     * from the emitter, the view distance, both ends beyond 64 blocks of the camera, and the sheath
     * range. A flare is kept only if the far end survives the cuts. Set {@code ray.gain} and the
     * colours before or after; they are not touched here.
     */
    public static void rayExtract(Ray ray, BlockPos pos, float ox, float oy, float oz,
            float dx, float dy, float dz, float length, boolean flare, Vec3 cameraPos, Level level,
            float partialTick, boolean animate) {
        ray.on = false;
        float norm = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (norm < 1.0E-4F || length < 1.0E-3F) {
            return;
        }
        float ux = dx / norm;
        float uy = dy / norm;
        float uz = dz / norm;
        float len = Math.min(Math.min(length, MAX_DRAWN), viewDistance());
        // Camera relative to the ray's origin.
        float cx = (float) (cameraPos.x - pos.getX()) - ox;
        float cy = (float) (cameraPos.y - pos.getY()) - oy;
        float cz = (float) (cameraPos.z - pos.getZ()) - oz;
        float toStart = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        float ex = cx - ux * len;
        float ey = cy - uy * len;
        float ez = cz - uz * len;
        float toEnd = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
        if (toStart > MAX_DRAWN && toEnd > MAX_DRAWN) {
            return;
        }
        float along = Mth.clamp(cx * ux + cy * uy + cz * uz, 0.0F, len);
        float px = cx - ux * along;
        float py = cy - uy * along;
        float pz = cz - uz * along;
        ray.on = true;
        ray.ox = ox;
        ray.oy = oy;
        ray.oz = oz;
        ray.dx = ux;
        ray.dy = uy;
        ray.dz = uz;
        ray.len = len;
        ray.sheath = px * px + py * py + pz * pz <= SHEATH_RANGE * SHEATH_RANGE;
        ray.flare = flare && len >= length - 1.0E-3F;
        if (animate && level != null) {
            float travel = ((level.getGameTime() % TIME_WRAP) + partialTick) * RAY_SCROLL;
            ray.scroll = 1.0F - (travel - Mth.floor(travel));
        } else {
            ray.scroll = 0.0F;
        }
    }

    /** The client's view distance in blocks. */
    private static float viewDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F;
    }

    /** Draw {@code ray}: the crossed core strips, the sheath over the first segment, the end flare. */
    public static void raySubmit(Ray ray, PoseStack poseStack, SubmitNodeCollector collector) {
        if (!ray.on) {
            return;
        }
        int light = PowerRenderHelper.FULL_BRIGHT;
        float[] basis = new float[6];
        PowerRenderHelper.basis(ray.dx, ray.dy, ray.dz, 0.0F, basis);
        float ax = basis[0];
        float ay = basis[1];
        float az = basis[2];
        float bx = basis[3];
        float by = basis[4];
        float bz = basis[5];
        float sx = ray.ox;
        float sy = ray.oy;
        float sz = ray.oz;
        float dirX = ray.dx;
        float dirY = ray.dy;
        float dirZ = ray.dz;
        float len = ray.len;
        float v0 = ray.scroll / RAY_FRAMES;
        int core = PowerRenderHelper.dim(ray.coreRgb, ray.gain);
        collector.order(2).submitCustomGeometry(poseStack, RenderTypes.entityCutout(RAY_TEX), (pose, c) -> {
            float start = 0.0F;
            while (start < len - 1.0E-3F) {
                float end = Math.min(len, start + SEGMENT);
                int repeats = Mth.clamp(Math.round(end - start), 1, RAY_REPEATS);
                float v1 = v0 + repeats / (float) RAY_FRAMES;
                strip(c, pose, light, core, sx, sy, sz, dirX, dirY, dirZ, start, end,
                        ax * RAY_HALF, ay * RAY_HALF, az * RAY_HALF, v0, v1);
                strip(c, pose, light, core, sx, sy, sz, dirX, dirY, dirZ, start, end,
                        bx * RAY_HALF, by * RAY_HALF, bz * RAY_HALF, v0, v1);
                start = end;
            }
        });
        if (ray.sheath) {
            int sheath = PowerRenderHelper.dim(ray.sheathRgb, ray.gain);
            float end = Math.min(len, SEGMENT);
            collector.order(3).submitCustomGeometry(poseStack, RenderTypes.entityCutout(SHEATH_TEX), (pose, c) -> {
                strip(c, pose, light, sheath, sx, sy, sz, dirX, dirY, dirZ, 0.0F, end,
                        ax * SHEATH_HALF, ay * SHEATH_HALF, az * SHEATH_HALF, 0.0F, 1.0F);
                strip(c, pose, light, sheath, sx, sy, sz, dirX, dirY, dirZ, 0.0F, end,
                        bx * SHEATH_HALF, by * SHEATH_HALF, bz * SHEATH_HALF, 0.0F, 1.0F);
            });
        }
        if (ray.flare) {
            float fx = sx + dirX * (len - 0.05F);
            float fy = sy + dirY * (len - 0.05F);
            float fz = sz + dirZ * (len - 0.05F);
            int flare = PowerRenderHelper.dim(PowerRenderHelper.lerpColor(0.5F, ray.coreRgb, 0xFFFFFF), ray.gain);
            collector.order(4).submitCustomGeometry(poseStack, RenderTypes.entityCutout(FLARE_TEX),
                    (pose, c) -> PowerRenderHelper.quad(c, pose, light, flare, fx, fy, fz,
                            ax * FLARE_HALF, ay * FLARE_HALF, az * FLARE_HALF,
                            bx * FLARE_HALF, by * FLARE_HALF, bz * FLARE_HALF, 0.0F, 1.0F));
        }
    }

    /** One strip along a ray from {@code start} to {@code end} blocks, {@code (wx,wy,wz)} its half-width. */
    static void strip(com.mojang.blaze3d.vertex.VertexConsumer c, PoseStack.Pose pose, int light, int rgb,
            float sx, float sy, float sz, float dirX, float dirY, float dirZ, float start, float end,
            float wx, float wy, float wz, float v0, float v1) {
        float mid = (start + end) * 0.5F;
        float half = (end - start) * 0.5F;
        PowerRenderHelper.quad(c, pose, light, rgb, sx + dirX * mid, sy + dirY * mid, sz + dirZ * mid,
                wx, wy, wz, dirX * half, dirY * half, dirZ * half, v0, v1);
    }

    private static void setNormal(State state, float dx, float dy, float dz) {
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6F) {
            state.nx = 0.0F;
            state.ny = 1.0F;
            state.nz = 0.0F;
            return;
        }
        state.nx = dx / len;
        state.ny = dy / len;
        state.nz = dz / len;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        int light = PowerRenderHelper.FULL_BRIGHT;
        float[] basis = new float[6];

        // The dish: a disc perpendicular to the aim, spun about it (pure vector maths — no pose ops).
        PowerRenderHelper.basis(state.nx, state.ny, state.nz, state.spin, basis);
        float ux = basis[0] * DISH_R;
        float uy = basis[1] * DISH_R;
        float uz = basis[2] * DISH_R;
        float vx = basis[3] * DISH_R;
        float vy = basis[4] * DISH_R;
        float vz = basis[5] * DISH_R;
        int dishTint = state.tint;
        collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(DISH_TEX),
                (pose, c) -> PowerRenderHelper.quad(c, pose, light, dishTint,
                        0.5F, DISH_Y, 0.5F, ux, uy, uz, vx, vy, vz, 0.0F, 1.0F));

        raySubmit(state.ray, poseStack, collector);
        BeamScopeOverlay.submit(state.scope, poseStack, collector);
    }
}
