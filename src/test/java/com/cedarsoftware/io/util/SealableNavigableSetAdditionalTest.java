package com.cedarsoftware.io.util;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

class SealableNavigableSetAdditionalTest {

    private boolean sealed;
    private Supplier<Boolean> sealedSupplier;
    private SealableNavigableSet<Integer> set;

    @BeforeEach
    void setUp() {
        sealed = false;
        sealedSupplier = () -> sealed;
        set = new SealableNavigableSet<>(sealedSupplier);
        set.addAll(asList(10, 20, 30));
    }

    @Test
    void testQueryMethods() {
        SealableNavigableSet<Integer> other = new SealableNavigableSet<>(sealedSupplier);
        other.addAll(asList(10, 20, 30));
        assertEquals(other.hashCode(), set.hashCode());

        assertFalse(set.isEmpty());
        set.clear();
        assertTrue(set.isEmpty());
        set.addAll(asList(10, 20, 30));

        assertTrue(set.contains(20));
        assertTrue(set.containsAll(asList(10, 30)));
        assertNull(set.comparator());
        assertEquals(Integer.valueOf(10), set.first());
        assertEquals(Integer.valueOf(30), set.last());
    }

    @Test
    void testCustomComparator() {
        SealableNavigableSet<Integer> rev =
                new SealableNavigableSet<>(Comparator.reverseOrder(), sealedSupplier);
        rev.addAll(asList(1, 2, 3));
        assertNotNull(rev.comparator());
        assertEquals(Integer.valueOf(3), rev.first());
        assertEquals(Integer.valueOf(1), rev.last());
    }

    @Test
    void testArrayAndNavigationMethods() {
        assertArrayEquals(new Object[] {10, 20, 30}, set.toArray());
        assertArrayEquals(new Integer[] {10, 20, 30}, set.toArray(new Integer[0]));

        assertNull(set.lower(10));
        assertEquals(Integer.valueOf(20), set.floor(20));
        assertEquals(Integer.valueOf(20), set.ceiling(15));
        assertEquals(Integer.valueOf(30), set.higher(20));
    }

    @Test
    void testViewSets() {
        NavigableSet<Integer> descending = set.descendingSet();
        assertTrue(descending instanceof SealableNavigableSet);
        assertEquals(Arrays.asList(30, 20, 10), new ArrayList<>(descending));

        SortedSet<Integer> subset = set.subSet(10, 30);
        assertEquals(Arrays.asList(10, 20), new ArrayList<>(subset));

        SortedSet<Integer> head = set.headSet(30);
        assertEquals(Arrays.asList(10, 20), new ArrayList<>(head));

        SortedSet<Integer> tail = set.tailSet(20);
        assertEquals(Arrays.asList(20, 30), new ArrayList<>(tail));

        sealed = true;
        assertThrows(UnsupportedOperationException.class, () -> descending.add(5));
        assertThrows(UnsupportedOperationException.class, () -> subset.add(25));
        assertThrows(UnsupportedOperationException.class, () -> head.add(5));
        assertThrows(UnsupportedOperationException.class, () -> tail.add(35));
    }

    @Test
    void testMutationMethods() {
        assertTrue(set.addAll(asList(40, 50)));
        assertTrue(set.remove(Integer.valueOf(20)));
        assertTrue(set.removeAll(asList(10)));
        assertTrue(set.retainAll(asList(30, 40, 50)));
        assertEquals(Integer.valueOf(30), set.pollFirst());
        assertEquals(Integer.valueOf(50), set.pollLast());
        set.clear();
        assertTrue(set.isEmpty());

        set.addAll(asList(1, 2, 3));
        sealed = true;
        assertThrows(UnsupportedOperationException.class, () -> set.addAll(asList(4, 5)));
        assertThrows(UnsupportedOperationException.class, set::clear);
        assertThrows(UnsupportedOperationException.class, () -> set.remove(1));
        assertThrows(UnsupportedOperationException.class, () -> set.removeAll(asList(2)));
        assertThrows(UnsupportedOperationException.class, () -> set.retainAll(asList(1)));
        assertThrows(UnsupportedOperationException.class, set::pollFirst);
        assertThrows(UnsupportedOperationException.class, set::pollLast);
    }

    @Test
    void testEntryIteratorWrapsAndSealHonored() {
        NavigableSet<Map.Entry<Integer, String>> entries =
                new TreeSet<>(Map.Entry.comparingByKey());
        Map.Entry<Integer, String> e1 = new AbstractMap.SimpleEntry<>(1, "one");
        Map.Entry<Integer, String> e2 = new AbstractMap.SimpleEntry<>(2, "two");
        entries.add(e1);
        entries.add(e2);

        SealableNavigableSet<Map.Entry<Integer, String>> entrySet =
                new SealableNavigableSet<>(entries, sealedSupplier);
        Iterator<Map.Entry<Integer, String>> it = entrySet.iterator();
        Map.Entry<Integer, String> wrapped = it.next();
        assertTrue(wrapped instanceof SealableSet.SealAwareEntry);
        assertNotSame(e1, wrapped);

        it.remove();
        assertEquals(1, entrySet.size());
        assertFalse(entries.contains(e1));

        it = entrySet.iterator();
        it.next();
        sealed = true;
        assertThrows(UnsupportedOperationException.class, it::remove);
    }

    @Test
    void testEquals() {
        SealableNavigableSet<Integer> other = new SealableNavigableSet<>(sealedSupplier);
        other.addAll(asList(10, 20, 30));
        assertEquals(set, other);
        other.add(40);
        assertNotEquals(set, other);
    }

    @Test
    void testToStringReflectsSetContents() {
        NavigableSet<Integer> expected = new TreeSet<>(asList(10, 20, 30));
        assertEquals(expected.toString(), set.toString());
        set.add(40);
        expected.add(40);
        assertEquals(expected.toString(), set.toString());
    }

    @Test
    void testPollFirstAndLast() {
        assertEquals(Integer.valueOf(10), set.pollFirst());
        assertEquals(Integer.valueOf(30), set.pollLast());
        assertFalse(set.contains(10));
        assertFalse(set.contains(30));

        set.clear();
        assertNull(set.pollFirst());
        assertNull(set.pollLast());
    }
}
