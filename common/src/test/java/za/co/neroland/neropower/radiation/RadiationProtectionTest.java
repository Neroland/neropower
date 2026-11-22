package za.co.neroland.neropower.radiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.failure.FailureAction;
import za.co.neroland.neropower.protection.ProtectionCheck;

/**
 * Radiation and protected ground, and the reactor's failure burst: a chunk whose centre column the
 * protection check reports protected is never written; dropped items add at most a fixed amount to one
 * chunk a pass; living things on protected ground are skipped by the effects pass; and every reactor
 * failure path, cryo-fed or not, runs the burst before the blast.
 */
class RadiationProtectionTest {

    /** Protects everything with block x in [0, 32) (chunks 0 and 1 on the x axis). */
    private static final class StripCheck implements ProtectionCheck {
        final List<BlockPos> asked = new ArrayList<>();

        @Override
        public boolean mayInteract(ServerPlayer player, ServerLevel level, BlockPos pos) {
            return true;
        }

        @Override
        public boolean isProtected(ServerLevel level, BlockPos pos) {
            this.asked.add(pos);
            return pos.getX() >= 0 && pos.getX() < 32;
        }
    }

    @Test
    void protectedChunksAreAskedAtTheirCentreAndRefused() {
        StripCheck check = new StripCheck();
        assertTrue(RadiationSources.chunkProtected(check, null, RadiationMath.chunkKey(0, 5), 63));
        assertEquals(new BlockPos(8, 63, 88), check.asked.get(0), "the chunk's centre column");
        assertTrue(RadiationSources.chunkProtected(check, null, RadiationMath.chunkKey(1, -3), 63));
        assertFalse(RadiationSources.chunkProtected(check, null, RadiationMath.chunkKey(2, 0), 63));
        assertFalse(RadiationSources.chunkProtected(check, null, RadiationMath.chunkKey(-1, 0), 63));
    }

    @Test
    void droppedItemsAreCappedPerChunkAndPass() {
        assertEquals(0, RadiationMath.droppedCap(0));
        assertEquals(12, RadiationMath.droppedCap(12));
        assertEquals(RadiationMath.DROPPED_CHUNK_CAP, RadiationMath.droppedCap(50 * 4), "50 thrown hot rods");
        assertTrue(RadiationMath.DROPPED_CHUNK_CAP < RadiationMath.DAMAGE_AT / 10,
                "a pile of rods needs many passes to reach the damage line");
    }

    @Test
    void everyWriteAndEffectAsksTheProtectionCheck() throws IOException {
        String sources = read("radiation/RadiationSources.java");
        String ticker = read("radiation/RadiationTicker.java");
        assertTrue(sources.contains("chunkProtected(Protection.get()"), "contaminate asks the check");
        assertTrue(ticker.contains("Protection.get().isProtected(level, entity.blockPosition())"),
                "effects skip protected ground");
        assertTrue(ticker.contains("RadiationMath.droppedCap("), "dropped items are capped");
        // every write goes through contaminate; ContaminationState is written nowhere else in these files
        assertEquals(1, count(sources, "ContaminationState.get(level).add("));
        assertFalse(ticker.contains(".add(chunk"), "the ticker never writes the store directly");
    }

    @Test
    void failureActionRunsTheBurstThenTheBlast() {
        List<String> ran = new ArrayList<>();
        FailureAction burst = (level, pos, ctx) -> ran.add("burst");
        FailureAction blast = (level, pos, ctx) -> ran.add("blast");
        burst.andThen(blast).apply(null, BlockPos.ZERO, null);
        assertEquals(List.of("burst", "blast"), ran);
    }

    @Test
    void everyReactorFailurePathCarriesTheBurst() throws IOException {
        String core = read("fission/FissionCoreBlockEntity.java");
        assertEquals(1, count(core, "RadiationSources.reactorFailure("), "one call, inside the failure action");
        assertTrue(core.contains("return withBurst(new ScorchZoneAction("), "the failure action carries the burst");
        // both the shared ladder (non-cryo) and the cryo ladder resolve through failureAction()
        assertTrue(core.contains("return super.tickFailure(level, pos);"));
        assertTrue(core.contains("failureAction().apply(serverLevel, pos, failureContext());"));
    }

    private static int count(String s, String needle) {
        int n = 0;
        for (int i = s.indexOf(needle); i >= 0; i = s.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    private static String read(String rel) throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("stonecutter.gradle"))) {
            dir = dir.getParent();
        }
        assertTrue(dir != null, "repository root not found");
        Path file = dir.resolve("common/src/main/java/za/co/neroland/neropower").resolve(rel);
        return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
