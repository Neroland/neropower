package za.co.neroland.neropower.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

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

import za.co.neroland.neropower.storage.MomentumWheelBlockEntity;

/**
 * Momentum Wheel BER: the rim wheel turning on its cross axle inside the guard cage (the block model
 * carries the cage, pillow blocks, axle and hub drum; the rim is drawn here). Speed is the read: the
 * synced charge bucket (0..20) sets the turn rate, up to 1.2 turns a second, and from 70 % the spokes
 * give way to a blur disc. The single cyan balance mark on the rim makes a slow turn readable; one
 * tread segment carries the balance weight for the edge-on view.
 *
 * <p>Sync discipline: extraction reads only {@code chargeBucket()} and {@code readoutCode()}. The
 * angle accumulates once per game tick on the client (the Stirling recipe), so a bucket change speeds
 * the wheel up rather than snapping it. A full wheel keeps turning (it is full, not stopped); with
 * animations off the wheel is a parked frame. Detail tiers by camera distance: to 24 blocks the rim
 * discs and tread, to 48 the discs only, beyond that nothing (the baked cage and hub carry the look).
 */
public class MomentumWheelRenderer
        implements BlockEntityRenderer<MomentumWheelBlockEntity, MomentumWheelRenderer.State> {

    private static final Identifier RIM_TEX = PowerRenderHelper.texture("momentum_wheel_rim");
    private static final Identifier BLUR_TEX = PowerRenderHelper.texture("momentum_wheel_rim_blur");
    private static final Identifier TREAD_TEX = PowerRenderHelper.texture("momentum_wheel_tread");

    /** Rim radius and the two disc planes either side of the hub drum (model x 6..10). */
    private static final float RADIUS = 6.5F / 16.0F;
    private static final float DISC_X0 = 5.9F / 16.0F;
    private static final float DISC_X1 = 10.1F / 16.0F;
    private static final float CENTRE = 8.0F / 16.0F;
    private static final int TREAD_SEGMENTS = 12;
    /** Degrees per tick for each charge bucket: bucket 20 is 1.2 turns a second. */
    private static final float SPIN_PER_BUCKET = 1.08F;
    /** From this bucket (70 %) the wheel shows its blur disc. */
    private static final int BLUR_BUCKET = 14;
    private static final double FULL_DETAIL = 24.0D;
    private static final double REDUCED_DETAIL = 48.0D;

    static final int LOD_FULL = 0;
    static final int LOD_REDUCED = 1;
    static final int LOD_NONE = 2;

    /** Render state: resolved angle, tier and lamp; nothing is read at submit time. */
    public static class State extends BlockEntityRenderState {
        float angle;
        boolean blur;
        int lod = LOD_NONE;
        Direction facing = Direction.NORTH;
        final PowerRenderHelper.Lamp lamp = new PowerRenderHelper.Lamp();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** The block itself: the wheel never leaves its cage. NeoForge-only frustum hook. */
    public AABB getRenderBoundingBox(MomentumWheelBlockEntity wheel) {
        return new AABB(wheel.getBlockPos());
    }

    @Override
    public void extractRenderState(MomentumWheelBlockEntity wheel, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(wheel, state, partialTick, cameraPos, breakProgress);
        Level level = wheel.getLevel();
        state.facing = wheel.getBlockState().getValue(NeroTechMachineBlock.FACING);
        double distance = Math.sqrt(wheel.getBlockPos().distToCenterSqr(cameraPos.x, cameraPos.y, cameraPos.z));
        state.lod = distance <= FULL_DETAIL ? LOD_FULL : distance <= REDUCED_DETAIL ? LOD_REDUCED : LOD_NONE;
        PowerRenderHelper.lampExtract(state.lamp, wheel.readoutCode(), false, level, partialTick);
        int bucket = Mth.clamp(wheel.chargeBucket(), 0, 20);
        state.blur = bucket >= BLUR_BUCKET && state.lamp.animate;
        // Explicit null check (not folded into a flag) so ecj's null-flow analysis can track it.
        if (level == null || !state.lamp.animate) {
            state.angle = (float) wheel.displayPos; // parked frame
            return;
        }
        long tick = level.getGameTime();
        float speed = bucket * SPIN_PER_BUCKET;
        if (!wheel.displayInit) {
            wheel.displayPos = wheel.prevDisplayPos = 0.0D;
            wheel.displayInit = true;
            wheel.displayLastTick = tick;
        } else if (tick != wheel.displayLastTick) {
            wheel.displayLastTick = tick;
            wheel.prevDisplayPos = wheel.displayPos;
            wheel.displayPos = (wheel.displayPos + speed) % 360.0D;
        }
        double prev = wheel.prevDisplayPos;
        double cur = wheel.displayPos;
        if (cur < prev) {
            cur += 360.0D; // wrapped this tick: lerp the long way round, not backwards
        }
        state.angle = (float) ((prev + (cur - prev) * partialTick) % 360.0D);
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
        PowerRenderHelper.lampSubmit(state.lamp, poseStack, collector);

        // The wheel: spun about the model's X axis through the block centre.
        poseStack.pushPose();
        poseStack.translate(0.0F, CENTRE, CENTRE);
        //? if >=26.3 {
        /*poseStack.rotate(Axis.XP.rotationDegrees(state.angle));
        *///?} else {
        poseStack.mulPose(Axis.XP.rotationDegrees(state.angle));
        //?}
        collector.order(1).submitCustomGeometry(poseStack,
                RenderTypes.entityCutout(state.blur ? BLUR_TEX : RIM_TEX), (pose, c) -> {
                    disc(c, pose, light, DISC_X0, -1.0F);
                    disc(c, pose, light, DISC_X1, 1.0F);
                });
        if (state.lod == LOD_FULL) {
            collector.order(2).submitCustomGeometry(poseStack, RenderTypes.entityCutout(TREAD_TEX), (pose, c) -> {
                for (int i = 0; i < TREAD_SEGMENTS; i++) {
                    float a0 = (float) (i * 2.0D * Math.PI / TREAD_SEGMENTS);
                    float a1 = (float) ((i + 1) * 2.0D * Math.PI / TREAD_SEGMENTS);
                    float am = (a0 + a1) / 2.0F;
                    float y0 = RADIUS * Mth.cos(a0);
                    float z0 = RADIUS * Mth.sin(a0);
                    float y1 = RADIUS * Mth.cos(a1);
                    float z1 = RADIUS * Mth.sin(a1);
                    // Segment 0 shows the half of the sheet with the balance weight, the rest the plain half.
                    float u0 = i == 0 ? 0.5F : 0.0F;
                    float u1 = u0 + 0.5F;
                    PowerRenderHelper.face(c, pose, light, 0xFFFFFF, 0.0F, Mth.cos(am), Mth.sin(am),
                            DISC_X0, y0, z0, u0, 0.0F,
                            DISC_X0, y1, z1, u0, 1.0F,
                            DISC_X1, y1, z1, u1, 1.0F,
                            DISC_X1, y0, z0, u1, 0.0F);
                }
            });
        }
        poseStack.popPose();
        poseStack.popPose();
    }

    /** One rim disc in the plane {@code x}, centred on the (already translated) axle. */
    private static void disc(com.mojang.blaze3d.vertex.VertexConsumer c, PoseStack.Pose pose, int light,
            float x, float nx) {
        PowerRenderHelper.face(c, pose, light, 0xFFFFFF, nx, 0.0F, 0.0F,
                x, -RADIUS, -RADIUS, 0.0F, 1.0F,
                x, RADIUS, -RADIUS, 0.0F, 0.0F,
                x, RADIUS, RADIUS, 1.0F, 0.0F,
                x, -RADIUS, RADIUS, 1.0F, 1.0F);
    }
}
