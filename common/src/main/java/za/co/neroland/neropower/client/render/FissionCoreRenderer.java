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
 *
 * <p><b>Structure dressing.</b> A formed reactor carries a thin full-bright seam along the twelve
 * outer edges of its shell (one quad per edge, whatever the shell size): ember when formed and idle,
 * amber when working, the overheat / trip / failure colours otherwise, and nothing when unformed, so
 * "is it formed?" reads from outside. It is drawn only within {@value #DRESS_RANGE} blocks. In steam
 * mode a white collar is laid over the viewport, and breeding progress lifts the core glow a step
 * at a time. All of it comes from the synced, bucketed fields ({@code shellSize}, {@code renderMode},
 * {@code renderBreedStep}, {@code renderReadout}).</p>
 */
public class FissionCoreRenderer
        implements BlockEntityRenderer<FissionCoreBlockEntity, FissionCoreRenderer.State> {

    private static final Identifier GLOW_TEX = PowerRenderHelper.texture("fission_core_glow");
    private static final Identifier STEAM_RING_TEX = PowerRenderHelper.texture("fission_core_steam_ring");

    /** Camera distance (blocks) past which the shell seams are not drawn. */
    private static final double DRESS_RANGE = 32.0;
    /** Half the seam's width (blocks): one pixel either side of the shell edge. */
    private static final float SEAM_HALF = 1.0F / 16.0F;
    /** How far the seam stands off the shell so it never z-fights the casing. */
    private static final float SEAM_GAP = 0.01F;
    /** Steam collar plane: between the recessed panel (z=2) and the glow plate. */
    private static final float RING_Z = 1.8F / 16.0F;
    /** Core glow gained per breeding step (0..4). */
    private static final float BREED_GLOW = 0.04F;

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
        /** Formed shell edge (3 / 5 / 7), 0 when unformed. */
        int shell;
        /** 1 while the core runs in steam mode. */
        int mode;
        /** Breeding progress bucket 0..4. */
        int breedStep;
        /** Shell seams are drawn (formed and within range). */
        boolean dress;
        /** Readout rhythm and colour for the seams; the core model has no lamp socket. */
        final PowerRenderHelper.Lamp lamp = new PowerRenderHelper.Lamp();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** One-block pad; NeoForge-only frustum hook, inert non-override on Fabric/Forge (NeroTech recipe). */
    public AABB getRenderBoundingBox(FissionCoreBlockEntity core) {
        AABB block = new AABB(core.getBlockPos());
        int size = core.shellSize();
        if (size < 3) {
            return block.inflate(1.0);
        }
        // The formed shell: the core sits at the centre of one wall, the cube lies behind it.
        Direction back = core.getBlockState().getValue(NeroTechMachineBlock.FACING).getOpposite();
        double half = (size - 1) / 2.0;
        return block.move(back.getStepX() * half, 0.0, back.getStepZ() * half).inflate(half + 0.25);
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
        state.shell = state.formed ? core.shellSize() : 0;
        state.mode = core.renderMode();
        state.breedStep = Mth.clamp(core.renderBreedStep(), 0, 4);
        PowerRenderHelper.lampExtract(state.lamp, core.renderReadout(), heat >= 0.9F, level, partialTick);
        state.dress = state.shell >= 3
                && cameraPos.distanceToSqr(Vec3.atCenterOf(core.getBlockPos())) <= DRESS_RANGE * DRESS_RANGE;
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
        boolean steam = state.formed && state.mode == 1;
        if (!glow && !state.strobe && !state.dress && !steam) {
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
                    ? Math.min(1.0F, 0.75F + 0.25F * state.pulse * (0.4F + 0.6F * state.heat)
                            + BREED_GLOW * state.breedStep)
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

        // Steam mode: the white collar over the viewport (full when running, dimmed when idle). The
        // sprite is the whole front face, so the quad takes the panel window's share of it.
        if (steam) {
            int ringRgb = PowerRenderHelper.dim(0xFFFFFF, state.active ? 1.0F : 0.6F);
            collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(STEAM_RING_TEX),
                    (pose, c) -> PowerRenderHelper.face(c, pose, light, ringRgb, 0, 0, -1,
                            WIN_X0, WIN_Y0, RING_Z, 1.0F - WIN_X0, 1.0F - WIN_Y0,
                            WIN_X0, WIN_Y1, RING_Z, 1.0F - WIN_X0, 1.0F - WIN_Y1,
                            WIN_X1, WIN_Y1, RING_Z, 1.0F - WIN_X1, 1.0F - WIN_Y1,
                            WIN_X1, WIN_Y0, RING_Z, 1.0F - WIN_X1, 1.0F - WIN_Y0));
        }

        if (state.dress) {
            submitSeams(state, poseStack, collector);
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

    /**
     * The shell seams: one diagonal full-bright strip standing on each of the twelve outer edges of
     * the formed cube, in model space (front wall at z 0, the cube behind it). Steady in every state
     * but failing, where it dims on the lamp's 2 Hz beat rather than going out.
     */
    private static void submitSeams(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        float half = (state.shell - 1) / 2.0F;
        float lo = -half;
        float hi = 1.0F + half;
        float z0 = 0.0F;
        float z1 = state.shell;
        int base = PowerRenderHelper.lampColor(state.lamp);
        boolean failing = state.lamp.readout == PowerRenderHelper.READOUT_FAIL;
        int rgb = failing && !PowerRenderHelper.lampLit(state.lamp) ? PowerRenderHelper.dim(base, 0.4F) : base;
        float mid = (lo + hi) / 2.0F;
        float zMid = (z0 + z1) / 2.0F;
        float len = (hi - lo) / 2.0F;
        float zLen = (z1 - z0) / 2.0F;
        float w = SEAM_HALF * 0.70710677F;
        collector.order(2).submitCustomGeometry(poseStack, RenderTypes.entityCutout(PowerRenderHelper.LAMP_TEX),
                (pose, c) -> {
                    for (int a = -1; a <= 1; a += 2) {
                        for (int b = -1; b <= 1; b += 2) {
                            float ea = a < 0 ? lo - SEAM_GAP : hi + SEAM_GAP;
                            float eb = b < 0 ? lo - SEAM_GAP : hi + SEAM_GAP;
                            float ez = b < 0 ? z0 - SEAM_GAP : z1 + SEAM_GAP;
                            // edges along z (corner in x / y)
                            PowerRenderHelper.quad(c, pose, PowerRenderHelper.FULL_BRIGHT, rgb,
                                    ea, eb, zMid, 0.0F, 0.0F, zLen, a * w, -b * w, 0.0F, 0.0F, 1.0F);
                            // edges along x (corner in y / z)
                            PowerRenderHelper.quad(c, pose, PowerRenderHelper.FULL_BRIGHT, rgb,
                                    mid, ea, ez, len, 0.0F, 0.0F, 0.0F, a * w, -b * w, 0.0F, 1.0F);
                            // edges along y (corner in x / z)
                            PowerRenderHelper.quad(c, pose, PowerRenderHelper.FULL_BRIGHT, rgb,
                                    ea, mid, ez, 0.0F, len, 0.0F, a * w, 0.0F, -b * w, 0.0F, 1.0F);
                        }
                    }
                });
    }
}
