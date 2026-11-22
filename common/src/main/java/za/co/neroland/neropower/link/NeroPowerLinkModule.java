package za.co.neroland.neropower.link;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.link.LinkActionHandler;
import za.co.neroland.nerolandcore.link.LinkActionResult;
import za.co.neroland.nerolandcore.link.LinkModuleInfo;
import za.co.neroland.nerolandcore.link.LinkSnapshotProvider;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.beam.BeamEndpointBlockEntity;
import za.co.neroland.neropower.beam.BeamTransmitterBlockEntity;
import za.co.neroland.neropower.beam.PhasedArrayEmitterBlockEntity;
import za.co.neroland.neropower.environmental.RadioisotopeGeneratorBlockEntity;
import za.co.neroland.neropower.environmental.StirlingGeneratorBlockEntity;
import za.co.neroland.neropower.fission.FissionCoreBlockEntity;
import za.co.neroland.neropower.generation.BetavoltaicCellBlockEntity;
import za.co.neroland.neropower.generation.FuelCellBlockEntity;
import za.co.neroland.neropower.machine.NeroPowerMachineBlock;
import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;
import za.co.neroland.neropower.platform.IPlatformHelper;
import za.co.neroland.neropower.platform.Services;
import za.co.neroland.neropower.storage.BankControllerBlockEntity;
import za.co.neroland.neropower.storage.FlowMeter;
import za.co.neroland.neropower.storage.MomentumWheelBlockEntity;
import za.co.neroland.neropower.storage.SmesControllerBlockEntity;
import za.co.neroland.neropower.storage.StorageBlockEntity;
import za.co.neroland.neropower.storage.StorageConfig;
import za.co.neroland.neropower.storage.SurgeBankBlockEntity;
import za.co.neroland.neropower.turbine.TurbineHelmBlockEntity;

/**
 * NeroPower's NeroLink module, <b>link schema 2</b> — the one class that plugs NeroPower into Core's
 * link SPI as a {@link LinkSnapshotProvider} (read side), a {@link LinkActionHandler} (write side) and a
 * publisher of live events, so the NeroLink bridge serves NeroPower to companion apps. Registered once
 * from {@code NeroPowerCommon.init()} via {@link #register()}.
 *
 * <p><b>Scoping (POPIA/GDPR; the pure rules live in {@link LinkScope} and {@link PlantScope}).</b> The
 * plant sections ({@code fission}, {@code turbines}, {@code storage}, {@code beams},
 * {@code generators}) list only what the asking <i>online</i> player could walk up to: loaded block
 * entities in their dimension within {@value LinkScope#PROXIMITY_CHUNKS} chunks on the chunk grid, each with a
 * coarse {@code band} only, and at most {@value SectionReadBudget#MAX_READS} fresh answers per section a
 * minute ({@link SectionReadBudget}). A machine that recorded
 * an owner (a beam transmitter / relay's link owner, a machine's NeroTech attribution owner) appears only
 * in that owner's answer. There is never a server-wide roster and {@code getNearestPlayer} is never used.
 * No entry carries a coordinate, a dimension, a distance, a direction, an owner or a name: each carries an
 * opaque handle {@code h} ({@link PlantHandles}) that only the asking player can use, for
 * {@value PlantHandles#TTL_MS} ms. The {@code catalogue} section is public and identical for everyone.
 *
 * <p><b>Compatibility.</b> Schema 2 is schema 1 minus location fields: every schema-1 section id and
 * non-location field keeps its name and meaning, so a schema-1 reader still gets a valid answer. A
 * schema-1 action call (with {@code dim/x/y/z} and no {@code h}) gets {@code update_required} — the same
 * answer whether or not a machine stands there, so positions cannot be probed.
 *
 * <p><b>Actions</b> (online only; {@value LinkRateLimiter#MAX_ACTIONS} per
 * {@value LinkRateLimiter#WINDOW_MS} ms per player on top of the bridge's limiter):
 * {@code acknowledge_alarm} clears a machine's alarm block state; {@code scram} drops a fission core's
 * control rods for {@value FissionCoreBlockEntity#SCRAM_TICKS} ticks; {@code turbine_trip} latches a
 * turbine's trip (the overspeed latch, no failure action — it only makes a plant safer). The target must
 * still be in the requester's scope when the action runs, and the requester must be its recorded owner:
 * an unowned machine accepts no remote action (use it in person).
 *
 * <p><b>Events.</b> {@link #publishFailure} (called by {@code FailureTelegraph}) delivers each failure-stage
 * transition per {@link PlantEvents}: online players near the machine (owner rule applied) get a live,
 * never-pushed notice; only a recorded opt-in owner gets a push-eligible copy, and only on a rise into
 * UNSTABLE or FAILURE. At most {@value FailureThrottle#MAX_EVENTS} events per machine per minute.
 *
 * <p><b>Memory.</b> Handles, rate windows and throttles live in memory only; handles go on logout, all of
 * a player's state goes on erasure ({@link PlayerDataErasure}). Nothing is persisted.
 */
public final class NeroPowerLinkModule implements LinkSnapshotProvider, LinkActionHandler {

    public static final String MODULE_ID = NeroPowerCommon.MOD_ID;
    public static final int SCHEMA_VERSION = 2;
    /** Shown in discovery when the loader cannot report NeroPower's version (tests, off-loader). */
    static final String FALLBACK_VERSION = "0.0.0";

    public static final String SECTION_FISSION = "fission";
    public static final String SECTION_TURBINES = "turbines";
    public static final String SECTION_STORAGE = "storage";
    public static final String SECTION_BEAMS = "beams";
    public static final String SECTION_GENERATORS = "generators";
    public static final String SECTION_CATALOGUE = "catalogue";
    public static final String ACTION_ACKNOWLEDGE_ALARM = "acknowledge_alarm";
    public static final String ACTION_SCRAM = "scram";
    public static final String ACTION_TURBINE_TRIP = "turbine_trip";
    /** The live-event topic failure-stage crossings are forwarded on (unchanged from schema 1). */
    public static final String TOPIC_FAILURE = PlantEvents.TOPIC_FAILURE;

    /** The one answer for a handle that is unknown, expired, foreign or out of scope. */
    static final String ERR_HANDLE = "unknown or expired handle";
    /** The one answer for a schema-1 call that addresses a machine by position. */
    static final String ERR_UPDATE_REQUIRED = "update_required: NeroPower link schema 2 addresses machines by"
            + " handle (h); update the app";

    static final List<String> SECTIONS = List.of(SECTION_FISSION, SECTION_TURBINES, SECTION_STORAGE,
            SECTION_BEAMS, SECTION_GENERATORS, SECTION_CATALOGUE);
    static final List<String> ACTIONS = List.of(ACTION_ACKNOWLEDGE_ALARM, ACTION_SCRAM, ACTION_TURBINE_TRIP);

    private static final NeroPowerLinkModule INSTANCE = new NeroPowerLinkModule();

    /** A handle's server-side target: never serialised. */
    record PlantTarget(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim, BlockPos pos) {
    }

    static final PlantHandles<PlantTarget> HANDLES = new PlantHandles<>(System::currentTimeMillis);
    static final LinkRateLimiter LIMITER = new LinkRateLimiter(System::currentTimeMillis);
    static final FailureThrottle THROTTLE = new FailureThrottle(System::currentTimeMillis);
    static final SectionReadBudget READS = new SectionReadBudget(System::currentTimeMillis);
    private static final Random SHUFFLE = new Random();

    /** Server ticks between sweeps of logged-out players' handles and expired windows (~5 s). */
    private static final int PRUNE_INTERVAL_TICKS = 100;
    private static int pruneCountdown = PRUNE_INTERVAL_TICKS;
    private static boolean erasureRegistered;

    /** Captured running server (see {@link #rememberServer}); volatile — written from the tick thread. */
    @Nullable
    private static volatile MinecraftServer server;

    private NeroPowerLinkModule() {
    }

    /** Register the module (snapshot + action) with Core's link registry. Idempotent per process. */
    public static void register() {
        LinkModuleInfo info = new LinkModuleInfo(MODULE_ID, modVersion(), SCHEMA_VERSION, SECTIONS, ACTIONS);
        NeroLinkRegistry.registerSnapshotProvider(INSTANCE, info);
        NeroLinkRegistry.registerActionHandler(INSTANCE, info);
        registerErasure();
    }

    /** POPIA/GDPR: one erase request also drops the player's in-memory link state at once. Idempotent. */
    static synchronized void registerErasure() {
        if (erasureRegistered) {
            return;
        }
        erasureRegistered = true;
        PlayerDataErasure.register((srv, uuid) -> forgetPlayer(uuid));
    }

    /** Drop every in-memory entry for {@code player}: handles, the action window and the read budget. */
    public static void forgetPlayer(UUID player) {
        HANDLES.forget(player);
        LIMITER.forget(player);
        READS.forget(player);
    }

    /** Whether any in-memory link state still refers to {@code player} (erasure probe; never persisted). */
    public static boolean holdsPlayer(UUID player) {
        return HANDLES.holds(player) || LIMITER.holds(player) || READS.holds(player);
    }

    /** Display version for discovery: the loader's NeroPower version, or {@link #FALLBACK_VERSION}. */
    static String modVersion() {
        String raw;
        try {
            raw = Services.load(IPlatformHelper.class).getModVersion();
        } catch (RuntimeException | LinkageError e) {
            raw = null;
        }
        return raw == null || raw.isBlank() ? FALLBACK_VERSION : raw.trim();
    }

    /** Capture the running server so provider/action calls can resolve players + levels; sweeps memory. */
    public static void rememberServer(MinecraftServer runningServer) {
        server = runningServer;
        if (--pruneCountdown <= 0) {
            pruneCountdown = PRUNE_INTERVAL_TICKS;
            Set<UUID> online = new HashSet<>();
            for (ServerPlayer player : runningServer.getPlayerList().getPlayers()) {
                online.add(player.getUUID());
            }
            HANDLES.retainOnly(online);
            READS.retainOnly(online);
            LIMITER.pruneExpired();
            THROTTLE.pruneExpired();
        }
    }

    // --- LinkSnapshotProvider --------------------------------------------------------

    @Override
    public String moduleId() {
        return MODULE_ID;
    }

    @Override
    public int schemaVersion() {
        return SCHEMA_VERSION;
    }

    @Override
    public List<String> sections() {
        return SECTIONS;
    }

    @Override
    public JsonObject snapshot(UUID playerId, String section, Map<String, String> params) {
        return switch (section) {
            case SECTION_FISSION -> plantSection(playerId, section, NeroPowerLinkModule::fissionEntry);
            case SECTION_TURBINES -> plantSection(playerId, section, NeroPowerLinkModule::turbineEntry);
            case SECTION_STORAGE -> plantSection(playerId, section, NeroPowerLinkModule::storageEntry);
            case SECTION_BEAMS -> plantSection(playerId, section, NeroPowerLinkModule::beamEntry);
            case SECTION_GENERATORS -> plantSection(playerId, section, NeroPowerLinkModule::generatorEntry);
            case SECTION_CATALOGUE -> PlantCatalogue.build(configuredTiers());
            default -> {
                JsonObject obj = new JsonObject();
                obj.addProperty("section", section);
                obj.addProperty("note", "unknown neropower section");
                yield obj;
            }
        };
    }

    /**
     * Fills {@code out} with a machine's non-location fields and returns true when the block entity
     * belongs to the section. Writers never add a position; {@link PlantScope#strip} would drop one anyway.
     */
    @FunctionalInterface
    private interface EntryWriter {
        boolean write(BlockEntity be, JsonObject out);
    }

    private static JsonObject plantSection(UUID playerId, String section, EntryWriter writer) {
        if (playerId != null && !READS.tryRead(playerId, section)) {
            // Over the read budget: repeat the last fresh answer (same handles, no new scan), or nothing.
            return READS.lastAnswer(playerId, section).orElseGet(() -> {
                JsonObject none = PlantScope.envelope(section, SCHEMA_VERSION, System.currentTimeMillis());
                none.addProperty("truncated", false);
                none.addProperty("note", "read limit: at most " + SectionReadBudget.MAX_READS
                        + " fresh answers a minute per section");
                return none;
            });
        }
        JsonObject out = PlantScope.envelope(section, SCHEMA_VERSION, System.currentTimeMillis());
        MinecraftServer srv = server;
        ServerPlayer player = srv == null || playerId == null ? null : srv.getPlayerList().getPlayer(playerId);
        if (player == null) {
            if (playerId != null) {
                HANDLES.forget(playerId);
            }
            out.addProperty("truncated", false);
            out.addProperty("note", "NeroPower shows only loaded machines within "
                    + LinkScope.PROXIMITY_BLOCKS + " blocks of you while you are online.");
            return out;
        }
        //? if >=1.21.6 {
        ServerLevel level = player.level();
        //?} else {
        /*ServerLevel level = player.serverLevel();
        *///?}
        String dim = level.dimension().identifier().toString();
        BlockPos origin = player.blockPosition();
        PlantScope.Viewer viewer = new PlantScope.Viewer(playerId, dim, origin.getX(), origin.getY(), origin.getZ());
        List<PlantScope.Candidate<PlantTarget>> found = new ArrayList<>();
        forEachNearbyBlockEntity(level, origin, be -> {
            if (!(be instanceof NeroPowerMachineBlockEntity machine)) {
                return;
            }
            JsonObject entry = new JsonObject();
            if (!writer.write(be, entry)) {
                return;
            }
            BlockPos pos = machine.getBlockPos();
            found.add(new PlantScope.Candidate<>(new PlantTarget(level.dimension(), pos.immutable()), dim,
                    pos.getX(), pos.getY(), pos.getZ(), ownerOf(machine), entry));
        });
        PlantScope.Selection<PlantTarget> selection = PlantScope.select(viewer, found, SHUFFLE);
        List<String> handles = HANDLES.issue(playerId, section, selection.targets());
        PlantScope.fill(out, selection.entries(), handles, selection.truncated());
        if (PlantScope.leaks(out)) {
            // Last line: never let a field added by mistake carry a place or an identity off the server.
            JsonObject safe = PlantScope.envelope(section, SCHEMA_VERSION, System.currentTimeMillis());
            safe.addProperty("truncated", false);
            safe.addProperty("note", "section withheld");
            HANDLES.forget(playerId);
            return safe;
        }
        READS.remember(playerId, section, out);
        return out;
    }

    /**
     * The recorded owner used for scoping: a transmitter / relay's link owner, otherwise the NeroTech
     * machine owner (only set when per-player attribution is on). Compared, never emitted.
     */
    private static Optional<UUID> ownerOf(NeroPowerMachineBlockEntity machine) {
        return LinkScope.ownerOf(machine instanceof BeamTransmitterBlockEntity transmitter
                ? transmitter.linkOwner() : Optional.empty(), machine.owner());
    }

    /**
     * Visit every block entity in a loaded chunk within {@link LinkScope#chunkRadius()} chunks of
     * {@code origin} whose position is within {@link LinkScope#PROXIMITY_BLOCKS} blocks. Unloaded
     * chunks are skipped ({@code getChunkNow}), never loaded.
     */
    private static void forEachNearbyBlockEntity(ServerLevel level, BlockPos origin, Consumer<BlockEntity> visitor) {
        int radius = LinkScope.chunkRadius();
        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
                    BlockPos pos = be.getBlockPos();
                    if (!be.isRemoved() && LinkScope.withinProximity(origin.getX(), origin.getY(), origin.getZ(),
                            pos.getX(), pos.getY(), pos.getZ())) {
                        visitor.accept(be);
                    }
                }
            }
        }
    }

    // --- entry writers (non-location fields only) --------------------------------------

    /** Fields every plant entry shares: schema-1 {@code id}/{@code status}, plus type, stage, alarm, heat. */
    private static void common(NeroPowerMachineBlockEntity machine, JsonObject out) {
        out.addProperty("id", machine.machineId());
        out.addProperty("type", machine.machineId());
        out.addProperty("status", machine.stats().status().name());
        out.addProperty("stage", machine.failureStage().name().toLowerCase(java.util.Locale.ROOT));
        out.addProperty("heatPermille", machine.heatPermille());
        out.addProperty("energyPermille", machine.energyPermille());
        BlockState state = machine.getBlockState();
        out.addProperty("alarm", state.hasProperty(NeroPowerMachineBlock.ALARM)
                && state.getValue(NeroPowerMachineBlock.ALARM));
    }

    private static boolean fissionEntry(BlockEntity be, JsonObject out) {
        if (!(be instanceof FissionCoreBlockEntity core)) {
            return false;
        }
        common(core, out);
        // schema 1 names, unchanged
        out.addProperty("failureStage", core.failureStage().name());
        out.addProperty("formed", core.formed());
        out.addProperty("shell", core.shellSize());
        out.addProperty("poison", core.poison());
        out.addProperty("controlRodPermille", core.controlRodFactorPermille());
        out.addProperty("scram", core.scrammed());
        // schema 2 additions
        out.addProperty("size", core.shellSize());
        out.addProperty("mode", core.mode().name().toLowerCase(java.util.Locale.ROOT));
        out.addProperty("rods", core.rodCount());
        out.addProperty("rodSlots", core.usableRodSlots());
        out.addProperty("rate", core.lastRate());
        out.addProperty("rateUnit", core.mode().name().equals("STEAM") ? "mB/t" : "NE/t");
        out.addProperty("state", PlantScope.readoutWord(core.renderReadout()));
        return true;
    }

    private static boolean turbineEntry(BlockEntity be, JsonObject out) {
        if (!(be instanceof TurbineHelmBlockEntity helm)) {
            return false;
        }
        common(helm, out);
        out.addProperty("formed", helm.formed());
        out.addProperty("length", helm.length());
        out.addProperty("rpm", helm.rpm());
        out.addProperty("outputNe", helm.lastOutput());
        out.addProperty("steamPermille", helm.steamPermille());
        out.addProperty("tripped", helm.tripped());
        out.addProperty("governor", helm.governorFitted());
        out.addProperty("state", PlantScope.readoutWord(helm.renderReadout()));
        return true;
    }

    private static boolean storageEntry(BlockEntity be, JsonObject out) {
        if (be instanceof BankControllerBlockEntity bank) {
            common(bank, out);
            out.addProperty("kind", "bank");
            // schema 1 names, unchanged
            out.addProperty("formed", bank.formed());
            out.addProperty("cells", bank.pool().memberCount());
            out.addProperty("amount", bank.pool().getAmount());
            out.addProperty("capacity", bank.pool().getCapacity());
            out.addProperty("mode", bank.mode().name());
            out.addProperty("storedPermille", PlantScope.permille(bank.pool().getAmount(), bank.pool().getCapacity()));
            out.addProperty("state", bank.formed() ? "working" : "idle");
            return true;
        }
        if (!(be instanceof StorageBlockEntity store)) {
            return false;
        }
        String kind;
        if (store instanceof SurgeBankBlockEntity surge) {
            kind = "surge_bank";
            out.addProperty("pooled", surge.pooled());
        } else if (store instanceof MomentumWheelBlockEntity) {
            kind = "momentum_wheel";
        } else if (store instanceof SmesControllerBlockEntity) {
            kind = "storage_ring";
        } else {
            return false;
        }
        common(store, out);
        out.addProperty("kind", kind);
        NeroEnergyStorage energy = store.getEnergy();
        out.addProperty("amount", energy.getAmount());
        out.addProperty("capacity", energy.getCapacity());
        out.addProperty("storedPermille", PlantScope.permille(energy.getAmount(), energy.getCapacity()));
        if (energy instanceof FlowMeter meter) {
            out.addProperty("inNe", meter.lastIn());
            out.addProperty("outNe", meter.lastOut());
        }
        out.addProperty("state", PlantScope.readoutWord(store.readoutCode()));
        return true;
    }

    private static boolean beamEntry(BlockEntity be, JsonObject out) {
        if (!(be instanceof BeamEndpointBlockEntity endpoint)) {
            return false;
        }
        common(endpoint, out);
        String type = endpoint.machineId();
        out.addProperty("kind", type.substring(type.indexOf(':') + 1));
        // schema 1 names, minus `distance` (a length between two places)
        out.addProperty("beam", endpoint.beamStatus().name());
        out.addProperty("linked", endpoint.linked());
        out.addProperty("lossPermille", endpoint.lossPermille());
        if (endpoint instanceof PhasedArrayEmitterBlockEntity array) {
            out.addProperty("lastPassNe", array.lastPassNe());
        }
        return true;
    }

    private static boolean generatorEntry(BlockEntity be, JsonObject out) {
        if (be instanceof RadioisotopeGeneratorBlockEntity rtg) {
            common(rtg, out);
            out.addProperty("kind", "rtg");
            out.addProperty("fuelled", rtg.fuelled());
            out.addProperty("outputPermille", rtg.outputPermille());
            out.addProperty("daysRemaining", rtg.daysRemaining());
            return true;
        }
        if (be instanceof StirlingGeneratorBlockEntity stirling) {
            common(stirling, out);
            out.addProperty("kind", "stirling");
            return true;
        }
        if (be instanceof FuelCellBlockEntity cell) {
            common(cell, out);
            out.addProperty("kind", "fuel_cell");
            out.addProperty("state", PlantScope.readoutWord(cell.renderReadout()));
            return true;
        }
        if (be instanceof BetavoltaicCellBlockEntity beta) {
            common(beta, out);
            out.addProperty("kind", "betavoltaic");
            out.addProperty("state", PlantScope.readoutWord(beta.renderReadout()));
            return true;
        }
        return false;
    }

    /** The storage tiers from the live server config (public numbers, the same for everyone). */
    static PlantCatalogue.Tiers configuredTiers() {
        List<PlantCatalogue.Tier> rows = new ArrayList<>();
        try {
            rows.add(new PlantCatalogue.Tier("battery_cell", "basic", StorageConfig.batteryCellCapacityBasic(),
                    StorageConfig.batteryCellIoBasic(), "block"));
            rows.add(new PlantCatalogue.Tier("battery_cell", "advanced", StorageConfig.batteryCellCapacityAdvanced(),
                    StorageConfig.batteryCellIoAdvanced(), "block"));
            rows.add(new PlantCatalogue.Tier("battery_cell", "elite", StorageConfig.batteryCellCapacityElite(),
                    StorageConfig.batteryCellIoElite(), "block"));
            rows.add(new PlantCatalogue.Tier("surge_bank", "fast", StorageConfig.surgeBankCapacity(),
                    StorageConfig.surgeBankIo(), "block"));
            rows.add(new PlantCatalogue.Tier("momentum_wheel", "standard", StorageConfig.momentumWheelCapacity(),
                    StorageConfig.momentumWheelIo(), "block"));
            rows.add(new PlantCatalogue.Tier("storage_ring", "coil", StorageConfig.smesCapacityPerCoil(),
                    StorageConfig.smesIoPerCoil(), "coil"));
            rows.add(new PlantCatalogue.Tier("power_cell", "basic", StorageConfig.powerCellCapacityBasic(),
                    StorageConfig.powerCellIoBasic(), "item"));
            rows.add(new PlantCatalogue.Tier("power_cell", "advanced", StorageConfig.powerCellCapacityAdvanced(),
                    StorageConfig.powerCellIoAdvanced(), "item"));
            rows.add(new PlantCatalogue.Tier("power_cell", "elite", StorageConfig.powerCellCapacityElite(),
                    StorageConfig.powerCellIoElite(), "item"));
        } catch (RuntimeException | LinkageError e) {
            NeroPowerCommon.LOGGER.debug("[NeroPower] catalogue storage tiers unavailable", e);
        }
        return new PlantCatalogue.Tiers(List.copyOf(rows));
    }

    // --- LinkActionHandler -----------------------------------------------------------

    @Override
    public List<String> actionIds() {
        return ACTIONS;
    }

    /** Every action changes world state: the player must be online (the bridge and this handler both check). */
    @Override
    public boolean allowOffline(String actionId) {
        return false;
    }

    @Override
    public LinkActionResult execute(UUID playerId, String actionId, JsonObject params) {
        if (!ACTIONS.contains(actionId)) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION, "unknown neropower action: " + actionId);
        }
        if (!LIMITER.tryAcquire(playerId)) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION,
                    "rate limited: at most " + LIMITER.max() + " neropower actions a minute");
        }
        LinkActionResult shape = checkShape(params);
        if (shape != null) {
            return shape;
        }
        return switch (actionId) {
            case ACTION_ACKNOWLEDGE_ALARM -> withTarget(playerId, params, (level, machine) -> {
                BlockPos pos = machine.getBlockPos();
                BlockState state = machine.getBlockState();
                if (!state.hasProperty(NeroPowerMachineBlock.ALARM)) {
                    return LinkActionResult.error(LinkActionResult.Error.VALIDATION, "this machine has no alarm");
                }
                if (state.getValue(NeroPowerMachineBlock.ALARM)) {
                    level.setBlock(pos, state.setValue(NeroPowerMachineBlock.ALARM, false), Block.UPDATE_ALL);
                }
                JsonObject result = actionState(params, machine);
                result.addProperty("alarm", false);
                return LinkActionResult.ok(result);
            });
            case ACTION_SCRAM -> withTarget(playerId, params, (level, machine) -> {
                if (!(machine instanceof FissionCoreBlockEntity core)) {
                    return LinkActionResult.error(LinkActionResult.Error.VALIDATION, "scram needs a fission core");
                }
                core.requestScram();
                JsonObject result = actionState(params, core);
                result.addProperty("scram", core.scrammed());
                result.addProperty("scramRequested", true);
                result.addProperty("scramTicks", FissionCoreBlockEntity.SCRAM_TICKS);
                return LinkActionResult.ok(result);
            });
            case ACTION_TURBINE_TRIP -> withTarget(playerId, params, (level, machine) -> {
                if (!(machine instanceof TurbineHelmBlockEntity helm)) {
                    return LinkActionResult.error(LinkActionResult.Error.VALIDATION, "turbine_trip needs a turbine helm");
                }
                helm.requestTrip();
                JsonObject result = actionState(params, helm);
                result.addProperty("tripped", helm.tripped());
                return LinkActionResult.ok(result);
            });
            default -> LinkActionResult.error(LinkActionResult.Error.VALIDATION, "unknown neropower action: " + actionId);
        };
    }

    /**
     * Shape check before anything touches the world: a schema-1 call (position, no handle) gets
     * {@link #ERR_UPDATE_REQUIRED}; a call with neither gets "h is required". Null when the shape is fine.
     * Pure, so the answer cannot depend on what stands anywhere.
     */
    @Nullable
    static LinkActionResult checkShape(@Nullable JsonObject params) {
        boolean hasHandle = params != null && params.has("h") && params.get("h").isJsonPrimitive()
                && !params.get("h").getAsString().isBlank();
        if (hasHandle) {
            return null;
        }
        boolean positional = params != null
                && (params.has("dim") || params.has("x") || params.has("y") || params.has("z"));
        return LinkActionResult.error(LinkActionResult.Error.VALIDATION,
                positional ? ERR_UPDATE_REQUIRED : "h is required");
    }

    @FunctionalInterface
    private interface MachineAction {
        LinkActionResult apply(ServerLevel level, NeroPowerMachineBlockEntity machine);
    }

    /**
     * Shared action preamble: server present, player ONLINE, the handle resolves for this player, the
     * target's chunk is loaded (never force-loaded), a NeroPower machine stands there, and it is still in
     * the player's scope ({@link PlantScope#inScope}). Any of those failing reads {@link #ERR_HANDLE}. Then
     * authority ({@link LinkScope#mayAct}): the recorded owner only; an unowned machine refuses every remote
     * action. Refusals are {@code NOT_OWNER}.
     */
    private static LinkActionResult withTarget(UUID playerId, JsonObject params, MachineAction action) {
        MinecraftServer srv = server;
        if (srv == null) {
            return LinkActionResult.error(LinkActionResult.Error.INTERNAL, "server not available");
        }
        ServerPlayer player = srv.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return LinkActionResult.error(LinkActionResult.Error.PLAYER_OFFLINE_REQUIRED,
                    "you must be online to operate a plant");
        }
        Optional<PlantTarget> resolved = HANDLES.resolve(playerId, params.get("h").getAsString());
        if (resolved.isEmpty()) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION, ERR_HANDLE);
        }
        PlantTarget target = resolved.get();
        ServerLevel level = srv.getLevel(target.dim());
        BlockPos pos = target.pos();
        if (level == null || !level.hasChunkAt(pos)
                || !(level.getBlockEntity(pos) instanceof NeroPowerMachineBlockEntity machine)) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION, ERR_HANDLE);
        }
        //? if >=1.21.6 {
        ServerLevel playerLevel = player.level();
        //?} else {
        /*ServerLevel playerLevel = player.serverLevel();
        *///?}
        BlockPos at = player.blockPosition();
        Optional<UUID> owner = ownerOf(machine);
        PlantScope.Viewer viewer = new PlantScope.Viewer(playerId,
                playerLevel.dimension().identifier().toString(), at.getX(), at.getY(), at.getZ());
        if (!PlantScope.inScope(viewer, level.dimension().identifier().toString(), pos.getX(), pos.getY(),
                pos.getZ(), owner)) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION, ERR_HANDLE);
        }
        // Remote control needs ownership; an unowned machine is used in person (screen, SCRAM Key).
        if (!LinkScope.mayAct(owner, playerId)) {
            return LinkActionResult.error(LinkActionResult.Error.NOT_OWNER, owner.isPresent()
                    ? "you do not own this machine"
                    : "remote control needs a machine you own; use this one in person");
        }
        return action.apply(level, machine);
    }

    /** What an action answers: the handle back, the type and the failure stage. No place. */
    private static JsonObject actionState(JsonObject params, NeroPowerMachineBlockEntity machine) {
        JsonObject state = new JsonObject();
        state.addProperty("h", params.get("h").getAsString());
        state.addProperty("type", machine.machineId());
        state.addProperty("failureStage", machine.failureStage().name());
        return state;
    }

    // --- live events -----------------------------------------------------------------

    /**
     * Deliver one failure-stage transition per {@link PlantEvents}. Called by {@code FailureTelegraph} on
     * the server thread with the machine in hand; its position is used only for the proximity check and
     * the per-machine throttle, in process, never in a payload. Nothing is ever broadcast.
     *
     * @param stage  the stage entered ({@code rising}) or left (falling), as on NeroTech's channel
     * @param rising {@code true} when the machine entered {@code stage}
     */
    public static void publishFailure(ServerLevel level, NeroPowerMachineBlockEntity machine, int stage,
            boolean rising) {
        try {
            BlockPos pos = machine.getBlockPos();
            if (!THROTTLE.tryFire(level.dimension().identifier() + "|" + pos.asLong())) {
                return;
            }
            List<PlantEvents.Nearby> players = new ArrayList<>();
            for (ServerPlayer player : level.players()) {
                BlockPos at = player.blockPosition();
                players.add(new PlantEvents.Nearby(player.getUUID(), at.getX(), at.getY(), at.getZ()));
            }
            // Push only to an already-recorded opt-in owner: NeroTech's attribution owner, never the
            // beam link owner (recorded for linking, not for notifications).
            UUID pushOwner = machine.owner().orElse(null);
            String type = machine.machineId();
            for (PlantEvents.Delivery delivery : PlantEvents.recipients(players, pos.getX(), pos.getY(), pos.getZ(),
                    ownerOf(machine), pushOwner, stage, rising)) {
                NeroLinkRegistry.eventBus().publish(PlantEvents.event(MODULE_ID, delivery, type, stage, rising));
            }
        } catch (RuntimeException e) {
            NeroPowerCommon.LOGGER.debug("[NeroPower] failure link event skipped", e);
        }
    }
}
