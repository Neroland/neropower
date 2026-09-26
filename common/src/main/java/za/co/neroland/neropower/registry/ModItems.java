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
        out.addAll(EnvironmentalContent.creativeItems());
        return out;
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
