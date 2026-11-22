package za.co.neroland.neropower.generation;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.fluid.FluidBuffer;
import za.co.neroland.nerolandcore.fluid.GatedFluidView;
import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;
import za.co.neroland.nerolandcore.gas.NeroGasStorage;
import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SideConfigComponent;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.nerotech.api.Atmosphere;
import za.co.neroland.nerotech.api.MachineSettings;
import za.co.neroland.nerotech.api.PlanetApi;
import za.co.neroland.nerotech.machine.MachineEnergy;
import za.co.neroland.nerotech.machine.MachineStatus;
import za.co.neroland.nerotech.machine.MediumExchange;

import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;
import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.registry.content.GenerationContent;
import za.co.neroland.neropower.turbine.PortExchange;
import za.co.neroland.neropower.turbine.PortTransfer;
import za.co.neroland.neropower.turbine.TurbineConfig;

/**
 * Hydrogen Fuel Cell: hydrogen and oxygen in (Core gases, or their transport fluids from a pipe), a
 * steady low trickle of NE and water out ({@link FuelCellCore}). With no oxygen in its tank it breathes
 * the air at half rate where NeroTech's {@link PlanetApi#atmosphereAt} reports {@code EARTHLIKE} or
 * {@code NITROGEN_OXYGEN} (Nerospace planet, else NeroTech's config, else Earth-like). It is a net sink
 * against NeroTech's Electrolyzer, so it is a way to carry power as gas, not a source. No heat, no
 * failure ladder, no player data.
 */
public class FuelCellBlockEntity extends NeroPowerMachineBlockEntity {

    /** Synced ints after NeroTech's seven ({@code P} = a hi/lo pair). */
    public static final int D_HYDROGEN_P = 0;
    public static final int D_HYDROGEN_CAP_P = 2;
    public static final int D_OXYGEN_P = 4;
    public static final int D_OXYGEN_CAP_P = 6;
    public static final int D_WATER_P = 8;
    public static final int D_WATER_CAP_P = 10;
    public static final int D_GAS_UNIT = 12;
    public static final int D_OUTPUT = 13;
    public static final int D_RATED = 14;
    public static final int D_OXYGEN_MODE = 15;
    public static final int D_TICKS_PER_UNIT = 16;
    public static final int D_NE_PER_UNIT = 17;
    public static final int D_WATER_PER_UNIT = 18;
    public static final int D_READOUT = 19;
    public static final int D_STALL = 20;
    public static final int D_COUNT = 21;

    /** How often the (dimension-wide) atmosphere is re-read. */
    private static final int ATMOSPHERE_REFRESH_TICKS = 200;

    private final FuelCellGases.Tank hydrogen;
    private final FuelCellGases.Tank oxygen;
    private final FluidBuffer water;
    private final FuelCellGases.Inlets inlets;
    private final NeroFluidStorage waterOut;
    private final FuelCellGases.FluidView hydrogenFluid;
    private final FuelCellGases.FluidView oxygenFluid;
    private final FuelCellCore core = new FuelCellCore();

    private boolean breathable;
    private long atmosphereReadAt = Long.MIN_VALUE;
    private int output;
    private int readout;
    private int stallCause;

    private int syncedReadout = -1;
    private int syncedStall = -1;
    private int syncedMode = -1;

    private final int phaseSeed = System.identityHashCode(this);

    public FuelCellBlockEntity(BlockPos pos, BlockState state) {
        super(GenerationContent.FUEL_CELL_TYPE.get(), pos, state, 0);
        long capacity = GenerationConfig.fuelCellTankMb();
        this.hydrogen = new FuelCellGases.Tank(capacity, FuelCellGases::isHydrogen, this::setChanged);
        this.oxygen = new FuelCellGases.Tank(capacity, FuelCellGases::isOxygen, this::setChanged);
        this.water = new FluidBuffer(capacity, this::setChanged);
        this.inlets = new FuelCellGases.Inlets(this.hydrogen, this.oxygen);
        this.waterOut = new OutletOnly(this.water);
        this.hydrogenFluid = new FuelCellGases.FluidView(this.hydrogen, FuelCellGases.HYDROGEN_FLUIDS,
                FuelCellGases.HYDROGEN_FLUID, FuelCellGases.HYDROGEN);
        this.oxygenFluid = new FuelCellGases.FluidView(this.oxygen, FuelCellGases.OXYGEN_FLUIDS,
                FuelCellGases.OXYGEN_FLUID, FuelCellGases.OXYGEN);
        // Energy out of every face; gas in on every face, pulled from neighbours that hold hydrogen or
        // oxygen; water out of every face, pushed to neighbours that take it (exchangeMedia, every
        // portTransferInterval ticks). The side config decides which faces do either.
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .channel(Channel.GAS)
                .channel(Channel.FLUID)
                .defaultPreset(SidePreset.GENERATOR)
                .preset(Channel.GAS, SidePreset.STORAGE)
                .preset(Channel.FLUID, SidePreset.STORAGE)
                .autoEject(Channel.ENERGY, true)
                .autoInput(Channel.GAS, true)
                .autoEject(Channel.FLUID, true)
                .build());
        sideConfig().withGas(() -> this.inlets);
        sideConfig().withFluid(() -> this.waterOut);
    }

    @Nullable
    @Override
    public NeroGasStorage gasStorage(@Nullable Direction side) {
        SideConfigComponent config = sideConfig();
        return config == null ? this.inlets : config.gasView(side);
    }

    @Nullable
    @Override
    public NeroFluidStorage fluidStorage(@Nullable Direction side) {
        SideConfigComponent config = sideConfig();
        return config == null ? this.waterOut : config.fluidView(side);
    }

    /** Standard fluid surface: hydrogen and oxygen as transport fluids (in only), water (out only). */
    @Override
    public List<GatedFluidView> standardFluidViews(@Nullable Direction side) {
        GatedFluidView h2 = gatedView(this.hydrogenFluid, Channel.GAS, side);
        GatedFluidView o2 = gatedView(this.oxygenFluid, Channel.GAS, side);
        GatedFluidView out = gatedView(this.water, Channel.FLUID, side);
        return List.of(
                new GatedFluidView(this.hydrogenFluid, h2::insertable, () -> false),
                new GatedFluidView(this.oxygenFluid, o2::insertable, () -> false),
                new GatedFluidView(this.water, () -> false, out::extractable));
    }

    /** A generator is never load-shed. */
    @Override
    public boolean shedable() {
        return false;
    }

    /**
     * Pipe-less exchange, every {@code portTransferInterval} ticks at this block's own phase: push the
     * water tank out of every face whose FLUID mode lets it out (auto-eject or PUSH), and pull hydrogen
     * and oxygen in through every face whose GAS mode takes input automatically. Every move goes through
     * NeroTech's conserving transfer, so nothing is created or lost.
     */
    @Override
    protected void exchangeMedia(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        int interval = TurbineConfig.portTransferInterval();
        if (!PortTransfer.due(level.getGameTime(), PortTransfer.phase(this.phaseSeed, interval), interval)) {
            return;
        }
        SideConfigComponent config = sideConfig();
        if (this.water.getAmount() > 0L) {
            PortExchange.push(level, pos, config, this.water, false, interval);
        }
        boolean autoInput = config != null && config.config().has(Channel.GAS)
                && config.config().get(Channel.GAS).autoInput();
        if (!autoInput) {
            return;
        }
        if (this.hydrogen.getAmount() < this.hydrogen.getCapacity()) {
            MediumExchange.pullGas(level, pos, config, this.hydrogen, mode -> PortTransfer.pulls(mode, true),
                    FuelCellGases::isHydrogen, neighbour -> neighbour instanceof FuelCellBlockEntity);
        }
        if (this.oxygen.getAmount() < this.oxygen.getCapacity()) {
            MediumExchange.pullGas(level, pos, config, this.oxygen, mode -> PortTransfer.pulls(mode, true),
                    FuelCellGases::isOxygen, neighbour -> neighbour instanceof FuelCellBlockEntity);
        }
    }

    private boolean breathable(Level level, BlockPos pos) {
        long now = level.getGameTime();
        if (now - this.atmosphereReadAt >= ATMOSPHERE_REFRESH_TICKS || now < this.atmosphereReadAt) {
            this.atmosphereReadAt = now;
            Atmosphere air = PlanetApi.atmosphereAt(level, pos);
            this.breathable = FuelCellCore.breathable(air == null ? null : air.name());
        }
        return this.breathable;
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        long capacity = GenerationConfig.fuelCellTankMb();
        this.hydrogen.resize(capacity);
        this.oxygen.resize(capacity);
        if (capacity != this.water.getCapacity()) {
            this.water.resize(capacity);
        }

        long room = Math.max(0L, getEnergy().getCapacity() - getEnergy().getAmount());
        FuelCellCore.Step step = this.core.tick(this.hydrogen.getAmount(), this.oxygen.getAmount(),
                breathable(level, pos), this.water.getCapacity() - this.water.getAmount(), room,
                GenerationConfig.fuelCellTicksPerUnit(), GenerationConfig.fuelCellNePerUnit(),
                GenerationConfig.fuelCellWaterPerUnitMb());
        if (step.drainHydrogen() > 0L) {
            this.hydrogen.drain(step.drainHydrogen(), false);
        }
        if (step.drainOxygen() > 0L) {
            this.oxygen.drain(step.drainOxygen(), false);
        }
        if (step.fillWater() > 0L) {
            this.water.fill(Fluids.WATER, step.fillWater(), false);
        }
        if (step.generate() > 0) {
            energyBuffer().generate(step.generate());
        }
        if (step.drainHydrogen() > 0L) {
            setChanged();
        }
        this.output = step.working() ? this.core.outputNePerTick() : 0;
        this.stallCause = step.cause();
        this.readout = GenerationReadout.readout(step.working(), step.cause());
        switch (step.cause()) {
            case GenerationReadout.CAUSE_NO_FUEL, GenerationReadout.CAUSE_NO_OXYGEN -> reportStatus(MachineStatus.STARVED);
            case GenerationReadout.CAUSE_OUTPUT_FULL, GenerationReadout.CAUSE_BUFFER_FULL -> reportStatus(MachineStatus.BLOCKED);
            default -> {
            }
        }
        setActive(step.working());
        MachineEnergy.pushToNeighbours(level, pos, energyBuffer(), MachineSettings.machineMaxTransfer(), sideConfig());
    }

    // --- read surface ---------------------------------------------------------------------------------

    /** Synced readout (rest / work / stall). */
    public int renderReadout() {
        return this.readout;
    }

    /** Synced stall cause. */
    public int renderStallCause() {
        return this.stallCause;
    }

    /** Synced oxygen mode (BER: on air every second plate lit): 0 fed, 1 air, 2 none. */
    public int renderOxygenMode() {
        return this.core.mode();
    }

    @Override
    protected boolean renderSyncDirty() {
        int mode = this.core.mode();
        boolean dirty = this.readout != this.syncedReadout || this.stallCause != this.syncedStall
                || mode != this.syncedMode;
        if (dirty) {
            this.syncedReadout = this.readout;
            this.syncedStall = this.stallCause;
            this.syncedMode = mode;
        }
        return dirty;
    }

    @Override
    protected void writeRenderExtras(CompoundTag tag) {
        tag.putInt(GenerationReadout.KEY_READOUT, this.readout);
        tag.putInt(GenerationReadout.KEY_STALL, this.stallCause);
        tag.putInt("NpOxygenMode", this.core.mode());
    }

    @Override
    protected void readRenderExtras(ValueInput input) {
        this.readout = input.getIntOr(GenerationReadout.KEY_READOUT, 0);
        this.stallCause = input.getIntOr(GenerationReadout.KEY_STALL, 0);
        this.core.load(this.core.progress(), this.core.length(), this.core.total(),
                input.getIntOr("NpOxygenMode", FuelCellCore.MODE_NONE));
    }

    // --- menu sync ------------------------------------------------------------------------------------

    @Override
    protected int extraDataCount() {
        return D_COUNT;
    }

    @Override
    protected int extraData(int index) {
        long value = switch (index) {
            case D_HYDROGEN_P, D_HYDROGEN_P + 1 -> this.hydrogen.getAmount();
            case D_HYDROGEN_CAP_P, D_HYDROGEN_CAP_P + 1 -> this.hydrogen.getCapacity();
            case D_OXYGEN_P, D_OXYGEN_P + 1 -> this.oxygen.getAmount();
            case D_OXYGEN_CAP_P, D_OXYGEN_CAP_P + 1 -> this.oxygen.getCapacity();
            case D_WATER_P, D_WATER_P + 1 -> this.water.getAmount();
            case D_WATER_CAP_P, D_WATER_CAP_P + 1 -> this.water.getCapacity();
            case D_GAS_UNIT -> FuelCellCore.GAS_UNIT_MB;
            case D_OUTPUT -> this.output;
            case D_RATED -> Math.round((float) GenerationConfig.fuelCellNePerUnit()
                    / Math.max(1, GenerationConfig.fuelCellTicksPerUnit()));
            case D_OXYGEN_MODE -> this.core.mode();
            case D_TICKS_PER_UNIT -> GenerationConfig.fuelCellTicksPerUnit();
            case D_NE_PER_UNIT -> GenerationConfig.fuelCellNePerUnit();
            case D_WATER_PER_UNIT -> GenerationConfig.fuelCellWaterPerUnitMb();
            case D_READOUT -> this.readout;
            case D_STALL -> this.stallCause;
            default -> 0L;
        };
        if (index >= D_HYDROGEN_P && index < D_GAS_UNIT) {
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
        this.hydrogen.save(output, "Hydrogen");
        this.oxygen.save(output, "Oxygen");
        if (this.water.getRawAmount() > 0) {
            output.putInt("WaterAmount", this.water.getRawAmount());
        }
        output.putInt("ChargeLeft", this.core.progress());
        output.putInt("ChargeTicks", this.core.length());
        output.putInt("ChargeNe", this.core.total());
        output.putInt("OxygenMode", this.core.mode());
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
        long capacity = GenerationConfig.fuelCellTankMb();
        this.hydrogen.resize(capacity);
        this.oxygen.resize(capacity);
        if (capacity != this.water.getCapacity()) {
            this.water.resize(capacity);
        }
        this.hydrogen.load(input, "Hydrogen");
        this.oxygen.load(input, "Oxygen");
        int water = Math.max(0, Math.min((int) Math.min(Integer.MAX_VALUE, capacity), input.getIntOr("WaterAmount", 0)));
        this.water.setRaw(water > 0 ? Fluids.WATER : Fluids.EMPTY, water);
        this.core.load(input.getIntOr("ChargeLeft", 0), input.getIntOr("ChargeTicks", 0),
                input.getIntOr("ChargeNe", 0), input.getIntOr("OxygenMode", FuelCellCore.MODE_NONE));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.fuel_cell");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new FuelCellMenu(containerId, playerInventory, this, this.data);
    }

    /** The water tank seen from outside: it can be emptied, never filled. */
    private record OutletOnly(FluidBuffer tank) implements NeroFluidStorage {

        @Override
        public Fluid getFluid() {
            return this.tank.getFluid();
        }

        @Override
        public long getAmount() {
            return this.tank.getAmount();
        }

        @Override
        public long getCapacity() {
            return this.tank.getCapacity();
        }

        @Override
        public long fill(Fluid fluid, long amount, boolean simulate) {
            return 0L;
        }

        @Override
        public long drain(long amount, boolean simulate) {
            return this.tank.drain(amount, simulate);
        }
    }
}
