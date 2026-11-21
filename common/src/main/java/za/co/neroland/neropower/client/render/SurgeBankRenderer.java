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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.storage.SurgeBankBlock;
import za.co.neroland.neropower.storage.SurgeBankBlockEntity;

/**
 * Surge Bank BER: the status lamp on the plinth and a short discharge between the two can rows when the
 * bank takes or gives a large step of charge. The fill itself is the block model ({@code cans} 0..6 lit
 * caps), so this renderer adds no charge read of its own.
 *
 * <p>The spark is keyed on the {@code cans} block state changing, which is a sixth of the bank's
 * capacity: no extra synced field. It shows for a few ticks within 24 blocks and never with animations
 * off. Beyond 48 blocks nothing is drawn.
 */
public class SurgeBankRenderer implements BlockEntityRenderer<SurgeBankBlockEntity, SurgeBankRenderer.State> {

    private static final Identifier SPARK_TEX = PowerRenderHelper.texture("surge_bank_spark");

    /** The lamp socket on the plinth front (the model's {@code status_socket} element). */
    private static final PowerRenderHelper.LampSocket SOCKET =
            PowerRenderHelper.LampSocket.model(13.0F, 0.5F, -0.5F, 15.0F, 2.5F, 0.0F);

    private static final int SPARK_TICKS = 5;
    private static final float SPARK_X0 = 1.0F / 16.0F;
    private static final float SPARK_X1 = 15.0F / 16.0F;
    private static final float SPARK_Y0 = 10.5F / 16.0F;
    private static final float SPARK_Y1 = 15.5F / 16.0F;
    /** The gap between the front and back can rows. */
    private static final float SPARK_Z = 8.0F / 16.0F;
    private static final double FULL_DETAIL = 24.0D;
    private static final double REDUCED_DETAIL = 48.0D;

    /** Render state: lamp, and whether (and which way round) the spark shows this frame. */
    public static class State extends BlockEntityRenderState {
        boolean visible;
        boolean spark;
        boolean flip;
        Direction facing = Direction.NORTH;
        final PowerRenderHelper.Lamp lamp = new PowerRenderHelper.Lamp();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** The block itself. NeoForge-only frustum hook, inert non-override on Fabric/Forge. */
    public AABB getRenderBoundingBox(SurgeBankBlockEntity bank) {
        return new AABB(bank.getBlockPos());
    }

    @Override
    public void extractRenderState(SurgeBankBlockEntity bank, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(bank, state, partialTick, cameraPos, breakProgress);
        Level level = bank.getLevel();
        BlockState blockState = bank.getBlockState();
        state.facing = blockState.getValue(NeroTechMachineBlock.FACING);
        double distance = Math.sqrt(bank.getBlockPos().distToCenterSqr(cameraPos.x, cameraPos.y, cameraPos.z));
        state.visible = distance <= REDUCED_DETAIL;
        PowerRenderHelper.lampExtract(state.lamp, bank.readoutCode(), false, level, partialTick);
        state.spark = false;
        if (level == null) {
            return;
        }
        // displayPos holds the last can count seen, displayLastTick the tick it last changed.
        int cans = blockState.hasProperty(SurgeBankBlock.CANS) ? blockState.getValue(SurgeBankBlock.CANS) : 0;
        long tick = level.getGameTime();
        if (!bank.displayInit) {
            bank.displayInit = true;
            bank.displayPos = cans;
            bank.displayLastTick = tick - SPARK_TICKS;
        } else if ((int) bank.displayPos != cans) {
            bank.displayPos = cans;
            bank.displayLastTick = tick;
        }
        long age = tick - bank.displayLastTick;
        state.spark = state.lamp.animate && distance <= FULL_DETAIL && age >= 0 && age < SPARK_TICKS;
        state.flip = (tick & 1L) == 0L;
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
        if (state.spark) {
            float u0 = state.flip ? 1.0F : 0.0F;
            float u1 = 1.0F - u0;
            collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(SPARK_TEX),
                    (pose, c) -> PowerRenderHelper.face(c, pose, PowerRenderHelper.FULL_BRIGHT, 0xFFFFFF, 0, 0, -1,
                            SPARK_X0, SPARK_Y0, SPARK_Z, u1, 1.0F,
                            SPARK_X0, SPARK_Y1, SPARK_Z, u1, 0.0F,
                            SPARK_X1, SPARK_Y1, SPARK_Z, u0, 0.0F,
                            SPARK_X1, SPARK_Y0, SPARK_Z, u0, 1.0F));
        }
        poseStack.popPose();
    }
}
