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
 * Spent Fuel Rack menu on the 176 × 206 hull: four rod slots in a 2 × 2 block (slot 0 front-left, as
 * the rod heads on the block), the upgrade block and the player inventory, plus the rack's synced
 * values (per-slot cooling and time left, rod states, water contacts, heat rates, failure stage,
 * readout and stall cause) read through the getters below. The gauge tail's extra value is the ticks
 * until the next rod is cooled.
 */
public class SpentFuelPoolMenu extends MachineMenu implements GaugeTailMenu {

    public static final int WIDTH = 176;
    public static final int HEIGHT = 206;

    /** Rod slot positions (panel-relative), slot 0..3. */
    public static final int[] SLOT_X = {40, 66, 40, 66};
    public static final int[] SLOT_Y = {24, 24, 50, 50};

    private static final int DATA_SIZE = 7 + SpentFuelPoolBlockEntity.D_COUNT;

    private final GaugeTail gaugeTail;

    public SpentFuelPoolMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(PoolMath.SLOTS + NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public SpentFuelPoolMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(BackEndContent.SPENT_FUEL_POOL_MENU.get(), id, container, data, PoolMath.SLOTS);
        this.gaugeTail = GaugeTail.of(container,
                be -> be instanceof SpentFuelPoolBlockEntity pool ? pool.ticksToNextCooled() : 0L);
        this.addDataSlots(this.gaugeTail);
        for (int i = 0; i < PoolMath.SLOTS; i++) {
            this.addSlot(new PredicateSlot(container, i, SLOT_X[i], SLOT_Y[i]));
        }
        addUpgradeAndPlayerSlots(playerInventory, 138, 18, 2, HEIGHT - 82);
    }

    /** Cooling progress of a hot rod in {@code slot}, permille (0 otherwise). */
    public int coolingPermille(int slot) {
        return inSlots(slot) ? extraValue(SpentFuelPoolBlockEntity.D_COOLING + slot) : 0;
    }

    /** Ticks until the hot rod in {@code slot} is cooled (0 otherwise). */
    public long coolTicksLeft(int slot) {
        if (!inSlots(slot)) {
            return 0L;
        }
        int base = SpentFuelPoolBlockEntity.D_LEFT + 2 * slot;
        return GaugeTail.join(extraValue(base), extraValue(base + 1));
    }

    /** 0 empty, 1 hot rod, 2 cooled rod. */
    public int rodState(int slot) {
        return inSlots(slot) ? extraValue(SpentFuelPoolBlockEntity.D_ROD_STATE + slot) : 0;
    }

    /** Hot rods in the rack. */
    public int hotRods() {
        int hot = 0;
        for (int i = 0; i < PoolMath.SLOTS; i++) {
            if (rodState(i) == 1) {
                hot++;
            }
        }
        return hot;
    }

    public int waterContacts() {
        return extraValue(SpentFuelPoolBlockEntity.D_CONTACTS);
    }

    public int rodHeatPerTick() {
        return extraValue(SpentFuelPoolBlockEntity.D_ROD_HEAT);
    }

    public int coolingPerContact() {
        return extraValue(SpentFuelPoolBlockEntity.D_COOLING_PER_CONTACT);
    }

    /** Failure stage code (0 stable .. 3 failure). */
    public int stageCode() {
        return extraValue(SpentFuelPoolBlockEntity.D_STAGE);
    }

    /** Heat now, permille of capacity. */
    public int stageHeat() {
        return extraValue(SpentFuelPoolBlockEntity.D_STAGE_HEAT);
    }

    /** Heat permille at which the next stage starts. */
    public int stageNext() {
        return extraValue(SpentFuelPoolBlockEntity.D_STAGE_NEXT);
    }

    public boolean radiationOn() {
        return extraValue(SpentFuelPoolBlockEntity.D_RADIATION_ON) != 0;
    }

    public int readout() {
        return extraValue(SpentFuelPoolBlockEntity.D_READOUT);
    }

    public int stallCause() {
        return extraValue(SpentFuelPoolBlockEntity.D_STALL);
    }

    /** Ticks until the next rod is cooled (0 with no hot rod). */
    public long ticksToNextCooled() {
        return this.gaugeTail.value(GaugeTail.EXTRA);
    }

    private static boolean inSlots(int slot) {
        return slot >= 0 && slot < PoolMath.SLOTS;
    }

    @Override
    public GaugeTail gaugeTail() {
        return this.gaugeTail;
    }
}
