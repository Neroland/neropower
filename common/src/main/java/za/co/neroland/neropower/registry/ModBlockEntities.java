package za.co.neroland.neropower.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerotech.api.MachineTypeRegistry;
import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.beam.BeamContent;
import za.co.neroland.neropower.environmental.EnvironmentalContent;
import za.co.neroland.neropower.fission.FissionContent;
import za.co.neroland.neropower.storage.StorageContent;

/**
 * Block-entity types for NeroPower's machines, registered cross-loader via {@link RegistrationProvider}
 * — and, through {@link #registerMachineTypes()}, with NeroTech's public {@link MachineTypeRegistry},
 * which is what every loader's capability wiring reads. NeroPower therefore ships no capability code:
 * a type listed in {@link #energyMachineTypes()} gets Core's energy capability / lookup on NeoForge,
 * Fabric and Forge, and one listed in {@link #itemMachineTypes()} gets the loader's item surface.
 *
 * <p><b>Adding a machine?</b> Register its {@link BlockEntityType} here and add it to the list(s)
 * below — that is all the cross-loader wiring it needs.
 */
public final class ModBlockEntities {

    public static final RegistrationProvider<BlockEntityType<?>> BLOCK_ENTITIES =
            RegistrationProvider.get(Registries.BLOCK_ENTITY_TYPE, NeroPowerCommon.MOD_ID);

    /** Whether {@link #registerMachineTypes()} has run — it must contribute NeroPower's machines exactly once. */
    private static boolean registered;

    /**
     * Every NeroPower machine on the energy surface. Empty until Stage 4 — the fission core, storage
     * cells / bank controller, beam endpoints, RTG and Stirling generator all join here.
     */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> energyMachineTypes() {
        List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> out = new ArrayList<>();
        out.addAll(FissionContent.energyTypes());
        out.addAll(StorageContent.energyTypes());
        out.addAll(BeamContent.energyTypes());
        out.addAll(EnvironmentalContent.energyTypes());
        return List.copyOf(out);
    }

    /**
     * Every NeroPower machine with slots that pipes / hoppers must reach (a subset of
     * {@link #energyMachineTypes()}): the fission core (fuel rods), the RTG (pellet slot).
     */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> itemMachineTypes() {
        List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> out = new ArrayList<>();
        out.addAll(FissionContent.itemTypes());
        out.addAll(StorageContent.itemTypes());
        out.addAll(BeamContent.itemTypes());
        out.addAll(EnvironmentalContent.itemTypes());
        return List.copyOf(out);
    }

    /**
     * Register NeroPower's machine types with NeroTech's {@link MachineTypeRegistry}, once, from mod
     * construction (see {@link za.co.neroland.neropower.NeroPowerCommon#init()} for the timing rule).
     */
    public static void registerMachineTypes() {
        if (registered) {
            return;
        }
        registered = true;
        energyMachineTypes().forEach(MachineTypeRegistry::registerEnergy);
        itemMachineTypes().forEach(MachineTypeRegistry::registerItem);
    }

    private ModBlockEntities() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }
}
