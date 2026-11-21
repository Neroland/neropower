package za.co.neroland.neropower.grid;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
import za.co.neroland.neropower.registry.content.GridContent;

/**
 * Power Monitor block: a wall plate two pixels deep against the block behind its face, which it reads.
 * Comparator = that block's fill.
 */
public class PowerMonitorBlock extends NeroPowerMachineBlock {

    public static final MapCodec<PowerMonitorBlock> CODEC = BlockCodecs.simple(PowerMonitorBlock::new);

    private static final VoxelShape NORTH = Block.box(0, 0, 14, 16, 16, 16);
    private static final VoxelShape SOUTH = Block.box(0, 0, 0, 16, 16, 2);
    private static final VoxelShape WEST = Block.box(14, 0, 0, 16, 16, 16);
    private static final VoxelShape EAST = Block.box(0, 0, 0, 2, 16, 16);

    public PowerMonitorBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<PowerMonitorBlock> codec() {
        return CODEC;
    }

    /** The plate sits on the side opposite its face (the face looks at the player who placed it). */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return switch (facing) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    //? if >=1.21.9 {
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
    //?} else {
    /*protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
    *///?}
        return level.getBlockEntity(pos) instanceof PowerMonitorBlockEntity monitor ? monitor.comparatorSignal() : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerMonitorBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return GridContent.POWER_MONITOR_BE.get();
    }
}
