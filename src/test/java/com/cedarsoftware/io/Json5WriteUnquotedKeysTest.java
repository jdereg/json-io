package com.cedarsoftware.io;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JSON5 unquoted keys writing support.
 *
 * When json5UnquotedKeys() is enabled, object keys that are valid ECMAScript
 * identifiers will be written without quotes. Keys containing special characters
 * will still be quoted.
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
class Json5WriteUnquotedKeysTest {

    // ========== Basic Unquoted Keys Tests ==========

    @Test
    void testUnquotedSimpleKey() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "John");

        WriteOptions options = new WriteOptionsBuilder().json5UnquotedKeys(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("name:"), "Key 'name' should be unquoted");
        assertFalse(json.contains("\"name\":"), "Key 'name' should not have quotes");
    }

    @Test
    void testUnquotedMultipleKeys() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("firstName", "John");
        map.put("lastName", "Doe");
        map.put("age", 30);

        WriteOptions options = new WriteOptionsBuilder().json5UnquotedKeys(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("firstName:"), "Key 'firstName' should be unquoted");
        assertTrue(json.contains("lastName:"), "Key 'lastName' should be unquoted");
        assertTrue(json.contains("age:"), "Key 'age' should be unquoted");
    }

    @Test
    void testUnquotedKeyWithUnderscore() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("first_name", "John");
        map.put("_private", "secret");

        WriteOptions options = new WriteOptionsBuilder().json5UnquotedKeys(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("first_name:"), "Key 'first_name' should be unquoted");
        assertTrue(json.contains("_private:"), "Key '_private' should be unquoted");
    }

    @Test
    void testUnquotedKeyWithDollarSign() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("$id", 123);
        map.put("$ref", "#/path");

        WriteOptions options = new WriteOptionsBuilder().json5UnquotedKeys(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("$id:"), "Key '$id' should be unquoted");
        assertTrue(json.contains("$ref:"), "Key '$ref' should be unquoted");
    }

    @Test
    void testUnquotedKeyWithDigits() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("item1", "first");
        map.put("value99", 99);

        WriteOptions options = new WriteOptionsBuilder().json5UnquotedKeys(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("item1:"), "Key 'item1' should be unquoted");
        assertTrue(json.contains("value99:"), "Key 'value99' should be unquoted");
    }

    // ========== Keys That Must Be Quoted ==========

    @Test
    void testQuotedKeyStartingWithDigit() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("123abc", "value");

        WriteOptions options = new WriteOptionsBuilder().json5UnquotedKeys(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("\"123abc\":"), "Key starting with digit should be quoted");
    }

    @Test
    void testQuotedKeyWithSpace() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("first name", "John");

        WriteOptions options = new WriteOptionsBuilder().json5UnquotedKeys(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("\"first name\":"), "Key with space should be quoted");
    }

    @Test
    void testQuotedKeyWithHyphen() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("first-name", "John");

        WriteOptions options = new WriteOptionsBuilder().json5UnquotedKeys(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("\"first-name\":"), "Key with hyphen should be quoted");
    }

    @Test
    void testQuotedKeyWithSpecialChars() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key@value", "test");
        map.put("key.value", "test2");

        WriteOptions options = new WriteOptionsBuilder().json5UnquotedKeys(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("\"key@value\":"), "Key with @ should be quoted");
        assertTrue(json.contains("\"key.value\":"), "Key with . should be quoted");
    }

    @Test
    void testQuotedEmptyKey() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("", "empty key");

        WriteOptions options = new WriteOptionsBuilder().json5UnquotedKeys(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("\"\":"), "Empty key should be quoted");
    }

    // ========== Mixed Keys ==========

    @Test
    void testMixedQuotedAndUnquotedKeys() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("validKey", "unquoted");
        map.put("invalid-key", "quoted");
        map.put("another_valid", "unquoted");
        map.put("123invalid", "quoted");

        WriteOptions options = new WriteOptionsBuilder().json5UnquotedKeys(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("validKey:"), "validKey should be unquoted");
        assertTrue(json.contains("\"invalid-key\":"), "invalid-key should be quoted");
        assertTrue(json.contains("another_valid:"), "another_valid should be unquoted");
        assertTrue(json.contains("\"123invalid\":"), "123invalid should be quoted");
    }

    // ========== Default Behavior (Disabled) ==========

    @Test
    void testDefaultBehaviorQuotesAllKeys() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "John");

        WriteOptions options = new WriteOptionsBuilder().build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("\"name\":"), "Key should be quoted by default");
        assertFalse(json.matches(".*[^\"](name):.*"), "Key should not be unquoted");
    }

    // ========== JSON5 Umbrella ==========

    @Test
    void testJson5UmbrellaEnablesUnquotedKeys() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "John");

        WriteOptions options = new WriteOptionsBuilder().json5().build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("name:"), "json5() should enable unquoted keys");
    }

    // ========== Round-Trip Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void testRoundTripWithUnquotedKeys() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("name", "John");
        original.put("age", 30L);
        original.put("_private", true);

        WriteOptions writeOptions = new WriteOptionsBuilder().json5UnquotedKeys(true).build();
        String json = JsonIo.toJson(original, writeOptions);

        // Verify JSON5 format
        assertTrue(json.contains("name:"), "name should be unquoted");
        assertTrue(json.contains("age:"), "age should be unquoted");
        assertTrue(json.contains("_private:"), "_private should be unquoted");

        // Verify can be read back (json-io reads JSON5 by default)
        Map<String, Object> result = JsonIo.toMaps(json, null).asClass(Map.class);
        assertEquals("John", result.get("name"));
        assertEquals(30L, result.get("age"));
        assertEquals(true, result.get("_private"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRoundTripWithMixedKeys() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("validKey", "value1");
        original.put("key-with-dash", "value2");

        WriteOptions writeOptions = new WriteOptionsBuilder().json5UnquotedKeys(true).build();
        String json = JsonIo.toJson(original, writeOptions);

        Map<String, Object> result = JsonIo.toMaps(json, null).asClass(Map.class);
        assertEquals("value1", result.get("validKey"));
        assertEquals("value2", result.get("key-with-dash"));
    }

    // ========== JsonObject Direct Serialization Tests ==========
    // These tests verify that writeJsonObjectObject() supports JSON5 unquoted keys

    @Test
    void testJsonObjectUnquotedKeys() {
        // Create a JsonObject directly - this triggers writeJsonObjectObject() code path
        JsonObject jsonObj = new JsonObject();
        jsonObj.put("name", "John");
        jsonObj.put("age", 30);
        jsonObj.put("_active", true);

        WriteOptions options = new WriteOptionsBuilder().json5UnquotedKeys(true).build();
        String json = JsonIo.toJson(jsonObj, options);

        // Verify keys are unquoted (valid ECMAScript identifiers)
        assertTrue(json.contains("name:"), "Key 'name' should be unquoted in JsonObject serialization");
        assertTrue(json.contains("age:"), "Key 'age' should be unquoted in JsonObject serialization");
        assertTrue(json.contains("_active:"), "Key '_active' should be unquoted in JsonObject serialization");
        assertFalse(json.contains("\"name\":"), "Key 'name' should not have quotes");
    }

    @Test
    void testJsonObjectMixedKeys() {
        // Create a JsonObject with both valid and invalid identifier keys
        JsonObject jsonObj = new JsonObject();
        jsonObj.put("validKey", "value1");
        jsonObj.put("key-with-dash", "value2");  // Invalid identifier - must be quoted
        jsonObj.put("123startsWithDigit", "value3");  // Invalid identifier - must be quoted

        WriteOptions options = new WriteOptionsBuilder().json5UnquotedKeys(true).build();
        String json = JsonIo.toJson(jsonObj, options);

        // Valid identifier should be unquoted
        assertTrue(json.contains("validKey:"), "validKey should be unquoted");
        // Invalid identifiers should remain quoted
        assertTrue(json.contains("\"key-with-dash\":"), "key-with-dash should be quoted");
        assertTrue(json.contains("\"123startsWithDigit\":"), "123startsWithDigit should be quoted");
    }

    @Test
    void testJsonObjectNestedUnquotedKeys() {
        // Create nested JsonObjects
        JsonObject inner = new JsonObject();
        inner.put("innerField", "innerValue");

        JsonObject outer = new JsonObject();
        outer.put("outerField", "outerValue");
        outer.put("nested", inner);

        WriteOptions options = new WriteOptionsBuilder().json5UnquotedKeys(true).build();
        String json = JsonIo.toJson(outer, options);

        // Both outer and inner keys should be unquoted
        assertTrue(json.contains("outerField:"), "outerField should be unquoted");
        assertTrue(json.contains("nested:"), "nested should be unquoted");
        assertTrue(json.contains("innerField:"), "innerField should be unquoted");
    }
}
