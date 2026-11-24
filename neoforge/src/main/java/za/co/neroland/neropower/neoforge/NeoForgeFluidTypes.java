package za.co.neroland.neropower.neoforge;

import java.util.EnumMap;
import java.util.Map;

import net.neoforged.neoforge.fluids.FluidType;
//? if <26.1 {
/*import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
*///?}
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.fluid.FluidSpec;
import za.co.neroland.neropower.registry.RegistrationProvider;

/**
 * The {@link FluidType}s behind NeroPower's fluids on NeoForge: one per fluid, never shared, built when
 * the registry fires. The fluids are never placed, so the types only carry the display name, physical
 * attributes and the client render extensions. Created from the NeoForge entry point before the
 * registers are attached to the mod bus.
 */
public final class NeoForgeFluidTypes {

    private static final RegistrationProvider<FluidType> TYPES =
            RegistrationProvider.get(NeoForgeRegistries.Keys.FLUID_TYPES, NeroPowerCommon.MOD_ID);

    private static final Map<FluidSpec, RegistrationProvider.RegistryEntry<FluidType>> BY_SPEC =
            new EnumMap<>(FluidSpec.class);

    static {
        for (FluidSpec spec : FluidSpec.values()) {
            BY_SPEC.put(spec, TYPES.register(spec.id(), key -> type(spec)));
        }
    }

    private NeoForgeFluidTypes() {
    }

    /** Force class-load so the types register. */
    public static void init() {
    }

    /** The type of {@code spec}. */
    public static FluidType forSpec(FluidSpec spec) {
        return BY_SPEC.get(spec).get();
    }

    /** Every type entry (for the client's pre-26.1 render extensions). */
    public static Map<FluidSpec, RegistrationProvider.RegistryEntry<FluidType>> all() {
        return java.util.Collections.unmodifiableMap(BY_SPEC);
    }

    private static FluidType type(FluidSpec spec) {
        FluidType.Properties properties = FluidType.Properties.create()
                .descriptionId(spec.translationKey())
                .density(spec.density())
                .viscosity(spec.viscosity())
                .temperature(spec.temperature())
                .canConvertToSource(false);
        if (spec.gaseous()) {
            properties.canDrown(false).canSwim(false).canPushEntity(false).canExtinguish(false);
        }
        //? if >=1.21 {
        return new FluidType(properties);
        //?} else {
        /*return new FluidType(properties) {
            @Override
            public void initializeClient(java.util.function.Consumer<IClientFluidTypeExtensions> consumer) {
                consumer.accept(extensions(spec));
            }
        };
        *///?}
    }

    //? if <26.1 {
    /*static IClientFluidTypeExtensions extensions(FluidSpec spec) {
        net.minecraft.resources.Identifier still = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                za.co.neroland.neropower.NeroPowerCommon.MOD_ID, spec.stillTexture());
        net.minecraft.resources.Identifier flowing = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                za.co.neroland.neropower.NeroPowerCommon.MOD_ID, spec.flowTexture());
        int tint = spec.tint();
        return new IClientFluidTypeExtensions() {
            @Override
            public net.minecraft.resources.Identifier getStillTexture() {
                return still;
            }

            @Override
            public net.minecraft.resources.Identifier getFlowingTexture() {
                return flowing;
            }

            @Override
            public int getTintColor() {
                return tint;
            }
        };
    }
    *///?}
}
