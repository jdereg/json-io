package com.cedarsoftware.io;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for JSON5 single-quoted string support.
 *
 * JSON5 allows strings to be enclosed in single quotes instead of double quotes:
 * - Values: {'name': 'John'}
 * - Keys: {'name': 'value'}
 *
 * By default, json-io operates in permissive mode and accepts single-quoted strings.
 * Use strictJson() to enforce RFC 8259 compliance (double quotes only).
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
class Json5SingleQuotedStringsTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        return TestUtil.toMaps(json, null).asClass(Map.class);
    }

    // ========== Single-Quoted Value Tests ==========

    @Test
    void testSingleQuotedStringValue() {
        String json = "{\"name\":'John'}";
        Map<String, Object> result = parseJson(json);
        assertEquals("John", result.get("name"));
    }

    @Test
    void testSingleQuotedStringWithSpaces() {
        String json = "{\"message\":'Hello World'}";
        Map<String, Object> result = parseJson(json);
        assertEquals("Hello World", result.get("message"));
    }

    @Test
    void testSingleQuotedEmptyString() {
        String json = "{\"empty\":''}";
        Map<String, Object> result = parseJson(json);
        assertEquals("", result.get("empty"));
    }

    @Test
    void testSingleQuotedStringWithDoubleQuoteInside() {
        // Single-quoted strings can contain unescaped double quotes
        String json = "{\"text\":'He said \"Hello\"'}";
        Map<String, Object> result = parseJson(json);
        assertEquals("He said \"Hello\"", result.get("text"));
    }

    @Test
    void testSingleQuotedStringWithEscapedSingleQuote() {
        // Escaped single quote inside single-quoted string
        String json = "{\"text\":'It\\'s working'}";
        Map<String, Object> result = parseJson(json);
        assertEquals("It's working", result.get("text"));
    }

    @Test
    void testSingleQuotedStringWithNewline() {
        String json = "{\"text\":'line1\\nline2'}";
        Map<String, Object> result = parseJson(json);
        assertEquals("line1\nline2", result.get("text"));
    }

    @Test
    void testSingleQuotedStringWithTab() {
        String json = "{\"text\":'col1\\tcol2'}";
        Map<String, Object> result = parseJson(json);
        assertEquals("col1\tcol2", result.get("text"));
    }

    @Test
    void testSingleQuotedStringWithUnicode() {
        String json = "{\"text\":'Hello \\u0041\\u0042\\u0043'}";
        Map<String, Object> result = parseJson(json);
        assertEquals("Hello ABC", result.get("text"));
    }

    // ========== Single-Quoted Key Tests ==========

    @Test
    void testSingleQuotedKey() {
        String json = "{'name':\"John\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("John", result.get("name"));
    }

    @Test
    void testSingleQuotedKeyAndValue() {
        String json = "{'name':'John'}";
        Map<String, Object> result = parseJson(json);
        assertEquals("John", result.get("name"));
    }

    @Test
    void testMultipleSingleQuotedKeys() {
        String json = "{'first':'John', 'last':'Doe', 'age':30}";
        Map<String, Object> result = parseJson(json);
        assertEquals("John", result.get("first"));
        assertEquals("Doe", result.get("last"));
        assertEquals(30L, result.get("age"));
    }

    @Test
    void testSingleQuotedKeyWithDoubleQuoteInside() {
        String json = "{'key\"with\"quotes':'value'}";
        Map<String, Object> result = parseJson(json);
        assertEquals("value", result.get("key\"with\"quotes"));
    }

    // ========== Mixed Quote Tests ==========

    @Test
    void testMixedQuotesKeysAndValues() {
        String json = "{\"double\":'single', 'single':\"double\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("single", result.get("double"));
        assertEquals("double", result.get("single"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testNestedObjectsWithSingleQuotes() {
        String json = "{'outer':{'inner':'value'}}";
        Map<String, Object> result = parseJson(json);
        Map<String, Object> outer = (Map<String, Object>) result.get("outer");
        assertEquals("value", outer.get("inner"));
    }

    @Test
    void testArrayWithSingleQuotedStrings() {
        String json = "{\"arr\":['one', 'two', 'three']}";
        Map<String, Object> result = parseJson(json);
        Object[] arr = (Object[]) result.get("arr");
        assertEquals(3, arr.length);
        assertEquals("one", arr[0]);
        assertEquals("two", arr[1]);
        assertEquals("three", arr[2]);
    }

    // ========== Combined JSON5 Features ==========

    @Test
    void testSingleQuotesWithUnquotedKeys() {
        String json = "{name:'John', age:30}";
        Map<String, Object> result = parseJson(json);
        assertEquals("John", result.get("name"));
        assertEquals(30L, result.get("age"));
    }

    @Test
    void testSingleQuotesWithTrailingComma() {
        String json = "{'name':'John', 'age':30,}";
        Map<String, Object> result = parseJson(json);
        assertEquals("John", result.get("name"));
        assertEquals(30L, result.get("age"));
    }

    @Test
    void testSingleQuotesWithComments() {
        String json = "{'name':'John' /* comment */, 'age':30}";
        Map<String, Object> result = parseJson(json);
        assertEquals("John", result.get("name"));
        assertEquals(30L, result.get("age"));
    }

    // ========== Strict Mode Tests ==========

    @Test
    void testStrictModeRejectsSingleQuotedValue() {
        String json = "{\"name\":'John'}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toMaps(json, strictOptions).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Single-quoted strings not allowed in strict JSON mode");
    }

    @Test
    void testStrictModeRejectsSingleQuotedKey() {
        String json = "{'name':\"John\"}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toMaps(json, strictOptions).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Single-quoted strings not allowed in strict JSON mode");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStrictModeAcceptsDoubleQuotes() {
        String json = "{\"name\":\"John\", \"age\":30}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        Map<String, Object> result = JsonIo.toMaps(json, strictOptions).asClass(Map.class);
        assertEquals("John", result.get("name"));
        assertEquals(30L, result.get("age"));
    }

    // ========== Edge Cases ==========

    @Test
    void testDoubleQuotedStringStillWorks() {
        String json = "{\"name\":\"John\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("John", result.get("name"));
    }

    @Test
    void testSingleQuotedStringWithBackslash() {
        String json = "{\"path\":'C:\\\\Users\\\\John'}";
        Map<String, Object> result = parseJson(json);
        assertEquals("C:\\Users\\John", result.get("path"));
    }

    @Test
    void testSingleQuotedStringWithAllValueTypes() {
        String json = "{'str':'text', 'num':42, 'bool':true, 'nil':null}";
        Map<String, Object> result = parseJson(json);
        assertEquals("text", result.get("str"));
        assertEquals(42L, result.get("num"));
        assertEquals(true, result.get("bool"));
        assertTrue(result.containsKey("nil"));
        assertNull(result.get("nil"));
    }

    @Test
    void testLongSingleQuotedString() {
        String longText = "This is a very long string that spans many characters and is enclosed in single quotes";
        String json = "{\"text\":'" + longText + "'}";
        Map<String, Object> result = parseJson(json);
        assertEquals(longText, result.get("text"));
    }
}
