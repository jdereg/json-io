package com.cedarsoftware.io;

import java.util.Map;

import com.cedarsoftware.util.CompactMap;
import com.cedarsoftware.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompactMapCustomTypeTest {

    @Test
    public void testCompactMapWithCustomMapType() {
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
        assertTrue(newMap instanceof CustomTestMap, "New map should be a CustomTestMap");

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
        assertTrue(restoredNewMap instanceof CustomTestMap,
                "Restored map should create CustomTestMap instances");
    }
}