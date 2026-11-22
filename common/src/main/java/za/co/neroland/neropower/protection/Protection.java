package za.co.neroland.neropower.protection;

/**
 * Holder of the active {@link ProtectionCheck}. Defaults to {@link VanillaProtection} with Neroland
 * Core's claim checks added ({@link CoreClaimsProtection}), so any claims mod that registers with Core's
 * {@code ClaimChecks} protects its land from NeroPower too. An integration mod can install a richer base
 * check with {@link #set} during mod construction, before any world loads; Core's claims stay on top. Main / mod-loading thread only — the field is read on the server thread
 * and written once at startup, so no synchronisation is needed.
 */
public final class Protection {

    private static ProtectionCheck check = new CoreClaimsProtection(VanillaProtection.INSTANCE);

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

    /** Install a richer base check; {@code null} restores {@link VanillaProtection}. Core's claims still apply. */
    public static void set(ProtectionCheck newCheck) {
        check = new CoreClaimsProtection(newCheck == null ? VanillaProtection.INSTANCE : newCheck);
    }
}
