package com.cedarsoftware.io;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for JSON5 unquoted object key support.
 *
 * JSON5 allows object keys to be valid ECMAScript 5.1 IdentifierNames,
 * which means they can be unquoted if they:
 * - Start with a letter (a-z, A-Z), underscore (_), or dollar sign ($)
 * - Subsequent characters can also include digits (0-9)
 *
 * By default, json-io operates in permissive mode and accepts JSON5 unquoted keys.
 * Use strictJson() to enforce RFC 8259 compliance (quoted keys only).
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
class Json5UnquotedKeysTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        return TestUtil.toMaps(json, null).asClass(Map.class);
    }

    // ========== Permissive Mode Tests (Default) ==========

    @Test
    void testSimpleUnquotedKey() {
        Map<String, Object> result = parseJson("{name:\"John\"}");
        assertEquals("John", result.get("name"));
    }

    @Test
    void testUnquotedKeyWithNumber() {
        Map<String, Object> result = parseJson("{field123:42}");
        assertEquals(42L, result.get("field123"));
    }

    @Test
    void testUnquotedKeyWithUnderscore() {
        Map<String, Object> result = parseJson("{_privateField:\"secret\"}");
        assertEquals("secret", result.get("_privateField"));
    }

    @Test
    void testUnquotedKeyWithDollarSign() {
        Map<String, Object> result = parseJson("{$jquery:\"library\"}");
        assertEquals("library", result.get("$jquery"));
    }

    @Test
    void testUnquotedKeyStartingWithUnderscore() {
        Map<String, Object> result = parseJson("{__proto__:\"prototype\"}");
        assertEquals("prototype", result.get("__proto__"));
    }

    @Test
    void testMultipleUnquotedKeys() {
        Map<String, Object> result = parseJson("{first:1, second:2, third:3}");
        assertEquals(1L, result.get("first"));
        assertEquals(2L, result.get("second"));
        assertEquals(3L, result.get("third"));
    }

    @Test
    void testMixedQuotedAndUnquotedKeys() {
        Map<String, Object> result = parseJson("{unquoted:1, \"quoted\":2, another:3}");
        assertEquals(1L, result.get("unquoted"));
        assertEquals(2L, result.get("quoted"));
        assertEquals(3L, result.get("another"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testNestedObjectsWithUnquotedKeys() {
        Map<String, Object> result = parseJson("{outer:{inner:{deep:\"value\"}}}");
        Map<String, Object> outer = (Map<String, Object>) result.get("outer");
        Map<String, Object> inner = (Map<String, Object>) outer.get("inner");
        assertEquals("value", inner.get("deep"));
    }

    @Test
    void testReservedWordsAsUnquotedKeys() {
        // In JSON5/ECMAScript, reserved words CAN be used as identifier names (property names)
        Map<String, Object> result = parseJson("{null:1, true:2, false:3}");
        assertEquals(1L, result.get("null"));
        assertEquals(2L, result.get("true"));
        assertEquals(3L, result.get("false"));
    }

    @Test
    void testUnquotedKeyWithArrayValue() {
        Map<String, Object> result = parseJson("{items:[1, 2, 3]}");
        assertNotNull(result.get("items"));
    }

    @Test
    void testCamelCaseUnquotedKey() {
        Map<String, Object> result = parseJson("{firstName:\"John\", lastName:\"Doe\"}");
        assertEquals("John", result.get("firstName"));
        assertEquals("Doe", result.get("lastName"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testUnquotedKeyWithComplexValue() {
        Map<String, Object> result = parseJson("{config:{enabled:true, count:5, name:\"test\"}}");
        Map<String, Object> config = (Map<String, Object>) result.get("config");
        assertEquals(true, config.get("enabled"));
        assertEquals(5L, config.get("count"));
        assertEquals("test", config.get("name"));
    }

    // ========== Strict Mode Tests ==========

    @Test
    void testStrictModeRejectsUnquotedKey() {
        String json = "{name:\"John\"}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toMaps(json, strictOptions).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unquoted field names not allowed in strict JSON mode");
    }

    @Test
    void testStrictModeRejectsMultipleUnquotedKeys() {
        String json = "{first:1, second:2}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toMaps(json, strictOptions).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unquoted field names not allowed in strict JSON mode");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStrictModeAcceptsQuotedKeys() {
        String json = "{\"name\":\"John\", \"age\":30}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        Map<String, Object> result = JsonIo.toMaps(json, strictOptions).asClass(Map.class);
        assertEquals("John", result.get("name"));
        assertEquals(30L, result.get("age"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStrictModeWithNestedObjects() {
        String json = "{\"outer\":{\"inner\":\"value\"}}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        Map<String, Object> result = JsonIo.toMaps(json, strictOptions).asClass(Map.class);
        Map<String, Object> outer = (Map<String, Object>) result.get("outer");
        assertEquals("value", outer.get("inner"));
    }

    @Test
    void testStrictModeRejectsNestedUnquotedKey() {
        String json = "{\"outer\":{inner:\"value\"}}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toMaps(json, strictOptions).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unquoted field names not allowed in strict JSON mode");
    }

    // ========== Edge Cases ==========

    @Test
    void testSingleCharacterUnquotedKey() {
        Map<String, Object> result = parseJson("{a:1, b:2, c:3}");
        assertEquals(1L, result.get("a"));
        assertEquals(2L, result.get("b"));
        assertEquals(3L, result.get("c"));
    }

    @Test
    void testUnquotedKeyWithBooleanValue() {
        Map<String, Object> result = parseJson("{enabled:true, disabled:false}");
        assertEquals(true, result.get("enabled"));
        assertEquals(false, result.get("disabled"));
    }

    @Test
    void testUnquotedKeyWithNullValue() {
        Map<String, Object> result = parseJson("{missing:null}");
        assertTrue(result.containsKey("missing"));
        assertNull(result.get("missing"));
    }

    @Test
    void testUnquotedKeyNoWhitespace() {
        Map<String, Object> result = parseJson("{key:\"value\"}");
        assertEquals("value", result.get("key"));
    }

    @Test
    void testUnquotedKeyWithWhitespace() {
        Map<String, Object> result = parseJson("{ key : \"value\" }");
        assertEquals("value", result.get("key"));
    }

    @Test
    void testLongUnquotedKey() {
        Map<String, Object> result = parseJson("{thisIsAVeryLongFieldNameThatShouldStillWork:\"yes\"}");
        assertEquals("yes", result.get("thisIsAVeryLongFieldNameThatShouldStillWork"));
    }

    // ========== toMaps Mode Tests ==========

    @Test
    void testUnquotedKeysInMapsMode() {
        Map<String, Object> result = parseJson("{name:\"John\", age:30}");
        assertEquals("John", result.get("name"));
        assertEquals(30L, result.get("age"));
    }

    @Test
    void testStrictModeInMapsMode() {
        String json = "{name:\"John\"}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toMaps(json, strictOptions).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unquoted field names not allowed in strict JSON mode");
    }
}
