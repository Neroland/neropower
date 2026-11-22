package za.co.neroland.neropower.generation;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neropower.machine.NeroPowerMachineBlock;
import za.co.neroland.neropower.registry.content.GenerationContent;

/** Betavoltaic Cell — half-height, directional, ticks its {@link BetavoltaicCellBlockEntity}. */
public class BetavoltaicCellBlock extends NeroPowerMachineBlock {

    public static final MapCodec<BetavoltaicCellBlock> CODEC = BlockCodecs.simple(BetavoltaicCellBlock::new);

    /** The bottom half of the block (the heat-spreader plinth and wafer). */
    //? if >=1.21.5 {
    private static final VoxelShape SHAPE = Block.column(16.0D, 0.0D, 8.0D);
    //?} else {
    /*private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
    *///?}

    public BetavoltaicCellBlock(Properties properties) {
        super(properties);
    }

    /** In-world status for the painted state models (decision 012 addendum). */
    @Override
    protected boolean hasStatus() {
        return true;
    }

    // No @Override: 26.3 removed Block#codec (see Core's BlockCodecs).
    protected MapCodec<BetavoltaicCellBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BetavoltaicCellBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return GenerationContent.BETAVOLTAIC_CELL_TYPE.get();
    }

    //? if <1.21.5 {
    /*// Before 1.21.5 the block spills the slots (NeroTech's onRemove); seal a part-used capsule first.
    @Override
    protected void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState,
            boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof BetavoltaicCellBlockEntity cell) {
            cell.sealOnRemoval();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    *///?}
}
