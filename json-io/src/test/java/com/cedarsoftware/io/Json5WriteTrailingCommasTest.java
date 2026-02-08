package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests verifying that json-io never writes trailing commas in JSON output.
 * Trailing commas are supported on READ (JSON5 tolerance) but never written.
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
    void testNoTrailingCommaInStringArray() {
        String[] array = {"a", "b", "c"};
        WriteOptions options = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(array, options);
        assertFalse(json.contains(",]"), "Should NOT contain trailing comma: " + json);
    }

    @Test
    void testNoTrailingCommaInIntegerArray() {
        Integer[] array = {1, 2, 3};
        WriteOptions options = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(array, options);
        assertFalse(json.contains(",]"), "Should NOT contain trailing comma: " + json);
    }

    @Test
    void testNoTrailingCommaInPrimitiveIntArray() {
        int[] array = {1, 2, 3};
        WriteOptions options = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(array, options);
        assertFalse(json.contains(",]"), "Should NOT contain trailing comma: " + json);
    }

    @Test
    void testNoTrailingCommaInDoubleArray() {
        double[] array = {1.1, 2.2, 3.3};
        WriteOptions options = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(array, options);
        assertFalse(json.contains(",]"), "Should NOT contain trailing comma: " + json);
    }

    @Test
    void testNoTrailingCommaInEmptyArray() {
        String[] array = {};
        WriteOptions options = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(array, options);
        assertFalse(json.contains(",]"), "Empty array should not have trailing comma: " + json);
        assertTrue(json.contains("[]"), "Should be empty array: " + json);
    }

    // ========== Object Tests ==========

    @Test
    void testNoTrailingCommaInMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "test");
        map.put("value", 42);
        WriteOptions options = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(map, options);
        assertFalse(json.contains(",}"), "Should NOT contain trailing comma: " + json);
    }

    @Test
    void testNoTrailingCommaInNestedStructure() {
        Map<String, Object> outer = new LinkedHashMap<>();
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("a", 1);
        inner.put("b", 2);
        outer.put("nested", inner);
        outer.put("list", new int[]{1, 2, 3});
        WriteOptions options = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(outer, options);
        assertFalse(json.contains(",}"), "Should NOT contain trailing comma in object: " + json);
        assertFalse(json.contains(",]"), "Should NOT contain trailing comma in array: " + json);
    }

    // ========== Collection Tests ==========

    @Test
    void testNoTrailingCommaInList() {
        List<String> list = new ArrayList<>();
        list.add("one");
        list.add("two");
        list.add("three");
        WriteOptions options = new WriteOptionsBuilder().json5TrailingCommas(true).build();
        String json = JsonIo.toJson(list, options);
        assertFalse(json.contains(",]"), "Should NOT contain trailing comma: " + json);
    }

    // ========== Default Behavior ==========

    @Test
    void testDefaultBehaviorNoTrailingCommas() {
        String[] array = {"a", "b", "c"};
        WriteOptions options = new WriteOptionsBuilder().build();
        String json = JsonIo.toJson(array, options);
        assertFalse(json.contains(",]"), "Default should not have trailing comma: " + json);
    }

    @Test
    void testDefaultBehaviorNoTrailingCommasInObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", "value");
        WriteOptions options = new WriteOptionsBuilder().build();
        String json = JsonIo.toJson(map, options);
        assertFalse(json.contains(",}"), "Default should not have trailing comma: " + json);
    }

    // ========== Round-Trip Tests (verifies read-side tolerance) ==========

    @Test
    void testReadToleratesTrailingCommaInArray() {
        // json-io can READ trailing commas (JSON5 tolerance) even though it doesn't write them
        String jsonWithTrailing = "[\"apple\",\"banana\",\"cherry\",]";
        String[] result = JsonIo.toJava(jsonWithTrailing, null).asClass(String[].class);
        assertArrayEquals(new String[]{"apple", "banana", "cherry"}, result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testReadToleratesTrailingCommaInObject() {
        String jsonWithTrailing = "{\"name\":\"test\",\"count\":5,}";
        Map<String, Object> result = JsonIo.toMaps(jsonWithTrailing, null).asClass(Map.class);
        assertEquals("test", result.get("name"));
        assertEquals(5L, ((Number) result.get("count")).longValue());
    }

    // ========== Pretty Print Combinations ==========

    @Test
    void testNoTrailingCommaWithPrettyPrint() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");
        WriteOptions options = new WriteOptionsBuilder()
                .prettyPrint(true)
                .json5TrailingCommas(true)
                .build();
        String json = JsonIo.toJson(map, options);
        // Should NOT have trailing comma even with prettyPrint + json5TrailingCommas enabled
        assertFalse(json.contains(",\n}") || json.contains(",\r\n}"),
                "Should NOT contain trailing comma: " + json);
    }

    // ========== Combined JSON5 Features ==========

    @Test
    void testNoTrailingCommasWithUnquotedKeys() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("simpleKey", "value");
        map.put("count", 42);
        WriteOptions options = new WriteOptionsBuilder()
                .json5UnquotedKeys(true)
                .json5TrailingCommas(true)
                .build();
        String json = JsonIo.toJson(map, options);
        assertTrue(json.contains("simpleKey:"), "Should have unquoted key: " + json);
        assertFalse(json.contains(",}"), "Should NOT have trailing comma: " + json);
    }

    @Test
    void testAllJson5FeaturesNoTrailingCommas() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "test");
        map.put("infinity", Double.POSITIVE_INFINITY);
        WriteOptions options = new WriteOptionsBuilder()
                .json5()
                .json5TrailingCommas(true)
                .build();
        String json = JsonIo.toJson(map, options);
        assertTrue(json.contains("name:"), "Should have unquoted keys: " + json);
        assertTrue(json.contains("Infinity"), "Should have Infinity literal: " + json);
        assertFalse(json.contains(",}"), "Should NOT have trailing comma: " + json);
    }
}
