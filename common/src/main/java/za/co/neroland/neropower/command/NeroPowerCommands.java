package za.co.neroland.neropower.command;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import za.co.neroland.neropower.NeroPowerCommon;

/**
 * {@code /neropower}: the creative gallery and the operator tick-budget probe. Player-only and
 * gamemaster-gated (the same predicate Core's commands use); the gallery subcommands also need creative.
 * <ul>
 *   <li>{@code /neropower gallery} builds the walkable power station of {@link GalleryPlan} at the caller's
 *       feet ({@link GalleryStation#build});</li>
 *   <li>{@code /neropower gallery tour [stop]} teleports the caller to a tour stop and shows its caption;</li>
 *   <li>{@code /neropower gallery clear} removes the station ({@link GalleryStation#clear});</li>
 *   <li>{@code /neropower budget start [ticks]} and {@code /neropower budget}: the tick-budget probe.</li>
 * </ul>
 *
 * <p><b>Privacy (POPIA/GDPR):</b> the gallery records no player: its beams are linked under
 * {@link #GALLERY_LINK_TOKEN}, a random per-process token that matches no player, never the operator's UUID,
 * and every machine is an exhibit with no owner or operator. The tour keeps no per-player counter: each
 * stop's caption names the command for the next one. The only thing remembered is where the gallery in each
 * dimension was built (a block position, world data, memory only, tied to the running server), so the tour and
 * {@code clear} find it; after a restart {@code clear} finds the gallery from its own exhibits near the
 * operator, or refuses. One gallery per dimension.
 */
public final class NeroPowerCommands {

    /**
     * The link owner of gallery exhibit beams: a random token made once per process, so an exhibit records
     * no player. It authorises nothing anyone can use; the gallery is gamemaster-only.
     */
    static final java.util.UUID GALLERY_LINK_TOKEN = java.util.UUID.randomUUID();

    /** Where the gallery in a dimension was built, and on which running server (world data; memory only). */
    private record Origin(MinecraftServer server, BlockPos pos) {
    }

    /** Where the gallery in each dimension was built (world data; memory only, lost on restart). */
    private static final Map<ResourceKey<Level>, Origin> ORIGINS = new ConcurrentHashMap<>();

    /** Chunks around the operator searched for a gallery whose origin is not remembered. */
    static final int LOCATE_CHUNK_RADIUS = 6;

    /** The remembered origin in {@code level}'s dimension on this server, or null (a stale entry is dropped). */
    private static BlockPos origin(ServerLevel level) {
        Origin o = ORIGINS.get(level.dimension());
        if (o == null) {
            return null;
        }
        if (o.server() != level.getServer()) {
            ORIGINS.remove(level.dimension(), o); // another world or an earlier server in this process
            return null;
        }
        return o.pos();
    }

    private NeroPowerCommands() {
    }

    /**
     * Cross-loader registration: each loader calls this from its command hook (Forge/NeoForge
     * {@code RegisterCommandsEvent}, Fabric {@code CommandRegistrationCallback}) with the dispatcher.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        int stops = GalleryPlan.create(true).tourStops().size();
        dispatcher.register(
                Commands.literal("neropower")
                        .requires(src -> src.getPlayer() != null
                                //? if >=1.21.6 {
                                && Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(src))
                                //?} else {
                                /*&& src.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                *///?}
                        .then(Commands.literal("gallery")
                                .executes(ctx -> runSafely(ctx.getSource(), "gallery",
                                        () -> buildGallery(ctx.getSource())))
                                .then(Commands.literal("clear")
                                        .executes(ctx -> runSafely(ctx.getSource(), "gallery clear",
                                                () -> clearGallery(ctx.getSource()))))
                                .then(Commands.literal("tour")
                                        .executes(ctx -> runSafely(ctx.getSource(), "gallery tour",
                                                () -> tour(ctx.getSource(), 1)))
                                        .then(Commands.argument("stop", IntegerArgumentType.integer(1, stops))
                                                .executes(ctx -> runSafely(ctx.getSource(), "gallery tour",
                                                        () -> tour(ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "stop")))))))
                        // Operator tick-time probe (same gamemaster gate; works in survival too).
                        .then(Commands.literal("budget")
                                .executes(ctx -> budgetReport(ctx.getSource()))
                                .then(Commands.literal("start")
                                        .executes(ctx -> budgetStart(ctx.getSource(), BudgetProbe.DEFAULT_TICKS))
                                        .then(Commands.argument("ticks",
                                                        IntegerArgumentType.integer(1, BudgetProbe.MAX_TICKS))
                                                .executes(ctx -> budgetStart(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "ticks")))))));
    }

    /** Open a sampling window. */
    private static int budgetStart(CommandSourceStack source, int ticks) {
        BudgetProbe.start(ticks);
        source.sendSuccess(() -> Component.translatable("command.neropower.budget.started", ticks), false);
        return 1;
    }

    /** Show ms per tick per block-entity type and the total for the current or last window. */
    private static int budgetReport(CommandSourceStack source) {
        BudgetProbe.Report report = BudgetProbe.report();
        if (report.ticks() == 0) {
            source.sendSuccess(() -> Component.translatable("command.neropower.budget.none"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(report.running()
                ? "command.neropower.budget.header_running" : "command.neropower.budget.header",
                report.ticks(), String.format(java.util.Locale.ROOT, "%.3f", report.totalMsPerTick())), false);
        for (BudgetProbe.Row row : report.rows()) {
            source.sendSuccess(() -> Component.translatable("command.neropower.budget.row", row.type(),
                    String.format(java.util.Locale.ROOT, "%.3f", row.msPerTick()),
                    String.format(java.util.Locale.ROOT, "%.1f", row.callsPerTick())), false);
        }
        return report.rows().size();
    }

    private static int runSafely(CommandSourceStack source, String commandName, CommandBody body) {
        try {
            return body.run();
        } catch (RuntimeException ex) {
            NeroPowerCommon.LOGGER.error("[NeroPower] /neropower {} failed", commandName, ex);
            source.sendFailure(Component.translatable("command.neropower.gallery.failed", commandName));
            return 0;
        }
    }

    @FunctionalInterface
    private interface CommandBody {
        int run();
    }

    /** The creative player running a gallery command, or null after telling them why not. */
    private static ServerPlayer creativePlayer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.neropower.gallery.player_only"));
            return null;
        }
        if (!player.getAbilities().instabuild) {
            source.sendFailure(Component.translatable("command.neropower.gallery.creative_only"));
            return null;
        }
        return player;
    }

    private static ServerLevel levelOf(ServerPlayer player) {
        //? if >=1.21.6 {
        return player.level();
        //?} else {
        /*return player.serverLevel();
        *///?}
    }

    /** Build the station at the player's feet. */
    private static int buildGallery(CommandSourceStack source) {
        ServerPlayer player = creativePlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = levelOf(player);
        if (origin(level) != null) {
            source.sendFailure(Component.translatable("command.neropower.gallery.exists"));
            return 0;
        }
        BlockPos origin = player.blockPosition();
        if (!GalleryStation.boxLoaded(level, origin)) {
            source.sendFailure(Component.translatable("command.neropower.gallery.not_loaded"));
            return 0;
        }
        ORIGINS.put(level.dimension(), new Origin(level.getServer(), origin.immutable()));
        GalleryStation.build(level, origin, line -> source.sendSuccess(() -> line, false));
        int[] size = GalleryPlan.clearBoxSize();
        source.sendSuccess(() -> Component.translatable("command.neropower.gallery.built"), false);
        source.sendSuccess(() -> Component.translatable("command.neropower.gallery.box", size[0], size[1], size[2]),
                false);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Remove the station: the one built in this dimension on this server, else one found from its own
     * exhibits near the player ({@link GalleryStation#locate}); with neither, refuse. Never guesses from
     * where the player stands, and removes only what the gallery placed.
     */
    private static int clearGallery(CommandSourceStack source) {
        ServerPlayer player = creativePlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = levelOf(player);
        BlockPos origin = origin(level);
        if (origin == null) {
            origin = GalleryStation.locate(level, player.blockPosition(), LOCATE_CHUNK_RADIUS);
        }
        if (origin == null) {
            source.sendFailure(Component.translatable("command.neropower.gallery.clear.none"));
            return 0;
        }
        int[] result = GalleryStation.clear(level, origin);
        ORIGINS.remove(level.dimension());
        source.sendSuccess(() -> Component.translatable("command.neropower.gallery.cleared",
                result[0], result[1]), false);
        return Command.SINGLE_SUCCESS;
    }

    /** Teleport the player to tour stop {@code n} (1-based) of the last gallery in this dimension. */
    private static int tour(CommandSourceStack source, int n) {
        ServerPlayer player = creativePlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = levelOf(player);
        BlockPos origin = origin(level);
        if (origin == null) {
            source.sendFailure(Component.translatable("command.neropower.gallery.tour.none"));
            return 0;
        }
        List<GalleryPlan.TourStop> stops = GalleryPlan.create(true).tourStops();
        GalleryPlan.TourStop stop = stops.get(Math.max(1, Math.min(stops.size(), n)) - 1);
        double x = origin.getX() + stop.dx();
        double y = origin.getY() + stop.dy();
        double z = origin.getZ() + stop.dz();
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        String tp = String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", x, y, z,
                stop.yaw(), stop.pitch());
        source.getServer().getCommands().performPrefixedCommand(source.withSuppressedOutput(), tp);
        Component caption = Component.translatable(stop.captionKey());
        player.connection.send(new ClientboundSetActionBarTextPacket(caption));
        int next = n + 1;
        source.sendSuccess(() -> caption, false);
        source.sendSuccess(() -> next <= stops.size()
                ? Component.translatable("command.neropower.gallery.tour.next", n, stops.size(),
                        "/neropower gallery tour " + next)
                : Component.translatable("command.neropower.gallery.tour.end"), false);
        return Command.SINGLE_SUCCESS;
    }
}
