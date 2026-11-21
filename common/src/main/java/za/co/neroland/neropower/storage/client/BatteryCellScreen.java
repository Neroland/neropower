package za.co.neroland.neropower.storage.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neropower.client.PowerMachineScreen;
import za.co.neroland.neropower.storage.BatteryCellMenu;

/**
 * Concrete screen for the Battery Cells: the shared energy gauge is the whole readout, and hovering it
 * gives stored / capacity in NE and the net charge rate.
 */
public class BatteryCellScreen extends PowerMachineScreen<BatteryCellMenu> {

    public BatteryCellScreen(BatteryCellMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }
}
