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

import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.backend.VitrifierBlockEntity;

/**
 * Vitrifier BER: the melt in the throat, the glass log growing in the front mould, and the status lamp.
 *
 * <p>While the machine works the throat floor glows and a hot log rises in the mould with the work
 * progress. A stalled machine keeps the part-made log where it stopped and the throat goes dark.
 * When a log is finished the mould shows it whole for two seconds, cooling from hot amber to dark
 * glass, before the next one starts. Extraction reads only NeroTech's {@code renderActive()},
 * {@code progressFraction()} and {@code renderPulse()}, and {@code renderReadout()}. Up to 24 blocks
 * everything is drawn, up to 48 only the log's front, past that nothing.</p>
 */
public class VitrifierRenderer implements BlockEntityRenderer<VitrifierBlockEntity, VitrifierRenderer.State> {

    private static final Identifier MELT_TEX = PowerRenderHelper.texture("vitrifier_melt");
    private static final Identifier LOG_TEX = PowerRenderHelper.texture("vitrifier_log");

    private static final PowerRenderHelper.LampSocket SOCKET =
            PowerRenderHelper.LampSocket.model(13.0F, 5.0F, -0.5F, 15.0F, 7.0F, 0.0F);
    /** Throat floor (the top of the model's upper step) and the melt quad just above it. */
    private static final float MELT_Y = 13.0F / 16.0F + 0.004F;
    private static final float MELT_0 = 2.0F / 16.0F;
    private static final float MELT_1 = 14.0F / 16.0F;
    /** The mould cavity is x 4..12, y 2..7, z 0..5; the log sits inside it. */
    private static final float LOG_X0 = 4.5F / 16.0F;
    private static final float LOG_X1 = 11.5F / 16.0F;
    private static final float LOG_Y0 = 2.0F / 16.0F + 0.002F;
    private static final float LOG_HEIGHT = 4.5F / 16.0F;
    private static final float LOG_Z0 = 0.5F / 16.0F;
    private static final float LOG_Z1 = 4.5F / 16.0F;
    private static final int LOG_HOT_RGB = 0xFFA028;
    private static final int LOG_COLD_RGB = 0x3A4A54;
    /** How long a finished log stays in the mould, cooling. */
    private static final float SET_TICKS = 40.0F;
    private static final double FULL_DETAIL_SQ = 24.0D * 24.0D;
    private static final double REDUCED_SQ = 48.0D * 48.0D;

    private static final int DETAIL_FULL = 0;
    private static final int DETAIL_REDUCED = 1;
    private static final int DETAIL_NONE = 2;

    /** Render state: everything resolved at extraction. */
    public static class State extends BlockEntityRenderState {
        final PowerRenderHelper.Lamp lamp = new PowerRenderHelper.Lamp();
        Direction facing = Direction.NORTH;
        boolean melt;
        /** Log height 0..1 (0 = no log) and its tint. */
        float log;
        int logRgb = LOG_HOT_RGB;
        int detail = DETAIL_FULL;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** Everything stays inside the block. NeoForge-only frustum hook, inert elsewhere. */
    public AABB getRenderBoundingBox(VitrifierBlockEntity vitrifier) {
        return new AABB(vitrifier.getBlockPos()).inflate(0.5);
    }

    @Override
    public void extractRenderState(VitrifierBlockEntity vitrifier, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(vitrifier, state, partialTick, cameraPos, breakProgress);
        Level level = vitrifier.getLevel();
        state.facing = vitrifier.getBlockState().getValue(NeroTechMachineBlock.FACING);
        int readout = vitrifier.renderReadout();
        PowerRenderHelper.lampExtract(state.lamp, readout, true, level, partialTick);
        state.melt = vitrifier.renderActive() && readout != PowerRenderHelper.READOUT_STALL;

        float progress = Mth.clamp(vitrifier.progressFraction(partialTick), 0.0F, 1.0F);
        state.log = progress;
        state.logRgb = PowerRenderHelper.lerpColor(progress * 0.35F, LOG_HOT_RGB, LOG_COLD_RGB);
        // A finished log: the pulse counter moved. Show it whole while it sets, unless the next is well under way.
        int pulse = vitrifier.renderPulse();
        if (level != null) {
            long now = level.getGameTime();
            if (vitrifier.clientSeenPulse == Integer.MIN_VALUE) {
                vitrifier.clientSeenPulse = pulse; // first sight: nothing just finished
            } else if (pulse != vitrifier.clientSeenPulse) {
                vitrifier.clientSeenPulse = pulse;
                vitrifier.clientPulseTime = now;
            }
            if (vitrifier.clientPulseTime != Long.MIN_VALUE) {
                float since = (now - vitrifier.clientPulseTime) + partialTick;
                if (since >= 0.0F && since < SET_TICKS && state.lamp.animate) {
                    state.log = 1.0F;
                    state.logRgb = PowerRenderHelper.lerpColor(since / SET_TICKS, LOG_HOT_RGB, LOG_COLD_RGB);
                }
            }
        }

        BlockPos pos = vitrifier.getBlockPos();
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

        if (state.melt && full) {
            collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(MELT_TEX),
                    (pose, c) -> PowerRenderHelper.face(c, pose, light, 0xFFFFFF, 0, 1, 0,
                            MELT_0, MELT_Y, MELT_0, 0, 0, MELT_0, MELT_Y, MELT_1, 0, 1,
                            MELT_1, MELT_Y, MELT_1, 1, 1, MELT_1, MELT_Y, MELT_0, 1, 0));
            PowerRenderHelper.shimmerSubmit(state.lamp, poseStack, collector, 1.0F);
        }

        if (state.log > 0.02F) {
            float top = LOG_Y0 + LOG_HEIGHT * state.log;
            int rgb = state.logRgb;
            collector.order(2).submitCustomGeometry(poseStack, RenderTypes.entityCutout(LOG_TEX), (pose, c) -> {
                if (full) {
                    PowerRenderHelper.box(c, pose, light, rgb, LOG_X0, LOG_Y0, LOG_Z0, LOG_X1, top, LOG_Z1);
                } else {
                    PowerRenderHelper.face(c, pose, light, rgb, 0, 0, -1,
                            LOG_X0, LOG_Y0, LOG_Z0, 1, 1, LOG_X0, top, LOG_Z0, 1, 0,
                            LOG_X1, top, LOG_Z0, 0, 0, LOG_X1, LOG_Y0, LOG_Z0, 0, 1);
                }
            });
        }
        poseStack.popPose();
    }
}
