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

/** Dry Cask — directional long-term waste store, ticks its {@link DryCaskBlockEntity}. */
public class DryCaskBlock extends NeroPowerMachineBlock {

    public static final MapCodec<DryCaskBlock> CODEC = BlockCodecs.simple(DryCaskBlock::new);

    public DryCaskBlock(Properties properties) {
        super(properties);
    }

    /** In-world status for the painted state models. */
    @Override
    protected boolean hasStatus() {
        return true;
    }

    // No @Override: 26.3 removed Block#codec (see Core's BlockCodecs).
    protected MapCodec<DryCaskBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DryCaskBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return BackEndContent.DRY_CASK_TYPE.get();
    }
}
