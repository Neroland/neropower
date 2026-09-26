package za.co.neroland.neropower.fission;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;
import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;

import za.co.neroland.neropower.machine.NeroPowerMachineBlock;

/**
 * Fission Reactor controller block — the wall-centre block of the {@link FissionStructure} shell.
 * Carries the {@link NeroPowerMachineBlock#ALARM} state (the failure ladder lights it from WARNING
 * up) and ticks its {@link FissionCoreBlockEntity}. Neighbour changes reach the block entity through
 * NeroTech's thermal-link invalidation, which the core also uses to schedule a structure recheck.
 */
public class FissionCoreBlock extends NeroPowerMachineBlock {

    public static final MapCodec<FissionCoreBlock> CODEC = BlockCodecs.simple(FissionCoreBlock::new);

    public FissionCoreBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<FissionCoreBlock> codec() {
        return CODEC;
    }

    /** Constant, per the base contract: consulted from the {@code Block} constructor. */
    @Override
    protected boolean hasAlarm() {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FissionCoreBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return FissionContent.FISSION_CORE_TYPE.get();
    }
}
