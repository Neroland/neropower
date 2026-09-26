package za.co.neroland.neropower.protection;

/**
 * Holder of the active {@link ProtectionCheck}. Defaults to {@link VanillaProtection}; a future
 * Core claim API (or an integration mod) installs its own with {@link #set} during mod construction,
 * before any world loads. Main / mod-loading thread only — the field is read on the server thread
 * and written once at startup, so no synchronisation is needed.
 */
public final class Protection {

    private static ProtectionCheck check = VanillaProtection.INSTANCE;

    private Protection() {
    }

    /** The active check. */
    public static ProtectionCheck get() {
        return check;
    }

    /** Install a richer check; {@code null} restores {@link VanillaProtection}. */
    public static void set(ProtectionCheck newCheck) {
        check = newCheck == null ? VanillaProtection.INSTANCE : newCheck;
    }
}
