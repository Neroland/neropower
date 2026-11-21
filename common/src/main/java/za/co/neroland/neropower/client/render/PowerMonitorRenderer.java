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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.grid.MonitorRing;
import za.co.neroland.neropower.grid.PowerMonitorBlockEntity;

/**
 * Power Monitor BER: three line traces on the wall plate's screen, drawn from the synced 60-sample
 * ring: power in (amber), power out (the cyan tell colour) and stored (white). The newest sample is
 * at the right edge, so the picture rolls left as samples arrive. In and out share one scale (the
 * largest flow in the window); stored is its own 0..100 %.
 *
 * <p>It is a line, not bars: each of the twenty columns draws only the short stroke that joins its
 * value to the one before. With nothing behind the plate the screen shows a flat dashed line. The ring
 * reaches a client only within sixteen blocks, so past that the last picture is shown dimmed; past 24
 * blocks only the lamp is drawn, past 48 nothing.
 *
 * <p>Reads only {@code ring()}, {@code hasSource()} and {@code readoutCode()}: flows and a fill, no
 * position of any kind.
 */
public class PowerMonitorRenderer
        implements BlockEntityRenderer<PowerMonitorBlockEntity, PowerMonitorRenderer.State> {

    /** The lamp socket on the key strip (the model's {@code status_socket} element). */
    private static final PowerRenderHelper.LampSocket SOCKET =
            PowerRenderHelper.LampSocket.model(13.0F, 1.0F, 13.5F, 15.0F, 3.0F, 14.0F);

    static final int COLUMNS = 20;
    /** The trace area on the screen (model x 2.5..13.5, y 5..13), just proud of the plate at z 15. */
    private static final float AREA_LEFT = 13.5F / 16.0F;
    private static final float AREA_WIDTH = 11.0F / 16.0F;
    private static final float AREA_Y0 = 5.0F / 16.0F;
    private static final float AREA_HEIGHT = 8.0F / 16.0F;
    private static final float STROKE = 0.5F / 16.0F;
    private static final float SCREEN_Z = 15.0F / 16.0F - 0.004F;
    private static final float LAYER = 0.002F;
    /** The lamp sprite's plain middle, used as a white texel. */
    private static final float UV0 = 0.25F;
    private static final float UV1 = 0.75F;
    private static final int IN_RGB = 0xE0B33A;
    private static final int OUT_RGB = PowerRenderHelper.CYAN_RGB;
    private static final int STORED_RGB = PowerRenderHelper.LAMP_TRIP_RGB;
    private static final float STALE_DIM = 0.45F;
    private static final double LIVE_RANGE = PowerMonitorBlockEntity.SYNC_RANGE;
    private static final double FULL_DETAIL = 24.0D;
    private static final double REDUCED_DETAIL = 48.0D;

    /** Render state: the three traces as 0..1 heights per column ({@code -1} = no sample yet). */
    public static class State extends BlockEntityRenderState {
        boolean visible;
        boolean traces;
        boolean source;
        float gain = 1.0F;
        final float[] in = new float[COLUMNS];
        final float[] out = new float[COLUMNS];
        final float[] stored = new float[COLUMNS];
        Direction facing = Direction.NORTH;
        final PowerRenderHelper.Lamp lamp = new PowerRenderHelper.Lamp();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** The block itself. NeoForge-only frustum hook, inert non-override on Fabric/Forge. */
    public AABB getRenderBoundingBox(PowerMonitorBlockEntity monitor) {
        return new AABB(monitor.getBlockPos());
    }

    @Override
    public void extractRenderState(PowerMonitorBlockEntity monitor, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(monitor, state, partialTick, cameraPos, breakProgress);
        Level level = monitor.getLevel();
        state.facing = monitor.getBlockState().getValue(NeroTechMachineBlock.FACING);
        double distance = Math.sqrt(monitor.getBlockPos().distToCenterSqr(cameraPos.x, cameraPos.y, cameraPos.z));
        state.visible = distance <= REDUCED_DETAIL;
        state.traces = distance <= FULL_DETAIL;
        state.gain = distance <= LIVE_RANGE ? 1.0F : STALE_DIM;
        state.source = monitor.hasSource();
        PowerRenderHelper.lampExtract(state.lamp, monitor.readoutCode(), false, level, partialTick);
        if (!state.traces) {
            return;
        }
        fill(monitor.ring(), state.in, state.out, state.stored);
    }

    /**
     * Resample the ring into {@link #COLUMNS} columns, newest at the right. Fewer samples than columns
     * are right-aligned one to a column; more are averaged in equal groups.
     */
    static void fill(MonitorRing ring, float[] in, float[] out, float[] stored) {
        int n = ring.size();
        long peak = 1L;
        for (int i = 0; i < n; i++) {
            peak = Math.max(peak, Math.max(ring.in(i), ring.out(i)));
        }
        for (int col = 0; col < COLUMNS; col++) {
            int from;
            int to;
            if (n >= COLUMNS) {
                from = col * n / COLUMNS;
                to = Math.max(from + 1, (col + 1) * n / COLUMNS);
            } else {
                from = col - (COLUMNS - n);
                to = from + 1;
            }
            if (from < 0 || n == 0) {
                in[col] = -1.0F;
                out[col] = -1.0F;
                stored[col] = -1.0F;
                continue;
            }
            double sumIn = 0.0;
            double sumOut = 0.0;
            double sumStored = 0.0;
            for (int i = from; i < to; i++) {
                sumIn += ring.in(i);
                sumOut += ring.out(i);
                sumStored += ring.storedPermille(i);
            }
            int count = to - from;
            in[col] = (float) Math.min(1.0, sumIn / count / peak);
            out[col] = (float) Math.min(1.0, sumOut / count / peak);
            stored[col] = (float) Math.min(1.0, sumStored / count / 1000.0);
        }
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
        if (state.traces) {
            float gain = state.gain;
            boolean source = state.source;
            float[] in = state.in.clone();
            float[] out = state.out.clone();
            float[] stored = state.stored.clone();
            collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(PowerRenderHelper.LAMP_TEX),
                    (pose, c) -> {
                        if (!source) {
                            dashes(c, pose, PowerRenderHelper.dim(STORED_RGB, 0.5F * gain));
                            return;
                        }
                        trace(c, pose, stored, PowerRenderHelper.dim(STORED_RGB, gain), SCREEN_Z);
                        trace(c, pose, out, PowerRenderHelper.dim(OUT_RGB, gain), SCREEN_Z - LAYER);
                        trace(c, pose, in, PowerRenderHelper.dim(IN_RGB, gain), SCREEN_Z - 2.0F * LAYER);
                    });
        }
        poseStack.popPose();
    }

    /** One line trace: per column, the stroke from the previous column's level to this one's. */
    private static void trace(VertexConsumer c, PoseStack.Pose pose, float[] values, int rgb, float z) {
        float previous = -1.0F;
        float width = AREA_WIDTH / COLUMNS;
        for (int col = 0; col < COLUMNS; col++) {
            float value = values[col];
            if (value < 0.0F) {
                previous = -1.0F;
                continue;
            }
            float y = AREA_Y0 + value * (AREA_HEIGHT - STROKE);
            float from = previous < 0.0F ? y : previous;
            float low = Math.min(from, y);
            float high = Math.max(from, y) + STROKE;
            // Column 0 is the oldest, at the viewer's left: the high-x side of a north-facing plate.
            float x1 = AREA_LEFT - col * width;
            float x0 = x1 - width;
            cell(c, pose, rgb, x0, low, x1, high, z);
            previous = y;
        }
    }

    /** The no-source read: a flat dashed line across the middle of the screen. */
    private static void dashes(VertexConsumer c, PoseStack.Pose pose, int rgb) {
        float width = AREA_WIDTH / COLUMNS;
        float y = AREA_Y0 + (AREA_HEIGHT - STROKE) * 0.5F;
        for (int col = 0; col < COLUMNS; col += 2) {
            float x1 = AREA_LEFT - col * width;
            cell(c, pose, rgb, x1 - width, y, x1, y + STROKE, SCREEN_Z);
        }
    }

    private static void cell(VertexConsumer c, PoseStack.Pose pose, int rgb,
            float x0, float y0, float x1, float y1, float z) {
        PowerRenderHelper.face(c, pose, PowerRenderHelper.FULL_BRIGHT, rgb, 0, 0, -1,
                x0, y0, z, UV1, UV1, x0, y1, z, UV1, UV0, x1, y1, z, UV0, UV0, x1, y0, z, UV0, UV1);
    }
}
