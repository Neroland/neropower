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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerotech.config.NeroTechConfig;
import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.failure.FailureStage;
import za.co.neroland.neropower.fission.FissionCoreBlockEntity;
import za.co.neroland.neropower.machine.NeroPowerMachineBlock;

/**
 * Fission Core BER — NeroTech's Fusion Reactor recipe on the reactor head: behind the recessed front
 * panel a core glow plate pulses on the NeroPower power ramp (ember → amber → hot orange →
 * white-gold) scaled by the synced heat bucket, brighter while fissioning and dimmed when idle-hot;
 * a small rod-port indicator disc spins in the cross between the four top ports while running; and
 * once the failure ladder raises {@code alarm=true} the panel carries the alternating H_CRIT
 * strobe. <b>Unformed and cold = dark</b>: the static head is the whole machine.
 *
 * <p>Sync discipline: extraction reads only the BE's synced render surface ({@code formed},
 * {@code renderActive}, {@code heatFraction}, {@code renderFailureStage}) and the {@code alarm} /
 * {@code facing} block state. {@code renderAnimationsEnabled=false} freezes the pulse and the disc
 * and holds the strobe steady-on, so the danger stays readable as a static frame.</p>
 */
public class FissionCoreRenderer
        implements BlockEntityRenderer<FissionCoreBlockEntity, FissionCoreRenderer.State> {

    private static final Identifier GLOW_TEX = PowerRenderHelper.texture("fission_core_glow");

    /** Glow plate depth: just in front of the recessed panel (body face at z=2), behind the frame. */
    private static final float GLOW_Z = 1.5F / 16.0F;
    /** Strobe overlay sits a hair in front of the glow plate. */
    private static final float STROBE_Z = 1.2F / 16.0F;
    /** The panel window inside the frame: x2..14, y2..12 (model px). */
    private static final float WIN_X0 = 2.0F / 16.0F;
    private static final float WIN_X1 = 14.0F / 16.0F;
    private static final float WIN_Y0 = 2.0F / 16.0F;
    private static final float WIN_Y1 = 12.0F / 16.0F;
    /** Rod-port indicator: a disc in the cross between the four ports, just above the top plate. */
    private static final float DISC_Y = 15.5F / 16.0F;
    private static final float DISC_R = 1.9F / 16.0F;
    /** Indicator spin speed (degrees per tick). */
    private static final float SPIN_SPEED = 6.0F;
    /** Core pulse period (radians per tick). */
    private static final float PULSE_SPEED = 0.12F;
    /** Strobe half-period (ticks): 4 on, 4 off. */
    private static final long STROBE_PERIOD = 4L;

    /** Render state: everything extracted from the BE + clocks, nothing read at submit time. */
    public static class State extends BlockEntityRenderState {
        boolean formed;
        boolean active;
        float heat;
        /** 0..1 pulse mix (sine, animations on) or 0 (static frame). */
        float pulse;
        float spin;
        boolean strobe;
        Direction facing = Direction.NORTH;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** One-block pad; NeoForge-only frustum hook, inert non-override on Fabric/Forge (NeroTech recipe). */
    public AABB getRenderBoundingBox(FissionCoreBlockEntity core) {
        return new AABB(core.getBlockPos()).inflate(1.0);
    }

    @Override
    public void extractRenderState(FissionCoreBlockEntity core, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(core, state, partialTick, cameraPos, breakProgress);
        Level level = core.getLevel();
        BlockState blockState = core.getBlockState();
        state.formed = core.formed();
        state.facing = blockState.getValue(NeroTechMachineBlock.FACING);
        // Heat drives the ramp; a ladder rung past WARNING pins it hot so the read never lags the alarm.
        float heat = core.heatFraction();
        FailureStage stage = core.renderFailureStage();
        if (stage == FailureStage.UNSTABLE || stage == FailureStage.FAILURE) {
            heat = Math.max(heat, 0.85F);
        }
        state.heat = heat;
        boolean alarm = blockState.hasProperty(NeroPowerMachineBlock.ALARM)
                && blockState.getValue(NeroPowerMachineBlock.ALARM);
        // Explicit null check (not folded into a flag) so ecj's null-flow analysis can track it.
        if (level == null) {
            state.active = false;
            state.pulse = 0.0F;
            state.spin = 0.0F;
            state.strobe = false;
            return;
        }
        state.active = core.renderActive();
        if (NeroTechConfig.renderAnimationsEnabled()) {
            float now = level.getGameTime() + partialTick;
            state.pulse = 0.5F + 0.5F * Mth.sin(now * PULSE_SPEED);
            state.spin = state.active ? (now * SPIN_SPEED) % 360.0F : 0.0F;
            state.strobe = alarm && (level.getGameTime() / STROBE_PERIOD) % 2L == 0L;
        } else {
            state.pulse = 0.0F;
            state.spin = 0.0F;
            state.strobe = alarm; // static frame: hold the warning steady-on
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        boolean glow = state.formed && (state.active || state.heat > 0.02F);
        if (!glow && !state.strobe) {
            return; // unformed / cold / safe: the static head is the whole machine
        }
        int light = PowerRenderHelper.FULL_BRIGHT;
        poseStack.pushPose();
        PowerRenderHelper.rotateToFacing(poseStack, state.facing);

        if (glow) {
            // Core glow plate on the power ramp; running cores breathe (pulse scaled by heat), an
            // idle-but-hot core dims to ~45% so brightness reads as output.
            int rgb = PowerRenderHelper.powerColor(state.heat);
            float brightness = state.active
                    ? 0.75F + 0.25F * state.pulse * (0.4F + 0.6F * state.heat)
                    : 0.45F;
            int glowRgb = PowerRenderHelper.dim(rgb, brightness);
            collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(GLOW_TEX),
                    (pose, c) -> PowerRenderHelper.face(c, pose, light, glowRgb, 0, 0, -1,
                            WIN_X0, WIN_Y0, GLOW_Z, 0, 1,
                            WIN_X0, WIN_Y1, GLOW_Z, 0, 0,
                            WIN_X1, WIN_Y1, GLOW_Z, 1, 0,
                            WIN_X1, WIN_Y0, GLOW_Z, 1, 1));

            // Rod-port indicator: a spinning disc in the cross between the four top ports while
            // running (parked at 0° when idle or animations off) — the cyan family tell.
            if (state.active) {
                poseStack.pushPose();
                poseStack.translate(0.5F, DISC_Y, 0.5F);
                //? if >=26.3 {
                /*poseStack.rotate(Axis.YP.rotationDegrees(state.spin));
                *///?} else {
                poseStack.mulPose(Axis.YP.rotationDegrees(state.spin));
                //?}
                collector.order(2).submitCustomGeometry(poseStack, RenderTypes.entityCutout(GLOW_TEX),
                        (pose, c) -> PowerRenderHelper.face(c, pose, light, PowerRenderHelper.CYAN_RGB,
                                0, 1, 0,
                                -DISC_R, 0, -DISC_R, 0, 1,
                                -DISC_R, 0, DISC_R, 0, 0,
                                DISC_R, 0, DISC_R, 1, 0,
                                DISC_R, 0, -DISC_R, 1, 1));
                poseStack.popPose();
            }
        }

        // Alarm strobe: an H_CRIT overlay across the front panel (the ladder's telegraph, readable
        // from the operator's side whatever the shell size).
        if (state.strobe) {
            collector.order(3).submitCustomGeometry(poseStack, RenderTypes.entityCutout(GLOW_TEX),
                    (pose, c) -> PowerRenderHelper.face(c, pose, light, PowerRenderHelper.CRIT_RGB,
                            0, 0, -1,
                            WIN_X0, WIN_Y0, STROBE_Z, 0, 1,
                            WIN_X0, WIN_Y1, STROBE_Z, 0, 0,
                            WIN_X1, WIN_Y1, STROBE_Z, 1, 0,
                            WIN_X1, WIN_Y0, STROBE_Z, 1, 1));
        }
        poseStack.popPose();
    }
}
