package za.co.neroland.neropower.fission;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neropower.registry.content.FissionContent;

/**
 * Reactor Control Computer: a reactor wall block (one per shell; a 7³ shell needs it) with redstone and
 * comparator outputs. Its redstone output is 15 while the signal chosen in its screen (heat, fuel, output
 * or failure stage) is past its threshold; a comparator reads the chosen signal as a level; its screen
 * also sets the auto-SCRAM heat level. Carries the {@code alarm} state, lit while the core it watches is
 * at WARNING or worse (or for a lamp test).
 */
public class ReactorControlComputerBlock extends ReactorShellPortBlock {

    public static final MapCodec<ReactorControlComputerBlock> CODEC =
            BlockCodecs.simple(ReactorControlComputerBlock::new);

    public ReactorControlComputerBlock(Properties properties) {
        super(properties);
    }

    /** In-world status for the painted state models (decision 012 addendum). */
    @Override
    protected boolean hasStatus() {
        return true;
    }

    protected MapCodec<ReactorControlComputerBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean hasAlarm() {
        return true;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof ReactorControlComputerBlockEntity computer
                ? computer.redstoneOutput() : 0;
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
        return level.getBlockEntity(pos) instanceof ReactorControlComputerBlockEntity computer
                ? computer.comparatorSignal() : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReactorControlComputerBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return FissionContent.REACTOR_CONTROL_COMPUTER_TYPE.get();
    }
}
