package com.cedarsoftware.io;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that verify exact JSON5 output format for correctness.
 * These tests verify the complete JSON output matches expected JSON5 format,
 * not just that certain patterns exist within the output.
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
class Json5WriteExactOutputTest {

    // ========== Unquoted Keys Exact Output ==========

    @Test
    void testUnquotedKeysExactOutput() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "John");

        WriteOptions options = new WriteOptionsBuilder()
                .json5UnquotedKeys(true)
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(map, options);

        assertEquals("{name:\"John\"}", json);
    }

    @Test
    void testUnquotedKeysMultipleExactOutput() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1);
        map.put("b", 2);

        WriteOptions options = new WriteOptionsBuilder()
                .json5UnquotedKeys(true)
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(map, options);

        assertEquals("{a:1,b:2}", json);
    }

    @Test
    void testMixedQuotedUnquotedKeysExactOutput() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("valid", 1);
        map.put("not-valid", 2);

        WriteOptions options = new WriteOptionsBuilder()
                .json5UnquotedKeys(true)
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(map, options);

        assertEquals("{valid:1,\"not-valid\":2}", json);
    }

    // ========== Smart Quotes Exact Output ==========

    @Test
    void testSmartQuotesExactOutput() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("msg", "He said \"Hi\"");

        WriteOptions options = new WriteOptionsBuilder()
                .json5SmartQuotes(true)
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(map, options);

        // Key uses double quotes, value uses single quotes (contains ")
        assertEquals("{\"msg\":'He said \"Hi\"'}", json);
    }

    @Test
    void testSmartQuotesNormalStringExactOutput() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("msg", "Hello");

        WriteOptions options = new WriteOptionsBuilder()
                .json5SmartQuotes(true)
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(map, options);

        // Normal string should use double quotes
        assertEquals("{\"msg\":\"Hello\"}", json);
    }

    @Test
    void testSmartQuotesWithApostropheExactOutput() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("msg", "It's OK");

        WriteOptions options = new WriteOptionsBuilder()
                .json5SmartQuotes(true)
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(map, options);

        // String with ' should use double quotes (no escaping needed)
        assertEquals("{\"msg\":\"It's OK\"}", json);
    }

    // ========== Infinity/NaN Exact Output ==========

    @Test
    void testPositiveInfinityExactOutput() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("val", Double.POSITIVE_INFINITY);

        WriteOptions options = new WriteOptionsBuilder()
                .json5InfinityNaN(true)
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(map, options);

        assertEquals("{\"val\":Infinity}", json);
    }

    @Test
    void testNegativeInfinityExactOutput() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("val", Double.NEGATIVE_INFINITY);

        WriteOptions options = new WriteOptionsBuilder()
                .json5InfinityNaN(true)
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(map, options);

        assertEquals("{\"val\":-Infinity}", json);
    }

    @Test
    void testNaNExactOutput() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("val", Double.NaN);

        WriteOptions options = new WriteOptionsBuilder()
                .json5InfinityNaN(true)
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(map, options);

        assertEquals("{\"val\":NaN}", json);
    }

    @Test
    void testFloatInfinityExactOutput() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("val", Float.POSITIVE_INFINITY);

        WriteOptions options = new WriteOptionsBuilder()
                .json5InfinityNaN(true)
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(map, options);

        assertEquals("{\"val\":Infinity}", json);
    }

    // ========== Trailing Commas Exact Output ==========

    @Test
    void testTrailingCommaArrayExactOutput() {
        int[] array = {1, 2, 3};

        WriteOptions options = new WriteOptionsBuilder()
                .json5TrailingCommas(true)
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(array, options);

        assertEquals("[1,2,3]", json);  // Trailing commas are never written
    }

    @Test
    void testTrailingCommaObjectExactOutput() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1);

        WriteOptions options = new WriteOptionsBuilder()
                .json5TrailingCommas(true)
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(map, options);

        assertEquals("{\"a\":1}", json);  // Trailing commas are never written
    }

    @Test
    void testTrailingCommaEmptyArrayExactOutput() {
        int[] array = {};

        WriteOptions options = new WriteOptionsBuilder()
                .json5TrailingCommas(true)
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(array, options);

        // Empty array should NOT have trailing comma
        assertEquals("[]", json);
    }

    @Test
    void testTrailingCommaEmptyObjectExactOutput() {
        Map<String, Object> map = new LinkedHashMap<>();

        WriteOptions options = new WriteOptionsBuilder()
                .json5TrailingCommas(true)
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(map, options);

        // Empty object should NOT have trailing comma
        assertEquals("{}", json);
    }

    // ========== Combined Features Exact Output ==========

    @Test
    void testAllFeaturesExactOutput() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "He said \"Hi\"");
        map.put("inf", Double.POSITIVE_INFINITY);

        WriteOptions options = new WriteOptionsBuilder()
                .json5()  // enables unquoted keys, smart quotes, infinity/nan
                .json5TrailingCommas(true)
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(map, options);

        // unquoted keys, single quotes for string with ", Infinity literal (no trailing commas written)
        assertEquals("{name:'He said \"Hi\"',inf:Infinity}", json);
    }

    @Test
    void testJson5UmbrellaExactOutput() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", "value");

        WriteOptions options = new WriteOptionsBuilder()
                .json5()
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(map, options);

        // json5() enables unquoted keys
        assertEquals("{key:\"value\"}", json);
    }

    @Test
    void testSmartQuotesOnlyAffectsValues_NotKeys() {
        // This test explicitly verifies that:
        // 1. Smart quotes only affect STRING VALUES, not keys
        // 2. Keys with invalid identifiers use double quotes (never single quotes)
        // 3. Values with " but no ' use single quotes
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key-with-dash", "He said \"Hello\"");  // Invalid identifier key, value with "

        WriteOptions options = new WriteOptionsBuilder()
                .json5UnquotedKeys(true)   // Would unquote if valid identifier
                .json5SmartQuotes(true)    // Affects values only
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(map, options);

        // Key: "key-with-dash" - DOUBLE QUOTED (invalid identifier, smart quotes don't apply to keys)
        // Value: 'He said "Hello"' - SINGLE QUOTED (has " but no ')
        assertEquals("{\"key-with-dash\":'He said \"Hello\"'}", json);
    }

    @Test
    void testKeysNeverUseSingleQuotes() {
        // Verify that keys NEVER use single quotes, even if they contain "
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key\"with\"quotes", "normal value");

        WriteOptions options = new WriteOptionsBuilder()
                .json5SmartQuotes(true)    // Only affects values
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(map, options);

        // Key uses double quotes with escaping (NEVER single quotes)
        // Value uses double quotes (no " in value)
        assertEquals("{\"key\\\"with\\\"quotes\":\"normal value\"}", json);
    }

    // ========== JSON5 Validity - Round Trip Verification ==========

    @Test
    @SuppressWarnings("unchecked")
    void testAllFeaturesRoundTrip() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("name", "He said \"Hi\"");
        original.put("posInf", Double.POSITIVE_INFINITY);
        original.put("negInf", Double.NEGATIVE_INFINITY);
        original.put("nan", Double.NaN);
        original.put("number", 42);

        WriteOptions writeOptions = new WriteOptionsBuilder()
                .json5()
                .json5TrailingCommas(true)
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(original, writeOptions);

        // Parse it back with JSON5 reader
        ReadOptions readOptions = new ReadOptionsBuilder()
                .allowNanAndInfinity(true)
                .build();
        Map<String, Object> result = JsonIo.toMaps(json, readOptions).asClass(Map.class);

        assertEquals("He said \"Hi\"", result.get("name"));
        assertEquals(Double.POSITIVE_INFINITY, ((Number) result.get("posInf")).doubleValue());
        assertEquals(Double.NEGATIVE_INFINITY, ((Number) result.get("negInf")).doubleValue());
        assertTrue(Double.isNaN(((Number) result.get("nan")).doubleValue()));
        assertEquals(42L, ((Number) result.get("number")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testNestedStructuresRoundTrip() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("x", 1);
        inner.put("y", 2);

        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("point", inner);
        outer.put("label", "A \"point\"");

        WriteOptions writeOptions = new WriteOptionsBuilder()
                .json5()
                .json5TrailingCommas(true)
                .showTypeInfoNever()
                .build();
        String json = JsonIo.toJson(outer, writeOptions);

        // Verify format
        assertTrue(json.contains("point:{"), "Should have unquoted key for nested object");
        assertTrue(json.contains("'A \"point\"'"), "Should use single quotes for label");
        assertFalse(json.contains(",}"), "Trailing commas are never written");

        // Round trip
        Map<String, Object> result = JsonIo.toMaps(json, null).asClass(Map.class);
        Map<String, Object> resultInner = (Map<String, Object>) result.get("point");
        assertEquals(1L, ((Number) resultInner.get("x")).longValue());
        assertEquals(2L, ((Number) resultInner.get("y")).longValue());
        assertEquals("A \"point\"", result.get("label"));
    }
}
