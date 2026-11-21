package za.co.neroland.neropower.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.backend.SpentFuelPoolBlockEntity;

/**
 * Spent Fuel Rack BER: one rod head in each occupied socket, the status lamp, the vapour column of
 * an overheated rack and the sparks of a failed one.
 *
 * <p>A rod head's colour is its synced cool step: white-gold just out of the core, then hot, then
 * amber, and plain dull metal once it has cooled, so the four steps also differ in brightness
 * alone. Extraction reads only {@code renderCoolStep(slot)}, {@code renderWaterContacts()} and
 * {@code renderReadout()}. Up to 24 blocks the heads are boxes, up to 48 they are flat caps, past
 * that the baked model carries the look. With animations off nothing sparks.</p>
 */
public class SpentFuelPoolRenderer
        implements BlockEntityRenderer<SpentFuelPoolBlockEntity, SpentFuelPoolRenderer.State> {

    private static final Identifier ROD_TEX = PowerRenderHelper.texture("spent_fuel_pool_rod_head");

    private static final int SLOTS = 4;
    /** Socket centres (model pixels x, z): the 2 x 2 cells of the block model. */
    private static final float[][] CELLS = {{4.5F, 4.5F}, {11.5F, 4.5F}, {4.5F, 11.5F}, {11.5F, 11.5F}};
    private static final float ROD_HALF = 1.5F / 16.0F;
    private static final float ROD_Y0 = 4.0F / 16.0F + 0.002F;
    private static final float ROD_Y1 = 10.0F / 16.0F;
    /** Rod-head tints by cool step 1..4 (index 0 unused): cooled metal, amber, hot, white-gold. */
    private static final int[] STEP_RGB = {0, 0x4A4A48, 0xCC7012, 0xFFA028, 0xFFE2A0};
    private static final PowerRenderHelper.LampSocket SOCKET =
            PowerRenderHelper.LampSocket.model(13.0F, 8.0F, -0.5F, 15.0F, 10.0F, 0.0F);
    /** The rack's rim: shimmer and sparks start here. */
    private static final float RIM_Y = 12.0F / 16.0F;
    private static final float SPARK_HALF = 0.6F / 16.0F;
    private static final double FULL_DETAIL_SQ = 24.0D * 24.0D;
    private static final double REDUCED_SQ = 48.0D * 48.0D;

    private static final int DETAIL_FULL = 0;
    private static final int DETAIL_REDUCED = 1;
    private static final int DETAIL_NONE = 2;

    /** Render state: everything resolved at extraction. */
    public static class State extends BlockEntityRenderState {
        final PowerRenderHelper.Lamp lamp = new PowerRenderHelper.Lamp();
        final int[] step = new int[SLOTS];
        Direction facing = Direction.NORTH;
        int detail = DETAIL_FULL;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** Shimmer and sparks rise half a block above the rack. NeoForge-only frustum hook, inert elsewhere. */
    public AABB getRenderBoundingBox(SpentFuelPoolBlockEntity rack) {
        return new AABB(rack.getBlockPos()).inflate(0.5);
    }

    @Override
    public void extractRenderState(SpentFuelPoolBlockEntity rack, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(rack, state, partialTick, cameraPos, breakProgress);
        state.facing = rack.getBlockState().getValue(NeroTechMachineBlock.FACING);
        for (int slot = 0; slot < SLOTS; slot++) {
            state.step[slot] = Mth.clamp(rack.renderCoolStep(slot), 0, 4);
        }
        PowerRenderHelper.lampExtract(state.lamp, rack.renderReadout(), true, rack.getLevel(), partialTick);
        BlockPos pos = rack.getBlockPos();
        double dx = pos.getX() + 0.5D - cameraPos.x;
        double dy = pos.getY() + 0.5D - cameraPos.y;
        double dz = pos.getZ() + 0.5D - cameraPos.z;
        double distSq = dx * dx + dy * dy + dz * dz;
        state.detail = distSq <= FULL_DETAIL_SQ ? DETAIL_FULL : distSq <= REDUCED_SQ ? DETAIL_REDUCED : DETAIL_NONE;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        if (state.detail == DETAIL_NONE) {
            return;
        }
        int light = PowerRenderHelper.FULL_BRIGHT;
        boolean boxes = state.detail == DETAIL_FULL;
        int[] step = state.step;
        poseStack.pushPose();
        PowerRenderHelper.rotateToFacing(poseStack, state.facing);
        PowerRenderHelper.lampSubmit(state.lamp, poseStack, collector, SOCKET);

        if (step[0] + step[1] + step[2] + step[3] > 0) {
            collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(ROD_TEX),
                    (pose, c) -> {
                        for (int slot = 0; slot < SLOTS; slot++) {
                            if (step[slot] > 0) {
                                rod(c, pose, light, STEP_RGB[step[slot]], CELLS[slot][0] / 16.0F,
                                        CELLS[slot][1] / 16.0F, boxes);
                            }
                        }
                    });
        }

        if (boxes) {
            PowerRenderHelper.shimmerSubmit(state.lamp, poseStack, collector, RIM_Y);
            if (state.lamp.readout == PowerRenderHelper.READOUT_FAIL && state.lamp.animate) {
                float time = state.lamp.time;
                collector.order(2).submitCustomGeometry(poseStack,
                        RenderTypes.entityCutout(PowerRenderHelper.LAMP_TEX), (pose, c) -> {
                            for (int i = 0; i < SLOTS; i++) {
                                spark(c, pose, light, CELLS[i][0] / 16.0F, CELLS[i][1] / 16.0F, time + i * 7.0F);
                            }
                        });
            }
        }
        poseStack.popPose();
    }

    /** One rod head: a box in its socket, or only its cap at a distance. */
    private static void rod(VertexConsumer c, PoseStack.Pose pose, int light, int rgb, float x, float z,
            boolean box) {
        float x0 = x - ROD_HALF;
        float x1 = x + ROD_HALF;
        float z0 = z - ROD_HALF;
        float z1 = z + ROD_HALF;
        if (box) {
            PowerRenderHelper.box(c, pose, light, rgb, x0, ROD_Y0, z0, x1, ROD_Y1, z1);
            return;
        }
        PowerRenderHelper.face(c, pose, light, rgb, 0, 1, 0,
                x0, ROD_Y1, z0, 0, 0, x0, ROD_Y1, z1, 0, 1, x1, ROD_Y1, z1, 1, 1, x1, ROD_Y1, z0, 1, 0);
    }

    /** A lime spark hopping above a socket: up for six ticks of every twenty-eight, then gone. */
    private static void spark(VertexConsumer c, PoseStack.Pose pose, int light, float x, float z, float time) {
        float phase = time % 28.0F;
        if (phase >= 6.0F) {
            return;
        }
        float y = RIM_Y + (1.0F + phase) / 16.0F;
        PowerRenderHelper.box(c, pose, light, PowerRenderHelper.RAD_LIME_RGB, x - SPARK_HALF, y - SPARK_HALF,
                z - SPARK_HALF, x + SPARK_HALF, y + SPARK_HALF, z + SPARK_HALF);
    }
}
