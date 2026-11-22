package za.co.neroland.neropower.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.nerotech.api.PowerMachine;
import za.co.neroland.nerotech.machine.MachineStatus;
import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;
import za.co.neroland.neropower.registry.content.GridContent;

/**
 * Power Monitor (0.4.0): a wall display for the block it is
 * mounted on (behind its face) — a NeroPower bank controller, Surge Bank, Momentum Wheel, Storage Ring or
 * any {@link PowerMachine}. Every 20 ticks it samples that block's stored energy and the change since the
 * last sample (split into "in" for a rise and "out" for a fall: a foreign store reports no separate flows)
 * into a 60-sample {@link MonitorRing}. The ring is memory only, never saved, and rides the client sync
 * tag only while a player is within 16 blocks. It draws no power and makes nothing. Comparator = fill.
 * Machine numbers only: no player data, and the source is named by its block id, never its position.
 */
public class PowerMonitorBlockEntity extends NeroPowerMachineBlockEntity {

    public static final int SAMPLE_TICKS = 20;
    public static final double SYNC_RANGE = 16.0;

    /** Menu-synced ints: [0] readout, [1] stallCause, [2] has source. */
    public static final int EXTRA_DATA = 3;

    private final MonitorRing ring = new MonitorRing();
    private long stored;
    private long capacity;
    private long lastStored = -1L;
    private String sourceId = "";
    private boolean hasSource;
    private int readout;
    private int stallCause;
    private int sampleSerial;
    private int syncedSerial = -1;
    private int comparator;
    private boolean nearbyPlayer;

    private final int phase = Math.floorMod(System.identityHashCode(this), SAMPLE_TICKS);

    public PowerMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(GridContent.POWER_MONITOR_BE.get(), pos, state, 0);
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .defaultPreset(SidePreset.ALL_DISABLED)
                .build());
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        if ((level.getGameTime() + this.phase) % SAMPLE_TICKS != 0) {
            return;
        }
        Direction front = state.hasProperty(NeroTechMachineBlock.FACING)
                ? state.getValue(NeroTechMachineBlock.FACING) : Direction.NORTH;
        BlockPos behind = pos.relative(front.getOpposite());
        NeroEnergyStorage source = null;
        String id = "";
        if (level.hasChunkAt(behind)) {
            BlockEntity be = level.getBlockEntity(behind);
            if (be instanceof PowerMachine machine) {
                source = machine.getEnergy();
                id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(behind).getBlock()).toString();
            }
        }
        if (source == null) {
            this.hasSource = false;
            this.sourceId = "";
            this.stored = 0L;
            this.capacity = 0L;
            this.lastStored = -1L;
            setReadout(GridReadout.STALL, GridReadout.CAUSE_NO_LINK, MachineStatus.STARVED);
        } else {
            if (!id.equals(this.sourceId)) {
                this.lastStored = -1L; // a different source: no delta across the swap
                this.ring.fromArray(null);
            }
            this.hasSource = true;
            this.sourceId = id;
            this.stored = Math.max(0L, source.getAmount());
            this.capacity = Math.max(0L, source.getCapacity());
            long[] flow = this.lastStored < 0L ? new long[] {0L, 0L}
                    : MonitorRing.flowFromDelta(this.lastStored, this.stored, SAMPLE_TICKS);
            this.lastStored = this.stored;
            int permille = this.capacity <= 0L ? 0 : (int) Math.min(1000L, this.stored * 1000L / this.capacity);
            this.ring.push(flow[0], flow[1], permille);
            if (flow[0] > 0L || flow[1] > 0L) {
                setReadout(GridReadout.WORK, GridReadout.CAUSE_NONE, MachineStatus.RUNNING);
            } else {
                setReadout(GridReadout.REST, GridReadout.CAUSE_NONE, MachineStatus.IDLE);
            }
        }
        this.sampleSerial++;
        this.nearbyPlayer = playerNear(level, pos);
        int signal = MonitorRing.comparator(this.stored, this.capacity);
        if (signal != this.comparator) {
            this.comparator = signal;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    /** Whether any player stands within {@link #SYNC_RANGE} (display only, never an authorisation). */
    private static boolean playerNear(Level level, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        for (Player player : level.players()) {
            if (player.distanceToSqr(cx, cy, cz) <= SYNC_RANGE * SYNC_RANGE) {
                return true;
            }
        }
        return false;
    }

    private void setReadout(int code, int cause, MachineStatus status) {
        this.readout = GridReadout.clampReadout(code);
        this.stallCause = GridReadout.clampCause(cause);
        if (code != GridReadout.WORK) {
            reportStatus(status);
        }
    }

    public MonitorRing ring() {
        return this.ring;
    }

    public long stored() {
        return this.stored;
    }

    public long capacity() {
        return this.capacity;
    }

    /** The source block's registry id ({@code neropower:smes_controller}), empty with none. */
    public String sourceId() {
        return this.sourceId;
    }

    public boolean hasSource() {
        return this.hasSource;
    }

    public int readoutCode() {
        return this.readout;
    }

    public int stallCause() {
        return this.stallCause;
    }

    public int comparatorSignal() {
        return MonitorRing.comparator(this.stored, this.capacity);
    }

    @Override
    public boolean shedable() {
        return false;
    }

    // --- sync: one compact packet a sample, only while someone is near ---------------------------

    @Override
    protected boolean renderSyncDirty() {
        if (this.sampleSerial != this.syncedSerial && this.nearbyPlayer) {
            this.syncedSerial = this.sampleSerial;
            return true;
        }
        return false;
    }

    @Override
    protected void writeRenderExtras(CompoundTag tag) {
        tag.putInt("Readout", this.readout);
        tag.putInt("StallCause", this.stallCause);
        tag.putBoolean("HasSource", this.hasSource);
        tag.putString("Source", this.sourceId);
        tag.putLong("Stored", this.stored);
        tag.putLong("Capacity", this.capacity);
        if (this.nearbyPlayer) {
            tag.putString("Ring", MonitorRing.encode(this.ring.toArray()));
        }
    }

    @Override
    protected void readRenderExtras(ValueInput input) {
        this.readout = input.getIntOr("Readout", GridReadout.REST);
        this.stallCause = input.getIntOr("StallCause", 0);
        this.hasSource = input.getBooleanOr("HasSource", false);
        this.sourceId = input.getStringOr("Source", "");
        this.stored = input.getLongOr("Stored", 0L);
        this.capacity = input.getLongOr("Capacity", 0L);
        this.ring.fromArray(MonitorRing.decode(input.getStringOr("Ring", "")));
    }

    @Override
    protected int extraDataCount() {
        return EXTRA_DATA;
    }

    @Override
    protected int extraData(int index) {
        return switch (index) {
            case 0 -> this.readout;
            case 1 -> this.stallCause;
            case 2 -> this.hasSource ? 1 : 0;
            default -> 0;
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.power_monitor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PowerMonitorMenu(containerId, playerInventory, this, this.data);
    }
}
