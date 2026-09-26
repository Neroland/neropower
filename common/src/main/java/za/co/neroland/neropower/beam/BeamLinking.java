package za.co.neroland.neropower.beam;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerolandcore.worldgen.SpaceTags;

import za.co.neroland.neropower.protection.Protection;

/**
 * The Configurator link flow for beam endpoints (server side only; the block routes the click here).
 * Two clicks make a link: the first on a transmitter or relay stores it as the pending source in
 * {@link BeamLinkSession} (keyed by the acting player, transient, 30 s), the second on a receiver
 * or relay completes it. Clicking a linked transmitter / relay with nothing pending unlinks it.
 *
 * <p><b>Authorisation</b> ({@link BeamLinkRules}): the acting player — always the one holding the
 * wrench, never a nearest-player lookup — must pass {@link Protection#get()}{@code .mayInteract} at
 * <i>both</i> endpoints, both must be loaded, and unlinking (or re-aiming) an existing link is reserved for the
 * player who made it ({@link BeamTransmitterBlockEntity#linkOwner()}) or a gamemaster (permission
 * level 2, the same predicate NeroTech's Configurator uses). Every refusal is
 * {@code neropower.beam.link_denied}; feedback is actionbar-only. The UUID is compared, never
 * logged or shown (POPIA/GDPR).
 */
public final class BeamLinking {

    private BeamLinking() {
    }

    /** Handle a configure-mode wrench click on {@code endpoint} at {@code pos} by {@code player}. */
    public static void handle(ServerLevel level, ServerPlayer player, BlockPos pos,
            BeamEndpointBlockEntity endpoint) {
        long now = level.getGameTime();
        String dimension = BeamTarget.dimensionId(level);
        UUID actor = player.getUUID();
        BeamLinkSession session = BeamLinkSession.INSTANCE;

        if (!Protection.get().mayInteract(player, level, pos)) {
            tell(player, "neropower.beam.link_denied");
            return;
        }
        Optional<BeamLinkSession.Pending> pending = session.peek(actor, now);
        if (pending.isPresent() && pending.get().at(dimension, pos.getX(), pos.getY(), pos.getZ())) {
            // Same block twice: keep the pending end so the player can simply click the other one.
            tell(player, "neropower.beam.link.same_block");
            return;
        }
        if (pending.isPresent() && endpoint.acceptsBeam()) {
            complete(level, player, pending.get(), pos, endpoint);
            return;
        }
        if (endpoint instanceof BeamTransmitterBlockEntity source) {
            if (source.linked() && pending.isEmpty()) {
                if (!mayRewire(player, source)) {
                    tell(player, "neropower.beam.link_denied");
                    return;
                }
                source.unlink();
                tell(player, "neropower.beam.link.cleared");
                return;
            }
            session.begin(actor, dimension, pos.getX(), pos.getY(), pos.getZ(), now);
            tell(player, "neropower.beam.link.stored");
            return;
        }
        tell(player, "neropower.beam.link.no_source");
    }

    /** Second click: aim the pending source at {@code targetPos} in {@code level}. */
    private static void complete(ServerLevel level, ServerPlayer player, BeamLinkSession.Pending pending,
            BlockPos targetPos, BeamEndpointBlockEntity target) {
        BeamLinkSession session = BeamLinkSession.INSTANCE;
        UUID actor = player.getUUID();
        String dimension = BeamTarget.dimensionId(level);
        boolean sameDimension = pending.dimension().equals(dimension);

        // Gather the facts, then let the pure rule decide (BeamLinkRules): dimension, loaded, authorised.
        ServerLevel sourceLevel = sameDimension ? level : level.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, Identifier.parse(pending.dimension())));
        BlockPos sourcePos = new BlockPos(pending.x(), pending.y(), pending.z());
        BeamTransmitterBlockEntity source = sourceLevel != null && sourceLevel.hasChunkAt(sourcePos)
                && sourceLevel.getBlockEntity(sourcePos) instanceof BeamTransmitterBlockEntity transmitter
                ? transmitter : null;
        boolean sourceLoaded = source != null;
        boolean mayInteractSource = sourceLoaded && Protection.get().mayInteract(player, sourceLevel, sourcePos);
        boolean mayInteractTarget = Protection.get().mayInteract(player, level, targetPos);
        BeamLinkRules.Decision decision = BeamLinkRules.decide(sourceLoaded, target.acceptsBeam(),
                mayInteractSource, mayInteractTarget, sameDimension, BeamConfig.beamCrossDimension(),
                target.orbital(), SpaceTags.isSpace(level));
        if (decision == BeamLinkRules.Decision.CROSS_DIMENSION) {
            // Only an orbital receiver in a space dimension may be aimed at from another world.
            tell(player, "neropower.beam.link.cross_dimension");
            return;
        }
        if (decision == BeamLinkRules.Decision.SOURCE_GONE || source == null) {
            // Source gone (or asleep) — drop the stale pending end rather than link blind.
            session.clear(actor);
            tell(player, "neropower.beam.link.source_gone");
            return;
        }
        if (decision == BeamLinkRules.Decision.DENIED) {
            // Keep the pending end: the player may simply have clicked a block that isn't theirs.
            tell(player, "neropower.beam.link_denied");
            return;
        }
        if (source.linked() && !mayRewire(player, source)) {
            tell(player, "neropower.beam.link_denied");
            return;
        }
        if (sameDimension && !BeamMath.withinRange(sourcePos.getX(), sourcePos.getY(), sourcePos.getZ(),
                targetPos.getX(), targetPos.getY(), targetPos.getZ(), BeamConfig.beamRange())) {
            tell(player, "neropower.beam.link.too_far", BeamConfig.beamRange());
            return;
        }
        source.link(BeamTarget.of(targetPos, dimension), actor);
        session.clear(actor);
        tell(player, "neropower.beam.link.linked");
    }

    /**
     * Whether {@code player} may drop or replace {@code source}'s current link: the link's owner, a
     * gamemaster, or anyone when no owner is recorded (erased, or a pre-ownership link).
     */
    private static boolean mayRewire(ServerPlayer player, BeamTransmitterBlockEntity source) {
        Optional<UUID> owner = source.linkOwner();
        return owner.isEmpty() || owner.get().equals(player.getUUID()) || isGamemaster(player);
    }

    /** Gamemaster (permission level 2) check, through the same predicate Core's commands gate on. */
    private static boolean isGamemaster(ServerPlayer player) {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack());
    }

    /** Actionbar feedback — transient, so linking in bulk does not flood the chat. */
    private static void tell(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args), true);
    }
}
