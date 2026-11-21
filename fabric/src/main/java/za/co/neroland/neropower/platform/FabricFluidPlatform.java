package za.co.neroland.neropower.platform;

import net.minecraft.world.level.material.Fluid;

import za.co.neroland.neropower.fluid.FluidSpec;
import za.co.neroland.neropower.fluid.NeroPowerFluid;

/** Fabric {@link IFluidPlatform}: Fabric needs no fluid type, so the common fluid is used as is. */
public final class FabricFluidPlatform implements IFluidPlatform {

    @Override
    public Fluid create(FluidSpec spec) {
        return new NeroPowerFluid(spec);
    }
}
