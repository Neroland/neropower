package za.co.neroland.neropower.environmental;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;
import za.co.neroland.nerotech.menu.MachineMenu;

/**
 * Stirling Generator menu: no machine slots — the upgrade block and the player inventory, plus three
 * synced ints after the seven shared ones (hot-neighbour gradient, heat drawn last tick, cold-face
 * flag) read by {@code client.StirlingScreen}.
 */
public class StirlingMenu extends MachineMenu {

    private static final int MACHINE_SLOTS = 0;

    /** Seven shared ContainerData indices + the engine's three readouts. */
    private static final int DATA_SIZE = 7 + 3;

    public StirlingMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(MACHINE_SLOTS + NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public StirlingMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(EnvironmentalContent.STIRLING_MENU.get(), id, container, data, MACHINE_SLOTS);
        addUpgradeAndPlayerSlots(playerInventory);
    }

    /** Heat above ambient of the hottest adjacent machine (0 when none). */
    public int gradient() {
        return extraValue(0);
    }

    /** Heat units drawn from that machine last tick. */
    public int drawnLastTick() {
        return extraValue(1);
    }

    /** Whether a cold sink (water / ice / snow / Radiator) touches the engine. */
    public boolean hasColdFace() {
        return extraValue(2) > 0;
    }
}
