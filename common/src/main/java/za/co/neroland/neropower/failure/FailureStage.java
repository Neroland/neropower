package za.co.neroland.neropower.failure;

import za.co.neroland.nerotech.api.MachineFailureEvents;

/**
 * The four rungs of NeroPower's failure ladder, worst last. A machine climbs one rung at a time (a
 * stage is never skipped — see {@link FailureController}) and each rung's {@link #code()} is the
 * stage int NeroTech's {@link MachineFailureEvents} publishes, so a listener on the ecosystem bus
 * hears NeroPower's reactors and NeroTech's Fusion Reactor in the same vocabulary.
 */
public enum FailureStage {

    /** Normal operation: below the warning threshold. Not a NeroTech stage (code 0 is never fired). */
    STABLE(0),
    /** Alarm state: hot, still at full output. {@link MachineFailureEvents#STAGE_WARNING}. */
    WARNING(MachineFailureEvents.STAGE_WARNING),
    /** Throttled: output scaled by the unstable penalty. {@link MachineFailureEvents#STAGE_UNSTABLE}. */
    UNSTABLE(MachineFailureEvents.STAGE_UNSTABLE),
    /** The machine is destroying itself; its {@link FailureAction} runs. {@link MachineFailureEvents#STAGE_FAILURE}. */
    FAILURE(MachineFailureEvents.STAGE_FAILURE);

    private final int code;

    FailureStage(int code) {
        this.code = code;
    }

    /** The stage int on NeroTech's machine-failure channel (STABLE = 0, WARNING = 1, UNSTABLE = 2, FAILURE = 3). */
    public int code() {
        return this.code;
    }

    /** The next rung up, or {@code null} from {@link #FAILURE}. */
    public FailureStage next() {
        return this == FAILURE ? null : values()[ordinal() + 1];
    }

    /** The next rung down, or {@code null} from {@link #STABLE}. */
    public FailureStage previous() {
        return this == STABLE ? null : values()[ordinal() - 1];
    }

    /** The stage with this {@link #code()}; anything out of range reads as {@link #STABLE}. */
    public static FailureStage byCode(int code) {
        for (FailureStage stage : values()) {
            if (stage.code == code) {
                return stage;
            }
        }
        return STABLE;
    }

    /** Translation key for the stage's display name ({@code neropower.failure.stage.<name>}). */
    public String translationKey() {
        return "neropower.failure.stage." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
