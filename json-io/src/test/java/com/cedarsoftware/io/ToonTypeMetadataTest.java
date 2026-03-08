package com.cedarsoftware.io;

import java.util.*;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TOON type metadata output using showTypeInfoAlways() and showTypeInfoMinimal().
 * Exercises round-trip (toToon -> fromToon) for types that exercise different ToonWriter code paths:
 * arrays, collections, maps, enums, EnumSets, POJOs, and nested polymorphic structures.
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
class ToonTypeMetadataTest {

    enum Color { RED, GREEN, BLUE }
    enum Season { SPRING, SUMMER, AUTUMN, WINTER }

    // ==================== Helper methods ====================

    private static WriteOptions alwaysType() {
        return new WriteOptionsBuilder().showTypeInfoAlways().build();
    }

    private static WriteOptions minimalType() {
        return new WriteOptionsBuilder().showTypeInfoMinimal().build();
    }

    /**
     * Write object to TOON with given options, then read it back and verify deep equality.
     */
    private static <T> T roundTrip(T obj, WriteOptions writeOptions) {
        String toon = JsonIo.toToon(obj, writeOptions);
        assertNotNull(toon, "toToon returned null");
        assertFalse(toon.isEmpty(), "toToon returned empty string");

        @SuppressWarnings("unchecked")
        Class<T> root = obj == null ? null : (Class<T>) obj.getClass();
        T result = JsonIo.fromToon(toon, null).asClass(root);
        assertTrue(DeepEquals.deepEquals(obj, result),
                "Round-trip mismatch for " + (obj == null ? "null" : obj.getClass().getSimpleName())
                        + "\nTOON:\n" + toon
                        + "\nOriginal: " + obj
                        + "\nResult:   " + result);
        return result;
    }

    /**
     * Verify that type metadata key appears in the TOON output.
     */
    private static void assertContainsType(String toon) {
        assertTrue(toon.contains("$type:") || toon.contains("$t:") ||
                        toon.contains("@type:") || toon.contains("@t:"),
                "Expected type metadata in TOON output:\n" + toon);
    }

    // ==================== showTypeInfoAlways tests ====================

    @Test
    void testAlways_simpleObject() {
        TestObject obj = new TestObject("test");
        String toon = JsonIo.toToon(obj, alwaysType());
        assertContainsType(toon);
        roundTrip(obj, alwaysType());
    }

    @Test
    void testAlways_nestedObject() {
        TestObject obj = new TestObject("parent");
        obj._other = new TestObject("child");
        roundTrip(obj, alwaysType());
    }

    @Test
    void testAlways_stringArray() {
        String[] arr = {"alpha", "beta", "gamma"};
        roundTrip(arr, alwaysType());
    }

    @Test
    void testAlways_intArray() {
        int[] arr = {10, 20, 30};
        roundTrip(arr, alwaysType());
    }

    @Test
    void testAlways_objectArray() {
        Object[] arr = {"hello", 42, true, null};
        roundTrip(arr, alwaysType());
    }

    @Test
    void testAlways_arrayList() {
        ArrayList<String> list = new ArrayList<>(Arrays.asList("a", "b", "c"));
        String toon = JsonIo.toToon(list, alwaysType());
        assertContainsType(toon);
        roundTrip(list, alwaysType());
    }

    @Test
    void testAlways_linkedList() {
        LinkedList<Integer> list = new LinkedList<>(Arrays.asList(1, 2, 3));
        String toon = JsonIo.toToon(list, alwaysType());
        assertContainsType(toon);
        roundTrip(list, alwaysType());
    }

    @Test
    void testAlways_hashSet() {
        HashSet<String> set = new HashSet<>(Arrays.asList("x", "y", "z"));
        roundTrip(set, alwaysType());
    }

    @Test
    void testAlways_treeSet() {
        TreeSet<String> set = new TreeSet<>(Arrays.asList("a", "b", "c"));
        String toon = JsonIo.toToon(set, alwaysType());
        assertContainsType(toon);
        roundTrip(set, alwaysType());
    }

    @Test
    void testAlways_hashMap() {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        roundTrip(map, alwaysType());
    }

    @Test
    void testAlways_linkedHashMap() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("first", "a");
        map.put("second", "b");
        String toon = JsonIo.toToon(map, alwaysType());
        assertContainsType(toon);
        roundTrip(map, alwaysType());
    }

    @Test
    void testAlways_treeMap() {
        TreeMap<String, Integer> map = new TreeMap<>();
        map.put("alpha", 1);
        map.put("beta", 2);
        String toon = JsonIo.toToon(map, alwaysType());
        assertContainsType(toon);
        roundTrip(map, alwaysType());
    }

    @Test
    void testAlways_enum() {
        Color color = Color.RED;
        String toon = JsonIo.toToon(color, alwaysType());
        // Enums are converter-supported (scalar), so type metadata may not appear
        Color result = JsonIo.fromToon(toon, null).asClass(Color.class);
        assertEquals(color, result);
    }

    @Test
    void testAlways_enumSet() {
        // EnumSet round-trip via TOON: write produces type metadata but the enum element type
        // is not preserved in the TOON text. Verify write succeeds and contains type info.
        EnumSet<Color> set = EnumSet.of(Color.RED, Color.BLUE);
        String toon = JsonIo.toToon(set, alwaysType());
        assertContainsType(toon);
        assertTrue(toon.contains("RED"), "Should contain RED");
        assertTrue(toon.contains("BLUE"), "Should contain BLUE");
    }

    @Test
    void testAlways_emptyEnumSet() {
        // Empty EnumSet loses its element type in TOON (no items to infer from).
        // Verify write produces valid output.
        EnumSet<Season> set = EnumSet.noneOf(Season.class);
        String toon = JsonIo.toToon(set, alwaysType());
        assertNotNull(toon);
    }

    @Test
    void testAlways_mapWithIntegerKeys() {
        // TOON format: map keys are textual. Integer keys become String keys on read-back.
        // Verify the write succeeds and values round-trip correctly.
        Map<Integer, String> map = new LinkedHashMap<>();
        map.put(1, "one");
        map.put(2, "two");
        String toon = JsonIo.toToon(map, alwaysType());
        assertContainsType(toon);
        Map<?, ?> result = JsonIo.fromToon(toon, null).asClass(LinkedHashMap.class);
        assertEquals(2, result.size());
        assertTrue(result.containsValue("one"));
        assertTrue(result.containsValue("two"));
    }

    @Test
    void testAlways_emptyCollections() {
        roundTrip(new ArrayList<>(), alwaysType());
        roundTrip(new HashMap<>(), alwaysType());
        roundTrip(new HashSet<>(), alwaysType());
    }

    @Test
    void testAlways_nestedCollections() {
        List<List<String>> nested = new ArrayList<>();
        nested.add(new ArrayList<>(Arrays.asList("a", "b")));
        nested.add(new ArrayList<>(Arrays.asList("c", "d")));
        roundTrip(nested, alwaysType());
    }

    @Test
    void testAlways_mapOfLists() {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        map.put("primes", Arrays.asList(2, 3, 5, 7));
        map.put("evens", Arrays.asList(2, 4, 6, 8));
        roundTrip(map, alwaysType());
    }

    @Test
    void testAlways_date() {
        Date date = new Date();
        roundTrip(date, alwaysType());
    }

    @Test
    void testAlways_calendar() {
        Calendar cal = Calendar.getInstance();
        cal.set(2025, Calendar.MARCH, 15, 10, 30, 0);
        cal.set(Calendar.MILLISECOND, 0);
        roundTrip(cal, alwaysType());
    }

    // ==================== showTypeInfoMinimal tests ====================

    @Test
    void testMinimal_simpleObject() {
        TestObject obj = new TestObject("test");
        String toon = JsonIo.toToon(obj, minimalType());
        assertContainsType(toon);
        roundTrip(obj, minimalType());
    }

    @Test
    void testMinimal_nestedObject() {
        TestObject obj = new TestObject("parent");
        obj._other = new TestObject("child");
        roundTrip(obj, minimalType());
    }

    @Test
    void testMinimal_stringArray() {
        String[] arr = {"alpha", "beta", "gamma"};
        roundTrip(arr, minimalType());
    }

    @Test
    void testMinimal_objectArray() {
        Object[] arr = {"hello", 42, true};
        roundTrip(arr, minimalType());
    }

    @Test
    void testMinimal_arrayList() {
        ArrayList<String> list = new ArrayList<>(Arrays.asList("a", "b", "c"));
        roundTrip(list, minimalType());
    }

    @Test
    void testMinimal_linkedList() {
        LinkedList<Integer> list = new LinkedList<>(Arrays.asList(1, 2, 3));
        String toon = JsonIo.toToon(list, minimalType());
        // LinkedList is not the default collection type, so type should appear
        assertContainsType(toon);
        roundTrip(list, minimalType());
    }

    @Test
    void testMinimal_hashSet() {
        HashSet<String> set = new HashSet<>(Arrays.asList("x", "y", "z"));
        roundTrip(set, minimalType());
    }

    @Test
    void testMinimal_treeSet() {
        TreeSet<String> set = new TreeSet<>(Arrays.asList("a", "b", "c"));
        roundTrip(set, minimalType());
    }

    @Test
    void testMinimal_hashMap() {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        roundTrip(map, minimalType());
    }

    @Test
    void testMinimal_linkedHashMap() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("first", "a");
        map.put("second", "b");
        roundTrip(map, minimalType());
    }

    @Test
    void testMinimal_treeMap() {
        TreeMap<String, Integer> map = new TreeMap<>();
        map.put("alpha", 1);
        map.put("beta", 2);
        roundTrip(map, minimalType());
    }

    @Test
    void testMinimal_enum() {
        Color color = Color.GREEN;
        String toon = JsonIo.toToon(color, minimalType());
        Color result = JsonIo.fromToon(toon, null).asClass(Color.class);
        assertEquals(color, result);
    }

    @Test
    void testMinimal_enumSet() {
        // EnumSet write succeeds but the enum element type is not preserved in TOON text.
        EnumSet<Color> set = EnumSet.of(Color.RED, Color.GREEN);
        String toon = JsonIo.toToon(set, minimalType());
        assertNotNull(toon);
        assertTrue(toon.contains("RED"));
        assertTrue(toon.contains("GREEN"));
    }

    @Test
    void testMinimal_nestedCollections() {
        List<List<String>> nested = new ArrayList<>();
        nested.add(new ArrayList<>(Arrays.asList("a", "b")));
        nested.add(new ArrayList<>(Arrays.asList("c", "d")));
        roundTrip(nested, minimalType());
    }

    @Test
    void testMinimal_mapOfLists() {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        map.put("primes", Arrays.asList(2, 3, 5, 7));
        map.put("evens", Arrays.asList(2, 4, 6, 8));
        roundTrip(map, minimalType());
    }

    @Test
    void testMinimal_date() {
        Date date = new Date();
        roundTrip(date, minimalType());
    }

    @Test
    void testMinimal_calendar() {
        Calendar cal = Calendar.getInstance();
        cal.set(2025, Calendar.MARCH, 15, 10, 30, 0);
        cal.set(Calendar.MILLISECOND, 0);
        roundTrip(cal, minimalType());
    }

    // ==================== Comparison tests ====================

    @Test
    void testAlways_typeOutputLargerThanMinimal() {
        TestObject obj = new TestObject("compare");
        obj._other = new TestObject("nested");
        String always = JsonIo.toToon(obj, alwaysType());
        String minimal = JsonIo.toToon(obj, minimalType());
        assertTrue(always.length() >= minimal.length(),
                "showTypeInfoAlways should produce at least as much output as showTypeInfoMinimal");
    }

    @Test
    void testAlways_typeOutputLargerThanDefault() {
        TestObject obj = new TestObject("compare");
        String always = JsonIo.toToon(obj, alwaysType());
        String defaultToon = JsonIo.toToon(obj, null);
        assertTrue(always.length() > defaultToon.length(),
                "showTypeInfoAlways should produce more output than default (no type metadata)");
    }

    // ==================== Complex structure tests ====================

    @Test
    void testAlways_objectWithCollectionFields() {
        // Use Integer[] instead of int[] — primitive arrays inside Maps lose their type
        // in TOON (no field type hint), so they round-trip as ArrayList<Long>.
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("name", "test");
        obj.put("tags", new ArrayList<>(Arrays.asList("a", "b")));
        obj.put("scores", new ArrayList<>(Arrays.asList(100, 200, 300)));
        obj.put("metadata", new LinkedHashMap<>(Collections.singletonMap("key", "value")));
        roundTrip(obj, alwaysType());
    }

    @Test
    void testMinimal_objectWithCollectionFields() {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("name", "test");
        obj.put("tags", new ArrayList<>(Arrays.asList("a", "b")));
        obj.put("scores", new ArrayList<>(Arrays.asList(100, 200, 300)));
        obj.put("metadata", new LinkedHashMap<>(Collections.singletonMap("key", "value")));
        roundTrip(obj, minimalType());
    }

    @Test
    void testAlways_arrayOfMixedObjects() {
        Object[] arr = new Object[]{
                "hello",
                42,
                new ArrayList<>(Arrays.asList(1, 2)),
                new LinkedHashMap<>(Collections.singletonMap("k", "v"))
        };
        roundTrip(arr, alwaysType());
    }

    @Test
    void testMinimal_arrayOfMixedObjects() {
        Object[] arr = new Object[]{
                "hello",
                42,
                new ArrayList<>(Arrays.asList(1, 2)),
                new LinkedHashMap<>(Collections.singletonMap("k", "v"))
        };
        roundTrip(arr, minimalType());
    }

    @Test
    void testAlways_deeplyNestedStructure() {
        Map<String, Object> level3 = new LinkedHashMap<>();
        level3.put("value", "deep");

        Map<String, Object> level2 = new LinkedHashMap<>();
        level2.put("inner", level3);

        Map<String, Object> level1 = new LinkedHashMap<>();
        level1.put("middle", level2);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("outer", level1);

        roundTrip(root, alwaysType());
    }

    @Test
    void testMinimal_deeplyNestedStructure() {
        Map<String, Object> level3 = new LinkedHashMap<>();
        level3.put("value", "deep");

        Map<String, Object> level2 = new LinkedHashMap<>();
        level2.put("inner", level3);

        Map<String, Object> level1 = new LinkedHashMap<>();
        level1.put("middle", level2);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("outer", level1);

        roundTrip(root, minimalType());
    }

    @Test
    void testAlways_listOfEnums_standalone() {
        // Standalone collection (no field context): enum values come back as Strings.
        List<Color> colors = new ArrayList<>(Arrays.asList(Color.RED, Color.GREEN, Color.BLUE));
        String toon = JsonIo.toToon(colors, alwaysType());
        assertContainsType(toon);
        assertTrue(toon.contains("RED"));
        assertTrue(toon.contains("GREEN"));
        assertTrue(toon.contains("BLUE"));
        List<?> result = JsonIo.fromToon(toon, null).asClass(ArrayList.class);
        assertEquals(3, result.size());
    }

    @Test
    void testMinimal_listOfEnums_standalone() {
        List<Color> colors = new ArrayList<>(Arrays.asList(Color.RED, Color.GREEN, Color.BLUE));
        String toon = JsonIo.toToon(colors, minimalType());
        assertTrue(toon.contains("RED"));
        List<?> result = JsonIo.fromToon(toon, null).asClass(ArrayList.class);
        assertEquals(3, result.size());
    }

    // ==================== Field-context tests ====================
    // These test that generic types, @IoTypeInfo, and $type metadata all work
    // to properly convert values when the receiving context provides type info.

    /**
     * POJO with typed fields that exercise all three "limitations":
     * 1. List<Enum> — generic type drives enum conversion
     * 2. Map<Integer, String> — generic key type drives key conversion
     * 3. EnumSet<Enum> — field type provides enum class
     */
    static class TypedFieldHolder {
        List<Color> colors;
        Map<Integer, String> intKeyMap;
        EnumSet<Color> colorSet;
        Map<Color, String> enumKeyMap;
        Color[] colorArray;
    }

    @Test
    void testFieldContext_listOfEnums_defaultToon() {
        // Default TOON (no type mode) — field generics provide element type
        TypedFieldHolder holder = new TypedFieldHolder();
        holder.colors = new ArrayList<>(Arrays.asList(Color.RED, Color.GREEN, Color.BLUE));
        String toon = JsonIo.toToon(holder, null);
        TypedFieldHolder result = JsonIo.fromToon(toon, null).asClass(TypedFieldHolder.class);
        assertEquals(3, result.colors.size());
        assertEquals(Color.RED, result.colors.get(0));
        assertEquals(Color.GREEN, result.colors.get(1));
        assertEquals(Color.BLUE, result.colors.get(2));
    }

    @Test
    void testFieldContext_listOfEnums_alwaysType() {
        TypedFieldHolder holder = new TypedFieldHolder();
        holder.colors = new ArrayList<>(Arrays.asList(Color.RED, Color.GREEN, Color.BLUE));
        String toon = JsonIo.toToon(holder, alwaysType());
        TypedFieldHolder result = JsonIo.fromToon(toon, null).asClass(TypedFieldHolder.class);
        assertEquals(3, result.colors.size());
        assertEquals(Color.RED, result.colors.get(0));
        assertEquals(Color.GREEN, result.colors.get(1));
        assertEquals(Color.BLUE, result.colors.get(2));
    }

    @Test
    void testFieldContext_listOfEnums_minimalType() {
        TypedFieldHolder holder = new TypedFieldHolder();
        holder.colors = new ArrayList<>(Arrays.asList(Color.RED, Color.GREEN, Color.BLUE));
        String toon = JsonIo.toToon(holder, minimalType());
        TypedFieldHolder result = JsonIo.fromToon(toon, null).asClass(TypedFieldHolder.class);
        assertEquals(3, result.colors.size());
        assertEquals(Color.RED, result.colors.get(0));
        assertEquals(Color.GREEN, result.colors.get(1));
        assertEquals(Color.BLUE, result.colors.get(2));
    }

    @Test
    void testFieldContext_mapWithIntegerKeys_defaultToon() {
        TypedFieldHolder holder = new TypedFieldHolder();
        holder.intKeyMap = new LinkedHashMap<>();
        holder.intKeyMap.put(1, "one");
        holder.intKeyMap.put(2, "two");
        holder.intKeyMap.put(42, "forty-two");
        String toon = JsonIo.toToon(holder, null);
        TypedFieldHolder result = JsonIo.fromToon(toon, null).asClass(TypedFieldHolder.class);
        assertEquals(3, result.intKeyMap.size());
        assertEquals("one", result.intKeyMap.get(1));
        assertEquals("two", result.intKeyMap.get(2));
        assertEquals("forty-two", result.intKeyMap.get(42));
    }

    @Test
    void testFieldContext_mapWithIntegerKeys_alwaysType() {
        TypedFieldHolder holder = new TypedFieldHolder();
        holder.intKeyMap = new LinkedHashMap<>();
        holder.intKeyMap.put(1, "one");
        holder.intKeyMap.put(2, "two");
        String toon = JsonIo.toToon(holder, alwaysType());
        TypedFieldHolder result = JsonIo.fromToon(toon, null).asClass(TypedFieldHolder.class);
        assertEquals(2, result.intKeyMap.size());
        assertEquals("one", result.intKeyMap.get(1));
        assertEquals("two", result.intKeyMap.get(2));
    }

    @Test
    void testFieldContext_enumSet_defaultToon() {
        TypedFieldHolder holder = new TypedFieldHolder();
        holder.colorSet = EnumSet.of(Color.RED, Color.BLUE);
        String toon = JsonIo.toToon(holder, null);
        TypedFieldHolder result = JsonIo.fromToon(toon, null).asClass(TypedFieldHolder.class);
        assertEquals(EnumSet.of(Color.RED, Color.BLUE), result.colorSet);
    }

    @Test
    void testFieldContext_enumSet_alwaysType() {
        TypedFieldHolder holder = new TypedFieldHolder();
        holder.colorSet = EnumSet.of(Color.RED, Color.BLUE);
        String toon = JsonIo.toToon(holder, alwaysType());
        TypedFieldHolder result = JsonIo.fromToon(toon, null).asClass(TypedFieldHolder.class);
        assertEquals(EnumSet.of(Color.RED, Color.BLUE), result.colorSet);
    }

    @Test
    void testFieldContext_emptyEnumSet_defaultToon() {
        TypedFieldHolder holder = new TypedFieldHolder();
        holder.colorSet = EnumSet.noneOf(Color.class);
        String toon = JsonIo.toToon(holder, null);
        TypedFieldHolder result = JsonIo.fromToon(toon, null).asClass(TypedFieldHolder.class);
        assertNotNull(result.colorSet);
        assertTrue(result.colorSet.isEmpty());
    }

    @Test
    void testFieldContext_enumKeyMap_defaultToon() {
        TypedFieldHolder holder = new TypedFieldHolder();
        holder.enumKeyMap = new LinkedHashMap<>();
        holder.enumKeyMap.put(Color.RED, "warm");
        holder.enumKeyMap.put(Color.BLUE, "cool");
        String toon = JsonIo.toToon(holder, null);
        TypedFieldHolder result = JsonIo.fromToon(toon, null).asClass(TypedFieldHolder.class);
        assertEquals(2, result.enumKeyMap.size());
        assertEquals("warm", result.enumKeyMap.get(Color.RED));
        assertEquals("cool", result.enumKeyMap.get(Color.BLUE));
    }

    @Test
    void testFieldContext_enumKeyMap_alwaysType() {
        TypedFieldHolder holder = new TypedFieldHolder();
        holder.enumKeyMap = new LinkedHashMap<>();
        holder.enumKeyMap.put(Color.RED, "warm");
        holder.enumKeyMap.put(Color.BLUE, "cool");
        String toon = JsonIo.toToon(holder, alwaysType());
        TypedFieldHolder result = JsonIo.fromToon(toon, null).asClass(TypedFieldHolder.class);
        assertEquals(2, result.enumKeyMap.size());
        assertEquals("warm", result.enumKeyMap.get(Color.RED));
        assertEquals("cool", result.enumKeyMap.get(Color.BLUE));
    }

    @Test
    void testFieldContext_colorArray_defaultToon() {
        TypedFieldHolder holder = new TypedFieldHolder();
        holder.colorArray = new Color[]{Color.RED, Color.GREEN, Color.BLUE};
        String toon = JsonIo.toToon(holder, null);
        TypedFieldHolder result = JsonIo.fromToon(toon, null).asClass(TypedFieldHolder.class);
        assertArrayEquals(new Color[]{Color.RED, Color.GREEN, Color.BLUE}, result.colorArray);
    }

    @Test
    void testFieldContext_colorArray_alwaysType() {
        TypedFieldHolder holder = new TypedFieldHolder();
        holder.colorArray = new Color[]{Color.RED, Color.GREEN, Color.BLUE};
        String toon = JsonIo.toToon(holder, alwaysType());
        TypedFieldHolder result = JsonIo.fromToon(toon, null).asClass(TypedFieldHolder.class);
        assertArrayEquals(new Color[]{Color.RED, Color.GREEN, Color.BLUE}, result.colorArray);
    }

    @Test
    void testFieldContext_allFieldsPopulated() {
        TypedFieldHolder holder = new TypedFieldHolder();
        holder.colors = new ArrayList<>(Arrays.asList(Color.RED, Color.GREEN));
        holder.intKeyMap = new LinkedHashMap<>();
        holder.intKeyMap.put(1, "one");
        holder.intKeyMap.put(2, "two");
        holder.colorSet = EnumSet.of(Color.RED, Color.BLUE);
        holder.enumKeyMap = new LinkedHashMap<>();
        holder.enumKeyMap.put(Color.GREEN, "go");
        holder.colorArray = new Color[]{Color.BLUE};

        // Default TOON
        String toon = JsonIo.toToon(holder, null);
        TypedFieldHolder result = JsonIo.fromToon(toon, null).asClass(TypedFieldHolder.class);
        assertEquals(Arrays.asList(Color.RED, Color.GREEN), result.colors);
        assertEquals("one", result.intKeyMap.get(1));
        assertEquals("two", result.intKeyMap.get(2));
        assertEquals(EnumSet.of(Color.RED, Color.BLUE), result.colorSet);
        assertEquals("go", result.enumKeyMap.get(Color.GREEN));
        assertArrayEquals(new Color[]{Color.BLUE}, result.colorArray);

        // Always type
        toon = JsonIo.toToon(holder, alwaysType());
        result = JsonIo.fromToon(toon, null).asClass(TypedFieldHolder.class);
        assertEquals(Arrays.asList(Color.RED, Color.GREEN), result.colors);
        assertEquals("one", result.intKeyMap.get(1));
        assertEquals(EnumSet.of(Color.RED, Color.BLUE), result.colorSet);
        assertEquals("go", result.enumKeyMap.get(Color.GREEN));
        assertArrayEquals(new Color[]{Color.BLUE}, result.colorArray);

        // Minimal type
        toon = JsonIo.toToon(holder, minimalType());
        result = JsonIo.fromToon(toon, null).asClass(TypedFieldHolder.class);
        assertEquals(Arrays.asList(Color.RED, Color.GREEN), result.colors);
        assertEquals("one", result.intKeyMap.get(1));
        assertEquals(EnumSet.of(Color.RED, Color.BLUE), result.colorSet);
        assertEquals("go", result.enumKeyMap.get(Color.GREEN));
        assertArrayEquals(new Color[]{Color.BLUE}, result.colorArray);
    }
}
