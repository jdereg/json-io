package com.cedarsoftware.io.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
class UnmodifiableSetTest {

    private UnmodifiableSet<Integer> set;

    @BeforeEach
    void setUp() {
        set = new UnmodifiableSet<>();
        set.add(10);
        set.add(20);
    }

    @Test
    void testAdd() {
        assertTrue(set.add(30));
        assertTrue(set.contains(30));
    }

    @Test
    void testRemove() {
        assertTrue(set.remove(20));
        assertFalse(set.contains(20));
    }

    @Test
    void testAddWhenSealed() {
        set.seal();
        assertThrows(UnsupportedOperationException.class, () -> set.add(40));
    }

    @Test
    void testRemoveWhenSealed() {
        set.seal();
        assertThrows(UnsupportedOperationException.class, () -> set.remove(10));
    }

    @Test
    void testIteratorRemoveWhenSealed() {
        Iterator<Integer> iterator = set.iterator();
        set.seal();
        iterator.next(); // Move to first element
        assertThrows(UnsupportedOperationException.class, iterator::remove);
    }

    @Test
    void testClearWhenSealed() {
        set.seal();
        assertThrows(UnsupportedOperationException.class, set::clear);
    }

    @Test
    void testIterator() {
        Iterator<Integer> iterator = set.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(Integer.valueOf(10), iterator.next());
        assertEquals(Integer.valueOf(20), iterator.next());
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void testRootSealStateHonored() {
        Iterator<Integer> iterator = set.iterator();
        assertEquals(Integer.valueOf(10), iterator.next());
        set.seal();
        assertThrows(UnsupportedOperationException.class, () -> iterator.remove());
        set.unseal();
        iterator.remove();
        assertEquals(set.size(), 1);
    }

    @Test
    void testContainsAll() {
        assertTrue(set.containsAll(Arrays.asList(10, 20)));
        assertFalse(set.containsAll(Arrays.asList(10, 30)));
    }

    @Test
    void testRetainAll() {
        set.retainAll(Arrays.asList(10));
        assertTrue(set.contains(10));
        assertFalse(set.contains(20));
    }

    @Test
    void testRetainAllWhenSealed() {
        set.seal();
        assertThrows(UnsupportedOperationException.class, () -> set.retainAll(Arrays.asList(10)));
    }

    @Test
    void testAddAll() {
        set.addAll(Arrays.asList(30, 40));
        assertTrue(set.containsAll(Arrays.asList(30, 40)));
    }

    @Test
    void testAddAllWhenSealed() {
        set.seal();
        assertThrows(UnsupportedOperationException.class, () -> set.addAll(Arrays.asList(30, 40)));
    }

    @Test
    void testRemoveAll() {
        set.removeAll(Arrays.asList(10, 20));
        assertTrue(set.isEmpty());
    }

    @Test
    void testRemoveAllWhenSealed() {
        set.seal();
        assertThrows(UnsupportedOperationException.class, () -> set.removeAll(Arrays.asList(10, 20)));
    }

    @Test
    void testSize() {
        assertEquals(2, set.size());
    }

    @Test
    void testIsEmpty() {
        assertFalse(set.isEmpty());
        set.clear();
        assertTrue(set.isEmpty());
    }

    @Test
    void testToArray() {
        Object[] arr = set.toArray();
        assertArrayEquals(new Object[]{10, 20}, arr);
    }

    @Test
    void testToArrayGenerics() {
        Integer[] arr = set.toArray(new Integer[0]);
        assertArrayEquals(new Integer[]{10, 20}, arr);
    }

    @Test
    void testEquals() {
        UnmodifiableSet<Integer> other = new UnmodifiableSet<>();
        other.add(10);
        other.add(20);
        assertEquals(set, other);
        other.add(30);
        assertNotEquals(set, other);
    }

    @Test
    void testHashCode() {
        int expectedHashCode = set.hashCode();
        set.add(30);
        assertNotEquals(expectedHashCode, set.hashCode());
    }
}
