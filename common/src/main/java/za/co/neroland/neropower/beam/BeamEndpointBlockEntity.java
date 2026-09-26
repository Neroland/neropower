package za.co.neroland.neropower.beam;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerotech.machine.MachineStatus;

import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;

/**
 * Shared base for the four beam endpoints (transmitter, receiver, relay, orbital receiver): the
 * GUI readout state ({@link BeamStatus}, distance, loss, last pass amount) synced through five
 * extra {@code ContainerData} ints, the shared {@link BeamMenu}, and the "energy arrived by beam"
 * entry point a transmitter calls on its target. No slots, no heat of note, no failure ladder —
 * a beam endpoint is a buffer with a direction.
 *
 * <p>Synced layout after the seven shared indices: {@code [0]} linked flag, {@code [1]} distance
 * (blocks), {@code [2]} whole-beam loss (permille), {@code [3]} NE moved last pass (clamped to
 * int), {@code [4]} {@link BeamStatus} ordinal.
 */
public abstract class BeamEndpointBlockEntity extends NeroPowerMachineBlockEntity {

    /** Extra synced ints after the seven shared ones (see class javadoc). */
    public static final int EXTRA_DATA = 5;

    protected BeamStatus status = BeamStatus.IDLE;
    protected int distance;
    protected int lossPermille;
    protected long lastPass;

    /** Last status ordinal pushed to clients (the {@link #renderSyncDirty} compare-and-record state). */
    private int syncedStatus;

    protected BeamEndpointBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 0);
    }

    /** Explicit-buffer variant (the relay's small pass-through buffer). */
    protected BeamEndpointBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
            int energyCapacity, int maxTransfer) {
        super(type, pos, state, 0, energyCapacity, maxTransfer);
    }

    // --- beam surface ------------------------------------------------------------------------------

    /** Whether a transmitter may aim at this block (receivers and relays: yes; transmitters: no). */
    public abstract boolean acceptsBeam();

    /** Whether this endpoint is an orbital receiver — the only legal cross-dimension target. */
    public boolean orbital() {
        return false;
    }

    /** Whether this endpoint sends a beam of its own (transmitter / relay) — for the GUI's linked flag. */
    public boolean linked() {
        return false;
    }

    /**
     * Called by the sending endpoint after {@code accepted} NE landed in this block's buffer this
     * pass. {@code visited} holds every endpoint that has already <i>sent</i> during this pass and
     * {@code hops} is the chain depth so far — a relay uses both to forward without ever looping.
     */
    public void onBeamReceived(long accepted, Set<BlockPos> visited, int hops) {
        if (accepted > 0L) {
            this.lastPass = accepted;
            this.status = BeamStatus.RECEIVING;
            this.lastReceiveTick = this.level == null ? 0L : this.level.getGameTime();
        }
    }

    /** Game time of the last beam delivery — lets a receiver fall back to IDLE once the beam stops. */
    protected long lastReceiveTick = Long.MIN_VALUE;

    /** Whether a delivery landed within the last two passes. */
    protected boolean recentlyReceived(long now) {
        return now - this.lastReceiveTick <= 2L * BeamConfig.beamCheckIntervalTicks();
    }

    /** The current beam status (server side). */
    public BeamStatus beamStatus() {
        return this.status;
    }

    /** Distance to the target in blocks at the last pass (0 when unlinked / orbital). */
    public int distance() {
        return this.distance;
    }

    /** Whole-beam loss at the last pass (permille). */
    public int lossPermille() {
        return this.lossPermille;
    }

    /** Record a status and mirror it onto NeroTech's analytics line. */
    protected void setStatus(BeamStatus newStatus) {
        this.status = newStatus;
        reportStatus(switch (newStatus) {
            case TRANSMITTING, RECEIVING -> MachineStatus.RUNNING;
            case BLOCKED, OUT_OF_RANGE, TARGET_FULL, LOOP, DENIED -> MachineStatus.BLOCKED;
            case TARGET_MISSING, TARGET_ASLEEP, UNLINKED -> MachineStatus.STARVED;
            default -> MachineStatus.IDLE;
        });
    }

    /** A beam endpoint has no work rate for a Grid Controller to throttle. */
    @Override
    public boolean shedable() {
        return false;
    }

    // --- BER read surface (status rides the update tag; see renderSyncDirty) -----------------------

    /**
     * The status as the BER sees it: {@code BeamStatus} rides {@code saveAdditional} and therefore the
     * update tag, and {@link #renderSyncDirty} pushes a packet only when it changes (the receiver
     * uses it to spin its dish; a transmitter / relay also gates the beam ray on TRANSMITTING).
     */
    public BeamStatus renderStatus() {
        return this.status;
    }

    /** NeroTech's render-sync hook: dirty when the status changed since the last push (never per tick). */
    @Override
    protected boolean renderSyncDirty() {
        int ordinal = this.status.ordinal();
        if (ordinal != this.syncedStatus) {
            this.syncedStatus = ordinal;
            return true;
        }
        return false;
    }

    // --- persistence: the status joins the update tag (NeroTech's base saves Active the same way) ---

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("BeamStatus", this.status.ordinal());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.status = BeamStatus.byOrdinal(input.getIntOr("BeamStatus", 0));
    }

    // --- menu sync ---------------------------------------------------------------------------------

    @Override
    protected int extraDataCount() {
        return EXTRA_DATA;
    }

    @Override
    protected int extraData(int index) {
        return switch (index) {
            case 0 -> linked() ? 1 : 0;
            case 1 -> this.distance;
            case 2 -> this.lossPermille;
            case 3 -> (int) Math.min(Integer.MAX_VALUE, this.lastPass);
            case 4 -> this.status.ordinal();
            default -> 0;
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.beam");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BeamMenu(containerId, playerInventory, this, this.data);
    }
}
