package za.co.neroland.neropower.beam;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

/**
 * Beam Relay block (Stage 6) — a {@link BeamTransmitterBlock} (same {@code linked} state) whose
 * block entity also accepts an incoming beam and forwards it.
 */
public class BeamRelayBlock extends BeamTransmitterBlock {

    public static final MapCodec<BeamRelayBlock> CODEC = BlockCodecs.simple(BeamRelayBlock::new);

    public BeamRelayBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BeamTransmitterBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeamRelayBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return BeamContent.BEAM_RELAY_BE.get();
    }
}
