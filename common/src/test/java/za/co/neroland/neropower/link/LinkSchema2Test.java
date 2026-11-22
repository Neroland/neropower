package za.co.neroland.neropower.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkActionResult;
import za.co.neroland.nerolandcore.link.LinkModuleInfo;
import za.co.neroland.nerolandcore.link.LinkSnapshotProvider;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

/**
 * Link schema 2 shape: no entry of any section carries a coordinate, a dimension, a UUID or a player
 * name; the {@code catalogue} is the same for every player; and a schema-1 client is still served
 * (same section ids, same envelope, non-location fields kept, positional actions answered with
 * {@code update_required}). Plain JVM: the section paths that need a running server are exercised
 * through {@link PlantScope}, the exact code the module runs.
 */
class LinkSchema2Test {

    private static final String DIM = "minecraft:overworld";

    @BeforeAll
    static void registerModule() {
        NeroPowerLinkModule.register();
    }

    private static LinkSnapshotProvider provider() {
        return NeroLinkRegistry.snapshotProvider("neropower").orElseThrow();
    }

    /** A schema-1 style entry: every location and identity field a careless writer could add. */
    private static JsonObject leakyEntry(UUID owner) {
        JsonObject e = new JsonObject();
        e.addProperty("id", "neropower:fission_core");
        e.addProperty("type", "neropower:fission_core");
        e.addProperty("status", "RUNNING");
        e.addProperty("failureStage", "STABLE");
        e.addProperty("x", 100);
        e.addProperty("y", 64);
        e.addProperty("z", -40);
        e.addProperty("dim", DIM);
        e.addProperty("distance", 12);
        e.addProperty("owner", owner.toString());
        e.addProperty("ownerName", "Alex");
        e.addProperty("note", "owned by " + owner);
        e.addProperty("_internal", 1);
        return e;
    }

    @Test
    void discoveryAdvertisesSchema2WithEverySchema1SectionKept() {
        LinkModuleInfo info = NeroLinkRegistry.module("neropower").orElseThrow();
        assertEquals(2, info.schemaVersion());
        assertTrue(info.dataSections().containsAll(List.of("beams", "fission", "storage", "generators")),
                "schema-1 section ids are kept");
        assertTrue(info.dataSections().containsAll(List.of("turbines", "catalogue")), "schema-2 additions");
        assertEquals(List.of("acknowledge_alarm", "scram", "turbine_trip"), info.actionIds());
    }

    @Test
    void noEntryCarriesLocationOrIdentity() {
        UUID me = UUID.randomUUID();
        PlantScope.Viewer viewer = new PlantScope.Viewer(me, DIM, 0, 64, 0);
        List<PlantScope.Candidate<String>> found = List.of(
                new PlantScope.Candidate<>("a", DIM, 10, 64, 10, Optional.of(me), leakyEntry(me)),
                new PlantScope.Candidate<>("b", DIM, -20, 70, 5, Optional.empty(), leakyEntry(UUID.randomUUID())));
        PlantScope.Selection<String> sel = PlantScope.select(viewer, found, new Random(1));
        assertEquals(2, sel.entries().size());
        JsonObject envelope = PlantScope.envelope("fission", 2, 0L);
        PlantScope.fill(envelope, sel.entries(), List.of("n-0", "n-1"), sel.truncated());
        assertFalse(PlantScope.leaks(envelope), envelope.toString());
        for (JsonElement el : envelope.getAsJsonArray("machines")) {
            JsonObject entry = el.getAsJsonObject();
            for (String key : List.of("x", "y", "z", "dim", "distance", "owner", "ownerName", "note", "_internal")) {
                assertFalse(entry.has(key), key + " must not leave the server");
            }
            assertFalse(entry.toString().contains("Alex"), "no player name");
            assertTrue(entry.has("h"), "every entry is addressed by handle");
            assertTrue(entry.has("ownedByYou") && entry.has("owned"));
        }
    }

    @Test
    void leakDetectorCatchesNestedPlacesAndUuids() {
        JsonObject nested = new JsonObject();
        JsonObject inner = new JsonObject();
        inner.addProperty("dim", DIM);
        nested.add("inner", inner);
        assertTrue(PlantScope.leaks(nested));
        JsonArray arr = new JsonArray();
        arr.add(UUID.randomUUID().toString());
        JsonObject withUuid = new JsonObject();
        withUuid.add("list", arr);
        assertTrue(PlantScope.leaks(withUuid));
        JsonObject clean = new JsonObject();
        clean.addProperty("type", "neropower:turbine_helm");
        assertFalse(PlantScope.leaks(clean));
    }

    @Test
    void catalogueIsTheSameForEveryPlayerAndCarriesNoWorldData() {
        JsonObject a = provider().snapshot(UUID.randomUUID(), "catalogue", Map.of());
        JsonObject b = provider().snapshot(UUID.randomUUID(), "catalogue", Map.of());
        assertEquals(a, b, "the public catalogue does not depend on who asks");
        assertEquals("public", a.get("scope").getAsString());
        assertFalse(PlantScope.leaks(a), a.toString());
        assertFalse(a.has("asOf"), "no clock: identical answers");
        assertEquals(41, a.getAsJsonArray("blocks").size(), "every NeroPower block");
        Set<String> lines = new java.util.HashSet<>();
        for (JsonElement l : a.getAsJsonArray("fuelLines")) {
            lines.add(l.getAsJsonObject().get("id").getAsString());
            assertTrue(l.getAsJsonObject().getAsJsonArray("steps").size() > 0);
        }
        assertTrue(lines.containsAll(Set.of("uranium", "pallurite", "braided", "waste")));
        assertNotNull(a.getAsJsonArray("storageTiers"));
    }

    @Test
    void catalogueBlockIdsAreUnique() {
        Set<String> ids = new java.util.HashSet<>();
        for (PlantCatalogue.Block b : PlantCatalogue.BLOCKS) {
            assertTrue(ids.add(b.id()), "duplicate " + b.id());
            assertTrue(b.id().startsWith("neropower:"));
        }
    }

    @Test
    void schema1ReaderStillGetsTheSameEnvelope() {
        for (String section : List.of("beams", "fission", "storage", "generators")) {
            JsonObject out = provider().snapshot(UUID.randomUUID(), section, Map.of());
            for (String key : List.of("section", "asOf", "scope", "radius", "machines")) {
                assertTrue(out.has(key), section + " keeps schema-1 key " + key);
            }
            assertEquals("proximity", out.get("scope").getAsString());
            assertEquals(2, out.get("schema").getAsInt());
        }
    }

    @Test
    void schema1FieldNamesSurviveStripping() {
        JsonObject v1 = new JsonObject();
        for (String key : List.of("id", "status", "owned", "failureStage", "formed", "shell", "poison",
                "controlRodPermille", "scram", "alarm", "beam", "linked", "lossPermille", "cells", "amount",
                "capacity", "mode", "kind", "fuelled", "outputPermille", "daysRemaining")) {
            v1.addProperty(key, 1);
        }
        JsonObject stripped = PlantScope.strip(v1);
        assertEquals(v1.keySet(), stripped.keySet(), "only location fields are removed in schema 2");
    }

    @Test
    void positionalSchema1ActionGetsUpdateRequiredWhateverStandsThere() {
        var handler = NeroLinkRegistry.actionHandler("neropower").orElseThrow();
        List<String> messages = new ArrayList<>();
        for (int[] at : new int[][] {{0, 64, 0}, {123456, 10, -98765}}) {
            JsonObject params = new JsonObject();
            params.addProperty("dim", DIM);
            params.addProperty("x", at[0]);
            params.addProperty("y", at[1]);
            params.addProperty("z", at[2]);
            LinkActionResult r = handler.execute(UUID.randomUUID(), "scram", params);
            assertFalse(r.ok());
            assertEquals(LinkActionResult.Error.VALIDATION, r.error());
            messages.add(r.message());
        }
        assertEquals(NeroPowerLinkModule.ERR_UPDATE_REQUIRED, messages.get(0));
        assertEquals(messages.get(0), messages.get(1), "the same answer for any position");
    }
}
