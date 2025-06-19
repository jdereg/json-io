package com.cedarsoftware.io.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SingletonSetTest {

    @Test
    void testConstructorsAndBasicQueries() {
        SingletonSet<String> withNull = new SingletonSet<>(null);
        assertTrue(withNull.isEmpty());
        assertEquals(0, withNull.size());
        assertFalse(withNull.contains("anything"));

        SingletonSet<String> empty = new SingletonSet<>();
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.size());

        SingletonSet<String> set = new SingletonSet<>("a");
        assertFalse(set.isEmpty());
        assertEquals(1, set.size());
    }

    @Test
    void testContainsAndIterator() {
        SingletonSet<String> set = new SingletonSet<>("foo");
        assertTrue(set.contains("foo"));
        assertFalse(set.contains("bar"));
        assertFalse(set.contains(null));

        SingletonSet<String> empty = new SingletonSet<>();
        assertFalse(empty.contains("foo"));
        Iterator<String> it = empty.iterator();
        assertFalse(it.hasNext());

        it = set.iterator();
        assertTrue(it.hasNext());
        assertEquals("foo", it.next());
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void testToArrayVariations() {
        SingletonSet<String> empty = new SingletonSet<>();
        assertArrayEquals(new Object[0], empty.toArray());
        Object[] seed = new Object[]{"x"};
        Object[] result = empty.toArray(seed);
        assertSame(seed, result);
        assertEquals("x", seed[0]);

        SingletonSet<String> set = new SingletonSet<>("x");
        assertArrayEquals(new Object[]{"x"}, set.toArray());

        String[] arr0 = new String[0];
        String[] arr0Out = set.toArray(arr0);
        assertArrayEquals(new String[]{"x"}, arr0Out);
        assertNotSame(arr0, arr0Out);

        String[] arr1 = new String[1];
        String[] arr1Out = set.toArray(arr1);
        assertSame(arr1, arr1Out);
        assertEquals("x", arr1[0]);

        String[] arr2 = new String[]{"a", "b"};
        String[] arr2Out = set.toArray(arr2);
        assertSame(arr2, arr2Out);
        assertEquals("x", arr2[0]);
        assertNull(arr2[1]);
    }

    @Test
    void testAddAndUnsupportedOperations() {
        SingletonSet<String> set = new SingletonSet<>();
        assertTrue(set.add("foo"));
        assertEquals("foo", set.iterator().next());
        assertThrows(UnsupportedOperationException.class, () -> set.add("bar"));
        assertThrows(UnsupportedOperationException.class, () -> set.remove("foo"));
        assertThrows(UnsupportedOperationException.class, () -> set.addAll(Collections.singleton("x")));
        assertThrows(UnsupportedOperationException.class, () -> set.retainAll(Collections.singleton("x")));
        assertThrows(UnsupportedOperationException.class, () -> set.removeAll(Collections.singleton("x")));
        assertThrows(UnsupportedOperationException.class, set::clear);
    }

    @Test
    void testContainsAllVariations() {
        SingletonSet<String> set = new SingletonSet<>("foo");
        Collection<String> singleMatch = Collections.singleton("foo");
        Collection<String> singleNoMatch = Collections.singleton("bar");
        Collection<String> multi = Arrays.asList("foo", "bar");

        assertTrue(set.containsAll(singleMatch));
        assertFalse(set.containsAll(singleNoMatch));
        assertFalse(set.containsAll(multi));
        assertFalse(set.containsAll(Collections.emptyList()));

        SingletonSet<String> empty = new SingletonSet<>();
        assertFalse(empty.containsAll(singleMatch));
    }
}
