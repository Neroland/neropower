package za.co.neroland.neropower.protection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.protection.ClaimCheck;
import za.co.neroland.nerolandcore.protection.ClaimChecks;

/** Core's claim checks are added on top of NeroPower's own protection check, never in place of it. */
class CoreClaimsProtectionTest {

    private static final BlockPos POS = new BlockPos(1, 64, 1);

    /** A base check that allows everything and protects nothing. */
    private static final ProtectionCheck OPEN = new ProtectionCheck() {
        @Override
        public boolean mayInteract(ServerPlayer player, ServerLevel level, BlockPos pos) {
            return true;
        }

        @Override
        public boolean isProtected(ServerLevel level, BlockPos pos) {
            return false;
        }
    };

    /** A base check that refuses everything and protects everything. */
    private static final ProtectionCheck CLOSED = new ProtectionCheck() {
        @Override
        public boolean mayInteract(ServerPlayer player, ServerLevel level, BlockPos pos) {
            return false;
        }

        @Override
        public boolean isProtected(ServerLevel level, BlockPos pos) {
            return true;
        }
    };

    @AfterEach
    void clear() {
        ClaimChecks.clearForTests();
    }

    @Test
    void withNoClaimsModItBehavesLikeTheBaseCheck() {
        assertTrue(new CoreClaimsProtection(OPEN).mayInteract(null, null, POS));
        assertFalse(new CoreClaimsProtection(OPEN).isProtected(null, POS));
        assertFalse(new CoreClaimsProtection(CLOSED).mayInteract(null, null, POS));
        assertTrue(new CoreClaimsProtection(CLOSED).isProtected(null, POS));
    }

    @Test
    void aClaimProtectsGroundTheBaseCheckLeavesOpen() {
        ClaimChecks.register(new ClaimCheck() {
            @Override
            public boolean isProtected(ServerLevel level, BlockPos pos) {
                return true;
            }

            @Override
            public boolean mayInteract(ServerPlayer player, ServerLevel level, BlockPos pos) {
                return false;
            }
        });
        CoreClaimsProtection check = new CoreClaimsProtection(OPEN);
        assertTrue(check.isProtected(null, POS), "radiation and blasts leave claimed land alone");
        assertFalse(check.mayInteract(null, null, POS), "a stranger may not link or configure in a claim");
    }

    @Test
    void theDefaultCheckIncludesCoreClaims() {
        assertTrue(Protection.get() instanceof CoreClaimsProtection);
        Protection.set(OPEN);
        assertTrue(Protection.get() instanceof CoreClaimsProtection);
        Protection.set(null);
        assertTrue(((CoreClaimsProtection) Protection.get()).delegate() == VanillaProtection.INSTANCE);
    }
}
