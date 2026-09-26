package za.co.neroland.neropower.fission;

/**
 * The fission core's neutron-poison state machine — pure Java, so the stall/recover hysteresis is
 * unit-testable. Poison (0..{@value FissionMath#PERMILLE}) builds while the core runs hot
 * ({@link FissionMath#poisonAccumulates}) and decays otherwise; at the stall line the core drops into the
 * <b>xenon pit</b> (no output, no burn-up) and only climbs out once the poison has decayed
 * {@value #RECOVERY_MARGIN} below that line, so a core hovering at the threshold cannot flap.
 */
public final class PoisonModel {

    /** How far (permille) below the stall line poison must fall before the core restarts. */
    public static final int RECOVERY_MARGIN = 300;

    private int poison;
    private boolean stalled;

    /** The current poison level (permille). */
    public int poison() {
        return this.poison;
    }

    /** Whether the core is in the xenon pit. */
    public boolean stalled() {
        return this.stalled;
    }

    /**
     * Advance one tick.
     *
     * @param accumulating   the core ran hot this tick ({@link FissionMath#poisonAccumulates})
     * @param perTick        poison gained per accumulating tick ({@code fissionPoisonPerTick})
     * @param decayPerTick   poison shed per non-accumulating tick ({@code fissionPoisonDecayPerTick})
     * @param stallPermille  the stall line ({@code fissionPoisonStallPermille})
     * @return whether the core is stalled after this tick
     */
    public boolean tick(boolean accumulating, int perTick, int decayPerTick, int stallPermille) {
        if (accumulating && !this.stalled) {
            this.poison = Math.min(FissionMath.PERMILLE, this.poison + Math.max(0, perTick));
        } else {
            this.poison = Math.max(0, this.poison - Math.max(0, decayPerTick));
        }
        int stall = Math.max(1, Math.min(FissionMath.PERMILLE, stallPermille));
        if (!this.stalled && this.poison >= stall) {
            this.stalled = true;
        } else if (this.stalled && this.poison <= recoveryLine(stall)) {
            this.stalled = false;
        }
        return this.stalled;
    }

    /** The level at or below which a stalled core recovers: the stall line minus the margin, floored at 0. */
    public static int recoveryLine(int stallPermille) {
        return Math.max(0, stallPermille - RECOVERY_MARGIN);
    }

    /** Restore persisted state (silent). Out-of-range poison is clamped. */
    public void load(int poison, boolean stalled) {
        this.poison = Math.max(0, Math.min(FissionMath.PERMILLE, poison));
        this.stalled = stalled;
    }
}
