package za.co.neroland.neropower.turbine;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neropower.machine.NeroPowerMachineBlock;
import za.co.neroland.neropower.registry.content.TurbineContent;

/**
 * Turbine Helm: the controller at the centre of one end cap of the steam turbine, facing out; the body
 * runs behind it. Carries the {@link NeroPowerMachineBlock#ALARM} state (the overspeed ladder lights it
 * from WARNING up). A rising redstone edge resets a latched overspeed trip once the rotor has coasted
 * below 600 rpm, as the screen's Reset button does.
 */
public class TurbineHelmBlock extends NeroPowerMachineBlock {

    public static final MapCodec<TurbineHelmBlock> CODEC = BlockCodecs.simple(TurbineHelmBlock::new);

    public TurbineHelmBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<TurbineHelmBlock> codec() {
        return CODEC;
    }

    /** Constant, per the base contract: consulted from the {@code Block} constructor. */
    @Override
    protected boolean hasAlarm() {
        return true;
    }

    //? if >=1.21.2 {
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
    //?} else {
    /*@Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            BlockPos fromPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
    *///?}
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TurbineHelmBlockEntity helm) {
            helm.onRedstone(level.hasNeighborSignal(pos));
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurbineHelmBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return TurbineContent.TURBINE_HELM_TYPE.get();
    }
}
