package za.co.neroland.neropower.telemetry;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;

import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.config.NeroPowerConfig;
import za.co.neroland.neropower.platform.IPlatformHelper;
import za.co.neroland.neropower.platform.Services;

/**
 * Crash/error reporting for NeroPower via Sentry (EU ingest), mirroring {@code NeroTechTelemetry} and the
 * rest of the Neroland family. Built for the CurseForge disclosure rule and POPIA/GDPR data-minimisation:
 *
 * <ul>
 *   <li><b>Opt-out:</b> gated on {@code telemetryEnabled} in {@link NeroPowerConfig} (default ON,
 *       client-local, disclosed in {@code PRIVACY.md}). Set it false to stop reporting (takes effect on
 *       restart). The key is read <i>before</i> Sentry is initialised, so an opted-out install never
 *       contacts Sentry at all.</li>
 *   <li><b>NeroPower errors only:</b> {@code beforeSend} drops any event whose stack trace does not
 *       touch {@code za.co.neroland.neropower}.</li>
 *   <li><b>No personal data:</b> no IP, no hostname, no username, no UUID, no coordinates; OS-account
 *       names are scrubbed from file paths. Payload: stack trace + mod/MC/loader/OS/Java versions.</li>
 *   <li><b>Bounded volume:</b> per-session de-duplication + a hard cap of
 *       {@value #MAX_EVENTS_PER_SESSION} events per session.</li>
 * </ul>
 *
 * <p>{@link #init()} is called once from {@code NeroPowerCommon.init()} after the config is loaded, and
 * reads loader facts through the {@link IPlatformHelper} service. Full disclosure text: {@code PRIVACY.md}.
 */
public final class NeroPowerTelemetry {

    /**
     * Sentry DSN — a public client key (write-only ingest), safe to ship in the jar. This is
     * NeroPower's own Sentry project (EU region, {@code de.sentry.io}). If blanked, telemetry
     * initialises to a no-op so events are never sent to the wrong project.
     */
    private static final String DSN =
            "https://996109ce14ac81677fcda379fbaa4730@o4511183823241216.ingest.de.sentry.io/4512151430496336";

    private static final String PACKAGE_MARKER = "za.co.neroland.neropower";
    private static final int MAX_EVENTS_PER_SESSION = 10;
    private static final int MAX_MODS_REPORTED = 300;
    private static final Pattern USER_PATH =
            Pattern.compile("(?i)(?:[A-Z]:)?[/\\\\](?:Users|home)[/\\\\][^/\\\\\\s:;,'\"]+");

    private static volatile boolean active;
    private static final AtomicInteger eventsSent = new AtomicInteger();
    private static final Set<String> seenFingerprints = ConcurrentHashMap.newKeySet();
    private static SentryLogAppender appender;
    private static IPlatformHelper platform;

    private NeroPowerTelemetry() {
    }

    /** Called once from common init, after {@link NeroPowerConfig#load()}. Starts unless opted out. */
    public static void init() {
        if (!NeroPowerConfig.telemetryEnabled()) {
            NeroPowerCommon.LOGGER.info("[NeroPower] Telemetry disabled (telemetryEnabled=false); "
                    + "no error reports will be sent.");
            return;
        }
        try {
            start();
        } catch (RuntimeException | LinkageError e) {
            // Telemetry must never take the mod down with it.
            NeroPowerCommon.LOGGER.warn("[NeroPower] Telemetry failed to start; continuing without it ({}).",
                    e.getClass().getSimpleName());
        }
    }

    private static synchronized void start() {
        if (active) {
            return;
        }
        if (DSN.isBlank()) {
            NeroPowerCommon.LOGGER.info("[NeroPower] Telemetry is enabled but no Sentry DSN is configured; "
                    + "error reporting is inactive.");
            return;
        }
        platform = Services.load(IPlatformHelper.class);
        String modVersion = platform.getModVersion();
        boolean dev = platform.isDevelopmentEnvironment();
        Sentry.init(options -> {
            options.setDsn(DSN);
            options.setRelease("neropower@" + modVersion);
            options.setEnvironment(dev ? "development" : environmentOf(modVersion));
            options.setSendDefaultPii(false);          // POPIA/GDPR: never the sender's IP/identity
            options.setAttachServerName(false);        // hostname is identifying
            options.setEnableUncaughtExceptionHandler(true);
            options.setEnableAutoSessionTracking(false);
            options.setBeforeSend((event, hint) -> filterAndScrub(event));
        });
        Sentry.configureScope(scope -> {
            scope.setTag("loader", platform.getPlatformName().toLowerCase(Locale.ROOT));
            scope.setTag("dist", platform.isClient() ? "client" : "dedicated_server");
            scope.setTag("runtime", dev ? "development" : "production");
            scope.setTag("mc_version", minecraftVersion());
            scope.setTag("cfg_overload", Boolean.toString(NeroPowerConfig.overloadEnabled()));
            scope.setTag("cfg_terrain_damage", NeroPowerConfig.terrainDamageMode());
            scope.setTag("cfg_transmission", Boolean.toString(NeroPowerConfig.transmissionEnabled()));
            try {
                List<String> mods = platform.getLoadedModIds();
                if (mods != null && !mods.isEmpty()) {
                    if (mods.size() > MAX_MODS_REPORTED) {
                        mods = mods.subList(0, MAX_MODS_REPORTED);
                    }
                    scope.setTag("mod_count", Integer.toString(mods.size()));
                    java.util.Map<String, Object> modContext = new java.util.HashMap<>();
                    modContext.put("count", mods.size());
                    modContext.put("ids", mods);
                    scope.setContexts("loaded_mods", modContext);
                }
            } catch (RuntimeException | LinkageError e) {
                // mod list not available this early — skip it
            }
        });
        Sentry.startSession();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Sentry.endSession();
                Sentry.flush(2000L);
            } catch (RuntimeException ignored) {
                // best-effort flush on shutdown
            }
        }, "neropower-sentry-shutdown"));
        if (appender == null) {
            appender = new SentryLogAppender();
            appender.start();
            ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).addAppender(appender);
        }
        active = true;
        NeroPowerCommon.LOGGER.info("[NeroPower] Telemetry enabled (anonymous error reports, EU servers; "
                + "opt out via telemetryEnabled=false in config/neropower.properties).");
    }

    /** A non-identifying breadcrumb that rides along with the next error report. No-op when off. */
    public static void breadcrumb(String category, String message) {
        if (!active) {
            return;
        }
        Breadcrumb crumb = new Breadcrumb();
        crumb.setType("default");
        crumb.setCategory(category);
        crumb.setLevel(SentryLevel.INFO);
        crumb.setMessage(scrub(message));
        Sentry.addBreadcrumb(crumb);
    }

    private static String environmentOf(String version) {
        String v = version.toLowerCase(Locale.ROOT);
        if (v.contains("-alpha")) {
            return "alpha";
        }
        if (v.contains("-beta")) {
            return "beta";
        }
        return "production";
    }

    private static String minecraftVersion() {
        try {
            for (String mod : platform.getLoadedModIds()) {
                if (mod.startsWith("minecraft ")) {
                    return mod.substring("minecraft ".length());
                }
            }
        } catch (RuntimeException | LinkageError e) {
            // fall through
        }
        return "unknown";
    }

    static boolean isActive() {
        return active;
    }

    /** True if any frame of the throwable (or its causes/suppressed) is NeroPower code. */
    static boolean touchesNeroPower(Throwable t) {
        int depth = 0;
        while (t != null && depth++ < 16) {
            for (StackTraceElement el : t.getStackTrace()) {
                if (el.getClassName().startsWith(PACKAGE_MARKER)) {
                    return true;
                }
            }
            for (Throwable s : t.getSuppressed()) {
                if (touchesNeroPower(s)) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    static void capture(Throwable t) {
        if (!active || t == null) {
            return;
        }
        Sentry.captureException(t);
    }

    /** Capture a handled exception if it is still clearly from NeroPower code. */
    public static void captureHandledException(Throwable t) {
        if (t != null && touchesNeroPower(t)) {
            capture(t);
        }
    }

    /** Capture a handled exception with a non-identifying source label for triage. */
    public static void captureHandledException(Throwable t, String source, String operation) {
        if (!active || t == null || !touchesNeroPower(t)) {
            return;
        }
        Sentry.withScope(scope -> {
            scope.setTag("handled", "true");
            scope.setTag("source", source);
            scope.setExtra("operation", operation);
            Sentry.captureException(t);
        });
    }

    static void captureMessage(String message) {
        if (!active) {
            return;
        }
        String scrubbed = scrub(message);
        if (scrubbed.length() > 4000) {
            scrubbed = scrubbed.substring(0, 4000) + "…[truncated]";
        }
        SentryEvent event = new SentryEvent();
        event.setLevel(SentryLevel.FATAL);
        Message msg = new Message();
        msg.setFormatted(scrubbed);
        event.setMessage(msg);
        Sentry.captureEvent(event);
    }

    private static SentryEvent filterAndScrub(SentryEvent event) {
        if (!isNeroPowerRelated(event)) {
            return null;
        }
        if (TelemetryNoise.isNoise(event)) {
            return null; // another mod's bug, or stale world data vanilla already discards
        }
        String fingerprint = fingerprintOf(event);
        if (!seenFingerprints.add(fingerprint)) {
            return null;
        }
        if (eventsSent.incrementAndGet() > MAX_EVENTS_PER_SESSION) {
            return null;
        }
        event.setUser(null);
        event.setServerName(null);
        List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            for (SentryException ex : exceptions) {
                String value = ex.getValue();
                if (value != null) {
                    ex.setValue(scrub(value));
                }
                SentryStackTrace st = ex.getStacktrace();
                List<SentryStackFrame> frames = st == null ? null : st.getFrames();
                if (frames != null) {
                    for (SentryStackFrame frame : frames) {
                        frame.setAbsPath(null);
                    }
                }
            }
        }
        Message message = event.getMessage();
        if (message != null && message.getFormatted() != null) {
            message.setFormatted(scrub(message.getFormatted()));
        }
        return event;
    }

    private static boolean isNeroPowerRelated(SentryEvent event) {
        Throwable t = event.getThrowable();
        if (t != null && touchesNeroPower(t)) {
            return true;
        }
        List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            for (SentryException ex : exceptions) {
                SentryStackTrace st = ex.getStacktrace();
                List<SentryStackFrame> frames = st == null ? null : st.getFrames();
                if (frames == null) {
                    continue;
                }
                for (SentryStackFrame frame : frames) {
                    String module = frame.getModule();
                    if (module != null && module.startsWith(PACKAGE_MARKER)) {
                        return true;
                    }
                }
            }
        }
        Message message = event.getMessage();
        String formatted = message == null ? null : message.getFormatted();
        return formatted != null && formatted.contains(PACKAGE_MARKER);
    }

    private static String fingerprintOf(SentryEvent event) {
        StringBuilder sb = new StringBuilder();
        List<SentryException> exceptions = event.getExceptions();
        Message message = event.getMessage();
        if (exceptions != null) {
            for (SentryException ex : exceptions) {
                sb.append(ex.getType()).append('|');
                SentryStackTrace st = ex.getStacktrace();
                List<SentryStackFrame> frames = st == null ? null : st.getFrames();
                if (frames != null) {
                    for (SentryStackFrame frame : frames) {
                        String module = frame.getModule();
                        if (module != null && module.startsWith(PACKAGE_MARKER)) {
                            sb.append(module).append(':').append(frame.getLineno()).append('|');
                        }
                    }
                }
            }
        } else if (message != null) {
            String formatted = message.getFormatted();
            if (formatted != null) {
                sb.append(formatted, 0, Math.min(200, formatted.length()));
            }
        }
        return sb.toString();
    }

    /** Replaces home-directory paths (which contain the OS account name) with a neutral marker. */
    static String scrub(String text) {
        return USER_PATH.matcher(text).replaceAll("/~");
    }
}
