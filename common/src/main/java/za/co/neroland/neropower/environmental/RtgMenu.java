package za.co.neroland.neropower.environmental;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;
import za.co.neroland.nerotech.menu.MachineMenu;

/**
 * Radioisotope Generator menu: a pellet input slot and a spent-pellet output slot, the upgrade block
 * and the player inventory, plus three synced ints after the seven shared ones (output permille,
 * days elapsed, days until the pellet is spent) read by {@code client.RtgScreen}.
 */
public class RtgMenu extends MachineMenu {

    private static final int MACHINE_SLOTS = 2;

    /** Seven shared ContainerData indices + the RTG's three readouts. */
    private static final int DATA_SIZE = 7 + 3;

    public RtgMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(MACHINE_SLOTS + NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public RtgMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(EnvironmentalContent.RTG_MENU.get(), id, container, data, MACHINE_SLOTS);
        this.addSlot(new PredicateSlot(container, RadioisotopeGeneratorBlockEntity.INPUT_SLOT, 62, 33));
        this.addSlot(new OutputSlot(container, RadioisotopeGeneratorBlockEntity.OUTPUT_SLOT, 98, 33));
        addUpgradeAndPlayerSlots(playerInventory);
    }

    /** Current output as permille of the fresh rate (0 without a pellet). */
    public int outputPermille() {
        return extraValue(0);
    }

    /** Whole in-game days the active pellet has been decaying. */
    public int elapsedDays() {
        return extraValue(1);
    }

    /** Whole in-game days until the active pellet is spent. */
    public int remainingDays() {
        return extraValue(2);
    }
}
