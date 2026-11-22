package za.co.neroland.neropower.backend;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;

import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;
import za.co.neroland.nerolandcore.sideconfig.SlotGroup;
import za.co.neroland.nerolandcore.upgrade.UpgradeModifiers;

import za.co.neroland.nerotech.config.NeroTechConfig;
import za.co.neroland.nerotech.machine.MachineStatus;

import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;
import za.co.neroland.neropower.registry.content.BackEndContent;

/**
 * Vitrifier — melts one {@code #neropower:vitrifiable} waste item (fission residue, a spent beta
 * capsule, a spent isotope pellet) with two {@code #minecraft:sand} into one inert Vitrified Waste,
 * for {@code vitrifierNePerTick} × {@code vitrifierTicksPerOp} NE (8 000 NE by default). It makes
 * nothing useful: no recipe takes Vitrified Waste. It is the one way waste leaves the world, and it is
 * paid for in energy.
 *
 * <p>Slots: {@link #WASTE_SLOT} and {@link #SAND_SLOT} in, {@link #OUTPUT_SLOT} out. Power in on every
 * face but the bottom ({@link SidePreset#PROCESSOR}).
 */
public class VitrifierBlockEntity extends NeroPowerMachineBlockEntity {

    public static final int WASTE_SLOT = 0;
    public static final int SAND_SLOT = 1;
    public static final int OUTPUT_SLOT = 2;

    /** {@code #minecraft:sand}, built here so the id is the same on every version. */
    public static final TagKey<Item> SAND =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("minecraft", "sand"));

    /** NE drawn last tick (the screen's "using" rate). */
    private int energyRate;

    /** Synced readout (0..5) and stall cause (0..15), see {@link BackEndReadout}. */
    private int readout;
    private int stallCause;
    private int syncedReadout = -1;

    public VitrifierBlockEntity(BlockPos pos, BlockState state) {
        super(BackEndContent.VITRIFIER_TYPE.get(), pos, state, 3);
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .channel(Channel.ITEM, SlotGroup.of("input", WASTE_SLOT, SAND_SLOT), SlotGroup.of("output", OUTPUT_SLOT))
                .defaultPreset(SidePreset.PROCESSOR)
                .build());
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        this.energyRate = 0;
        ItemStack waste = this.items.get(WASTE_SLOT);
        ItemStack sand = this.items.get(SAND_SLOT);
        ItemStack out = this.items.get(OUTPUT_SLOT);
        int wasteCount = waste.is(BackEndContent.VITRIFIABLE) ? waste.getCount() : 0;
        int sandCount = sand.is(SAND) ? sand.getCount() : 0;
        boolean outputFits = out.isEmpty() || out.is(BackEndContent.VITRIFIED_WASTE.get());
        int outCount = outputFits ? out.getCount() : Integer.MAX_VALUE;
        int outMax = out.isEmpty() ? 64 : out.getMaxStackSize();

        if (!CaskMath.canVitrify(wasteCount, sandCount, outCount, outMax)) {
            resetProgress();
            setActive(false);
            boolean full = outCount >= outMax;
            reportStatus(wasteCount >= 1 && sandCount >= CaskMath.SAND_PER_OP ? MachineStatus.BLOCKED : MachineStatus.STARVED);
            setReadout(BackEndReadout.vitrifierCause(wasteCount, sandCount, full, true), false);
            return;
        }

        UpgradeModifiers mods = modifiers();
        int ticks = (int) Math.max(1L, Math.round(BackEndConfig.vitrifierTicksPerOp()
                / Math.max(0.01D, mods.speedMultiplier() * presetSpeedFactor())));
        int cost = (int) Math.max(0L, Math.round(BackEndConfig.vitrifierNePerTick()
                * mods.energyMultiplier() * presetEnergyFactor()));
        this.maxProgress = ticks;

        if (overheated()) {
            setActive(false);
            reportStatus(MachineStatus.THROTTLED);
            setReadout(BackEndReadout.CAUSE_NONE, false);
            return;
        }
        if (!energyBuffer().has(cost)) {
            setActive(false);
            reportStatus(MachineStatus.NO_ENERGY);
            setReadout(BackEndReadout.CAUSE_NO_POWER, false);
            return;
        }
        energyBuffer().consume(cost);
        this.energyRate = cost;
        this.progress++;
        addHeat(NeroTechConfig.heatPerOperation());
        setActive(true);
        setReadout(BackEndReadout.CAUSE_NONE, true);
        if (this.progress >= ticks) {
            waste.shrink(1);
            sand.shrink(CaskMath.SAND_PER_OP);
            if (out.isEmpty()) {
                this.items.set(OUTPUT_SLOT, new ItemStack(BackEndContent.VITRIFIED_WASTE.get()));
            } else {
                out.grow(1);
            }
            this.progress = 0;
            pulseClient();
            if (level instanceof ServerLevel serverLevel) {
                BackEndHooks.vitrified().vitrified(serverLevel, pos, this);
            }
        }
        setChanged();
    }

    private void setReadout(int cause, boolean working) {
        this.stallCause = cause;
        this.readout = BackEndReadout.readout(false, overheated(), cause, working);
    }

    /** Synced readout (0..5, {@link BackEndReadout}). */
    public int renderReadout() {
        return this.readout;
    }

    /** Synced stall cause (0..15, {@link BackEndReadout}). */
    public int renderStallCause() {
        return this.stallCause;
    }

    @Override
    protected boolean renderSyncDirty() {
        int packed = this.readout * 16 + this.stallCause;
        if (packed != this.syncedReadout) {
            this.syncedReadout = packed;
            return true;
        }
        return false;
    }

    @Override
    protected void writeRenderExtras(CompoundTag tag) {
        tag.putInt("NpReadout", this.readout);
        tag.putInt("NpStall", this.stallCause);
    }

    @Override
    protected void readRenderExtras(ValueInput input) {
        this.readout = input.getIntOr("NpReadout", 0);
        this.stallCause = input.getIntOr("NpStall", 0);
    }

    private void resetProgress() {
        if (this.progress != 0 || this.maxProgress != 0) {
            this.progress = 0;
            this.maxProgress = 0;
            setChanged();
        }
    }

    /** NE drawn last tick (0 while idle). */
    public int energyRate() {
        return this.energyRate;
    }

    @Override
    public boolean canPlaceMachineItem(int slot, ItemStack stack) {
        return switch (slot) {
            case WASTE_SLOT -> stack.is(BackEndContent.VITRIFIABLE);
            case SAND_SLOT -> stack.is(SAND);
            default -> false;
        };
    }

    @Override
    public boolean canTakeMachineItem(int slot) {
        return slot == OUTPUT_SLOT;
    }

    // --- menu sync ----------------------------------------------------

    /** Extra-data layout; read only through {@code VitrifierMenu}'s getters. */
    static final int D_READOUT = 0;
    static final int D_STALL = 1;
    static final int D_NE_PER_TICK = 2;
    static final int D_TICKS_PER_OP = 3;
    static final int D_COUNT = 4;

    @Override
    protected int extraDataCount() {
        return D_COUNT;
    }

    @Override
    protected int extraData(int index) {
        return switch (index) {
            case D_READOUT -> this.readout;
            case D_STALL -> this.stallCause;
            case D_NE_PER_TICK -> Math.min(Short.MAX_VALUE, BackEndConfig.vitrifierNePerTick());
            case D_TICKS_PER_OP -> Math.min(Short.MAX_VALUE, BackEndConfig.vitrifierTicksPerOp());
            default -> 0;
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.vitrifier");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new VitrifierMenu(containerId, playerInventory, this, this.data);
    }
}
