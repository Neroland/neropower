package za.co.neroland.neropower.progression;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * The pure decisions behind operator-in-the-moment credit: when a NeroPower machine event credits the
 * player operating it with a milestone gate (and so any trophy keyed on that gate). No world access, so
 * they are unit-tested directly; {@link MilestoneGates#credit} then checks the player is online and not
 * opted out, and opens the gate idempotently.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> nothing here stores a player. The operator is NeroTech's transient
 * {@code lastOperator} (never saved, never synced, empty for a gallery exhibit); the "already tried"
 * operator a machine keeps to avoid re-running the gate lookup every tick is a transient field of the
 * loaded block entity, never saved, and only ever compared for equality.
 */
public final class OperatorCredit {

    private OperatorCredit() {
    }

    /**
     * Whether a machine should try to credit {@code operator} now: the milestone condition holds, an
     * operator is known, and it is not the operator this loaded machine already credited.
     */
    public static boolean shouldTry(boolean condition, @Nullable UUID operator, @Nullable UUID alreadyTried) {
        return condition && operator != null && !operator.equals(alreadyTried);
    }

    /** The first known operator of a multiblock's parts, in the order given (e.g. the core, then its computer). */
    @Nullable
    public static UUID firstKnown(@Nullable UUID... candidates) {
        if (candidates == null) {
            return null;
        }
        for (UUID candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Criticality: a formed reactor is actually fissioning — at least one rod aged this tick
     * ({@code burnupDelta > 0}). Crafting or forming the core is not criticality.
     */
    public static boolean critical(boolean formed, int burnupDelta) {
        return formed && burnupDelta > 0;
    }

    /** Turbine spin-up: a formed turbine delivers power at or above rated speed. */
    public static boolean spunUp(long output, int rpm, int ratedRpm) {
        return output > 0L && rpm >= ratedRpm;
    }

    /** Bank capacity: a formed bank's pooled capacity reached the configured milestone. */
    public static boolean bankCapacity(boolean formed, long capacity, long milestone) {
        return formed && capacity >= milestone;
    }

    /** Storage ring charged: a formed SMES ring holds its full capacity. */
    public static boolean ringCharged(boolean formed, long energy, long capacity) {
        return formed && capacity > 0L && energy >= capacity;
    }
}
