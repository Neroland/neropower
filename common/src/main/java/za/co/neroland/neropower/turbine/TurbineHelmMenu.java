package za.co.neroland.neropower.turbine;

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
import za.co.neroland.neropower.registry.content.TurbineContent;

import static za.co.neroland.neropower.turbine.TurbineHelmBlockEntity.*;

/**
 * Turbine Helm menu (wide tall hull, 248 x 222): no machine slots; upgrade slots at {@code 210,18}
 * (one may hold the Governor Module) and the player inventory at x 43. Synced ints after NeroTech's
 * seven, read through the named getters below (never by absolute slot); the {@link GaugeTail} extra
 * value is the NE delivered last tick. The Reset control is vanilla's container button
 * ({@code action * 1000 + value}); the server re-checks the trip and the speed.
 */
public class TurbineHelmMenu extends MachineMenu implements GaugeTailMenu {

    /** Button action: reset a latched trip (value 0). */
    public static final int ACTION_RESET = 1;

    private static final int MACHINE_SLOTS = 0;
    private static final int DATA_SIZE = 7 + EXTRA_DATA;

    /** Wide-hull layout. */
    public static final int UPGRADE_X = 210;
    public static final int UPGRADE_Y = 18;
    public static final int PLAYER_X = 43;
    public static final int PLAYER_Y = 140;

    private final GaugeTail gaugeTail;

    public TurbineHelmMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(MACHINE_SLOTS + NeroTechMachineBlockEntity.UPGRADE_SLOTS),
                new SimpleContainerData(DATA_SIZE));
    }

    public TurbineHelmMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(TurbineContent.TURBINE_HELM_MENU.get(), id, container, data, MACHINE_SLOTS);
        this.gaugeTail = GaugeTail.of(container,
                be -> be instanceof TurbineHelmBlockEntity helm ? helm.lastOutput() : 0L);
        this.addDataSlots(this.gaugeTail);
        addWideSlots(playerInventory);
    }

    /** Upgrades then the 27 inventory and 9 hotbar slots, in NeroTech's index order, on the wide hull. */
    private void addWideSlots(Inventory playerInventory) {
        captureStatsTarget(playerInventory);
        int upgrades = this.totalNonPlayer - this.machineSlots;
        for (int i = 0; i < upgrades; i++) {
            this.addSlot(new PredicateSlot(this.container, this.machineSlots + i, UPGRADE_X + (i % 2) * 18,
                    UPGRADE_Y + (i / 2) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, PLAYER_X + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, PLAYER_X + col * 18, PLAYER_Y + 58));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        int action = MenuButtons.action(id);
        if (action != ACTION_RESET || !stillValid(player)
                || !(this.container instanceof TurbineHelmBlockEntity helm)) {
            return false;
        }
        helm.setLastOperator(player.getUUID());
        helm.requestReset();
        return true;
    }

    @Override
    public GaugeTail gaugeTail() {
        return this.gaugeTail;
    }

    private long pair(int index) {
        return GaugeTail.join(extraValue(index), extraValue(index + 1));
    }

    public int rpm() {
        return extraValue(EXTRA_RPM);
    }

    public int tripRpm() {
        return extraValue(EXTRA_TRIP_RPM);
    }

    public int warnRpm() {
        return extraValue(EXTRA_WARN_RPM);
    }

    public int unstableRpm() {
        return extraValue(EXTRA_UNSTABLE_RPM);
    }

    public boolean tripped() {
        return extraValue(EXTRA_TRIPPED) != 0;
    }

    public int failureStageCode() {
        return extraValue(EXTRA_FAILURE_STAGE);
    }

    /** 0 trip, 1 shed vanes, 2 blast. */
    public int failureAction() {
        return extraValue(EXTRA_FAILURE_ACTION);
    }

    public long steamMb() {
        return pair(EXTRA_STEAM_P);
    }

    public long steamCapacityMb() {
        return pair(EXTRA_STEAM_CAPACITY_P);
    }

    public long exhaustMb() {
        return pair(EXTRA_EXHAUST_P);
    }

    public long exhaustCapacityMb() {
        return pair(EXTRA_EXHAUST_CAPACITY_P);
    }

    public long steamUsedMbPerTick() {
        return pair(EXTRA_STEAM_USED_P);
    }

    public long flowCapMbPerTick() {
        return pair(EXTRA_FLOW_CAP_P);
    }

    public int efficiencyPermille() {
        return extraValue(EXTRA_EFFICIENCY);
    }

    public int exhaustFactorPermille() {
        return extraValue(EXTRA_EXHAUST_FACTOR);
    }

    public int ventEfficiencyPermille() {
        return extraValue(EXTRA_VENT_EFFICIENCY);
    }

    public long outputNePerTick() {
        return pair(EXTRA_OUTPUT_P);
    }

    public long coilCeilingNePerTick() {
        return pair(EXTRA_CEILING_P);
    }

    public int woundStators() {
        return extraValue(EXTRA_WOUND_STATORS);
    }

    public boolean governor() {
        return extraValue(EXTRA_GOVERNOR) != 0;
    }

    public int portsInlet() {
        return extraValue(EXTRA_PORTS_INLET);
    }

    public int portsExhaust() {
        return extraValue(EXTRA_PORTS_EXHAUST);
    }

    public int portsVent() {
        return extraValue(EXTRA_PORTS_VENT);
    }

    public int sliceCount() {
        return Math.max(0, Math.min(MAX_SLICES, extraValue(EXTRA_SLICE_COUNT)));
    }

    /** Slice code {@code i}: 0 incomplete, 1-3 vane tier, 4-6 stator tier, 7 bare. */
    public int sliceCode(int i) {
        if (i < 0 || i >= MAX_SLICES) {
            return SLICE_INVALID;
        }
        return (extraValue(EXTRA_SLICE_CODES + i / 5) >> (3 * (i % 5))) & 7;
    }

    /** Vane flow per slice for tier 1-3 (steel, cindersteel, superconducting). */
    public int tierFlow(int tier) {
        return extraValue(EXTRA_TIER_FLOW + Math.max(0, Math.min(2, tier - 1)));
    }

    public int tierEfficiency(int tier) {
        return extraValue(EXTRA_TIER_EFF + Math.max(0, Math.min(2, tier - 1)));
    }

    /** Ceiling per wound stator for winding tier 1-3. */
    public int tierCeiling(int tier) {
        return extraValue(EXTRA_TIER_CEILING + Math.max(0, Math.min(2, tier - 1)));
    }

    public boolean formed() {
        return extraValue(EXTRA_FORMED) != 0;
    }

    public int readout() {
        return extraValue(EXTRA_READOUT);
    }

    public int stallCause() {
        return extraValue(EXTRA_STALL);
    }
}
