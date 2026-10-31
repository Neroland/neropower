package za.co.neroland.neropower.client.render.legacy;

// Minecraft 1.21.1 - 1.21.8 only (empty on every other line). Part of a small look-alike of the 1.21.9+
// block-entity render-state API (extract a state, then submit geometry), so NeroPower's renderers keep one
// source on every line. The build renames the vanilla imports to these classes below 1.21.9 (see
// legacyRenames), and LegacyBlockEntityRenderer adapts them to the older immediate-mode renderer.

//? if <1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public interface BlockEntityRenderer<T extends BlockEntity, S extends BlockEntityRenderState> {

    S createRenderState();

    default void extractRenderState(T blockEntity, S state, float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        state.blockPos = blockEntity.getBlockPos();
        state.blockState = blockEntity.getBlockState();
        state.blockEntityType = blockEntity.getType();
        state.breakProgress = breakProgress;
    }

    void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState);

    // NeoForge's per-block-entity frustum box (forwarded by LegacyBlockEntityRenderer).
    default AABB getRenderBoundingBox(T blockEntity) {
        return new AABB(blockEntity.getBlockPos());
    }
}
*///?}
