package za.co.neroland.neropower.generation;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neropower.machine.NeroPowerMachineBlock;
import za.co.neroland.neropower.registry.content.GenerationContent;

/** Hydrogen Fuel Cell — directional, ticks its {@link FuelCellBlockEntity}. */
public class FuelCellBlock extends NeroPowerMachineBlock {

    public static final MapCodec<FuelCellBlock> CODEC = BlockCodecs.simple(FuelCellBlock::new);

    public FuelCellBlock(Properties properties) {
        super(properties);
    }

    // No @Override: 26.3 removed Block#codec (see Core's BlockCodecs).
    protected MapCodec<FuelCellBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FuelCellBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return GenerationContent.FUEL_CELL_TYPE.get();
    }
}
