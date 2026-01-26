package com.cedarsoftware.io.util;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SealableNavigableMapAdditionalTest {

    private boolean sealed;
    private Supplier<Boolean> sealedSupplier;

    @BeforeEach
    void setUp() {
        sealed = false;
        sealedSupplier = () -> sealed;
    }

    @Test
    void testSortedMapConstructorAndNavigation() {
        SortedMap<String, Integer> source = new TreeMap<>();
        source.put("a", 1);
        source.put("b", 2);
        source.put("c", 3);

        SealableNavigableMap<String, Integer> map = new SealableNavigableMap<>(source, sealedSupplier);
        source.put("d", 4); // changes to source should not appear
        assertFalse(map.containsKey("d"));

        // Create expected map using the same constructor approach as 'map'
        // to ensure they use the same underlying implementation for valid hashCode comparison
        SortedMap<String, Integer> expectedSource = new TreeMap<>();
        expectedSource.put("a", 1);
        expectedSource.put("b", 2);
        expectedSource.put("c", 3);
        SealableNavigableMap<String, Integer> expected = new SealableNavigableMap<>(expectedSource, sealedSupplier);
        assertEquals(expected.hashCode(), map.hashCode());

        assertTrue(map.containsKey("b"));
        assertTrue(map.containsValue(2));
        assertEquals(Integer.valueOf(3), map.get("c"));
        assertNull(map.comparator());
        assertEquals("a", map.firstKey());
        assertEquals("c", map.lastKey());

        assertEquals("a", map.lowerKey("b"));
        assertEquals("a", map.lowerEntry("b").getKey());
        assertEquals("b", map.floorKey("b"));
        assertEquals("b", map.floorEntry("b").getKey());
        assertEquals("b", map.ceilingKey("b"));
        assertEquals("b", map.ceilingEntry("b").getKey());
        assertEquals("c", map.higherKey("b"));
        assertEquals("c", map.higherEntry("b").getKey());
        assertEquals("a", map.firstEntry().getKey());
        assertEquals("c", map.lastEntry().getKey());

        SortedMap<String, Integer> sub = map.subMap("a", "c");
        assertEquals(2, sub.size());
        sub.put("bb", 22);
        assertTrue(map.containsKey("bb"));

        assertEquals(1, map.headMap("b").size());
        assertEquals(3, map.tailMap("b").size());
    }

    @Test
    void testNavigableMapConstructorAndMutations() {
        NavigableMap<Integer, String> backing = new TreeMap<>();
        backing.put(1, "one");
        backing.put(2, "two");
        SealableNavigableMap<Integer, String> map = new SealableNavigableMap<>(backing, sealedSupplier);

        backing.put(99, "ninety-nine");
        assertTrue(map.containsKey(99));
        map.put(3, "three");
        assertEquals("three", backing.get(3));

        assertEquals(Integer.valueOf(1), map.pollFirstEntry().getKey());
        assertFalse(backing.containsKey(1));
        assertEquals(Integer.valueOf(99), map.pollLastEntry().getKey());
        assertFalse(backing.containsKey(99));

        map.remove(2);
        assertFalse(backing.containsKey(2));
        map.putAll(Collections.singletonMap(4, "four"));
        assertEquals("four", backing.get(4));

        sealed = true;
        assertThrows(UnsupportedOperationException.class, () -> map.put(5, "five"));
        assertThrows(UnsupportedOperationException.class, map::pollFirstEntry);
        assertThrows(UnsupportedOperationException.class, map::pollLastEntry);
        assertThrows(UnsupportedOperationException.class, () -> map.remove(3));
        assertThrows(UnsupportedOperationException.class, () -> map.putAll(Collections.singletonMap(6, "six")));
        assertThrows(UnsupportedOperationException.class, map::clear);

        sealed = false;
        map.clear();
        assertTrue(backing.isEmpty());
    }

    @Test
    void testEmptyMapNavigation() {
        SealableNavigableMap<String, Integer> map = new SealableNavigableMap<>(sealedSupplier);
        assertNull(map.firstEntry());
        assertNull(map.lastEntry());
        assertNull(map.pollFirstEntry());
        assertNull(map.pollLastEntry());
        assertThrows(java.util.NoSuchElementException.class, map::firstKey);
    }
}
