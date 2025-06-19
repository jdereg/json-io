package com.cedarsoftware.io.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SingletonListTest {

    @Test
    void testSetInvalidIndex() {
        SingletonList<String> list = new SingletonList<>("a");
        assertThrows(IndexOutOfBoundsException.class, () -> list.set(1, "b"));
    }

    @Test
    void testSetOnUninitialized() {
        SingletonList<String> list = new SingletonList<>();
        list.set(0, "a");
        assertEquals(1, list.size());
        assertEquals("a", list.get(0));
    }

    @Test
    void testSetOnInitialized() {
        SingletonList<String> list = new SingletonList<>("a");
        String old = list.set(0, "b");
        assertEquals("a", old);
        assertEquals("b", list.get(0));
    }

    @Test
    void testAddIndexUnsupported() {
        SingletonList<String> list = new SingletonList<>();
        assertThrows(UnsupportedOperationException.class, () -> list.add(0, "x"));
    }

    @Test
    void testRemoveIndexUnsupported() {
        SingletonList<String> list = new SingletonList<>("x");
        assertThrows(UnsupportedOperationException.class, () -> list.remove(0));
    }

    @Test
    void testIndexOfScenarios() {
        SingletonList<String> empty = new SingletonList<>();
        assertEquals(-1, empty.indexOf("a"));
        assertEquals(-1, empty.indexOf(null));

        SingletonList<String> list = new SingletonList<>("foo");
        assertEquals(0, list.indexOf("foo"));
        assertEquals(-1, list.indexOf("bar"));
        assertEquals(-1, list.indexOf(null));

        SingletonList<String> nullList = new SingletonList<>();
        nullList.set(0, null);
        assertThrows(NullPointerException.class, () -> nullList.indexOf(null));
    }

    @Test
    void testLastIndexOfDelegates() {
        SingletonList<String> list = new SingletonList<>("x");
        assertEquals(list.indexOf("x"), list.lastIndexOf("x"));

        SingletonList<String> empty = new SingletonList<>();
        assertEquals(empty.indexOf("y"), empty.lastIndexOf("y"));
    }

    @Test
    void testListIteratorUninitialized() {
        SingletonList<String> list = new SingletonList<>();
        ListIterator<String> it = list.listIterator();
        assertFalse(it.hasNext());
        assertFalse(it.hasPrevious());
        assertEquals(0, it.nextIndex());
        assertEquals(-1, it.previousIndex());
        assertThrows(NoSuchElementException.class, it::next);
        assertThrows(NoSuchElementException.class, it::previous);
        assertThrows(IllegalStateException.class, () -> it.set("x"));
        assertThrows(UnsupportedOperationException.class, it::remove);
        assertThrows(UnsupportedOperationException.class, () -> it.add("y"));
    }

    @Test
    void testListIteratorInitialized() {
        SingletonList<String> list = new SingletonList<>("foo");
        ListIterator<String> it = list.listIterator();
        assertTrue(it.hasNext());
        assertEquals("foo", it.next());
        assertFalse(it.hasNext());
        assertTrue(it.hasPrevious());
        assertEquals("foo", it.previous());
        assertTrue(it.hasNext());
        assertEquals(0, it.nextIndex());
        it.next();
        it.set("bar");
        assertEquals("bar", list.get(0));
        assertThrows(UnsupportedOperationException.class, it::remove);
        assertThrows(UnsupportedOperationException.class, () -> it.add("baz"));
    }

    @Test
    void testListIteratorIndexValues() {
        SingletonList<String> list = new SingletonList<>("x");
        assertThrows(IndexOutOfBoundsException.class, () -> list.listIterator(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> list.listIterator(2));

        ListIterator<String> it = list.listIterator(1);
        assertFalse(it.hasNext());
        assertTrue(it.hasPrevious());
        assertEquals("x", it.previous());

        SingletonList<String> empty = new SingletonList<>();
        assertThrows(IndexOutOfBoundsException.class, () -> empty.listIterator(1));
    }

    @Test
    void testSubListVariations() {
        SingletonList<String> empty = new SingletonList<>();
        assertTrue(empty.subList(0, 0).isEmpty());
        assertTrue(empty.subList(0, 1).isEmpty());

        SingletonList<String> list = new SingletonList<>("foo");
        assertTrue(list.subList(0, 0).isEmpty());
        assertEquals(Collections.singletonList("foo"), list.subList(0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> list.subList(1, 1));
    }

    @Test
    void testEqualsAndHashCode() {
        SingletonList<String> list = new SingletonList<>("x");
        assertTrue(list.equals(list));
        assertFalse(list.equals("str"));
        assertFalse(list.equals(Arrays.asList("x", "y")));
        assertFalse(list.equals(Collections.singletonList("y")));
        assertTrue(list.equals(Collections.singletonList("x")));
        assertFalse(new SingletonList<>().equals(Collections.emptyList()));

        SingletonList<String> nullList = new SingletonList<>();
        nullList.set(0, null);
        List<String> otherNull = Collections.singletonList(null);
        assertThrows(NullPointerException.class, () -> nullList.equals(otherNull));

        assertEquals(Arrays.hashCode(new Object[]{"x"}), list.hashCode());
        assertEquals(1, new SingletonList<>().hashCode());
        assertEquals(Arrays.hashCode(new Object[]{null}), nullList.hashCode());
    }
}
