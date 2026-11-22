package za.co.neroland.neropower.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

/**
 * Privacy: the client sync tag of a transmitter / relay never carries an absolute target
 * beyond the render cut or in another dimension. {@code clientTarget} is what the update
 * tag writes; server NBT is unchanged.
 */
class BeamClientTargetTest {

    private static final String OW = "minecraft:overworld";
    private static final BlockPos FROM = new BlockPos(1_000, 70, -2_000);

    @Test
    void aNearTargetInThisDimensionIsShownAsIs() {
        BeamTarget near = BeamTarget.of(FROM.offset(40, -10, 30), OW);
        assertEquals(near, BeamClientView.clientTarget(FROM, near, OW, 128));
    }

    @Test
    void aFarTargetBecomesADirectionOnly() {
        BeamTarget far = BeamTarget.of(FROM.offset(300, 0, 0), OW);
        BeamTarget shown = BeamClientView.clientTarget(FROM, far, OW, 128);
        assertNotEquals(far.pos(), shown.pos(), "the real receiver is not on the client");
        assertEquals(FROM.offset(128, 0, 0), shown.pos(), "a point along the same direction at the fixed length");
        BeamTarget farther = BeamTarget.of(FROM.offset(450, 0, 0), OW);
        assertEquals(shown, BeamClientView.clientTarget(FROM, farther, OW, 128),
                "two far targets on one bearing look the same: no distance");
    }

    @Test
    void anotherDimensionShowsNoPositionAtAll() {
        BeamTarget orbital = BeamTarget.of(new BlockPos(5, 100, 5), "nerospace:luna");
        BeamTarget shown = BeamClientView.clientTarget(FROM, orbital, OW, 128);
        assertEquals(FROM, shown.pos());
        assertTrue(shown.dimension().isEmpty(), "no dimension id reaches the client");
    }

    @Test
    void unlinkedStaysUnlinked() {
        assertNull(BeamClientView.clientTarget(FROM, null, OW, 128));
    }
}
