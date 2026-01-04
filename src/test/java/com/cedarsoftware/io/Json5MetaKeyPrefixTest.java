package com.cedarsoftware.io;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JSON5 meta key prefix handling.
 *
 * In JSON5 mode, meta keys use $ prefix ($type, $id, $ref, $items, $keys)
 * which are valid unquoted ECMAScript identifiers.
 *
 * In standard JSON mode, meta keys use @ prefix ("@type", "@id", etc.)
 * which must be quoted since @ is not a valid identifier character.
 *
 * When reading, both prefixes are accepted for backward compatibility.
 */
class Json5MetaKeyPrefixTest {

    // ========================================
    // Writing tests - JSON5 mode uses $ prefix
    // ========================================

    @Test
    void testJson5WriteUsesDollarPrefixForType() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .json5()
                .build();

        TestObject obj = new TestObject("test", 42);
        String json = JsonIo.toJson(obj, writeOptions);

        // Should use $type: not "@type":
        assertTrue(json.contains("$type:"), "JSON5 mode should use $type: prefix");
        assertFalse(json.contains("\"@type\":"), "JSON5 mode should not use quoted @type");
    }

    @Test
    void testJson5WriteUsesDollarPrefixForId() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .json5()
                .build();

        // Create a self-referencing object to force @id/@ref usage
        SelfRefObject obj = new SelfRefObject();
        obj.self = obj;

        String json = JsonIo.toJson(obj, writeOptions);

        // Should use $id: not "@id":
        assertTrue(json.contains("$id:"), "JSON5 mode should use $id: prefix");
        assertFalse(json.contains("\"@id\":"), "JSON5 mode should not use quoted @id");
    }

    @Test
    void testJson5WriteUsesDollarPrefixForRef() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .json5()
                .build();

        // Create a self-referencing object to force @ref usage
        SelfRefObject obj = new SelfRefObject();
        obj.self = obj;

        String json = JsonIo.toJson(obj, writeOptions);

        // Should use $ref: not "@ref":
        assertTrue(json.contains("$ref:"), "JSON5 mode should use $ref: prefix");
        assertFalse(json.contains("\"@ref\":"), "JSON5 mode should not use quoted @ref");
    }

    @Test
    void testJson5WriteUsesDollarPrefixForItemsAndKeys() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .json5()
                .build();

        // Create a Map with non-String keys to force @keys/@items usage
        Map<Integer, String> map = new LinkedHashMap<>();
        map.put(1, "one");
        map.put(2, "two");
        map.put(3, "three");

        String json = JsonIo.toJson(map, writeOptions);

        // Should use $keys: and $items: not quoted versions
        assertTrue(json.contains("$keys:"), "JSON5 mode should use $keys: prefix");
        assertTrue(json.contains("$items:"), "JSON5 mode should use $items: prefix");
        assertFalse(json.contains("\"@keys\":"), "JSON5 mode should not use quoted @keys");
        assertFalse(json.contains("\"@items\":"), "JSON5 mode should not use quoted @items");
    }

    @Test
    void testStandardModeUsesAtPrefixForType() {
        WriteOptions writeOptions = new WriteOptionsBuilder().build();

        TestObject obj = new TestObject("test", 42);
        String json = JsonIo.toJson(obj, writeOptions);

        // Should use "@type": not $type:
        assertTrue(json.contains("\"@type\":"), "Standard mode should use quoted @type");
        assertFalse(json.contains("$type:"), "Standard mode should not use $type prefix");
    }

    @Test
    void testStandardModeUsesAtPrefixForIdAndRef() {
        WriteOptions writeOptions = new WriteOptionsBuilder().build();

        SelfRefObject obj = new SelfRefObject();
        obj.self = obj;

        String json = JsonIo.toJson(obj, writeOptions);

        // Should use "@id": and "@ref": not $ versions
        assertTrue(json.contains("\"@id\":"), "Standard mode should use quoted @id");
        assertTrue(json.contains("\"@ref\":"), "Standard mode should use quoted @ref");
        assertFalse(json.contains("$id:"), "Standard mode should not use $id prefix");
        assertFalse(json.contains("$ref:"), "Standard mode should not use $ref prefix");
    }

    // ========================================
    // Reading tests - Accept both @ and $ prefixes
    // ========================================

    @Test
    void testReadDollarTypePrefix() {
        String json = "{$type:\"com.cedarsoftware.io.Json5MetaKeyPrefixTest$TestObject\",name:\"test\",value:42}";

        Object result = JsonIo.toJava(json, null).asClass(Object.class);

        assertTrue(result instanceof TestObject, "Expected TestObject but got " + result.getClass().getName());
        TestObject obj = (TestObject) result;
        assertEquals("test", obj.name);
        assertEquals(42, obj.value);
    }

    @Test
    void testReadAtTypePrefix() {
        String json = "{\"@type\":\"com.cedarsoftware.io.Json5MetaKeyPrefixTest$TestObject\",\"name\":\"test\",\"value\":42}";

        Object result = JsonIo.toJava(json, null).asClass(Object.class);

        assertTrue(result instanceof TestObject, "Expected TestObject but got " + result.getClass().getName());
        TestObject obj = (TestObject) result;
        assertEquals("test", obj.name);
        assertEquals(42, obj.value);
    }

    @Test
    void testReadDollarIdAndRefPrefix() {
        // JSON with $id and $ref
        String json = "{$type:\"com.cedarsoftware.io.Json5MetaKeyPrefixTest$SelfRefObject\",$id:1,self:{$ref:1}}";

        Object result = JsonIo.toJava(json, null).asClass(Object.class);

        assertTrue(result instanceof SelfRefObject, "Expected SelfRefObject but got " + result.getClass().getName());
        SelfRefObject obj = (SelfRefObject) result;
        assertSame(obj, obj.self, "self reference should point to same object");
    }

    @Test
    void testReadAtIdAndRefPrefix() {
        // JSON with @id and @ref (quoted)
        String json = "{\"@type\":\"com.cedarsoftware.io.Json5MetaKeyPrefixTest$SelfRefObject\",\"@id\":1,\"self\":{\"@ref\":1}}";

        Object result = JsonIo.toJava(json, null).asClass(Object.class);

        assertTrue(result instanceof SelfRefObject, "Expected SelfRefObject but got " + result.getClass().getName());
        SelfRefObject obj = (SelfRefObject) result;
        assertSame(obj, obj.self, "self reference should point to same object");
    }

    @Test
    void testReadDollarKeysAndItemsPrefix() {
        // JSON with $keys and $items for Map with non-String keys
        String json = "{$type:\"java.util.LinkedHashMap\",$keys:[1,2,3],$items:[\"one\",\"two\",\"three\"]}";

        ReadOptions readOptions = new ReadOptionsBuilder().build();
        Object result = JsonIo.toJava(json, readOptions).asClass(Object.class);

        assertTrue(result instanceof LinkedHashMap, "Expected LinkedHashMap but got " + result.getClass().getName());
        @SuppressWarnings("unchecked")
        Map<Object, String> map = (Map<Object, String>) result;
        assertEquals(3, map.size());
        assertEquals("one", map.get(1L));  // Numbers read as Long by default
        assertEquals("two", map.get(2L));
        assertEquals("three", map.get(3L));
    }

    @Test
    void testReadAtKeysAndItemsPrefix() {
        // JSON with @keys and @items (quoted)
        String json = "{\"@type\":\"java.util.LinkedHashMap\",\"@keys\":[1,2,3],\"@items\":[\"one\",\"two\",\"three\"]}";

        ReadOptions readOptions = new ReadOptionsBuilder().build();
        Object result = JsonIo.toJava(json, readOptions).asClass(Object.class);

        assertTrue(result instanceof LinkedHashMap, "Expected LinkedHashMap but got " + result.getClass().getName());
        @SuppressWarnings("unchecked")
        Map<Object, String> map = (Map<Object, String>) result;
        assertEquals(3, map.size());
        assertEquals("one", map.get(1L));
        assertEquals("two", map.get(2L));
        assertEquals("three", map.get(3L));
    }

    // ========================================
    // Round-trip tests
    // ========================================

    @Test
    void testJson5RoundTripWithType() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .json5()
                .build();

        TestObject original = new TestObject("round-trip", 123);
        String json = JsonIo.toJson(original, writeOptions);

        // Verify JSON5 format
        assertTrue(json.contains("$type:"), "Should write with $type prefix");

        // Read back
        TestObject restored = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(TestObject.class);
        assertEquals(original.name, restored.name);
        assertEquals(original.value, restored.value);
    }

    @Test
    void testJson5RoundTripWithReferences() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .json5()
                .build();

        SelfRefObject original = new SelfRefObject();
        original.self = original;

        String json = JsonIo.toJson(original, writeOptions);

        // Verify JSON5 format
        assertTrue(json.contains("$id:"), "Should write with $id prefix");
        assertTrue(json.contains("$ref:"), "Should write with $ref prefix");

        // Read back
        SelfRefObject restored = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(SelfRefObject.class);
        assertSame(restored, restored.self, "Self reference should be restored");
    }

    @Test
    void testJson5RoundTripWithNonStringKeyMap() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .json5()
                .build();

        Map<Integer, String> original = new LinkedHashMap<>();
        original.put(10, "ten");
        original.put(20, "twenty");

        String json = JsonIo.toJson(original, writeOptions);

        // Verify JSON5 format
        assertTrue(json.contains("$keys:"), "Should write with $keys prefix");
        assertTrue(json.contains("$items:"), "Should write with $items prefix");

        // Read back
        @SuppressWarnings("unchecked")
        Map<Object, String> restored = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(LinkedHashMap.class);
        assertEquals(2, restored.size());

        // Check values exist (keys may be Integer or Long depending on type preservation)
        assertTrue(restored.containsValue("ten"), "Should contain value 'ten'");
        assertTrue(restored.containsValue("twenty"), "Should contain value 'twenty'");
    }

    @Test
    void testShortMetaKeysStillWorkWithAtPrefix() {
        // Short meta keys (@t, @i, @r, @e, @k) should still use @ prefix
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .shortMetaKeys(true)
                .build();

        TestObject obj = new TestObject("test", 42);
        String json = JsonIo.toJson(obj, writeOptions);

        // Should use short "@t": format
        assertTrue(json.contains("\"@t\":"), "Short meta keys should use @t");
        assertFalse(json.contains("$t:"), "Short meta keys should not use $t");
    }

    @Test
    void testReadShortMetaKeysWithAtPrefix() {
        // Verify reading short meta keys works
        String json = "{\"@t\":\"com.cedarsoftware.io.Json5MetaKeyPrefixTest$TestObject\",\"name\":\"test\",\"value\":42}";

        Object result = JsonIo.toJava(json, null).asClass(Object.class);

        assertTrue(result instanceof TestObject, "Expected TestObject but got " + result.getClass().getName());
        TestObject obj = (TestObject) result;
        assertEquals("test", obj.name);
        assertEquals(42, obj.value);
    }

    // ========================================
    // JSON5 + Short Meta Keys tests
    // ========================================

    @Test
    void testJson5ShortMetaKeysWriteUsesDollarShortPrefixForType() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .json5()
                .shortMetaKeys(true)
                .build();

        TestObject obj = new TestObject("test", 42);
        String json = JsonIo.toJson(obj, writeOptions);

        // Should use $t: not $type: or "@t":
        assertTrue(json.contains("$t:"), "JSON5 + short mode should use $t: prefix, got: " + json);
        assertFalse(json.contains("$type:"), "JSON5 + short mode should not use $type: prefix");
        assertFalse(json.contains("\"@t\":"), "JSON5 + short mode should not use quoted @t");
        assertFalse(json.contains("\"@type\":"), "JSON5 + short mode should not use quoted @type");
    }

    @Test
    void testJson5ShortMetaKeysWriteUsesDollarShortPrefixForIdAndRef() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .json5()
                .shortMetaKeys(true)
                .build();

        // Create a self-referencing object to force @id/@ref usage
        SelfRefObject obj = new SelfRefObject();
        obj.self = obj;

        String json = JsonIo.toJson(obj, writeOptions);

        // Should use $i: and $r: not $id:/$ref: or "@i":/"@r":
        assertTrue(json.contains("$i:"), "JSON5 + short mode should use $i: prefix, got: " + json);
        assertTrue(json.contains("$r:"), "JSON5 + short mode should use $r: prefix, got: " + json);
        assertFalse(json.contains("$id:"), "JSON5 + short mode should not use $id: prefix");
        assertFalse(json.contains("$ref:"), "JSON5 + short mode should not use $ref: prefix");
        assertFalse(json.contains("\"@i\":"), "JSON5 + short mode should not use quoted @i");
        assertFalse(json.contains("\"@r\":"), "JSON5 + short mode should not use quoted @r");
    }

    @Test
    void testJson5ShortMetaKeysWriteUsesDollarShortPrefixForItemsAndKeys() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .json5()
                .shortMetaKeys(true)
                .build();

        // Create a Map with non-String keys to force @keys/@items usage
        Map<Integer, String> map = new LinkedHashMap<>();
        map.put(1, "one");
        map.put(2, "two");
        map.put(3, "three");

        String json = JsonIo.toJson(map, writeOptions);

        // Should use $k: and $e: not $keys:/$items: or "@k":/"@e":
        assertTrue(json.contains("$k:"), "JSON5 + short mode should use $k: prefix, got: " + json);
        assertTrue(json.contains("$e:"), "JSON5 + short mode should use $e: prefix, got: " + json);
        assertFalse(json.contains("$keys:"), "JSON5 + short mode should not use $keys: prefix");
        assertFalse(json.contains("$items:"), "JSON5 + short mode should not use $items: prefix");
        assertFalse(json.contains("\"@k\":"), "JSON5 + short mode should not use quoted @k");
        assertFalse(json.contains("\"@e\":"), "JSON5 + short mode should not use quoted @e");
    }

    @Test
    void testReadDollarShortTypePrefix() {
        // JSON5 short: $t for type
        String json = "{$t:\"com.cedarsoftware.io.Json5MetaKeyPrefixTest$TestObject\",name:\"test\",value:42}";

        Object result = JsonIo.toJava(json, null).asClass(Object.class);

        assertTrue(result instanceof TestObject, "Expected TestObject but got " + result.getClass().getName());
        TestObject obj = (TestObject) result;
        assertEquals("test", obj.name);
        assertEquals(42, obj.value);
    }

    @Test
    void testReadDollarShortIdAndRefPrefix() {
        // JSON5 short: $i and $r for id and ref
        String json = "{$t:\"com.cedarsoftware.io.Json5MetaKeyPrefixTest$SelfRefObject\",$i:1,self:{$r:1}}";

        Object result = JsonIo.toJava(json, null).asClass(Object.class);

        assertTrue(result instanceof SelfRefObject, "Expected SelfRefObject but got " + result.getClass().getName());
        SelfRefObject obj = (SelfRefObject) result;
        assertSame(obj, obj.self, "self reference should point to same object");
    }

    @Test
    void testReadDollarShortKeysAndItemsPrefix() {
        // JSON5 short: $k and $e for keys and items
        String json = "{$t:\"java.util.LinkedHashMap\",$k:[1,2,3],$e:[\"one\",\"two\",\"three\"]}";

        ReadOptions readOptions = new ReadOptionsBuilder().build();
        Object result = JsonIo.toJava(json, readOptions).asClass(Object.class);

        assertTrue(result instanceof LinkedHashMap, "Expected LinkedHashMap but got " + result.getClass().getName());
        @SuppressWarnings("unchecked")
        Map<Object, String> map = (Map<Object, String>) result;
        assertEquals(3, map.size());
        assertEquals("one", map.get(1L));  // Numbers read as Long by default
        assertEquals("two", map.get(2L));
        assertEquals("three", map.get(3L));
    }

    @Test
    void testJson5ShortMetaKeysRoundTripWithType() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .json5()
                .shortMetaKeys(true)
                .build();

        TestObject original = new TestObject("round-trip-short", 999);
        String json = JsonIo.toJson(original, writeOptions);

        // Verify JSON5 short format
        assertTrue(json.contains("$t:"), "Should write with $t prefix");
        assertFalse(json.contains("$type:"), "Should not write with $type prefix");

        // Read back
        TestObject restored = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(TestObject.class);
        assertEquals(original.name, restored.name);
        assertEquals(original.value, restored.value);
    }

    @Test
    void testJson5ShortMetaKeysRoundTripWithReferences() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .json5()
                .shortMetaKeys(true)
                .build();

        SelfRefObject original = new SelfRefObject();
        original.self = original;

        String json = JsonIo.toJson(original, writeOptions);

        // Verify JSON5 short format
        assertTrue(json.contains("$i:"), "Should write with $i prefix");
        assertTrue(json.contains("$r:"), "Should write with $r prefix");

        // Read back
        SelfRefObject restored = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(SelfRefObject.class);
        assertSame(restored, restored.self, "Self reference should be restored");
    }

    @Test
    void testJson5ShortMetaKeysRoundTripWithNonStringKeyMap() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .json5()
                .shortMetaKeys(true)
                .build();

        Map<Integer, String> original = new LinkedHashMap<>();
        original.put(100, "hundred");
        original.put(200, "two-hundred");

        String json = JsonIo.toJson(original, writeOptions);

        // Verify JSON5 short format
        assertTrue(json.contains("$k:"), "Should write with $k prefix");
        assertTrue(json.contains("$e:"), "Should write with $e prefix");

        // Read back
        @SuppressWarnings("unchecked")
        Map<Object, String> restored = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(LinkedHashMap.class);
        assertEquals(2, restored.size());

        // Check values exist
        assertTrue(restored.containsValue("hundred"), "Should contain value 'hundred'");
        assertTrue(restored.containsValue("two-hundred"), "Should contain value 'two-hundred'");
    }

    @Test
    void testAllFourMetaKeyModes() {
        // Test all 4 combinations to verify they produce different outputs
        TestObject obj = new TestObject("test", 42);

        // Standard long: "@type":
        WriteOptions standardLong = new WriteOptionsBuilder().build();
        String jsonStandardLong = JsonIo.toJson(obj, standardLong);
        assertTrue(jsonStandardLong.contains("\"@type\":"), "Standard long should use \"@type\":");

        // Standard short: "@t":
        WriteOptions standardShort = new WriteOptionsBuilder().shortMetaKeys(true).build();
        String jsonStandardShort = JsonIo.toJson(obj, standardShort);
        assertTrue(jsonStandardShort.contains("\"@t\":"), "Standard short should use \"@t\":");

        // JSON5 long: $type:
        WriteOptions json5Long = new WriteOptionsBuilder().json5().build();
        String jsonJson5Long = JsonIo.toJson(obj, json5Long);
        assertTrue(jsonJson5Long.contains("$type:"), "JSON5 long should use $type:");

        // JSON5 short: $t:
        WriteOptions json5Short = new WriteOptionsBuilder().json5().shortMetaKeys(true).build();
        String jsonJson5Short = JsonIo.toJson(obj, json5Short);
        assertTrue(jsonJson5Short.contains("$t:"), "JSON5 short should use $t:");

        // All 4 should produce different meta key representations
        assertNotEquals(jsonStandardLong, jsonStandardShort);
        assertNotEquals(jsonStandardLong, jsonJson5Long);
        assertNotEquals(jsonStandardLong, jsonJson5Short);
        assertNotEquals(jsonStandardShort, jsonJson5Long);
        assertNotEquals(jsonStandardShort, jsonJson5Short);
        assertNotEquals(jsonJson5Long, jsonJson5Short);
    }

    // ========================================
    // Test classes
    // ========================================

    static class TestObject {
        String name;
        int value;

        TestObject() {}

        TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    static class SelfRefObject {
        SelfRefObject self;
    }
}
