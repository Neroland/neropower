package za.co.neroland.neropower.client.render.legacy;

// Minecraft 1.21.1 - 1.21.8 only (empty on every other line). Part of a small look-alike of the 1.21.9+
// block-entity render-state API (extract a state, then submit geometry), so NeroPower's renderers keep one
// source on every line. The build renames the vanilla imports to these classes below 1.21.9 (see
// legacyRenames), and LegacyBlockEntityRenderer adapts them to the older immediate-mode renderer.

//? if <1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Runs a render-state renderer inside the pre-1.21.9 immediate-mode BlockEntityRenderer: extract a fresh
// state for the block entity, then submit it straight into the frame's buffers.
public final class LegacyBlockEntityRenderer<T extends BlockEntity, S extends BlockEntityRenderState>
        implements net.minecraft.client.renderer.blockentity.BlockEntityRenderer<T> {

    private final BlockEntityRenderer<T, S> renderer;
    private final CameraRenderState camera = new CameraRenderState();

    public LegacyBlockEntityRenderer(BlockEntityRenderer<T, S> renderer) {
        this.renderer = renderer;
    }

    private void draw(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light,
            Vec3 cameraPos) {
        S state = this.renderer.createRenderState();
        this.renderer.extractRenderState(blockEntity, state, partialTick, cameraPos, null);
        state.lightCoords = light;
        this.renderer.submit(state, poseStack, new SubmitNodeCollector(buffers), this.camera);
    }

    // NeoForge routes the per-block-entity frustum test through this; an unused method elsewhere.
    public AABB getRenderBoundingBox(T blockEntity) {
        return this.renderer.getRenderBoundingBox(blockEntity);
    }

*///?}
//? if >=1.21.5 && <1.21.9 {
/*    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light,
            int overlay, Vec3 cameraPos) {
        draw(blockEntity, partialTick, poseStack, buffers, light, cameraPos);
    }
*///?}
//? if <1.21.5 {
/*    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light,
            int overlay) {
        draw(blockEntity, partialTick, poseStack, buffers, light,
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
    }
*///?}
//? if <1.21.9 {
/*}
*///?}
