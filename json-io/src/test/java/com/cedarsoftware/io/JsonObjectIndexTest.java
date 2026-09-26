package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Past the linear-search threshold, JsonObject finds keys through a lazily built hash index -- an open-addressing
 * int[] of positions (it was a HashMap&lt;Object, Integer&gt;, which cost a node and often a boxed Integer per field).
 * These tests hold every lookup to what a LinkedHashMap answers, through growth, keys whose hash codes are equal,
 * repeated keys (the last value wins), removal and null keys.
 */
class JsonObjectIndexTest {

    /** Strings of "Aa"/"BB" pairs all share one String.hashCode() -- the worst case for probing. */
    private static List<String> collidingKeys(int count) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; keys.size() < count; i++) {
            StringBuilder sb = new StringBuilder();
            for (int bit = 0; bit < 10; bit++) {
                sb.append(((i >> bit) & 1) == 0 ? "Aa" : "BB");
            }
            keys.add(sb.toString());
        }
        return keys;
    }

    @Test
    void keysWithEqualHashCodesAreAllFound() {
        List<String> keys = collidingKeys(200);
        assertEquals(keys.get(0).hashCode(), keys.get(199).hashCode());
        JsonObject jo = new JsonObject();
        for (int i = 0; i < keys.size(); i++) {
            jo.appendFieldForParser(keys.get(i), i);
            if (i % 7 == 0) {
                assertEquals(i, jo.get(keys.get(i)));        // lookups build and use the index as it grows
            }
        }
        for (int i = 0; i < keys.size(); i++) {
            assertEquals(i, jo.get(keys.get(i)));
        }
        assertFalse(jo.containsKey("AaAaAaAaAaAaAaAaAaBBBB"));   // same hash, not present
    }

    @Test
    void everyKeyIsFoundAsTheObjectGrows() {
        JsonObject jo = new JsonObject();
        for (int i = 0; i < 2000; i++) {
            jo.appendFieldForParser("k" + i, i);
            assertEquals(i, jo.get("k" + i));
            assertEquals(i / 2, jo.get("k" + (i / 2)));
            assertNull(jo.get("absent" + i));
        }
        for (int i = 0; i < 2000; i++) {
            assertEquals(i, jo.get("k" + i));
        }
    }

    @Test
    void theThresholdCrossingByPutIsIndexed() {
        int threshold = JsonObject.getLinearSearchThreshold();
        JsonObject jo = new JsonObject();
        for (int i = 0; i <= threshold + 2; i++) {
            jo.put("p" + i, i);
            for (int j = 0; j <= i; j++) {
                assertEquals(j, jo.get("p" + j), "after " + (i + 1) + " puts");
            }
        }
    }

    @Test
    void aRepeatedKeyPastTheThresholdReadsItsLastValue() {
        JsonObject jo = new JsonObject();
        for (int i = 0; i < 40; i++) {
            jo.appendFieldForParser("f" + i, i);
        }
        jo.get("f0");                                   // index built
        jo.appendFieldForParser("f3", "again");
        assertEquals("again", jo.get("f3"));
        assertEquals("again", jo.put("f3", "put"));
        assertEquals("put", jo.get("f3"));
        assertEquals("put", jo.remove("f3"));
        assertFalse(jo.containsKey("f3"));
        assertEquals(39, jo.get("f39"));
    }

    @Test
    void aNullKeyIsIndexedToo() {
        JsonObject jo = new JsonObject();
        for (int i = 0; i < 20; i++) {
            jo.put("n" + i, i);
        }
        jo.put(null, "nothing");
        assertEquals("nothing", jo.get(null));
        assertTrue(jo.containsKey(null));
        assertEquals("nothing", jo.remove(null));
        assertFalse(jo.containsKey(null));
    }

    /**
     * Random appends, puts, removes and lookups over a key pool mixing ordinary and hash-colliding keys, against a
     * LinkedHashMap. Fixed seed.
     */
    @Test
    void randomOperationsAgreeWithALinkedHashMap() {
        Random random = new Random(20260926L);
        List<String> pool = new ArrayList<>(collidingKeys(60));
        for (int i = 0; i < 140; i++) {
            pool.add("key" + i);
        }
        for (int round = 0; round < 20; round++) {
            JsonObject jo = new JsonObject();
            Map<Object, Object> model = new LinkedHashMap<>();
            for (int step = 0; step < 1000; step++) {
                String key = pool.get(random.nextInt(pool.size()));
                int op = random.nextInt(10);
                String where = "round " + round + " step " + step + " op " + op + " key " + key;
                if (op < 4) {
                    Object value = step;
                    jo.appendFieldForParser(key, value);
                    model.put(key, value);
                } else if (op < 6) {
                    assertEquals(model.put(key, step), jo.put(key, step), where);
                } else if (op < 7) {
                    assertEquals(model.remove(key), jo.remove(key), where);
                } else {
                    assertEquals(model.get(key), jo.get(key), where);
                    assertEquals(model.containsKey(key), jo.containsKey(key), where);
                }
            }
            for (String key : pool) {
                assertEquals(model.get(key), jo.get(key), "round " + round + " final " + key);
            }
        }
    }
}
