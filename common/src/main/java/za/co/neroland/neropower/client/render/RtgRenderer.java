package za.co.neroland.neropower.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerotech.config.NeroTechConfig;

import za.co.neroland.neropower.environmental.RadioisotopeGeneratorBlockEntity;

/**
 * Radioisotope Generator BER: four diagonal {@code rtg_fin} vanes in the corner gaps between the
 * model's fin slabs, glowing on the NeroPower power ramp with the synced output permille (a fresh
 * pellet runs white-gold, a nearly spent one a dull ember) and shimmering slowly — decay heat made
 * visible. No pellet = no output = nothing drawn: the finned canister is the whole machine.
 *
 * <p>Sync discipline: extraction reads only the synced {@code outputFraction()} (the output
 * permille rides the update tag on bucket change). {@code renderAnimationsEnabled=false} holds
 * the fins at a steady glow.</p>
 */
public class RtgRenderer
        implements BlockEntityRenderer<RadioisotopeGeneratorBlockEntity, RtgRenderer.State> {

    private static final Identifier FIN_TEX = PowerRenderHelper.texture("rtg_fin");

    /** Vane vertical extent (between the base plate and the column top). */
    private static final float FIN_Y0 = 2.0F / 16.0F;
    private static final float FIN_Y1 = 14.0F / 16.0F;
    /** Vane runs from the column corner (4 px in) out to half a pixel short of the block corner. */
    private static final float INNER = 4.0F / 16.0F;
    private static final float OUTER = 0.5F / 16.0F;
    /** Shimmer period (radians per tick). */
    private static final float SHIMMER_SPEED = 0.05F;

    /** Render state: the resolved vane tint, nothing read at submit time. */
    public static class State extends BlockEntityRenderState {
        /** 0..1 output fraction (0 = nothing to draw). */
        float output;
        float shimmer;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** One-block pad; NeoForge-only frustum hook, inert non-override on Fabric/Forge (NeroTech recipe). */
    public AABB getRenderBoundingBox(RadioisotopeGeneratorBlockEntity rtg) {
        return new AABB(rtg.getBlockPos()).inflate(1.0);
    }

    @Override
    public void extractRenderState(RadioisotopeGeneratorBlockEntity rtg, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(rtg, state, partialTick, cameraPos, breakProgress);
        Level level = rtg.getLevel();
        state.output = rtg.outputFraction();
        // Explicit null check (not folded into a flag) so ecj's null-flow analysis can track it.
        if (level == null || !NeroTechConfig.renderAnimationsEnabled()) {
            state.shimmer = 1.0F; // static frame
            return;
        }
        float now = level.getGameTime() + partialTick;
        state.shimmer = 0.85F + 0.15F * Mth.sin(now * SHIMMER_SPEED);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        if (state.output <= 0.0F) {
            return; // no pellet: the static canister is the whole machine
        }
        int light = PowerRenderHelper.FULL_BRIGHT;
        // The ramp tracks output; the whole vane also fades with it so a dying pellet reads dim.
        int rgb = PowerRenderHelper.dim(PowerRenderHelper.powerColor(state.output),
                (0.35F + 0.65F * state.output) * state.shimmer);
        collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(FIN_TEX),
                (pose, c) -> {
                    // Four diagonal vanes, one per corner gap, running column corner → block corner.
                    vane(c, pose, light, rgb, 1.0F - INNER, 1.0F - INNER, 1.0F - OUTER, 1.0F - OUTER); // +X +Z
                    vane(c, pose, light, rgb, INNER, 1.0F - INNER, OUTER, 1.0F - OUTER);               // -X +Z
                    vane(c, pose, light, rgb, INNER, INNER, OUTER, OUTER);                             // -X -Z
                    vane(c, pose, light, rgb, 1.0F - INNER, INNER, 1.0F - OUTER, OUTER);               // +X -Z
                });
    }

    /** One vertical, double-sided vane from {@code (x0, z0)} to {@code (x1, z1)} across the fin band. */
    private static void vane(VertexConsumer c, PoseStack.Pose pose, int light, int rgb,
            float x0, float z0, float x1, float z1) {
        // Normal: horizontal, perpendicular to the vane's run.
        float dx = x1 - x0;
        float dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        float nx = len > 0.0F ? -dz / len : 0.0F;
        float nz = len > 0.0F ? dx / len : 1.0F;
        PowerRenderHelper.face(c, pose, light, rgb, nx, 0, nz,
                x0, FIN_Y0, z0, 0, 1,
                x0, FIN_Y1, z0, 0, 0,
                x1, FIN_Y1, z1, 1, 0,
                x1, FIN_Y0, z1, 1, 1);
    }
}
