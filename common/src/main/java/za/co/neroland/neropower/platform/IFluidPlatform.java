package za.co.neroland.neropower.platform;

import net.minecraft.world.level.material.Fluid;

import za.co.neroland.neropower.fluid.FluidSpec;

/**
 * Loader seam for NeroPower's fluids. NeoForge and Forge require every fluid to name a loader-only
 * {@code FluidType}, so each loader builds its own flavour of
 * {@link za.co.neroland.neropower.fluid.NeroPowerFluid} here, with one type per fluid built lazily.
 * Fabric returns the common class unchanged. Registered via {@code META-INF/services}.
 */
public interface IFluidPlatform {

    /** The loader's flavour of the fluid for {@code spec}. */
    Fluid create(FluidSpec spec);
}
