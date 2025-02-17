package com.cedarsoftware.io;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static com.cedarsoftware.util.DeepEquals.deepEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
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
class ArrayTest
{
    @Test
    void testArray()
    {
        ManyArrays obj = new ManyArrays();
        obj.init();
        String json = TestUtil.toJson(obj);
        TestUtil.printLine(json);

        ManyArrays root = TestUtil.toObjects(json, null);
        assert deepEquals(obj, root);
        assertArray(root);
    }

    private void assertArray(ManyArrays root)
    {
        assertNotNull(root._empty_a);
        assertNull(root._empty_b);
        assertEquals(0, root._empty_c.length);
        assertEquals(2, root._empty_d.length);
        assertEquals(new Empty(), root._empty_d[0]);
        assertNull(root._empty_d[1]);
        assertEquals(1, root._empty_e.length);
        assertEquals(0, root._empty_e[0].length);
        assertEquals(4, root._empty_f.length);
        assertEquals(2, root._empty_f[0].length);
        assertEquals(new Empty(), root._empty_f[0][0]);
        assertNull(root._empty_f[0][1]);
        assertNull(root._empty_f[1]);
        assertEquals(0, root._empty_f[2].length);
        assertEquals(1, root._empty_f[3].length);

        assertEquals(root._booleans_a.getClass(), boolean[].class);
        assertEquals(3, root._booleans_a.length);
        assertTrue(root._booleans_a[0]);
        assertFalse(root._booleans_a[1]);
        assertTrue(root._booleans_a[2]);
        assertEquals(0, root._booleans_b.length);
        assertNull(root._booleans_c);
        assertEquals(3, root._booleans_d.length);
        assertTrue(root._booleans_d[0]);
        assertFalse(root._booleans_d[1]);
        assertNull(root._booleans_d[2]);
        assertEquals(0, root._booleans_e.length);
        assertNull(root._booleans_f);
        assertEquals(1, root._booleans_g.length);
        assertNull(root._booleans_g[0]);
        assertEquals(5, root._booleans_h.length);
        assertEquals(1, root._booleans_h[0].length);
        assertEquals(2, root._booleans_h[1].length);
        assertEquals(3, root._booleans_h[2].length);
        assertTrue(root._booleans_h[0][0]);
        assertTrue(root._booleans_h[1][0]);
        assertFalse(root._booleans_h[1][1]);
        assertTrue(root._booleans_h[2][0]);
        assertFalse(root._booleans_h[2][1]);
        assertTrue(root._booleans_h[2][2]);
        assertNull(root._booleans_h[3]);
        assertEquals(0, root._booleans_h[4].length);
        assertNull(root._booleans_i);
        assertEquals(1, root._booleans_j.length);
        assertNull(root._booleans_j[0]);

        assertEquals('a', root._chars_a[0]);
        assertEquals('\t', root._chars_a[1]);
        assertEquals('\u0005', root._chars_a[2]);
        assertEquals(0, root._chars_b.length);
        assertNull(root._chars_c);
        assertEquals('a', root._chars_d[0].charValue());
        assertEquals('\t', root._chars_d[1].charValue());
        assertEquals('\u0006', root._chars_d[2].charValue());
        assertEquals(0, root._chars_e.length);
        assertNull(root._chars_f);

        assertEquals(Byte.MIN_VALUE, root._bytes_a[0]);
        assertEquals(-1, root._bytes_a[1]);
        assertEquals(0, root._bytes_a[2]);
        assertEquals(1, root._bytes_a[3]);
        assertEquals(Byte.MAX_VALUE, root._bytes_a[4]);
        assertEquals(0, root._bytes_b.length);
        assertNull(root._bytes_c);
        assertEquals(Byte.MIN_VALUE, root._bytes_d[0].byteValue());
        assertEquals(-1, root._bytes_d[1].byteValue());
        assertEquals(0, root._bytes_d[2].byteValue());
        assertEquals(1, root._bytes_d[3].byteValue());
        assertEquals(Byte.MAX_VALUE, root._bytes_d[4].byteValue());
        assertEquals(0, root._bytes_e.length);
        assertNull(root._bytes_f);
        assertEquals(3, root._bytes_g.length);
        assertNull(root._bytes_g[0]);
        assertEquals(0, root._bytes_g[1].length);
        assertEquals(1, root._bytes_g[2].length);
        assertEquals(Byte.MAX_VALUE, root._bytes_g[2][0]);
        assertEquals(3, root._bytes_h.length);
        assertNull(root._bytes_h[0]);
        assertEquals(0, root._bytes_h[1].length);
        assertEquals(1, root._bytes_h[2].length);
        assertEquals(Byte.MAX_VALUE, root._bytes_h[2][0].byteValue());
        assertEquals(16, root._bytes_i[0]);

        assertEquals(3, root._chars_g.length);
        assertEquals(3, root._chars_g[0].length);
        assertEquals('a', root._chars_g[0][0]);
        assertEquals('\t', root._chars_g[0][1]);
        assertEquals('\u0004', root._chars_g[0][2]);
        assertNull(root._chars_g[1]);
        assertEquals(0, root._chars_g[2].length);
        assertEquals(3, root._chars_h.length);
        assertEquals(3, root._chars_h[0].length);
        assertEquals('a', (char) root._chars_h[0][0]);
        assertEquals('\t', (char) root._chars_h[0][1]);
        assertEquals('\u0004', (char) root._chars_h[0][2]);
        assertNull(root._chars_h[1]);
        assertEquals(0, root._chars_h[2].length);

        assertEquals(Short.MIN_VALUE, root._shorts_a[0]);
        assertEquals(-1, root._shorts_a[1]);
        assertEquals(0, root._shorts_a[2]);
        assertEquals(1, root._shorts_a[3]);
        assertEquals(Short.MAX_VALUE, root._shorts_a[4]);
        assertEquals(0, root._shorts_b.length);
        assertNull(root._shorts_c);
        assertEquals(Short.MIN_VALUE, root._shorts_d[0].shortValue());
        assertEquals(-1, root._shorts_d[1].shortValue());
        assertEquals(0, root._shorts_d[2].shortValue());
        assertEquals(1, root._shorts_d[3].shortValue());
        assertEquals(Short.MAX_VALUE, root._shorts_d[4].shortValue());
        assertEquals(0, root._shorts_e.length);
        assertNull(root._shorts_f);
        assertEquals(3, root._shorts_g.length);
        assertNull(root._shorts_g[0]);
        assertEquals(0, root._shorts_g[1].length);
        assertEquals(1, root._shorts_g[2].length);
        assertEquals(Short.MAX_VALUE, root._shorts_g[2][0]);
        assertEquals(3, root._shorts_h.length);
        assertNull(root._shorts_h[0]);
        assertEquals(0, root._shorts_h[1].length);
        assertEquals(1, root._shorts_h[2].length);
        assertEquals(Short.MAX_VALUE, root._shorts_h[2][0].shortValue());

        assertEquals(Integer.MIN_VALUE, root._ints_a[0]);
        assertEquals(-1, root._ints_a[1]);
        assertEquals(0, root._ints_a[2]);
        assertEquals(1, root._ints_a[3]);
        assertEquals(Integer.MAX_VALUE, root._ints_a[4]);
        assertEquals(0, root._ints_b.length);
        assertNull(root._ints_c);
        assertEquals(Integer.MIN_VALUE, root._ints_d[0].intValue());
        assertEquals(-1, (int) root._ints_d[1]);
        assertEquals(0, (int) root._ints_d[2]);
        assertEquals(1, (int) root._ints_d[3]);
        assertEquals(Integer.MAX_VALUE, (int) root._ints_d[4]);
        assertEquals(0, root._ints_e.length);
        assertNull(root._ints_f);
        assertEquals(3, root._int_1.length);
        assertEquals(5, root._int_1[0].length);
        assertEquals(Integer.MIN_VALUE, root._int_1[0][0]);
        assertEquals(-1, root._int_1[0][1]);
        assertEquals(0, root._int_1[0][2]);
        assertEquals(1, root._int_1[0][3]);
        assertEquals(Integer.MAX_VALUE, root._int_1[0][4]);
        assertNull(root._int_1[1]);
        assertEquals(4, root._int_1[2].length);
        assertEquals(-1, root._int_1[2][0]);
        assertEquals(0, root._int_1[2][1]);
        assertEquals(1, root._int_1[2][2]);
        assertEquals(2, root._int_1[2][3]);
        assertEquals(3, root._ints_g.length);
        assertNull(root._ints_g[0]);
        assertEquals(0, root._ints_g[1].length);
        assertEquals(1, root._ints_g[2].length);
        assertEquals(Integer.MAX_VALUE, root._ints_g[2][0].intValue());

        assertEquals(Long.MIN_VALUE, root._longs_a[0]);
        assertEquals(-1, root._longs_a[1]);
        assertEquals(0, root._longs_a[2]);
        assertEquals(1, root._longs_a[3]);
        assertEquals(Long.MAX_VALUE, root._longs_a[4]);
        assertEquals(0, root._longs_b.length);
        assertNull(root._longs_c);
        assertEquals(Long.MIN_VALUE, root._longs_d[0].longValue());
        assertEquals(-1, root._longs_d[1].longValue());
        assertEquals(0, root._longs_d[2].longValue());
        assertEquals(1, root._longs_d[3].longValue());
        assertEquals(Long.MAX_VALUE, root._longs_d[4].longValue());
        assertEquals(0, root._longs_e.length);
        assertNull(root._longs_f);
        assertEquals(4, root._longs_1.length);
        assertEquals(3, root._longs_1[0].length);
        assertEquals(0, root._longs_1[1].length);
        assertEquals(1, root._longs_1[2].length);
        assertEquals(1, root._longs_1[3].length);
        assertEquals(3, root._ints_g.length);
        assertNull(root._longs_g[0]);
        assertEquals(0, root._longs_g[1].length);
        assertEquals(1, root._longs_g[2].length);
        assertEquals(Long.MAX_VALUE, root._longs_g[2][0].longValue());

        assertEquals(4, root._floats_a.length);
        assertEquals(0.0f, root._floats_a[0]);
        assertEquals(Float.MIN_VALUE, root._floats_a[1]);
        assertEquals(Float.MAX_VALUE, root._floats_a[2]);
        assertEquals(-1.0f, root._floats_a[3]);
        assertEquals(0, root._floats_b.length);
        assertNull(root._floats_c);
        assertEquals(5, root._floats_d.length);
        assertEquals(root._floats_d[0], 0.0f);
        assertEquals(root._floats_d[1], Float.MIN_VALUE);
        assertEquals(root._floats_d[2], Float.MAX_VALUE);
        assertEquals(root._floats_d[3], -1.0f);
        assertNull(root._floats_d[4]);
        assertEquals(0, root._floats_e.length);
        assertNull(root._floats_f);
        assertNull(root._floats_g[0]);
        assertEquals(0, root._floats_g[1].length);
        assertEquals(1, root._floats_g[2].length);
        assertEquals(Float.MAX_VALUE, root._floats_g[2][0]);
        assertNull(root._floats_h[0]);
        assertEquals(0, root._floats_h[1].length);
        assertEquals(1, root._floats_h[2].length);
        assertEquals(Float.MAX_VALUE, root._floats_h[2][0].floatValue());

        assertEquals(4, root._doubles_a.length);
        assertEquals(0.0, root._doubles_a[0]);
        assertEquals(Double.MIN_VALUE, root._doubles_a[1]);
        assertEquals(Double.MAX_VALUE, root._doubles_a[2]);
        assertEquals(-1.0, root._doubles_a[3]);
        assertEquals(0, root._doubles_b.length);
        assertNull(root._doubles_c);
        assertEquals(5, root._doubles_d.length);
        assertEquals(0.0d, root._doubles_d[0]);
        assertEquals(Double.MIN_VALUE, root._doubles_d[1]);
        assertEquals(Double.MAX_VALUE, root._doubles_d[2]);
        assertEquals(-1.0d, root._doubles_d[3]);
        assertNull(root._doubles_d[4]);
        assertEquals(0, root._doubles_e.length);
        assertNull(root._doubles_f);
        assertNull(root._doubles_g[0]);
        assertEquals(0, root._doubles_g[1].length);
        assertEquals(1, root._doubles_g[2].length);
        assertEquals(Double.MAX_VALUE, root._doubles_g[2][0]);
        assertNull(root._doubles_h[0]);
        assertEquals(0, root._doubles_h[1].length);
        assertEquals(1, root._doubles_h[2].length);
        assertEquals(Double.MAX_VALUE, root._doubles_h[2][0].doubleValue());

        assertNull(root._strings_a[0]);
        assertEquals("\u0007", root._strings_a[1]);
        assertEquals("\t\rfood\n\f", root._strings_a[2]);
        assertEquals("null", root._strings_a[3]);
        assertEquals(4, root._strings_b.length);
        assertEquals(3, root._strings_b[0].length);
        assertEquals("alpha", root._strings_b[0][0]);
        assertEquals("bravo", root._strings_b[0][1]);
        assertEquals("charlie", root._strings_b[0][2]);
        assertEquals(4, root._strings_b[1].length);
        assertNull(root._strings_b[1][0]);
        assertEquals("\u0007", root._strings_b[1][1]);
        assertEquals("\t", root._strings_b[1][2]);
        assertEquals("null", root._strings_b[1][3]);
        assertNull(root._strings_b[2]);
        assertEquals(0, root._strings_b[3].length);

        assertEquals(root._dates_a[0], new Date(0));
        assertEquals(root._dates_a[1], _testDate);
        assertNull(root._dates_a[2]);
        assertNull(root._dates_b[0]);
        assertEquals(0, root._dates_b[1].length);
        assertEquals(1, root._dates_b[2].length);
        assertEquals(root._dates_b[2][0], _testDate);

        assertEquals(10, root._classes_a.length);
        assertEquals(root._classes_a[0], boolean.class);
        assertEquals(root._classes_a[1], char.class);
        assertEquals(root._classes_a[2], byte.class);
        assertEquals(root._classes_a[3], short.class);
        assertEquals(root._classes_a[4], int.class);
        assertEquals(root._classes_a[5], long.class);
        assertEquals(root._classes_a[6], float.class);
        assertEquals(root._classes_a[7], double.class);
        assertNull(root._classes_a[8]);
        assertEquals(root._classes_a[9], String.class);
        assertNull(root._classes_b[0]);
        assertEquals(0, root._classes_b[1].length);
        assertEquals(1, root._classes_b[2].length);
        assertEquals(root._classes_b[2][0], Date.class);

        assertEquals("food", root._stringbuffer_a.toString());
        assertEquals(3, root._stringbuffer_b.length);
        assertEquals("first", root._stringbuffer_b[0].toString());
        assertEquals("second", root._stringbuffer_b[1].toString());
        assertNull(root._stringbuffer_b[2]);
        assertEquals(3, root._stringbuffer_c.length);
        assertNull(root._stringbuffer_c[0]);
        assertEquals(0, root._stringbuffer_c[1].length);
        assertEquals(1, root._stringbuffer_c[2].length);
        assertEquals("sham-wow", root._stringbuffer_c[2][0].toString());

        assertEquals("murder my days one at a time", root._oStringBuffer_a.toString());
        assertEquals(1, root._oStringBuffer_b.length);
        assertEquals("chaiyya chaiyya", root._oStringBuffer_b[0].toString());

        assertEquals(root._testobj_a, new TestObject("food"));
        assertEquals(2, root._testobj_b.length);
        assertEquals("ten", root._testobj_b[0].getName());
        assertEquals("hut", root._testobj_b[1].getName());
        assertEquals(3, root._testobj_c.length);
        assertNull(root._testobj_c[0]);
        assertEquals(0, root._testobj_c[1].length);
        assertEquals(1, root._testobj_c[2].length);
        assertEquals(root._testobj_c[2][0], new TestObject("mighty-mend"));

        assertEquals(1, root._test_a.length);
        assertEquals(1, root._test_b.length);
        assertSame(root._test_a[0], root._test_b);
        assertSame(root._test_b[0], root._test_a);

        assertEquals(15, root._hetero_a.length);
        assertEquals('a', root._hetero_a[0]);
        assertEquals(root._hetero_a[1], Boolean.TRUE);
        assertEquals(root._hetero_a[2], (byte) 9);
        assertEquals(root._hetero_a[3], (short) 9);
        assertEquals(root._hetero_a[4], 9);
        assertEquals(root._hetero_a[5], 9L);
        assertEquals(root._hetero_a[6], 9.9f);
        assertEquals(root._hetero_a[7], 9.9d);
        assertEquals("getStartupInfo", root._hetero_a[8]);
        assertEquals(root._hetero_a[9], _testDate);
        assertEquals(root._hetero_a[10], boolean.class);
        assertNull(root._hetero_a[11]);
        assertEquals("null", root._hetero_a[12]);
        assertEquals(root._hetero_a[13], _CONST_INT);
        assertEquals(root._hetero_a[14], Class.class);

        assertEquals(10, root._testRefs0.length);
        assertEquals(root._testRefs0[0], _testDate);
        assertEquals(root._testRefs0[1], Boolean.FALSE);
        assertEquals(root._testRefs0[2], _CONST_CHAR);
        assertEquals(root._testRefs0[3], _CONST_BYTE);
        assertEquals(root._testRefs0[4], _CONST_SHORT);
        assertEquals(root._testRefs0[5], _CONST_INT);
        assertEquals(root._testRefs0[6], _CONST_LONG);
        assertEquals(root._testRefs0[7], _CONST_FLOAT);
        assertEquals(root._testRefs0[8], _CONST_DOUBLE);
        assertEquals("Happy", root._testRefs0[9]);

        assertEquals(10, root._testRefs1.length);
        assertNotSame(root._testRefs1[0], root._testRefs0[0]);
        assertSame(root._testRefs1[1], root._testRefs0[1]);// Works because we only read in Boolean.TRUE, Boolean.FALSE, or null
        assertSame(root._testRefs1[2], root._testRefs0[2]);
        assertSame(root._testRefs1[3], root._testRefs0[3]);
        assertSame(root._testRefs1[4], root._testRefs0[4]);
        assertSame(root._testRefs1[5], root._testRefs0[5]);
        assertEquals(root._testRefs1[6], root._testRefs0[6]);
        assertNotSame(root._testRefs1[7], root._testRefs0[7]);// Primitive Wrappers are treated like primitives
        assertEquals(root._testRefs1[8], root._testRefs0[8]);
        assertEquals(root._testRefs1[9], root._testRefs0[9]);

        assertTrue(root._arrayO instanceof Object[]);
        Object[] items = (Object[]) root._arrayO;
        assertEquals(5, items.length);
        assertEquals("foo", items[0]);
        assertEquals(Boolean.TRUE, items[1]);
        assertNull(items[2]);
        assertEquals(16L, items[3]);
        assertEquals(3.14, items[4]);

        assertTrue(root._arrayS instanceof String[]);
        String[] strItems = (String[]) root._arrayS;
        assertEquals(2, strItems.length);
        assertEquals("fingers", strItems[0]);
        assertEquals("toes", strItems[1]);

        assertTrue(root._arrayArrayO instanceof Object[]);
        assertTrue(root._arrayArrayO instanceof Object[][]);
        assertFalse(root._arrayArrayO instanceof Object[][][]);

        assertEquals(root._bigInts[0], new BigInteger("-123456789012345678901234567890"));
        assertEquals(root._bigInts[1], new BigInteger("0"));
        assertEquals(root._bigInts[2], new BigInteger("123456789012345678901234567890"));

        assertEquals(root._oBigInts[0], new BigInteger("-123456789012345678901234567890"));
        assertEquals(root._oBigInts[1], new BigInteger("0"));
        assertEquals(root._oBigInts[2], new BigInteger("123456789012345678901234567890"));

        assertEquals(root._bigDecs[0], new BigDecimal("-123456789012345678901234567890.123456789012345678901234567890"));
        assertEquals(root._bigDecs[1], new BigDecimal("0.0"));
        assertEquals(root._bigDecs[2], new BigDecimal("123456789012345678901234567890.123456789012345678901234567890"));

        assertEquals(root._oBigDecs[0], new BigDecimal("-123456789012345678901234567890.123456789012345678901234567890"));
        assertEquals(root._oBigDecs[1], new BigDecimal("0.0"));
        assertEquals(root._oBigDecs[2], new BigDecimal("123456789012345678901234567890.123456789012345678901234567890"));
    }

    @Test
    void testReconstituteObjectArray()
    {
        Date now = new Date();
        TestObject to = new TestObject("football");
        TimeZone tz = TimeZone.getTimeZone("EST");
        Collection<Object> col = new ArrayList<>();
        col.add("Collection inside array");
        col.add(tz);
        col.add(now);
        Object[] objs = new Object[]{now, 123.45d, 0.04f, "This is a string", null, true, to, tz, col, (short) 7, (byte) -127};
        Object[] two = new Object[]{objs, "bella", objs};
        String json0 = TestUtil.toJson(two);

        Object[] array = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        String json1 = TestUtil.toJson(array);

        // Read back into typed Java objects, the Maps of Maps versus what was dumped out
        Object[] result = TestUtil.toObjects(json1, null);
        assertEquals(3, result.length);
        Object[] arr1 = (Object[]) result[0];
        assertEquals(11, arr1.length);
        String bella = (String) result[1];
        Object[] arr2 = (Object[]) result[2];
        assertSame(arr1, arr2);
        assertEquals("bella", bella);
        assertEquals(now, arr1[0]);
        assertEquals(123.45d, arr1[1]);
        assertEquals(0.04f, arr1[2]);
        assertEquals("This is a string", arr1[3]);
        assertNull(arr1[4]);
        assertSame(arr1[5], Boolean.TRUE);
        assertEquals(to, arr1[6]);
        assertEquals(tz, arr1[7]);
        List c = (List) arr1[8];
        assertEquals("Collection inside array", c.get(0));
        assertEquals(tz, c.get(1));
        assertEquals(now, c.get(2));
        assertEquals(7, (short) (Short) arr1[9]);
        assertEquals(-127, (byte) (Byte) arr1[10]);
        assertEquals(json0, json1);

        ManyArrays ta = new ManyArrays();
        ta.init();
        json0 = TestUtil.toJson(ta);
        TestUtil.printLine("json0=" + json0);

        Map map = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        json1 = TestUtil.toJson(map);
        TestUtil.printLine("json1=" + json1);

        assertEquals(json0, json1);
    }

    @Test
    void testReconstituteObjectArrayTypes()
    {
        TestUtil.printLine("testReconstituteObjectArrayTypes");
        Object[] bytes = new Object[]{_CONST_BYTE, _CONST_BYTE};
        testReconstituteArrayHelper(bytes);
        Byte[] Bytes = new Byte[]{_CONST_BYTE, _CONST_BYTE};
        testReconstituteArrayHelper(Bytes);
        Byte[] bites = new Byte[]{_CONST_BYTE, _CONST_BYTE};
        testReconstituteArrayHelper(bites);

        Object[] shorts = new Object[]{_CONST_SHORT, _CONST_SHORT};
        testReconstituteArrayHelper(shorts);
        Short[] Shorts = new Short[]{_CONST_SHORT, _CONST_SHORT};
        testReconstituteArrayHelper(Shorts);
        Short[] shortz = new Short[]{_CONST_SHORT, _CONST_SHORT};
        testReconstituteArrayHelper(shortz);

        Object[] ints = new Object[]{_CONST_INT, _CONST_INT};
        testReconstituteArrayHelper(ints);
        Integer[] Ints = new Integer[]{_CONST_INT, _CONST_INT};
        testReconstituteArrayHelper(Ints);
        Integer[] intz = new Integer[]{_CONST_INT, _CONST_INT};
        testReconstituteArrayHelper(intz);

        Object[] longs = new Object[]{_CONST_LONG, _CONST_LONG};
        testReconstituteArrayHelper(longs);
        Long[] Longs = new Long[]{_CONST_LONG, _CONST_LONG};
        testReconstituteArrayHelper(Longs);
        Long[] longz = new Long[]{_CONST_LONG, _CONST_LONG};
        testReconstituteArrayHelper(longz);

        Object[] floats = new Object[]{_CONST_FLOAT, _CONST_FLOAT};
        testReconstituteArrayHelper(floats);
        Float[] Floats = new Float[]{_CONST_FLOAT, _CONST_FLOAT};
        testReconstituteArrayHelper(Floats);
        Float[] floatz = new Float[]{_CONST_FLOAT, _CONST_FLOAT};
        testReconstituteArrayHelper(floatz);

        Object[] doubles = new Object[]{_CONST_DOUBLE, _CONST_DOUBLE};
        testReconstituteArrayHelper(doubles);
        Double[] Doubles = new Double[]{_CONST_DOUBLE, _CONST_DOUBLE};
        testReconstituteArrayHelper(Doubles);
        Double[] doublez = new Double[]{_CONST_DOUBLE, _CONST_DOUBLE};
        testReconstituteArrayHelper(doublez);

        Object[] booleans = new Object[]{Boolean.TRUE, Boolean.TRUE};
        testReconstituteArrayHelper(booleans);
        Boolean[] Booleans = new Boolean[]{Boolean.TRUE, Boolean.TRUE};
        testReconstituteArrayHelper(Booleans);
        Boolean[] booleanz = new Boolean[]{true, true};
        testReconstituteArrayHelper(booleanz);

        Object[] chars = new Object[]{'J', 'J'};
        testReconstituteArrayHelper(chars);
        Character[] Chars = new Character[]{'S', 'S'};
        testReconstituteArrayHelper(Chars);
        char[] charz = new char[]{'R', 'R'};
        testReconstituteArrayHelper(charz);

        Object[] classes = new Object[]{LinkedList.class, LinkedList.class};
        testReconstituteArrayHelper(classes);
        Class<?>[] Classes = new Class<?>[]{LinkedList.class, LinkedList.class};
        testReconstituteArrayHelper(Classes);

        Date now = new Date();
        Object[] dates = new Object[]{now, now};
        testReconstituteArrayHelper(dates);
        Date[] Dates = new Date[]{now, now};
        testReconstituteArrayHelper(Dates);

        BigDecimal pi = new BigDecimal("3.1415926535897932384626433");
        Object[] bigDecimals = new Object[]{pi, pi};
        testReconstituteArrayHelper(bigDecimals);
        BigDecimal[] BigDecimals = new BigDecimal[]{pi, pi};
        testReconstituteArrayHelper(BigDecimals);

        String s = "json-io";
        Object[] strings = new Object[]{s, s};
        testReconstituteArrayHelper(strings);
        String[] Strings = new String[]{s, s};
        testReconstituteArrayHelper(Strings);
    }

    @Test
    void testReconstitute_withCalendars_whenArrayTypeisObject() {
        GregorianCalendar cal = (GregorianCalendar) Calendar.getInstance(TimeZone.getTimeZone("GMT-05:00"));
        Object[] calendars = new Object[]{cal, cal};
        testReconstituteArrayHelper(calendars);
    }

    @Test
    void testReconsitutute_withCalendars_whenArrayTypeIsCalendar() {
        GregorianCalendar cal = (GregorianCalendar) Calendar.getInstance(TimeZone.getTimeZone("GMT-05:00"));
        Calendar[] Calendars = new Calendar[]{cal, cal};
        testReconstituteArrayHelper(Calendars);
    }

    @Test
    void testReconstitute_withGregorianCalendars_whenArrayTypeIsCalendar() {
        GregorianCalendar cal = (GregorianCalendar) Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
        GregorianCalendar[] calendarz = new GregorianCalendar[]{cal, cal};
        testReconstituteArrayHelper(calendarz);
    }

    private void testReconstituteArrayHelper(Object foo)
    {
        assertEquals(2, Array.getLength(foo));
        String json0 = TestUtil.toJson(foo);
        TestUtil.printLine("json0=" + json0);

        Object array = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        String json1 = TestUtil.toJson(array);
        TestUtil.printLine("json1=" + json1);
        assertEquals(json0, json1);
    }

    @Test
    void testReconstituteEmptyArray()
    {
        Object[] empty = new Object[0];
        String json0 = TestUtil.toJson(empty);
        TestUtil.printLine("json0=" + json0);

        empty = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assertNotNull(empty);
        assertNotNull(empty);
        assertEquals(0, empty.length);
        String json1 = TestUtil.toJson(empty);
        TestUtil.printLine("json1=" + json1);

        assertEquals(json0, json1);

        Object[] list = new Object[]{empty, empty};
        json0 = TestUtil.toJson(list);
        TestUtil.printLine("json0=" + json0);

        list = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assertNotNull(list);
        assertEquals(2, list.length);
        JsonObject e1 = (JsonObject) list[0];
        JsonObject e2 = (JsonObject) list[1];
        assertEquals(e1.getItems(), e2.getItems());
        assertEquals(0, e1.size());

        json1 = TestUtil.toJson(list);
        TestUtil.printLine("json1=" + json1);
        assertEquals(json0, json1);
    }

    @Test
    void testReconstituteTypedArray()
    {
        String[] strs = new String[]{"tom", "dick", "harry"};
        Object[] objs = new Object[]{strs, "a", strs};
        String json0 = TestUtil.toJson(objs);
        TestUtil.printLine("json0=" + json0);
        Object array = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        String json1 = TestUtil.toJson(array);
        TestUtil.printLine("json1=" + json1);

        Object[] result = TestUtil.toObjects(json1, null);
        assertEquals(3, result.length);
        assertSame(result[0], result[2]);
        assertEquals("a", result[1]);
        String[] sa = (String[]) result[0];
        assertEquals(3, sa.length);
        assertEquals("tom", sa[0]);
        assertEquals("dick", sa[1]);
        assertEquals("harry", sa[2]);
        String json2 = TestUtil.toJson(result);
        TestUtil.printLine("json2=" + json2);
        assertEquals(json0, json1);
        assertEquals(json1, json2);
    }

    @Test
    void testReconstituteArray()
    {
        ManyArrays testArray = new ManyArrays();
        testArray.init();
        String json0 = TestUtil.toJson(testArray);
        TestUtil.printLine("json0=" + json0);
        Map testArray2 = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);

        String json1 = TestUtil.toJson(testArray2);
        TestUtil.printLine("json1=" + json1);

        ManyArrays testArray3 = TestUtil.toObjects(json1, null);
        assertArray(testArray3);// Re-written from Map of Maps and re-loaded correctly
        assertEquals(json0, json1);
    }

    @Test
    void testReconstituteEmptyObject()
    {
        Empty empty = new Empty();
        String json0 = TestUtil.toJson(empty);
        TestUtil.printLine("json0=" + json0);

        Map m = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assertTrue(m.isEmpty());

        String json1 = TestUtil.toJson(m);
        TestUtil.printLine("json1=" + json1);
        assertEquals(json0, json1);
    }

    @Test
    void testAlwaysShowType()
    {
        ManyArrays ta = new ManyArrays();
        ta.init();
        WriteOptions options = new WriteOptionsBuilder().showTypeInfoAlways().build();

        String json0 = TestUtil.toJson(ta, options);
        ManyArrays thatTa = TestUtil.toObjects(json0, null);
        assertTrue(deepEquals(ta, thatTa));
        String json1 = TestUtil.toJson(ta);
        TestUtil.printLine("json0 = " + json0);
        TestUtil.printLine("json1 = " + json1);
        assertTrue(json0.length() > json1.length());
    }

    @Test
    void testArraysAsList()
    {
        List<String> strs = new ArrayList<>(Arrays.asList("alpha", "bravo", "charlie"));
        String json = TestUtil.toJson(strs);
        List<String> foo = TestUtil.toObjects(json, null);
        assertEquals(3, foo.size());
        assertEquals("alpha", foo.get(0));
        assertEquals("charlie", foo.get(2));
    }

    @Test
    void testBadArray()
    {
        String json = "[1, 10, 100,]";
        Object[] array = TestUtil.toObjects(json, null);
        assertEquals(3, array.length);
        assertEquals(array[0], 1L);
        assertEquals(array[1], 10L);
        assertEquals(array[2], 100L);
    }


    private static Stream<Arguments> charArrayParams() {
        return Stream.of(
                Arguments.of(new char[] { 'a', '\t', '\u0005' }, "{\"@type\":\"char[]\",\"@items\":[\"a\\t\\u0005\"]}"),
                Arguments.of(new char[] { 'Z', '1', '\0' }, "{\"@type\":\"char[]\",\"@items\":[\"Z1\\u0000\"]}")
        );
    }

    @ParameterizedTest
    @MethodSource("charArrayParams")
    void testCharArray_toJsonString(char[] test, String expected) {
        String json = TestUtil.toJson(test);
        assertThat(json).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("charArrayParams")
    void testJsonString_toCharArray(char[] expected, String test) {
        char[] chars = TestUtil.toObjects(test, char[].class);
        assertThat(chars).isEqualTo(expected);
    }

    private static Stream<Arguments> charObjectArrayParams() {
        return Stream.of(
                Arguments.of(new Character[] { 'a', '\t', '\u0005' }, "{\"@type\":\"Character[]\",\"@items\":[\"a\",\"\\t\",\"\\u0005\"]}"),
                Arguments.of(new Character[] { 'Z', '1', '\0' }, "{\"@type\":\"Character[]\",\"@items\":[\"Z\",\"1\",\"\\u0000\"]}")
        );
    }

    @ParameterizedTest
    @MethodSource("charObjectArrayParams")
    void testCharArray_toJsonString(Character[] test, String expected) {
        String json = TestUtil.toJson(test);
        assertThat(json).isEqualTo(expected);
    }


    @Test
    void testCharArray()
    {
        CharArrayTest cat = new CharArrayTest();
        cat.chars_a = new Character[] {'a', '\t', '\u0005'};
        cat.chars_b = new Character[] {'b', '\t', '\u0002'};

        String json0 = TestUtil.toJson(cat);
        TestUtil.printLine(json0);

        CharArrayTest cat2 = TestUtil.toObjects(json0, null);
        Character[] chars_a = cat2.chars_a;
        assertEquals(chars_a.length, 3);
        assertEquals(chars_a[0], 'a');
        assertEquals(chars_a[1], '\t');
        assertEquals(chars_a[2], '\u0005');

        Character[] chars_b = cat2.chars_b;
        assertEquals(chars_b.length, 3);
        assertEquals('b', (char) chars_b[0]);
        assertEquals('\t', (char) chars_b[1]);
        assertEquals('\u0002', (char) chars_b[2]);

        String json1 = TestUtil.toJson(cat);
        TestUtil.printLine(json1);

        cat2 = TestUtil.toObjects(json0, null);
        chars_a = cat2.chars_a;
        assertEquals(chars_a.length, 3);
        assertEquals(chars_a[0], 'a');
        assertEquals(chars_a[1], '\t');
        assertEquals(chars_a[2], '\u0005');

        chars_b = cat2.chars_b;
        assertEquals(chars_b.length, 3);
        assertEquals('b', (char) chars_b[0]);
        assertEquals('\t', (char) chars_b[1]);
        assertEquals('\u0002', (char) chars_b[2]);
    }

    @Test
    void testEmptyArray()
    {
        String json = "{\"@type\":\"[Ljava.lang.String;\"}";
        String[] s = TestUtil.toObjects(json, null);
        assertNull(s);
    }

    @Test
    void testMultiDimensionalArrays()
    {
        int[][][][] x = {{{{0,1},{0,1}},{{0,1},{0,1}}},{{{0,1},{0,1}},{{0,1},{0,1}}}};

        for (int a = 0; a < 2 ; a++)
        {
            for (int b = 0; b < 2 ; b++)
            {
                for (int c = 0; c < 2 ; c++)
                {
                    for (int d = 0; d < 2 ; d++)
                    {
                        x[a][b][c][d] = a + b + c + d;
                    }
                }
            }
        }

        String json = TestUtil.toJson(x);
        int[][][][] y = TestUtil.toObjects(json, null);
        
        for (int a = 0; a < 2 ; a++)
        {
            for (int b = 0; b < 2 ; b++)
            {
                for (int c = 0; c < 2 ; c++)
                {
                    for (int d = 0; d < 2 ; d++)
                    {
                        assertEquals(y[a][b][c][d], a + b + c + d);
                    }
                }
            }
        }

        Integer[][][][] xx = {{{{0,1},{0,1}},{{0,1},{0,1}}},{{{0,1},{0,1}},{{0,1},{0,1}}}};

        for (int a = 0; a < 2 ; a++)
        {
            for (int b = 0; b < 2 ; b++)
            {
                for (int c = 0; c < 2 ; c++)
                {
                    for (int d = 0; d < 2 ; d++)
                    {
                        xx[a][b][c][d] = a + b + c + d;
                    }
                }
            }
        }

        json = TestUtil.toJson(xx);
        Integer[][][][] yy = TestUtil.toObjects(json, null);

        for (int a = 0; a < 2 ; a++)
        {
            for (int b = 0; b < 2 ; b++)
            {
                for (int c = 0; c < 2 ; c++)
                {
                    for (int d = 0; d < 2 ; d++)
                    {
                        assertEquals((int) yy[a][b][c][d], a + b + c + d);
                    }

                }
            }
        }
    }

    @Test
    void testObjectArrayStringReference()
    {
        String s = "dogs";
        String json = TestUtil.toJson(new Object[]{s, s});
        TestUtil.printLine("json = " + json);
        Object[] o = TestUtil.toObjects(json, null);
        assertEquals(2, o.length);
        assertEquals("dogs", o[0]);
        assertSame(o[0], o[1]); // Strings are LRU cached (instance folding).
    }

    @Test
    void testStringArrayStringReference()
    {
        String s = "dogs";
        String json = TestUtil.toJson(new String[]{s, s});
        TestUtil.printLine("json = " + json);
        String[] o = TestUtil.toObjects(json, null);
        assertEquals(2, o.length);
        assertEquals("dogs", o[0]);
        assertSame(o[0], o[1]); // Strings are LRU Cached (instance folding).
    }

    @Test
    void testReferencedEmptyArray()
    {
        String[] array = new String[0];
        Object[] refArray = new Object[]{array};
        String json = TestUtil.toJson(refArray);
        TestUtil.printLine("json=" + json);
        Object[] oa = TestUtil.toObjects(json, null);
        assertEquals(oa[0].getClass(), String[].class);
        assertEquals(0, ((String[]) oa[0]).length);
    }

    @Test
    void testUntypedArray()
    {
        Object[] args = TestUtil.toObjects("[\"string\",17, null, true, false, [], -1273123,32131, 1e6, 3.14159, -9223372036854775808, 9223372036854775807]", null);

        for (int i = 0; i < args.length; i++)
        {
            TestUtil.printLine("args[" + i + "]=" + args[i]);
            if (args[i] != null)
            {
                TestUtil.printLine("args[" + i + "]=" + args[i].getClass().getName());
            }
        }

        assertEquals("string", args[0]);
        assertEquals(17L, args[1]);
        assertNull(args[2]);
        assertEquals(args[3], Boolean.TRUE);
        assertEquals(args[4], Boolean.FALSE);
        assertTrue(args[5].getClass().isArray());
        assertEquals(-1273123L, args[6]);
        assertEquals(32131L, args[7]);
        assertEquals(args[8], 1000000d);
        assertEquals(args[9], 3.14159d);
        assertEquals(Long.MIN_VALUE, args[10]);
        assertEquals(Long.MAX_VALUE, args[11]);
    }

    @Test
    void testArrayListSaveAndRestoreGenericJSON() throws Throwable
    {
        List<Integer> numbers = new ArrayList<>();
        numbers.add(10);
        numbers.add(20);
        numbers.add(30);
        numbers.add(40);

        // Serialize the ArrayList to Json
        String json = TestUtil.toJson(numbers, new WriteOptionsBuilder().showTypeInfoNever().build());

        TestUtil.printLine("Numbers ArrayList = " + numbers + ". Numbers to json = " + json);
        // This prints: "Numbers ArrayList = [10, 20, 30, 40]. Numbers to json = [10,20,30,40]"

        Object[] restoredNumbers;
        restoredNumbers = TestUtil.toObjects(json, null);
        assert deepEquals(Arrays.asList(restoredNumbers), numbers);
    }

    @Test
    void testToEnsureAtEIsWorking() throws Throwable
    {
        String[] testArray = new String[1];
        testArray[0] = "Test";
        String testOut = TestUtil.toJson(testArray, new WriteOptionsBuilder().shortMetaKeys(true).build());
        TestUtil.printLine(testOut);

        // The line below blew-up when the @i was being written by JsonWriter instead of @e in shorthand.
        Object object = TestUtil.toObjects(testOut, null);
    }

    @Test
    void testEmptyArrayAsObjectArrayAndList()
    {
        String json = "[]";
        Object[] empty = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), Object[].class);
        assertNotNull(empty);
        assertEquals(0, empty.length);

        List<?> emptyList = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), ArrayList.class);
        assertNotNull(emptyList);
        assertTrue(emptyList.isEmpty());
    }

    @Test
    void testEmptySubArray()
    {
        String json = "[[], []]";
        Object[] outer = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), Object[].class);
        assertEquals(2, outer.length);

        Object[] empty1 = (Object[])outer[0];
        assertEquals(0, empty1.length);
        Object[] empty2 = (Object[])outer[1];
        assertEquals(0, empty2.length);

        // TODO: This test should work
//        List<List<?>> outerList = JsonIo.toObjectsGeneric(json, new ReadOptionsBuilder().build(), new TypeHolder<List<List<?>>>(){});
//        assertEquals(2, outerList.size());
//
//        List<?> emptyList1 = (List)outer[0];
//        assertEquals(0, emptyList1.size());
//        List<?> emptyList2 = (List)outer[1];
//        assertEquals(0, emptyList2.size());
    }

    @Test
    void testArrayWithCircularReference()
    {
        TestArray ta = new TestArray();
        String json0 = TestUtil.toJson(ta, new WriteOptionsBuilder().build());

        // First convert to Maps
        Object objAsMap = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        String json1 = TestUtil.toJson(objAsMap);

        // Then convert back to objects
        Object obj = TestUtil.toObjects(json1, new ReadOptionsBuilder().build(), null);
        String json2 = TestUtil.toJson(obj);

        assertEquals(json0, json1);
        assertEquals(json1, json2);
    }
    
    private static Stream<Arguments> allShowTypeInfos() {
        return Stream.of(
                Arguments.of(new WriteOptionsBuilder().showTypeInfoNever().build()),
                Arguments.of(new WriteOptionsBuilder().showTypeInfoAlways().build()),
                Arguments.of(new WriteOptionsBuilder().build())
        );
    }

    @ParameterizedTest
    @MethodSource("allShowTypeInfos")
    void testObjectArray_withLongs_doesNotExportTypes(WriteOptions options) throws Throwable {
        Object[] array = {10L, 20L, 30L};

        String json = TestUtil.toJson(array, options);
        assertThat(json).isEqualTo("[10,20,30]");
    }

    @ParameterizedTest
    @MethodSource("allShowTypeInfos")
    void testObjectArray_withDoubles_doesNotExportTypes(WriteOptions options) throws Throwable {
        Object[] array = {10.2d, 20.5d, 30.8d};

        String json = TestUtil.toJson(array, options);
        assertThat(json).isEqualTo("[10.2,20.5,30.8]");
    }

    @ParameterizedTest
    @MethodSource("allShowTypeInfos")
    void testObjectArray_withStrings_doesNotExportTypes(WriteOptions options) throws Throwable {
        Object[] array = {"foo", "bar", "qux"};

        String json = TestUtil.toJson(array, options);
        assertThat(json).isEqualTo("[\"foo\",\"bar\",\"qux\"]");
    }

    @ParameterizedTest
    @MethodSource("allShowTypeInfos")
    void testObjectArray_withBooleans_doesNotExportTypes(WriteOptions options) throws Throwable {
        Object[] array = {false, true, false};

        String json = TestUtil.toJson(array, options);
        assertThat(json).isEqualTo("[false,true,false]");
    }

    @ParameterizedTest
    @MethodSource("allShowTypeInfos")
    void testObjectArray_emptyArray_doesNotExportTypes(WriteOptions options) throws Throwable {
        Object[] array = {};

        String json = TestUtil.toJson(array, options);
        assertThat(json).isEqualTo("[]");
    }

    @ParameterizedTest
    @MethodSource("allShowTypeInfos")
    void testObjectArray_withNestedObjectArray_doesNotExportTypes(WriteOptions options) throws Throwable {
        Object[] array = {new Object[0], new Object[0], new Object[0]};

        String json = TestUtil.toJson(array, options);
        assertThat(json).isEqualTo("[[],[],[]]");
    }

    private static Stream<Arguments> alwaysShowAndMinimalShow() {
        return Stream.of(
                Arguments.of(new WriteOptionsBuilder().showTypeInfoAlways().build()),
                Arguments.of(new WriteOptionsBuilder().build())
        );
    }

    @ParameterizedTest
    @MethodSource("alwaysShowAndMinimalShow")
    void testObjectArray_withInts_doesNotExportTypes(WriteOptions options) throws Throwable {
        Object[] array = {10, 20, 30};

        String json = TestUtil.toJson(array, options);
        assertThat(json).isEqualTo("[{\"@type\":\"Integer\",\"value\":10},{\"@type\":\"Integer\",\"value\":20},{\"@type\":\"Integer\",\"value\":30}]");
    }

    private static Stream<Arguments> integerVariants() {
        return Stream.of(
                Arguments.of((byte)0x0A, (byte)0x14, (byte)0x1E),
                Arguments.of((short) 10, (short) 20, (short) 30),
                Arguments.of(10, 20, 30),
                Arguments.of(10L, 20L, 30L),
                Arguments.of(Integer.valueOf(10), Integer.valueOf(20), Integer.valueOf(30)),
                Arguments.of(Byte.valueOf((byte) 10), Byte.valueOf((byte) 20), Byte.valueOf((byte) 30)),
                Arguments.of(Long.valueOf(10), Long.valueOf(20), Long.valueOf(30)),
                Arguments.of(Short.valueOf((short) 10), Short.valueOf((short) 20), Short.valueOf((short) 30))
        );
    }

    @ParameterizedTest
    @MethodSource("integerVariants")
    void testObjectArray_withIntegerVariants_andNoTyping_outputsTheSame(Object one, Object two, Object three) {
        WriteOptions options = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = TestUtil.toJson(new Object[]{one, two, three}, options);
        assertThat(json).isEqualTo("[10,20,30]");
    }

    private static Stream<Arguments> alwaysShowAndMinimalShowWithLongAsStrings() {
        return Stream.of(
                Arguments.of(new WriteOptionsBuilder().showTypeInfoAlways().writeLongsAsStrings(true).build()),
                Arguments.of(new WriteOptionsBuilder().writeLongsAsStrings(true).build())
        );
    }

    @ParameterizedTest
    @MethodSource("alwaysShowAndMinimalShowWithLongAsStrings")
    void testObjectArray_withLongsWrittenAsStrings_andMinimalOrAlwaysShow_writesTypes(WriteOptions writeOptions) throws Throwable {
        Object[] array = {10L, 20L, 30L};

        String json = TestUtil.toJson(array, writeOptions);
        assertThat(json).isEqualTo("[{\"@type\":\"Long\",\"value\":\"10\"},{\"@type\":\"Long\",\"value\":\"20\"},{\"@type\":\"Long\",\"value\":\"30\"}]");
    }

    private static Stream<Arguments> stringVariants() {
        return Stream.of(
                Arguments.of("10", "20", "30"),  // Only keep string variants
                Arguments.of(new StringBuilder("10"), new StringBuilder("20"), new StringBuilder("30")),
                Arguments.of(new StringBuffer("10"), new StringBuffer("20"), new StringBuffer("30"))
        );
    }

    @ParameterizedTest
    @MethodSource("stringVariants")
    void testObjectArray_withLongsWrittenAsStrings_andNeverShowTypes_writesLikeStringVariants(Object arg1, Object arg2, Object arg3) {
        // The variant we're comparing against (string-like values)
        Object[] variantArray = {arg1, arg2, arg3};
        String variantJson = TestUtil.toJson(variantArray, new WriteOptionsBuilder().showTypeInfoNever().build());

        // The Long array we're testing
        Object[] longArray = {10L, 20L, 30L};
        WriteOptions longOptions = new WriteOptionsBuilder()
                .writeLongsAsStrings(true)
                .showTypeInfoNever()
                .build();

        String longJson = TestUtil.toJson(longArray, longOptions);
        assertThat(longJson).isEqualTo(variantJson);

        Collection<String> col = TestUtil.toObjects(longJson, ReadOptionsBuilder.getDefaultReadOptions(), Set.class);
        assert col.size() == 3;
        assert col.containsAll(Arrays.asList("10", "20", "30"));

        String[] values = TestUtil.toObjects(longJson, ReadOptionsBuilder.getDefaultReadOptions(), String[].class);
        assert values.length == 3;
        assert values[0].equals("10");
        assert values[1].equals("20");
        assert values[2].equals("30");
    }

    private static final Date _testDate = new Date();
    private static final Character _CONST_CHAR = 'j';
    private static final Byte _CONST_BYTE = (byte) 16;
    private static final Short _CONST_SHORT = (short) 26;
    private static final Integer _CONST_INT = 36;
    private static final Long _CONST_LONG = 46L;
    private static final Float _CONST_FLOAT = 56.56f;
    private static final Double _CONST_DOUBLE = 66.66d;

    static class CharArrayTest
    {
        public Character[] getChars_a()
        {
            return chars_a;
        }

        public void setChars_a(Character[] chars_a)
        {
            this.chars_a = chars_a;
        }

        public Character[] getChars_b()
        {
            return chars_b;
        }

        public void setChars_b(Character[] chars_b)
        {
            this.chars_b = chars_b;
        }

        private Character[] chars_a;
        private Character[]  chars_b;
    }

    private static class Empty implements Serializable
    {
        public static double multiply(double x, double y)
        {
            return x * y;
        }

        public boolean equals(Object other)
        {
            return other instanceof Empty;
        }
    }

    public static class ManyArrays implements Serializable
    {
        public ManyArrays()
        {
        }

        public void init()
        {
            _empty_a = new Empty();
            _empty_b = null;
            _empty_c = new Empty[0];
            _empty_d = new Empty[]{new Empty(), null};

            _empty_e = new Empty[][] {{}};
            _empty_f = new Empty[][] {{new Empty(), null}, null, {}, {new Empty()}};
            
            _booleans_a = new boolean[]{true, false, true};
            _booleans_b = new boolean[]{};
            _booleans_c = null;
            _booleans_d = new Boolean[]{Boolean.TRUE, Boolean.FALSE, null};
            _booleans_e = new Boolean[0];
            _booleans_f = null;
            _booleans_g = new Boolean[]{null};
            _booleans_h = new boolean[][]{{true}, {true, false}, {true, false, true}, null, {}};

            _booleans_i = null;
            _booleans_j = new boolean[][]{null};

            _chars_a = new char[] {'a', '\t', '\u0005'};
            _chars_b = new char[]{};
            _chars_c = null;
            _chars_d = new Character[] {'a', '\t', '\u0006'};
            _chars_e = new Character[0];
            _chars_f = null;
            _chars_g = new char[][]{{'a', '\t', '\u0004'}, null, {}};
            _chars_h = new Character[][]{{'a', '\t', '\u0004'}, null, {}};

            _bytes_a = new byte[]{Byte.MIN_VALUE, -1, 0, 1, Byte.MAX_VALUE};
            _bytes_b = new byte[0];
            _bytes_c = null;
            _bytes_d = new Byte[]{Byte.MIN_VALUE, (byte) -1, (byte) 0, (byte) 1, Byte.MAX_VALUE};
            _bytes_e = new Byte[0];
            _bytes_f = null;
            _bytes_g = new byte[][]{null, {}, {Byte.MAX_VALUE}};
            _bytes_h = new Byte[][]{null, {}, {Byte.MAX_VALUE}};
            _bytes_i = new byte[]{16};

            _shorts_a = new short[]{Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE};
            _shorts_b = new short[0];
            _shorts_c = null;
            _shorts_d = new Short[]{Short.MIN_VALUE, (short) -1, (short) 0, (short) 1, Short.MAX_VALUE};
            _shorts_e = new Short[0];
            _shorts_f = null;
            _shorts_g = new short[][]{null, {}, {Short.MAX_VALUE}};
            _shorts_h = new Short[][]{null, {}, {Short.MAX_VALUE}};

            _ints_a = new int[]{Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};
            _ints_b = new int[0];
            _ints_c = null;
            _int_1 = new int[][]{{Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE}, null, {-1, 0, 1, 2}};
            _ints_d = new Integer[]{Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};
            _ints_e = new Integer[0];
            _ints_f = null;
            _ints_g = new Integer[][]{null, {}, {Integer.MAX_VALUE}};

            _longs_a = new long[]{Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE};
            _longs_b = new long[0];
            _longs_c = null;
            _longs_1 = new long[][][]{{{-1}, {0, 1}, {1, 2, 3}}, {}, {{1, 2}}, {{}}};
            _longs_d = new Long[]{Long.MIN_VALUE, (long) -1, 0L, 1L, Long.MAX_VALUE};
            _longs_e = new Long[0];
            _longs_f = null;
            _longs_g = new Long[][] {null, {}, {Long.MAX_VALUE}};

            _floats_a = new float[]{0.0f, Float.MIN_VALUE, Float.MAX_VALUE, -1.0f};
            _floats_b = new float[0];
            _floats_c = null;
            _floats_d = new Float[]{0.0f, Float.MIN_VALUE, Float.MAX_VALUE, -1.0f, null};
            _floats_e = new Float[0];
            _floats_f = null;
            _floats_g = new float[][]{null, {}, {Float.MAX_VALUE}};
            _floats_h = new Float[][]{null, {}, {Float.MAX_VALUE}};

            _doubles_a = new double[]{0.0d, Double.MIN_VALUE, Double.MAX_VALUE, -1.0d};
            _doubles_b = new double[0];
            _doubles_c = null;
            _doubles_d = new Double[]{0.0d, Double.MIN_VALUE, Double.MAX_VALUE, -1.0d, null};
            _doubles_e = new Double[0];
            _doubles_f = null;
            _doubles_g = new double[][]{null, {}, {Double.MAX_VALUE}};
            _doubles_h = new Double[][]{null, {}, {Double.MAX_VALUE}};

            _strings_a = new String[]{null, "\u0007", "\t\rfood\n\f", "null"};
            _strings_b = new String[][]{{"alpha", "bravo", "charlie"}, {null, "\u0007", "\t", "null"}, null, {}};

            _dates_a = new Date[]{new Date(0), _testDate, null};
            _dates_b = new Date[][] {null, {}, {_testDate}};

            _classes_a = new Class[]{boolean.class, char.class, byte.class, short.class, int.class, long.class, float.class, double.class, null, String.class};
            _classes_b = new Class[][] {null, {}, {Date.class}};

            _stringbuffer_a = new StringBuffer("food");
            _stringbuffer_b = new StringBuffer[3];
            _stringbuffer_b[0] = new StringBuffer("first");
            _stringbuffer_b[1] = new StringBuffer("second");
            _stringbuffer_b[2] = null;
            _stringbuffer_c = new StringBuffer[][]{null, {}, {new StringBuffer("sham-wow")}};
            _oStringBuffer_a = new StringBuffer("murder my days one at a time");
            _oStringBuffer_b = new Object[]{new StringBuffer("chaiyya chaiyya")};

            _testobj_a = new TestObject("food");
            _testobj_b = new TestObject[]{new TestObject("ten"), new TestObject("hut")};
            _testobj_c = new TestObject[][] {null, {}, {new TestObject("mighty-mend")}};

            _test_a = new Object[1];
            _test_b = new Object[1];
            _test_a[0] = _test_b;
            _test_b[0] = _test_a;

            _hetero_a = new Object[]{'a', Boolean.TRUE, (byte) 9, (short) 9, 9, 9L, 9.9f, 9.9d, "getStartupInfo", _testDate, boolean.class, null, "null", _CONST_INT, Class.class};
            _testRefs0 = new Object[]{_testDate, Boolean.FALSE, _CONST_CHAR, _CONST_BYTE, _CONST_SHORT, _CONST_INT, _CONST_LONG, _CONST_FLOAT, _CONST_DOUBLE, "Happy"};
            _testRefs1 = new Object[]{_testDate, Boolean.FALSE, _CONST_CHAR, _CONST_BYTE, _CONST_SHORT, _CONST_INT, _CONST_LONG, _CONST_FLOAT, _CONST_DOUBLE, "Happy"};
            _arrayO = new Object[]{"foo", true, null, 16L, 3.14d};
            _arrayS = new String[]{"fingers", "toes"};
            _arrayArrayO = new Object[][]{{"true", "false"}, {1L, 2L, 3L}, null, {1.1d, 2.2f}, {true, false}};

            _bigInts = new BigInteger[]{new BigInteger("-123456789012345678901234567890"), new BigInteger("0"), new BigInteger("123456789012345678901234567890")};
            _oBigInts = new Object[]{new BigInteger("-123456789012345678901234567890"), new BigInteger("0"), new BigInteger("123456789012345678901234567890")};
            _bigDecs = new BigDecimal[]{new BigDecimal("-123456789012345678901234567890.123456789012345678901234567890"), new BigDecimal("0.0"), new BigDecimal("123456789012345678901234567890.123456789012345678901234567890")};
            _oBigDecs = new Object[]{new BigDecimal("-123456789012345678901234567890.123456789012345678901234567890"), new BigDecimal("0.0"), new BigDecimal("123456789012345678901234567890.123456789012345678901234567890")};
        }

        private Empty _empty_a;
        private Empty _empty_b;
        private Empty[] _empty_c;
        private Empty[] _empty_d;
        private Empty[][] _empty_e;
        private Empty[][] _empty_f;

        private boolean[] _booleans_a;
        private boolean[] _booleans_b;
        private boolean[] _booleans_c;
        private Boolean[] _booleans_d;
        private Boolean[] _booleans_e;
        private Boolean[] _booleans_f;
        private Boolean[] _booleans_g;
        private boolean[][] _booleans_h;
        private boolean[][] _booleans_i;
        private boolean[][] _booleans_j;

        private char[] _chars_a;
        private char[] _chars_b;
        private char[] _chars_c;
        private Character[] _chars_d;
        private Character[] _chars_e;
        private Character[] _chars_f;
        private char[][] _chars_g;
        private Character[][] _chars_h;

        private byte[] _bytes_a;
        private byte[] _bytes_b;
        private byte[] _bytes_c;
        private Byte[] _bytes_d;
        private Byte[] _bytes_e;
        private Byte[] _bytes_f;
        private byte[][] _bytes_g;
        private Byte[][] _bytes_h;
        private byte[] _bytes_i;

        private short[] _shorts_a;
        private short[] _shorts_b;
        private short[] _shorts_c;
        private Short[] _shorts_d;
        private Short[] _shorts_e;
        private Short[] _shorts_f;
        private short[][] _shorts_g;
        private Short[][] _shorts_h;

        private int[] _ints_a;
        private int[] _ints_b;
        private int[] _ints_c;
        private int[][] _int_1;
        private Integer[] _ints_d;
        private Integer[] _ints_e;
        private Integer[] _ints_f;
        private Integer[][] _ints_g;

        private long[] _longs_a;
        private long[] _longs_b;
        private long[] _longs_c;
        private long[][][] _longs_1;
        private Long[] _longs_d;
        private Long[] _longs_e;
        private Long[] _longs_f;
        private Long[][] _longs_g;

        private float[] _floats_a;
        private float[] _floats_b;
        private float[] _floats_c;
        private Float[] _floats_d;
        private Float[] _floats_e;
        private Float[] _floats_f;
        private float[][] _floats_g;
        private Float[][] _floats_h;

        private double[] _doubles_a;
        private double[] _doubles_b;
        private double[] _doubles_c;
        private Double[] _doubles_d;
        private Double[] _doubles_e;
        private Double[] _doubles_f;
        private double[][] _doubles_g;
        private Double[][] _doubles_h;

        private String[] _strings_a;
        private String[][] _strings_b;

        private Date[] _dates_a;
        private Date[][] _dates_b;

        private Class<?>[] _classes_a;
        private Class<?>[][] _classes_b;

        private StringBuffer _stringbuffer_a;
        private StringBuffer[] _stringbuffer_b;
        private StringBuffer[][] _stringbuffer_c;

        private Object _oStringBuffer_a;
        private Object [] _oStringBuffer_b;

        private TestObject _testobj_a;
        private TestObject[] _testobj_b;
        private TestObject[][] _testobj_c;

        private Object[] _test_a;
        private Object[] _test_b;
        private Object[] _hetero_a;
        private Object[] _testRefs0;
        private Object[] _testRefs1;

        private BigInteger[] _bigInts;
        private Object[] _oBigInts;
        private BigDecimal[] _bigDecs;
        private Object[] _oBigDecs;

        private Object _arrayO;
        private Object _arrayS;
        private Object _arrayArrayO;
    }

    public static class TestArray {
        private Object[] _test_a;
        private Object[] _test_b;

        public TestArray() {
            _test_a = new Object[1];
            _test_b = new Object[1];
            _test_a[0] = _test_b;
            _test_b[0] = _test_a;
        }
    }
}
