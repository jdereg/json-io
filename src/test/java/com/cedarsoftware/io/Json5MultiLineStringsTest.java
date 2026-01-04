package com.cedarsoftware.io;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for JSON5 multi-line string support.
 *
 * JSON5 allows strings to span multiple lines using backslash line continuation:
 * - Backslash followed by LF (\n) removes both
 * - Backslash followed by CR (\r) removes both
 * - Backslash followed by CRLF (\r\n) removes all three characters
 *
 * The result is a single-line string with the line terminator removed.
 *
 * By default, json-io operates in permissive mode and accepts multi-line strings.
 * Use strictJson() to enforce RFC 8259 compliance (no multi-line strings).
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
class Json5MultiLineStringsTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        return TestUtil.toMaps(json, null).asClass(Map.class);
    }

    // ========== Basic Multi-line String Tests (LF) ==========

    @Test
    void testMultiLineStringWithLF() {
        // Backslash followed by newline - both are removed
        String json = "{\"value\":\"hello \\\nworld\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("hello world", result.get("value"));
    }

    @Test
    void testMultiLineStringStartOfLine() {
        // Line continuation at start of continuation line
        String json = "{\"value\":\"hello\\\nworld\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("helloworld", result.get("value"));
    }

    @Test
    void testMultiLineStringPreservesLeadingWhitespace() {
        // Leading whitespace on continuation line is preserved
        String json = "{\"value\":\"hello\\\n    world\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("hello    world", result.get("value"));
    }

    @Test
    void testMultiLineStringMultipleContinuations() {
        // Multiple line continuations
        String json = "{\"value\":\"line1\\\nline2\\\nline3\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("line1line2line3", result.get("value"));
    }

    // ========== Multi-line String Tests (CR) ==========

    @Test
    void testMultiLineStringWithCR() {
        // Backslash followed by carriage return
        String json = "{\"value\":\"hello \\\rworld\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("hello world", result.get("value"));
    }

    // ========== Multi-line String Tests (CRLF) ==========

    @Test
    void testMultiLineStringWithCRLF() {
        // Backslash followed by CRLF (Windows line ending)
        String json = "{\"value\":\"hello \\\r\nworld\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("hello world", result.get("value"));
    }

    @Test
    void testMultiLineStringMultipleCRLF() {
        // Multiple CRLF continuations
        String json = "{\"value\":\"a\\\r\nb\\\r\nc\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("abc", result.get("value"));
    }

    // ========== Single-quoted Multi-line Strings ==========

    @Test
    void testMultiLineStringSingleQuoted() {
        // Multi-line with single quotes
        String json = "{\"value\":'hello \\\nworld'}";
        Map<String, Object> result = parseJson(json);
        assertEquals("hello world", result.get("value"));
    }

    @Test
    void testMultiLineStringSingleQuotedKey() {
        // Multi-line in single-quoted key
        String json = "{'multi\\\nkey':\"value\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("value", result.get("multikey"));
    }

    // ========== Multi-line Strings in Arrays ==========

    @Test
    void testMultiLineStringInArray() {
        String json = "{\"arr\":[\"first\\\nsecond\", \"third\"]}";
        Map<String, Object> result = parseJson(json);
        Object[] arr = (Object[]) result.get("arr");
        assertEquals(2, arr.length);
        assertEquals("firstsecond", arr[0]);
        assertEquals("third", arr[1]);
    }

    // ========== Combined with Other Escape Sequences ==========

    @Test
    void testMultiLineWithOtherEscapes() {
        // Multi-line combined with other escape sequences
        // \\t = tab, \\\n = line continuation (removed), \\n = newline character
        String json = "{\"value\":\"tab\\there\\\nnewline\\nend\"}";
        Map<String, Object> result = parseJson(json);
        // Line continuation removes backslash+newline, so "here" directly joins "newline"
        assertEquals("tab\therenewline\nend", result.get("value"));
    }

    @Test
    void testMultiLineWithUnicodeEscape() {
        // Multi-line with unicode escape
        String json = "{\"value\":\"hello\\u0020\\\nworld\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("hello world", result.get("value"));
    }

    // ========== Combined with Other JSON5 Features ==========

    @Test
    void testMultiLineWithUnquotedKeys() {
        String json = "{text:\"hello\\\nworld\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("helloworld", result.get("text"));
    }

    @Test
    void testMultiLineWithTrailingComma() {
        String json = "{\"value\":\"multi\\\nline\",}";
        Map<String, Object> result = parseJson(json);
        assertEquals("multiline", result.get("value"));
    }

    @Test
    void testMultiLineWithComments() {
        String json = "{\"value\":\"hello\\\nworld\" /* multi-line string */}";
        Map<String, Object> result = parseJson(json);
        assertEquals("helloworld", result.get("value"));
    }

    // ========== Strict Mode Tests ==========

    @Test
    void testStrictModeRejectsMultiLineLF() {
        String json = "{\"value\":\"hello\\\nworld\"}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toMaps(json, strictOptions).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Multi-line strings not allowed in strict JSON mode");
    }

    @Test
    void testStrictModeRejectsMultiLineCR() {
        String json = "{\"value\":\"hello\\\rworld\"}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toMaps(json, strictOptions).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Multi-line strings not allowed in strict JSON mode");
    }

    @Test
    void testStrictModeRejectsMultiLineCRLF() {
        String json = "{\"value\":\"hello\\\r\nworld\"}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toMaps(json, strictOptions).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Multi-line strings not allowed in strict JSON mode");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStrictModeAcceptsNormalStrings() {
        String json = "{\"value\":\"hello world\"}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        Map<String, Object> result = JsonIo.toMaps(json, strictOptions).asClass(Map.class);
        assertEquals("hello world", result.get("value"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStrictModeAcceptsEscapedNewline() {
        // \\n is an escaped newline character, not a line continuation
        String json = "{\"value\":\"hello\\nworld\"}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        Map<String, Object> result = JsonIo.toMaps(json, strictOptions).asClass(Map.class);
        assertEquals("hello\nworld", result.get("value"));
    }

    // ========== Edge Cases ==========

    @Test
    void testMultiLineEmptyMiddleLine() {
        // Empty line in between
        String json = "{\"value\":\"hello\\\n\\\nworld\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("helloworld", result.get("value"));
    }

    @Test
    void testMultiLineOnlyWhitespace() {
        // Just whitespace after continuation
        String json = "{\"value\":\"\\\n   \"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("   ", result.get("value"));
    }

    @Test
    void testRegularNewlineEscapeStillWorks() {
        // \\n should still produce a newline character
        String json = "{\"value\":\"line1\\nline2\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("line1\nline2", result.get("value"));
    }

    @Test
    void testRegularCarriageReturnEscapeStillWorks() {
        // \\r should still produce a carriage return character
        String json = "{\"value\":\"line1\\rline2\"}";
        Map<String, Object> result = parseJson(json);
        assertEquals("line1\rline2", result.get("value"));
    }
}
