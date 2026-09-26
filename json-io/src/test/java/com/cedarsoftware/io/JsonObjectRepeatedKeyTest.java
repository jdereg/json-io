package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A JSON document may repeat a key: {@code {"k":"first","k":"second"}}. The parser keeps every copy (checking each
 * field for a repeat costs 5-9% of read time, measured, for keys that almost never repeat), and get("k") returned
 * "first" up to the lookup threshold -- a linear scan from the front -- but "second" above it, where the hash index
 * keeps the last. Maps mode and field injection already took the last value. Now every lookup finds the last value
 * at every size, so everything that reads a JsonObject agrees: the last value wins.
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
}
