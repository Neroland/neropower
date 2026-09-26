package za.co.neroland.neropower.fission;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The fission reactor's balance maths — pure Java with no Minecraft types, so every curve and
 * factor is unit-testable. The block entity feeds it config values and rod burn-ups; it never
 * reads config itself (the caller parses {@code fissionBurnupCurve} once through
 * {@link #parseCurve(String)} and caches the {@link Curve}).
 *
 * <p>Units: burn-up and every factor are <b>permille</b> ints ({@value #PERMILLE} = 100%); the
 * control-rod factor is a plain fraction because it multiplies a double product.
 */
public final class FissionMath {

    /** 100%, and the ceiling of every permille quantity here. */
    public static final int PERMILLE = 1000;

    /** A rod at this burn-up is spent. */
    public static final int MAX_BURNUP = PERMILLE;

    /** The shipped burn-up curve: a fresh rod over-performs, a nearly spent one limps. */
    public static final String DEFAULT_CURVE = "0=1200,200=1000,600=800,1000=300";

    /** Heat runs this much (permille) above the output factor — fresh rods are the hottest. */
    public static final int HEAT_OFFSET = 200;

    /** The control-rod factor never drops below this, however many assemblies are fitted. */
    public static final double CONTROL_ROD_FLOOR = 0.15D;

    /** Rod slots the core exposes (a 5³ shell uses all of them, a 3³ shell two). */
    public static final int ROD_SLOTS = 4;

    private FissionMath() {
    }

    // --- burn-up curve -------------------------------------------------------------

    /**
     * A piecewise-linear curve over burn-up (permille) with at least two knots in ascending order.
     * Outside the knot range the curve holds its end values.
     */
    public static final class Curve {
        private final int[] burnups;
        private final int[] factors;

        private Curve(int[] burnups, int[] factors) {
            this.burnups = burnups;
            this.factors = factors;
        }

        /** Number of knots (always >= 2). */
        public int knots() {
            return this.burnups.length;
        }

        /** The value (permille) at {@code burnup}, linearly interpolated between the two enclosing knots. */
        public int at(int burnup) {
            if (burnup <= this.burnups[0]) {
                return this.factors[0];
            }
            int last = this.burnups.length - 1;
            if (burnup >= this.burnups[last]) {
                return this.factors[last];
            }
            for (int i = 1; i <= last; i++) {
                if (burnup <= this.burnups[i]) {
                    int x0 = this.burnups[i - 1];
                    int x1 = this.burnups[i];
                    int y0 = this.factors[i - 1];
                    int y1 = this.factors[i];
                    if (x1 == x0) {
                        return y1;
                    }
                    return (int) Math.round(y0 + (double) (y1 - y0) * (burnup - x0) / (x1 - x0));
                }
            }
            return this.factors[last];
        }

        @Override
        public String toString() {
            return "Curve" + Arrays.toString(this.burnups) + "->" + Arrays.toString(this.factors);
        }
    }

    /** The parsed {@link #DEFAULT_CURVE}. */
    public static final Curve DEFAULT = parseCurve(DEFAULT_CURVE);

    /**
     * Parse {@code "burnup=factor,burnup=factor,..."} into a {@link Curve}. Malformed pairs are
     * skipped (the rest still applies), knots are sorted by burn-up, and duplicate burn-ups keep
     * the last value. Fewer than two valid knots yields the default curve rather than a flat line.
     */
    public static Curve parseCurve(String raw) {
        List<long[]> knots = new ArrayList<>();
        if (raw != null) {
            for (String pair : raw.split(",")) {
                String entry = pair.trim();
                int eq = entry.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                try {
                    long x = Long.parseLong(entry.substring(0, eq).trim());
                    long y = Long.parseLong(entry.substring(eq + 1).trim());
                    if (x < 0 || x > MAX_BURNUP || y < 0 || y > 100_000L) {
                        continue;
                    }
                    knots.add(new long[] {x, y});
                } catch (NumberFormatException ignored) {
                    // Skip the malformed pair; the remaining knots still form the curve.
                }
            }
        }
        knots.sort((a, b) -> Long.compare(a[0], b[0]));
        // Duplicate burn-ups: the later entry wins (matches "last one in the file applies").
        List<long[]> unique = new ArrayList<>();
        for (long[] knot : knots) {
            if (!unique.isEmpty() && unique.get(unique.size() - 1)[0] == knot[0]) {
                unique.set(unique.size() - 1, knot);
            } else {
                unique.add(knot);
            }
        }
        if (unique.size() < 2) {
            if (DEFAULT_CURVE.equals(raw)) {
                throw new IllegalStateException("the default fission burn-up curve does not parse");
            }
            return DEFAULT == null ? parseCurve(DEFAULT_CURVE) : DEFAULT;
        }
        int[] xs = new int[unique.size()];
        int[] ys = new int[unique.size()];
        for (int i = 0; i < unique.size(); i++) {
            xs[i] = (int) unique.get(i)[0];
            ys[i] = (int) unique.get(i)[1];
        }
        return new Curve(xs, ys);
    }

    /** Output factor (permille of nominal) of a rod at {@code burnup} (clamped to 0..1000). */
    public static int outputFactor(Curve curve, int burnup) {
        return curve.at(clampBurnup(burnup));
    }

    /** Output factor on the default curve. */
    public static int outputFactor(int burnup) {
        return outputFactor(DEFAULT, burnup);
    }

    /**
     * Heat factor (permille) of a rod at {@code burnup}: the output factor plus {@value #HEAT_OFFSET},
     * so a fresh rod runs hotter than its power alone would suggest and even a spent one still glows.
     */
    public static int heatFactor(Curve curve, int burnup) {
        return outputFactor(curve, burnup) + HEAT_OFFSET;
    }

    /** Heat factor on the default curve. */
    public static int heatFactor(int burnup) {
        return heatFactor(DEFAULT, burnup);
    }

    /** Burn-up clamped to {@code 0..MAX_BURNUP}. */
    public static int clampBurnup(int burnup) {
        return Math.max(0, Math.min(MAX_BURNUP, burnup));
    }

    /** Whether a rod at {@code burnup} is spent. */
    public static boolean spent(int burnup) {
        return burnup >= MAX_BURNUP;
    }

    // --- control rods --------------------------------------------------------------

    /**
     * The control-rod factor: {@code max(0.15, 1 - rods x permille / 1000)}. More assemblies than
     * the interior can hold are impossible, so {@code rodCount} is capped at {@code interiorVolume}
     * first; a negative count or permille reads as 0.
     *
     * @param rodCount           Control Rod Assemblies inside the shell
     * @param interiorVolume     interior blocks of the shell ({@link #interiorVolume(int)})
     * @param controlRodPermille reduction per assembly, permille ({@code fissionControlRodPermille})
     */
    public static double controlRodFactor(int rodCount, int interiorVolume, int controlRodPermille) {
        int rods = Math.max(0, Math.min(rodCount, Math.max(0, interiorVolume)));
        int permille = Math.max(0, controlRodPermille);
        double factor = 1.0D - rods * permille / (double) PERMILLE;
        return Math.max(CONTROL_ROD_FLOOR, factor);
    }

    // --- shell geometry ------------------------------------------------------------

    /** Interior (non-shell) blocks of a cubic shell of edge {@code shellSize}; 0 below 3. */
    public static int interiorVolume(int shellSize) {
        if (shellSize < 3) {
            return 0;
        }
        int inner = shellSize - 2;
        return inner * inner * inner;
    }

    /**
     * Rod slots a shell can use: {@code shellSize - 1}, clamped to {@code 0..ROD_SLOTS} — 2 for a
     * 3³ shell, all 4 for a 5³ one, none while unformed (size 0).
     */
    public static int usableRodSlots(int shellSize) {
        if (shellSize < 3) {
            return 0;
        }
        return Math.max(0, Math.min(ROD_SLOTS, shellSize - 1));
    }

    // --- output --------------------------------------------------------------------

    /**
     * NE/tick for the whole core: {@code nePerRod x sum(outputFactor/1000) x controlRodFactor x
     * speed x failureOutputPermille/1000}, rounded and never negative.
     *
     * @param nePerRod         nominal NE/tick per rod ({@code fissionNePerRod})
     * @param rodFactorSum     sum over loaded rods of {@code outputFactor(burnup) / 1000}
     * @param controlRodFactor {@link #controlRodFactor}
     * @param speed            preset speed factor x upgrade speed multiplier
     * @param failurePermille  the failure ladder's output multiplier (permille)
     */
    public static long outputPerTick(int nePerRod, double rodFactorSum, double controlRodFactor, double speed,
            int failurePermille) {
        double value = nePerRod * rodFactorSum * controlRodFactor * speed * Math.max(0, failurePermille)
                / PERMILLE;
        return Math.max(0L, Math.round(value));
    }

    /**
     * Heat per tick for the whole core: {@code heatPerRod x sum(heatFactor/1000) x controlRodFactor},
     * rounded, and at least 1 while any rod is loaded (a running core is never heat-free).
     */
    public static int heatPerTick(int heatPerRod, double rodHeatSum, double controlRodFactor) {
        if (rodHeatSum <= 0.0D || heatPerRod <= 0) {
            return 0;
        }
        double value = heatPerRod * rodHeatSum * controlRodFactor;
        return Math.max(1, (int) Math.min(Integer.MAX_VALUE, Math.round(value)));
    }
}
