package za.co.neroland.neropower.beam;

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
 * Phased Array Emitter menu (tall hull 176 × 222, screens/phased_array_emitter.md): no machine slots. Synced
 * data: {@link PhasedArrayEmitterBlockEntity#EXTRA_DATA}; no coordinates. Controls (decision 012):
 * {@code move} (value = row × 2 + 0 up / 1 down) and {@code unlink} (value = row).
 */
public class PhasedArrayEmitterMenu extends MachineMenu implements GaugeTailMenu {

    public static final int ACTION_MOVE = 1;
    public static final int ACTION_UNLINK = 2;

    private static final int DATA_SIZE = 7 + PhasedArrayEmitterBlockEntity.EXTRA_DATA;

    private final GaugeTail gaugeTail;

    public PhasedArrayEmitterMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public PhasedArrayEmitterMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(GridContent.PHASED_ARRAY_EMITTER_MENU.get(), id, container, data, 0);
        this.gaugeTail = GaugeTail.of(container,
                be -> be instanceof PhasedArrayEmitterBlockEntity emitter ? emitter.lastPassNe() : 0L);
        this.addDataSlots(this.gaugeTail);
        addUpgradeAndPlayerSlots(playerInventory, 138, 18, 2, 140); // tall 176 x 222 hull
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(this.container instanceof PhasedArrayEmitterBlockEntity emitter) || !stillValid(player)
                || !(player instanceof ServerPlayer serverPlayer) || !(emitter.getLevel() instanceof ServerLevel level)
                || !Protection.get().mayInteract(serverPlayer, level, emitter.getBlockPos())) {
            return false;
        }
        int value = MenuButtons.value(id);
        switch (MenuButtons.action(id)) {
            case ACTION_MOVE -> emitter.move(value / 2, value % 2 == 0);
            case ACTION_UNLINK -> emitter.unlink(value);
            default -> {
                return false;
            }
        }
        return true;
    }

    public int readout() {
        return extraValue(0);
    }

    public int stallCause() {
        return extraValue(1);
    }

    public int maxTargets() {
        return Math.max(1, Math.min(PhasedArrayEmitterBlockEntity.ROWS, extraValue(2)));
    }

    public int targetCount() {
        return Math.max(0, Math.min(PhasedArrayEmitterBlockEntity.ROWS, extraValue(3)));
    }

    /** The row served by the last pass, or -1. */
    public int servingIndex() {
        return extraValue(4) - 1;
    }

    public long passNe() {
        return GaugeTail.join(extraValue(5), extraValue(6));
    }

    public long transferPerPass() {
        return GaugeTail.join(extraValue(7), extraValue(8));
    }

    public int distance(int row) {
        return extraValue(9 + 3 * row);
    }

    public int lossPermille(int row) {
        return extraValue(10 + 3 * row);
    }

    public boolean blocked(int row) {
        return (extraValue(11 + 3 * row) & 1) != 0;
    }

    public boolean outOfRange(int row) {
        return (extraValue(11 + 3 * row) & 2) != 0;
    }

    @Override
    public GaugeTail gaugeTail() {
        return this.gaugeTail;
    }
}
