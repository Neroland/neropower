package za.co.neroland.neropower.config;

import za.co.neroland.nerolandcore.config.ConfigManager;
import za.co.neroland.nerolandcore.config.ConfigSchema;
import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.neropower.failure.TerrainPolicy;

/**
 * NeroPower's config, backed by Neroland Core's shared {@link ConfigManager}. Core owns the single
 * {@code config/neropower.properties} file (defaults, range validation, in-place key migration, the
 * {@code /neroland config reload} hot-reload, and server-authoritative client sync) — NeroPower just
 * declares the schema once and reads the typed {@link ConfigValue} handles through static getters.
 *
 * <p>This is NeroPower's <b>own</b> file: it never reads {@code nerotech.properties}. NeroTech's
 * thermal balance (heat capacity, dissipation, throttle) stays NeroTech's; NeroPower layers the
 * failure model and its own machines on top.
 *
 * <p>Gameplay-balance values are <b>server-authoritative</b> (a connected client uses the server's
 * values). The snapshot Core syncs carries only config keys/values — never player data (POPIA/GDPR).
 */
public final class NeroPowerConfig {

    private static final ConfigSchema SCHEMA = ConfigSchema.create("neropower",
            "NeroPower config (managed by Neroland Core). Power-depth add-on balance for NeroTech.");

    // --- master toggles (design record) ---------------------------------------
    private static final ConfigValue<Boolean> OVERLOAD_ENABLED = SCHEMA.bool("overloadEnabled",
            true, true, "true: an unmanaged NeroPower reactor walks the full failure ladder "
            + "(stable > warning > unstable > FAILURE) and destroys itself at the top; false "
            + "(survival-friendly): the ladder is pinned at UNSTABLE — throttled output, alarms, but "
            + "never a failure action");
    private static final ConfigValue<String> TERRAIN_DAMAGE_MODE = SCHEMA.string("terrainDamageMode",
            "auto", true, "whether a reactor failure destroys terrain: on / off / auto (auto = off on a "
            + "dedicated server, on in singleplayer and LAN); off keeps the blast (damage, knockback, the "
            + "machine itself is still lost) but breaks no blocks");
    private static final ConfigValue<Integer> FAILURE_RADIUS_CAP = SCHEMA.intRange("failureRadiusCap",
            8, 1, 16, true, "hard cap on any NeroPower failure blast radius (blocks), whatever the machine asks for");
    private static final ConfigValue<Boolean> TRANSMISSION_ENABLED = SCHEMA.bool("transmissionEnabled",
            true, true, "true: beamed power (transmitter / receiver / relay, Stage 6) links and transfers; "
            + "false: the blocks place but every beam stays dark");
    private static final ConfigValue<Boolean> PLANET_EFFICIENCY_ENABLED = SCHEMA.bool("planetEfficiencyEnabled",
            true, true, "true: the Stirling Generator's cold-face bonus scales with the local ambient read "
            + "through NeroTech's PlanetApi (Nerospace-aware; ambient -80 = +40%); false: a flat bonus in "
            + "every dimension");

    // --- failure ladder (Stage 3: the overload model) ----------------------------
    private static final ConfigValue<Integer> FAILURE_WARNING = SCHEMA.intRange("failureWarningPermille",
            600, 1, 1_000, true, "heat (permille of heat capacity) at which a reactor enters WARNING "
            + "(alarm state, full output)");
    private static final ConfigValue<Integer> FAILURE_UNSTABLE = SCHEMA.intRange("failureUnstablePermille",
            800, 1, 1_000, true, "heat (permille) at which a reactor enters UNSTABLE (output scaled by "
            + "unstablePenaltyPermille, WARN alert to the owner); must exceed failureWarningPermille");
    private static final ConfigValue<Integer> FAILURE_FAILURE = SCHEMA.intRange("failureFailurePermille",
            1_000, 1, 1_000, true, "heat (permille) at which a reactor FAILS (its failure action runs — "
            + "removal or an explosion); must exceed failureUnstablePermille; ignored when "
            + "overloadEnabled is false");
    private static final ConfigValue<Integer> FAILURE_HYSTERESIS = SCHEMA.intRange("failureHysteresisPermille",
            50, 0, 1_000, true, "a stage is only left downwards once heat drops this far (permille) BELOW "
            + "the threshold that entered it, so a reactor hovering at a threshold does not flap");
    private static final ConfigValue<Integer> FAILURE_MIN_DWELL = SCHEMA.intRange("failureMinDwellTicks",
            100, 0, 72_000, true, "minimum ticks a reactor stays in each stage before it may climb to the "
            + "next — no stage is ever skipped, so every escalation is telegraphed (100 = 5 s)");
    private static final ConfigValue<Integer> UNSTABLE_PENALTY = SCHEMA.intRange("unstablePenaltyPermille",
            600, 0, 1_000, true, "output multiplier (permille) applied while a reactor is UNSTABLE "
            + "(600 = 60% output); FAILURE always yields 0");

    // --- telemetry (anonymous crash reporting; CLIENT-LOCAL opt-out, not server-synced) -----
    private static final ConfigValue<Boolean> TELEMETRY_ENABLED = SCHEMA.bool("telemetryEnabled",
            true, false, "anonymous error reporting to the developers (Sentry, EU servers): NeroPower stack "
            + "traces + mod/MC/loader/OS/Java versions, installed mod list and a few NeroPower config values "
            + "only — never names, UUIDs, IPs, coordinates or world data; file paths are scrubbed of your "
            + "account name (POPIA/GDPR-compliant, see PRIVACY.md). Set false to opt out (takes effect on restart)");

    private NeroPowerConfig() {
    }

    // --- master toggles ----------------------------------------------------------

    /** Client-local telemetry opt-out (read before Sentry is ever initialised). */
    public static boolean telemetryEnabled() {
        return TELEMETRY_ENABLED.get();
    }

    public static boolean overloadEnabled() {
        return OVERLOAD_ENABLED.get();
    }

    /**
     * Raw {@code terrainDamageMode} setting: {@code "on"}, {@code "off"} or {@code "auto"} (anything
     * unrecognised reads as {@code "auto"}). Resolve it with {@link #terrainDamage(boolean)}.
     */
    public static String terrainDamageMode() {
        return TerrainPolicy.normalize(TERRAIN_DAMAGE_MODE.get());
    }

    /**
     * Whether a NeroPower failure may break blocks, resolved for this runtime by
     * {@link TerrainPolicy#resolve}: {@code "on"} / {@code "off"} are explicit; {@code "auto"} (the
     * default) is <b>off on a dedicated server</b> and on in singleplayer / LAN.
     *
     * @param dedicatedServer {@code MinecraftServer.isDedicatedServer()} for the running server
     */
    public static boolean terrainDamage(boolean dedicatedServer) {
        return TerrainPolicy.resolve(TERRAIN_DAMAGE_MODE.get(), dedicatedServer);
    }

    /** Hard cap on any failure blast radius (blocks, 1..16). */
    public static int failureRadiusCap() {
        return FAILURE_RADIUS_CAP.get();
    }

    public static boolean transmissionEnabled() {
        return TRANSMISSION_ENABLED.get();
    }

    public static boolean planetEfficiencyEnabled() {
        return PLANET_EFFICIENCY_ENABLED.get();
    }

    // --- failure ladder ----------------------------------------------------------

    public static int failureWarningPermille() {
        return FAILURE_WARNING.get();
    }

    public static int failureUnstablePermille() {
        return FAILURE_UNSTABLE.get();
    }

    public static int failureFailurePermille() {
        return FAILURE_FAILURE.get();
    }

    public static int failureHysteresisPermille() {
        return FAILURE_HYSTERESIS.get();
    }

    public static int failureMinDwellTicks() {
        return FAILURE_MIN_DWELL.get();
    }

    public static int unstablePenaltyPermille() {
        return UNSTABLE_PENALTY.get();
    }

    /**
     * The shared {@code neropower} schema, for feature packages that declare their own keys
     * ({@code fission.FissionConfig}, {@code storage.StorageConfig}, ...). Those classes must be
     * class-loaded BEFORE {@link #load()} runs, so their {@link ConfigValue}s are part of the schema
     * Core registers — {@code NeroPowerCommon.init()} touches each one first.
     */
    public static ConfigSchema schema() {
        return SCHEMA;
    }

    /** Register the schema with Core (reads/creates {@code neropower.properties}). Idempotent. */
    public static synchronized void load() {
        ConfigManager.register(SCHEMA);
    }
}
