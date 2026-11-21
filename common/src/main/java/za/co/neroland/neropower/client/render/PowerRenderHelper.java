package za.co.neroland.neropower.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import za.co.neroland.nerotech.config.NeroTechConfig;

import za.co.neroland.neropower.NeroPowerCommon;

/**
 * Shared textured-quad helpers for NeroPower's block-entity renderers — NeroTech's
 * {@code MachineRenderHelper} recipe carried over verbatim (full-bright emissive light, double-sided
 * faces, simple boxes with a full sprite per face) so NeroPower's client never links against
 * NeroTech's client classes, plus the NeroPower "power" ramp: the same heat-readability lerp, but
 * deep ember → amber → hot orange → white-gold instead of NeroTech's cyan → amber → red, and a few
 * oriented-quad helpers the dish / beam / fin renderers need. The 0.4.0 blocks add a shared status
 * lamp and heat shimmer (see {@link Lamp}).
 *
 * <p>All geometry is submitted through {@code RenderTypes.entityCutout(texture)} via
 * {@code SubmitNodeCollector.submitCustomGeometry} (the 26.x submit API — never the old
 * {@code render()}). Pure client visuals; no player data anywhere near this (POPIA/GDPR n/a).
 */
public final class PowerRenderHelper {

    /**
     * Packed full-bright light for emissive quads. Submitted custom geometry cannot rely on
     * {@code state.lightCoords} being populated — left at 0 the quads read pitch-black — so every
     * emissive part draws at full brightness and shades only by its normals (NeroTech finding).
     */
    public static final int FULL_BRIGHT = 0x00F000F0;

    // The NeroPower "power" ramp: ember → amber → hot orange → white-gold.
    private static final int[] P_EMBER = {158, 62, 18};
    private static final int[] P_AMBER = {255, 170, 40};
    private static final int[] P_HOT = {255, 120, 24};
    private static final int[] P_GOLD = {255, 236, 170};

    /** The thin cyan family tell (NeroTech's T_CYAN) — one detail per block reads as the same family. */
    public static final int CYAN_RGB = (36 << 16) | (208 << 8) | 222;

    /** Heat-critical RGB (the alarm strobe overlay — NeroTech's H_CRIT). */
    public static final int CRIT_RGB = (255 << 16) | (84 << 8) | 56;

    // Battery charge window ramp: red (empty) → amber (half) → green (full).
    private static final int[] C_RED = {232, 64, 48};
    private static final int[] C_AMBER = {255, 178, 56};
    private static final int[] C_GREEN = {96, 224, 96};

    private PowerRenderHelper() {
    }

    /** {@code neropower:textures/block/<name>.png}. */
    public static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "textures/block/" + name + ".png");
    }

    /**
     * The NeroPower power lerp: 0 → ember, ⅓ → amber, ⅔ → hot orange, 1 → white-gold. Returns packed
     * 0xRRGGBB; split with {@link #red}/{@link #green}/{@link #blue}.
     */
    public static int powerColor(float fraction) {
        float f = Mth.clamp(fraction, 0.0F, 1.0F) * 3.0F;
        int[] from;
        int[] to;
        float t;
        if (f < 1.0F) {
            from = P_EMBER;
            to = P_AMBER;
            t = f;
        } else if (f < 2.0F) {
            from = P_AMBER;
            to = P_HOT;
            t = f - 1.0F;
        } else {
            from = P_HOT;
            to = P_GOLD;
            t = f - 2.0F;
        }
        return lerpRgb(t, from, to);
    }

    /** The battery charge lerp: 0 → red, 0.5 → amber, 1 → green (packed 0xRRGGBB). */
    public static int chargeColor(float fraction) {
        float f = Mth.clamp(fraction, 0.0F, 1.0F);
        int[] from = f < 0.5F ? C_RED : C_AMBER;
        int[] to = f < 0.5F ? C_AMBER : C_GREEN;
        float t = f < 0.5F ? f * 2.0F : (f - 0.5F) * 2.0F;
        return lerpRgb(t, from, to);
    }

    private static int lerpRgb(float t, int[] from, int[] to) {
        int r = (int) Mth.lerp(t, from[0], to[0]);
        int g = (int) Mth.lerp(t, from[1], to[1]);
        int b = (int) Mth.lerp(t, from[2], to[2]);
        return (r << 16) | (g << 8) | b;
    }

    /** Scale a packed 0xRRGGBB by {@code factor} (0..1) — dimming for idle-hot / shimmer. */
    public static int dim(int rgb, float factor) {
        float f = Mth.clamp(factor, 0.0F, 1.0F);
        return ((int) (red(rgb) * f) << 16) | ((int) (green(rgb) * f) << 8) | (int) (blue(rgb) * f);
    }

    public static int red(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    public static int green(int rgb) {
        return (rgb >> 8) & 0xFF;
    }

    public static int blue(int rgb) {
        return rgb & 0xFF;
    }

    /**
     * Rotate the pose from model space (all NeroPower machines are authored facing NORTH) to the
     * block's horizontal facing, matching the blockstate {@code y} rotation exactly
     * (north=0, east=90, south=180, west=270 — clockwise from above, hence the negated angle
     * for the counter-clockwise {@code Axis.YP}). Pivots about the block centre column.
     */
    public static void rotateToFacing(PoseStack poseStack, Direction facing) {
        float yRot = switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
        if (yRot != 0.0F) {
            poseStack.translate(0.5F, 0.0F, 0.5F);
            //? if >=26.3 {
            /*poseStack.rotate(Axis.YP.rotationDegrees(-yRot));
            *///?} else {
            poseStack.mulPose(Axis.YP.rotationDegrees(-yRot));
            //?}
            poseStack.translate(-0.5F, 0.0F, -0.5F);
        }
    }

    /** A tinted box, every face carrying the full sprite; all faces double-sided (cutout-safe). */
    public static void box(VertexConsumer c, PoseStack.Pose pose, int light, int rgb,
            float x0, float y0, float z0, float x1, float y1, float z1) {
        // top (+Y) / bottom (-Y)
        face(c, pose, light, rgb, 0, 1, 0,
                x0, y1, z0, 0, 0, x0, y1, z1, 0, 1, x1, y1, z1, 1, 1, x1, y1, z0, 1, 0);
        face(c, pose, light, rgb, 0, -1, 0,
                x0, y0, z0, 0, 0, x1, y0, z0, 1, 0, x1, y0, z1, 1, 1, x0, y0, z1, 0, 1);
        // north (-Z) / south (+Z)
        face(c, pose, light, rgb, 0, 0, -1,
                x0, y0, z0, 0, 1, x0, y1, z0, 0, 0, x1, y1, z0, 1, 0, x1, y0, z0, 1, 1);
        face(c, pose, light, rgb, 0, 0, 1,
                x1, y0, z1, 0, 1, x1, y1, z1, 0, 0, x0, y1, z1, 1, 0, x0, y0, z1, 1, 1);
        // west (-X) / east (+X)
        face(c, pose, light, rgb, -1, 0, 0,
                x0, y0, z1, 0, 1, x0, y1, z1, 0, 0, x0, y1, z0, 1, 0, x0, y0, z0, 1, 1);
        face(c, pose, light, rgb, 1, 0, 0,
                x1, y0, z0, 0, 1, x1, y1, z0, 0, 0, x1, y1, z1, 1, 0, x1, y0, z1, 1, 1);
    }

    /** White box convenience overload. */
    public static void box(VertexConsumer c, PoseStack.Pose pose, int light,
            float x0, float y0, float z0, float x1, float y1, float z1) {
        box(c, pose, light, 0xFFFFFF, x0, y0, z0, x1, y1, z1);
    }

    /**
     * A tinted quad emitted BOTH ways (front with the given normal, back reversed) so it shows from
     * either side through the cutout cull — the Nerospace double-sided-face recipe.
     */
    public static void face(VertexConsumer c, PoseStack.Pose pose, int light, int rgb,
            float nx, float ny, float nz,
            float ax, float ay, float az, float au, float av,
            float bx, float by, float bz, float bu, float bv,
            float cx, float cy, float cz, float cu, float cv,
            float dx, float dy, float dz, float du, float dv) {
        vertex(c, pose, light, rgb, ax, ay, az, au, av, nx, ny, nz);
        vertex(c, pose, light, rgb, bx, by, bz, bu, bv, nx, ny, nz);
        vertex(c, pose, light, rgb, cx, cy, cz, cu, cv, nx, ny, nz);
        vertex(c, pose, light, rgb, dx, dy, dz, du, dv, nx, ny, nz);
        vertex(c, pose, light, rgb, dx, dy, dz, du, dv, -nx, -ny, -nz);
        vertex(c, pose, light, rgb, cx, cy, cz, cu, cv, -nx, -ny, -nz);
        vertex(c, pose, light, rgb, bx, by, bz, bu, bv, -nx, -ny, -nz);
        vertex(c, pose, light, rgb, ax, ay, az, au, av, -nx, -ny, -nz);
    }

    /**
     * A double-sided quad given its centre and two half-extent basis vectors {@code u} / {@code v}
     * (already scaled to the half-size): corners are {@code centre ± u ± v}; the normal is
     * {@code u × v}. UVs run 0..1 along u and {@code v0..v1} along v, so callers can stream a
     * texture strip along a beam. The way the dish, fins and beam segments are oriented without a
     * rotation stack — pure vector maths, no extra pose ops.
     */
    public static void quad(VertexConsumer c, PoseStack.Pose pose, int light, int rgb,
            float cx, float cy, float cz,
            float ux, float uy, float uz,
            float vx, float vy, float vz,
            float v0, float v1) {
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0E-6F) {
            nx /= len;
            ny /= len;
            nz /= len;
        } else {
            ny = 1.0F;
        }
        face(c, pose, light, rgb, nx, ny, nz,
                cx - ux - vx, cy - uy - vy, cz - uz - vz, 0, v0,
                cx - ux + vx, cy - uy + vy, cz - uz + vz, 0, v1,
                cx + ux + vx, cy + uy + vy, cz + uz + vz, 1, v1,
                cx + ux - vx, cy + uy - vy, cz + uz - vz, 1, v0);
    }

    /**
     * Two unit vectors perpendicular to {@code (dx, dy, dz)} (need not be normalised), rotated about
     * it by {@code spinDegrees}, written to {@code out} as {@code [ux,uy,uz, vx,vy,vz]}. Picks the
     * world X axis as the seed when the direction is near-vertical, else world up — so a dish aimed
     * straight up still gets a stable basis.
     */
    public static void basis(float dx, float dy, float dz, float spinDegrees, float[] out) {
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6F) {
            dx = 0.0F;
            dy = 1.0F;
            dz = 0.0F;
            len = 1.0F;
        }
        float nx = dx / len;
        float ny = dy / len;
        float nz = dz / len;
        // Seed: anything not parallel to n.
        float sx;
        float sy;
        float sz;
        if (Math.abs(ny) > 0.9F) {
            sx = 1.0F;
            sy = 0.0F;
            sz = 0.0F;
        } else {
            sx = 0.0F;
            sy = 1.0F;
            sz = 0.0F;
        }
        // u = normalise(s × n), v = n × u.
        float ux = sy * nz - sz * ny;
        float uy = sz * nx - sx * nz;
        float uz = sx * ny - sy * nx;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        float vx = ny * uz - nz * uy;
        float vy = nz * ux - nx * uz;
        float vz = nx * uy - ny * ux;
        // Spin both about n (Rodrigues, n unit).
        float rad = spinDegrees * ((float) Math.PI / 180.0F);
        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);
        out[0] = ux * cos + vx * sin;
        out[1] = uy * cos + vy * sin;
        out[2] = uz * cos + vz * sin;
        out[3] = vx * cos - ux * sin;
        out[4] = vy * cos - uy * sin;
        out[5] = vz * cos - uz * sin;
    }

    /** One vertex with tint + full sprite UV + packed light + normal. */
    public static void vertex(VertexConsumer c, PoseStack.Pose pose, int light, int rgb,
            float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        c.addVertex(pose, x, y, z)
                .setColor(red(rgb), green(rgb), blue(rgb), 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    // ---- 0.4.0 shared kit: status lamp and heat shimmer ----------------------------------------
    //
    // A local take on the status-lamp idea NeroTech's machines use, written against NeroPower's own
    // readout codes and amber palette, so no renderer here links against another mod's client classes.

    /** Readout codes every 0.4.0 block entity syncs, in contract order. */
    public static final int READOUT_REST = 0;
    public static final int READOUT_WORK = 1;
    public static final int READOUT_STALL = 2;
    public static final int READOUT_HOT = 3;
    public static final int READOUT_TRIP = 4;
    public static final int READOUT_FAIL = 5;

    /** Lamp colours, packed 0xRRGGBB: idle ember, working amber gliding to hot, and the three warnings. */
    public static final int LAMP_EMBER_RGB = 0x401806;
    public static final int LAMP_AMBER_RGB = 0xCC7012;
    public static final int LAMP_HOT_RGB = 0xFFA028;
    public static final int LAMP_OVERHEAT_RGB = 0xFF5438;
    public static final int LAMP_TRIP_RGB = 0xF2F4F6;
    public static final int LAMP_FAIL_RGB = 0xFF3824;

    /** The arc-violet ramp (superconducting-tier fields only) and the contamination lime. */
    public static final int ARC_DEEP_RGB = 0x1C1240;
    public static final int ARC_RGB = 0x5B3FD6;
    public static final int ARC_LIGHT_RGB = 0x9B7BFF;
    public static final int ARC_GLOW_RGB = 0xE0D6FF;
    public static final int RAD_LIME_RGB = 0xB6F23A;

    /** Kit sprites painted by {@code tools/painters/kit.py} (white, tinted here). */
    public static final Identifier LAMP_TEX = texture("status_lamp");
    public static final Identifier LAMP_HALF_TEX = texture("status_lamp_half");
    public static final Identifier SHIMMER_TEX = texture("heat_shimmer");

    /** STALL double blink: on 4, off 4, on 4, off 28 (a 40-tick cycle). */
    private static final int STALL_CYCLE = 40;
    /** TRIP: on 30, off 10. FAIL: on 5, off 5 (2 Hz). HOT at its peak: on 4, off 4. */
    private static final int TRIP_CYCLE = 40;
    private static final int TRIP_ON = 30;
    private static final int FAIL_CYCLE = 10;
    private static final int FAIL_ON = 5;
    /** WORK glide between amber and hot, in ticks per full swing. */
    private static final float WORK_GLIDE_TICKS = 40.0F;
    /** Lamp and shimmer time wraps here: a multiple of every cycle above, so nothing jumps at the wrap. */
    private static final long TIME_WRAP = 2400L;
    private static final float LAMP_GAP = 0.004F;
    private static final float SHIMMER_HEIGHT = 0.5F;
    private static final float SHIMMER_SPEED = 0.02F;

    /**
     * Where a model's lamp socket sits (block units, model authored facing north): the socket's x/y
     * box and the z of the lamp quad, just in front of the socket's north face.
     */
    public record LampSocket(float x0, float y0, float x1, float y1, float z) {

        /**
         * The standard socket: model x 13-15, y 12-14, standing half a pixel proud of a front face at
         * z 0. That is the upper left as the player faces the block.
         */
        public static final LampSocket STANDARD = model(13.0F, 12.0F, -0.5F, 15.0F, 14.0F, 0.0F);

        /** From the {@code status_socket} element's {@code from} / {@code to} in the model JSON (pixels). */
        public static LampSocket model(float fromX, float fromY, float fromZ, float toX, float toY, float toZ) {
            return new LampSocket(Math.min(fromX, toX) / 16.0F, Math.min(fromY, toY) / 16.0F,
                    Math.max(fromX, toX) / 16.0F, Math.max(fromY, toY) / 16.0F,
                    Math.min(fromZ, toZ) / 16.0F - LAMP_GAP);
        }
    }

    /** The lamp's share of a renderer's render state. Filled by {@link #lampExtract}. */
    public static final class Lamp {
        /** One of the {@code READOUT_*} codes. */
        public int readout = READOUT_REST;
        /** HOT only: the block is in its top heat bucket, so the lamp blinks rather than holds. */
        public boolean peak;
        /** Draw nothing at all (an unformed structure part, a block with no power to show a lamp). */
        public boolean dark;
        /** False with animations switched off: steady colours, no blink, parked poses. */
        public boolean animate = true;
        /** Game time (wrapped) plus partial tick; 0 while the level is not there yet. */
        public float time;
        long tick;
    }

    /**
     * Copy a block entity's synced readout into {@code lamp}. Call from {@code extractRenderState}
     * with the bucketed fields only: the readout code, and whether the heat bucket is the top one.
     */
    public static void lampExtract(Lamp lamp, int readout, boolean peak, Level level, float partialTick) {
        lamp.readout = Mth.clamp(readout, READOUT_REST, READOUT_FAIL);
        lamp.peak = peak;
        lamp.animate = NeroTechConfig.renderAnimationsEnabled();
        if (level == null) {
            lamp.tick = 0L;
            lamp.time = 0.0F;
            return;
        }
        lamp.tick = level.getGameTime();
        lamp.time = (lamp.tick % TIME_WRAP) + partialTick;
    }

    /**
     * Speed factor for a block's signature motion: 1 working, a quarter when hot, 0 otherwise. A
     * stalled block must HOLD its last pose ({@link #lampHolds}), a tripped or failing one coasts to
     * rest, and only a resting one parks.
     */
    public static float lampMotionScale(Lamp lamp) {
        if (!lamp.animate) {
            return 0.0F;
        }
        return lamp.readout == READOUT_WORK ? 1.0F : lamp.readout == READOUT_HOT ? 0.25F : 0.0F;
    }

    /** True while stalled: freeze mid-stroke, never park. */
    public static boolean lampHolds(Lamp lamp) {
        return lamp.readout == READOUT_STALL;
    }

    /** Whether the lamp is lit this tick. Rhythm carries the state; colour only backs it up. */
    public static boolean lampLit(Lamp lamp) {
        if (lamp.dark) {
            return false;
        }
        if (!lamp.animate) {
            return true;
        }
        switch (lamp.readout) {
            case READOUT_STALL: {
                int phase = (int) (lamp.tick % STALL_CYCLE);
                return phase < 4 || (phase >= 8 && phase < 12);
            }
            case READOUT_HOT:
                return !lamp.peak || (lamp.tick & 7L) < 4L;
            case READOUT_TRIP:
                return lamp.tick % TRIP_CYCLE < TRIP_ON;
            case READOUT_FAIL:
                return lamp.tick % FAIL_CYCLE < FAIL_ON;
            default:
                return true;
        }
    }

    /** The lamp colour for the current readout, packed 0xRRGGBB. */
    public static int lampColor(Lamp lamp) {
        switch (lamp.readout) {
            case READOUT_WORK: {
                if (!lamp.animate) {
                    return LAMP_AMBER_RGB;
                }
                float swing = 0.5F - 0.5F * Mth.cos(lamp.time * ((float) Math.PI * 2.0F) / WORK_GLIDE_TICKS);
                return lerpColor(swing, LAMP_AMBER_RGB, LAMP_HOT_RGB);
            }
            case READOUT_STALL:
                return LAMP_AMBER_RGB;
            case READOUT_HOT:
                return LAMP_OVERHEAT_RGB;
            case READOUT_TRIP:
                return LAMP_TRIP_RGB;
            case READOUT_FAIL:
                return LAMP_FAIL_RGB;
            default:
                return LAMP_EMBER_RGB;
        }
    }

    /** Linear blend of two packed colours ({@code t} clamped to 0..1). */
    public static int lerpColor(float t, int from, int to) {
        float f = Mth.clamp(t, 0.0F, 1.0F);
        int r = (int) Mth.lerp(f, red(from), red(to));
        int g = (int) Mth.lerp(f, green(from), green(to));
        int b = (int) Mth.lerp(f, blue(from), blue(to));
        return (r << 16) | (g << 8) | b;
    }

    /** Draw the lamp in the standard socket. Call inside {@link #rotateToFacing}. */
    public static void lampSubmit(Lamp lamp, PoseStack poseStack, SubmitNodeCollector collector) {
        lampSubmit(lamp, poseStack, collector, LampSocket.STANDARD);
    }

    /**
     * Draw the lamp in {@code socket}: one full-bright quad facing model north. Call inside
     * {@link #rotateToFacing}. With animations off a stalled block shows the half-lit sprite, so the
     * state still reads without a blink.
     */
    public static void lampSubmit(Lamp lamp, PoseStack poseStack, SubmitNodeCollector collector,
            LampSocket socket) {
        if (!lampLit(lamp)) {
            return;
        }
        int rgb = lampColor(lamp);
        Identifier sprite = !lamp.animate && lamp.readout == READOUT_STALL ? LAMP_HALF_TEX : LAMP_TEX;
        float x0 = socket.x0();
        float y0 = socket.y0();
        float x1 = socket.x1();
        float y1 = socket.y1();
        float z = socket.z();
        collector.order(3).submitCustomGeometry(poseStack, RenderTypes.entityCutout(sprite),
                (pose, c) -> face(c, pose, FULL_BRIGHT, rgb, 0, 0, -1,
                        x0, y0, z, 1, 1, x0, y1, z, 1, 0, x1, y1, z, 0, 0, x1, y0, z, 0, 1));
    }

    /**
     * Heat shimmer over a footprint on a top face (block units, from {@code yTop} up half a block):
     * two crossed full-bright quads whose wave sprite rises. Draws only while hot with animations on,
     * and stays inside the block above, so a one-block render-bounds pad covers it.
     */
    public static void shimmerSubmit(Lamp lamp, PoseStack poseStack, SubmitNodeCollector collector,
            float x0, float z0, float x1, float z1, float yTop) {
        if (lamp.readout != READOUT_HOT || !lamp.animate) {
            return;
        }
        int rgb = lerpColor(0.5F, LAMP_OVERHEAT_RGB, 0xFFFFFF);
        float scroll = lamp.time * SHIMMER_SPEED;
        float v = scroll - Mth.floor(scroll);
        float xm = (x0 + x1) / 2.0F;
        float zm = (z0 + z1) / 2.0F;
        collector.order(4).submitCustomGeometry(poseStack, RenderTypes.entityCutout(SHIMMER_TEX),
                (pose, c) -> {
                    scrolledSide(c, pose, rgb, 0, -1, x0, zm, x1, zm, yTop, yTop + SHIMMER_HEIGHT, v);
                    scrolledSide(c, pose, rgb, -1, 0, xm, z0, xm, z1, yTop, yTop + SHIMMER_HEIGHT, v);
                });
    }

    /** Shimmer over the usual 12 x 12 pixel footprint of a full-height block's top. */
    public static void shimmerSubmit(Lamp lamp, PoseStack poseStack, SubmitNodeCollector collector, float yTop) {
        shimmerSubmit(lamp, poseStack, collector, 2.0F / 16.0F, 2.0F / 16.0F, 14.0F / 16.0F, 14.0F / 16.0F, yTop);
    }

    /**
     * One vertical full-bright quad from {@code (xa,za)} to {@code (xb,zb)} showing the whole sprite
     * height starting {@code vStart} (0..1) down the texture. Where the window runs off the bottom of
     * the sprite the quad is split in two, so the result never depends on the texture's wrap mode.
     */
    private static void scrolledSide(VertexConsumer c, PoseStack.Pose pose, int rgb, float nx, float nz,
            float xa, float za, float xb, float zb, float yBottom, float yTop, float vStart) {
        if (vStart <= 0.0F) {
            side(c, pose, rgb, nx, nz, xa, za, xb, zb, yBottom, yTop, 0.0F, 1.0F);
            return;
        }
        float yMid = yTop - (yTop - yBottom) * (1.0F - vStart);
        side(c, pose, rgb, nx, nz, xa, za, xb, zb, yMid, yTop, vStart, 1.0F);
        side(c, pose, rgb, nx, nz, xa, za, xb, zb, yBottom, yMid, 0.0F, vStart);
    }

    private static void side(VertexConsumer c, PoseStack.Pose pose, int rgb, float nx, float nz,
            float xa, float za, float xb, float zb, float yBottom, float yTop, float vTop, float vBottom) {
        face(c, pose, FULL_BRIGHT, rgb, nx, 0, nz,
                xa, yBottom, za, 0, vBottom, xa, yTop, za, 0, vTop,
                xb, yTop, zb, 1, vTop, xb, yBottom, zb, 1, vBottom);
    }
}
