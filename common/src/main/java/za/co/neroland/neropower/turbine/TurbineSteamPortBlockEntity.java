package za.co.neroland.neropower.turbine;

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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.fluid.GatedFluidView;
import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;
import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SideConfigComponent;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.nerotech.machine.MachineStatus;

import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;
import za.co.neroland.neropower.registry.ModTags;
import za.co.neroland.neropower.registry.content.TurbineContent;

/**
 * A Turbine Steam Port's two tanks: live steam ({@code c:steam}) for an inlet, exhaust steam for an
 * exhaust port. Only the tank of the current {@link PortMode} is exposed, one way: an inlet only takes
 * steam in, an exhaust port only gives exhaust out (auto-ejected into the neighbour, e.g. a Steam
 * Condenser); a vent port exposes nothing. The Turbine Helm moves the steam; the port reports whether it
 * is flowing, starved or blocked through the synced {@code readout} / {@code stallCause}. No player data.
 */
public class TurbineSteamPortBlockEntity extends NeroPowerMachineBlockEntity {

    /** Stall causes (synced; the shared screen table: 3 no steam, 5 exhaust blocked). */
    public static final int STALL_NONE = 0;
    public static final int STALL_STARVED = 3;
    public static final int STALL_EXHAUST_FULL = 5;

    /** Ticks a port keeps reading as working after the helm last moved steam through it. */
    private static final int FLOW_MEMORY_TICKS = 20;

    private final TurbineTank steam;
    private final TurbineTank exhaust;
    private final FluidRoute inletRoute;
    private final FluidRoute exhaustRoute;
    private final FluidRoute ventRoute;

    /** Ticks since the helm last moved steam through this port (transient). */
    private int sinceFlow = FLOW_MEMORY_TICKS;

    private int readout;
    private int stallCause;
    private int syncedReadout = -1;
    private int syncedStall = -1;

    public TurbineSteamPortBlockEntity(BlockPos pos, BlockState state) {
        super(TurbineContent.TURBINE_STEAM_PORT_TYPE.get(), pos, state, 0);
        long capacity = TurbineConfig.portTankMb();
        this.steam = new TurbineTank(capacity, TurbineTank.tagged(ModTags.C_STEAM), this::setChanged);
        this.exhaust = new TurbineTank(capacity, TurbineTank.tagged(ModTags.EXHAUST_STEAM), this::setChanged);
        this.inletRoute = new FluidRoute(List.of(this.steam), null);
        this.exhaustRoute = new FluidRoute(List.of(), this.exhaust);
        this.ventRoute = new FluidRoute(List.of(), null);
        // Fluid I/O on every face (STORAGE); the route decides the direction per mode. Exhaust auto-ejects.
        setupSideConfig(SideConfig.builder()
                .channel(Channel.FLUID)
                .defaultPreset(SidePreset.STORAGE)
                .autoEject(Channel.FLUID, true)
                .build());
        sideConfig().withFluid(this::route);
    }

    /** The port's current mode, from its block state. */
    public PortMode mode() {
        return TurbineSteamPortBlock.mode(getBlockState());
    }

    private NeroFluidStorage route() {
        return switch (mode()) {
            case INLET -> this.inletRoute;
            case EXHAUST -> this.exhaustRoute;
            case VENT -> this.ventRoute;
        };
    }

    /** The live-steam tank (the helm drains it while this port is an inlet). */
    public TurbineTank steamTank() {
        return this.steam;
    }

    /** The exhaust tank (the helm fills it while this port is an exhaust port). */
    public TurbineTank exhaustTank() {
        return this.exhaust;
    }

    /** The helm moved steam through this port this tick. */
    public void noteFlow() {
        this.sinceFlow = 0;
    }

    @Nullable
    @Override
    public NeroFluidStorage fluidStorage(@Nullable Direction side) {
        SideConfigComponent config = sideConfig();
        return config == null ? route() : config.fluidView(side);
    }

    @Override
    public List<GatedFluidView> standardFluidViews(@Nullable Direction side) {
        return switch (mode()) {
            case INLET -> List.of(oneWay(this.steam, side, true));
            case EXHAUST -> List.of(oneWay(this.exhaust, side, false));
            case VENT -> List.of();
        };
    }

    private GatedFluidView oneWay(TurbineTank tank, @Nullable Direction side, boolean in) {
        GatedFluidView face = gatedView(tank, Channel.FLUID, side);
        return new GatedFluidView(tank, () -> in && face.insertable(), () -> !in && face.extractable());
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        long capacity = TurbineConfig.portTankMb();
        this.steam.resize(capacity);
        this.exhaust.resize(capacity);
        if (this.sinceFlow < FLOW_MEMORY_TICKS) {
            this.sinceFlow++;
        }
        boolean flowing = this.sinceFlow < FLOW_MEMORY_TICKS;
        int cause = STALL_NONE;
        switch (mode()) {
            case INLET -> cause = this.steam.getAmount() <= 0L ? STALL_STARVED : STALL_NONE;
            case EXHAUST -> cause = this.exhaust.room() <= 0L ? STALL_EXHAUST_FULL : STALL_NONE;
            case VENT -> cause = STALL_NONE;
        }
        this.stallCause = cause;
        this.readout = flowing ? TurbineReadout.WORK : cause != STALL_NONE ? TurbineReadout.STALL : TurbineReadout.REST;
        setActive(flowing);
        if (cause == STALL_STARVED) {
            reportStatus(MachineStatus.STARVED);
        } else if (cause == STALL_EXHAUST_FULL) {
            reportStatus(MachineStatus.BLOCKED);
        }
    }

    // --- render sync (contract: readout 0-5, stallCause 0-15) ------------------------------------

    /** Synced readout (rest / work / stall / hot / trip / fail). */
    public int renderReadout() {
        return this.readout;
    }

    /** Synced stall cause. */
    public int renderStallCause() {
        return this.stallCause;
    }

    @Override
    protected boolean renderSyncDirty() {
        if (this.readout != this.syncedReadout || this.stallCause != this.syncedStall) {
            this.syncedReadout = this.readout;
            this.syncedStall = this.stallCause;
            return true;
        }
        return false;
    }

    @Override
    protected void writeRenderExtras(CompoundTag tag) {
        tag.putInt(TurbineReadout.KEY_READOUT, this.readout);
        tag.putInt(TurbineReadout.KEY_STALL, this.stallCause);
    }

    @Override
    protected void readRenderExtras(ValueInput input) {
        this.readout = input.getIntOr(TurbineReadout.KEY_READOUT, 0);
        this.stallCause = input.getIntOr(TurbineReadout.KEY_STALL, 0);
    }

    // --- persistence --------------------------------------------------------------------------------

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
        this.steam.save(output, "Steam");
        this.exhaust.save(output, "Exhaust");
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
        long capacity = TurbineConfig.portTankMb();
        this.steam.resize(capacity);
        this.exhaust.resize(capacity);
        this.steam.load(input, "Steam");
        this.exhaust.load(input, "Exhaust");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.neropower.turbine_steam_port");
    }

    /** No screen: the port's tanks are shown in the Turbine Helm's screen. */
    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return null;
    }
}
