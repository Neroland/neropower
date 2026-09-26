package za.co.neroland.neropower.telemetry;

import java.util.List;
import java.util.Locale;

import io.sentry.SentryEvent;

/**
 * Known-noise rules for {@link NeroPowerTelemetry}'s {@code beforeSend} gate: events that name NeroPower somewhere in
 * the stack but that NeroPower did not cause and cannot fix. Each rule is narrow and documented so a real NeroPower
 * bug is never silenced by accident.
 *
 * <ul>
 *   <li><b>Another mod's own exception.</b> The deepest cause was thrown inside a third-party mod's code
 *       (its innermost frame is outside the JDK, Minecraft, the mod loaders, the libraries they ship and the
 *       Neroland mods). A NeroPower frame on that stack only means another mod called into us — e.g. a recipe
 *       viewer constructing one of our entities while its own config was not loaded yet.</li>
 *   <li><b>World-data mismatch.</b> Vanilla's {@code BlockEntity.validateBlockState} rejecting saved block
 *       entity data whose block was replaced (another mod remapped or removed blocks in that chunk). Vanilla
 *       logs it and discards the stale block entity; the world keeps working, and nothing in NeroPower is wrong.</li>
 * </ul>
 */
final class TelemetryNoise {

    /** Frames from these packages count as "the platform", never as another mod's code. */
    private static final List<String> PLATFORM_PREFIXES = List.of(
            "java.", "javax.", "jdk.", "sun.", "com.sun.",
            "net.minecraft.", "com.mojang.",
            "net.neoforged.", "net.minecraftforge.", "cpw.mods.", "net.fabricmc.",
            "org.spongepowered.", "com.llamalad7.",
            "com.google.", "it.unimi.", "io.netty.", "org.apache.", "org.lwjgl.", "org.slf4j.",
            "io.sentry.",
            "za.co.neroland.");

    private TelemetryNoise() {
    }

    /** True when {@code event} matches a known-noise rule and should be dropped. */
    static boolean isNoise(SentryEvent event) {
        Throwable root = rootCause(event.getThrowable());
        if (root == null) {
            return false;
        }
        return isStaleBlockEntity(root) || thrownByAnotherMod(root);
    }

    private static boolean isStaleBlockEntity(Throwable root) {
        String message = root.getMessage();
        if (!(root instanceof IllegalStateException) || message == null) {
            return false;
        }
        if (!message.startsWith("Invalid block entity ") || !message.contains(" state at ")) {
            return false;
        }
        for (StackTraceElement frame : root.getStackTrace()) {
            if ("validateBlockState".equals(frame.getMethodName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean thrownByAnotherMod(Throwable root) {
        StackTraceElement[] frames = root.getStackTrace();
        if (frames.length == 0) {
            return false;
        }
        String thrower = frames[0].getClassName();
        if (thrower == null || thrower.isEmpty()) {
            return false;
        }
        String lower = thrower.toLowerCase(Locale.ROOT);
        for (String prefix : PLATFORM_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return false;
            }
        }
        // Mixin-generated or hidden classes carry no stable package; don't guess about them.
        return thrower.indexOf('.') > 0 && !thrower.startsWith("$");
    }

    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        for (int depth = 0; current != null && current.getCause() != null && current.getCause() != current
                && depth < 32; depth++) {
            current = current.getCause();
        }
        return current;
    }
}
