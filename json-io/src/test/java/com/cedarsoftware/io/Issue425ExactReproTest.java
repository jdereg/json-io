package com.cedarsoftware.io;

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

        ReadOptions options = new ReadOptionsBuilder()
                .unknownTypeClass(LinkedHashMap.class)
                .build();

        Object objects = JsonIo.toJava(json, options).asClass(Object.class);

        String roundTrip = JsonIo.toJson(objects, null);

        Map<?, ?> map = (Map<?, ?>) objects;

        // Bug #425: inner values should NOT be duplicated to outer level
        assertFalse(map.containsKey("inner"), "Bug #425: 'inner' key should not be at top level");
        assertEquals(1, map.size(), "Should have only 'outer' at top level");
        assertTrue(map.containsKey("outer"), "Should have 'outer' key");
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

        assertTrue(objects instanceof Map, "Result should be a Map");
        Map<?, ?> map = (Map<?, ?>) objects;
        assertEquals(1, map.size(), "Should have only 'outer' at top level");
        assertTrue(map.containsKey("outer"), "Should have 'outer' key");
        assertFalse(map.containsKey("inner"), "Should NOT have 'inner' at top level");
    }
}