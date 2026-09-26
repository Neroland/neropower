package za.co.neroland.neropower.storage;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.platform.EnergyLookup;
import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfigComponent;

import za.co.neroland.nerotech.machine.BatteryBankBlockEntity;

/**
 * The storage tier's own neighbour push — NeroTech's {@code MachineEnergy.pushToNeighbours} with the
 * two things storage needs on top: a <b>slosh guard</b> (a storage block never pushes into another
 * storage block, so two half-full cells, a cell and its controller, or a cell and a NeroTech
 * Battery Bank never ping-pong energy every tick — Core's {@code AdjacentEnergyPusher.skipBatteries}
 * idea) and the optional <b>PRIORITY_SOURCE</b> fill test. The source is any
 * {@link NeroEnergyStorage} (a cell's buffer or the controller's pool), and the budget is a total
 * for the tick, not per side, so a bank's effective I/O really is its ceiling.
 */
public final class StorageEnergy {

    private StorageEnergy() {
    }

    /** Whether a block entity is one of the storage blocks the slosh guard protects. */
    public static boolean isStorage(@Nullable BlockEntity be) {
        return be instanceof BatteryCellBlockEntity
                || be instanceof BankControllerBlockEntity
                || be instanceof BatteryBankBlockEntity;
    }

    /**
     * Push up to {@code budget} NE in total from {@code source} into the six neighbours, in
     * {@link Direction} order, skipping storage blocks, anything {@code skip} names, faces the
     * ENERGY side config cannot extract through, and — when {@code priorityThresholdPermille} is
     * non-negative — neighbours already at or above that fill.
     *
     * @param sideConfig                the pusher's side config, or null for every face
     * @param skip                      extra positions never pushed into (a controller's member cells)
     * @param priorityThresholdPermille PRIORITY_SOURCE threshold, or {@code -1} for BUFFER behaviour
     * @return total NE moved
     */
    public static long pushToNeighbours(Level level, BlockPos pos, NeroEnergyStorage source, long budget,
            @Nullable SideConfigComponent sideConfig, @Nullable Predicate<BlockPos> skip,
            int priorityThresholdPermille) {
        if (budget <= 0 || source.getAmount() <= 0) {
            return 0L;
        }
        SideConfigComponent gate =
                (sideConfig != null && sideConfig.config().has(Channel.ENERGY)) ? sideConfig : null;
        long moved = 0L;
        for (Direction side : Direction.values()) {
            long remaining = budget - moved;
            if (remaining <= 0 || source.getAmount() <= 0) {
                break;
            }
            if (gate != null && !gate.config().modeAbsolute(Channel.ENERGY, gate.facing(), side).canExtract()) {
                continue;
            }
            BlockPos neighbourPos = pos.relative(side);
            boolean member = skip != null && skip.test(neighbourPos);
            if (PoolMath.shouldSkip(!member && isStorage(level.getBlockEntity(neighbourPos)), member)) {
                continue; // slosh guard: storage never feeds storage, nor a controller its own cells
            }
            NeroEnergyStorage neighbour = EnergyLookup.INSTANCE.find(level, neighbourPos, side.getOpposite());
            if (neighbour == null || neighbour == source || !neighbour.canReceive()) {
                continue;
            }
            if (priorityThresholdPermille >= 0 && !PoolMath.belowThreshold(neighbour.getAmount(),
                    neighbour.getCapacity(), priorityThresholdPermille)) {
                continue; // PRIORITY_SOURCE: the neighbour is not short, let its generators feed it
            }
            long offer = source.extract(remaining, true);
            if (offer <= 0) {
                continue;
            }
            // MachineEnergy's order: the neighbour commits first, then the source gives exactly that
            // much (a simulated extract of `offer` guarantees `accepted ≤ offer` is available).
            long accepted = neighbour.insert(offer, false);
            if (accepted > 0) {
                source.extract(accepted, false);
                moved += accepted;
            }
        }
        return moved;
    }
}
