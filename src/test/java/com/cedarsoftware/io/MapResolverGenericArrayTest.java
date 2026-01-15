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

    // ========================================================================
    // Tests for traverseFields() coverage in MapResolver
    // ========================================================================

    /**
     * Test null field value handling in Maps mode with @type.
     * This exercises lines 340-341 in MapResolver.traverseFields().
     * When a field has null value, it should be preserved in the Map.
     */
    @Test
    void testNullFieldValueInMapsMode() {
        // JSON with @type and a null field value
        String json = "{\"@type\":\"com.cedarsoftware.io.models.Race\",\"name\":null,\"age\":25}";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        assertTrue(result instanceof Map, "Result should be a Map");

        Map<?, ?> map = (Map<?, ?>) result;
        assertTrue(map.containsKey("name"), "Map should contain 'name' key");
        assertNull(map.get("name"), "name field should be null");
        // With @type, int field is coerced to Integer
        assertEquals(25, map.get("age"), "age field should be 25");
    }

    /**
     * Test multiple null field values in Maps mode.
     * This exercises lines 340-341 in MapResolver.traverseFields().
     */
    @Test
    void testMultipleNullFieldsInMapsMode() {
        String json = "{\"@type\":\"com.cedarsoftware.io.models.Race\",\"name\":null,\"age\":30,\"strength\":null,\"wisdom\":100}";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;

        assertNull(map.get("name"), "name should be null");
        assertEquals(30, map.get("age"));
        assertNull(map.get("strength"), "strength should be null");
        assertEquals(100, map.get("wisdom"));
    }

    /**
     * Test empty string is preserved for String field (not converted to null).
     * Verifies that String fields keep empty strings.
     */
    @Test
    void testEmptyStringPreservedForStringFieldInMapsMode() {
        String json = "{\"@type\":\"com.cedarsoftware.io.models.Race\",\"name\":\"\",\"age\":25}";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;

        // Empty string for String field should be preserved, not converted to null
        assertEquals("", map.get("name"), "Empty string for String field should be preserved");
        assertEquals(25, map.get("age"));
    }

    /**
     * Test JsonObject value for non-referenceable type field in Maps mode.
     * This exercises lines 362-363 in MapResolver.traverseFields().
     * When a field has a JsonObject value and the injector type is non-referenceable,
     * the value is converted directly.
     */
    @Test
    void testJsonObjectValueForIntFieldInMapsMode() {
        // JSON with nested @type for int field - this creates a JsonObject for the value
        String json = "{\"@type\":\"com.cedarsoftware.io.models.Race\",\"name\":\"Hero\",\"age\":{\"@type\":\"int\",\"value\":30}}";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;

        assertEquals("Hero", map.get("name"));
        // The nested JsonObject should be converted to the appropriate value
        assertNotNull(map.get("age"));
    }

    /**
     * Test JsonObject value for String field (non-referenceable) in Maps mode.
     * This exercises lines 362-363 in MapResolver.traverseFields().
     */
    @Test
    void testJsonObjectValueForStringFieldInMapsMode() {
        // JSON with nested @type for String field
        String json = "{\"@type\":\"com.cedarsoftware.io.models.Race\",\"name\":{\"@type\":\"java.lang.String\",\"value\":\"TestName\"},\"age\":25}";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;

        assertEquals(25, map.get("age"));
        // The nested JsonObject for String should be converted
        assertNotNull(map.get("name"));
    }

    /**
     * Test empty string to null fallback for custom type field without String converter.
     * This exercises the fallback path in MapResolver.traverseFields() where
     * converter.isConversionSupportedFor(String.class, fieldType) returns false.
     */
    @Test
    void testEmptyStringToNullForCustomTypeField() {
        // JSON with @type and empty string for a custom type field
        // The CustomType class doesn't have String conversion support in the Converter
        String json = "{\"@type\":\"com.cedarsoftware.io.models.CustomTypeHolder\",\"name\":\"Test\",\"custom\":\"\"}";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;

        assertEquals("Test", map.get("name"));
        // Empty string for custom type field should be converted to null
        // because the converter doesn't support String->CustomType conversion
        assertNull(map.get("custom"), "Empty string for non-convertible type should become null");
    }

    /**
     * Test whitespace string to null fallback for custom type field.
     */
    @Test
    void testWhitespaceStringToNullForCustomTypeField() {
        String json = "{\"@type\":\"com.cedarsoftware.io.models.CustomTypeHolder\",\"name\":\"Test\",\"custom\":\"   \"}";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;

        assertEquals("Test", map.get("name"));
        // Whitespace string for custom type field should be converted to null
        assertNull(map.get("custom"), "Whitespace string for non-convertible type should become null");
    }

    // ========================================================================
    // Tests for coerceLong and coerceDouble coverage in MapResolver
    // ========================================================================

    /**
     * Test coerceLong converting Long (JSON integer) to double field.
     * This exercises line 691 in MapResolver.coerceLong().
     */
    @Test
    void testCoerceLongToDoubleInMapsMode() {
        // JSON with integer value for a double field - triggers Long -> double coercion
        String json = "{\"@type\":\"com.cedarsoftware.io.models.NumericCoercionHolder\",\"doubleField\":42}";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;

        // Integer 42 should be coerced to double 42.0
        Object value = map.get("doubleField");
        assertNotNull(value, "doubleField should not be null");
        assertEquals(Double.class, value.getClass(), "Should be coerced to Double");
        assertEquals(42.0, value, "Value should be 42.0");
    }

    /**
     * Test coerceLong converting Long (JSON integer) to float field.
     * This exercises line 693 in MapResolver.coerceLong().
     */
    @Test
    void testCoerceLongToFloatInMapsMode() {
        // JSON with integer value for a float field - triggers Long -> float coercion
        String json = "{\"@type\":\"com.cedarsoftware.io.models.NumericCoercionHolder\",\"floatField\":123}";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;

        // Integer 123 should be coerced to float 123.0f
        Object value = map.get("floatField");
        assertNotNull(value, "floatField should not be null");
        assertEquals(Float.class, value.getClass(), "Should be coerced to Float");
        assertEquals(123.0f, value, "Value should be 123.0f");
    }

    /**
     * Test coerceDouble converting Double (JSON decimal) to long field.
     * This exercises line 705 in MapResolver.coerceDouble().
     */
    @Test
    void testCoerceDoubleToLongInMapsMode() {
        // JSON with decimal value for a long field - triggers Double -> long coercion
        String json = "{\"@type\":\"com.cedarsoftware.io.models.NumericCoercionHolder\",\"longField\":99.7}";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;

        // Decimal 99.7 should be coerced to long 99 (truncated)
        Object value = map.get("longField");
        assertNotNull(value, "longField should not be null");
        assertEquals(Long.class, value.getClass(), "Should be coerced to Long");
        assertEquals(99L, value, "Value should be 99 (truncated from 99.7)");
    }

    /**
     * Test coerceDouble converting Double (JSON decimal) to int field.
     * This exercises line 707 in MapResolver.coerceDouble().
     */
    @Test
    void testCoerceDoubleToIntInMapsMode() {
        // JSON with decimal value for an int field - triggers Double -> int coercion
        String json = "{\"@type\":\"com.cedarsoftware.io.models.NumericCoercionHolder\",\"intField\":55.9}";

        Object result = JsonIo.toMaps(json, null).asClass(null);

        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;

        // Decimal 55.9 should be coerced to int 55 (truncated)
        Object value = map.get("intField");
        assertNotNull(value, "intField should not be null");
        assertEquals(Integer.class, value.getClass(), "Should be coerced to Integer");
        assertEquals(55, value, "Value should be 55 (truncated from 55.9)");
    }
}
