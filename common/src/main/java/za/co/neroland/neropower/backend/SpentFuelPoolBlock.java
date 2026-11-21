package za.co.neroland.neropower.backend;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neropower.machine.NeroPowerMachineBlock;
import za.co.neroland.neropower.registry.content.BackEndContent;

/** Spent Fuel Rack ({@code spent_fuel_pool}) — a single rack block placed under water, ticks its {@link SpentFuelPoolBlockEntity}; carries the alarm state. */
public class SpentFuelPoolBlock extends NeroPowerMachineBlock {

    public static final MapCodec<SpentFuelPoolBlock> CODEC = BlockCodecs.simple(SpentFuelPoolBlock::new);

    public SpentFuelPoolBlock(Properties properties) {
        super(properties);
    }

    // No @Override: 26.3 removed Block#codec (see Core's BlockCodecs).
    protected MapCodec<SpentFuelPoolBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean hasAlarm() {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpentFuelPoolBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return BackEndContent.SPENT_FUEL_POOL_TYPE.get();
    }
}
