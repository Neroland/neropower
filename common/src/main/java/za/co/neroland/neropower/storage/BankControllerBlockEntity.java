package za.co.neroland.neropower.storage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.nerotech.machine.MachineStatus;

import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;

/**
 * Battery Bank Controller (Stage 5) — pools the Battery Cells connected to it into one energy
 * surface. Every {@value #SCAN_INTERVAL} ticks it re-runs {@link BankNetwork#scan} inside
 * {@code bankScanRadius} (cells touching the controller, then face-to-face through cells only) and
 * hands the member list to its {@link PooledEnergyStorage}; that pool is what {@link #getEnergy()}
 * returns, so Core's capability lookup, the side-config gate and the shared energy gauge on the
 * controller all read the bank rather than the controller's own (zero-capacity) buffer.
 *
 * <p><b>Modes</b> ({@link BankMode}): BUFFER pushes into every willing neighbour each tick;
 * PRIORITY_SOURCE pushes only into neighbours whose own buffer is below
 * {@code bankPriorityThresholdPermille}. Both go through {@link StorageEnergy}, which never feeds a
 * cell, another controller or a NeroTech Battery Bank — and the member set is skipped explicitly on
 * top of that. Neighbours that <i>pull</i> from the controller (pipes, auto-input faces) are served
 * in both modes: the mode governs the controller's own push, not a consumer's request. Cycle the
 * mode by sneak-right-clicking the controller with an empty hand.
 *
 * <p><b>Side posture:</b> {@link SidePreset#STORAGE}, auto-eject off (the push loop here is the
 * eject). Member positions are transient — rebuilt from the world on load, never saved; the mode is
 * the only persisted state beyond the base's. No player data (POPIA/GDPR).
 */
public class BankControllerBlockEntity extends NeroPowerMachineBlockEntity {

    /** Rescan cadence (ticks) — never a per-tick scan; a broken member forces one early. */
    public static final int SCAN_INTERVAL = 20;

    /** Menu-synced ints after the seven shared ones (see {@link #extraData}). */
    public static final int EXTRA_DATA = 7;

    private final PooledEnergyStorage pool = new PooledEnergyStorage();
    /** Member positions from the last scan — the explicit push skip set. */
    private Set<BlockPos> memberPositions = Set.of();
    private BankMode mode = BankMode.BUFFER;
    private final int scanPhase = Math.floorMod(System.identityHashCode(this), SCAN_INTERVAL);

    public BankControllerBlockEntity(BlockPos pos, BlockState state) {
        // The controller's own buffer is a placeholder: capacity 0, so nothing ever lands in it —
        // every unit of energy lives in a cell, and the pool below is the surface the world sees.
        super(StorageContent.BATTERY_BANK_CONTROLLER.get(), pos, state, 0, 0, 0);
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .defaultPreset(SidePreset.STORAGE)
                .autoEject(Channel.ENERGY, false)
                .build());
    }

    /**
     * The bank's pooled view — always the pool, formed or not (an unformed pool is an empty,
     * zero-capacity store). Core's side-config component captured {@code this::getEnergy} at
     * install time, so its gated {@code energyView} and every loader's capability resolve to this.
     */
    @Override
    public NeroEnergyStorage getEnergy() {
        return this.pool;
    }

    public PooledEnergyStorage pool() {
        return this.pool;
    }

    public BankMode mode() {
        return this.mode;
    }

    /** Whether the bank has at least one member cell. */
    public boolean formed() {
        return this.pool.formed();
    }

    /** Advance to the next {@link BankMode} (server side; player-driven, so the eager sync is fine). */
    public BankMode cycleMode() {
        this.mode = this.mode.next();
        setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            BlockState state = getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
        return this.mode;
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        if ((level.getGameTime() + this.scanPhase) % SCAN_INTERVAL == 0 || anyMemberGone()) {
            rescan(level, pos);
        }
        boolean charged = this.pool.getAmount() > 0;
        setActive(charged);
        if (!this.pool.formed()) {
            reportStatus(MachineStatus.IDLE);
        } else if (!charged) {
            reportStatus(MachineStatus.NO_ENERGY);
        }
        int threshold = this.mode == BankMode.PRIORITY_SOURCE ? StorageConfig.bankPriorityThresholdPermille() : -1;
        StorageEnergy.pushToNeighbours(level, pos, this.pool, this.pool.effectiveIo(), sideConfig(),
                this.memberPositions::contains, threshold);
    }

    /** A member broken since the last scan (its BE is removed) must leave the pool before it is used. */
    private boolean anyMemberGone() {
        for (BatteryCellBlockEntity cell : this.pool.members()) {
            if (cell.isRemoved()) {
                return true;
            }
        }
        return false;
    }

    private void rescan(Level level, BlockPos pos) {
        List<BatteryCellBlockEntity> cells = BankNetwork.scan(level, pos, StorageConfig.bankScanRadius());
        this.pool.setMembers(cells);
        Set<BlockPos> positions = new HashSet<>(cells.size() * 2);
        for (BatteryCellBlockEntity cell : cells) {
            positions.add(cell.getBlockPos().immutable());
        }
        this.memberPositions = positions;
    }

    /** Shedding a buffer would achieve nothing — it has no work rate to throttle. */
    @Override
    public boolean shedable() {
        return false;
    }

    // --- menu sync: [0] members, [1|2] amount hi|lo, [3|4] capacity hi|lo, [5] mode, [6] effective I/O
    // Pooled totals can exceed an int (an Elite 9×9×9 bank), so they ride as two ints each.

    @Override
    protected int extraDataCount() {
        return EXTRA_DATA;
    }

    @Override
    protected int extraData(int index) {
        return switch (index) {
            case 0 -> this.pool.memberCount();
            case 1 -> (int) (this.pool.getAmount() >>> 32);
            case 2 -> (int) this.pool.getAmount();
            case 3 -> (int) (this.pool.getCapacity() >>> 32);
            case 4 -> (int) this.pool.getCapacity();
            case 5 -> this.mode.ordinal();
            case 6 -> (int) Math.min(Integer.MAX_VALUE, this.pool.effectiveIo());
            default -> 0;
        };
    }

    // --- persistence -----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("BankMode", this.mode.ordinal());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.mode = BankMode.byOrdinal(input.getIntOr("BankMode", BankMode.BUFFER.ordinal()));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.battery_bank_controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BankControllerMenu(containerId, playerInventory, this, this.data);
    }
}
