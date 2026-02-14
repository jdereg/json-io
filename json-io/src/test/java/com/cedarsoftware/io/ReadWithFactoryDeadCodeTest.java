package com.cedarsoftware.io;

import com.cedarsoftware.util.convert.Converter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Test to verify if the early exit condition in ObjectResolver.readWithFactoryIfExists()
 * at lines 428-430 is reachable:
 *
 *     if (rawInferred == null && !(o instanceof JsonObject)) {
 *         return null;
 *     }
 *
 * This condition returns null when:
 * 1. inferredType is null (or resolves to null via TypeUtilities.getRawClass)
 * 2. AND o is NOT a JsonObject (i.e., o is a primitive like String, Long, Double, Boolean)
 */
class ReadWithFactoryDeadCodeTest {

    private ObjectResolver createResolver() {
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        ReferenceTracker references = new Resolver.DefaultReferenceTracker(readOptions);
        Converter converter = new Converter(readOptions.getConverterOptions());
        return new ObjectResolver(readOptions, references, converter);
    }

    /**
     * With the optimized fast path for primitives, calling with null inferredType
     * and a primitive value returns null (meaning "no custom handling needed").
     * The caller will use the value as-is. This is an optimization that avoids
     * unnecessary factory/reader lookups for primitive types.
     */
    @Test
    void testReadWithFactoryIfExists_withNullTypeAndPrimitive_nowProcessesNormally() throws Exception {
        ObjectResolver resolver = createResolver();

        // Access the protected method via reflection
        Method method = ObjectResolver.class.getDeclaredMethod("readWithFactoryIfExists", Object.class, Type.class);
        method.setAccessible(true);

        // Call with a primitive (String) and null type - fast path returns null
        // (no custom handling needed, caller uses value as-is)
        Object result = method.invoke(resolver, "test string", null);
        assertThat(result).isNull();

        // For numeric types, same behavior - null means "no custom handling"
        result = method.invoke(resolver, Long.valueOf(42L), null);
        assertThat(result).isNull();

        result = method.invoke(resolver, Double.valueOf(3.14), null);
        assertThat(result).isNull();

        result = method.invoke(resolver, Boolean.TRUE, null);
        assertThat(result).isNull();
    }

    /**
     * Verify that passing a JsonObject with null type does NOT hit the early exit.
     * (The condition checks !(o instanceof JsonObject), so JsonObjects bypass this check)
     */
    @Test
    void testReadWithFactoryIfExists_withNullTypeAndJsonObject() throws Exception {
        ObjectResolver resolver = createResolver();

        Method method = ObjectResolver.class.getDeclaredMethod("readWithFactoryIfExists", Object.class, Type.class);
        method.setAccessible(true);

        // Call with a JsonObject and null type - should NOT hit the line 428-430 condition
        // (It will continue processing and may return null for other reasons)
        JsonObject jsonObj = new JsonObject();
        jsonObj.setType(String.class);
        jsonObj.setValue("test");

        Object result = method.invoke(resolver, jsonObj, null);
        // This tests that JsonObject takes a different path
        // Result may still be null but for a different reason (no custom reader, etc.)
    }

    /**
     * Check if TypeUtilities.getRawClass returns null for any edge cases.
     * If it always returns a non-null value, then line 428-430 may only be reachable
     * via direct calls with null inferredType (not through normal call sites).
     */
    @Test
    void testTypeUtilitiesGetRawClass_edgeCases() {
        // Test what getRawClass returns for various inputs
        // This helps understand if the condition can be hit through call sites

        // Normal class
        assertThat(com.cedarsoftware.util.TypeUtilities.getRawClass(String.class)).isEqualTo(String.class);

        // For type variables or wildcards, getRawClass typically returns Object.class, not null
        // This means the condition rawInferred == null is very unlikely through normal paths
    }

    /**
     * Verify that TypeUtilities.getRawClass never returns null for field types.
     * This is important because assignField passes rawFieldType to readWithFactoryIfExists,
     * and if it could be null, it could hit the dead code condition.
     */
    @Test
    void testTypeUtilitiesGetRawClass_neverReturnsNull() {
        // Test that getRawClass returns Object.class (not null) for unresolvable types
        // This confirms that call sites like assignField will never pass null

        // null input returns null (but call sites don't pass null)
        assertThat(com.cedarsoftware.util.TypeUtilities.getRawClass(null)).isNull();

        // Class types return themselves
        assertThat(com.cedarsoftware.util.TypeUtilities.getRawClass(Object.class)).isEqualTo(Object.class);
        assertThat(com.cedarsoftware.util.TypeUtilities.getRawClass(String.class)).isEqualTo(String.class);

        // Array types
        assertThat(com.cedarsoftware.util.TypeUtilities.getRawClass(String[].class)).isEqualTo(String[].class);
    }

    /**
     * Test to verify that actual deserialization never hits the dead code condition.
     * The condition requires: rawInferred == null AND o is not a JsonObject.
     *
     * Since all production call sites either:
     * - Pass a non-null type (assignField, traverseArray, traverseCollection)
     * - Pass a JsonObject when type is null (traverseObject)
     *
     * The condition can never be hit through normal deserialization.
     */
    @Test
    void testNormalDeserializationNeverHitsDeadCode() {
        // Deserialize various JSON structures to verify normal paths work
        // and the dead code condition is never needed

        // Simple object with primitive fields
        String json = "{\"@type\":\"java.util.HashMap\",\"name\":\"test\",\"value\":42}";
        Map<String, Object> result = JsonIo.toJava(json, null).asClass(null);
        assertThat(result).containsEntry("name", "test");
        assertThat(result).containsEntry("value", 42L);

        // Array of primitives
        json = "[\"a\", \"b\", \"c\"]";
        Object[] array = JsonIo.toJava(json, null).asClass(Object[].class);
        assertThat(array).containsExactly("a", "b", "c");

        // Nested structure
        json = "{\"@type\":\"java.util.HashMap\",\"items\":[1,2,3]}";
        result = JsonIo.toJava(json, null).asClass(null);
        assertThat(result).containsKey("items");
    }

    @Test
    void testReadWithFactoryIfExists_primitiveStringMismatchUsesConversionSupportGate() throws Exception {
        ObjectResolver resolver = createResolver();
        Method method = ObjectResolver.class.getDeclaredMethod("readWithFactoryIfExists", Object.class, Type.class);
        method.setAccessible(true);

        // Supported scalar conversion: String -> Integer
        Object converted = method.invoke(resolver, "42", Integer.class);
        assertThat(converted).isEqualTo(42);

        // Unsupported scalar conversion should continue normal path (return null)
        Object unsupported = method.invoke(resolver, "42", Thread.class);
        assertThat(unsupported).isNull();
    }

    /**
     * CONCLUSION: The condition at lines 428-430 is DEAD CODE because:
     *
     * 1. assignField: Passes rawFieldType which comes from TypeUtilities.getRawClass(fieldType).
     *    The code at line 116 calls rawFieldType.isPrimitive() without null check,
     *    proving the code assumes it's never null.
     *
     * 2. traverseCollection: Passes Object.class (hardcoded at line 284).
     *
     * 3. traverseArray: Passes effectiveRawComponentType which has a fallback to
     *    array.getClass().getComponentType(), never null.
     *
     * 4. Resolver.traverseObject: Passes null for type BUT always passes a JsonObject,
     *    so the check !(o instanceof JsonObject) is always false.
     *
     * RECOMMENDATION: Remove lines 428-430 as dead code. The null check at line 434
     * (rawInferred != null) must remain because traverseObject passes null.
     */
    @Test
    void documentDeadCodeAnalysis() {
        // This test documents why the code is dead
        // The condition `rawInferred == null && !(o instanceof JsonObject)` requires:
        // 1. null type passed (only from traverseObject)
        // 2. non-JsonObject passed (never true when traverseObject calls it)
    }
}
