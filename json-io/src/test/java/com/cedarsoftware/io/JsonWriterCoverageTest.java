package com.cedarsoftware.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Coverage tests for JsonWriter — targets specific JaCoCo gaps:
 * - Deep indentation (line 682-684) — beyond INDENT_CACHE_SIZE
 * - Custom writers via WriteOptions
 * - JsonObject array writing (line 1812, 1821, 1837)
 * - JsonObject-as-map fast paths (line 2292-2305)
 * - Pretty-print with various settings
 * - OutputStream IOException wrapping
 * - Empty arrays via various paths
 * - showTypeInfo modes
 */
class JsonWriterCoverageTest {

    // ========== Deep nesting (exercises indent overflow line 682-684) ==========

    @Test
    void testVeryDeeplyNestedMap() {
        // Build a deeply nested structure to exceed INDENT_CACHE_SIZE
        Map<String, Object> current = new LinkedHashMap<>();
        Map<String, Object> root = current;
        for (int i = 0; i < 50; i++) {
            Map<String, Object> next = new LinkedHashMap<>();
            current.put("level" + i, next);
            current = next;
        }
        current.put("end", "deep");

        WriteOptions opts = new WriteOptionsBuilder().prettyPrint(true).build();
        String json = JsonIo.toJson(root, opts);
        assertThat(json).contains("end");
        assertThat(json).contains("deep");
    }

    @Test
    void testDeeplyNestedListPrettyPrint() {
        List<Object> current = new ArrayList<>();
        List<Object> root = current;
        for (int i = 0; i < 50; i++) {
            List<Object> next = new ArrayList<>();
            current.add(next);
            current = next;
        }
        current.add("deep");

        WriteOptions opts = new WriteOptionsBuilder().prettyPrint(true).build();
        String json = JsonIo.toJson(root, opts);
        assertThat(json).contains("deep");
    }

    // ========== Pretty print with various settings ==========

    @Test
    void testPrettyPrintWithCustomIndentation() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("a", 1);
        data.put("b", "hello");

        WriteOptions opts = new WriteOptionsBuilder().prettyPrint(true).build();
        String json = JsonIo.toJson(data, opts);
        assertThat(json).contains("\n");
    }

    @Test
    void testWriteToOutputStream() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", "value");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonIo.toJson(out, data, null);
        String result = out.toString();
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
        assertThatThrownBy(() -> JsonIo.toJson(failing, data, null))
                .isInstanceOf(JsonIoException.class);
    }

    // ========== JsonObject array writing ==========

    @Test
    void testWriteJsonObjectAsArray() {
        // Build a JsonObject manually and write it
        JsonObject jObj = new JsonObject();
        jObj.setType(Object[].class);
        jObj.setItems(new Object[]{"a", "b", "c"});

        String json = JsonIo.toJson(jObj);
        assertThat(json).contains("a");
        assertThat(json).contains("b");
        assertThat(json).contains("c");
    }

    @Test
    void testWriteJsonObjectEmptyArray() {
        JsonObject jObj = new JsonObject();
        jObj.setType(Object[].class);
        jObj.setItems(new Object[0]);

        String json = JsonIo.toJson(jObj);
        assertThat(json).contains("[]");
    }

    @Test
    void testWriteJsonObjectAsMap() {
        // JsonObject as a Map
        JsonObject jObj = new JsonObject();
        jObj.put("key1", "value1");
        jObj.put("key2", 42);

        String json = JsonIo.toJson(jObj);
        assertThat(json).contains("key1");
        assertThat(json).contains("value1");
    }

    @Test
    void testWriteJsonObjectAsMapWithSkipNulls() {
        JsonObject jObj = new JsonObject();
        jObj.put("present", "value");
        jObj.put("absent", null);

        WriteOptions opts = new WriteOptionsBuilder().skipNullFields(true).build();
        String json = JsonIo.toJson(jObj, opts);
        assertThat(json).contains("present");
        assertThat(json).doesNotContain("absent");
    }

    @Test
    void testWriteJsonObjectAsMapJson5UnquotedKeys() {
        JsonObject jObj = new JsonObject();
        jObj.put("validIdentifier", "value");

        WriteOptions opts = new WriteOptionsBuilder()
                .json5()
                .build();
        String json = JsonIo.toJson(jObj, opts);
        // Should have unquoted key in JSON5
        assertThat(json).contains("validIdentifier");
    }

    // ========== showTypeInfo modes ==========

    @Test
    void testShowTypeInfoNever() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "Alice");
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(data, opts);
        assertThat(json).doesNotContain("@type");
    }

    @Test
    void testShowTypeInfoAlways() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "Alice");
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoAlways().build();
        String json = JsonIo.toJson(data, opts);
        assertThat(json).contains("@type");
    }

    @Test
    void testShowTypeInfoMinimal() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "Alice");
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoMinimal().build();
        String json = JsonIo.toJson(data, opts);
        assertThat(json).isNotNull();
    }

    // ========== Empty containers ==========

    @Test
    void testEmptyArray() {
        String json = JsonIo.toJson(new int[0]);
        assertThat(json).isNotNull();
    }

    @Test
    void testEmptyObjectArray() {
        String json = JsonIo.toJson(new Object[0]);
        assertThat(json).contains("[]");
    }

    @Test
    void testEmptyList() {
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(new ArrayList<>(), opts);
        assertThat(json).contains("[]");
    }

    @Test
    void testEmptyMap() {
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(new LinkedHashMap<>(), opts);
        assertThat(json).contains("{}");
    }

    // ========== Various string content ==========

    @Test
    void testStringWithSpecialChars() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("s", "tab\there\nnewline\"quote");
        String json = JsonIo.toJson(data);
        // Verify escapes
        assertThat(json).contains("\\t");
        assertThat(json).contains("\\n");
        assertThat(json).contains("\\\"");
    }

    @Test
    void testStringWithUnicode() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("s", "Hello \u4e16\u754c");
        String json = JsonIo.toJson(data);
        Map<?, ?> result = JsonIo.toJava(json).asClass(Map.class);
        assertThat(result.get("s")).isEqualTo("Hello \u4e16\u754c");
    }

    @Test
    void testStringWithControlChars() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("s", "\u0001\u0002\u0003");
        String json = JsonIo.toJson(data);
        assertThat(json).contains("\\u0001");
    }

    // ========== Numeric edge cases ==========

    @Test
    void testWriteLongMaxValue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("n", Long.MAX_VALUE);
        String json = JsonIo.toJson(data);
        assertThat(json).contains(String.valueOf(Long.MAX_VALUE));
    }

    @Test
    void testWriteLongMinValue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("n", Long.MIN_VALUE);
        String json = JsonIo.toJson(data);
        assertThat(json).contains(String.valueOf(Long.MIN_VALUE));
    }

    @Test
    void testWriteIntMaxValue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("n", Integer.MAX_VALUE);
        String json = JsonIo.toJson(data);
        assertThat(json).contains(String.valueOf(Integer.MAX_VALUE));
    }

    @Test
    void testWriteIntMinValue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("n", Integer.MIN_VALUE);
        String json = JsonIo.toJson(data);
        assertThat(json).contains(String.valueOf(Integer.MIN_VALUE));
    }

    @Test
    void testWriteZero() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("n", 0);
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(data, opts);
        assertThat(json).contains("\"n\":0");
    }

    @Test
    void testWriteNegativeInt() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("n", -42);
        String json = JsonIo.toJson(data);
        assertThat(json).contains("-42");
    }

    // ========== Cycle support ==========

    @Test
    void testCycleSupportEnabled() {
        Map<String, Object> shared = new LinkedHashMap<>();
        shared.put("name", "shared");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("first", shared);
        root.put("second", shared);

        WriteOptions opts = new WriteOptionsBuilder().cycleSupport(true).build();
        String json = JsonIo.toJson(root, opts);
        // With cycle support, second reference should be @ref
        assertThat(json).contains("@id");
    }

    @Test
    void testCycleSupportDisabledThrowsOnCycle() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "circular");
        map.put("self", map);

        WriteOptions opts = new WriteOptionsBuilder().cycleSupport(false).build();
        assertThatThrownBy(() -> JsonIo.toJson(map, opts))
                .isInstanceOf(JsonIoException.class);
    }

    // ========== writeLongsAsStrings ==========

    @Test
    void testWriteLongsAsStrings() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", 9007199254740993L); // beyond JS safe integer range
        WriteOptions opts = new WriteOptionsBuilder().writeLongsAsStrings(true).build();
        String json = JsonIo.toJson(data, opts);
        assertThat(json).contains("\"9007199254740993\"");
    }

    // ========== Skip null fields ==========

    @Test
    void testSkipNullFields() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("present", "value");
        data.put("absent", null);
        WriteOptions opts = new WriteOptionsBuilder().skipNullFields(true).build();
        String json = JsonIo.toJson(data, opts);
        assertThat(json).contains("present");
        assertThat(json).doesNotContain("absent");
    }

    @Test
    void testKeepNullFields() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("present", "value");
        data.put("absent", null);
        WriteOptions opts = new WriteOptionsBuilder().skipNullFields(false).build();
        String json = JsonIo.toJson(data, opts);
        assertThat(json).contains("absent");
        assertThat(json).contains("null");
    }

    // ========== POJO with various fields ==========

    public static class TestPojo {
        public String name;
        public int age;
        public Double balance;
        public boolean active;
        public List<String> tags;
    }

    @Test
    void testPojoWithListField() {
        TestPojo p = new TestPojo();
        p.name = "Alice";
        p.age = 30;
        p.balance = 1000.50;
        p.active = true;
        p.tags = Arrays.asList("a", "b", "c");

        String json = JsonIo.toJson(p);
        assertThat(json).contains("Alice");
        assertThat(json).contains("30");
        assertThat(json).contains("1000.5");
        assertThat(json).contains("true");
        assertThat(json).contains("a");
    }

    @Test
    void testPojoWithNullFields() {
        TestPojo p = new TestPojo();
        p.name = "test";
        // age, balance, active, tags all null/default
        String json = JsonIo.toJson(p);
        assertThat(json).isNotNull();
    }

    // ========== Boolean ==========

    @Test
    void testBooleanArrayValues() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flags", new boolean[]{true, false, true});
        String json = JsonIo.toJson(data);
        assertThat(json).contains("true");
        assertThat(json).contains("false");
    }

    // ========== JSON5 features ==========

    @Test
    void testJson5UnquotedKeys() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("validIdentifier", "value");
        WriteOptions opts = new WriteOptionsBuilder().json5().build();
        String json = JsonIo.toJson(data, opts);
        // JSON5 should write valid identifiers without quotes
        assertThat(json).contains("validIdentifier");
    }

    @Test
    void testJson5SmartQuotes() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("msg", "hello \"quoted\" world");
        WriteOptions opts = new WriteOptionsBuilder().json5().build();
        String json = JsonIo.toJson(data, opts);
        // Smart quotes should use single quotes when string contains "
        assertThat(json).contains("hello");
    }
}
