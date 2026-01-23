package com.cedarsoftware.io;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for ToonReader - TOON format parsing.
 * Tests round-trip: Object -> ToonWriter -> ToonReader -> Object
 */
class ToonReaderTest {

    // ========== Helper Methods ==========

    /**
     * Round-trip test: write object to TOON, read back, verify equality.
     */
    @SuppressWarnings("unchecked")
    private <T> void roundTrip(T original) {
        if (original == null) {
            String toon = JsonIo.toToon(null, null);
            Object restored = JsonIo.fromToon(toon, null).asClass(Object.class);
            assertNull(restored, "Expected null after round-trip");
            return;
        }

        String toon = JsonIo.toToon(original, null);
        assertNotNull(toon, "TOON output should not be null");

        T restored = (T) JsonIo.fromToon(toon, null).asClass(original.getClass());
        assertTrue(DeepEquals.deepEquals(original, restored),
                "Round-trip failed for " + original.getClass().getSimpleName() +
                        "\nOriginal: " + original +
                        "\nTOON: " + toon +
                        "\nRestored: " + restored);
    }

    private Map<String, Object> mapOf(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    // ========== 1. Primitive Values ==========

    @Test
    void testNull() {
        roundTrip(null);
    }

    @Test
    void testBooleanTrue() {
        roundTrip(true);
    }

    @Test
    void testBooleanFalse() {
        roundTrip(false);
    }

    @Test
    void testByte() {
        roundTrip((byte) 42);
    }

    @Test
    void testShort() {
        roundTrip((short) 1000);
    }

    @Test
    void testInt() {
        roundTrip(12345);
    }

    @Test
    void testLong() {
        roundTrip(9876543210L);
    }

    @Test
    void testFloat() {
        roundTrip(3.14f);
    }

    @Test
    void testDouble() {
        roundTrip(2.718281828);
    }

    @Test
    void testChar() {
        roundTrip('X');
    }

    // ========== 2. Primitive Arrays (1D) ==========

    @Test
    void testBooleanArray() {
        roundTrip(new boolean[]{true, false, true});
    }

    @Test
    void testByteArray() {
        roundTrip(new byte[]{1, 2, 3});
    }

    @Test
    void testShortArray() {
        roundTrip(new short[]{100, 200, 300});
    }

    @Test
    void testIntArray() {
        roundTrip(new int[]{1, 2, 3, 4, 5});
    }

    @Test
    void testLongArray() {
        roundTrip(new long[]{10L, 20L, 30L});
    }

    @Test
    void testFloatArray() {
        roundTrip(new float[]{1.1f, 2.2f, 3.3f});
    }

    @Test
    void testDoubleArray() {
        roundTrip(new double[]{1.1, 2.2, 3.3});
    }

    @Test
    void testCharArray() {
        roundTrip(new char[]{'a', 'b', 'c'});
    }

    // ========== 3. Primitive Arrays (2D) ==========
    // Note: 2D arrays require ToonWriter improvements

    @Test
    void testInt2DArray() {
        roundTrip(new int[][]{{1, 2}, {3, 4}});
    }

    @Test
    void testDouble2DArray() {
        roundTrip(new double[][]{{1.1, 2.2}, {3.3, 4.4}});
    }

    // ========== 4. Object Arrays ==========

    @Test
    void testStringArray() {
        roundTrip(new String[]{"hello", "world"});
    }

    @Test
    void testObjectArray() {
        roundTrip(new Object[]{1, "two", 3.0, true});
    }

    @Test
    void testEmptyArray() {
        roundTrip(new Object[]{});
    }

    // ========== 5. Collections - List ==========

    @Test
    void testArrayList() {
        roundTrip(new ArrayList<>(Arrays.asList(1, 2, 3)));
    }

    @Test
    void testLinkedList() {
        roundTrip(new LinkedList<>(Arrays.asList("a", "b")));
    }

    @Test
    void testEmptyList() {
        roundTrip(new ArrayList<>());
    }

    @Test
    void testNestedList() {
        List<List<Integer>> nested = new ArrayList<>();
        nested.add(Arrays.asList(1, 2));
        nested.add(Arrays.asList(3, 4));
        roundTrip(nested);
    }

    // ========== 6. Collections - Set ==========

    @Test
    void testHashSet() {
        roundTrip(new HashSet<>(Arrays.asList(1, 2, 3)));
    }

    @Test
    void testTreeSet() {
        roundTrip(new TreeSet<>(Arrays.asList("a", "b", "c")));
    }

    // ========== 7. Maps ==========

    @Test
    void testSimpleMap() {
        roundTrip(mapOf("name", "John", "age", 30));
    }

    @Test
    void testNestedMap() {
        roundTrip(mapOf("person", mapOf("name", "John")));
    }

    @Test
    void testEmptyMap() {
        roundTrip(new HashMap<>());
    }

    @Test
    void testMapWithList() {
        roundTrip(mapOf("items", Arrays.asList(1, 2, 3)));
    }

    // ========== 8. Date/Time Types ==========
    // Note: Many Java time types require ToonWriter to use custom writers (like JsonWriter does)
    // These tests are disabled until ToonWriter is enhanced

    @Test
    void testDate() {
        roundTrip(new Date());
    }

    @Test
    void testSqlDate() {
        // Use valueOf() to create a proper date-only java.sql.Date (no time component)
        roundTrip(java.sql.Date.valueOf(LocalDate.now()));
    }

    @Test
    void testTimestamp() {
        roundTrip(new Timestamp(System.currentTimeMillis()));
    }

    @Test
    void testCalendar() {
        roundTrip(Calendar.getInstance());
    }

    @Test
    void testInstant() {
        roundTrip(Instant.now());
    }

    @Test
    void testDuration() {
        roundTrip(Duration.ofHours(5));
    }

    @Test
    void testPeriod() {
        roundTrip(Period.ofDays(30));
    }

    @Test
    void testLocalDate() {
        roundTrip(LocalDate.of(2024, 1, 15));
    }

    @Test
    void testLocalTime() {
        roundTrip(LocalTime.of(14, 30, 0));
    }

    @Test
    void testLocalDateTime() {
        roundTrip(LocalDateTime.of(2024, 1, 15, 14, 30));
    }

    @Test
    void testZonedDateTime() {
        roundTrip(ZonedDateTime.now());
    }

    @Test
    void testOffsetDateTime() {
        roundTrip(OffsetDateTime.now());
    }

    @Test
    void testOffsetTime() {
        roundTrip(OffsetTime.now());
    }

    @Test
    void testYear() {
        roundTrip(Year.of(2024));
    }

    @Test
    void testYearMonth() {
        roundTrip(YearMonth.of(2024, 6));
    }

    @Test
    void testMonthDay() {
        roundTrip(MonthDay.of(12, 25));
    }

    @Test
    void testZoneId() {
        roundTrip(ZoneId.of("America/New_York"));
    }

    @Test
    void testZoneOffset() {
        roundTrip(ZoneOffset.ofHours(-5));
    }

    @Test
    void testTimeZone() {
        roundTrip(TimeZone.getTimeZone("America/New_York"));
    }

    // ========== 9. ID/Location Types ==========
    // Note: Some types require ToonWriter custom writers

    @Test
    void testUUID() {
        roundTrip(UUID.randomUUID());
    }

    @Test
    void testURI() {
        roundTrip(URI.create("https://example.com"));
    }

    @Test
    void testURL() throws Exception {
        roundTrip(new URL("https://example.com"));
    }

    @Test
    void testLocale() {
        roundTrip(Locale.US);
    }

    @Test
    void testCurrency() {
        roundTrip(Currency.getInstance("USD"));
    }

    @Test
    void testFile() {
        roundTrip(new File("/tmp/test.txt"));
    }

    @Test
    void testPath() {
        roundTrip(Paths.get("/tmp/test.txt"));
    }

    @Test
    void testClass() {
        roundTrip(String.class);
    }

    // ========== 10. Big Numbers & Atomic Types ==========

    @Test
    void testBigInteger() {
        roundTrip(new BigInteger("12345678901234567890"));
    }

    @Test
    void testBigDecimal() {
        roundTrip(new BigDecimal("123.456789"));
    }

    @Test
    void testAtomicInteger() {
        AtomicInteger original = new AtomicInteger(42);
        String toon = JsonIo.toToon(original, null);
        AtomicInteger restored = JsonIo.fromToon(toon, null).asClass(AtomicInteger.class);
        assertEquals(original.get(), restored.get());
    }

    @Test
    void testAtomicLong() {
        AtomicLong original = new AtomicLong(123456789L);
        String toon = JsonIo.toToon(original, null);
        AtomicLong restored = JsonIo.fromToon(toon, null).asClass(AtomicLong.class);
        assertEquals(original.get(), restored.get());
    }

    @Test
    void testAtomicBoolean() {
        AtomicBoolean original = new AtomicBoolean(true);
        String toon = JsonIo.toToon(original, null);
        AtomicBoolean restored = JsonIo.fromToon(toon, null).asClass(AtomicBoolean.class);
        assertEquals(original.get(), restored.get());
    }

    // ========== 11. String Types & Buffers ==========

    @Test
    void testString() {
        roundTrip("hello world");
    }

    @Test
    void testStringBuffer() {
        StringBuffer original = new StringBuffer("hello");
        String toon = JsonIo.toToon(original, null);
        StringBuffer restored = JsonIo.fromToon(toon, null).asClass(StringBuffer.class);
        assertEquals(original.toString(), restored.toString());
    }

    @Test
    void testStringBuilder() {
        StringBuilder original = new StringBuilder("world");
        String toon = JsonIo.toToon(original, null);
        StringBuilder restored = JsonIo.fromToon(toon, null).asClass(StringBuilder.class);
        assertEquals(original.toString(), restored.toString());
    }

    // ========== 12. Enum Types ==========
    // Note: Enum types require ToonWriter custom writers

    @Test
    void testEnum() {
        roundTrip(java.time.DayOfWeek.MONDAY);
    }

    @Test
    void testMonth() {
        roundTrip(java.time.Month.JANUARY);
    }

    // ========== 13. Business Objects ==========

    @Test
    void testSimplePerson() {
        TestPerson original = new TestPerson("John", 30);
        String toon = JsonIo.toToon(original, null);
        TestPerson restored = JsonIo.fromToon(toon, null).asClass(TestPerson.class);
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getAge(), restored.getAge());
    }

    @Test
    void testPersonWithAddress() {
        TestPerson person = new TestPerson("John", 30);
        person.setAddress(new TestAddress("NYC", "10001"));
        String toon = JsonIo.toToon(person, null);
        TestPerson restored = JsonIo.fromToon(toon, null).asClass(TestPerson.class);
        assertEquals(person.getName(), restored.getName());
        assertEquals(person.getAddress().getCity(), restored.getAddress().getCity());
        assertEquals(person.getAddress().getZip(), restored.getAddress().getZip());
    }

    @Test
    @Disabled("Requires @type hint support in ToonWriter/ToonReader for generic collection element conversion")
    void testListOfPerson() {
        List<TestPerson> people = Arrays.asList(
                new TestPerson("John", 30),
                new TestPerson("Jane", 25)
        );
        String toon = JsonIo.toToon(people, null);
        List<TestPerson> restored = JsonIo.fromToon(toon, null)
                .asType(new TypeHolder<List<TestPerson>>() {});
        assertEquals(2, restored.size());
        assertEquals("John", restored.get(0).getName());
        assertEquals("Jane", restored.get(1).getName());
    }

    @Test
    void testMapOfPerson() {
        Map<String, TestPerson> map = new HashMap<>();
        map.put("emp1", new TestPerson("John", 30));
        map.put("emp2", new TestPerson("Jane", 25));
        String toon = JsonIo.toToon(map, null);
        Map<String, TestPerson> restored = JsonIo.fromToon(toon, null)
                .asType(new TypeHolder<Map<String, TestPerson>>() {});
        assertEquals(2, restored.size());
        assertNotNull(restored.get("emp1"));
        assertNotNull(restored.get("emp2"));
    }

    // ========== 14. String Edge Cases ==========

    @Test
    void testEmptyString() {
        roundTrip("");
    }

    @Test
    void testStringWithSpaces() {
        roundTrip("hello world");
    }

    @Test
    void testStringWithColon() {
        roundTrip("key: value");
    }

    @Test
    void testStringWithNewline() {
        roundTrip("line1\nline2");
    }

    @Test
    void testStringWithTab() {
        roundTrip("col1\tcol2");
    }

    @Test
    void testStringWithQuote() {
        roundTrip("say \"hello\"");
    }

    @Test
    void testStringWithBackslash() {
        roundTrip("path\\to\\file");
    }

    @Test
    void testStringLooksLikeNumber() {
        // When stored as object field, "123" should remain a string
        Map<String, Object> map = mapOf("value", "123");
        String toon = JsonIo.toToon(map, null);
        Map<String, Object> restored = JsonIo.fromToonToMaps(toon, null).asClass(Map.class);
        assertEquals("123", restored.get("value"));
    }

    @Test
    void testStringLooksLikeBoolean() {
        Map<String, Object> map = mapOf("value", "true");
        String toon = JsonIo.toToon(map, null);
        Map<String, Object> restored = JsonIo.fromToonToMaps(toon, null).asClass(Map.class);
        assertEquals("true", restored.get("value"));
    }

    @Test
    void testStringLooksLikeNull() {
        Map<String, Object> map = mapOf("value", "null");
        String toon = JsonIo.toToon(map, null);
        Map<String, Object> restored = JsonIo.fromToonToMaps(toon, null).asClass(Map.class);
        assertEquals("null", restored.get("value"));
    }

    @Test
    void testStringWithLeadingSpace() {
        roundTrip(" hello");
    }

    @Test
    void testStringWithTrailingSpace() {
        roundTrip("hello ");
    }

    // ========== 15. fromToonToMaps Tests ==========

    @Test
    void testFromToonToMaps() {
        String toon = "name: John\nage: 30";
        Map<String, Object> map = JsonIo.fromToonToMaps(toon).asClass(Map.class);
        assertEquals("John", map.get("name"));
        assertEquals(30L, map.get("age"));
    }

    @Test
    void testFromToonToMapsWithStream() {
        String toon = "name: Jane\nage: 25";
        ByteArrayInputStream stream = new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> map = JsonIo.fromToonToMaps(stream).asClass(Map.class);
        assertEquals("Jane", map.get("name"));
        assertEquals(25L, map.get("age"));
    }

    // ========== 16. InputStream Tests ==========

    @Test
    void testFromToonStream() {
        TestPerson original = new TestPerson("John", 30);
        String toon = JsonIo.toToon(original, null);
        ByteArrayInputStream stream = new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8));
        TestPerson restored = JsonIo.fromToon(stream, null).asClass(TestPerson.class);
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getAge(), restored.getAge());
    }

    // ========== Test Model Classes ==========

    public static class TestPerson {
        private String name;
        private int age;
        private TestAddress address;

        public TestPerson() {}

        public TestPerson(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        public TestAddress getAddress() { return address; }
        public void setAddress(TestAddress address) { this.address = address; }
    }

    public static class TestAddress {
        private String city;
        private String zip;

        public TestAddress() {}

        public TestAddress(String city, String zip) {
            this.city = city;
            this.zip = zip;
        }

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getZip() { return zip; }
        public void setZip(String zip) { this.zip = zip; }
    }

    // ========== Direct ToonReader Parsing Tests ==========
    // These tests verify ToonReader can parse TOON strings directly

    @Test
    void testParseSimpleObject() {
        String toon = "name: John\nage: 30";
        Map<String, Object> map = JsonIo.fromToonToMaps(toon).asClass(Map.class);
        assertEquals("John", map.get("name"));
        assertEquals(30L, map.get("age"));
    }

    @Test
    void testParseNestedObject() {
        String toon = "person:\n  name: John\n  age: 30";
        Map<String, Object> map = JsonIo.fromToonToMaps(toon).asClass(Map.class);
        assertNotNull(map.get("person"));
        Map<String, Object> person = (Map<String, Object>) map.get("person");
        assertEquals("John", person.get("name"));
        assertEquals(30L, person.get("age"));
    }

    @Test
    void testParseInlineArray() {
        String toon = "[3]: 1,2,3";
        Object[] arr = JsonIo.fromToonToMaps(toon).asClass(Object[].class);
        assertEquals(3, arr.length);
        assertEquals(1L, arr[0]);
        assertEquals(2L, arr[1]);
        assertEquals(3L, arr[2]);
    }

    @Test
    void testParseEmptyArray() {
        String toon = "[0]:";
        Object[] arr = JsonIo.fromToonToMaps(toon).asClass(Object[].class);
        assertEquals(0, arr.length);
    }

    @Test
    void testParseQuotedString() {
        String toon = "value: \"hello world\"";
        Map<String, Object> map = JsonIo.fromToonToMaps(toon).asClass(Map.class);
        assertEquals("hello world", map.get("value"));
    }

    @Test
    void testParseEscapeSequences() {
        String toon = "value: \"line1\\nline2\"";
        Map<String, Object> map = JsonIo.fromToonToMaps(toon).asClass(Map.class);
        assertEquals("line1\nline2", map.get("value"));
    }

    @Test
    void testParseBooleansAndNull() {
        String toon = "a: true\nb: false\nc: null";
        Map<String, Object> map = JsonIo.fromToonToMaps(toon).asClass(Map.class);
        assertEquals(true, map.get("a"));
        assertEquals(false, map.get("b"));
        assertNull(map.get("c"));
    }

    @Test
    void testParseNumbers() {
        String toon = "integer: 42\nfloat: 3.14\nnegative: -5";
        Map<String, Object> map = JsonIo.fromToonToMaps(toon).asClass(Map.class);
        assertEquals(42L, map.get("integer"));
        assertEquals(3.14, map.get("float"));
        assertEquals(-5L, map.get("negative"));
    }

    @Test
    void testParseListArray() {
        String toon = "[2]:\n  - hello\n  - world";
        Object[] arr = JsonIo.fromToonToMaps(toon).asClass(Object[].class);
        assertEquals(2, arr.length);
        assertEquals("hello", arr[0]);
        assertEquals("world", arr[1]);
    }

    @Test
    void testParseObjectWithArray() {
        String toon = "name: John\ntags:\n  [3]: java,json,toon";
        Map<String, Object> map = JsonIo.fromToonToMaps(toon).asClass(Map.class);
        assertEquals("John", map.get("name"));
        List<?> tags = (List<?>) map.get("tags");
        assertEquals(3, tags.size());
        assertEquals("java", tags.get(0));
        assertEquals("json", tags.get(1));
        assertEquals("toon", tags.get(2));
    }
}
