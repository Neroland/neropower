package za.co.neroland.neropower.fission;

/**
 * The Reactor Control Computer's rules, pure so they are testable. Every reading is permille except the
 * failure stage (0..3); no player data.
 *
 * <p><b>Signals</b>: {@link #HEAT} (heat / capacity), {@link #FUEL} (fuel left in the most burnt usable
 * rod), {@link #OUTPUT} (power or steam now against the core's rated output) and {@link #STAGE}. Each has
 * a threshold; a signal is <i>on</i> at or past it (fuel at or <i>below</i>, since it warns of running
 * out). The redstone output is 15 while the chosen signal is on ({@link #OFF} = never); a comparator
 * reads the chosen signal as a level 0..15 (stage as 0 / 5 / 10 / 15).
 *
 * <p><b>Auto-SCRAM</b>: when enabled, the core is SCRAMmed (and held) while heat is at or above the
 * auto-SCRAM level (500..1000 permille).
 */
public final class ControlComputerLogic {

    public static final int HEAT = 0;
    public static final int FUEL = 1;
    public static final int OUTPUT = 2;
    public static final int STAGE = 3;
    /** Redstone choice only: the output stays off. */
    public static final int OFF = 4;
    public static final int SIGNALS = 4;

    public static final int DEFAULT_THRESHOLD_HEAT = 800;
    public static final int DEFAULT_THRESHOLD_FUEL = 100;
    public static final int DEFAULT_THRESHOLD_OUTPUT = 500;
    public static final int DEFAULT_THRESHOLD_STAGE = 1;
    public static final int DEFAULT_AUTO_SCRAM_AT = 900;
    public static final int DEFAULT_REDSTONE_SIGNAL = STAGE;
    public static final int DEFAULT_COMPARATOR_SIGNAL = HEAT;

    public static final int AUTO_SCRAM_MIN = 500;
    public static final int AUTO_SCRAM_MAX = 1000;

    private ControlComputerLogic() {
    }

    /** Comparator level 0..15 of a permille value ({@code value x 15 / 1000}, any value above 0 at least 1). */
    public static int level(int permille) {
        int p = Math.max(0, Math.min(1000, permille));
        if (p == 0) {
            return 0;
        }
        return Math.max(1, Math.min(15, p * 15 / 1000));
    }

    /** Comparator level of {@code signal}. */
    public static int comparator(int signal, int heat, int fuel, int output, int stage) {
        return switch (signal) {
            case HEAT -> level(heat);
            case FUEL -> level(fuel);
            case OUTPUT -> level(output);
            case STAGE -> Math.max(0, Math.min(3, stage)) * 5;
            default -> 0;
        };
    }

    /** Whether {@code signal} is on: at or above its threshold (fuel at or below; stage by code). */
    public static boolean on(int signal, int heat, int fuel, int output, int stage, int threshold) {
        return switch (signal) {
            case HEAT -> heat >= threshold;
            case FUEL -> fuel <= threshold;
            case OUTPUT -> output >= threshold;
            case STAGE -> stage >= Math.max(1, threshold);
            default -> false;
        };
    }

    /** The redstone output (0 or 15). */
    public static int redstone(boolean on) {
        return on ? 15 : 0;
    }

    /** Whether the computer holds the core SCRAMmed this tick. */
    public static boolean shouldScram(boolean autoScram, int heat, int autoScramAt) {
        return autoScram && heat >= clampAutoScram(autoScramAt);
    }

    public static int clampAutoScram(int permille) {
        return Math.max(AUTO_SCRAM_MIN, Math.min(AUTO_SCRAM_MAX, permille));
    }

    /** A threshold for {@code signal}, clamped to its range (stage 1..3, the rest 0..1000 permille). */
    public static int clampThreshold(int signal, int value) {
        return signal == STAGE ? Math.max(1, Math.min(3, value)) : Math.max(0, Math.min(1000, value));
    }
}
