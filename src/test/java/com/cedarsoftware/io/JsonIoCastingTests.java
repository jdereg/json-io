package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class JsonIoCastingTests {

    // Basic primitive and object type tests
    @Test
    public void testPrimitiveNumberCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsNativeJsonObjects().build();

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
    public void testComplexNumberCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsNativeJsonObjects().build();

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
    public void testStringCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsNativeJsonObjects().build();

        assertEquals("test", TestUtil.toObjects("\"test\"", options, null));
        assertEquals("test", TestUtil.toObjects("\"test\"", options, String.class));
        assertEquals("16", TestUtil.toObjects("\"16\"", options, String.class));
    }

    @Test
    public void testDateAndUUIDCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsNativeJsonObjects().build();

        UUID uuid = UUID.randomUUID();
        assertEquals(uuid, TestUtil.toObjects("\"" + uuid + "\"", options, UUID.class));

        String isoDate = "2024-01-01T00:00:00Z";
        Instant instant = Instant.parse(isoDate);
        Date expected = Date.from(instant);
        assertEquals(expected, TestUtil.toObjects("\"" + isoDate + "\"", options, Date.class));
    }

    // Array tests
    @Test
    public void testSimpleArrayCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsNativeJsonObjects().build();

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
    public void testNestedArrayCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsNativeJsonObjects().build();

        // Nested arrays with nulls
        String json = "[[1, null, 3], null, [4, 5, null]]";
        Long[][] expected = new Long[][]{{1L, null, 3L}, null, {4L, 5L, null}};
        Long[][] result = TestUtil.toObjects(json, options, Long[][].class);
        assertArrayEquals(expected, result);
        assert DeepEquals.deepEquals(expected, result);
    }

    // Collection tests
    @Test
    public void testCollectionCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsNativeJsonObjects().build();

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
    public void testNestedCollectionCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsNativeJsonObjects().build();

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
    public void testNestedArrayCastingVariations() {
        ReadOptions options = new ReadOptionsBuilder().returnAsNativeJsonObjects().build();

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
    public void testPrimitiveArrayCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsNativeJsonObjects().build();

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
    public void testNestedArrayWithTypeCoercion() {
        ReadOptions options = new ReadOptionsBuilder().returnAsNativeJsonObjects().build();

        // Test coercion of string numbers to Long
        String json = "[[\"1\", null, \"3\"], null, [\"4\", \"5\", null]]";
        Long[][] expected = new Long[][]{{1L, null, 3L}, null, {4L, 5L, null}};
        Long[][] result = TestUtil.toObjects(json, options, Long[][].class);
        assertArrayEquals(expected, result);
    }

    @Test
    public void testDeeplyNestedArrayCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsNativeJsonObjects().build();

        // Triple nested arrays
        String json = "[[[1], [2, 3]], null, [[4, 5], [6, null]]]";
        Integer[][][] expected = new Integer[][][]{{{1}, {2, 3}}, null, {{4, 5}, {6, null}}};
        Integer[][][] result = TestUtil.toObjects(json, options, Integer[][][].class);
        assertArrayEquals(expected, result);
        assert DeepEquals.deepEquals(expected, result);
    }

    @Test
    public void testMixedNestedArrayCasting() {
        ReadOptions options = new ReadOptionsBuilder().returnAsNativeJsonObjects().build();

        // Nested arrays with mixed types and nulls
        String json = "[[1, null, \"three\"], null, [4.0, true, null]]";
        Object[][] expected = new Object[][]{{1L, null, "three"}, null, {4.0, true, null}};
        Object[][] result = TestUtil.toObjects(json, options, Object[][].class);
        assertArrayEquals(expected, result);
        assert DeepEquals.deepEquals(expected, result);
    }
}