package za.co.neroland.neropower.turbine;

import java.util.List;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;

/**
 * The one storage a port face shows on Core's fluid surface, over shared tanks: fills go to the first input
 * pool that takes the fluid, drains (and the reported fluid) come only from the output pool. Inputs never
 * leak back out and the output never takes anything in, as with {@link FluidRoute}.
 */
public final class PoolRoute implements NeroFluidStorage {

    private final List<PooledFluidStorage> inputs;
    @Nullable
    private final PooledFluidStorage output;

    public PoolRoute(List<PooledFluidStorage> inputs, @Nullable PooledFluidStorage output) {
        this.inputs = List.copyOf(inputs);
        this.output = output;
    }

    @Nullable
    private PooledFluidStorage shown() {
        if (this.output != null) {
            return this.output;
        }
        return this.inputs.isEmpty() ? null : this.inputs.get(0);
    }

    @Override
    public Fluid getFluid() {
        PooledFluidStorage pool = shown();
        return pool == null ? Fluids.EMPTY : pool.getFluid();
    }

    @Override
    public long getAmount() {
        PooledFluidStorage pool = shown();
        return pool == null ? 0L : pool.getAmount();
    }

    @Override
    public long getCapacity() {
        PooledFluidStorage pool = shown();
        return pool == null ? 0L : pool.getCapacity();
    }

    @Override
    public long fill(Fluid fluid, long amount, boolean simulate) {
        for (PooledFluidStorage pool : this.inputs) {
            if (pool.accepts(fluid)) {
                return pool.fill(fluid, amount, simulate);
            }
        }
        return 0L;
    }

    @Override
    public long drain(long amount, boolean simulate) {
        return this.output == null ? 0L : this.output.drain(amount, simulate);
    }
}
