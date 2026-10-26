package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A JSON document may repeat a key: {@code {"k":"first","k":"second"}}. The parsers hold it once: its last value,
 * where the key first stood, as {@code LinkedHashMap.put} and JavaScript's {@code JSON.parse} leave it. They look a
 * field up only where its name's hash bit is already set in the object (looking every field up cost 5-9% of read
 * time, measured); they kept every copy, the object's size, keys and a rewrite counting both. Strict JSON refuses a
 * repeated key. A JsonObject given a key twice ({@link JsonObject#appendFieldForParser}) still finds its last value at
 * every size, as before.
 */
class JsonObjectRepeatedKeyTest {

    private static JsonObject withRepeat(int fields) {
        JsonObject jo = new JsonObject();
        for (int i = 0; i < fields; i++) {
            jo.appendFieldForParser("f" + i, i);
        }
        jo.appendFieldForParser("f0", "last");
        return jo;
    }

    @Test
    void getFindsTheLastValueAtEverySize() {
        int threshold = JsonObject.getLinearSearchThreshold();
        for (int n : new int[]{1, 2, threshold - 1, threshold, threshold + 1, 100}) {
            JsonObject jo = withRepeat(n);
            assertEquals("last", jo.get("f0"), n + " fields");
        }
    }

    @Test
    void putReplacesTheValueGetReturns() {
        JsonObject jo = withRepeat(2);
        assertEquals("last", jo.put("f0", "replaced"));
        assertEquals("replaced", jo.get("f0"));
    }

    @Test
    void removeTakesEveryCopy() {
        for (int n : new int[]{2, JsonObject.getLinearSearchThreshold() + 2}) {
            JsonObject jo = withRepeat(n);
            assertEquals("last", jo.remove("f0"));
            assertNull(jo.get("f0"), n + " fields: removing the last copy must not bring back the first");
            assertFalse(jo.containsKey("f0"));
            assertFalse(new ArrayList<>(jo.keySet()).contains("f0"));
        }
    }

    public static class Seen {
        Object k;
    }

    /** A ClassFactory reads the JsonObject itself: get() gave the first value when the object was small. */
    @Test
    void aClassFactoryReadsTheLastValue() {
        ClassFactory factory = new ClassFactory() {
            @Override
            public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
                Seen s = new Seen();
                s.k = jObj.get("k");
                return s;
            }

            @Override
            public boolean isObjectFinal() {
                return true;
            }
        };
        ReadOptions options = new ReadOptionsBuilder().addClassFactory(Seen.class, factory).build();
        Seen seen = JsonIo.toJava("{\"@type\":\"" + Seen.class.getName() + "\",\"k\":\"first\",\"k\":\"second\"}", options)
                .asClass(Seen.class);
        assertEquals("second", seen.k);
    }

    /** Only a constructor sees these: no setters, final fields, and parameter names to match by. */
    public static class Pair {
        private final String k;
        private final String other;

        public Pair(String k, String other) {
            this.k = k;
            this.other = other;
        }

        String k() {
            return k;
        }

        String other() {
            return other;
        }
    }

    public static class Named {
        public String name;
    }

    /** Already true before this change, through field injection and Maps mode; must stay true. */
    @Test
    @SuppressWarnings("unchecked")
    void aDocumentWithARepeatedKeyReadsTheLastValueEverywhere() {
        String json = "{\"k\":\"first\",\"other\":\"o\",\"k\":\"second\"}";

        Pair pair = JsonIo.toJava(json, null).asClass(Pair.class);
        assertEquals("second", pair.k());
        assertEquals("o", pair.other());

        Named named = JsonIo.toJava("{\"name\":\"first\",\"name\":\"second\"}", null).asClass(Named.class);
        assertEquals("second", named.name);

        Map<String, Object> map = JsonIo.toMaps(json, null).asClass(Map.class);
        assertEquals(2, map.size());
        assertEquals("second", map.get("k"));
        assertEquals(Arrays.asList("k", "other"), new ArrayList<>(map.keySet()));
    }

    /** Already true in Maps mode; must stay true. */
    @Test
    @SuppressWarnings("unchecked")
    void nonStrictToonTakesTheLastValue() {
        Map<String, Object> map = JsonIo.fromToon("k: first\nother: o\nk: second\n", null).asClass(Map.class);
        assertEquals(2, map.size());
        assertEquals("second", map.get("k"));
    }

    /**
     * The document's JsonObject holds a repeated key once: its last value, where the key first stood. Its size, its
     * keys and its rewrite counted every copy. At the root, nested, an unknown @-key, in Maps and in Java mode.
     */
    @Test
    void aRepeatedKeyIsHeldOnce() {
        String json = "{\"a\":{\"x\":1,\"y\":0,\"x\":2},\"x\":3,\"@note\":\"p\",\"x\":4,\"@note\":\"q\"}";
        for (ReadOptions options : new ReadOptions[] {new ReadOptionsBuilder().returnAsJsonObjects().build(), null}) {
            Map<?, ?> root = (Map<?, ?>) JsonIo.toJava(json, options).asClass(null);
            assertEquals(Arrays.asList("a", "x", "@note"), new ArrayList<>(root.keySet()));
            assertEquals(4L, root.get("x"));
            assertEquals("q", root.get("@note"));
            Map<?, ?> nested = (Map<?, ?>) root.get("a");
            assertEquals(Arrays.asList("x", "y"), new ArrayList<>(nested.keySet()));
            assertEquals(2L, nested.get("x"));
            assertEquals("{\"a\":{\"x\":2,\"y\":0},\"x\":4,\"@note\":\"q\"}", JsonIo.toJson(root, null));
        }
    }

    /** Different keys are kept, though they share a hash bit or a hash ("Aa" and "BB"), and past 64 fields. */
    @Test
    void differentKeysAreKept() {
        Map<?, ?> same = JsonIo.toMaps("{\"Aa\":1,\"BB\":2,\"Aa\":3}", null).asClass(Map.class);
        assertEquals(Arrays.asList("Aa", "BB"), new ArrayList<>(same.keySet()));
        assertEquals(3L, same.get("Aa"));
        assertEquals(2L, same.get("BB"));
        StringBuilder many = new StringBuilder("{");
        for (int i = 0; i < 100; i++) {
            many.append(i > 0 ? "," : "").append("\"k").append(i).append("\":").append(i);
        }
        many.append(",\"k7\":-7}");
        Map<?, ?> big = JsonIo.toMaps(many.toString(), null).asClass(Map.class);
        assertEquals(100, big.size());
        assertEquals(-7L, big.get("k7"));
        assertEquals(99L, big.get("k99"));
    }

    /** Strict JSON refuses a repeated key (RFC 8259: the names within an object SHOULD be unique), naming it. */
    @Test
    void strictJsonRefusesARepeatedKey() {
        ReadOptions strict = new ReadOptionsBuilder().strictJson().build();
        JsonIoException e = assertThrows(JsonIoException.class,
                () -> JsonIo.toMaps("{\"a\":{\"x\":1,\"x\":2}}", strict).asClass(null));
        assertTrue(e.getMessage().contains("Duplicate key 'x'"), e.getMessage());
        assertEquals(1L, ((Map<?, ?>) JsonIo.toMaps("{\"Aa\":1,\"BB\":1}", strict).asClass(Map.class)).get("Aa"));
    }

    /**
     * Non-strict TOON holds a repeated key once, its last value (spec 14.4): in key/value lines, a list item, a
     * tabular header that names a field twice, a keyed object. Strict TOON refuses each, as before.
     */
    @Test
    void nonStrictToonHoldsARepeatedKeyOnce() {
        String[][] cases = {
                {"a: 1\nb:\n  x: 1\n  y: 2\n  x: 3\na: 4\n", "{\"a\":4,\"b\":{\"x\":3,\"y\":2}}"},
                {"items[2]:\n  - k: 1\n    k: 2\n  - k: 3\n", "{\"items\":[{\"k\":2},{\"k\":3}]}"},
                {"rows[2]{a,b,a}:\n  1,2,3\n  4,5,6\n", "{\"rows\":[{\"a\":3,\"b\":2},{\"a\":6,\"b\":5}]}"},
                {"byId[2:]{v}:\n  k1: 1\n  k1: 2\n", "{\"byId\":{\"k1\":{\"v\":2}}}"},
        };
        WriteOptions plain = new WriteOptionsBuilder().showTypeInfoNever().build();
        ReadOptions strict = new ReadOptionsBuilder().strictToon().build();
        for (String[] c : cases) {
            assertEquals(c[1], JsonIo.toJson(JsonIo.fromToonToMaps(c[0], null).asClass(null), plain), c[0]);
            assertThrows(JsonIoException.class, () -> JsonIo.fromToonToMaps(c[0], strict).asClass(null), c[0]);
        }
    }
}
