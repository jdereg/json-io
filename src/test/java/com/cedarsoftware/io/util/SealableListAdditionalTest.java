package com.cedarsoftware.io.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SealableListAdditionalTest {

    private volatile boolean sealedState = false;
    private Supplier<Boolean> sealedSupplier = () -> sealedState;
    private SealableList<Integer> list;

    @BeforeEach
    void setUp() {
        sealedState = false;
        list = new SealableList<>(new ArrayList<>(Arrays.asList(10, 20, 30)), sealedSupplier);
    }

    @Test
    void testConstructorCopiesCollection() {
        Collection<Integer> original = new ArrayList<>(Arrays.asList(1, 2, 3));
        SealableList<Integer> copy = new SealableList<>(original, sealedSupplier);
        original.add(4);
        assertEquals(3, copy.size());
        assertTrue(copy.containsAll(Arrays.asList(1, 2, 3)));
    }

    @Test
    void testIsEmptyAndClear() {
        assertFalse(list.isEmpty());
        list.clear();
        assertTrue(list.isEmpty());
    }

    @Test
    void testContainsAllAndIndexes() {
        list.add(20);
        assertTrue(list.containsAll(Arrays.asList(10, 20)));
        assertFalse(list.containsAll(Arrays.asList(10, 40)));
        assertEquals(1, list.indexOf(20));
        assertEquals(3, list.lastIndexOf(20));
    }

    @Test
    void testToArrayMethods() {
        Object[] arr = list.toArray();
        assertArrayEquals(new Object[] {10, 20, 30}, arr);

        Integer[] ints = list.toArray(new Integer[0]);
        assertArrayEquals(new Integer[] {10, 20, 30}, ints);
    }

    @Test
    void testIteratorRemove() {
        Iterator<Integer> it = list.iterator();
        assertEquals(10, it.next());
        it.remove();
        assertFalse(list.contains(10));
    }

    @Test
    void testListIteratorNavigationAndModification() {
        ListIterator<Integer> it = list.listIterator(1);
        assertTrue(it.hasPrevious());
        assertEquals(1, it.nextIndex());
        assertEquals(0, it.previousIndex());
        assertEquals(Integer.valueOf(20), it.next());
        assertEquals(Integer.valueOf(30), it.next());
        assertFalse(it.hasNext());
        assertEquals(Integer.valueOf(30), it.previous());
        it.set(33);
        it.add(34);
        assertEquals(Arrays.asList(10, 20, 34, 33), list);
        assertEquals(Integer.valueOf(34), it.previous());
        it.remove();
        assertEquals(Arrays.asList(10, 20, 33), list);
    }

    @Test
    void testSubListModificationAndSeal() {
        List<Integer> sub = list.subList(1, 3);
        sub.remove(Integer.valueOf(20));
        assertFalse(list.contains(20));
        sealedState = true;
        assertThrows(UnsupportedOperationException.class, () -> sub.add(50));
    }

    @Test
    void testBulkOperations() {
        list.addAll(Arrays.asList(40, 50));
        list.addAll(1, Arrays.asList(5, 6));
        assertEquals(Arrays.asList(10, 5, 6, 20, 30, 40, 50), list);
        list.removeAll(Arrays.asList(6, 40));
        assertEquals(Arrays.asList(10, 5, 20, 30, 50), list);
        list.retainAll(Arrays.asList(10, 30));
        assertEquals(Arrays.asList(10, 30), list);
        list.add(1, 15);
        list.set(0, 9);
        Integer removed = list.remove(2);
        assertEquals(Integer.valueOf(30), removed);
        assertEquals(Arrays.asList(9, 15), list);
    }

    @Test
    void testHashCodeConsistency() {
        SealableList<Integer> other = new SealableList<>(new ArrayList<>(Arrays.asList(10, 20, 30)), sealedSupplier);
        assertEquals(list.hashCode(), other.hashCode());
        other.add(40);
        assertNotEquals(list.hashCode(), other.hashCode());
    }
}
