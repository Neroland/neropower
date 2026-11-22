package za.co.neroland.neropower.turbine;

import java.util.List;
import java.util.Objects;

/**
 * The pure rule behind a structure's <b>shared</b> port tanks: every port of a reactor or
 * turbine keeps its own tank on disk, and every face of every port fills and drains the whole set as if it
 * were one tank. Pure JVM (generic over the fluid identity), so conservation is locked by tests.
 *
 * <p><b>One fluid per pool.</b> The pool's fluid is the fluid of its first non-empty slot; a fill of any
 * other fluid is refused, so a loader's transactional undo of a fill (a plain drain) always takes back the
 * same fluid. Fills top up slots that already hold the fluid first, then empty slots that accept it.
 * Drains take only the pool's fluid. Nothing is created or voided: every fill and drain is the sum of
 * what the slots themselves accepted or gave.
 */
public final class PortPool {

    private PortPool() {
    }

    /** One port tank as the pool sees it. */
    public interface Slot<F> {
        /** The fluid held; meaningless when {@link #slotEmpty()}. */
        F slotFluid();

        /** Whether the slot holds nothing. */
        boolean slotEmpty();

        long slotAmount();

        long slotCapacity();

        /** Whether the slot would take {@code fluid} at all (ignoring room). */
        boolean slotAccepts(F fluid);

        long slotFill(F fluid, long amount, boolean simulate);

        long slotDrain(long amount, boolean simulate);
    }

    /** The pool's fluid (first non-empty slot), or null when every slot is empty. */
    public static <F> F fluid(List<? extends Slot<F>> slots) {
        for (Slot<F> slot : slots) {
            if (!slot.slotEmpty()) {
                return slot.slotFluid();
            }
        }
        return null;
    }

    /** mB of the pool's own fluid. */
    public static <F> long amount(List<? extends Slot<F>> slots) {
        F fluid = fluid(slots);
        return fluid == null ? 0L : amountOf(slots, fluid);
    }

    /** mB of {@code fluid} held across every slot (also counts slots behind the pool's first fluid). */
    public static <F> long amountOf(List<? extends Slot<F>> slots, F fluid) {
        long total = 0L;
        for (Slot<F> slot : slots) {
            if (!slot.slotEmpty() && Objects.equals(slot.slotFluid(), fluid)) {
                total += Math.max(0L, slot.slotAmount());
            }
        }
        return total;
    }

    /** Summed capacity. */
    public static <F> long capacity(List<? extends Slot<F>> slots) {
        long total = 0L;
        for (Slot<F> slot : slots) {
            total += Math.max(0L, slot.slotCapacity());
        }
        return total;
    }

    /** Room for {@code fluid}: what {@link #fill} would take (simulated). */
    public static <F> long roomFor(List<? extends Slot<F>> slots, F fluid) {
        return fill(slots, fluid, Long.MAX_VALUE, true);
    }

    /** Fill up to {@code amount} of {@code fluid}; refused (0) while the pool holds another fluid. */
    public static <F> long fill(List<? extends Slot<F>> slots, F fluid, long amount, boolean simulate) {
        if (fluid == null || amount <= 0L) {
            return 0L;
        }
        F held = fluid(slots);
        if (held != null && !Objects.equals(held, fluid)) {
            return 0L;
        }
        long left = amount;
        // Slots already holding the fluid first, then empty slots that take it.
        for (int pass = 0; pass < 2 && left > 0L; pass++) {
            for (Slot<F> slot : slots) {
                if (left <= 0L) {
                    break;
                }
                boolean holds = !slot.slotEmpty() && Objects.equals(slot.slotFluid(), fluid);
                boolean empty = slot.slotEmpty() && slot.slotAccepts(fluid);
                if (pass == 0 ? !holds : !empty) {
                    continue;
                }
                long put = Math.max(0L, Math.min(left, slot.slotFill(fluid, left, simulate)));
                left -= put;
            }
        }
        return amount - left;
    }

    /** Drain up to {@code amount} of the pool's fluid. */
    public static <F> long drain(List<? extends Slot<F>> slots, long amount, boolean simulate) {
        F fluid = fluid(slots);
        return fluid == null ? 0L : drainFluid(slots, fluid, amount, simulate);
    }

    /** Drain up to {@code amount} of {@code fluid} from whichever slots hold it. */
    public static <F> long drainFluid(List<? extends Slot<F>> slots, F fluid, long amount, boolean simulate) {
        if (fluid == null || amount <= 0L) {
            return 0L;
        }
        long left = amount;
        for (Slot<F> slot : slots) {
            if (left <= 0L) {
                break;
            }
            if (slot.slotEmpty() || !Objects.equals(slot.slotFluid(), fluid)) {
                continue;
            }
            long took = Math.max(0L, Math.min(left, slot.slotDrain(left, simulate)));
            left -= took;
        }
        return amount - left;
    }
}
