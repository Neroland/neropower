package za.co.neroland.neropower.storage;

/**
 * The Storage Ring's state and rules, Minecraft-free so they are unit-testable (decision 008). The
 * controller block entity owns one, feeds it the ring shape, the coolant draw and the failure ladder's
 * verdict, and exposes {@link #insert}/{@link #extract} as its energy surface.
 *
 * <ul>
 *   <li><b>Energy</b> is one {@code long} in the controller; coils hold nothing. Capacity is
 *       coils x {@code smesCapacityPerCoil} while formed. Unforming keeps the energy (no I/O); re-forming
 *       caps it to the new capacity and the excess is <b>lost</b>, never duplicated
 *       ({@link #applyStructure}).</li>
 *   <li><b>Warmth</b> (0..1000 permille) rises while starved of cryo coolant and falls while cooled; the
 *       controller feeds it to the failure ladder as heat.</li>
 *   <li><b>Quench</b> (drain mode): lose {@code lossPermille} of the charge, go offline for the lockout,
 *       and re-arm only once the lockout has run out <i>and</i> warmth is below the re-arm line (the
 *       warning threshold minus the hysteresis).</li>
 *   <li><b>I/O</b>: coils x {@code smesIoPerCoil}, capped at {@code smesMaxIo}, halved while UNSTABLE,
 *       zero while unformed or quenched. It is a per-tick budget for each direction.</li>
 * </ul>
 * Energy enters only through {@link #insert} and leaves only through {@link #extract}, the quench and the
 * re-form cap: nothing here creates a unit.
 */
public final class SmesCore {

    /** Warmth ceiling (permille). */
    public static final int MAX_WARMTH = 1000;

    private long energy;
    private long capacity;
    private boolean formed;
    private int coils;
    private int warmth;
    private int lockout;
    private boolean quenched;
    private long inLeft;
    private long outLeft;
    private long inThisTick;
    private long outThisTick;

    // --- structure -------------------------------------------------------------------------------------

    /**
     * Apply a validation result. Formed: capacity = {@code coils x capacityPerCoil} and the energy is
     * capped to it. Unformed: the energy is kept untouched (no I/O until it re-forms).
     *
     * @return NE lost to the cap (0 unless the ring re-formed smaller than its charge)
     */
    public long applyStructure(boolean nowFormed, int coilCount, long capacityPerCoil) {
        this.formed = nowFormed && coilCount > 0;
        this.coils = this.formed ? coilCount : 0;
        if (!this.formed) {
            return 0L;
        }
        long cap = capacityPerCoil <= 0 ? 0L
                : (coilCount > Long.MAX_VALUE / capacityPerCoil ? Long.MAX_VALUE : coilCount * capacityPerCoil);
        this.capacity = cap;
        long lost = Math.max(0L, this.energy - cap);
        this.energy -= lost;
        return lost;
    }

    /** Coolant this ring needs per draw: coils x {@code perCoil} while formed, else 0. */
    public long coolantNeed(int perCoil) {
        return this.formed && perCoil > 0 ? (long) this.coils * perCoil : 0L;
    }

    // --- thermal and quench -------------------------------------------------------------------------------

    /** One tick of warmth: up by {@code warming} while starved, down by {@code cooling} otherwise. */
    public void tickWarmth(boolean starved, int warming, int cooling) {
        if (starved) {
            this.warmth = Math.min(MAX_WARMTH, this.warmth + Math.max(0, warming));
        } else {
            this.warmth = Math.max(0, this.warmth - Math.max(0, cooling));
        }
    }

    /**
     * A drain-mode quench: lose {@code lossPermille} of the stored energy and lock the ring out.
     *
     * @return NE lost
     */
    public long quench(int lossPermille, int lockoutTicks) {
        int permille = Math.max(0, Math.min(1000, lossPermille));
        long lost = this.energy / 1000L * permille + (this.energy % 1000L) * permille / 1000L;
        this.energy -= Math.min(this.energy, lost);
        this.lockout = Math.max(0, lockoutTicks);
        this.quenched = true;
        return lost;
    }

    /** Count the lockout down; re-arm once it is over and warmth is below {@code rearmPermille}. */
    public void tickLockout(int rearmPermille) {
        if (this.lockout > 0) {
            this.lockout--;
        }
        if (this.quenched && this.lockout == 0 && this.warmth < rearmPermille) {
            this.quenched = false;
        }
    }

    /** Whether the ring may move energy: formed and not quenched. */
    public boolean online() {
        return this.formed && !this.quenched;
    }

    // --- I/O -------------------------------------------------------------------------------------------------

    /** The ring's per-tick limit: coils x {@code perCoil} capped at {@code max}, halved when unstable; 0 offline. */
    public long ioLimit(int perCoil, int max, boolean unstable) {
        if (!online() || perCoil <= 0 || max <= 0) {
            return 0L;
        }
        long io = Math.min((long) this.coils * perCoil, max);
        return unstable ? io / 2 : io;
    }

    /** Start a tick with {@code io} available in each direction. */
    public void beginTick(long io) {
        this.inLeft = Math.max(0L, io);
        this.outLeft = Math.max(0L, io);
        this.inThisTick = 0L;
        this.outThisTick = 0L;
    }

    /** Insert up to {@code amount}; NE accepted. */
    public long insert(long amount, boolean simulate) {
        if (amount <= 0 || !online()) {
            return 0L;
        }
        long accepted = Math.min(amount, Math.min(this.inLeft, Math.max(0L, this.capacity - this.energy)));
        if (accepted > 0 && !simulate) {
            this.energy += accepted;
            this.inLeft -= accepted;
            this.inThisTick += accepted;
        }
        return Math.max(0L, accepted);
    }

    /** Extract up to {@code amount}; NE given. */
    public long extract(long amount, boolean simulate) {
        if (amount <= 0 || !online()) {
            return 0L;
        }
        long given = Math.min(amount, Math.min(this.outLeft, this.energy));
        if (given > 0 && !simulate) {
            this.energy -= given;
            this.outLeft -= given;
            this.outThisTick += given;
        }
        return Math.max(0L, given);
    }

    // --- reads ----------------------------------------------------------------------------------------------

    public long energy() {
        return this.energy;
    }

    /** Capacity while formed; while unformed, the charge it still holds (so a gauge reads full, not overfull). */
    public long capacity() {
        return this.formed ? this.capacity : this.energy;
    }

    public boolean formed() {
        return this.formed;
    }

    public int coils() {
        return this.coils;
    }

    public int warmth() {
        return this.warmth;
    }

    public int lockout() {
        return this.lockout;
    }

    public boolean quenched() {
        return this.quenched;
    }

    /** NE inserted since {@link #beginTick}. */
    public long inThisTick() {
        return this.inThisTick;
    }

    /** NE extracted since {@link #beginTick}. */
    public long outThisTick() {
        return this.outThisTick;
    }

    // --- persistence ----------------------------------------------------------------------------------------

    /** Restore saved values (clamped). Structure is not saved: the first tick after load re-validates. */
    public void load(long savedEnergy, int savedWarmth, int savedLockout, boolean savedQuenched) {
        this.energy = Math.max(0L, savedEnergy);
        this.warmth = Math.max(0, Math.min(MAX_WARMTH, savedWarmth));
        this.lockout = Math.max(0, savedLockout);
        this.quenched = savedQuenched;
    }
}
