package za.co.neroland.neropower.forge;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.resources.Identifier;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.ForgeRegistries;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.fluid.FluidSpec;
import za.co.neroland.neropower.registry.RegistrationProvider;

/**
 * The {@link FluidType}s behind NeroPower's fluids on Forge: one per fluid, never shared, built when the
 * registry fires. Forge has no client-extension event, so each type hands its sprites over from
 * {@code initializeClient} (used before 26.1; from 26.1 the baked fluid models in
 * {@link ForgeClientSetup} take over).
 */
public final class ForgeFluidTypes {

    private static final RegistrationProvider<FluidType> TYPES =
            RegistrationProvider.get(ForgeRegistries.Keys.FLUID_TYPES, NeroPowerCommon.MOD_ID);

    private static final Map<FluidSpec, RegistrationProvider.RegistryEntry<FluidType>> BY_SPEC =
            new EnumMap<>(FluidSpec.class);

    static {
        for (FluidSpec spec : FluidSpec.values()) {
            BY_SPEC.put(spec, TYPES.register(spec.id(), key -> type(spec)));
        }
    }

    private ForgeFluidTypes() {
    }

    /** Force class-load so the types register. */
    public static void init() {
    }

    /** The type of {@code spec}. */
    public static FluidType forSpec(FluidSpec spec) {
        return BY_SPEC.get(spec).get();
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
        return new TexturedFluidType(properties, spec);
    }

    /** A type that hands its sprites and tint to the client (the consumer must get an anonymous object). */
    private static final class TexturedFluidType extends FluidType {

        private final Identifier still;
        private final Identifier flowing;
        private final int tint;

        private TexturedFluidType(Properties properties, FluidSpec spec) {
            super(properties);
            this.still = Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, spec.stillTexture());
            this.flowing = Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, spec.flowTexture());
            this.tint = spec.tint();
        }

        @Override
        public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
            consumer.accept(new IClientFluidTypeExtensions() {
                @Override
                public Identifier getStillTexture() {
                    return TexturedFluidType.this.still;
                }

                @Override
                public Identifier getFlowingTexture() {
                    return TexturedFluidType.this.flowing;
                }

                @Override
                public int getTintColor() {
                    return TexturedFluidType.this.tint;
                }
            });
        }
    }
}
