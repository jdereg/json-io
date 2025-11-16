package com.cedarsoftware.io;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exact reproduction test for Issue #425 as reported
 */
public class Issue425ExactReproTest {

    @Test
    public void testExactIssueAsReported() {
        String json = "{\"outer\":{\"inner\":\"value\"}}";
        System.out.println("Input JSON: " + json);
        
        ReadOptions options = new ReadOptionsBuilder()
                .unknownTypeClass(LinkedHashMap.class)
                .build();
        
        Object objects = JsonIo.toJava(json, options).asClass(Object.class);
        System.out.println("Result: " + objects);
        
        String roundTrip = JsonIo.toJson(objects, null);
        System.out.println("Round-trip JSON: " + roundTrip);
        
        // Check if the bug is present: inner values duplicated to outer
        // The reported bug shows: {inner=value, outer={inner=value}}
        // Which means both "inner" and "outer" keys at top level
        
        Map<?, ?> map = (Map<?, ?>) objects;
        
        // Print detailed structure for debugging
        System.out.println("Top-level keys: " + map.keySet());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            System.out.println("  Key: " + entry.getKey() + ", Value: " + entry.getValue() + 
                             ", Value type: " + (entry.getValue() != null ? entry.getValue().getClass() : "null"));
        }
        
        // According to the bug report, this should fail because inner is duplicated
        if (map.containsKey("inner")) {
            System.out.println("BUG REPRODUCED: 'inner' key found at top level!");
            System.out.println("Expected: {outer={inner=value}}");
            System.out.println("Actual: " + objects);
            fail("Bug #425 reproduced: inner values are duplicated to outer level");
        } else {
            System.out.println("Bug NOT reproduced - structure is correct");
            assertEquals(1, map.size(), "Should have only 'outer' at top level");
            assertTrue(map.containsKey("outer"), "Should have 'outer' key");
        }
    }
    
    @Test 
    public void testWithReturnAsJsonObjects() {
        // Try with returnAsJsonObjects option - need to use Map.class as root type
        String json = "{\"outer\":{\"inner\":\"value\"}}";
        
        ReadOptions options = new ReadOptionsBuilder()
                .unknownTypeClass(LinkedHashMap.class)
                .returnAsJsonObjects()
                .build();
        
        // With returnAsJsonObjects, must use Map.class, not Object.class
        Map objects = JsonIo.toJava(json, options).asClass(Map.class);
        System.out.println("With returnAsJsonObjects: " + objects);
        System.out.println("Top-level keys with returnAsJsonObjects: " + objects.keySet());
        
        // Check structure
        assertEquals(1, objects.size(), "Should have only 'outer' at top level");
        assertTrue(objects.containsKey("outer"), "Should have 'outer' key");
        assertFalse(objects.containsKey("inner"), "Should NOT have 'inner' at top level");
    }
    
    @Test
    public void testWithFailOnUnknownTypeFalse() {
        // Try with failOnUnknownType(false)
        String json = "{\"outer\":{\"inner\":\"value\"}}";
        
        ReadOptions options = new ReadOptionsBuilder()
                .unknownTypeClass(LinkedHashMap.class)
                .failOnUnknownType(false)
                .build();
        
        Object objects = JsonIo.toJava(json, options).asClass(Object.class);
        System.out.println("With failOnUnknownType(false): " + objects);
        
        if (objects instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) objects;
            System.out.println("Top-level keys with failOnUnknownType(false): " + map.keySet());
        }
    }
}