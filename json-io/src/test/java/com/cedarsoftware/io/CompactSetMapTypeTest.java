package com.cedarsoftware.io;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import com.cedarsoftware.util.CompactMap;
import com.cedarsoftware.util.CompactSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A CompactSet keeps its backing map through JSON. The config string had no place for it, so a set built on
 * ConcurrentHashMap was read back on HashMap, and one on ConcurrentSkipListMap on TreeMap: no longer thread-safe,
 * without a word. The map type is written as a fourth part of the config when it is not the ordering's default; a
 * set on its default map is written as before, so older json-io reads it.
 */
class CompactSetMapTypeTest {

    private static CompactSet<String> filled(CompactSet<String> set) {
        set.addAll(Arrays.asList("delta", "alpha", "charlie", "bravo"));
        return set;
    }

    private static CompactSet<String> roundTrip(CompactSet<String> set) {
        return JsonIo.toJava(JsonIo.toJson(set, null), null).asClass(CompactSet.class);
    }

    private static Object mapType(CompactSet<?> set) {
        return set.getConfig().get(CompactMap.MAP_TYPE);
    }

    @Test
    void aConcurrentBackingMapIsKept() {
        CompactSet<String> unordered = filled(CompactSet.<String>builder().compactSize(2).noOrder()
                .mapType(ConcurrentHashMap.class).build());
        String json = JsonIo.toJson(unordered, null);
        assertTrue(json.contains("\"CS/S2/Unord/java.util.concurrent.ConcurrentHashMap\""), json);
        CompactSet<String> back = JsonIo.toJava(json, null).asClass(CompactSet.class);
        assertEquals(ConcurrentHashMap.class, mapType(back));
        assertEquals(unordered, back);

        CompactSet<String> sorted = filled(CompactSet.<String>builder().compactSize(2).sortedOrder()
                .mapType(ConcurrentSkipListMap.class).build());
        CompactSet<String> sortedBack = roundTrip(sorted);
        assertEquals(ConcurrentSkipListMap.class, mapType(sortedBack));
        assertEquals(Arrays.asList("alpha", "bravo", "charlie", "delta"), Arrays.asList(sortedBack.toArray()));

        CompactSet<String> caseless = filled(CompactSet.<String>builder().compactSize(2).caseSensitive(false)
                .noOrder().mapType(ConcurrentHashMap.class).build());
        CompactSet<String> caselessBack = roundTrip(caseless);
        assertEquals(ConcurrentHashMap.class, mapType(caselessBack));
        assertTrue(caselessBack.contains("ALPHA"));
    }

    /** A set on its ordering's default map is written exactly as before, and reads back the same. */
    @Test
    void aDefaultBackingMapIsWrittenAsBefore() {
        Object[][] cases = {
                {CompactSet.<String>builder().compactSize(2).noOrder().build(), "CS/S2/Unord", HashMap.class},
                {CompactSet.<String>builder().compactSize(2).sortedOrder().build(), "CS/S2/Sort", TreeMap.class},
                {CompactSet.<String>builder().compactSize(2).reverseOrder().build(), "CS/S2/Rev", TreeMap.class},
                {CompactSet.<String>builder().compactSize(2).insertionOrder().build(), "CS/S2/Ins",
                        LinkedHashMap.class},
        };
        for (Object[] each : cases) {
            @SuppressWarnings("unchecked")
            CompactSet<String> set = filled((CompactSet<String>) each[0]);
            String json = JsonIo.toJson(set, null);
            assertTrue(json.contains("\"config\":\"" + each[1] + "\""), json);
            CompactSet<String> back = JsonIo.toJava(json, null).asClass(CompactSet.class);
            assertEquals(each[2], mapType(back), json);
            assertEquals(set, back);
        }
    }

    /** JSON written before (three parts) reads as before. */
    @Test
    void theOldFormReads() {
        CompactSet<String> back = JsonIo.toJava(
                "{\"@type\":\"CompactSet\",\"config\":\"CI/S3/Sort\",\"data\":[\"b\",\"a\"]}", null)
                .asClass(CompactSet.class);
        assertEquals(TreeMap.class, mapType(back));
        assertTrue(back.contains("A"));
        assertEquals(Arrays.asList("a", "b"), Arrays.asList(back.toArray()));
    }

    /**
     * The map type comes from the JSON, so it is vetted as CompactMap's is: the class is looked up through the
     * security block list, and must be a Map from an allowed package. Anything else is refused, not replaced.
     */
    @Test
    void aMapTypeThatIsNotAnAllowedMapIsRefused() {
        for (String type : new String[] {"java.lang.String", "java.lang.ProcessBuilder", "com.example.NoSuchMap",
                "java.util.concurrent.ConcurrentHashMap$KeySetView"}) {
            String json = "{\"@type\":\"CompactSet\",\"config\":\"CS/S2/Unord/" + type + "\",\"data\":[\"a\"]}";
            assertThrows(JsonIoException.class, () -> JsonIo.toJava(json, null).asClass(CompactSet.class), type);
        }
        String extra = "{\"@type\":\"CompactSet\",\"config\":\"CS/S2/Unord/java.util.HashMap/x\",\"data\":[\"a\"]}";
        assertThrows(JsonIoException.class, () -> JsonIo.toJava(extra, null).asClass(CompactSet.class));
    }

    /** A CompactSet inside an object, and in a map, keeps its backing map too. */
    @Test
    void nestedSetsKeepTheirBackingMap() {
        Map<String, Object> holder = new LinkedHashMap<>();
        holder.put("users", filled(CompactSet.<String>builder().compactSize(2).noOrder()
                .mapType(ConcurrentHashMap.class).build()));
        Map<String, Object> back = JsonIo.toJava(JsonIo.toJson(holder, null), null).asClass(Map.class);
        assertEquals(ConcurrentHashMap.class, mapType((CompactSet<?>) back.get("users")));
    }
}
