package za.co.neroland.neropower.storage;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;
import za.co.neroland.nerotech.menu.MachineMenu;

import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.menu.GaugeTailMenu;
import za.co.neroland.neropower.registry.content.StorageContent;

/**
 * Surge Bank menu: the charge slot, the upgrade block and the player inventory. The gauge tail carries
 * the wide stored / capacity values and, as its extra value, the bank's I/O rate (NE/t).
 */
public class SurgeBankMenu extends MachineMenu implements GaugeTailMenu {

    private static final int MACHINE_SLOTS = 1;

    private final GaugeTail gaugeTail;

    public SurgeBankMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(MACHINE_SLOTS + NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(7 + SurgeBankBlockEntity.EXTRA_DATA));
    }

    public SurgeBankMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(StorageContent.SURGE_BANK_MENU.get(), id, container, data, MACHINE_SLOTS);
        this.gaugeTail = GaugeTail.storage(container, be -> StorageConfig.surgeBankIo());
        this.addDataSlots(this.gaugeTail);
        this.addSlot(new PredicateSlot(container, ChargeSlot.SLOT, 116, 20));
        addUpgradeAndPlayerSlots(playerInventory, 138, 18, 2, 104); // tall 176 x 186 hull
    }

    /** The bank's I/O rate (NE/t). */
    public long ioRate() {
        return gaugeExtra();
    }

    /** Contract readout 0..5. */
    public int readout() {
        return extraValue(0);
    }

    /** Contract stall cause 0..15. */
    public int stallCause() {
        return extraValue(1);
    }

    /** NE inserted last tick. */
    public long inPerTick() {
        return GaugeTail.join(extraValue(2), extraValue(3));
    }

    /** NE extracted last tick. */
    public long outPerTick() {
        return GaugeTail.join(extraValue(4), extraValue(5));
    }

    /** Whether a Battery Bank pools this one (as its fast tier). */
    public boolean pooled() {
        return extraValue(6) != 0;
    }

    /** Members of the bank it is pooled in (0 alone). */
    public int bankMembers() {
        return extraValue(7);
    }

    @Override
    public GaugeTail gaugeTail() {
        return this.gaugeTail;
    }
}
