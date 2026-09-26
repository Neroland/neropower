package za.co.neroland.neropower.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import za.co.neroland.neropower.NeroPowerCommon;

/**
 * Shared textured-quad helpers for NeroPower's block-entity renderers — NeroTech's
 * {@code MachineRenderHelper} recipe carried over verbatim (full-bright emissive light, double-sided
 * faces, simple boxes with a full sprite per face) so NeroPower's client never links against
 * NeroTech's client classes, plus the NeroPower "power" ramp: the same heat-readability lerp, but
 * deep ember → amber → hot orange → white-gold instead of NeroTech's cyan → amber → red, and a few
 * oriented-quad helpers the dish / beam / fin renderers need.
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
}
