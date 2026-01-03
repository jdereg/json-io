package com.cedarsoftware.io;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for JSON5 comment support.
 *
 * JSON5 allows two types of comments:
 * - Single-line comments: // comment until end of line
 * - Block comments: slash-star comment star-slash
 *
 * Comments can appear anywhere whitespace is allowed.
 * By default, json-io operates in permissive mode and accepts comments.
 * Use strictJson() to enforce RFC 8259 compliance (no comments allowed).
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
class Json5CommentsTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        return TestUtil.toMaps(json, null).asClass(Map.class);
    }

    // ========== Single-line Comment Tests ==========

    @Test
    void testSingleLineCommentAtStart() {
        String json = "// This is a comment\n{\"name\":\"John\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("John", result.get("name"));
    }

    @Test
    void testSingleLineCommentAfterValue() {
        String json = "{\"name\":\"John\" // inline comment\n}";
        Map<String, Object> result = parseJson(json);
        assertEquals("John", result.get("name"));
    }

    @Test
    void testSingleLineCommentAfterColon() {
        String json = "{\"name\": // comment here\n\"John\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("John", result.get("name"));
    }

    @Test
    void testSingleLineCommentBetweenFields() {
        String json = "{\"a\":1, // comment\n\"b\":2}";
        Map<String, Object> result = parseJson(json);
        assertEquals(1L, result.get("a"));
        assertEquals(2L, result.get("b"));
    }

    @Test
    void testMultipleSingleLineComments() {
        String json = "// first comment\n// second comment\n{\"x\":42}";
        Map<String, Object> result = parseJson(json);
        assertEquals(42L, result.get("x"));
    }

    @Test
    void testSingleLineCommentAtEnd() {
        String json = "{\"value\":100}// trailing comment";
        Map<String, Object> result = parseJson(json);
        assertEquals(100L, result.get("value"));
    }

    @Test
    void testSingleLineCommentWithCarriageReturn() {
        String json = "// comment\r{\"key\":\"value\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("value", result.get("key"));
    }

    @Test
    void testSingleLineCommentWithCRLF() {
        String json = "// comment\r\n{\"key\":\"value\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("value", result.get("key"));
    }

    // ========== Block Comment Tests ==========

    @Test
    void testBlockCommentAtStart() {
        String json = "/* This is a block comment */{\"name\":\"John\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("John", result.get("name"));
    }

    @Test
    void testBlockCommentAfterValue() {
        String json = "{\"name\":\"John\" /* inline block comment */}";
        Map<String, Object> result = parseJson(json);
        assertEquals("John", result.get("name"));
    }

    @Test
    void testBlockCommentBetweenFields() {
        String json = "{\"a\":1, /* comment */ \"b\":2}";
        Map<String, Object> result = parseJson(json);
        assertEquals(1L, result.get("a"));
        assertEquals(2L, result.get("b"));
    }

    @Test
    void testMultiLineBlockComment() {
        String json = "{\n/* This comment\n   spans multiple\n   lines */\n\"key\":\"value\"\n}";
        Map<String, Object> result = parseJson(json);
        assertEquals("value", result.get("key"));
    }

    @Test
    void testBlockCommentWithAsterisks() {
        String json = "{ /* ** stars ** */ \"key\":\"value\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("value", result.get("key"));
    }

    @Test
    void testNestedAsterisksInBlockComment() {
        String json = "{ /* * * * not end * * * */ \"key\":\"value\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("value", result.get("key"));
    }

    @Test
    void testBlockCommentAfterColon() {
        String json = "{\"name\": /* comment */ \"John\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("John", result.get("name"));
    }

    // ========== Mixed Comments Tests ==========

    @Test
    void testMixedComments() {
        String json = "// single line\n{/* block */\"a\":1// another single\n}";
        Map<String, Object> result = parseJson(json);
        assertEquals(1L, result.get("a"));
    }

    @Test
    void testConsecutiveComments() {
        String json = "// comment 1\n/* comment 2 *//* comment 3 */{\"x\":1}";
        Map<String, Object> result = parseJson(json);
        assertEquals(1L, result.get("x"));
    }

    // ========== Comments with Arrays ==========

    @Test
    void testCommentsInArray() {
        String json = "{\"arr\":[1, // comment\n2, /* block */ 3]}";
        Map<String, Object> result = parseJson(json);
        assertNotNull(result.get("arr"));
    }

    @Test
    void testCommentBeforeArrayElement() {
        String json = "{\"arr\":[/* first */1, /* second */2]}";
        Map<String, Object> result = parseJson(json);
        assertNotNull(result.get("arr"));
    }

    // ========== Comments with Nested Objects ==========

    @Test
    @SuppressWarnings("unchecked")
    void testCommentsInNestedObject() {
        String json = "{\"outer\":{// inner comment\n\"inner\":\"value\"}}";
        Map<String, Object> result = parseJson(json);
        Map<String, Object> outer = (Map<String, Object>) result.get("outer");
        assertEquals("value", outer.get("inner"));
    }

    // ========== Edge Cases ==========

    @Test
    void testEmptyBlockComment() {
        String json = "{/**/\"key\":\"value\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("value", result.get("key"));
    }

    @Test
    void testEmptySingleLineComment() {
        String json = "//\n{\"key\":\"value\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("value", result.get("key"));
    }

    @Test
    void testSlashInString() {
        // Slash in string value should NOT be treated as comment
        String json = "{\"url\":\"http://example.com\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("http://example.com", result.get("url"));
    }

    @Test
    void testSlashNotFollowedBySlashOrStar() {
        // Single slash outside string is an error (invalid JSON)
        String json = "{\"a\":1 / \"b\":2}";
        assertThatThrownBy(() -> parseJson(json))
                .isInstanceOf(JsonIoException.class);
    }

    @Test
    void testUnterminatedBlockComment() {
        String json = "{ /* unterminated comment \"key\":\"value\"}";
        assertThatThrownBy(() -> parseJson(json))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unterminated block comment");
    }

    // ========== Strict Mode Tests ==========

    @Test
    void testStrictModeRejectsSingleLineComment() {
        String json = "// comment\n{\"key\":\"value\"}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toMaps(json, strictOptions).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Comments not allowed in strict JSON mode");
    }

    @Test
    void testStrictModeRejectsBlockComment() {
        String json = "/* comment */{\"key\":\"value\"}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toMaps(json, strictOptions).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Comments not allowed in strict JSON mode");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStrictModeAcceptsStandardJson() {
        String json = "{\"name\":\"John\", \"age\":30}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        Map<String, Object> result = JsonIo.toMaps(json, strictOptions).asClass(Map.class);
        assertEquals("John", result.get("name"));
        assertEquals(30L, result.get("age"));
    }

    // ========== Comments with All Value Types ==========

    @Test
    void testCommentBeforeNumber() {
        String json = "{\"num\":/* comment */42}";
        Map<String, Object> result = parseJson(json);
        assertEquals(42L, result.get("num"));
    }

    @Test
    void testCommentBeforeBoolean() {
        String json = "{\"flag\":/* true or false? */true}";
        Map<String, Object> result = parseJson(json);
        assertEquals(true, result.get("flag"));
    }

    @Test
    void testCommentBeforeNull() {
        String json = "{\"nothing\":/* nullable */null}";
        Map<String, Object> result = parseJson(json);
        assertTrue(result.containsKey("nothing"));
        assertNull(result.get("nothing"));
    }

    @Test
    void testCommentBeforeString() {
        String json = "{\"text\":/* string value */\"hello\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("hello", result.get("text"));
    }

    @Test
    void testCommentBeforeObject() {
        String json = "{\"obj\":/* nested */{\"a\":1}}";
        Map<String, Object> result = parseJson(json);
        assertNotNull(result.get("obj"));
    }

    @Test
    void testCommentBeforeArray() {
        String json = "{\"arr\":/* array */[1,2,3]}";
        Map<String, Object> result = parseJson(json);
        assertNotNull(result.get("arr"));
    }
}
