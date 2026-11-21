package za.co.neroland.neropower.backend;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;
import za.co.neroland.nerotech.menu.MachineMenu;

import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.menu.GaugeTailMenu;
import za.co.neroland.neropower.registry.content.BackEndContent;

/**
 * Vitrifier menu on the 176 × 186 hull: waste and sand slots, an output slot, the upgrade block and
 * the player inventory, plus the readout, stall cause and the two cost values for the screen's cost
 * line. The gauge tail's extra value is the NE drawn last tick.
 */
public class VitrifierMenu extends MachineMenu implements GaugeTailMenu {

    public static final int WIDTH = 176;
    public static final int HEIGHT = 186;

    public static final int WASTE_X = 48;
    public static final int WASTE_Y = 22;
    public static final int SAND_X = 48;
    public static final int SAND_Y = 42;
    public static final int OUTPUT_X = 108;
    public static final int OUTPUT_Y = 33;

    private static final int MACHINE_SLOTS = 3;
    private static final int DATA_SIZE = 7 + VitrifierBlockEntity.D_COUNT;

    private final GaugeTail gaugeTail;

    public VitrifierMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(MACHINE_SLOTS + NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public VitrifierMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(BackEndContent.VITRIFIER_MENU.get(), id, container, data, MACHINE_SLOTS);
        this.gaugeTail = GaugeTail.of(container, be -> be instanceof VitrifierBlockEntity v ? v.energyRate() : 0L);
        this.addDataSlots(this.gaugeTail);
        this.addSlot(new PredicateSlot(container, VitrifierBlockEntity.WASTE_SLOT, WASTE_X, WASTE_Y));
        this.addSlot(new PredicateSlot(container, VitrifierBlockEntity.SAND_SLOT, SAND_X, SAND_Y));
        this.addSlot(new OutputSlot(container, VitrifierBlockEntity.OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y));
        addUpgradeAndPlayerSlots(playerInventory, 138, 18, 2, HEIGHT - 82);
    }

    public int readout() {
        return extraValue(VitrifierBlockEntity.D_READOUT);
    }

    public int stallCause() {
        return extraValue(VitrifierBlockEntity.D_STALL);
    }

    public int nePerTick() {
        return extraValue(VitrifierBlockEntity.D_NE_PER_TICK);
    }

    public int ticksPerOp() {
        return extraValue(VitrifierBlockEntity.D_TICKS_PER_OP);
    }

    /** NE drawn last tick. */
    public long energyRate() {
        return this.gaugeTail.value(GaugeTail.EXTRA);
    }

    @Override
    public GaugeTail gaugeTail() {
        return this.gaugeTail;
    }
}
