package za.co.neroland.neropower.beam;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neropower.registry.content.GridContent;

/**
 * Phased Array Emitter block (0.4.0): a long-range, low-loss source that serves several receivers in turn.
 * The Configurator links it like a transmitter: click it, then each receiver.
 */
public class PhasedArrayEmitterBlock extends BeamEndpointBlock {

    public static final MapCodec<PhasedArrayEmitterBlock> CODEC = BlockCodecs.simple(PhasedArrayEmitterBlock::new);

    public PhasedArrayEmitterBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<? extends PhasedArrayEmitterBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhasedArrayEmitterBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return GridContent.PHASED_ARRAY_EMITTER_BE.get();
    }
}
