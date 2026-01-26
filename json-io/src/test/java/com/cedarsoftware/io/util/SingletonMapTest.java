package com.cedarsoftware.io.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SingletonMapTest {

    @Test
    void testConstructorsAndSize() {
        SingletonMap<String, String> empty = new SingletonMap<>();
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.size());

        SingletonMap<String, String> nullKey = new SingletonMap<>(null, "v");
        assertTrue(nullKey.isEmpty());
        assertEquals(0, nullKey.size());

        SingletonMap<String, String> map = new SingletonMap<>("k", "v");
        assertFalse(map.isEmpty());
        assertEquals(1, map.size());
    }

    @Test
    void testContainsAndGetMethods() {
        SingletonMap<String, String> map = new SingletonMap<>("k", "v");
        assertTrue(map.containsKey("k"));
        assertFalse(map.containsKey("x"));
        assertFalse(map.containsKey(null));

        assertTrue(map.containsValue("v"));
        assertFalse(map.containsValue("x"));
        assertFalse(map.containsValue(null));

        assertEquals("v", map.get("k"));
        assertNull(map.get("x"));

        SingletonMap<String, String> empty = new SingletonMap<>();
        assertFalse(empty.containsKey("k"));
        assertFalse(empty.containsValue("v"));
        assertNull(empty.get("k"));
    }

    @Test
    void testPutScenarios() {
        SingletonMap<String, Integer> map = new SingletonMap<>();
        assertNull(map.put("a", 1));
        assertEquals(Integer.valueOf(1), map.get("a"));

        assertEquals(Integer.valueOf(1), map.put("a", 2));
        assertEquals(Integer.valueOf(2), map.get("a"));

        assertThrows(UnsupportedOperationException.class, () -> map.put("b", 3));
    }

    @Test
    void testUnsupportedMutators() {
        SingletonMap<String, String> map = new SingletonMap<>("a", "b");
        assertThrows(UnsupportedOperationException.class, () -> map.remove("a"));
        assertThrows(UnsupportedOperationException.class, () -> map.putAll(Collections.emptyMap()));
        assertThrows(UnsupportedOperationException.class, map::clear);
    }

    @Test
    void testViewCollectionsAndEntryBehavior() {
        SingletonMap<String, String> empty = new SingletonMap<>();
        assertTrue(empty.keySet().isEmpty());
        assertTrue(empty.values().isEmpty());
        assertTrue(empty.entrySet().isEmpty());

        SingletonMap<String, String> map = new SingletonMap<>("k", "v");
        Set<String> keys = map.keySet();
        assertEquals(Collections.singleton("k"), keys);
        assertThrows(UnsupportedOperationException.class, () -> keys.add("x"));

        Collection<String> values = map.values();
        assertEquals(Collections.singletonList("v"), values);

        Map.Entry<String, String> entry = map.entrySet().iterator().next();
        assertEquals("k", entry.getKey());
        assertEquals("v", entry.getValue());
        assertEquals("v", entry.setValue("x"));
        assertEquals("x", map.get("k"));
    }

    @Test
    void testEqualsAndHashCode() {
        SingletonMap<String, String> empty = new SingletonMap<>();
        assertEquals(empty, empty);
        assertEquals(empty, Collections.emptyMap());
        assertFalse(empty.equals("str"));
        assertEquals(0, empty.hashCode());

        SingletonMap<String, String> map = new SingletonMap<>("k", "v");
        Map<String, String> same = Collections.singletonMap("k", "v");
        Map<String, String> diffKey = Collections.singletonMap("x", "v");
        Map<String, String> diffVal = Collections.singletonMap("k", "x");
        Map<String, String> bigger = new LinkedHashMap<>();
        bigger.put("k", "v");
        bigger.put("x", "y");

        assertTrue(map.equals(map));
        assertTrue(map.equals(same));
        assertFalse(map.equals(diffKey));
        assertFalse(map.equals(diffVal));
        assertFalse(map.equals(bigger));
        assertFalse(map.equals("not a map"));

        int expectedHash = "k".hashCode() ^ "v".hashCode();
        assertEquals(expectedHash, map.hashCode());

        SingletonMap<String, String> nullValueMap = new SingletonMap<>();
        nullValueMap.put("k", null);
        int expectedNullHash = "k".hashCode();
        assertEquals(expectedNullHash, nullValueMap.hashCode());
    }
}
