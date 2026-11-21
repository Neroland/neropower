package za.co.neroland.neropower.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neropower.multiblock.MultiblockPartBlock;

/**
 * Storage Ring Coil (contract row 21): a block-entity-less ring member. Holds no energy (the controller
 * does), so breaking one can never drop or duplicate charge; it only unforms the ring through
 * {@link MultiblockPartBlock}'s change hook. Its {@link #SHAPE} follows the neighbouring ring members.
 */
public class SmesCoilBlock extends MultiblockPartBlock {

    public static final EnumProperty<SmesCoilShape> SHAPE = EnumProperty.create("shape", SmesCoilShape.class);

    @SuppressWarnings("this-escape")
    public SmesCoilBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(SHAPE, SmesCoilShape.STRAIGHT_X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SHAPE);
    }

    /** Whether a state is a ring member a coil joins visually (a coil or the controller). */
    public static boolean ringMember(BlockState state) {
        return state.getBlock() instanceof SmesCoilBlock || state.getBlock() instanceof SmesControllerBlock;
    }

    /** The shape for a coil at {@code pos}, from its four horizontal neighbours. */
    public static SmesCoilShape shapeAt(BlockGetter level, BlockPos pos) {
        return SmesCoilShape.of(
                ringMember(level.getBlockState(pos.north())),
                ringMember(level.getBlockState(pos.south())),
                ringMember(level.getBlockState(pos.east())),
                ringMember(level.getBlockState(pos.west())));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(SHAPE, shapeAt(context.getLevel(), context.getClickedPos()));
    }

    //? if >=1.21.2 {
    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader level,
            net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, net.minecraft.util.RandomSource random) {
        if (direction.getAxis().isHorizontal() && state.is(this)) {
            return state.setValue(SHAPE, shapeAt(level, pos));
        }
        return state;
    }
    //?} else {
    /*@Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
            net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction.getAxis().isHorizontal() && state.is(this)) {
            return state.setValue(SHAPE, shapeAt(level, pos));
        }
        return state;
    }
    *///?}
}
