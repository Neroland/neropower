package za.co.neroland.neropower.environmental;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;
import za.co.neroland.nerolandcore.upgrade.UpgradeModifiers;

import za.co.neroland.nerotech.api.PowerMachine;
import za.co.neroland.nerotech.config.NeroTechConfig;
import za.co.neroland.nerotech.machine.MachineEnergy;
import za.co.neroland.nerotech.machine.MachineStatus;
import za.co.neroland.nerotech.registry.ModBlocks;

import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;

/**
 * Stirling Generator — turns a temperature gradient into NE. Each tick it finds the hottest adjacent
 * {@link PowerMachine} (any NeroTech-family machine, NeroPower's reactors included), draws a share of
 * that machine's heat above its ambient level ({@link PowerMachine#extractHeat}, never below ambient)
 * and converts every unit drawn into {@code stirlingNePerHeatUnit} NE ({@link StirlingMath}). A cold
 * face — water, ice, snow or a NeroTech Radiator on any side, the same coolant set NeroTech's thermal
 * model recognises — raises the output; without one the usable gradient is halved.
 *
 * <p>It is therefore a passive coolant with a dividend: parked against a hot reactor it both cools it
 * and pays out. No slots, no fuel, no failure ladder, no planet dependence.
 */
public class StirlingGeneratorBlockEntity extends NeroPowerMachineBlockEntity {

    /** GUI readouts (server-side; synced through {@link #extraData}). */
    private int gradient;
    private int drawnLastTick;
    private boolean coldFace;

    /** Client-visible draw granularity: sync fires on BUCKET change over {@code stirlingMaxDrawPerOp}. */
    public static final int DRAW_SYNC_BUCKETS = 6;

    /** Last draw bucket pushed to clients (the {@link #renderSyncDirty} compare-and-record state). */
    private int syncedDrawBucket;

    public StirlingGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(EnvironmentalContent.STIRLING_TYPE.get(), pos, state, 0);
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .defaultPreset(SidePreset.GENERATOR)
                .autoEject(Channel.ENERGY, true)
                .build());
    }

    /** A generator is never load-shed. */
    @Override
    public boolean shedable() {
        return false;
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        PowerMachine hot = null;
        int hotGradient = 0;
        boolean cold = false;
        for (Direction side : Direction.values()) {
            BlockPos neighbourPos = pos.relative(side);
            if (level.getBlockEntity(neighbourPos) instanceof PowerMachine pm && pm != this) {
                int g = pm.heat() - pm.ambient();
                if (g > hotGradient) {
                    hotGradient = g;
                    hot = pm;
                }
                continue;
            }
            if (!cold && isColdFace(level.getBlockState(neighbourPos))) {
                cold = true;
            }
        }

        this.coldFace = cold;
        this.gradient = hotGradient;
        int drawn = 0;
        if (hot != null && hotGradient > 0) {
            int usable = StirlingMath.usableGradient(hotGradient, cold);
            int draw = StirlingMath.drawFor(usable, EnvironmentalConfig.stirlingDrawPermille(),
                    EnvironmentalConfig.stirlingMaxDrawPerOp());
            if (draw > 0 && getEnergy().getAmount() < getEnergy().getCapacity()) {
                drawn = hot.extractHeat(draw, hot.ambient());
                if (drawn > 0) {
                    UpgradeModifiers mods = modifiers();
                    int output = StirlingMath.outputFor(drawn, EnvironmentalConfig.stirlingNePerHeatUnit(),
                            cold ? EnvironmentalConfig.stirlingColdFaceBonusPermille() : 0);
                    output = (int) Math.round(output * mods.speedMultiplier() * presetSpeedFactor());
                    if (output > 0) {
                        energyBuffer().generate(output);
                    }
                }
            } else if (draw > 0) {
                reportStatus(MachineStatus.BLOCKED);
            }
        } else {
            reportStatus(MachineStatus.IDLE);
        }
        this.drawnLastTick = drawn;

        setActive(drawn > 0);
        MachineEnergy.pushToNeighbours(level, pos, energyBuffer(), NeroTechConfig.machineMaxTransfer(), sideConfig());
    }

    /**
     * The blocks that count as a cold sink — the same set NeroTech's thermal model scores as coolant
     * ({@code NeroTechMachineBlockEntity.rebuildThermalLinks}): natural water/ice/snow plus the Radiator.
     */
    static boolean isColdFace(@Nullable BlockState ns) {
        if (ns == null) {
            return false;
        }
        return ns.is(ModBlocks.RADIATOR.get())
                || ns.is(Blocks.WATER) || ns.is(Blocks.ICE) || ns.is(Blocks.PACKED_ICE)
                || ns.is(Blocks.BLUE_ICE) || ns.is(Blocks.SNOW_BLOCK) || ns.is(Blocks.POWDER_SNOW);
    }

    // --- BER read surface (drawn heat rides the update tag; see renderSyncDirty) --------------------

    /**
     * Heat drawn last tick as a 0..1 fraction of {@code stirlingMaxDrawPerOp} — the BER flywheel
     * speed input. Client-side it is the last synced value ({@code Drawn} rides
     * {@code saveAdditional} / the update tag).
     */
    public float drawnFraction() {
        int cap = EnvironmentalConfig.stirlingMaxDrawPerOp();
        return cap <= 0 ? 0.0F : Math.min(1.0F, Math.max(0, this.drawnLastTick) / (float) cap);
    }

    /**
     * NeroTech's render-sync hook: dirty when the draw BUCKET ({@value #DRAW_SYNC_BUCKETS} over
     * {@code stirlingMaxDrawPerOp}) moved — never per tick.
     */
    @Override
    protected boolean renderSyncDirty() {
        int cap = EnvironmentalConfig.stirlingMaxDrawPerOp();
        int bucket = cap <= 0 ? 0
                : Math.min(DRAW_SYNC_BUCKETS - 1, Math.max(0, this.drawnLastTick) * DRAW_SYNC_BUCKETS / cap);
        if (bucket != this.syncedDrawBucket) {
            this.syncedDrawBucket = bucket;
            return true;
        }
        return false;
    }

    // --- persistence: the drawn amount joins the update tag (NeroTech's base saves Active the same way)

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Drawn", this.drawnLastTick);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.drawnLastTick = input.getIntOr("Drawn", 0);
    }

    // --- menu sync: three extra ContainerData ints after the seven shared ones ----------------------

    /** [0] gradient of the hottest neighbour, [1] heat drawn last tick, [2] 1 with a cold face. */
    @Override
    protected int extraDataCount() {
        return 3;
    }

    @Override
    protected int extraData(int index) {
        return switch (index) {
            case 0 -> this.gradient;
            case 1 -> this.drawnLastTick;
            case 2 -> this.coldFace ? 1 : 0;
            default -> 0;
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.stirling_generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new StirlingMenu(containerId, playerInventory, this, this.data);
    }
}
