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

    /**
     * The menu-button rule shared by every NeroPower screen: on the server, {@code player} may act on the
     * block entity {@code be} only where the active check lets them interact. Client side always true (the
     * server decides).
     */
    public static boolean mayUse(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.level.block.entity.BlockEntity be) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return true;
        }
        return be.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && check.mayInteract(serverPlayer, level, be.getBlockPos());
    }

    /** Install a richer check; {@code null} restores {@link VanillaProtection}. */
    public static void set(ProtectionCheck newCheck) {
        check = newCheck == null ? VanillaProtection.INSTANCE : newCheck;
    }
}
