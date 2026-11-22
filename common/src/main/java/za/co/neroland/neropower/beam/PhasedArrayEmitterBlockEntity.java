package za.co.neroland.neropower.beam;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.nerotech.machine.MachineStatus;

import za.co.neroland.neropower.data.SaveAdapters;
import za.co.neroland.neropower.grid.GridReadout;
import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.registry.content.GridContent;

/**
 * Phased Array Emitter (0.4.0): the superconductor-tier source. It takes NE in on
 * every face and, every {@code beamCheckIntervalTicks}, beams {@code phasedArrayTransferPerPass} to <b>one</b>
 * of up to {@code phasedArrayMaxTargets} receivers, round-robin in list order ({@link PhasedArrayMath}),
 * over {@code phasedArrayRange} blocks at {@code phasedArrayLossPermillePerBlock} — the transmitter's
 * line-of-sight and entity rules ({@link BeamShot}). Same dimension only.
 *
 * <p><b>No owner is recorded</b> for any target: the Configurator's link-time check is the authorisation.
 * Target coordinates are saved to disk only; the client sync tag carries the bucketed
 * {@code targetIndex}, a 1/16-step direction and {@code blocked} for the BER, never a position.
 */
public class PhasedArrayEmitterBlockEntity extends BeamEndpointBlockEntity implements MultiBeamSource {

    public static final int ROWS = 8;
    public static final int BUFFER = 64_000;

    /**
     * Menu-synced ints after the seven shared ones: [0] readout, [1] stallCause, [2] max targets, [3] target
     * count, [4] serving index + 1 (0 none), [5|6] last pass NE, [7|8] transfer per pass, then per row i:
     * [9+3i] distance, [10+3i] loss permille, [11+3i] flags (bit 0 blocked, bit 1 out of range).
     */
    public static final int EXTRA_DATA = 9 + 3 * ROWS;

    private final BeamLinkList targets = new BeamLinkList();
    private final int[] rowDistance = new int[ROWS];
    private final int[] rowLoss = new int[ROWS];
    private final int[] rowFlags = new int[ROWS];
    private int cursor;
    private int serving = -1;
    private long passNe;
    private int readout;
    private int stallCause;

    // bucketed render inputs (server computes, client reads from the update tag)
    private int targetIndex;
    private int dirX;
    private int dirY;
    private int dirZ;
    private boolean blocked;
    private int syncedKey = Integer.MIN_VALUE;

    private final int phase = Math.floorMod(System.identityHashCode(this), 40);

    public PhasedArrayEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(GridContent.PHASED_ARRAY_EMITTER_BE.get(), pos, state, 0, BUFFER, BUFFER);
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .defaultPreset(SidePreset.ALL_INPUT)
                .build());
    }

    /** A source only: nothing may be aimed at it. */
    @Override
    public boolean acceptsBeam() {
        return false;
    }

    @Override
    public boolean linked() {
        return !this.targets.isEmpty();
    }

    @Override
    public boolean addLink(BeamTarget target) {
        if (target.pos().equals(this.worldPosition)) {
            return false;
        }
        boolean added = this.targets.add(target, BeamConfig.phasedArrayMaxTargets());
        if (added) {
            setChanged();
        }
        return added;
    }

    @Override
    public int linkRange() {
        return BeamConfig.phasedArrayRange();
    }

    /** Move row {@code index} earlier ({@code up}) or later in the serving order. */
    public void move(int index, boolean up) {
        if (this.targets.move(index, up)) {
            setChanged();
        }
    }

    public void unlink(int index) {
        if (this.targets.remove(index)) {
            this.cursor = 0;
            setChanged();
        }
    }

    public BeamLinkList targets() {
        return this.targets;
    }

    // --- ticking --------------------------------------------------------------------------------

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        this.targets.trim(BeamConfig.phasedArrayMaxTargets());
        if ((level.getGameTime() + this.phase) % BeamConfig.beamCheckIntervalTicks() == 0) {
            pass(level, pos);
        }
        boolean allBlocked = !this.targets.isEmpty();
        for (int i = 0; i < Math.min(ROWS, this.targets.size()); i++) {
            allBlocked &= (this.rowFlags[i] & 3) != 0;
        }
        if (this.targets.isEmpty()) {
            setReadout(GridReadout.STALL, GridReadout.CAUSE_NO_LINK, MachineStatus.BLOCKED);
        } else if (allBlocked) {
            setReadout(GridReadout.STALL, GridReadout.CAUSE_BEAM_BLOCKED, MachineStatus.BLOCKED);
        } else if (this.passNe > 0L) {
            setReadout(GridReadout.WORK, GridReadout.CAUSE_NONE, MachineStatus.RUNNING);
        } else if (energyBuffer().getAmount() <= 0L) {
            setReadout(GridReadout.STALL, GridReadout.CAUSE_NO_POWER, MachineStatus.NO_ENERGY);
        } else {
            setReadout(GridReadout.REST, GridReadout.CAUSE_NONE, MachineStatus.IDLE);
        }
        this.status = this.passNe > 0L ? BeamStatus.TRANSMITTING
                : this.targets.isEmpty() ? BeamStatus.UNLINKED : BeamStatus.IDLE;
        setActive(this.passNe > 0L);
    }

    private void pass(Level level, BlockPos pos) {
        int count = this.targets.size();
        int range = BeamConfig.phasedArrayRange();
        int loss = BeamConfig.phasedArrayLossPermillePerBlock();
        long budget = BeamMath.budget(BeamConfig.phasedArrayTransferPerPass(), energyBuffer().getAmount());
        long[] delivered = new long[1];
        boolean[] dead = new boolean[count];
        int served = PhasedArrayMath.serveNext(this.cursor, count, i -> {
            Set<BlockPos> visited = new HashSet<>();
            visited.add(pos.immutable());
            BeamShot.Result result = BeamShot.fire(level, pos, this.targets.get(i).target(), energyBuffer(), budget,
                    range, loss, visited, 0);
            record(i, result);
            dead[i] = result.dead();
            delivered[0] = result.accepted();
            return result.delivered();
        });
        // Rows not tried this pass still show a fresh distance / loss / sight reading.
        for (int i = 0; i < count; i++) {
            if (served >= 0 && i != served && !dead[i] && Math.floorMod(i - this.cursor, count)
                    > Math.floorMod(served - this.cursor, count)) {
                record(i, BeamShot.survey(level, pos, this.targets.get(i).target(), range, loss));
            }
        }
        this.serving = served;
        this.passNe = served >= 0 ? delivered[0] : 0L;
        this.lastPass = this.passNe;
        this.cursor = PhasedArrayMath.nextCursor(this.cursor, served, count);
        updateDirection(served >= 0 ? served : Math.floorMod(this.cursor, Math.max(1, count)));
        for (int i = count - 1; i >= 0; i--) {
            if (dead[i]) {
                this.targets.remove(i);
                setChanged();
            }
        }
        if (this.targets.size() != count) {
            this.cursor = 0;
            this.serving = -1;
        }
        for (int i = this.targets.size(); i < ROWS; i++) {
            this.rowDistance[i] = 0;
            this.rowLoss[i] = 0;
            this.rowFlags[i] = 0;
        }
    }

    private void record(int i, BeamShot.Result result) {
        if (i >= ROWS) {
            return;
        }
        this.rowDistance[i] = result.distance();
        this.rowLoss[i] = result.lossPermille();
        this.rowFlags[i] = (result.blocked() ? 1 : 0) | (result.outOfRange() ? 2 : 0);
    }

    private void updateDirection(int index) {
        if (index < 0 || index >= this.targets.size()) {
            this.dirX = 0;
            this.dirY = 0;
            this.dirZ = 0;
            this.blocked = false;
            this.targetIndex = 0;
            return;
        }
        BlockPos to = this.targets.get(index).target().pos();
        double dx = to.getX() - this.worldPosition.getX();
        double dy = to.getY() - this.worldPosition.getY();
        double dz = to.getZ() - this.worldPosition.getZ();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        this.dirX = PhasedArrayMath.directionStep(dx, length);
        this.dirY = PhasedArrayMath.directionStep(dy, length);
        this.dirZ = PhasedArrayMath.directionStep(dz, length);
        this.targetIndex = Math.min(7, index);
        this.blocked = index < ROWS && (this.rowFlags[index] & 1) != 0;
    }

    private void setReadout(int code, int cause, MachineStatus status) {
        this.readout = GridReadout.clampReadout(code);
        this.stallCause = GridReadout.clampCause(cause);
        if (code != GridReadout.WORK) {
            reportStatus(status);
        }
    }

    /** NE delivered by the last pass. */
    public long lastPassNe() {
        return this.passNe;
    }

    public int readoutCode() {
        return this.readout;
    }

    public int stallCause() {
        return this.stallCause;
    }

    // --- render surface (contract §2: targetIndex 0..7, direction in 1/16 steps, blocked) ----------

    public int targetIndex() {
        return this.targetIndex;
    }

    /** Direction to the aimed target, each component in -16..16 (1/16 steps). */
    public int directionX() {
        return this.dirX;
    }

    public int directionY() {
        return this.dirY;
    }

    public int directionZ() {
        return this.dirZ;
    }

    public boolean blocked() {
        return this.blocked;
    }

    @Override
    protected boolean renderSyncDirty() {
        boolean dirty = super.renderSyncDirty();
        int key = this.readout | (this.stallCause << 3) | (this.targetIndex << 7) | ((this.dirX + 16) << 10)
                | ((this.dirY + 16) << 16) | ((this.dirZ + 16) << 22) | ((this.blocked ? 1 : 0) << 28);
        if (key != this.syncedKey) {
            this.syncedKey = key;
            dirty = true;
        }
        return dirty;
    }

    @Override
    protected void writeRenderExtras(CompoundTag tag) {
        super.writeRenderExtras(tag);
        tag.putInt("Readout", this.readout);
        tag.putInt("StallCause", this.stallCause);
        tag.putInt("TargetIndex", this.targetIndex);
        tag.putInt("DirX", this.dirX);
        tag.putInt("DirY", this.dirY);
        tag.putInt("DirZ", this.dirZ);
        tag.putBoolean("Blocked", this.blocked);
    }

    @Override
    protected void readRenderExtras(ValueInput input) {
        super.readRenderExtras(input);
        this.readout = input.getIntOr("Readout", GridReadout.REST);
        this.stallCause = input.getIntOr("StallCause", 0);
        this.targetIndex = input.getIntOr("TargetIndex", 0);
        this.dirX = input.getIntOr("DirX", 0);
        this.dirY = input.getIntOr("DirY", 0);
        this.dirZ = input.getIntOr("DirZ", 0);
        this.blocked = input.getBooleanOr("Blocked", false);
    }

    /** The client sync tag never carries a target's coordinates or dimension. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        BeamSplitterBlockEntity.stripLinks(tag);
        return tag;
    }

    // --- persistence --------------------------------------------------------------------------

    //? if >=1.21.6 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
    //?} else {
    /*@Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ValueOutput output = ValueOutput.of(tag, registries);
    *///?}
        this.targets.write(SaveAdapters.writer(output));
    }

    //? if >=1.21.6 {
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
    //?} else {
    /*@Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ValueInput input = ValueInput.of(tag, registries);
    *///?}
        this.targets.read(SaveAdapters.reader(input), ROWS);
    }

    // --- menu sync ----------------------------------------------------------------------------

    @Override
    protected int extraDataCount() {
        return EXTRA_DATA;
    }

    @Override
    protected int extraData(int index) {
        if (index >= 9) {
            int row = (index - 9) / 3;
            if (row >= this.targets.size()) {
                return 0;
            }
            return switch ((index - 9) % 3) {
                case 0 -> this.rowDistance[row];
                case 1 -> this.rowLoss[row];
                default -> this.rowFlags[row];
            };
        }
        return switch (index) {
            case 0 -> this.readout;
            case 1 -> this.stallCause;
            case 2 -> BeamConfig.phasedArrayMaxTargets();
            case 3 -> this.targets.size();
            case 4 -> this.serving + 1;
            case 5 -> GaugeTail.hi2(this.passNe);
            case 6 -> GaugeTail.lo2(this.passNe);
            case 7 -> GaugeTail.hi2(BeamConfig.phasedArrayTransferPerPass());
            case 8 -> GaugeTail.lo2(BeamConfig.phasedArrayTransferPerPass());
            default -> 0;
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.phased_array_emitter");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PhasedArrayEmitterMenu(containerId, playerInventory, this, this.data);
    }
}
