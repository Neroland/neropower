package za.co.neroland.neropower.link;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.event.ThresholdEvents;
import za.co.neroland.nerolandcore.link.LinkActionHandler;
import za.co.neroland.nerolandcore.link.LinkActionResult;
import za.co.neroland.nerolandcore.link.LinkEvent;
import za.co.neroland.nerolandcore.link.LinkModuleInfo;
import za.co.neroland.nerolandcore.link.LinkSnapshotProvider;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.nerotech.api.MachineFailureEvents;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.beam.BeamEndpointBlockEntity;
import za.co.neroland.neropower.beam.BeamTransmitterBlockEntity;
import za.co.neroland.neropower.environmental.RadioisotopeGeneratorBlockEntity;
import za.co.neroland.neropower.environmental.StirlingGeneratorBlockEntity;
import za.co.neroland.neropower.fission.FissionCoreBlockEntity;
import za.co.neroland.neropower.machine.NeroPowerMachineBlock;
import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;
import za.co.neroland.neropower.protection.Protection;
import za.co.neroland.neropower.storage.BankControllerBlockEntity;

/**
 * NeroPower's NeroLink module (Stage 8) — the one class that plugs NeroPower into Core's link SPI
 * as a {@link LinkSnapshotProvider} (read side), a {@link LinkActionHandler} (write side) and a
 * publisher of live {@link LinkEvent}s, so the NeroLink companion bridge serves NeroPower to app
 * clients. Registered once from {@code NeroPowerCommon.init()} via {@link #register()}. Mirrors
 * NeroTech's {@code NeroTechLinkModule} in structure and Core API usage.
 *
 * <p><b>Scoping (POPIA/GDPR; the rules live in {@link LinkScope}).</b> Every section enumerates
 * only what the requesting <i>online</i> player could walk up to: loaded block entities in their
 * dimension within {@value LinkScope#PROXIMITY_BLOCKS} blocks. There is never a server-wide roster,
 * {@code getNearestPlayer} is never used, and the requester is only ever the UUID Core passes in.
 * On top of that, a machine that recorded an owner — a beam transmitter / relay's link owner, a
 * fission core's NeroTech owner (only set when per-player attribution is on) — appears only in that
 * owner's snapshot; unowned machines appear by proximity alone. No snapshot carries a UUID or a name.
 *
 * <p><b>Sections:</b> {@code beams} (transmitters, relays, receivers: status, linked flag,
 * distance, loss), {@code fission} (cores: status, failure stage, formed / shell, poison, control-rod
 * factor, alarm, scram), {@code storage} (bank controllers: pooled amount / capacity, mode),
 * {@code generators} (RTG: output permille, days remaining; Stirling: status).
 *
 * <p><b>Actions</b> (online-only): {@code acknowledge_alarm} clears a fission core's {@code alarm}
 * block state; {@code scram} asks a core to drop its control rods for
 * {@value FissionCoreBlockEntity#SCRAM_TICKS} ticks. Who may act is {@link LinkScope#mayAct}: an
 * owned core obeys its owner only; an unowned core (NeroTech's opt-in attribution off at placement)
 * obeys a requester who is online, in the core's dimension, within
 * {@value LinkScope#PROXIMITY_BLOCKS} blocks of it and passes {@code Protection.get().mayInteract}.
 * The bridge validates token, module presence, action-enabled config, rate limit and offline
 * policy; this handler still re-checks authority, exactly as NeroTech.
 *
 * <p><b>Events.</b> Failure-stage crossings NeroPower publishes on NeroTech's
 * {@link MachineFailureEvents#CHANNEL} are forwarded as <i>player-targeted</i>
 * ({@link LinkEvent#forPlayer}) {@code failure} events carrying the non-personal scope string
 * (machine id, dimension, packed position). The machine at the scope position is resolved if its
 * chunk is loaded: an owned machine's event goes to its owner only; otherwise one event goes to
 * each online player in that dimension within {@value LinkScope#PROXIMITY_BLOCKS} blocks of it.
 * Nothing is ever broadcast server-wide, and with no captured server nothing is published.
 *
 * <p><b>Server handle.</b> The link SPI passes no server; {@link #rememberServer(MinecraftServer)}
 * captures it from the per-loader server tick (see {@code ScorchTicker.tick}), as NeroTech does from
 * {@code PollutionManager.tick}. Every method here runs on the server thread when the bridge calls it.
 */
public final class NeroPowerLinkModule implements LinkSnapshotProvider, LinkActionHandler {

    public static final String MODULE_ID = NeroPowerCommon.MOD_ID;
    public static final int SCHEMA_VERSION = 1;

    /** Display version reported in discovery (matches NeroPower's mod version). */
    // TODO: hand-maintained duplicate — bump this in step with `mod_version` in gradle.properties,
    // or the companion app reports a stale NeroPower version.
    public static final String MOD_VERSION = "0.1.0-alpha.1";

    public static final String SECTION_BEAMS = "beams";
    public static final String SECTION_FISSION = "fission";
    public static final String SECTION_STORAGE = "storage";
    public static final String SECTION_GENERATORS = "generators";
    public static final String ACTION_ACKNOWLEDGE_ALARM = "acknowledge_alarm";
    public static final String ACTION_SCRAM = "scram";
    /** The live-event topic failure-stage crossings are forwarded on. */
    public static final String TOPIC_FAILURE = "failure";

    private static final List<String> SECTIONS =
            List.of(SECTION_BEAMS, SECTION_FISSION, SECTION_STORAGE, SECTION_GENERATORS);
    private static final List<String> ACTIONS = List.of(ACTION_ACKNOWLEDGE_ALARM, ACTION_SCRAM);

    private static final NeroPowerLinkModule INSTANCE = new NeroPowerLinkModule();

    /** Captured running server (see {@link #rememberServer}); volatile — written from the tick thread. */
    @Nullable
    private static volatile MinecraftServer server;

    /** Guards the one-time failure-event subscription (register() is idempotent per process). */
    private static volatile boolean eventsSubscribed;

    private NeroPowerLinkModule() {
    }

    /** Register the module (snapshot + action + event forwarding) with Core's link registry. Idempotent per process. */
    public static void register() {
        LinkModuleInfo info = new LinkModuleInfo(MODULE_ID, MOD_VERSION, SCHEMA_VERSION, SECTIONS, ACTIONS);
        NeroLinkRegistry.registerSnapshotProvider(INSTANCE, info);
        NeroLinkRegistry.registerActionHandler(INSTANCE, info);
        if (!eventsSubscribed) {
            eventsSubscribed = true;
            ThresholdEvents.onCrossing(NeroPowerLinkModule::forwardFailureCrossing);
        }
    }

    /** Capture the running server so provider/action calls can resolve players + levels. */
    public static void rememberServer(MinecraftServer runningServer) {
        server = runningServer;
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
            case SECTION_BEAMS -> proximitySection(playerId, section, be -> be instanceof BeamEndpointBlockEntity,
                    NeroPowerLinkModule::beamEntry);
            case SECTION_FISSION -> proximitySection(playerId, section, be -> be instanceof FissionCoreBlockEntity,
                    NeroPowerLinkModule::fissionEntry);
            case SECTION_STORAGE -> proximitySection(playerId, section, be -> be instanceof BankControllerBlockEntity,
                    NeroPowerLinkModule::storageEntry);
            case SECTION_GENERATORS -> proximitySection(playerId, section,
                    be -> be instanceof RadioisotopeGeneratorBlockEntity || be instanceof StirlingGeneratorBlockEntity,
                    NeroPowerLinkModule::generatorEntry);
            default -> {
                JsonObject obj = new JsonObject();
                obj.addProperty("section", section);
                obj.addProperty("note", "unknown neropower section");
                yield obj;
            }
        };
    }

    /** One entry builder per section: fills {@code out} from a machine that already passed scoping. */
    @FunctionalInterface
    private interface EntryWriter {
        void write(NeroPowerMachineBlockEntity machine, JsonObject out);
    }

    /**
     * The shared section shape: {@code machines[]} of everything matching {@code filter} within the
     * requester's proximity (their dimension, {@value LinkScope#PROXIMITY_BLOCKS} blocks, loaded
     * chunks) that {@link LinkScope#visibleTo} allows. No server or an offline requester yields an
     * empty list with a note — never a fallback to "everything".
     */
    private static JsonObject proximitySection(UUID playerId, String section,
            Predicate<BlockEntity> filter, EntryWriter writer) {
        JsonObject out = new JsonObject();
        out.addProperty("section", section);
        out.addProperty("asOf", System.currentTimeMillis());
        out.addProperty("scope", "proximity");
        out.addProperty("radius", LinkScope.PROXIMITY_BLOCKS);
        JsonArray machines = new JsonArray();
        out.add("machines", machines);
        MinecraftServer srv = server;
        ServerPlayer player = srv == null ? null : srv.getPlayerList().getPlayer(playerId);
        if (player == null) {
            out.addProperty("note", "NeroPower sections show only loaded machines within "
                    + LinkScope.PROXIMITY_BLOCKS + " blocks of you while you are online.");
            return out;
        }
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();
        out.addProperty("dim", level.dimension().identifier().toString());
        forEachNearbyBlockEntity(level, origin, be -> {
            if (!filter.test(be) || !(be instanceof NeroPowerMachineBlockEntity machine)) {
                return;
            }
            if (!LinkScope.visibleTo(ownerOf(machine), playerId)) {
                return;
            }
            JsonObject entry = new JsonObject();
            BlockPos pos = machine.getBlockPos();
            entry.addProperty("id", machine.machineId());
            entry.addProperty("x", pos.getX());
            entry.addProperty("y", pos.getY());
            entry.addProperty("z", pos.getZ());
            entry.addProperty("status", machine.stats().status().name());
            entry.addProperty("owned", LinkScope.isOwner(ownerOf(machine), playerId));
            writer.write(machine, entry);
            machines.add(entry);
        });
        return out;
    }

    /**
     * The recorded owner used for scoping: a transmitter / relay's link owner, otherwise the NeroTech
     * machine owner (only set when per-player attribution is on). Compared, never emitted.
     */
    private static Optional<UUID> ownerOf(NeroPowerMachineBlockEntity machine) {
        if (machine instanceof BeamTransmitterBlockEntity transmitter) {
            return transmitter.linkOwner();
        }
        return machine.owner();
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

    private static void beamEntry(NeroPowerMachineBlockEntity machine, JsonObject out) {
        BeamEndpointBlockEntity endpoint = (BeamEndpointBlockEntity) machine;
        out.addProperty("beam", endpoint.beamStatus().name());
        out.addProperty("linked", endpoint.linked());
        out.addProperty("distance", endpoint.distance());
        out.addProperty("lossPermille", endpoint.lossPermille());
    }

    private static void fissionEntry(NeroPowerMachineBlockEntity machine, JsonObject out) {
        FissionCoreBlockEntity core = (FissionCoreBlockEntity) machine;
        out.addProperty("failureStage", core.failureStage().name());
        out.addProperty("formed", core.formed());
        out.addProperty("shell", core.shellSize());
        out.addProperty("poison", core.poison());
        out.addProperty("controlRodPermille", core.controlRodFactorPermille());
        out.addProperty("scram", core.scrammed());
        BlockState state = core.getBlockState();
        out.addProperty("alarm", state.hasProperty(NeroPowerMachineBlock.ALARM)
                && state.getValue(NeroPowerMachineBlock.ALARM));
    }

    private static void storageEntry(NeroPowerMachineBlockEntity machine, JsonObject out) {
        BankControllerBlockEntity bank = (BankControllerBlockEntity) machine;
        out.addProperty("formed", bank.formed());
        out.addProperty("cells", bank.pool().memberCount());
        out.addProperty("amount", bank.pool().getAmount());
        out.addProperty("capacity", bank.pool().getCapacity());
        out.addProperty("mode", bank.mode().name());
    }

    private static void generatorEntry(NeroPowerMachineBlockEntity machine, JsonObject out) {
        if (machine instanceof RadioisotopeGeneratorBlockEntity rtg) {
            out.addProperty("kind", "rtg");
            out.addProperty("fuelled", rtg.fuelled());
            out.addProperty("outputPermille", rtg.outputPermille());
            out.addProperty("daysRemaining", rtg.daysRemaining());
        } else {
            out.addProperty("kind", "stirling");
        }
    }

    // --- LinkActionHandler -----------------------------------------------------------

    @Override
    public List<String> actionIds() {
        return ACTIONS;
    }

    /** Both actions change world state: the player must be online (the bridge and this handler both check). */
    @Override
    public boolean allowOffline(String actionId) {
        return false;
    }

    @Override
    public LinkActionResult execute(UUID playerId, String actionId, JsonObject params) {
        return switch (actionId) {
            case ACTION_ACKNOWLEDGE_ALARM -> acknowledgeAlarm(playerId, params);
            case ACTION_SCRAM -> scram(playerId, params);
            default -> LinkActionResult.error(LinkActionResult.Error.VALIDATION,
                    "unknown neropower action: " + actionId);
        };
    }

    /** Clear the {@code alarm} block state on a fission core (the telegraph re-raises it on the next stage change). */
    private LinkActionResult acknowledgeAlarm(UUID playerId, JsonObject params) {
        return withActionableCore(playerId, params, (level, core) -> {
            BlockPos pos = core.getBlockPos();
            BlockState state = core.getBlockState();
            if (state.hasProperty(NeroPowerMachineBlock.ALARM) && state.getValue(NeroPowerMachineBlock.ALARM)) {
                level.setBlock(pos, state.setValue(NeroPowerMachineBlock.ALARM, false), Block.UPDATE_ALL);
            }
            JsonObject result = coreState(level, core);
            result.addProperty("alarm", false);
            return LinkActionResult.ok(result);
        });
    }

    /** SCRAM a fission core: every control rod in for {@value FissionCoreBlockEntity#SCRAM_TICKS} ticks. */
    private LinkActionResult scram(UUID playerId, JsonObject params) {
        return withActionableCore(playerId, params, (level, core) -> {
            core.requestScram();
            JsonObject result = coreState(level, core);
            result.addProperty("scramRequested", true);
            result.addProperty("scramTicks", FissionCoreBlockEntity.SCRAM_TICKS);
            return LinkActionResult.ok(result);
        });
    }

    @FunctionalInterface
    private interface CoreAction {
        LinkActionResult apply(ServerLevel level, FissionCoreBlockEntity core);
    }

    /**
     * Shared action preamble, in NeroTech's order: server present, player ONLINE, {@code dim/x/y/z}
     * present and valid, chunk loaded (never force-loaded), a fission core there, and
     * {@link LinkScope#mayAct}: its recorded owner, or — for an unowned core — a requester in its
     * dimension within {@value LinkScope#PROXIMITY_BLOCKS} blocks who passes the protection seam.
     * Refusals are {@code NOT_OWNER}.
     */
    private static LinkActionResult withActionableCore(UUID playerId, JsonObject params, CoreAction action) {
        MinecraftServer srv = server;
        if (srv == null) {
            return LinkActionResult.error(LinkActionResult.Error.INTERNAL, "server not available");
        }
        ServerPlayer player = srv.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return LinkActionResult.error(LinkActionResult.Error.PLAYER_OFFLINE_REQUIRED,
                    "you must be online to operate a reactor");
        }
        if (params == null || !params.has("dim") || !params.has("x") || !params.has("y") || !params.has("z")) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION, "dim, x, y and z are required");
        }
        Identifier dimId;
        try {
            dimId = Identifier.parse(params.get("dim").getAsString());
        } catch (RuntimeException e) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION, "invalid dimension id");
        }
        ServerLevel level = srv.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        if (level == null) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION, "unknown dimension");
        }
        BlockPos pos;
        try {
            pos = new BlockPos(params.get("x").getAsInt(), params.get("y").getAsInt(), params.get("z").getAsInt());
        } catch (RuntimeException e) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION, "x, y and z must be integers");
        }
        if (!level.hasChunkAt(pos)) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION, "target chunk is not loaded");
        }
        if (!(level.getBlockEntity(pos) instanceof FissionCoreBlockEntity core)) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION, "no fission core there");
        }
        Optional<UUID> owner = core.owner();
        BlockPos at = player.blockPosition();
        boolean inProximity = player.level().dimension().equals(level.dimension())
                && LinkScope.withinProximity(at.getX(), at.getY(), at.getZ(), pos.getX(), pos.getY(), pos.getZ());
        // The protection seam only matters for an unowned core; an owned one is its owner's alone.
        boolean mayInteract = owner.isEmpty() && inProximity && Protection.get().mayInteract(player, level, pos);
        if (!LinkScope.mayAct(owner, playerId, inProximity, mayInteract)) {
            return LinkActionResult.error(LinkActionResult.Error.NOT_OWNER, owner.isPresent()
                    ? "you do not own this reactor"
                    : "stand within " + LinkScope.PROXIMITY_BLOCKS + " blocks of an unowned reactor you may use");
        }
        return action.apply(level, core);
    }

    private static JsonObject coreState(ServerLevel level, FissionCoreBlockEntity core) {
        JsonObject state = new JsonObject();
        BlockPos pos = core.getBlockPos();
        state.addProperty("dim", level.dimension().identifier().toString());
        state.addProperty("x", pos.getX());
        state.addProperty("y", pos.getY());
        state.addProperty("z", pos.getZ());
        state.addProperty("failureStage", core.failureStage().name());
        state.addProperty("scram", core.scrammed());
        return state;
    }

    // --- live events -----------------------------------------------------------------

    /**
     * Forward a NeroPower failure-stage crossing from NeroTech's machine-failure channel as
     * player-targeted {@code failure} events (see {@link #failureRecipients}): the non-personal scope
     * string, the stage and direction. Other publishers' crossings (NeroTech's own fusion reactor,
     * other channels) are ignored, and nothing is ever broadcast.
     */
    static void forwardFailureCrossing(ThresholdEvents.ThresholdCrossing crossing) {
        if (!MachineFailureEvents.CHANNEL.equals(crossing.channel()) || !LinkScope.isNeroPowerScope(crossing.scope())) {
            return;
        }
        for (UUID recipient : failureRecipients(crossing.scope())) {
            NeroLinkRegistry.eventBus().publish(LinkEvent.forPlayer(MODULE_ID, TOPIC_FAILURE, recipient,
                    failurePayload(crossing)));
        }
    }

    /**
     * Who a failure event is for: resolve the block entity at the scope's position (only if its
     * chunk is loaded — never force-loaded). An owned machine ({@link #ownerOf}) → its owner alone,
     * online or not (the bridge may notify a closed app). Otherwise — unowned, or the block already
     * gone (a FAILURE removes it) — every online player in that dimension within
     * {@value LinkScope#PROXIMITY_BLOCKS} blocks: people who could see or hear it anyway. No server,
     * a malformed scope or an unknown dimension → nobody.
     */
    private static List<UUID> failureRecipients(String scope) {
        MinecraftServer srv = server;
        String dim = LinkScope.dimensionOf(scope);
        OptionalLong packed = LinkScope.packedPosOf(scope);
        if (srv == null || dim.isEmpty() || packed.isEmpty()) {
            return List.of();
        }
        Identifier dimId;
        try {
            dimId = Identifier.parse(dim);
        } catch (RuntimeException e) {
            return List.of();
        }
        ServerLevel level = srv.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        if (level == null) {
            return List.of();
        }
        BlockPos pos = BlockPos.of(packed.getAsLong());
        if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof NeroPowerMachineBlockEntity machine) {
            Optional<UUID> owner = ownerOf(machine);
            if (owner.isPresent()) {
                return List.of(owner.get());
            }
        }
        List<UUID> nearby = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            BlockPos at = player.blockPosition();
            if (LinkScope.withinProximity(at.getX(), at.getY(), at.getZ(), pos.getX(), pos.getY(), pos.getZ())) {
                nearby.add(player.getUUID());
            }
        }
        return nearby;
    }

    /** The {@code failure} event payload: scope (machine + place), machine id, stage, rising. Pure; tested. */
    static JsonObject failurePayload(ThresholdEvents.ThresholdCrossing crossing) {
        JsonObject payload = new JsonObject();
        payload.addProperty("scope", crossing.scope());
        payload.addProperty("machineId", LinkScope.machineIdOf(crossing.scope()));
        payload.addProperty("stage", crossing.value());
        payload.addProperty("rising", crossing.rising());
        return payload;
    }
}
