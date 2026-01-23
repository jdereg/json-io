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