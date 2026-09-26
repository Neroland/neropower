package za.co.neroland.neropower.failure;

import org.jetbrains.annotations.Nullable;

/**
 * The staged failure state machine every NeroPower reactor owns — a pure-Java ladder driven by heat,
 * with no Minecraft types in it so the balance is unit-testable. The owning block entity calls
 * {@link #tick} once per server tick with its current heat and reacts to the returned stage (see
 * {@code NeroPowerMachineBlockEntity.tickFailure}); telegraphing, events and the failure action live
 * outside this class.
 *
 * <p>Rules, in the order they are applied each tick:
 * <ol>
 *   <li><b>Rising</b>: heat permille at or above the <i>next</i> stage's entry threshold <i>and</i>
 *       the current stage held for at least {@code minDwellTicks} climbs exactly one rung. A stage is
 *       never skipped, however fast the heat spikes — every escalation is visible.</li>
 *   <li><b>Falling</b>: otherwise, heat permille below the current stage's exit threshold (entry minus
 *       hysteresis) drops exactly one rung; no dwell applies to cooling.</li>
 *   <li><b>Clamp</b>: with overload disabled the ladder tops out at {@link FailureStage#UNSTABLE} —
 *       the machine throttles and alarms but never fails; a persisted FAILURE reads back as UNSTABLE.</li>
 * </ol>
 * At most one transition per tick, so a listener sees every rung in order.
 */
public final class FailureController {

    /** Notified once per transition, after the new stage is visible through {@link #stage()}. */
    @FunctionalInterface
    public interface Listener {
        void onStageChanged(FailureStage from, FailureStage to);
    }

    private final FailureThresholds thresholds;
    private FailureStage stage = FailureStage.STABLE;
    private int ticksInStage;
    private boolean justEntered;
    @Nullable
    private Listener listener;

    public FailureController(FailureThresholds thresholds) {
        if (thresholds == null) {
            throw new IllegalArgumentException("FailureController needs thresholds");
        }
        this.thresholds = thresholds;
    }

    /** The ladder this controller walks. */
    public FailureThresholds thresholds() {
        return this.thresholds;
    }

    /** Subscribe to transitions (one listener; {@code null} clears it). */
    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /**
     * Advance the ladder by one tick.
     *
     * @param heat            the machine's current heat
     * @param heatCapacity    the machine's heat ceiling ({@code <= 0} reads as 0 heat)
     * @param overloadEnabled {@code false} pins the ladder at {@link FailureStage#UNSTABLE}
     * @return the stage after this tick
     */
    public FailureStage tick(int heat, int heatCapacity, boolean overloadEnabled) {
        this.justEntered = false;
        // A persisted FAILURE with overload since disabled: back off to the pinned rung at once.
        if (!overloadEnabled && this.stage == FailureStage.FAILURE) {
            transition(FailureStage.UNSTABLE);
            return this.stage;
        }
        this.ticksInStage++;
        int permille = permille(heat, heatCapacity);

        FailureStage next = this.stage.next();
        if (next != null
                && permille >= this.thresholds.entryPermille(next)
                && this.ticksInStage >= this.thresholds.minDwellTicks()
                && (overloadEnabled || next != FailureStage.FAILURE)) {
            transition(next);
            return this.stage;
        }

        FailureStage previous = this.stage.previous();
        if (previous != null && permille < this.thresholds.exitPermille(this.stage)) {
            transition(previous);
        }
        return this.stage;
    }

    private void transition(FailureStage to) {
        FailureStage from = this.stage;
        this.stage = to;
        this.ticksInStage = 0;
        this.justEntered = true;
        if (this.listener != null) {
            this.listener.onStageChanged(from, to);
        }
    }

    /** Heat as permille of capacity, never negative; a non-positive capacity reads as 0. */
    static int permille(int heat, int heatCapacity) {
        if (heatCapacity <= 0 || heat <= 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, (long) heat * FailureThresholds.PERMILLE / heatCapacity);
    }

    /** The current rung. */
    public FailureStage stage() {
        return this.stage;
    }

    /** Ticks spent on the current rung since it was entered (counts the ticks evaluated in it). */
    public int ticksInStage() {
        return this.ticksInStage;
    }

    /** True only on the tick a transition happened (rising or falling). */
    public boolean justEntered() {
        return this.justEntered;
    }

    /**
     * The output multiplier for the current rung: full ({@link FailureThresholds#PERMILLE}) in STABLE
     * and WARNING, the configured penalty in UNSTABLE, 0 in FAILURE.
     */
    public int outputPermille() {
        return switch (this.stage) {
            case STABLE, WARNING -> FailureThresholds.PERMILLE;
            case UNSTABLE -> this.thresholds.unstablePenaltyPermille();
            case FAILURE -> 0;
        };
    }

    // --- persistence (two ints; the owner writes them to NBT) ----------------------

    /** The stage {@link FailureStage#code()} to persist. */
    public int saveStage() {
        return this.stage.code();
    }

    /** The dwell counter to persist. */
    public int saveTicksInStage() {
        return this.ticksInStage;
    }

    /**
     * Restore from persisted ints. Silent — no listener call, no {@link #justEntered()}: a reload is
     * not a transition. An unknown stage code reads as STABLE; a negative dwell as 0.
     */
    public void load(int stageCode, int ticksInStage) {
        this.stage = FailureStage.byCode(stageCode);
        this.ticksInStage = Math.max(0, ticksInStage);
        this.justEntered = false;
    }
}
