package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JSON5 trailing commas writing support.
 *
 * When json5TrailingCommas() is enabled, arrays and objects have a trailing
 * comma after the last element, which is valid in JSON5 but not strict JSON.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
class Json5WriteTrailingCommasTest {

    // ========== Array Tests ==========

    @Test
    void testTrailingCommaInStringArray() {
        String[] array = {"a", "b", "c"};

        WriteOptions options = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(array, options);

        // Should have trailing comma before closing bracket
        assertTrue(json.contains(",]"), "Should contain trailing comma: " + json);
    }

    @Test
    void testTrailingCommaInIntegerArray() {
        Integer[] array = {1, 2, 3};

        WriteOptions options = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(array, options);

        assertTrue(json.contains(",]"), "Should contain trailing comma: " + json);
    }

    @Test
    void testTrailingCommaInPrimitiveIntArray() {
        int[] array = {1, 2, 3};

        WriteOptions options = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(array, options);

        assertTrue(json.contains(",]"), "Should contain trailing comma: " + json);
    }

    @Test
    void testTrailingCommaInDoubleArray() {
        double[] array = {1.1, 2.2, 3.3};

        WriteOptions options = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(array, options);

        assertTrue(json.contains(",]"), "Should contain trailing comma: " + json);
    }

    @Test
    void testNoTrailingCommaInEmptyArray() {
        String[] array = {};

        WriteOptions options = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(array, options);

        // Empty array should not have trailing comma
        assertFalse(json.contains(",]"), "Empty array should not have trailing comma: " + json);
        assertTrue(json.contains("[]"), "Should be empty array: " + json);
    }

    // ========== Object Tests ==========

    @Test
    void testTrailingCommaInMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "test");
        map.put("value", 42);

        WriteOptions options = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(map, options);

        // Should have trailing comma before closing brace
        assertTrue(json.contains(",}"), "Should contain trailing comma: " + json);
    }

    @Test
    void testNoTrailingCommaInEmptyMap() {
        Map<String, Object> map = new LinkedHashMap<>();

        WriteOptions options = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(map, options);

        // Empty map should not have trailing comma
        assertFalse(json.contains(",}"), "Empty map should not have trailing comma: " + json);
    }

    @Test
    void testTrailingCommaInNestedStructure() {
        Map<String, Object> outer = new LinkedHashMap<>();
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("a", 1);
        inner.put("b", 2);
        outer.put("nested", inner);
        outer.put("list", new int[]{1, 2, 3});

        WriteOptions options = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(outer, options);

        // Both nested object and array should have trailing commas
        assertTrue(json.contains(",}"), "Should contain trailing comma in object: " + json);
        assertTrue(json.contains(",]"), "Should contain trailing comma in array: " + json);
    }

    // ========== Collection Tests ==========

    @Test
    void testTrailingCommaInList() {
        List<String> list = new ArrayList<>();
        list.add("one");
        list.add("two");
        list.add("three");

        WriteOptions options = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(list, options);

        assertTrue(json.contains(",]"), "Should contain trailing comma: " + json);
    }

    @Test
    void testTrailingCommaInEmptyList() {
        List<String> list = new ArrayList<>();

        WriteOptions options = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(list, options);

        assertFalse(json.contains(",]"), "Empty list should not have trailing comma: " + json);
    }

    // ========== Default Behavior (Disabled) ==========

    @Test
    void testDefaultBehaviorNoTrailingCommas() {
        String[] array = {"a", "b", "c"};

        WriteOptions options = new WriteOptionsBuilder().build();
        String json = JsonIo.toJson(array, options);

        // Default behavior: no trailing commas
        assertFalse(json.contains(",]"), "Default should not have trailing comma: " + json);
    }

    @Test
    void testDefaultBehaviorNoTrailingCommasInObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", "value");

        WriteOptions options = new WriteOptionsBuilder().build();
        String json = JsonIo.toJson(map, options);

        // Default behavior: no trailing commas in objects
        assertFalse(json.contains(",}"), "Default should not have trailing comma: " + json);
    }

    // ========== JSON5 Umbrella ==========

    @Test
    void testJson5UmbrellaDoesNotEnableTrailingCommas() {
        String[] array = {"a", "b", "c"};

        // json5() umbrella should NOT enable trailing commas (explicit opt-in only)
        WriteOptions options = new WriteOptionsBuilder().json5().build();
        String json = JsonIo.toJson(array, options);

        assertFalse(json.contains(",]"), "json5() umbrella should not enable trailing commas: " + json);
    }

    @Test
    void testJson5WithExplicitTrailingCommas() {
        String[] array = {"a", "b", "c"};

        // Can enable trailing commas alongside json5() umbrella
        WriteOptions options = new WriteOptionsBuilder()
                .json5()
                .json5TrailingCommas(true)
                .build();
        String json = JsonIo.toJson(array, options);

        assertTrue(json.contains(",]"), "Explicit trailing commas should work with json5(): " + json);
    }

    // ========== Round-Trip Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void testRoundTripArrayWithTrailingComma() {
        String[] original = {"apple", "banana", "cherry"};

        WriteOptions writeOptions = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(original, writeOptions);

        assertTrue(json.contains(",]"), "Should contain trailing comma: " + json);

        // json-io reads JSON5 by default, so trailing commas should be accepted
        String[] result = JsonIo.toJava(json, null).asClass(String[].class);

        assertArrayEquals(original, result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRoundTripMapWithTrailingComma() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("name", "test");
        original.put("count", 5);

        WriteOptions writeOptions = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(original, writeOptions);

        assertTrue(json.contains(",}"), "Should contain trailing comma: " + json);

        // Round trip through toMaps
        Map<String, Object> result = JsonIo.toMaps(json, null).asClass(Map.class);

        assertEquals("test", result.get("name"));
        // JSON numbers are parsed as Long by default
        assertEquals(5L, ((Number) result.get("count")).longValue());
    }

    // ========== Pretty Print Combinations ==========

    @Test
    void testTrailingCommaWithPrettyPrint() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");

        WriteOptions options = new WriteOptionsBuilder()
                .prettyPrint(true)
                .json5TrailingCommas(true)
                .build();
        String json = JsonIo.toJson(map, options);

        // Pretty printed JSON with trailing comma
        assertTrue(json.contains(",\n}") || json.contains(",\r\n}"),
                "Should contain trailing comma with newline before closing brace: " + json);
    }

    @Test
    void testTrailingCommaArrayWithPrettyPrint() {
        int[] array = {1, 2, 3};

        WriteOptions options = new WriteOptionsBuilder()
                .prettyPrint(true)
                .json5TrailingCommas(true)
                .build();
        String json = JsonIo.toJson(array, options);

        // Verify trailing comma is present in @items array (after 3)
        // Pretty print may add whitespace between comma and closing bracket
        assertTrue(json.contains("3,"), "Should have trailing comma after last element: " + json);
        // Also verify JSON structure is correct
        assertTrue(json.contains("@items"), "Should contain @items: " + json);
    }

    // ========== Combined JSON5 Features ==========

    @Test
    void testTrailingCommasWithUnquotedKeys() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("simpleKey", "value");
        map.put("count", 42);

        WriteOptions options = new WriteOptionsBuilder()
                .json5UnquotedKeys(true)
                .json5TrailingCommas(true)
                .build();
        String json = JsonIo.toJson(map, options);

        // Should have both unquoted keys and trailing comma
        assertTrue(json.contains("simpleKey:"), "Should have unquoted key: " + json);
        assertTrue(json.contains(",}"), "Should have trailing comma: " + json);
    }

    @Test
    void testTrailingCommasWithSmartQuotes() {
        String[] array = {"simple", "has \"double\" quotes"};

        WriteOptions options = new WriteOptionsBuilder()
                .json5SmartQuotes(true)
                .json5TrailingCommas(true)
                .build();
        String json = JsonIo.toJson(array, options);

        // Should have smart quotes and trailing comma
        assertTrue(json.contains("'has \"double\" quotes'"), "Should use single quotes: " + json);
        assertTrue(json.contains(",]"), "Should have trailing comma: " + json);
    }

    @Test
    void testAllJson5FeaturesWithTrailingCommas() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "test");
        map.put("infinity", Double.POSITIVE_INFINITY);
        map.put("quoted", "has \"quotes\"");

        WriteOptions options = new WriteOptionsBuilder()
                .json5()  // enables unquoted keys, smart quotes, infinity/nan
                .json5TrailingCommas(true)  // explicit opt-in
                .build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("name:"), "Should have unquoted keys: " + json);
        assertTrue(json.contains("Infinity"), "Should have Infinity literal: " + json);
        assertTrue(json.contains(",}"), "Should have trailing comma: " + json);
    }
}
