package com.cedarsoftware.io.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SealableNavigableSetConstructorTest {

    private volatile boolean sealed;
    private Supplier<Boolean> sealedSupplier;

    @BeforeEach
    void setUp() {
        sealed = false;
        sealedSupplier = () -> sealed;
    }

    @Test
    void testComparatorConstructor() {
        Comparator<Integer> desc = Comparator.reverseOrder();
        SealableNavigableSet<Integer> set = new SealableNavigableSet<>(desc, sealedSupplier);
        set.addAll(Arrays.asList(1, 2, 3));

        assertSame(desc, set.comparator());
        assertEquals(Integer.valueOf(3), set.first());
        assertEquals(Integer.valueOf(1), set.last());
    }

    @Test
    void testCollectionConstructor() {
        Collection<Integer> source = new ArrayList<>(Arrays.asList(1, 2, 3));
        SealableNavigableSet<Integer> set = new SealableNavigableSet<>(source, sealedSupplier);
        source.add(4);

        assertEquals(3, set.size());
        assertTrue(set.containsAll(Arrays.asList(1, 2, 3)));
        assertFalse(set.contains(4));
    }

    @Test
    void testSortedSetConstructor() {
        SortedSet<Integer> backing = new TreeSet<>(Comparator.reverseOrder());
        backing.addAll(Arrays.asList(1, 2, 3));
        SealableNavigableSet<Integer> set = new SealableNavigableSet<>(backing, sealedSupplier);
        backing.add(4);

        assertEquals(3, set.size());
        assertFalse(set.contains(4));
        assertSame(backing.comparator(), set.comparator());
    }
}
