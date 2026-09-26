package za.co.neroland.neropower.storage.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.nerotech.client.MachineScreen;

import za.co.neroland.neropower.storage.BatteryCellMenu;

/** Concrete screen for the Battery Cells — the shared energy gauge is the whole readout. */
public class BatteryCellScreen extends MachineScreen<BatteryCellMenu> {

    public BatteryCellScreen(BatteryCellMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }
}
