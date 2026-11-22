package za.co.neroland.neropower.fission;

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

/**
 * Fission Core menu (tall hull, 176 × 222): six rod slots in two rows of three, the blanket pair under
 * them, NeroTech's upgrade block and the player inventory at y 140, plus the core's
 * {@value FissionCoreBlockEntity#EXTRA_DATA} synced ints and the output rate (NE/t or mB/t of steam) as
 * the gauge tail's wide value. The mode button arrives through {@link #clickMenuButton} as a
 * {@link MenuButtons} id. Locked rod slots refuse every stack (the block entity knows the
 * shell size).
 */
public class FissionCoreMenu extends MachineMenu implements GaugeTailMenu {

    /** Action: switch mode (value 0 direct, 1 steam). */
    public static final int ACTION_MODE = 0;

    public static final int HEIGHT = 222;

    private static final int MACHINE_SLOTS = FissionCoreBlockEntity.MACHINE_SLOTS;
    private static final int DATA_SIZE = 7 + FissionCoreBlockEntity.EXTRA_DATA;

    private final GaugeTail gaugeTail;

    public FissionCoreMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(MACHINE_SLOTS + NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public FissionCoreMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(FissionContent.FISSION_CORE_MENU.get(), id, container, data, MACHINE_SLOTS);
        this.gaugeTail = GaugeTail.of(container,
                be -> be instanceof FissionCoreBlockEntity core ? core.lastRate() : 0L);
        this.addDataSlots(this.gaugeTail);
        for (int slot = 0; slot < FissionCoreBlockEntity.ROD_SLOTS; slot++) {
            this.addSlot(new PredicateSlot(container, slot, 76 + (slot % 3) * 18, 20 + (slot / 3) * 18));
        }
        this.addSlot(new PredicateSlot(container, FissionCoreBlockEntity.BLANKET_IN, 76, 60));
        this.addSlot(new PredicateSlot(container, FissionCoreBlockEntity.BLANKET_OUT, 112, 60));
        addUpgradeAndPlayerSlots(playerInventory, 138, 18, 2, 140);
    }

    /**
     * Hand path: a part-burnt rod clicked out of a rod slot (pick up, shift-click, swap with
     * the hotbar, drop) leaves as a Hot Spent Fuel Rod. Server side the rod is converted before the click and
     * put back if it did not leave (a click that changed nothing, or a full inventory).
     */
    @Override
    //? if >=26.1 {
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ContainerInput clickType, Player player) {
    //?} else {
    /*public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {
    *///?}
        if (slotId >= 0 && slotId < this.slots.size() && !player.level().isClientSide()
                && this.container instanceof FissionCoreBlockEntity core
                && this.slots.get(slotId).container == core
                && this.slots.get(slotId).getContainerSlot() < FissionCoreBlockEntity.ROD_SLOTS) {
            int index = this.slots.get(slotId).getContainerSlot();
            net.minecraft.world.item.ItemStack original = core.spendLeavingRod(index);
            if (original != null) {
                net.minecraft.world.item.ItemStack converted = core.getItem(index);
                super.clicked(slotId, button, clickType, player);
                core.restoreRod(index, converted, original);
                return;
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(this.container instanceof FissionCoreBlockEntity core) || !stillValid(player)
                || !za.co.neroland.neropower.protection.Protection.mayUse(player, core)) {
            return false;
        }
        if (MenuButtons.action(id) == ACTION_MODE) {
            core.requestMode(MenuButtons.value(id) != 0 ? ReactorMode.STEAM : ReactorMode.DIRECT);
            return true;
        }
        return false;
    }

    private int x(int index) {
        return extraValue(index);
    }

    private long pair(int index) {
        return GaugeTail.join(x(index), x(index + 1));
    }

    /** Burn-up (permille) of the rod in slot {@code slot}; 0 when empty, 1000 when spent. */
    public int rodBurnup(int slot) {
        return slot < 0 || slot >= FissionCoreBlockEntity.ROD_SLOTS ? 0 : x(slot);
    }

    public int poison() {
        return x(FissionCoreBlockEntity.EXTRA_POISON);
    }

    /** Reactivity reduction (permille; 1000 = nothing absorbing inside). */
    public int controlRodPermille() {
        return x(FissionCoreBlockEntity.EXTRA_CONTROL_ROD);
    }

    public int failureStageCode() {
        return x(FissionCoreBlockEntity.EXTRA_FAILURE_STAGE);
    }

    /** The formed shell's edge (3 / 5 / 7), or 0 while unformed. */
    public int shellSize() {
        return x(FissionCoreBlockEntity.EXTRA_SHELL_SIZE);
    }

    public int usableRodSlots() {
        return FissionMath.usableRodSlots(shellSize());
    }

    public ReactorMode mode() {
        return ReactorMode.byCode(x(FissionCoreBlockEntity.EXTRA_MODE));
    }

    public ReactorMode requestedMode() {
        return ReactorMode.byCode(x(FissionCoreBlockEntity.EXTRA_REQUESTED_MODE));
    }

    public int steamPorts() {
        return x(FissionCoreBlockEntity.EXTRA_STEAM_PORTS);
    }

    public int breedPermille() {
        return x(FissionCoreBlockEntity.EXTRA_BREED);
    }

    public int blankets() {
        return x(FissionCoreBlockEntity.EXTRA_BLANKETS);
    }

    public int blanketsCounted() {
        return x(FissionCoreBlockEntity.EXTRA_BLANKETS_COUNTED);
    }

    public int blanketsMax() {
        return x(FissionCoreBlockEntity.EXTRA_BLANKETS_MAX);
    }

    public int blanketCostPermille() {
        return x(FissionCoreBlockEntity.EXTRA_BLANKET_COST);
    }

    public boolean cryoFed() {
        return x(FissionCoreBlockEntity.EXTRA_CRYO_FED) != 0;
    }

    public int cryoBandPermille() {
        return x(FissionCoreBlockEntity.EXTRA_CRYO_BAND);
    }

    public int scramTicks() {
        return x(FissionCoreBlockEntity.EXTRA_SCRAM_TICKS);
    }

    public int moderatorPermille() {
        return x(FissionCoreBlockEntity.EXTRA_MODERATOR);
    }

    public int readout() {
        return x(FissionCoreBlockEntity.EXTRA_READOUT);
    }

    public int stallCause() {
        return x(FissionCoreBlockEntity.EXTRA_STALL);
    }

    public long waterMb() {
        return pair(FissionCoreBlockEntity.EXTRA_WATER);
    }

    public long waterCapacityMb() {
        return pair(FissionCoreBlockEntity.EXTRA_WATER_CAP);
    }

    public long steamMb() {
        return pair(FissionCoreBlockEntity.EXTRA_STEAM);
    }

    public long steamCapacityMb() {
        return pair(FissionCoreBlockEntity.EXTRA_STEAM_CAP);
    }

    /** NE/t (direct) or mB/t of steam (steam mode) this tick. */
    public long rate() {
        return this.gaugeTail.value(GaugeTail.EXTRA);
    }

    @Override
    public GaugeTail gaugeTail() {
        return this.gaugeTail;
    }
}
