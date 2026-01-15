package com.cedarsoftware.io;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MapResolver coverage including:
 * - GenericArrayType handling in getUltimateComponentType()
 * - reconcileResult() method for simple types and JsonObjects
 *
 * @author Claude Opus 4.5
 */
class MapResolverGenericArrayTest {

    /**
     * Test that a generic array type like List&lt;String&gt;[] is properly handled in Maps mode.
     * This exercises the GenericArrayType branch in getUltimateComponentType().
     */
    @Test
    void testGenericArrayTypeInMapsMode() {
        // Create JSON representing an array of lists
        String json = "[[\"a\", \"b\"], [\"c\", \"d\"]]";

        // Parse using a generic array type - this triggers the GenericArrayType code path
        // because List<String>[] is represented as GenericArrayType at runtime
        Object result = JsonIo.toMaps(json, null).asType(new TypeHolder<List<String>[]>() {});

        // Verify the result is an array
        assertNotNull(result);
        assertTrue(result.getClass().isArray(), "Result should be an array");

        // Verify it's an Object array (in Maps mode, elements are Lists/JsonObjects)
        Object[] array = (Object[]) result;
        assertEquals(2, array.length, "Array should have 2 elements");

        // Verify each element is a List
        assertTrue(array[0] instanceof List, "First element should be a List");
        assertTrue(array[1] instanceof List, "Second element should be a List");

        // Verify the contents
        List<?> first = (List<?>) array[0];
        List<?> second = (List<?>) array[1];
        assertEquals(2, first.size());
        assertEquals(2, second.size());
        assertEquals("a", first.get(0));
        assertEquals("b", first.get(1));
        assertEquals("c", second.get(0));
        assertEquals("d", second.get(1));
    }

    /**
     * Test generic array type with Map elements using Java mode.
     * This exercises GenericArrayType in ObjectResolver's type processing.
     */
    @Test
    void testGenericMapArrayTypeInJavaMode() {
        // Create JSON representing an array of maps
        String json = "[{\"a\": 1, \"b\": 2}, {\"c\": 3, \"d\": 4}]";

        // Parse using Java mode (toJava) with a generic Map array type
        Map<String, Integer>[] result = JsonIo.toJava(json, null).asType(new TypeHolder<Map<String, Integer>[]>() {});

        assertNotNull(result);
        assertEquals(2, result.length, "Array should have 2 elements");

        // Verify each element is a Map with correct content
        assertNotNull(result[0], "First element should not be null");
        assertNotNull(result[1], "Second element should not be null");

        assertEquals(2, result[0].size());
        assertEquals(2, result[1].size());
        // JSON numbers are parsed as Long by default
        assertEquals(Long.valueOf(1), result[0].get("a"));
        assertEquals(Long.valueOf(2), result[0].get("b"));
        assertEquals(Long.valueOf(3), result[1].get("c"));
        assertEquals(Long.valueOf(4), result[1].get("d"));
    }

    /**
     * Verify that TypeHolder for generic array types actually produces GenericArrayType.
     * This is a sanity check to ensure our test methodology is correct.
     */
    @Test
    void testTypeHolderProducesGenericArrayType() {
        TypeHolder<List<String>[]> holder = new TypeHolder<List<String>[]>() {};
        Type type = holder.getType();

        assertTrue(type instanceof GenericArrayType,
                "TypeHolder<List<String>[]> should produce GenericArrayType, but got: " + type.getClass().getName());

        GenericArrayType gat = (GenericArrayType) type;
        Type componentType = gat.getGenericComponentType();

        // The component type should be a ParameterizedType (List<String>)
        assertNotNull(componentType);
        assertTrue(componentType.toString().contains("List"),
                "Component type should be List<String>, but got: " + componentType);
    }

    /**
     * Test empty generic array in Maps mode.
     */
    @Test
    void testEmptyGenericArrayInMapsMode() {
        String json = "[]";

        Object result = JsonIo.toMaps(json, null).asType(new TypeHolder<List<String>[]>() {});

        assertNotNull(result);
        assertTrue(result.getClass().isArray(), "Result should be an array");
        assertEquals(0, ((Object[]) result).length, "Array should be empty");
    }

    /**
     * Test generic array with null elements in Maps mode.
     */
    @Test
    void testGenericArrayWithNullsInMapsMode() {
        String json = "[[\"a\"], null, [\"b\"]]";

        Object result = JsonIo.toMaps(json, null).asType(new TypeHolder<List<String>[]>() {});

        assertNotNull(result);
        Object[] array = (Object[]) result;
        assertEquals(3, array.length);
        assertNotNull(array[0]);
        assertNull(array[1]);
        assertNotNull(array[2]);
    }

    /**
     * Test that a generic array type works in Java mode (toJava).
     * This exercises the GenericArrayType path in ObjectResolver.
     */
    @Test
    void testGenericListArrayInJavaMode() {
        String json = "[[\"x\", \"y\"], [\"z\"]]";

        List<String>[] result = JsonIo.toJava(json, null).asType(new TypeHolder<List<String>[]>() {});

        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals(2, result[0].size());
        assertEquals(1, result[1].size());
        assertEquals("x", result[0].get(0));
        assertEquals("y", result[0].get(1));
        assertEquals("z", result[1].get(0));
    }

    // ========================================================================
    // Tests for Maps mode parsing of simple JSON values
    // ========================================================================

    /**
     * Test parsing simple String JSON value in Maps mode.
     */
    @Test
    void testMapsModeParsesSimpleString() {
        String json = "\"hello world\"";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        assertEquals(String.class, result.getClass(), "Result should be a String");
        assertEquals("hello world", result);
    }

    /**
     * Test parsing simple Long JSON value in Maps mode.
     */
    @Test
    void testMapsModeParsesSimpleLong() {
        String json = "12345";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        assertEquals(Long.class, result.getClass(), "Result should be a Long");
        assertEquals(12345L, result);
    }

    /**
     * Test parsing simple Double JSON value in Maps mode.
     */
    @Test
    void testMapsModeParsesSimpleDouble() {
        String json = "3.14159";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        assertEquals(Double.class, result.getClass(), "Result should be a Double");
        assertEquals(3.14159, result);
    }

    /**
     * Test parsing simple Boolean JSON value in Maps mode.
     */
    @Test
    void testMapsModeParsesSimpleBoolean() {
        String json = "true";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        assertEquals(Boolean.class, result.getClass(), "Result should be a Boolean");
        assertEquals(true, result);
    }

    /**
     * Test parsing JSON object returns Map in Maps mode.
     */
    @Test
    void testMapsModeReturnsMapForJsonObject() {
        String json = "{\"name\": \"John\", \"age\": 30}";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        assertTrue(result instanceof Map, "Result should be a Map (JsonObject)");

        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals("John", map.get("name"));
        assertEquals(30L, map.get("age"));
    }

    /**
     * Test parsing empty JSON object returns empty Map in Maps mode.
     */
    @Test
    void testMapsModeReturnsEmptyMapForEmptyObject() {
        String json = "{}";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        assertTrue(result instanceof Map, "Result should be a Map (JsonObject)");
        assertTrue(((Map<?, ?>) result).isEmpty(), "Map should be empty");
    }

    /**
     * Test parsing JSON null returns null in Maps mode.
     */
    @Test
    void testMapsModeReturnsNullForJsonNull() {
        String json = "null";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNull(result, "Result should be null");
    }

    // ========================================================================
    // Tests for getJsonSynonymType() - Atomic type mappings (lines 280, 282, 284)
    // ========================================================================

    /**
     * Test AtomicInteger @type is converted to Integer in Maps mode.
     * This exercises line 280 in MapResolver.getJsonSynonymType().
     */
    @Test
    void testAtomicIntegerTypeConvertedToIntegerInMapsMode() {
        // JSON with @type = AtomicInteger
        String json = "{\"@type\":\"java.util.concurrent.atomic.AtomicInteger\",\"value\":42}";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        // In Maps mode with AtomicInteger @type, getJsonSynonymType maps it to Integer
        assertEquals(Integer.class, result.getClass(), "AtomicInteger should be converted to Integer");
        assertEquals(42, result);
    }

    /**
     * Test AtomicLong @type is converted to Long in Maps mode.
     * This exercises line 282 in MapResolver.getJsonSynonymType().
     */
    @Test
    void testAtomicLongTypeConvertedToLongInMapsMode() {
        // JSON with @type = AtomicLong
        String json = "{\"@type\":\"java.util.concurrent.atomic.AtomicLong\",\"value\":123456789}";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        // In Maps mode with AtomicLong @type, getJsonSynonymType maps it to Long
        assertEquals(Long.class, result.getClass(), "AtomicLong should be converted to Long");
        assertEquals(123456789L, result);
    }

    /**
     * Test AtomicBoolean @type is converted to Boolean in Maps mode.
     * This exercises line 284 in MapResolver.getJsonSynonymType().
     */
    @Test
    void testAtomicBooleanTypeConvertedToBooleanInMapsMode() {
        // JSON with @type = AtomicBoolean
        String json = "{\"@type\":\"java.util.concurrent.atomic.AtomicBoolean\",\"value\":true}";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        // In Maps mode with AtomicBoolean @type, getJsonSynonymType maps it to Boolean
        assertEquals(Boolean.class, result.getClass(), "AtomicBoolean should be converted to Boolean");
        assertEquals(true, result);
    }

    /**
     * Test AtomicBoolean with false value in Maps mode.
     */
    @Test
    void testAtomicBooleanFalseInMapsMode() {
        String json = "{\"@type\":\"java.util.concurrent.atomic.AtomicBoolean\",\"value\":false}";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        assertEquals(Boolean.class, result.getClass(), "AtomicBoolean should be converted to Boolean");
        assertEquals(false, result);
    }
}
