package za.co.neroland.neropower.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.beam.BeamSplitterBlockEntity;
import za.co.neroland.neropower.grid.GridReadout;

/**
 * Beam Splitter BER: the glass prism in the yoke, the lens pane in the collar behind it, and for each
 * live link a tapered fan of light from the prism to that link's barrel plus the barrel's lit cap.
 * Links 0..3 take the barrels left to right as the player faces the block; a fan's width and
 * brightness follow the link's share bucket (0..10).
 *
 * <p>States: working, the prism turns slowly; starved of an incoming beam it hunts five degrees
 * either way every two seconds; in any other stall it holds where it stopped; at rest it sits square.
 * With no lens the collar is empty, the prism is dark and no fan is drawn (the lamp double-blinks).
 *
 * <p>Reads only {@code linkMask()}, {@code shareBucket(i)}, {@code lensFitted()}, {@code readoutCode()}
 * and {@code stallCause()}. The client is never told where a link goes, so the outgoing beams
 * themselves are not drawn. Within 24 blocks everything; to 48 the prism and lamp; beyond that nothing.
 * The frustum box is the block grown by half a block.
 */
public class BeamSplitterRenderer
        implements BlockEntityRenderer<BeamSplitterBlockEntity, BeamSplitterRenderer.State> {

    private static final Identifier PRISM_TEX = PowerRenderHelper.texture("beam_splitter_prism");
    private static final Identifier OPTICS_TEX = PowerRenderHelper.texture("beam_splitter_optics");

    /** The lamp socket on the slab front (the model's {@code status_socket} element). */
    private static final PowerRenderHelper.LampSocket SOCKET =
            PowerRenderHelper.LampSocket.model(13.0F, 0.5F, -0.5F, 15.0F, 2.5F, 0.0F);

    private static final float PRISM_X = 8.0F / 16.0F;
    private static final float PRISM_Y = 7.5F / 16.0F;
    private static final float PRISM_Z = 8.0F / 16.0F;
    private static final float PRISM_HALF = 2.25F / 16.0F;
    private static final float PARKED = 0.0F;
    private static final float TURN_PER_TICK = 1.5F;
    private static final float HUNT_DEGREES = 5.0F;
    private static final float HUNT_TICKS = 40.0F;

    /** Lens pane in the collar (model x 6..10, y 5.5..9.5, z 12.5); optics sheet texels 0..8 by 16..24. */
    private static final float LENS_X0 = 6.0F / 16.0F;
    private static final float LENS_X1 = 10.0F / 16.0F;
    private static final float LENS_Y0 = 5.5F / 16.0F;
    private static final float LENS_Y1 = 9.5F / 16.0F;
    private static final float LENS_Z = 12.5F / 16.0F;

    /** Barrel muzzles, links 0..3 left to right as seen: centre x, z and the barrel's swing (degrees about +y). */
    private static final float[] MUZZLE_X = {12.5F + 1.148F, 9.5F, 6.5F, 3.5F - 1.148F};
    private static final float[] MUZZLE_Z = {0.728F, 0.0F, 0.0F, 0.728F};
    private static final float[] SWING = {-22.5F, 0.0F, 0.0F, 22.5F};
    private static final float MUZZLE_Y = 6.0F / 16.0F;
    private static final float CAP_HALF = 0.5F / 16.0F;
    private static final float GAP = 0.004F;
    /** Fans lie just over the manifold top (model y 8), from the prism to above each barrel's root. */
    private static final float[] FAN_X = {12.5F / 16.0F, 9.5F / 16.0F, 6.5F / 16.0F, 3.5F / 16.0F};
    private static final float FAN_Y = 8.0F / 16.0F + GAP;
    private static final float FAN_Z_PRISM = 6.5F / 16.0F;
    private static final float FAN_Z_BARREL = 3.25F / 16.0F;
    private static final float FAN_NARROW = 0.25F / 16.0F;

    private static final double FULL_DETAIL = 24.0D;
    private static final double REDUCED_DETAIL = 48.0D;
    static final int LOD_FULL = 0;
    static final int LOD_REDUCED = 1;
    static final int LOD_NONE = 2;

    /** Render state: resolved prism angle, links and lamp; nothing is read at submit time. */
    public static class State extends BlockEntityRenderState {
        int lod = LOD_NONE;
        float angle;
        int prismTint = 0xFFFFFF;
        boolean lens;
        int mask;
        final int[] share = new int[4];
        Direction facing = Direction.NORTH;
        final PowerRenderHelper.Lamp lamp = new PowerRenderHelper.Lamp();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** The block grown by half a block (the prism's corners and the fans stay inside). NeoForge-only hook. */
    public AABB getRenderBoundingBox(BeamSplitterBlockEntity splitter) {
        return new AABB(splitter.getBlockPos()).inflate(0.5);
    }

    @Override
    public void extractRenderState(BeamSplitterBlockEntity splitter, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(splitter, state, partialTick, cameraPos, breakProgress);
        Level level = splitter.getLevel();
        state.facing = splitter.getBlockState().getValue(NeroTechMachineBlock.FACING);
        double distance = Math.sqrt(splitter.getBlockPos().distToCenterSqr(cameraPos.x, cameraPos.y, cameraPos.z));
        state.lod = distance <= FULL_DETAIL ? LOD_FULL : distance <= REDUCED_DETAIL ? LOD_REDUCED : LOD_NONE;
        int readout = splitter.readoutCode();
        PowerRenderHelper.lampExtract(state.lamp, readout, false, level, partialTick);
        state.lens = splitter.lensFitted();
        boolean working = readout == PowerRenderHelper.READOUT_WORK;
        state.mask = state.lens && working ? splitter.linkMask() & 0xF : 0;
        for (int i = 0; i < 4; i++) {
            state.share[i] = Mth.clamp(splitter.shareBucket(i), 0, 10);
        }
        state.prismTint = !state.lens ? 0x5A5A5A : working ? 0xFFFFFF : 0xB4B4B4;
        // Explicit null check (not folded into a flag) so ecj's null-flow analysis can track it.
        if (level == null || !state.lamp.animate) {
            state.angle = PARKED;
            return;
        }
        long tick = level.getGameTime();
        if (!splitter.displayInit) {
            splitter.displayInit = true;
            splitter.displayPos = splitter.prevDisplayPos = PARKED;
            splitter.displayLastTick = tick;
        } else if (tick != splitter.displayLastTick) {
            splitter.displayLastTick = tick;
            splitter.prevDisplayPos = splitter.displayPos;
            if (working) {
                splitter.displayPos = (splitter.displayPos + TURN_PER_TICK) % 360.0D;
            }
        }
        double prev = splitter.prevDisplayPos;
        double cur = splitter.displayPos;
        if (cur < prev) {
            cur += 360.0D; // wrapped this tick: lerp the long way round, not backwards
        }
        float angle = (float) ((prev + (cur - prev) * partialTick) % 360.0D);
        if (readout == PowerRenderHelper.READOUT_STALL && state.lens
                && splitter.stallCause() == GridReadout.CAUSE_NO_POWER) {
            angle += HUNT_DEGREES * Mth.sin(state.lamp.time * ((float) Math.PI * 2.0F) / HUNT_TICKS);
        }
        state.angle = angle;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        if (state.lod == LOD_NONE) {
            return;
        }
        int light = PowerRenderHelper.FULL_BRIGHT;
        poseStack.pushPose();
        PowerRenderHelper.rotateToFacing(poseStack, state.facing);
        PowerRenderHelper.lampSubmit(state.lamp, poseStack, collector, SOCKET);

        float angle = state.angle;
        int prismTint = state.prismTint;
        collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(PRISM_TEX),
                (pose, c) -> prism(c, pose, light, prismTint, angle));

        if (state.lod == LOD_FULL && (state.lens || state.mask != 0)) {
            boolean lens = state.lens;
            int mask = state.mask;
            int[] share = state.share.clone();
            collector.order(2).submitCustomGeometry(poseStack, RenderTypes.entityCutout(OPTICS_TEX), (pose, c) -> {
                if (lens) {
                    PowerRenderHelper.face(c, pose, light, 0xFFFFFF, 0, 0, 1,
                            LENS_X0, LENS_Y0, LENS_Z, 0.0F, 0.75F, LENS_X0, LENS_Y1, LENS_Z, 0.0F, 0.5F,
                            LENS_X1, LENS_Y1, LENS_Z, 0.25F, 0.5F, LENS_X1, LENS_Y0, LENS_Z, 0.25F, 0.75F);
                }
                for (int i = 0; i < 4; i++) {
                    if ((mask & (1 << i)) != 0) {
                        fan(c, pose, light, i, share[i]);
                    }
                }
            });
            if (mask != 0) {
                collector.order(3).submitCustomGeometry(poseStack,
                        RenderTypes.entityCutout(PowerRenderHelper.LAMP_TEX), (pose, c) -> {
                            for (int i = 0; i < 4; i++) {
                                if ((mask & (1 << i)) != 0) {
                                    cap(c, pose, light, i, share[i]);
                                }
                            }
                        });
            }
        }
        poseStack.popPose();
    }

    /** The prism: four upright faces and the two ends, turned {@code degrees} about its own axis. */
    private static void prism(VertexConsumer c, PoseStack.Pose pose, int light, int rgb, float degrees) {
        float h = PRISM_HALF;
        for (int k = 0; k < 4; k++) {
            float rad = (degrees + 90.0F * k) * ((float) Math.PI / 180.0F);
            float nx = Mth.cos(rad);
            float nz = Mth.sin(rad);
            PowerRenderHelper.quad(c, pose, light, rgb,
                    PRISM_X + nx * h, PRISM_Y, PRISM_Z + nz * h,
                    -nz * h, 0.0F, nx * h, 0.0F, h, 0.0F, 1.0F, 0.0F);
        }
        float rad = degrees * ((float) Math.PI / 180.0F);
        float ux = Mth.cos(rad) * h;
        float uz = Mth.sin(rad) * h;
        PowerRenderHelper.quad(c, pose, light, rgb, PRISM_X, PRISM_Y + h, PRISM_Z,
                ux, 0.0F, uz, -uz, 0.0F, ux, 0.0F, 1.0F);
        PowerRenderHelper.quad(c, pose, light, rgb, PRISM_X, PRISM_Y - h, PRISM_Z,
                ux, 0.0F, uz, -uz, 0.0F, ux, 0.0F, 1.0F);
    }

    /** Link {@code i}'s fan: a flat wedge from the prism out to the barrel's root, wider with the share. */
    private static void fan(VertexConsumer c, PoseStack.Pose pose, int light, int i, int bucket) {
        float wide = (0.3F + 0.12F * bucket) / 16.0F;
        int rgb = PowerRenderHelper.powerColor(0.35F + 0.05F * bucket);
        float bx = FAN_X[i];
        // The wedge sprite is the top half of the sheet: wide at v 0, narrow at v 0.5.
        PowerRenderHelper.face(c, pose, light, rgb, 0, 1, 0,
                PRISM_X - FAN_NARROW, FAN_Y, FAN_Z_PRISM, 0.4F, 0.5F,
                bx - wide, FAN_Y, FAN_Z_BARREL, 0.0F, 0.0F,
                bx + wide, FAN_Y, FAN_Z_BARREL, 1.0F, 0.0F,
                PRISM_X + FAN_NARROW, FAN_Y, FAN_Z_PRISM, 0.6F, 0.5F);
    }

    /** Link {@code i}'s lit lens cap, square on its barrel's muzzle. */
    private static void cap(VertexConsumer c, PoseStack.Pose pose, int light, int i, int bucket) {
        float rad = SWING[i] * ((float) Math.PI / 180.0F);
        float sin = Mth.sin(rad);
        float cos = Mth.cos(rad);
        // Muzzle normal (-sin, 0, -cos) and its level tangent (cos, 0, -sin).
        float cx = MUZZLE_X[i] / 16.0F - sin * GAP;
        float cz = MUZZLE_Z[i] / 16.0F - cos * GAP;
        int rgb = PowerRenderHelper.powerColor(0.45F + 0.05F * bucket);
        PowerRenderHelper.quad(c, pose, light, rgb, cx, MUZZLE_Y, cz,
                cos * CAP_HALF, 0.0F, -sin * CAP_HALF, 0.0F, CAP_HALF, 0.0F, 0.0F, 1.0F);
    }
}
