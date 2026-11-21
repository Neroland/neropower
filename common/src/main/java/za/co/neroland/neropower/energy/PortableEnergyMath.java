package za.co.neroland.neropower.energy;

/**
 * The arithmetic of portable energy, Minecraft-free so it is unit-testable: how much a stack holding
 * {@code stored} of {@code capacity} at {@code io} per transfer may take or give, and the FE to NE
 * conversion an Induction Charger offer goes through. Rounding always favours the world: an item
 * accepts NE rounded down from an FE offer and reports FE rounded up, so no ratio makes energy.
 */
public final class PortableEnergyMath {

    private PortableEnergyMath() {
    }

    /** NE a stack may accept: bounded by the request, the room left and the rate. Never negative. */
    public static long acceptable(long stored, long capacity, long io, long request) {
        if (request <= 0 || io <= 0 || capacity <= 0) {
            return 0L;
        }
        long room = Math.max(0L, capacity - Math.max(0L, stored));
        return Math.min(request, Math.min(room, io));
    }

    /** NE a stack may give: bounded by the request, what it holds and the rate. Never negative. */
    public static long givable(long stored, long io, long request) {
        if (request <= 0 || io <= 0) {
            return 0L;
        }
        return Math.min(request, Math.min(Math.max(0L, stored), io));
    }

    /** NE an NE-denominated item may accept from an offer of {@code maxFe} FE at {@code ratio} FE per NE (floor). */
    public static long feOfferToNe(long maxFe, double ratio) {
        if (maxFe <= 0 || !(ratio > 0)) {
            return 0L;
        }
        double ne = Math.floor(maxFe / ratio + 1e-9);
        return ne >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) ne;
    }

    /** FE to report for {@code ne} NE accepted (ceiling, capped at {@code maxFe}): the charger pays at least that. */
    public static long acceptedFe(long ne, double ratio, long maxFe) {
        if (ne <= 0 || !(ratio > 0) || maxFe <= 0) {
            return 0L;
        }
        double fe = Math.ceil(ne * ratio - 1e-9);
        return Math.min(maxFe, fe >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) fe);
    }

    /** FE a stack can hand out for {@code ne} NE (floor), for the loaders' FE item capabilities. */
    public static long neToFeFloor(long ne, double ratio) {
        if (ne <= 0 || !(ratio > 0)) {
            return 0L;
        }
        double fe = Math.floor(ne * ratio + 1e-9);
        return fe >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) fe;
    }

    /** NE a delivery of {@code fe} FE costs (ceiling, at least 1 for any non-zero delivery). */
    public static long feToNeCeil(long fe, double ratio) {
        if (fe <= 0 || !(ratio > 0)) {
            return 0L;
        }
        double ne = Math.ceil(fe / ratio - 1e-9);
        return Math.max(1L, ne >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) ne);
    }

    /**
     * One Induction Charger top-up, end to end: {@code maxFe} offered to a stack holding {@code stored} of
     * {@code capacity} at {@code io}. Returns {@code {neAccepted, feReported}}; {@code feReported} never
     * exceeds the offer and never pays for less than {@code neAccepted} NE.
     */
    public static long[] chargeFromFe(long stored, long capacity, long io, long maxFe, double ratio) {
        long ne = acceptable(stored, capacity, io, feOfferToNe(maxFe, ratio));
        // ne <= floor(maxFe / ratio), so ne * ratio <= maxFe and the ceiling below is never capped:
        // the charger is always paid at least what the item gained.
        return new long[] {ne, acceptedFe(ne, ratio, maxFe)};
    }
}
