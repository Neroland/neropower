package za.co.neroland.neropower.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base of every block-entity-less multiblock member (shell, interior, ring). Placing or removing one
 * marks every indexed structure containing it dirty, so controllers validate on change and never on a
 * cadence. A piston moving a part also lands here (removal with {@code movedByPiston}), so a pushed
 * shell unforms its structure like a broken one.
 */
public class MultiblockPartBlock extends Block {

    public MultiblockPartBlock(Properties properties) {
        super(properties);
    }

    /** Mark the structures around a changed part dirty (server side only). */
    public static void notifyChanged(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            MultiblockIndex.markDirty(level.dimension(), pos.getX(), pos.getY(), pos.getZ());
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) {
            notifyChanged(level, pos);
        }
    }

    //? if >=1.21.5 {
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
            BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        notifyChanged(level, pos);
    }
    //?} else {
    /*@Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            notifyChanged(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    *///?}
}
