package com.cedarsoftware.io.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SingletonListTest {

    @Test
    void testConstructorAndBasicAccess() {
        SingletonList<String> list = new SingletonList<>("foo");
        assertEquals(1, list.size());
        assertFalse(list.isEmpty());
        assertTrue(list.contains("foo"));
        assertFalse(list.contains("bar"));
        assertFalse(list.contains(null));
        assertEquals("foo", list.get(0));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(1));

        Object[] arr = list.toArray();
        assertArrayEquals(new Object[]{"foo"}, arr);

        String[] zeroInput = new String[0];
        String[] zero = list.toArray(zeroInput);
        assertArrayEquals(new String[]{"foo"}, zero);
        assertNotSame(zeroInput, zero); // ensure new array created when length 0

        String[] one = new String[]{""};
        assertSame(one, list.toArray(one));
        assertEquals("foo", one[0]);

        String[] two = new String[]{"", ""};
        assertSame(two, list.toArray(two));
        assertEquals("foo", two[0]);
        assertNull(two[1]);

        assertThrows(NullPointerException.class, () -> list.toArray(null));

        Iterator<String> it = list.iterator();
        assertTrue(it.hasNext());
        assertEquals("foo", it.next());
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);

        assertThrows(UnsupportedOperationException.class, () -> list.add("bar"));
        assertThrows(UnsupportedOperationException.class, () -> list.remove("foo"));
        assertTrue(list.containsAll(Collections.singleton("foo")));
        assertFalse(list.containsAll(Collections.singleton("bar")));
        assertFalse(list.containsAll(Arrays.asList("foo", "bar")));
        assertThrows(UnsupportedOperationException.class, () -> list.addAll(Collections.singleton("x")));
        assertThrows(UnsupportedOperationException.class, () -> list.addAll(0, Collections.singleton("x")));
        assertThrows(UnsupportedOperationException.class, () -> list.removeAll(Collections.singleton("x")));
        assertThrows(UnsupportedOperationException.class, () -> list.retainAll(Collections.singleton("x")));
        assertThrows(UnsupportedOperationException.class, list::clear);
    }

    @Test
    void testUninitializedListBehavior() {
        SingletonList<String> list = new SingletonList<>();
        assertEquals(0, list.size());
        assertTrue(list.isEmpty());
        assertFalse(list.contains("x"));

        Object[] arr = list.toArray();
        assertEquals(0, arr.length);

        String[] zero = new String[0];
        assertSame(zero, list.toArray(zero));
        assertEquals(0, zero.length);

        String[] one = new String[]{"init"};
        assertSame(one, list.toArray(one));
        assertNull(one[0]);

        Iterator<String> it = list.iterator();
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);

        assertFalse(list.containsAll(Collections.singleton("foo")));
        assertFalse(list.containsAll(Arrays.asList("foo", "bar")));

        assertTrue(list.add("first"));
        assertEquals(1, list.size());
        assertEquals("first", list.get(0));
        assertThrows(UnsupportedOperationException.class, () -> list.add("second"));
    }

    @Test
    void testNullConstructorActsUninitialized() {
        SingletonList<String> list = new SingletonList<>(null);
        assertTrue(list.isEmpty());
        assertEquals(0, list.size());
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(0));
    }

    @Test
    void testUnsupportedOperations() {
        SingletonList<String> list = new SingletonList<>("foo");
        Collection<String> c = Collections.singleton("bar");
        assertThrows(UnsupportedOperationException.class, () -> list.addAll(c));
        assertThrows(UnsupportedOperationException.class, () -> list.addAll(0, c));
        assertThrows(UnsupportedOperationException.class, () -> list.removeAll(c));
        assertThrows(UnsupportedOperationException.class, () -> list.retainAll(c));
        assertThrows(UnsupportedOperationException.class, list::clear);
    }
}
