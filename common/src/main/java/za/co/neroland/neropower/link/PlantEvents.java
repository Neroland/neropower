package za.co.neroland.neropower.link;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkEvent;

import za.co.neroland.nerotech.api.MachineFailureEvents;

/**
 * The pure half of link schema 2's {@code failure} events: who gets one and what it says.
 *
 * <p><b>Recipients.</b> Online players in the machine's dimension within
 * {@value LinkScope#PROXIMITY_BLOCKS} blocks of it at that moment, filtered by the owner rule (an owned
 * machine is only its owner's business). On top of that, an <i>already recorded opt-in owner</i>
 * (NeroTech's per-player attribution owner — never the beam link owner) gets a push-eligible copy when
 * the machine rises into UNSTABLE or FAILURE, wherever they are. No other store and no new opt-in.
 *
 * <p><b>Push.</b> Only the push-eligible copy rides topic {@link #TOPIC_FAILURE} with {@code _notify}
 * text. Every other copy rides topic {@link #BYSTANDER_TOPIC} with {@code event} =
 * {@link #BYSTANDER_VERB}: the bridge pushes an {@code alerts} event only when its verb is absent or
 * {@code raised}, so such a copy reaches a live socket and is never a phone push. The bridge also pushes
 * only for players who switched the {@code neropower} category on (off by default).
 *
 * <p><b>Body.</b> {@code type} (machine type id), {@code machineId} (the same, schema 1's name),
 * {@code stage}, {@code rising}. No place, no handle, no player.
 */
final class PlantEvents {

    static final String TOPIC_FAILURE = "failure";
    static final String BYSTANDER_TOPIC = "alerts";
    static final String BYSTANDER_VERB = "nearby";

    private PlantEvents() {
    }

    /** A nearby online player as the module saw them. */
    record Nearby(UUID id, int x, int y, int z) {
    }

    /** One event to publish. */
    record Delivery(UUID recipient, boolean push) {
    }

    /** Whether a transition is one the owner may be pushed about. */
    static boolean pushWorthy(int stage, boolean rising) {
        return rising && stage >= MachineFailureEvents.STAGE_UNSTABLE;
    }

    /**
     * Who hears about a transition of a machine at {@code (x, y, z)}.
     *
     * @param visibilityOwner the owner used for scoping (link owner or attribution owner)
     * @param pushOwner       the recorded opt-in owner (NeroTech attribution), or null
     */
    static List<Delivery> recipients(List<Nearby> sameDimension, int x, int y, int z,
            Optional<UUID> visibilityOwner, @Nullable UUID pushOwner, int stage, boolean rising) {
        Set<UUID> live = new LinkedHashSet<>();
        for (Nearby p : sameDimension) {
            if (p.id() != null && LinkScope.withinProximity(p.x(), p.y(), p.z(), x, y, z)
                    && LinkScope.visibleTo(visibilityOwner, p.id())) {
                live.add(p.id());
            }
        }
        boolean push = pushOwner != null && pushWorthy(stage, rising);
        List<Delivery> out = new java.util.ArrayList<>(live.size() + 1);
        for (UUID id : live) {
            out.add(new Delivery(id, push && id.equals(pushOwner)));
        }
        if (push && !live.contains(pushOwner)) {
            out.add(new Delivery(pushOwner, true));
        }
        return out;
    }

    /** The event body: type, machineId, stage, rising. Pure. */
    static JsonObject body(String type, int stage, boolean rising) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", type);
        payload.addProperty("machineId", type);
        payload.addProperty("stage", stage);
        payload.addProperty("rising", rising);
        return payload;
    }

    /** The event for one delivery: the push-eligible owner copy, or a never-pushed live notice. */
    static LinkEvent event(String moduleId, Delivery delivery, String type, int stage, boolean rising) {
        JsonObject payload = body(type, stage, rising);
        if (!delivery.push()) {
            JsonObject notice = new JsonObject();
            notice.addProperty("event", BYSTANDER_VERB);
            notice.addProperty("source", TOPIC_FAILURE);
            for (var e : payload.entrySet()) {
                notice.add(e.getKey(), e.getValue().deepCopy());
            }
            return LinkEvent.forPlayer(moduleId, BYSTANDER_TOPIC, delivery.recipient(), notice);
        }
        JsonObject notify = new JsonObject();
        notify.addProperty("title", "NeroPower alarm");
        notify.addProperty("body", readable(type) + ": " + stageWord(stage));
        payload.add("_notify", notify);
        return LinkEvent.forPlayer(moduleId, TOPIC_FAILURE, delivery.recipient(), payload);
    }

    /** {@code neropower:fission_core} to {@code Fission core}. */
    static String readable(String id) {
        int colon = id == null ? -1 : id.indexOf(':');
        String path = id == null ? "machine" : (colon >= 0 ? id.substring(colon + 1) : id);
        path = path.replace('_', ' ').trim();
        if (path.isEmpty()) {
            return "Machine";
        }
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    static String stageWord(int stage) {
        return switch (stage) {
            case MachineFailureEvents.STAGE_WARNING -> "warning";
            case MachineFailureEvents.STAGE_UNSTABLE -> "unstable";
            case MachineFailureEvents.STAGE_FAILURE -> "failing";
            case MachineFailureEvents.STAGE_BREACH -> "breach";
            default -> "stable";
        };
    }
}
