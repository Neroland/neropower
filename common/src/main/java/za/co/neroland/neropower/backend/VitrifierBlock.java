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

/** Vitrifier — directional, ticks its {@link VitrifierBlockEntity}. */
public class VitrifierBlock extends NeroPowerMachineBlock {

    public static final MapCodec<VitrifierBlock> CODEC = BlockCodecs.simple(VitrifierBlock::new);

    public VitrifierBlock(Properties properties) {
        super(properties);
    }

    // No @Override: 26.3 removed Block#codec (see Core's BlockCodecs).
    protected MapCodec<VitrifierBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VitrifierBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return BackEndContent.VITRIFIER_TYPE.get();
    }
}
