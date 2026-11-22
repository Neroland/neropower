package za.co.neroland.neropower.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Rule test for the Surge Bank: it pools as the bank's fast tier. Its I/O adds on top of the
 * cells' {@code min x count} (each tier under its own cap), and a transfer is filled into and drained from
 * the surge banks first. Pure JVM (fake members, explicit caps).
 */
class SurgeBankFastTierTest {

    private static PooledEnergyStorage pool(long slowCap, long fastCap, PoolMember... members) {
        PooledEnergyStorage pool = new PooledEnergyStorage(() -> slowCap, () -> fastCap);
        pool.setMembers(List.of(members));
        return pool;
    }

    @Test
    void fastTierIoAddsOnTopOfTheCells() {
        PooledEnergyStorage p = pool(64_000, 256_000,
                FakeMember.cell(500_000, 1_000, 0), FakeMember.cell(500_000, 1_000, 0),
                FakeMember.surge(200_000, 50_000, 0));
        assertEquals(2_000L, p.slowIo());
        assertEquals(50_000L, p.fastIo());
        assertEquals(52_000L, p.effectiveIo());
        assertEquals(1, p.fastCount());
    }

    @Test
    void eachTierHasItsOwnCap() {
        PooledEnergyStorage p = pool(1_500, 60_000,
                FakeMember.cell(500_000, 1_000, 0), FakeMember.cell(500_000, 1_000, 0),
                FakeMember.surge(200_000, 50_000, 0), FakeMember.surge(200_000, 50_000, 0));
        assertEquals(1_500L, p.slowIo(), "bankMaxIoCap still caps the cells");
        assertEquals(60_000L, p.fastIo(), "bankFastTierIoCap caps the surge banks");
    }

    @Test
    void insertFillsTheSurgeBankFirst() {
        FakeMember cell = FakeMember.cell(500_000, 1_000, 0);
        FakeMember surge = FakeMember.surge(200_000, 50_000, 0);
        PooledEnergyStorage p = pool(64_000, 256_000, cell, surge);
        assertEquals(30_000L, p.insert(30_000, false));
        assertEquals(30_000L, surge.buffer.getAmount());
        assertEquals(0L, cell.buffer.getAmount(), "the cells only take what the fast tier could not");
        assertEquals(51_000L, p.insert(100_000, false), "fast I/O plus slow I/O in one transfer");
        assertEquals(80_000L, surge.buffer.getAmount());
        assertEquals(1_000L, cell.buffer.getAmount());
    }

    @Test
    void extractDrainsTheSurgeBankFirst() {
        FakeMember cell = FakeMember.cell(500_000, 1_000, 100_000);
        FakeMember surge = FakeMember.surge(200_000, 50_000, 10_000);
        PooledEnergyStorage p = pool(64_000, 256_000, cell, surge);
        assertEquals(10_500L, p.extract(10_500, false));
        assertEquals(0L, surge.buffer.getAmount());
        assertEquals(99_500L, cell.buffer.getAmount());
    }

    @Test
    void aBankOfCellsOnlyBehavesAsBefore() {
        PooledEnergyStorage p = pool(64_000, 256_000,
                FakeMember.cell(500_000, 1_000, 0), FakeMember.cell(500_000, 4_000, 0));
        assertEquals(2_000L, p.effectiveIo(), "min(cell I/O) x count, no fast tier");
        assertEquals(2_000L, p.insert(10_000, false));
    }
}
