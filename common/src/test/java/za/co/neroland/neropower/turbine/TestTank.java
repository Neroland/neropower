package za.co.neroland.neropower.turbine;

import java.util.function.Predicate;

/** A plain-JVM port tank for the decision-016 tests: one fluid (a string id), a filter and a capacity. */
public final class TestTank implements PortPool.Slot<String> {

    private final long capacity;
    private final Predicate<String> accepts;
    private String fluid = "";
    private long amount;

    public TestTank(long capacity, Predicate<String> accepts) {
        this.capacity = capacity;
        this.accepts = accepts;
    }

    public static TestTank of(long capacity, String... fluids) {
        java.util.Set<String> set = java.util.Set.of(fluids);
        return new TestTank(capacity, set::contains);
    }

    public TestTank with(String fluid, long amount) {
        this.fluid = fluid;
        this.amount = amount;
        return this;
    }

    public long amount() {
        return this.amount;
    }

    public String fluid() {
        return this.amount <= 0L ? "" : this.fluid;
    }

    @Override
    public String slotFluid() {
        return this.fluid;
    }

    @Override
    public boolean slotEmpty() {
        return this.amount <= 0L;
    }

    @Override
    public long slotAmount() {
        return this.amount;
    }

    @Override
    public long slotCapacity() {
        return this.capacity;
    }

    @Override
    public boolean slotAccepts(String f) {
        return f != null && this.accepts.test(f);
    }

    @Override
    public long slotFill(String f, long add, boolean simulate) {
        if (!slotAccepts(f) || add <= 0L || (this.amount > 0L && !this.fluid.equals(f))) {
            return 0L;
        }
        long put = Math.min(add, this.capacity - this.amount);
        if (!simulate && put > 0L) {
            this.fluid = f;
            this.amount += put;
        }
        return Math.max(0L, put);
    }

    @Override
    public long slotDrain(long take, boolean simulate) {
        long out = Math.max(0L, Math.min(take, this.amount));
        if (!simulate && out > 0L) {
            this.amount -= out;
            if (this.amount == 0L) {
                this.fluid = "";
            }
        }
        return out;
    }
}
