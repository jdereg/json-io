package com.cedarsoftware.io;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for JSON5 hexadecimal number support.
 *
 * JSON5 allows hexadecimal integer literals:
 * - Lowercase: 0xff
 * - Uppercase: 0xFF
 * - Mixed case: 0xAbCd
 * - Negative: -0xFF
 *
 * By default, json-io operates in permissive mode and accepts hex numbers.
 * Use strictJson() to enforce RFC 8259 compliance (no hex numbers).
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
class Json5HexNumbersTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        return TestUtil.toMaps(json, null).asClass(Map.class);
    }

    // ========== Basic Hex Number Tests ==========

    @Test
    void testHexNumberLowercase() {
        String json = "{\"value\":0xff}";
        Map<String, Object> result = parseJson(json);
        assertEquals(255L, result.get("value"));
    }

    @Test
    void testHexNumberUppercase() {
        String json = "{\"value\":0xFF}";
        Map<String, Object> result = parseJson(json);
        assertEquals(255L, result.get("value"));
    }

    @Test
    void testHexNumberMixedCase() {
        String json = "{\"value\":0xAbCdEf}";
        Map<String, Object> result = parseJson(json);
        assertEquals(0xABCDEFL, result.get("value"));
    }

    @Test
    void testHexNumberUppercaseX() {
        String json = "{\"value\":0XFF}";
        Map<String, Object> result = parseJson(json);
        assertEquals(255L, result.get("value"));
    }

    @Test
    void testHexNumberZero() {
        String json = "{\"value\":0x0}";
        Map<String, Object> result = parseJson(json);
        assertEquals(0L, result.get("value"));
    }

    @Test
    void testHexNumberOne() {
        String json = "{\"value\":0x1}";
        Map<String, Object> result = parseJson(json);
        assertEquals(1L, result.get("value"));
    }

    // ========== Negative Hex Numbers ==========

    @Test
    void testNegativeHexNumber() {
        String json = "{\"value\":-0xff}";
        Map<String, Object> result = parseJson(json);
        assertEquals(-255L, result.get("value"));
    }

    @Test
    void testNegativeHexNumberLarge() {
        String json = "{\"value\":-0xABCD}";
        Map<String, Object> result = parseJson(json);
        assertEquals(-0xABCDL, result.get("value"));
    }

    // ========== Various Hex Values ==========

    @Test
    void testHexSingleDigit() {
        String json = "{\"a\":0x0,\"b\":0x1,\"c\":0x9,\"d\":0xa,\"e\":0xf}";
        Map<String, Object> result = parseJson(json);
        assertEquals(0L, result.get("a"));
        assertEquals(1L, result.get("b"));
        assertEquals(9L, result.get("c"));
        assertEquals(10L, result.get("d"));
        assertEquals(15L, result.get("e"));
    }

    @Test
    void testHexCommonValues() {
        String json = "{\"byte\":0xFF,\"short\":0xFFFF,\"int\":0xFFFFFFFF}";
        Map<String, Object> result = parseJson(json);
        assertEquals(255L, result.get("byte"));
        assertEquals(65535L, result.get("short"));
        assertEquals(0xFFFFFFFFL, result.get("int"));
    }

    @Test
    void testHexLargeValue() {
        String json = "{\"value\":0x7FFFFFFFFFFFFFFF}";
        Map<String, Object> result = parseJson(json);
        assertEquals(Long.MAX_VALUE, result.get("value"));
    }

    @Test
    void testHexMaxUnsigned() {
        // 16 hex digits = full 64-bit value
        String json = "{\"value\":0xFFFFFFFFFFFFFFFF}";
        Map<String, Object> result = parseJson(json);
        assertEquals(-1L, result.get("value")); // Wraps to -1 as signed long
    }

    // ========== Hex in Arrays ==========

    @Test
    void testHexInArray() {
        String json = "{\"arr\":[0x10, 0x20, 0x30]}";
        Map<String, Object> result = parseJson(json);
        Object[] arr = (Object[]) result.get("arr");
        assertEquals(3, arr.length);
        assertEquals(16L, arr[0]);
        assertEquals(32L, arr[1]);
        assertEquals(48L, arr[2]);
    }

    // ========== Mixed with Other JSON5 Features ==========

    @Test
    void testHexWithUnquotedKeys() {
        String json = "{value:0xFF, count:0x10}";
        Map<String, Object> result = parseJson(json);
        assertEquals(255L, result.get("value"));
        assertEquals(16L, result.get("count"));
    }

    @Test
    void testHexWithTrailingComma() {
        String json = "{\"value\":0xFF,}";
        Map<String, Object> result = parseJson(json);
        assertEquals(255L, result.get("value"));
    }

    @Test
    void testHexWithComments() {
        String json = "{\"value\":0xFF /* hex value */}";
        Map<String, Object> result = parseJson(json);
        assertEquals(255L, result.get("value"));
    }

    @Test
    void testHexWithSingleQuotedKey() {
        String json = "{'value':0xFF}";
        Map<String, Object> result = parseJson(json);
        assertEquals(255L, result.get("value"));
    }

    // ========== Mixed Decimal and Hex ==========

    @Test
    void testMixedDecimalAndHex() {
        String json = "{\"dec\":255,\"hex\":0xFF}";
        Map<String, Object> result = parseJson(json);
        assertEquals(255L, result.get("dec"));
        assertEquals(255L, result.get("hex"));
    }

    // ========== Strict Mode Tests ==========

    @Test
    void testStrictModeRejectsHexNumber() {
        String json = "{\"value\":0xFF}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toMaps(json, strictOptions).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Hexadecimal numbers not allowed in strict JSON mode");
    }

    @Test
    void testStrictModeRejectsNegativeHexNumber() {
        String json = "{\"value\":-0xFF}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toMaps(json, strictOptions).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Hexadecimal numbers not allowed in strict JSON mode");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStrictModeAcceptsDecimalNumbers() {
        String json = "{\"value\":255}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        Map<String, Object> result = JsonIo.toMaps(json, strictOptions).asClass(Map.class);
        assertEquals(255L, result.get("value"));
    }

    // ========== Edge Cases ==========

    @Test
    void testRegularZeroStillWorks() {
        String json = "{\"value\":0}";
        Map<String, Object> result = parseJson(json);
        assertEquals(0L, result.get("value"));
    }

    @Test
    void testZeroPointFiveStillWorks() {
        String json = "{\"value\":0.5}";
        Map<String, Object> result = parseJson(json);
        assertEquals(0.5, result.get("value"));
    }

    @Test
    void testHexInvalidDigit() {
        String json = "{\"value\":0xGG}";
        assertThatThrownBy(() -> parseJson(json))
                .isInstanceOf(JsonIoException.class);
    }

    @Test
    void testHexNoDigits() {
        String json = "{\"value\":0x}";
        assertThatThrownBy(() -> parseJson(json))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Expected hexadecimal digit after 0x");
    }
}
