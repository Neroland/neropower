package za.co.neroland.neropower.turbine;

import java.util.List;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;

/**
 * The one {@link NeroFluidStorage} a turbine-family block shows on Core's fluid surface: fills are routed
 * to whichever input tank accepts the fluid, drains come only from the output tank. Inputs never leak
 * back out and the output never takes anything in, so a pipe or an auto-eject cannot loop steam back
 * into the reactor or exhaust into an inlet.
 */
public final class FluidRoute implements NeroFluidStorage {

    private final List<TurbineTank> inputs;
    @Nullable
    private final TurbineTank output;

    public FluidRoute(List<TurbineTank> inputs, @Nullable TurbineTank output) {
        this.inputs = List.copyOf(inputs);
        this.output = output;
    }

    @Nullable
    private TurbineTank shown() {
        if (this.output != null) {
            return this.output;
        }
        return this.inputs.isEmpty() ? null : this.inputs.get(0);
    }

    @Override
    public Fluid getFluid() {
        TurbineTank tank = shown();
        return tank == null ? Fluids.EMPTY : tank.getFluid();
    }

    @Override
    public long getAmount() {
        TurbineTank tank = shown();
        return tank == null ? 0L : tank.getAmount();
    }

    @Override
    public long getCapacity() {
        TurbineTank tank = shown();
        return tank == null ? 0L : tank.getCapacity();
    }

    @Override
    public long fill(Fluid fluid, long amount, boolean simulate) {
        for (TurbineTank tank : this.inputs) {
            if (tank.accepts(fluid)) {
                return tank.fill(fluid, amount, simulate);
            }
        }
        return 0L;
    }

    @Override
    public long drain(long amount, boolean simulate) {
        return this.output == null ? 0L : this.output.drain(amount, simulate);
    }
}
