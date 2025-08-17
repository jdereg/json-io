package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for Issue #423: Default Unknown type is not LinkedHashMap
 * https://github.com/jdereg/json-io/issues/423
 * 
 * The documentation previously stated that the default unknown type is LinkedHashMap,
 * but actually a JsonObject is returned. JsonObject used to extend LinkedHashMap
 * in 4.19.1 but since 4.19.2 implements Map. This test verifies the actual behavior
 * and that the documentation has been updated to reflect reality.
 */
public class Issue423UnknownTypeTest {

    @Test
    public void testDefaultUnknownTypeReturnsJsonObject() {
        // This is the reported issue - default behavior returns JsonObject, not LinkedHashMap
        String json = "{\"test\":{}}";
        ReadOptions options = new ReadOptionsBuilder().build();
        
        // Check that unknownTypeClass is null by default
        assertNull(options.getUnknownTypeClass(), "Default unknownTypeClass should be null");
        
        // Parse JSON with Object.class target
        Object result = JsonIo.toJava(json, options).asClass(Object.class);
        
        // Verify actual behavior: returns JsonObject, not LinkedHashMap
        System.out.println("Result type: " + result.getClass().getName());
        System.out.println("Result: " + result);
        
        // Default returns JsonObject (which implements Map)
        assertTrue(result instanceof JsonObject, "Default behavior returns JsonObject");
        assertFalse(result instanceof LinkedHashMap, "Default does NOT return LinkedHashMap");
        
        // But it should be a Map
        assertTrue(result instanceof Map, "Result should be a Map");
        
        // Verify the nested object is also a JsonObject
        Map<?, ?> resultMap = (Map<?, ?>) result;
        Object nestedObj = resultMap.get("test");
        assertTrue(nestedObj instanceof JsonObject || nestedObj instanceof Map, 
                "Nested object should be JsonObject or Map");
    }
    
    @Test
    public void testExplicitLinkedHashMapUnknownType() {
        // When explicitly setting LinkedHashMap, it should work correctly
        String json = "{\"test\":{}}";
        ReadOptions options = new ReadOptionsBuilder()
                .unknownTypeClass(LinkedHashMap.class)
                .build();
        
        assertEquals(LinkedHashMap.class, options.getUnknownTypeClass(), 
                "unknownTypeClass should be LinkedHashMap when explicitly set");
        
        Object result = JsonIo.toJava(json, options).asClass(Object.class);
        
        System.out.println("With LinkedHashMap - Result type: " + result.getClass().getName());
        System.out.println("With LinkedHashMap - Result: " + result);
        
        // With explicit LinkedHashMap, should return LinkedHashMap
        assertTrue(result instanceof LinkedHashMap, 
                "Should return LinkedHashMap when explicitly set");
        
        // Verify the nested object is also a JsonObject
        Map<?, ?> resultMap = (Map<?, ?>) result;
        Object nestedObj = resultMap.get("test");
        assertTrue(nestedObj instanceof JsonObject || nestedObj instanceof Map, 
                "Nested object should be JsonObject or Map");
    }
    
    @Test
    public void testUnknownTypeWithFailOnUnknownTypeFalse() {
        // Test with an unknown @type and failOnUnknownType(false)
        String json = "{\"@type\":\"com.example.NonExistentClass\",\"field1\":\"value1\",\"field2\":42}";
        
        // Default behavior
        ReadOptions defaultOptions = new ReadOptionsBuilder()
                .failOnUnknownType(false)
                .build();
        
        Object defaultResult = JsonIo.toJava(json, defaultOptions).asClass(Object.class);
        System.out.println("Unknown type default - Result type: " + defaultResult.getClass().getName());
        // When unknown type with failOnUnknownType(false), actually uses LinkedHashMap
        assertTrue(defaultResult instanceof LinkedHashMap, 
                "With unknown @type and failOnUnknownType(false), returns LinkedHashMap");
        
        // With explicit LinkedHashMap
        ReadOptions linkedHashMapOptions = new ReadOptionsBuilder()
                .failOnUnknownType(false)
                .unknownTypeClass(LinkedHashMap.class)
                .build();
        
        Object linkedHashMapResult = JsonIo.toJava(json, linkedHashMapOptions).asClass(Object.class);
        System.out.println("Unknown type LinkedHashMap - Result type: " + linkedHashMapResult.getClass().getName());
        assertTrue(linkedHashMapResult instanceof LinkedHashMap, 
                "With LinkedHashMap setting, unknown types should be LinkedHashMap");
    }
    
    @Test
    public void testNestedUnknownTypes() {
        // Test with nested unknown objects
        String json = "{\"outer\":{\"inner\":{\"deep\":\"value\"}}}";
        
        // Default behavior
        ReadOptions defaultOptions = new ReadOptionsBuilder().build();
        Object defaultResult = JsonIo.toJava(json, defaultOptions).asClass(Object.class);
        
        assertTrue(defaultResult instanceof JsonObject, "Root should be JsonObject by default");
        Map<?, ?> outer = (Map<?, ?>) ((Map<?, ?>) defaultResult).get("outer");
        assertTrue(outer instanceof JsonObject || outer instanceof Map, "Nested should be JsonObject/Map");
        Map<?, ?> inner = (Map<?, ?>) outer.get("inner");
        assertTrue(inner instanceof JsonObject || inner instanceof Map, "Deep nested should be JsonObject/Map");
        
        // With LinkedHashMap
        ReadOptions linkedHashMapOptions = new ReadOptionsBuilder()
                .unknownTypeClass(LinkedHashMap.class)
                .build();
        Object linkedHashMapResult = JsonIo.toJava(json, linkedHashMapOptions).asClass(Object.class);
        
        assertTrue(linkedHashMapResult instanceof LinkedHashMap, "Root should be LinkedHashMap");
        Map<?, ?> outerLHM = (Map<?, ?>) ((Map<?, ?>) linkedHashMapResult).get("outer");
        assertTrue(outerLHM instanceof LinkedHashMap, "Nested should be LinkedHashMap");
        Map<?, ?> innerLHM = (Map<?, ?>) outerLHM.get("inner");
        assertTrue(innerLHM instanceof LinkedHashMap, "Deep nested should be LinkedHashMap");
    }
    
    @Test
    public void testToObjectsMethod() {
        // Test the toObjects method mentioned in the issue
        String json = "{\"test\":{}}";
        ReadOptions options = new ReadOptionsBuilder().build();
        
        // Note: JsonIo doesn't have a toObjects method, using toJava instead
        Object result = JsonIo.toJava(json, options).asClass(Object.class);
        
        System.out.println("toJava result: " + result);
        System.out.println("Result class: " + result.getClass().getName());
        
        // Verify actual behavior
        assertTrue(result instanceof JsonObject, 
                "toJava with Object.class returns JsonObject by default");
    }
    
    @Test
    public void testDocumentationConsistency() {
        // This test documents what the behavior SHOULD be according to documentation
        // The documentation says default unknown type is LinkedHashMap
        
        String json = "{\"unknownField\":{\"nested\":\"value\"}}";
        ReadOptions options = new ReadOptionsBuilder().build();
        
        Object result = JsonIo.toJava(json, options).asClass(Object.class);
        
        // Documentation has been updated to reflect actual behavior:
        assertTrue(result instanceof JsonObject, "Default is JsonObject as now documented");
        assertFalse(result instanceof LinkedHashMap, "Default is not LinkedHashMap");
    }
}