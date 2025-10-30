package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import com.cedarsoftware.util.DeepEquals;
import com.cedarsoftware.util.MultiKeyMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for MultiKeyMap serialization and deserialization with json-io.
 *
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
public class MultiKeyMapTest {
    private static final Logger LOG = Logger.getLogger(MultiKeyMapTest.class.getName());

    @Test
    void testMultiKeyMapDefaultConfig() {
        // Test with default configuration
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();
        map.put("singleKey", "value1");
        map.putMultiKey("value2", "key1", "key2");
        map.putMultiKey("value3", "key1", "key2", "key3");

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        Map<String, Object> options = new HashMap<>();
        boolean equals = DeepEquals.deepEquals(map, deserializedMap, options);
        if (!equals) {
            LOG.fine(json);
            LOG.fine(options.get("diff").toString());
        }
        assertTrue(equals);

        // Verify values can be retrieved
        assertEquals("value1", deserializedMap.get("singleKey"));
        assertEquals("value2", deserializedMap.getMultiKey("key1", "key2"));
        assertEquals("value3", deserializedMap.getMultiKey("key1", "key2", "key3"));
    }

    @Test
    void testMultiKeyMapCustomConfig() {
        // Test with custom configuration
        MultiKeyMap<String> map = MultiKeyMap.<String>builder()
                .capacity(1024)
                .loadFactor(0.8f)
                .simpleKeysMode(true)
                .caseSensitive(false)
                .valueBasedEquality(false)
                .build();

        map.put("KEY1", "value1");
        map.put("key2", "value2");

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        // Verify configuration was preserved
        assertTrue(deserializedMap.getSimpleKeysMode());
        assertEquals(false, deserializedMap.getCaseSensitive());

        // Verify case-insensitive matching works
        assertEquals("value1", deserializedMap.get("key1"));  // Should match "KEY1"
        assertEquals("value2", deserializedMap.get("KEY2"));  // Should match "key2"
    }

    @Test
    void testMultiKeyMapWithComplexKeys() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();

        // Test with simple multi-key entries
        map.putMultiKey("value1", "a", "b", "c");
        map.putMultiKey("value2", "x", "y", "z");
        map.putMultiKey("value3", 1, 2, 3);

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        // Verify all values can be retrieved
        assertEquals("value1", deserializedMap.getMultiKey("a", "b", "c"));
        assertEquals("value2", deserializedMap.getMultiKey("x", "y", "z"));
        assertEquals("value3", deserializedMap.getMultiKey(1, 2, 3));

        // Verify size matches
        assertEquals(map.size(), deserializedMap.size());
    }

    @Test
    void testMultiKeyMapWithNullValues() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();

        map.put("nullValue", null);           // String key with null value
        map.putMultiKey(null, "key1", "key2"); // Multi-key with null value
        map.put(null, "nullKey");              // Null key with String value

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        // Verify null values preserved
        assertTrue(deserializedMap.containsKey("nullValue"));
        assertEquals(null, deserializedMap.get("nullValue"));

        // Verify null value in multi-key
        assertEquals(null, deserializedMap.getMultiKey("key1", "key2"));

        // Verify null key with non-null value
        assertEquals("nullKey", deserializedMap.get(null));

        // Verify size matches
        assertEquals(3, map.size());
        assertEquals(map.size(), deserializedMap.size());
    }

    @Test
    void testMultiKeyMapEmpty() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        assertTrue(deserializedMap.isEmpty());
        assertEquals(0, deserializedMap.size());
    }

    @Test
    void testMultiKeyMapWithNumericKeys() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder()
                .valueBasedEquality(true)
                .build();

        // With value-based equality, 1 (int) should match 1.0 (double)
        map.putMultiKey("value1", 1, "suffix");
        map.putMultiKey("value2", 42L, 3.14);

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        // Verify value-based numeric matching works
        assertEquals("value1", deserializedMap.getMultiKey(1.0, "suffix"));  // double 1.0 matches int 1
        assertEquals("value2", deserializedMap.getMultiKey(42, 3.14));
    }

    @Test
    void testMultiKeyMapRoundTrip() {
        // Test that serializing → deserializing → serializing produces same JSON
        MultiKeyMap<String> map = MultiKeyMap.<String>builder()
                .capacity(256)
                .loadFactor(0.75f)
                .simpleKeysMode(false)
                .caseSensitive(true)
                .build();

        map.put("key1", "value1");
        map.putMultiKey("value2", "a", "b");
        map.putMultiKey("value3", 1, 2, 3);

        String json1 = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json1, null).asType(new TypeHolder<MultiKeyMap<String>>(){});
        String json2 = JsonIo.toJson(deserializedMap, null);

        // The JSON should be identical (or at least functionally equivalent)
        MultiKeyMap<String> deserializedMap2 = JsonIo.toJava(json2, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        Map<String, Object> options = new HashMap<>();
        boolean equals = DeepEquals.deepEquals(map, deserializedMap2, options);
        if (!equals) {
            LOG.fine("JSON1: " + json1);
            LOG.fine("JSON2: " + json2);
            LOG.fine(options.get("diff").toString());
        }
        assertTrue(equals);
    }

    @Test
    void testMultiKeyMapWithCollectionKeyMode() {
        // Test COLLECTIONS_EXPANDED mode (default)
        MultiKeyMap<String> map1 = MultiKeyMap.<String>builder()
                .collectionKeyMode(MultiKeyMap.CollectionKeyMode.COLLECTIONS_EXPANDED)
                .build();

        map1.put(Arrays.asList("a", "b"), "expanded");

        String json1 = JsonIo.toJson(map1, null);
        MultiKeyMap<String> deserialized1 = JsonIo.toJava(json1, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        assertEquals("expanded", deserialized1.get(Arrays.asList("a", "b")));
        assertEquals(MultiKeyMap.CollectionKeyMode.COLLECTIONS_EXPANDED, deserialized1.getCollectionKeyMode());

        // Test COLLECTIONS_NOT_EXPANDED mode
        MultiKeyMap<String> map2 = MultiKeyMap.<String>builder()
                .collectionKeyMode(MultiKeyMap.CollectionKeyMode.COLLECTIONS_NOT_EXPANDED)
                .build();

        map2.put(Arrays.asList("a", "b"), "notExpanded");

        String json2 = JsonIo.toJson(map2, null);
        MultiKeyMap<String> deserialized2 = JsonIo.toJava(json2, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        assertEquals("notExpanded", deserialized2.get(Arrays.asList("a", "b")));
        assertEquals(MultiKeyMap.CollectionKeyMode.COLLECTIONS_NOT_EXPANDED, deserialized2.getCollectionKeyMode());
    }

    @Test
    void testMultiKeyMapWithFlattenDimensions() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder()
                .flattenDimensions(true)
                .build();

        Object[][] nested = {{"a", "b"}, {"c", "d"}};
        map.put(nested, "flattenedValue");

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        assertTrue(deserializedMap.getFlattenDimensions());
        assertEquals("flattenedValue", deserializedMap.get(nested));
    }

    @Test
    void testMultiKeyMapWithSetKeys() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();

        // Test with Set as a key component
        Set<String> set1 = new HashSet<>(Arrays.asList("a", "b", "c"));
        map.put(set1, "setValue1");

        // Test with Set as part of multi-key
        Set<Integer> set2 = new HashSet<>(Arrays.asList(1, 2, 3));
        map.putMultiKey("setValue2", set2, "suffix");

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        // Verify sets can be retrieved (order doesn't matter for sets)
        Set<String> lookupSet1 = new HashSet<>(Arrays.asList("c", "a", "b")); // Different order
        assertEquals("setValue1", deserializedMap.get(lookupSet1));

        Set<Integer> lookupSet2 = new HashSet<>(Arrays.asList(3, 1, 2)); // Different order
        assertEquals("setValue2", deserializedMap.getMultiKey(lookupSet2, "suffix"));

        assertEquals(map.size(), deserializedMap.size());
    }

    @Test
    void testMultiKeyMapWithListKeys() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();

        // Test with List as a key component
        List<String> list1 = Arrays.asList("a", "b", "c");
        map.put(list1, "listValue1");

        // Test with List as part of multi-key
        List<Integer> list2 = Arrays.asList(1, 2, 3);
        map.putMultiKey("listValue2", list2, "suffix");

        String json = JsonIo.toJson(map, null);

        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        // Verify lists can be retrieved (order matters for lists)
        assertEquals("listValue1", deserializedMap.get(Arrays.asList("a", "b", "c")));
        assertEquals("listValue2", deserializedMap.getMultiKey(Arrays.asList(1, 2, 3), "suffix"));

        // Different order should NOT match for lists
        assertEquals(null, deserializedMap.get(Arrays.asList("c", "a", "b")));

        assertEquals(map.size(), deserializedMap.size());
    }

    @Test
    void testMultiKeyMapWithMixedSetAndListKeys() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();

        // Test with mixed Set and List keys
        Set<String> set = new HashSet<>(Arrays.asList("x", "y"));
        List<String> list = Arrays.asList("a", "b");
        map.putMultiKey("mixedValue1", set, list);

        // Test with Set, Array, and List
        Object[] array = new Object[]{"p", "q"};
        map.putMultiKey("mixedValue2", set, array, list);

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        // Verify mixed keys can be retrieved
        Set<String> lookupSet = new HashSet<>(Arrays.asList("y", "x")); // Different order
        assertEquals("mixedValue1", deserializedMap.getMultiKey(lookupSet, Arrays.asList("a", "b")));

        assertEquals("mixedValue2", deserializedMap.getMultiKey(lookupSet, new Object[]{"p", "q"}, Arrays.asList("a", "b")));

        assertEquals(map.size(), deserializedMap.size());
    }

    @Test
    void testMultiKeyMapWithNestedCollections() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();

        // Test with nested collections
        List<List<String>> nestedList = Arrays.asList(
                Arrays.asList("a", "b"),
                Arrays.asList("c", "d")
        );
        map.put(nestedList, "nestedListValue");

        // Test with nested set
        // Note: Nested Set<Set<>> is a complex edge case due to Set equality/hashCode requirements
        // TODO: Add support for nested Set keys in future enhancement
        // Set<Set<Integer>> nestedSet = new HashSet<>();
        // nestedSet.add(new HashSet<>(Arrays.asList(1, 2)));
        // nestedSet.add(new HashSet<>(Arrays.asList(3, 4)));
        // map.put(nestedSet, "nestedSetValue");

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        assertEquals("nestedListValue", deserializedMap.get(nestedList));
        // assertEquals("nestedSetValue", deserializedMap.get(nestedSet));

        assertEquals(map.size(), deserializedMap.size());
    }

    @Test
    void testMultiKeyMapWithNestedArraysAndNulls() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();

        // Test with nested arrays containing nulls
        Object[][] nestedArrayWithNulls = {
                {"a", null, "b"},
                {null, "c", null}
        };
        map.put(nestedArrayWithNulls, "nestedArrayValue");

        // Test with array containing null at top level
        Object[] arrayWithNull = new Object[]{"key1", null, "key2"};
        map.put(arrayWithNull, "arrayWithNullValue");

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        // After deserialization, array keys become List keys (new format behavior)
        // Convert to Lists for lookup
        List<List<Object>> nestedListKey = Arrays.asList(
                Arrays.asList("a", null, "b"),
                Arrays.asList(null, "c", null)
        );
        List<Object> listKey = Arrays.asList("key1", null, "key2");

        assertEquals("nestedArrayValue", deserializedMap.get(nestedListKey));
        assertEquals("arrayWithNullValue", deserializedMap.get(listKey));
        assertEquals(map.size(), deserializedMap.size());
    }

    @Test
    void testMultiKeyMapWithSetsContainingNulls() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();

        // Test with Set containing null
        Set<String> setWithNull = new HashSet<>(Arrays.asList("a", null, "b"));
        map.put(setWithNull, "setWithNullValue");

        // Test with multi-key where Set component has null
        Set<Integer> setWithNullInt = new HashSet<>(Arrays.asList(1, null, 2));
        map.putMultiKey("multiKeySetWithNull", setWithNullInt, "suffix");

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        // Verify sets with nulls can be retrieved
        Set<String> lookupSetWithNull = new HashSet<>(Arrays.asList(null, "b", "a")); // Different order
        assertEquals("setWithNullValue", deserializedMap.get(lookupSetWithNull));

        Set<Integer> lookupSetWithNullInt = new HashSet<>(Arrays.asList(null, 2, 1)); // Different order
        assertEquals("multiKeySetWithNull", deserializedMap.getMultiKey(lookupSetWithNullInt, "suffix"));

        assertEquals(map.size(), deserializedMap.size());
    }

    // Helper class for testing complex objects
    static class Person {
        String name;
        int age;
        String city;

        Person() {} // For deserialization

        Person(String name, int age, String city) {
            this.name = name;
            this.age = age;
            this.city = city;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Person person = (Person) o;
            return age == person.age &&
                    java.util.Objects.equals(name, person.name) &&
                    java.util.Objects.equals(city, person.city);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, age, city);
        }
    }

    @Test
    void testMultiKeyMapWithComplexObjectValues() {
        MultiKeyMap<Person> map = MultiKeyMap.<Person>builder().build();

        // Test with complex objects as values
        Person person1 = new Person("Alice", 30, "NYC");
        Person person2 = new Person("Bob", 25, "SF");
        Person person3 = new Person("Charlie", 35, "LA");

        map.put("key1", person1);
        map.putMultiKey(person2, "composite", "key");

        Set<String> setKey = new HashSet<>(Arrays.asList("x", "y"));
        map.put(setKey, person3);

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<Person> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<Person>>(){});

        // Verify complex objects are properly deserialized
        Person retrieved1 = deserializedMap.get("key1");
        assertEquals("Alice", retrieved1.name);
        assertEquals(30, retrieved1.age);
        assertEquals("NYC", retrieved1.city);

        Person retrieved2 = deserializedMap.getMultiKey("composite", "key");
        assertEquals("Bob", retrieved2.name);
        assertEquals(25, retrieved2.age);
        assertEquals("SF", retrieved2.city);

        Person retrieved3 = deserializedMap.get(new HashSet<>(Arrays.asList("y", "x")));
        assertEquals("Charlie", retrieved3.name);
        assertEquals(35, retrieved3.age);
        assertEquals("LA", retrieved3.city);

        assertEquals(map.size(), deserializedMap.size());
    }

    @Test
    void testMultiKeyMapWithComplexObjectKeys() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();

        // Test with complex objects as keys
        Person person1 = new Person("Alice", 30, "NYC");
        Person person2 = new Person("Bob", 25, "SF");

        map.put(person1, "value1");
        map.putMultiKey("value2", person2, "suffix");

        // Mix complex object with Set/List keys
        List<String> list = Arrays.asList("a", "b");
        map.putMultiKey("value3", person1, list);

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        // Verify complex object keys work
        Person lookupPerson1 = new Person("Alice", 30, "NYC");
        assertEquals("value1", deserializedMap.get(lookupPerson1));

        Person lookupPerson2 = new Person("Bob", 25, "SF");
        assertEquals("value2", deserializedMap.getMultiKey(lookupPerson2, "suffix"));

        Person lookupPerson3 = new Person("Alice", 30, "NYC");
        assertEquals("value3", deserializedMap.getMultiKey(lookupPerson3, Arrays.asList("a", "b")));

        assertEquals(map.size(), deserializedMap.size());
    }

    // Helper enum for testing
    enum Status {
        ACTIVE, INACTIVE, PENDING
    }

    @Test
    void testMultiKeyMapWithEnums() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();

        // Test enum as key
        map.put(Status.ACTIVE, "activeValue");

        // Test enum in multi-key
        map.putMultiKey("enumMultiKey", Status.PENDING, "suffix");

        // Test enum in Set key
        Set<Status> enumSet = new HashSet<>(Arrays.asList(Status.ACTIVE, Status.INACTIVE));
        map.put(enumSet, "enumSetValue");

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        assertEquals("activeValue", deserializedMap.get(Status.ACTIVE));
        assertEquals("enumMultiKey", deserializedMap.getMultiKey(Status.PENDING, "suffix"));

        Set<Status> lookupSet = new HashSet<>(Arrays.asList(Status.INACTIVE, Status.ACTIVE));
        assertEquals("enumSetValue", deserializedMap.get(lookupSet));

        assertEquals(map.size(), deserializedMap.size());
    }

    @Test
    void testMultiKeyMapWithTemporalTypes() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();

        // Test temporal types as keys
        LocalDate date = LocalDate.of(2025, 10, 26);
        map.put(date, "dateValue");

        LocalDateTime dateTime = LocalDateTime.of(2025, 10, 26, 14, 30);
        map.putMultiKey("dateTimeValue", dateTime, "suffix");

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        assertEquals("dateValue", deserializedMap.get(LocalDate.of(2025, 10, 26)));
        assertEquals("dateTimeValue", deserializedMap.getMultiKey(LocalDateTime.of(2025, 10, 26, 14, 30), "suffix"));

        assertEquals(map.size(), deserializedMap.size());
    }

    @Test
    void testMultiKeyMapWithBigNumbers() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();

        // Test BigDecimal and BigInteger
        BigDecimal decimal = new BigDecimal("123.456");
        map.put(decimal, "decimalValue");

        BigInteger bigInt = new BigInteger("999999999999999999");
        map.putMultiKey("bigIntValue", bigInt, "suffix");

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        assertEquals("decimalValue", deserializedMap.get(new BigDecimal("123.456")));
        assertEquals("bigIntValue", deserializedMap.getMultiKey(new BigInteger("999999999999999999"), "suffix"));

        assertEquals(map.size(), deserializedMap.size());
    }

    @Test
    void testMultiKeyMapWithUUID() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();

        // Test UUID as key
        UUID uuid1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        map.put(uuid1, "uuidValue");

        UUID uuid2 = UUID.randomUUID();
        map.putMultiKey("uuidMultiKey", uuid2, "suffix");

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        assertEquals("uuidValue", deserializedMap.get(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")));
        assertEquals("uuidMultiKey", deserializedMap.getMultiKey(uuid2, "suffix"));

        assertEquals(map.size(), deserializedMap.size());
    }

    @Test
    void testMultiKeyMapWithEmptyCollections() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();

        // Test empty Set as key
        Set<String> emptySet = new HashSet<>();
        map.put(emptySet, "emptySetValue");

        // NOTE: Empty List and empty Object[] are indistinguishable after serialization
        // Both serialize to [] without markers, so they become equivalent
        // We test empty array here as the canonical empty collection representation
        Object[] emptyArray = new Object[0];
        map.put(emptyArray, "emptyArrayValue");

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        // Empty Set has markers so it remains distinct
        assertEquals("emptySetValue", deserializedMap.get(new HashSet<>()));

        // Empty array and empty list are equivalent after round-trip
        assertEquals("emptyArrayValue", deserializedMap.get(new Object[0]));
        assertEquals("emptyArrayValue", deserializedMap.get(Collections.emptyList())); // Same as empty array

        assertEquals(map.size(), deserializedMap.size());
    }

    @Test
    void testMultiKeyMapWithPrimitiveArrays() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();

        // Test int array as key
        int[] intArray = {1, 2, 3};
        map.put(intArray, "intArrayValue");

        // Test byte array as key
        byte[] byteArray = {10, 20, 30};
        map.put(byteArray, "byteArrayValue");

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        assertEquals("intArrayValue", deserializedMap.get(new int[]{1, 2, 3}));
        assertEquals("byteArrayValue", deserializedMap.get(new byte[]{10, 20, 30}));

        assertEquals(map.size(), deserializedMap.size());
    }

    @Test
    void testMultiKeyMapWithMarkerCollision() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();

        // CRITICAL TEST: User strings that match marker names
        // These should be escaped to prevent collision with internal markers

        // Direct marker name collisions
        map.put("~SET_OPEN~", "value1");
        map.put("~SET_CLOSE~", "value2");
        map.put("~OPEN~", "value3");
        map.put("~CLOSE~", "value4");

        // Test marker name in multi-key
        map.putMultiKey("value5", "~SET_OPEN~", "normal");

        // Test marker names in arrays
        Object[] arrayWithMarkers = {"~OPEN~", "data", "~CLOSE~"};
        map.put(arrayWithMarkers, "value6");

        // Test already-escaped strings (recursive escaping)
        map.put("~~ESC~~^OPEN~", "value7");
        map.put("~~ESC~~~~ESC~~^SET_OPEN~", "value8");

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        // Verify marker name collisions are properly escaped/unescaped
        assertEquals("value1", deserializedMap.get("~SET_OPEN~"));
        assertEquals("value2", deserializedMap.get("~SET_CLOSE~"));
        assertEquals("value3", deserializedMap.get("~OPEN~"));
        assertEquals("value4", deserializedMap.get("~CLOSE~"));
        assertEquals("value5", deserializedMap.getMultiKey("~SET_OPEN~", "normal"));
        assertEquals("value6", deserializedMap.get(new Object[]{"~OPEN~", "data", "~CLOSE~"}));

        // Verify recursive escaping works (these are user strings that already start with ~~ESC~~)
        assertEquals("value7", deserializedMap.get("~~ESC~~^OPEN~"));
        assertEquals("value8", deserializedMap.get("~~ESC~~~~ESC~~^SET_OPEN~"));

        assertEquals(map.size(), deserializedMap.size());
    }

    @Test
    void testMultiKeyMapWithStringEdgeCases() {
        MultiKeyMap<String> map = MultiKeyMap.<String>builder().build();

        // Test empty string as key
        map.put("", "emptyStringValue");

        // Test unicode/emoji strings
        map.put("Hello 🌍", "emojiValue");

        // Test very long string
        StringBuilder sb = new StringBuilder(1000);
        for (int i = 0; i < 1000; i++) {
            sb.append('x');
        }
        String longString = sb.toString();
        map.put(longString, "longStringValue");

        // Test whitespace strings
        map.put("   ", "whitespaceValue");

        String json = JsonIo.toJson(map, null);
        MultiKeyMap<String> deserializedMap = JsonIo.toJava(json, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        assertEquals("emptyStringValue", deserializedMap.get(""));
        assertEquals("emojiValue", deserializedMap.get("Hello 🌍"));

        StringBuilder sb2 = new StringBuilder(1000);
        for (int i = 0; i < 1000; i++) {
            sb2.append('x');
        }
        assertEquals("longStringValue", deserializedMap.get(sb2.toString()));
        assertEquals("whitespaceValue", deserializedMap.get("   "));

        assertEquals(map.size(), deserializedMap.size());
    }

    @Test
    void testMultiKeyMapWithDeeplyNestedCollections() {
        // Test List<Set<List>> and Set<List<Set>> with both collection key modes

        // ===== Test with COLLECTIONS_EXPANDED mode (default) =====
        MultiKeyMap<String> expandedMap = MultiKeyMap.<String>builder()
                .collectionKeyMode(MultiKeyMap.CollectionKeyMode.COLLECTIONS_EXPANDED)
                .build();

        // Test 1: List<Set<List<String>>>
        List<Set<List<String>>> listSetList = new ArrayList<>();
        Set<List<String>> innerSet1 = new HashSet<>();
        innerSet1.add(Arrays.asList("a", "b", "c"));
        innerSet1.add(Arrays.asList("d", "e"));
        listSetList.add(innerSet1);

        Set<List<String>> innerSet2 = new HashSet<>();
        innerSet2.add(Arrays.asList("x", "y"));
        listSetList.add(innerSet2);

        expandedMap.put(listSetList, "listSetListValue");

        // Test 2: Set<List<Set<Integer>>>
        Set<List<Set<Integer>>> setListSet = new HashSet<>();
        List<Set<Integer>> innerList1 = new ArrayList<>();
        innerList1.add(new HashSet<>(Arrays.asList(1, 2, 3)));
        innerList1.add(new HashSet<>(Arrays.asList(4, 5)));
        setListSet.add(innerList1);

        List<Set<Integer>> innerList2 = new ArrayList<>();
        innerList2.add(new HashSet<>(Arrays.asList(6, 7)));
        setListSet.add(innerList2);

        expandedMap.put(setListSet, "setListSetValue");

        // Test 3: List<Set<List<String>>> with nulls and empty collections
        List<Set<List<String>>> listSetListWithNulls = new ArrayList<>();
        Set<List<String>> setWithNulls = new HashSet<>();
        setWithNulls.add(Arrays.asList("a", null, "b"));
        setWithNulls.add(Collections.emptyList());
        setWithNulls.add(Arrays.asList("c"));
        listSetListWithNulls.add(setWithNulls);
        listSetListWithNulls.add(new HashSet<>()); // Empty set

        expandedMap.put(listSetListWithNulls, "listSetListWithNullsValue");

        // Test 4: Set<List<Set<String>>> with nulls
        Set<List<Set<String>>> setListSetWithNulls = new HashSet<>();
        List<Set<String>> listWithNulls = new ArrayList<>();
        listWithNulls.add(new HashSet<>(Arrays.asList("p", null)));
        listWithNulls.add(new HashSet<>()); // Empty set
        listWithNulls.add(new HashSet<>(Arrays.asList("q", "r")));
        setListSetWithNulls.add(listWithNulls);

        expandedMap.put(setListSetWithNulls, "setListSetWithNullsValue");

        // Serialize and deserialize EXPANDED mode map
        String expandedJson = JsonIo.toJson(expandedMap, null);
        MultiKeyMap<String> deserializedExpanded = JsonIo.toJava(expandedJson, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        // Verify EXPANDED mode - all nested collections should work
        assertEquals(MultiKeyMap.CollectionKeyMode.COLLECTIONS_EXPANDED, deserializedExpanded.getCollectionKeyMode());
        assertEquals("listSetListValue", deserializedExpanded.get(listSetList));
        assertEquals("setListSetValue", deserializedExpanded.get(setListSet));
        assertEquals("listSetListWithNullsValue", deserializedExpanded.get(listSetListWithNulls));
        assertEquals("setListSetWithNullsValue", deserializedExpanded.get(setListSetWithNulls));
        assertEquals(expandedMap.size(), deserializedExpanded.size());

        // ===== Test with COLLECTIONS_NOT_EXPANDED mode =====
        MultiKeyMap<String> notExpandedMap = MultiKeyMap.<String>builder()
                .collectionKeyMode(MultiKeyMap.CollectionKeyMode.COLLECTIONS_NOT_EXPANDED)
                .build();

        // Test 1: Set<List<String>> as single key (MultiKeyMap will strip any outer wrapper)
        Set<List<String>> setOfLists = new HashSet<>();
        setOfLists.add(Arrays.asList("m", "n"));
        setOfLists.add(Arrays.asList("o", "p"));

        notExpandedMap.put(setOfLists, "notExpandedSetOfLists");

        // Test 2: List<Set<Integer>> as single key
        List<Set<Integer>> listOfSets = new ArrayList<>();
        listOfSets.add(new HashSet<>(Arrays.asList(10, 20)));
        listOfSets.add(new HashSet<>(Arrays.asList(30)));

        notExpandedMap.put(listOfSets, "notExpandedListOfSets");

        // Test 3: Set with nulls
        Set<List<String>> setWithNullsNotExpanded = new HashSet<>();
        setWithNullsNotExpanded.add(Arrays.asList("w", null));
        setWithNullsNotExpanded.add(Collections.emptyList());

        notExpandedMap.put(setWithNullsNotExpanded, "notExpandedWithNulls");

        // Serialize and deserialize NOT_EXPANDED mode map
        String notExpandedJson = JsonIo.toJson(notExpandedMap, null);
        MultiKeyMap<String> deserializedNotExpanded = JsonIo.toJava(notExpandedJson, null).asType(new TypeHolder<MultiKeyMap<String>>(){});

        // Verify NOT_EXPANDED mode - collections should be treated as single keys
        assertEquals(MultiKeyMap.CollectionKeyMode.COLLECTIONS_NOT_EXPANDED, deserializedNotExpanded.getCollectionKeyMode());
        assertEquals("notExpandedSetOfLists", deserializedNotExpanded.get(setOfLists));
        assertEquals("notExpandedListOfSets", deserializedNotExpanded.get(listOfSets));
        assertEquals("notExpandedWithNulls", deserializedNotExpanded.get(setWithNullsNotExpanded));
        assertEquals(notExpandedMap.size(), deserializedNotExpanded.size());

        // ===== Test cross-mode compatibility: verify different collection implementations match =====
        // This tests that deserialized collections match fresh collections with same content
        Set<List<String>> lookupSet = new HashSet<>();
        lookupSet.add(Arrays.asList("m", "n"));
        lookupSet.add(Arrays.asList("o", "p"));

        assertEquals("notExpandedSetOfLists", deserializedNotExpanded.get(lookupSet));
    }
}
