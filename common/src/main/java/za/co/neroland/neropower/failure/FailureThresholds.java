package za.co.neroland.neropower.failure;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.config.NeroPowerConfig;

/**
 * The tunables of one failure ladder, validated once so {@link FailureController} can trust them:
 * the entry threshold of each stage above {@link FailureStage#STABLE} as permille of heat capacity,
 * the hysteresis a stage must cool below its own threshold before it is left, the minimum dwell
 * before the next climb, and the output penalty applied while {@link FailureStage#UNSTABLE}.
 *
 * <p>Pure data: built from config by {@link #fromConfig()} in game, or directly in tests.
 *
 * @param warningPermille        heat permille that enters {@link FailureStage#WARNING} (1..1000)
 * @param unstablePermille       heat permille that enters {@link FailureStage#UNSTABLE}; above warning
 * @param failurePermille        heat permille that enters {@link FailureStage#FAILURE}; above unstable
 * @param hysteresisPermille     permille below a stage's own threshold before it is left downwards (>= 0)
 * @param minDwellTicks          ticks a stage must be held before the next climb (>= 0)
 * @param unstablePenaltyPermille output multiplier while UNSTABLE (0..1000)
 */
public record FailureThresholds(int warningPermille, int unstablePermille, int failurePermille,
        int hysteresisPermille, int minDwellTicks, int unstablePenaltyPermille) {

    /** Full output, and the ceiling of every permille field. */
    public static final int PERMILLE = 1000;

    /**
     * Validate: {@code 0 < warning < unstable < failure <= 1000}, hysteresis in {@code 0..1000},
     * dwell {@code >= 0}, penalty in {@code 0..1000}.
     *
     * @throws IllegalArgumentException when the ladder is not strictly monotonic or a field is out of range
     */
    public FailureThresholds {
        if (warningPermille <= 0 || warningPermille > PERMILLE) {
            throw new IllegalArgumentException("warningPermille must be in 1..1000, got " + warningPermille);
        }
        if (unstablePermille <= warningPermille || unstablePermille > PERMILLE) {
            throw new IllegalArgumentException("unstablePermille must exceed warningPermille (" + warningPermille
                    + ") and be <= 1000, got " + unstablePermille);
        }
        if (failurePermille <= unstablePermille || failurePermille > PERMILLE) {
            throw new IllegalArgumentException("failurePermille must exceed unstablePermille (" + unstablePermille
                    + ") and be <= 1000, got " + failurePermille);
        }
        if (hysteresisPermille < 0 || hysteresisPermille > PERMILLE) {
            throw new IllegalArgumentException("hysteresisPermille must be in 0..1000, got " + hysteresisPermille);
        }
        if (minDwellTicks < 0) {
            throw new IllegalArgumentException("minDwellTicks must be >= 0, got " + minDwellTicks);
        }
        if (unstablePenaltyPermille < 0 || unstablePenaltyPermille > PERMILLE) {
            throw new IllegalArgumentException("unstablePenaltyPermille must be in 0..1000, got "
                    + unstablePenaltyPermille);
        }
    }

    /**
     * The heat permille that enters {@code stage}; {@link FailureStage#STABLE} has no entry threshold
     * and reads as 0 (it is entered by cooling, never by heating).
     */
    public int entryPermille(FailureStage stage) {
        return switch (stage) {
            case STABLE -> 0;
            case WARNING -> this.warningPermille;
            case UNSTABLE -> this.unstablePermille;
            case FAILURE -> this.failurePermille;
        };
    }

    /**
     * The heat permille below which {@code stage} is left downwards: its entry threshold minus the
     * hysteresis, floored at 0. {@link FailureStage#STABLE} is never left downwards (returns
     * {@code Integer.MIN_VALUE}).
     */
    public int exitPermille(FailureStage stage) {
        if (stage == FailureStage.STABLE) {
            return Integer.MIN_VALUE;
        }
        return Math.max(0, entryPermille(stage) - this.hysteresisPermille);
    }

    /** The shipped defaults (600 / 800 / 1000, 50 hysteresis, 100-tick dwell, 60% unstable output). */
    public static final FailureThresholds DEFAULTS = new FailureThresholds(600, 800, 1000, 50, 100, 600);

    /**
     * The ladder as configured in {@code neropower.properties} (read fresh each call — hot-reload
     * safe). Core validates each key's range but not the ladder's ordering; a non-monotonic ladder
     * (say {@code failureWarningPermille} above {@code failureUnstablePermille}) is logged once per
     * call and replaced by {@link #DEFAULTS} rather than crashing a machine's tick.
     */
    public static FailureThresholds fromConfig() {
        try {
            return new FailureThresholds(
                    NeroPowerConfig.failureWarningPermille(),
                    NeroPowerConfig.failureUnstablePermille(),
                    NeroPowerConfig.failureFailurePermille(),
                    NeroPowerConfig.failureHysteresisPermille(),
                    NeroPowerConfig.failureMinDwellTicks(),
                    NeroPowerConfig.unstablePenaltyPermille());
        } catch (IllegalArgumentException invalid) {
            NeroPowerCommon.LOGGER.warn("[NeroPower] failure thresholds in neropower.properties are not a "
                    + "rising ladder ({}); using the defaults", invalid.getMessage());
            return DEFAULTS;
        }
    }
}
