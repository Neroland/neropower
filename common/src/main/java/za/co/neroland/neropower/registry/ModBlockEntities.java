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
        out.addAll(za.co.neroland.neropower.registry.content.TurbineContent.energyTypes());
        out.addAll(za.co.neroland.neropower.registry.content.BackEndContent.energyTypes());
        out.addAll(za.co.neroland.neropower.registry.content.GenerationContent.energyTypes());
        out.addAll(za.co.neroland.neropower.registry.content.StorageContent.energyTypes());
        out.addAll(za.co.neroland.neropower.registry.content.GridContent.energyTypes());
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
        out.addAll(za.co.neroland.neropower.registry.content.BackEndContent.itemTypes());
        out.addAll(za.co.neroland.neropower.registry.content.GenerationContent.itemTypes());
        out.addAll(za.co.neroland.neropower.registry.content.StorageContent.itemTypes());
        out.addAll(za.co.neroland.neropower.registry.content.GridContent.itemTypes());
        return List.copyOf(out);
    }

    /**
     * Every NeroPower machine with a fluid surface (Core's fluid capability plus the loaders' standard
     * fluid views, which NeroTech's loader wiring reads from {@link MachineTypeRegistry}): the reactor
     * steam port, the turbine steam ports and condenser, the Storage Ring's cryo tank, the fuel cell's
     * water outlet.
     */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> fluidMachineTypes() {
        List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> out = new ArrayList<>();
        out.addAll(za.co.neroland.neropower.registry.content.FissionContent.fluidTypes());
        out.addAll(za.co.neroland.neropower.registry.content.TurbineContent.fluidTypes());
        out.addAll(za.co.neroland.neropower.registry.content.StorageContent.fluidTypes());
        out.addAll(za.co.neroland.neropower.registry.content.GenerationContent.fluidTypes());
        return List.copyOf(out);
    }

    /** Every NeroPower machine on Core's gas surface: the fuel cell's hydrogen and oxygen inlets. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> gasMachineTypes() {
        return List.copyOf(za.co.neroland.neropower.registry.content.GenerationContent.gasTypes());
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
        fluidMachineTypes().forEach(MachineTypeRegistry::registerFluid);
        gasMachineTypes().forEach(MachineTypeRegistry::registerGas);
    }

    private ModBlockEntities() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }
}
