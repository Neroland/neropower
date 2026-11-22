package za.co.neroland.neropower.fission;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
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

import za.co.neroland.nerolandcore.fluid.GatedFluidView;
import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;
import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SideConfigComponent;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.nerotech.machine.MachineStatus;

import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;
import za.co.neroland.neropower.registry.ModTags;
import za.co.neroland.neropower.registry.content.FissionContent;
import za.co.neroland.neropower.turbine.PoolRoute;
import za.co.neroland.neropower.turbine.PortExchange;
import za.co.neroland.neropower.turbine.PortPool;
import za.co.neroland.neropower.turbine.PortTankSave;
import za.co.neroland.neropower.turbine.PortTransfer;
import za.co.neroland.neropower.turbine.PooledFluidStorage;
import za.co.neroland.neropower.turbine.TurbineConfig;

/**
 * Reactor Steam Port: two tanks in a reactor wall. The <b>coolant</b> tank takes water or any
 * {@code #nerotech:cryo_coolant} fluid (one at a time); the <b>steam</b> tank holds what the core boils.
 * The Fission Core does all the work: in steam mode it drains water and fills steam here; in either mode
 * it draws cryo coolant to carry heat off and widen its safe band.
 *
 * <p><b>Shared tanks.</b> While a formed core serves it, every port of that reactor is a
 * face onto the reactor's <em>shared</em> coolant and steam tanks ({@link PortPool} over the member
 * ports' own tanks, which stay the saved storage): water piped or pushed into any port can be boiled into
 * steam that leaves through any other. Every {@code portTransferInterval} ticks the port pushes steam into
 * the block it touches (a Turbine Steam Port inlet; on an automatic face never into a tank that also takes
 * water) and pulls water / cryo coolant from it, by its FLUID side configuration (STORAGE preset, auto-eject
 * and auto-input on). Pipes still work through the same capability.
 *
 * <p>Synced render inputs: {@code readout} (rest / work / stall) and {@code stallCause} (none, no coolant,
 * steam full, no reactor). No player data.
 */
public class ReactorSteamPortBlockEntity extends NeroPowerMachineBlockEntity {

    /** NeroTech's cryo coolant fluid tag (there is no {@code c:} tag for it). */
    public static final TagKey<Fluid> CRYO_COOLANT =
            TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("nerotech", "cryo_coolant"));

    /** Readout codes (render inputs). */
    public static final int READOUT_REST = 0;
    public static final int READOUT_WORK = 1;
    public static final int READOUT_STALL = 2;

    /** Stall causes (synced). */
    public static final int STALL_NONE = 0;
    public static final int STALL_NO_COOLANT = 1;
    public static final int STALL_STEAM_FULL = 2;
    public static final int STALL_NO_REACTOR = 3;

    /** Update-tag keys (render extras, never saved). */
    public static final String KEY_READOUT = "NpReadout";
    public static final String KEY_STALL = "NpStall";

    /** Ticks a port keeps counting as linked / flowing after the core last served it. */
    private static final int MEMORY_TICKS = 20;

    private final ReactorPortTank coolant;
    private final ReactorPortTank steam;

    /** The reactor's shared coolant tank as seen from this port (only this port's tank while unlinked). */
    private final PooledFluidStorage coolantPool = new PooledFluidStorage(this::coolantSlots);
    /** The reactor's shared steam tank as seen from this port. */
    private final PooledFluidStorage steamPool = new PooledFluidStorage(this::steamSlots);

    /** The face view: fills go to the shared coolant tank, drains (and the reported fluid) come from the shared steam tank. */
    private final NeroFluidStorage route = new PoolRoute(List.of(this.coolantPool), this.steamPool);

    /** The core that served this port last (transient, never saved; dropped once stale or removed). */
    @Nullable
    private FissionCoreBlockEntity core;

    /** Exchange phase (transient), spread across ports. */
    private final int phaseSeed = System.identityHashCode(this);

    /** Ticks since a core last served this port, and since steam last flowed (transient). */
    private int sinceLink = MEMORY_TICKS;
    private int sinceFlow = MEMORY_TICKS;

    private int readout;
    private int stallCause;
    private int syncedReadout = -1;
    private int syncedStall = -1;

    public ReactorSteamPortBlockEntity(BlockPos pos, BlockState state) {
        super(FissionContent.REACTOR_STEAM_PORT_TYPE.get(), pos, state, 0);
        long capacity = FissionConfig.steamPortTankMb();
        this.coolant = new ReactorPortTank(capacity, ReactorSteamPortBlockEntity::isCoolant, this::setChanged);
        this.steam = new ReactorPortTank(capacity, ReactorPortTank.tagged(ModTags.C_STEAM), this::setChanged);
        // Fluid I/O on every face: coolant in, steam out (auto-ejected into a pipe or turbine port), and
        // auto-input pulls water from an adjacent tank. No energy and no items.
        setupSideConfig(SideConfig.builder()
                .channel(Channel.FLUID)
                .defaultPreset(SidePreset.STORAGE)
                .autoEject(Channel.FLUID, true)
                .autoInput(Channel.FLUID, true)
                .build());
        sideConfig().withFluid(() -> this.route);
    }

    /** Whether the coolant tank takes {@code fluid}: water or a cryo coolant. */
    public static boolean isCoolant(Fluid fluid) {
        return fluid == Fluids.WATER || (fluid != Fluids.EMPTY && fluid.defaultFluidState().is(CRYO_COOLANT));
    }

    // --- the core's side -------------------------------------------------------------------------

    /** The core served this port this tick (it counts as linked for {@value #MEMORY_TICKS} ticks). */
    public void link() {
        this.sinceLink = 0;
    }

    /** As {@link #link()}, and this port becomes a face onto {@code core}'s shared tanks. */
    public void link(FissionCoreBlockEntity core) {
        this.sinceLink = 0;
        this.core = core;
    }

    /** The ports sharing tanks with this one: the serving core's ports, else this port alone. */
    private List<ReactorSteamPortBlockEntity> members() {
        FissionCoreBlockEntity c = this.core;
        if (c == null || c.isRemoved() || this.sinceLink >= MEMORY_TICKS) {
            return List.of(this);
        }
        List<ReactorSteamPortBlockEntity> ports = c.linkedPorts();
        return ports.contains(this) ? ports : List.of(this);
    }

    private List<ReactorPortTank> coolantSlots() {
        List<ReactorSteamPortBlockEntity> members = members();
        java.util.ArrayList<ReactorPortTank> out = new java.util.ArrayList<>(members.size());
        for (ReactorSteamPortBlockEntity port : members) {
            out.add(port.coolant);
        }
        return out;
    }

    private List<ReactorPortTank> steamSlots() {
        List<ReactorSteamPortBlockEntity> members = members();
        java.util.ArrayList<ReactorPortTank> out = new java.util.ArrayList<>(members.size());
        for (ReactorSteamPortBlockEntity port : members) {
            out.add(port.steam);
        }
        return out;
    }

    /** The shared coolant tank seen from this port. */
    public PooledFluidStorage coolantPool() {
        return this.coolantPool;
    }

    /** The shared steam tank seen from this port. */
    public PooledFluidStorage steamPool() {
        return this.steamPool;
    }

    /** Water (mB) the core may boil from here. */
    public long waterAvailable() {
        return this.coolant.getFluid() == Fluids.WATER ? this.coolant.getAmount() : 0L;
    }

    /** Cryo coolant (mB) the core may draw from here. */
    public long cryoAvailable() {
        Fluid fluid = this.coolant.getFluid();
        return fluid != Fluids.WATER && fluid != Fluids.EMPTY ? this.coolant.getAmount() : 0L;
    }

    /** Draw coolant (water or cryo, whichever the tank holds); returns what was drawn. */
    public long drawCoolant(long amount) {
        return amount <= 0L ? 0L : this.coolant.drain(amount, false);
    }

    /** Free room (mB) in this port's steam tank for NeroPower steam (0 while it holds another mod's steam). */
    public long steamRoom() {
        return this.steam.slotEmpty() || this.steam.getFluid() == steamFluid() ? this.steam.room() : 0L;
    }

    /** Add steam the core raised; returns what fitted. */
    public long addSteam(long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        long added = this.steam.fill(steamFluid(), amount, false);
        if (added > 0L) {
            this.sinceFlow = 0;
        }
        return added;
    }

    private static Fluid steamFluid() {
        return za.co.neroland.neropower.fluid.NeroPowerFluids.STEAM.get();
    }

    /** Coolant held (mB). */
    public long coolantAmount() {
        return this.coolant.getAmount();
    }

    /** Coolant capacity (mB). */
    public long coolantCapacity() {
        return this.coolant.getCapacity();
    }

    /** Steam held (mB). */
    public long steamAmount() {
        return this.steam.getAmount();
    }

    /** Steam capacity (mB). */
    public long steamCapacity() {
        return this.steam.getCapacity();
    }

    // --- fluid surfaces ---------------------------------------------------------------------------

    @Nullable
    @Override
    public NeroFluidStorage fluidStorage(@Nullable Direction side) {
        SideConfigComponent config = sideConfig();
        return config == null ? this.route : config.fluidView(side);
    }

    /** Tank 0 is the shared coolant tank (in only), tank 1 the shared steam tank (out only): a fixed order per face. */
    @Override
    public List<GatedFluidView> standardFluidViews(@Nullable Direction side) {
        GatedFluidView face = gatedView(this.route, Channel.FLUID, side);
        return List.of(
                new GatedFluidView(this.coolantPool, face::insertable, () -> false),
                new GatedFluidView(this.steamPool, () -> false, face::extractable));
    }

    // --- pipe-less exchange ------------------------------------------------------------

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
        if (this.steamPool.getAmount() > 0L) {
            PortExchange.push(level, pos, config, this.steamPool, true, interval);
        }
        PortExchange.pull(level, pos, config, this.coolantPool, ReactorSteamPortBlockEntity::isCoolant, interval);
    }

    // --- tick ---------------------------------------------------------------------------------------

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        long capacity = FissionConfig.steamPortTankMb();
        this.coolant.resize(capacity);
        this.steam.resize(capacity);
        if (this.sinceLink < MEMORY_TICKS) {
            this.sinceLink++;
        }
        if (this.sinceFlow < MEMORY_TICKS) {
            this.sinceFlow++;
        }
        boolean linked = this.sinceLink < MEMORY_TICKS;
        boolean flowing = this.sinceFlow < MEMORY_TICKS;
        int cause;
        if (!linked) {
            cause = STALL_NO_REACTOR;
        } else if (this.steamPool.roomFor(steamFluid()) <= 0L) {
            cause = STALL_STEAM_FULL;
        } else if (this.coolantPool.getAmount() <= 0L) {
            cause = STALL_NO_COOLANT;
        } else {
            cause = STALL_NONE;
        }
        this.stallCause = cause;
        this.readout = flowing ? READOUT_WORK : linked && cause != STALL_NONE ? READOUT_STALL : READOUT_REST;
        za.co.neroland.neropower.machine.NeroPowerMachineBlock.applyStatus(level, pos,
                flowing ? za.co.neroland.neropower.machine.NeroPowerMachineBlock.VisualStatus.WORKING
                        : linked && cause == STALL_STEAM_FULL
                                ? za.co.neroland.neropower.machine.NeroPowerMachineBlock.VisualStatus.TRIPPED
                                : linked && cause != STALL_NONE
                                        ? za.co.neroland.neropower.machine.NeroPowerMachineBlock.VisualStatus.STARVED
                                        : za.co.neroland.neropower.machine.NeroPowerMachineBlock.VisualStatus.IDLE);
        setActive(flowing);
        if (linked && cause == STALL_NO_COOLANT) {
            reportStatus(MachineStatus.STARVED);
        } else if (linked && cause == STALL_STEAM_FULL) {
            reportStatus(MachineStatus.BLOCKED);
        }
    }

    // --- render sync --------------------------------------------------------------------------------

    /** Synced readout (rest / work / stall). */
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
        tag.putInt(KEY_READOUT, this.readout);
        tag.putInt(KEY_STALL, this.stallCause);
    }

    @Override
    protected void readRenderExtras(ValueInput input) {
        this.readout = input.getIntOr(KEY_READOUT, 0);
        this.stallCause = input.getIntOr(KEY_STALL, 0);
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
        this.coolant.save(output, PortTankSave.REACTOR_COOLANT);
        this.steam.save(output, PortTankSave.REACTOR_STEAM);
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
        long capacity = FissionConfig.steamPortTankMb();
        this.coolant.resize(capacity);
        this.steam.resize(capacity);
        this.coolant.load(input, PortTankSave.REACTOR_COOLANT);
        this.steam.load(input, PortTankSave.REACTOR_STEAM);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.neropower.reactor_steam_port");
    }

    /** No screen: the port's tanks are shown in the Fission Core screen. */
    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return null;
    }
}
