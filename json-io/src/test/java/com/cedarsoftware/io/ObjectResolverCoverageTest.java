package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coverage tests for ObjectResolver — targets JaCoCo gaps:
 * - Generic type extraction for Collection/Map fields
 * - Map traversal with various key/value types
 * - Collection of collections
 * - Wildcard, raw, and missing generic type info
 * - Skip nulls in iterations
 * - Unresolved field handling
 * - Map<Long, ...> key conversion (ParameterizedType keyType)
 */
class ObjectResolverCoverageTest {

    // ========== Test models ==========

    public static class StringHolder {
        public String value;
    }

    public static class ListHolder {
        public List<String> items;
    }

    public static class MapHolder {
        public Map<String, Integer> data;
    }

    public static class TypedMapHolder {
        public Map<Long, String> longKeyMap;
        public Map<UUID, Integer> uuidKeyMap;
    }

    public static class NestedListHolder {
        public List<List<String>> matrix;
    }

    public static class CollectionFieldHolder {
        public Collection<String> items;
    }

    public static class SetFieldHolder {
        public Set<Integer> ids;
    }

    public static class RawListHolder {
        @SuppressWarnings("rawtypes")
        public List items;  // raw list, no type param
    }

    public static class WildcardListHolder {
        public List<? extends Number> numbers;
    }

    public static class CustomMapField {
        public CustomTreeMap myMap;
    }

    @SuppressWarnings("serial")
    public static class CustomTreeMap extends TreeMap<String, Integer> {
        // inherits Map<String, Integer>
    }

    public static class MultipleCollectionFields {
        public List<String> strings;
        public Set<Integer> ints;
        public Map<String, Boolean> flags;
    }

    public static class MapWithCollectionValue {
        public Map<String, List<Integer>> grouped;
    }

    // ========== Basic deserialization ==========

    @Test
    void testStringField() {
        String json = "{\"@type\":\"" + StringHolder.class.getName() + "\",\"value\":\"hello\"}";
        StringHolder h = JsonIo.toJava(json).asClass(StringHolder.class);
        assertThat(h.value).isEqualTo("hello");
    }

    @Test
    void testStringFieldNull() {
        String json = "{\"@type\":\"" + StringHolder.class.getName() + "\",\"value\":null}";
        StringHolder h = JsonIo.toJava(json).asClass(StringHolder.class);
        assertThat(h.value).isNull();
    }

    @Test
    void testStringFieldMissing() {
        String json = "{\"@type\":\"" + StringHolder.class.getName() + "\"}";
        StringHolder h = JsonIo.toJava(json).asClass(StringHolder.class);
        assertThat(h.value).isNull();
    }

    // ========== List with generic type ==========

    @Test
    void testListOfStrings() {
        String json = "{\"@type\":\"" + ListHolder.class.getName() + "\",\"items\":[\"a\",\"b\",\"c\"]}";
        ListHolder h = JsonIo.toJava(json).asClass(ListHolder.class);
        assertThat(h.items).containsExactly("a", "b", "c");
    }

    @Test
    void testListEmpty() {
        String json = "{\"@type\":\"" + ListHolder.class.getName() + "\",\"items\":[]}";
        ListHolder h = JsonIo.toJava(json).asClass(ListHolder.class);
        assertThat(h.items).isEmpty();
    }

    @Test
    void testListNull() {
        String json = "{\"@type\":\"" + ListHolder.class.getName() + "\",\"items\":null}";
        ListHolder h = JsonIo.toJava(json).asClass(ListHolder.class);
        assertThat(h.items).isNull();
    }

    // ========== Collection field (interface-typed) ==========

    @Test
    void testCollectionField() {
        String json = "{\"@type\":\"" + CollectionFieldHolder.class.getName() + "\",\"items\":[\"x\",\"y\"]}";
        CollectionFieldHolder h = JsonIo.toJava(json).asClass(CollectionFieldHolder.class);
        assertThat(h.items).containsExactlyInAnyOrder("x", "y");
    }

    // ========== Set field ==========

    @Test
    void testSetField() {
        String json = "{\"@type\":\"" + SetFieldHolder.class.getName() + "\",\"ids\":[1,2,3]}";
        SetFieldHolder h = JsonIo.toJava(json).asClass(SetFieldHolder.class);
        assertThat(h.ids).containsExactlyInAnyOrder(1, 2, 3);
    }

    // ========== Raw list (no generic type) ==========

    @Test
    void testRawList() {
        String json = "{\"@type\":\"" + RawListHolder.class.getName() + "\",\"items\":[\"a\",1,true]}";
        RawListHolder h = JsonIo.toJava(json).asClass(RawListHolder.class);
        assertThat(h.items).hasSize(3);
    }

    // ========== Wildcard generic type ==========

    @Test
    void testWildcardList() {
        String json = "{\"@type\":\"" + WildcardListHolder.class.getName() + "\",\"numbers\":[1,2,3]}";
        WildcardListHolder h = JsonIo.toJava(json).asClass(WildcardListHolder.class);
        assertThat(h.numbers).hasSize(3);
    }

    // ========== Map with String keys ==========

    @Test
    void testMapStringIntegerKeys() {
        String json = "{\"@type\":\"" + MapHolder.class.getName() + "\",\"data\":{\"a\":1,\"b\":2}}";
        MapHolder h = JsonIo.toJava(json).asClass(MapHolder.class);
        assertThat(h.data).containsEntry("a", 1).containsEntry("b", 2);
    }

    // ========== Map with non-String keys ==========

    @Test
    void testMapWithLongKeys() {
        // String keys in JSON converted to Long via Converter
        String json = "{\"@type\":\"" + TypedMapHolder.class.getName() +
                "\",\"longKeyMap\":{\"100\":\"alpha\",\"200\":\"beta\"}}";
        TypedMapHolder h = JsonIo.toJava(json).asClass(TypedMapHolder.class);
        assertThat(h.longKeyMap).containsEntry(100L, "alpha").containsEntry(200L, "beta");
    }

    @Test
    void testMapWithUUIDKeys() {
        UUID u1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID u2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        String json = "{\"@type\":\"" + TypedMapHolder.class.getName() +
                "\",\"uuidKeyMap\":{\"" + u1 + "\":1,\"" + u2 + "\":2}}";
        TypedMapHolder h = JsonIo.toJava(json).asClass(TypedMapHolder.class);
        assertThat(h.uuidKeyMap).containsEntry(u1, 1).containsEntry(u2, 2);
    }

    // ========== Map with collection values ==========

    @Test
    void testMapWithListValues() {
        String json = "{\"@type\":\"" + MapWithCollectionValue.class.getName() +
                "\",\"grouped\":{\"a\":[1,2,3],\"b\":[4,5]}}";
        MapWithCollectionValue h = JsonIo.toJava(json).asClass(MapWithCollectionValue.class);
        assertThat(h.grouped.get("a")).containsExactly(1, 2, 3);
        assertThat(h.grouped.get("b")).containsExactly(4, 5);
    }

    // ========== Custom map subclass (extracts types from generic superclass) ==========

    @Test
    void testCustomMapSubclass() {
        String json = "{\"@type\":\"" + CustomMapField.class.getName() +
                "\",\"myMap\":{\"@type\":\"" + CustomTreeMap.class.getName() +
                "\",\"a\":1,\"b\":2}}";
        CustomMapField h = JsonIo.toJava(json).asClass(CustomMapField.class);
        assertThat(h.myMap).containsEntry("a", 1).containsEntry("b", 2);
    }

    // ========== Nested list ==========

    @Test
    void testNestedList() {
        String json = "{\"@type\":\"" + NestedListHolder.class.getName() +
                "\",\"matrix\":[[\"a\",\"b\"],[\"c\",\"d\"]]}";
        NestedListHolder h = JsonIo.toJava(json).asClass(NestedListHolder.class);
        assertThat(h.matrix).hasSize(2);
        assertThat(h.matrix.get(0)).containsExactly("a", "b");
    }

    // ========== Multiple collection fields ==========

    @Test
    void testMultipleCollectionFields() {
        String json = "{\"@type\":\"" + MultipleCollectionFields.class.getName() +
                "\",\"strings\":[\"x\",\"y\"]," +
                "\"ints\":[1,2,3]," +
                "\"flags\":{\"a\":true,\"b\":false}}";
        MultipleCollectionFields h = JsonIo.toJava(json).asClass(MultipleCollectionFields.class);
        assertThat(h.strings).containsExactly("x", "y");
        assertThat(h.ints).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(h.flags).containsEntry("a", true).containsEntry("b", false);
    }

    // ========== Map with @keys/@items legacy format ==========

    @Test
    void testMapKeysItemsLegacyFormat() {
        // Old format with @keys/@items array pair
        String json = "{\"@type\":\"" + TypedMapHolder.class.getName() +
                "\",\"longKeyMap\":{\"@type\":\"java.util.LinkedHashMap\"," +
                "\"@keys\":[100,200],\"@items\":[\"alpha\",\"beta\"]}}";
        TypedMapHolder h = JsonIo.toJava(json).asClass(TypedMapHolder.class);
        assertThat(h.longKeyMap).containsEntry(100L, "alpha").containsEntry(200L, "beta");
    }

    // ========== Empty map ==========

    @Test
    void testEmptyMap() {
        String json = "{\"@type\":\"" + MapHolder.class.getName() + "\",\"data\":{}}";
        MapHolder h = JsonIo.toJava(json).asClass(MapHolder.class);
        assertThat(h.data).isEmpty();
    }

    @Test
    void testNullMap() {
        String json = "{\"@type\":\"" + MapHolder.class.getName() + "\",\"data\":null}";
        MapHolder h = JsonIo.toJava(json).asClass(MapHolder.class);
        assertThat(h.data).isNull();
    }

    // ========== ArrayList vs LinkedList ==========

    public static class LinkedListField {
        public LinkedList<String> linkedItems;
    }

    @Test
    void testLinkedListField() {
        String json = "{\"@type\":\"" + LinkedListField.class.getName() +
                "\",\"linkedItems\":[\"a\",\"b\"]}";
        LinkedListField h = JsonIo.toJava(json).asClass(LinkedListField.class);
        assertThat(h.linkedItems).containsExactly("a", "b");
    }

    // ========== Read as Maps (toMaps) ==========

    @Test
    void testReadAsMapsPreservesStructure() {
        String json = "{\"@type\":\"" + StringHolder.class.getName() + "\",\"value\":\"hello\"}";
        @SuppressWarnings("unchecked")
        Map<String, Object> m = JsonIo.toMaps(json).asClass(Map.class);
        assertThat(m.get("value")).isEqualTo("hello");
    }

    // ========== Polymorphic — interface field with concrete @type ==========

    public static class InterfaceField {
        public Collection<String> items;  // interface
    }

    @Test
    void testInterfaceFieldWithConcreteType() {
        String json = "{\"@type\":\"" + InterfaceField.class.getName() +
                "\",\"items\":{\"@type\":\"java.util.ArrayList\",\"@items\":[\"a\",\"b\"]}}";
        InterfaceField h = JsonIo.toJava(json).asClass(InterfaceField.class);
        assertThat(h.items).hasSize(2);
    }
}
