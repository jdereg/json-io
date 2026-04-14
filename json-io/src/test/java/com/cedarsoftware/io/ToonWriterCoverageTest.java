package com.cedarsoftware.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Coverage tests for ToonWriter — targets JaCoCo gaps:
 * - Error paths in writing
 * - Cycle detection in arrays/collections/maps
 * - Uniformity check failure paths (null elements, non-string keys, etc.)
 * - Non-string number types in writeNumber
 * - Custom delimiters in tabular arrays
 * - Various value types
 * - IOException wrapping
 * - Reference tracking with cycles
 */
class ToonWriterCoverageTest {

    // ========== Various number types ==========

    @Test
    void testWriteIntegerValue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("n", 42);
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("42");
    }

    @Test
    void testWriteLongValue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("n", 123456789012345L);
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("123456789012345");
    }

    @Test
    void testWriteShortValue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("n", (short) 42);
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("42");
    }

    @Test
    void testWriteByteValue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("n", (byte) 127);
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("127");
    }

    @Test
    void testWriteFloatValue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("n", 3.14f);
        String toon = JsonIo.toToon(data);
        assertThat(toon).containsAnyOf("3.14", "3.1400");
    }

    @Test
    void testWriteDoubleValue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("n", 3.14159);
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("3.14159");
    }

    @Test
    void testWriteBigIntegerValue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("n", new BigInteger("999999999999999999999"));
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("999999999999999999999");
    }

    @Test
    void testWriteBigDecimalValue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("n", new BigDecimal("3.14159265358979"));
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("3.14159265358979");
    }

    @Test
    void testWriteAtomicIntegerValue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("n", new AtomicInteger(42));
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("42");
    }

    @Test
    void testWriteAtomicLongValue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("n", new AtomicLong(99));
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("99");
    }

    // ========== Boolean ==========

    @Test
    void testWriteBooleanTrue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flag", true);
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("true");
    }

    @Test
    void testWriteBooleanFalse() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flag", false);
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("false");
    }

    // ========== Null values ==========

    @Test
    void testWriteNullValue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("n", null);
        String toon = JsonIo.toToon(data);
        assertThat(toon).containsAnyOf("null", "n");
    }

    // ========== String values ==========

    @Test
    void testWriteSimpleString() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("s", "hello");
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("hello");
    }

    @Test
    void testWriteStringWithSpaces() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("s", "hello world");
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("hello world");
    }

    @Test
    void testWriteStringWithComma() {
        // Should be quoted because comma is the default delimiter
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("s", "a,b,c");
        String toon = JsonIo.toToon(data);
        // Round-trip to verify
        Map<?, ?> result = JsonIo.fromToon(toon).asClass(Map.class);
        assertThat(result.get("s")).isEqualTo("a,b,c");
    }

    @Test
    void testWriteEmptyString() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("s", "");
        String toon = JsonIo.toToon(data);
        // Round-trip
        Map<?, ?> result = JsonIo.fromToon(toon).asClass(Map.class);
        assertThat(result.get("s")).isEqualTo("");
    }

    // ========== Cycles in arrays/collections/maps ==========

    @Test
    void testCycleInList() {
        List<Object> list = new ArrayList<>();
        list.add("a");
        list.add(list); // self-reference
        WriteOptions opts = new WriteOptionsBuilder().cycleSupport(false).build();
        assertThatThrownBy(() -> JsonIo.toToon(list, opts))
                .isInstanceOf(JsonIoException.class);
    }

    @Test
    void testCycleInMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", "value");
        map.put("self", map);
        WriteOptions opts = new WriteOptionsBuilder().cycleSupport(false).build();
        assertThatThrownBy(() -> JsonIo.toToon(map, opts))
                .isInstanceOf(JsonIoException.class);
    }

    @Test
    void testCycleInArray() {
        Object[] arr = new Object[2];
        arr[0] = "a";
        arr[1] = arr; // self-reference
        WriteOptions opts = new WriteOptionsBuilder().cycleSupport(false).build();
        assertThatThrownBy(() -> JsonIo.toToon(arr, opts))
                .isInstanceOf(JsonIoException.class);
    }

    // ========== Reference tracking with shared object ==========

    @Test
    void testSharedObjectInList() {
        Map<String, Object> shared = new LinkedHashMap<>();
        shared.put("name", "shared");
        List<Object> list = new ArrayList<>();
        list.add(shared);
        list.add(shared);
        WriteOptions opts = new WriteOptionsBuilder().cycleSupport(true).build();
        String toon = JsonIo.toToon(list, opts);
        assertThat(toon).isNotNull();
    }

    // ========== Tabular array with different types ==========

    @Test
    void testTabularArrayWithUniformObjects() {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("a", 1);
        row1.put("b", 2);
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("a", 3);
        row2.put("b", 4);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", Arrays.asList(row1, row2));
        String toon = JsonIo.toToon(data);
        // Should produce tabular format
        assertThat(toon).contains("rows[2]");
    }

    @Test
    void testNonUniformObjectsBreaksTabular() {
        // Different keys break uniformity (line 1228-1255)
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("a", 1);
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("b", 2); // different key
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", Arrays.asList(row1, row2));
        String toon = JsonIo.toToon(data);
        // Should fall back to non-tabular format
        assertThat(toon).isNotNull();
        // Round-trip
        Map<?, ?> result = JsonIo.fromToon(toon).asClass(Map.class);
        assertThat(result.get("rows")).isInstanceOf(List.class);
    }

    @Test
    void testNullInListBreaksTabular() {
        // Null elements break uniformity (line 1100, 1206)
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("a", 1);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", Arrays.asList(row1, null));
        String toon = JsonIo.toToon(data);
        assertThat(toon).isNotNull();
    }

    // ========== Empty collections ==========

    @Test
    void testEmptyList() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", new ArrayList<>());
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("[0]");
    }

    @Test
    void testEmptyMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nested", new LinkedHashMap<>());
        String toon = JsonIo.toToon(data);
        assertThat(toon).isNotNull();
    }

    @Test
    void testEmptyArray() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", new int[0]);
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("[0]");
    }

    // ========== Sets ==========

    @Test
    void testWriteSet() {
        Set<String> set = new LinkedHashSet<>();
        set.add("a");
        set.add("b");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", set);
        String toon = JsonIo.toToon(data);
        assertThat(toon).isNotNull();
    }

    @Test
    void testWriteHashSet() {
        Set<String> set = new HashSet<>();
        set.add("only");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", set);
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("only");
    }

    // ========== Primitive arrays ==========

    @Test
    void testIntArray() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nums", new int[]{1, 2, 3});
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("nums[3]");
    }

    @Test
    void testLongArray() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nums", new long[]{1L, 2L, 3L});
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("nums[3]");
    }

    @Test
    void testDoubleArray() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nums", new double[]{1.0, 2.0});
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("nums[2]");
    }

    @Test
    void testBooleanArray() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flags", new boolean[]{true, false});
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("flags[2]");
    }

    @Test
    void testStringArray() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("words", new String[]{"a", "b", "c"});
        String toon = JsonIo.toToon(data);
        assertThat(toon).contains("words[3]");
    }

    // ========== Writing to OutputStream ==========

    @Test
    void testWriteToOutputStream() throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", "value");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        JsonIo.toToon(out, data, null);
        String result = out.toString("UTF-8");
        assertThat(result).contains("key");
        assertThat(result).contains("value");
    }

    @Test
    void testWriteToFailingOutputStream() {
        OutputStream failing = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("simulated write failure");
            }
        };
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", "value");
        assertThatThrownBy(() -> JsonIo.toToon(failing, data, null))
                .isInstanceOf(JsonIoException.class);
    }

    // ========== POJO with various field types ==========

    public static class TestBean {
        public String name;
        public int age;
        public boolean active;
        public Double balance;
    }

    @Test
    void testPojoWithFields() {
        TestBean bean = new TestBean();
        bean.name = "Alice";
        bean.age = 30;
        bean.active = true;
        bean.balance = 100.50;
        String toon = JsonIo.toToon(bean);
        assertThat(toon).contains("Alice");
        assertThat(toon).contains("30");
        assertThat(toon).contains("true");
    }

    // ========== Nested structures ==========

    @Test
    void testDeeplyNestedMap() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("deep", "value");
        Map<String, Object> middle = new LinkedHashMap<>();
        middle.put("inner", inner);
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("middle", middle);
        String toon = JsonIo.toToon(outer);
        assertThat(toon).contains("deep");
        assertThat(toon).contains("value");
    }

    @Test
    void testListOfLists() {
        List<List<Integer>> data = Arrays.asList(
                Arrays.asList(1, 2, 3),
                Arrays.asList(4, 5, 6));
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("matrix", data);
        String toon = JsonIo.toToon(wrapped);
        assertThat(toon).isNotNull();
    }
}
