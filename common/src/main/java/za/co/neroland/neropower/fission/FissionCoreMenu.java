package za.co.neroland.neropower.fission;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;
import za.co.neroland.nerotech.menu.MachineMenu;

/**
 * Fission Core menu: four rod slots in a row across the machine area (locked ones simply refuse
 * every stack — the block entity's {@code canPlaceItem} knows the shell size), NeroTech's upgrade
 * block and player inventory, plus {@value FissionCoreBlockEntity#EXTRA_DATA} machine-specific
 * synced ints after the seven shared ones: each rod's burn-up, poison, the control-rod factor,
 * the failure stage code and the shell size — read by {@code client.FissionCoreScreen}.
 */
public class FissionCoreMenu extends MachineMenu {

    private static final int MACHINE_SLOTS = FissionCoreBlockEntity.ROD_SLOTS;

    /** Seven shared ContainerData indices + the core's status ints. */
    private static final int DATA_SIZE = 7 + FissionCoreBlockEntity.EXTRA_DATA;

    /** Rod row origin: clear of the gauge column (x+8..30) and the upgrade block (x+138); status text below. */
    private static final int ROD_X = 44;
    private static final int ROD_Y = 19;
    private static final int ROD_STRIDE = 22;

    public FissionCoreMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(MACHINE_SLOTS + NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public FissionCoreMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(FissionContent.FISSION_CORE_MENU.get(), id, container, data, MACHINE_SLOTS);
        for (int slot = 0; slot < MACHINE_SLOTS; slot++) {
            this.addSlot(new PredicateSlot(container, slot, ROD_X + slot * ROD_STRIDE, ROD_Y));
        }
        addUpgradeAndPlayerSlots(playerInventory);
    }

    /** Burn-up (permille) of the rod in slot {@code slot}; 0 when empty, 1000 when spent. */
    public int rodBurnup(int slot) {
        return slot < 0 || slot >= MACHINE_SLOTS ? 0 : extraValue(slot);
    }

    /** Neutron poison (permille). */
    public int poison() {
        return extraValue(FissionCoreBlockEntity.EXTRA_POISON);
    }

    /** Control-rod factor (permille; 1000 = no assemblies). */
    public int controlRodPermille() {
        return extraValue(FissionCoreBlockEntity.EXTRA_CONTROL_ROD);
    }

    /** The failure stage code ({@code FailureStage.code()}: 0 stable .. 3 failure). */
    public int failureStageCode() {
        return extraValue(FissionCoreBlockEntity.EXTRA_FAILURE_STAGE);
    }

    /** The formed shell's edge (3 / 5), or 0 while unformed. */
    public int shellSize() {
        return extraValue(FissionCoreBlockEntity.EXTRA_SHELL_SIZE);
    }

    /** Rod slots the current shell can use. */
    public int usableRodSlots() {
        return FissionMath.usableRodSlots(shellSize());
    }
}
