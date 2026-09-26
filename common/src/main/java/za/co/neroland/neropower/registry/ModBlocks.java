package za.co.neroland.neropower.registry;

import java.util.function.Function;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.registry.RegistrationProvider.RegistryEntry;

/**
 * NeroPower's blocks, registered cross-loader through the {@link RegistrationProvider} seam over
 * the vanilla block registry. Empty for now: the fission reactor (Stage 4), storage tiers (Stage 5),
 * beamed power (Stage 6) and environmental generators (Stage 7) register here through
 * {@link #register(String, Function)}, which hands every machine the shared metal-machine properties.
 */
public final class ModBlocks {

    public static final RegistrationProvider<Block> BLOCKS =
            RegistrationProvider.get(Registries.BLOCK, NeroPowerCommon.MOD_ID);

    /** Register a machine block with the shared NeroPower machine properties (id set from the key). */
    public static <B extends Block> RegistryEntry<B> register(String name,
            Function<BlockBehaviour.Properties, B> factory) {
        return BLOCKS.register(name, key -> factory.apply(machineProperties().setId(key)));
    }

    /**
     * The properties every NeroPower machine shares: metal, pickaxe-tier strength, and no full-cube
     * occlusion (machines carry detail geometry, so neighbours must not cull their faces).
     */
    public static BlockBehaviour.Properties machineProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                .noOcclusion();
    }

    private ModBlocks() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }
}
