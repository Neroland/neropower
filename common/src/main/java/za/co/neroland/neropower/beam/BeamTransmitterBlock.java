package za.co.neroland.neropower.beam;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

/**
 * Beam Transmitter block (Stage 6) — directional, ticks its {@link BeamTransmitterBlockEntity},
 * and carries the {@link #LINKED} state the block entity mirrors its link onto (model swap: dark
 * emitter vs lit). Linking is the Configurator click routed by {@link BeamEndpointBlock}.
 */
public class BeamTransmitterBlock extends BeamEndpointBlock {

    /** {@code linked=true} while the block entity holds a target; set by the BE, never by hand. */
    public static final BooleanProperty LINKED = BooleanProperty.create("linked");

    public static final MapCodec<BeamTransmitterBlock> CODEC = BlockCodecs.simple(BeamTransmitterBlock::new);

    @SuppressWarnings("this-escape")
    public BeamTransmitterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LINKED, false));
    }

    protected MapCodec<? extends BeamTransmitterBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LINKED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeamTransmitterBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return BeamContent.BEAM_TRANSMITTER_BE.get();
    }
}
