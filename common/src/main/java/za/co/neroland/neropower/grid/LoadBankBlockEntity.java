package za.co.neroland.neropower.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.nerotech.machine.MachineStatus;

import za.co.neroland.neropower.environmental.EnvironmentalConfig;
import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;
import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.registry.content.GridContent;

/**
 * Load Bank (0.4.0, decision 008 annex; screens/load_bank.md): a dummy load. It burns up to its set
 * rate ({@code Rate}, 0..{@code loadBankMaxRate} NE/t) from its buffer and turns it into NeroTech heat —
 * {@link LoadBankMath#nePerHeat} NE per unit — and makes nothing else. The heat is ordinary machine heat
 * (NeroTech's thermal model), so a Stirling Generator or a coolant loop beside it draws it through the
 * existing {@code PowerMachine.extractHeat} seam; the divisor clamp guarantees a Stirling gets back less
 * than half. At NeroTech's throttle threshold it stops burning until it cools. Blockstate {@code heat}
 * 0..4 is the element glow. NBT: {@code Rate}, {@code HeatCarry}. No player data.
 */
public class LoadBankBlockEntity extends NeroPowerMachineBlockEntity {

    /** Menu-synced ints: [0] readout, [1] stallCause, [2|3] rate set, [4|5] drawn, [6|7] max rate, [8] heat/t × 10. */
    public static final int EXTRA_DATA = 9;

    private int rate;
    private long heatCarry;
    private long drawn;
    private int heatTenths;
    private int readout;
    private int stallCause;
    private int syncedKey = -1;

    public LoadBankBlockEntity(BlockPos pos, BlockState state) {
        super(GridContent.LOAD_BANK_BE.get(), pos, state, 0,
                (int) Math.min(Integer.MAX_VALUE, 2L * GridConfig.loadBankMaxRate()), GridConfig.loadBankMaxRate());
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .defaultPreset(SidePreset.ALL_INPUT)
                .build());
    }

    public int rate() {
        return this.rate;
    }

    public void setRate(long value) {
        this.rate = (int) Math.max(0L, Math.min(GridConfig.loadBankMaxRate(), value));
        setChanged();
    }

    /** NE burned per heat unit made, after the anti-free-energy clamp. */
    public static int nePerHeat() {
        return LoadBankMath.nePerHeat(GridConfig.loadBankNePerHeat(), EnvironmentalConfig.stirlingNePerHeatUnit(),
                EnvironmentalConfig.stirlingColdFaceBonusPermille());
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        if (this.rate > GridConfig.loadBankMaxRate()) {
            this.rate = GridConfig.loadBankMaxRate();
        }
        long stored = energyBuffer().getAmount();
        boolean hot = overheated();
        long burn = LoadBankMath.draw(this.rate, stored, hot);
        int divisor = nePerHeat();
        if (burn > 0L) {
            energyBuffer().consume((int) Math.min(Integer.MAX_VALUE, burn));
            long[] made = LoadBankMath.heat(burn, this.heatCarry, divisor);
            this.heatCarry = made[1];
            int amount = LoadBankMath.presetAdjusted(made[0], preset().heatPermille());
            if (amount > 0) {
                addHeat(amount);
            }
            setChanged();
        }
        this.drawn = burn;
        this.heatTenths = (int) Math.min(Integer.MAX_VALUE, burn * 10L / divisor);

        int step = LoadBankMath.heatStep(heat(), heatThrottleThreshold());
        if (this.rate == 0) {
            setReadout(GridReadout.REST, GridReadout.CAUSE_NONE, MachineStatus.IDLE);
        } else if (hot || step >= 3) {
            setReadout(GridReadout.HOT, GridReadout.CAUSE_NONE, MachineStatus.THROTTLED);
        } else if (burn <= 0L) {
            setReadout(GridReadout.STALL, GridReadout.CAUSE_NO_POWER, MachineStatus.NO_ENERGY);
        } else {
            setReadout(GridReadout.WORK, GridReadout.CAUSE_NONE, MachineStatus.RUNNING);
        }
        setActive(burn > 0L);
        if (state.hasProperty(LoadBankBlock.HEAT) && state.getValue(LoadBankBlock.HEAT) != step) {
            level.setBlock(pos, state.setValue(LoadBankBlock.HEAT, step), Block.UPDATE_CLIENTS);
        }
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

    @Override
    public boolean shedable() {
        return true;
    }

    @Override
    protected boolean renderSyncDirty() {
        int key = this.readout | (this.stallCause << 3);
        if (key != this.syncedKey) {
            this.syncedKey = key;
            return true;
        }
        return false;
    }

    @Override
    protected void writeRenderExtras(CompoundTag tag) {
        tag.putInt("Readout", this.readout);
        tag.putInt("StallCause", this.stallCause);
    }

    @Override
    protected void readRenderExtras(ValueInput input) {
        this.readout = input.getIntOr("Readout", GridReadout.REST);
        this.stallCause = input.getIntOr("StallCause", 0);
    }

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
        output.putInt("Rate", this.rate);
        output.putLong("HeatCarry", this.heatCarry);
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
        this.rate = Math.max(0, input.getIntOr("Rate", 0));
        this.heatCarry = Math.max(0L, input.getLongOr("HeatCarry", 0L));
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
            case 2 -> GaugeTail.hi2(this.rate);
            case 3 -> GaugeTail.lo2(this.rate);
            case 4 -> GaugeTail.hi2(this.drawn);
            case 5 -> GaugeTail.lo2(this.drawn);
            case 6 -> GaugeTail.hi2(GridConfig.loadBankMaxRate());
            case 7 -> GaugeTail.lo2(GridConfig.loadBankMaxRate());
            case 8 -> this.heatTenths;
            default -> 0;
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.load_bank");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new LoadBankMenu(containerId, playerInventory, this, this.data);
    }
}
