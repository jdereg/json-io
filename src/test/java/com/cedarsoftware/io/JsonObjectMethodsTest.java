package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.AbstractMap;

import static org.junit.jupiter.api.Assertions.*;

class JsonObjectMethodsTest {

    private static int computeExpectedHash(Object array, Map<Object, Integer> seen) {
        if (array == null) {
            return 1;
        }
        if (!array.getClass().isArray()) {
            return array.hashCode();
        }
        Integer cached = seen.get(array);
        if (cached != null) {
            return cached;
        }
        seen.put(array, null);
        int result = 1;
        for (Object item : (Object[]) array) {
            result = 31 * result + computeExpectedHash(item, seen);
        }
        seen.put(array, result);
        return result;
    }

    private static Map<Object, Object> getJsonStore(JsonObject obj) throws Exception {
        Field f = JsonObject.class.getDeclaredField("jsonStore");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Object, Object> store = (Map<Object, Object>) f.get(obj);
        return store;
    }

    @Test
    void testTypeStringGetterSetter() {
        JsonObject obj = new JsonObject();
        assertNull(obj.getTypeString());
        obj.setTypeString("testType");
        assertEquals("testType", obj.getTypeString());
        obj.setTypeString(null);
        assertNull(obj.getTypeString());
    }

    @Test
    void testGetLengthDelegatesToSize() {
        JsonObject withItems = new JsonObject();
        withItems.setItems(new Object[]{1, 2, 3});
        assertEquals(3, withItems.getLength());

        JsonObject withMap = new JsonObject();
        withMap.put("a", 1);
        withMap.put("b", 2);
        assertEquals(2, withMap.getLength());
    }

    @Test
    void testHashCodeWithKeysItemsAndMap() throws Exception {
        JsonObject obj = new JsonObject();
        Object[] keys = {"k1", "k2"};
        Object[] items = {10, 20};
        obj.setKeys(keys);
        obj.setItems(items);
        obj.put("extra", 99);

        int expected = 1;
        expected = 31 * expected + computeExpectedHash(keys, new IdentityHashMap<>());
        expected = 31 * expected + computeExpectedHash(items, new IdentityHashMap<>());
        expected = 31 * expected + getJsonStore(obj).hashCode();

        assertEquals(expected, obj.hashCode());
        assertEquals(expected, obj.hashCode());
    }

    @Test
    void testHashCodeWithOnlyKeys() throws Exception {
        JsonObject obj = new JsonObject();
        Object[] keys = {"a", "b"};
        obj.setKeys(keys);

        int expected = 1;
        expected = 31 * expected + computeExpectedHash(keys, new IdentityHashMap<>());

        assertEquals(expected, obj.hashCode());
    }

    @Test
    void testHashCodeWithOnlyItems() throws Exception {
        JsonObject obj = new JsonObject();
        Object[] items = {"x", "y"};
        obj.setItems(items);

        int expected = 1;
        expected = 31 * expected + computeExpectedHash(items, new IdentityHashMap<>());

        assertEquals(expected, obj.hashCode());
    }

    @Test
    void testHashCodeWithOnlyMap() throws Exception {
        JsonObject obj = new JsonObject();
        obj.put("a", 1);
        obj.put("b", 2);

        int expected = 1;
        expected = 31 * expected + getJsonStore(obj).hashCode();

        assertEquals(expected, obj.hashCode());
    }

    @Test
    void testPrivateHashCodeNullNonArrayCachedAndRecursive() throws Exception {
        JsonObject obj = new JsonObject();
        Method m = JsonObject.class.getDeclaredMethod("hashCode", Object.class, Map.class);
        m.setAccessible(true);

        Map<Object, Integer> seen = new IdentityHashMap<>();
        assertEquals(1, (int) m.invoke(obj, null, seen));
        assertEquals("abc".hashCode(), (int) m.invoke(obj, "abc", seen));

        Object[] inner = {"x"};
        int first = (int) m.invoke(obj, inner, seen);
        assertEquals(first, (int) m.invoke(obj, inner, seen));

        Object[] outer = {inner, new Object[]{1, 2}};
        int expected = computeExpectedHash(outer, new IdentityHashMap<>());
        assertEquals(expected, (int) m.invoke(obj, outer, new IdentityHashMap<>()));
    }

    @Test
    void testEqualsVariousPaths() {
        JsonObject obj = new JsonObject();
        assertTrue(obj.equals(obj));
        assertFalse(obj.equals("notJsonObject"));

        JsonObject one = new JsonObject();
        JsonObject two = new JsonObject();
        one.setItems(new Object[]{1});
        two.setItems(new Object[]{2});
        assertFalse(one.equals(two));

        two.setItems(new Object[]{1});
        one.setKeys(new Object[]{"k1"});
        two.setKeys(new Object[]{"k2"});
        assertFalse(one.equals(two));

        two.setKeys(new Object[]{"k1"});
        one.put("a", 1);
        two.put("a", 2);
        assertFalse(one.equals(two));

        two.put("a", 1);
        assertTrue(one.equals(two));
    }

    @Test
    void testShallowArrayEquals() throws Exception {
        Method m = JsonObject.class.getDeclaredMethod("shallowArrayEquals", Object[].class, Object[].class);
        m.setAccessible(true);

        Object[] array = {"a", null};
        assertTrue((boolean) m.invoke(null, array, array));
        assertTrue((boolean) m.invoke(null, null, null));
        assertFalse((boolean) m.invoke(null, array, null));
        assertFalse((boolean) m.invoke(null, new Object[]{"a"}, new Object[]{"a", "b"}));
        assertFalse((boolean) m.invoke(null, new Object[]{"a"}, new Object[]{"b"}));
        assertTrue((boolean) m.invoke(null, new Object[]{"a", null}, new Object[]{"a", null}));
    }

    @Test
    void testSetItemsNullThrows() {
        JsonObject obj = new JsonObject();
        JsonIoException ex = assertThrows(JsonIoException.class, () -> obj.setItems(null));
        assertTrue(ex.getMessage().toLowerCase().contains("cannot be null"));
    }

    @Test
    void testSetKeysNullThrows() {
        JsonObject obj = new JsonObject();
        JsonIoException ex = assertThrows(JsonIoException.class, () -> obj.setKeys(null));
        assertTrue(ex.getMessage().toLowerCase().contains("cannot be null"));
    }

    @Test
    void testContainsKeyUsesArraysAndDelegates() {
        JsonObject obj = new JsonObject();
        obj.setKeys(new Object[]{"a", "b"});
        assertTrue(obj.containsKey("a"));
        assertFalse(obj.containsKey("c"));

        JsonObject delegated = new JsonObject();
        delegated.put("x", 1);
        assertTrue(delegated.containsKey("x"));
        assertFalse(delegated.containsKey("y"));
    }

    @Test
    void testContainsValueUsesArraysAndDelegates() {
        JsonObject obj = new JsonObject();
        obj.setItems(new Object[]{1, 2});
        assertTrue(obj.containsValue(2));
        assertFalse(obj.containsValue(3));

        JsonObject delegated = new JsonObject();
        delegated.put("k", 5);
        assertTrue(delegated.containsValue(5));
        assertFalse(delegated.containsValue(6));
    }

    @Test
    void testGetKeyFromArraysAndDelegates() {
        JsonObject obj = new JsonObject();
        obj.setKeys(new Object[]{"a"});
        obj.setItems(new Object[]{10});
        assertEquals(10, obj.get("a"));
        assertNull(obj.get("b"));

        JsonObject delegated = new JsonObject();
        delegated.put("x", 42);
        assertEquals(42, delegated.get("x"));
    }

    @Test
    void testKeySetAndValues() {
        JsonObject obj = new JsonObject();
        Object[] keys = {"k1", "k2"};
        Object[] items = {1, 2};
        obj.setKeys(keys);
        obj.setItems(items);
        assertEquals(new LinkedHashSet<>(Arrays.asList(keys)), obj.keySet());
        assertEquals(new LinkedHashSet<>(Arrays.asList(items)), new LinkedHashSet<>(obj.values()));

        JsonObject delegated = new JsonObject();
        delegated.put("x", 1);
        assertTrue(delegated.keySet().contains("x"));
        assertTrue(delegated.values().contains(1));
    }

    @Test
    void testEntrySetFromArraysAndDelegate() {
        JsonObject obj = new JsonObject();
        obj.setKeys(new Object[]{"a"});
        obj.setItems(new Object[]{1});
        Set<Map.Entry<Object, Object>> entries = obj.entrySet();
        Map.Entry<Object, Object> entry = entries.iterator().next();
        assertEquals("a", entry.getKey());
        assertEquals(1, entry.getValue());
        assertEquals(1, entry.setValue(5));
        assertEquals(5, entry.getValue());
        assertEquals(5, obj.getItems()[0]);
        assertTrue(entry.equals(new AbstractMap.SimpleEntry<>("a", 5)));

        JsonObject delegated = new JsonObject();
        delegated.put("k", 10);
        Set<Map.Entry<Object, Object>> delegatedEntries = delegated.entrySet();
        Map.Entry<Object, Object> delegatedEntry = delegatedEntries.iterator().next();
        assertEquals("k", delegatedEntry.getKey());
        assertEquals(10, delegatedEntry.getValue());
    }
}

