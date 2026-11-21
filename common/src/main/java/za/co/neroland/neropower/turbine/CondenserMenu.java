package za.co.neroland.neropower.turbine;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;
import za.co.neroland.nerotech.menu.MachineMenu;

import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.menu.GaugeTailMenu;
import za.co.neroland.neropower.registry.content.TurbineContent;

import static za.co.neroland.neropower.turbine.CondenserBlockEntity.*;

/**
 * Steam Condenser menu (tall hull, 176 x 186): no machine slots, NeroTech's upgrade block and the player
 * inventory at y 104. Synced ints after NeroTech's seven, read through the named getters.
 */
public class CondenserMenu extends MachineMenu implements GaugeTailMenu {

    private static final int MACHINE_SLOTS = 0;
    private static final int DATA_SIZE = 7 + EXTRA_DATA;

    private final GaugeTail gaugeTail;

    public CondenserMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(MACHINE_SLOTS + NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public CondenserMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(TurbineContent.CONDENSER_MENU.get(), id, container, data, MACHINE_SLOTS);
        this.gaugeTail = GaugeTail.of(container);
        this.addDataSlots(this.gaugeTail);
        addUpgradeAndPlayerSlots(playerInventory, 138, 18, 2, 104);
    }

    @Override
    public GaugeTail gaugeTail() {
        return this.gaugeTail;
    }

    private long pair(int index) {
        return GaugeTail.join(extraValue(index), extraValue(index + 1));
    }

    public long exhaustMb() {
        return pair(EXTRA_EXHAUST_P);
    }

    public long exhaustCapacityMb() {
        return pair(EXTRA_EXHAUST_CAPACITY_P);
    }

    public long waterMb() {
        return pair(EXTRA_WATER_P);
    }

    public long waterCapacityMb() {
        return pair(EXTRA_WATER_CAPACITY_P);
    }

    public long cryoMb() {
        return pair(EXTRA_CRYO_P);
    }

    public long cryoCapacityMb() {
        return pair(EXTRA_CRYO_CAPACITY_P);
    }

    public int airFactorPermille() {
        return extraValue(EXTRA_AIR_FACTOR);
    }

    public int radiators() {
        return extraValue(EXTRA_RADIATORS);
    }

    public int radiatorMbPerTick() {
        return extraValue(EXTRA_RADIATOR_RATE);
    }

    public int cryoMbPerTick() {
        return extraValue(EXTRA_CRYO_USED);
    }

    public int steamPerCryoMb() {
        return extraValue(EXTRA_STEAM_PER_CRYO);
    }

    public long rateMbPerTick() {
        return pair(EXTRA_RATE_P);
    }

    public long possibleMbPerTick() {
        return pair(EXTRA_POSSIBLE_P);
    }

    public int readout() {
        return extraValue(EXTRA_READOUT);
    }

    public int stallCause() {
        return extraValue(EXTRA_STALL);
    }

    public int sink() {
        return extraValue(EXTRA_SINK);
    }
}
