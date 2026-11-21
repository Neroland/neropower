package za.co.neroland.neropower.generation;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;
import za.co.neroland.nerotech.menu.MachineMenu;

import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.menu.GaugeTailMenu;
import za.co.neroland.neropower.registry.content.BackEndContent;
import za.co.neroland.neropower.registry.content.GenerationContent;

import static za.co.neroland.neropower.generation.BetavoltaicCellBlockEntity.*;

/**
 * Betavoltaic Cell menu (tall hull 176 × 186): the capsule slot at (80, 30) — one capsule, sealed while
 * live, takeable once spent — the upgrade block, the player inventory and the cell's synced values.
 */
public class BetavoltaicCellMenu extends MachineMenu implements GaugeTailMenu {

    public static final int WIDTH = 176;
    public static final int HEIGHT = 186;
    public static final int SLOT_X = 80;
    public static final int SLOT_Y = 30;

    private static final int MACHINE_SLOTS = 1;
    private static final int DATA_SIZE = 7 + D_COUNT;

    private final GaugeTail gaugeTail;

    public BetavoltaicCellMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(MACHINE_SLOTS + NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public BetavoltaicCellMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(GenerationContent.BETAVOLTAIC_CELL_MENU.get(), id, container, data, MACHINE_SLOTS);
        this.gaugeTail = GaugeTail.of(container);
        this.addDataSlots(this.gaugeTail);
        this.addSlot(new CapsuleSlot(container, CAPSULE_SLOT, SLOT_X, SLOT_Y));
        addUpgradeAndPlayerSlots(playerInventory, 138, 18, 2, HEIGHT - 82);
    }

    @Override
    public GaugeTail gaugeTail() {
        return this.gaugeTail;
    }

    private long pair(int index) {
        return GaugeTail.join(extraValue(index), extraValue(index + 1));
    }

    /** Ticks of output left in the live capsule (0 otherwise). */
    public long capsuleTicksLeft() {
        return pair(D_TICKS_P);
    }

    /** Ticks of output a fresh capsule gives. */
    public long capsuleTicksMax() {
        return pair(D_TICKS_MAX_P);
    }

    public int outputNePerTick() {
        return extraValue(D_OUTPUT);
    }

    /** 0 none, 1 live, 2 spent. */
    public int capsuleState() {
        return extraValue(D_STATE);
    }

    public int readout() {
        return extraValue(D_READOUT);
    }

    public int stallCause() {
        return extraValue(D_STALL);
    }

    /** One capsule at a time; a live capsule is sealed in, a spent one can be taken. */
    private static final class CapsuleSlot extends Slot {

        CapsuleSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.container.canPlaceItem(this.getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPickup(Player player) {
            return !getItem().is(BackEndContent.BETA_CAPSULE.get());
        }
    }
}
