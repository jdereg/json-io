package com.cedarsoftware.io.util;

import java.util.*;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SealableSetConstructorEntryTest {

    @Test
    void testCollectionConstructorCopiesElements() {
        Collection<Integer> src = new LinkedHashSet<>(Arrays.asList(1, 2, null));
        Supplier<Boolean> sealed = () -> false;
        SealableSet<Integer> set = new SealableSet<>(src, sealed);
        src.add(3);
        assertEquals(3, set.size());
        assertTrue(set.containsAll(Arrays.asList(1, 2)));
        assertTrue(set.contains(null));
        assertFalse(set.contains(3));
    }

    @Test
    void testToArrayReturnsAllElements() {
        SealableSet<String> set = new SealableSet<>(Arrays.asList("a", "b", null), () -> false);
        Object[] arr = set.toArray();
        List<Object> list = Arrays.asList(arr);
        assertEquals(3, arr.length);
        assertTrue(list.contains("a"));
        assertTrue(list.contains("b"));
        assertTrue(list.contains(null));
    }

    @Test
    void testSealAwareEntryEquals() {
        Map.Entry<String, Integer> base = new AbstractMap.SimpleEntry<>("k", 1);
        SealableSet.SealAwareEntry<String, Integer> entry =
                new SealableSet.SealAwareEntry<>(base, () -> false);
        Map.Entry<String, Integer> equal = new AbstractMap.SimpleEntry<>("k", 1);
        Map.Entry<String, Integer> diff = new AbstractMap.SimpleEntry<>("k", 2);
        assertTrue(entry.equals(equal));
        assertTrue(entry.equals(entry));
        assertFalse(entry.equals(diff));
        assertFalse(entry.equals(null));
    }
}
