package com.cedarsoftware.io;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for JSON5 trailing comma support.
 *
 * JSON5 allows trailing commas in both objects and arrays:
 * - Objects: {"a": 1, "b": 2,}
 * - Arrays: [1, 2, 3,]
 *
 * By default, json-io operates in permissive mode and accepts trailing commas.
 * Use strictJson() to enforce RFC 8259 compliance (no trailing commas allowed).
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
class Json5TrailingCommasTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        return TestUtil.toMaps(json, null).asClass(Map.class);
    }

    // ========== Object Trailing Comma Tests ==========

    @Test
    void testTrailingCommaInObject() {
        String json = "{\"a\":1, \"b\":2,}";
        Map<String, Object> result = parseJson(json);
        assertEquals(1L, result.get("a"));
        assertEquals(2L, result.get("b"));
    }

    @Test
    void testTrailingCommaInObjectSingleField() {
        String json = "{\"only\":42,}";
        Map<String, Object> result = parseJson(json);
        assertEquals(42L, result.get("only"));
    }

    @Test
    void testTrailingCommaWithWhitespace() {
        String json = "{\"a\":1, \"b\":2 , }";
        Map<String, Object> result = parseJson(json);
        assertEquals(1L, result.get("a"));
        assertEquals(2L, result.get("b"));
    }

    @Test
    void testTrailingCommaWithNewline() {
        String json = "{\n  \"a\":1,\n  \"b\":2,\n}";
        Map<String, Object> result = parseJson(json);
        assertEquals(1L, result.get("a"));
        assertEquals(2L, result.get("b"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTrailingCommaInNestedObject() {
        String json = "{\"outer\":{\"inner\":1,},}";
        Map<String, Object> result = parseJson(json);
        Map<String, Object> outer = (Map<String, Object>) result.get("outer");
        assertEquals(1L, outer.get("inner"));
    }

    // ========== Array Trailing Comma Tests ==========

    @Test
    void testTrailingCommaInArray() {
        String json = "{\"arr\":[1, 2, 3,]}";
        Map<String, Object> result = parseJson(json);
        Object[] arr = (Object[]) result.get("arr");
        assertEquals(3, arr.length);
        assertEquals(1L, arr[0]);
        assertEquals(2L, arr[1]);
        assertEquals(3L, arr[2]);
    }

    @Test
    void testTrailingCommaInArraySingleElement() {
        String json = "{\"arr\":[42,]}";
        Map<String, Object> result = parseJson(json);
        Object[] arr = (Object[]) result.get("arr");
        assertEquals(1, arr.length);
        assertEquals(42L, arr[0]);
    }

    @Test
    void testTrailingCommaInArrayWithWhitespace() {
        String json = "{\"arr\":[1, 2, 3 , ]}";
        Map<String, Object> result = parseJson(json);
        Object[] arr = (Object[]) result.get("arr");
        assertEquals(3, arr.length);
    }

    @Test
    void testTrailingCommaInArrayWithNewlines() {
        String json = "{\"arr\":[\n  1,\n  2,\n  3,\n]}";
        Map<String, Object> result = parseJson(json);
        Object[] arr = (Object[]) result.get("arr");
        assertEquals(3, arr.length);
    }

    @Test
    void testTrailingCommaInNestedArray() {
        String json = "{\"arr\":[[1, 2,], [3, 4,],]}";
        Map<String, Object> result = parseJson(json);
        Object[] arr = (Object[]) result.get("arr");
        assertEquals(2, arr.length);
    }

    // ========== Mixed Object and Array Tests ==========

    @Test
    void testTrailingCommasBothObjectAndArray() {
        String json = "{\"values\":[1, 2,], \"name\":\"test\",}";
        Map<String, Object> result = parseJson(json);
        Object[] values = (Object[]) result.get("values");
        assertEquals(2, values.length);
        assertEquals("test", result.get("name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDeeplyNestedTrailingCommas() {
        String json = "{\"a\":{\"b\":{\"c\":[1,2,],},},}";
        Map<String, Object> result = parseJson(json);
        Map<String, Object> a = (Map<String, Object>) result.get("a");
        Map<String, Object> b = (Map<String, Object>) a.get("b");
        Object[] c = (Object[]) b.get("c");
        assertEquals(2, c.length);
    }

    // ========== Trailing Commas with Other JSON5 Features ==========

    @Test
    void testTrailingCommaWithUnquotedKeys() {
        String json = "{a:1, b:2,}";
        Map<String, Object> result = parseJson(json);
        assertEquals(1L, result.get("a"));
        assertEquals(2L, result.get("b"));
    }

    @Test
    void testTrailingCommaWithComments() {
        String json = "{\"a\":1, // comment\n\"b\":2, /* block */}";
        Map<String, Object> result = parseJson(json);
        assertEquals(1L, result.get("a"));
        assertEquals(2L, result.get("b"));
    }

    @Test
    void testTrailingCommaAfterComment() {
        String json = "{\"a\":1, \"b\":2, // trailing comment\n}";
        Map<String, Object> result = parseJson(json);
        assertEquals(1L, result.get("a"));
        assertEquals(2L, result.get("b"));
    }

    // ========== Strict Mode Tests ==========

    @Test
    void testStrictModeRejectsTrailingCommaInObject() {
        String json = "{\"a\":1, \"b\":2,}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toMaps(json, strictOptions).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Trailing commas not allowed in strict JSON mode");
    }

    @Test
    void testStrictModeRejectsTrailingCommaInArray() {
        String json = "{\"arr\":[1, 2, 3,]}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toMaps(json, strictOptions).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Trailing commas not allowed in strict JSON mode");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStrictModeAcceptsStandardJson() {
        String json = "{\"a\":1, \"b\":2}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        Map<String, Object> result = JsonIo.toMaps(json, strictOptions).asClass(Map.class);
        assertEquals(1L, result.get("a"));
        assertEquals(2L, result.get("b"));
    }

    @Test
    void testStrictModeAcceptsArrayWithoutTrailingComma() {
        String json = "{\"arr\":[1, 2, 3]}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        Map<String, Object> result = JsonIo.toMaps(json, strictOptions).asClass(Map.class);
        Object[] arr = (Object[]) result.get("arr");
        assertEquals(3, arr.length);
    }

    // ========== Edge Cases ==========

    @Test
    void testNoTrailingCommaStillWorks() {
        String json = "{\"a\":1, \"b\":2}";
        Map<String, Object> result = parseJson(json);
        assertEquals(1L, result.get("a"));
        assertEquals(2L, result.get("b"));
    }

    @Test
    void testArrayWithMixedTypes() {
        String json = "{\"arr\":[1, \"two\", true, null,]}";
        Map<String, Object> result = parseJson(json);
        Object[] arr = (Object[]) result.get("arr");
        assertEquals(4, arr.length);
        assertEquals(1L, arr[0]);
        assertEquals("two", arr[1]);
        assertEquals(true, arr[2]);
        assertEquals(null, arr[3]);
    }

    @Test
    void testEmptyArrayNoTrailingComma() {
        String json = "{\"arr\":[]}";
        Map<String, Object> result = parseJson(json);
        Object[] arr = (Object[]) result.get("arr");
        assertNotNull(arr);
        assertEquals(0, arr.length);
    }

    @Test
    void testEmptyObjectNoTrailingComma() {
        String json = "{}";
        Map<String, Object> result = parseJson(json);
        assertNotNull(result);
        assertEquals(0, result.size());
    }
}
