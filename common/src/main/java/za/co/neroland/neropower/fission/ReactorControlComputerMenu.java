package za.co.neroland.neropower.fission;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;
import za.co.neroland.nerotech.menu.MachineMenu;

import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.menu.GaugeTailMenu;
import za.co.neroland.neropower.menu.MenuButtons;
import za.co.neroland.neropower.registry.content.FissionContent;

/**
 * Reactor Control Computer menu (wide tall hull, 248 × 222): no machine slots, the upgrade block at
 * {@code 210,18}, the player inventory at x 43, plus the computer's
 * {@value ReactorControlComputerBlockEntity#EXTRA_DATA} synced ints and the output rate as the gauge
 * tail's wide value. Controls arrive through {@link #clickMenuButton} as {@link MenuButtons} ids
 *.
 */
public class ReactorControlComputerMenu extends MachineMenu implements GaugeTailMenu {

    /** Actions (value in brackets): thresholds in percent (stage 1..3), toggles 0/1, choices by index. */
    public static final int ACTION_THRESHOLD_HEAT = 0;
    public static final int ACTION_THRESHOLD_FUEL = 1;
    public static final int ACTION_THRESHOLD_OUTPUT = 2;
    public static final int ACTION_THRESHOLD_STAGE = 3;
    public static final int ACTION_AUTO_SCRAM = 4;
    public static final int ACTION_AUTO_SCRAM_AT = 5;
    public static final int ACTION_REDSTONE_SIGNAL = 6;
    public static final int ACTION_COMPARATOR_SIGNAL = 7;
    public static final int ACTION_TEST_LAMPS = 8;

    public static final int WIDTH = 248;
    public static final int HEIGHT = 222;

    private static final int DATA_SIZE = 7 + ReactorControlComputerBlockEntity.EXTRA_DATA;

    private final GaugeTail gaugeTail;

    public ReactorControlComputerMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public ReactorControlComputerMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(FissionContent.REACTOR_CONTROL_COMPUTER_MENU.get(), id, container, data, 0);
        this.gaugeTail = GaugeTail.of(container,
                be -> be instanceof ReactorControlComputerBlockEntity computer ? computer.outputRate() : 0L);
        this.addDataSlots(this.gaugeTail);
        addWideSlots(playerInventory);
    }

    /** NeroTech's slot order (upgrades, 27 inventory, 9 hotbar) on the wide hull. */
    private void addWideSlots(Inventory playerInventory) {
        captureStatsTarget(playerInventory);
        int upgrades = this.totalNonPlayer - this.machineSlots;
        for (int i = 0; i < upgrades; i++) {
            this.addSlot(new PredicateSlot(this.container, this.machineSlots + i, 210 + (i % 2) * 18,
                    18 + (i / 2) * 18));
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

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(this.container instanceof ReactorControlComputerBlockEntity computer) || !stillValid(player)
                || !za.co.neroland.neropower.protection.Protection.mayUse(player, computer)) {
            return false;
        }
        int value = MenuButtons.value(id);
        switch (MenuButtons.action(id)) {
            case ACTION_THRESHOLD_HEAT -> computer.setThreshold(ControlComputerLogic.HEAT,
                    MenuButtons.clamp(value, 0, 100) * 10);
            case ACTION_THRESHOLD_FUEL -> computer.setThreshold(ControlComputerLogic.FUEL,
                    MenuButtons.clamp(value, 0, 100) * 10);
            case ACTION_THRESHOLD_OUTPUT -> computer.setThreshold(ControlComputerLogic.OUTPUT,
                    MenuButtons.clamp(value, 0, 100) * 10);
            case ACTION_THRESHOLD_STAGE -> computer.setThreshold(ControlComputerLogic.STAGE,
                    MenuButtons.clamp(value, 1, 3));
            case ACTION_AUTO_SCRAM -> computer.setAutoScram(value != 0);
            case ACTION_AUTO_SCRAM_AT -> computer.setAutoScramAt(MenuButtons.clamp(value, 50, 100) * 10);
            case ACTION_REDSTONE_SIGNAL -> computer.setRedstoneSignal(
                    MenuButtons.clamp(value, 0, ControlComputerLogic.OFF));
            case ACTION_COMPARATOR_SIGNAL -> computer.setComparatorSignal(
                    MenuButtons.clamp(value, 0, ControlComputerLogic.SIGNALS - 1));
            case ACTION_TEST_LAMPS -> computer.testLamps();
            default -> {
                return false;
            }
        }
        return true;
    }

    private int x(int index) {
        return extraValue(index);
    }

    public int heat() {
        return x(ReactorControlComputerBlockEntity.EXTRA_HEAT);
    }

    public int fuel() {
        return x(ReactorControlComputerBlockEntity.EXTRA_FUEL);
    }

    public int rods() {
        return x(ReactorControlComputerBlockEntity.EXTRA_RODS);
    }

    public int output() {
        return x(ReactorControlComputerBlockEntity.EXTRA_OUTPUT);
    }

    public boolean steamMode() {
        return x(ReactorControlComputerBlockEntity.EXTRA_STEAM_MODE) != 0;
    }

    public int stage() {
        return x(ReactorControlComputerBlockEntity.EXTRA_STAGE);
    }

    /** The threshold of {@code signal}: permille, or the stage code for {@link ControlComputerLogic#STAGE}. */
    public int threshold(int signal) {
        return x(ReactorControlComputerBlockEntity.EXTRA_T_HEAT + Math.max(0, Math.min(3, signal)));
    }

    /** The live reading of {@code signal}: permille, or the stage code. */
    public int reading(int signal) {
        return switch (signal) {
            case ControlComputerLogic.HEAT -> heat();
            case ControlComputerLogic.FUEL -> fuel();
            case ControlComputerLogic.OUTPUT -> output();
            default -> stage();
        };
    }

    public boolean autoScram() {
        return x(ReactorControlComputerBlockEntity.EXTRA_AUTO_SCRAM) != 0;
    }

    public int autoScramAt() {
        return x(ReactorControlComputerBlockEntity.EXTRA_AUTO_SCRAM_AT);
    }

    public int redstoneSignal() {
        return x(ReactorControlComputerBlockEntity.EXTRA_REDSTONE_SIGNAL);
    }

    public int comparatorSignal() {
        return x(ReactorControlComputerBlockEntity.EXTRA_COMPARATOR_SIGNAL);
    }

    public int redstoneNow() {
        return x(ReactorControlComputerBlockEntity.EXTRA_REDSTONE_NOW);
    }

    public boolean linked() {
        return x(ReactorControlComputerBlockEntity.EXTRA_LINKED) != 0;
    }

    public boolean tripped() {
        return x(ReactorControlComputerBlockEntity.EXTRA_TRIPPED) != 0;
    }

    /** NE/t (direct) or mB/t of steam (steam mode) now. */
    public long outputRate() {
        return this.gaugeTail.value(GaugeTail.EXTRA);
    }

    @Override
    public GaugeTail gaugeTail() {
        return this.gaugeTail;
    }
}
