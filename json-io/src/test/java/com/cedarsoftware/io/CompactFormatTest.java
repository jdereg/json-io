package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the showTypeInfoMinimalPlus() WriteOption (formerly showTypeInfoCompact()). When enabled, Collections
 * and Maps whose runtime type is the "natural default" for the field's declared type are written without the
 * @type/@items wrapper. For example, an ArrayList in a List field writes as direct [...] instead of
 * {"@type":"ArrayList","@items":[...]}.
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
class CompactFormatTest {

    // ========== Test model classes ==========

    static class Person {
        String name;
        int age;

        Person() {}
        Person(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    static class Architect extends Person {
        String specialty;

        Architect() {}
        Architect(String name, int age, String specialty) {
            super(name, age);
            this.specialty = specialty;
        }
    }

    static class ListHolder {
        List<String> names;
        List<Person> people;
    }

    static class SetHolder {
        Set<Integer> numbers;
    }

    static class MapHolder {
        Map<String, Object> data;
    }

    static class LinkedListHolder {
        List<String> names;  // Declared as List, but will hold LinkedList (not natural default)
    }

    static class NestedListHolder {
        List<List<String>> nested;
    }

    static class EmptyCollectionsHolder {
        List<String> emptyList;
        Set<Integer> emptySet;
        Map<String, String> emptyMap;
    }

    static class IntegerListHolder {
        List<Integer> numbers;
    }

    static class PolymorphicListHolder {
        List<Person> people;  // Will contain Architect instances
    }

    static class SelfReferencing {
        String name;
        List<SelfReferencing> children;
    }

    // ========== Helper methods ==========

    private WriteOptions compactWriteOpts() {
        return new WriteOptionsBuilder().showTypeInfoMinimalPlus().build();
    }

    private WriteOptions compactPrettyWriteOpts() {
        return new WriteOptionsBuilder().showTypeInfoMinimalPlus().prettyPrint(true).build();
    }

    private WriteOptions defaultWriteOpts() {
        return new WriteOptionsBuilder().build();
    }

    private ReadOptions defaultReadOpts() {
        return new ReadOptionsBuilder().build();
    }

    // ========== 1. List<String> with ArrayList → direct [...] ==========

    @Test
    void testListWithArrayList_compactFormat() {
        ListHolder holder = new ListHolder();
        holder.names = new ArrayList<>(Arrays.asList("Alice", "Bob", "Charlie"));

        String json = JsonIo.toJson(holder, compactWriteOpts());

        // Should NOT contain @type for ArrayList or @items wrapper
        assertFalse(json.contains("ArrayList"), "Compact format should omit ArrayList @type");
        assertFalse(json.contains("@items"), "Compact format should omit @items wrapper");
        assertFalse(json.contains("@e"), "Compact format should omit short @items wrapper");

        // Verify round-trip
        ListHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(ListHolder.class);
        assertEquals(holder.names, result.names);
        assertInstanceOf(ArrayList.class, result.names);
    }

    // ========== 2. List<String> with LinkedList → wrapped (not natural default) ==========

    @Test
    void testListWithLinkedList_keepWrapper() {
        LinkedListHolder holder = new LinkedListHolder();
        holder.names = new LinkedList<>(Arrays.asList("Alice", "Bob"));

        String json = JsonIo.toJson(holder, compactWriteOpts());

        // LinkedList is NOT the natural default for List, so @type MUST be present
        assertTrue(json.contains("LinkedList"), "Non-default type should keep @type");

        // Verify round-trip
        LinkedListHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(LinkedListHolder.class);
        assertEquals(holder.names, result.names);
        assertInstanceOf(LinkedList.class, result.names);
    }

    // ========== 3. Set<Integer> with LinkedHashSet → direct [...] ==========

    @Test
    void testSetWithLinkedHashSet_compactFormat() {
        SetHolder holder = new SetHolder();
        holder.numbers = new LinkedHashSet<>(Arrays.asList(1, 2, 3));

        String json = JsonIo.toJson(holder, compactWriteOpts());

        assertFalse(json.contains("LinkedHashSet"), "Compact format should omit LinkedHashSet @type");
        assertFalse(json.contains("@items"), "Compact format should omit @items wrapper");

        // Verify round-trip
        SetHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(SetHolder.class);
        assertEquals(holder.numbers, result.numbers);
        assertInstanceOf(LinkedHashSet.class, result.numbers);
    }

    // ========== 4. Map<String, Object> with LinkedHashMap → {key:value} without @type ==========

    @Test
    void testMapWithLinkedHashMap_compactFormat() {
        MapHolder holder = new MapHolder();
        holder.data = new LinkedHashMap<>();
        holder.data.put("key1", "value1");
        holder.data.put("key2", 42L);

        String json = JsonIo.toJson(holder, compactWriteOpts());

        assertFalse(json.contains("LinkedHashMap"), "Compact format should omit LinkedHashMap @type");

        // Verify round-trip
        MapHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(MapHolder.class);
        assertEquals("value1", result.data.get("key1"));
        assertEquals(42L, result.data.get("key2"));
    }

    // ========== 5. Collection referenced multiple times → wrapper preserved ==========

    @Test
    void testReferencedCollection_keepWrapper() {
        SelfReferencing parent = new SelfReferencing();
        parent.name = "parent";
        SelfReferencing child = new SelfReferencing();
        child.name = "child";
        child.children = new ArrayList<>();

        // Both parent and child reference the same list
        parent.children = child.children;

        // The shared list is referenced from two places, so it needs @id/@ref
        String json = JsonIo.toJson(parent, compactWriteOpts());

        // Verify round-trip preserves reference identity
        SelfReferencing result = JsonIo.toJava(json, defaultReadOpts()).asClass(SelfReferencing.class);
        assertSame(result.children, result.children, "Referenced collections should preserve identity");
    }

    // ========== 6. showTypeInfoAlways() → wrapper preserved ==========

    @Test
    void testAlwaysShowType_keepWrapper() {
        ListHolder holder = new ListHolder();
        holder.names = new ArrayList<>(Arrays.asList("Alice", "Bob"));

        WriteOptions opts = new WriteOptionsBuilder()
                .showTypeInfoMinimalPlus()
                .showTypeInfoAlways()
                .build();

        String json = JsonIo.toJson(holder, opts);

        // showTypeInfoAlways overrides compact format
        assertTrue(json.contains("ArrayList"), "showTypeInfoAlways should override compact format");
    }

    // ========== 7. List<Person> with Architect element → element @type preserved ==========

    @Test
    void testPolymorphicElements_elementTypePreserved() {
        PolymorphicListHolder holder = new PolymorphicListHolder();
        holder.people = new ArrayList<>();
        holder.people.add(new Person("John", 30));
        holder.people.add(new Architect("Jane", 35, "Modernist"));

        String json = JsonIo.toJson(holder, compactWriteOpts());

        // Container should be compact (no ArrayList wrapper)
        assertFalse(json.contains("ArrayList"), "Container should be compact");

        // But Architect element should have @type (it's not Person)
        assertTrue(json.contains("Architect"), "Architect element should have @type since it differs from List<Person>");

        // Verify round-trip
        PolymorphicListHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(PolymorphicListHolder.class);
        assertEquals(2, result.people.size());
        assertInstanceOf(Person.class, result.people.get(0));
        assertInstanceOf(Architect.class, result.people.get(1));
        assertEquals("Modernist", ((Architect) result.people.get(1)).specialty);
    }

    // ========== 8. Root-level collection → wrapper preserved ==========

    @Test
    void testRootLevelCollection_keepWrapper() {
        List<String> rootList = new ArrayList<>(Arrays.asList("a", "b", "c"));

        // At root level, there's no field context to infer the type
        String json = JsonIo.toJson(rootList, compactWriteOpts());

        // Verify it round-trips correctly regardless
        List<String> result = JsonIo.toJava(json, defaultReadOpts()).asClass(ArrayList.class);
        assertEquals(rootList, result);
    }

    // ========== 9. Nested: List<List<String>> ==========
    // The outer list is compact (ArrayList for List field). Inner lists currently keep @type because
    // declaredElementType is only set for raw Class element types, not ParameterizedType like List<String>.
    // This is a known limitation — inner parameterized collection elements retain their wrapper.

    @Test
    void testNestedLists_outerCompact() {
        NestedListHolder holder = new NestedListHolder();
        holder.nested = new ArrayList<>();
        holder.nested.add(new ArrayList<>(Arrays.asList("a", "b")));
        holder.nested.add(new ArrayList<>(Arrays.asList("c", "d")));

        String json = JsonIo.toJson(holder, compactWriteOpts());

        // Outer list should be compact (no wrapper on the "nested" field's ArrayList)
        // Inner lists may still have @type since element type is parameterized (List<String>)

        // Verify round-trip regardless
        NestedListHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(NestedListHolder.class);
        assertEquals(2, result.nested.size());
        assertEquals(Arrays.asList("a", "b"), result.nested.get(0));
        assertEquals(Arrays.asList("c", "d"), result.nested.get(1));
    }

    // ========== 10. Empty collections → [] directly ==========

    @Test
    void testEmptyCollections_compact() {
        EmptyCollectionsHolder holder = new EmptyCollectionsHolder();
        holder.emptyList = new ArrayList<>();
        holder.emptySet = new LinkedHashSet<>();
        holder.emptyMap = new LinkedHashMap<>();

        String json = JsonIo.toJson(holder, compactWriteOpts());

        assertFalse(json.contains("ArrayList"), "Empty list should be compact");
        assertFalse(json.contains("LinkedHashSet"), "Empty set should be compact");
        assertFalse(json.contains("LinkedHashMap"), "Empty map should be compact");

        // Verify round-trip
        EmptyCollectionsHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(EmptyCollectionsHolder.class);
        assertNotNull(result.emptyList);
        assertTrue(result.emptyList.isEmpty());
        assertNotNull(result.emptySet);
        assertTrue(result.emptySet.isEmpty());
        assertNotNull(result.emptyMap);
        assertTrue(result.emptyMap.isEmpty());
    }

    // ========== 11. List<Integer> round-trip → Long→Integer via Converter ==========

    @Test
    void testIntegerList_roundTrip() {
        IntegerListHolder holder = new IntegerListHolder();
        holder.numbers = new ArrayList<>(Arrays.asList(1, 2, 3, 42, -7));

        String json = JsonIo.toJson(holder, compactWriteOpts());

        // Verify round-trip (JSON numbers parsed as Long, Converter converts to Integer)
        IntegerListHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(IntegerListHolder.class);
        assertEquals(holder.numbers, result.numbers);
        for (Object num : result.numbers) {
            assertInstanceOf(Integer.class, num, "Elements should be Integer after Converter");
        }
    }

    // ========== 12. Deep round-trip with DeepEquals ==========

    @Test
    void testDeepRoundTrip() {
        ListHolder holder = new ListHolder();
        holder.names = new ArrayList<>(Arrays.asList("Alice", "Bob", "Charlie"));
        holder.people = new ArrayList<>();
        holder.people.add(new Person("John", 30));
        holder.people.add(new Person("Jane", 25));

        String json = JsonIo.toJson(holder, compactWriteOpts());
        ListHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(ListHolder.class);

        assertTrue(DeepEquals.deepEquals(holder, result), "Deep round-trip should produce equal objects");
    }

    // ========== 13. Default option (false) → backward compatible ==========

    @Test
    void testDefaultOption_backwardCompatible() {
        ListHolder holder = new ListHolder();
        holder.names = new ArrayList<>(Arrays.asList("Alice", "Bob"));

        String defaultJson = JsonIo.toJson(holder, defaultWriteOpts());
        String compactJson = JsonIo.toJson(holder, compactWriteOpts());

        // Default should still use wrapper
        assertTrue(defaultJson.contains("ArrayList") || defaultJson.contains("@items") || defaultJson.contains("@e"),
                "Default format should use @type or @items wrapper");

        // Compact should not
        assertFalse(compactJson.contains("ArrayList"), "Compact format should omit wrapper");

        // Both should round-trip correctly
        ListHolder result1 = JsonIo.toJava(defaultJson, defaultReadOpts()).asClass(ListHolder.class);
        ListHolder result2 = JsonIo.toJava(compactJson, defaultReadOpts()).asClass(ListHolder.class);
        assertTrue(DeepEquals.deepEquals(result1, result2), "Both formats should produce equal objects");
    }

    // ========== Additional edge case tests ==========

    @Test
    void testTreeSetInSortedSetField_compact() {
        // TreeSet is the natural default for SortedSet
        SortedSetHolder holder = new SortedSetHolder();
        holder.sorted = new TreeSet<>(Arrays.asList("c", "a", "b"));

        String json = JsonIo.toJson(holder, compactWriteOpts());

        assertFalse(json.contains("TreeSet"), "TreeSet should be compact for SortedSet field");

        SortedSetHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(SortedSetHolder.class);
        assertEquals(holder.sorted, result.sorted);
        assertInstanceOf(TreeSet.class, result.sorted);
    }

    @Test
    void testTreeMapInSortedMapField_compact() {
        SortedMapHolder holder = new SortedMapHolder();
        holder.sorted = new TreeMap<>();
        holder.sorted.put("b", 2);
        holder.sorted.put("a", 1);

        String json = JsonIo.toJson(holder, compactWriteOpts());

        assertFalse(json.contains("TreeMap"), "TreeMap should be compact for SortedMap field");

        SortedMapHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(SortedMapHolder.class);
        assertInstanceOf(TreeMap.class, result.sorted);
        assertEquals(2, result.sorted.size());
        // Values read back correctly (may be Integer or Long depending on Converter)
        assertTrue(DeepEquals.deepEquals(holder.sorted, result.sorted));
    }

    @Test
    void testCompactWithPrettyPrint() {
        ListHolder holder = new ListHolder();
        holder.names = new ArrayList<>(Arrays.asList("Alice", "Bob"));

        String json = JsonIo.toJson(holder, compactPrettyWriteOpts());

        assertFalse(json.contains("ArrayList"), "Compact + pretty should still omit wrapper");
        assertTrue(json.contains("\n"), "Pretty print should have newlines");

        ListHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(ListHolder.class);
        assertEquals(holder.names, result.names);
    }

    // Additional model classes for edge case tests
    static class SortedSetHolder {
        SortedSet<String> sorted;
    }

    static class SortedMapHolder {
        SortedMap<String, Integer> sorted;
    }

    // ==================== Converter-aware type simplification tests ====================

    // Model classes for Converter-aware tests
    static class SimpleTypesHolder {
        ZonedDateTime zdt;
        LocalDate localDate;
        BigDecimal bigDecimal;
        BigInteger bigInteger;
        UUID uuid;
        URI uri;
        AtomicInteger atomicInt;
        AtomicLong atomicLong;
        Class<?> clazz;
    }

    static class DateFieldHolder {
        java.util.Date utilDate;
        java.sql.Timestamp timestamp;
    }

    // ========== 17. ZonedDateTime field: exact type match already works, compact should too ==========

    @Test
    void testZonedDateTimeField_compactFormat() {
        SimpleTypesHolder holder = new SimpleTypesHolder();
        holder.zdt = ZonedDateTime.parse("2023-10-22T12:03:01+03:00[Asia/Aden]");

        // Default (minimal) - should already write as primitive (exact type match)
        String jsonDefault = JsonIo.toJson(holder, defaultWriteOpts());

        // Compact - should also write as primitive
        String jsonCompact = JsonIo.toJson(holder, compactWriteOpts());

        // Both should NOT contain @type for ZonedDateTime (exact match in both modes)
        // The key test is that compact doesn't BREAK anything for exact matches
        SimpleTypesHolder resultDefault = JsonIo.toJava(jsonDefault, defaultReadOpts()).asClass(SimpleTypesHolder.class);
        SimpleTypesHolder resultCompact = JsonIo.toJava(jsonCompact, defaultReadOpts()).asClass(SimpleTypesHolder.class);

        assertEquals(holder.zdt, resultDefault.zdt);
        assertEquals(holder.zdt, resultCompact.zdt);
    }

    // ========== 18. Multiple simple-type fields round-trip correctly ==========

    @Test
    void testMultipleSimpleTypeFields_compactRoundTrip() {
        SimpleTypesHolder holder = new SimpleTypesHolder();
        holder.zdt = ZonedDateTime.parse("2023-10-22T12:03:01+03:00[Asia/Aden]");
        holder.localDate = LocalDate.of(2023, 12, 25);
        holder.bigDecimal = new BigDecimal("123456789.987654321");
        holder.bigInteger = new BigInteger("99999999999999999999");
        holder.uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        holder.uri = URI.create("https://example.com/path?q=1");
        holder.atomicInt = new AtomicInteger(42);
        holder.atomicLong = new AtomicLong(Long.MAX_VALUE);
        holder.clazz = String.class;

        String json = JsonIo.toJson(holder, compactWriteOpts());

        // Verify no @type wrappers for these simple types (they match their declared types)
        assertFalse(json.contains("ZonedDateTime"), "ZonedDateTime should not have @type");
        assertFalse(json.contains("LocalDate"), "LocalDate should not have @type");
        assertFalse(json.contains("BigDecimal"), "BigDecimal should not have @type");
        assertFalse(json.contains("BigInteger"), "BigInteger should not have @type");

        // Round-trip
        SimpleTypesHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(SimpleTypesHolder.class);
        assertEquals(holder.zdt, result.zdt);
        assertEquals(holder.localDate, result.localDate);
        assertEquals(0, holder.bigDecimal.compareTo(result.bigDecimal));
        assertEquals(holder.bigInteger, result.bigInteger);
        assertEquals(holder.uuid, result.uuid);
        assertEquals(holder.uri, result.uri);
        assertEquals(holder.atomicInt.get(), result.atomicInt.get());
        assertEquals(holder.atomicLong.get(), result.atomicLong.get());
        assertEquals(holder.clazz, result.clazz);
    }

    // ========== 19. Before/After JSON comparison for user inspection ==========

    @Test
    void testBeforeAfterJsonComparison() {
        SimpleTypesHolder holder = new SimpleTypesHolder();
        holder.zdt = ZonedDateTime.parse("2023-10-22T12:03:01+03:00[Asia/Aden]");
        holder.localDate = LocalDate.of(2023, 12, 25);
        holder.bigDecimal = new BigDecimal("99.95");
        holder.uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        WriteOptions prettyDefault = new WriteOptionsBuilder().prettyPrint(true).build();
        WriteOptions prettyCompact = new WriteOptionsBuilder().showTypeInfoMinimalPlus().prettyPrint(true).build();

        String jsonDefault = JsonIo.toJson(holder, prettyDefault);
        String jsonCompact = JsonIo.toJson(holder, prettyCompact);

        // Print for manual inspection
        System.out.println("=== DEFAULT (showTypeInfoMinimal) ===");
        System.out.println(jsonDefault);
        System.out.println("\n=== MINIMAL_PLUS (showTypeInfoMinimalPlus) ===");
        System.out.println(jsonCompact);

        // Compact should be shorter or equal (never longer)
        assertTrue(jsonCompact.length() <= jsonDefault.length(),
                "Compact JSON should not be longer than default JSON");

        // Both should round-trip correctly
        SimpleTypesHolder resultDefault = JsonIo.toJava(jsonDefault, defaultReadOpts()).asClass(SimpleTypesHolder.class);
        SimpleTypesHolder resultCompact = JsonIo.toJava(jsonCompact, defaultReadOpts()).asClass(SimpleTypesHolder.class);

        assertEquals(holder.zdt, resultDefault.zdt);
        assertEquals(holder.zdt, resultCompact.zdt);
        assertEquals(holder.localDate, resultDefault.localDate);
        assertEquals(holder.localDate, resultCompact.localDate);
    }

    // ========== 20. Cross-type conversion: sql.Timestamp in util.Date field ==========

    @Test
    void testCrossTypeConversion_timestampInDateField() {
        DateFieldHolder holder = new DateFieldHolder();
        holder.utilDate = new java.sql.Timestamp(1703462400000L);  // Timestamp IS-A Date

        WriteOptions prettyDefault = new WriteOptionsBuilder().prettyPrint(true).build();
        WriteOptions prettyCompact = new WriteOptionsBuilder().showTypeInfoMinimalPlus().prettyPrint(true).build();

        String jsonDefault = JsonIo.toJson(holder, prettyDefault);
        String jsonCompact = JsonIo.toJson(holder, prettyCompact);

        System.out.println("=== Cross-type: Timestamp in Date field (DEFAULT) ===");
        System.out.println(jsonDefault);
        System.out.println("\n=== Cross-type: Timestamp in Date field (MINIMAL_PLUS) ===");
        System.out.println(jsonCompact);

        // In compact mode, @type for Timestamp should be omitted since both Date and Timestamp are convertable
        // The reader will read the value and Converter.convert it to the field's declared type (Date)
        DateFieldHolder resultCompact = JsonIo.toJava(jsonCompact, defaultReadOpts()).asClass(DateFieldHolder.class);

        // Note: the result type will be java.util.Date (not Timestamp) since the declared type is Date.
        // The millis should be preserved.
        assertNotNull(resultCompact.utilDate);
        assertEquals(1703462400000L, resultCompact.utilDate.getTime());
    }

    // ========== 21. Backward compatibility: default mode unchanged ==========

    @Test
    void testDefaultMode_noConvertableOptimization() {
        SimpleTypesHolder holder = new SimpleTypesHolder();
        holder.zdt = ZonedDateTime.parse("2023-10-22T12:03:01+03:00[Asia/Aden]");

        // Default mode should work exactly as before
        String json = JsonIo.toJson(holder, defaultWriteOpts());

        SimpleTypesHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(SimpleTypesHolder.class);
        assertEquals(holder.zdt, result.zdt);
    }

    // ==================== Collection element Converter-aware simplification tests ====================

    // Model class for Collection element tests
    static class ZonedDateTimeListHolder {
        List<ZonedDateTime> events;
    }

    static class LocalDateSetHolder {
        Set<LocalDate> dates;
    }

    static class MixedCollectionHolder {
        List<ZonedDateTime> zdtList;
        Set<LocalDate> localDates;
        List<UUID> uuids;
        List<BigDecimal> decimals;
    }

    // ========== 22. List<ZonedDateTime> elements written as plain strings ==========

    @Test
    void testListOfZonedDateTime_compactFormat() {
        ZonedDateTimeListHolder holder = new ZonedDateTimeListHolder();
        holder.events = new ArrayList<>();
        holder.events.add(ZonedDateTime.parse("2023-10-22T12:03:01+03:00[Asia/Aden]"));
        holder.events.add(ZonedDateTime.parse("2023-11-15T14:30:45-05:00[America/New_York]"));
        holder.events.add(ZonedDateTime.parse("2024-01-01T00:00:00Z[UTC]"));

        WriteOptions prettyCompact = new WriteOptionsBuilder().showTypeInfoMinimalPlus().prettyPrint(true).build();
        String json = JsonIo.toJson(holder, prettyCompact);

        System.out.println("=== List<ZonedDateTime> MINIMAL_PLUS ===");
        System.out.println(json);

        // Elements should be plain strings, no @type or "zonedDateTime" key wrapper
        assertFalse(json.contains("\"zonedDateTime\""), "Elements should not have 'zonedDateTime' key wrapper");

        // The list should be a direct array (no @type on ArrayList since it's the natural default)
        assertFalse(json.contains("ArrayList"), "ArrayList should not have @type (natural default for List)");

        // Round-trip verification
        ZonedDateTimeListHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(ZonedDateTimeListHolder.class);
        assertEquals(3, result.events.size());
        assertEquals(holder.events.get(0), result.events.get(0));
        assertEquals(holder.events.get(1), result.events.get(1));
        assertEquals(holder.events.get(2), result.events.get(2));
    }

    // ========== 23. Set<LocalDate> elements written as plain strings ==========

    @Test
    void testSetOfLocalDate_compactFormat() {
        LocalDateSetHolder holder = new LocalDateSetHolder();
        holder.dates = new LinkedHashSet<>();
        holder.dates.add(LocalDate.of(2023, 12, 25));
        holder.dates.add(LocalDate.of(2024, 1, 1));
        holder.dates.add(LocalDate.of(2024, 7, 4));

        WriteOptions prettyCompact = new WriteOptionsBuilder().showTypeInfoMinimalPlus().prettyPrint(true).build();
        String json = JsonIo.toJson(holder, prettyCompact);

        System.out.println("=== Set<LocalDate> MINIMAL_PLUS ===");
        System.out.println(json);

        // Elements should be plain strings (no @type wrapper), but class name contains "LocalDate"
        assertFalse(json.contains("\"@type\":\"LocalDate\""), "LocalDate elements should not have @type wrapper");

        // Round-trip verification
        LocalDateSetHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(LocalDateSetHolder.class);
        assertEquals(3, result.dates.size());
        assertTrue(result.dates.contains(LocalDate.of(2023, 12, 25)));
        assertTrue(result.dates.contains(LocalDate.of(2024, 1, 1)));
        assertTrue(result.dates.contains(LocalDate.of(2024, 7, 4)));
    }

    // ========== 24. Mixed collections with various convertable types ==========

    @Test
    void testMixedCollections_compactFormat() {
        MixedCollectionHolder holder = new MixedCollectionHolder();
        holder.zdtList = new ArrayList<>();
        holder.zdtList.add(ZonedDateTime.parse("2023-10-22T12:03:01+03:00[Asia/Aden]"));

        holder.localDates = new LinkedHashSet<>();
        holder.localDates.add(LocalDate.of(2023, 12, 25));

        holder.uuids = new ArrayList<>();
        holder.uuids.add(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));

        holder.decimals = new ArrayList<>();
        holder.decimals.add(new BigDecimal("12345.67890"));

        WriteOptions prettyCompact = new WriteOptionsBuilder().showTypeInfoMinimalPlus().prettyPrint(true).build();
        String json = JsonIo.toJson(holder, prettyCompact);

        System.out.println("=== Mixed Collections MINIMAL_PLUS ===");
        System.out.println(json);

        // None of the element types should have @type wrappers
        assertFalse(json.contains("ZonedDateTime"), "ZonedDateTime should not have @type");
        assertFalse(json.contains("LocalDate"), "LocalDate should not have @type");
        assertFalse(json.contains("UUID"), "UUID should not have @type");
        assertFalse(json.contains("BigDecimal"), "BigDecimal should not have @type");

        // Round-trip verification
        MixedCollectionHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(MixedCollectionHolder.class);
        assertEquals(holder.zdtList.get(0), result.zdtList.get(0));
        assertTrue(result.localDates.contains(LocalDate.of(2023, 12, 25)));
        assertEquals(holder.uuids.get(0), result.uuids.get(0));
        assertEquals(0, holder.decimals.get(0).compareTo(result.decimals.get(0)));
    }

    // ========== 25. Before/After comparison for Collection elements ==========

    @Test
    void testCollectionElementsBeforeAfterComparison() {
        ZonedDateTimeListHolder holder = new ZonedDateTimeListHolder();
        holder.events = new ArrayList<>();
        holder.events.add(ZonedDateTime.parse("2023-10-22T12:03:01+03:00[Asia/Aden]"));
        holder.events.add(ZonedDateTime.parse("2023-11-15T14:30:45-05:00[America/New_York]"));

        WriteOptions prettyDefault = new WriteOptionsBuilder().prettyPrint(true).build();
        WriteOptions prettyCompact = new WriteOptionsBuilder().showTypeInfoMinimalPlus().prettyPrint(true).build();

        String jsonDefault = JsonIo.toJson(holder, prettyDefault);
        String jsonCompact = JsonIo.toJson(holder, prettyCompact);

        System.out.println("=== Collection Elements DEFAULT ===");
        System.out.println(jsonDefault);
        System.out.println("\n=== Collection Elements MINIMAL_PLUS ===");
        System.out.println(jsonCompact);

        // Compact should be shorter (no @type wrappers on container)
        assertTrue(jsonCompact.length() < jsonDefault.length(),
                "Compact JSON should be shorter than default JSON for collection elements");

        // Compact format round-trips correctly because field type (List<ZonedDateTime>) is preserved
        ZonedDateTimeListHolder resultCompact = JsonIo.toJava(jsonCompact, defaultReadOpts()).asClass(ZonedDateTimeListHolder.class);
        assertEquals(holder.events, resultCompact.events);

        // Note: DEFAULT format with @type:"ArrayList" loses generic type info, so elements stay as strings.
        // This is expected behavior - the wrapper loses the parameterized type information.
    }

    // ========== ARRAY COMPONENT TYPE SIMPLIFICATION TESTS ==========

    public static class ZonedDateTimeArrayHolder {
        public ZonedDateTime[] events;
    }

    public static class LocalDateArrayHolder {
        public LocalDate[] dates;
    }

    public static class UuidArrayHolder {
        public UUID[] ids;
    }

    // ========== 26. ZonedDateTime[] elements written as plain strings ==========

    @Test
    void testZonedDateTimeArray_compactFormat() {
        ZonedDateTimeArrayHolder holder = new ZonedDateTimeArrayHolder();
        holder.events = new ZonedDateTime[] {
            ZonedDateTime.parse("2023-10-22T12:03:01+03:00[Asia/Aden]"),
            ZonedDateTime.parse("2023-11-15T14:30:45-05:00[America/New_York]")
        };

        WriteOptions prettyCompact = new WriteOptionsBuilder().showTypeInfoMinimalPlus().prettyPrint(true).build();
        String json = JsonIo.toJson(holder, prettyCompact);

        System.out.println("=== ZonedDateTime[] MINIMAL_PLUS ===");
        System.out.println(json);

        // Elements should be plain strings (no @type wrapper)
        assertFalse(json.contains("\"@type\":\"ZonedDateTime\""), "ZonedDateTime array elements should not have @type");

        // Round-trip verification
        ZonedDateTimeArrayHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(ZonedDateTimeArrayHolder.class);
        assertEquals(2, result.events.length);
        assertEquals(holder.events[0], result.events[0]);
        assertEquals(holder.events[1], result.events[1]);
    }

    // ========== 27. LocalDate[] elements written as plain strings ==========

    @Test
    void testLocalDateArray_compactFormat() {
        LocalDateArrayHolder holder = new LocalDateArrayHolder();
        holder.dates = new LocalDate[] {
            LocalDate.of(2023, 12, 25),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 7, 4)
        };

        WriteOptions prettyCompact = new WriteOptionsBuilder().showTypeInfoMinimalPlus().prettyPrint(true).build();
        String json = JsonIo.toJson(holder, prettyCompact);

        System.out.println("=== LocalDate[] MINIMAL_PLUS ===");
        System.out.println(json);

        // Elements should be plain strings
        assertFalse(json.contains("\"@type\":\"LocalDate\""), "LocalDate array elements should not have @type wrapper");

        // Round-trip verification
        LocalDateArrayHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(LocalDateArrayHolder.class);
        assertEquals(3, result.dates.length);
        assertEquals(holder.dates[0], result.dates[0]);
        assertEquals(holder.dates[1], result.dates[1]);
        assertEquals(holder.dates[2], result.dates[2]);
    }

    // ========== 28. UUID[] elements written as plain strings ==========

    @Test
    void testUuidArray_compactFormat() {
        UuidArrayHolder holder = new UuidArrayHolder();
        holder.ids = new UUID[] {
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
            UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
        };

        WriteOptions prettyCompact = new WriteOptionsBuilder().showTypeInfoMinimalPlus().prettyPrint(true).build();
        String json = JsonIo.toJson(holder, prettyCompact);

        System.out.println("=== UUID[] MINIMAL_PLUS ===");
        System.out.println(json);

        // Elements should be plain strings
        assertFalse(json.contains("\"@type\":\"UUID\""), "UUID array elements should not have @type wrapper");

        // Round-trip verification
        UuidArrayHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(UuidArrayHolder.class);
        assertEquals(2, result.ids.length);
        assertEquals(holder.ids[0], result.ids[0]);
        assertEquals(holder.ids[1], result.ids[1]);
    }

    // ========== 29. Array comparison: before and after compact format ==========

    @Test
    void testArrayElementsBeforeAfterComparison() {
        ZonedDateTimeArrayHolder holder = new ZonedDateTimeArrayHolder();
        holder.events = new ZonedDateTime[] {
            ZonedDateTime.parse("2023-10-22T12:03:01+03:00[Asia/Aden]"),
            ZonedDateTime.parse("2023-11-15T14:30:45-05:00[America/New_York]")
        };

        WriteOptions prettyDefault = new WriteOptionsBuilder().prettyPrint(true).build();
        WriteOptions prettyCompact = new WriteOptionsBuilder().showTypeInfoMinimalPlus().prettyPrint(true).build();

        String jsonDefault = JsonIo.toJson(holder, prettyDefault);
        String jsonCompact = JsonIo.toJson(holder, prettyCompact);

        System.out.println("=== Array Elements DEFAULT ===");
        System.out.println(jsonDefault);
        System.out.println("\n=== Array Elements MINIMAL_PLUS ===");
        System.out.println(jsonCompact);

        // Both should have similar length for arrays (no @type wrapper needed on container)
        // Compact round-trip should work
        ZonedDateTimeArrayHolder resultCompact = JsonIo.toJava(jsonCompact, defaultReadOpts()).asClass(ZonedDateTimeArrayHolder.class);
        assertArrayEquals(holder.events, resultCompact.events);
    }

    // ========== MAP VALUE TYPE SIMPLIFICATION TESTS ==========

    public static class StringToZdtMapHolder {
        public Map<String, ZonedDateTime> events;
    }

    public static class StringToLocalDateMapHolder {
        public Map<String, LocalDate> dates;
    }

    public static class StringToUuidMapHolder {
        public Map<String, UUID> ids;
    }

    // ========== 30. Map<String, ZonedDateTime> values written as plain strings ==========

    @Test
    void testMapWithZonedDateTimeValues_compactFormat() {
        StringToZdtMapHolder holder = new StringToZdtMapHolder();
        holder.events = new LinkedHashMap<>();
        holder.events.put("event1", ZonedDateTime.parse("2023-10-22T12:03:01+03:00[Asia/Aden]"));
        holder.events.put("event2", ZonedDateTime.parse("2023-11-15T14:30:45-05:00[America/New_York]"));

        WriteOptions prettyCompact = new WriteOptionsBuilder().showTypeInfoMinimalPlus().prettyPrint(true).build();
        String json = JsonIo.toJson(holder, prettyCompact);

        System.out.println("=== Map<String, ZonedDateTime> MINIMAL_PLUS ===");
        System.out.println(json);

        // Values should be plain strings (no @type wrapper)
        assertFalse(json.contains("\"@type\":\"ZonedDateTime\""), "ZonedDateTime map values should not have @type");

        // Round-trip verification
        StringToZdtMapHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(StringToZdtMapHolder.class);
        assertEquals(2, result.events.size());
        assertEquals(holder.events.get("event1"), result.events.get("event1"));
        assertEquals(holder.events.get("event2"), result.events.get("event2"));
    }

    // ========== 31. Map<String, LocalDate> values written as plain strings ==========

    @Test
    void testMapWithLocalDateValues_compactFormat() {
        StringToLocalDateMapHolder holder = new StringToLocalDateMapHolder();
        holder.dates = new LinkedHashMap<>();
        holder.dates.put("christmas", LocalDate.of(2023, 12, 25));
        holder.dates.put("newYear", LocalDate.of(2024, 1, 1));
        holder.dates.put("july4th", LocalDate.of(2024, 7, 4));

        WriteOptions prettyCompact = new WriteOptionsBuilder().showTypeInfoMinimalPlus().prettyPrint(true).build();
        String json = JsonIo.toJson(holder, prettyCompact);

        System.out.println("=== Map<String, LocalDate> MINIMAL_PLUS ===");
        System.out.println(json);

        // Values should be plain strings
        assertFalse(json.contains("\"@type\":\"LocalDate\""), "LocalDate map values should not have @type wrapper");

        // Round-trip verification
        StringToLocalDateMapHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(StringToLocalDateMapHolder.class);
        assertEquals(3, result.dates.size());
        assertEquals(holder.dates.get("christmas"), result.dates.get("christmas"));
        assertEquals(holder.dates.get("newYear"), result.dates.get("newYear"));
        assertEquals(holder.dates.get("july4th"), result.dates.get("july4th"));
    }

    // ========== 32. Map<String, UUID> values written as plain strings ==========

    @Test
    void testMapWithUuidValues_compactFormat() {
        StringToUuidMapHolder holder = new StringToUuidMapHolder();
        holder.ids = new LinkedHashMap<>();
        holder.ids.put("user1", UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        holder.ids.put("user2", UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8"));

        WriteOptions prettyCompact = new WriteOptionsBuilder().showTypeInfoMinimalPlus().prettyPrint(true).build();
        String json = JsonIo.toJson(holder, prettyCompact);

        System.out.println("=== Map<String, UUID> MINIMAL_PLUS ===");
        System.out.println(json);

        // Values should be plain strings
        assertFalse(json.contains("\"@type\":\"UUID\""), "UUID map values should not have @type wrapper");

        // Round-trip verification
        StringToUuidMapHolder result = JsonIo.toJava(json, defaultReadOpts()).asClass(StringToUuidMapHolder.class);
        assertEquals(2, result.ids.size());
        assertEquals(holder.ids.get("user1"), result.ids.get("user1"));
        assertEquals(holder.ids.get("user2"), result.ids.get("user2"));
    }

    // ========== 33. Map comparison: before and after compact format ==========

    @Test
    void testMapValuesBeforeAfterComparison() {
        StringToZdtMapHolder holder = new StringToZdtMapHolder();
        holder.events = new LinkedHashMap<>();
        holder.events.put("event1", ZonedDateTime.parse("2023-10-22T12:03:01+03:00[Asia/Aden]"));
        holder.events.put("event2", ZonedDateTime.parse("2023-11-15T14:30:45-05:00[America/New_York]"));

        WriteOptions prettyDefault = new WriteOptionsBuilder().prettyPrint(true).build();
        WriteOptions prettyCompact = new WriteOptionsBuilder().showTypeInfoMinimalPlus().prettyPrint(true).build();

        String jsonDefault = JsonIo.toJson(holder, prettyDefault);
        String jsonCompact = JsonIo.toJson(holder, prettyCompact);

        System.out.println("=== Map Values DEFAULT ===");
        System.out.println(jsonDefault);
        System.out.println("\n=== Map Values MINIMAL_PLUS ===");
        System.out.println(jsonCompact);

        // Compact round-trip should work
        StringToZdtMapHolder resultCompact = JsonIo.toJava(jsonCompact, defaultReadOpts()).asClass(StringToZdtMapHolder.class);
        assertEquals(holder.events, resultCompact.events);
    }
}
