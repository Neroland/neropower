package za.co.neroland.neropower.storage;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.neropower.energy.PortableEnergy;
import za.co.neroland.neropower.energy.PortableEnergyItem;

/**
 * A portable Power Cell: NE kept on the stack in {@code neropower:cell_energy}, shown as
 * the item bar (green when full, amber, red when nearly empty). Charged in a Battery Cell or Surge Bank
 * charge slot and by NeroTech's Induction Charger; drawn by any NeroPower user through
 * {@link PortableEnergy} and, on NeoForge and Forge, by other mods through the FE item capability.
 * Stack size 1. The stack carries energy only, never player data.
 */
public class PowerCellItem extends Item implements PortableEnergyItem {

    private final PowerCellTier tier;

    public PowerCellItem(Properties properties, PowerCellTier tier) {
        super(properties);
        this.tier = tier;
    }

    public PowerCellTier tier() {
        return this.tier;
    }

    @Override
    public long portableCapacity(ItemStack stack) {
        return this.tier.capacity();
    }

    @Override
    public long portableIo(ItemStack stack) {
        return this.tier.io();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        long capacity = Math.max(1L, PortableEnergy.capacity(stack));
        return (int) Math.round(13.0D * PortableEnergy.stored(stack) / capacity);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        long capacity = Math.max(1L, PortableEnergy.capacity(stack));
        long permille = PortableEnergy.stored(stack) * 1000L / capacity;
        if (permille >= 500L) {
            return 0x5FD16A; // green
        }
        return permille >= 150L ? 0xE0B33A : 0xD8483A; // amber, then red
    }
}
