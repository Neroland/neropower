package za.co.neroland.neropower.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.data.SaveKeys;

/**
 * Rule test for the Beam Splitter (decision 008 annex): splitting never creates energy (lens loss
 * rounded up, allocations floored, shares capped at 1000 in total, even from a tampered save), the
 * lens table parses with defaults, and the link list saves and loads without any owner key.
 */
class BeamSplitterRulesTest {

    private static BeamTarget at(int x) {
        return BeamTarget.of(new BlockPos(x, 64, 0), "minecraft:overworld");
    }

    @Test
    void splittingNeverCreatesEnergy() {
        int[][] shareSets = {{1000}, {500, 500}, {333, 333, 334}, {250, 250, 250, 250}, {999, 999}, {0, 0}, {700, 700, 700}};
        long[] inputs = {0L, 1L, 7L, 999L, 8_000L, 16_000L, 123_457L};
        int[] lossTable = SplitMath.parseLensLoss(SplitMath.DEFAULT_LENS_LOSS);
        for (int loss : lossTable) {
            for (long arrived : inputs) {
                long kept = arrived - SplitMath.lensLoss(arrived, loss);
                assertTrue(kept >= 0 && kept <= arrived);
                for (int[] shares : shareSets) {
                    long sum = 0L;
                    for (long part : SplitMath.allocate(kept, shares)) {
                        assertTrue(part >= 0L);
                        sum += part;
                    }
                    assertTrue(sum <= kept, "allocated " + sum + " of " + kept);
                }
            }
        }
    }

    @Test
    void lensLossRoundsUpAndNoLensPassesNothing() {
        assertEquals(1L, SplitMath.lensLoss(1L, 10));
        assertEquals(400L, SplitMath.lensLoss(8_000L, 50));
        int[] table = SplitMath.parseLensLoss(SplitMath.DEFAULT_LENS_LOSS);
        assertEquals(1000, table[SplitMath.Lens.NONE.ordinal()]);
        assertEquals(50, table[SplitMath.Lens.QUARTZ.ordinal()]);
        assertEquals(10, table[SplitMath.Lens.VOID.ordinal()]);
        assertEquals(8_000L, SplitMath.lensLoss(8_000L, table[SplitMath.Lens.NONE.ordinal()]));
    }

    @Test
    void badLensConfigFallsBackPerEntry() {
        int[] table = SplitMath.parseLensLoss("quartz=abc,ember=2000,void=5");
        assertEquals(50, table[SplitMath.Lens.QUARTZ.ordinal()]);
        assertEquals(1000, table[SplitMath.Lens.EMBER.ordinal()]);
        assertEquals(5, table[SplitMath.Lens.VOID.ordinal()]);
        assertEquals(SplitMath.Lens.GLACITE, SplitMath.Lens.byItemPath("glacite_focus_lens"));
        assertEquals(SplitMath.Lens.NONE, SplitMath.Lens.byItemPath("diamond"));
    }

    @Test
    void sharesAreCappedAtTheWhole() {
        BeamLinkList links = new BeamLinkList();
        assertTrue(links.add(at(1), 4));
        assertTrue(links.add(at(2), 4));
        assertEquals(700, links.setShare(0, 700));
        assertEquals(300, links.setShare(1, 900));
        assertEquals(1000, links.totalShare());
        assertEquals(-1, links.setShare(5, 100));
        assertFalse(links.add(at(1), 4), "duplicate");
        assertTrue(links.add(at(3), 4));
        assertTrue(links.add(at(4), 4));
        assertFalse(links.add(at(5), 4), "full");
    }

    @Test
    void linksRoundTripWithoutOwnerKeysAndTamperedSharesAreCapped() {
        BeamLinkList links = new BeamLinkList();
        links.add(at(10), 4);
        links.add(at(20), 4);
        links.setShare(0, 400);
        links.setShare(1, 600);
        Map<String, Object> saved = new HashMap<>();
        links.write(SaveKeys.into(saved));
        for (String key : saved.keySet()) {
            assertFalse(key.contains("Owner"), key);
        }
        BeamLinkList back = new BeamLinkList();
        back.read(SaveKeys.of(saved), 8);
        assertEquals(2, back.size());
        assertEquals(at(20), back.get(1).target());
        assertEquals(600, back.get(1).share());

        saved.put("Link0Share", 900);
        saved.put("Link1Share", 900);
        back.read(SaveKeys.of(saved), 8);
        assertEquals(1000, back.totalShare());
    }

    @Test
    void aSaveWithoutLinksReadsEmpty() {
        BeamLinkList back = new BeamLinkList();
        back.read(SaveKeys.of(new HashMap<>()), 8);
        assertTrue(back.isEmpty());
    }

    @Test
    void existingTransmitterLinksLoadUnchanged() {
        // 0.3.x transmitter / relay layout: Linked + TargetX/Y/Z + TargetDim, owner beside it.
        Map<String, Object> old = new HashMap<>();
        old.put("Linked", true);
        old.put("TargetX", 12);
        old.put("TargetY", 70);
        old.put("TargetZ", -4);
        old.put("TargetDim", "minecraft:overworld");
        old.put("LinkOwnerMost", 1L);
        old.put("LinkOwnerLeast", 2L);
        BeamTarget target = BeamTarget.read(SaveKeys.of(old));
        assertEquals(BeamTarget.of(new BlockPos(12, 70, -4), "minecraft:overworld"), target);
        assertEquals(new java.util.UUID(1L, 2L), SaveKeys.readLinkOwner(SaveKeys.of(old)));
        // The splitter's keys never collide with the transmitter's.
        BeamLinkList none = new BeamLinkList();
        none.read(SaveKeys.of(old), 8);
        assertTrue(none.isEmpty());
    }
}
