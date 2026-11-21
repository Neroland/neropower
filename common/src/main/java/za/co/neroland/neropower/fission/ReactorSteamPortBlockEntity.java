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

/**
 * Reactor Steam Port: two tanks in a reactor wall. The <b>coolant</b> tank takes water or any
 * {@code #nerotech:cryo_coolant} fluid (one at a time); the <b>steam</b> tank holds what the core boils.
 * The Fission Core does all the work: in steam mode it drains water and fills steam here; in either mode
 * it draws cryo coolant to carry heat off and widen its safe band. The port only stores, exposes and
 * auto-ejects: pipes fill the coolant tank and draw the steam tank on every face (STORAGE preset,
 * auto-eject and auto-input on).
 *
 * <p>Synced render inputs: {@code readout} (rest / work / stall) and {@code stallCause} (none, no coolant,
 * steam full, no reactor). No player data.
 */
public class ReactorSteamPortBlockEntity extends NeroPowerMachineBlockEntity {

    /** NeroTech's cryo coolant fluid tag (there is no {@code c:} tag for it). */
    public static final TagKey<Fluid> CRYO_COOLANT =
            TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("nerotech", "cryo_coolant"));

    /** Readout codes (block contract). */
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

    /** The face view: fills go to the coolant tank, drains (and the reported fluid) come from the steam tank. */
    private final NeroFluidStorage route = new NeroFluidStorage() {
        @Override
        public Fluid getFluid() {
            return ReactorSteamPortBlockEntity.this.steam.getFluid();
        }

        @Override
        public long getAmount() {
            return ReactorSteamPortBlockEntity.this.steam.getAmount();
        }

        @Override
        public long getCapacity() {
            return ReactorSteamPortBlockEntity.this.steam.getCapacity();
        }

        @Override
        public long fill(Fluid fluid, long amount, boolean simulate) {
            return ReactorSteamPortBlockEntity.this.coolant.fill(fluid, amount, simulate);
        }

        @Override
        public long drain(long amount, boolean simulate) {
            return ReactorSteamPortBlockEntity.this.steam.drain(amount, simulate);
        }
    };

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

    /** Free room (mB) in the steam tank. */
    public long steamRoom() {
        return this.steam.room();
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

    /** Tank 0 is coolant (in only), tank 1 steam (out only): a fixed order per face. */
    @Override
    public List<GatedFluidView> standardFluidViews(@Nullable Direction side) {
        GatedFluidView face = gatedView(this.route, Channel.FLUID, side);
        return List.of(
                new GatedFluidView(this.coolant, face::insertable, () -> false),
                new GatedFluidView(this.steam, () -> false, face::extractable));
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
        } else if (this.steam.room() <= 0L) {
            cause = STALL_STEAM_FULL;
        } else if (this.coolant.getAmount() <= 0L) {
            cause = STALL_NO_COOLANT;
        } else {
            cause = STALL_NONE;
        }
        this.stallCause = cause;
        this.readout = flowing ? READOUT_WORK : linked && cause != STALL_NONE ? READOUT_STALL : READOUT_REST;
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
        this.coolant.save(output, "Coolant");
        this.steam.save(output, "Steam");
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
        this.coolant.load(input, "Coolant");
        this.steam.load(input, "Steam");
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
