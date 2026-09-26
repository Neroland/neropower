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

/** Radioisotope Generator block — directional, ticks its {@link RadioisotopeGeneratorBlockEntity}. */
public class RadioisotopeGeneratorBlock extends NeroPowerMachineBlock {

    public static final MapCodec<RadioisotopeGeneratorBlock> CODEC = BlockCodecs.simple(RadioisotopeGeneratorBlock::new);

    public RadioisotopeGeneratorBlock(Properties properties) {
        super(properties);
    }

    // No @Override: 26.3 removed Block#codec (see Core's BlockCodecs) — NeroTech's blocks do the same.
    protected MapCodec<RadioisotopeGeneratorBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadioisotopeGeneratorBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return EnvironmentalContent.RTG_TYPE.get();
    }
}
