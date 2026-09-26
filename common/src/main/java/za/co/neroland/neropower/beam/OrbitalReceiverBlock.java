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
 * Orbital Receiver block (Stage 6) — a {@link BeamReceiverBlock} whose block entity is the one
 * legal cross-dimension beam target.
 */
public class OrbitalReceiverBlock extends BeamReceiverBlock {

    public static final MapCodec<OrbitalReceiverBlock> CODEC = BlockCodecs.simple(OrbitalReceiverBlock::new);

    public OrbitalReceiverBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BeamReceiverBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OrbitalReceiverBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return BeamContent.ORBITAL_RECEIVER_BE.get();
    }
}
