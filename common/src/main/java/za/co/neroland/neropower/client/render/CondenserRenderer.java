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

import za.co.neroland.nerotech.api.MachineSettings;
import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.turbine.CondenserBlockEntity;
import za.co.neroland.neropower.turbine.TurbineMath;

/**
 * Steam Condenser BER: the fan in the top well, the state of the tube bundle and the status lamp.
 *
 * <p>The fan turns with the synced condensing-rate bucket while the condenser works, idles slowly
 * while it waits for exhaust steam, and stands still when there is no heat sink at all (the lamp's
 * double blink says why). Cryo-fed tubes rime over, hot tubes glow, and a working condenser sheds
 * two drips from the front tube into the tray. Extraction reads only {@code rateBucket()},
 * {@code sink()} and {@code renderReadout()}; the fan angle is integrated once per game tick. With
 * animations off the fan is parked and nothing drips.</p>
 */
public class CondenserRenderer implements BlockEntityRenderer<CondenserBlockEntity, CondenserRenderer.State> {

    private static final Identifier FAN_TEX = PowerRenderHelper.texture("condenser_fan");
    private static final Identifier OVERLAY_TEX = PowerRenderHelper.texture("condenser_tube_overlay");

    /** Tube low corners (model pixels y, z), as the block model has them; each is 14 x 3 x 3 from x 1. */
    private static final float[][] TUBES = {{5.0F, 2.0F}, {8.5F, 6.5F}, {5.0F, 11.0F}};
    private static final float TUBE_X0 = 1.0F / 16.0F;
    private static final float TUBE_X1 = 15.0F / 16.0F;
    private static final float TUBE_SIZE = 3.0F / 16.0F;
    private static final float GAP = 0.004F;
    /** One tube band of the overlay sprite: six texel rows. */
    private static final float OVERLAY_V = 6.0F / 32.0F;
    private static final float FAN_Y = 1.0F + 0.02F;
    private static final float FAN_R = 5.0F / 16.0F;
    /** Fan speed (degrees a tick): idle while waiting for steam, then by rate bucket 0..4. */
    private static final float FAN_IDLE = 2.0F;
    private static final float FAN_MIN = 8.0F;
    private static final float FAN_STEP = 5.0F;
    private static final int FROST_RGB = 0xD6E8F4;
    private static final int DRIP_RGB = 0x6FA3D8;
    private static final float DRIP_TICKS = 14.0F;
    private static final float DRIP_TOP = 5.0F / 16.0F;
    private static final float DRIP_FALL = 2.0F / 16.0F;
    private static final float DRIP_HALF = 0.5F / 16.0F;

    private static final int OVERLAY_NONE = 0;
    private static final int OVERLAY_FROST = 1;
    private static final int OVERLAY_GLOW = 2;

    /** Render state: everything resolved at extraction. */
    public static class State extends BlockEntityRenderState {
        final PowerRenderHelper.Lamp lamp = new PowerRenderHelper.Lamp();
        Direction facing = Direction.NORTH;
        float angle;
        int overlay = OVERLAY_NONE;
        boolean drips;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** The fan and shimmer sit just above the block. NeoForge-only frustum hook, inert elsewhere. */
    public AABB getRenderBoundingBox(CondenserBlockEntity condenser) {
        return new AABB(condenser.getBlockPos()).inflate(0.5);
    }

    @Override
    public void extractRenderState(CondenserBlockEntity condenser, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(condenser, state, partialTick, cameraPos, breakProgress);
        Level level = condenser.getLevel();
        state.facing = condenser.getBlockState().getValue(NeroTechMachineBlock.FACING);
        int readout = condenser.renderReadout();
        int sink = condenser.sink();
        int rate = Mth.clamp(condenser.rateBucket(), 0, 4);
        PowerRenderHelper.lampExtract(state.lamp, readout, true, level, partialTick);
        boolean working = readout == PowerRenderHelper.READOUT_WORK;
        state.overlay = readout == PowerRenderHelper.READOUT_HOT ? OVERLAY_GLOW
                : working && sink == TurbineMath.SINK_CRYO ? OVERLAY_FROST : OVERLAY_NONE;
        boolean animate = MachineSettings.renderAnimationsEnabled();
        state.drips = working && animate;
        // Explicit null check (not folded into a flag) so ecj's null-flow analysis can track it.
        if (level == null || !animate) {
            state.angle = (float) condenser.displayPos;
            return;
        }
        float speed;
        if (sink == TurbineMath.SINK_NONE) {
            speed = 0.0F; // no heat sink: the fan stands still
        } else if (working) {
            speed = FAN_MIN + FAN_STEP * rate;
        } else if (readout == PowerRenderHelper.READOUT_STALL) {
            speed = FAN_IDLE; // waiting for exhaust steam, or for room in the water tank
        } else {
            speed = 0.0F;
        }
        long tick = level.getGameTime();
        if (!condenser.displayInit) {
            condenser.displayPos = condenser.prevDisplayPos = 0.0D;
            condenser.displayInit = true;
            condenser.displayLastTick = tick;
        } else if (tick != condenser.displayLastTick) {
            condenser.displayLastTick = tick;
            condenser.prevDisplayPos = condenser.displayPos;
            condenser.displayPos = (condenser.displayPos + speed) % 360.0D;
        }
        double prev = condenser.prevDisplayPos;
        double cur = condenser.displayPos;
        if (cur < prev) {
            cur += 360.0D;
        }
        state.angle = (float) ((prev + (cur - prev) * partialTick) % 360.0D);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        int light = PowerRenderHelper.FULL_BRIGHT;
        poseStack.pushPose();
        PowerRenderHelper.rotateToFacing(poseStack, state.facing);
        PowerRenderHelper.lampSubmit(state.lamp, poseStack, collector);
        PowerRenderHelper.shimmerSubmit(state.lamp, poseStack, collector, 1.0F);

        // Fan: a flat impeller in the top well, turning about the vertical.
        float rad = state.angle * Mth.DEG_TO_RAD;
        float ux = Mth.cos(rad) * FAN_R;
        float uz = Mth.sin(rad) * FAN_R;
        collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(FAN_TEX),
                (pose, c) -> PowerRenderHelper.quad(c, pose, light, 0xC8D0D6, 0.5F, FAN_Y, 0.5F,
                        ux, 0.0F, uz, -uz, 0.0F, ux, 0.0F, 1.0F));

        if (state.overlay != OVERLAY_NONE) {
            int rgb = state.overlay == OVERLAY_FROST ? FROST_RGB : PowerRenderHelper.LAMP_OVERHEAT_RGB;
            collector.order(2).submitCustomGeometry(poseStack, RenderTypes.entityCutout(OVERLAY_TEX),
                    (pose, c) -> {
                        for (float[] tube : TUBES) {
                            tubeOverlay(c, pose, light, rgb, tube[0] / 16.0F, tube[1] / 16.0F);
                        }
                    });
        }

        if (state.drips) {
            float time = state.lamp.time;
            collector.order(2).submitCustomGeometry(poseStack, RenderTypes.entityCutout(PowerRenderHelper.LAMP_TEX),
                    (pose, c) -> {
                        drip(c, pose, light, 5.0F / 16.0F, time);
                        drip(c, pose, light, 11.0F / 16.0F, time + DRIP_TICKS * 0.5F);
                    });
        }
        poseStack.popPose();
    }

    /** The overlay on a tube's front and top faces, a hair proud of the model. */
    private static void tubeOverlay(VertexConsumer c, PoseStack.Pose pose, int light, int rgb, float y, float z) {
        float y1 = y + TUBE_SIZE;
        float zf = z - GAP;
        PowerRenderHelper.face(c, pose, light, rgb, 0, 0, -1,
                TUBE_X0, y, zf, 1, OVERLAY_V, TUBE_X0, y1, zf, 1, 0, TUBE_X1, y1, zf, 0, 0, TUBE_X1, y, zf, 0, OVERLAY_V);
        float yt = y1 + GAP;
        float z1 = z + TUBE_SIZE;
        PowerRenderHelper.face(c, pose, light, rgb, 0, 1, 0,
                TUBE_X0, yt, z, 1, 0, TUBE_X0, yt, z1, 1, OVERLAY_V, TUBE_X1, yt, z1, 0, OVERLAY_V, TUBE_X1, yt, z, 0, 0);
    }

    /** One drop falling from under the front tube to the tray, on a {@link #DRIP_TICKS} cycle. */
    private static void drip(VertexConsumer c, PoseStack.Pose pose, int light, float x, float time) {
        float phase = (time % DRIP_TICKS) / DRIP_TICKS;
        float y = DRIP_TOP - DRIP_FALL * phase * phase;
        float z = 3.5F / 16.0F;
        PowerRenderHelper.box(c, pose, light, DRIP_RGB, x - DRIP_HALF, y - DRIP_HALF * 2.0F, z - DRIP_HALF,
                x + DRIP_HALF, y, z + DRIP_HALF);
    }
}
