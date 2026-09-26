package za.co.neroland.neropower.beam;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

/**
 * Beam Relay (Stage 6) — receives like a receiver and forwards like a transmitter, so a beam can
 * bend around terrain or extend past {@code beamRange}. It carries only a small pass-through
 * buffer ({@value #BUFFER} NE): what arrives is forwarded to its own target <i>in the same pass</i>
 * (chain depth capped by {@code beamMaxHops}, loops cut by the pass's visited set), and whatever
 * is left drains on the relay's own tick. Its faces are disabled by default — a relay is beam-in,
 * beam-out; open a face in the Side Config tab to tap it.
 */
public class BeamRelayBlockEntity extends BeamTransmitterBlockEntity {

    /** Pass-through buffer: room for a few passes of the default {@code beamTransferPerPass}. */
    public static final int BUFFER = 8_000;

    /** Per-tick face transfer, only relevant when a player opens a face. */
    public static final int TRANSFER = 4_000;

    public BeamRelayBlockEntity(BlockPos pos, BlockState state) {
        super(BeamContent.BEAM_RELAY_BE.get(), pos, state, BUFFER, TRANSFER);
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .defaultPreset(SidePreset.ALL_DISABLED)
                .build());
    }

    @Override
    public boolean acceptsBeam() {
        return true;
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        super.tickMachine(level, pos, state);
        // An unlinked relay is just a receiver with a small buffer: show what is arriving, not UNLINKED.
        if (target() == null && recentlyReceived(level.getGameTime())) {
            setStatus(BeamStatus.RECEIVING);
            setActive(true);
        }
    }

    /**
     * Energy just landed by beam: forward it on at once, continuing the sender's pass (same visited
     * set, one hop deeper). The transmitter's own loop / depth guards apply unchanged.
     */
    @Override
    public void onBeamReceived(long accepted, Set<BlockPos> visited, int hops) {
        super.onBeamReceived(accepted, visited, hops);
        Level level = this.level;
        if (accepted > 0L && level != null && !level.isClientSide()) {
            transmit(level, this.worldPosition, visited, hops);
        }
    }
}
