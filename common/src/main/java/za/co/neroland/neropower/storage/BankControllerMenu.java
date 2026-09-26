package za.co.neroland.neropower.storage;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;
import za.co.neroland.nerotech.menu.MachineMenu;

/**
 * Battery Bank Controller menu: no machine slots — upgrade block + player inventory, plus the
 * controller's {@value BankControllerBlockEntity#EXTRA_DATA} synced ints after the seven shared
 * ones (member count, pooled amount and capacity as hi/lo int pairs, mode ordinal, effective I/O),
 * read by {@code storage.client.BankControllerScreen}. The shared energy gauge (index 0) already
 * shows the pool's fill, since the controller's energy surface is the pool.
 */
public class BankControllerMenu extends MachineMenu {

    private static final int MACHINE_SLOTS = 0;

    /** Seven shared ContainerData indices + the controller's status ints. */
    private static final int DATA_SIZE = 7 + BankControllerBlockEntity.EXTRA_DATA;

    public BankControllerMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(MACHINE_SLOTS + NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public BankControllerMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(StorageContent.BATTERY_BANK_CONTROLLER_MENU.get(), id, container, data, MACHINE_SLOTS);
        addUpgradeAndPlayerSlots(playerInventory);
    }

    /** Cells pooled by the last scan. */
    public int memberCount() {
        return extraValue(0);
    }

    /** Pooled stored NE (two synced ints re-joined). */
    public long pooledAmount() {
        return join(extraValue(1), extraValue(2));
    }

    /** Pooled capacity in NE (two synced ints re-joined). */
    public long pooledCapacity() {
        return join(extraValue(3), extraValue(4));
    }

    /** The controller's current {@link BankMode}. */
    public BankMode mode() {
        return BankMode.byOrdinal(extraValue(5));
    }

    /** The pool's effective per-tick I/O (NE/t). */
    public int effectiveIo() {
        return extraValue(6);
    }

    private static long join(int hi, int lo) {
        return ((long) hi << 32) | (lo & 0xFFFF_FFFFL);
    }
}
