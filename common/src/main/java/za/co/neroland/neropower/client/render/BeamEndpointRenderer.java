package za.co.neroland.neropower.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

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
import za.co.neroland.neropower.beam.BeamStatus;
import za.co.neroland.neropower.beam.BeamTarget;
import za.co.neroland.neropower.beam.BeamTransmitterBlockEntity;

/**
 * Beam endpoint BER — one renderer for all four beam blocks (transmitter, relay, receiver, orbital
 * receiver). A {@code beam_dish} disc sits on the pedestal neck (the block model tops out at y=13,
 * leaving the space for exactly this), tilted toward the linked target when there is one in this
 * dimension (straight up for an orbital hop, toward the block's facing when unlinked) and spinning
 * while energy moves. A transmitter or relay that is linked and actually TRANSMITTING draws a
 * {@code beam_ray} segment from its dish centre to the target's, same dimension only, clamped to
 * {@code beamRange}, streamed along the ray in one-block frames (the 32×256 strip is eight 32×32
 * frames; raw {@code entityCutout} textures never play {@code .mcmeta} animations, so the frame
 * index and the segment anchors both advance with the game clock — a scrolling, flickering beam).
 *
 * <p>Sync discipline: extraction reads only the synced surface — the transmitter's
 * {@code target()} ({@code Target*} rides the update tag), {@code renderStatus()} and
 * {@code linked()} — never the world. {@code renderAnimationsEnabled=false} parks the dish and
 * freezes the ray as a static frame.</p>
 */
public class BeamEndpointRenderer
        implements BlockEntityRenderer<BeamEndpointBlockEntity, BeamEndpointRenderer.State> {

    private static final Identifier DISH_TEX = PowerRenderHelper.texture("beam_dish");
    private static final Identifier RAY_TEX = PowerRenderHelper.texture("beam_ray");

    /** Dish centre height above the block origin (neck ends at y=13). */
    private static final float DISH_Y = 14.5F / 16.0F;
    /** Dish half-size (a 12×12 px disc). */
    private static final float DISH_R = 6.0F / 16.0F;
    /** Dish spin speed (degrees per tick) while energy moves; a slow idle turn otherwise. */
    private static final float SPIN_ACTIVE = 4.0F;
    private static final float SPIN_IDLE = 0.5F;
    /** Ray half-width (blocks). */
    private static final float RAY_HALF = 1.5F / 16.0F;
    /** Ray frames in the 32×256 strip, and the clock rates: frame flips per 3 ticks, anchors slide 1/10 block a tick. */
    private static final int RAY_FRAMES = 8;
    private static final float RAY_FRAME_TICKS = 3.0F;
    private static final float RAY_SCROLL = 0.1F;
    /** Facing tilt for an unlinked dish: mostly up, leaning toward the front. */
    private static final float IDLE_LEAN = 0.6F;

    /** Render state: everything extracted from the BE + clocks, nothing read at submit time. */
    public static class State extends BlockEntityRenderState {
        /** Dish normal (unit) in block-local space. */
        float nx = 0.0F;
        float ny = 1.0F;
        float nz = 0.0F;
        float spin;
        /** Whether to draw the ray, and its end point relative to this block's origin (blocks). */
        boolean ray;
        float rayX;
        float rayY;
        float rayZ;
        /** Ray frame index and anchor offset (0..1 block) from the clock. */
        int rayFrame;
        float rayScroll;
        int tint = 0xFFFFFF;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /**
     * Cover the whole ray when linked (it can run {@code beamRange} blocks away); one-block pad
     * otherwise. NeoForge-only frustum hook, inert non-override on Fabric/Forge (NeroTech recipe).
     */
    public AABB getRenderBoundingBox(BeamEndpointBlockEntity endpoint) {
        BlockPos pos = endpoint.getBlockPos();
        if (endpoint instanceof BeamTransmitterBlockEntity transmitter) {
            BeamTarget target = transmitter.target();
            Level level = endpoint.getLevel();
            if (target != null && level != null && target.sameDimension(level)) {
                BlockPos to = target.pos();
                return new AABB(
                        Math.min(pos.getX(), to.getX()) - 1.0, Math.min(pos.getY(), to.getY()) - 1.0,
                        Math.min(pos.getZ(), to.getZ()) - 1.0, Math.max(pos.getX(), to.getX()) + 2.0,
                        Math.max(pos.getY(), to.getY()) + 2.0, Math.max(pos.getZ(), to.getZ()) + 2.0);
            }
        }
        return new AABB(pos).inflate(1.0);
    }

    @Override
    public void extractRenderState(BeamEndpointBlockEntity endpoint, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(endpoint, state, partialTick, cameraPos, breakProgress);
        Level level = endpoint.getLevel();
        Direction facing = endpoint.getBlockState().getValue(NeroTechMachineBlock.FACING);
        BeamStatus status = endpoint.renderStatus();
        boolean moving = status == BeamStatus.TRANSMITTING || status == BeamStatus.RECEIVING;
        state.ray = false;

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
                    // Ray only while energy is actually crossing (BLOCKED / OUT_OF_RANGE etc. draw none).
                    if (status == BeamStatus.TRANSMITTING) {
                        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                        float range = BeamConfig.beamRange();
                        float scale = len > range && len > 0.0F ? range / len : 1.0F;
                        state.ray = len > 0.0F;
                        state.rayX = dx * scale;
                        state.rayY = dy * scale;
                        state.rayZ = dz * scale;
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

        if (NeroTechConfig.renderAnimationsEnabled()) {
            float now = level.getGameTime() + partialTick;
            state.spin = (now * (moving ? SPIN_ACTIVE : SPIN_IDLE)) % 360.0F;
            state.rayFrame = (int) (level.getGameTime() / (long) RAY_FRAME_TICKS) % RAY_FRAMES;
            state.rayScroll = (now * RAY_SCROLL) % 1.0F;
        } else {
            state.spin = 0.0F; // static parked frame
            state.rayFrame = 0;
            state.rayScroll = 0.0F;
        }
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

        if (!state.ray) {
            return;
        }
        // The ray: two crossed strips from this dish centre to the target's, in one-block segments
        // anchored at rayScroll so the frames stream along it; each segment takes one 32×32 frame.
        float sx = 0.5F;
        float sy = DISH_Y;
        float sz = 0.5F;
        float rx = state.rayX;
        float ry = state.rayY;
        float rz = state.rayZ;
        float len = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (len < 1.0E-3F) {
            return;
        }
        float[] rayBasis = new float[6];
        PowerRenderHelper.basis(rx, ry, rz, 0.0F, rayBasis);
        float ax = rayBasis[0] * RAY_HALF;
        float ay = rayBasis[1] * RAY_HALF;
        float az = rayBasis[2] * RAY_HALF;
        float bx = rayBasis[3] * RAY_HALF;
        float by = rayBasis[4] * RAY_HALF;
        float bz = rayBasis[5] * RAY_HALF;
        float dirX = rx / len;
        float dirY = ry / len;
        float dirZ = rz / len;
        int frame0 = state.rayFrame;
        float scroll = state.rayScroll;
        int rayTint = PowerRenderHelper.powerColor(0.7F);
        collector.order(2).submitCustomGeometry(poseStack, RenderTypes.entityCutout(RAY_TEX),
                (pose, c) -> {
                    // Segment boundaries at scroll, scroll+1, ... (partial first and last segments;
                    // scroll=0 simply starts with a whole block).
                    float firstEnd = scroll > 0.0F ? scroll : 1.0F;
                    float start = 0.0F;
                    int i = 0;
                    while (start < len) {
                        float end = Math.min(len, i == 0 ? firstEnd : start + 1.0F);
                        float segLen = end - start;
                        float mid = (start + end) * 0.5F;
                        float cx = sx + dirX * mid;
                        float cy = sy + dirY * mid;
                        float cz = sz + dirZ * mid;
                        // Half-extent along the ray for this segment.
                        float hx = dirX * segLen * 0.5F;
                        float hy = dirY * segLen * 0.5F;
                        float hz = dirZ * segLen * 0.5F;
                        // Frame band in the strip: a partial segment takes a proportional slice.
                        int frame = Math.floorMod(frame0 + i, RAY_FRAMES);
                        float v0 = frame / (float) RAY_FRAMES;
                        float v1 = v0 + Mth.clamp(segLen, 0.0F, 1.0F) / RAY_FRAMES;
                        PowerRenderHelper.quad(c, pose, light, rayTint, cx, cy, cz,
                                ax, ay, az, hx, hy, hz, v0, v1);
                        PowerRenderHelper.quad(c, pose, light, rayTint, cx, cy, cz,
                                bx, by, bz, hx, hy, hz, v0, v1);
                        start = end;
                        i++;
                    }
                });
    }
}
