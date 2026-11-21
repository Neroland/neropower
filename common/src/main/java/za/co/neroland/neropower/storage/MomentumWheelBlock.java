package za.co.neroland.neropower.storage;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neropower.machine.NeroPowerMachineBlock;
import za.co.neroland.neropower.registry.content.StorageContent;

/**
 * Momentum Wheel block (0.4.0): a kinetic store. Directional only; the rim is drawn by the renderer from
 * the block entity's synced speed bucket.
 */
public class MomentumWheelBlock extends NeroPowerMachineBlock {

    public static final MapCodec<MomentumWheelBlock> CODEC = BlockCodecs.simple(MomentumWheelBlock::new);

    public MomentumWheelBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<MomentumWheelBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MomentumWheelBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return StorageContent.MOMENTUM_WHEEL.get();
    }
}
