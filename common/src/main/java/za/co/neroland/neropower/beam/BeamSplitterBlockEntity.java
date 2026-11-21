package za.co.neroland.neropower.beam;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.nerotech.machine.MachineStatus;

import za.co.neroland.neropower.data.SaveAdapters;
import za.co.neroland.neropower.grid.GridReadout;
import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.registry.content.GridContent;

/**
 * Beam Splitter (0.4.0, decision 008 annex). A receiver for one incoming beam and a source for up to
 * {@code beamSplitterMaxLinks} outgoing links, each with a share (permille) of its buffer every pass.
 *
 * <ul>
 *   <li><b>Lens:</b> a {@code #nerotech:focus_lenses} item in slot 0 is required. Without one the
 *       splitter accepts no beam (its energy view refuses inserts, so the sender reads "receivers full");
 *       with one, the lens takes its loss ({@code beamSplitterLensLoss}) from what arrived, rounded up.</li>
 *   <li><b>Splitting</b> ({@link SplitMath#allocate}): every {@code beamCheckIntervalTicks} each link is
 *       offered {@code floor(buffer × share / 1000)} and the buffer pays {@link BeamMath#sourceCost} for
 *       what the target kept. Shares never add up to more than 1000, so no energy is created; the unshared
 *       part stays in the buffer.</li>
 *   <li><b>No owner is recorded</b> for any link: authorisation is checked once, when the Configurator
 *       links ({@link BeamLinking}); links are removed from the screen. Link coordinates are world data,
 *       saved to disk only and stripped from the client sync tag (the BER reads the bucketed
 *       {@code linkMask} / {@code shareBucket} / {@code lens} fields instead).</li>
 * </ul>
 */
public class BeamSplitterBlockEntity extends BeamEndpointBlockEntity implements MultiBeamSource {

    public static final int LENS_SLOT = 0;
    public static final int BUFFER = 16_000;
    /** Link rows the menu always carries (the hard ceiling of {@code beamSplitterMaxLinks}). */
    public static final int ROWS = 8;

    /**
     * Menu-synced ints after the seven shared ones: [0] readout, [1] stallCause, [2] max links, [3] link
     * count, [4] lens loss permille, [5] lens kind, [6] incoming flag, [7|8] last incoming pass (15-bit
     * pair), [9..12] configured loss of quartz / glacite / ember / void, then per row i (0..7):
     * [13+3i] distance, [14+3i] share permille, [15+3i] flags (bit 0 blocked, bit 1 out of range).
     */
    public static final int EXTRA_DATA = 13 + 3 * ROWS;

    public static final TagKey<Item> FOCUS_LENSES =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("nerotech", "focus_lenses"));

    private final BeamLinkList links = new BeamLinkList();
    private final int[] rowDistance = new int[ROWS];
    private final int[] rowFlags = new int[ROWS];
    private long lastIn;
    private long sentLastPass;
    private int readout;
    private int stallCause;

    /** Client copies of the bucketed render inputs (from the update tag). */
    private int clientLinkMask;
    private int clientShareBuckets;
    private boolean clientLens;

    private int syncedReadout = -1;
    private int syncedStall = -1;
    private int syncedMask = -1;
    private int syncedShares = -1;
    private int syncedLens = -1;

    private final int phase = Math.floorMod(System.identityHashCode(this), 40);

    /** The view Core's capability and the beam see: refuses every insert while no lens is fitted. */
    private final NeroEnergyStorage gated = new NeroEnergyStorage() {
        @Override
        public long getAmount() {
            return energyBuffer().getAmount();
        }

        @Override
        public long getCapacity() {
            return energyBuffer().getCapacity();
        }

        @Override
        public long insert(long maxAmount, boolean simulate) {
            return lens() == SplitMath.Lens.NONE ? 0L : energyBuffer().insert(maxAmount, simulate);
        }

        @Override
        public long extract(long maxAmount, boolean simulate) {
            return energyBuffer().extract(maxAmount, simulate);
        }
    };

    public BeamSplitterBlockEntity(BlockPos pos, BlockState state) {
        super(GridContent.BEAM_SPLITTER_BE.get(), pos, state, 1, BUFFER, BUFFER);
        // Beam in, beams out: faces are closed by default (open one in the Side Config tab to tap it).
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .defaultPreset(SidePreset.ALL_DISABLED)
                .build());
    }

    @Override
    public NeroEnergyStorage getEnergy() {
        return this.gated;
    }

    @Override
    public boolean acceptsBeam() {
        return true;
    }

    @Override
    public boolean linked() {
        return !this.links.isEmpty();
    }

    // --- lens ---------------------------------------------------------------------------------

    @Override
    public boolean canPlaceMachineItem(int slot, ItemStack stack) {
        return slot == LENS_SLOT && stack.is(FOCUS_LENSES);
    }

    /** The fitted lens (server and client: the slot rides the update tag). */
    public SplitMath.Lens lens() {
        ItemStack stack = getItem(LENS_SLOT);
        if (stack.isEmpty() || !stack.is(FOCUS_LENSES)) {
            return SplitMath.Lens.NONE;
        }
        return SplitMath.Lens.byItemPath(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
    }

    /** Loss of the fitted lens in permille (1000 with none). Any other lens in the tag counts as quartz. */
    public int lensLossPermille() {
        SplitMath.Lens lens = lens();
        int[] table = BeamConfig.beamSplitterLensLoss();
        if (lens == SplitMath.Lens.NONE && !getItem(LENS_SLOT).isEmpty() && getItem(LENS_SLOT).is(FOCUS_LENSES)) {
            return table[SplitMath.Lens.QUARTZ.ordinal()];
        }
        return table[lens.ordinal()];
    }

    private boolean hasLens() {
        ItemStack stack = getItem(LENS_SLOT);
        return !stack.isEmpty() && stack.is(FOCUS_LENSES);
    }

    // --- links (MultiBeamSource) ----------------------------------------------------------------

    @Override
    public boolean addLink(BeamTarget target) {
        if (target.pos().equals(this.worldPosition)) {
            return false;
        }
        boolean added = this.links.add(target, BeamConfig.beamSplitterMaxLinks());
        if (added) {
            setChanged();
        }
        return added;
    }

    @Override
    public int linkRange() {
        return BeamConfig.beamRange();
    }

    /** Set row {@code index}'s share (capped so the shares stay at or under 100 %). */
    public void setShare(int index, int permille) {
        if (this.links.setShare(index, permille) >= 0) {
            setChanged();
        }
    }

    /** Drop row {@code index}; a stale index does nothing. */
    public void unlink(int index) {
        if (this.links.remove(index)) {
            setChanged();
        }
    }

    public BeamLinkList links() {
        return this.links;
    }

    // --- beam in --------------------------------------------------------------------------------

    @Override
    public void onBeamReceived(long accepted, Set<BlockPos> visited, int hops) {
        super.onBeamReceived(accepted, visited, hops);
        if (accepted <= 0L) {
            return;
        }
        long loss = SplitMath.lensLoss(accepted, lensLossPermille());
        if (loss > 0L) {
            energyBuffer().consume((int) Math.min(Integer.MAX_VALUE, loss));
        }
        this.lastIn = accepted;
        setChanged();
    }

    // --- ticking --------------------------------------------------------------------------------

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        this.links.trim(BeamConfig.beamSplitterMaxLinks());
        long now = level.getGameTime();
        if ((now + this.phase) % BeamConfig.beamCheckIntervalTicks() == 0) {
            split(level, pos);
        }
        boolean receiving = recentlyReceived(now);
        if (!receiving) {
            this.lastIn = 0L;
        }
        boolean allBlocked = !this.links.isEmpty();
        for (int i = 0; i < this.links.size(); i++) {
            allBlocked &= (this.rowFlags[i] & 1) != 0;
        }
        if (!hasLens()) {
            setReadout(GridReadout.STALL, GridReadout.CAUSE_NO_LENS, MachineStatus.STARVED);
        } else if (this.links.isEmpty()) {
            setReadout(GridReadout.STALL, GridReadout.CAUSE_NO_LINK, MachineStatus.BLOCKED);
        } else if (allBlocked) {
            setReadout(GridReadout.STALL, GridReadout.CAUSE_BEAM_BLOCKED, MachineStatus.BLOCKED);
        } else if (this.sentLastPass > 0L) {
            setReadout(GridReadout.WORK, GridReadout.CAUSE_NONE, MachineStatus.RUNNING);
        } else if (!receiving && energyBuffer().getAmount() <= 0L) {
            setReadout(GridReadout.STALL, GridReadout.CAUSE_NO_LINK, MachineStatus.STARVED);
        } else {
            setReadout(GridReadout.REST, GridReadout.CAUSE_NONE, MachineStatus.IDLE);
        }
        this.status = this.sentLastPass > 0L ? BeamStatus.TRANSMITTING : receiving ? BeamStatus.RECEIVING : BeamStatus.IDLE;
        setActive(this.sentLastPass > 0L);
    }

    /** One pass: offer each link its share of the buffer as it stands now. */
    private void split(Level level, BlockPos pos) {
        long available = energyBuffer().getAmount();
        long[] alloc = SplitMath.allocate(available, this.links.shares());
        long sent = 0L;
        for (int i = this.links.size() - 1; i >= 0; i--) {
            BeamTarget aim = this.links.get(i).target();
            BeamShot.Result result;
            if (alloc[i] > 0L) {
                Set<BlockPos> visited = new HashSet<>();
                visited.add(pos.immutable());
                result = BeamShot.fire(level, pos, aim, energyBuffer(), alloc[i], BeamConfig.beamRange(),
                        BeamConfig.beamLossPermillePerBlock(), visited, 0);
            } else {
                result = BeamShot.survey(level, pos, aim, BeamConfig.beamRange(), BeamConfig.beamLossPermillePerBlock());
            }
            if (result.dead()) {
                this.links.remove(i); // target broken or replaced: the link is dead
                setChanged();
                continue;
            }
            sent += result.accepted();
            if (i < ROWS) {
                this.rowDistance[i] = result.distance();
                this.rowFlags[i] = (result.blocked() ? 1 : 0) | (result.outOfRange() ? 2 : 0);
            }
        }
        for (int i = this.links.size(); i < ROWS; i++) {
            this.rowDistance[i] = 0;
            this.rowFlags[i] = 0;
        }
        this.sentLastPass = sent;
        this.lastPass = sent;
    }

    private void setReadout(int code, int cause, MachineStatus status) {
        this.readout = GridReadout.clampReadout(code);
        this.stallCause = GridReadout.clampCause(cause);
        if (code != GridReadout.WORK) {
            reportStatus(status);
        }
    }

    public int readoutCode() {
        return this.readout;
    }

    public int stallCause() {
        return this.stallCause;
    }

    /** NE that arrived in the last pass (0 once the beam stops). */
    public long lastIn() {
        return this.lastIn;
    }

    // --- bucketed render inputs (contract §2) -----------------------------------------------------

    private int serverLinkMask() {
        int mask = 0;
        for (int i = 0; i < Math.min(4, this.links.size()); i++) {
            if ((this.rowFlags[i] & 1) == 0) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    private int serverShareBuckets() {
        int packed = 0;
        for (int i = 0; i < Math.min(4, this.links.size()); i++) {
            packed |= SplitMath.shareBucket(this.links.get(i).share()) << (4 * i);
        }
        return packed;
    }

    /** Client: links 0..3 present and unblocked, one bit each. */
    public int linkMask() {
        return this.level != null && this.level.isClientSide() ? this.clientLinkMask : serverLinkMask();
    }

    /** Client: link {@code i}'s share bucket 0..10. */
    public int shareBucket(int i) {
        int packed = this.level != null && this.level.isClientSide() ? this.clientShareBuckets : serverShareBuckets();
        return i < 0 || i > 3 ? 0 : (packed >> (4 * i)) & 0xF;
    }

    /** Client: whether a lens is fitted. */
    public boolean lensFitted() {
        return this.level != null && this.level.isClientSide() ? this.clientLens : hasLens();
    }

    @Override
    protected boolean renderSyncDirty() {
        boolean dirty = super.renderSyncDirty();
        int mask = serverLinkMask();
        int shares = serverShareBuckets();
        int lens = hasLens() ? 1 : 0;
        if (this.readout != this.syncedReadout || this.stallCause != this.syncedStall || mask != this.syncedMask
                || shares != this.syncedShares || lens != this.syncedLens) {
            this.syncedReadout = this.readout;
            this.syncedStall = this.stallCause;
            this.syncedMask = mask;
            this.syncedShares = shares;
            this.syncedLens = lens;
            dirty = true;
        }
        return dirty;
    }

    @Override
    protected void writeRenderExtras(CompoundTag tag) {
        super.writeRenderExtras(tag);
        tag.putInt("Readout", this.readout);
        tag.putInt("StallCause", this.stallCause);
        tag.putInt("LinkMask", serverLinkMask());
        tag.putInt("ShareBuckets", serverShareBuckets());
        tag.putBoolean("Lens", hasLens());
    }

    @Override
    protected void readRenderExtras(ValueInput input) {
        super.readRenderExtras(input);
        this.readout = input.getIntOr("Readout", GridReadout.REST);
        this.stallCause = input.getIntOr("StallCause", 0);
        this.clientLinkMask = input.getIntOr("LinkMask", 0);
        this.clientShareBuckets = input.getIntOr("ShareBuckets", 0);
        this.clientLens = input.getBooleanOr("Lens", false);
    }

    /** The client sync tag never carries a link's coordinates or dimension (contract §2). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        stripLinks(tag);
        return tag;
    }

    /** Remove every {@link BeamLinkList} key (up to the hard ceiling of eight links). */
    static void stripLinks(CompoundTag tag) {
        tag.remove(BeamLinkList.COUNT);
        for (int i = 0; i < ROWS; i++) {
            for (String field : new String[] {"X", "Y", "Z", "Dim", "Share"}) {
                tag.remove(BeamLinkList.PREFIX + i + field);
            }
        }
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
        this.links.write(SaveAdapters.writer(output));
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
        this.links.read(SaveAdapters.reader(input), ROWS);
    }

    // --- menu sync ----------------------------------------------------------------------------

    @Override
    protected int extraDataCount() {
        return EXTRA_DATA;
    }

    @Override
    protected int extraData(int index) {
        if (index >= 13) {
            int row = (index - 13) / 3;
            int field = (index - 13) % 3;
            if (row >= this.links.size()) {
                return 0;
            }
            return switch (field) {
                case 0 -> this.rowDistance[row];
                case 1 -> this.links.get(row).share();
                default -> this.rowFlags[row];
            };
        }
        return switch (index) {
            case 0 -> this.readout;
            case 1 -> this.stallCause;
            case 2 -> BeamConfig.beamSplitterMaxLinks();
            case 3 -> this.links.size();
            case 4 -> hasLens() ? lensLossPermille() : 0;
            case 5 -> lens().ordinal();
            case 6 -> this.lastIn > 0L ? 1 : 0;
            case 7 -> GaugeTail.hi2(this.lastIn);
            case 8 -> GaugeTail.lo2(this.lastIn);
            case 9, 10, 11, 12 -> BeamConfig.beamSplitterLensLoss()[index - 8];
            default -> 0;
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.beam_splitter");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BeamSplitterMenu(containerId, playerInventory, this, this.data);
    }
}
