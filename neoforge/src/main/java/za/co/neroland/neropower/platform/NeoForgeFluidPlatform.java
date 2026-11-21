package za.co.neroland.neropower.platform;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;

import za.co.neroland.neropower.fluid.FluidSpec;
import za.co.neroland.neropower.fluid.NeroPowerFluid;
import za.co.neroland.neropower.neoforge.NeoForgeFluidTypes;

/** NeoForge {@link IFluidPlatform}: each fluid names its own {@link FluidType}. */
public final class NeoForgeFluidPlatform implements IFluidPlatform {

    @Override
    public Fluid create(FluidSpec spec) {
        return new Typed(spec);
    }

    private static final class Typed extends NeroPowerFluid {

        private Typed(FluidSpec spec) {
            super(spec);
        }

        @Override
        public FluidType getFluidType() {
            return NeoForgeFluidTypes.forSpec(spec());
        }
    }
}
