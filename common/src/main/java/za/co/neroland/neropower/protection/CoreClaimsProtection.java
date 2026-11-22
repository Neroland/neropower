package za.co.neroland.neropower.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerolandcore.protection.ClaimChecks;

/**
 * Adds Neroland Core's land-protection checks ({@link ClaimChecks}, registered by claims mods such as
 * NeroColonies and NeroSecurity) on top of another {@link ProtectionCheck}. Ground is protected when
 * either says so, and a player may act only where both allow it. With no claims mod installed Core has
 * no checks, so this behaves exactly like the wrapped check. Nothing is stored or logged here.
 */
public final class CoreClaimsProtection implements ProtectionCheck {

    private final ProtectionCheck delegate;

    public CoreClaimsProtection(ProtectionCheck delegate) {
        this.delegate = delegate == null ? VanillaProtection.INSTANCE : delegate;
    }

    /** The check this one adds Core's claims to. */
    public ProtectionCheck delegate() {
        return delegate;
    }

    @Override
    public boolean mayInteract(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return delegate.mayInteract(player, level, pos) && ClaimChecks.mayInteract(player, level, pos);
    }

    @Override
    public boolean isProtected(ServerLevel level, BlockPos pos) {
        return delegate.isProtected(level, pos) || ClaimChecks.isProtected(level, pos);
    }
}
