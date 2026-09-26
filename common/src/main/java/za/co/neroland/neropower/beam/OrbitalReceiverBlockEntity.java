package za.co.neroland.neropower.beam;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Orbital Receiver (Stage 6) — a {@link BeamReceiverBlockEntity} that may be targeted from a
 * transmitter in <i>another</i> dimension, provided {@code beamCrossDimension} is on and this
 * block sits in a dimension Core tags as space ({@code neroland:space/dimensions}). A
 * cross-dimension hop skips line of sight and pays the fixed {@code beamOrbitalHopLossPermille}
 * instead of distance loss; it never force-loads a chunk. Same-dimension links work exactly like
 * an ordinary receiver.
 */
public class OrbitalReceiverBlockEntity extends BeamReceiverBlockEntity {

    public OrbitalReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(BeamContent.ORBITAL_RECEIVER_BE.get(), pos, state);
    }

    @Override
    public boolean orbital() {
        return true;
    }
}
