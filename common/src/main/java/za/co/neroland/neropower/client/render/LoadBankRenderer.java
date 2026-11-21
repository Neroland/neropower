package za.co.neroland.neropower.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.grid.LoadBankBlock;
import za.co.neroland.neropower.grid.LoadBankBlockEntity;

/**
 * Load Bank BER: the status lamp on the frame post, and the heat shimmer over the exhaust cowl. The
 * glow of the resistor elements is the block model ({@code heat} 0..4); the shimmer starts at heat
 * step 2, which is where the bank's sizzle starts too.
 *
 * <p>Reads only {@code readoutCode()} and the block state. The shimmer shows within 24 blocks and
 * never with animations off; to 48 only the lamp shows; beyond that nothing.
 */
public class LoadBankRenderer implements BlockEntityRenderer<LoadBankBlockEntity, LoadBankRenderer.State> {

    /** The lamp socket on the left frame post (the model's {@code status_socket} element). */
    private static final PowerRenderHelper.LampSocket SOCKET =
            PowerRenderHelper.LampSocket.model(14.0F, 8.5F, -0.5F, 16.0F, 10.5F, 0.0F);

    /** The cowl cap's footprint and top (model x 2.5..13.5, z 3..15, y 15). */
    private static final float COWL_X0 = 3.5F / 16.0F;
    private static final float COWL_Z0 = 4.0F / 16.0F;
    private static final float COWL_X1 = 12.5F / 16.0F;
    private static final float COWL_Z1 = 14.0F / 16.0F;
    private static final float COWL_TOP = 15.0F / 16.0F;
    private static final int SHIMMER_STEP = 2;
    private static final double FULL_DETAIL = 24.0D;
    private static final double REDUCED_DETAIL = 48.0D;

    /** Render state: the lamp, and a second lamp record that drives the shared shimmer. */
    public static class State extends BlockEntityRenderState {
        boolean visible;
        boolean shimmer;
        Direction facing = Direction.NORTH;
        final PowerRenderHelper.Lamp lamp = new PowerRenderHelper.Lamp();
        final PowerRenderHelper.Lamp heat = new PowerRenderHelper.Lamp();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** The block and the half block of shimmer above it. NeoForge-only frustum hook. */
    public AABB getRenderBoundingBox(LoadBankBlockEntity bank) {
        return new AABB(bank.getBlockPos()).expandTowards(0.0, 0.5, 0.0);
    }

    @Override
    public void extractRenderState(LoadBankBlockEntity bank, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(bank, state, partialTick, cameraPos, breakProgress);
        Level level = bank.getLevel();
        BlockState blockState = bank.getBlockState();
        state.facing = blockState.getValue(NeroTechMachineBlock.FACING);
        double distance = Math.sqrt(bank.getBlockPos().distToCenterSqr(cameraPos.x, cameraPos.y, cameraPos.z));
        state.visible = distance <= REDUCED_DETAIL;
        int step = blockState.hasProperty(LoadBankBlock.HEAT) ? blockState.getValue(LoadBankBlock.HEAT) : 0;
        PowerRenderHelper.lampExtract(state.lamp, bank.readoutCode(), step >= 4, level, partialTick);
        state.shimmer = step >= SHIMMER_STEP && distance <= FULL_DETAIL;
        // The shared shimmer draws for a hot readout; the bank shimmers from its heat step instead.
        PowerRenderHelper.lampExtract(state.heat, PowerRenderHelper.READOUT_HOT, false, level, partialTick);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        if (!state.visible) {
            return;
        }
        poseStack.pushPose();
        PowerRenderHelper.rotateToFacing(poseStack, state.facing);
        PowerRenderHelper.lampSubmit(state.lamp, poseStack, collector, SOCKET);
        if (state.shimmer) {
            PowerRenderHelper.shimmerSubmit(state.heat, poseStack, collector,
                    COWL_X0, COWL_Z0, COWL_X1, COWL_Z1, COWL_TOP);
        }
        poseStack.popPose();
    }
}
