package za.co.neroland.neropower.fluid;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.platform.IFluidPlatform;
import za.co.neroland.neropower.platform.Services;
import za.co.neroland.neropower.registry.RegistrationProvider;
import za.co.neroland.neropower.registry.RegistrationProvider.RegistryEntry;

/**
 * NeroPower's three fluids: {@code neropower:steam}, {@code neropower:exhaust_steam} and
 * {@code neropower:heavy_water}. Non-placeable transport fluids (see {@link NeroPowerFluid}); tags
 * {@code c:steam}, {@code c:heavy_water} and {@code neropower:exhaust_steam}. The loader flavour comes
 * from {@link IFluidPlatform}, loaded on first use so common code never class-loads a loader type.
 */
public final class NeroPowerFluids {

    public static final RegistrationProvider<Fluid> FLUIDS =
            RegistrationProvider.get(Registries.FLUID, NeroPowerCommon.MOD_ID);

    private static IFluidPlatform platform;

    private static final Map<FluidSpec, RegistryEntry<Fluid>> ENTRIES = new EnumMap<>(FluidSpec.class);

    public static final RegistryEntry<Fluid> STEAM = register(FluidSpec.STEAM);
    public static final RegistryEntry<Fluid> EXHAUST_STEAM = register(FluidSpec.EXHAUST_STEAM);
    public static final RegistryEntry<Fluid> HEAVY_WATER = register(FluidSpec.HEAVY_WATER);

    private NeroPowerFluids() {
    }

    /** Force class-load so the fluids register. */
    public static void init() {
    }

    private static synchronized IFluidPlatform platform() {
        if (platform == null) {
            platform = Services.load(IFluidPlatform.class);
        }
        return platform;
    }

    private static RegistryEntry<Fluid> register(FluidSpec spec) {
        RegistryEntry<Fluid> entry = FLUIDS.register(spec.id(), key -> platform().create(spec));
        ENTRIES.put(spec, entry);
        return entry;
    }

    /** The registered fluid for {@code spec}. */
    public static Fluid get(FluidSpec spec) {
        return ENTRIES.get(spec).get();
    }
}
