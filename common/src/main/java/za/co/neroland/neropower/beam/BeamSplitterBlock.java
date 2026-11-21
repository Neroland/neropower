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
 * Beam Splitter block (0.4.0): one beam in, up to four (config: eight) beams out. The Configurator links
 * it like a transmitter (click it, then a receiver) and a transmitter can be aimed at it like a receiver.
 */
public class BeamSplitterBlock extends BeamEndpointBlock {

    public static final MapCodec<BeamSplitterBlock> CODEC = BlockCodecs.simple(BeamSplitterBlock::new);

    public BeamSplitterBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<? extends BeamSplitterBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeamSplitterBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return GridContent.BEAM_SPLITTER_BE.get();
    }
}
