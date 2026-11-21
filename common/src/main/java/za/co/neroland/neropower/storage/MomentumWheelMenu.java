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
 * Momentum Wheel menu: no machine slots. Synced after the seven shared ints: readout, stall cause, last
 * tick's in and out (15-bit pairs) and the loss permille; the gauge tail carries the wide stored /
 * capacity values and, as its extra value, the wheel's I/O rate.
 */
public class MomentumWheelMenu extends MachineMenu implements GaugeTailMenu {

    private static final int MACHINE_SLOTS = 0;
    private static final int DATA_SIZE = 7 + MomentumWheelBlockEntity.EXTRA_DATA;

    private final GaugeTail gaugeTail;

    public MomentumWheelMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(MACHINE_SLOTS + NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public MomentumWheelMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(StorageContent.MOMENTUM_WHEEL_MENU.get(), id, container, data, MACHINE_SLOTS);
        this.gaugeTail = GaugeTail.storage(container, be -> StorageConfig.momentumWheelIo());
        this.addDataSlots(this.gaugeTail);
        addUpgradeAndPlayerSlots(playerInventory, 138, 18, 2, 104); // tall 176 x 186 hull
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

    /** Friction loss, permille of the stored energy per second. */
    public int lossPermillePerSecond() {
        return extraValue(6);
    }

    /** NE lost to friction per second at the current charge (computed from the synced values). */
    public long lossPerSecond() {
        return MomentumMath.lossPerSecond(wideEnergyStored(), lossPermillePerSecond());
    }

    /** The wheel's I/O rate (NE/t). */
    public long ioRate() {
        return gaugeExtra();
    }

    @Override
    public GaugeTail gaugeTail() {
        return this.gaugeTail;
    }
}
