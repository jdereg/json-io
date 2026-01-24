package com.cedarsoftware.io;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
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
import java.util.BitSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import com.cedarsoftware.util.DeepEquals;
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

    @Test
    void testLongMaxValue() {
        roundTrip(Long.MAX_VALUE);
    }

    @Test
    void testLongMinValue() {
        roundTrip(Long.MIN_VALUE);
    }

    @Test
    void testDoubleMaxValue() {
        roundTrip(Double.MAX_VALUE);
    }

    @Test
    void testDoubleMinValue() {
        roundTrip(Double.MIN_VALUE);
    }

    @Test
    void testNaN_becomesNull() {
        // Per TOON spec, NaN becomes null
        String toon = JsonIo.toToon(Double.NaN, null);
        Object restored = JsonIo.fromToon(toon, null).asClass(Object.class);
        assertNull(restored, "NaN should become null in TOON");
    }

    @Test
    void testPositiveInfinity_becomesNull() {
        // Per TOON spec, Infinity becomes null
        String toon = JsonIo.toToon(Double.POSITIVE_INFINITY, null);
        Object restored = JsonIo.fromToon(toon, null).asClass(Object.class);
        assertNull(restored, "Positive Infinity should become null in TOON");
    }

    @Test
    void testNegativeInfinity_becomesNull() {
        // Per TOON spec, -Infinity becomes null
        String toon = JsonIo.toToon(Double.NEGATIVE_INFINITY, null);
        Object restored = JsonIo.fromToon(toon, null).asClass(Object.class);
        assertNull(restored, "-Infinity should become null in TOON");
    }

    @Test
    void testNegativeZero_becomesZero() {
        // Per TOON spec, -0 normalizes to 0
        String toon = JsonIo.toToon(-0.0, null);
        Double restored = JsonIo.fromToon(toon, null).asClass(Double.class);
        assertEquals(0.0, restored, "Negative zero should become 0 in TOON");
        // Verify it's not negative zero
        assertFalse(Double.toString(restored).startsWith("-"), "Should not be negative zero");
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

    @Test
    void testPattern() {
        Pattern original = Pattern.compile("\\d+\\.\\d+");
        String toon = JsonIo.toToon(original, null);
        Pattern restored = JsonIo.fromToon(toon, null).asClass(Pattern.class);
        assertEquals(original.pattern(), restored.pattern());
    }

    @Test
    void testBitSet() {
        BitSet original = new BitSet();
        original.set(0);
        original.set(3);
        original.set(7);
        original.set(15);
        String toon = JsonIo.toToon(original, null);
        BitSet restored = JsonIo.fromToon(toon, null).asClass(BitSet.class);
        assertEquals(original, restored);
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

    @Test
    void testListOfPersonAsObjectArray() {
        // When reading TOON as Object[].class (without generic type info),
        // elements should be Maps since there's no type hint in the data.
        List<TestPerson> people = Arrays.asList(
                new TestPerson("John", 30),
                new TestPerson("Jane", 25)
        );
        String toon = JsonIo.toToon(people, null);

        // Read as Object[] - elements should be Maps (no type info to convert them)
        Object[] result = JsonIo.fromToon(toon, null).asClass(Object[].class);

        assertEquals(2, result.length);

        // Each element should be a Map (JsonObject) since no type hint is available
        assertTrue(result[0] instanceof Map, "Element should be a Map");
        assertTrue(result[1] instanceof Map, "Element should be a Map");

        Map<?, ?> map0 = (Map<?, ?>) result[0];
        Map<?, ?> map1 = (Map<?, ?>) result[1];

        assertEquals("John", map0.get("name"));
        assertEquals(30L, map0.get("age"));  // Numbers come back as Long
        assertEquals("Jane", map1.get("name"));
        assertEquals(25L, map1.get("age"));
    }

    @Test
    void testMapOfPersonWithTypeHolder() {
        // With full generic type info via TypeHolder, values should be converted to TestPerson
        Map<String, TestPerson> original = new LinkedHashMap<>();
        original.put("emp1", new TestPerson("John", 30));
        original.put("emp2", new TestPerson("Jane", 25));

        String toon = JsonIo.toToon(original, null);

        // Read with TypeHolder - values should be TestPerson instances
        Map<String, TestPerson> restored = JsonIo.fromToon(toon, null)
                .asType(new TypeHolder<Map<String, TestPerson>>() {});

        assertEquals(2, restored.size());
        assertTrue(restored.get("emp1") instanceof TestPerson, "Value should be TestPerson");
        assertTrue(restored.get("emp2") instanceof TestPerson, "Value should be TestPerson");
        assertEquals("John", restored.get("emp1").getName());
        assertEquals(30, restored.get("emp1").getAge());
        assertEquals("Jane", restored.get("emp2").getName());
        assertEquals(25, restored.get("emp2").getAge());
    }

    @Test
    void testMapOfPersonAsMapClass() {
        // With just Map.class (no generic info), values should remain as Maps
        Map<String, TestPerson> original = new LinkedHashMap<>();
        original.put("emp1", new TestPerson("John", 30));
        original.put("emp2", new TestPerson("Jane", 25));

        String toon = JsonIo.toToon(original, null);

        // Read as Map.class - values should be Maps (no type info to convert them)
        Map<?, ?> restored = JsonIo.fromToon(toon, null).asClass(Map.class);

        assertEquals(2, restored.size());

        // Values should be Maps (JsonObjects) since no type hint is available
        Object val1 = restored.get("emp1");
        Object val2 = restored.get("emp2");
        assertTrue(val1 instanceof Map, "Value should be a Map, got: " + val1.getClass().getName());
        assertTrue(val2 instanceof Map, "Value should be a Map, got: " + val2.getClass().getName());

        Map<?, ?> map1 = (Map<?, ?>) val1;
        Map<?, ?> map2 = (Map<?, ?>) val2;
        assertEquals("John", map1.get("name"));
        assertEquals(30L, map1.get("age"));
        assertEquals("Jane", map2.get("name"));
        assertEquals(25L, map2.get("age"));
    }

    @Test
    void testMapOfPersonAsLinkedHashMapClass() {
        // With LinkedHashMap.class (specific type, no generic info), values should remain as Maps
        Map<String, TestPerson> original = new LinkedHashMap<>();
        original.put("emp1", new TestPerson("John", 30));
        original.put("emp2", new TestPerson("Jane", 25));

        String toon = JsonIo.toToon(original, null);

        // Read as LinkedHashMap.class - values should be Maps (no type info to convert them)
        LinkedHashMap<?, ?> restored = JsonIo.fromToon(toon, null).asClass(LinkedHashMap.class);

        assertEquals(2, restored.size());
        assertTrue(restored instanceof LinkedHashMap, "Should be LinkedHashMap");

        // Values should be Maps (JsonObjects) since no type hint is available
        Object val1 = restored.get("emp1");
        Object val2 = restored.get("emp2");
        assertTrue(val1 instanceof Map, "Value should be a Map, got: " + val1.getClass().getName());
        assertTrue(val2 instanceof Map, "Value should be a Map, got: " + val2.getClass().getName());

        Map<?, ?> map1 = (Map<?, ?>) val1;
        Map<?, ?> map2 = (Map<?, ?>) val2;
        assertEquals("John", map1.get("name"));
        assertEquals(30L, map1.get("age"));
        assertEquals("Jane", map2.get("name"));
        assertEquals(25L, map2.get("age"));
    }

    @Test
    void testMapWithPersonKeyAndStringValue() {
        // Test Map<Person, String> where Person is the KEY type (not value)
        // ToonWriter serializes maps with complex keys using array-of-entries format:
        //   [2]:
        //   -
        //     $key:
        //       name: John
        //       age: 30
        //     $value: employee1
        // The $ prefix avoids collision with objects that have "key"/"value" fields.
        // Resolver detects this pattern and converts back to a proper Map.
        Map<TestPerson, String> original = new LinkedHashMap<>();
        original.put(new TestPerson("John", 30), "employee1");
        original.put(new TestPerson("Jane", 25), "employee2");

        String toon = JsonIo.toToon(original, null);

        // Read with TypeHolder - keys should be TestPerson instances
        Map<TestPerson, String> restored = JsonIo.fromToon(toon, null)
                .asType(new TypeHolder<Map<TestPerson, String>>() {});

        assertEquals(2, restored.size());

        // Verify keys are TestPerson instances
        boolean foundJohn = false;
        boolean foundJane = false;
        for (Map.Entry<TestPerson, String> entry : restored.entrySet()) {
            assertTrue(entry.getKey() instanceof TestPerson,
                    "Key should be TestPerson, got: " + entry.getKey().getClass().getName());
            assertTrue(entry.getValue() instanceof String,
                    "Value should be String, got: " + entry.getValue().getClass().getName());

            if ("John".equals(entry.getKey().getName())) {
                assertEquals(30, entry.getKey().getAge());
                assertEquals("employee1", entry.getValue());
                foundJohn = true;
            } else if ("Jane".equals(entry.getKey().getName())) {
                assertEquals(25, entry.getKey().getAge());
                assertEquals("employee2", entry.getValue());
                foundJane = true;
            }
        }
        assertTrue(foundJohn, "Should find John as key");
        assertTrue(foundJane, "Should find Jane as key");
    }

    @Test
    void testMapWithComplexKeyAndComplexValue() {
        // Both key AND value are complex objects requiring conversion
        Map<TestPerson, TestPerson> original = new LinkedHashMap<>();
        original.put(new TestPerson("Manager", 40), new TestPerson("Employee1", 25));
        original.put(new TestPerson("Director", 50), new TestPerson("Employee2", 30));

        String toon = JsonIo.toToon(original, null);

        Map<TestPerson, TestPerson> restored = JsonIo.fromToon(toon, null)
                .asType(new TypeHolder<Map<TestPerson, TestPerson>>() {});

        assertEquals(2, restored.size());

        // Verify both keys and values are properly converted
        for (Map.Entry<TestPerson, TestPerson> entry : restored.entrySet()) {
            assertTrue(entry.getKey() instanceof TestPerson, "Key should be TestPerson");
            assertTrue(entry.getValue() instanceof TestPerson, "Value should be TestPerson");

            if ("Manager".equals(entry.getKey().getName())) {
                assertEquals(40, entry.getKey().getAge());
                assertEquals("Employee1", entry.getValue().getName());
                assertEquals(25, entry.getValue().getAge());
            } else if ("Director".equals(entry.getKey().getName())) {
                assertEquals(50, entry.getKey().getAge());
                assertEquals("Employee2", entry.getValue().getName());
                assertEquals(30, entry.getValue().getAge());
            }
        }
    }

    @Test
    void testMapWithComplexKeyNullValue() {
        // Complex key with null value
        Map<TestPerson, String> original = new LinkedHashMap<>();
        original.put(new TestPerson("John", 30), null);
        original.put(new TestPerson("Jane", 25), "hasValue");

        String toon = JsonIo.toToon(original, null);

        Map<TestPerson, String> restored = JsonIo.fromToon(toon, null)
                .asType(new TypeHolder<Map<TestPerson, String>>() {});

        assertEquals(2, restored.size());

        boolean foundJohnWithNull = false;
        boolean foundJaneWithValue = false;
        for (Map.Entry<TestPerson, String> entry : restored.entrySet()) {
            if ("John".equals(entry.getKey().getName())) {
                assertNull(entry.getValue(), "John's value should be null");
                foundJohnWithNull = true;
            } else if ("Jane".equals(entry.getKey().getName())) {
                assertEquals("hasValue", entry.getValue());
                foundJaneWithValue = true;
            }
        }
        assertTrue(foundJohnWithNull, "Should find John with null value");
        assertTrue(foundJaneWithValue, "Should find Jane with value");
    }

    @Test
    void testEmptyMapWithComplexKeyType() {
        // Empty map should still work
        Map<TestPerson, String> original = new LinkedHashMap<>();

        String toon = JsonIo.toToon(original, null);

        Map<TestPerson, String> restored = JsonIo.fromToon(toon, null)
                .asType(new TypeHolder<Map<TestPerson, String>>() {});

        assertTrue(restored.isEmpty(), "Restored map should be empty");
    }

    /**
     * Test class with fields named $key and $value to verify our collision avoidance works.
     * If ToonWriter/ToonReader didn't use $key/$value prefixes, this object's fields
     * would collide with the map entry markers.
     */
    static class ObjectWithDollarFields {
        public String $key;
        public String $value;
        public String normalField;

        public ObjectWithDollarFields() {}

        public ObjectWithDollarFields(String key, String value, String normal) {
            this.$key = key;
            this.$value = value;
            this.normalField = normal;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ObjectWithDollarFields that = (ObjectWithDollarFields) o;
            return java.util.Objects.equals($key, that.$key) &&
                   java.util.Objects.equals($value, that.$value) &&
                   java.util.Objects.equals(normalField, that.normalField);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash($key, $value, normalField);
        }
    }

    @Test
    void testMapWithKeyHavingDollarFields() {
        // Key object has fields named $key and $value - tests collision avoidance
        // The map entry markers ($key/$value) should not collide with the object's fields
        Map<ObjectWithDollarFields, String> original = new LinkedHashMap<>();
        original.put(new ObjectWithDollarFields("keyField1", "valueField1", "normal1"), "mapValue1");
        original.put(new ObjectWithDollarFields("keyField2", "valueField2", "normal2"), "mapValue2");

        String toon = JsonIo.toToon(original, null);

        // The TOON should have nested $key fields - the outer ones are entry markers,
        // the inner ones are the object's actual fields
        assertTrue(toon.contains("$key:"), "Should contain $key entry marker");
        assertTrue(toon.contains("$value:"), "Should contain $value entry marker");

        Map<ObjectWithDollarFields, String> restored = JsonIo.fromToon(toon, null)
                .asType(new TypeHolder<Map<ObjectWithDollarFields, String>>() {});

        assertEquals(2, restored.size());

        // Verify the keys were properly restored with their $key and $value fields
        for (Map.Entry<ObjectWithDollarFields, String> entry : restored.entrySet()) {
            ObjectWithDollarFields key = entry.getKey();
            assertNotNull(key.$key, "Object's $key field should not be null");
            assertNotNull(key.$value, "Object's $value field should not be null");

            if ("keyField1".equals(key.$key)) {
                assertEquals("valueField1", key.$value);
                assertEquals("normal1", key.normalField);
                assertEquals("mapValue1", entry.getValue());
            } else if ("keyField2".equals(key.$key)) {
                assertEquals("valueField2", key.$value);
                assertEquals("normal2", key.normalField);
                assertEquals("mapValue2", entry.getValue());
            }
        }
    }

    @Test
    void testSimpleKeyMapStillUsesSimpleFormat() {
        // Verify that maps with simple keys (String, Integer, etc.) still use
        // the simple key: value format, not the array-of-entries format
        Map<String, TestPerson> original = new LinkedHashMap<>();
        original.put("emp1", new TestPerson("John", 30));
        original.put("emp2", new TestPerson("Jane", 25));

        String toon = JsonIo.toToon(original, null);

        // Simple format should NOT have $key/$value markers
        assertFalse(toon.contains("$key:"), "Simple key map should not use $key marker");
        assertFalse(toon.contains("$value:"), "Simple key map should not use $value marker");

        // Should have the simple format: emp1: followed by nested object
        assertTrue(toon.contains("emp1:"), "Should use simple key format");
        assertTrue(toon.contains("emp2:"), "Should use simple key format");
    }

    @Test
    void testSingleEntryMapWithComplexKey() {
        // Single entry to verify array-of-entries format works for size 1
        Map<TestPerson, String> original = new LinkedHashMap<>();
        original.put(new TestPerson("Solo", 35), "only-entry");

        String toon = JsonIo.toToon(original, null);

        // Should use array format even for single entry
        assertTrue(toon.contains("[1]:"), "Should have [1]: for single entry");
        assertTrue(toon.contains("$key:"), "Should use $key marker");
        assertTrue(toon.contains("$value:"), "Should use $value marker");

        Map<TestPerson, String> restored = JsonIo.fromToon(toon, null)
                .asType(new TypeHolder<Map<TestPerson, String>>() {});

        assertEquals(1, restored.size());
        Map.Entry<TestPerson, String> entry = restored.entrySet().iterator().next();
        assertEquals("Solo", entry.getKey().getName());
        assertEquals(35, entry.getKey().getAge());
        assertEquals("only-entry", entry.getValue());
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

    @Test
    void testUnicodeCharacters() {
        // Test various unicode characters
        roundTrip("café résumé");  // Latin extended
    }

    @Test
    void testChineseCharacters() {
        roundTrip("中文测试");  // Chinese
    }

    @Test
    void testJapaneseCharacters() {
        roundTrip("日本語テスト");  // Japanese
    }

    @Test
    void testEmoji() {
        roundTrip("Hello 👋 World 🌍");  // Emoji (surrogate pairs)
    }

    @Test
    void testMixedUnicode() {
        // Mix of ASCII, Latin extended, CJK, and emoji
        Map<String, String> original = new LinkedHashMap<>();
        original.put("greeting", "Hello 你好 こんにちは 👋");
        original.put("currency", "€100 £50 ¥1000");
        original.put("math", "α + β = γ");

        String toon = JsonIo.toToon(original, null);
        Map<String, String> restored = JsonIo.fromToon(toon, null)
                .asType(new TypeHolder<Map<String, String>>() {});

        assertEquals(original.get("greeting"), restored.get("greeting"));
        assertEquals(original.get("currency"), restored.get("currency"));
        assertEquals(original.get("math"), restored.get("math"));
    }

    @Test
    void testDeeplyNestedStructure() {
        // Stress test with deep nesting
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("value", "leaf");

        // Create 20 levels of nesting
        for (int i = 0; i < 20; i++) {
            Map<String, Object> parent = new LinkedHashMap<>();
            parent.put("level", i);
            parent.put("child", current);
            current = parent;
        }

        String toon = JsonIo.toToon(current, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> restored = JsonIo.fromToon(toon, null).asClass(Map.class);

        // Navigate to the deepest level
        Map<String, Object> node = restored;
        for (int i = 19; i >= 0; i--) {
            assertEquals((long) i, node.get("level"));
            if (i > 0) {
                node = (Map<String, Object>) node.get("child");
            }
        }
        // Check the leaf
        Map<String, Object> leaf = (Map<String, Object>) node.get("child");
        assertEquals("leaf", leaf.get("value"));
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
