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

import za.co.neroland.neropower.failure.FailureStage;
import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.menu.GaugeTailMenu;
import za.co.neroland.neropower.menu.MenuButtons;
import za.co.neroland.neropower.protection.Protection;
import za.co.neroland.neropower.registry.content.GridContent;

/**
 * Circuit Breaker menu (tall hull 176 × 222): no machine slots. Controls
 *: {@code reset} (value 0), {@code redstoneReset} (0/1) and {@code limit} as a
 * {@link ScaledValue}: action {@code ACTION_LIMIT + exponent}, value = mantissa.
 */
public class CircuitBreakerMenu extends MachineMenu implements GaugeTailMenu {

    public static final int ACTION_RESET = 1;
    public static final int ACTION_REDSTONE_RESET = 2;
    /** Limit: actions {@code ACTION_LIMIT .. ACTION_LIMIT + ScaledValue.MAX_EXP}. */
    public static final int ACTION_LIMIT = 10;

    private static final int DATA_SIZE = 7 + CircuitBreakerBlockEntity.EXTRA_DATA;

    private final GaugeTail gaugeTail;

    public CircuitBreakerMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public CircuitBreakerMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(GridContent.CIRCUIT_BREAKER_MENU.get(), id, container, data, 0);
        this.gaugeTail = GaugeTail.of(container);
        this.addDataSlots(this.gaugeTail);
        addUpgradeAndPlayerSlots(playerInventory, 138, 18, 2, 140); // tall 176 x 222 hull
    }

    /** The button id that sets the limit to {@code value} NE/t (three significant digits). */
    public static int limitButton(long value) {
        int[] ev = ScaledValue.encode(BreakerLogic.clampLimit(value));
        return MenuButtons.encode(ACTION_LIMIT + ev[0], ev[1]);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(this.container instanceof CircuitBreakerBlockEntity breaker) || !stillValid(player)
                || !(player instanceof ServerPlayer serverPlayer) || !(breaker.getLevel() instanceof ServerLevel level)
                || !Protection.get().mayInteract(serverPlayer, level, breaker.getBlockPos())) {
            return false;
        }
        int action = MenuButtons.action(id);
        int value = MenuButtons.value(id);
        if (action == ACTION_RESET) {
            breaker.reset();
            return true;
        }
        if (action == ACTION_REDSTONE_RESET) {
            breaker.setRedstoneReset(value != 0);
            return true;
        }
        if (action >= ACTION_LIMIT && action <= ACTION_LIMIT + ScaledValue.MAX_EXP) {
            breaker.setLimit(ScaledValue.decode(action - ACTION_LIMIT, value));
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

    public long limit() {
        return GaugeTail.join(extraValue(2), extraValue(3));
    }

    public long demand() {
        return GaugeTail.join(extraValue(4), extraValue(5));
    }

    public long passed() {
        return GaugeTail.join(extraValue(6), extraValue(7));
    }

    public boolean tripped() {
        return extraValue(8) != 0;
    }

    public boolean redstoneReset() {
        return extraValue(9) != 0;
    }

    public FailureStage stage() {
        return FailureStage.byCode(extraValue(10));
    }

    public int stressPermille() {
        return extraValue(11);
    }

    @Override
    public GaugeTail gaugeTail() {
        return this.gaugeTail;
    }
}
