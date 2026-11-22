package za.co.neroland.neropower.beam;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import za.co.neroland.neropower.protection.Protection;
import za.co.neroland.neropower.registry.content.GridContent;

/**
 * Beam Splitter menu (wide tall hull 248 × 222): the lens slot, the upgrade block
 * at {@code 210,18} and the player inventory at x 43. Synced data: see
 * {@link BeamSplitterBlockEntity#EXTRA_DATA}; the gauge tail's extra value is the last incoming pass. No
 * coordinates are synced. Controls arrive as {@link MenuButtons} ids.
 */
public class BeamSplitterMenu extends MachineMenu implements GaugeTailMenu {

    /** {@code unlink}: value = row. */
    public static final int ACTION_UNLINK = 1;
    /** {@code share} for row r: action {@code ACTION_SHARE + r}, value = permille 0..999. */
    public static final int ACTION_SHARE = 10;
    /** {@code share} of exactly 1000 permille for row r: action {@code ACTION_SHARE_FULL + r}. */
    public static final int ACTION_SHARE_FULL = 20;

    public static final int WIDTH = 248;
    public static final int HEIGHT = 222;
    private static final int MACHINE_SLOTS = 1;
    private static final int DATA_SIZE = 7 + BeamSplitterBlockEntity.EXTRA_DATA;

    private final GaugeTail gaugeTail;

    public BeamSplitterMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(MACHINE_SLOTS + NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public BeamSplitterMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(GridContent.BEAM_SPLITTER_MENU.get(), id, container, data, MACHINE_SLOTS);
        this.gaugeTail = GaugeTail.of(container,
                be -> be instanceof BeamSplitterBlockEntity splitter ? splitter.lastIn() : 0L);
        this.addDataSlots(this.gaugeTail);
        this.addSlot(new PredicateSlot(container, BeamSplitterBlockEntity.LENS_SLOT, 188, 20));
        addWideSlots(playerInventory);
    }

    /** NeroTech's slot order (machine, upgrades, 27 inventory, 9 hotbar) on the wide hull. */
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

    /** The button id that sets row {@code row}'s share to {@code permille} (0..1000). */
    public static int shareButton(int row, int permille) {
        int p = MenuButtons.clamp(permille, 0, 1000);
        return p >= 1000 ? MenuButtons.encode(ACTION_SHARE_FULL + row, 0) : MenuButtons.encode(ACTION_SHARE + row, p);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(this.container instanceof BeamSplitterBlockEntity splitter) || !stillValid(player)
                || !(player instanceof ServerPlayer serverPlayer) || !(splitter.getLevel() instanceof ServerLevel level)
                || !Protection.get().mayInteract(serverPlayer, level, splitter.getBlockPos())) {
            return false;
        }
        int action = MenuButtons.action(id);
        int value = MenuButtons.value(id);
        if (action == ACTION_UNLINK) {
            splitter.unlink(value);
            return true;
        }
        if (action >= ACTION_SHARE && action < ACTION_SHARE + BeamSplitterBlockEntity.ROWS) {
            splitter.setShare(action - ACTION_SHARE, MenuButtons.clamp(value, 0, 999));
            return true;
        }
        if (action >= ACTION_SHARE_FULL && action < ACTION_SHARE_FULL + BeamSplitterBlockEntity.ROWS) {
            splitter.setShare(action - ACTION_SHARE_FULL, 1000);
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

    public int maxLinks() {
        return Math.max(1, Math.min(BeamSplitterBlockEntity.ROWS, extraValue(2)));
    }

    public int linkCount() {
        return Math.max(0, Math.min(BeamSplitterBlockEntity.ROWS, extraValue(3)));
    }

    public int lensLossPermille() {
        return extraValue(4);
    }

    public SplitMath.Lens lens() {
        return SplitMath.Lens.byOrdinal(extraValue(5));
    }

    public boolean hasIncoming() {
        return extraValue(6) != 0;
    }

    /** NE that arrived in the last pass. */
    public long lastIn() {
        return GaugeTail.join(extraValue(7), extraValue(8));
    }

    /** Configured loss of {@code lens} (1..4) in permille. */
    public int configuredLoss(SplitMath.Lens lens) {
        return lens == SplitMath.Lens.NONE ? 1000 : extraValue(8 + lens.ordinal());
    }

    public int distance(int row) {
        return extraValue(13 + 3 * row);
    }

    public int share(int row) {
        return extraValue(14 + 3 * row);
    }

    public boolean blocked(int row) {
        return (extraValue(15 + 3 * row) & 1) != 0;
    }

    public boolean outOfRange(int row) {
        return (extraValue(15 + 3 * row) & 2) != 0;
    }

    /** Sum of the shares (permille). */
    public int totalShare() {
        int sum = 0;
        for (int i = 0; i < linkCount(); i++) {
            sum += share(i);
        }
        return Math.min(1000, sum);
    }

    @Override
    public GaugeTail gaugeTail() {
        return this.gaugeTail;
    }
}
