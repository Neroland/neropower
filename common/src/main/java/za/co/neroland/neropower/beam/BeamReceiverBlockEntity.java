package za.co.neroland.neropower.beam;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.nerotech.config.NeroTechConfig;
import za.co.neroland.nerotech.machine.MachineEnergy;

/**
 * Beam Receiver (Stage 6) — the far end of a beam. Holds no target: a transmitter or relay is
 * aimed at it, its buffer fills by beam, and it feeds its neighbours like a generator (every face
 * output, auto-eject). Same dimension only; an {@link OrbitalReceiverBlockEntity} is the
 * cross-dimension variant.
 */
public class BeamReceiverBlockEntity extends BeamEndpointBlockEntity {

    public BeamReceiverBlockEntity(BlockPos pos, BlockState state) {
        this(BeamContent.BEAM_RECEIVER_BE.get(), pos, state);
    }

    protected BeamReceiverBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .defaultPreset(SidePreset.GENERATOR)
                .autoEject(Channel.ENERGY, true)
                .build());
    }

    @Override
    public boolean acceptsBeam() {
        return true;
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        boolean receiving = recentlyReceived(level.getGameTime());
        if (!receiving && this.status == BeamStatus.RECEIVING) {
            this.lastPass = 0L;
        }
        setStatus(receiving ? BeamStatus.RECEIVING : BeamStatus.IDLE);
        setActive(receiving);
        MachineEnergy.pushToNeighbours(level, pos, energyBuffer(), NeroTechConfig.machineMaxTransfer(), sideConfig());
    }
}
