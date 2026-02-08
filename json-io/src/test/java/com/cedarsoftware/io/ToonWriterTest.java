package com.cedarsoftware.io;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ToonWriter - TOON (Token-Oriented Object Notation) output.
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
class ToonWriterTest {

    // ==================== Primitive Tests ====================

    @Test
    void testNull() {
        String toon = JsonIo.toToon(null, null);
        assertEquals("null", toon);
    }

    @Test
    void testBoolean() {
        assertEquals("true", JsonIo.toToon(true, null));
        assertEquals("false", JsonIo.toToon(false, null));
    }

    @Test
    void testNumbers() {
        assertEquals("42", JsonIo.toToon(42, null));
        assertEquals("42", JsonIo.toToon(42L, null));
        assertEquals("3.14", JsonIo.toToon(3.14, null));
        assertEquals("-999", JsonIo.toToon(-999, null));
    }

    @Test
    void testNanAndInfinity() {
        // TOON spec: NaN and Infinity should be converted to null
        assertEquals("null", JsonIo.toToon(Double.NaN, null));
        assertEquals("null", JsonIo.toToon(Double.POSITIVE_INFINITY, null));
        assertEquals("null", JsonIo.toToon(Double.NEGATIVE_INFINITY, null));
        assertEquals("null", JsonIo.toToon(Float.NaN, null));
    }

    @Test
    void testNegativeZero() {
        // TOON spec: -0 should be normalized to 0
        assertEquals("0", JsonIo.toToon(-0.0, null));
        assertEquals("0", JsonIo.toToon(-0.0f, null));
    }

    // ==================== String Tests ====================

    @Test
    void testSimpleString() {
        // Simple strings without special chars don't need quoting
        assertEquals("hello", JsonIo.toToon("hello", null));
        assertEquals("world", JsonIo.toToon("world", null));
    }

    @Test
    void testStringNeedingQuotes_Empty() {
        // Empty strings must be quoted
        assertEquals("\"\"", JsonIo.toToon("", null));
    }

    @Test
    void testStringNeedingQuotes_ReservedWords() {
        // Reserved words must be quoted
        assertEquals("\"true\"", JsonIo.toToon("true", null));
        assertEquals("\"false\"", JsonIo.toToon("false", null));
        assertEquals("\"null\"", JsonIo.toToon("null", null));
    }

    @Test
    void testStringNeedingQuotes_Numbers() {
        // Strings that look like numbers must be quoted
        assertEquals("\"42\"", JsonIo.toToon("42", null));
        assertEquals("\"3.14\"", JsonIo.toToon("3.14", null));
        assertEquals("\"-5\"", JsonIo.toToon("-5", null));
        assertEquals("\"1e10\"", JsonIo.toToon("1e10", null));
    }

    @Test
    void testStringNeedingQuotes_SpecialChars() {
        // Strings with special characters must be quoted
        assertTrue(JsonIo.toToon("hello:world", null).startsWith("\""));
        assertTrue(JsonIo.toToon("test[1]", null).startsWith("\""));
        assertTrue(JsonIo.toToon("key{val}", null).startsWith("\""));
    }

    @Test
    void testStringNeedingQuotes_Whitespace() {
        // Leading/trailing whitespace requires quoting
        assertEquals("\" hello\"", JsonIo.toToon(" hello", null));
        assertEquals("\"hello \"", JsonIo.toToon("hello ", null));
    }

    @Test
    void testStringEscaping() {
        // TOON only allows 5 escapes: \\, \", \n, \r, \t
        String withNewline = "line1\nline2";
        String toon = JsonIo.toToon(withNewline, null);
        assertTrue(toon.contains("\\n"));

        String withTab = "col1\tcol2";
        toon = JsonIo.toToon(withTab, null);
        assertTrue(toon.contains("\\t"));

        String withQuote = "say \"hello\"";
        toon = JsonIo.toToon(withQuote, null);
        assertTrue(toon.contains("\\\""));
    }

    // ==================== Array Tests ====================

    @Test
    void testEmptyArray() {
        int[] empty = new int[0];
        String toon = JsonIo.toToon(empty, null);
        assertEquals("[0]:", toon);
    }

    @Test
    void testPrimitiveIntArray() {
        int[] arr = {1, 2, 3};
        String toon = JsonIo.toToon(arr, null);
        assertEquals("[3]: 1,2,3", toon);
    }

    @Test
    void testStringArray() {
        String[] arr = {"foo", "bar", "baz"};
        String toon = JsonIo.toToon(arr, null);
        assertEquals("[3]: foo,bar,baz", toon);
    }

    @Test
    void testMixedNonUniformArray_Level1() {
        // Level 1: Single level of nesting - array with mixed elements including a map
        // Per TOON spec:
        // - Field name combines with array size: items[3]:
        // - First field of map element goes on hyphen line: - a: 1
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("a", 1);

        Object[] items = {1, obj, "text"};

        // Test as standalone array
        String toon = JsonIo.toToon(items, null);
        assertTrue(toon.contains("[3]:"), "Should show array size marker");
        assertTrue(toon.contains("- 1"), "Should have hyphen for integer element");
        assertTrue(toon.contains("- a: 1"), "First field should be on hyphen line");
        assertTrue(toon.contains("- text"), "Should have hyphen for string element");

        // Test wrapped in object - field name combines with array size
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("items", items);
        toon = JsonIo.toToon(wrapper, null);
        assertTrue(toon.contains("items[3]:"), "Field name should combine with array size");
        assertTrue(toon.contains("- 1"), "Should have hyphen for integer element");
        assertTrue(toon.contains("- a: 1"), "First field should be on hyphen line");
        assertTrue(toon.contains("- text"), "Should have hyphen for string element");
    }

    @Test
    void testMixedNonUniformArray_Level2() {
        // Level 2: Two levels of nesting - map inside array inside object
        // Expected format:
        // outer:
        //   inner[2]:
        //     - x: 1
        //       y: 2
        //     - name: test
        Map<String, Object> elem1 = new LinkedHashMap<>();
        elem1.put("x", 1);
        elem1.put("y", 2);

        Map<String, Object> elem2 = new LinkedHashMap<>();
        elem2.put("name", "test");

        List<Object> inner = new ArrayList<>();
        inner.add(elem1);
        inner.add(elem2);

        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("inner", inner);

        String toon = JsonIo.toToon(outer, null);

        // Verify structure
        assertTrue(toon.contains("inner[2]:"), "Field name should combine with array size");
        assertTrue(toon.contains("- x: 1"), "First field of first element on hyphen line");
        assertTrue(toon.contains("y: 2"), "Second field should be present");
        assertTrue(toon.contains("- name: test"), "First field of second element on hyphen line");
    }

    @Test
    void testMixedNonUniformArray_Level3() {
        // Level 3: Three levels of nesting - ensures generic handling
        // Structure: root -> items[] -> nested object -> data[] -> uniform objects
        // Expected format (tabular for uniform object arrays per TOON spec):
        // items[1]:
        //   - info:
        //       data[2]{id,value}:
        //         1,a
        //         2,b
        Map<String, Object> dataElem1 = new LinkedHashMap<>();
        dataElem1.put("id", 1);
        dataElem1.put("value", "a");

        Map<String, Object> dataElem2 = new LinkedHashMap<>();
        dataElem2.put("id", 2);
        dataElem2.put("value", "b");

        List<Object> dataArray = new ArrayList<>();
        dataArray.add(dataElem1);
        dataArray.add(dataElem2);

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("data", dataArray);

        Map<String, Object> itemElem = new LinkedHashMap<>();
        itemElem.put("info", nested);

        List<Object> items = new ArrayList<>();
        items.add(itemElem);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", items);

        String toon = JsonIo.toToon(root, null);

        // Verify three-level nesting structure with tabular format for uniform arrays
        assertTrue(toon.contains("items[1]:"), "Root field should combine with array size");
        assertTrue(toon.contains("- info:"), "First field of items element on hyphen line");
        assertTrue(toon.contains("data[2]{id,value}:"), "Uniform array uses tabular format with headers");
        assertTrue(toon.contains("1,a"), "First row of tabular data");
        assertTrue(toon.contains("2,b"), "Second row of tabular data");
    }

    @Test
    void testMixedArrayWithNestedObject() {
        // Test array containing complex object - triggers list format with hyphens
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("x", 10);
        nested.put("y", 20);

        List<Object> mixedList = new ArrayList<>();
        mixedList.add(42);
        mixedList.add(nested);
        mixedList.add("hello");
        mixedList.add(true);

        String toon = JsonIo.toToon(mixedList, null);

        // Verify list format with first field on hyphen line
        assertTrue(toon.contains("[4]:"), "Should show list size marker");
        assertTrue(toon.contains("- 42"), "Should have hyphen for integer");
        assertTrue(toon.contains("- x: 10"), "First field should be on hyphen line");
        assertTrue(toon.contains("y: 20"), "Second field should be present");
        assertTrue(toon.contains("- hello"), "Should have hyphen for string");
        assertTrue(toon.contains("- true"), "Should have hyphen for boolean");
    }

    @Test
    void testUniformArrayUsesCompactFormat() {
        // Arrays with only simple types use compact comma format (even if different types)
        Object[] uniformStrings = {"foo", "bar", "baz"};
        String toon = JsonIo.toToon(uniformStrings, null);
        assertEquals("[3]: foo,bar,baz", toon);

        Object[] uniformIntegers = {1, 2, 3};
        toon = JsonIo.toToon(uniformIntegers, null);
        assertEquals("[3]: 1,2,3", toon);
    }

    @Test
    void testMixedSimpleTypesUseCompactFormat() {
        // Arrays with mixed simple types (no complex objects) still use compact format
        List<Object> mixed = new ArrayList<>();
        mixed.add(100);
        mixed.add("string value");
        mixed.add(false);

        String toon = JsonIo.toToon(mixed, null);
        // All elements are simple types, so uses compact comma format
        assertEquals("[3]: 100,string value,false", toon);
    }

    @Test
    void testDeeplyNestedArrays_Level4() {
        // Level 4: Four levels of nesting to prove the algorithm is generic
        // a[1] -> b[1] -> c[1] -> d[1] -> {value: deep}
        // Uniform single-element arrays use tabular format: d[1]{value}: deep
        Map<String, Object> deepest = new LinkedHashMap<>();
        deepest.put("value", "deep");

        List<Object> level4 = new ArrayList<>();
        level4.add(deepest);

        Map<String, Object> level3Obj = new LinkedHashMap<>();
        level3Obj.put("d", level4);

        List<Object> level3 = new ArrayList<>();
        level3.add(level3Obj);

        Map<String, Object> level2Obj = new LinkedHashMap<>();
        level2Obj.put("c", level3);

        List<Object> level2 = new ArrayList<>();
        level2.add(level2Obj);

        Map<String, Object> level1Obj = new LinkedHashMap<>();
        level1Obj.put("b", level2);

        List<Object> level1 = new ArrayList<>();
        level1.add(level1Obj);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("a", level1);

        String toon = JsonIo.toToon(root, null);

        // Verify four-level nesting - each level should have proper format
        // Uniform arrays use tabular format with column headers
        assertTrue(toon.contains("a[1]:"), "Level 1 field+size");
        assertTrue(toon.contains("- b[1]:"), "Level 2 on hyphen line with size");
        assertTrue(toon.contains("- c[1]:"), "Level 3 on hyphen line with size");
        assertTrue(toon.contains("- d[1]{value}:"), "Level 4 uses tabular format");
        assertTrue(toon.contains("deep"), "Deepest value in tabular row");
    }

    // ==================== Collection Tests ====================

    @Test
    void testEmptyList() {
        List<String> empty = new ArrayList<>();
        String toon = JsonIo.toToon(empty, null);
        assertEquals("[0]:", toon);
    }

    @Test
    void testPrimitiveList() {
        List<Integer> list = Arrays.asList(1, 2, 3);
        String toon = JsonIo.toToon(list, null);
        assertEquals("[3]: 1,2,3", toon);
    }

    // ==================== Map/Object Tests ====================

    @Test
    void testEmptyMap() {
        Map<String, Object> empty = new HashMap<>();
        String toon = JsonIo.toToon(empty, null);
        // Empty maps use {} syntax for round-trip support
        assertEquals("{}", toon);
    }

    @Test
    void testSimpleMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "John");
        map.put("age", 30);

        String toon = JsonIo.toToon(map, null);
        assertTrue(toon.contains("name: John"));
        assertTrue(toon.contains("age: 30"));
    }

    @Test
    void testMapWithStringArrayField_RoundTrip() {
        // Test TOON spec example: tags[3]: admin,ops,dev
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tags", new String[]{"admin", "ops", "dev"});

        // Write to TOON
        String toon = JsonIo.toToon(map, null);
        assertEquals("tags[3]: admin,ops,dev", toon);

        // Read back
        Map<String, Object> restored = JsonIo.fromToon(toon, null).asClass(Map.class);
        assertNotNull(restored.get("tags"));
        assertTrue(restored.get("tags") instanceof List);
        List<?> tags = (List<?>) restored.get("tags");
        assertEquals(3, tags.size());
        assertEquals("admin", tags.get(0));
        assertEquals("ops", tags.get(1));
        assertEquals("dev", tags.get(2));

        // Write again and verify round-trip
        String toon2 = JsonIo.toToon(restored, null);
        assertEquals("tags[3]: admin,ops,dev", toon2);
    }

    @Test
    void testNestedTabularArrayInObject_RoundTrip() {
        // Test TOON spec nested tabular format:
        // items[1]:
        //   - users[2]{id,name}:
        //       1,Ada
        //       2,Bob
        //     status: active
        //
        // This tests:
        // - Hyphen list element containing an object
        // - First field (users) is a tabular array on the hyphen line
        // - Tabular rows are indented relative to the field
        // - Second field (status) is at the same level as the field name

        Map<String, Object> user1 = new LinkedHashMap<>();
        user1.put("id", 1);
        user1.put("name", "Ada");

        Map<String, Object> user2 = new LinkedHashMap<>();
        user2.put("id", 2);
        user2.put("name", "Bob");

        List<Map<String, Object>> users = new ArrayList<>();
        users.add(user1);
        users.add(user2);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("users", users);
        item.put("status", "active");

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", items);

        // Write to TOON
        String toon = JsonIo.toToon(root, null);
        assertTrue(toon.contains("items[1]:"), "Should have items array");
        assertTrue(toon.contains("- users[2]{id,name}:"), "First field should be tabular on hyphen line");
        assertTrue(toon.contains("1,Ada"), "First tabular row");
        assertTrue(toon.contains("2,Bob"), "Second tabular row");
        assertTrue(toon.contains("status: active"), "Second field of item");

        // Read back
        Map<String, Object> restored = JsonIo.fromToon(toon, null).asClass(Map.class);
        List<?> restoredItems = (List<?>) restored.get("items");
        assertEquals(1, restoredItems.size());

        Map<?, ?> restoredItem = (Map<?, ?>) restoredItems.get(0);
        assertEquals("active", restoredItem.get("status"));

        List<?> restoredUsers = (List<?>) restoredItem.get("users");
        assertEquals(2, restoredUsers.size());

        Map<?, ?> restoredUser1 = (Map<?, ?>) restoredUsers.get(0);
        assertEquals(1L, restoredUser1.get("id"));
        assertEquals("Ada", restoredUser1.get("name"));

        Map<?, ?> restoredUser2 = (Map<?, ?>) restoredUsers.get(1);
        assertEquals(2L, restoredUser2.get("id"));
        assertEquals("Bob", restoredUser2.get("name"));

        // Write again and verify round-trip
        String toon2 = JsonIo.toToon(restored, null);
        assertEquals(toon, toon2, "Round-trip should produce identical TOON");
    }

    @Test
    void testTabularArrayFormat_RoundTrip() {
        // Test TOON spec tabular format:
        // items[2]{sku,qty,price}:
        //   A1,2,9.99
        //   B2,1,14.5
        //
        // This is a compact CSV-like format for arrays of uniform objects.
        // - items: field name
        // - [2]: array has 2 elements
        // - {sku,qty,price}: column headers (property names)
        // - Each indented line is one object with values in header order

        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("sku", "A1");
        item1.put("qty", 2);
        item1.put("price", 9.99);

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("sku", "B2");
        item2.put("qty", 1);
        item2.put("price", 14.5);

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item1);
        items.add(item2);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", items);

        // Write to TOON - should produce tabular format
        String toon = JsonIo.toToon(root, null);
        assertTrue(toon.contains("items[2]{sku,qty,price}:"), "Should use tabular header format");
        assertTrue(toon.contains("A1,2,9.99"), "First row of tabular data");
        assertTrue(toon.contains("B2,1,14.5"), "Second row of tabular data");

        // Read back
        Map<String, Object> restored = JsonIo.fromToon(toon, null).asClass(Map.class);
        assertNotNull(restored.get("items"));
        List<?> restoredItems = (List<?>) restored.get("items");
        assertEquals(2, restoredItems.size());

        // Verify first item
        Map<?, ?> restoredItem1 = (Map<?, ?>) restoredItems.get(0);
        assertEquals("A1", restoredItem1.get("sku"));
        assertEquals(2L, restoredItem1.get("qty"));
        assertEquals(9.99, restoredItem1.get("price"));

        // Verify second item
        Map<?, ?> restoredItem2 = (Map<?, ?>) restoredItems.get(1);
        assertEquals("B2", restoredItem2.get("sku"));
        assertEquals(1L, restoredItem2.get("qty"));
        assertEquals(14.5, restoredItem2.get("price"));

        // Write again and verify round-trip
        String toon2 = JsonIo.toToon(restored, null);
        assertEquals(toon, toon2, "Round-trip should produce identical TOON");
    }

    @Test
    void testNestedMap() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("city", "NYC");
        address.put("zip", 10001);  // Use integer so it's not quoted

        Map<String, Object> person = new LinkedHashMap<>();
        person.put("name", "John");
        person.put("address", address);

        String toon = JsonIo.toToon(person, null);
        assertTrue(toon.contains("name: John"));
        assertTrue(toon.contains("address:"));
        assertTrue(toon.contains("city: NYC"));
        assertTrue(toon.contains("zip: 10001"));
    }

    // ==================== POJO Tests ====================

    static class Person {
        String name;
        int age;

        Person() {}
        Person(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    @Test
    void testSimplePojo() {
        Person person = new Person("Alice", 25);
        String toon = JsonIo.toToon(person, null);
        assertTrue(toon.contains("name: Alice"));
        assertTrue(toon.contains("age: 25"));
    }

    static class Company {
        String name;
        List<Person> employees;

        Company() {}
    }

    @Test
    void testComplexPojo() {
        Company company = new Company();
        company.name = "Acme";
        company.employees = Arrays.asList(
            new Person("Alice", 25),
            new Person("Bob", 30)
        );

        String toon = JsonIo.toToon(company, null);
        assertTrue(toon.contains("name: Acme"));
        assertTrue(toon.contains("employees"));
    }

    // ==================== Cycle Detection Tests ====================

    static class Node {
        String name;
        Node next;

        Node(String name) {
            this.name = name;
        }
    }

    @Test
    void testCycleDetection_NoInfiniteLoop() {
        Node a = new Node("alpha");
        Node b = new Node("beta");
        a.next = b;
        b.next = a;  // Cycle!

        // Should not hang or infinite loop
        String toon = JsonIo.toToon(a, null);
        assertNotNull(toon);
        assertTrue(toon.contains("alpha"));
        assertTrue(toon.contains("beta"));
    }

    @Test
    void testSelfReference_NoInfiniteLoop() {
        Node self = new Node("self");
        self.next = self;  // Self-cycle!

        // Should not hang or infinite loop
        String toon = JsonIo.toToon(self, null);
        assertNotNull(toon);
        assertTrue(toon.contains("self"));
    }

    @Test
    void testTabularDelimiterVariants_Read() {
        // Test TOON spec: three equivalent tabular format delimiter variants
        // 1. Comma-separated (default): [N]{col1,col2}:
        // 2. Tab-separated: [N\t]{col1\tcol2}:
        // 3. Pipe-separated: [N|]{col1|col2}:

        // Variant 1: Comma (default)
        String toonComma = "items[2]{sku,name,qty,price}:\n" +
                           "  A1,Widget,2,9.99\n" +
                           "  B2,Gadget,1,14.5";

        Map<String, Object> parsedComma = JsonIo.fromToon(toonComma, null).asClass(Map.class);
        verifyTabularData(parsedComma, "comma");

        // Variant 2: Tab-separated
        String toonTab = "items[2\t]{sku\tname\tqty\tprice}:\n" +
                         "  A1\tWidget\t2\t9.99\n" +
                         "  B2\tGadget\t1\t14.5";

        Map<String, Object> parsedTab = JsonIo.fromToon(toonTab, null).asClass(Map.class);
        verifyTabularData(parsedTab, "tab");

        // Variant 3: Pipe-separated
        String toonPipe = "items[2|]{sku|name|qty|price}:\n" +
                          "  A1|Widget|2|9.99\n" +
                          "  B2|Gadget|1|14.5";

        Map<String, Object> parsedPipe = JsonIo.fromToon(toonPipe, null).asClass(Map.class);
        verifyTabularData(parsedPipe, "pipe");
    }

    private void verifyTabularData(Map<String, Object> parsed, String variant) {
        List<?> items = (List<?>) parsed.get("items");
        assertNotNull(items, variant + ": items should not be null");
        assertEquals(2, items.size(), variant + ": should have 2 items");

        Map<?, ?> item0 = (Map<?, ?>) items.get(0);
        assertEquals("A1", item0.get("sku"), variant + ": item[0].sku");
        assertEquals("Widget", item0.get("name"), variant + ": item[0].name");
        assertEquals(2L, item0.get("qty"), variant + ": item[0].qty");
        assertEquals(9.99, item0.get("price"), variant + ": item[0].price");

        Map<?, ?> item1 = (Map<?, ?>) items.get(1);
        assertEquals("B2", item1.get("sku"), variant + ": item[1].sku");
        assertEquals("Gadget", item1.get("name"), variant + ": item[1].name");
        assertEquals(1L, item1.get("qty"), variant + ": item[1].qty");
        assertEquals(14.5, item1.get("price"), variant + ": item[1].price");
    }

    // ========== Key Folding Tests ==========

    @Test
    void testKeyFolding_WriteAndRead() {
        // Create nested single-key structure: {data: {metadata: {value: 42}}}
        Map<String, Object> innerMost = new LinkedHashMap<>();
        innerMost.put("value", 42);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("metadata", innerMost);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("data", metadata);

        // Write WITHOUT key folding (default)
        String toonNormal = JsonIo.toToon(data, null);
        assertTrue(toonNormal.contains("data:"), "Without folding: should have nested 'data:'");
        assertTrue(toonNormal.contains("metadata:"), "Without folding: should have nested 'metadata:'");
        assertFalse(toonNormal.contains("data.metadata"), "Without folding: should NOT have dotted key");

        // Write WITH key folding
        WriteOptions foldingOptions = new WriteOptionsBuilder().toonKeyFolding(true).build();
        String toonFolded = JsonIo.toToon(data, foldingOptions);
        assertTrue(toonFolded.contains("data.metadata.value:"), "With folding: should have folded key");
        assertFalse(toonFolded.contains("\n  metadata:"), "With folding: should NOT have nested structure");

        // Read folded TOON and verify structure
        Map<String, Object> restored = JsonIo.fromToon(toonFolded, null).asClass(Map.class);
        Map<?, ?> restoredData = (Map<?, ?>) restored.get("data");
        assertNotNull(restoredData, "Restored should have 'data' key");
        Map<?, ?> restoredMetadata = (Map<?, ?>) restoredData.get("metadata");
        assertNotNull(restoredMetadata, "Restored should have 'metadata' key");
        assertEquals(42L, restoredMetadata.get("value"), "Restored value should be 42");

        // Verify round-trip: folded -> read -> write without folding -> same nested structure
        String toonUnfolded = JsonIo.toToon(restored, null);
        assertTrue(toonUnfolded.contains("data:"), "Round-trip: should expand to nested 'data:'");
        assertTrue(toonUnfolded.contains("metadata:"), "Round-trip: should expand to nested 'metadata:'");
    }

    @Test
    void testKeyFolding_WithArray() {
        // Create structure: {data: {metadata: {items: [a, b, c]}}}
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("items", Arrays.asList("a", "b", "c"));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("metadata", items);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("data", metadata);

        // Write with key folding
        WriteOptions foldingOptions = new WriteOptionsBuilder().toonKeyFolding(true).build();
        String toonFolded = JsonIo.toToon(data, foldingOptions);
        assertTrue(toonFolded.contains("data.metadata.items[3]:"), "Should fold with array notation");

        // Read and verify
        Map<String, Object> restored = JsonIo.fromToon(toonFolded, null).asClass(Map.class);
        Map<?, ?> restoredData = (Map<?, ?>) restored.get("data");
        Map<?, ?> restoredMetadata = (Map<?, ?>) restoredData.get("metadata");
        List<?> restoredItems = (List<?>) restoredMetadata.get("items");
        assertEquals(3, restoredItems.size());
        assertEquals("a", restoredItems.get(0));
        assertEquals("b", restoredItems.get(1));
        assertEquals("c", restoredItems.get(2));
    }

    @Test
    void testKeyFolding_MultipleKeysNotFolded() {
        // Structure with multiple keys should NOT be folded
        Map<String, Object> multiKey = new LinkedHashMap<>();
        multiKey.put("a", 1);
        multiKey.put("b", 2);

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("data", multiKey);

        WriteOptions foldingOptions = new WriteOptionsBuilder().toonKeyFolding(true).build();
        String toon = JsonIo.toToon(wrapper, foldingOptions);

        // Should NOT fold because inner map has multiple keys
        assertTrue(toon.contains("data:"), "Should have 'data:' (not folded)");
        assertTrue(toon.contains("a: 1"), "Should have 'a: 1'");
        assertTrue(toon.contains("b: 2"), "Should have 'b: 2'");
        assertFalse(toon.contains("data.a"), "Should NOT have 'data.a'");
    }

    @Test
    void testKeyFolding_ReadExpandsDottedKeys() {
        // Test that reader expands dotted keys into nested structure
        String folded = "config.database.host: localhost";
        Map<String, Object> parsed = JsonIo.fromToon(folded, null).asClass(Map.class);

        Map<?, ?> config = (Map<?, ?>) parsed.get("config");
        assertNotNull(config, "Should have 'config' key");
        Map<?, ?> database = (Map<?, ?>) config.get("database");
        assertNotNull(database, "Should have 'database' key");
        assertEquals("localhost", database.get("host"));
    }

    @Test
    void testKeyFolding_QuotedKeysNotExpanded() {
        // Quoted keys with dots should NOT be expanded
        String literal = "\"dotted.key\": value";
        Map<String, Object> parsed = JsonIo.fromToon(literal, null).asClass(Map.class);

        assertTrue(parsed.containsKey("dotted.key"), "Should have literal 'dotted.key'");
        assertEquals("value", parsed.get("dotted.key"));
        assertFalse(parsed.containsKey("dotted"), "Should NOT have 'dotted' key");
    }

    // ========== High Priority Gap Tests: Output Format Compliance ==========

    @Test
    void testNoTrailingSpacesOnOutputLines() {
        // TOON spec: No trailing spaces at the end of any line
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("name", "John");
        nested.put("age", 30);
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("city", "NYC");
        address.put("zip", "10001");
        nested.put("address", address);

        String toon = JsonIo.toToon(nested, null);
        String[] lines = toon.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            assertFalse(lines[i].endsWith(" "),
                    "Line " + (i + 1) + " has trailing space: '" + lines[i] + "'");
        }
    }

    @Test
    void testNoTrailingNewlineAtEndOfDocument() {
        // TOON spec: No trailing newline at the end of the document
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "John");
        map.put("age", 30);

        String toon = JsonIo.toToon(map, null);
        assertFalse(toon.endsWith("\n"), "TOON output should not end with newline");
        assertFalse(toon.endsWith("\r"), "TOON output should not end with carriage return");
    }

    @Test
    void testOutputUsesLfOnly() {
        // TOON spec: UTF-8 with LF line endings (not CRLF)
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("name", "John");
        Map<String, Object> addr = new LinkedHashMap<>();
        addr.put("city", "NYC");
        nested.put("address", addr);

        String toon = JsonIo.toToon(nested, null);
        assertFalse(toon.contains("\r\n"), "TOON output must use LF, not CRLF");
        assertFalse(toon.contains("\r"), "TOON output must not contain carriage returns");
        assertTrue(toon.contains("\n"), "Multi-line output should use LF");
    }

    @Test
    void testNoTrailingSpaces_TabularFormat() {
        // TOON spec: No trailing spaces - specifically in tabular format
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("id", 1);
        item1.put("name", "Alice");

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("id", 2);
        item2.put("name", "Bob");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", Arrays.asList(item1, item2));

        String toon = JsonIo.toToon(root, null);
        String[] lines = toon.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            assertFalse(lines[i].endsWith(" "),
                    "Tabular line " + (i + 1) + " has trailing space: '" + lines[i] + "'");
        }
    }

    @Test
    void testNoTrailingSpaces_EmptyValues() {
        // Check output with null values doesn't produce trailing spaces
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "John");
        map.put("middle", null);
        map.put("age", 30);

        String toon = JsonIo.toToon(map, null);
        String[] lines = toon.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            assertFalse(lines[i].endsWith(" "),
                    "Line " + (i + 1) + " has trailing space: '" + lines[i] + "'");
        }
    }

    @Test
    void testNoTrailingNewline_SinglePrimitive() {
        // Even single-value outputs should not end with newline
        String toon = JsonIo.toToon(42, null);
        assertFalse(toon.endsWith("\n"), "Single primitive should not end with newline");

        toon = JsonIo.toToon("hello", null);
        assertFalse(toon.endsWith("\n"), "Single string should not end with newline");

        toon = JsonIo.toToon(true, null);
        assertFalse(toon.endsWith("\n"), "Single boolean should not end with newline");

        toon = JsonIo.toToon(null, null);
        assertFalse(toon.endsWith("\n"), "Null should not end with newline");
    }

    @Test
    void testNoTrailingNewline_Array() {
        int[] arr = {1, 2, 3};
        String toon = JsonIo.toToon(arr, null);
        assertFalse(toon.endsWith("\n"), "Array output should not end with newline");
    }

    @Test
    void testNoTrailingNewline_ListArray() {
        // List-format arrays (with hyphens) also must not have trailing newline
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("x", 10);

        List<Object> mixedList = new ArrayList<>();
        mixedList.add(42);
        mixedList.add(obj);

        String toon = JsonIo.toToon(mixedList, null);
        assertFalse(toon.endsWith("\n"), "List-format array should not end with newline");
    }

    // ========== High Priority Gap Tests: Float Infinity ==========

    @Test
    void testFloatPositiveInfinity() {
        // TOON spec: Infinity should be converted to null
        assertEquals("null", JsonIo.toToon(Float.POSITIVE_INFINITY, null));
    }

    @Test
    void testFloatNegativeInfinity() {
        // TOON spec: -Infinity should be converted to null
        assertEquals("null", JsonIo.toToon(Float.NEGATIVE_INFINITY, null));
    }

    @Test
    void testFloatNegativeZero() {
        // TOON spec: -0 should be normalized to 0
        assertEquals("0", JsonIo.toToon(-0.0f, null));
    }

    // ========== High Priority Gap Tests: OutputStream API ==========

    @Test
    void testToToonOutputStream_SimpleObject() {
        // Verify OutputStream API produces same output as String API
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "John");
        map.put("age", 30);

        String toonString = JsonIo.toToon(map, null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonIo.toToon(baos, map, null);
        String toonStream = new String(baos.toByteArray(), StandardCharsets.UTF_8);

        assertEquals(toonString, toonStream, "String and OutputStream APIs should produce identical output");
    }

    @Test
    void testToToonOutputStream_NestedObject() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("city", "NYC");
        address.put("zip", "10001");

        Map<String, Object> person = new LinkedHashMap<>();
        person.put("name", "John");
        person.put("address", address);

        String toonString = JsonIo.toToon(person, null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonIo.toToon(baos, person, null);
        String toonStream = new String(baos.toByteArray(), StandardCharsets.UTF_8);

        assertEquals(toonString, toonStream, "Nested object via OutputStream should match String API");
    }

    @Test
    void testToToonOutputStream_Array() {
        int[] arr = {1, 2, 3, 4, 5};

        String toonString = JsonIo.toToon(arr, null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonIo.toToon(baos, arr, null);
        String toonStream = new String(baos.toByteArray(), StandardCharsets.UTF_8);

        assertEquals(toonString, toonStream, "Array via OutputStream should match String API");
    }

    @Test
    void testToToonOutputStream_TabularArray() {
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("sku", "A1");
        item1.put("qty", 2);

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("sku", "B2");
        item2.put("qty", 1);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", Arrays.asList(item1, item2));

        String toonString = JsonIo.toToon(root, null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonIo.toToon(baos, root, null);
        String toonStream = new String(baos.toByteArray(), StandardCharsets.UTF_8);

        assertEquals(toonString, toonStream, "Tabular array via OutputStream should match String API");
    }

    @Test
    void testToToonOutputStream_Null() {
        String toonString = JsonIo.toToon(null, null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonIo.toToon(baos, null, null);
        String toonStream = new String(baos.toByteArray(), StandardCharsets.UTF_8);

        assertEquals(toonString, toonStream, "Null via OutputStream should match String API");
    }

    @Test
    void testToToonOutputStream_RoundTrip() {
        // Write via OutputStream, then read back and verify
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("name", "Alice");
        original.put("scores", Arrays.asList(95, 87, 92));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonIo.toToon(baos, original, null);
        String toon = new String(baos.toByteArray(), StandardCharsets.UTF_8);

        Map<String, Object> restored = JsonIo.fromToon(toon, null).asClass(Map.class);
        assertEquals("Alice", restored.get("name"));
        List<?> scores = (List<?>) restored.get("scores");
        assertEquals(3, scores.size());
        assertEquals(95L, scores.get(0));
        assertEquals(87L, scores.get(1));
        assertEquals(92L, scores.get(2));
    }

    // ========== High Priority Gap Tests: String "-" (Hyphen) ==========

    @Test
    void testStringHyphen_Standalone() {
        // TOON spec: String "-" must be quoted (equals "-" or starts with "-")
        String toon = JsonIo.toToon("-", null);
        assertTrue(toon.startsWith("\""), "Hyphen string should be quoted: " + toon);
        assertTrue(toon.endsWith("\""), "Hyphen string should be quoted: " + toon);
    }

    @Test
    void testStringHyphen_AsFieldValue() {
        // Hyphen as a field value must be quoted to avoid confusion with list marker
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("value", "-");
        String toon = JsonIo.toToon(map, null);
        assertTrue(toon.contains("\"-\""), "Hyphen field value should be quoted: " + toon);

        Map<String, Object> restored = JsonIo.fromToon(toon, null).asClass(Map.class);
        assertEquals("-", restored.get("value"));
    }

    // ========== Medium Priority Gap Tests ==========

    @Test
    void testReservedWordsAsMapKeys() {
        // TOON spec: Keys "true", "false", "null" - should they be quoted?
        // These are valid identifiers per ^[A-Za-z_][A-Za-z0-9_.]*$ but could be
        // confused with boolean/null values during parsing.
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("true", "value1");
        map.put("false", "value2");
        map.put("null", "value3");

        String toon = JsonIo.toToon(map, null);
        Map<String, Object> restored = JsonIo.fromToon(toon, null).asClass(Map.class);

        assertEquals("value1", restored.get("true"), "Key 'true' should survive round-trip");
        assertEquals("value2", restored.get("false"), "Key 'false' should survive round-trip");
        assertEquals("value3", restored.get("null"), "Key 'null' should survive round-trip");
    }

    @Test
    void testNumericLookingKeys() {
        // TOON spec: Keys that look like numbers must be quoted
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("42", "numeric");
        map.put("3.14", "decimal");
        map.put("-5", "negative");
        map.put("1e10", "exponent");

        String toon = JsonIo.toToon(map, null);
        Map<String, Object> restored = JsonIo.fromToon(toon, null).asClass(Map.class);

        assertEquals("numeric", restored.get("42"), "Numeric key '42' should survive");
        assertEquals("decimal", restored.get("3.14"), "Decimal key '3.14' should survive");
        assertEquals("negative", restored.get("-5"), "Negative key '-5' should survive");
        assertEquals("exponent", restored.get("1e10"), "Exponent key '1e10' should survive");
    }

    @Test
    void testSingleColumnTabular() {
        // Edge case: tabular array with only 1 field per object
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("name", "Alice");

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("name", "Bob");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("people", Arrays.asList(item1, item2));

        String toon = JsonIo.toToon(root, null);
        assertTrue(toon.contains("{name}:"), "Single-column tabular should have header: " + toon);

        Map<String, Object> restored = JsonIo.fromToon(toon, null).asClass(Map.class);
        List<?> people = (List<?>) restored.get("people");
        assertEquals(2, people.size());
        assertEquals("Alice", ((Map<?, ?>) people.get(0)).get("name"));
        assertEquals("Bob", ((Map<?, ?>) people.get(1)).get("name"));
    }

    @Test
    void testCaseSensitiveReservedWords() {
        // TOON spec: Reserved words are case-sensitive.
        // "True", "FALSE", "Null" are NOT reserved and should NOT be quoted.
        assertEquals("True", JsonIo.toToon("True", null), "\"True\" should not be quoted");
        assertEquals("FALSE", JsonIo.toToon("FALSE", null), "\"FALSE\" should not be quoted");
        assertEquals("Null", JsonIo.toToon("Null", null), "\"Null\" should not be quoted");
        assertEquals("TRUE", JsonIo.toToon("TRUE", null), "\"TRUE\" should not be quoted");
    }

    @Test
    void testMapWithNullValues() {
        // Map<String, Object> containing null values
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "John");
        map.put("middle", null);
        map.put("last", "Doe");

        String toon = JsonIo.toToon(map, null);
        Map<String, Object> restored = JsonIo.fromToon(toon, null).asClass(Map.class);

        assertEquals("John", restored.get("name"));
        assertTrue(restored.containsKey("middle"), "Key 'middle' should exist");
        assertNull(restored.get("middle"), "Null value should survive round-trip");
        assertEquals("Doe", restored.get("last"));
    }

    @Test
    void testInlineArrayWithQuotedValues() {
        // Array containing strings that need quoting in inline format
        String[] arr = {"hello,world", "true", "", "-flag", "normal"};
        String toon = JsonIo.toToon(arr, null);

        // Should be inline format since all values are primitives
        String[] restored = JsonIo.fromToon(toon, null).asClass(String[].class);
        assertEquals(5, restored.length);
        assertEquals("hello,world", restored[0]);
        assertEquals("true", restored[1]);
        assertEquals("", restored[2]);
        assertEquals("-flag", restored[3]);
        assertEquals("normal", restored[4]);
    }

    @Test
    void testArrayOfAllNulls() {
        // Array where every element is null
        Object[] arr = {null, null, null};
        String toon = JsonIo.toToon(arr, null);

        Object[] restored = JsonIo.fromToon(toon, null).asClass(Object[].class);
        assertEquals(3, restored.length);
        assertNull(restored[0]);
        assertNull(restored[1]);
        assertNull(restored[2]);
    }

    @Test
    void testExponentNumberInput() {
        // TOON spec: Reader should accept exponent notation even though writer never emits it
        String toon = "value: 1e6";
        Map<String, Object> map = JsonIo.fromToon(toon, null).asClass(Map.class);
        assertEquals(1000000.0, ((Number) map.get("value")).doubleValue(), 0.1,
                "Reader should parse 1e6 as 1000000");

        toon = "value: 1.5E-3";
        map = JsonIo.fromToon(toon, null).asClass(Map.class);
        assertEquals(0.0015, ((Number) map.get("value")).doubleValue(), 0.00001,
                "Reader should parse 1.5E-3 as 0.0015");
    }

    @Test
    void test3DArray() {
        // 3D primitive array
        int[][][] arr = {{{1, 2}, {3, 4}}, {{5, 6}, {7, 8}}};
        String toon = JsonIo.toToon(arr, null);

        int[][][] restored = JsonIo.fromToon(toon, null).asClass(int[][][].class);
        assertEquals(1, restored[0][0][0]);
        assertEquals(4, restored[0][1][1]);
        assertEquals(5, restored[1][0][0]);
        assertEquals(8, restored[1][1][1]);
    }

    @Test
    void testObjectWithAllNullFields() {
        // POJO with all null/zero fields
        Person person = new Person();
        String toon = JsonIo.toToon(person, null);

        Person restored = JsonIo.fromToon(toon, null).asClass(Person.class);
        assertNull(restored.name);
        assertEquals(0, restored.age);
    }

    @Test
    void testVeryLongString() {
        // String > 10K chars
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append((char) ('a' + (i % 26)));
        }
        String longStr = sb.toString();

        String toon = JsonIo.toToon(longStr, null);
        String restored = JsonIo.fromToon(toon, null).asClass(String.class);
        assertEquals(longStr, restored, "Very long string should survive round-trip");
    }

    @Test
    void testLargeArray() {
        // Array with 1000+ elements
        int[] arr = new int[1000];
        for (int i = 0; i < 1000; i++) {
            arr[i] = i;
        }
        String toon = JsonIo.toToon(arr, null);
        assertTrue(toon.contains("[1000]:"), "Should declare correct count");

        int[] restored = JsonIo.fromToon(toon, null).asClass(int[].class);
        assertEquals(1000, restored.length);
        assertEquals(0, restored[0]);
        assertEquals(999, restored[999]);
    }

    @Test
    void testNumberRoundTripFidelity() {
        // TOON spec: decode(encode(x)) MUST equal x for numbers
        // Note: Extreme values (Double.MIN_NORMAL, Double.MAX_VALUE) produce 300+ digit
        // plain decimal strings per TOON's no-exponent rule. These are a known limitation
        // where the reader may not parse back correctly. Test with practical doubles.
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pi", Math.PI);
        map.put("euler", Math.E);
        map.put("small", 0.000001);
        map.put("large", 9999999.999999);
        map.put("negative", -123.456789);

        String toon = JsonIo.toToon(map, null);
        Map<String, Object> restored = JsonIo.fromToon(toon, null).asClass(Map.class);

        assertEquals(Math.PI, ((Number) restored.get("pi")).doubleValue(), 0,
                "Math.PI should survive round-trip with full precision");
        assertEquals(Math.E, ((Number) restored.get("euler")).doubleValue(), 0,
                "Math.E should survive round-trip with full precision");
        assertEquals(0.000001, ((Number) restored.get("small")).doubleValue(), 0,
                "Small decimal should survive round-trip");
        assertEquals(9999999.999999, ((Number) restored.get("large")).doubleValue(), 0,
                "Large decimal should survive round-trip");
        assertEquals(-123.456789, ((Number) restored.get("negative")).doubleValue(), 0,
                "Negative decimal should survive round-trip");
    }

    @Test
    void testEmptyObjectInListArray_WriterOutput() {
        // Test that ToonWriter writes empty map as "- {}" in list context
        Map<String, Object> emptyObj = new LinkedHashMap<>();
        Map<String, Object> namedObj = new LinkedHashMap<>();
        namedObj.put("name", "Bob");

        List<Object> items = new ArrayList<>();
        items.add(emptyObj);
        items.add(namedObj);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", items);

        String toon = JsonIo.toToon(root, null);
        // Writer should produce "- {}" for empty map in list
        assertTrue(toon.contains("- {}"), "Empty map in list should write as '- {}': " + toon);
        assertTrue(toon.contains("- name: Bob"), "Named object should use hyphen format");
        // NOTE: ToonReader currently parses "{}" as a string in list context,
        // not as an empty map. This is a known ToonReader bug to fix separately.
    }
}