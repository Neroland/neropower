package za.co.neroland.neropower.grid;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;
import za.co.neroland.nerotech.menu.MachineMenu;

import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.menu.GaugeTailMenu;
import za.co.neroland.neropower.menu.MenuButtons;
import za.co.neroland.neropower.protection.Protection;
import za.co.neroland.neropower.registry.content.GridContent;

/**
 * Load Bank menu (tall hull 176 × 186): no machine slots. Control:
 * {@code rate} as a {@link ScaledValue} (action {@code ACTION_RATE + exponent}, value = mantissa); "Zero"
 * sends rate 0.
 */
public class LoadBankMenu extends MachineMenu implements GaugeTailMenu {

    public static final int ACTION_RATE = 10;

    private static final int DATA_SIZE = 7 + LoadBankBlockEntity.EXTRA_DATA;

    private final GaugeTail gaugeTail;

    public LoadBankMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public LoadBankMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(GridContent.LOAD_BANK_MENU.get(), id, container, data, 0);
        this.gaugeTail = GaugeTail.of(container);
        this.addDataSlots(this.gaugeTail);
        addUpgradeAndPlayerSlots(playerInventory, 138, 18, 2, 104); // tall 176 x 186 hull
    }

    public static int rateButton(long value) {
        int[] ev = ScaledValue.encode(value);
        return MenuButtons.encode(ACTION_RATE + ev[0], ev[1]);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(this.container instanceof LoadBankBlockEntity bank) || !stillValid(player)
                || !(player instanceof ServerPlayer serverPlayer) || !(bank.getLevel() instanceof ServerLevel level)
                || !Protection.get().mayInteract(serverPlayer, level, bank.getBlockPos())) {
            return false;
        }
        int action = MenuButtons.action(id);
        if (action >= ACTION_RATE && action <= ACTION_RATE + ScaledValue.MAX_EXP) {
            bank.setRate(ScaledValue.decode(action - ACTION_RATE, MenuButtons.value(id)));
            return true;
        }
        return false;
    }

    public int readout() {
        return extraValue(0);
    }

    public int stallCause() {
        return extraValue(1);
    }

    public long rateSet() {
        return GaugeTail.join(extraValue(2), extraValue(3));
    }

    public long rateDrawn() {
        return GaugeTail.join(extraValue(4), extraValue(5));
    }

    public long maxRate() {
        return GaugeTail.join(extraValue(6), extraValue(7));
    }

    /** Heat made per tick, in tenths. */
    public int heatTenthsPerTick() {
        return extraValue(8);
    }

    @Override
    public GaugeTail gaugeTail() {
        return this.gaugeTail;
    }
}
