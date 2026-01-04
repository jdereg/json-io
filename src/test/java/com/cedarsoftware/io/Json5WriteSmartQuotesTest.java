package com.cedarsoftware.io;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JSON5 smart quotes writing support.
 *
 * When json5SmartQuotes() is enabled, string values use adaptive quote selection:
 * - Uses single quotes if the string contains " but no '
 * - Uses double quotes otherwise (standard behavior)
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
class Json5WriteSmartQuotesTest {

    // ========== Single Quotes for Strings with Double Quotes ==========

    @Test
    void testStringWithDoubleQuotesUsesSingleQuotes() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("message", "He said \"Hello\"");

        WriteOptions options = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        String json = JsonIo.toJson(map, options);

        // The value should use single quotes since it contains double quotes
        assertTrue(json.contains("'He said \"Hello\"'"), "String with double quotes should use single quotes: " + json);
    }

    @Test
    void testStringWithOnlyDoubleQuotesUsesSingleQuotes() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("quote", "\"");

        WriteOptions options = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("'\"'"), "Single double quote should use single quotes: " + json);
    }

    @Test
    void testStringWithMultipleDoubleQuotesUsesSingleQuotes() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("text", "\"one\" and \"two\"");

        WriteOptions options = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("'\"one\" and \"two\"'"), "String with multiple double quotes should use single quotes: " + json);
    }

    // ========== Double Quotes for Normal Strings ==========

    @Test
    void testStringWithoutQuotesUsesDoubleQuotes() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "John");

        WriteOptions options = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        String json = JsonIo.toJson(map, options);

        // Standard strings should use double quotes
        assertTrue(json.contains("\"John\""), "Normal string should use double quotes: " + json);
    }

    @Test
    void testStringWithSingleQuoteUsesDoubleQuotes() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("text", "It's working");

        WriteOptions options = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        String json = JsonIo.toJson(map, options);

        // String with single quote should use double quotes (no escaping needed for ')
        assertTrue(json.contains("\"It's working\""), "String with single quote should use double quotes: " + json);
    }

    @Test
    void testStringWithBothQuoteTypesUsesDoubleQuotes() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("text", "He said \"It's mine\"");

        WriteOptions options = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        String json = JsonIo.toJson(map, options);

        // Has both " and ' - should use double quotes and escape the "
        assertTrue(json.contains("\"He said \\\"It's mine\\\"\""), "String with both quote types should use double quotes: " + json);
    }

    // ========== Keys Always Use Double Quotes or Unquoted ==========

    @Test
    void testKeysAreNotAffectedBySmartQuotes() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key\"with\"quotes", "value");

        WriteOptions options = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        String json = JsonIo.toJson(map, options);

        // Keys should still use double quotes with escaping, not single quotes
        assertTrue(json.contains("\"key\\\"with\\\"quotes\""), "Keys should use double quotes regardless of content: " + json);
        assertFalse(json.contains("'key\"with\"quotes'"), "Keys should not use single quotes: " + json);
    }

    // ========== Default Behavior (Disabled) ==========

    @Test
    void testDefaultBehaviorUsesDoubleQuotes() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("message", "He said \"Hello\"");

        WriteOptions options = new WriteOptionsBuilder().build();
        String json = JsonIo.toJson(map, options);

        // Default should escape double quotes within double quotes
        assertTrue(json.contains("\"He said \\\"Hello\\\"\""), "Default should use double quotes with escaping: " + json);
        assertFalse(json.contains("'"), "Default should not use single quotes: " + json);
    }

    // ========== JSON5 Umbrella ==========

    @Test
    void testJson5UmbrellaEnablesSmartQuotes() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("message", "He said \"Hello\"");

        WriteOptions options = new WriteOptionsBuilder().json5().build();
        String json = JsonIo.toJson(map, options);

        // json5() should enable smart quotes
        assertTrue(json.contains("'He said \"Hello\"'"), "json5() should enable smart quotes: " + json);
    }

    // ========== Round-Trip Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void testRoundTripWithSingleQuotes() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("quote", "She said \"Yes!\"");

        WriteOptions writeOptions = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        String json = JsonIo.toJson(original, writeOptions);

        // Verify single quotes are used
        assertTrue(json.contains("'She said \"Yes!\"'"), "Should use single quotes: " + json);

        // Verify round-trip works (json-io reads JSON5 by default)
        Map<String, Object> result = JsonIo.toMaps(json, null).asClass(Map.class);
        assertEquals("She said \"Yes!\"", result.get("quote"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRoundTripWithMixedStrings() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("normal", "Hello World");
        original.put("withQuotes", "He said \"Hi\"");
        original.put("withApostrophe", "It's fine");

        WriteOptions writeOptions = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        String json = JsonIo.toJson(original, writeOptions);

        Map<String, Object> result = JsonIo.toMaps(json, null).asClass(Map.class);
        assertEquals("Hello World", result.get("normal"));
        assertEquals("He said \"Hi\"", result.get("withQuotes"));
        assertEquals("It's fine", result.get("withApostrophe"));
    }

    // ========== Edge Cases ==========

    @Test
    void testEmptyString() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("empty", "");

        WriteOptions options = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("\"\""), "Empty string should use double quotes: " + json);
    }

    @Test
    void testNullValue() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("nullValue", null);

        WriteOptions options = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("null"), "Null should be written as null: " + json);
    }

    @Test
    void testStringInArray() {
        String[] array = {"Hello", "With \"quotes\"", "Normal"};

        WriteOptions options = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        String json = JsonIo.toJson(array, options);

        assertTrue(json.contains("\"Hello\""), "Normal string in array should use double quotes: " + json);
        assertTrue(json.contains("'With \"quotes\"'"), "String with quotes in array should use single quotes: " + json);
        assertTrue(json.contains("\"Normal\""), "Normal string in array should use double quotes: " + json);
    }

    @Test
    void testCharacterArrayAsSingleString() {
        char[] chars = "He said \"Hi\"".toCharArray();

        WriteOptions options = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        String json = JsonIo.toJson(chars, options);

        // char[] is written as a single string
        assertTrue(json.contains("'He said \"Hi\"'"), "char[] with quotes should use single quotes: " + json);
    }

    // ========== Combined with Unquoted Keys ==========

    @Test
    void testSmartQuotesWithUnquotedKeys() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "John");
        map.put("quote", "He said \"Hello\"");

        WriteOptions options = new WriteOptionsBuilder()
                .json5UnquotedKeys(true)
                .json5SmartQuotes(true)
                .build();
        String json = JsonIo.toJson(map, options);

        // Keys should be unquoted
        assertTrue(json.contains("name:"), "Keys should be unquoted: " + json);
        assertTrue(json.contains("quote:"), "Keys should be unquoted: " + json);

        // Values should use smart quotes
        assertTrue(json.contains("\"John\""), "Normal value should use double quotes: " + json);
        assertTrue(json.contains("'He said \"Hello\"'"), "Value with quotes should use single quotes: " + json);
    }
}
