package za.co.neroland.neropower.registry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.beam.BeamContent;
import za.co.neroland.neropower.environmental.EnvironmentalContent;
import za.co.neroland.neropower.fission.FissionContent;
import za.co.neroland.neropower.storage.StorageContent;
import za.co.neroland.neropower.registry.RegistrationProvider.RegistryEntry;

/**
 * NeroPower's items, registered cross-loader through the {@link RegistrationProvider} seam over the
 * vanilla item registry. Every item is listed in {@link #creativeOrder()} for NeroPower's own
 * creative tab.
 *
 * <p>NeroPower does not re-register NeroTech's intermediates (Machine Frame, Nero Coil, Circuit
 * Board) — recipes consume them from NeroTech. Tagging is hand-authored (no datagen).
 */
public final class ModItems {

    public static final RegistrationProvider<Item> ITEMS =
            RegistrationProvider.get(Registries.ITEM, NeroPowerCommon.MOD_ID);

    // --- Fission (Stage 4) ---------------------------------------------------
    /**
     * Control rod — the neutron-absorbing insert a Control Rod Assembly holds. A plain item until the
     * fission reactor lands (Stage 4); doubles as the creative tab icon.
     */
    public static final RegistryEntry<Item> CONTROL_ROD = item("control_rod");

    /** Every NeroPower item, in display order, for NeroPower's own creative tab. */
    private static List<RegistryEntry<? extends ItemLike>> creativeOrder() {
        List<RegistryEntry<? extends ItemLike>> out = new ArrayList<>();
        out.add(CONTROL_ROD);
        out.addAll(FissionContent.creativeItems());
        out.addAll(StorageContent.creativeItems());
        out.addAll(BeamContent.creativeItems());
        out.addAll(za.co.neroland.neropower.registry.content.GridContent.creativeItems()); // beam family, after orbital_receiver
        out.addAll(EnvironmentalContent.creativeItems());
        add040(out);
        return out;
    }

    /** NeroPower 0.4.0 content, appended in the decision-001 family order. */
    private static void add040(List<RegistryEntry<? extends ItemLike>> out) {
        // Fission
        out.add(za.co.neroland.neropower.registry.content.FissionContent.GRAPHITE_MODERATOR_ITEM);
        out.add(za.co.neroland.neropower.registry.content.FissionContent.MODERATOR_TANK_ITEM);
        out.add(za.co.neroland.neropower.registry.content.FissionContent.BREEDER_BLANKET_ITEM);
        out.add(za.co.neroland.neropower.registry.content.FissionContent.EUROPIUM_CONTROL_ROD_ASSEMBLY_ITEM);
        out.add(za.co.neroland.neropower.registry.content.FissionContent.REACTOR_STEAM_PORT_ITEM);
        out.add(za.co.neroland.neropower.registry.content.FissionContent.REACTOR_CONTROL_COMPUTER_ITEM);
        // Steam, Back end, Generation, Storage, Grid, Radiation
        out.addAll(za.co.neroland.neropower.registry.content.TurbineContent.creativeItems());
        out.addAll(za.co.neroland.neropower.registry.content.BackEndContent.creativeBlocks());
        out.addAll(za.co.neroland.neropower.registry.content.GenerationContent.creativeBlocks());
        out.addAll(za.co.neroland.neropower.registry.content.StorageContent.creativeItems());
        out.addAll(za.co.neroland.neropower.registry.content.GridContent.gridCreativeItems());
        out.addAll(za.co.neroland.neropower.registry.content.RadiationContent.creativeBlocks());
        // fuel and waste items
        out.add(za.co.neroland.neropower.registry.content.FissionContent.EUROPIUM_CONTROL_ROD);
        out.add(za.co.neroland.neropower.registry.content.FissionContent.PALLURITE_DUST);
        out.add(za.co.neroland.neropower.registry.content.FissionContent.PALLURITE_PELLET);
        out.add(za.co.neroland.neropower.registry.content.FissionContent.KINDLED_PELLET);
        out.add(za.co.neroland.neropower.registry.content.FissionContent.BRAIDED_FUEL_ROD);
        out.add(za.co.neroland.neropower.registry.content.FissionContent.HOT_SPENT_FUEL_ROD);
        out.addAll(za.co.neroland.neropower.registry.content.BackEndContent.creativeItems());
        // cells and blades
        out.addAll(za.co.neroland.neropower.registry.content.StorageContent.powerCellItems());
        out.addAll(za.co.neroland.neropower.registry.content.TurbineContent.creativeVaneSets());
        // modules
        out.addAll(za.co.neroland.neropower.registry.content.TurbineContent.creativeModules());
        out.add(za.co.neroland.neropower.registry.content.FissionContent.FUEL_ECONOMIZER);
        // tools
        out.addAll(za.co.neroland.neropower.registry.content.RadiationContent.creativeItems());
        out.addAll(za.co.neroland.neropower.registry.content.ToolContent.creativeItems());
    }

    private static RegistryEntry<Item> item(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties().setId(key)));
    }

    /** Every NeroPower item as {@link ItemLike}, in display order — drained into NeroPower's creative tab. */
    public static List<ItemLike> creativeContents() {
        List<ItemLike> out = new ArrayList<>();
        for (RegistryEntry<? extends ItemLike> entry : creativeOrder()) {
            out.add(entry.get());
        }
        return out;
    }

    private ModItems() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }
}
