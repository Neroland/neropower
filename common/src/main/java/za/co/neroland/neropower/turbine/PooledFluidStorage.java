package za.co.neroland.neropower.turbine;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;

/**
 * A structure's shared tank on Core's fluid surface: the {@link PortPool} over the member
 * ports' tanks, re-read on every call so a port that joins or leaves the structure counts at once. Both
 * directions are open (a loader's transactional handler must always be able to undo); one-way faces come
 * from the {@link PoolRoute} or the gated views the port hands out. World data only.
 */
public final class PooledFluidStorage implements NeroFluidStorage {

    private final Supplier<List<? extends PortPool.Slot<Fluid>>> slots;

    public PooledFluidStorage(Supplier<List<? extends PortPool.Slot<Fluid>>> slots) {
        this.slots = slots;
    }

    private List<? extends PortPool.Slot<Fluid>> slots() {
        List<? extends PortPool.Slot<Fluid>> list = this.slots.get();
        return list == null ? List.of() : list;
    }

    /** Whether any member tank would take {@code fluid} at all. */
    public boolean accepts(Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) {
            return false;
        }
        for (PortPool.Slot<Fluid> slot : slots()) {
            if (slot.slotAccepts(fluid)) {
                return true;
            }
        }
        return false;
    }

    /** mB of {@code fluid} across the members (also behind another first fluid). */
    public long amountOf(Fluid fluid) {
        return PortPool.amountOf(slots(), fluid);
    }

    /** Drain {@code fluid} specifically. */
    public long drainFluid(Fluid fluid, long amount, boolean simulate) {
        return PortPool.drainFluid(slots(), fluid, amount, simulate);
    }

    /** Free room for {@code fluid}. */
    public long roomFor(Fluid fluid) {
        return PortPool.roomFor(slots(), fluid);
    }

    @Override
    public Fluid getFluid() {
        Fluid fluid = PortPool.fluid(slots());
        return fluid == null ? Fluids.EMPTY : fluid;
    }

    @Override
    public long getAmount() {
        return PortPool.amount(slots());
    }

    @Override
    public long getCapacity() {
        return PortPool.capacity(slots());
    }

    @Override
    public long fill(Fluid fluid, long amount, boolean simulate) {
        if (fluid == null || fluid == Fluids.EMPTY) {
            return 0L;
        }
        return PortPool.fill(slots(), fluid, amount, simulate);
    }

    @Override
    public long drain(long amount, boolean simulate) {
        return PortPool.drain(slots(), amount, simulate);
    }
}
