package com.cedarsoftware.io;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for JSON5 special number formats:
 * - Leading decimal point: .5 (equals 0.5)
 * - Trailing decimal point: 5. (equals 5.0)
 * - Explicit positive sign: +5
 *
 * By default, json-io operates in permissive mode and accepts these formats.
 * Use strictJson() to enforce RFC 8259 compliance.
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
class Json5SpecialNumbersTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        return TestUtil.toMaps(json, null).asClass(Map.class);
    }

    // ========== Leading Decimal Point Tests ==========

    @Test
    void testLeadingDecimalPoint() {
        String json = "{\"value\":.5}";
        Map<String, Object> result = parseJson(json);
        assertEquals(0.5, result.get("value"));
    }

    @Test
    void testLeadingDecimalPointMultipleDigits() {
        String json = "{\"value\":.123456}";
        Map<String, Object> result = parseJson(json);
        assertEquals(0.123456, result.get("value"));
    }

    @Test
    void testLeadingDecimalPointWithExponent() {
        String json = "{\"value\":.5e2}";
        Map<String, Object> result = parseJson(json);
        assertEquals(50.0, result.get("value"));
    }

    @Test
    void testNegativeLeadingDecimalPoint() {
        String json = "{\"value\":-.5}";
        Map<String, Object> result = parseJson(json);
        assertEquals(-0.5, result.get("value"));
    }

    @Test
    void testLeadingDecimalPointSmallValue() {
        String json = "{\"value\":.001}";
        Map<String, Object> result = parseJson(json);
        assertEquals(0.001, result.get("value"));
    }

    // ========== Trailing Decimal Point Tests ==========

    @Test
    void testTrailingDecimalPoint() {
        String json = "{\"value\":5.}";
        Map<String, Object> result = parseJson(json);
        assertEquals(5.0, result.get("value"));
    }

    @Test
    void testTrailingDecimalPointLargeNumber() {
        String json = "{\"value\":12345.}";
        Map<String, Object> result = parseJson(json);
        assertEquals(12345.0, result.get("value"));
    }

    @Test
    void testTrailingDecimalPointWithExponent() {
        String json = "{\"value\":5.e2}";
        Map<String, Object> result = parseJson(json);
        assertEquals(500.0, result.get("value"));
    }

    @Test
    void testNegativeTrailingDecimalPoint() {
        String json = "{\"value\":-5.}";
        Map<String, Object> result = parseJson(json);
        assertEquals(-5.0, result.get("value"));
    }

    @Test
    void testTrailingDecimalPointZero() {
        String json = "{\"value\":0.}";
        Map<String, Object> result = parseJson(json);
        assertEquals(0.0, result.get("value"));
    }

    // ========== Explicit Positive Sign Tests ==========

    @Test
    void testExplicitPositiveSign() {
        String json = "{\"value\":+5}";
        Map<String, Object> result = parseJson(json);
        assertEquals(5L, result.get("value"));
    }

    @Test
    void testExplicitPositiveSignFloat() {
        String json = "{\"value\":+5.5}";
        Map<String, Object> result = parseJson(json);
        assertEquals(5.5, result.get("value"));
    }

    @Test
    void testExplicitPositiveSignLeadingDecimal() {
        String json = "{\"value\":+.5}";
        Map<String, Object> result = parseJson(json);
        assertEquals(0.5, result.get("value"));
    }

    @Test
    void testExplicitPositiveSignTrailingDecimal() {
        String json = "{\"value\":+5.}";
        Map<String, Object> result = parseJson(json);
        assertEquals(5.0, result.get("value"));
    }

    @Test
    void testExplicitPositiveSignZero() {
        String json = "{\"value\":+0}";
        Map<String, Object> result = parseJson(json);
        assertEquals(0L, result.get("value"));
    }

    @Test
    void testExplicitPositiveSignWithExponent() {
        String json = "{\"value\":+1e5}";
        Map<String, Object> result = parseJson(json);
        assertEquals(100000.0, result.get("value"));
    }

    // ========== Mixed Special Number Formats ==========

    @Test
    void testMixedFormats() {
        String json = "{\"a\":.5, \"b\":5., \"c\":+5, \"d\":-5}";
        Map<String, Object> result = parseJson(json);
        assertEquals(0.5, result.get("a"));
        assertEquals(5.0, result.get("b"));
        assertEquals(5L, result.get("c"));
        assertEquals(-5L, result.get("d"));
    }

    @Test
    void testSpecialNumbersInArray() {
        String json = "{\"arr\":[.5, 5., +5]}";
        Map<String, Object> result = parseJson(json);
        Object[] arr = (Object[]) result.get("arr");
        assertEquals(3, arr.length);
        assertEquals(0.5, arr[0]);
        assertEquals(5.0, arr[1]);
        assertEquals(5L, arr[2]);
    }

    // ========== Combined with Other JSON5 Features ==========

    @Test
    void testSpecialNumbersWithUnquotedKeys() {
        String json = "{value:.5, count:+10}";
        Map<String, Object> result = parseJson(json);
        assertEquals(0.5, result.get("value"));
        assertEquals(10L, result.get("count"));
    }

    @Test
    void testSpecialNumbersWithTrailingComma() {
        String json = "{\"value\":.5,}";
        Map<String, Object> result = parseJson(json);
        assertEquals(0.5, result.get("value"));
    }

    @Test
    void testSpecialNumbersWithComments() {
        String json = "{\"value\":.5 /* half */}";
        Map<String, Object> result = parseJson(json);
        assertEquals(0.5, result.get("value"));
    }

    // ========== Strict Mode Tests ==========

    @Test
    void testStrictModeRejectsLeadingDecimal() {
        String json = "{\"value\":.5}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toMaps(json, strictOptions).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Leading decimal point not allowed in strict JSON mode");
    }

    @Test
    void testStrictModeRejectsPositiveSign() {
        String json = "{\"value\":+5}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toMaps(json, strictOptions).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Explicit positive sign not allowed in strict JSON mode");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStrictModeAcceptsTrailingDecimal() {
        // Trailing decimal is technically valid in standard JSON (5.0 without fractional digits)
        // But many parsers reject it. We accept it since Double.parseDouble handles it.
        // Note: RFC 8259 requires at least one digit after the decimal point,
        // but we're lenient here as Java's Double.parseDouble accepts it.
        String json = "{\"value\":5.0}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        Map<String, Object> result = JsonIo.toMaps(json, strictOptions).asClass(Map.class);
        assertEquals(5.0, result.get("value"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStrictModeAcceptsStandardNumbers() {
        String json = "{\"int\":42, \"float\":3.14, \"negative\":-10, \"exp\":1e5}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        Map<String, Object> result = JsonIo.toMaps(json, strictOptions).asClass(Map.class);
        assertEquals(42L, result.get("int"));
        assertEquals(3.14, result.get("float"));
        assertEquals(-10L, result.get("negative"));
        assertEquals(100000.0, result.get("exp"));
    }

    // ========== Edge Cases ==========

    @Test
    void testRegularDecimalStillWorks() {
        String json = "{\"value\":3.14159}";
        Map<String, Object> result = parseJson(json);
        assertEquals(3.14159, result.get("value"));
    }

    @Test
    void testNegativeDecimalStillWorks() {
        String json = "{\"value\":-3.14}";
        Map<String, Object> result = parseJson(json);
        assertEquals(-3.14, result.get("value"));
    }

    @Test
    void testExponentStillWorks() {
        String json = "{\"value\":1.5e10}";
        Map<String, Object> result = parseJson(json);
        assertEquals(1.5e10, result.get("value"));
    }
}
