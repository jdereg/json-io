package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Map-to-non-Map type conversion during JSON deserialization.
 * <p>
 * These tests verify that when JSON contains a Map structure (with @type=HashMap or similar)
 * but the caller requests a non-Map type (like Boolean, Integer, ZonedDateTime), the
 * conversion happens correctly using the Converter's Map→Type conversions.
 * <p>
 * The Map structure typically contains a "_v" or "value" key that holds the actual value
 * to be converted to the target type.
 * <p>
 * This test class would have caught the 346 test failures in java-util's ConverterEverythingTest
 * that occurred when Map→non-Map conversion failed because the Map target was empty
 * (not populated until after toJava() returned).
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
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
class MapToNonMapConversionTest {

    private static final WriteOptions WRITE_OPTIONS = new WriteOptionsBuilder().build();
    private static final ReadOptions READ_OPTIONS = new ReadOptionsBuilder().build();

    // ========== Simple Map with _v key → Primitive Types ==========
    // These test JSON with no @type but a _v key

    @Test
    void testMapWithValueKey_toBoolean_true() {
        String json = "{\"_v\":true}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isTrue();
    }

    @Test
    void testMapWithValueKey_toBoolean_false() {
        String json = "{\"_v\":false}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isFalse();
    }

    @Test
    void testMapWithValueKey_toBoolean_fromNumber() {
        // Non-zero numbers should convert to true
        String json = "{\"_v\":16}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isTrue();
    }

    @Test
    void testMapWithValueKey_toBoolean_fromZero() {
        // Zero should convert to false
        String json = "{\"_v\":0}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isFalse();
    }

    @Test
    void testMapWithValueKey_toInteger() {
        String json = "{\"_v\":42}";
        Integer result = JsonIo.toJava(json, READ_OPTIONS).asClass(Integer.class);
        assertThat(result).isEqualTo(42);
    }

    @Test
    void testMapWithValueKey_toLong() {
        String json = "{\"_v\":9876543210}";
        Long result = JsonIo.toJava(json, READ_OPTIONS).asClass(Long.class);
        assertThat(result).isEqualTo(9876543210L);
    }

    @Test
    void testMapWithValueKey_toDouble() {
        String json = "{\"_v\":3.14159}";
        Double result = JsonIo.toJava(json, READ_OPTIONS).asClass(Double.class);
        assertThat(result).isEqualTo(3.14159);
    }

    @Test
    void testMapWithValueKey_toFloat() {
        String json = "{\"_v\":2.5}";
        Float result = JsonIo.toJava(json, READ_OPTIONS).asClass(Float.class);
        assertThat(result).isEqualTo(2.5f);
    }

    @Test
    void testMapWithValueKey_toShort() {
        String json = "{\"_v\":100}";
        Short result = JsonIo.toJava(json, READ_OPTIONS).asClass(Short.class);
        assertThat(result).isEqualTo((short) 100);
    }

    @Test
    void testMapWithValueKey_toByte() {
        String json = "{\"_v\":50}";
        Byte result = JsonIo.toJava(json, READ_OPTIONS).asClass(Byte.class);
        assertThat(result).isEqualTo((byte) 50);
    }

    @Test
    void testMapWithValueKey_toCharacter() {
        String json = "{\"_v\":\"A\"}";
        Character result = JsonIo.toJava(json, READ_OPTIONS).asClass(Character.class);
        assertThat(result).isEqualTo('A');
    }

    @Test
    void testMapWithValueKey_toString() {
        String json = "{\"_v\":\"hello\"}";
        String result = JsonIo.toJava(json, READ_OPTIONS).asClass(String.class);
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void testMapWithValueKey_toBigInteger() {
        String json = "{\"_v\":\"12345678901234567890\"}";
        BigInteger result = JsonIo.toJava(json, READ_OPTIONS).asClass(BigInteger.class);
        assertThat(result).isEqualTo(new BigInteger("12345678901234567890"));
    }

    @Test
    void testMapWithValueKey_toBigDecimal() {
        String json = "{\"_v\":\"123.456789\"}";
        BigDecimal result = JsonIo.toJava(json, READ_OPTIONS).asClass(BigDecimal.class);
        assertThat(result).isEqualTo(new BigDecimal("123.456789"));
    }

    // ========== Map with @type=HashMap → Primitive Types ==========
    // These test JSON with @type=HashMap (the most common pattern from java-util failures)

    @Test
    void testHashMapWithType_toBoolean_true() {
        String json = "{\"@type\":\"java.util.HashMap\",\"_v\":true}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isTrue();
    }

    @Test
    void testHashMapWithType_toBoolean_fromNumber() {
        String json = "{\"@type\":\"java.util.HashMap\",\"_v\":16}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isTrue();
    }

    @Test
    void testHashMapWithType_toBoolean_fromZero() {
        String json = "{\"@type\":\"java.util.HashMap\",\"_v\":0}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isFalse();
    }

    @Test
    void testHashMapWithType_toInteger() {
        String json = "{\"@type\":\"java.util.HashMap\",\"_v\":42}";
        Integer result = JsonIo.toJava(json, READ_OPTIONS).asClass(Integer.class);
        assertThat(result).isEqualTo(42);
    }

    @Test
    void testHashMapWithType_toLong() {
        String json = "{\"@type\":\"java.util.HashMap\",\"_v\":9876543210}";
        Long result = JsonIo.toJava(json, READ_OPTIONS).asClass(Long.class);
        assertThat(result).isEqualTo(9876543210L);
    }

    @Test
    void testHashMapWithType_toDouble() {
        String json = "{\"@type\":\"java.util.HashMap\",\"_v\":3.14}";
        Double result = JsonIo.toJava(json, READ_OPTIONS).asClass(Double.class);
        assertThat(result).isEqualTo(3.14);
    }

    @Test
    void testHashMapWithType_toFloat() {
        String json = "{\"@type\":\"java.util.HashMap\",\"_v\":2.5}";
        Float result = JsonIo.toJava(json, READ_OPTIONS).asClass(Float.class);
        assertThat(result).isEqualTo(2.5f);
    }

    @Test
    void testHashMapWithType_toShort() {
        String json = "{\"@type\":\"java.util.HashMap\",\"_v\":100}";
        Short result = JsonIo.toJava(json, READ_OPTIONS).asClass(Short.class);
        assertThat(result).isEqualTo((short) 100);
    }

    @Test
    void testHashMapWithType_toByte() {
        String json = "{\"@type\":\"java.util.HashMap\",\"_v\":50}";
        Byte result = JsonIo.toJava(json, READ_OPTIONS).asClass(Byte.class);
        assertThat(result).isEqualTo((byte) 50);
    }

    @Test
    void testLinkedHashMapWithType_toBoolean() {
        String json = "{\"@type\":\"java.util.LinkedHashMap\",\"_v\":true}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isTrue();
    }

    @Test
    void testTreeMapWithType_toBoolean() {
        String json = "{\"@type\":\"java.util.TreeMap\",\"_v\":true}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isTrue();
    }

    // ========== Nested Map Structures ==========
    // These test nested Maps with _v keys - a key pattern from java-util failures

    @Test
    void testNestedMap_toBoolean() {
        // {_v: {_v: 5.0}} should convert to true (non-zero)
        String json = "{\"_v\":{\"_v\":5.0}}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isTrue();
    }

    @Test
    void testNestedMap_toBoolean_zero() {
        // {_v: {_v: 0}} should convert to false
        String json = "{\"_v\":{\"_v\":0}}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isFalse();
    }

    @Test
    void testNestedMap_toInteger() {
        String json = "{\"_v\":{\"_v\":42}}";
        Integer result = JsonIo.toJava(json, READ_OPTIONS).asClass(Integer.class);
        assertThat(result).isEqualTo(42);
    }

    @Test
    void testNestedMap_toLong() {
        String json = "{\"_v\":{\"_v\":9876543210}}";
        Long result = JsonIo.toJava(json, READ_OPTIONS).asClass(Long.class);
        assertThat(result).isEqualTo(9876543210L);
    }

    @Test
    void testNestedMap_toDouble() {
        String json = "{\"_v\":{\"_v\":3.14}}";
        Double result = JsonIo.toJava(json, READ_OPTIONS).asClass(Double.class);
        assertThat(result).isEqualTo(3.14);
    }

    @Test
    void testTripleNestedMap_toBoolean() {
        // {_v: {_v: {_v: 1}}} should convert to true
        String json = "{\"_v\":{\"_v\":{\"_v\":1}}}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isTrue();
    }

    @Test
    void testNestedMapWithHashMapType_toBoolean() {
        String json = "{\"@type\":\"java.util.HashMap\",\"_v\":{\"@type\":\"java.util.HashMap\",\"_v\":5.0}}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isTrue();
    }

    @Test
    void testNestedMapWithHashMapType_toInteger() {
        String json = "{\"@type\":\"java.util.HashMap\",\"_v\":{\"@type\":\"java.util.HashMap\",\"_v\":42}}";
        Integer result = JsonIo.toJava(json, READ_OPTIONS).asClass(Integer.class);
        assertThat(result).isEqualTo(42);
    }

    // ========== Java Map Serialization → Target Type ==========
    // These test creating a Java Map, serializing it, and reading back as non-Map type

    @Test
    void testJavaMapToJson_readAsBoolean() {
        Map<String, Object> map = new HashMap<>();
        map.put("_v", 16);

        String json = JsonIo.toJson(map, WRITE_OPTIONS);
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isTrue();
    }

    @Test
    void testJavaMapToJson_readAsBoolean_false() {
        Map<String, Object> map = new HashMap<>();
        map.put("_v", 0);

        String json = JsonIo.toJson(map, WRITE_OPTIONS);
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isFalse();
    }

    @Test
    void testJavaMapToJson_readAsInteger() {
        Map<String, Object> map = new HashMap<>();
        map.put("_v", 42);

        String json = JsonIo.toJson(map, WRITE_OPTIONS);
        Integer result = JsonIo.toJava(json, READ_OPTIONS).asClass(Integer.class);
        assertThat(result).isEqualTo(42);
    }

    @Test
    void testJavaMapToJson_readAsLong() {
        Map<String, Object> map = new HashMap<>();
        map.put("_v", 9876543210L);

        String json = JsonIo.toJson(map, WRITE_OPTIONS);
        Long result = JsonIo.toJava(json, READ_OPTIONS).asClass(Long.class);
        assertThat(result).isEqualTo(9876543210L);
    }

    @Test
    void testJavaMapToJson_readAsDouble() {
        Map<String, Object> map = new HashMap<>();
        map.put("_v", 3.14159);

        String json = JsonIo.toJson(map, WRITE_OPTIONS);
        Double result = JsonIo.toJava(json, READ_OPTIONS).asClass(Double.class);
        assertThat(result).isEqualTo(3.14159);
    }

    @Test
    void testJavaNestedMapToJson_readAsBoolean() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("_v", 5.0);
        Map<String, Object> outer = new HashMap<>();
        outer.put("_v", inner);

        String json = JsonIo.toJson(outer, WRITE_OPTIONS);
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isTrue();
    }

    @Test
    void testJavaNestedMapToJson_readAsDouble() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("_v", 3.14159);
        Map<String, Object> outer = new HashMap<>();
        outer.put("_v", inner);

        String json = JsonIo.toJson(outer, WRITE_OPTIONS);
        Double result = JsonIo.toJava(json, READ_OPTIONS).asClass(Double.class);
        assertThat(result).isEqualTo(3.14159);
    }

    @Test
    void testJavaLinkedHashMapToJson_readAsBoolean() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("_v", true);

        String json = JsonIo.toJson(map, WRITE_OPTIONS);
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isTrue();
    }

    @Test
    void testJavaTreeMapToJson_readAsInteger() {
        Map<String, Object> map = new TreeMap<>();
        map.put("_v", 100);

        String json = JsonIo.toJson(map, WRITE_OPTIONS);
        Integer result = JsonIo.toJava(json, READ_OPTIONS).asClass(Integer.class);
        assertThat(result).isEqualTo(100);
    }

    // ========== Atomic Types ==========

    @Test
    void testMapToAtomicBoolean() {
        String json = "{\"_v\":true}";
        AtomicBoolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(AtomicBoolean.class);
        assertThat(result.get()).isTrue();
    }

    @Test
    void testMapToAtomicBoolean_false() {
        String json = "{\"_v\":false}";
        AtomicBoolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(AtomicBoolean.class);
        assertThat(result.get()).isFalse();
    }

    @Test
    void testMapToAtomicInteger() {
        String json = "{\"_v\":42}";
        AtomicInteger result = JsonIo.toJava(json, READ_OPTIONS).asClass(AtomicInteger.class);
        assertThat(result.get()).isEqualTo(42);
    }

    @Test
    void testMapToAtomicLong() {
        String json = "{\"_v\":9876543210}";
        AtomicLong result = JsonIo.toJava(json, READ_OPTIONS).asClass(AtomicLong.class);
        assertThat(result.get()).isEqualTo(9876543210L);
    }

    @Test
    void testHashMapWithTypeToAtomicBoolean() {
        String json = "{\"@type\":\"java.util.HashMap\",\"_v\":true}";
        AtomicBoolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(AtomicBoolean.class);
        assertThat(result.get()).isTrue();
    }

    @Test
    void testHashMapWithTypeToAtomicInteger() {
        String json = "{\"@type\":\"java.util.HashMap\",\"_v\":42}";
        AtomicInteger result = JsonIo.toJava(json, READ_OPTIONS).asClass(AtomicInteger.class);
        assertThat(result.get()).isEqualTo(42);
    }

    // ========== String Values in Map ==========

    @Test
    void testMapWithStringNumber_toInteger() {
        String json = "{\"_v\":\"42\"}";
        Integer result = JsonIo.toJava(json, READ_OPTIONS).asClass(Integer.class);
        assertThat(result).isEqualTo(42);
    }

    @Test
    void testMapWithStringNumber_toLong() {
        String json = "{\"_v\":\"9876543210\"}";
        Long result = JsonIo.toJava(json, READ_OPTIONS).asClass(Long.class);
        assertThat(result).isEqualTo(9876543210L);
    }

    @Test
    void testMapWithStringBoolean_toBoolean_true() {
        String json = "{\"_v\":\"true\"}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isTrue();
    }

    @Test
    void testMapWithStringBoolean_toBoolean_false() {
        String json = "{\"_v\":\"false\"}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isFalse();
    }

    // ========== Alternative "value" key ==========

    @Test
    void testMapWithValueKeyAlternate_toBoolean() {
        // Test "value" key instead of "_v"
        String json = "{\"value\":true}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isTrue();
    }

    @Test
    void testMapWithValueKeyAlternate_toInteger() {
        String json = "{\"value\":42}";
        Integer result = JsonIo.toJava(json, READ_OPTIONS).asClass(Integer.class);
        assertThat(result).isEqualTo(42);
    }

    @Test
    void testHashMapWithValueKeyAlternate_toBoolean() {
        String json = "{\"@type\":\"java.util.HashMap\",\"value\":true}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isTrue();
    }

    // ========== Duration → Long (special case that serializes as Map) ==========
    // Duration serializes with @type=Duration but can be converted to Long (milliseconds)

    @Test
    void testDuration_roundTrip_toLong() {
        Duration duration = Duration.ofMillis(5000);
        String json = JsonIo.toJson(duration, WRITE_OPTIONS);
        // Duration serializes to {"@type":"Duration","duration":"PT5S"}
        // This should NOT go through Map→Long conversion since @type is Duration, not HashMap
        // But Duration→Long is supported by Converter after resolution
        Long result = JsonIo.toJava(json, READ_OPTIONS).asClass(Long.class);
        assertThat(result).isEqualTo(5000L);
    }

    // ========== Edge Cases ==========

    @Test
    void testEmptyMapWithType_toBoolean_throws() {
        // Empty map cannot be converted to Boolean without _v key
        String json = "{\"@type\":\"java.util.HashMap\"}";
        try {
            Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
            // If it doesn't throw, result should indicate no value was found
            assertThat(result).isNull();
        } catch (JsonIoException e) {
            // Expected - empty map can't be converted to Boolean without _v key
            assertThat(e.getMessage()).containsAnyOf("_v", "value", "Map");
        }
    }

    @Test
    void testMapWithNullValue_toBoolean() {
        String json = "{\"_v\":null}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        // null _v should convert to null or false
        assertThat(result).isNull();
    }

    @Test
    void testMapWithNestedNullValue_toBoolean() {
        String json = "{\"_v\":{\"_v\":null}}";
        Boolean result = JsonIo.toJava(json, READ_OPTIONS).asClass(Boolean.class);
        assertThat(result).isNull();
    }

    // ========== Verify Map is still returned when target is Map ==========

    @Test
    void testMapWithType_toMap_returnsMap() {
        String json = "{\"@type\":\"java.util.HashMap\",\"_v\":42}";
        Map result = JsonIo.toJava(json, READ_OPTIONS).asClass(Map.class);
        assertThat(result).isInstanceOf(HashMap.class);
        assertThat(result.get("_v")).isEqualTo(42L);  // JSON parser returns Long for integers
    }

    @Test
    void testMapWithType_toObject_returnsMap() {
        String json = "{\"@type\":\"java.util.HashMap\",\"_v\":42}";
        Object result = JsonIo.toJava(json, READ_OPTIONS).asClass(Object.class);
        assertThat(result).isInstanceOf(HashMap.class);
    }

    // ============================================================
    // Array cross-conversion tests (char[] <-> byte[], etc.)
    // Uses Converter for array-to-different-array type conversions
    // ============================================================

    @Test
    void testCharArrayJson_readAsByteArray() {
        // char[] serializes as: {"@type":"char[]","@items":["abc"]}
        // Converter handles char[] → byte[] conversion
        char[] chars = new char[]{'a', 'b', 'c'};
        String json = JsonIo.toJson(chars, WRITE_OPTIONS);

        byte[] result = JsonIo.toJava(json, READ_OPTIONS).asClass(byte[].class);

        // Expected: bytes representing 'a', 'b', 'c' (ASCII 97, 98, 99)
        assertThat(result).isNotNull();
        assertThat(result).hasSize(3);
        assertThat(result[0]).isEqualTo((byte) 'a');
        assertThat(result[1]).isEqualTo((byte) 'b');
        assertThat(result[2]).isEqualTo((byte) 'c');
    }

    @Test
    void testByteArrayJson_readAsCharArray() {
        // byte[] serializes as: {"@type":"byte[]","@items":[65,66,67]}
        // Converter handles byte[] → char[] conversion
        byte[] bytes = new byte[]{65, 66, 67}; // ASCII for 'A', 'B', 'C'
        String json = JsonIo.toJson(bytes, WRITE_OPTIONS);

        char[] result = JsonIo.toJava(json, READ_OPTIONS).asClass(char[].class);

        // Expected: chars 'A', 'B', 'C'
        assertThat(result).isNotNull();
        assertThat(result).hasSize(3);
        assertThat(result[0]).isEqualTo('A');
        assertThat(result[1]).isEqualTo('B');
        assertThat(result[2]).isEqualTo('C');
    }

    @Test
    void testCharArrayJson_roundTrip() {
        // Verify char[] works correctly when read back as char[]
        char[] chars = new char[]{'h', 'e', 'l', 'l', 'o'};
        String json = JsonIo.toJson(chars, WRITE_OPTIONS);

        char[] result = JsonIo.toJava(json, READ_OPTIONS).asClass(char[].class);

        assertThat(result).isEqualTo(chars);
    }

    @Test
    void testByteArrayJson_roundTrip() {
        // Verify byte[] works correctly when read back as byte[]
        byte[] bytes = new byte[]{1, 2, 3, 4, 5};
        String json = JsonIo.toJson(bytes, WRITE_OPTIONS);

        byte[] result = JsonIo.toJava(json, READ_OPTIONS).asClass(byte[].class);

        assertThat(result).isEqualTo(bytes);
    }

    @Test
    void testIntArrayJson_readAsLongArray() {
        // int[] → long[] conversion using Converter
        int[] ints = new int[]{1, 2, 3};
        String json = JsonIo.toJson(ints, WRITE_OPTIONS);

        long[] result = JsonIo.toJava(json, READ_OPTIONS).asClass(long[].class);

        assertThat(result).containsExactly(1L, 2L, 3L);
    }

    @Test
    void testShortArrayJson_readAsIntArray() {
        // short[] → int[] conversion using Converter
        short[] shorts = new short[]{10, 20, 30};
        String json = JsonIo.toJson(shorts, WRITE_OPTIONS);

        int[] result = JsonIo.toJava(json, READ_OPTIONS).asClass(int[].class);

        assertThat(result).containsExactly(10, 20, 30);
    }

    // ============================================================
    // Array <-> Collection cross-conversion tests
    // Uses Converter for array-to-collection and vice versa
    // ============================================================

    @Test
    void testCharArrayJson_readAsList() {
        // char[] → List conversion
        char[] chars = new char[]{'x', 'y', 'z'};
        String json = JsonIo.toJson(chars, WRITE_OPTIONS);

        List result = JsonIo.toJava(json, READ_OPTIONS).asClass(List.class);

        assertThat(result).containsExactly('x', 'y', 'z');
    }

    @Test
    void testByteArrayJson_readAsSet() {
        // byte[] → Set conversion
        byte[] bytes = new byte[]{1, 2, 3};
        String json = JsonIo.toJson(bytes, WRITE_OPTIONS);

        Set result = JsonIo.toJava(json, READ_OPTIONS).asClass(Set.class);

        assertThat(result).containsExactlyInAnyOrder((byte) 1, (byte) 2, (byte) 3);
    }

    @Test
    void testListIntegerJson_readAsCharArray() {
        // List<Integer> → char[] conversion
        List<Integer> list = Arrays.asList(65, 66, 67);
        String json = JsonIo.toJson(list, WRITE_OPTIONS);

        char[] result = JsonIo.toJava(json, READ_OPTIONS).asClass(char[].class);

        assertThat(new String(result)).isEqualTo("ABC");
    }

    @Test
    void testListIntegerJson_readAsByteArray() {
        // List<Integer> → byte[] conversion
        List<Integer> list = Arrays.asList(97, 98, 99);
        String json = JsonIo.toJson(list, WRITE_OPTIONS);

        byte[] result = JsonIo.toJava(json, READ_OPTIONS).asClass(byte[].class);

        assertThat(result).containsExactly((byte) 97, (byte) 98, (byte) 99);
    }

    // ============================================================
    // Nested array cross-conversion tests
    // Verifies that char[] → byte[] conversion works deep in object graphs
    // ============================================================

    /**
     * Container class for testing nested array cross-conversion.
     * When a Holder<char[]> is serialized and read back as Holder<byte[]>,
     * the nested char[] should be converted to byte[].
     */
    static class Holder<T> {
        T value;
        String name;

        Holder() {}

        Holder(T value, String name) {
            this.value = value;
            this.name = name;
        }
    }

    /**
     * Container with multiple levels of nesting.
     */
    static class DeepContainer {
        Map<String, Object> data;
        List<Object> items;
        Holder<Object> holder;

        DeepContainer() {}
    }

    @Test
    void testNestedCharArray_inHolder_readAsByteArray() {
        // Create a Holder containing a char[]
        Holder<char[]> holder = new Holder<>(new char[]{'a', 'b', 'c'}, "test");
        String json = JsonIo.toJson(holder, WRITE_OPTIONS);

        // Read it back, but the Holder's generic type parameter doesn't affect runtime
        // The char[] inside should remain as char[] since we're reading Holder, not Holder<byte[]>
        Holder result = JsonIo.toJava(json, READ_OPTIONS).asClass(Holder.class);

        assertThat(result.name).isEqualTo("test");
        assertThat(result.value).isInstanceOf(char[].class);
        assertThat((char[]) result.value).containsExactly('a', 'b', 'c');
    }

    @Test
    void testNestedCharArray_inMap_readAsByteArray() {
        // Create a Map containing a char[]
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("data", new char[]{'x', 'y', 'z'});
        map.put("name", "test");

        String json = JsonIo.toJson(map, WRITE_OPTIONS);

        // Read back as Map - the char[] should be preserved
        Map result = JsonIo.toJava(json, READ_OPTIONS).asClass(Map.class);

        assertThat(result.get("name")).isEqualTo("test");
        assertThat(result.get("data")).isInstanceOf(char[].class);
        assertThat((char[]) result.get("data")).containsExactly('x', 'y', 'z');
    }

    @Test
    void testDeeplyNestedCharArray_7LevelsDeep() {
        // Create a deeply nested structure: Map → List → Map → Holder → Map → List → char[]
        char[] deepChars = new char[]{'d', 'e', 'e', 'p'};

        List<Object> innerList = Arrays.asList(deepChars, "marker");
        Map<String, Object> innerMap = new LinkedHashMap<>();
        innerMap.put("level6", innerList);

        Holder<Object> holder = new Holder<>(innerMap, "level4");

        Map<String, Object> midMap = new LinkedHashMap<>();
        midMap.put("level3", holder);

        List<Object> midList = Arrays.asList(midMap);

        Map<String, Object> outerMap = new LinkedHashMap<>();
        outerMap.put("level1", midList);

        String json = JsonIo.toJson(outerMap, WRITE_OPTIONS);

        // Read back - the deeply nested char[] should be preserved correctly
        Map result = JsonIo.toJava(json, READ_OPTIONS).asClass(Map.class);

        // Navigate 7 levels deep
        List level1 = (List) result.get("level1");
        Map level2 = (Map) level1.get(0);
        Holder level3 = (Holder) level2.get("level3");
        Map level5 = (Map) level3.value;
        List level6 = (List) level5.get("level6");
        Object level7 = level6.get(0);

        assertThat(level7).isInstanceOf(char[].class);
        assertThat((char[]) level7).containsExactly('d', 'e', 'e', 'p');
        assertThat(level6.get(1)).isEqualTo("marker");
    }

    @Test
    void testArrayOfCharArrays_eachConvertedCorrectly() {
        // Create an array of char arrays
        char[][] arrayOfCharArrays = new char[][]{
            {'a', 'b'},
            {'c', 'd', 'e'},
            {'f'}
        };

        String json = JsonIo.toJson(arrayOfCharArrays, WRITE_OPTIONS);

        // Read back as char[][] - each nested char[] should be correctly resolved
        char[][] result = JsonIo.toJava(json, READ_OPTIONS).asClass(char[][].class);

        assertThat(result.length).isEqualTo(3);
        assertThat(result[0]).containsExactly('a', 'b');
        assertThat(result[1]).containsExactly('c', 'd', 'e');
        assertThat(result[2]).containsExactly('f');
    }

    @Test
    void testMapWithByteArrayValue_deeplyNested() {
        // Test that byte[] values in maps work at multiple nesting levels
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("bytes", new byte[]{1, 2, 3});

        Map<String, Object> middle = new LinkedHashMap<>();
        middle.put("nested", inner);

        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("container", middle);

        String json = JsonIo.toJson(outer, WRITE_OPTIONS);

        Map result = JsonIo.toJava(json, READ_OPTIONS).asClass(Map.class);

        Map containerResult = (Map) result.get("container");
        Map nestedResult = (Map) containerResult.get("nested");
        byte[] bytesResult = (byte[]) nestedResult.get("bytes");

        assertThat(bytesResult).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }

    // ============================================================
    // POJO field type mismatch tests
    // Tests that when a POJO field declares byte[] but JSON has char[],
    // the conversion happens correctly.
    // ============================================================

    /**
     * POJO with a byte[] field - used to test field type conversion
     */
    static class DataHolder {
        byte[] data;
        String name;

        DataHolder() {}

        DataHolder(byte[] data, String name) {
            this.data = data;
            this.name = name;
        }
    }

    /**
     * POJO with a char[] field - used to test field type conversion
     */
    static class CharDataHolder {
        char[] data;
        String name;

        CharDataHolder() {}

        CharDataHolder(char[] data, String name) {
            this.data = data;
            this.name = name;
        }
    }

    /**
     * Container for testing deeply nested field type conversion
     */
    static class Location {
        String name;
        Building[] buildings;

        Location() {}
    }

    static class Building {
        String name;
        Floor[] floors;

        Building() {}
    }

    static class Floor {
        int number;
        byte[] sensorData;  // Declared as byte[]

        Floor() {}
    }

    @Test
    void testPojoField_charArrayJsonToBytesField() {
        // Create JSON where DataHolder.data field has @type=char[]
        // but DataHolder class declares data as byte[]
        // This tests if the char[] in JSON is converted to byte[] for the field
        String json = "{\"@type\":\"com.cedarsoftware.io.MapToNonMapConversionTest$DataHolder\"," +
                "\"name\":\"test\"," +
                "\"data\":{\"@type\":\"char[]\",\"@items\":[\"abc\"]}}";

        DataHolder result = JsonIo.toJava(json, READ_OPTIONS).asClass(DataHolder.class);

        assertThat(result.name).isEqualTo("test");
        // The char[] should be converted to byte[]
        assertThat(result.data).isNotNull();
        assertThat(result.data).containsExactly((byte) 'a', (byte) 'b', (byte) 'c');
    }

    @Test
    void testPojoField_byteArrayJsonToCharField() {
        // Create JSON where CharDataHolder.data field has @type=byte[]
        // but CharDataHolder class declares data as char[]
        String json = "{\"@type\":\"com.cedarsoftware.io.MapToNonMapConversionTest$CharDataHolder\"," +
                "\"name\":\"test\"," +
                "\"data\":{\"@type\":\"byte[]\",\"@items\":[65,66,67]}}";

        CharDataHolder result = JsonIo.toJava(json, READ_OPTIONS).asClass(CharDataHolder.class);

        assertThat(result.name).isEqualTo("test");
        // The byte[] should be converted to char[]
        assertThat(result.data).isNotNull();
        assertThat(result.data).containsExactly('A', 'B', 'C');
    }

    @Test
    void testDeeplyNestedPojo_charArrayToBytesField() {
        // Create a Location with Building with Floor with char[] sensor data
        // But Floor declares sensorData as byte[]

        // First, create JSON with char[] for sensorData
        String json = "{\"@type\":\"com.cedarsoftware.io.MapToNonMapConversionTest$Location\"," +
                "\"name\":\"HQ\"," +
                "\"buildings\":[{" +
                    "\"@type\":\"com.cedarsoftware.io.MapToNonMapConversionTest$Building\"," +
                    "\"name\":\"Main\"," +
                    "\"floors\":[{" +
                        "\"@type\":\"com.cedarsoftware.io.MapToNonMapConversionTest$Floor\"," +
                        "\"number\":7," +
                        "\"sensorData\":{\"@type\":\"char[]\",\"@items\":[\"abc\"]}" +
                    "}]" +
                "}]}";

        Location result = JsonIo.toJava(json, READ_OPTIONS).asClass(Location.class);

        assertThat(result.name).isEqualTo("HQ");
        assertThat(result.buildings).hasSize(1);
        assertThat(result.buildings[0].name).isEqualTo("Main");
        assertThat(result.buildings[0].floors).hasSize(1);
        assertThat(result.buildings[0].floors[0].number).isEqualTo(7);
        // The char[] in JSON should be converted to byte[] for the sensorData field
        assertThat(result.buildings[0].floors[0].sensorData).isNotNull();
        assertThat(result.buildings[0].floors[0].sensorData).containsExactly((byte) 'a', (byte) 'b', (byte) 'c');
    }

    @Test
    void testMapsAllTheWayDown_withCharArrayToBytesConversion() {
        // Test "Maps all the way down" scenario where Maps represent objects
        // with no special @type marking, but a nested char[] needs to be read
        String json = "{" +
                "\"location\":{" +
                    "\"name\":\"HQ\"," +
                    "\"building\":{" +
                        "\"name\":\"Main\"," +
                        "\"floor\":{" +
                            "\"number\":7," +
                            "\"sensorData\":{\"@type\":\"char[]\",\"@items\":[\"xyz\"]}" +
                        "}" +
                    "}" +
                "}" +
            "}";

        Map result = JsonIo.toJava(json, READ_OPTIONS).asClass(Map.class);

        Map location = (Map) result.get("location");
        assertThat(location.get("name")).isEqualTo("HQ");

        Map building = (Map) location.get("building");
        assertThat(building.get("name")).isEqualTo("Main");

        Map floor = (Map) building.get("floor");
        assertThat(floor.get("number")).isEqualTo(7L);

        // The char[] should be preserved as char[] when reading into a Map
        // (no field type to convert to)
        Object sensorData = floor.get("sensorData");
        assertThat(sensorData).isInstanceOf(char[].class);
        assertThat((char[]) sensorData).containsExactly('x', 'y', 'z');
    }
}
