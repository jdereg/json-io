package com.cedarsoftware.io;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for configurable TOON delimiter (comma, tab, pipe) on write.
 * Verifies that ToonWriter respects WriteOptions.getToonDelimiter()
 * and that ToonReader auto-detects the delimiter on read for round-trip fidelity.
 */
class ToonDelimiterTest {

    // ==================== POJOs ====================

    static class Person {
        String name;
        int age;

        Person() {}
        Person(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    // ==================== Default Comma ====================

    @Test
    void testDefaultCommaDelimiter() {
        List<Person> people = Arrays.asList(
                new Person("Alice", 25),
                new Person("Bob", 30)
        );
        String toon = JsonIo.toToon(people, null);

        assertTrue(toon.contains("{name,age}:"), "Default should use comma in header: " + toon);
        assertTrue(toon.contains("Alice,25"), "Default should use comma in rows: " + toon);
        assertTrue(toon.startsWith("[2]"), "Default comma should not have delimiter suffix in count: " + toon);
    }

    // ==================== Tab Delimiter ====================

    @Test
    void testTabDelimiter_tabularOutput() {
        WriteOptions opts = new WriteOptionsBuilder().toonDelimiter('\t').build();
        List<Person> people = Arrays.asList(
                new Person("Alice", 25),
                new Person("Bob", 30)
        );
        String toon = JsonIo.toToon(people, opts);

        assertTrue(toon.contains("{name\tage}:"), "Tab delimiter should appear in header: " + toon);
        assertTrue(toon.contains("Alice\t25"), "Tab delimiter should appear in rows: " + toon);
        assertTrue(toon.startsWith("[2\t]"), "Tab count marker should be [2\\t]: " + toon);
    }

    @Test
    void testTabDelimiter_roundTrip() {
        WriteOptions opts = new WriteOptionsBuilder().toonDelimiter('\t').build();
        List<Map<String, Object>> data = Arrays.asList(
                makeMap("name", "Alice", "age", 25),
                makeMap("name", "Bob", "age", 30)
        );
        String toon = JsonIo.toToon(data, opts);
        List<?> restored = JsonIo.fromToon(toon, null).asClass(List.class);

        assertEquals(2, restored.size());
        Map<?, ?> first = (Map<?, ?>) restored.get(0);
        assertEquals("Alice", first.get("name"));
        assertEquals(25L, first.get("age"));
    }

    // ==================== Pipe Delimiter ====================

    @Test
    void testPipeDelimiter_tabularOutput() {
        WriteOptions opts = new WriteOptionsBuilder().toonDelimiter('|').build();
        List<Person> people = Arrays.asList(
                new Person("Alice", 25),
                new Person("Bob", 30)
        );
        String toon = JsonIo.toToon(people, opts);

        assertTrue(toon.contains("{name|age}:"), "Pipe delimiter should appear in header: " + toon);
        assertTrue(toon.contains("Alice|25"), "Pipe delimiter should appear in rows: " + toon);
        assertTrue(toon.startsWith("[2|]"), "Pipe count marker should be [2|]: " + toon);
    }

    @Test
    void testPipeDelimiter_roundTrip() {
        WriteOptions opts = new WriteOptionsBuilder().toonDelimiter('|').build();
        List<Map<String, Object>> data = Arrays.asList(
                makeMap("name", "Alice", "age", 25),
                makeMap("name", "Bob", "age", 30)
        );
        String toon = JsonIo.toToon(data, opts);
        List<?> restored = JsonIo.fromToon(toon, null).asClass(List.class);

        assertEquals(2, restored.size());
        Map<?, ?> second = (Map<?, ?>) restored.get(1);
        assertEquals("Bob", second.get("name"));
        assertEquals(30L, second.get("age"));
    }

    // ==================== Invalid Delimiter ====================

    @Test
    void testInvalidDelimiter_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new WriteOptionsBuilder().toonDelimiter(';'));
    }

    // ==================== Inline Primitive Arrays ====================

    @Test
    void testTabDelimiter_inlinePrimitives() {
        WriteOptions opts = new WriteOptionsBuilder().toonDelimiter('\t').build();
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
        String toon = JsonIo.toToon(numbers, opts);

        // Inline primitives should use tab separator
        assertTrue(toon.contains("1\t2\t3\t4\t5"), "Inline primitives should use tab: " + toon);
    }

    @Test
    void testPipeDelimiter_inlinePrimitives() {
        WriteOptions opts = new WriteOptionsBuilder().toonDelimiter('|').build();
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
        String toon = JsonIo.toToon(numbers, opts);

        assertTrue(toon.contains("1|2|3|4|5"), "Inline primitives should use pipe: " + toon);
    }

    // ==================== Values Containing Delimiter ====================

    @Test
    void testTabDelimiter_valuesWithCommasNotQuoted() {
        // With tab delimiter, commas in values should NOT need quoting
        WriteOptions opts = new WriteOptionsBuilder().toonDelimiter('\t').build();
        List<Map<String, Object>> data = Arrays.asList(
                makeMap("name", "Smith, John", "age", 30)
        );
        String toon = JsonIo.toToon(data, opts);

        // "Smith, John" should NOT be quoted since comma is not the active delimiter
        assertTrue(toon.contains("Smith, John\t30"), "Comma in value should not need quoting with tab delimiter: " + toon);
    }

    @Test
    void testPipeDelimiter_valuesWithPipeQuoted() {
        // With pipe delimiter, pipe characters in values SHOULD be quoted
        WriteOptions opts = new WriteOptionsBuilder().toonDelimiter('|').build();
        List<Map<String, Object>> data = Arrays.asList(
                makeMap("name", "A|B", "age", 30)
        );
        String toon = JsonIo.toToon(data, opts);

        // "A|B" should be quoted because pipe is the active delimiter
        assertTrue(toon.contains("\"A|B\""), "Pipe in value should be quoted with pipe delimiter: " + toon);
    }

    // ==================== Nested Structures ====================

    @Test
    void testTabDelimiter_nestedStructure() {
        WriteOptions opts = new WriteOptionsBuilder().toonDelimiter('\t').build();

        Map<String, Object> company = new LinkedHashMap<>();
        company.put("name", "Acme");
        List<Map<String, Object>> employees = Arrays.asList(
                makeMap("name", "Alice", "age", 25),
                makeMap("name", "Bob", "age", 30)
        );
        company.put("employees", employees);

        String toon = JsonIo.toToon(company, opts);

        // Inner tabular array should also use tab delimiter
        assertTrue(toon.contains("{name\tage}:"), "Nested tabular should use tab: " + toon);
        assertTrue(toon.contains("Alice\t25"), "Nested rows should use tab: " + toon);
    }

    @Test
    void testPipeDelimiter_nestedRoundTrip() {
        WriteOptions opts = new WriteOptionsBuilder().toonDelimiter('|').build();

        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("title", "Report");
        List<Map<String, Object>> rows = Arrays.asList(
                makeMap("x", 1, "y", 2),
                makeMap("x", 3, "y", 4)
        );
        outer.put("data", rows);

        String toon = JsonIo.toToon(outer, opts);
        Map<?, ?> restored = JsonIo.fromToon(toon, null).asClass(Map.class);

        assertEquals("Report", restored.get("title"));
        List<?> restoredRows = (List<?>) restored.get("data");
        assertEquals(2, restoredRows.size());
        Map<?, ?> row1 = (Map<?, ?>) restoredRows.get(0);
        assertEquals(1L, row1.get("x"));
        assertEquals(2L, row1.get("y"));
    }

    // ==================== Helper ====================

    private static Map<String, Object> makeMap(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }
}
