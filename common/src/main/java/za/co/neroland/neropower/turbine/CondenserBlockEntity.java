package za.co.neroland.neropower.turbine;

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

import za.co.neroland.nerotech.api.Atmosphere;
import za.co.neroland.nerotech.api.PlanetApi;
import za.co.neroland.nerotech.machine.MachineStatus;

import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;
import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.registry.ForeignBlocks;
import za.co.neroland.neropower.registry.ModTags;
import za.co.neroland.neropower.registry.content.TurbineContent;

/**
 * Steam Condenser: turns {@code neropower:exhaust_steam} back into {@code minecraft:water}, 1:1, using no
 * power (water is not energy, so a closed reactor-turbine-condenser loop gains and loses nothing). Its
 * heat sinks, used in this order: the air, at {@code condenserMbPerTick} scaled by the factor of the air
 * NeroTech's {@link PlanetApi#atmosphereAt} reports here (Nerospace planet, else NeroTech's config, else
 * Earth-like; a vacuum carries no heat away); then each face-adjacent NeroTech Radiator; then
 * {@code #nerotech:cryo_coolant} from its coolant tank. With none of them it stops and says "no heat
 * sink" rather than failing silently. No player data.
 */
public class CondenserBlockEntity extends NeroPowerMachineBlockEntity {

    /** NeroTech's cryo coolant fluid tag (there is no {@code c:} tag for it). */
    public static final TagKey<Fluid> CRYO_COOLANT =
            TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("nerotech", "cryo_coolant"));

    /** Stall causes (synced; the shared screen table: 3 no exhaust steam, 6 no heat sink, 7 water full). */
    public static final int STALL_NONE = 0;
    public static final int STALL_NO_EXHAUST = 3;
    public static final int STALL_NO_SINK = 6;
    public static final int STALL_WATER_FULL = 7;

    /** Synced ints after NeroTech's seven ({@code P} = a hi/lo pair of two ints). */
    public static final int EXTRA_EXHAUST_P = 0;
    public static final int EXTRA_EXHAUST_CAPACITY_P = 2;
    public static final int EXTRA_WATER_P = 4;
    public static final int EXTRA_WATER_CAPACITY_P = 6;
    public static final int EXTRA_CRYO_P = 8;
    public static final int EXTRA_CRYO_CAPACITY_P = 10;
    public static final int EXTRA_AIR_FACTOR = 12;
    public static final int EXTRA_RADIATORS = 13;
    public static final int EXTRA_RADIATOR_RATE = 14;
    public static final int EXTRA_CRYO_USED = 15;
    public static final int EXTRA_STEAM_PER_CRYO = 16;
    public static final int EXTRA_RATE_P = 17;
    public static final int EXTRA_POSSIBLE_P = 19;
    public static final int EXTRA_READOUT = 21;
    public static final int EXTRA_STALL = 22;
    public static final int EXTRA_SINK = 23;
    public static final int EXTRA_DATA = 24;

    /** How often the (dimension-wide) atmosphere is re-read. */
    private static final int ATMOSPHERE_REFRESH_TICKS = 200;

    private final TurbineTank exhaust;
    private final TurbineTank water;
    private final TurbineTank cryo;
    private final FluidRoute route;

    private int airFactor = TurbineMath.UNKNOWN_ATMOSPHERE_FACTOR;
    private long atmosphereReadAt = Long.MIN_VALUE;
    private int radiators = -1;
    private long lastCondensed;
    private long lastCryoUsed;
    private long possible;
    private int sink = TurbineMath.SINK_AIR;
    private int rateBucket;
    private int stallCause;
    private int readout;

    private int syncedRate = -1;
    private int syncedSink = -1;
    private int syncedReadout = -1;
    private int syncedStall = -1;

    public CondenserBlockEntity(BlockPos pos, BlockState state) {
        super(TurbineContent.CONDENSER_TYPE.get(), pos, state, 0);
        long capacity = TurbineConfig.condenserTankMb();
        this.exhaust = new TurbineTank(capacity, TurbineTank.tagged(ModTags.EXHAUST_STEAM), this::setChanged);
        this.water = new TurbineTank(capacity, fluid -> fluid == Fluids.WATER, this::setChanged);
        this.cryo = new TurbineTank(capacity, TurbineTank.tagged(CRYO_COOLANT), this::setChanged);
        this.route = new FluidRoute(List.of(this.exhaust, this.cryo), this.water);
        // Fluid I/O on every face: exhaust steam and coolant in, water out (auto-ejected).
        setupSideConfig(SideConfig.builder()
                .channel(Channel.FLUID)
                .defaultPreset(SidePreset.STORAGE)
                .autoEject(Channel.FLUID, true)
                .build());
        sideConfig().withFluid(() -> this.route);
    }

    @Nullable
    @Override
    public NeroFluidStorage fluidStorage(@Nullable Direction side) {
        SideConfigComponent config = sideConfig();
        return config == null ? this.route : config.fluidView(side);
    }

    @Override
    public List<GatedFluidView> standardFluidViews(@Nullable Direction side) {
        return List.of(oneWay(this.exhaust, side, true), oneWay(this.cryo, side, true),
                oneWay(this.water, side, false));
    }

    private GatedFluidView oneWay(TurbineTank tank, @Nullable Direction side, boolean in) {
        GatedFluidView face = gatedView(tank, Channel.FLUID, side);
        return new GatedFluidView(tank, () -> in && face.insertable(), () -> !in && face.extractable());
    }

    /** A neighbour changed: recount the adjacent Radiators on the next tick. */
    @Override
    public void invalidateThermalLinks() {
        super.invalidateThermalLinks();
        this.radiators = -1;
    }

    private int radiators(Level level, BlockPos pos) {
        if (this.radiators < 0) {
            int n = 0;
            for (Direction d : Direction.values()) {
                if (ForeignBlocks.is(level.getBlockState(pos.relative(d)), ForeignBlocks.NEROTECH_RADIATOR)) {
                    n++;
                }
            }
            this.radiators = n;
        }
        return this.radiators;
    }

    private int airFactor(Level level, BlockPos pos) {
        long now = level.getGameTime();
        if (now - this.atmosphereReadAt >= ATMOSPHERE_REFRESH_TICKS || now < this.atmosphereReadAt) {
            this.atmosphereReadAt = now;
            Atmosphere air = PlanetApi.atmosphereAt(level, pos);
            this.airFactor = air == null || !air.condensable()
                    ? 0 : TurbineMath.atmosphereFactor(TurbineConfig.condenserAtmosphereFactors(), air.name());
        }
        return this.airFactor;
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        long capacity = TurbineConfig.condenserTankMb();
        this.exhaust.resize(capacity);
        this.water.resize(capacity);
        this.cryo.resize(capacity);

        long air = TurbineMath.airRate(TurbineConfig.condenserMbPerTick(), airFactor(level, pos));
        long rad = (long) radiators(level, pos) * TurbineConfig.condenserRadiatorMbPerTick();
        TurbineMath.Condense result = TurbineMath.condense(this.exhaust.getAmount(), this.water.room(), air, rad,
                this.cryo.getAmount(), TurbineConfig.condenserSteamPerCryoMb());
        long done = result.condensed();
        if (done > 0L) {
            long drained = this.exhaust.drain(done, false);
            this.water.fill(Fluids.WATER, drained, false);
            if (result.cryoUsed() > 0L) {
                this.cryo.drain(result.cryoUsed(), false);
            }
            done = drained;
        }
        this.lastCondensed = done;
        this.lastCryoUsed = done > 0L ? result.cryoUsed() : 0L;
        this.possible = air + rad + this.cryo.getAmount() * TurbineConfig.condenserSteamPerCryoMb();
        this.sink = result.sink();
        this.rateBucket = TurbineMath.rateBucket(done, TurbineConfig.condenserMbPerTick());

        int cause = STALL_NONE;
        if (done <= 0L) {
            if (this.exhaust.getAmount() <= 0L) {
                cause = STALL_NO_EXHAUST;
                reportStatus(MachineStatus.STARVED);
            } else if (result.sink() == TurbineMath.SINK_NONE) {
                cause = STALL_NO_SINK;
                reportStatus(MachineStatus.BLOCKED, "status.neropower.condenser.no_heat_sink");
            } else if (this.water.room() <= 0L) {
                cause = STALL_WATER_FULL;
                reportStatus(MachineStatus.BLOCKED);
            }
        }
        this.stallCause = cause;
        this.readout = done > 0L ? TurbineReadout.WORK : cause != STALL_NONE ? TurbineReadout.STALL : TurbineReadout.REST;
        setActive(done > 0L);
    }

    // --- read surface ---------------------------------------------------------------------------------

    /** Synced render input: condensing rate bucket 0..4. */
    public int rateBucket() {
        return this.rateBucket;
    }

    /** Synced render input: the heat sink in use ({@link TurbineMath#SINK_AIR} .. {@link TurbineMath#SINK_NONE}). */
    public int sink() {
        return this.sink;
    }

    /** Synced readout (rest / work / stall / hot / trip / fail). */
    public int renderReadout() {
        return this.readout;
    }

    /** Synced stall cause. */
    public int renderStallCause() {
        return this.stallCause;
    }

    /** The exhaust-steam tank (input). */
    public TurbineTank exhaustTank() {
        return this.exhaust;
    }

    /** The water tank (output). */
    public TurbineTank waterTank() {
        return this.water;
    }

    /** The cryo-coolant tank (input). */
    public TurbineTank cryoTank() {
        return this.cryo;
    }

    @Override
    protected boolean renderSyncDirty() {
        boolean dirty = this.rateBucket != this.syncedRate || this.sink != this.syncedSink
                || this.readout != this.syncedReadout || this.stallCause != this.syncedStall;
        if (dirty) {
            this.syncedRate = this.rateBucket;
            this.syncedSink = this.sink;
            this.syncedReadout = this.readout;
            this.syncedStall = this.stallCause;
        }
        return dirty;
    }

    @Override
    protected void writeRenderExtras(CompoundTag tag) {
        tag.putInt(TurbineReadout.KEY_READOUT, this.readout);
        tag.putInt(TurbineReadout.KEY_STALL, this.stallCause);
        tag.putInt("NpRate", this.rateBucket);
        tag.putInt("NpSink", this.sink);
    }

    @Override
    protected void readRenderExtras(ValueInput input) {
        this.readout = input.getIntOr(TurbineReadout.KEY_READOUT, 0);
        this.stallCause = input.getIntOr(TurbineReadout.KEY_STALL, 0);
        this.rateBucket = input.getIntOr("NpRate", 0);
        this.sink = input.getIntOr("NpSink", TurbineMath.SINK_AIR);
    }

    // --- menu sync --------------------------------------------------------------------------------------

    @Override
    protected int extraDataCount() {
        return EXTRA_DATA;
    }

    @Override
    protected int extraData(int index) {
        long value = switch (index) {
            case EXTRA_EXHAUST_P, EXTRA_EXHAUST_P + 1 -> this.exhaust.getAmount();
            case EXTRA_EXHAUST_CAPACITY_P, EXTRA_EXHAUST_CAPACITY_P + 1 -> this.exhaust.getCapacity();
            case EXTRA_WATER_P, EXTRA_WATER_P + 1 -> this.water.getAmount();
            case EXTRA_WATER_CAPACITY_P, EXTRA_WATER_CAPACITY_P + 1 -> this.water.getCapacity();
            case EXTRA_CRYO_P, EXTRA_CRYO_P + 1 -> this.cryo.getAmount();
            case EXTRA_CRYO_CAPACITY_P, EXTRA_CRYO_CAPACITY_P + 1 -> this.cryo.getCapacity();
            case EXTRA_RATE_P, EXTRA_RATE_P + 1 -> this.lastCondensed;
            case EXTRA_POSSIBLE_P, EXTRA_POSSIBLE_P + 1 -> this.possible;
            case EXTRA_AIR_FACTOR -> this.airFactor;
            case EXTRA_RADIATORS -> Math.max(0, this.radiators);
            case EXTRA_RADIATOR_RATE -> TurbineConfig.condenserRadiatorMbPerTick();
            case EXTRA_CRYO_USED -> this.lastCryoUsed;
            case EXTRA_STEAM_PER_CRYO -> TurbineConfig.condenserSteamPerCryoMb();
            case EXTRA_READOUT -> this.readout;
            case EXTRA_STALL -> this.stallCause;
            case EXTRA_SINK -> this.sink;
            default -> 0L;
        };
        boolean pairHi = index == EXTRA_EXHAUST_P || index == EXTRA_EXHAUST_CAPACITY_P || index == EXTRA_WATER_P
                || index == EXTRA_WATER_CAPACITY_P || index == EXTRA_CRYO_P || index == EXTRA_CRYO_CAPACITY_P
                || index == EXTRA_RATE_P || index == EXTRA_POSSIBLE_P;
        boolean pairLo = index == EXTRA_EXHAUST_P + 1 || index == EXTRA_EXHAUST_CAPACITY_P + 1
                || index == EXTRA_WATER_P + 1 || index == EXTRA_WATER_CAPACITY_P + 1 || index == EXTRA_CRYO_P + 1
                || index == EXTRA_CRYO_CAPACITY_P + 1 || index == EXTRA_RATE_P + 1 || index == EXTRA_POSSIBLE_P + 1;
        if (pairHi) {
            return GaugeTail.hi2(value);
        }
        if (pairLo) {
            return GaugeTail.lo2(value);
        }
        return (int) Math.max(0L, Math.min(Short.MAX_VALUE, value));
    }

    // --- persistence ------------------------------------------------------------------------------------

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
        this.exhaust.save(output, "Exhaust");
        this.water.save(output, "Water");
        this.cryo.save(output, "Cryo");
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
        long capacity = TurbineConfig.condenserTankMb();
        this.exhaust.resize(capacity);
        this.water.resize(capacity);
        this.cryo.resize(capacity);
        this.exhaust.load(input, "Exhaust");
        this.water.load(input, "Water");
        this.cryo.load(input, "Cryo");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.condenser");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CondenserMenu(containerId, playerInventory, this, this.data);
    }
}
