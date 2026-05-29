package com.cedarsoftware.io;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Reproduction + regression tests for re-serializing a resolved Maps graph (a {@link JsonObject}
 * graph from {@code toMaps}) with type info enabled.
 * <p>
 * Suspected latent bug: the writer emits json-io's INTERNAL container classes
 * ({@code com.cedarsoftware.io.JsonObject}, {@code JsonObjectMap}) as {@code @type} when a
 * JsonObject carries no meaningful original {@code @type}. Those internal classes are noise at
 * best, and {@code @type:JsonObjectMap} cannot even be read back. A re-serialized untyped object
 * should look like the plain object/map it represents.
 */
class JsonObjectReserializeTest {

    private static final String INTERNAL = "com.cedarsoftware.io.JsonObject";

    @Test
    void plainObjectGraph_minimal_doesNotLeakInternalType() {
        Object graph = JsonIo.toMaps("{\"a\":1,\"b\":\"x\"}").asClass(null);
        String json = JsonIo.toJson(graph, new WriteOptionsBuilder().showTypeInfoMinimal().build());
        assertFalse(json.contains(INTERNAL), "internal class leaked as @type: " + json);
    }

    @Test
    void plainObjectGraph_minimalPlus_doesNotLeakInternalType() {
        Object graph = JsonIo.toMaps("{\"a\":1,\"b\":\"x\"}").asClass(null);
        String json = JsonIo.toJson(graph, new WriteOptionsBuilder().showTypeInfoMinimalPlus().build());
        assertFalse(json.contains(INTERNAL), "internal class leaked as @type: " + json);
    }

    @Test
    void plainObjectGraph_minimal_roundTrips() {
        Object graph = JsonIo.toMaps("{\"a\":1,\"b\":\"x\"}").asClass(null);
        String json = JsonIo.toJson(graph, new WriteOptionsBuilder().showTypeInfoMinimal().build());
        // Re-reading must reproduce the same {a=1, b=x} structure.
        @SuppressWarnings("unchecked")
        Map<String, Object> back = (Map<String, Object>) JsonIo.toMaps(json).asClass(null);
        assertEquals(1L, ((Number) back.get("a")).longValue());
        assertEquals("x", back.get("b"));
    }

    @Test
    void toon_plainObjectGraph_doesNotLeakInternalType() {
        // ToonWriter equivalent of the JsonWriter leak: re-serializing a resolved Maps graph to
        // TOON must not emit json-io's internal carrier class as $type/@type.
        Object graph = JsonIo.toMaps("{\"a\":1,\"b\":\"x\"}").asClass(null);
        String toon = JsonIo.toToon(graph, new WriteOptionsBuilder().showTypeInfoMinimalPlus().cycleSupport(true).build());
        assertFalse(toon.contains("com.cedarsoftware.io.JsonObject"), "internal class leaked into TOON: " + toon);
    }

    @Test
    void toon_nonStringKeyMapGraph_noLeakNoCrash() {
        // Previously leaked "$type: com.cedarsoftware.io.JsonObjectMap" into TOON, which then
        // crashed on read-back ("put is not supported on JsonObjectMap").
        Map<Long, String> m = new LinkedHashMap<>();
        m.put(1L, "one");
        m.put(2L, "two");
        Object graph = JsonIo.toMaps(JsonIo.toJson(m)).asClass(null);

        String toon = JsonIo.toToon(graph, new WriteOptionsBuilder().showTypeInfoMinimalPlus().cycleSupport(true).build());
        assertFalse(toon.contains("JsonObjectMap"), "internal JsonObjectMap leaked into TOON: " + toon);
        // Must read back without throwing.
        Object back = JsonIo.fromToonToMaps(toon).asClass(null);
        assertEquals(true, back instanceof Map);
    }

    @Test
    void nonStringKeyMapGraph_minimal_roundTripsWithoutCrash() {
        // Build a JsonObjectMap-shaped graph (non-String keys) and re-serialize with type info.
        Map<Long, String> m = new LinkedHashMap<>();
        m.put(1L, "one");
        m.put(2L, "two");
        String json = JsonIo.toJson(m);   // {"@type":"LinkedHashMap","@keys":[1,2],"@items":["one","two"]}
        Object graph = JsonIo.toMaps(json).asClass(null);

        String reser = JsonIo.toJson(graph, new WriteOptionsBuilder().showTypeInfoMinimal().build());
        assertFalse(reser.contains("JsonObjectMap"), "internal JsonObjectMap leaked as @type: " + reser);
        // Must be readable again (today this throws "put is not supported on JsonObjectMap").
        Object back = JsonIo.toMaps(reser).asClass(null);
        assertEquals(true, back instanceof Map);
    }
}
