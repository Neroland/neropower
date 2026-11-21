package za.co.neroland.neropower.turbine;

/**
 * The steam turbine's and Steam Condenser's rules as pure arithmetic (no Minecraft types), so inertia,
 * the overspeed trip, the stator ceiling and condenser behaviour are unit-testable. The block entities
 * feed these from config and the world.
 *
 * <p>Energy is never created: a tick's output is at most {@code steam used x NE per mB x efficiency x
 * exhaust factor}, cut to the stators' ceiling and the buffer's room, then scaled by
 * {@code min(1, rpm / rated)}. The rotor stores no energy, so a coasting rotor makes nothing.
 */
public final class TurbineMath {

    public static final int PERMILLE = 1000;
    /** Rated rotor speed: full output at or above it. */
    public static final int RATED_RPM = 1800;
    /** The highest speed the rotor is ever driven toward (an unloaded turbine). */
    public static final int MAX_TARGET_RPM = 2400;
    /** A trip may be reset only once the rotor has coasted below this speed. */
    public static final int RESET_BELOW_RPM = 600;
    /** rpm per synced render bucket. */
    public static final int RPM_PER_BUCKET = 100;
    /** The largest render bucket (2400 rpm). */
    public static final int MAX_RPM_BUCKET = MAX_TARGET_RPM / RPM_PER_BUCKET;

    /** The condenser's default air-cooling table (decision 005), by NeroTech {@code Atmosphere} name. */
    public static final String DEFAULT_ATMOSPHERE_FACTORS =
            "EARTHLIKE=1000,NITROGEN_OXYGEN=1000,NITROGEN_METHANE=1100,THIN_CO2=900,SULFUROUS=600,VACUUM=0";
    /** Factor used for an atmosphere the table does not list. */
    public static final int UNKNOWN_ATMOSPHERE_FACTOR = 1000;
    /** Highest factor a table entry may give. */
    public static final int MAX_ATMOSPHERE_FACTOR = 2000;

    /** Condenser heat sink kinds (synced {@code sink} render input). */
    public static final int SINK_AIR = 0;
    public static final int SINK_RADIATOR = 1;
    public static final int SINK_CRYO = 2;
    public static final int SINK_NONE = 3;

    private TurbineMath() {
    }

    // --- the rotor ------------------------------------------------------------------------------

    /**
     * What a formed turbine can do, aggregated from its slices.
     *
     * @param flowCap     mB/t all vane slices pass together
     * @param effPermille flow-weighted mean vane efficiency
     * @param ceiling     NE/t all wound stators convert together
     */
    public record Rotor(int flowCap, int effPermille, long ceiling) {

        public static final Rotor NONE = new Rotor(0, 0, 0L);

        /** Aggregate vane slices (flow and efficiency per slice) and the stators' total ceiling. */
        public static Rotor of(int[] vaneFlows, int[] vaneEffs, long ceiling) {
            long flow = 0L;
            long weighted = 0L;
            for (int i = 0; i < vaneFlows.length; i++) {
                int f = Math.max(0, vaneFlows[i]);
                flow += f;
                weighted += (long) f * Math.max(0, Math.min(PERMILLE, vaneEffs[i]));
            }
            int cap = (int) Math.min(Integer.MAX_VALUE, flow);
            int eff = flow <= 0 ? 0 : (int) (weighted / flow);
            return new Rotor(cap, eff, Math.max(0L, ceiling));
        }

        /** Whether the rotor can turn steam into power at all (vanes and wound stators present). */
        public boolean runnable() {
            return this.flowCap > 0 && this.effPermille > 0 && this.ceiling > 0;
        }
    }

    /**
     * One tick's steam and power plan.
     *
     * @param steamPassed     mB drawn from the inlets (converted or bypassed); also the exhaust made
     * @param steamUsed       mB actually converted (less than passed only under a governor)
     * @param condensed       exhaust mB sent to exhaust ports (condenser-bound)
     * @param vented          exhaust mB lost to the air through vent ports
     * @param exhaustPermille the exhaust factor for this tick
     * @param potential       NE the converted steam could yield
     * @param extracted       NE the stators and buffer can take (before the rpm factor)
     * @param targetRpm       the speed the rotor is driven toward
     */
    public record Plan(long steamPassed, long steamUsed, long condensed, long vented, int exhaustPermille,
            long potential, long extracted, int targetRpm) {

        public static final Plan IDLE = new Plan(0L, 0L, 0L, 0L, 0, 0L, 0L, 0);
    }

    /**
     * Plan a tick.
     *
     * @param rotor         the turbine's aggregate
     * @param steamAvailable live steam stored at the inlets
     * @param exhaustRoom   room in the exhaust ports' tanks
     * @param canVent       whether at least one vent port exists
     * @param ventPermille  yield kept for vented exhaust
     * @param nePerMb       NE per mB at rated speed and full efficiency
     * @param bufferRoom    room in the helm's energy buffer
     * @param governor      a Governor Module is fitted
     * @param tripped       the trip is latched (inlets closed)
     */
    public static Plan plan(Rotor rotor, long steamAvailable, long exhaustRoom, boolean canVent, int ventPermille,
            int nePerMb, long bufferRoom, boolean governor, boolean tripped) {
        if (tripped || rotor.flowCap() <= 0) {
            return Plan.IDLE;
        }
        long passed = Math.max(0L, Math.min(steamAvailable, rotor.flowCap()));
        long condensed = Math.min(passed, Math.max(0L, exhaustRoom));
        long vented = canVent ? passed - condensed : 0L;
        passed = condensed + vented; // exhaust with nowhere to go stalls the inlet
        int exhaust = exhaustPermille(condensed, vented, ventPermille);
        long sink = Math.max(0L, Math.min(rotor.ceiling(), bufferRoom));

        long used = passed;
        if (governor) {
            long denominator = (long) Math.max(0, nePerMb) * rotor.effPermille() * exhaust;
            used = denominator <= 0L ? 0L : Math.min(passed, sink * 1_000_000L / denominator);
        }
        long potential = potential(used, nePerMb, rotor.effPermille(), exhaust);
        long extracted = Math.min(potential, sink);

        int target;
        if (governor) {
            // The governor bleeds the surplus but keeps the rotor at speed: never above rated.
            target = (int) (RATED_RPM * passed / rotor.flowCap());
        } else {
            target = (int) (RATED_RPM * used / rotor.flowCap());
            if (extracted < potential) {
                // Nothing can take the power: the rotor runs away toward MAX_TARGET_RPM.
                long runaway = RATED_RPM * potential / Math.max(1L, extracted);
                target = (int) Math.max(target, Math.min(MAX_TARGET_RPM, runaway));
            }
        }
        return new Plan(passed, used, condensed, vented, exhaust, potential, extracted,
                Math.max(0, Math.min(MAX_TARGET_RPM, target)));
    }

    /** Flow-weighted exhaust factor: condensed exhaust counts in full, vented at {@code ventPermille}. */
    public static int exhaustPermille(long condensed, long vented, int ventPermille) {
        long total = condensed + vented;
        if (total <= 0L) {
            return 0;
        }
        int vent = Math.max(0, Math.min(PERMILLE, ventPermille));
        return (int) ((condensed * PERMILLE + vented * vent) / total);
    }

    /** NE a quantity of steam yields at rated speed. */
    public static long potential(long steamMb, int nePerMb, int effPermille, int exhaustPermille) {
        if (steamMb <= 0L) {
            return 0L;
        }
        return steamMb * Math.max(0, nePerMb) * Math.max(0, effPermille) * Math.max(0, exhaustPermille) / 1_000_000L;
    }

    /** Move {@code rpm} toward {@code target} by at most {@code up} (spin-up) or {@code down} (coast-down). */
    public static int stepRpm(int rpm, int target, int up, int down) {
        if (target > rpm) {
            return Math.min(target, rpm + Math.max(1, up));
        }
        if (target < rpm) {
            return Math.max(target, rpm - Math.max(1, down));
        }
        return rpm;
    }

    /** NE delivered this tick: the extracted power scaled by the rotor speed and the failure ladder. */
    public static long output(long extracted, int rpm, int failurePermille) {
        if (extracted <= 0L || rpm <= 0) {
            return 0L;
        }
        long atSpeed = extracted * Math.min(rpm, RATED_RPM) / RATED_RPM;
        return atSpeed * Math.max(0, Math.min(PERMILLE, failurePermille)) / PERMILLE;
    }

    /** Overspeed stress (permille) fed to the failure ladder: 0 at rated speed, 1000 at the trip speed. */
    public static int overspeedPermille(int rpm, int tripRpm) {
        int span = Math.max(1, tripRpm - RATED_RPM);
        long stress = (long) (rpm - RATED_RPM) * PERMILLE / span;
        return (int) Math.max(0L, Math.min(PERMILLE, stress));
    }

    /** The synced render bucket for a speed (rpm / 100, 0..24). */
    public static int rpmBucket(int rpm) {
        return Math.max(0, Math.min(MAX_RPM_BUCKET, rpm / RPM_PER_BUCKET));
    }

    /** Whether a latched trip may be reset at this speed. */
    public static boolean canReset(int rpm) {
        return rpm < RESET_BELOW_RPM;
    }

    // --- the condenser ----------------------------------------------------------------------------

    /**
     * One condenser tick.
     *
     * @param condensed exhaust mB turned into water
     * @param cryoUsed  cryo coolant mB consumed
     * @param sink      the dominant heat sink ({@link #SINK_AIR} .. {@link #SINK_NONE})
     */
    public record Condense(long condensed, long cryoUsed, int sink) {
    }

    /**
     * Condense exhaust: air first, then radiators, then cryo coolant (so coolant is spent last). Water
     * out equals exhaust in, 1:1. With no air, no radiator and no coolant the sink is {@link #SINK_NONE}
     * and nothing condenses.
     */
    public static Condense condense(long exhaust, long waterRoom, long airRate, long radiatorRate,
            long cryoAvailable, int steamPerCryo) {
        long air = Math.max(0L, airRate);
        long rad = Math.max(0L, radiatorRate);
        long cryo = Math.max(0L, cryoAvailable);
        int perCryo = Math.max(1, steamPerCryo);
        if (air == 0L && rad == 0L && cryo == 0L) {
            return new Condense(0L, 0L, SINK_NONE);
        }
        long want = Math.max(0L, Math.min(exhaust, waterRoom));
        long byAir = Math.min(want, air);
        want -= byAir;
        long byRad = Math.min(want, rad);
        want -= byRad;
        long byCryo = Math.min(want, cryo * perCryo);
        long cryoUsed = (byCryo + perCryo - 1) / perCryo;
        int sink;
        if (byAir + byRad + byCryo == 0L) {
            sink = air > 0L ? SINK_AIR : rad > 0L ? SINK_RADIATOR : SINK_CRYO;
        } else if (byAir >= byRad && byAir >= byCryo) {
            sink = SINK_AIR;
        } else if (byRad >= byCryo) {
            sink = SINK_RADIATOR;
        } else {
            sink = SINK_CRYO;
        }
        return new Condense(byAir + byRad + byCryo, cryoUsed, sink);
    }

    /** Air-cooled rate for a base rate and an atmosphere factor (permille). */
    public static long airRate(int baseMbPerTick, int factorPermille) {
        return (long) Math.max(0, baseMbPerTick) * Math.max(0, Math.min(MAX_ATMOSPHERE_FACTOR, factorPermille)) / PERMILLE;
    }

    /**
     * The factor for {@code atmosphere} in a {@code NAME=permille,...} table; an unlisted or malformed
     * entry reads {@link #UNKNOWN_ATMOSPHERE_FACTOR}, values clamp to 0..{@link #MAX_ATMOSPHERE_FACTOR}.
     */
    public static int atmosphereFactor(String table, String atmosphere) {
        if (table == null || atmosphere == null) {
            return UNKNOWN_ATMOSPHERE_FACTOR;
        }
        for (String entry : table.split(",")) {
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if (!entry.substring(0, eq).trim().equalsIgnoreCase(atmosphere)) {
                continue;
            }
            try {
                int value = Integer.parseInt(entry.substring(eq + 1).trim());
                return Math.max(0, Math.min(MAX_ATMOSPHERE_FACTOR, value));
            } catch (NumberFormatException ignored) {
                return UNKNOWN_ATMOSPHERE_FACTOR;
            }
        }
        return UNKNOWN_ATMOSPHERE_FACTOR;
    }

    /** The condenser's synced rate bucket (0 idle, 1..4 by share of the base air rate). */
    public static int rateBucket(long condensed, int baseMbPerTick) {
        if (condensed <= 0L) {
            return 0;
        }
        long base = Math.max(1, baseMbPerTick);
        return (int) Math.max(1L, Math.min(4L, (condensed * 4L + base - 1L) / base));
    }
}
