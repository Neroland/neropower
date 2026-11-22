package za.co.neroland.neropower.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Adversarial accounting: joining or leaving a bank pool moves no energy, and breaking and
 * rebuilding a Storage Ring never yields more than it held (a smaller ring loses the excess). Pure JVM.
 */
class NoEnergyCreatedTest {

    private static long total(FakeMember... members) {
        long sum = 0L;
        for (FakeMember m : members) {
            sum += m.buffer.getAmount();
        }
        return sum;
    }

    @Test
    void poolJoinAndLeaveMoveNothing() {
        FakeMember a = FakeMember.cell(500_000, 1_000, 123_456);
        FakeMember b = FakeMember.surge(200_000, 50_000, 77_000);
        FakeMember c = FakeMember.cell(2_000_000, 4_000, 999);
        long before = total(a, b, c);
        PooledEnergyStorage pool = new PooledEnergyStorage(() -> 64_000L, () -> 256_000L);
        for (int i = 0; i < 50; i++) {
            pool.setMembers(i % 3 == 0 ? List.of(a, b, c) : i % 3 == 1 ? List.of(b) : List.of());
            assertEquals(before, total(a, b, c), "a rescan alone never changes a member");
        }
        pool.setMembers(List.of(a, b, c));
        assertEquals(before, pool.getAmount(), "the pool reads exactly the members' sum");
        pool.setMembers(List.of(a));
        assertEquals(a.buffer.getAmount(), pool.getAmount());
    }

    @Test
    void transfersConserveEnergy() {
        FakeMember a = FakeMember.cell(500_000, 1_000, 10_000);
        FakeMember b = FakeMember.surge(200_000, 50_000, 0);
        PooledEnergyStorage pool = new PooledEnergyStorage(() -> 64_000L, () -> 256_000L);
        pool.setMembers(List.of(a, b));
        long in = pool.insert(40_000, false);
        long out = pool.extract(15_000, false);
        assertEquals(10_000L + in - out, total(a, b));
    }

    @Test
    void ringBreakAndRebuildNeverCreatesEnergy() {
        SmesCore core = new SmesCore();
        core.applyStructure(true, 16, 50_000_000L);
        core.beginTick(Long.MAX_VALUE);
        assertEquals(500_000_000L, core.insert(500_000_000L, false));
        long held = core.energy();

        core.applyStructure(false, 0, 50_000_000L); // a coil broken: unformed
        assertEquals(held, core.energy(), "unforming keeps the charge");
        core.beginTick(Long.MAX_VALUE);
        assertEquals(0L, core.extract(1, false), "no I/O while unformed");
        assertEquals(0L, core.insert(1, false));

        core.applyStructure(true, 16, 50_000_000L); // rebuilt the same
        assertEquals(held, core.energy());

        long lost = core.applyStructure(true, 8, 50_000_000L); // rebuilt smaller
        assertEquals(400_000_000L, core.energy(), "capped to the smaller ring");
        assertEquals(100_000_000L, lost);

        core.applyStructure(true, 56, 50_000_000L); // rebuilt bigger: the lost part does not come back
        assertEquals(400_000_000L, core.energy());
        assertTrue(core.energy() <= held);
    }

    @Test
    void largestRingPassesTheOldGaugeLimit() {
        SmesCore core = new SmesCore();
        core.applyStructure(true, 56, 50_000_000L);
        assertEquals(2_800_000_000L, core.capacity());
    }
}
