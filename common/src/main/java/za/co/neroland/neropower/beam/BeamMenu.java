package za.co.neroland.neropower.beam;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;
import za.co.neroland.nerotech.menu.MachineMenu;

import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.menu.GaugeTailMenu;

/**
 * The one menu every beam endpoint shares: no machine slots — upgrade column + player inventory —
 * plus the five synced beam ints after the seven shared ones (see
 * {@link BeamEndpointBlockEntity}), read by {@code beam.client.BeamScreen}.
 */
public class BeamMenu extends MachineMenu implements GaugeTailMenu {

    /** Gauge tooltip tail (energy, heat, NE moved in the last pass), after NeroTech's shared data. */
    private final GaugeTail gaugeTail;

    private static final int MACHINE_SLOTS = 0;

    /** Seven shared ContainerData indices + the endpoint's five beam ints. */
    private static final int DATA_SIZE = 7 + BeamEndpointBlockEntity.EXTRA_DATA;

    public BeamMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(MACHINE_SLOTS + NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public BeamMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(BeamContent.BEAM_MENU.get(), id, container, data, MACHINE_SLOTS);
        this.gaugeTail = GaugeTail.of(container,
                be -> be instanceof BeamEndpointBlockEntity beam ? beam.lastPass : 0L);
        this.addDataSlots(this.gaugeTail);
        addUpgradeAndPlayerSlots(playerInventory);
    }

    /** Whether this endpoint holds a link of its own (transmitter / relay). */
    public boolean linked() {
        return extraValue(0) > 0;
    }

    /** Straight-line distance to the target in blocks (0 for receivers and orbital hops). */
    public int distance() {
        return extraValue(1);
    }

    /** Whole-beam loss, permille. */
    public int lossPermille() {
        return extraValue(2);
    }

    /** NE moved in the last pass. */
    public int lastPass() {
        return extraValue(3);
    }

    /** The endpoint's current {@link BeamStatus}. */
    public BeamStatus status() {
        return BeamStatus.byOrdinal(extraValue(4));
    }

    @Override
    public GaugeTail gaugeTail() {
        return this.gaugeTail;
    }
}
