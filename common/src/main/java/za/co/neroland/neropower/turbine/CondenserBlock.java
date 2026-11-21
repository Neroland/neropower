package za.co.neroland.neropower.turbine;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neropower.machine.NeroPowerMachineBlock;
import za.co.neroland.neropower.registry.content.TurbineContent;

/** Steam Condenser (id {@code condenser}): exhaust steam in, water out, 1:1. See {@link CondenserBlockEntity}. */
public class CondenserBlock extends NeroPowerMachineBlock {

    public static final MapCodec<CondenserBlock> CODEC = BlockCodecs.simple(CondenserBlock::new);

    public CondenserBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<CondenserBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CondenserBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return TurbineContent.CONDENSER_TYPE.get();
    }
}
