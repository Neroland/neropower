package za.co.neroland.neropower.turbine;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neropower.multiblock.MultiblockPartBlock;

/**
 * Turbine Spindle: the axle down the middle of every interior slice. BE-less; its {@code vanes} state
 * says which Vane Set rides it (mounted by right-clicking with the set, see {@link VaneSetItem}), and the
 * helm reads it per slice. Breaking it drops the spindle and the mounted set through the loot table's
 * block-state conditions, so nothing is duplicated or lost. {@code axis} follows the clicked face.
 */
public class TurbineSpindleBlock extends MultiblockPartBlock {

    public static final MapCodec<TurbineSpindleBlock> CODEC = BlockCodecs.simple(TurbineSpindleBlock::new);

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final EnumProperty<VaneTier> VANES = EnumProperty.create("vanes", VaneTier.class);

    @SuppressWarnings("this-escape")
    public TurbineSpindleBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AXIS, Direction.Axis.Z)
                .setValue(VANES, VaneTier.NONE));
    }

    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, VANES);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(AXIS, TurbineStatorBlock.rotateAxis(state.getValue(AXIS), rotation));
    }

    /** The vane tier of a spindle state ({@link VaneTier#NONE} for anything else). */
    public static VaneTier vanes(BlockState state) {
        return state.hasProperty(VANES) ? state.getValue(VANES) : VaneTier.NONE;
    }

    /**
     * Mount {@code tier} on a bare spindle (server side). The state change keeps the block, so the part
     * hook does not fire; the structure is marked dirty here instead.
     *
     * @return true when the set was mounted (the caller consumes it)
     */
    public static boolean mount(Level level, BlockPos pos, BlockState state, VaneTier tier) {
        if (tier == VaneTier.NONE || !state.hasProperty(VANES) || state.getValue(VANES) != VaneTier.NONE) {
            return false;
        }
        level.setBlock(pos, state.setValue(VANES, tier), Block.UPDATE_ALL);
        notifyChanged(level, pos);
        return true;
    }

    /** Strip the vanes (overspeed {@code shed_blades}); the set is destroyed, nothing drops. */
    public static void shed(Level level, BlockPos pos, BlockState state) {
        if (state.hasProperty(VANES) && state.getValue(VANES) != VaneTier.NONE) {
            level.setBlock(pos, state.setValue(VANES, VaneTier.NONE), Block.UPDATE_ALL);
        }
    }
}
