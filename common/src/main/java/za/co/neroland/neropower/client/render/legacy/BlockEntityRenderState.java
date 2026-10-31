package za.co.neroland.neropower.client.render.legacy;

// Minecraft 1.21.1 - 1.21.8 only (empty on every other line). Part of a small look-alike of the 1.21.9+
// block-entity render-state API (extract a state, then submit geometry), so NeroPower's renderers keep one
// source on every line. The build renames the vanilla imports to these classes below 1.21.9 (see
// legacyRenames), and LegacyBlockEntityRenderer adapts them to the older immediate-mode renderer.

//? if <1.21.9 {
/*import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BlockEntityRenderState {

    public BlockPos blockPos = BlockPos.ZERO;
    public BlockState blockState;
    public BlockEntityType<?> blockEntityType;
    public int lightCoords;
    public ModelFeatureRenderer.CrumblingOverlay breakProgress;
}
*///?}
