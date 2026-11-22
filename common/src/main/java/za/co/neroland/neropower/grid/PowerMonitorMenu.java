package za.co.neroland.neropower.grid;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;
import za.co.neroland.nerotech.menu.MachineMenu;

import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.menu.GaugeTailMenu;
import za.co.neroland.neropower.registry.content.GridContent;

/**
 * Power Monitor menu (wide hull 248 × 200): no machine slots and no serverbound
 * controls. The screen reads the ring, stored, capacity and source id from the block entity at
 * {@link #machinePos()} (the update tag); this menu syncs only readout, stall cause and "has source".
 */
public class PowerMonitorMenu extends MachineMenu implements GaugeTailMenu {

    public static final int WIDTH = 248;
    public static final int HEIGHT = 200;
    private static final int DATA_SIZE = 7 + PowerMonitorBlockEntity.EXTRA_DATA;

    private final GaugeTail gaugeTail;

    public PowerMonitorMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public PowerMonitorMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(GridContent.POWER_MONITOR_MENU.get(), id, container, data, 0);
        this.gaugeTail = GaugeTail.of(container);
        this.addDataSlots(this.gaugeTail);
        captureStatsTarget(playerInventory);
        int upgrades = this.totalNonPlayer - this.machineSlots;
        for (int i = 0; i < upgrades; i++) {
            this.addSlot(new PredicateSlot(this.container, this.machineSlots + i, 210 + (i % 2) * 18,
                    18 + (i / 2) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 43 + col * 18, 118 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 43 + col * 18, 176));
        }
    }

    public int readout() {
        return extraValue(0);
    }

    public int stallCause() {
        return extraValue(1);
    }

    public boolean hasSource() {
        return extraValue(2) != 0;
    }

    @Override
    public GaugeTail gaugeTail() {
        return this.gaugeTail;
    }
}
