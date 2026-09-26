package za.co.neroland.neropower.environmental;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import za.co.neroland.nerolandcore.upgrade.UpgradeModifiers;

import za.co.neroland.nerotech.config.NeroTechConfig;
import za.co.neroland.nerotech.machine.MachineEnergy;
import za.co.neroland.nerotech.machine.MachineStatus;

import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;

/**
 * Radioisotope Generator (RTG) — a fuel-free trickle of NE from the decay of one isotope pellet.
 * Inserting a pellet consumes it and records the game tick; from then on the output is
 * {@code rtgNePerTick × 2^(-elapsed / halfLife)} ({@link RtgMath}) until it falls below
 * {@code rtgCutoffPermille}, when the pellet is spent and a Spent Isotope Pellet drops into the output
 * slot. No heat, no running pollution, no failure ladder, no planet dependence: decay is the same
 * everywhere. (A spent pellet destroyed as a dropped item vents pollution — {@link SpentIsotopePelletItem}.)
 *
 * <p>Two machine slots: {@link #INPUT_SLOT} (accepts {@code neropower:isotope_pellet}) and
 * {@link #OUTPUT_SLOT} (spent pellets, extract-only). Energy leaves every face
 * ({@link SidePreset#GENERATOR}).
 */
public class RadioisotopeGeneratorBlockEntity extends NeroPowerMachineBlockEntity {

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;

    /** {@code level.getGameTime()} when the active pellet was inserted; {@code -1} when none is decaying. */
    private long placedTick = -1L;

    /** Last computed output permille (GUI readout; 0 without a pellet). */
    private int outputPermille;

    /** Client-visible output granularity: sync fires on BUCKET change over 1000‰, never per decay tick. */
    public static final int OUTPUT_SYNC_BUCKETS = 8;

    /** Last output bucket pushed to clients (the {@link #renderSyncDirty} compare-and-record state). */
    private int syncedOutputBucket;

    /** Cached {@link RtgMath#ticksToCutoff} for the config it was computed under. */
    private long cutoffTicks = -1L;
    private long cutoffHalfLife = -1L;
    private int cutoffPermille = -1;

    public RadioisotopeGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(EnvironmentalContent.RTG_TYPE.get(), pos, state, 2);
        // GENERATOR preset: energy OUT on every face, pellets IN on every face; spent pellets come out
        // of any face the player sets to output (the Side Config tab).
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .channel(Channel.ITEM, SlotGroup.of("input", INPUT_SLOT), SlotGroup.of("output", OUTPUT_SLOT))
                .defaultPreset(SidePreset.GENERATOR)
                .autoEject(Channel.ENERGY, true)
                .build());
    }

    /** Whether a pellet is currently decaying inside the generator. */
    public boolean fuelled() {
        return this.placedTick >= 0L;
    }

    /** The last computed output as permille of {@code rtgNePerTick} (0 without a pellet). */
    public int outputPermille() {
        return this.outputPermille;
    }

    /** Whole game days until the active pellet is spent (0 without a pellet). */
    public int daysRemaining() {
        return fuelled() && this.level != null
                ? RtgMath.days(cutoffTicks() - (this.level.getGameTime() - this.placedTick)) : 0;
    }

    /** A generator is never load-shed. */
    @Override
    public boolean shedable() {
        return false;
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        long now = level.getGameTime();

        if (!fuelled()) {
            ItemStack pellet = this.items.get(INPUT_SLOT);
            if (pellet.is(EnvironmentalContent.ISOTOPE_PELLET.get())) {
                pellet.shrink(1);
                this.placedTick = now;
                this.outputPermille = 1000;
                setChanged();
            } else {
                this.outputPermille = 0;
                reportStatus(MachineStatus.STARVED);
            }
        }

        if (fuelled()) {
            long halfLife = EnvironmentalConfig.rtgHalfLifeTicks();
            int permille = RtgMath.outputPermille(now - this.placedTick, halfLife);
            if (RtgMath.spent(permille, EnvironmentalConfig.rtgCutoffPermille())) {
                // Spent: hand over the spent pellet (waits, dark, while the output slot is full).
                if (ejectSpentPellet()) {
                    this.placedTick = -1L;
                    this.outputPermille = 0;
                    setChanged();
                } else {
                    this.outputPermille = 0;
                    reportStatus(MachineStatus.BLOCKED);
                }
            } else {
                this.outputPermille = permille;
                if (getEnergy().getAmount() < getEnergy().getCapacity()) {
                    UpgradeModifiers mods = modifiers();
                    int rate = (int) Math.round(EnvironmentalConfig.rtgNePerTick() * (permille / 1000.0D)
                            * mods.speedMultiplier() * presetSpeedFactor());
                    if (rate > 0) {
                        energyBuffer().generate(rate);
                    }
                } else {
                    reportStatus(MachineStatus.BLOCKED);
                }
            }
        }

        setActive(this.outputPermille > 0);
        MachineEnergy.pushToNeighbours(level, pos, energyBuffer(), NeroTechConfig.machineMaxTransfer(), sideConfig());
    }

    /** Put one spent pellet in the output slot; false when the slot cannot take it. */
    private boolean ejectSpentPellet() {
        ItemStack out = this.items.get(OUTPUT_SLOT);
        if (out.isEmpty()) {
            this.items.set(OUTPUT_SLOT, new ItemStack(EnvironmentalContent.SPENT_ISOTOPE_PELLET.get()));
            return true;
        }
        if (out.is(EnvironmentalContent.SPENT_ISOTOPE_PELLET.get()) && out.getCount() < out.getMaxStackSize()) {
            out.grow(1);
            return true;
        }
        return false;
    }

    @Override
    public boolean canPlaceMachineItem(int slot, ItemStack stack) {
        return slot == INPUT_SLOT && stack.is(EnvironmentalContent.ISOTOPE_PELLET.get());
    }

    @Override
    public boolean canTakeMachineItem(int slot) {
        return slot == OUTPUT_SLOT;
    }

    // --- menu sync: three extra ContainerData ints after the seven shared ones ----------------------

    /** [0] output permille, [1] whole days the pellet has decayed, [2] whole days until it is spent. */
    @Override
    protected int extraDataCount() {
        return 3;
    }

    @Override
    protected int extraData(int index) {
        return switch (index) {
            case 0 -> this.outputPermille;
            case 1 -> fuelled() && this.level != null
                    ? RtgMath.days(this.level.getGameTime() - this.placedTick) : 0;
            case 2 -> daysRemaining();
            default -> 0;
        };
    }

    /** Ticks from insertion to cutoff under the current config, recomputed only when the config moves. */
    private long cutoffTicks() {
        long halfLife = EnvironmentalConfig.rtgHalfLifeTicks();
        int cutoff = EnvironmentalConfig.rtgCutoffPermille();
        if (this.cutoffTicks < 0L || halfLife != this.cutoffHalfLife || cutoff != this.cutoffPermille) {
            this.cutoffHalfLife = halfLife;
            this.cutoffPermille = cutoff;
            this.cutoffTicks = RtgMath.ticksToCutoff(halfLife, cutoff);
        }
        return this.cutoffTicks;
    }

    // --- BER read surface (output permille rides the update tag; see renderSyncDirty) ------------

    /**
     * Output as a 0..1 fraction of {@code rtgNePerTick} — the BER fin-glow input. Client-side it is
     * the last synced value ({@code OutputPermille} rides {@code saveAdditional} / the update tag).
     */
    public float outputFraction() {
        return Math.min(1.0F, Math.max(0.0F, this.outputPermille / 1000.0F));
    }

    /**
     * NeroTech's render-sync hook: dirty when the output BUCKET ({@value #OUTPUT_SYNC_BUCKETS} over
     * 1000‰) moved — the heat-bucket sync discipline applied to the decay curve.
     */
    @Override
    protected boolean renderSyncDirty() {
        int bucket = Math.min(OUTPUT_SYNC_BUCKETS - 1, Math.max(0, this.outputPermille) * OUTPUT_SYNC_BUCKETS / 1000);
        if (bucket != this.syncedOutputBucket) {
            this.syncedOutputBucket = bucket;
            return true;
        }
        return false;
    }

    // --- persistence -----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("PlacedTick", this.placedTick);
        output.putInt("OutputPermille", this.outputPermille);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.placedTick = input.getLongOr("PlacedTick", -1L);
        this.outputPermille = input.getIntOr("OutputPermille", 0);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.radioisotope_generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new RtgMenu(containerId, playerInventory, this, this.data);
    }
}
