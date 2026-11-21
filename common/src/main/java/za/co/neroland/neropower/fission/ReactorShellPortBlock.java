package za.co.neroland.neropower.fission;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.neropower.machine.NeroPowerMachineBlock;
import za.co.neroland.neropower.multiblock.MultiblockPartBlock;

/**
 * Base of the machine blocks that sit in a reactor wall (Reactor Steam Port, Reactor Control Computer):
 * a NeroPower machine block whose placement or removal marks the surrounding reactor dirty, exactly like
 * a casing part, so the core revalidates on change.
 */
public abstract class ReactorShellPortBlock extends NeroPowerMachineBlock {

    protected ReactorShellPortBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) {
            MultiblockPartBlock.notifyChanged(level, pos);
        }
    }

    //? if >=1.21.5 {
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
            BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        MultiblockPartBlock.notifyChanged(level, pos);
    }
    //?} else {
    /*@Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            MultiblockPartBlock.notifyChanged(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    *///?}
}
