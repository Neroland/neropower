package za.co.neroland.neropower.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import za.co.neroland.neropower.NeroPowerCommon;

/**
 * Beam Alignment Scope overlay. While the player holds the scope and looks at a beam emitter, that
 * emitter's renderer draws a thin dashed line along its link: white while the way is clear, red from
 * the first block in the way onward, with an outline on that block. Client only, for the one emitter
 * looked at, and never past 64 blocks.
 *
 * <p>This is not a level-wide pass: the emitter's own block-entity renderer calls {@link #aimed},
 * {@link #extract} and {@link #submit}, so it needs no registration and costs nothing unless the scope
 * is held on an emitter. It reads the held item by registry id, the crosshair target, and the
 * client's own loaded blocks; it sends nothing and stores nothing.
 */
public final class BeamScopeOverlay {

    private static final Identifier LINE_TEX = PowerRenderHelper.texture("beam_scope_line");
    private static final Identifier BOX_TEX = PowerRenderHelper.texture("beam_scope_box");

    private static final String SCOPE_ITEM = "beam_alignment_scope";
    private static final float MAX = 64.0F;
    /** Each quad shows the sprite's eight dashes over this many blocks. */
    private static final float DASH_SPAN = 8.0F;
    /** Quad half-width (the line itself is an eighth of the sprite's width). */
    private static final float HALF = 0.25F;
    private static final float BOX_PAD = 0.01F;
    private static final int CLEAR_RGB = PowerRenderHelper.LAMP_TRIP_RGB;
    private static final int BLOCKED_RGB = PowerRenderHelper.LAMP_FAIL_RGB;

    /** The overlay's share of a render state. */
    public static final class Scope {
        boolean on;
        float ox;
        float oy;
        float oz;
        float dx;
        float dy;
        float dz;
        /** Length drawn white, and the whole length drawn. */
        float clear;
        float total;
        boolean box;
        float bx;
        float by;
        float bz;

        public void off() {
            this.on = false;
        }
    }

    private BeamScopeOverlay() {
    }

    /** True when the local player holds the scope in either hand and the crosshair is on {@code pos}. */
    public static boolean aimed(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return false;
        }
        if (!isScope(player.getMainHandItem()) && !isScope(player.getOffhandItem())) {
            return false;
        }
        HitResult hit = minecraft.hitResult;
        return hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
                && block.getBlockPos().equals(pos);
    }

    private static boolean isScope(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return NeroPowerCommon.MOD_ID.equals(id.getNamespace()) && SCOPE_ITEM.equals(id.getPath());
    }

    /**
     * Fill {@code scope} for a link leaving {@code origin} at the block-relative point
     * {@code (ox,oy,oz)} along {@code (dx,dy,dz)} for {@code length} blocks. {@code blockedAt} is the
     * distance to the first block in the way ({@code < 0} when clear) and {@code hit} that block.
     */
    public static void extract(Scope scope, BlockPos origin, float ox, float oy, float oz,
            float dx, float dy, float dz, float length, float blockedAt, BlockPos hit) {
        scope.on = false;
        float norm = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (norm < 1.0E-4F || length < 1.0E-3F) {
            return;
        }
        scope.on = true;
        scope.ox = ox;
        scope.oy = oy;
        scope.oz = oz;
        scope.dx = dx / norm;
        scope.dy = dy / norm;
        scope.dz = dz / norm;
        scope.total = Math.min(length, MAX);
        scope.clear = blockedAt >= 0.0F ? Math.min(blockedAt, scope.total) : scope.total;
        scope.box = blockedAt >= 0.0F && blockedAt <= MAX && hit != null;
        if (scope.box && hit != null) {
            scope.bx = hit.getX() - origin.getX();
            scope.by = hit.getY() - origin.getY();
            scope.bz = hit.getZ() - origin.getZ();
        }
    }

    public static void submit(Scope scope, PoseStack poseStack, SubmitNodeCollector collector) {
        if (!scope.on) {
            return;
        }
        int light = PowerRenderHelper.FULL_BRIGHT;
        float[] basis = new float[6];
        PowerRenderHelper.basis(scope.dx, scope.dy, scope.dz, 0.0F, basis);
        float ax = basis[0] * HALF;
        float ay = basis[1] * HALF;
        float az = basis[2] * HALF;
        float bx = basis[3] * HALF;
        float by = basis[4] * HALF;
        float bz = basis[5] * HALF;
        float sx = scope.ox;
        float sy = scope.oy;
        float sz = scope.oz;
        float dirX = scope.dx;
        float dirY = scope.dy;
        float dirZ = scope.dz;
        float clear = scope.clear;
        float total = scope.total;
        collector.order(5).submitCustomGeometry(poseStack, RenderTypes.entityCutout(LINE_TEX), (pose, c) -> {
            float start = 0.0F;
            while (start < total - 1.0E-3F) {
                // Cut at the colour change as well as every DASH_SPAN blocks.
                float end = Math.min(total, start + DASH_SPAN);
                if (start < clear && end > clear) {
                    end = clear;
                }
                int rgb = start < clear ? CLEAR_RGB : BLOCKED_RGB;
                float v1 = (end - start) / DASH_SPAN;
                BeamEndpointRenderer.strip(c, pose, light, rgb, sx, sy, sz, dirX, dirY, dirZ, start, end,
                        ax, ay, az, 0.0F, v1);
                BeamEndpointRenderer.strip(c, pose, light, rgb, sx, sy, sz, dirX, dirY, dirZ, start, end,
                        bx, by, bz, 0.0F, v1);
                start = end;
            }
        });
        if (scope.box) {
            float x0 = scope.bx - BOX_PAD;
            float y0 = scope.by - BOX_PAD;
            float z0 = scope.bz - BOX_PAD;
            float x1 = scope.bx + 1.0F + BOX_PAD;
            float y1 = scope.by + 1.0F + BOX_PAD;
            float z1 = scope.bz + 1.0F + BOX_PAD;
            collector.order(6).submitCustomGeometry(poseStack, RenderTypes.entityCutout(BOX_TEX),
                    (pose, c) -> PowerRenderHelper.box(c, pose, light, BLOCKED_RGB, x0, y0, z0, x1, y1, z1));
        }
    }
}
