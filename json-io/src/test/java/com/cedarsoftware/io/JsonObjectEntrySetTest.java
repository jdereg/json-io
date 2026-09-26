package com.cedarsoftware.io;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.convert.Converter;
import com.cedarsoftware.util.convert.DefaultConverterOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * JsonObject's entrySet() iterator handed out ONE Map.Entry, re-pointed at the next field on every next(). Code
 * that only reads each entry before advancing never noticed; anything that keeps entries -- a List of them,
 * toArray(), a sorted or collected stream -- ended up holding the last field N times, and setValue() on an entry
 * kept from earlier wrote to whichever field the iterator had reached. The JDK treated the same pattern in EnumMap
 * and IdentityHashMap as a bug (JDK-6312706, fixed in Java 7). Each next() now returns its own entry, which reads
 * and writes through to the JsonObject.
 */
class JsonObjectEntrySetTest {

    private static JsonObject abc() {
        JsonObject jo = new JsonObject();
        jo.put("a", 1);
        jo.put("b", 2);
        jo.put("c", 3);
        return jo;
    }

    private static List<String> describe(List<Map.Entry<Object, Object>> entries) {
        return entries.stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.toList());
    }

    @Test
    void entriesKeptInAListAreEachTheirOwn() {
        List<Map.Entry<Object, Object>> kept = new ArrayList<>(abc().entrySet());
        assertEquals(Arrays.asList("a=1", "b=2", "c=3"), describe(kept));
        assertNotSame(kept.get(0), kept.get(1));
    }

    @Test
    void aSortedStreamOfEntriesKeepsEveryEntry() {
        JsonObject jo = new JsonObject();
        jo.put("b", 2);
        jo.put("c", 3);
        jo.put("a", 1);
        List<Map.Entry<Object, Object>> sorted = jo.entrySet().stream()
                .sorted((x, y) -> ((String) x.getKey()).compareTo((String) y.getKey()))
                .collect(Collectors.toList());
        assertEquals(Arrays.asList("a=1", "b=2", "c=3"), describe(sorted));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toArrayHoldsEveryEntry() {
        Object[] array = abc().entrySet().toArray();
        List<Map.Entry<Object, Object>> entries = new ArrayList<>();
        for (Object o : array) {
            entries.add((Map.Entry<Object, Object>) o);
        }
        assertEquals(Arrays.asList("a=1", "b=2", "c=3"), describe(entries));
    }

    @Test
    void setValueOnAnEarlierEntryWritesThatEntrysField() {
        JsonObject jo = abc();
        Iterator<Map.Entry<Object, Object>> it = jo.entrySet().iterator();
        Map.Entry<Object, Object> first = it.next();
        it.next();
        it.next();
        assertEquals(1, first.setValue(10));
        assertEquals(10, jo.get("a"));
        assertEquals(3, jo.get("c"), "the write must not land on the field the iterator reached");
    }

    @Test
    void setValueWritesThroughAndClearsTheCachedHash() {
        JsonObject jo = abc();
        jo.hashCode();
        for (Map.Entry<Object, Object> e : jo.entrySet()) {
            e.setValue(((Integer) e.getValue()) * 10);
        }
        assertEquals(10, jo.get("a"));
        assertEquals(30, jo.get("c"));
        JsonObject same = new JsonObject();
        same.put("a", 10);
        same.put("b", 20);
        same.put("c", 30);
        assertEquals(same.hashCode(), jo.hashCode());
    }

    @Test
    void anEntryFollowsTheMapEntryContract() {
        Map.Entry<Object, Object> e = abc().entrySet().iterator().next();
        Map.Entry<Object, Object> expected = new AbstractMap.SimpleEntry<>("a", 1);
        assertEquals(expected, e);
        assertEquals(e, expected);
        assertEquals(expected.hashCode(), e.hashCode());
        assertEquals("a=1", e.toString());
    }

    @Test
    void aCopyOfTheEntrySetEqualsTheMapsOwn() {
        JsonObject jo = abc();
        Map<Object, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> e : new ArrayList<>(jo.entrySet())) {
            copy.put(e.getKey(), e.getValue());
        }
        assertEquals(3, copy.size());
        assertEquals(Arrays.asList("a", "b", "c"), new ArrayList<>(copy.keySet()));
    }

    /** Parameter names that look synthetic, so ClassUtilities matches the map's values by position. */
    static class Gapped {
        final String first;
        final String second;

        Gapped(String arg0, String arg1) {
            this.first = arg0;
            this.second = arg1;
        }
    }

    /**
     * ClassUtilities orders keys like arg0, arg2 (a gap) by keeping the entries in a list and sorting them -- so it
     * built Gapped("c", "c") from a JsonObject where a LinkedHashMap gives Gapped("a", "c").
     */
    @Test
    @SuppressWarnings("unchecked")
    void classUtilitiesBuildsTheSameFromAJsonObjectAsFromALinkedHashMap() {
        Converter converter = new Converter(new DefaultConverterOptions());
        JsonObject jo = new JsonObject();
        jo.put("arg0", "a");
        jo.put("arg2", "c");
        Gapped g = (Gapped) ClassUtilities.newInstance(converter, Gapped.class, (Map<String, Object>) (Map<?, ?>) jo);
        assertEquals("a", g.first);
        assertEquals("c", g.second);
    }
}
