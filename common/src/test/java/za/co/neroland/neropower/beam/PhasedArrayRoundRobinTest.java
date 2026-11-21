package za.co.neroland.neropower.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

/**
 * Rule test for the Phased Array Emitter: one target served a pass, in list order, round-robin; blocked
 * targets are skipped (each tried at most once a pass); all blocked serves none; reordering works; and
 * it loses less over distance than a transmitter at the defaults (1 vs 3 permille a block).
 */
class PhasedArrayRoundRobinTest {

    @Test
    void servesEachTargetInTurn() {
        List<Integer> served = new ArrayList<>();
        int cursor = 0;
        for (int pass = 0; pass < 6; pass++) {
            int s = PhasedArrayMath.serveNext(cursor, 3, i -> true);
            served.add(s);
            cursor = PhasedArrayMath.nextCursor(cursor, s, 3);
        }
        assertEquals(List.of(0, 1, 2, 0, 1, 2), served);
    }

    @Test
    void skipsBlockedAndTriesEachOnce() {
        int[] tries = new int[4];
        int s = PhasedArrayMath.serveNext(1, 4, i -> {
            tries[i]++;
            return i == 0;
        });
        assertEquals(0, s);
        assertEquals(1, tries[1]);
        assertEquals(1, tries[2]);
        assertEquals(1, tries[3]);
        assertEquals(1, tries[0]);
        int none = PhasedArrayMath.serveNext(0, 4, i -> false);
        assertEquals(-1, none);
        assertEquals(2, PhasedArrayMath.nextCursor(2, -1, 4));
        assertEquals(-1, PhasedArrayMath.serveNext(0, 0, i -> true));
    }

    @Test
    void reorderingMovesTargets() {
        BeamLinkList list = new BeamLinkList();
        for (int i = 0; i < 3; i++) {
            list.add(BeamTarget.of(new BlockPos(i, 0, 0), "minecraft:overworld"), 4);
        }
        assertTrue(list.move(2, true));
        assertEquals(new BlockPos(2, 0, 0), list.get(1).target().pos());
        assertTrue(!list.move(0, true) && !list.move(2, false));
    }

    @Test
    void lowerLossThanATransmitter() {
        assertTrue(BeamMath.lossFactor(128, 1) > BeamMath.lossFactor(128, 3));
        assertTrue(BeamMath.lossFactor(384, 1) > 0.65);
    }

    @Test
    void directionStepsAreSixteenths() {
        assertEquals(16, PhasedArrayMath.directionStep(5, 5));
        assertEquals(-8, PhasedArrayMath.directionStep(-1, 2));
        assertEquals(0, PhasedArrayMath.directionStep(1, 0));
    }
}
