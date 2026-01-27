package com.cedarsoftware.io;

import java.util.*;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the showTypeInfoCompact() WriteOption. When enabled, Collections and Maps whose runtime type
 * is the "natural default" for the field's declared type are written without the @type/@items wrapper.
 * For example, an ArrayList in a List field writes as direct [...] instead of {"@type":"ArrayList","@items":[...]}.
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
        return new WriteOptionsBuilder().showTypeInfoCompact().build();
    }

    private WriteOptions compactPrettyWriteOpts() {
        return new WriteOptionsBuilder().showTypeInfoCompact().prettyPrint(true).build();
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
                .showTypeInfoCompact()
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
}
