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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerotech.config.NeroTechConfig;
import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.multiblock.Bounds;
import za.co.neroland.neropower.turbine.TurbineHelmBlockEntity;
import za.co.neroland.neropower.turbine.TurbineSpindleBlock;
import za.co.neroland.neropower.turbine.VaneTier;

/**
 * Turbine Helm BER: the whole rotor of a formed turbine, plus the helm face's moving parts.
 *
 * <ul>
 * <li><b>Rotor.</b> Every vaned slice behind the helm carries twelve pitched vanes of its tier,
 * turning about the turbine axis. Speed is read as a ladder: single vanes up to a quarter of the
 * rated speed, vanes plus a sparse smear up to three quarters, and above that a streaked blur disc
 * in place of the vanes (the true rate would only strobe). Overspeed puts a red rim behind the disc
 * and shakes the rotor half a pixel along its axis. Coast-down is the same ladder backwards, because
 * the synced speed bucket simply falls.</li>
 * <li><b>Helm face.</b> A hub cap turning in the bearing boss, the tachometer needle, and the
 * mechanical trip flag that drops over the arc while the trip is latched (the needle falls with
 * it). The status lamp is the shared one.</li>
 * <li><b>Dressing.</b> A thin emissive seam along the twelve outer edges of the formed shell: ember
 * at rest, amber working, the warning colours when hot or failing, nothing when unformed.</li>
 * </ul>
 *
 * <p>Level of detail, by camera distance to the centre of the structure: up to 24 blocks the full
 * rotor; up to 48 one blur disc per vaned slice whatever the speed; beyond that only the helm face;
 * the seam stops at 32. The frustum box is the formed structure's bounds, so the renderer is never
 * drawn "always": {@code shouldRender} stays the default distance test.</p>
 *
 * <p>Sync discipline: only the bucketed, synced render inputs are read ({@code rpmBucket()},
 * {@code tripped()}, {@code formedBounds()}, {@code renderReadout()}) and, per slice, the spindle's
 * {@code vanes} block state. The angle is integrated on the client once per game tick, so a bucket
 * change speeds the rotor up or down instead of snapping it. A stalled rotor holds its pose; with
 * animations off everything is parked and steady.</p>
 */
public class TurbineRenderer implements BlockEntityRenderer<TurbineHelmBlockEntity, TurbineRenderer.State> {

    private static final Identifier[] BLADE_TEX = {
        PowerRenderHelper.texture("turbine_vane_blade_steel"),
        PowerRenderHelper.texture("turbine_vane_blade_cindersteel"),
        PowerRenderHelper.texture("turbine_vane_blade_superconducting"),
    };
    private static final Identifier BLUR_TEX = PowerRenderHelper.texture("turbine_rotor_blur");
    private static final Identifier SMEAR_TEX = PowerRenderHelper.texture("turbine_rotor_smear");
    private static final Identifier HUB_TEX = PowerRenderHelper.texture("turbine_helm_hub");
    private static final Identifier FLAG_TEX = PowerRenderHelper.texture("turbine_helm_flag");

    /** Blur and smear tints by vane tier (steel, cindersteel, superconductor). */
    private static final int[] TIER_RGB = {0xC4CCD2, 0xA87060, 0x60DCD6};

    /** The most interior slices a turbine can have (length 16 less the two end caps). */
    private static final int MAX_SLICES = 14;
    private static final int VANES = 12;
    /** Vane root and tip radius (blocks from the axis), half-width and pitch. */
    private static final float VANE_R0 = 7.0F / 16.0F;
    private static final float VANE_R1 = 1.42F;
    private static final float VANE_HALF_WIDTH = 0.25F;
    private static final float PITCH_COS = 0.883F;
    private static final float PITCH_SIN = 0.469F;
    private static final float DISC_R = 1.45F;
    /** Display rate: 27 degrees a tick (1.5 turns a second) at the rated bucket, never the true rate. */
    private static final float RATED_BUCKET = 18.0F;
    private static final float RATED_DEG_PER_TICK = 27.0F;
    private static final int OVERSPEED_BUCKET = 22;
    /** Level-of-detail limits (blocks, camera to the centre of the box). */
    private static final double LOD_FULL = 24.0D;
    private static final double LOD_REDUCED = 48.0D;
    private static final double LOD_DRESSING = 32.0D;
    private static final int DETAIL_FULL = 0;
    private static final int DETAIL_REDUCED = 1;
    private static final int DETAIL_FACE = 2;
    /** Half a pixel of axial shudder at most. */
    private static final float SHUDDER = 0.5F / 16.0F;

    // Helm face layout, from the painter (texels on the 32 px front; model x = 1 - texel / 32).
    private static final float TACH_X = 1.0F - 17.5F / 32.0F;
    private static final float TACH_Y = 1.0F - 15.0F / 32.0F;
    private static final float TACH_SWEEP = 50.0F;
    private static final float NEEDLE_R0 = 6.5F / 32.0F;
    private static final float NEEDLE_R1 = 10.5F / 32.0F;
    private static final float NEEDLE_HALF = 0.5F / 32.0F;
    private static final float HUB_X = 0.5F;
    private static final float HUB_Y = 5.0F / 16.0F;
    private static final float HUB_R = 1.75F / 16.0F;
    private static final float FACE_Z = -0.012F;
    private static final float SEAM = 1.0F / 16.0F;
    private static final float SEAM_GAP = 0.004F;
    private static final int NEEDLE_RGB = 0xFFE2A0;

    /** Render state: everything resolved at extraction, nothing read at submit time. */
    public static class State extends BlockEntityRenderState {
        final PowerRenderHelper.Lamp lamp = new PowerRenderHelper.Lamp();
        Direction facing = Direction.NORTH;
        boolean formed;
        boolean tripped;
        boolean dressing;
        int detail = DETAIL_FACE;
        int bucket;
        /** Blocks along the axis, helm included. */
        int length;
        float angle;
        float shudder;
        /** Interior slices from the helm: vane tier ordinal (0 = none) and packed light. */
        final int[] tiers = new int[MAX_SLICES];
        final int[] light = new int[MAX_SLICES];
        int slices;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /**
     * The formed structure's bounds (the rotor and the seam stay inside them), else the block.
     * NeoForge-only frustum hook, inert non-override on Fabric/Forge.
     */
    public AABB getRenderBoundingBox(TurbineHelmBlockEntity helm) {
        Bounds b = helm.formedBounds();
        if (b == null) {
            return new AABB(helm.getBlockPos()).inflate(0.5);
        }
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1.0, b.maxY() + 1.0, b.maxZ() + 1.0).inflate(0.25);
    }

    @Override
    public void extractRenderState(TurbineHelmBlockEntity helm, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(helm, state, partialTick, cameraPos, breakProgress);
        Level level = helm.getLevel();
        BlockPos pos = helm.getBlockPos();
        Direction facing = helm.getBlockState().getValue(NeroTechMachineBlock.FACING);
        state.facing = facing;
        int bucket = helm.rpmBucket();
        state.bucket = bucket;
        state.tripped = helm.tripped();
        PowerRenderHelper.lampExtract(state.lamp, helm.renderReadout(), bucket >= OVERSPEED_BUCKET, level,
                partialTick);

        Bounds b = helm.formedBounds();
        state.formed = b != null;
        state.slices = 0;
        state.length = 0;
        state.dressing = false;
        state.detail = DETAIL_FACE;
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 0.5D;
        double cz = pos.getZ() + 0.5D;
        if (b != null) {
            cx = (b.minX() + b.maxX() + 1) / 2.0D;
            cy = (b.minY() + b.maxY() + 1) / 2.0D;
            cz = (b.minZ() + b.maxZ() + 1) / 2.0D;
            int span = facing.getAxis() == Direction.Axis.X ? b.maxX() - b.minX() + 1 : b.maxZ() - b.minZ() + 1;
            state.length = Mth.clamp(span, 0, MAX_SLICES + 2);
        }
        double dx = cameraPos.x - cx;
        double dy = cameraPos.y - cy;
        double dz = cameraPos.z - cz;
        double distSq = dx * dx + dy * dy + dz * dz;
        if (b != null) {
            state.detail = distSq <= LOD_FULL * LOD_FULL ? DETAIL_FULL
                    : distSq <= LOD_REDUCED * LOD_REDUCED ? DETAIL_REDUCED : DETAIL_FACE;
            state.dressing = distSq <= LOD_DRESSING * LOD_DRESSING;
        }

        // Explicit null check (not folded into a flag) so ecj's null-flow analysis can track it.
        if (level == null) {
            state.angle = 0.0F;
            state.shudder = 0.0F;
            return;
        }
        if (state.detail != DETAIL_FACE) {
            Direction back = facing.getOpposite();
            int slices = Math.min(MAX_SLICES, state.length - 2);
            for (int i = 0; i < slices; i++) {
                BlockPos slice = pos.relative(back, i + 1);
                VaneTier tier = TurbineSpindleBlock.vanes(level.getBlockState(slice));
                state.tiers[i] = tier.ordinal();
                // Lit by the air ring above the spindle, not by the solid helm.
                BlockPos air = slice.above();
                state.light[i] = (level.getBrightness(LightLayer.BLOCK, air) << 4)
                        | (level.getBrightness(LightLayer.SKY, air) << 20);
            }
            state.slices = Math.max(0, slices);
        }

        boolean animate = NeroTechConfig.renderAnimationsEnabled();
        if (!animate) {
            state.angle = (float) helm.displayPos; // parked: the last pose, or 0
            state.shudder = 0.0F;
            return;
        }
        long tick = level.getGameTime();
        float speed = RATED_DEG_PER_TICK * bucket / RATED_BUCKET;
        if (!helm.displayInit) {
            helm.displayPos = helm.prevDisplayPos = 0.0D;
            helm.displayInit = true;
            helm.displayLastTick = tick;
        } else if (tick != helm.displayLastTick) {
            // Advance once per tick, keeping the previous-tick angle as the lerp partner. A stalled
            // rotor has speed 0 and so holds its pose; it is never reset to a parked one.
            helm.displayLastTick = tick;
            helm.prevDisplayPos = helm.displayPos;
            helm.displayPos = (helm.displayPos + speed) % 360.0D;
        }
        double prev = helm.prevDisplayPos;
        double cur = helm.displayPos;
        if (cur < prev) {
            cur += 360.0D;
        }
        state.angle = (float) ((prev + (cur - prev) * partialTick) % 360.0D);
        boolean shaking = bucket > 0 && (state.lamp.readout == PowerRenderHelper.READOUT_HOT
                || state.lamp.readout == PowerRenderHelper.READOUT_FAIL);
        state.shudder = shaking ? SHUDDER * Mth.sin(state.lamp.time * 2.4F) : 0.0F;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        poseStack.pushPose();
        PowerRenderHelper.rotateToFacing(poseStack, state.facing);
        PowerRenderHelper.lampSubmit(state.lamp, poseStack, collector);
        PowerRenderHelper.shimmerSubmit(state.lamp, poseStack, collector, 1.0F);
        submitFace(state, poseStack, collector);
        if (state.formed && state.slices > 0) {
            submitRotor(state, poseStack, collector);
        }
        if (state.formed && state.dressing && state.length >= 2) {
            submitDressing(state, poseStack, collector);
        }
        poseStack.popPose();
    }

    // ---- helm face: hub cap, needle, trip flag ---------------------------------------------------

    private static void submitFace(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        int light = PowerRenderHelper.FULL_BRIGHT;
        // Hub cap: turns with the rotor (seen from the front, so the sense is mirrored).
        float rad = -state.angle * Mth.DEG_TO_RAD;
        float ux = Mth.cos(rad) * HUB_R;
        float uy = Mth.sin(rad) * HUB_R;
        collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(HUB_TEX),
                (pose, c) -> PowerRenderHelper.quad(c, pose, light, 0xFFFFFF, HUB_X, HUB_Y, FACE_Z,
                        ux, uy, 0.0F, -uy, ux, 0.0F, 0.0F, 1.0F));

        // Needle: rests at zero while tripped (it falls with the flag).
        int shown = state.tripped ? 0 : state.bucket;
        float a = (-TACH_SWEEP + 2.0F * TACH_SWEEP * shown / 24.0F) * Mth.DEG_TO_RAD;
        // Texel x runs to the viewer's right, which is model -x on a north-facing front.
        float rx = -Mth.sin(a);
        float ry = Mth.cos(a);
        float mid = (NEEDLE_R0 + NEEDLE_R1) / 2.0F;
        float half = (NEEDLE_R1 - NEEDLE_R0) / 2.0F;
        float nx = TACH_X + rx * mid;
        float ny = TACH_Y + ry * mid;
        collector.order(2).submitCustomGeometry(poseStack, RenderTypes.entityCutout(PowerRenderHelper.LAMP_TEX),
                (pose, c) -> PowerRenderHelper.quad(c, pose, light, NEEDLE_RGB, nx, ny, FACE_Z,
                        ry * NEEDLE_HALF, -rx * NEEDLE_HALF, 0.0F, rx * half, ry * half, 0.0F, 0.25F, 0.75F));

        if (state.tripped) {
            // The flag covers the arc window (texels x9..27, y3..9); its sprite has a clear margin.
            float x0 = 1.0F - 27.5F / 32.0F;
            float x1 = 1.0F - 8.5F / 32.0F;
            float y0 = 1.0F - 9.5F / 32.0F;
            float y1 = 1.0F - 2.5F / 32.0F;
            float z = FACE_Z - 0.004F;
            float v0 = 4.0F / 32.0F;
            float v1 = 28.0F / 32.0F;
            collector.order(3).submitCustomGeometry(poseStack, RenderTypes.entityCutout(FLAG_TEX),
                    (pose, c) -> PowerRenderHelper.face(c, pose, light, 0xFFFFFF, 0, 0, -1,
                            x0, y0, z, 1, v1, x0, y1, z, 1, v0, x1, y1, z, 0, v0, x1, y0, z, 0, v1));
        }
    }

    // ---- rotor ---------------------------------------------------------------------------------------

    private static void submitRotor(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        final int slices = state.slices;
        final int[] tiers = state.tiers.clone();
        final int[] lights = state.light.clone();
        final float angle = state.angle;
        final float shudder = state.shudder;
        float speed = state.bucket / RATED_BUCKET;
        boolean blur = state.detail == DETAIL_REDUCED || speed > 0.75F;
        boolean smear = !blur && speed > 0.25F;
        boolean overspeed = state.bucket > RATED_BUCKET && (state.lamp.readout == PowerRenderHelper.READOUT_HOT
                || state.lamp.readout == PowerRenderHelper.READOUT_FAIL);

        if (!blur) {
            for (int t = 1; t <= BLADE_TEX.length; t++) {
                final int tier = t;
                boolean any = false;
                for (int i = 0; i < slices; i++) {
                    any |= tiers[i] == tier;
                }
                if (!any) {
                    continue;
                }
                collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(BLADE_TEX[tier - 1]),
                        (pose, c) -> {
                            for (int i = 0; i < slices; i++) {
                                if (tiers[i] == tier) {
                                    vanes(c, pose, lights[i], i + 1.5F + shudder, angle + i * 7.5F);
                                }
                            }
                        });
            }
        }
        if (blur || smear) {
            final float dim = blur ? 1.0F : 0.8F;
            collector.order(2).submitCustomGeometry(poseStack, RenderTypes.entityCutout(blur ? BLUR_TEX : SMEAR_TEX),
                    (pose, c) -> {
                        for (int i = 0; i < slices; i++) {
                            if (tiers[i] > 0) {
                                disc(c, pose, lights[i], PowerRenderHelper.dim(TIER_RGB[tiers[i] - 1], dim),
                                        i + 1.5F + shudder + (blur ? 0.0F : 0.03F), angle + i * 7.5F, DISC_R);
                            }
                        }
                    });
        }
        if (blur && overspeed) {
            // A red rim: the same streaks a little larger, on both sides of the disc.
            collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(BLUR_TEX),
                    (pose, c) -> {
                        for (int i = 0; i < slices; i++) {
                            if (tiers[i] > 0) {
                                float z = i + 1.5F + shudder;
                                disc(c, pose, PowerRenderHelper.FULL_BRIGHT, PowerRenderHelper.LAMP_OVERHEAT_RGB,
                                        z - 0.012F, angle, DISC_R * 1.05F);
                                disc(c, pose, PowerRenderHelper.FULL_BRIGHT, PowerRenderHelper.LAMP_OVERHEAT_RGB,
                                        z + 0.012F, angle, DISC_R * 1.05F);
                            }
                        }
                    });
        }
    }

    /** Twelve pitched vanes about the axis (block-local x 0.5, y 0.5) in the plane z. */
    private static void vanes(VertexConsumer c, PoseStack.Pose pose, int light, float z, float angle) {
        float rc = (VANE_R0 + VANE_R1) / 2.0F;
        float hl = (VANE_R1 - VANE_R0) / 2.0F;
        for (int i = 0; i < VANES; i++) {
            float rad = (angle + i * (360.0F / VANES)) * Mth.DEG_TO_RAD;
            float rx = Mth.cos(rad);
            float ry = Mth.sin(rad);
            // Width runs along the tangent, pitched toward the axis direction.
            float wx = -ry * PITCH_COS * VANE_HALF_WIDTH;
            float wy = rx * PITCH_COS * VANE_HALF_WIDTH;
            float wz = PITCH_SIN * VANE_HALF_WIDTH;
            PowerRenderHelper.quad(c, pose, light, 0xFFFFFF, 0.5F + rx * rc, 0.5F + ry * rc, z,
                    wx, wy, wz, rx * hl, ry * hl, 0.0F, 1.0F, 0.0F);
        }
    }

    /** One streaked disc about the axis in the plane z. */
    private static void disc(VertexConsumer c, PoseStack.Pose pose, int light, int rgb, float z, float angle,
            float radius) {
        float rad = angle * Mth.DEG_TO_RAD;
        float ux = Mth.cos(rad) * radius;
        float uy = Mth.sin(rad) * radius;
        PowerRenderHelper.quad(c, pose, light, rgb, 0.5F, 0.5F, z, ux, uy, 0.0F, -uy, ux, 0.0F, 0.0F, 1.0F);
    }

    // ---- structure dressing --------------------------------------------------------------------------

    private static int seamColor(PowerRenderHelper.Lamp lamp) {
        switch (lamp.readout) {
            case PowerRenderHelper.READOUT_WORK:
                return PowerRenderHelper.LAMP_AMBER_RGB;
            case PowerRenderHelper.READOUT_HOT:
                return PowerRenderHelper.LAMP_OVERHEAT_RGB;
            case PowerRenderHelper.READOUT_FAIL:
                return PowerRenderHelper.LAMP_FAIL_RGB;
            default:
                return PowerRenderHelper.LAMP_EMBER_RGB;
        }
    }

    /**
     * A seam along each of the shell's twelve outer edges: two thin strips per edge, one on each of
     * the faces that meet there. In model space the 5 x 5 shell spans x and y -2..3 and runs from the
     * helm's front (z 0) back along +z.
     */
    private static void submitDressing(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        final int rgb = seamColor(state.lamp);
        final float[] min = {-2.0F, -2.0F, 0.0F};
        final float[] max = {3.0F, 3.0F, state.length};
        collector.order(5).submitCustomGeometry(poseStack, RenderTypes.entityCutout(PowerRenderHelper.LAMP_TEX),
                (pose, c) -> {
                    float[] p = new float[12];
                    for (int a = 0; a < 3; a++) {
                        int b = (a + 1) % 3;
                        int d = (a + 2) % 3;
                        for (int corner = 0; corner < 4; corner++) {
                            boolean bHigh = (corner & 1) != 0;
                            boolean dHigh = (corner & 2) != 0;
                            strip(c, pose, rgb, p, a, min[a], max[a], b, bHigh ? max[b] + SEAM_GAP : min[b] - SEAM_GAP,
                                    bHigh, d, dHigh ? max[d] : min[d], dHigh ? -SEAM : SEAM);
                            strip(c, pose, rgb, p, a, min[a], max[a], d, dHigh ? max[d] + SEAM_GAP : min[d] - SEAM_GAP,
                                    dHigh, b, bHigh ? max[b] : min[b], bHigh ? -SEAM : SEAM);
                        }
                    }
                });
    }

    /**
     * One seam strip: it runs {@code lo..hi} along axis {@code a}, lies in the plane {@code flat = at}
     * and is {@code width} wide from {@code edge} along axis {@code across}.
     */
    private static void strip(VertexConsumer c, PoseStack.Pose pose, int rgb, float[] p, int a, float lo, float hi,
            int flat, float at, boolean outward, int across, float edge, float width) {
        float[] n = new float[3];
        n[flat] = outward ? 1.0F : -1.0F;
        for (int k = 0; k < 4; k++) {
            p[k * 3 + a] = k == 0 || k == 3 ? lo : hi;
            p[k * 3 + flat] = at;
            p[k * 3 + across] = k < 2 ? edge : edge + width;
        }
        PowerRenderHelper.face(c, pose, PowerRenderHelper.FULL_BRIGHT, rgb, n[0], n[1], n[2],
                p[0], p[1], p[2], 0.5F, 0.5F, p[3], p[4], p[5], 0.5F, 0.5F,
                p[6], p[7], p[8], 0.5F, 0.5F, p[9], p[10], p[11], 0.5F, 0.5F);
    }
}
