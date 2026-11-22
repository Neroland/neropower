package za.co.neroland.neropower.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;
import za.co.neroland.nerolandcore.sideconfig.SlotGroup;

import za.co.neroland.nerotech.config.NeroTechConfig;
import za.co.neroland.nerotech.machine.MachineEnergy;
import za.co.neroland.nerotech.machine.MachineStatus;

import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;
import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.registry.content.BackEndContent;
import za.co.neroland.neropower.registry.content.GenerationContent;

/**
 * Betavoltaic Cell: a tiny, very long-lived trickle from one Beta Capsule ({@link BetavoltaicCore}). The
 * capsule sits in slot 0 and turns into a Spent Beta Capsule there when its life is used up; nothing is
 * voided. A live capsule cannot be taken out (it is sealed in), so its clock cannot be reset by
 * re-inserting it; if the block is broken with a part-used capsule inside, the capsule drops as spent.
 * No heat, no failure ladder, no player data. NBT: {@code CapsuleTicks} (ticks of output given).
 */
public class BetavoltaicCellBlockEntity extends NeroPowerMachineBlockEntity {

    public static final int CAPSULE_SLOT = 0;

    /** Synced ints after NeroTech's seven ({@code P} = a hi/lo pair). */
    public static final int D_TICKS_P = 0;
    public static final int D_TICKS_MAX_P = 2;
    public static final int D_OUTPUT = 4;
    public static final int D_STATE = 5;
    public static final int D_READOUT = 6;
    public static final int D_STALL = 7;
    public static final int D_COUNT = 8;

    private final BetavoltaicCore core = new BetavoltaicCore();

    private int output;
    private int readout;
    private int stallCause;

    private int syncedReadout = -1;
    private int syncedStall = -1;

    public BetavoltaicCellBlockEntity(BlockPos pos, BlockState state) {
        super(GenerationContent.BETAVOLTAIC_CELL_TYPE.get(), pos, state, 1);
        // Energy out of every face; capsules in, spent capsules out, on any face set to do so.
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .channel(Channel.ITEM, SlotGroup.of("input", CAPSULE_SLOT), SlotGroup.of("output", CAPSULE_SLOT))
                .defaultPreset(SidePreset.GENERATOR)
                .autoEject(Channel.ENERGY, true)
                .build());
    }

    /** What slot 0 holds. */
    public int capsuleState() {
        ItemStack stack = this.items.get(CAPSULE_SLOT);
        if (stack.is(BackEndContent.BETA_CAPSULE.get())) {
            return BetavoltaicCore.STATE_LIVE;
        }
        return stack.is(BackEndContent.SPENT_BETA_CAPSULE.get()) ? BetavoltaicCore.STATE_SPENT : BetavoltaicCore.STATE_NONE;
    }

    /** A generator is never load-shed. */
    @Override
    public boolean shedable() {
        return false;
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        long room = Math.max(0L, getEnergy().getCapacity() - getEnergy().getAmount());
        BetavoltaicCore.Step step = this.core.tick(capsuleState(), room, GenerationConfig.betavoltaicNePerTick(),
                GenerationConfig.betavoltaicCapsuleTicks());
        if (step.generate() > 0) {
            energyBuffer().generate(step.generate());
        }
        if (step.spendCapsule()) {
            spendCapsule(level, pos);
        }
        if (step.working() && level.getGameTime() % 200L == 0L) {
            setChanged();
        }
        this.output = step.generate();
        this.stallCause = step.cause();
        this.readout = GenerationReadout.readout(step.working(), step.cause());
        za.co.neroland.neropower.machine.NeroPowerMachineBlock.applyStatus(level, pos,
                za.co.neroland.neropower.machine.NeroPowerMachineBlock.VisualStatus.fromReadout(this.readout));
        if (step.cause() == GenerationReadout.CAUSE_NO_FUEL) {
            reportStatus(MachineStatus.STARVED);
        } else if (step.cause() == GenerationReadout.CAUSE_BUFFER_FULL) {
            reportStatus(MachineStatus.BLOCKED);
        }
        setActive(step.working());
        MachineEnergy.pushToNeighbours(level, pos, energyBuffer(), NeroTechConfig.machineMaxTransfer(), sideConfig());
    }

    /** The live capsule becomes a spent one in place (a stray extra capsule in the stack is kept). */
    private void spendCapsule(Level level, BlockPos pos) {
        ItemStack stack = this.items.get(CAPSULE_SLOT);
        ItemStack spent = new ItemStack(BackEndContent.SPENT_BETA_CAPSULE.get());
        if (stack.getCount() <= 1) {
            this.items.set(CAPSULE_SLOT, spent);
        } else {
            // Only reachable if something inserted more than the slot's one: keep the rest live and
            // put the spent capsule in the world rather than void it.
            stack.shrink(1);
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, spent);
        }
        this.core.reset();
        setChanged();
    }

    /** Called when the block is removed: a part-used live capsule leaves as spent (its clock is not portable). */
    void sealOnRemoval() {
        if (capsuleState() == BetavoltaicCore.STATE_LIVE && this.core.used() > 0L
                && this.items.get(CAPSULE_SLOT).getCount() == 1) {
            this.items.set(CAPSULE_SLOT, new ItemStack(BackEndContent.SPENT_BETA_CAPSULE.get()));
            this.core.reset();
        }
    }

    //? if >=1.21.5 {
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        sealOnRemoval();
        super.preRemoveSideEffects(pos, state);
    }
    //?}

    @Override
    public boolean canPlaceMachineItem(int slot, ItemStack stack) {
        return slot == CAPSULE_SLOT && this.items.get(CAPSULE_SLOT).isEmpty()
                && stack.is(BackEndContent.BETA_CAPSULE.get());
    }

    @Override
    public boolean canTakeMachineItem(int slot) {
        return slot == CAPSULE_SLOT && this.items.get(CAPSULE_SLOT).is(BackEndContent.SPENT_BETA_CAPSULE.get());
    }

    /** A new stack in the slot starts the capsule clock again (never carried over to another capsule). */
    @Override
    public void setItem(int slot, ItemStack stack) {
        boolean changed = slot == CAPSULE_SLOT && !ItemStack.isSameItem(this.items.get(CAPSULE_SLOT), stack);
        super.setItem(slot, stack);
        if (changed) {
            this.core.reset();
        }
    }

    // --- read surface ---------------------------------------------------------------------------------

    public int renderReadout() {
        return this.readout;
    }

    public int renderStallCause() {
        return this.stallCause;
    }

    @Override
    protected boolean renderSyncDirty() {
        boolean dirty = this.readout != this.syncedReadout || this.stallCause != this.syncedStall;
        if (dirty) {
            this.syncedReadout = this.readout;
            this.syncedStall = this.stallCause;
        }
        return dirty;
    }

    @Override
    protected void writeRenderExtras(CompoundTag tag) {
        tag.putInt(GenerationReadout.KEY_READOUT, this.readout);
        tag.putInt(GenerationReadout.KEY_STALL, this.stallCause);
    }

    @Override
    protected void readRenderExtras(ValueInput input) {
        this.readout = input.getIntOr(GenerationReadout.KEY_READOUT, 0);
        this.stallCause = input.getIntOr(GenerationReadout.KEY_STALL, 0);
    }

    // --- menu sync ------------------------------------------------------------------------------------

    @Override
    protected int extraDataCount() {
        return D_COUNT;
    }

    @Override
    protected int extraData(int index) {
        int max = GenerationConfig.betavoltaicCapsuleTicks();
        long value = switch (index) {
            case D_TICKS_P, D_TICKS_P + 1 -> capsuleState() == BetavoltaicCore.STATE_LIVE ? this.core.left(max) : 0L;
            case D_TICKS_MAX_P, D_TICKS_MAX_P + 1 -> max;
            case D_OUTPUT -> this.output;
            case D_STATE -> capsuleState();
            case D_READOUT -> this.readout;
            case D_STALL -> this.stallCause;
            default -> 0L;
        };
        if (index >= D_TICKS_P && index < D_OUTPUT) {
            return index % 2 == 0 ? GaugeTail.hi2(value) : GaugeTail.lo2(value);
        }
        return (int) Math.max(0L, Math.min(Short.MAX_VALUE, value));
    }

    // --- persistence ----------------------------------------------------------------------------------

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
        output.putInt("CapsuleTicks", (int) Math.min(Integer.MAX_VALUE, this.core.used()));
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
        this.core.load(input.getIntOr("CapsuleTicks", 0));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.betavoltaic_cell");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BetavoltaicCellMenu(containerId, playerInventory, this, this.data);
    }
}
