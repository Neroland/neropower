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
 * Storage Ring Controller block (contract row 20, M A): replaces a mid-side coil of the ring and owns its
 * energy, coolant and failure ladder. Carries the {@code alarm} state the failure telegraph drives.
 */
public class SmesControllerBlock extends NeroPowerMachineBlock {

    public static final MapCodec<SmesControllerBlock> CODEC = BlockCodecs.simple(SmesControllerBlock::new);

    public SmesControllerBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<SmesControllerBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean hasAlarm() {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmesControllerBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return StorageContent.SMES_CONTROLLER.get();
    }
}
