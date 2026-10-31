package za.co.neroland.neropower.client.render.legacy;

// Minecraft 1.21.1 - 1.21.8 only (empty on every other line). Part of a small look-alike of the 1.21.9+
// block-entity render-state API (extract a state, then submit geometry), so NeroPower's renderers keep one
// source on every line. The build renames the vanilla imports to these classes below 1.21.9 (see
// legacyRenames), and LegacyBlockEntityRenderer adapts them to the older immediate-mode renderer.

//? if <1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

// Draws straight into the frame's buffer source, so geometry submitted here renders in call order.
public final class SubmitNodeCollector {

    private final MultiBufferSource buffers;

    public SubmitNodeCollector(MultiBufferSource buffers) {
        this.buffers = buffers;
    }

    public MultiBufferSource buffers() {
        return this.buffers;
    }

    // Draw-order hint. Immediate-mode rendering draws in call order, so the hint is not needed.
    public SubmitNodeCollector order(int order) {
        return this;
    }

    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer renderer) {
        renderer.render(poseStack.last(), this.buffers.getBuffer(renderType));
    }

    @FunctionalInterface
    public interface CustomGeometryRenderer {
        void render(PoseStack.Pose pose, VertexConsumer consumer);
    }
}
*///?}
