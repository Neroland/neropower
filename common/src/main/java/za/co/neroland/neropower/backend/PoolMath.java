package za.co.neroland.neropower.backend;

/**
 * Pure arithmetic of the Spent Fuel Rack (no Minecraft types, unit-tested): decay heat in, water
 * cooling out, and the per-slot cooling clock that turns a hot rod into a spent rod.
 *
 * <p>The one rule that matters: a hot rod cools <b>only</b> in a rack, <b>only</b> while that rack is
 * calm (below the failure ladder's warning stage), and only once its clock reaches
 * {@code poolCoolTicks}. Nothing else in the mod turns a hot rod into a spent one, and no recipe takes a
 * hot rod, so reprocessing always waits for the pool.
 */
public final class PoolMath {

    /** Rod slots in one rack. */
    public static final int SLOTS = 4;

    /** Most water contacts a rack can have: its five non-bottom faces. */
    public static final int MAX_CONTACTS = 5;

    /** Highest {@link #coolStep} bucket. */
    public static final int MAX_COOL_STEP = 4;

    private PoolMath() {
    }

    /** Decay heat added this tick by {@code hotRods} hot rods. */
    public static int heatIn(int hotRods, int heatPerRod) {
        return Math.max(0, hotRods) * Math.max(0, heatPerRod);
    }

    /** Heat removed this tick by {@code contacts} water-source contacts (clamped to 0..5). */
    public static int heatOut(int contacts, int coolingPerContact) {
        return clampContacts(contacts) * Math.max(0, coolingPerContact);
    }

    /** Contacts clamped to the rack's five usable faces. */
    public static int clampContacts(int contacts) {
        return Math.max(0, Math.min(MAX_CONTACTS, contacts));
    }

    /**
     * One tick of a hot rod's cooling clock.
     *
     * @param progress the clock before this tick
     * @param calm     whether the rack is below the warning stage
     * @return the clock after this tick (unchanged while the rack is not calm)
     */
    public static int advance(int progress, boolean calm) {
        if (!calm) {
            return Math.max(0, progress);
        }
        int next = Math.max(0, progress) + 1;
        return next < 0 ? Integer.MAX_VALUE : next;
    }

    /** Whether a clock at {@code progress} has finished cooling under {@code coolTicks}. */
    public static boolean cooled(int progress, int coolTicks) {
        return progress >= Math.max(1, coolTicks);
    }

    /** Cooling progress as permille (0..1000). */
    public static int permille(int progress, int coolTicks) {
        int total = Math.max(1, coolTicks);
        return (int) Math.max(0L, Math.min(1000L, (long) Math.max(0, progress) * 1000L / total));
    }

    /**
     * The per-slot render bucket (the render input {@code coolStep}, 0..4): {@code 0} empty slot,
     * {@code 1} a cooled spent rod, {@code 4} a hot rod just in, {@code 3} past a third of its cooling,
     * {@code 2} past two thirds.
     */
    public static int coolStep(boolean empty, boolean hot, int progress, int coolTicks) {
        if (empty) {
            return 0;
        }
        if (!hot) {
            return 1;
        }
        int permille = permille(progress, coolTicks);
        if (permille >= 667) {
            return 2;
        }
        return permille >= 333 ? 3 : 4;
    }
}
