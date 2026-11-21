package za.co.neroland.neropower.radiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

/**
 * The contamination store's logic ({@link ContaminationMap}, which {@link ContaminationState} persists
 * behind Core's recovery guard): clamping, deterministic decay passes within the per-tick budget, prune
 * order and cap, gallery clearing, and a persistence round trip of the entries. Pure JVM (the guard
 * itself is Core's and covered by Core's tests).
 */
class ContaminationStateTest {

    private static long chunk(int x, int z) {
        return ((long) z << 32) | (x & 0xFFFFFFFFL); // ChunkPos.toLong layout
    }

    @Test
    void addClampsAndDropsZero() {
        ContaminationMap m = new ContaminationMap();
        assertEquals(1000, m.add(chunk(0, 0), 5000));
        assertEquals(0, m.add(chunk(0, 0), -2000));
        assertEquals(0, m.size());
    }

    @Test
    void decayAmountFollowsTheFormula() {
        assertEquals(5, ContaminationMap.decayAmount(1000, 5));
        assertEquals(1, ContaminationMap.decayAmount(10, 5), "at least one while above zero");
        assertEquals(0, ContaminationMap.decayAmount(500, 0), "0 permille disables decay");
    }

    /** Run decay for {@code ticks} ticks from tick 0 and return the result. */
    private static Map<Long, Short> run(int chunks, int budget, int ticks) {
        ContaminationMap m = new ContaminationMap();
        for (int i = 0; i < chunks; i++) {
            m.add(chunk(i, -i), 100 + (i * 37) % 900);
        }
        for (long t = 0; t < ticks; t++) {
            m.decayTick(t, 5, budget);
        }
        return m.snapshot();
    }

    @Test
    void decayIsDeterministic() {
        assertEquals(run(500, 64, 5000), run(500, 64, 5000));
    }

    @Test
    void aPassVisitsEveryChunkOnceWithinTheBudget() {
        ContaminationMap m = new ContaminationMap();
        for (int i = 0; i < 10; i++) {
            m.add(chunk(i, 0), 1000);
        }
        assertEquals(4, m.decayTick(0, 5, 4));
        assertEquals(4, m.decayTick(1, 5, 4));
        assertEquals(2, m.decayTick(2, 5, 4));
        assertEquals(0, m.decayTick(3, 5, 4), "pass finished until the next period");
        m.snapshot().values().forEach(v -> assertEquals(995, (int) v));
        assertEquals(4, m.decayTick(ContaminationMap.DECAY_PERIOD_TICKS, 5, 4), "next pass");
    }

    @Test
    void contaminationEventuallyDecaysAway() {
        ContaminationMap m = new ContaminationMap();
        m.add(chunk(3, 3), 1000);
        for (long t = 0; t < ContaminationMap.DECAY_PERIOD_TICKS * 2_000L; t += ContaminationMap.DECAY_PERIOD_TICKS) {
            m.decayTick(t, 5, 16);
        }
        assertEquals(0, m.size());
    }

    @Test
    void pruneDropsLowestFirstThenLowestKey() {
        ContaminationMap m = new ContaminationMap();
        m.add(chunk(1, 0), 50);
        m.add(chunk(2, 0), 10);
        m.add(chunk(3, 0), 10);
        m.add(chunk(4, 0), 900);
        assertEquals(2, m.prune(2));
        assertEquals(List.of(chunk(1, 0), chunk(4, 0)).stream().sorted().toList(),
                List.copyOf(m.snapshot().keySet()));
        assertEquals(0, m.prune(2));
    }

    @Test
    void clearRemovesListedChunks() {
        ContaminationMap m = new ContaminationMap();
        m.add(chunk(0, 0), 10);
        m.add(chunk(1, 0), 10);
        assertEquals(1, m.clear(List.of(chunk(0, 0), chunk(9, 9))));
        assertEquals(1, m.size());
    }

    @Test
    void entriesRoundTrip() {
        ContaminationMap m = new ContaminationMap();
        m.add(chunk(-5, 7), 321);
        m.add(chunk(100, -100), 999);
        Map<Long, Short> saved = m.snapshot();
        ContaminationMap loaded = new ContaminationMap();
        Map<Long, Short> raw = new TreeMap<>(saved);
        raw.put(chunk(0, 1), (short) 0); // a zero entry in a file is dropped
        loaded.load(raw);
        assertEquals(saved, loaded.snapshot());
        assertTrue(loaded.level(chunk(0, 1)) == 0);
    }
}
