package za.co.neroland.neropower.platform;

import java.util.List;

/**
 * Loader-specific facts the common module may depend on, resolved per loader via {@link Services} (loaded lazily by telemetry, only when it is enabled)
 * ({@link java.util.ServiceLoader}). Kept intentionally small — currently only what telemetry needs
 * (release tag, environment, dist, and the loaded-mod list for crash triage). Grow as needed.
 */
public interface IPlatformHelper {

    /** Human-readable platform name ("Fabric" / "Forge" / "NeoForge"). */
    String getPlatformName();

    /** True when running in a development (IDE/dev) environment. */
    boolean isDevelopmentEnvironment();

    /** True on the physical client. */
    boolean isClient();

    /** NeroPower's version string (for telemetry release tags), or "unknown" if unavailable. */
    String getModVersion();

    /** Ids + versions of every loaded mod ("modid version"), sorted — public manifest strings only. */
    List<String> getLoadedModIds();
}
