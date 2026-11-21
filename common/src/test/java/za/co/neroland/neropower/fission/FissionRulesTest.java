package za.co.neroland.neropower.fission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.multiblock.Bounds;
import za.co.neroland.neropower.multiblock.Cell;
import za.co.neroland.neropower.multiblock.Probe;

/**
 * The one distinctive rule of each NeroPower 0.4.0 fission block, on the plain JVM: a moderator raises
 * reactivity per adjacent fuel channel; a breeder blanket breeds while the core runs and costs reactivity;
 * a steam port switches the core to steam mode; the control computer's auto-SCRAM threshold.
 */
class FissionRulesTest {

    /** A sparse test world: everything air unless set. */
    private static final class Grid implements Probe {
        private final Map<String, Cell> cells = new HashMap<>();

        Grid set(int x, int y, int z, Cell cell) {
            this.cells.put(x + "," + y + "," + z, cell);
            return this;
        }

        Grid shell(Bounds b) {
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int y = b.minY(); y <= b.maxY(); y++) {
                    for (int z = b.minZ(); z <= b.maxZ(); z++) {
                        if (b.faces(x, y, z) > 0) {
                            set(x, y, z, Cell.FISSION_CASING);
                        }
                    }
                }
            }
            return this;
        }

        @Override
        public Cell at(int x, int y, int z) {
            return this.cells.getOrDefault(x + "," + y + "," + z, Cell.AIR);
        }
    }

    private static final Bounds SHELL_5 = new Bounds(0, 0, 0, 4, 4, 4);
    private static final Bounds SHELL_7 = new Bounds(0, 0, 0, 6, 6, 6);

    // --- moderators ------------------------------------------------------------------------------

    @Test
    void moderatorRaisesReactivityPerAdjacentChannel() {
        // Centre of a 5-shell's 3x3x3 interior: six interior air neighbours.
        ReactorLayout centre = ReactorLayout.scan(new Grid().shell(SHELL_5).set(2, 2, 2, Cell.GRAPHITE_MODERATOR), SHELL_5);
        assertEquals(6, centre.graphiteContacts());
        // A corner of the interior touches the shell on three faces: three channels.
        ReactorLayout corner = ReactorLayout.scan(new Grid().shell(SHELL_5).set(1, 1, 1, Cell.GRAPHITE_MODERATOR), SHELL_5);
        assertEquals(3, corner.graphiteContacts());
        // Packing a block against it removes a channel.
        ReactorLayout packed = ReactorLayout.scan(new Grid().shell(SHELL_5).set(2, 2, 2, Cell.GRAPHITE_MODERATOR)
                .set(2, 3, 2, Cell.CONTROL_ROD), SHELL_5);
        assertEquals(5, packed.graphiteContacts());

        int perContact = 10;
        int cap = 400;
        assertEquals(60, FissionMath.moderatorBonusPermille(centre.moderatorScore(), perContact, cap));
        assertEquals(30, FissionMath.moderatorBonusPermille(corner.moderatorScore(), perContact, cap));
        assertEquals(50, FissionMath.moderatorBonusPermille(packed.moderatorScore(), perContact, cap));
        // A Moderator Tank weighs twice graphite.
        ReactorLayout tank = ReactorLayout.scan(new Grid().shell(SHELL_5).set(2, 2, 2, Cell.MODERATOR_TANK), SHELL_5);
        assertEquals(12, tank.moderatorScore());
        assertEquals(12, tank.heavyWaterScore());
        assertEquals(120, FissionMath.moderatorBonusPermille(tank.moderatorScore(), perContact, cap));
        // The cap holds.
        assertEquals(cap, FissionMath.moderatorBonusPermille(1_000, perContact, cap));
        // Moderators against each other are not channels.
        ReactorLayout pair = ReactorLayout.scan(new Grid().shell(SHELL_5).set(2, 2, 2, Cell.GRAPHITE_MODERATOR)
                .set(2, 2, 3, Cell.GRAPHITE_MODERATOR), SHELL_5);
        assertEquals(5 + 4, pair.graphiteContacts());
    }

    @Test
    void portsAndComputerAreFoundOnWallFaces() {
        ReactorLayout layout = ReactorLayout.scan(new Grid().shell(SHELL_7)
                .set(3, 0, 3, Cell.REACTOR_STEAM_PORT)
                .set(0, 3, 3, Cell.REACTOR_STEAM_PORT)
                .set(3, 3, 6, Cell.REACTOR_CONTROL_COMPUTER), SHELL_7);
        assertEquals(2, layout.steamPorts().size());
        assertEquals(6, layout.computer()[2]);
        assertNull(ReactorLayout.scan(new Grid().shell(SHELL_7), SHELL_7).computer());
    }

    // --- breeder ---------------------------------------------------------------------------------

    @Test
    void breederConvertsWhileRunningAndCostsReactivity() {
        int ticksPerPellet = 1_200;
        Breeder breeder = new Breeder();
        int step = FissionMath.breedStep(2, 8, 1_000); // two blankets at 100% output
        int ticks = 0;
        boolean bred = false;
        while (!bred && ticks < 10_000) {
            bred = breeder.tick(true, true, step, ticksPerPellet);
            ticks++;
        }
        assertTrue(bred);
        assertEquals(600, ticks, "two blankets breed twice as fast as one");
        // Idle core, or nothing to breed: no progress at all.
        Breeder idle = new Breeder();
        for (int i = 0; i < 5_000; i++) {
            assertFalse(idle.tick(false, true, step, ticksPerPellet));
            assertFalse(idle.tick(true, false, step, ticksPerPellet));
        }
        assertEquals(0, idle.progress());
        // Only breederMaxBlankets count for the rate.
        assertEquals(FissionMath.breedStep(8, 8, 1_000), FissionMath.breedStep(20, 8, 1_000));
        // Every blanket costs reactivity, sharing the 15% floor with the control rods.
        double none = FissionMath.reactivityFactor(0, 0, 0, 27, 150, 250, 50);
        double two = FissionMath.reactivityFactor(0, 0, 2, 27, 150, 250, 50);
        assertEquals(1.0D, none, 1e-9);
        assertEquals(0.9D, two, 1e-9);
        assertEquals(FissionMath.CONTROL_ROD_FLOOR, FissionMath.reactivityFactor(5, 0, 20, 27, 150, 250, 50), 1e-9);
        // With nothing new inside, the 0.3.x control-rod factor is unchanged.
        for (int rods = 0; rods <= 8; rods++) {
            assertEquals(FissionMath.controlRodFactor(rods, 27, 150),
                    FissionMath.reactivityFactor(rods, 0, 0, 27, 150, 250, 50), 1e-12);
        }
        // Europium rods are the stronger tier.
        assertEquals(0.75D, FissionMath.reactivityFactor(0, 1, 0, 27, 150, 250, 50), 1e-9);
    }

    // --- steam port ------------------------------------------------------------------------------

    @Test
    void steamPortSwitchesMode() {
        assertEquals(ReactorMode.DIRECT, ReactorMode.effective(ReactorMode.DIRECT, 2));
        assertEquals(ReactorMode.STEAM, ReactorMode.effective(ReactorMode.STEAM, 1));
        assertEquals(ReactorMode.DIRECT, ReactorMode.effective(ReactorMode.STEAM, 0), "no port: back to direct");
        assertFalse(ReactorMode.canRequest(ReactorMode.STEAM, 0));
        assertTrue(ReactorMode.canRequest(ReactorMode.STEAM, 1));
        assertTrue(ReactorMode.canRequest(ReactorMode.DIRECT, 0));
        assertEquals(ReactorMode.DIRECT, ReactorMode.byId(null));
        assertEquals(ReactorMode.STEAM, ReactorMode.byId(ReactorMode.STEAM.id()));
        // Steam mode raises fissionSteamPerHeat mB per heat, scaled by the output multipliers.
        assertEquals(24L, FissionMath.steamFromHeat(6, 4, 1_000));
        assertEquals(0L, FissionMath.steamFromHeat(6, 4, 0), "FAILURE output: nothing");
    }

    // --- control computer ------------------------------------------------------------------------

    @Test
    void controlComputerAutoScramThreshold() {
        assertFalse(ControlComputerLogic.shouldScram(false, 1_000, 900), "disabled: never");
        assertFalse(ControlComputerLogic.shouldScram(true, 899, 900));
        assertTrue(ControlComputerLogic.shouldScram(true, 900, 900));
        // The level is clamped to 50..100%.
        assertTrue(ControlComputerLogic.shouldScram(true, 500, 100));
        assertFalse(ControlComputerLogic.shouldScram(true, 499, 100));
        // Signals and outputs.
        assertTrue(ControlComputerLogic.on(ControlComputerLogic.FUEL, 0, 100, 0, 0, 100), "fuel is on at or below");
        assertFalse(ControlComputerLogic.on(ControlComputerLogic.FUEL, 0, 101, 0, 0, 100));
        assertTrue(ControlComputerLogic.on(ControlComputerLogic.STAGE, 0, 0, 0, 2, 2));
        assertFalse(ControlComputerLogic.on(ControlComputerLogic.OFF, 1_000, 0, 1_000, 3, 0));
        assertEquals(15, ControlComputerLogic.comparator(ControlComputerLogic.HEAT, 1_000, 0, 0, 0));
        assertEquals(1, ControlComputerLogic.comparator(ControlComputerLogic.HEAT, 1, 0, 0, 0));
        assertEquals(10, ControlComputerLogic.comparator(ControlComputerLogic.STAGE, 0, 0, 0, 2));
        assertEquals(0, ControlComputerLogic.comparator(ControlComputerLogic.OUTPUT, 0, 0, 0, 0));
    }
}
