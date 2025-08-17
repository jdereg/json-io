package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for Issue #425: toObjects() with unknownTypeClass() and nested JSON is broken
 * https://github.com/jdereg/json-io/issues/425
 * 
 * When deserializing nested JSON with unknownTypeClass(LinkedHashMap.class),
 * the inner object's values are incorrectly duplicated into the outer object.
 */
public class Issue425NestedJsonTest {

    @Test
    public void testNestedJsonWithLinkedHashMapDuplication() {
        // This is the reported issue - inner values are duplicated to outer
        String json = "{\"outer\":{\"inner\":\"value\"}}";
        System.out.println("Original JSON: " + json);
        
        ReadOptions options = new ReadOptionsBuilder()
                .unknownTypeClass(LinkedHashMap.class)
                .build();
        
        // Use toObjects as reported in the issue
        Object result = JsonIo.toObjects(json, options, Object.class);
        
        System.out.println("Parsed result: " + result);
        
        // The bug: result contains both "inner" and "outer" at top level
        assertTrue(result instanceof LinkedHashMap, "Result should be LinkedHashMap");
        Map<?, ?> resultMap = (Map<?, ?>) result;
        
        // Should only have "outer" key at top level
        assertEquals(1, resultMap.size(), "Top level should only have 'outer' key");
        assertTrue(resultMap.containsKey("outer"), "Should have 'outer' key");
        assertFalse(resultMap.containsKey("inner"), 
                "BUG: Should NOT have 'inner' key at top level - it's duplicated from nested object");
        
        // Check nested structure
        Object outerValue = resultMap.get("outer");
        assertTrue(outerValue instanceof Map, "Outer value should be a Map");
        Map<?, ?> outerMap = (Map<?, ?>) outerValue;
        assertEquals(1, outerMap.size(), "Outer map should only have 'inner' key");
        assertEquals("value", outerMap.get("inner"), "Inner value should be 'value'");
        
        // Verify round-trip
        String roundTripJson = JsonIo.toJson(result, null);
        System.out.println("Round-trip JSON: " + roundTripJson);
        
        // The bug manifests in round-trip: inner value appears at both levels
        assertFalse(roundTripJson.contains("\"inner\":\"value\",\"outer\""), 
                "Round-trip should not have duplicated inner value at top level");
    }
    
    @Test
    public void testNestedJsonWithJsonObjectDefault() {
        // Test with default JsonObject - should work correctly
        String json = "{\"outer\":{\"inner\":\"value\"}}";
        
        ReadOptions options = new ReadOptionsBuilder().build();
        
        Object result = JsonIo.toJava(json, options).asClass(Object.class);
        
        System.out.println("JsonObject default result: " + result);
        
        assertTrue(result instanceof JsonObject, "Result should be JsonObject by default");
        JsonObject jsonObj = (JsonObject) result;
        
        // Should only have "outer" at top level
        assertEquals(1, jsonObj.size(), "Should only have one key at top level");
        assertTrue(jsonObj.containsKey("outer"), "Should have 'outer' key");
        assertFalse(jsonObj.containsKey("inner"), "Should NOT have 'inner' at top level");
        
        // Check nested structure
        Object outerValue = jsonObj.get("outer");
        assertTrue(outerValue instanceof JsonObject || outerValue instanceof Map, 
                "Outer value should be JsonObject or Map");
        
        // Verify round-trip
        String roundTripJson = JsonIo.toJson(result, null);
        System.out.println("JsonObject round-trip: " + roundTripJson);
    }
    
    @Test
    public void testNestedJsonWithExplicitJsonObject() {
        // Test with explicit JsonObject as unknownTypeClass
        String json = "{\"outer\":{\"inner\":\"value\"}}";
        
        ReadOptions options = new ReadOptionsBuilder()
                .unknownTypeClass(JsonObject.class)
                .build();
        
        Object result = JsonIo.toJava(json, options).asClass(Object.class);
        
        System.out.println("Explicit JsonObject result: " + result);
        
        assertTrue(result instanceof JsonObject, "Result should be JsonObject");
        JsonObject jsonObj = (JsonObject) result;
        
        // Should work correctly with explicit JsonObject
        assertEquals(1, jsonObj.size(), "Should only have one key at top level");
        assertTrue(jsonObj.containsKey("outer"), "Should have 'outer' key");
        assertFalse(jsonObj.containsKey("inner"), "Should NOT have 'inner' at top level");
        
        // Verify round-trip - should not have @type markers with explicit JsonObject
        String roundTripJson = JsonIo.toJson(result, null);
        System.out.println("Explicit JsonObject round-trip: " + roundTripJson);
        
        // With explicit JsonObject, no @type markers expected
        assertEquals("{\"outer\":{\"inner\":\"value\"}}", roundTripJson,
                "Round-trip should match original JSON when using explicit JsonObject");
    }
    
    @Test
    public void testDeeplyNestedJsonWithLinkedHashMap() {
        // Test with deeper nesting to see if issue compounds
        String json = "{\"level1\":{\"level2\":{\"level3\":{\"value\":\"deep\"}}}}";
        
        ReadOptions options = new ReadOptionsBuilder()
                .unknownTypeClass(LinkedHashMap.class)
                .build();
        
        Object result = JsonIo.toJava(json, options).asClass(Object.class);
        
        System.out.println("Deep nested result: " + result);
        
        assertTrue(result instanceof LinkedHashMap);
        Map<?, ?> resultMap = (Map<?, ?>) result;
        
        // Check that only level1 exists at top
        assertEquals(1, resultMap.size(), "Top level should only have 'level1'");
        assertTrue(resultMap.containsKey("level1"), "Should have 'level1'");
        assertFalse(resultMap.containsKey("level2"), "Should NOT have 'level2' at top");
        assertFalse(resultMap.containsKey("level3"), "Should NOT have 'level3' at top");
        assertFalse(resultMap.containsKey("value"), "Should NOT have 'value' at top");
        
        // Navigate to level3
        Map<?, ?> level1 = (Map<?, ?>) resultMap.get("level1");
        assertNotNull(level1);
        assertEquals(1, level1.size(), "Level1 should only have 'level2'");
        
        Map<?, ?> level2 = (Map<?, ?>) level1.get("level2");
        assertNotNull(level2);
        assertEquals(1, level2.size(), "Level2 should only have 'level3'");
        
        Map<?, ?> level3 = (Map<?, ?>) level2.get("level3");
        assertNotNull(level3);
        assertEquals(1, level3.size(), "Level3 should only have 'value'");
        assertEquals("deep", level3.get("value"));
    }
    
    @Test
    public void testMultipleNestedPropertiesWithLinkedHashMap() {
        // Test with multiple properties at different levels
        String json = "{\"a\":\"1\",\"nested\":{\"b\":\"2\",\"c\":\"3\"},\"d\":\"4\"}";
        
        ReadOptions options = new ReadOptionsBuilder()
                .unknownTypeClass(LinkedHashMap.class)
                .build();
        
        Object result = JsonIo.toJava(json, options).asClass(Object.class);
        
        System.out.println("Multiple properties result: " + result);
        
        assertTrue(result instanceof LinkedHashMap);
        Map<?, ?> resultMap = (Map<?, ?>) result;
        
        // Should have exactly 3 keys at top level: a, nested, d
        assertEquals(3, resultMap.size(), "Should have 3 keys at top level");
        assertEquals("1", resultMap.get("a"));
        assertEquals("4", resultMap.get("d"));
        
        // Should NOT have nested object's properties at top level
        assertFalse(resultMap.containsKey("b"), "Should NOT have 'b' at top level");
        assertFalse(resultMap.containsKey("c"), "Should NOT have 'c' at top level");
        
        // Check nested object
        Map<?, ?> nested = (Map<?, ?>) resultMap.get("nested");
        assertNotNull(nested);
        assertEquals(2, nested.size(), "Nested should have 2 keys");
        assertEquals("2", nested.get("b"));
        assertEquals("3", nested.get("c"));
    }
    
    @Test
    public void testArrayWithNestedObjectsAndLinkedHashMap() {
        // Test with array containing nested objects
        String json = "[{\"outer\":{\"inner\":\"value1\"}},{\"outer\":{\"inner\":\"value2\"}}]";
        
        ReadOptions options = new ReadOptionsBuilder()
                .unknownTypeClass(LinkedHashMap.class)
                .build();
        
        Object result = JsonIo.toJava(json, options).asClass(Object[].class);
        
        System.out.println("Array with nested objects: " + java.util.Arrays.toString((Object[]) result));
        
        assertTrue(result instanceof Object[]);
        Object[] array = (Object[]) result;
        assertEquals(2, array.length);
        
        // Check first element
        Map<?, ?> elem1 = (Map<?, ?>) array[0];
        assertEquals(1, elem1.size(), "First element should only have 'outer'");
        assertFalse(elem1.containsKey("inner"), "First element should NOT have 'inner' at top");
        
        // Check second element  
        Map<?, ?> elem2 = (Map<?, ?>) array[1];
        assertEquals(1, elem2.size(), "Second element should only have 'outer'");
        assertFalse(elem2.containsKey("inner"), "Second element should NOT have 'inner' at top");
    }
}