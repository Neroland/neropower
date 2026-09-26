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

import za.co.neroland.nerotech.config.NeroTechConfig;
import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.environmental.StirlingGeneratorBlockEntity;

/**
 * Stirling Generator BER: a {@code stirling_flywheel} disc turning in the front cut-out (the block
 * model leaves x3..13 × y3..13 × z0..4 open for exactly this) at a speed proportional to the synced
 * heat drawn last tick, with a piston nub reciprocating beside it — the wheel is always drawn (a
 * parked flywheel is still a flywheel); it only moves while heat is being drawn.
 *
 * <p>The wheel angle accumulates ONCE per game tick through the base's client display fields
 * (NeroTech's quarry easing recipe: {@code displayPos} advances per tick, {@code prevDisplayPos}
 * is the lerp partner), so a bucket change speeds the wheel up rather than snapping it. Sync
 * discipline: extraction reads only {@code drawnFraction()} / {@code renderActive()};
 * {@code renderAnimationsEnabled=false} parks the wheel as a static frame.</p>
 */
public class StirlingRenderer
        implements BlockEntityRenderer<StirlingGeneratorBlockEntity, StirlingRenderer.State> {

    private static final Identifier WHEEL_TEX = PowerRenderHelper.texture("stirling_flywheel");

    /** Wheel plane depth inside the cut-out (z0..4). */
    private static final float WHEEL_Z = 2.0F / 16.0F;
    /** Wheel centre (the cut-out centre, x8 y8) and half-size (a 9×9 px disc in the 10×10 window). */
    private static final float CENTRE = 8.0F / 16.0F;
    private static final float WHEEL_R = 4.5F / 16.0F;
    /** Piston nub: a small box at the cut-out's left edge, stroking up and down with the wheel. */
    private static final float NUB_X0 = 3.2F / 16.0F;
    private static final float NUB_X1 = 4.4F / 16.0F;
    private static final float NUB_Z0 = 1.0F / 16.0F;
    private static final float NUB_Z1 = 3.0F / 16.0F;
    private static final float NUB_H = 1.6F / 16.0F;
    private static final float NUB_Y_MID = 8.0F / 16.0F;
    private static final float NUB_STROKE = 2.5F / 16.0F;
    /** Spin (degrees per tick): a slow creep at the first bucket, up to this at full draw. */
    private static final float SPIN_MIN = 2.0F;
    private static final float SPIN_MAX = 20.0F;

    /** Render state: the resolved wheel angle + tint, nothing read at submit time. */
    public static class State extends BlockEntityRenderState {
        float angle;
        int tint = 0xFFFFFF;
        Direction facing = Direction.NORTH;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** One-block pad; NeoForge-only frustum hook, inert non-override on Fabric/Forge (NeroTech recipe). */
    public AABB getRenderBoundingBox(StirlingGeneratorBlockEntity stirling) {
        return new AABB(stirling.getBlockPos()).inflate(1.0);
    }

    @Override
    public void extractRenderState(StirlingGeneratorBlockEntity stirling, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(stirling, state, partialTick, cameraPos, breakProgress);
        Level level = stirling.getLevel();
        state.facing = stirling.getBlockState().getValue(NeroTechMachineBlock.FACING);
        float drawn = stirling.drawnFraction();
        boolean running = stirling.renderActive() && drawn > 0.0F;
        // A working wheel warms toward amber with the draw; idle stays plain.
        state.tint = running ? PowerRenderHelper.powerColor(0.3F + 0.5F * drawn) : 0xFFFFFF;
        // Explicit null check (not folded into a flag) so ecj's null-flow analysis can track it.
        if (level == null || !NeroTechConfig.renderAnimationsEnabled()) {
            state.angle = (float) stirling.displayPos; // static parked frame (last angle, or 0)
            return;
        }
        long tick = level.getGameTime();
        float speed = running ? Mth.lerp(drawn, SPIN_MIN, SPIN_MAX) : 0.0F;
        if (!stirling.displayInit) {
            stirling.displayPos = stirling.prevDisplayPos = 0.0D;
            stirling.displayInit = true;
            stirling.displayLastTick = tick;
        } else if (tick != stirling.displayLastTick) {
            // Advance ONCE per tick (FPS-independent), keeping the previous-tick angle so the lerp
            // below interpolates across the tick — smooth at any frame rate (quarry recipe).
            stirling.displayLastTick = tick;
            stirling.prevDisplayPos = stirling.displayPos;
            stirling.displayPos = (stirling.displayPos + speed) % 360.0D;
        }
        double prev = stirling.prevDisplayPos;
        double cur = stirling.displayPos;
        if (cur < prev) {
            cur += 360.0D; // wrapped this tick: lerp the long way round, not backwards
        }
        state.angle = (float) ((prev + (cur - prev) * partialTick) % 360.0D);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        int light = PowerRenderHelper.FULL_BRIGHT;
        int tint = state.tint;
        poseStack.pushPose();
        PowerRenderHelper.rotateToFacing(poseStack, state.facing);

        // Flywheel: a disc in the cut-out plane, spun about Z (the block's front axis).
        poseStack.pushPose();
        poseStack.translate(CENTRE, CENTRE, WHEEL_Z);
        //? if >=26.3 {
        /*poseStack.rotate(Axis.ZP.rotationDegrees(state.angle));
        *///?} else {
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.angle));
        //?}
        collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(WHEEL_TEX),
                (pose, c) -> PowerRenderHelper.face(c, pose, light, tint, 0, 0, -1,
                        -WHEEL_R, -WHEEL_R, 0, 0, 1,
                        -WHEEL_R, WHEEL_R, 0, 0, 0,
                        WHEEL_R, WHEEL_R, 0, 1, 0,
                        WHEEL_R, -WHEEL_R, 0, 1, 1));
        poseStack.popPose();

        // Piston nub: strokes with the wheel's crank angle beside it (the cyan family tell).
        float rad = state.angle * ((float) Math.PI / 180.0F);
        float nubY = NUB_Y_MID + Mth.sin(rad) * NUB_STROKE;
        collector.order(2).submitCustomGeometry(poseStack, RenderTypes.entityCutout(WHEEL_TEX),
                (pose, c) -> PowerRenderHelper.box(c, pose, light, PowerRenderHelper.CYAN_RGB,
                        NUB_X0, nubY - NUB_H / 2.0F, NUB_Z0, NUB_X1, nubY + NUB_H / 2.0F, NUB_Z1));
        poseStack.popPose();
    }
}
