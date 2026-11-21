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
 * Dry Cask menu on the 176 × 222 hull: the 27-slot store at the top, the four upgrade slots in a row
 * beside the heat bar, the player inventory, plus the cask's synced values (slots used, shielded faces,
 * leaking, trace heat, radiation on, readout). The gauge tail's extra value is the radioactive slot count.
 */
public class DryCaskMenu extends MachineMenu implements GaugeTailMenu {

    public static final int WIDTH = 176;
    public static final int HEIGHT = 222;

    /** Store grid origin. */
    public static final int GRID_X = 8;
    public static final int GRID_Y = 20;

    /** Upgrade row origin (four in a row). */
    public static final int UPGRADE_X = 98;
    public static final int UPGRADE_Y = 78;

    private static final int DATA_SIZE = 7 + DryCaskBlockEntity.D_COUNT;

    private final GaugeTail gaugeTail;

    public DryCaskMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(CaskMath.CASK_SLOTS + NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public DryCaskMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(BackEndContent.DRY_CASK_MENU.get(), id, container, data, CaskMath.CASK_SLOTS);
        this.gaugeTail = GaugeTail.of(container,
                be -> be instanceof DryCaskBlockEntity cask ? cask.radioactiveSlots() : 0L);
        this.addDataSlots(this.gaugeTail);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new PredicateSlot(container, col + row * 9, GRID_X + col * 18, GRID_Y + row * 18));
            }
        }
        addUpgradeAndPlayerSlots(playerInventory, UPGRADE_X, UPGRADE_Y, 4, HEIGHT - 82);
    }

    public int occupied() {
        return extraValue(DryCaskBlockEntity.D_OCCUPIED);
    }

    /** Faces covered by a radiation barrier (0..6). */
    public int shieldedFaces() {
        return extraValue(DryCaskBlockEntity.D_SHIELDED);
    }

    /** Holding spent rods or residue with at least one open face. */
    public boolean leaking() {
        return extraValue(DryCaskBlockEntity.D_LEAKING) != 0;
    }

    /** Heat added once per {@link #heatInterval()} ticks. */
    public int traceHeat() {
        return extraValue(DryCaskBlockEntity.D_TRACE);
    }

    public int heatInterval() {
        return extraValue(DryCaskBlockEntity.D_INTERVAL);
    }

    public boolean radiationOn() {
        return extraValue(DryCaskBlockEntity.D_RADIATION_ON) != 0;
    }

    public int readout() {
        return extraValue(DryCaskBlockEntity.D_READOUT);
    }

    public int stallCause() {
        return extraValue(DryCaskBlockEntity.D_STALL);
    }

    /** Slots holding spent rods or residue. */
    public int radioactive() {
        return extraValue(DryCaskBlockEntity.D_RADIOACTIVE);
    }

    @Override
    public GaugeTail gaugeTail() {
        return this.gaugeTail;
    }
}
