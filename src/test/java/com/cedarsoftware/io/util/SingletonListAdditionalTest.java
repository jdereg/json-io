package com.cedarsoftware.io.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SingletonListAdditionalTest {

    @Test
    void testConstructorsAndBasicQueries() {
        SingletonList<String> withNull = new SingletonList<>(null);
        assertTrue(withNull.isEmpty());
        assertEquals(0, withNull.size());

        SingletonList<String> empty = new SingletonList<>();
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.size());

        SingletonList<String> list = new SingletonList<>("a");
        assertFalse(list.isEmpty());
        assertEquals(1, list.size());
    }

    @Test
    void testContainsAndIterator() {
        SingletonList<String> list = new SingletonList<>("foo");
        assertTrue(list.contains("foo"));
        assertFalse(list.contains("bar"));
        assertFalse(list.contains(null));

        SingletonList<String> empty = new SingletonList<>(null);
        assertFalse(empty.contains(null));
        Iterator<String> it = empty.iterator();
        assertFalse(it.hasNext());

        it = list.iterator();
        assertTrue(it.hasNext());
        assertEquals("foo", it.next());
        assertFalse(it.hasNext());
    }

    @Test
    void testToArrayVariations() {
        SingletonList<String> empty = new SingletonList<>();
        assertArrayEquals(new Object[0], empty.toArray());
        Object[] seed = new Object[]{"x"};
        Object[] result = empty.toArray(seed);
        assertSame(seed, result);
        assertNull(seed[0]);

        SingletonList<String> list = new SingletonList<>("x");
        assertArrayEquals(new Object[]{"x"}, list.toArray());

        String[] arr0 = new String[0];
        String[] arr0Out = list.toArray(arr0);
        assertArrayEquals(new String[]{"x"}, arr0Out);
        assertNotSame(arr0, arr0Out);

        String[] arr1 = new String[1];
        String[] arr1Out = list.toArray(arr1);
        assertSame(arr1, arr1Out);
        assertEquals("x", arr1[0]);

        String[] arr2 = new String[]{"a", "b"};
        String[] arr2Out = list.toArray(arr2);
        assertSame(arr2, arr2Out);
        assertEquals("x", arr2[0]);
        assertNull(arr2[1]);
    }

    @Test
    void testAddAndUnsupportedOperations() {
        SingletonList<String> list = new SingletonList<>();
        assertTrue(list.add("foo"));
        assertEquals("foo", list.get(0));
        assertThrows(UnsupportedOperationException.class, () -> list.add("bar"));
        assertThrows(UnsupportedOperationException.class, () -> list.remove("foo"));
        assertThrows(UnsupportedOperationException.class, () -> list.addAll(Collections.singleton("x")));
        assertThrows(UnsupportedOperationException.class, () -> list.addAll(0, Collections.singleton("x")));
        assertThrows(UnsupportedOperationException.class, () -> list.removeAll(Collections.singleton("x")));
        assertThrows(UnsupportedOperationException.class, () -> list.retainAll(Collections.singleton("x")));
        assertThrows(UnsupportedOperationException.class, list::clear);
    }

    @Test
    void testContainsAllAndGetInvalid() {
        SingletonList<String> list = new SingletonList<>("foo");
        Collection<String> singleMatch = Collections.singleton("foo");
        Collection<String> singleNoMatch = Collections.singleton("bar");
        Collection<String> multi = Arrays.asList("foo", "bar");

        assertTrue(list.containsAll(singleMatch));
        assertFalse(list.containsAll(singleNoMatch));
        assertFalse(list.containsAll(multi));

        assertThrows(IndexOutOfBoundsException.class, () -> list.get(1));
        SingletonList<String> empty = new SingletonList<>();
        assertThrows(IndexOutOfBoundsException.class, () -> empty.get(0));
    }
}
