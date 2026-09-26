package za.co.neroland.neropower.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerotech.config.NeroTechConfig;
import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.storage.BatteryCellBlock;
import za.co.neroland.neropower.storage.BatteryCellBlockEntity;
import za.co.neroland.neropower.storage.PoolMath;

/**
 * Battery Cell BER: a glowing charge strip along the foot of the recessed front window whose length
 * follows the {@code charge} block state (0..4 buckets) and whose colour runs red → amber → green
 * with it — the in-world twin of the GUI gauge. Nothing is synced for this: the block state is the
 * whole read surface (the cell's tick keeps it in step with the buffer).
 *
 * <p>The strip is static indicator geometry (always drawn while charged); with animations on it
 * breathes faintly so a charged wall reads as live. {@code renderAnimationsEnabled=false} holds it
 * at full brightness.</p>
 */
public class BatteryCellRenderer
        implements BlockEntityRenderer<BatteryCellBlockEntity, BatteryCellRenderer.State> {

    private static final Identifier BAR_TEX = PowerRenderHelper.texture("battery_cell_bar");

    /** Strip depth: inside the front recess (frame z0..2, window face at z=2). */
    private static final float BAR_Z = 1.5F / 16.0F;
    /** Strip extent along the foot of the window (window = x2..14, y2..12). */
    private static final float BAR_X0 = 3.0F / 16.0F;
    private static final float BAR_X1 = 13.0F / 16.0F;
    private static final float BAR_Y0 = 2.5F / 16.0F;
    private static final float BAR_Y1 = 4.0F / 16.0F;
    /** Breathing period (radians per tick). */
    private static final float BREATHE_SPEED = 0.08F;

    /** Render state: the resolved charge fraction + brightness, nothing read at submit time. */
    public static class State extends BlockEntityRenderState {
        /** 0..1 stored-charge fraction from the block-state bucket (0 = nothing to draw). */
        float charge;
        float brightness;
        Direction facing = Direction.NORTH;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** One-block pad; NeoForge-only frustum hook, inert non-override on Fabric/Forge (NeroTech recipe). */
    public AABB getRenderBoundingBox(BatteryCellBlockEntity cell) {
        return new AABB(cell.getBlockPos()).inflate(1.0);
    }

    @Override
    public void extractRenderState(BatteryCellBlockEntity cell, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(cell, state, partialTick, cameraPos, breakProgress);
        Level level = cell.getLevel();
        BlockState blockState = cell.getBlockState();
        state.facing = blockState.getValue(NeroTechMachineBlock.FACING);
        int bucket = blockState.hasProperty(BatteryCellBlock.CHARGE)
                ? blockState.getValue(BatteryCellBlock.CHARGE) : 0;
        state.charge = Mth.clamp(bucket / (float) PoolMath.CHARGE_LEVELS, 0.0F, 1.0F);
        // Explicit null check (not folded into a flag) so ecj's null-flow analysis can track it.
        if (level == null || !NeroTechConfig.renderAnimationsEnabled()) {
            state.brightness = 1.0F; // static frame
            return;
        }
        float now = level.getGameTime() + partialTick;
        state.brightness = 0.85F + 0.15F * Mth.sin(now * BREATHE_SPEED);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        if (state.charge <= 0.0F) {
            return; // empty: the dark window is the whole read
        }
        int light = PowerRenderHelper.FULL_BRIGHT;
        int rgb = PowerRenderHelper.dim(PowerRenderHelper.chargeColor(state.charge), state.brightness);
        float x1 = BAR_X0 + (BAR_X1 - BAR_X0) * state.charge;
        poseStack.pushPose();
        PowerRenderHelper.rotateToFacing(poseStack, state.facing);
        collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(BAR_TEX),
                (pose, c) -> PowerRenderHelper.face(c, pose, light, rgb, 0, 0, -1,
                        BAR_X0, BAR_Y0, BAR_Z, 0, 1,
                        BAR_X0, BAR_Y1, BAR_Z, 0, 0,
                        x1, BAR_Y1, BAR_Z, state.charge, 0,
                        x1, BAR_Y0, BAR_Z, state.charge, 1));
        poseStack.popPose();
    }
}
