package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JsonObject public API methods.
 * Tests the parallel-array based storage implementation.
 *
 * IMPORTANT: JsonObject has two SEPARATE storage mechanisms:
 * 1. Map interface (put/get/containsKey) - uses mapKeys/mapValues arrays
 * 2. Items/Keys arrays (setItems/getItems/setKeys/getKeys) - uses itemsArray/keysArray
 *
 * These are independent! setItems does NOT populate the Map interface.
 * This separation exists because:
 * - Regular JSON objects use the Map interface: {"name": "Joe"} -> put("name", "Joe")
 * - @items format uses items array: {"@items": [1,2,3]} -> setItems([1,2,3])
 * - @keys/@items format uses both: {"@keys": ["a"], "@items": [1]} -> setKeys/setItems
 */
class JsonObjectMethodsTest {

    // ========== Map Interface Tests ==========

    @Test
    void testMapPutAndGet() {
        JsonObject obj = new JsonObject();
        obj.put("name", "John");
        obj.put("age", 30);

        assertEquals("John", obj.get("name"));
        assertEquals(30, obj.get("age"));
        assertNull(obj.get("nonexistent"));
    }

    @Test
    void testMapSizeAndEmpty() {
        JsonObject obj = new JsonObject();
        assertTrue(obj.isEmpty());
        assertEquals(0, obj.size());

        obj.put("a", 1);
        assertFalse(obj.isEmpty());
        assertEquals(1, obj.size());

        obj.put("b", 2);
        assertEquals(2, obj.size());
    }

    @Test
    void testMapContainsKey() {
        JsonObject obj = new JsonObject();
        obj.put("x", 1);

        assertTrue(obj.containsKey("x"));
        assertFalse(obj.containsKey("y"));
    }

    @Test
    void testMapContainsValue() {
        JsonObject obj = new JsonObject();
        obj.put("k", 5);

        assertTrue(obj.containsValue(5));
        assertFalse(obj.containsValue(6));
    }

    @Test
    void testMapRemove() {
        JsonObject obj = new JsonObject();
        obj.put("a", 1);
        obj.put("b", 2);
        obj.put("c", 3);

        assertEquals(2, obj.remove("b"));
        assertEquals(2, obj.size());
        assertNull(obj.get("b"));
        assertEquals(1, obj.get("a"));
        assertEquals(3, obj.get("c"));
    }

    @Test
    void testMapClear() {
        JsonObject obj = new JsonObject();
        obj.put("a", 1);
        obj.put("b", 2);
        obj.clear();

        assertEquals(0, obj.size());
        assertTrue(obj.isEmpty());
        assertNull(obj.get("a"));
    }

    @Test
    void testMapPutAll() {
        JsonObject obj = new JsonObject();
        obj.put("existing", 0);

        java.util.Map<String, Integer> toAdd = new java.util.LinkedHashMap<>();
        toAdd.put("a", 1);
        toAdd.put("b", 2);

        obj.putAll(toAdd);
        assertEquals(3, obj.size());
        assertEquals(0, obj.get("existing"));
        assertEquals(1, obj.get("a"));
        assertEquals(2, obj.get("b"));
    }

    @Test
    void testMapKeySet() {
        JsonObject obj = new JsonObject();
        obj.put("x", 1);
        obj.put("y", 2);

        Set<Object> keys = obj.keySet();
        assertEquals(2, keys.size());
        assertTrue(keys.contains("x"));
        assertTrue(keys.contains("y"));
    }

    @Test
    void testMapValues() {
        JsonObject obj = new JsonObject();
        obj.put("x", 1);
        obj.put("y", 2);

        assertTrue(obj.values().contains(1));
        assertTrue(obj.values().contains(2));
    }

    @Test
    void testMapEntrySet() {
        JsonObject obj = new JsonObject();
        obj.put("k", 10);

        Set<Map.Entry<Object, Object>> entries = obj.entrySet();
        assertEquals(1, entries.size());

        Map.Entry<Object, Object> entry = entries.iterator().next();
        assertEquals("k", entry.getKey());
        assertEquals(10, entry.getValue());
    }

    @Test
    void testInsertionOrderMaintained() {
        JsonObject obj = new JsonObject();
        obj.put("z", 1);
        obj.put("a", 2);
        obj.put("m", 3);

        Object[] expectedKeys = {"z", "a", "m"};
        Object[] actualKeys = obj.keySet().toArray();
        assertArrayEquals(expectedKeys, actualKeys);
    }

    // ========== Items/Keys Array Tests (separate from Map interface) ==========

    @Test
    void testSetGetItems() {
        JsonObject obj = new JsonObject();
        Object[] items = {1, 2, 3};
        obj.setItems(items);

        // getItems should return what was set
        assertSame(items, obj.getItems());

        // But Map interface should be unaffected
        assertEquals(0, obj.size());
        assertTrue(obj.isEmpty());
    }

    @Test
    void testSetGetKeys() {
        JsonObject obj = new JsonObject();
        Object[] keys = {"a", "b"};
        obj.setKeys(keys);

        // getKeys should return what was set
        assertSame(keys, obj.getKeys());

        // But Map interface should be unaffected
        assertEquals(0, obj.size());
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
    void testItemsNullByDefault() {
        JsonObject obj = new JsonObject();
        assertNull(obj.getItems());
    }

    @Test
    void testKeysNullByDefault() {
        JsonObject obj = new JsonObject();
        assertNull(obj.getKeys());
    }

    @Test
    void testItemsAndMapAreIndependent() {
        JsonObject obj = new JsonObject();

        // Set items
        Object[] items = {"x", "y"};
        obj.setItems(items);

        // Set map entries
        obj.put("name", "test");

        // Both should be independent
        assertSame(items, obj.getItems());
        assertEquals("test", obj.get("name"));
        assertEquals(1, obj.size());  // Map size, not items length
    }

    // ========== Type String Tests ==========

    @Test
    void testTypeStringGetterSetter() {
        JsonObject obj = new JsonObject();
        assertNull(obj.getTypeString());
        obj.setTypeString("testType");
        assertEquals("testType", obj.getTypeString());
        obj.setTypeString(null);
        assertNull(obj.getTypeString());
    }

    // ========== Hash and Equals Tests ==========

    @Test
    void testHashCodeStability() {
        JsonObject obj = new JsonObject();
        obj.put("a", 1);
        obj.put("b", 2);

        int first = obj.hashCode();
        assertEquals(first, obj.hashCode());
    }

    @Test
    void testHashCodeHandlesArraysAndNulls() {
        JsonObject obj = new JsonObject();
        obj.put("array", new Object[]{"a", "b"});
        obj.put("null", null);

        // Should not throw
        int hash = obj.hashCode();
        assertEquals(hash, obj.hashCode());
    }

    @Test
    void testEqualsBasic() {
        JsonObject obj = new JsonObject();
        assertTrue(obj.equals(obj));
        assertFalse(obj.equals("notJsonObject"));
        assertFalse(obj.equals(null));
    }

    @Test
    void testEqualsMapContent() {
        JsonObject one = new JsonObject();
        JsonObject two = new JsonObject();

        one.put("a", 1);
        two.put("a", 1);
        assertTrue(one.equals(two));

        two.put("a", 2);
        assertFalse(one.equals(two));
    }

    // ========== Deprecated Method Tests ==========

    @Test
    @SuppressWarnings("deprecation")
    void testGetLengthDelegatesToSize() {
        JsonObject obj = new JsonObject();
        obj.put("a", 1);
        obj.put("b", 2);
        assertEquals(2, obj.getLength());
    }
}
