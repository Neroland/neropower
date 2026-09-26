package za.co.neroland.neropower.storage;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;
import za.co.neroland.nerotech.menu.MachineMenu;

/**
 * Battery Cell menu: no machine slots — the shared energy gauge <i>is</i> the readout (stored NE as
 * a fraction of the tier's capacity), plus the upgrade block and player inventory. One menu type
 * serves all three tiers; the title carries the tier.
 */
public class BatteryCellMenu extends MachineMenu {

    private static final int MACHINE_SLOTS = 0;

    public BatteryCellMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(MACHINE_SLOTS + NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(7));
    }

    public BatteryCellMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(StorageContent.BATTERY_CELL_MENU.get(), id, container, data, MACHINE_SLOTS);
        addUpgradeAndPlayerSlots(playerInventory);
    }
}
