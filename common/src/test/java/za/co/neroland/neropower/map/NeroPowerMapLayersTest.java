package za.co.neroland.neropower.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapLayerConformance;
import za.co.neroland.nerolandcore.map.MapViewer;
import za.co.neroland.neropower.fission.ScorchZones;
import za.co.neroland.neropower.link.LinkScope;

/** Core's map-layer conformance fixture over NeroPower's layers, plus the map-vs-link parity test. */
class NeroPowerMapLayersTest {

    static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("minecraft", "overworld"));
    static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("minecraft", "the_nether"));

    static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID STRANGER = UUID.fromString("00000000-0000-4000-8000-0000000000c3");
    static final UUID THIRD = UUID.fromString("00000000-0000-4000-8000-0000000000d4");
    static final long TODAY = 20_000L;

    /** Seeded machines and zones in the overworld; the source hands everything over unfiltered. */
    static final class Seeded implements NeroPowerMapSource {
        final List<Machine> machines = new ArrayList<>();
        final List<ScorchZones.Zone> zones = new ArrayList<>();

        @Override
        public Collection<Machine> machines(@Nullable MinecraftServer server, ResourceKey<Level> dimension,
                BlockPos around, int radius) {
            return OVERWORLD.equals(dimension) ? machines : List.of();
        }

        @Override
        public Collection<ScorchZones.Zone> scorchZones(@Nullable MinecraftServer server) {
            return zones;
        }

        @Override
        public long today() {
            return TODAY;
        }
    }

    static Seeded seeded() {
        Seeded s = new Seeded();
        s.machines.add(new NeroPowerMapSource.Machine(NeroPowerMapSource.Kind.REACTOR, new BlockPos(10, 64, 10),
                Optional.of(OWNER)));
        s.machines.add(new NeroPowerMapSource.Machine(NeroPowerMapSource.Kind.BEAM, new BlockPos(100, 64, 0),
                Optional.of(OWNER)));
        s.machines.add(new NeroPowerMapSource.Machine(NeroPowerMapSource.Kind.BEAM, new BlockPos(12, 64, -5),
                Optional.of(THIRD)));
        s.machines.add(new NeroPowerMapSource.Machine(NeroPowerMapSource.Kind.REACTOR, new BlockPos(-20, 64, 4),
                Optional.empty()));
        s.machines.add(new NeroPowerMapSource.Machine(NeroPowerMapSource.Kind.BEAM, new BlockPos(90, 64, 0),
                Optional.empty()));
        s.zones.add(new ScorchZones.Zone("minecraft:overworld", new BlockPos(120, 60, 0), 12, TODAY + 2));
        s.zones.add(new ScorchZones.Zone("minecraft:overworld", new BlockPos(-30, 60, 0), 12, TODAY - 1));
        s.zones.add(new ScorchZones.Zone("minecraft:the_nether", new BlockPos(0, 60, 0), 12, TODAY + 2));
        s.zones.add(new ScorchZones.Zone("minecraft:overworld", new BlockPos(900, 60, 0), 12, TODAY + 2));
        return s;
    }

    static MapViewer at(UUID who, int x, int z) {
        return MapViewer.forTest(who, null, OVERWORLD, new BlockPos(x, 64, z));
    }

    @Test
    void ownerConforms() {
        MapLayerConformance.create(new NeroPowerMapLayers(seeded()))
                .viewers(at(OWNER, 0, 0), at(STRANGER, 4, 0))
                .dimensions(OVERWORLD, NETHER)
                .bounds(MapBounds.around(0, 0, 512))
                .farBounds(MapBounds.around(20_000, 20_000, 256))
                .forbiddenIds(THIRD)
                .expectEntitledSees(NeroPowerMapLayers.POWER, 2)
                .expectEntitledSees(NeroPowerMapLayers.POWER_NEARBY, 1)
                .expectEntitledSees(NeroPowerMapLayers.SCORCH_ZONES, 1)
                .verify();
    }

    @Test
    void anotherPlayersMachineNeverShowsAndUnownedStopsAtSixtyFour() {
        NeroPowerMapLayers layers = new NeroPowerMapLayers(seeded());
        za.co.neroland.nerolandcore.map.MapLayerRegistry.register(layers);
        var r = za.co.neroland.nerolandcore.map.MapLayerRegistry.query("neropower", at(STRANGER, 0, 0), OVERWORLD,
                MapBounds.around(0, 0, 4_096));
        List<String> ids = new ArrayList<>();
        r.markers().forEach(m -> ids.add(m.layer().getPath() + "/" + m.id()));
        assertEquals(List.of("power_nearby/reactor.-20.64.4"), ids,
                "a stranger sees only the unowned reactor in range: not the owner's, not the third player's");
        assertEquals(1, r.areas().size(), "live scorch zone in range only; expired, far and other-dimension skipped");
    }

    /** Map visibility (own on {@code power} + unowned on {@code power_nearby}) equals LinkScope's. */
    @Test
    void mapAndLinkAgreeOnMachineVisibility() {
        List<Optional<UUID>> owners = List.of(Optional.of(OWNER), Optional.of(THIRD), Optional.empty());
        for (UUID viewer : new UUID[] { OWNER, STRANGER, THIRD }) {
            for (Optional<UUID> owner : owners) {
                boolean map = NeroPowerMapLayers.ownedBy(owner, viewer) || NeroPowerMapLayers.unowned(owner);
                assertEquals(LinkScope.visibleTo(owner, viewer), map);
            }
        }
        // An erased owner reads as no owner: proximity-only for everyone, owned by nobody.
        Optional<UUID> erased = Optional.empty();
        assertEquals(false, NeroPowerMapLayers.ownedBy(erased, OWNER));
        assertEquals(LinkScope.isOwner(erased, OWNER), NeroPowerMapLayers.ownedBy(erased, OWNER));
    }
}
