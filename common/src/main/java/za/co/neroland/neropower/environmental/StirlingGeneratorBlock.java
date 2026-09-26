package za.co.neroland.neropower.environmental;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neropower.machine.NeroPowerMachineBlock;

/** Stirling Generator block — directional, ticks its {@link StirlingGeneratorBlockEntity}. */
public class StirlingGeneratorBlock extends NeroPowerMachineBlock {

    public static final MapCodec<StirlingGeneratorBlock> CODEC = BlockCodecs.simple(StirlingGeneratorBlock::new);

    public StirlingGeneratorBlock(Properties properties) {
        super(properties);
    }

    // No @Override: 26.3 removed Block#codec (see Core's BlockCodecs) — NeroTech's blocks do the same.
    protected MapCodec<StirlingGeneratorBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StirlingGeneratorBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return EnvironmentalContent.STIRLING_TYPE.get();
    }
}
