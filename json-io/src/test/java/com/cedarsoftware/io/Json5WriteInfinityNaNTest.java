package com.cedarsoftware.io;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JSON5 Infinity and NaN writing support.
 *
 * When json5InfinityNaN() is enabled, Double and Float special values
 * (Infinity, -Infinity, NaN) are written as literals instead of null.
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
class Json5WriteInfinityNaNTest {

    // ========== Infinity Tests ==========

    @Test
    void testPositiveInfinityDouble() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("value", Double.POSITIVE_INFINITY);

        WriteOptions options = new WriteOptionsBuilder().json5InfinityNaN(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("Infinity"), "Should contain Infinity literal: " + json);
        assertFalse(json.contains("null"), "Should not contain null: " + json);
    }

    @Test
    void testNegativeInfinityDouble() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("value", Double.NEGATIVE_INFINITY);

        WriteOptions options = new WriteOptionsBuilder().json5InfinityNaN(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("-Infinity"), "Should contain -Infinity literal: " + json);
        assertFalse(json.contains("null"), "Should not contain null: " + json);
    }

    @Test
    void testPositiveInfinityFloat() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("value", Float.POSITIVE_INFINITY);

        WriteOptions options = new WriteOptionsBuilder().json5InfinityNaN(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("Infinity"), "Should contain Infinity literal: " + json);
        assertFalse(json.contains("null"), "Should not contain null: " + json);
    }

    @Test
    void testNegativeInfinityFloat() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("value", Float.NEGATIVE_INFINITY);

        WriteOptions options = new WriteOptionsBuilder().json5InfinityNaN(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("-Infinity"), "Should contain -Infinity literal: " + json);
        assertFalse(json.contains("null"), "Should not contain null: " + json);
    }

    // ========== NaN Tests ==========

    @Test
    void testNaNDouble() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("value", Double.NaN);

        WriteOptions options = new WriteOptionsBuilder().json5InfinityNaN(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("NaN"), "Should contain NaN literal: " + json);
        assertFalse(json.contains("null"), "Should not contain null: " + json);
    }

    @Test
    void testNaNFloat() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("value", Float.NaN);

        WriteOptions options = new WriteOptionsBuilder().json5InfinityNaN(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("NaN"), "Should contain NaN literal: " + json);
        assertFalse(json.contains("null"), "Should not contain null: " + json);
    }

    // ========== Default Behavior (Disabled) ==========

    @Test
    void testDefaultBehaviorWritesNull() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("infinity", Double.POSITIVE_INFINITY);
        map.put("nan", Double.NaN);

        WriteOptions options = new WriteOptionsBuilder().build();
        String json = JsonIo.toJson(map, options);

        // Default behavior writes null for special values
        assertTrue(json.contains("null"), "Default should write null: " + json);
        assertFalse(json.contains("Infinity"), "Default should not write Infinity: " + json);
        assertFalse(json.contains("NaN"), "Default should not write NaN: " + json);
    }

    // ========== JSON5 Umbrella ==========

    @Test
    void testJson5UmbrellaEnablesInfinityNaN() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("value", Double.POSITIVE_INFINITY);

        WriteOptions options = new WriteOptionsBuilder().json5().build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("Infinity"), "json5() should enable Infinity/NaN: " + json);
    }

    // ========== Round-Trip Tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void testRoundTripInfinity() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("posInf", Double.POSITIVE_INFINITY);
        original.put("negInf", Double.NEGATIVE_INFINITY);

        WriteOptions writeOptions = new WriteOptionsBuilder().json5InfinityNaN(true).build();
        String json = JsonIo.toJson(original, writeOptions);

        // Verify literals are in output
        assertTrue(json.contains("Infinity"), "Should contain Infinity: " + json);
        assertTrue(json.contains("-Infinity"), "Should contain -Infinity: " + json);

        // json-io reads JSON5 by default, so this should work
        ReadOptions readOptions = new ReadOptionsBuilder().allowNanAndInfinity(true).build();
        Map<String, Object> result = JsonIo.toMaps(json, readOptions).asClass(Map.class);

        assertTrue(Double.isInfinite((Double) result.get("posInf")));
        assertTrue((Double) result.get("posInf") > 0);
        assertTrue(Double.isInfinite((Double) result.get("negInf")));
        assertTrue((Double) result.get("negInf") < 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRoundTripNaN() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("nan", Double.NaN);

        WriteOptions writeOptions = new WriteOptionsBuilder().json5InfinityNaN(true).build();
        String json = JsonIo.toJson(original, writeOptions);

        assertTrue(json.contains("NaN"), "Should contain NaN: " + json);

        ReadOptions readOptions = new ReadOptionsBuilder().allowNanAndInfinity(true).build();
        Map<String, Object> result = JsonIo.toMaps(json, readOptions).asClass(Map.class);

        assertTrue(Double.isNaN((Double) result.get("nan")));
    }

    // ========== Mixed Values ==========

    @Test
    void testMixedNormalAndSpecialValues() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("normal", 3.14);
        map.put("infinity", Double.POSITIVE_INFINITY);
        map.put("nan", Double.NaN);
        map.put("zero", 0.0);

        WriteOptions options = new WriteOptionsBuilder().json5InfinityNaN(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("3.14"), "Should contain normal number: " + json);
        assertTrue(json.contains("Infinity"), "Should contain Infinity: " + json);
        assertTrue(json.contains("NaN"), "Should contain NaN: " + json);
        assertTrue(json.contains("0.0") || json.contains("0"), "Should contain zero: " + json);
    }

    // ========== Array Tests ==========

    @Test
    void testSpecialValuesInArray() {
        Double[] array = {1.0, Double.POSITIVE_INFINITY, Double.NaN, Double.NEGATIVE_INFINITY};

        WriteOptions options = new WriteOptionsBuilder().json5InfinityNaN(true).build();
        String json = JsonIo.toJson(array, options);

        assertTrue(json.contains("1.0") || json.contains("1"), "Should contain 1.0: " + json);
        assertTrue(json.contains("Infinity"), "Should contain Infinity: " + json);
        assertTrue(json.contains("NaN"), "Should contain NaN: " + json);
        assertTrue(json.contains("-Infinity"), "Should contain -Infinity: " + json);
    }

    // ========== Legacy Option Compatibility ==========

    @Test
    void testLegacyAllowNanAndInfinityStillWorks() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("value", Double.POSITIVE_INFINITY);

        // Use legacy option
        WriteOptions options = new WriteOptionsBuilder().allowNanAndInfinity(true).build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("Infinity"), "Legacy option should enable Infinity: " + json);
    }

    @Test
    void testBothOptionsEnabled() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("value", Double.NaN);

        WriteOptions options = new WriteOptionsBuilder()
                .allowNanAndInfinity(true)
                .json5InfinityNaN(true)
                .build();
        String json = JsonIo.toJson(map, options);

        assertTrue(json.contains("NaN"), "Both options enabled should work: " + json);
    }
}
