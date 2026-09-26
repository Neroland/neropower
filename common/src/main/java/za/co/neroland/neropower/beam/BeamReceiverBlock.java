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
 * Beam Receiver block (Stage 6) — directional, ticks its {@link BeamReceiverBlockEntity}. Holds
 * no link of its own; the Configurator click on it completes a pending link from a transmitter.
 */
public class BeamReceiverBlock extends BeamEndpointBlock {

    public static final MapCodec<BeamReceiverBlock> CODEC = BlockCodecs.simple(BeamReceiverBlock::new);

    public BeamReceiverBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<? extends BeamReceiverBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeamReceiverBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return BeamContent.BEAM_RECEIVER_BE.get();
    }
}
