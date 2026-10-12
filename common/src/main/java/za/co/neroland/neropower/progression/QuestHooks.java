package za.co.neroland.neropower.progression;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.event.CoreEvents;
import za.co.neroland.nerolandcore.progression.GateScope;
import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.fission.FissionContent;

/**
 * Listener for the <b>hook gates</b> a quest mod opens when a player completes one of NeroPower's
 * own quests ({@code data/neropower/neroland_gates/hook/*.json}, named by the pack's
 * {@code mod_hook} rewards). NeroPower never imports the quest mod: it only hears Core's progression
 * events.
 *
 * <p>{@code neropower:hook/fuel_rod_refill} — hands the player one <b>fresh Fuel Rod</b>: a new
 * item, never a rod already in a reactor, so no burning rod's components are touched.
 *
 * <p>Delivery is bounded and idempotent: one rod per hook opening, after which the gate is closed
 * again to re-arm it. A hook that opened while the player was offline is delivered on their next
 * login ({@link #onJoin}); logins are detected loader-free from the server tick ({@link #tick}).
 *
 * <p><b>Privacy (POPIA/GDPR):</b> the gate change's target is the player's UUID string; it is used
 * only to find the online player and to close the gate, never logged or broadcast. Hook gates are
 * records that gate nothing and are never checked for access. The online-player set kept for join
 * detection is process-local, holds UUIDs only and is dropped when the server changes.
 */
public final class QuestHooks {

    /** The hook gate the "Reactor Log" pack rewards with a fresh Fuel Rod. */
    public static final Identifier FUEL_ROD_REFILL =
            Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "hook/fuel_rod_refill");

    /** Login sweep cadence (ticks). */
    private static final int JOIN_SWEEP_INTERVAL = 20;

    /** The running server, captured from the server tick so the listener can resolve online players. */
    private static volatile @Nullable MinecraftServer server;
    /** Players seen online at the last sweep — a join is a UUID that was not in it. Process-local. */
    private static final Set<UUID> ONLINE = new HashSet<>();
    private static @Nullable MinecraftServer sweptServer;

    private QuestHooks() {
    }

    /** Register the progression listener; call once from common init. */
    public static void init() {
        CoreEvents.onProgression(change -> {
            if (!change.open() || change.scope() != GateScope.PLAYER || !FUEL_ROD_REFILL.equals(change.gate())) {
                return;
            }
            MinecraftServer running = server;
            if (running == null) {
                return; // no server yet: the gate stays open and onJoin delivers it
            }
            UUID id;
            try {
                id = UUID.fromString(change.target());
            } catch (IllegalArgumentException e) {
                return;
            }
            ServerPlayer player = running.getPlayerList().getPlayer(id);
            if (player == null) {
                return; // offline: the gate stays open, see onJoin
            }
            giveFuelRod(player);
            ProgressionGates.close(running, id, FUEL_ROD_REFILL); // re-arm; the open=false change is ignored above
        });
    }

    /**
     * Server tick: remember the server and, once a second, deliver pending hooks to players who
     * logged in since the last sweep. Cheap — a set diff over the online player list.
     */
    public static void tick(MinecraftServer running) {
        server = running;
        if (running.getTickCount() % JOIN_SWEEP_INTERVAL != 0) {
            return;
        }
        if (sweptServer != running) {
            ONLINE.clear(); // a new server in this process (singleplayer world switch) starts fresh
            sweptServer = running;
        }
        Set<UUID> now = new HashSet<>();
        for (ServerPlayer player : running.getPlayerList().getPlayers()) {
            now.add(player.getUUID());
            if (ONLINE.add(player.getUUID())) {
                onJoin(player);
            }
        }
        ONLINE.retainAll(now);
    }

    /** On login: deliver a hook that opened while the player was offline. */
    public static void onJoin(ServerPlayer player) {
        if (ProgressionGates.isOpen(player, FUEL_ROD_REFILL)) {
            giveFuelRod(player);
            ProgressionGates.close(player, FUEL_ROD_REFILL);
        }
    }

    /** One fresh Fuel Rod into the player's inventory (dropped at their feet when it is full), and a chat line. */
    static void giveFuelRod(ServerPlayer player) {
        ItemStack rod = new ItemStack(FissionContent.FUEL_ROD.get());
        //? if >=26.3 {
        /*player.getInventory().placeItemBackInInventory(rod, net.minecraft.util.Prediction.SERVER_ONLY);
        *///?} else {
        player.getInventory().placeItemBackInInventory(rod);
        //?}
        player.sendSystemMessage(Component.translatable("message.neropower.fuel_rod_refill"));
    }
}
