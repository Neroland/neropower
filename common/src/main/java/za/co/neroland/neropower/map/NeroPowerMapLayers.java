package za.co.neroland.neropower.map;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import za.co.neroland.nerolandcore.map.MapArea;
import za.co.neroland.nerolandcore.map.MapAreaStyle;
import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapLabel;
import za.co.neroland.nerolandcore.map.MapLayerInfo;
import za.co.neroland.nerolandcore.map.MapLayerProvider;
import za.co.neroland.nerolandcore.map.MapLayerRegistry;
import za.co.neroland.nerolandcore.map.MapLayerSink;
import za.co.neroland.nerolandcore.map.MapMarker;
import za.co.neroland.nerolandcore.map.MapScope;
import za.co.neroland.nerolandcore.map.MapViewer;
import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.fission.ScorchZones;
import za.co.neroland.neropower.link.LinkScope;

/**
 * NeroPower's layers for Neroland Core's map API (NeroMaps and the companion app draw them; NeroPower draws
 * nothing). One scope per layer:
 * <ul>
 *   <li>{@code power} (OWNER): reactors and beam endpoints whose recorded owner is the viewer, among the
 *       machines already loaded within 128 blocks of the online viewer (NeroPower keeps no machine index).
 *       Another player's owned machine is never shown, near or far.</li>
 *   <li>There is no layer for other players' or unowned machines (removed in 0.4.0):
 *       a marker carries an exact position, so a machine appears on the map only for its recorded owner.</li>
 *   <li>{@code scorch_zones} (PROXIMITY, 128): live fission scorch zones (a hazard with no owner) as areas,
 *       within 128 blocks of the online viewer.</li>
 * </ul>
 * The ownership rule is {@link LinkScope}'s ({@link LinkScope#ownerOf}), so the map and the link module cannot
 * drift; a parity test holds them together. POPIA/GDPR: owners are compared, never shown; no label carries a player's name or UUID.
 */
public final class NeroPowerMapLayers implements MapLayerProvider {

    public static final Identifier POWER = id("power");
    public static final Identifier SCORCH_ZONES = id("scorch_zones");

    /** How far the owned-machine walk reaches: the link module's proximity radius. */
    static final int OWNED_REACH = MapLayerInfo.MAX_PROXIMITY;
    static final int HAZARD = MapLayerInfo.HAZARD_PROXIMITY;

    private static final Identifier REACTOR_ICON = id("reactor");
    private static final Identifier BEAM_ICON = id("beam_link");
    private static final int SCORCH_FILL = 0x40D55E00;
    private static final int SCORCH_BORDER = 0xCCD55E00;

    private static final List<MapLayerInfo> LAYERS = List.of(
            MapLayerInfo.of(POWER, "map.layer.neropower.power", BEAM_ICON, MapScope.OWNER)
                    .withDefaults(true, true),
            MapLayerInfo.proximityHazard(SCORCH_ZONES, "map.layer.neropower.scorch_zones", id("scorch_zone")));

    private final NeroPowerMapSource source;

    public NeroPowerMapLayers() {
        this(new ServerMapSource());
    }

    /** For tests: the same rules over a seeded source. */
    public NeroPowerMapLayers(NeroPowerMapSource source) {
        this.source = source;
    }

    /** Registers the provider with Core. A failure is logged and never takes the mod down. */
    public static void register() {
        try {
            MapLayerRegistry.register(new NeroPowerMapLayers());
        } catch (RuntimeException e) {
            NeroPowerCommon.LOGGER.warn("[NeroPower] map layers not registered: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public String modId() {
        return NeroPowerCommon.MOD_ID;
    }

    @Override
    public List<MapLayerInfo> layers() {
        return LAYERS;
    }

    @Override
    public void query(MapViewer viewer, ResourceKey<Level> dimension, MapBounds bounds, MapLayerSink sink) {
        BlockPos at = viewer.position().orElse(null);
        if (at == null || !viewer.dimension().map(dimension::equals).orElse(false)) {
            return; // every NeroPower layer needs the viewer online in this dimension
        }
        MinecraftServer server = viewer.server();
        for (NeroPowerMapSource.Machine machine : this.source.machines(server, dimension, at, OWNED_REACH)) {
            int x = machine.pos().getX();
            int z = machine.pos().getZ();
            // Owned by the viewer only. Unowned and other players' machines are never drawn (a marker is an
            // exact position).
            if (ownedBy(machine.owner(), viewer.id())
                    && !sink.isFull(POWER) && viewer.within(dimension, x, z, OWNED_REACH)) {
                sink.marker(marker(machine, POWER, dimension, true));
            }
        }
        scorch(server, viewer, dimension, sink);
    }

    private void scorch(@Nullable MinecraftServer server, MapViewer viewer, ResourceKey<Level> dimension,
            MapLayerSink sink) {
        String dimId = dimension.identifier().toString();
        long today = this.source.today();
        for (ScorchZones.Zone zone : this.source.scorchZones(server)) {
            if (sink.isFull(SCORCH_ZONES)) {
                return;
            }
            if (zone.expired(today) || !dimId.equals(zone.dimension())) {
                continue;
            }
            BlockPos c = zone.center();
            int r = Math.max(1, Math.min(MapBounds.MAX_SPAN / 2 - 1, zone.radius()));
            MapArea area = new MapArea.Rect(
                    "scorch." + c.getX() + "." + c.getZ(), SCORCH_ZONES, dimension,
                    MapAreaStyle.of(SCORCH_FILL, SCORCH_BORDER)
                            .withLabel(MapLabel.translatable("map.area.neropower.scorch_zone")),
                    c.getX() - r, c.getZ() - r, c.getX() + r, c.getZ() + r);
            if (viewer.within(dimension, area.bounds(), HAZARD)) {
                sink.area(area);
            }
        }
    }

    private static MapMarker marker(NeroPowerMapSource.Machine machine, Identifier layer,
            ResourceKey<Level> dimension, boolean own) {
        boolean reactor = machine.kind() == NeroPowerMapSource.Kind.REACTOR;
        BlockPos p = machine.pos();
        MapMarker.Builder b = MapMarker.builder(
                (reactor ? "reactor." : "beam.") + p.getX() + "." + p.getY() + "." + p.getZ(), layer, dimension, p)
                .icon(reactor ? REACTOR_ICON : BEAM_ICON)
                .label(MapLabel.translatable(reactor ? "map.marker.neropower.reactor" : "map.marker.neropower.beam"));
        if (own) {
            b.detail(MapLabel.translatable("map.detail.neropower.yours"));
        }
        return b.build();
    }

    /** The viewer is the recorded owner ({@link LinkScope#isOwner}). */
    public static boolean ownedBy(Optional<UUID> owner, @Nullable UUID viewer) {
        return viewer != null && LinkScope.isOwner(owner, viewer);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, path);
    }
}
