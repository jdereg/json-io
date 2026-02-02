package com.cedarsoftware.io;

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
        // Structure: root -> items[] -> nested object -> data[] -> objects
        // Expected format:
        // items[1]:
        //   - info:
        //       data[2]:
        //         - id: 1
        //           value: a
        //         - id: 2
        //           value: b
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

        // Verify three-level nesting structure
        assertTrue(toon.contains("items[1]:"), "Root field should combine with array size");
        assertTrue(toon.contains("- info:"), "First field of items element on hyphen line");
        assertTrue(toon.contains("data[2]:"), "Nested field should combine with array size");
        assertTrue(toon.contains("- id: 1"), "First field of data element on hyphen line");
        assertTrue(toon.contains("value: a"), "Second field of first data element");
        assertTrue(toon.contains("- id: 2"), "First field of second data element on hyphen line");
        assertTrue(toon.contains("value: b"), "Second field of second data element");
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
        assertTrue(toon.contains("a[1]:"), "Level 1 field+size");
        assertTrue(toon.contains("- b[1]:"), "Level 2 on hyphen line with size");
        assertTrue(toon.contains("- c[1]:"), "Level 3 on hyphen line with size");
        assertTrue(toon.contains("- d[1]:"), "Level 4 on hyphen line with size");
        assertTrue(toon.contains("- value: deep"), "Deepest value on hyphen line");
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
}