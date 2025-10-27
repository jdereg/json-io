package com.cedarsoftware.io;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
}
