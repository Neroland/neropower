package za.co.neroland.neropower.storage;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;
import za.co.neroland.nerotech.menu.MachineMenu;

import za.co.neroland.neropower.failure.FailureStage;
import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.menu.GaugeTailMenu;
import za.co.neroland.neropower.registry.content.StorageContent;

/**
 * Storage Ring Controller menu (wide tall hull, 248 x 222): no machine slots; upgrades at {@code 210,18},
 * player inventory at {@code 43,140}, hotbar at {@code 43,198}, in NeroTech's slot index order so
 * {@code quickMoveStack} is unchanged. The gauge tail carries the ring's stored energy and capacity as
 * wide (45-bit) values, since a 56-coil ring holds 2.8e9 NE, past NeroTech's 2^30 tail, and the I/O
 * limit in force as its extra value. The controller's {@value SmesControllerBlockEntity#EXTRA_DATA}
 * synced ints follow the seven shared ones (layout in {@code SmesControllerBlockEntity.extraData}).
 */
public class SmesMenu extends MachineMenu implements GaugeTailMenu {

    public static final int WIDTH = 248;
    public static final int HEIGHT = 222;

    private static final int MACHINE_SLOTS = 0;
    private static final int DATA_SIZE = 7 + SmesControllerBlockEntity.EXTRA_DATA;

    private final GaugeTail gaugeTail;

    public SmesMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(MACHINE_SLOTS + NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public SmesMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(StorageContent.SMES_CONTROLLER_MENU.get(), id, container, data, MACHINE_SLOTS);
        this.gaugeTail = GaugeTail.storage(container,
                be -> be instanceof SmesControllerBlockEntity smes ? smes.ioLimit() : 0L);
        this.addDataSlots(this.gaugeTail);
        addWideSlots(playerInventory);
    }

    /** The wide hull's slots: upgrades 2 x 2 at 210,18, inventory at 43,140, hotbar at 43,198. */
    private void addWideSlots(Inventory playerInventory) {
        captureStatsTarget(playerInventory);
        int upgrades = this.totalNonPlayer - this.machineSlots;
        for (int i = 0; i < upgrades; i++) {
            this.addSlot(new PredicateSlot(this.container, this.machineSlots + i, 210 + (i % 2) * 18, 18 + (i / 2) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 43 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 43 + col * 18, 198));
        }
    }

    private long pair(int index) {
        return GaugeTail.join(extraValue(index), extraValue(index + 1));
    }

    public int coils() {
        return extraValue(0);
    }

    public long capacityPerCoil() {
        return pair(1);
    }

    public long ioPerCoil() {
        return pair(3);
    }

    public long maxIo() {
        return pair(5);
    }

    /** Cryo coolant in the tank (mB). */
    public long coolant() {
        return pair(7);
    }

    /** Tank size (mB). */
    public long coolantCapacity() {
        return pair(9);
    }

    /** Coolant per coil per 100 ticks (mB). */
    public int coolantPerCoil() {
        return extraValue(11);
    }

    /** Warmth, permille of the quench point. */
    public int warmth() {
        return extraValue(12);
    }

    public int warningPermille() {
        return extraValue(13);
    }

    public int unstablePermille() {
        return extraValue(14);
    }

    public FailureStage stage() {
        return FailureStage.byCode(extraValue(15));
    }

    /** Quench lockout ticks left. */
    public long lockoutTicks() {
        return pair(16);
    }

    /** Whether the server runs blast quenches (read-only here). */
    public boolean quenchBlast() {
        return extraValue(18) != 0;
    }

    public int quenchLossPermille() {
        return extraValue(19);
    }

    public long quenchLockoutTicks() {
        return pair(20);
    }

    public long inPerTick() {
        return pair(22);
    }

    public long outPerTick() {
        return pair(24);
    }

    public boolean formed() {
        return extraValue(26) != 0;
    }

    public int readout() {
        return extraValue(27);
    }

    public int stallCause() {
        return extraValue(28);
    }

    /** The I/O limit in force (NE/t). */
    public long ioLimit() {
        return gaugeExtra();
    }

    @Override
    public GaugeTail gaugeTail() {
        return this.gaugeTail;
    }
}
