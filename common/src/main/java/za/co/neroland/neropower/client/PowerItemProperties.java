package za.co.neroland.neropower.client;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.radiation.DosimeterBadgeItem;
import za.co.neroland.neropower.radiation.RadiationMath;
import za.co.neroland.neropower.radiation.client.GeigerReadModel;

/**
 * The two item-model properties the radiation tools read. From 1.21.4 they are {@code select} properties
 * ({@code items/geiger_counter.json}, {@code items/dosimeter_badge.json}) with the values {@code "0".."2"} and
 * {@code "0".."4"}; on 1.21.1-1.21.3 they are item predicates for the legacy {@code models/item} overrides
 * (frame / 2 and step / 4). Client only; the values come from the stack alone.
 */
public final class PowerItemProperties {

    public static final Identifier GEIGER_FRAME = Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "geiger_frame");
    public static final Identifier DOSIMETER_STEP = Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "dosimeter_step");

    private static boolean registered;

    private PowerItemProperties() {
    }

    /** Geiger Counter frame, 0 (clear/trace) to 2 (high/severe). */
    public static int geigerFrame(ItemStack stack) {
        return Math.max(0, Math.min(2, GeigerReadModel.frame(stack)));
    }

    /** Dosimeter Badge step, 0 to 4. */
    public static int dosimeterStep(ItemStack stack) {
        return Math.max(0, Math.min(4, RadiationMath.dosimeterStep(DosimeterBadgeItem.reading(stack))));
    }

    /** Register both properties once (client setup on every loader). */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        //? if >=1.21.4 {
        put(GEIGER_FRAME, GeigerFrame.TYPE);
        put(DOSIMETER_STEP, DosimeterStep.TYPE);
        //?} else {
        /*net.minecraft.client.renderer.item.ItemProperties.registerGeneric(GEIGER_FRAME,
                (stack, level, entity, seed) -> geigerFrame(stack) / 2.0F);
        net.minecraft.client.renderer.item.ItemProperties.registerGeneric(DOSIMETER_STEP,
                (stack, level, entity, seed) -> dosimeterStep(stack) / 4.0F);
        *///?}
    }

    //? if >=1.21.4 {
    /**
     * Adds a select property type to vanilla's id mapper, found by type (the field is private outside
     * Fabric's access widener and Forge has no event for it); failing that the item shows frame 0.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void put(Identifier id, Object type) {
        try {
            for (java.lang.reflect.Field field : net.minecraft.client.renderer.item.properties.select
                    .SelectItemModelProperties.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        && net.minecraft.util.ExtraCodecs.LateBoundIdMapper.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    ((net.minecraft.util.ExtraCodecs.LateBoundIdMapper) field.get(null)).put(id, type);
                    return;
                }
            }
            NeroPowerCommon.LOGGER.warn("[NeroPower] item model property {} not registered: no id mapper", id);
        } catch (ReflectiveOperationException | RuntimeException e) {
            NeroPowerCommon.LOGGER.warn("[NeroPower] item model property {} not registered ({})", id,
                    e.getClass().getSimpleName());
        }
    }

    /** {@code neropower:geiger_frame}. */
    public record GeigerFrame()
            implements net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty<String> {
        public static final Type<GeigerFrame, String> TYPE = Type.create(
                com.mojang.serialization.MapCodec.unit(new GeigerFrame()), com.mojang.serialization.Codec.STRING);

        @Override
        public String get(ItemStack stack,
                @org.jetbrains.annotations.Nullable net.minecraft.client.multiplayer.ClientLevel level,
                @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity entity, int seed,
                net.minecraft.world.item.ItemDisplayContext context) {
            return Integer.toString(geigerFrame(stack));
        }

        @Override
        public Type<GeigerFrame, String> type() {
            return TYPE;
        }
        //? if >=1.21.5 {

        @Override
        public com.mojang.serialization.Codec<String> valueCodec() {
            return com.mojang.serialization.Codec.STRING;
        }
        //?}
    }

    /** {@code neropower:dosimeter_step}. */
    public record DosimeterStep()
            implements net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty<String> {
        public static final Type<DosimeterStep, String> TYPE = Type.create(
                com.mojang.serialization.MapCodec.unit(new DosimeterStep()), com.mojang.serialization.Codec.STRING);

        @Override
        public String get(ItemStack stack,
                @org.jetbrains.annotations.Nullable net.minecraft.client.multiplayer.ClientLevel level,
                @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity entity, int seed,
                net.minecraft.world.item.ItemDisplayContext context) {
            return Integer.toString(dosimeterStep(stack));
        }

        @Override
        public Type<DosimeterStep, String> type() {
            return TYPE;
        }
        //? if >=1.21.5 {

        @Override
        public com.mojang.serialization.Codec<String> valueCodec() {
            return com.mojang.serialization.Codec.STRING;
        }
        //?}
    }
    //?}
}
