package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonIoCastingTests {

    // Basic primitive and object type tests
    @Test
    void testPrimitiveNumberCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        // Integer types
        assertEquals(16L, (Long)TestUtil.toObjects("16", options, null));
        assertEquals(16, TestUtil.toObjects("16", options, Integer.class));
        assertEquals((short)16, TestUtil.toObjects("16", options, Short.class));
        assertEquals((byte)16, TestUtil.toObjects("16", options, Byte.class));
        assertEquals(16L, TestUtil.toObjects("16", options, Long.class));

        // Floating point types
        assertEquals(16.0, TestUtil.toObjects("16.0", options, Double.class));
        assertEquals(16.0f, TestUtil.toObjects("16.0", options, Float.class));
    }

    @Test
    void testComplexNumberCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        // BigInteger & BigDecimal
        assertEquals(new BigInteger("16"), TestUtil.toObjects("16", options, BigInteger.class));
        assertEquals(new BigDecimal("16.0"), TestUtil.toObjects("16.0", options, BigDecimal.class));

        // Atomic types
        AtomicInteger expectedAtomic = new AtomicInteger(16);
        assertEquals(expectedAtomic.get(), TestUtil.toObjects("16", options, AtomicInteger.class).get());

        AtomicLong expectedAtomicLong = new AtomicLong(16L);
        assertEquals(expectedAtomicLong.get(), TestUtil.toObjects("16", options, AtomicLong.class).get());
    }

    @Test
    void testStringCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        assertEquals("test", TestUtil.toObjects("\"test\"", options, null));
        assertEquals("test", TestUtil.toObjects("\"test\"", options, String.class));
        assertEquals("16", TestUtil.toObjects("\"16\"", options, String.class));
    }

    @Test
    void testDateAndUUIDCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        UUID uuid = UUID.randomUUID();
        assertEquals(uuid, TestUtil.toObjects("\"" + uuid + "\"", options, UUID.class));

        String isoDate = "2024-01-01T00:00:00Z";
        Instant instant = Instant.parse(isoDate);
        Date expected = Date.from(instant);
        assertEquals(expected, TestUtil.toObjects("\"" + isoDate + "\"", options, Date.class));
    }

    // Array tests
    @Test
    void testSimpleArrayCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        // Object array with mixed types
        String json = "[1, 2.0, \"three\", null]";
        Object[] expected = new Object[]{1L, 2.0, "three", null};
        assertArrayEquals(expected, TestUtil.toObjects(json, options, Object[].class));

        // Typed arrays
        String intArrayJson = "[1, 2, 3]";
        int[] expectedInts = new int[]{1, 2, 3};
        assertArrayEquals(expectedInts, TestUtil.toObjects(intArrayJson, options, int[].class));

        String doubleArrayJson = "[1.0, 2.0, 3.0]";
        double[] expectedDoubles = new double[]{1.0, 2.0, 3.0};
        assertArrayEquals(expectedDoubles, TestUtil.toObjects(doubleArrayJson, options, double[].class));
    }

    @Test
    void testNestedArrayCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        // Nested arrays with nulls
        String json = "[[1, null, 3], null, [4, 5, null]]";
        Long[][] expected = new Long[][]{{1L, null, 3L}, null, {4L, 5L, null}};
        Long[][] result = TestUtil.toObjects(json, options, Long[][].class);
        assertArrayEquals(expected, result);
        assert DeepEquals.deepEquals(expected, result);
    }

    // Collection tests
    @Test
    void testCollectionCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        // List
        String json = "[1, 2, 3]";
        List<Long> expected = Arrays.asList(1L, 2L, 3L);
        assertEquals(expected, TestUtil.toObjects(json, options, List.class));

        // Set
        String setJson = "[1, 2, 3]";
        Set<Long> expectedSet = new HashSet<>(Arrays.asList(1L, 2L, 3L));
        assertEquals(expectedSet, TestUtil.toObjects(setJson, options, Set.class));

        // Collection interface
        String colJson = "[1, 2, 3]";
        Collection<?> expectedCol = Arrays.asList(1L, 2L, 3L);
        assertEquals(expectedCol, TestUtil.toObjects(colJson, options, Collection.class));
    }

    @Test
    void testNestedCollectionCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        // Nested List with mixed types and nulls
        String json = "[[1, null, \"three\"], null, [4.0, true, null]]";
        List<List<Object>> expected = Arrays.asList(
                Arrays.asList(1L, null, "three"),
                null,
                Arrays.asList(4.0, true, null)
        );
        assertEquals(expected, TestUtil.toObjects(json, options, List.class));
    }

    @Test
    void testNestedArrayCastingVariations() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        // Test 1: Basic nested array without type specification
        String json1 = "[[1, null, 3], null, [4, 5, null]]";
        Object[] result1 = TestUtil.toObjects(json1, options, null);
        assertNotNull(result1);
        assertEquals(3, result1.length);
        assertArrayEquals(new Object[]{1L, null, 3L}, (Object[])result1[0]);
        assertNull(result1[1]);
        assertArrayEquals(new Object[]{4L, 5L, null}, (Object[])result1[2]);

        // Test 2: Single level typed array
        String json2 = "[1, null, 3]";
        Long[] result2 = TestUtil.toObjects(json2, options, Long[].class);
        assertArrayEquals(new Long[]{1L, null, 3L}, result2);

        // Test 3: Mixed type nested array
        String json3 = "[[1, \"2\", 3.0], null, [true, null, 6]]";
        Object[][] result3 = TestUtil.toObjects(json3, options, Object[][].class);
        assertNotNull(result3);
        assertEquals(3, result3.length);
        assertArrayEquals(new Object[]{1L, "2", 3.0}, result3[0]);
        assertNull(result3[1]);
        assertArrayEquals(new Object[]{true, null, 6L}, result3[2]);

        // Test 4: Three levels deep
        String json4 = "[[[1]], [[2, 3]], null]";
        Object[][][] result4 = TestUtil.toObjects(json4, options, Object[][][].class);
        assertNotNull(result4);
        assertEquals(3, result4.length);
        assertEquals(1L, result4[0][0][0]);
        assertEquals(2L, result4[1][0][0]);
        assertEquals(3L, result4[1][0][1]);
        assertNull(result4[2]);
    }

    @Test
    void testPrimitiveArrayCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        // Test primitive array casting
        String json = "[1, 2, 3]";
        int[] result1 = TestUtil.toObjects(json, options, int[].class);
        assertArrayEquals(new int[]{1, 2, 3}, result1);

        long[] result2 = TestUtil.toObjects(json, options, long[].class);
        assertArrayEquals(new long[]{1L, 2L, 3L}, result2);

        // Test nested primitive arrays
        String nestedJson = "[[1, 2], [3, 4]]";
        int[][] result3 = TestUtil.toObjects(nestedJson, options, int[][].class);
        assertArrayEquals(new int[][]{{1, 2}, {3, 4}}, result3);
    }

    @Test
    void testNestedArrayWithTypeCoercion() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        // Test coercion of string numbers to Long
        String json = "[[\"1\", null, \"3\"], null, [\"4\", \"5\", null]]";
        Long[][] expected = new Long[][]{{1L, null, 3L}, null, {4L, 5L, null}};
        Long[][] result = TestUtil.toObjects(json, options, Long[][].class);
        assertArrayEquals(expected, result);
    }

    @Test
    void testDeeplyNestedArrayCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        // Triple nested arrays
        String json = "[[[1], [2, 3]], null, [[4, 5], [6, null]]]";
        Integer[][][] expected = new Integer[][][]{{{1}, {2, 3}}, null, {{4, 5}, {6, null}}};
        Integer[][][] result = TestUtil.toObjects(json, options, Integer[][][].class);
        assertArrayEquals(expected, result);
        assert DeepEquals.deepEquals(expected, result);
    }

    @Test
    void testMixedNestedArrayCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        // Nested arrays with mixed types and nulls
        String json = "[[1, null, \"three\"], null, [4.0, true, null]]";
        Object[][] expected = new Object[][]{{1L, null, "three"}, null, {4.0, true, null}};
        Object[][] result = TestUtil.toObjects(json, options, Object[][].class);
        assertArrayEquals(expected, result);
        assert DeepEquals.deepEquals(expected, result);
    }

    @Test
    void testSpecificCollectionTypes() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        // TreeSet (maintains natural order)
        String json = "[3, 1, 2, 5, 4]";
        TreeSet<Long> treeSetResult = TestUtil.toObjects(json, options, TreeSet.class);
        assertEquals(Arrays.asList(1L, 2L, 3L, 4L, 5L), new ArrayList<>(treeSetResult));

        // LinkedHashSet (maintains insertion order)
        LinkedHashSet<Long> linkedSetResult = TestUtil.toObjects(json, options, LinkedHashSet.class);
        assertEquals(Arrays.asList(3L, 1L, 2L, 5L, 4L), new ArrayList<>(linkedSetResult));

        // ConcurrentSkipListSet (thread-safe sorted set)
        ConcurrentSkipListSet<Long> concurrentSetResult = TestUtil.toObjects(json, options, ConcurrentSkipListSet.class);
        assertEquals(Arrays.asList(1L, 2L, 3L, 4L, 5L), new ArrayList<>(concurrentSetResult));

        // CopyOnWriteArraySet (thread-safe set)
        CopyOnWriteArraySet<Long> cowSetResult = TestUtil.toObjects(json, options, CopyOnWriteArraySet.class);
        assertEquals(new HashSet<>(Arrays.asList(3L, 1L, 2L, 5L, 4L)), new HashSet<>(cowSetResult));
    }

    @Test
    void testSpecificListTypes() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        String json = "[1, 2, 3]";

        // LinkedList
        LinkedList<Long> linkedListResult = TestUtil.toObjects(json, options, LinkedList.class);
        assertEquals(Arrays.asList(1L, 2L, 3L), linkedListResult);

        // Vector (synchronized)
        Vector<Long> vectorResult = TestUtil.toObjects(json, options, Vector.class);
        assertEquals(Arrays.asList(1L, 2L, 3L), vectorResult);

        // CopyOnWriteArrayList (thread-safe)
        CopyOnWriteArrayList<Long> cowListResult = TestUtil.toObjects(json, options, CopyOnWriteArrayList.class);
        assertEquals(Arrays.asList(1L, 2L, 3L), cowListResult);
    }

    @Test
    void testMapTypes() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        // Basic HashMap
        String json = "{\"a\":1, \"b\":2, \"c\":3}";
        Map<String, Long> mapResult = TestUtil.toObjects(json, options, Map.class);
        assertEquals(3, mapResult.size());
        assertEquals(1L, mapResult.get("a"));
        assertEquals(2L, mapResult.get("b"));
        assertEquals(3L, mapResult.get("c"));

        // TreeMap (sorted by keys)
        TreeMap<String, Long> treeMapResult = TestUtil.toObjects(json, options, TreeMap.class);
        assertEquals(Arrays.asList("a", "b", "c"), new ArrayList<>(treeMapResult.keySet()));

        // LinkedHashMap (maintains insertion order)
        LinkedHashMap<String, Long> linkedMapResult = TestUtil.toObjects(json, options, LinkedHashMap.class);
        assertEquals(Arrays.asList("a", "b", "c"), new ArrayList<>(linkedMapResult.keySet()));

        // ConcurrentHashMap (thread-safe)
        ConcurrentHashMap<String, Long> concurrentMapResult = TestUtil.toObjects(json, options, ConcurrentHashMap.class);
        assertEquals(3, concurrentMapResult.size());
        assertEquals(1L, concurrentMapResult.get("a"));
    }

    @Test
    void testNestedMapsAndCollections() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        // Map with nested collections
        String json = "{\"list\":[1,2,3], \"set\":[4,5,6], \"map\":{\"x\":7,\"y\":8}}";
        Map<String, Object> result = TestUtil.toObjects(json, options, Map.class);

        assert DeepEquals.deepEquals(new Object[] {1L, 2L, 3L}, result.get("list"));
        assert DeepEquals.deepEquals(new Object[] {4L, 5L, 6L}, result.get("set"));

        @SuppressWarnings("unchecked")
        Map<String, Long> nestedMap = (Map<String, Long>) result.get("map");
        assertEquals(7L, nestedMap.get("x"));
        assertEquals(8L, nestedMap.get("y"));
    }

    @Test
    void testComplexMapStructures() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        // Map with mixed value types
        String json = "{\"string\":\"text\", \"number\":42, \"boolean\":true, \"null\":null, \"array\":[1,2,3]}";
        Map<String, Object> result = TestUtil.toObjects(json, options, Map.class);

        assertEquals("text", result.get("string"));
        assertEquals(42L, result.get("number"));
        assertEquals(true, result.get("boolean"));
        assertNull(result.get("null"));
        assert DeepEquals.deepEquals(new Object[] {1L, 2L, 3L}, result.get("array"));

        // Nested maps
        String nestedJson = "{\"level1\":{\"level2\":{\"level3\":\"value\"}}}";
        Map<String, Object> nestedResult = TestUtil.toObjects(nestedJson, options, Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> level1 = (Map<String, Object>) nestedResult.get("level1");
        @SuppressWarnings("unchecked")
        Map<String, Object> level2 = (Map<String, Object>) level1.get("level2");
        assertEquals("value", level2.get("level3"));
    }

    @Test
    void testNavigableCollections() {
        ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();

        // NavigableSet
        String setJson = "[5,2,8,1,9]";
        NavigableSet<Long> navSetResult = TestUtil.toObjects(setJson, options, NavigableSet.class);
        assertEquals(1L, navSetResult.first());
        assertEquals(9L, navSetResult.last());
        assertEquals(5L, navSetResult.ceiling(4L));

        // NavigableMap
        String mapJson = "{\"a\":1,\"c\":3,\"b\":2}";
        NavigableMap<String, Long> navMapResult = TestUtil.toObjects(mapJson, options, NavigableMap.class);
        assertEquals("a", navMapResult.firstKey());
        assertEquals("c", navMapResult.lastKey());
        assertEquals("b", navMapResult.ceilingKey("b"));
    }
}