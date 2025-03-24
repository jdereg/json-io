package com.cedarsoftware.io;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

import com.cedarsoftware.util.CompactCIHashMap;
import com.cedarsoftware.util.CompactCIHashSet;
import com.cedarsoftware.util.CompactCILinkedMap;
import com.cedarsoftware.util.CompactCILinkedSet;
import com.cedarsoftware.util.CompactLinkedMap;
import com.cedarsoftware.util.CompactLinkedSet;
import com.cedarsoftware.util.CompactMap;
import com.cedarsoftware.util.CompactSet;
import com.cedarsoftware.util.DeepEquals;
import com.cedarsoftware.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CompactMapCustomTypeTest {

    @Test
    void testCompactMapWithCustomMapType() {
        // Create a CompactMap with a custom map type
        CompactMap<String, String> map = CompactMap.<String, String>builder()
                .caseSensitive(true)
                .compactSize(42)
                .singleValueKey("code")
                .noOrder()
                .mapType(CustomTestMap.class)  // Use our custom map implementation
                .build();

        // Add some entries
        map.put("one", "First");
        map.put("two", "Second");
        map.put("three", "Third");

        // Add more entries to exceed compactSize and force map creation
        for (int i = 0; i < 50; i++) {
            map.put("key" + i, "value" + i);
        }

        // Print the class name for debugging
//        System.out.println("CompactMap class: " + map.getClass().getName());

        // Get a new map from the CompactMap to verify type
        Map<?, ?> newMap = (Map<?, ?>) ReflectionUtils.call(map, "getNewMap");
//        System.out.println("New map type: " + newMap.getClass().getName());
        assertInstanceOf(CustomTestMap.class, newMap, "New map should be a CustomTestMap");

        // Serialize to JSON
        String json = JsonIo.toJson(map, null);
//        System.out.println("JSON output: " + json);

        // Deserialize back
        CompactMap<String, String> restored = JsonIo.toJava(json, null).asType(new TypeHolder<CompactMap<String, String>>(){});

        // Verify the map contents were preserved
        assertEquals(53, restored.size());
        assertEquals("First", restored.get("one"));
        assertEquals("Second", restored.get("two"));
        assertEquals("Third", restored.get("three"));

        // Verify the map is correctly configured
        assertEquals(false, ReflectionUtils.call(restored, "isCaseInsensitive"),
                "Case sensitivity should be preserved");
        assertEquals(42, ReflectionUtils.call(restored, "compactSize"),
                "Compact size should be preserved");
        assertEquals("code", ReflectionUtils.call(restored, "getSingleValueKey"),
                "Single value key should be preserved");

        // Get a new map from the restored CompactMap to verify type
        Map<?, ?> restoredNewMap = (Map<?, ?>) ReflectionUtils.call(restored, "getNewMap");
//        System.out.println("Restored new map type: " + restoredNewMap.getClass().getName());
        assertInstanceOf(CustomTestMap.class, restoredNewMap, "Restored map should create CustomTestMap instances");
    }

    @Test
    void testCompactMapAsIsWithJsonIo() {
        Map<String, Comparable<?>> map = new CompactMap<>();
        map.put("apple", 1);
        map.put("banana", 2);
        map.put("cherry", 3);
        map.put("Apple", 4);
        assert map.size() == 4;  // Case-insensitive (one apple)
        assert !map.containsKey("APPLE");
        assert 1 == (Integer) map.get("apple");
        assert 4 == (Integer) map.get("Apple");

        String json = JsonIo.toJson(map, null);
//        System.out.println(json);
        Map<String, Comparable<?>> map2 = JsonIo.toJava(json, null).asType(new TypeHolder<Map<String, Comparable<?>>>(){});
        assert DeepEquals.deepEquals(map, map2);
        assert map2 instanceof CompactMap;
        assert !map2.getClass().getName().startsWith("com.cedarsoftware.util.CompactMap$");
    }

    @Test
    void testConfiguredCompactMapWithJsonIo() {
        Map<String, Comparable<?>> map = CompactMap.<String, Comparable<?>>builder().caseSensitive(false).mapType(ConcurrentSkipListMap.class).reverseOrder().build();
        map.put("apple", 1);
        map.put("banana", 2);
        map.put("cherry", 3);
        map.put("Apple", 4);
        assert map.size() == 3;  // Case-insensitive (one apple)
        assert map.containsKey("APPLE");
        assert 4 == (Integer) map.get("apple");

        String json = JsonIo.toJson(map, null);
//        System.out.println(json);
        Map<String, Comparable<?>> map2 = JsonIo.toJava(json, null).asType(new TypeHolder<Map<String, Comparable<?>>>(){});
        assert DeepEquals.deepEquals(map, map2);
        assert map2 instanceof CompactMap;
        assert map2.getClass().getName().startsWith("com.cedarsoftware.util.CompactMap$");
    }

    @Test
    void testCompactCIHashMapWithJsonIo() {
        Map<String, Comparable<?>> map = new CompactCIHashMap<>();
        map.put("apple", 1);
        map.put("banana", 2);
        map.put("cherry", 3);
        map.put("Apple", 4);
        assert map.size() == 3;  // Case-insensitive (one apple)
        assert map.containsKey("APPLE");
        assert 4 == (Integer) map.get("APple");

        String json = JsonIo.toJson(map, null);
//        System.out.println(json);
        Map<String, Comparable<?>> map2 = JsonIo.toJava(json, null).asType(new TypeHolder<Map<String, Comparable<?>>>(){});
        assert DeepEquals.deepEquals(map, map2);
        assert map2 instanceof CompactCIHashMap;
        assert !map2.getClass().getName().contains("$");
    }

    @Test
    void testCompactCILinkedMapWithJsonIo() {
        Map<String, Comparable<?>> map = new CompactCILinkedMap<>();
        map.put("apple", 1);
        map.put("banana", 2);
        map.put("cherry", 3);
        map.put("Apple", 4);
        assert map.size() == 3;  // Case-insensitive (one apple)
        assert map.containsKey("APPLE");
        assert 4 == (Integer) map.get("APple");

        String json = JsonIo.toJson(map, null);
//        System.out.println(json);
        Map<String, Comparable<?>> map2 = JsonIo.toJava(json, null).asType(new TypeHolder<Map<String, Comparable<?>>>(){});
        assert DeepEquals.deepEquals(map, map2);
        assert map2 instanceof CompactCILinkedMap;
        assert !map2.getClass().getName().contains("$");
    }

    @Test
    void testCompactLinkedMapWithJsonIo() {
        Map<String, Comparable<?>> map = new CompactLinkedMap<>();
        map.put("apple", 1);
        map.put("banana", 2);
        map.put("cherry", 3);
        map.put("Apple", 4);
        assert map.size() == 4;  // Case-insensitive (one apple)
        assert !map.containsKey("APPLE");
        assert 1 == (Integer) map.get("apple");
        assert 4 == (Integer) map.get("Apple");

        String json = JsonIo.toJson(map, null);
//        System.out.println(json);
        Map<String, Comparable<?>> map2 = JsonIo.toJava(json, null).asType(new TypeHolder<Map<String, Comparable<?>>>(){});
        assert DeepEquals.deepEquals(map, map2);
        assert map2 instanceof CompactLinkedMap;
        assert !map2.getClass().getName().contains("$");
    }

    @Test
    void testCompactSetAsIsWithJsonIo() {
        Set<String> set = new CompactSet<>();
        set.add("apple");
        set.add("banana");
        set.add("cherry");
        set.add("Apple");
        assert set.size() == 4;  // Case-sensitive (two apples)
        assert set.contains("apple");
        assert set.contains("Apple");
        assert !set.contains("APPLE");

        String json = JsonIo.toJson(set, null);
//        System.out.println(json);
        Set<String> set2 = JsonIo.toJava(json, null).asType(new TypeHolder<Set<String>>(){});
        assert DeepEquals.deepEquals(set, set2);
        assert set2 instanceof CompactSet;
        assert !json.contains("config");
    }

    @Test
    void testConfiguredCompactSetWithJsonIo() {
        Set<String> set = CompactSet.<String>builder().reverseOrder().caseSensitive(false).build();
        set.add("apple");
        set.add("banana");
        set.add("cherry");
        set.add("Apple");
        assert set.size() == 3;  // Case-insensitive (one apple)
        assert set.contains("APPLE");

        String json = JsonIo.toJson(set, null);
//        System.out.println(json);
        Set<String> set2 = JsonIo.toJava(json, null).asType(new TypeHolder<Set<String>>(){});
        assert DeepEquals.deepEquals(set, set2);
        assert set2 instanceof CompactSet;
        assert json.contains("config");
    }

    @Test
    void testCompactCIHashSetWithJsonIo() {
        Set<String> set = new CompactCIHashSet<>();
        set.add("apple");
        set.add("banana");
        set.add("cherry");
        set.add("Apple");
        assert set.size() == 3;  // Case-insensitive (one apple)
        assert set.contains("APPLE");

        String json = JsonIo.toJson(set, null);
//        System.out.println(json);
        Set<String> set2 = JsonIo.toJava(json, null).asType(new TypeHolder<Set<String>>(){});
        assert DeepEquals.deepEquals(set, set2);
        assert set2 instanceof CompactCIHashSet;
        assert !set2.getClass().getName().contains("$");
    }

    @Test
    void testCompactCILinkedSetWithJsonIo() {
        Set<String> set = new CompactCILinkedSet<>();
        set.add("apple");
        set.add("banana");
        set.add("cherry");
        set.add("Apple");
        assert set.size() == 3;  // Case-insensitive (one apple)
        assert set.contains("APPLE");

        String json = JsonIo.toJson(set, null);
//        System.out.println(json);
        Set<String> set2 = JsonIo.toJava(json, null).asType(new TypeHolder<Set<String>>(){});
        assert DeepEquals.deepEquals(set, set2);
        assert set2 instanceof CompactCILinkedSet;
        assert !set2.getClass().getName().contains("$");
    }

    @Test
    void testCompactLinkedSetWithJsonIo() {
        Set<String> set = new CompactLinkedSet<>();
        set.add("apple");
        set.add("banana");
        set.add("cherry");
        set.add("Apple");
        assert set.size() == 4;  // Case-sensitive (two apples)
        assert set.contains("apple");
        assert set.contains("Apple");
        assert !set.contains("APPLE");

        String json = JsonIo.toJson(set, null);
//        System.out.println(json);
        Set<String> set2 = JsonIo.toJava(json, null).asType(new TypeHolder<Set<String>>(){});
        assert DeepEquals.deepEquals(set, set2);
        assert set2 instanceof CompactLinkedSet;
        assert !set2.getClass().getName().contains("$");
    }
}