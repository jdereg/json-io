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

    // ========== Coverage Tests for data[] unification ==========

    @Test
    void testConstructorClampsCapacityToOne() {
        // Covers line 116: initialCapacity = 1 when < 1
        JsonObject obj = new JsonObject(0);
        obj.put("x", 42);
        assertEquals(42, obj.get("x"));
        assertEquals(1, obj.size());

        JsonObject obj2 = new JsonObject(-5);
        obj2.put("y", 99);
        assertEquals(99, obj2.get("y"));
    }

    @Test
    void testValuesCollectionSize() {
        // Covers line 549: ValuesCollection.size()
        JsonObject obj = new JsonObject();
        obj.put("a", 1);
        obj.put("b", 2);
        assertEquals(2, obj.values().size());
        assertFalse(obj.values().isEmpty());
    }

    @Test
    void testEntrySetIsEmptyAndContains() {
        // Covers lines 591, 602-605: EntrySet.isEmpty(), EntrySet.contains()
        JsonObject obj = new JsonObject();
        assertTrue(obj.entrySet().isEmpty());

        obj.put("name", "John");
        assertFalse(obj.entrySet().isEmpty());

        // Test contains with a matching entry
        Map.Entry<String, String> matchingEntry = new java.util.AbstractMap.SimpleEntry<>("name", "John");
        assertTrue(obj.entrySet().contains(matchingEntry));

        // Test contains with wrong value
        Map.Entry<String, String> wrongValue = new java.util.AbstractMap.SimpleEntry<>("name", "Jane");
        assertFalse(obj.entrySet().contains(wrongValue));

        // Test contains with wrong key
        Map.Entry<String, String> wrongKey = new java.util.AbstractMap.SimpleEntry<>("age", "John");
        assertFalse(obj.entrySet().contains(wrongKey));

        // Test contains with non-Entry object
        assertFalse(obj.entrySet().contains("not-an-entry"));
    }

    @Test
    void testReusableEntryEquals() {
        // Covers line 654: ReusableEntry.equals()
        JsonObject obj = new JsonObject();
        obj.put("alpha", 100);
        obj.put("beta", 200);

        for (Map.Entry<Object, Object> entry : obj.entrySet()) {
            if ("alpha".equals(entry.getKey())) {
                // Test equals against a matching SimpleEntry
                assertTrue(entry.equals(new java.util.AbstractMap.SimpleEntry<>("alpha", 100)));
                // Test equals against a non-matching entry
                assertFalse(entry.equals(new java.util.AbstractMap.SimpleEntry<>("alpha", 999)));
                // Test equals against non-Entry
                assertFalse(entry.equals("not-an-entry"));
            }
        }
    }

    @Test
    void testHashCodeIncludesItemsWhenSet() {
        // Covers line 680: Arrays.hashCode(itemsRef) branch
        JsonObject obj1 = new JsonObject();
        obj1.setItems(new Object[]{"x", "y"});

        JsonObject obj2 = new JsonObject();
        obj2.setItems(new Object[]{"x", "y"});

        // Same items → same hashCode
        assertEquals(obj1.hashCode(), obj2.hashCode());

        // Different items → (very likely) different hashCode
        JsonObject obj3 = new JsonObject();
        obj3.setItems(new Object[]{"a", "b"});
        assertNotEquals(obj1.hashCode(), obj3.hashCode());
    }

    @Test
    void testEqualsWithItemsContent() {
        // Covers line 790: return true after items compare
        JsonObject a = new JsonObject();
        a.setItems(new Object[]{1, 2, 3});

        JsonObject b = new JsonObject();
        b.setItems(new Object[]{1, 2, 3});

        assertTrue(a.equals(b));
        assertEquals(a.hashCode(), b.hashCode());

        // Different items
        JsonObject c = new JsonObject();
        c.setItems(new Object[]{4, 5, 6});
        assertFalse(a.equals(c));
    }

    @Test
    void testAsTwoArraysKeysOnlyThrows() {
        // Covers line 730: @keys without @items throws
        JsonObject obj = new JsonObject();
        obj.setKeys(new Object[]{"k1", "k2"});
        assertThrows(JsonIoException.class, obj::asTwoArrays);
    }

    @Test
    void testRehashMapsUnwrapsKeysAndValues() {
        // Covers lines 745, 780, 782: rehashMaps key/value unwrapping
        java.util.HashMap<Object, Object> target = new java.util.HashMap<>();
        JsonObject mapObj = new JsonObject();
        mapObj.setTarget(target);
        mapObj.setType(java.util.HashMap.class);

        // Create a key that is a JsonObject with a target
        JsonObject keyWrapper = new JsonObject();
        keyWrapper.setTarget("resolvedKey");

        // Create a value that is a JsonObject with a hasValue() pattern
        JsonObject valueWrapper = new JsonObject();
        valueWrapper.setValue("resolvedValue");

        // Create a key that is a JsonObject with hasValue() pattern
        JsonObject keyWithValue = new JsonObject();
        keyWithValue.setValue("keyFromValue");

        mapObj.put(keyWrapper, valueWrapper);
        mapObj.put(keyWithValue, "directValue");

        mapObj.rehashMaps();

        // The target map should have the unwrapped entries
        assertEquals("resolvedValue", target.get("resolvedKey"));
        assertEquals("directValue", target.get("keyFromValue"));
        assertEquals(2, target.size());
    }

    @Test
    void testIndexOfReturnsMinusOneForLargeObjectMiss() {
        // Covers line 440: indexOf returns -1 for POJO linear search miss
        // (with enough entries to stay below INDEX_THRESHOLD)
        JsonObject obj = new JsonObject();
        obj.put("a", 1);
        obj.put("b", 2);
        assertNull(obj.get("nonexistent"));
        assertFalse(obj.containsKey("nonexistent"));
    }
}
