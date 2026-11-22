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
 *
 * <p><b>Shared tanks.</b> While a formed helm serves it, an inlet is a face onto the
 * turbine's shared steam-in tank (every inlet port's tank) and an exhaust port onto its shared exhaust
 * tank. Every {@code portTransferInterval} ticks an exhaust port pushes exhaust steam into the block it
 * touches (a Steam Condenser; on an automatic face never into a tank that also takes water), by its FLUID
 * side configuration (STORAGE preset, auto-eject on). An inlet is passive: an abutting Reactor Steam Port
 * pushes into it. Pipes still work through the same capability.
 */
public class TurbineSteamPortBlockEntity extends NeroPowerMachineBlockEntity {

    /** Stall causes (synced; the shared screen table: 3 no steam, 5 exhaust blocked). */
    public static final int STALL_NONE = 0;
    public static final int STALL_STARVED = 3;
    public static final int STALL_EXHAUST_FULL = 5;

    /** Ticks a port keeps reading as working after the helm last moved steam through it. */
    private static final int FLOW_MEMORY_TICKS = 20;

    /** Ticks a port keeps counting as part of its turbine after the helm last served it. */
    private static final int LINK_MEMORY_TICKS = 20;

    private final TurbineTank steam;
    private final TurbineTank exhaust;
    /** The turbine's shared steam-in tank as seen from this port (this port's tank alone while unlinked). */
    private final PooledFluidStorage steamPool = new PooledFluidStorage(this::steamSlots);
    /** The turbine's shared exhaust tank as seen from this port. */
    private final PooledFluidStorage exhaustPool = new PooledFluidStorage(this::exhaustSlots);
    private final PoolRoute inletRoute;
    private final PoolRoute exhaustRoute;
    private final PoolRoute ventRoute;

    /** The helm that served this port last (transient, never saved). */
    @Nullable
    private TurbineHelmBlockEntity helm;
    private int sinceLink = LINK_MEMORY_TICKS;
    private final int phaseSeed = System.identityHashCode(this);

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
        this.inletRoute = new PoolRoute(List.of(this.steamPool), null);
        this.exhaustRoute = new PoolRoute(List.of(), this.exhaustPool);
        this.ventRoute = new PoolRoute(List.of(), null);
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

    /** The helm served this port this tick: it becomes a face onto that turbine's shared tanks. */
    public void link(TurbineHelmBlockEntity helm) {
        this.helm = helm;
        this.sinceLink = 0;
    }

    /** Ports sharing tanks with this one in {@code mode}: the serving helm's, else this port alone. */
    private List<TurbineSteamPortBlockEntity> members(PortMode mode) {
        TurbineHelmBlockEntity h = this.helm;
        if (h == null || h.isRemoved() || this.sinceLink >= LINK_MEMORY_TICKS) {
            return List.of(this);
        }
        List<TurbineSteamPortBlockEntity> ports = h.linkedPorts(mode);
        return ports.contains(this) ? ports : List.of(this);
    }

    private List<TurbineTank> steamSlots() {
        List<TurbineSteamPortBlockEntity> members = members(PortMode.INLET);
        java.util.ArrayList<TurbineTank> out = new java.util.ArrayList<>(members.size());
        for (TurbineSteamPortBlockEntity port : members) {
            out.add(port.steam);
        }
        return out;
    }

    private List<TurbineTank> exhaustSlots() {
        List<TurbineSteamPortBlockEntity> members = members(PortMode.EXHAUST);
        java.util.ArrayList<TurbineTank> out = new java.util.ArrayList<>(members.size());
        for (TurbineSteamPortBlockEntity port : members) {
            out.add(port.exhaust);
        }
        return out;
    }

    /** The shared steam-in tank seen from this port. */
    public PooledFluidStorage steamPool() {
        return this.steamPool;
    }

    /** The shared exhaust tank seen from this port. */
    public PooledFluidStorage exhaustPool() {
        return this.exhaustPool;
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
            case INLET -> List.of(oneWay(this.steamPool, side, true));
            case EXHAUST -> List.of(oneWay(this.exhaustPool, side, false));
            case VENT -> List.of();
        };
    }

    private GatedFluidView oneWay(NeroFluidStorage tank, @Nullable Direction side, boolean in) {
        GatedFluidView face = gatedView(tank, Channel.FLUID, side);
        return new GatedFluidView(tank, () -> in && face.insertable(), () -> !in && face.extractable());
    }

    // --- pipe-less exchange ------------------------------------------------------------

    @Override
    protected void exchangeMedia(Level level, BlockPos pos) {
        if (level.isClientSide() || mode() != PortMode.EXHAUST) {
            return;
        }
        int interval = TurbineConfig.portTransferInterval();
        if (!PortTransfer.due(level.getGameTime(), PortTransfer.phase(this.phaseSeed, interval), interval)) {
            return;
        }
        if (this.exhaustPool.getAmount() > 0L) {
            PortExchange.push(level, pos, sideConfig(), this.exhaustPool, true, interval);
        }
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        long capacity = TurbineConfig.portTankMb();
        this.steam.resize(capacity);
        this.exhaust.resize(capacity);
        if (this.sinceFlow < FLOW_MEMORY_TICKS) {
            this.sinceFlow++;
        }
        if (this.sinceLink < LINK_MEMORY_TICKS) {
            this.sinceLink++;
        } else {
            this.helm = null;
        }
        boolean flowing = this.sinceFlow < FLOW_MEMORY_TICKS;
        int cause = STALL_NONE;
        switch (mode()) {
            case INLET -> cause = this.steamPool.getAmount() <= 0L ? STALL_STARVED : STALL_NONE;
            case EXHAUST -> cause = this.exhaustPool.getAmount() >= this.exhaustPool.getCapacity()
                    ? STALL_EXHAUST_FULL : STALL_NONE;
            case VENT -> cause = STALL_NONE;
        }
        this.stallCause = cause;
        this.readout = flowing ? TurbineReadout.WORK : cause != STALL_NONE ? TurbineReadout.STALL : TurbineReadout.REST;
        setActive(flowing);
        // Starved and blocked both show the hollow-ring model; a flowing port the animated one.
        za.co.neroland.neropower.machine.NeroPowerMachineBlock.applyStatus(level, pos,
                za.co.neroland.neropower.machine.NeroPowerMachineBlock.VisualStatus.fromReadout(this.readout));
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
        this.steam.save(output, PortTankSave.TURBINE_STEAM);
        this.exhaust.save(output, PortTankSave.TURBINE_EXHAUST);
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
        this.steam.load(input, PortTankSave.TURBINE_STEAM);
        this.exhaust.load(input, PortTankSave.TURBINE_EXHAUST);
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
