package za.co.neroland.neropower.backend;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;

import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;
import za.co.neroland.nerolandcore.sideconfig.SlotGroup;

import za.co.neroland.nerotech.machine.MachineStatus;

import za.co.neroland.neropower.fission.FissionContent;
import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;
import za.co.neroland.neropower.radiation.RadiationConfig;
import za.co.neroland.neropower.registry.content.BackEndContent;

/**
 * Dry Cask — the long-term waste store: 27 slots of {@code #neropower:cask_storable} (spent fuel rods,
 * fission residue, vitrified waste). Waste costs space here instead of energy. A cask holding spent
 * rods or residue gives off trace heat (1 per {@code caskHeatIntervalTicks} for every
 * {@code caskSlotsPerHeat} such slots, shed passively by NeroTech's thermal model, faster beside heat
 * sinks or water) and is a {@link RadiationEmitter} whose strength the contamination code scales by the
 * faces not covered by {@code #neropower:radiation_barrier}. Vitrified waste is inert: no heat, no
 * radiation.
 *
 * <p>No energy buffer, no failure ladder. Every face is item I/O ({@link SidePreset#STORAGE}). Breaking
 * the cask drops its contents once, through NeroTech's container drop; the loot table drops only the
 * cask, so nothing is duplicated and nothing is voided.
 */
public class DryCaskBlockEntity extends NeroPowerMachineBlockEntity implements RadiationEmitter {

    /** Cached counts, refreshed on the heat interval (and on first tick). */
    private int occupied;
    private int radioactive;
    private int openFaces = 6;
    private boolean counted;

    private boolean emitterRegistered;

    /** Synced readout (0 rest / 3 hot, {@link BackEndReadout}); the cask never stalls. */
    private int readout;
    private int syncedReadout = -1;

    public DryCaskBlockEntity(BlockPos pos, BlockState state) {
        super(BackEndContent.DRY_CASK_TYPE.get(), pos, state, CaskMath.CASK_SLOTS, 0, 0);
        int[] all = new int[CaskMath.CASK_SLOTS];
        for (int i = 0; i < all.length; i++) {
            all[i] = i;
        }
        SlotGroup store = SlotGroup.of("store", all);
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ITEM, store, store)
                .defaultPreset(SidePreset.STORAGE)
                .build());
    }

    /** Whether {@code stack} is radioactive waste (a spent rod or fission residue). */
    public static boolean radioactive(ItemStack stack) {
        return stack.is(FissionContent.SPENT_FUEL_ROD.get()) || stack.is(BackEndContent.FISSION_RESIDUE.get());
    }

    /** A cask is never load-shed (it draws no power). */
    @Override
    public boolean shedable() {
        return false;
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        if (!this.emitterRegistered) {
            this.emitterRegistered = true;
            BackEndHooks.emitters().add(level, this);
        }
        int interval = BackEndConfig.caskHeatIntervalTicks();
        if (!this.counted || (level.getGameTime() + exchangePhase()) % interval == 0) {
            recount(level, pos);
            int trace = CaskMath.traceHeat(this.radioactive, BackEndConfig.caskSlotsPerHeat());
            if (trace > 0) {
                this.heat = Math.min(heatCapacity(), this.heat + trace);
                setChanged();
            }
        }
        this.readout = BackEndReadout.readout(false, overheated(), BackEndReadout.CAUSE_NONE, false);
        za.co.neroland.neropower.machine.NeroPowerMachineBlock.applyStatus(level, pos,
                za.co.neroland.neropower.machine.NeroPowerMachineBlock.VisualStatus.fromReadout(this.readout));
        reportStatus(MachineStatus.IDLE);
        setActive(false);
    }

    private void recount(Level level, BlockPos pos) {
        int used = 0;
        int hot = 0;
        for (int i = 0; i < CaskMath.CASK_SLOTS; i++) {
            ItemStack stack = this.items.get(i);
            if (!stack.isEmpty()) {
                used++;
                if (radioactive(stack)) {
                    hot++;
                }
            }
        }
        this.occupied = used;
        this.radioactive = hot;
        this.openFaces = Shielding.unshieldedFaces(level, pos);
        this.counted = true;
    }

    /** Slots holding spent rods or residue (last count). */
    public int radioactiveSlots() {
        return this.radioactive;
    }

    // --- RadiationEmitter ------------------------------------------------------------------------

    @Override
    public BlockPos emitterPos() {
        return this.worldPosition;
    }

    @Override
    public boolean emitting() {
        return CaskMath.emitting(this.radioactive);
    }

    @Override
    public int unshieldedFaces() {
        return this.openFaces;
    }

    @Override
    public void setRemoved() {
        if (this.emitterRegistered && this.level != null) {
            this.emitterRegistered = false;
            BackEndHooks.emitters().remove(this.level, this);
        }
        super.setRemoved();
    }

    // --- slots -----------------------------------------------------------------------------------

    @Override
    public boolean canPlaceMachineItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < CaskMath.CASK_SLOTS && stack.is(BackEndContent.CASK_STORABLE);
    }

    @Override
    public boolean canTakeMachineItem(int slot) {
        return slot >= 0 && slot < CaskMath.CASK_SLOTS;
    }

    // --- menu sync (spec: screens/dry_cask.md) -----------------------------------------------------

    /** Extra-data layout; read only through {@code DryCaskMenu}'s getters. */
    static final int D_OCCUPIED = 0;
    static final int D_SHIELDED = 1;
    static final int D_LEAKING = 2;
    static final int D_TRACE = 3;
    static final int D_INTERVAL = 4;
    static final int D_RADIATION_ON = 5;
    static final int D_READOUT = 6;
    static final int D_STALL = 7;
    static final int D_RADIOACTIVE = 8;
    static final int D_COUNT = 9;

    @Override
    protected int extraDataCount() {
        return D_COUNT;
    }

    @Override
    protected int extraData(int index) {
        return switch (index) {
            case D_OCCUPIED -> this.occupied;
            case D_SHIELDED -> 6 - this.openFaces;
            case D_LEAKING -> emitting() && this.openFaces > 0 ? 1 : 0;
            case D_TRACE -> CaskMath.traceHeat(this.radioactive, BackEndConfig.caskSlotsPerHeat());
            case D_INTERVAL -> BackEndConfig.caskHeatIntervalTicks();
            case D_RADIATION_ON -> this.level instanceof ServerLevel serverLevel
                    && RadiationConfig.enabled(serverLevel.getServer().isDedicatedServer()) ? 1 : 0;
            case D_READOUT -> this.readout;
            case D_STALL -> BackEndReadout.CAUSE_NONE;
            case D_RADIOACTIVE -> this.radioactive;
            default -> 0;
        };
    }

    // --- render surface (update tag only) ---------------------------------------------------------

    @Override
    protected boolean renderSyncDirty() {
        if (this.readout != this.syncedReadout) {
            this.syncedReadout = this.readout;
            return true;
        }
        return false;
    }

    @Override
    protected void writeRenderExtras(CompoundTag tag) {
        tag.putInt("NpReadout", this.readout);
        tag.putInt("NpStall", BackEndReadout.CAUSE_NONE);
    }

    @Override
    protected void readRenderExtras(ValueInput input) {
        this.readout = input.getIntOr("NpReadout", 0);
    }

    /** Synced readout (0 rest, 3 hot). */
    public int renderReadout() {
        return this.readout;
    }

    /** Synced stall cause (always 0: a cask has nothing to stall on). */
    public int renderStallCause() {
        return BackEndReadout.CAUSE_NONE;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.dry_cask");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new DryCaskMenu(containerId, playerInventory, this, this.data);
    }
}
