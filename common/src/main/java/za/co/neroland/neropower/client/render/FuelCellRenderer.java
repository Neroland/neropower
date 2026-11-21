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

import za.co.neroland.neropower.generation.FuelCellBlockEntity;

/**
 * Hydrogen Fuel Cell BER: the light that travels along the nine plates, the water bead and the lamp.
 *
 * <p>Fed with oxygen, a slow amber wave passes along the stack. Breathing air at half rate, every
 * second plate is lit and holds steady, so the two modes differ in shape and motion, not colour.
 * With no oxygen at all, or stalled for any other reason, the stack is dark and the lamp's double
 * blink speaks. Extraction reads only NeroTech's {@code renderActive()}, {@code renderOxygenMode()}
 * and {@code renderReadout()}. Up to 24 blocks the plates light on the front and top and a bead
 * drips; up to 48 only the front; past that nothing. With animations off the wave is a steady row.</p>
 */
public class FuelCellRenderer implements BlockEntityRenderer<FuelCellBlockEntity, FuelCellRenderer.State> {

    private static final Identifier PLATE_TEX = PowerRenderHelper.texture("fuel_cell_plate");

    private static final PowerRenderHelper.LampSocket SOCKET =
            PowerRenderHelper.LampSocket.model(13.0F, 11.0F, 0.5F, 15.0F, 13.0F, 1.0F);
    private static final int PLATES = 9;
    /** Plate i is centred on model x 4 + i; even plates are the raised ones. */
    private static final float PLATE_X = 4.0F / 16.0F;
    private static final float PLATE_PITCH = 1.0F / 16.0F;
    private static final float PLATE_HALF = 0.5F / 16.0F;
    private static final float GAP = 0.004F;
    private static final float RAISED_FRONT = 2.0F / 16.0F;
    private static final float RAISED_Y0 = 2.0F / 16.0F;
    private static final float RAISED_Y1 = 14.0F / 16.0F;
    private static final float CORE_FRONT = 3.0F / 16.0F;
    private static final float CORE_Y0 = 3.0F / 16.0F;
    private static final float CORE_Y1 = 13.0F / 16.0F;
    /** One pass of the wave, in ticks: divides the lamp clock's wrap, so it never jumps. */
    private static final float WAVE_TICKS = 48.0F;
    private static final float WAVE_REACH = 1.6F;
    private static final int DRIP_RGB = 0x6FA3D8;
    private static final float DRIP_TICKS = 30.0F;
    private static final float DRIP_HALF = 0.5F / 16.0F;

    private static final int OXYGEN_FED = 0;
    private static final int OXYGEN_AIR = 1;
    private static final double FULL_DETAIL_SQ = 24.0D * 24.0D;
    private static final double REDUCED_SQ = 48.0D * 48.0D;

    private static final int DETAIL_FULL = 0;
    private static final int DETAIL_REDUCED = 1;
    private static final int DETAIL_NONE = 2;

    /** Render state: everything resolved at extraction. */
    public static class State extends BlockEntityRenderState {
        final PowerRenderHelper.Lamp lamp = new PowerRenderHelper.Lamp();
        final int[] plateRgb = new int[PLATES];
        final boolean[] plateLit = new boolean[PLATES];
        Direction facing = Direction.NORTH;
        boolean anyLit;
        boolean drips;
        int detail = DETAIL_FULL;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** Everything stays inside the block. NeoForge-only frustum hook, inert elsewhere. */
    public AABB getRenderBoundingBox(FuelCellBlockEntity cell) {
        return new AABB(cell.getBlockPos()).inflate(0.5);
    }

    @Override
    public void extractRenderState(FuelCellBlockEntity cell, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(cell, state, partialTick, cameraPos, breakProgress);
        state.facing = cell.getBlockState().getValue(NeroTechMachineBlock.FACING);
        int readout = cell.renderReadout();
        PowerRenderHelper.lampExtract(state.lamp, readout, true, cell.getLevel(), partialTick);
        int oxygen = cell.renderOxygenMode();
        boolean running = cell.renderActive()
                && (readout == PowerRenderHelper.READOUT_WORK || readout == PowerRenderHelper.READOUT_HOT);
        state.anyLit = false;
        // The wave's centre runs from just before the first plate to just past the last.
        float centre = (state.lamp.time % WAVE_TICKS) / WAVE_TICKS * (PLATES - 1 + 2.0F * WAVE_REACH) - WAVE_REACH;
        for (int i = 0; i < PLATES; i++) {
            boolean lit = false;
            int rgb = PowerRenderHelper.LAMP_AMBER_RGB;
            if (running && oxygen == OXYGEN_AIR) {
                lit = (i & 1) == 0;
            } else if (running && oxygen == OXYGEN_FED) {
                if (state.lamp.animate) {
                    float near = 1.0F - Math.abs(i - centre) / WAVE_REACH;
                    lit = near > 0.0F;
                    rgb = PowerRenderHelper.lerpColor(near, PowerRenderHelper.LAMP_AMBER_RGB,
                            PowerRenderHelper.LAMP_HOT_RGB);
                } else {
                    lit = true;
                }
            }
            state.plateLit[i] = lit;
            state.plateRgb[i] = rgb;
            state.anyLit |= lit;
        }
        state.drips = running && state.lamp.animate;

        BlockPos pos = cell.getBlockPos();
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
        boolean full = state.detail == DETAIL_FULL;
        poseStack.pushPose();
        PowerRenderHelper.rotateToFacing(poseStack, state.facing);
        PowerRenderHelper.lampSubmit(state.lamp, poseStack, collector, SOCKET);

        if (state.anyLit) {
            boolean[] lit = state.plateLit;
            int[] rgb = state.plateRgb;
            collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(PLATE_TEX), (pose, c) -> {
                for (int i = 0; i < PLATES; i++) {
                    if (lit[i]) {
                        plate(c, pose, light, rgb[i], i, full);
                    }
                }
            });
        }

        if (state.drips && full) {
            float phase = (state.lamp.time % DRIP_TICKS) / DRIP_TICKS;
            float y = CORE_Y0 - (2.0F / 16.0F) * phase * phase;
            collector.order(2).submitCustomGeometry(poseStack,
                    RenderTypes.entityCutout(PowerRenderHelper.LAMP_TEX),
                    (pose, c) -> PowerRenderHelper.box(c, pose, light, DRIP_RGB, 0.5F - DRIP_HALF,
                            y - DRIP_HALF * 2.0F, CORE_FRONT - DRIP_HALF, 0.5F + DRIP_HALF, y,
                            CORE_FRONT + DRIP_HALF));
        }
        poseStack.popPose();
    }

    /** Light one plate: its front edge and, close up, its top edge, a hair proud of the model. */
    private static void plate(VertexConsumer c, PoseStack.Pose pose, int light, int rgb, int index, boolean top) {
        boolean raised = (index & 1) == 0;
        float x = PLATE_X + PLATE_PITCH * index;
        float x0 = x - PLATE_HALF;
        float x1 = x + PLATE_HALF;
        float z = (raised ? RAISED_FRONT : CORE_FRONT) - GAP;
        float y0 = raised ? RAISED_Y0 : CORE_Y0;
        float y1 = raised ? RAISED_Y1 : CORE_Y1;
        PowerRenderHelper.face(c, pose, light, rgb, 0, 0, -1,
                x0, y0, z, 1, 1, x0, y1, z, 1, 0, x1, y1, z, 0, 0, x1, y0, z, 0, 1);
        if (top) {
            float yt = y1 + GAP;
            float z1 = 1.0F - z;
            PowerRenderHelper.face(c, pose, light, rgb, 0, 1, 0,
                    x0, yt, z, 0, 0, x0, yt, z1, 0, 1, x1, yt, z1, 1, 1, x1, yt, z, 1, 0);
        }
    }
}
