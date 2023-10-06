package com.cedarsoftware.util.io

import com.cedarsoftware.util.DeepEquals
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import java.lang.reflect.Array

import static com.cedarsoftware.util.io.JsonObject.ITEMS
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotSame
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertSame
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@CompileStatic
class TestArrays
{
    public static Date _testDate = new Date()
	public static Character _CONST_CHAR = new Character('j' as char)
	public static Byte _CONST_BYTE = new Byte((byte) 16)
	public static Short _CONST_SHORT = new Short((short) 26)
	public static Integer _CONST_INT = new Integer(36)
	public static Long _CONST_LONG = new Long(46)
	public static Float _CONST_FLOAT = new Float(56.56f)
	public static Double _CONST_DOUBLE = new Double(66.66d)

    static class CharArrayTest
    {
        char[] chars_a;
        Character[] chars_b;
    }

    private static class Empty implements Serializable
    {
        static double multiply(double x, double y)
        {
            return x * y;
        }

        boolean equals(Object other)
        {
            return other instanceof Empty;
        }
    }

    static class ManyArrays implements Serializable
    {
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

        private Class[] _classes_a;
        private Class[][] _classes_b;

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

        public ManyArrays() { }

        public void init()
        {
            _empty_a = new Empty()
            _empty_b = null
            _empty_c = [] as Empty[]
            _empty_d = [new Empty(), null] as Empty[]
            _empty_e = [[]] as Empty[][]
            _empty_f = [[new Empty(), null], null, [], [new Empty()]] as Empty[][]

            _booleans_a = [true, false, true] as boolean[]
            _booleans_b = [] as boolean[]
            _booleans_c = null
            _booleans_d = [Boolean.TRUE, Boolean.FALSE, null] as Boolean[]
            _booleans_e = [] as Boolean[]
            _booleans_f = null;
            _booleans_g = [null] as Boolean[]
            _booleans_h = [[true], [true, false], [true, false, true], null, []] as boolean[][]
            _booleans_i = null;
            _booleans_j = [null] as boolean[][]

            _chars_a = ['a' as char, '\t' as char, '\u0005' as char] as char[]
            _chars_b = [] as char[]
            _chars_c = null;
            _chars_d = [new Character('a' as char), new Character('\t' as char), new Character('\u0006' as char)] as Character[]
            _chars_e = [] as Character[]
            _chars_f = null;
            _chars_g = [['a' as char, '\t' as char, '\u0004' as char], null, []] as char[][]
            _chars_h = [[new Character('a' as char), new Character('\t' as char), new Character('\u0004' as char)], null, []] as Character[][]

            _bytes_a = [Byte.MIN_VALUE, -1, 0, 1, Byte.MAX_VALUE] as byte[]
            _bytes_b = [] as byte[]
            _bytes_c = null;
            _bytes_d = [new Byte(Byte.MIN_VALUE), new Byte((byte) -1), new Byte((byte) 0), new Byte((byte) 1), new Byte(Byte.MAX_VALUE)] as Byte[]
            _bytes_e = [] as Byte[]
            _bytes_f = null;
            _bytes_g = [null, [], [Byte.MAX_VALUE]] as byte[][]
            _bytes_h = [null, [], [new Byte(Byte.MAX_VALUE)]] as Byte[][]
            _bytes_i = [16] as byte[]

            _shorts_a = [Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE] as short[]
            _shorts_b = [] as short[]
            _shorts_c = null;
            _shorts_d = [new Short(Short.MIN_VALUE), new Short((short) -1), new Short((short) 0), new Short((short) 1), new Short(Short.MAX_VALUE)] as Short[]
            _shorts_e = [] as Short[]
            _shorts_f = null;
            _shorts_g = [null, [], [Short.MAX_VALUE]] as short[][]
            _shorts_h = [null, [], [new Short(Short.MAX_VALUE)]] as Short[][]

            _ints_a = [Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE] as int[]
            _ints_b = [] as int[]
            _ints_c = null;
            _int_1 = [[Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE], null, [-1, 0, 1, 2]] as int[][]
            _ints_d = [new Integer(Integer.MIN_VALUE), new Integer(-1), new Integer(0), new Integer(1), new Integer(Integer.MAX_VALUE)] as Integer[]
            _ints_e = [] as Integer[]
            _ints_f = null;
            _ints_g = [null, [], [new Integer(Integer.MAX_VALUE)]] as Integer[][]

            _longs_a = [Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE] as long[]
            _longs_b = [] as long[]
            _longs_c = null;
            _longs_1 = [[[-1], [0, 1], [1, 2, 3]], [], [[1, 2]], [[]]] as long[][][]
            _longs_d = [new Long(Long.MIN_VALUE), new Long(-1), new Long(0), new Long(1), new Long(Long.MAX_VALUE)] as Long[]
            _longs_e = [] as Long[]
            _longs_f = null;
            _longs_g = [null, [], [new Long(Long.MAX_VALUE)]] as Long[][]

            _floats_a = [0.0f, Float.MIN_VALUE, Float.MAX_VALUE, -1.0f] as float[]
            _floats_b = [] as float[]
            _floats_c = null;
            _floats_d = [new Float(0.0f), new Float(Float.MIN_VALUE), new Float(Float.MAX_VALUE), new Float(-1.0f), null] as Float[]
            _floats_e = [] as Float[]
            _floats_f = null;
            _floats_g = [null, [], [Float.MAX_VALUE]] as float[][]
            _floats_h = [null, [], [new Float(Float.MAX_VALUE)]] as Float[][]

            _doubles_a = [0.0d, Double.MIN_VALUE, Double.MAX_VALUE, -1.0d] as double[]
            _doubles_b = [] as double[]
            _doubles_c = null;
            _doubles_d = [new Double(0.0d), new Double(Double.MIN_VALUE), new Double(Double.MAX_VALUE), new Double(-1.0d), null] as Double[]
            _doubles_e = [] as Double[]
            _doubles_f = null;
            _doubles_g = [null, [], [Double.MAX_VALUE]] as double[][]
            _doubles_h = [null, [], [new Double(Double.MAX_VALUE)]] as Double[][]

            _strings_a = [null, "\u0007", "\t\rfood\n\f", "null"] as String[]
            _strings_b = [["alpha", "bravo", "charlie"], [null, "\u0007", "\t", "null"], null, []] as String[][]

            _dates_a = [new Date(0), _testDate, null] as Date[]
            _dates_b = [null, [], [_testDate]] as Date[][]

            _classes_a = [boolean.class, char.class, byte.class, short.class, int.class, long.class, float.class, double.class, null, String.class] as Class[]
            _classes_b = [null, [], [Date.class]] as Class[][]

            _stringbuffer_a = new StringBuffer("food")
            _stringbuffer_b = new StringBuffer[3];
            _stringbuffer_b[0] = new StringBuffer("first")
            _stringbuffer_b[1] = new StringBuffer("second")
            _stringbuffer_b[2] = null;
            _stringbuffer_c = [null, [], [new StringBuffer("sham-wow")]] as StringBuffer[][]
            _oStringBuffer_a = new StringBuffer("murder my days one at a time")
            _oStringBuffer_b = [new StringBuffer("chaiyya chaiyya")] as Object[]

            _testobj_a = new TestObject("food")
            _testobj_b = [new TestObject("ten"), new TestObject("hut")] as TestObject[]
            _testobj_c = [null, [], [new TestObject("mighty-mend")]] as TestObject[][]

            _test_a = new Object[1]
            _test_b = new Object[1]
            _test_a[0] = _test_b
            _test_b[0] = _test_a

            _hetero_a = [new Character('a' as char), Boolean.TRUE, new Byte((byte) 9), new Short((short) 9), new Integer(9), new Long(9), new Float(9.9d), new Double(9.9d), "getStartupInfo", _testDate, boolean.class, null, "null", _CONST_INT, Class.class] as Object[]
            _testRefs0 = [_testDate, Boolean.FALSE, _CONST_CHAR, _CONST_BYTE, _CONST_SHORT, _CONST_INT, _CONST_LONG, _CONST_FLOAT, _CONST_DOUBLE, "Happy"] as Object[]
            _testRefs1 = [_testDate, Boolean.FALSE, _CONST_CHAR, _CONST_BYTE, _CONST_SHORT, _CONST_INT, _CONST_LONG, _CONST_FLOAT, _CONST_DOUBLE, "Happy"] as Object[]
            _arrayO = ["foo", true, null, 16L, 3.14d] as Object[]
            _arrayS = ["fingers", "toes"] as String[]
            _arrayArrayO = [["true", "false"], [1L, 2L, 3L], null, [1.1, 2.2], [true, false]] as Object[][]

            _bigInts = [new BigInteger("-123456789012345678901234567890"), new BigInteger("0"), new BigInteger("123456789012345678901234567890")] as BigInteger[]
            _oBigInts = [new BigInteger("-123456789012345678901234567890"), new BigInteger("0"), new BigInteger("123456789012345678901234567890")] as Object[]
            _bigDecs = [new BigDecimal("-123456789012345678901234567890.123456789012345678901234567890"), new BigDecimal("0.0"), new BigDecimal("123456789012345678901234567890.123456789012345678901234567890")] as BigDecimal[]
            _oBigDecs = [new BigDecimal("-123456789012345678901234567890.123456789012345678901234567890"), new BigDecimal("0.0"), new BigDecimal("123456789012345678901234567890.123456789012345678901234567890")] as Object[]
        }
    }

    @Test
    void testArray()
    {
        ManyArrays obj = new ManyArrays()
        obj.init()
        String jsonOut = TestUtil.getJsonString(obj)
        TestUtil.printLine(jsonOut)

        ManyArrays root = (ManyArrays) TestUtil.readJsonObject(jsonOut)
        assertArray(root)

        // GSON cannot handle it
//        Gson gson = new Gson()
//        String j = gson.toJson(obj)
    }

    private void assertArray(ManyArrays root)
    {
        assertTrue(root._empty_a != null)
        assertNull(root._empty_b)
        assertTrue(root._empty_c.length == 0)
        assertTrue(root._empty_d.length == 2)
        assertTrue(root._empty_d[0].equals(new Empty()))
        assertNull(root._empty_d[1])
        assertTrue(root._empty_e.length == 1)
        assertTrue(root._empty_e[0].length == 0)
        assertTrue(root._empty_f.length == 4)
        assertTrue(root._empty_f[0].length == 2)
        assertTrue(root._empty_f[0][0].equals(new Empty()))
        assertNull(root._empty_f[0][1])
        assertNull(root._empty_f[1])
        assertTrue(root._empty_f[2].length == 0)
        assertTrue(root._empty_f[3].length == 1)

        assertTrue(root._booleans_a.getClass().equals(([] as boolean[]).class))
        assertTrue(root._booleans_a.length == 3)
        assertTrue(root._booleans_a[0])
        assertFalse(root._booleans_a[1])
        assertTrue(root._booleans_a[2])
        assertTrue(root._booleans_b.length == 0)
        assertNull(root._booleans_c)
        assertTrue(root._booleans_d.length == 3)
        assertTrue(root._booleans_d[0].booleanValue())
        assertFalse(root._booleans_d[1].booleanValue())
        assertNull(root._booleans_d[2])
        assertTrue(root._booleans_e.length == 0)
        assertNull(root._booleans_f)
        assertTrue(root._booleans_g.length == 1)
        assertNull(root._booleans_g[0])
        assertTrue(root._booleans_h.length == 5)
        assertTrue(root._booleans_h[0].length == 1)
        assertTrue(root._booleans_h[1].length == 2)
        assertTrue(root._booleans_h[2].length == 3)
        assertTrue(root._booleans_h[0][0])
        assertTrue(root._booleans_h[1][0])
        assertFalse(root._booleans_h[1][1])
        assertTrue(root._booleans_h[2][0])
        assertFalse(root._booleans_h[2][1])
        assertTrue(root._booleans_h[2][2])
        assertNull(root._booleans_h[3])
        assertTrue(root._booleans_h[4].length == 0)
        assertNull(root._booleans_i)
        assertTrue(root._booleans_j.length == 1)
        assertNull(root._booleans_j[0])

        assertTrue(root._chars_a[0] == 'a')
        assertTrue(root._chars_a[1] == '\t')
        assertTrue(root._chars_a[2] == '\u0005')
        assertTrue(root._chars_b.length == 0)
        assertNull(root._chars_c)
        assertTrue(root._chars_d[0].charValue() == 'a')
        assertTrue(root._chars_d[1].charValue() == '\t')
        assertTrue(root._chars_d[2].charValue() == '\u0006')
        assertTrue(root._chars_e.length == 0)
        assertNull(root._chars_f)

        assertTrue(root._bytes_a[0] == Byte.MIN_VALUE)
        assertTrue(root._bytes_a[1] == -1)
        assertTrue(root._bytes_a[2] == 0)
        assertTrue(root._bytes_a[3] == 1)
        assertTrue(root._bytes_a[4] == Byte.MAX_VALUE)
        assertTrue(root._bytes_b.length == 0)
        assertNull(root._bytes_c)
        assertTrue(root._bytes_d[0].byteValue() == Byte.MIN_VALUE)
        assertTrue(root._bytes_d[1].byteValue() == -1)
        assertTrue(root._bytes_d[2].byteValue() == 0)
        assertTrue(root._bytes_d[3].byteValue() == 1)
        assertTrue(root._bytes_d[4].byteValue() == Byte.MAX_VALUE)
        assertTrue(root._bytes_e.length == 0)
        assertNull(root._bytes_f)
        assertTrue(root._bytes_g.length == 3)
        assertNull(root._bytes_g[0])
        assertTrue(root._bytes_g[1].length == 0)
        assertTrue(root._bytes_g[2].length == 1)
        assertTrue(root._bytes_g[2][0] == Byte.MAX_VALUE)
        assertTrue(root._bytes_h.length == 3)
        assertNull(root._bytes_h[0])
        assertTrue(root._bytes_h[1].length == 0)
        assertTrue(root._bytes_h[2].length == 1)
        assertTrue(root._bytes_h[2][0].byteValue() == Byte.MAX_VALUE)
        assertTrue(root._bytes_i[0] == 16)

        assertTrue(root._chars_g.length == 3)
        assertTrue(root._chars_g[0].length == 3)
        assertTrue(root._chars_g[0][0] == 'a')
        assertTrue(root._chars_g[0][1] == '\t')
        assertTrue(root._chars_g[0][2] == '\u0004')
        assertNull(root._chars_g[1])
        assertTrue(root._chars_g[2].length == 0)
        assertTrue(root._chars_h.length == 3)
        assertTrue(root._chars_h[0].length == 3)
        assertTrue(root._chars_h[0][0].equals(new Character('a' as char)))
        assertTrue(root._chars_h[0][1].equals(new Character('\t' as char)))
        assertTrue(root._chars_h[0][2].equals(new Character('\u0004' as char)))
        assertNull(root._chars_h[1])
        assertTrue(root._chars_h[2].length == 0)

        assertTrue(root._shorts_a[0] == Short.MIN_VALUE)
        assertTrue(root._shorts_a[1] == -1)
        assertTrue(root._shorts_a[2] == 0)
        assertTrue(root._shorts_a[3] == 1)
        assertTrue(root._shorts_a[4] == Short.MAX_VALUE)
        assertTrue(root._shorts_b.length == 0)
        assertNull(root._shorts_c)
        assertTrue(root._shorts_d[0].shortValue() == Short.MIN_VALUE)
        assertTrue(root._shorts_d[1].shortValue() == -1)
        assertTrue(root._shorts_d[2].shortValue() == 0)
        assertTrue(root._shorts_d[3].shortValue() == 1)
        assertTrue(root._shorts_d[4].shortValue() == Short.MAX_VALUE)
        assertTrue(root._shorts_e.length == 0)
        assertNull(root._shorts_f)
        assertTrue(root._shorts_g.length == 3)
        assertNull(root._shorts_g[0])
        assertTrue(root._shorts_g[1].length == 0)
        assertTrue(root._shorts_g[2].length == 1)
        assertTrue(root._shorts_g[2][0] == Short.MAX_VALUE)
        assertTrue(root._shorts_h.length == 3)
        assertNull(root._shorts_h[0])
        assertTrue(root._shorts_h[1].length == 0)
        assertTrue(root._shorts_h[2].length == 1)
        assertTrue(root._shorts_h[2][0].shortValue() == Short.MAX_VALUE)

        assertTrue(root._ints_a[0] == Integer.MIN_VALUE)
        assertTrue(root._ints_a[1] == -1)
        assertTrue(root._ints_a[2] == 0)
        assertTrue(root._ints_a[3] == 1)
        assertTrue(root._ints_a[4] == Integer.MAX_VALUE)
        assertTrue(root._ints_b.length == 0)
        assertNull(root._ints_c)
        assertTrue(root._ints_d[0].intValue() == Integer.MIN_VALUE)
        assertTrue(root._ints_d[1].intValue() == -1)
        assertTrue(root._ints_d[2].intValue() == 0)
        assertTrue(root._ints_d[3].intValue() == 1)
        assertTrue(root._ints_d[4].intValue() == Integer.MAX_VALUE)
        assertTrue(root._ints_e.length == 0)
        assertNull(root._ints_f)
        assertTrue(root._int_1.length == 3)
        assertTrue(root._int_1[0].length == 5)
        assertTrue(root._int_1[0][0] == Integer.MIN_VALUE)
        assertTrue(root._int_1[0][1] == -1)
        assertTrue(root._int_1[0][2] == 0)
        assertTrue(root._int_1[0][3] == 1)
        assertTrue(root._int_1[0][4] == Integer.MAX_VALUE)
        assertNull(root._int_1[1])
        assertTrue(root._int_1[2].length == 4)
        assertTrue(root._int_1[2][0] == -1)
        assertTrue(root._int_1[2][1] == 0)
        assertTrue(root._int_1[2][2] == 1)
        assertTrue(root._int_1[2][3] == 2)
        assertTrue(root._ints_g.length == 3)
        assertNull(root._ints_g[0])
        assertTrue(root._ints_g[1].length == 0)
        assertTrue(root._ints_g[2].length == 1)
        assertTrue(root._ints_g[2][0].intValue() == Integer.MAX_VALUE)

        assertTrue(root._longs_a[0] == Long.MIN_VALUE)
        assertTrue(root._longs_a[1] == -1)
        assertTrue(root._longs_a[2] == 0)
        assertTrue(root._longs_a[3] == 1)
        assertTrue(root._longs_a[4] == Long.MAX_VALUE)
        assertTrue(root._longs_b.length == 0)
        assertNull(root._longs_c)
        assertTrue(root._longs_d[0].longValue() == Long.MIN_VALUE)
        assertTrue(root._longs_d[1].longValue() == -1)
        assertTrue(root._longs_d[2].longValue() == 0)
        assertTrue(root._longs_d[3].longValue() == 1)
        assertTrue(root._longs_d[4].longValue() == Long.MAX_VALUE)
        assertTrue(root._longs_e.length == 0)
        assertNull(root._longs_f)
        assertTrue(root._longs_1.length == 4)
        assertTrue(root._longs_1[0].length == 3)
        assertTrue(root._longs_1[1].length == 0)
        assertTrue(root._longs_1[2].length == 1)
        assertTrue(root._longs_1[3].length == 1)
        assertTrue(root._ints_g.length == 3)
        assertNull(root._longs_g[0])
        assertTrue(root._longs_g[1].length == 0)
        assertTrue(root._longs_g[2].length == 1)
        assertTrue(root._longs_g[2][0].longValue() == Long.MAX_VALUE)

        assertTrue(root._floats_a.length == 4)
        assertTrue(root._floats_a[0] == 0.0f)
        assertTrue(root._floats_a[1] == Float.MIN_VALUE)
        assertTrue(root._floats_a[2] == Float.MAX_VALUE)
        assertTrue(root._floats_a[3] == -1.0f)
        assertTrue(root._floats_b.length == 0)
        assertNull(root._floats_c)
        assertTrue(root._floats_d.length == 5)
        assertTrue(root._floats_d[0].equals(new Float(0.0f)))
        assertTrue(root._floats_d[1].equals(new Float(Float.MIN_VALUE)))
        assertTrue(root._floats_d[2].equals(new Float(Float.MAX_VALUE)))
        assertTrue(root._floats_d[3].equals(new Float(-1.0f)))
        assertNull(root._floats_d[4])
        assertTrue(root._floats_e.length == 0)
        assertNull(root._floats_f)
        assertNull(root._floats_g[0])
        assertTrue(root._floats_g[1].length == 0)
        assertTrue(root._floats_g[2].length == 1)
        assertTrue(root._floats_g[2][0] == Float.MAX_VALUE)
        assertNull(root._floats_h[0])
        assertTrue(root._floats_h[1].length == 0)
        assertTrue(root._floats_h[2].length == 1)
        assertTrue(root._floats_h[2][0].floatValue() == Float.MAX_VALUE)

        assertTrue(root._doubles_a.length == 4)
        assertTrue(root._doubles_a[0] == 0.0)
        assertTrue(root._doubles_a[1] == Double.MIN_VALUE)
        assertTrue(root._doubles_a[2] == Double.MAX_VALUE)
        assertTrue(root._doubles_a[3] == -1.0)
        assertTrue(root._doubles_b.length == 0)
        assertNull(root._doubles_c)
        assertTrue(root._doubles_d.length == 5)
        assertTrue(root._doubles_d[0].equals(new Double(0.0d)))
        assertTrue(root._doubles_d[1].equals(new Double(Double.MIN_VALUE)))
        assertTrue(root._doubles_d[2].equals(new Double(Double.MAX_VALUE)))
        assertTrue(root._doubles_d[3].equals(new Double(-1.0d)))
        assertNull(root._doubles_d[4])
        assertTrue(root._doubles_e.length == 0)
        assertNull(root._doubles_f)
        assertNull(root._doubles_g[0])
        assertTrue(root._doubles_g[1].length == 0)
        assertTrue(root._doubles_g[2].length == 1)
        assertTrue(root._doubles_g[2][0] == Double.MAX_VALUE)
        assertNull(root._doubles_h[0])
        assertTrue(root._doubles_h[1].length == 0)
        assertTrue(root._doubles_h[2].length == 1)
        assertTrue(root._doubles_h[2][0].doubleValue() == Double.MAX_VALUE)

        assertNull(root._strings_a[0])
        assertTrue(root._strings_a[1].equals("\u0007"))
        assertTrue(root._strings_a[2].equals("\t\rfood\n\f"))
        assertTrue(root._strings_a[3].equals("null"))
        assertTrue(root._strings_b.length == 4)
        assertTrue(root._strings_b[0].length == 3)
        assertTrue(root._strings_b[0][0].equals("alpha"))
        assertTrue(root._strings_b[0][1].equals("bravo"))
        assertTrue(root._strings_b[0][2].equals("charlie"))
        assertTrue(root._strings_b[1].length == 4)
        assertNull(root._strings_b[1][0])
        assertTrue(root._strings_b[1][1].equals("\u0007"))
        assertTrue(root._strings_b[1][2].equals("\t"))
        assertTrue(root._strings_b[1][3].equals("null"))
        assertNull(root._strings_b[2])
        assertTrue(root._strings_b[3].length == 0)

        assertTrue(root._dates_a[0].equals(new Date(0)))
        assertTrue(root._dates_a[1].equals(_testDate))
        assertNull(root._dates_a[2])
        assertNull(root._dates_b[0])
        assertTrue(root._dates_b[1].length == 0)
        assertTrue(root._dates_b[2].length == 1)
        assertTrue(root._dates_b[2][0].equals(_testDate))

        assertTrue(root._classes_a.length == 10)
        assertTrue(root._classes_a[0].equals(boolean.class))
        assertTrue(root._classes_a[1].equals(char.class))
        assertTrue(root._classes_a[2].equals(byte.class))
        assertTrue(root._classes_a[3].equals(short.class))
        assertTrue(root._classes_a[4].equals(int.class))
        assertTrue(root._classes_a[5].equals(long.class))
        assertTrue(root._classes_a[6].equals(float.class))
        assertTrue(root._classes_a[7].equals(double.class))
        assertNull(root._classes_a[8])
        assertTrue(root._classes_a[9].equals(String.class))
        assertNull(root._classes_b[0])
        assertTrue(root._classes_b[1].length == 0)
        assertTrue(root._classes_b[2].length == 1)
        assertTrue(root._classes_b[2][0].equals(Date.class))

        assertTrue(root._stringbuffer_a.toString().equals("food"))
        assertTrue(root._stringbuffer_b.length == 3)
        assertTrue(root._stringbuffer_b[0].toString().equals("first"))
        assertTrue(root._stringbuffer_b[1].toString().equals("second"))
        assertNull(root._stringbuffer_b[2])
        assertTrue(root._stringbuffer_c.length == 3)
        assertNull(root._stringbuffer_c[0])
        assertTrue(root._stringbuffer_c[1].length == 0)
        assertTrue(root._stringbuffer_c[2].length == 1)
        assertTrue(root._stringbuffer_c[2][0].toString().equals("sham-wow"))

        assertTrue("murder my days one at a time".equals(root._oStringBuffer_a.toString()))
        assertTrue(root._oStringBuffer_b.length == 1)
        assertTrue("chaiyya chaiyya".equals(root._oStringBuffer_b[0].toString()))

        assertTrue(root._testobj_a.equals(new TestObject("food")))
        assertTrue(root._testobj_b.length == 2)
        assertTrue(root._testobj_b[0].getName().equals("ten"))
        assertTrue(root._testobj_b[1].getName().equals("hut"))
        assertTrue(root._testobj_c.length == 3)
        assertNull(root._testobj_c[0])
        assertTrue(root._testobj_c[1].length == 0)
        assertTrue(root._testobj_c[2].length == 1)
        assertTrue(root._testobj_c[2][0].equals(new TestObject("mighty-mend")))

        assertTrue(root._test_a.length == 1)
        assertTrue(root._test_b.length == 1)
        assertSame(root._test_a[0], root._test_b)
        assertSame(root._test_b[0], root._test_a)

        assertTrue(root._hetero_a.length == 15)
        assertTrue(root._hetero_a[0].equals(new Character('a' as char)))
        assertTrue(root._hetero_a[1].equals(Boolean.TRUE))
        assertTrue(root._hetero_a[2].equals(new Byte((byte) 9)))
        assertTrue(root._hetero_a[3].equals(new Short((short) 9)))
        assertTrue(root._hetero_a[4].equals(new Integer(9)))
        assertTrue(root._hetero_a[5].equals(new Long(9)))
        assertTrue(root._hetero_a[6].equals(new Float(9.9f)))
        assertTrue(root._hetero_a[7].equals(new Double(9.9d)))
        assertTrue(root._hetero_a[8].equals("getStartupInfo"))
        assertTrue(root._hetero_a[9].equals(_testDate))
        assertTrue(root._hetero_a[10].equals(boolean.class))
        assertNull(root._hetero_a[11])
        assertTrue(root._hetero_a[12].equals("null"))
        assertTrue(root._hetero_a[13].equals(_CONST_INT))
        assertTrue(root._hetero_a[14].equals(Class.class))

        assertTrue(root._testRefs0.length == 10)
        assertTrue(root._testRefs0[0].equals(_testDate))
        assertTrue(root._testRefs0[1].equals(Boolean.FALSE))
        assertTrue(root._testRefs0[2].equals(_CONST_CHAR))
        assertTrue(root._testRefs0[3].equals(_CONST_BYTE))
        assertTrue(root._testRefs0[4].equals(_CONST_SHORT))
        assertTrue(root._testRefs0[5].equals(_CONST_INT))
        assertTrue(root._testRefs0[6].equals(_CONST_LONG))
        assertTrue(root._testRefs0[7].equals(_CONST_FLOAT))
        assertTrue(root._testRefs0[8].equals(_CONST_DOUBLE))
        assertTrue(root._testRefs0[9].equals("Happy"))

        assertTrue(root._testRefs1.length == 10)
        assertNotSame(root._testRefs1[0], root._testRefs0[0])
        assertSame(root._testRefs1[1], root._testRefs0[1])    // Works because we only read in Boolean.TRUE, Boolean.FALSE, or null
        assertSame(root._testRefs1[2], root._testRefs0[2])
        assertSame(root._testRefs1[3], root._testRefs0[3])
        assertSame(root._testRefs1[4], root._testRefs0[4])
        assertSame(root._testRefs1[5], root._testRefs0[5])
        assertTrue(root._testRefs1[6].equals(root._testRefs0[6]))
        assertNotSame(root._testRefs1[7], root._testRefs0[7])       // Primitive Wrappers are treated like primitives
        assertTrue(root._testRefs1[8].equals(root._testRefs0[8]))
        assertTrue(root._testRefs1[9].equals(root._testRefs0[9]))

        assertTrue(root._arrayO instanceof Object[])
        Object[] items = (Object[]) root._arrayO;
        assertTrue(items.length == 5)
        assertTrue("foo".equals(items[0]))
        assertTrue(Boolean.TRUE.equals(items[1]))
        assertNull(items[2])
        assertTrue(((Long)16L).equals(items[3]))
        assertTrue(((Double)3.14).equals(items[4]))

        assertTrue(root._arrayS instanceof String[])
        String[] strItems = (String[]) root._arrayS;
        assertTrue(strItems.length == 2)
        assertTrue("fingers".equals(strItems[0]))
        assertTrue("toes".equals(strItems[1]))

        assertTrue(root._arrayArrayO instanceof Object[])
        assertTrue(root._arrayArrayO instanceof Object[][])
        assertFalse(root._arrayArrayO instanceof Object[][][])

        assertTrue(root._bigInts[0].equals(new BigInteger("-123456789012345678901234567890")))
        assertTrue(root._bigInts[1].equals(new BigInteger("0")))
        assertTrue(root._bigInts[2].equals(new BigInteger("123456789012345678901234567890")))

        assertTrue(root._oBigInts[0].equals(new BigInteger("-123456789012345678901234567890")))
        assertTrue(root._oBigInts[1].equals(new BigInteger("0")))
        assertTrue(root._oBigInts[2].equals(new BigInteger("123456789012345678901234567890")))

        assertTrue(root._bigDecs[0].equals(new BigDecimal("-123456789012345678901234567890.123456789012345678901234567890")))
        assertTrue(root._bigDecs[1].equals(new BigDecimal("0.0")))
        assertTrue(root._bigDecs[2].equals(new BigDecimal("123456789012345678901234567890.123456789012345678901234567890")))

        assertTrue(root._oBigDecs[0].equals(new BigDecimal("-123456789012345678901234567890.123456789012345678901234567890")))
        assertTrue(root._oBigDecs[1].equals(new BigDecimal("0.0")))
        assertTrue(root._oBigDecs[2].equals(new BigDecimal("123456789012345678901234567890.123456789012345678901234567890")))
    }

    @Test
    void testReconstituteObjectArray()
    {
        Date now = new Date()
        TestObject to = new TestObject("football")
        TimeZone tz = TimeZone.getTimeZone("EST")
        Collection col = new ArrayList()
        col.add("Collection inside array")
        col.add(tz)
        col.add(now)
        Object[] objs = [now, 123.45d, 0.04f, "This is a string", null,  true, to, tz, col, (short) 7, (byte) -127] as Object[]
        Object[] two = [objs, "bella", objs] as Object[]
        String json0 = TestUtil.getJsonString(two)
        TestUtil.printLine("json0=" + json0)
        Object[] array = (Object[]) JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS):true] as Map)
        String json1 = TestUtil.getJsonString(array)
        TestUtil.printLine("json1=" + json1)

        // Read back into typed Java objects, the Maps of Maps versus that was dumped out
        Object[] result = (Object[]) TestUtil.readJsonObject(json1)
        assertTrue(result.length == 3)
        Object[] arr1 = (Object[]) result[0];
        assertTrue(arr1.length == 11)
        String bella = (String) result[1];
        Object[] arr2 = (Object[]) result[2];
        assertSame(arr1, arr2)
        assertTrue("bella".equals(bella))
        assertTrue(now.equals(arr1[0]))
        assertTrue(arr1[1].equals(123.45d))
        assertTrue(arr1[2].equals(0.04f))
        assertTrue("This is a string".equals(arr1[3]))
        assertTrue(arr1[4] == null)
        assertSame(arr1[5], Boolean.TRUE)
        assertTrue(to.equals(arr1[6]))
        assertTrue(tz.equals(arr1[7]))
        List c = (List) arr1[8];
        assertTrue("Collection inside array".equals(c.get(0)))
        assertTrue(tz.equals(c.get(1)))
        assertTrue(now.equals(c.get(2)))
        assertTrue(7 == (Short)arr1[9])
        assertTrue(-127 == (Byte) arr1[10])
        assertTrue(json0.equals(json1))

        ManyArrays ta = new ManyArrays()
        ta.init()
        json0 = TestUtil.getJsonString(ta)
        TestUtil.printLine("json0=" + json0)

        Map map = (Map) JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS):true] as Map)
        json1 = TestUtil.getJsonString(map)
        TestUtil.printLine("json1=" + json1)

        assertTrue(json0.equals(json1))
    }

    @Test
    void testReconstituteObjectArrayTypes()
    {
        TestUtil.printLine("testReconstituteObjectArrayTypes")
        Object[] bytes = [_CONST_BYTE,  _CONST_BYTE] as Object[]
        testReconstituteArrayHelper(bytes)
        Byte[] Bytes = [_CONST_BYTE,  _CONST_BYTE] as Byte[]
        testReconstituteArrayHelper(Bytes)
        byte[] bites = [_CONST_BYTE,  _CONST_BYTE] as byte[]
        testReconstituteArrayHelper(bites)

        Object[] shorts = [_CONST_SHORT,  _CONST_SHORT] as Object[]
        testReconstituteArrayHelper(shorts)
        Short[] Shorts = [_CONST_SHORT,  _CONST_SHORT] as Short[]
        testReconstituteArrayHelper(Shorts)
        short[] shortz = [_CONST_SHORT,  _CONST_SHORT] as short[]
        testReconstituteArrayHelper(shortz)

        Object[] ints = [_CONST_INT,  _CONST_INT] as Object[]
        testReconstituteArrayHelper(ints)
        Integer[] Ints = [_CONST_INT,  _CONST_INT] as Integer[]
        testReconstituteArrayHelper(Ints)
        int[] intz = [_CONST_INT,  _CONST_INT] as int[]
        testReconstituteArrayHelper(intz)

        Object[] longs = [_CONST_LONG,  _CONST_LONG] as Object[]
        testReconstituteArrayHelper(longs)
        Long[] Longs = [_CONST_LONG,  _CONST_LONG] as Long[]
        testReconstituteArrayHelper(Longs)
        long[] longz = [_CONST_LONG,  _CONST_LONG] as long[]
        testReconstituteArrayHelper(longz)

        Object[] floats = [_CONST_FLOAT,  _CONST_FLOAT] as Object[]
        testReconstituteArrayHelper(floats)
        Float[] Floats = [_CONST_FLOAT,  _CONST_FLOAT] as Float[]
        testReconstituteArrayHelper(Floats)
        float[] floatz = [_CONST_FLOAT,  _CONST_FLOAT] as float[]
        testReconstituteArrayHelper(floatz)

        Object[] doubles = [_CONST_DOUBLE,  _CONST_DOUBLE] as Object[]
        testReconstituteArrayHelper(doubles)
        Double[] Doubles = [_CONST_DOUBLE,  _CONST_DOUBLE] as Double[]
        testReconstituteArrayHelper(Doubles)
        double[] doublez = [_CONST_DOUBLE,  _CONST_DOUBLE] as double[]
        testReconstituteArrayHelper(doublez)

        Object[] booleans = [Boolean.TRUE, Boolean.TRUE] as Object[]
        testReconstituteArrayHelper(booleans)
        Boolean[] Booleans = [Boolean.TRUE,  Boolean.TRUE] as Boolean[]
        testReconstituteArrayHelper(Booleans)
        boolean[] booleanz = [true, true] as boolean[]
        testReconstituteArrayHelper(booleanz)

        Object[] chars = ['J' as char, 'J' as char] as Object[]
        testReconstituteArrayHelper(chars)
        Character[] Chars = ['S' as char, 'S' as char] as Character[]
        testReconstituteArrayHelper(Chars)
        char[] charz = ['R' as char, 'R' as char] as char[]
        testReconstituteArrayHelper(charz)

        Object[] classes = [LinkedList.class, LinkedList.class] as Object[]
        testReconstituteArrayHelper(classes)
        Class[] Classes = [LinkedList.class, LinkedList.class] as Class[]
        testReconstituteArrayHelper(Classes)

        Date now = new Date()
        Object[] dates = [now, now] as Object[]
        testReconstituteArrayHelper(dates)
        Date[] Dates = [now, now] as Date[]
        testReconstituteArrayHelper(Dates)

        BigDecimal pi = new BigDecimal(3.1415926535897932384626433d)
        Object[] bigDecimals = [pi, pi] as Object[]
        testReconstituteArrayHelper(bigDecimals)
        BigDecimal[] BigDecimals = [pi, pi] as BigDecimal[]
        testReconstituteArrayHelper(BigDecimals)

        String s = "json-io"
        Object[] strings = [s, s] as Object[]
        testReconstituteArrayHelper(strings)
        String[] Strings = [s, s] as String[]
        testReconstituteArrayHelper(Strings)

        GregorianCalendar cal = (GregorianCalendar) Calendar.getInstance(TimeZone.getTimeZone("EST"))
        Object[] calendars = [cal, cal] as Object[]
        testReconstituteArrayHelper(calendars)
        Calendar[] Calendars = [cal, cal] as Calendar[]
        testReconstituteArrayHelper(Calendars)
        GregorianCalendar[] calendarz = [cal, cal] as GregorianCalendar[]
        testReconstituteArrayHelper(calendarz)
    }

    private void testReconstituteArrayHelper(Object foo)
    {
        assertTrue(Array.getLength(foo) == 2)
        String json0 = TestUtil.getJsonString(foo)
        TestUtil.printLine("json0=" + json0)

        Object array = JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS):true] as Map)
        String json1 = TestUtil.getJsonString(array)
        TestUtil.printLine("json1=" + json1)
        assertEquals(json0, json1)

    }

    @Test
    void testReconstituteEmptyArray()
    {
        Object[] empty = [] as Object[]
        String json0 = TestUtil.getJsonString(empty)
        TestUtil.printLine("json0=" + json0)

        empty = (Object[]) JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS):true] as Map)
        assertTrue(empty != null)
        assertTrue(empty != null)
        assertTrue(empty.length == 0)
        String json1 = TestUtil.getJsonString(empty)
        TestUtil.printLine("json1=" + json1)

        assertTrue(json0.equals(json1))

        Object[] list = [empty, empty] as Object[]
        json0 = TestUtil.getJsonString(list)
        TestUtil.printLine("json0=" + json0)

        list = (Object[]) JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS):true] as Map)
        assertTrue(list != null)
        assertTrue(list.length == 2)
        Map e1 = (Map) list[0];
        Map e2 = (Map) list[1];
        assertTrue(e1.get(ITEMS) == e2.get(ITEMS))
        assertTrue(((Object[])e1.get(ITEMS)).length == 0)

        json1 = TestUtil.getJsonString(list)
        TestUtil.printLine("json1=" + json1)
        assertTrue(json0.equals(json1))
    }

    @Test
    void testReconstituteTypedArray()
    {
        String[] strs = ["tom", "dick", "harry"] as String[]
        Object[] objs = [strs, "a", strs] as Object[]
        String json0 = TestUtil.getJsonString(objs)
        TestUtil.printLine("json0=" + json0)
        Object array = JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS):true] as Map)
        String json1 = TestUtil.getJsonString(array)
        TestUtil.printLine("json1=" + json1)

        Object[] result = (Object[]) TestUtil.readJsonObject(json1)
        assertTrue(result.length == 3)
        assertSame(result[0], result[2])
        assertTrue("a".equals(result[1]))
        String[] sa = (String[]) result[0];
        assertTrue(sa.length == 3)
        assertTrue("tom".equals(sa[0]))
        assertTrue("dick".equals(sa[1]))
        assertTrue("harry".equals(sa[2]))
        String json2 = TestUtil.getJsonString(result)
        TestUtil.printLine("json2=" + json2)
        assertTrue(json0.equals(json1))
        assertTrue(json1.equals(json2))
    }

    @Test
    void testReconstituteArray()
    {
        ManyArrays testArray = new ManyArrays()
        testArray.init()
        String json0 = TestUtil.getJsonString(testArray)
        TestUtil.printLine("json0=" + json0)
        Map testArray2 = (Map) JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS):true] as Map)

        String json1 = TestUtil.getJsonString(testArray2)
        TestUtil.printLine("json1=" + json1)

        ManyArrays testArray3 = (ManyArrays) TestUtil.readJsonObject(json1)
        assertArray(testArray3)    // Re-written from Map of Maps and re-loaded correctly
        assertTrue(json0.equals(json1))
    }

    @Test
    void testReconstituteEmptyObject()
    {
        Empty empty = new Empty()
        String json0 = TestUtil.getJsonString(empty)
        TestUtil.printLine("json0=" + json0)

        Map m = (Map) JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS):true] as Map)
        assertTrue(m.isEmpty())

        String json1 = TestUtil.getJsonString(m)
        TestUtil.printLine("json1=" + json1)
        assertTrue(json0.equals(json1))
    }

    @Test
    void testAlwaysShowType()
    {
        ManyArrays ta = new ManyArrays()
        ta.init()
        String json0 = JsonWriter.objectToJson(ta, [(JsonWriter.TYPE): (Object)true])
        ManyArrays thatTa = (ManyArrays) TestUtil.readJsonObject(json0)
        assertTrue(DeepEquals.deepEquals(ta, thatTa))
        String json1 = JsonWriter.objectToJson(ta)
        TestUtil.printLine("json0 = " + json0)
        TestUtil.printLine("json1 = " + json1)
        assertTrue(json0.length() > json1.length())
    }

    @Test
    void testArraysAsList()
    {
        List strs = ['alpha', 'bravo', 'charlie']
        String json = TestUtil.getJsonString(strs)
        List foo = (List) TestUtil.readJsonObject(json)
        assertTrue(foo.size() == 3)
        assertTrue("alpha".equals(foo.get(0)))
        assertTrue("charlie".equals(foo.get(2)))
    }

    // Currently allows , at end.  Future, may drop this support.
    @Test
    void testBadArray()
    {
        String json = "[1, 10, 100,]"
        Object[] array = (Object[]) TestUtil.readJsonObject(json)
        assertTrue(array.length == 3)
        assertEquals(array[0], 1L)
        assertEquals(array[1], 10L)
        assertEquals(array[2], 100L)
    }

    @Test
    void testCharArray()
    {
        CharArrayTest cat = new CharArrayTest()
        cat.chars_a = ['a' as char, '\t' as char, '\u0005' as char] as char[]
        cat.chars_b = ['b' as char, '\t' as char, '\u0002' as char] as Character[]

        String json0 = JsonWriter.objectToJson(cat, [(JsonWriter.TYPE):(Object)true])
        TestUtil.printLine(json0)

        CharArrayTest cat2 = (CharArrayTest) TestUtil.readJsonObject(json0)
        char[] chars_a = cat2.chars_a;
        assertEquals(chars_a.length, 3)
        assertEquals(chars_a[0], 'a' as char)
        assertEquals(chars_a[1], '\t' as char)
        assertEquals(chars_a[2], '\u0005' as char)

        Character[] chars_b = cat2.chars_b;
        assertEquals(chars_b.length, 3)
        assertTrue(chars_b[0] == 'b' as char)
        assertTrue(chars_b[1] == '\t' as char)
        assertTrue(chars_b[2] =='\u0002' as char)

        String json1 = JsonWriter.objectToJson(cat)
        TestUtil.printLine(json1)

        cat2 = (CharArrayTest) TestUtil.readJsonObject(json0)
        chars_a = cat2.chars_a;
        assertEquals(chars_a.length, 3)
        assertEquals(chars_a[0], 'a' as char)
        assertEquals(chars_a[1], '\t' as char)
        assertEquals(chars_a[2], '\u0005' as char)

        chars_b = cat2.chars_b;
        assertEquals(chars_b.length, 3)
        assertTrue(chars_b[0] == 'b' as char)
        assertTrue(chars_b[1] == '\t' as char)
        assertTrue(chars_b[2] =='\u0002' as char)
    }

    @Test
    void testEmptyArray()
    {
        String json = '{"@type":"[Ljava.lang.String;"}'
        String[] s = (String[])JsonReader.jsonToJava(json)
        assertTrue(s != null)
        assertTrue(s.length == 0)
    }

    @Test
    void testMultiDimensionalArrays()
    {
        int[][][][] x = [[[[0,1],[0,1]],[[0,1],[0,1]]],[[[0,1],[0,1]],[[0,1],[0,1]]]] as int[][][][]
        for (int a=0; a < 2; a++)
        {
            for (int b=0; b < 2; b++)
            {
                for (int c=0; c < 2; c++)
                {
                    for (int d=0; d < 2; d++)
                    {
                        x[a][b][c][d] = a + b + c + d
                    }
                }
            }
        }

        String json = TestUtil.getJsonString(x)
        int[][][][] y = (int[][][][]) TestUtil.readJsonObject(json)


        for (int a=0; a < 2; a++)
        {
            for (int b=0; b < 2; b++)
            {
                for (int c=0; c < 2; c++)
                {
                    for (int d=0; d < 2; d++)
                    {
                        assertTrue(y[a][b][c][d] == a + b + c + d)
                    }
                }
            }
        }

        Integer[][][][] xx = [[[[0,1],[0,1]],[[0,1],[0,1]]],[[[0,1],[0,1]],[[0,1],[0,1]]]] as Integer[][][][]
        for (int a=0; a < 2; a++)
        {
            for (int b=0; b < 2; b++)
            {
                for (int c=0; c < 2; c++)
                {
                    for (int d=0; d < 2; d++)
                    {
                        xx[a][b][c][d] = a + b + c + d
                    }
                }
            }
        }

        json = TestUtil.getJsonString(xx)
        Integer[][][][] yy = (Integer[][][][]) TestUtil.readJsonObject(json)

        for (int a=0; a < 2; a++)
        {
            for (int b=0; b < 2; b++)
            {
                for (int c=0; c < 2; c++)
                {
                    for (int d=0; d < 2; d++)
                    {
                        assertTrue(yy[a][b][c][d] == a + b + c + d)
                    }
                }
            }
        }
    }

    @Test
    void testObjectArrayStringReference()
    {
        String s = "dogs"
        String json = TestUtil.getJsonString([s, s] as Object[])
        TestUtil.printLine("json = " + json)
        Object[] o = (Object[]) TestUtil.readJsonObject(json)
        assertTrue(o.length == 2)
        assertTrue("dogs".equals(o[0]))
        assertNotSame(o[0], o[1])
    }

    @Test
    void testStringArrayStringReference()
    {
        String s = "dogs"
        String json = TestUtil.getJsonString([s, s] as String[])
        TestUtil.printLine("json = " + json)
        String[] o = (String[]) TestUtil.readJsonObject(json)
        assertTrue(o.length == 2)
        assertTrue("dogs".equals(o[0]))
        assertNotSame(o[0], o[1])
    }

    @Test
    void testReferencedEmptyArray()
    {
        String[] array = [] as String[]
        Object[] refArray = [array] as Object[]
        String json = JsonWriter.objectToJson(refArray)
        TestUtil.printLine("json=" + json)
        Object[] oa = (Object[]) JsonReader.jsonToJava(json)
        assertTrue(oa[0].getClass().equals(([] as String[]).class))
        assertTrue(((String[])oa[0]).length == 0)
    }

    @Test
    void testUntypedArray()
    {
        Object[] args = (Object[]) TestUtil.readJsonObject('["string",17, null, true, false, [], -1273123,32131, 1e6, 3.14159, -9223372036854775808, 9223372036854775807]')

        for (int i=0; i < args.length; i++)
        {
            TestUtil.printLine("args[" + i + "]=" + args[i])
            if (args[i] != null)
            {
                TestUtil.printLine("args[" + i + "]=" + args[i].getClass().getName())
            }
        }

        assertTrue(args[0].equals("string"))
        assertTrue(args[1].equals(17L))
        assertTrue(args[2] == null)
        assertTrue(args[3].equals(Boolean.TRUE))
        assertTrue(args[4].equals(Boolean.FALSE))
        assertTrue(args[5].getClass().isArray())
        assertTrue(args[6].equals(-1273123L))
        assertTrue(args[7].equals(32131L))
        assertTrue(args[8].equals(new Double(1000000)))
        assertTrue(args[9].equals(new Double(3.14159d)))
        assertTrue(args[10].equals(Long.MIN_VALUE))
        assertTrue(args[11].equals(Long.MAX_VALUE))
    }

    @Test
    void testArrayListSaveAndRestoreGenericJSON()
    {
        ArrayList<Integer> numbers = new ArrayList<>()
        numbers.add(10)
        numbers.add(20)
        numbers.add(30)
        numbers.add(40)

        // Serialize the ArrayList to Json
        HashMap disableTypes = new HashMap();
        disableTypes.put(JsonWriter.TYPE, false);
        String json = JsonWriter.objectToJson(numbers, disableTypes);

        System.out.println("Numbers ArrayList = " + numbers + ". Numbers to json = " + json);
        // This prints: "Numbers ArrayList = [10, 20, 30, 40]. Numbers to json = [10,20,30,40]"

        List<Integer> restoredNumbers;
        restoredNumbers = (List<Integer>) JsonReader.jsonToJava(json);

        assert numbers.equals(restoredNumbers)
    }

    @Test
    void testToEnsureAtEIsWorking()
    {
        String[] testArray = new String[1]
        testArray[0] = "Test"
        String testOut = JsonWriter.objectToJson(testArray, [(JsonWriter.SHORT_META_KEYS) : true] as Map)
//        System.out.println(testOut);

        // The line below blew-up when the @i was being written by JsonWriter instead of @e in short-hand.
        Object object = JsonReader.jsonToJava(testOut);
    }
}
