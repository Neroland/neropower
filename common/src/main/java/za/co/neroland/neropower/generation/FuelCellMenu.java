package za.co.neroland.neropower.generation;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;
import za.co.neroland.nerotech.menu.MachineMenu;

import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.menu.GaugeTailMenu;
import za.co.neroland.neropower.registry.content.GenerationContent;

import static za.co.neroland.neropower.generation.FuelCellBlockEntity.*;

/**
 * Hydrogen Fuel Cell menu (tall hull 176 × 186): no machine slots, NeroTech's upgrade block and the
 * player inventory at y 104, and the cell's synced values read through the named getters below.
 */
public class FuelCellMenu extends MachineMenu implements GaugeTailMenu {

    public static final int WIDTH = 176;
    public static final int HEIGHT = 186;

    private static final int MACHINE_SLOTS = 0;
    private static final int DATA_SIZE = 7 + D_COUNT;

    private final GaugeTail gaugeTail;

    public FuelCellMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(MACHINE_SLOTS + NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public FuelCellMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(GenerationContent.FUEL_CELL_MENU.get(), id, container, data, MACHINE_SLOTS);
        this.gaugeTail = GaugeTail.of(container);
        this.addDataSlots(this.gaugeTail);
        addUpgradeAndPlayerSlots(playerInventory, 138, 18, 2, HEIGHT - 82);
    }

    @Override
    public GaugeTail gaugeTail() {
        return this.gaugeTail;
    }

    private long pair(int index) {
        return GaugeTail.join(extraValue(index), extraValue(index + 1));
    }

    public long hydrogenMb() {
        return pair(D_HYDROGEN_P);
    }

    public long hydrogenCapacityMb() {
        return pair(D_HYDROGEN_CAP_P);
    }

    public long oxygenMb() {
        return pair(D_OXYGEN_P);
    }

    public long oxygenCapacityMb() {
        return pair(D_OXYGEN_CAP_P);
    }

    public long waterMb() {
        return pair(D_WATER_P);
    }

    public long waterCapacityMb() {
        return pair(D_WATER_CAP_P);
    }

    public int gasUnitMb() {
        return Math.max(1, extraValue(D_GAS_UNIT));
    }

    public int outputNePerTick() {
        return extraValue(D_OUTPUT);
    }

    public int ratedNePerTick() {
        return extraValue(D_RATED);
    }

    /** 0 fed from the tank, 1 on air, 2 no oxygen. */
    public int oxygenMode() {
        return extraValue(D_OXYGEN_MODE);
    }

    public int ticksPerUnit() {
        return extraValue(D_TICKS_PER_UNIT);
    }

    public int nePerUnit() {
        return extraValue(D_NE_PER_UNIT);
    }

    public int waterPerUnitMb() {
        return extraValue(D_WATER_PER_UNIT);
    }

    public int readout() {
        return extraValue(D_READOUT);
    }

    public int stallCause() {
        return extraValue(D_STALL);
    }
}
