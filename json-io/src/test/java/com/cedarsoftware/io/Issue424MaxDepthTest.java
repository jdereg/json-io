package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for Issue #424: maxObjectGraphDepth fails on lower depth with too many objects
 * https://github.com/jdereg/json-io/issues/424
 * 
 * The issue is that maxObjectGraphDepth is counting objects rather than actual depth.
 * A list with 12 elements at depth 2 should not trigger a depth limit of 10.
 */
public class Issue424MaxDepthTest {

    @Test
    public void testListWithManyElementsShouldNotTriggerDepthLimit() {
        // This should work - the actual depth is only 2 (root -> list -> elements)
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .maxObjectGraphDepth(10)
                .build();
        
        // Create a list with 12 elements
        List<String> list = IntStream.range(0, 12)
                .mapToObj(i -> "test" + i)
                .collect(Collectors.toList());
        
        // This should NOT throw an exception as the depth is only 2
        String json = JsonIo.toJson(list, writeOptions);
        assertNotNull(json);
        assertTrue(json.contains("test0"));
        assertTrue(json.contains("test11"));
        
        // Verify we can read it back
        List result = JsonIo.toJava(json, null).asClass(List.class);
        assertEquals(12, result.size());
        assertEquals("test0", result.get(0));
        assertEquals("test11", result.get(11));
    }
    
    @Test
    public void testListWith100ElementsShouldWork() {
        // Even with 100 elements, the depth is still only 2
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .maxObjectGraphDepth(5)
                .build();
        
        List<Integer> list = IntStream.range(0, 100)
                .boxed()
                .collect(Collectors.toList());
        
        // Should work fine - depth is 2, not 100
        String json = JsonIo.toJson(list, writeOptions);
        assertNotNull(json);
        
        List result = JsonIo.toJava(json, null).asClass(List.class);
        assertEquals(100, result.size());
    }
    
    @Test 
    public void testMapWithManyEntriesShouldWork() {
        // Map with many entries - depth is still only 2
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .maxObjectGraphDepth(10)
                .build();
        
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            map.put("key" + i, "value" + i);
        }
        
        // Should work - depth is 2 (root -> map -> entries)
        String json = JsonIo.toJson(map, writeOptions);
        assertNotNull(json);
        
        Map result = JsonIo.toJava(json, null).asClass(Map.class);
        assertEquals(20, result.size());
    }
    
    @Test
    public void testActualDeepNestingShouldFail() {
        // This SHOULD fail - actual deep nesting beyond limit
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .maxObjectGraphDepth(5)
                .build();
        
        // Create actual deep nesting: depth of 6
        List<Object> level1 = new ArrayList<>();
        List<Object> level2 = new ArrayList<>();
        List<Object> level3 = new ArrayList<>();
        List<Object> level4 = new ArrayList<>();
        List<Object> level5 = new ArrayList<>();
        List<Object> level6 = new ArrayList<>();
        
        level1.add(level2);
        level2.add(level3);
        level3.add(level4);
        level4.add(level5);
        level5.add(level6);
        level6.add("bottom");
        
        // This SHOULD throw an exception as depth is 6, exceeding limit of 5
        assertThrows(JsonIoException.class, () -> {
            JsonIo.toJson(level1, writeOptions);
        }, "Should fail with actual deep nesting beyond limit");
    }
    
    @Test
    public void testWideButShallowStructureShouldWork() {
        // Wide structure with many siblings but shallow depth
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .maxObjectGraphDepth(3)
                .build();
        
        List<Map<String, String>> listOfMaps = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Map<String, String> map = new HashMap<>();
            map.put("id", String.valueOf(i));
            map.put("name", "item" + i);
            listOfMaps.add(map);
        }
        
        // Should work - depth is 3 (root -> list -> map -> string)
        String json = JsonIo.toJson(listOfMaps, writeOptions);
        assertNotNull(json);
        
        List result = JsonIo.toJava(json, null).asClass(List.class);
        assertEquals(50, result.size());
    }
    
    @Test
    public void testCorrectDepthCountingWithComplexStructure() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .maxObjectGraphDepth(4)
                .build();
        
        // Create structure with exact depth of 4
        Map<String, Object> root = new HashMap<>();
        List<Map<String, Object>> level2 = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            Map<String, Object> level2Map = new HashMap<>();
            List<String> level3List = new ArrayList<>();
            for (int j = 0; j < 5; j++) {
                level3List.add("item_" + i + "_" + j); // depth 4
            }
            level2Map.put("items", level3List);
            level2Map.put("id", i);
            level2.add(level2Map);
        }
        root.put("data", level2);
        
        // Should work - max depth is exactly 4
        String json = JsonIo.toJson(root, writeOptions);
        assertNotNull(json);
        
        // Now add one more level to exceed limit
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("wrapped", root);
        
        // This should fail - depth is now 5
        assertThrows(JsonIoException.class, () -> {
            JsonIo.toJson(wrapper, writeOptions);
        }, "Should fail when actual depth exceeds limit");
    }
}