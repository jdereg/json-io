package com.cedarsoftware.io.util;

import java.util.*;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SealableSetAdditionalTest {

    @Test
    void testCollectionConstructorMakesCopy() {
        Collection<Integer> source = new LinkedHashSet<>(Arrays.asList(1, 2, 3));
        Supplier<Boolean> sealedSupplier = () -> false;
        SealableSet<Integer> copy = new SealableSet<>(source, sealedSupplier);
        source.add(4);
        assertEquals(3, copy.size());
        assertTrue(copy.containsAll(Arrays.asList(1, 2, 3)));
        assertFalse(copy.contains(4));
    }

    @Test
    void testSetConstructorWrapsBackingSet() {
        Set<Integer> backing = new LinkedHashSet<>(Arrays.asList(5, 6));
        boolean[] sealed = new boolean[1];
        SealableSet<Integer> wrapper = new SealableSet<>(backing, () -> sealed[0]);
        backing.add(7);
        assertTrue(wrapper.contains(7));
        wrapper.add(8);
        assertTrue(backing.contains(8));
        assertEquals(backing.hashCode(), wrapper.hashCode());
        assertEquals(backing.toString(), wrapper.toString());
    }

    @Test
    void testIteratorReturnsSealAwareEntry() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("a", 1);
        final boolean[] sealed = new boolean[1];
        SealableSet<Map.Entry<String, Integer>> set = new SealableSet<>(map.entrySet(), () -> sealed[0]);
        Iterator<Map.Entry<String, Integer>> it = set.iterator();
        assertTrue(it.hasNext());
        Map.Entry<String, Integer> entry = it.next();
        assertTrue(entry instanceof SealableSet.SealAwareEntry);
        assertEquals("a", entry.getKey());
        entry.setValue(5);
        assertEquals(Integer.valueOf(5), map.get("a"));
        Map.Entry<String, Integer> cmp = new AbstractMap.SimpleEntry<>("a", 5);
        assertEquals(cmp, entry);
        assertEquals(cmp.hashCode(), entry.hashCode());
        sealed[0] = true;
        assertThrows(UnsupportedOperationException.class, () -> entry.setValue(6));
    }

    @Test
    void testIteratorRemoveHonorsSealState() {
        Set<Integer> src = new LinkedHashSet<>(Arrays.asList(9, 10));
        final boolean[] sealed = new boolean[1];
        SealableSet<Integer> set = new SealableSet<>(src, () -> sealed[0]);
        Iterator<Integer> it = set.iterator();
        assertTrue(it.hasNext());
        it.next();
        it.remove();
        assertEquals(1, set.size());
        sealed[0] = true;
        Iterator<Integer> it2 = set.iterator();
        it2.next();
        assertThrows(UnsupportedOperationException.class, it2::remove);
    }
}
