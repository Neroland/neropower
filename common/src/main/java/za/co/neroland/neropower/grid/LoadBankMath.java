package za.co.neroland.neropower.grid;

/**
 * Load Bank rules, Minecraft-free. It burns NE and makes heat and nothing else. The
 * NE-per-heat divisor is clamped so a Stirling Generator drawing that heat can never return half of what
 * was burned, under <i>any</i> config: the clamp covers the Stirling's largest possible multiplier, the
 * configured cold-face bonus doubled by the coldest planet ({@code StirlingMath.coldFaceBonusPermille}
 * caps the planet scale at +100 %), and the machine preset's heat scaling.
 */
public final class LoadBankMath {

    public static final int PERMILLE = 1_000;

    private LoadBankMath() {
    }

    /**
     * NE burned per heat unit made: {@code max(loadBankNePerHeat, 2 × stirlingNePerHeatUnit × (1000 + 2 ×
     * bonus) / 1000 + 1)}, so a Stirling returns strictly under half; never below 1.
     */
    public static int nePerHeat(int loadBankNePerHeat, int stirlingNePerHeatUnit, int stirlingColdFaceBonusPermille) {
        long bonus = Math.max(0, stirlingColdFaceBonusPermille);
        long stirling = Math.max(1, stirlingNePerHeatUnit);
        long floor = 2L * stirling * (PERMILLE + 2L * bonus) / PERMILLE + 1L; // strictly above half
        long divisor = Math.max(Math.max(1, loadBankNePerHeat), floor);
        return (int) Math.min(Integer.MAX_VALUE, divisor);
    }

    /**
     * Heat made from {@code burned} NE plus the carried remainder: {@code [heat, newRemainder]}. The
     * remainder carries the fraction of a heat unit to the next tick so a low rate still makes heat and
     * no rounding ever makes heat for free.
     */
    public static long[] heat(long burned, long remainder, int nePerHeat) {
        int divisor = Math.max(1, nePerHeat);
        long total = Math.max(0L, burned) + Math.max(0L, remainder);
        return new long[] {total / divisor, total % divisor};
    }

    /**
     * The amount to hand {@code addHeat} so that, after a preset scales it by {@code presetHeatPermille},
     * the machine gains at most {@code heat} units (Overdrive would otherwise double the heat per NE).
     */
    public static int presetAdjusted(long heat, int presetHeatPermille) {
        if (heat <= 0L) {
            return 0;
        }
        long scale = Math.max(PERMILLE, presetHeatPermille);
        return (int) Math.min(Integer.MAX_VALUE, heat * PERMILLE / scale);
    }

    /** What it draws this tick: the set rate, what it holds, nothing while it is too hot to shed heat. */
    public static long draw(long rateSet, long stored, boolean overheated) {
        if (overheated || rateSet <= 0L || stored <= 0L) {
            return 0L;
        }
        return Math.min(rateSet, stored);
    }

    /** Blockstate {@code heat} 0..4 from heat over the throttle threshold. */
    public static int heatStep(int heat, int throttleThreshold) {
        if (heat <= 0 || throttleThreshold <= 0) {
            return 0;
        }
        return (int) Math.max(0L, Math.min(4L, heat * 4L / throttleThreshold));
    }
}
