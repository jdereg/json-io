package com.cedarsoftware.util.io;

import com.cedarsoftware.util.DeepEquals;
import com.cedarsoftware.util.io.JsonWriter.JsonClassWriter;
import com.google.gson.Gson;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.awt.Point;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test cases for JsonReader / JsonWriter
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestJsonReaderWriter
{
	public static Date _testDate = new Date();
	public static Character _CONST_CHAR = new Character('j');
	public static Byte _CONST_BYTE = new Byte((byte) 16);
	public static Short _CONST_SHORT = new Short((short) 26);
	public static Integer _CONST_INT = new Integer(36);
	public static Long _CONST_LONG = new Long(46);
	public static Float _CONST_FLOAT = new Float(56.56);
	public static Double _CONST_DOUBLE = new Double(66.66);

	private static class TestJsonNoDefaultOrPublicConstructor
	{
		private final String _str;
		private final Date _date;
		private final byte _byte;
		private final Byte _Byte;
		private final short _short;
		private final Short _Short;
		private final int _int;
		private final Integer _Integer;
		private final long _long;
		private final Long _Long;
		private final float _float;
		private final Float _Float;
		private final double _double;
		private final Double _Double;
		private final boolean _boolean;
		private final Boolean _Boolean;
		private final char _char;
		private final Character _Char;
		private final String[] _strings;
		private final int[] _ints;
		private final BigDecimal _bigD;

		private TestJsonNoDefaultOrPublicConstructor(String string, Date date, byte b, Byte B, short s, Short S, int i, Integer I,
													 long l, Long L, float f, Float F, double d, Double D, boolean bool, Boolean Bool,
													 char c, Character C, String[] strings, int[] ints, BigDecimal bigD)
		{
			_str = string;
			_date = date;
			_byte = b;
			_Byte = B;
			_short = s;
			_Short = S;
			_int = i;
			_Integer = I;
			_long = l;
			_Long = L;
			_float = f;
			_Float = F;
			_double = d;
			_Double = D;
			_boolean = bool;
			_Boolean = Bool;
			_char = c;
			_Char = C;
			_strings = strings;
			_ints = ints;
			_bigD = bigD;
		}

		public String getString()
		{
			return _str;
		}

		public Date getDate()
		{
			return _date;
		}

		public byte getByte()
		{
			return _byte;
		}

		public short getShort()
		{
			return _short;
		}

		public int getInt()
		{
			return _int;
		}

		public long getLong()
		{
			return _long;
		}

		public float getFloat()
		{
			return _float;
		}

		public double getDouble()
		{
			return _double;
		}

		public boolean getBoolean()
		{
			return _boolean;
		}

		public char getChar()
		{
			return _char;
		}

		public String[] getStrings()
		{
			return _strings;
		}

		public int[] getInts()
		{
			return _ints;
		}
	}

	private static class Empty implements Serializable
	{
		public static double multiply(double x, double y)
		{
			return x * y;
		}

		@Override
		public boolean equals(Object other)
		{
			return other instanceof Empty;
		}
	}

	public static class TestArray implements Serializable
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

		public TestArray() { }

		public void init()
		{
			_empty_a = new Empty();
			_empty_b = null;
			_empty_c = new Empty[]{};
			_empty_d = new Empty[]{new Empty(), null};
			_empty_e = new Empty[][]{{}};
			_empty_f = new Empty[][]{{new Empty(), null}, null, {}, {new Empty()}};

			_booleans_a = new boolean[]{true, false, true};
			_booleans_b = new boolean[]{};
			_booleans_c = null;
			_booleans_d = new Boolean[]{Boolean.TRUE, Boolean.FALSE, null};
			_booleans_e = new Boolean[]{};
			_booleans_f = null;
			_booleans_g = new Boolean[]{null};
			_booleans_h = new boolean[][]{{true}, {true, false}, {true, false, true}, null, {}};
			_booleans_i = null;
			_booleans_j = new boolean[][]{null};

			_chars_a = new char[]{'a', '\t', '\u0005'};
			_chars_b = new char[]{};
			_chars_c = null;
			_chars_d = new Character[]{new Character('a'), new Character('\t'), new Character('\u0006')};
			_chars_e = new Character[]{};
			_chars_f = null;
			_chars_g = new char[][]{{'a', '\t', '\u0004'}, null, {}};
			_chars_h = new Character[][]{{new Character('a'), new Character('\t'), new Character('\u0004')}, null, {}};

			_bytes_a = new byte[]{Byte.MIN_VALUE, -1, 0, 1, Byte.MAX_VALUE};
			_bytes_b = new byte[]{};
			_bytes_c = null;
			_bytes_d = new Byte[]{new Byte(Byte.MIN_VALUE), new Byte((byte) -1), new Byte((byte) 0), new Byte((byte) 1), new Byte(Byte.MAX_VALUE)};
			_bytes_e = new Byte[]{};
			_bytes_f = null;
			_bytes_g = new byte[][]{null, {}, {Byte.MAX_VALUE}};
			_bytes_h = new Byte[][]{null, {}, {new Byte(Byte.MAX_VALUE)}};
			_bytes_i = new byte[]{16};

			_shorts_a = new short[]{Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE};
			_shorts_b = new short[]{};
			_shorts_c = null;
			_shorts_d = new Short[]{new Short(Short.MIN_VALUE), new Short((short) -1), new Short((short) 0), new Short((short) 1), new Short(Short.MAX_VALUE)};
			_shorts_e = new Short[]{};
			_shorts_f = null;
			_shorts_g = new short[][]{null, {}, {Short.MAX_VALUE}};
			_shorts_h = new Short[][]{null, {}, {new Short(Short.MAX_VALUE)}};

			_ints_a = new int[]{Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};
			_ints_b = new int[]{};
			_ints_c = null;
			_int_1 = new int[][]{{Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE}, null, {-1, 0, 1, 2}};
			_ints_d = new Integer[]{new Integer(Integer.MIN_VALUE), new Integer(-1), new Integer(0), new Integer(1), new Integer(Integer.MAX_VALUE)};
			_ints_e = new Integer[]{};
			_ints_f = null;
			_ints_g = new Integer[][]{null, {}, {new Integer(Integer.MAX_VALUE)}};

			_longs_a = new long[]{Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE};
			_longs_b = new long[]{};
			_longs_c = null;
			_longs_1 = new long[][][]{{{-1}, {0, 1}, {1, 2, 3}}, {}, {{1, 2}}, {{}}};
			_longs_d = new Long[]{new Long(Long.MIN_VALUE), new Long(-1), new Long(0), new Long(1), new Long(Long.MAX_VALUE)};
			_longs_e = new Long[]{};
			_longs_f = null;
			_longs_g = new Long[][]{null, {}, {new Long(Long.MAX_VALUE)}};

			_floats_a = new float[]{0.0f, Float.MIN_VALUE, Float.MAX_VALUE, -1.0f};
			_floats_b = new float[]{};
			_floats_c = null;
			_floats_d = new Float[]{new Float(0.0f), new Float(Float.MIN_VALUE), new Float(Float.MAX_VALUE), new Float(-1.0f), null};
			_floats_e = new Float[]{};
			_floats_f = null;
			_floats_g = new float[][]{null, {}, {Float.MAX_VALUE}};
			_floats_h = new Float[][]{null, {}, {new Float(Float.MAX_VALUE)}};

			_doubles_a = new double[]{0.0, Double.MIN_VALUE, Double.MAX_VALUE, -1.0};
			_doubles_b = new double[]{};
			_doubles_c = null;
			_doubles_d = new Double[]{new Double(0.0), new Double(Double.MIN_VALUE), new Double(Double.MAX_VALUE), new Double(-1.0), null};
			_doubles_e = new Double[]{};
			_doubles_f = null;
			_doubles_g = new double[][]{null, {}, {Double.MAX_VALUE}};
			_doubles_h = new Double[][]{null, {}, {new Double(Double.MAX_VALUE)}};

			_strings_a = new String[]{null, "\u0007", "\t\rfood\n\f", "null"};
			_strings_b = new String[][]{{"alpha", "bravo", "charlie"}, {null, "\u0007", "\t", "null"}, null, {}};

			_dates_a = new Date[]{new Date(0), _testDate, null};
			_dates_b = new Date[][]{null, {}, {_testDate}};

			_classes_a = new Class[]{boolean.class, char.class, byte.class, short.class, int.class, long.class, float.class, double.class, null, String.class};
			_classes_b = new Class[][]{null, {}, {Date.class}};

			_stringbuffer_a = new StringBuffer("food");
			_stringbuffer_b = new StringBuffer[3];
			_stringbuffer_b[0] = new StringBuffer("first");
			_stringbuffer_b[1] = new StringBuffer("second");
			_stringbuffer_b[2] = null;
			_stringbuffer_c = new StringBuffer[][]{null, {}, {new StringBuffer("sham-wow")}};
			_oStringBuffer_a = new StringBuffer("murder my days one at a time");
			_oStringBuffer_b = new Object[] {new StringBuffer("chaiyya chaiyya")};

			_testobj_a = new TestObject("food");
			_testobj_b = new TestObject[]{new TestObject("ten"), new TestObject("hut")};
			_testobj_c = new TestObject[][]{null, {}, {new TestObject("mighty-mend")}};

			_test_a = new Object[1];
			_test_b = new Object[1];
			_test_a[0] = _test_b;
			_test_b[0] = _test_a;

			_hetero_a = new Object[]{new Character('a'), Boolean.TRUE, new Byte((byte) 9), new Short((short) 9), new Integer(9), new Long(9), new Float(9.9), new Double(9.9), "getStartupInfo", _testDate, boolean.class, null, "null", _CONST_INT, Class.class};
			_testRefs0 = new Object[]{_testDate, Boolean.FALSE, _CONST_CHAR, _CONST_BYTE, _CONST_SHORT, _CONST_INT, _CONST_LONG, _CONST_FLOAT, _CONST_DOUBLE, "Happy"};
			_testRefs1 = new Object[]{_testDate, Boolean.FALSE, _CONST_CHAR, _CONST_BYTE, _CONST_SHORT, _CONST_INT, _CONST_LONG, _CONST_FLOAT, _CONST_DOUBLE, "Happy"};
			_arrayO = new Object[] { "foo", true, null, 16L, 3.14};
			_arrayS = new String[] {"fingers", "toes"};
			_arrayArrayO = new Object[][] {{"true", "false"}, {1L, 2L, 3L}, null, {1.1, 2.2}, {true, false}};

			_bigInts = new BigInteger[] { new BigInteger("-123456789012345678901234567890"), new BigInteger("0"), new BigInteger("123456789012345678901234567890")};
			_oBigInts = new Object[] { new BigInteger("-123456789012345678901234567890"), new BigInteger("0"), new BigInteger("123456789012345678901234567890")};
			_bigDecs = new BigDecimal[] { new BigDecimal("-123456789012345678901234567890.123456789012345678901234567890"), new BigDecimal("0.0"), new BigDecimal("123456789012345678901234567890.123456789012345678901234567890")};
			_oBigDecs = new Object[] { new BigDecimal("-123456789012345678901234567890.123456789012345678901234567890"), new BigDecimal("0.0"), new BigDecimal("123456789012345678901234567890.123456789012345678901234567890")};
		}
	}

	@Test
	public void testArray() throws Exception
	{
		TestArray obj = new TestArray();
		obj.init();
		String jsonOut = TestUtil.getJsonString(obj);
		TestUtil.printLine(jsonOut);

		TestArray root = (TestArray) TestUtil.readJsonObject(jsonOut);
		assertArray(root);

		// GSON cannot handle it
//        Gson gson = new Gson();
//        String j = gson.toJson(obj);
	}

	private void assertArray(TestArray root)
	{
		assertTrue(root._empty_a != null);
		assertNull(root._empty_b);
		assertTrue(root._empty_c.length == 0);
		assertTrue(root._empty_d.length == 2);
		assertTrue(root._empty_d[0].equals(new Empty()));
		assertNull(root._empty_d[1]);
		assertTrue(root._empty_e.length == 1);
		assertTrue(root._empty_e[0].length == 0);
		assertTrue(root._empty_f.length == 4);
		assertTrue(root._empty_f[0].length == 2);
		assertTrue(root._empty_f[0][0].equals(new Empty()));
		assertNull(root._empty_f[0][1]);
		assertNull(root._empty_f[1]);
		assertTrue(root._empty_f[2].length == 0);
		assertTrue(root._empty_f[3].length == 1);

		assertTrue(root._booleans_a.getClass().equals(boolean[].class));
		assertTrue(root._booleans_a.length == 3);
		assertTrue(root._booleans_a[0]);
		assertFalse(root._booleans_a[1]);
		assertTrue(root._booleans_a[2]);
		assertTrue(root._booleans_b.length == 0);
		assertNull(root._booleans_c);
		assertTrue(root._booleans_d.length == 3);
		assertTrue(root._booleans_d[0].booleanValue());
		assertFalse(root._booleans_d[1].booleanValue());
		assertNull(root._booleans_d[2]);
		assertTrue(root._booleans_e.length == 0);
		assertNull(root._booleans_f);
		assertTrue(root._booleans_g.length == 1);
		assertNull(root._booleans_g[0]);
		assertTrue(root._booleans_h.length == 5);
		assertTrue(root._booleans_h[0].length == 1);
		assertTrue(root._booleans_h[1].length == 2);
		assertTrue(root._booleans_h[2].length == 3);
		assertTrue(root._booleans_h[0][0]);
		assertTrue(root._booleans_h[1][0]);
		assertFalse(root._booleans_h[1][1]);
		assertTrue(root._booleans_h[2][0]);
		assertFalse(root._booleans_h[2][1]);
		assertTrue(root._booleans_h[2][2]);
		assertNull(root._booleans_h[3]);
		assertTrue(root._booleans_h[4].length == 0);
		assertNull(root._booleans_i);
		assertTrue(root._booleans_j.length == 1);
		assertNull(root._booleans_j[0]);

		assertTrue(root._chars_a[0] == 'a');
		assertTrue(root._chars_a[1] == '\t');
		assertTrue(root._chars_a[2] == '\u0005');
		assertTrue(root._chars_b.length == 0);
		assertNull(root._chars_c);
		assertTrue(root._chars_d[0].charValue() == 'a');
		assertTrue(root._chars_d[1].charValue() == '\t');
		assertTrue(root._chars_d[2].charValue() == '\u0006');
		assertTrue(root._chars_e.length == 0);
		assertNull(root._chars_f);

		assertTrue(root._bytes_a[0] == Byte.MIN_VALUE);
		assertTrue(root._bytes_a[1] == -1);
		assertTrue(root._bytes_a[2] == 0);
		assertTrue(root._bytes_a[3] == 1);
		assertTrue(root._bytes_a[4] == Byte.MAX_VALUE);
		assertTrue(root._bytes_b.length == 0);
		assertNull(root._bytes_c);
		assertTrue(root._bytes_d[0].byteValue() == Byte.MIN_VALUE);
		assertTrue(root._bytes_d[1].byteValue() == -1);
		assertTrue(root._bytes_d[2].byteValue() == 0);
		assertTrue(root._bytes_d[3].byteValue() == 1);
		assertTrue(root._bytes_d[4].byteValue() == Byte.MAX_VALUE);
		assertTrue(root._bytes_e.length == 0);
		assertNull(root._bytes_f);
		assertTrue(root._bytes_g.length == 3);
		assertNull(root._bytes_g[0]);
		assertTrue(root._bytes_g[1].length == 0);
		assertTrue(root._bytes_g[2].length == 1);
		assertTrue(root._bytes_g[2][0] == Byte.MAX_VALUE);
		assertTrue(root._bytes_h.length == 3);
		assertNull(root._bytes_h[0]);
		assertTrue(root._bytes_h[1].length == 0);
		assertTrue(root._bytes_h[2].length == 1);
		assertTrue(root._bytes_h[2][0].byteValue() == Byte.MAX_VALUE);
		assertTrue(root._bytes_i[0] == 16);

		assertTrue(root._chars_g.length == 3);
		assertTrue(root._chars_g[0].length == 3);
		assertTrue(root._chars_g[0][0] == 'a');
		assertTrue(root._chars_g[0][1] == '\t');
		assertTrue(root._chars_g[0][2] == '\u0004');
		assertNull(root._chars_g[1]);
		assertTrue(root._chars_g[2].length == 0);
		assertTrue(root._chars_h.length == 3);
		assertTrue(root._chars_h[0].length == 3);
		assertTrue(root._chars_h[0][0].equals(new Character('a')));
		assertTrue(root._chars_h[0][1].equals(new Character('\t')));
		assertTrue(root._chars_h[0][2].equals(new Character('\u0004')));
		assertNull(root._chars_h[1]);
		assertTrue(root._chars_h[2].length == 0);

		assertTrue(root._shorts_a[0] == Short.MIN_VALUE);
		assertTrue(root._shorts_a[1] == -1);
		assertTrue(root._shorts_a[2] == 0);
		assertTrue(root._shorts_a[3] == 1);
		assertTrue(root._shorts_a[4] == Short.MAX_VALUE);
		assertTrue(root._shorts_b.length == 0);
		assertNull(root._shorts_c);
		assertTrue(root._shorts_d[0].shortValue() == Short.MIN_VALUE);
		assertTrue(root._shorts_d[1].shortValue() == -1);
		assertTrue(root._shorts_d[2].shortValue() == 0);
		assertTrue(root._shorts_d[3].shortValue() == 1);
		assertTrue(root._shorts_d[4].shortValue() == Short.MAX_VALUE);
		assertTrue(root._shorts_e.length == 0);
		assertNull(root._shorts_f);
		assertTrue(root._shorts_g.length == 3);
		assertNull(root._shorts_g[0]);
		assertTrue(root._shorts_g[1].length == 0);
		assertTrue(root._shorts_g[2].length == 1);
		assertTrue(root._shorts_g[2][0] == Short.MAX_VALUE);
		assertTrue(root._shorts_h.length == 3);
		assertNull(root._shorts_h[0]);
		assertTrue(root._shorts_h[1].length == 0);
		assertTrue(root._shorts_h[2].length == 1);
		assertTrue(root._shorts_h[2][0].shortValue() == Short.MAX_VALUE);

		assertTrue(root._ints_a[0] == Integer.MIN_VALUE);
		assertTrue(root._ints_a[1] == -1);
		assertTrue(root._ints_a[2] == 0);
		assertTrue(root._ints_a[3] == 1);
		assertTrue(root._ints_a[4] == Integer.MAX_VALUE);
		assertTrue(root._ints_b.length == 0);
		assertNull(root._ints_c);
		assertTrue(root._ints_d[0].intValue() == Integer.MIN_VALUE);
		assertTrue(root._ints_d[1].intValue() == -1);
		assertTrue(root._ints_d[2].intValue() == 0);
		assertTrue(root._ints_d[3].intValue() == 1);
		assertTrue(root._ints_d[4].intValue() == Integer.MAX_VALUE);
		assertTrue(root._ints_e.length == 0);
		assertNull(root._ints_f);
		assertTrue(root._int_1.length == 3);
		assertTrue(root._int_1[0].length == 5);
		assertTrue(root._int_1[0][0] == Integer.MIN_VALUE);
		assertTrue(root._int_1[0][1] == -1);
		assertTrue(root._int_1[0][2] == 0);
		assertTrue(root._int_1[0][3] == 1);
		assertTrue(root._int_1[0][4] == Integer.MAX_VALUE);
		assertNull(root._int_1[1]);
		assertTrue(root._int_1[2].length == 4);
		assertTrue(root._int_1[2][0] == -1);
		assertTrue(root._int_1[2][1] == 0);
		assertTrue(root._int_1[2][2] == 1);
		assertTrue(root._int_1[2][3] == 2);
		assertTrue(root._ints_g.length == 3);
		assertNull(root._ints_g[0]);
		assertTrue(root._ints_g[1].length == 0);
		assertTrue(root._ints_g[2].length == 1);
		assertTrue(root._ints_g[2][0].intValue() == Integer.MAX_VALUE);

		assertTrue(root._longs_a[0] == Long.MIN_VALUE);
		assertTrue(root._longs_a[1] == -1);
		assertTrue(root._longs_a[2] == 0);
		assertTrue(root._longs_a[3] == 1);
		assertTrue(root._longs_a[4] == Long.MAX_VALUE);
		assertTrue(root._longs_b.length == 0);
		assertNull(root._longs_c);
		assertTrue(root._longs_d[0].longValue() == Long.MIN_VALUE);
		assertTrue(root._longs_d[1].longValue() == -1);
		assertTrue(root._longs_d[2].longValue() == 0);
		assertTrue(root._longs_d[3].longValue() == 1);
		assertTrue(root._longs_d[4].longValue() == Long.MAX_VALUE);
		assertTrue(root._longs_e.length == 0);
		assertNull(root._longs_f);
		assertTrue(root._longs_1.length == 4);
		assertTrue(root._longs_1[0].length == 3);
		assertTrue(root._longs_1[1].length == 0);
		assertTrue(root._longs_1[2].length == 1);
		assertTrue(root._longs_1[3].length == 1);
		assertTrue(root._ints_g.length == 3);
		assertNull(root._longs_g[0]);
		assertTrue(root._longs_g[1].length == 0);
		assertTrue(root._longs_g[2].length == 1);
		assertTrue(root._longs_g[2][0].longValue() == Long.MAX_VALUE);

		assertTrue(root._floats_a.length == 4);
		assertTrue(root._floats_a[0] == 0.0f);
		assertTrue(root._floats_a[1] == Float.MIN_VALUE);
		assertTrue(root._floats_a[2] == Float.MAX_VALUE);
		assertTrue(root._floats_a[3] == -1.0f);
		assertTrue(root._floats_b.length == 0);
		assertNull(root._floats_c);
		assertTrue(root._floats_d.length == 5);
		assertTrue(root._floats_d[0].equals(new Float(0.0f)));
		assertTrue(root._floats_d[1].equals(new Float(Float.MIN_VALUE)));
		assertTrue(root._floats_d[2].equals(new Float(Float.MAX_VALUE)));
		assertTrue(root._floats_d[3].equals(new Float(-1.0f)));
		assertNull(root._floats_d[4]);
		assertTrue(root._floats_e.length == 0);
		assertNull(root._floats_f);
		assertNull(root._floats_g[0]);
		assertTrue(root._floats_g[1].length == 0);
		assertTrue(root._floats_g[2].length == 1);
		assertTrue(root._floats_g[2][0] == Float.MAX_VALUE);
		assertNull(root._floats_h[0]);
		assertTrue(root._floats_h[1].length == 0);
		assertTrue(root._floats_h[2].length == 1);
		assertTrue(root._floats_h[2][0].floatValue() == Float.MAX_VALUE);

		assertTrue(root._doubles_a.length == 4);
		assertTrue(root._doubles_a[0] == 0.0);
		assertTrue(root._doubles_a[1] == Double.MIN_VALUE);
		assertTrue(root._doubles_a[2] == Double.MAX_VALUE);
		assertTrue(root._doubles_a[3] == -1.0);
		assertTrue(root._doubles_b.length == 0);
		assertNull(root._doubles_c);
		assertTrue(root._doubles_d.length == 5);
		assertTrue(root._doubles_d[0].equals(new Double(0.0)));
		assertTrue(root._doubles_d[1].equals(new Double(Double.MIN_VALUE)));
		assertTrue(root._doubles_d[2].equals(new Double(Double.MAX_VALUE)));
		assertTrue(root._doubles_d[3].equals(new Double(-1.0)));
		assertNull(root._doubles_d[4]);
		assertTrue(root._doubles_e.length == 0);
		assertNull(root._doubles_f);
		assertNull(root._doubles_g[0]);
		assertTrue(root._doubles_g[1].length == 0);
		assertTrue(root._doubles_g[2].length == 1);
		assertTrue(root._doubles_g[2][0] == Double.MAX_VALUE);
		assertNull(root._doubles_h[0]);
		assertTrue(root._doubles_h[1].length == 0);
		assertTrue(root._doubles_h[2].length == 1);
		assertTrue(root._doubles_h[2][0].doubleValue() == Double.MAX_VALUE);

		assertNull(root._strings_a[0]);
		assertTrue(root._strings_a[1].equals("\u0007"));
		assertTrue(root._strings_a[2].equals("\t\rfood\n\f"));
		assertTrue(root._strings_a[3].equals("null"));
		assertTrue(root._strings_b.length == 4);
		assertTrue(root._strings_b[0].length == 3);
		assertTrue(root._strings_b[0][0].equals("alpha"));
		assertTrue(root._strings_b[0][1].equals("bravo"));
		assertTrue(root._strings_b[0][2].equals("charlie"));
		assertTrue(root._strings_b[1].length == 4);
		assertNull(root._strings_b[1][0]);
		assertTrue(root._strings_b[1][1].equals("\u0007"));
		assertTrue(root._strings_b[1][2].equals("\t"));
		assertTrue(root._strings_b[1][3].equals("null"));
		assertNull(root._strings_b[2]);
		assertTrue(root._strings_b[3].length == 0);

		assertTrue(root._dates_a[0].equals(new Date(0)));
		assertTrue(root._dates_a[1].equals(_testDate));
		assertNull(root._dates_a[2]);
		assertNull(root._dates_b[0]);
		assertTrue(root._dates_b[1].length == 0);
		assertTrue(root._dates_b[2].length == 1);
		assertTrue(root._dates_b[2][0].equals(_testDate));

		assertTrue(root._classes_a.length == 10);
		assertTrue(root._classes_a[0].equals(boolean.class));
		assertTrue(root._classes_a[1].equals(char.class));
		assertTrue(root._classes_a[2].equals(byte.class));
		assertTrue(root._classes_a[3].equals(short.class));
		assertTrue(root._classes_a[4].equals(int.class));
		assertTrue(root._classes_a[5].equals(long.class));
		assertTrue(root._classes_a[6].equals(float.class));
		assertTrue(root._classes_a[7].equals(double.class));
		assertNull(root._classes_a[8]);
		assertTrue(root._classes_a[9].equals(String.class));
		assertNull(root._classes_b[0]);
		assertTrue(root._classes_b[1].length == 0);
		assertTrue(root._classes_b[2].length == 1);
		assertTrue(root._classes_b[2][0].equals(Date.class));

		assertTrue(root._stringbuffer_a.toString().equals("food"));
		assertTrue(root._stringbuffer_b.length == 3);
		assertTrue(root._stringbuffer_b[0].toString().equals("first"));
		assertTrue(root._stringbuffer_b[1].toString().equals("second"));
		assertNull(root._stringbuffer_b[2]);
		assertTrue(root._stringbuffer_c.length == 3);
		assertNull(root._stringbuffer_c[0]);
		assertTrue(root._stringbuffer_c[1].length == 0);
		assertTrue(root._stringbuffer_c[2].length == 1);
		assertTrue(root._stringbuffer_c[2][0].toString().equals("sham-wow"));

		assertTrue("murder my days one at a time".equals(root._oStringBuffer_a.toString()));
		assertTrue(root._oStringBuffer_b.length == 1);
		assertTrue("chaiyya chaiyya".equals(root._oStringBuffer_b[0].toString()));

		assertTrue(root._testobj_a.equals(new TestObject("food")));
		assertTrue(root._testobj_b.length == 2);
		assertTrue(root._testobj_b[0].getName().equals("ten"));
		assertTrue(root._testobj_b[1].getName().equals("hut"));
		assertTrue(root._testobj_c.length == 3);
		assertNull(root._testobj_c[0]);
		assertTrue(root._testobj_c[1].length == 0);
		assertTrue(root._testobj_c[2].length == 1);
		assertTrue(root._testobj_c[2][0].equals(new TestObject("mighty-mend")));

		assertTrue(root._test_a.length == 1);
		assertTrue(root._test_b.length == 1);
		assertTrue(root._test_a[0] == root._test_b);
		assertTrue(root._test_b[0] == root._test_a);

		assertTrue(root._hetero_a.length == 15);
		assertTrue(root._hetero_a[0].equals(new Character('a')));
		assertTrue(root._hetero_a[1].equals(Boolean.TRUE));
		assertTrue(root._hetero_a[2].equals(new Byte((byte) 9)));
		assertTrue(root._hetero_a[3].equals(new Short((short) 9)));
		assertTrue(root._hetero_a[4].equals(new Integer(9)));
		assertTrue(root._hetero_a[5].equals(new Long(9)));
		assertTrue(root._hetero_a[6].equals(new Float(9.9)));
		assertTrue(root._hetero_a[7].equals(new Double(9.9)));
		assertTrue(root._hetero_a[8].equals("getStartupInfo"));
		assertTrue(root._hetero_a[9].equals(_testDate));
		assertTrue(root._hetero_a[10].equals(boolean.class));
		assertNull(root._hetero_a[11]);
		assertTrue(root._hetero_a[12].equals("null"));
		assertTrue(root._hetero_a[13].equals(_CONST_INT));
		assertTrue(root._hetero_a[14].equals(Class.class));

		assertTrue(root._testRefs0.length == 10);
		assertTrue(root._testRefs0[0].equals(_testDate));
		assertTrue(root._testRefs0[1].equals(Boolean.FALSE));
		assertTrue(root._testRefs0[2].equals(_CONST_CHAR));
		assertTrue(root._testRefs0[3].equals(_CONST_BYTE));
		assertTrue(root._testRefs0[4].equals(_CONST_SHORT));
		assertTrue(root._testRefs0[5].equals(_CONST_INT));
		assertTrue(root._testRefs0[6].equals(_CONST_LONG));
		assertTrue(root._testRefs0[7].equals(_CONST_FLOAT));
		assertTrue(root._testRefs0[8].equals(_CONST_DOUBLE));
		assertTrue(root._testRefs0[9].equals("Happy"));

		assertTrue(root._testRefs1.length == 10);
		assertFalse(root._testRefs1[0] == root._testRefs0[0]);
		assertTrue(root._testRefs1[1] == root._testRefs0[1]);    // Works because we only read in Boolean.TRUE, Boolean.FALSE, or null
		assertTrue(root._testRefs1[2] == root._testRefs0[2]);
		assertTrue(root._testRefs1[3] == root._testRefs0[3]);
		assertTrue(root._testRefs1[4] == root._testRefs0[4]);
		assertTrue(root._testRefs1[5] == root._testRefs0[5]);
		assertTrue(root._testRefs1[6].equals(root._testRefs0[6]));
		assertTrue(root._testRefs1[7] != root._testRefs0[7]);       // Primitive Wrappers are treated like primitives
		assertTrue(root._testRefs1[8].equals(root._testRefs0[8]));
		assertTrue(root._testRefs1[9].equals(root._testRefs0[9]));

		assertTrue(root._arrayO instanceof Object[]);
		Object[] items = (Object[]) root._arrayO;
		assertTrue(items.length == 5);
		assertTrue("foo".equals(items[0]));
		assertTrue(Boolean.TRUE.equals(items[1]));
		assertNull(items[2]);
		assertTrue(((Long)16L).equals(items[3]));
		assertTrue(((Double)3.14).equals(items[4]));

		assertTrue(root._arrayS instanceof String[]);
		String[] strItems = (String[]) root._arrayS;
		assertTrue(strItems.length == 2);
		assertTrue("fingers".equals(strItems[0]));
		assertTrue("toes".equals(strItems[1]));

		assertTrue(root._arrayArrayO instanceof Object[]);
		assertTrue(root._arrayArrayO instanceof Object[][]);
		assertFalse(root._arrayArrayO instanceof Object[][][]);

		assertTrue(root._bigInts[0].equals(new BigInteger("-123456789012345678901234567890")));
		assertTrue(root._bigInts[1].equals(new BigInteger("0")));
		assertTrue(root._bigInts[2].equals(new BigInteger("123456789012345678901234567890")));

		assertTrue(root._oBigInts[0].equals(new BigInteger("-123456789012345678901234567890")));
		assertTrue(root._oBigInts[1].equals(new BigInteger("0")));
		assertTrue(root._oBigInts[2].equals(new BigInteger("123456789012345678901234567890")));

		assertTrue(root._bigDecs[0].equals(new BigDecimal("-123456789012345678901234567890.123456789012345678901234567890")));
		assertTrue(root._bigDecs[1].equals(new BigDecimal("0.0")));
		assertTrue(root._bigDecs[2].equals(new BigDecimal("123456789012345678901234567890.123456789012345678901234567890")));

		assertTrue(root._oBigDecs[0].equals(new BigDecimal("-123456789012345678901234567890.123456789012345678901234567890")));
		assertTrue(root._oBigDecs[1].equals(new BigDecimal("0.0")));
		assertTrue(root._oBigDecs[2].equals(new BigDecimal("123456789012345678901234567890.123456789012345678901234567890")));
	}

	private static class TestMap implements Serializable
	{
		private HashMap _strings_a;
		private Map _strings_b;
		private Map _strings_c;
		private Map _testobjs_a;
		private Map _map_col;
		private Map _map_col_2;
		private Map _map_col_3;
		private Map _map_obj;
		private Map _map_con;
		private TreeMap _typedMap;

		private void init()
		{
			_strings_a = new HashMap();
			_strings_b = new HashMap();
			_strings_c = null;
			_testobjs_a = new TreeMap();
			_map_col = new HashMap();
			_map_col_2 = new TreeMap();
			_map_col_3 = new HashMap();
			_map_obj = new HashMap();
			_map_con = new ConcurrentHashMap();

			_strings_a.put("woods", "tiger");
			_strings_a.put("mickleson", "phil");
			_strings_a.put("garcia", "sergio");

			_testobjs_a.put(new TestObject("one"), new TestObject("alpha"));
			_testobjs_a.put(new TestObject("two"), new TestObject("bravo"));

			List l = new LinkedList();
			l.add("andromeda");
			_map_col.put(new TestObject[]{new TestObject("earth"), new TestObject("jupiter")}, l);
			_map_col_2.put("cat", new Object[]{"tiger", "lion", "cheetah", "jaguar"});
			_map_col_3.put(new Object[]{"composite", "key"}, "value");

			_map_obj.put(new Integer(99), new Double(.123));
			_map_obj.put(null, null);

			_map_con.put(new TestObject("alpha"), new TestObject("one"));
			_map_con.put(new TestObject("bravo"), new TestObject("two"));

			_typedMap = new TreeMap();
			_typedMap.put("a", "alpha");
			_typedMap.put("b", "bravo");
		}
	}

	@Test
	public void testMap() throws Exception
	{
		TestMap obj = new TestMap();
		obj.init();
		String jsonOut = TestUtil.getJsonString(obj);
		TestUtil.printLine(jsonOut);

		TestMap root = (TestMap) TestUtil.readJsonObject(jsonOut);
		assertMap(root);
	}

	private void assertMap(TestMap root)
	{
		assertTrue(root._strings_a.size() == 3);
		assertTrue(root._strings_a.get("woods").equals("tiger"));
		assertTrue(root._strings_a.get("mickleson").equals("phil"));
		assertTrue(root._strings_a.get("garcia").equals("sergio"));
		assertTrue(root._strings_b.isEmpty());
		assertNull(root._strings_c);

		assertTrue(root._testobjs_a.size() == 2);
		assertTrue(root._testobjs_a.get(new TestObject("one")).equals(new TestObject("alpha")));
		assertTrue(root._testobjs_a.get(new TestObject("two")).equals(new TestObject("bravo")));

		assertTrue(root._map_col.size() == 1);
		Iterator i = root._map_col.keySet().iterator();
		TestObject[] key = (TestObject[]) i.next();
		key[0]._name.equals("earth");
		key[1]._name.equals("jupiter");
		i = root._map_col.values().iterator();
		Collection list = (Collection) i.next();
		list.contains("andromeda");

		// Check value-side of Map with Object[] (special case because Object[]'s @type is never written)
		Object[] catTypes = (Object[]) root._map_col_2.get("cat");
		assertTrue(catTypes[0].equals("tiger"));
		assertTrue(catTypes[1].equals("lion"));
		assertTrue(catTypes[2].equals("cheetah"));
		assertTrue(catTypes[3].equals("jaguar"));

		assertTrue(root._map_col_3.size() == 1);
		i = root._map_col_3.keySet().iterator();
		Object[] key_a = (Object[]) i.next();
		key_a[0].equals("composite");
		key_a[1].equals("key");
		String value = (String) root._map_col_3.get(key_a);
		assertTrue("value".equals(value));

		assertTrue(root._map_obj.size() == 2);
		assertTrue(root._map_obj.get(new Integer(99)).equals(new Double(.123)));
		assertNull(root._map_obj.get(null));

		assertTrue(root._map_con.size() == 2);
		assertTrue(root._map_con instanceof ConcurrentHashMap);
		i = root._map_con.entrySet().iterator();
		while (i.hasNext())
		{
			Map.Entry e = (Map.Entry) i.next();
			TestObject key1 = (TestObject) e.getKey();
			TestObject value1 = (TestObject) e.getValue();
			if (key1.equals(new TestObject("alpha")))
			{
				assertTrue(value1.getName().equals("one"));
			}
			else if (key1.equals(new TestObject("bravo")))
			{
				assertTrue(value1.getName().equals("two"));
			}
			else
			{
				assertTrue("Unknown key", false);
			}
		}

		assertTrue(root._typedMap != null);
		assertTrue(root._typedMap.size() == 2);
		assertTrue(root._typedMap.get("a").equals("alpha"));
		assertTrue(root._typedMap.get("b").equals("bravo"));
	}

	@Test
	public void testPerformance() throws Exception
	{
		byte[] bytes = new byte[128 * 1024];
		Random r = new Random();
		r.nextBytes(bytes);
		String json = TestUtil.getJsonString(bytes);

		byte[] bytes2 = (byte[]) TestUtil.readJsonObject(json);

		for (int i = 0; i < bytes.length; i++)
		{
			assertTrue(bytes[i] == bytes2[i]);
		}
	}

	@Test
	public void testRoots() throws Exception
	{
		// Test Object[] as root element passed in
		Object[] foo = {new TestObject("alpha"), new TestObject("beta")};

		String jsonOut = TestUtil.getJsonString(foo);
		TestUtil.printLine(jsonOut);

		Object[] bar = (Object[]) JsonReader.jsonToJava(jsonOut);
		assertTrue(bar.length == 2);
		assertTrue(bar[0].equals(new TestObject("alpha")));
		assertTrue(bar[1].equals(new TestObject("beta")));

		String json = "[\"getStartupInfo\",[\"890.022905.16112006.00024.0067ur\",\"machine info\"]]";
		Object[] baz = (Object[]) JsonReader.jsonToJava(json);
		assertTrue(baz.length == 2);
		assertTrue("getStartupInfo".equals(baz[0]));
		Object[] args = (Object[]) baz[1];
		assertTrue(args.length == 2);
		assertTrue("890.022905.16112006.00024.0067ur".equals(args[0]));
		assertTrue("machine info".equals(args[1]));

		String hw = "[\"Hello, World\"]";
		Object[] qux = (Object[]) JsonReader.jsonToJava(hw);
		assertTrue(qux != null);
		assertTrue("Hello, World".equals(qux[0]));

		// Whitespace
		String pkg = TestObject.class.getName();
		Object[] fred = (Object[]) JsonReader.jsonToJava("[  {  \"@type\"  :  \"" + pkg + "\"  ,  \"_name\"  :  \"alpha\"  ,  \"_other\"  :  null  }  ,  {  \"@type\"  :  \"" + pkg + "\"  ,  \"_name\"  :  \"beta\"  ,  \"_other\" : null  }  ]  ");
		assertTrue(fred != null);
		assertTrue(fred.length == 2);
		assertTrue(fred[0].equals(new TestObject("alpha")));
		assertTrue(fred[1].equals(new TestObject("beta")));

		Object[] wilma = (Object[]) JsonReader.jsonToJava("[{\"@type\":\"" + pkg + "\",\"_name\" : \"alpha\" , \"_other\":null,\"fake\":\"_typeArray\"},{\"@type\": \"" + pkg + "\",\"_name\":\"beta\",\"_other\":null}]");
		assertTrue(wilma != null);
		assertTrue(wilma.length == 2);
		assertTrue(wilma[0].equals(new TestObject("alpha")));
		assertTrue(wilma[1].equals(new TestObject("beta")));
	}

	@Test
	public void testRoots2() throws Exception
	{
		// Test root JSON type as [ ]
		Object array = new Object[] {"Hello"};
		String json = TestUtil.getJsonString(array);
		Object oa = JsonReader.jsonToJava(json);
		assertTrue(oa.getClass().isArray());
		assertTrue(((Object[])oa)[0].equals("Hello"));

		// Test root JSON type as { }
		Calendar cal = Calendar.getInstance();
		cal.set(1965, 11, 17);
		json = TestUtil.getJsonString(cal);
		TestUtil.printLine("json = " + json);
		Object obj = JsonReader.jsonToJava(json);
		assertTrue(!obj.getClass().isArray());
		Calendar date = (Calendar) obj;
		assertTrue(date.get(Calendar.YEAR) == 1965);
		assertTrue(date.get(Calendar.MONTH) == 11);
		assertTrue(date.get(Calendar.DAY_OF_MONTH) == 17);
	}

	@Test
	public void testNoDefaultConstructor() throws Exception
	{
		Calendar c = Calendar.getInstance();
		c.set(2010, 5, 5, 5, 5, 5);
		String[] strings = new String[] { "C", "C++", "Java"};
		int[] ints = new int[] {1, 2, 4, 8, 16, 32, 64, 128};
		Object foo = new TestJsonNoDefaultOrPublicConstructor("Hello, World.", c.getTime(), (byte) 1, new Byte((byte)11), (short) 2, new Short((short)22), 3, new Integer(33), 4L, new Long(44L), 5.0f, new Float(55.0f), 6.0, new Double(66.0), true, Boolean.TRUE,'J', new Character('K'), strings, ints, new BigDecimal(1.1));
		String jsonOut = TestUtil.getJsonString(foo);
		TestUtil.printLine(jsonOut);

		TestJsonNoDefaultOrPublicConstructor bar = (TestJsonNoDefaultOrPublicConstructor) TestUtil.readJsonObject(jsonOut);

		assertTrue("Hello, World.".equals(bar.getString()));
		assertTrue(bar.getDate().equals(c.getTime()));
		assertTrue(bar.getByte() == 1);
		assertTrue(bar.getShort() == 2);
		assertTrue(bar.getInt() == 3);
		assertTrue(bar.getLong() == 4);
		assertTrue(bar.getFloat() == 5.0f);
		assertTrue(bar.getDouble() == 6.0);
		assertTrue(bar.getBoolean());
		assertTrue(bar.getChar() == 'J');
		assertTrue(bar.getStrings() != null);
		assertTrue(bar.getStrings().length == strings.length);
		assertTrue(bar.getInts() != null);
		assertTrue(bar.getInts().length == ints.length);
		assertTrue(bar._bigD.equals(new BigDecimal(1.1)));
	}

	private static class TestByte implements Serializable
	{
		private final Byte _arrayElement;
		private final Byte[] _typeArray;
		private final Object[] _objArray;
		private final Object _polyRefTarget;
		private final Object _polyRef;
		private final Object _polyNotRef;
		private final Byte _min;
		private final Byte _max;
		private final Byte _null;

		private TestByte()
		{
			_arrayElement = new Byte((byte) -1);
			_polyRefTarget = new Byte((byte)71);
			_polyRef = _polyRefTarget;
			_polyNotRef = new Byte((byte) 71);
			Byte local = new Byte((byte)75);
			_null  = null;
			_typeArray = new Byte[] {_arrayElement, (byte) 44, local, _null, null, new Byte((byte)44)};
			_objArray = new Object[] {_arrayElement, (byte) 69, local, _null, null, new Byte((byte)69)};
			_min = Byte.MIN_VALUE;
			_max = Byte.MAX_VALUE;
		}
	}

	@Test
	public void testByte() throws Exception
	{
		TestByte test = new TestByte();
		String json = TestUtil.getJsonString(test);
		TestUtil.printLine("json = " + json);

		TestByte that = (TestByte) TestUtil.readJsonObject(json);

		assertTrue(that._arrayElement.equals((byte) -1));
		assertTrue(that._polyRefTarget.equals((byte) 71));
		assertTrue(that._polyRef.equals((byte) 71));
		assertTrue(that._polyNotRef.equals((byte) 71));
		assertTrue(that._polyRef == that._polyRefTarget);
		assertTrue(that._polyNotRef == that._polyRef);             // byte cache is working

		assertTrue(that._typeArray.length == 6);
		assertTrue(that._typeArray[0] == that._arrayElement);  // byte cache is working
		assertTrue(that._typeArray[1] instanceof Byte);
		assertTrue(that._typeArray[1] instanceof Byte);
		assertTrue(that._typeArray[1].equals((byte) 44));
		assertTrue(that._objArray.length == 6);
		assertTrue(that._objArray[0] == that._arrayElement);   // byte cache is working
		assertTrue(that._objArray[1] instanceof Byte);
		assertTrue(that._objArray[1].equals((byte) 69));
		assertTrue(that._polyRefTarget instanceof Byte);
		assertTrue(that._polyNotRef instanceof Byte);
		assertTrue(that._objArray[2].equals((byte) 75));

		assertTrue(that._null == null);
		assertTrue(that._typeArray[3] == null);
		assertTrue(that._typeArray[4] == null);
		assertTrue(that._objArray[3] == null);
		assertTrue(that._objArray[4] == null);

		assertTrue(that._min.equals(Byte.MIN_VALUE));
		assertTrue(that._max.equals(Byte.MAX_VALUE));
		assertTrue(that._min == Byte.MIN_VALUE);   // Verifies non-referenced byte caching is working
		assertTrue(that._max == Byte.MAX_VALUE); // Verifies non-referenced byte caching is working
	}

	private static class TestShort implements Serializable
	{
		private final Short _arrayElement;
		private final Short[] _typeArray;
		private final Object[] _objArray;
		private final Object _polyRefTarget;
		private final Object _polyRef;
		private final Object _polyNotRef;
		private final Short _min;
		private final Short _max;
		private final Short _null;

		private TestShort()
		{
			_arrayElement = new Short((short) -1);
			_polyRefTarget = new Short((short)710);
			_polyRef = _polyRefTarget;
			_polyNotRef = new Short((short) 710);
			Short local = new Short((short)75);
			_null  = null;
			_typeArray = new Short[] {_arrayElement, (short) 44, local, _null, null, new Short((short)44)};
			_objArray = new Object[] {_arrayElement, (short) 69, local, _null, null, new Short((short)69)};
			_min = Short.MIN_VALUE;
			_max = Short.MAX_VALUE;
		}
	}

	@Test
	public void testShort() throws Exception
	{
		TestShort test = new TestShort();
		String json = TestUtil.getJsonString(test);
		TestUtil.printLine("json = " + json);
		TestShort that = (TestShort) TestUtil.readJsonObject(json);

		assertTrue(that._arrayElement.equals((short) -1));
		assertTrue(that._polyRefTarget.equals((short) 710));
		assertTrue(that._polyRef.equals((short) 710));
		assertTrue(that._polyNotRef.equals((short) 710));
		assertTrue(that._polyRef != that._polyRefTarget);   // Primitive wrappers are treated like primitives (no ref)
		assertFalse(that._polyNotRef == that._polyRef);

		assertTrue(that._typeArray.length == 6);
		assertTrue(that._typeArray[0] == that._arrayElement);
		assertTrue(that._typeArray[1] instanceof Short);
		assertTrue(that._typeArray[1] instanceof Short);
		assertTrue(that._typeArray[1].equals((short) 44));
		assertTrue(that._objArray.length == 6);
		assertTrue(that._objArray[0] == that._arrayElement);
		assertTrue(that._objArray[1] instanceof Short);
		assertTrue(that._objArray[1].equals((short) 69));
		assertTrue(that._polyRefTarget instanceof Short);
		assertTrue(that._polyNotRef instanceof Short);

		assertTrue(that._objArray[2] == that._typeArray[2]);
		assertTrue(that._objArray[2].equals((short) 75));

		// Because of cache in Short.valueOf(), these values between -128 and 127 will have same address.
		assertTrue(that._typeArray[1] == that._typeArray[5]);
		assertTrue(that._objArray[1] == that._objArray[5]);

		assertTrue(that._null == null);
		assertTrue(that._typeArray[3] == null);
		assertTrue(that._typeArray[4] == null);
		assertTrue(that._objArray[3] == null);
		assertTrue(that._objArray[4] == null);

		assertTrue(that._min.equals(Short.MIN_VALUE));
		assertTrue(that._max.equals(Short.MAX_VALUE));
		assertTrue(that._min == Short.MIN_VALUE);
		assertTrue(that._max == Short.MAX_VALUE);
	}

	private static class TestInteger implements Serializable
	{
		private final Integer _arrayElement;
		private final Integer[] _typeArray;
		private final Object[] _objArray;
		private final Object _polyRefTarget;
		private final Object _polyRef;
		private final Object _polyNotRef;
		private final Integer _min;
		private final Integer _max;
		private final Integer _null;

		private TestInteger()
		{
			_arrayElement = -1;
			_polyRefTarget = new Integer(710);
			_polyRef = _polyRefTarget;
			_polyNotRef = new Integer(710);
			Integer local = new Integer(75);
			_null  = null;
			_typeArray = new Integer[] {_arrayElement, 44, local, _null, null, new Integer(44), 0, new Integer(0)};
			_objArray = new Object[] {_arrayElement, 69, local, _null, null, new Integer(69), 0, new Integer(0)};
			_min = Integer.MIN_VALUE;
			_max = Integer.MAX_VALUE;
		}
	}

	@Test
	public void testInteger() throws Exception
	{
		TestInteger test = new TestInteger();
		String json = TestUtil.getJsonString(test);
		TestUtil.printLine("json = " + json);
		TestInteger that = (TestInteger) TestUtil.readJsonObject(json);

		assertTrue(that._arrayElement.equals(-1));
		assertTrue(that._polyRefTarget.equals(710));
		assertTrue(that._polyRef.equals(710));
		assertTrue(that._polyNotRef.equals(710));
		assertTrue(that._polyRef != that._polyRefTarget); // Primitive wrappers are treated like primitives (no ref)
		assertFalse(that._polyNotRef == that._polyRef);

		assertTrue(that._typeArray.length == 8);
		assertTrue(that._typeArray[0] == that._arrayElement);
		assertTrue(that._typeArray[1] instanceof Integer);
		assertTrue(that._typeArray[1] instanceof Integer);
		assertTrue(that._typeArray[1].equals(44));
		assertTrue(that._objArray.length == 8);
		assertTrue(that._objArray[0] == that._arrayElement);
		assertTrue(that._objArray[1] instanceof Integer);
		assertTrue(that._objArray[1].equals(69));
		assertTrue(that._polyRefTarget instanceof Integer);
		assertTrue(that._polyNotRef instanceof Integer);
		assertTrue(that._objArray[2].equals(75));

		assertTrue(that._objArray[2] == that._typeArray[2]);
		assertTrue(that._typeArray[1] == that._typeArray[5]);
		assertTrue(that._objArray[1] == that._objArray[5]);
		// an unreferenced 0 is cached
		assertTrue(that._typeArray[6] == that._typeArray[7]);
		assertTrue(that._objArray[6] == that._objArray[7]);

		assertTrue(that._null == null);
		assertTrue(that._typeArray[3] == null);
		assertTrue(that._typeArray[4] == null);
		assertTrue(that._objArray[3] == null);
		assertTrue(that._objArray[4] == null);

		assertTrue(that._min.equals(Integer.MIN_VALUE));
		assertTrue(that._max.equals(Integer.MAX_VALUE));
		assertTrue(that._min == Integer.MIN_VALUE);
		assertTrue(that._max == Integer.MAX_VALUE);
	}

	private static class TestLong implements Serializable
	{
		private final Long _arrayElement;
		private final Long[] _typeArray;
		private final Object[] _objArray;
		private final Object _polyRefTarget;
		private final Object _polyRef;
		private final Object _polyNotRef;
		private final Long _min;
		private final Long _max;
		private final Long _null;

		private TestLong()
		{
			_arrayElement = new Long(-1);
			_polyRefTarget = new Long(710);
			_polyRef = _polyRefTarget;
			_polyNotRef = new Long(710);
			Long local = new Long(75);
			_null = null;
			// 44 below is between -128 and 127, values cached by     Long Long.valueOf(long l)
			_typeArray = new Long[] {_arrayElement, 44L, local, _null, null, new Long(44)};
			_objArray = new Object[] {_arrayElement, 69L, local, _null, null, new Long(69)};
			_min = Long.MIN_VALUE;
			_max = Long.MAX_VALUE;
		}
	}

	@Test
	public void testLong() throws Exception
	{
		TestLong test = new TestLong();
		String json = TestUtil.getJsonString(test);
		TestUtil.printLine("json = " + json);
		TestLong that = (TestLong) TestUtil.readJsonObject(json);

		assertTrue(that._arrayElement.equals(-1L));
		assertTrue(that._polyRefTarget.equals(710L));
		assertTrue(that._polyRef.equals(710L));
		assertTrue(that._polyNotRef.equals(710L));
		assertTrue(that._polyRef != that._polyRefTarget);   // Primitive wrappers are treated like primitives (no ref)
		assertFalse(that._polyNotRef == that._polyRef);

		assertTrue(that._typeArray.length == 6);
		assertTrue(that._typeArray[0] == that._arrayElement);
		assertTrue(that._typeArray[1] instanceof Long);
		assertTrue(that._typeArray[1] instanceof Long);
		assertTrue(that._typeArray[1].equals(44L));
		assertTrue(that._objArray.length == 6);
		assertTrue(that._objArray[0] == that._arrayElement);
		assertTrue(that._objArray[1] instanceof Long);
		assertTrue(that._objArray[1].equals(69L));
		assertTrue(that._polyRefTarget instanceof Long);
		assertTrue(that._polyNotRef instanceof Long);

		assertTrue(that._objArray[2] == that._typeArray[2]);
		assertTrue(that._typeArray[1] == that._typeArray[5]);
		assertTrue(that._objArray[1] == that._objArray[5]);

		assertTrue(that._null == null);
		assertTrue(that._typeArray[3] == null);
		assertTrue(that._typeArray[4] == null);
		assertTrue(that._objArray[3] == null);
		assertTrue(that._objArray[4] == null);

		assertTrue(that._min.equals(Long.MIN_VALUE));
		assertTrue(that._max.equals(Long.MAX_VALUE));
		assertTrue(that._min == Long.MIN_VALUE);
		assertTrue(that._max == Long.MAX_VALUE);
	}

	private static class TestDouble implements Serializable
	{
		private final Double _arrayElement;
		private final Double[] _typeArray;
		private final Object[] _objArray;
		private final Object _polyRefTarget;
		private final Object _polyRef;
		private final Object _polyNotRef;
		private final Double _min;
		private final Double _max;
		private final Double _null;

		private TestDouble()
		{
			_arrayElement = new Double(-1);
			_polyRefTarget = new Double(71);
			_polyRef = _polyRefTarget;
			_polyNotRef = new Double(71);
			Double local = new Double(75);
			_null = null;
			_typeArray = new Double[] {_arrayElement, 44.0, local, _null, null, new Double(44)};
			_objArray = new Object[] {_arrayElement, 69.0, local, _null, null, new Double(69)};
			_min = Double.MIN_VALUE;
			_max = Double.MAX_VALUE;
		}
	}

	@Test
	public void testDouble() throws Exception
	{
		TestDouble test = new TestDouble();
		String json = TestUtil.getJsonString(test);
		TestUtil.printLine("json = " + json);
		TestDouble that = (TestDouble) TestUtil.readJsonObject(json);

		assertTrue(that._arrayElement.equals(-1.0));
		assertTrue(that._polyRefTarget.equals(71.0));
		assertTrue(that._polyRef.equals(71.0));
		assertTrue(that._polyNotRef.equals(71.0));
		assertTrue(that._polyRef != that._polyRefTarget);   // Primitive wrappers are treated like primitives (no ref)
		assertFalse(that._polyNotRef == that._polyRef);

		assertTrue(that._typeArray.length == 6);
		assertTrue(that._typeArray[1] instanceof Double);
		assertTrue(that._typeArray[1] instanceof Double);
		assertTrue(that._typeArray[1].equals(44.0));
		assertTrue(that._objArray.length == 6);
		assertTrue(that._objArray[1] instanceof Double);
		assertTrue(that._objArray[1].equals(69.0));
		assertTrue(that._polyRefTarget instanceof Double);
		assertTrue(that._polyNotRef instanceof Double);

		assertTrue(that._null == null);
		assertTrue(that._typeArray[3] == null);
		assertTrue(that._typeArray[4] == null);
		assertTrue(that._objArray[3] == null);
		assertTrue(that._objArray[4] == null);

		assertTrue(that._min.equals(Double.MIN_VALUE));
		assertTrue(that._max.equals(Double.MAX_VALUE));
		assertTrue(that._min == Double.MIN_VALUE);
		assertTrue(that._max == Double.MAX_VALUE);
	}

	private static class TestFloat implements Serializable
	{
		private final Float _arrayElement;
		private final Float[] _typeArray;
		private final Object[] _objArray;
		private final Object _polyRefTarget;
		private final Object _polyRef;
		private final Object _polyNotRef;
		private final Float _min;
		private final Float _max;
		private final Float _null;

		private TestFloat()
		{
			_arrayElement = new Float(-1);
			_polyRefTarget = new Float(71);
			_polyRef = _polyRefTarget;
			_polyNotRef = new Float(71);
			Float local = new Float(75);
			_null = null;
			_typeArray = new Float[] {_arrayElement, 44f, local, _null, null, new Float(44f)};
			_objArray = new Object[] {_arrayElement, 69f, local, _null, null, new Float(69f)};
			_min = Float.MIN_VALUE;
			_max = Float.MAX_VALUE;
		}
	}

	@Test
	public void testFloat() throws Exception
	{
		TestFloat test = new TestFloat();
		String json = TestUtil.getJsonString(test);
		TestUtil.printLine("json = " + json);
		TestFloat that = (TestFloat) TestUtil.readJsonObject(json);

		assertTrue(that._arrayElement.equals(-1.0f));
		assertTrue(that._polyRefTarget.equals(71.0f));
		assertTrue(that._polyRef.equals(71.0f));
		assertTrue(that._polyNotRef.equals(71.0f));
		assertTrue(that._polyRef != that._polyRefTarget);   // Primitive wrappers are treated like primitives (no ref)
		assertFalse(that._polyNotRef == that._polyRef);

		assertTrue(that._typeArray.length == 6);
		assertTrue(that._typeArray[1] instanceof Float);
		assertTrue(that._typeArray[1] instanceof Float);
		assertTrue(that._typeArray[1].equals(44.0f));
		assertTrue(that._objArray.length == 6);
		assertTrue(that._objArray[1] instanceof Float);
		assertTrue(that._objArray[1].equals(69.0f));
		assertTrue(that._polyRefTarget instanceof Float);
		assertTrue(that._polyNotRef instanceof Float);

		assertTrue(that._objArray[2].equals(that._typeArray[2]));
		assertFalse(that._typeArray[1] == that._typeArray[5]);
		assertFalse(that._objArray[1] == that._objArray[5]);
		assertFalse(that._typeArray[1] == that._objArray[1]);
		assertFalse(that._typeArray[5] == that._objArray[5]);

		assertTrue(that._null == null);
		assertTrue(that._typeArray[3] == null);
		assertTrue(that._typeArray[4] == null);
		assertTrue(that._objArray[3] == null);
		assertTrue(that._objArray[4] == null);

		assertTrue(that._min.equals(Float.MIN_VALUE));
		assertTrue(that._max.equals(Float.MAX_VALUE));
		assertTrue(that._min == Float.MIN_VALUE);
		assertTrue(that._max == Float.MAX_VALUE);
	}

	private static class TestBoolean implements Serializable
	{
		private final Boolean _arrayElement;
		private final Boolean[] _typeArray;
		private final Object[] _objArray;
		private final Object _polyRefTarget;
		private final Object _polyRef;
		private final Object _polyNotRef;
		private final Boolean _null;

		private TestBoolean()
		{
			_arrayElement = new Boolean(true);
			_polyRefTarget = new Boolean(true);
			_polyRef = _polyRefTarget;
			_polyNotRef = new Boolean(true);
			Boolean local = new Boolean(true);
			_null = null;
			_typeArray = new Boolean[] {_arrayElement, true, local, _null, null, Boolean.FALSE, new Boolean(false)};
			_objArray = new Object[] {_arrayElement, true, local, _null, null, Boolean.FALSE, new Boolean(false) };
		}
	}

	@Test
	public void testBoolean() throws Exception
	{
		TestBoolean test = new TestBoolean();
		String json = TestUtil.getJsonString(test);
		TestUtil.printLine("json = " + json);
		TestBoolean that = (TestBoolean) TestUtil.readJsonObject(json);

		assertTrue(that._arrayElement.equals(true));
		assertTrue(that._polyRefTarget.equals(true));
		assertTrue(that._polyRef.equals(true));
		assertTrue(that._polyNotRef.equals(true));
		assertTrue(that._polyRef == that._polyRefTarget);
		assertTrue(that._polyNotRef == that._polyRef); // because only Boolean.TRUE or Boolean.FALSE used

		assertTrue(that._typeArray.length == 7);
		assertTrue(that._typeArray[0] == that._arrayElement);
		assertTrue(that._typeArray[1] instanceof Boolean);
		assertTrue(that._typeArray[1] instanceof Boolean);
		assertTrue(that._typeArray[1].equals(true));
		assertTrue(that._objArray.length == 7);
		assertTrue(that._objArray[0] == that._arrayElement);
		assertTrue(that._objArray[1] instanceof Boolean);
		assertTrue(that._objArray[1].equals(true));
		assertTrue(that._polyRefTarget instanceof Boolean);
		assertTrue(that._polyNotRef instanceof Boolean);

		assertTrue(that._objArray[2] == that._typeArray[2]);
		assertTrue(that._typeArray[5] == that._objArray[5]);
		assertTrue(that._typeArray[6] == that._objArray[6]);
		assertTrue(that._typeArray[5] == that._typeArray[6]);
		assertTrue(that._objArray[5] == that._objArray[6]);

		assertTrue(that._null == null);
		assertTrue(that._typeArray[3] == null);
		assertTrue(that._typeArray[4] == null);
		assertTrue(that._objArray[3] == null);
		assertTrue(that._objArray[4] == null);
	}

	private static class TestCharacter implements Serializable
	{
		private final Character _arrayElement;
		private final Character[] _typeArray;
		private final Object[] _objArray;
		private final Object _polyRefTarget;
		private final Object _polyRef;
		private final Object _polyNotRef;
		private final Character _min;
		private final Character _max;
		private final Character _null;

		private TestCharacter()
		{
			_arrayElement = new Character((char) 1);
			_polyRefTarget = new Character((char)71);
			_polyRef = _polyRefTarget;
			_polyNotRef = new Character((char) 71);
			Character local = new Character((char)75);
			_null  = null;
			_typeArray = new Character[] {_arrayElement, 'a', local, _null, null};
			_objArray = new Object[] {_arrayElement, 'b', local, _null, null};
			_min = Character.MIN_VALUE;
			_max = Character.MAX_VALUE;
		}
	}

	@Test
	public void testCharacter() throws Exception
	{
		TestCharacter test = new TestCharacter();
		String json = TestUtil.getJsonString(test);
		TestUtil.printLine("json = " + json);
		TestCharacter that = (TestCharacter) TestUtil.readJsonObject(json);

		assertTrue(that._arrayElement.equals((char) 1));
		assertTrue(that._polyRefTarget.equals((char) 71));
		assertTrue(that._polyRef.equals((char) 71));
		assertTrue(that._polyNotRef.equals((char) 71));
		assertTrue(that._polyRef == that._polyRefTarget);
		assertTrue (that._polyNotRef == that._polyRef);    // Character cache working

		assertTrue(that._typeArray.length == 5);
		assertTrue(that._typeArray[0] == that._arrayElement);
		assertTrue(that._typeArray[1] instanceof Character);
		assertTrue(that._typeArray[1] instanceof Character);
		assertTrue(that._typeArray[1].equals('a'));
		assertTrue(that._objArray.length == 5);
		assertTrue(that._objArray[0] == that._arrayElement);
		assertTrue(that._objArray[1] instanceof Character);
		assertTrue(that._objArray[1].equals('b'));
		assertTrue(that._polyRefTarget instanceof Character);
		assertTrue(that._polyNotRef instanceof Character);

		assertTrue(that._objArray[2] == that._typeArray[2]);
		assertTrue(that._objArray[2].equals((char) 75));

		assertTrue(that._null == null);
		assertTrue(that._typeArray[3] == null);
		assertTrue(that._typeArray[4] == null);
		assertTrue(that._objArray[3] == null);
		assertTrue(that._objArray[4] == null);

		assertTrue(that._min.equals(Character.MIN_VALUE));
		assertTrue(that._max.equals(Character.MAX_VALUE));
		assertTrue(that._min == Character.MIN_VALUE);
		assertTrue(that._max == Character.MAX_VALUE);
	}

	private static class TestString implements Serializable
	{
		private static final int MAX_UTF8_CHAR = 100;
		// Foreign characters test (UTF8 multi-byte chars)
		private final String _range;
		private String _utf8HandBuilt;
		private final String[] _strArray;
		private final Object[] _objArray;
		private final Object[] _objStrArray;
		private final Object[] _cache;
		private final Object _poly;
		private final String _null;

		private TestString()
		{
			_null = null;
			StringBuffer s = new StringBuffer();
			for (int i = 0; i < MAX_UTF8_CHAR; i++)
			{
				s.append((char) i);
			}
			_range = s.toString();

			// BYZANTINE MUSICAL SYMBOL PSILI
			try
			{
				byte[] symbol = {(byte) 0xf0, (byte) 0x9d, (byte) 0x80, (byte) 0x80};
				_utf8HandBuilt = new String(symbol, "UTF-8");
			}
			catch (UnsupportedEncodingException e)
			{
				TestUtil.printLine("Get a new JVM that supports UTF-8");
			}

			_strArray = new String[] {"1st", "2nd", _null, null, new String("3rd")};
			_objArray = new Object[] {"1st", "2nd", _null, null, new String("3rd")};
			_objStrArray = new String[] {"1st", "2nd", _null, null, new String("3rd")};
			_cache = new Object[] {"true", "true", "golf", "golf"};
			_poly = "Poly";
		}
	}

	@Test
	public void testString() throws Exception
	{
		TestString test = new TestString();
		String jsonOut = TestUtil.getJsonString(test);
		TestUtil.printLine("json=" + jsonOut);
		TestString that = (TestString) TestUtil.readJsonObject(jsonOut);

		for (int i = 0; i < TestString.MAX_UTF8_CHAR; i++)
		{
			assertTrue(that._range.charAt(i) == (char) i);
		}

		// UTF-8 serialization makes it through clean.
		byte[] bytes = that._utf8HandBuilt.getBytes("UTF-8");
		assertTrue(bytes[0] == (byte) 0xf0);
		assertTrue(bytes[1] == (byte) 0x9d);
		assertTrue(bytes[2] == (byte) 0x80);
		assertTrue(bytes[3] == (byte) 0x80);

		assertTrue(that._strArray.length == 5);
		assertTrue(that._objArray.length == 5);
		assertTrue(that._objStrArray.length == 5);
		assertTrue(that._strArray[2] == null);
		assertTrue(that._objArray[2] == null);
		assertTrue(that._objStrArray[2] == null);
		assertTrue(that._strArray[3] == null);
		assertTrue(that._objArray[3] == null);
		assertTrue(that._objStrArray[3] == null);
		assertTrue("Poly".equals(that._poly));

		assertTrue(that._cache[0] == that._cache[1]);       // "true' is part of the reusable cache.
	}

	@Test
	public void testRootString() throws Exception
	{
		String s = "\"root string\"";
		Object o = JsonReader.jsonToMaps(s);
		assertTrue(o instanceof JsonObject);
		JsonObject jo = (JsonObject) o;
		assertTrue(jo.containsKey("@items"));
		Object[] items = (Object[]) jo.get("@items");
		assertTrue(items.length == 1);
		assertEquals("root string", items[0]);
		o = TestUtil.readJsonObject(s);
		assertEquals("root string", o);
	}

	@Test
	public void testObjectArrayStringReference() throws Exception
	{
		String s = "dogs";
		String json = TestUtil.getJsonString(new Object[] {s, s});
		TestUtil.printLine("json = " + json);
		Object[] o = (Object[]) TestUtil.readJsonObject(json);
		assertTrue(o.length == 2);
		assertTrue("dogs".equals(o[0]));
	}

	@Test
	public void testStringArrayStringReference() throws Exception
	{
		String s = "dogs";
		String json = TestUtil.getJsonString(new String[] {s, s});
		TestUtil.printLine("json = " + json);
		String[] o = (String[]) TestUtil.readJsonObject(json);
		assertTrue(o.length == 2);
		assertTrue("dogs".equals(o[0]));
		assertTrue(o[0] != o[1]);   // Change this to == if we decide to collapse identical String instances
	}

	static class TestClass implements Serializable
	{
		private List _classes_a;

		private final Class _booleanClass;
		private final Class _BooleanClass;
		private final Object _booleanClassO;
		private final Object _BooleanClassO;
		private final Class[] _booleanClassArray;
		private final Class[] _BooleanClassArray;
		private final Object[] _booleanClassArrayO;
		private final Object[] _BooleanClassArrayO;

		private final Class _charClass;
		private final Class _CharacterClass;
		private final Object _charClassO;
		private final Object _CharacterClassO;
		private final Class[] _charClassArray;
		private final Class[] _CharacterClassArray;
		private final Object[] _charClassArrayO;
		private final Object[] _CharacterClassArrayO;

		private TestClass()
		{
			_classes_a = new ArrayList();
			_classes_a.add(char.class);
			_booleanClass = boolean.class;
			_BooleanClass = Boolean.class;
			_booleanClassO = boolean.class;
			_BooleanClassO = Boolean.class;
			_booleanClassArray = new Class[] { boolean.class };
			_BooleanClassArray = new Class[] { Boolean.class };
			_booleanClassArrayO = new Object[] { boolean.class };
			_BooleanClassArrayO = new Object[] { Boolean.class };

			_charClass = char.class;
			_CharacterClass = Character.class;
			_charClassO = char.class;
			_CharacterClassO = Character.class;
			_charClassArray = new Class[] { char.class };
			_CharacterClassArray = new Class[] { Character.class };
			_charClassArrayO = new Object[] { char.class };
			_CharacterClassArrayO = new Object[] { Character.class };
		}
	}

	@Test
	public void testClassAtRoot() throws Exception
	{
		Class c = Double.class;
		String json = TestUtil.getJsonString(c);
		TestUtil.printLine("json=" + json);
		Class r = (Class) TestUtil.readJsonObject(json);
		assertTrue(c.getName().equals(r.getName()));
	}

	@Test
	public void testClass() throws Exception
	{
		TestClass test = new TestClass();
		String json = TestUtil.getJsonString(test);
		TestUtil.printLine("json = " + json);
		TestClass that = (TestClass) TestUtil.readJsonObject(json);

		assertTrue(that._classes_a.get(0) == char.class);

		assertTrue(boolean.class == that._booleanClass);
		assertTrue(Boolean.class == that._BooleanClass);
		assertTrue(boolean.class == that._booleanClassO);
		assertTrue(Boolean.class == that._BooleanClassO);
		assertTrue(boolean.class == that._booleanClassArray[0]);
		assertTrue(Boolean.class == that._BooleanClassArray[0]);
		assertTrue(boolean.class == that._booleanClassArrayO[0]);
		assertTrue(Boolean.class == that._BooleanClassArrayO[0]);

		assertTrue(char.class == that._charClass);
		assertTrue(Character.class == that._CharacterClass);
		assertTrue(char.class == that._charClassO);
		assertTrue(Character.class == that._CharacterClassO);
		assertTrue(char.class == that._charClassArray[0]);
		assertTrue(Character.class == that._CharacterClassArray[0]);
		assertTrue(char.class == that._charClassArrayO[0]);
		assertTrue(Character.class == that._CharacterClassArrayO[0]);
	}

	static class TestSet implements Serializable
	{
		private Set _hashSet;
		private Set _treeSet;

		private void init()
		{
			_hashSet = new HashSet();
			_hashSet.add("alpha");
			_hashSet.add("bravo");
			_hashSet.add("charlie");
			_hashSet.add("delta");
			_hashSet.add("echo");
			_hashSet.add("foxtrot");
			_hashSet.add("golf");
			_hashSet.add("hotel");
			_hashSet.add("indigo");
			_hashSet.add("juliet");
			_hashSet.add("kilo");
			_hashSet.add("lima");
			_hashSet.add("mike");
			_hashSet.add("november");
			_hashSet.add("oscar");
			_hashSet.add("papa");
			_hashSet.add("quebec");
			_hashSet.add("romeo");
			_hashSet.add("sierra");
			_hashSet.add("tango");
			_hashSet.add("uniform");
			_hashSet.add("victor");
			_hashSet.add("whiskey");
			_hashSet.add("xray");
			_hashSet.add("yankee");
			_hashSet.add("zulu");

			_treeSet = new TreeSet();
			_treeSet.addAll(_hashSet);
		}

		private TestSet()
		{
		}
	}

	@Test
	public void testSet() throws Exception
	{
		TestSet set = new TestSet();
		set.init();
		String json = TestUtil.getJsonString(set);

		TestSet testSet = (TestSet) TestUtil.readJsonObject(json);
		TestUtil.printLine("json = " + json);

		assertTrue(testSet._treeSet.size() == 26);
		assertTrue(testSet._hashSet.size() == 26);
		assertTrue(testSet._treeSet.containsAll(testSet._hashSet));
		assertTrue(testSet._hashSet.containsAll(testSet._treeSet));
	}

	@Test
	public void testMap2() throws Exception
	{
		TestObject a = new TestObject("A");
		TestObject b = new TestObject("B");
		TestObject c = new TestObject("C");
		a._other = b;
		b._other = c;
		c._other = a;

		Map map = new HashMap();
		map.put(a, b);
		String json = TestUtil.getJsonString(map);
		TestUtil.printLine("json = " + json);
		map = (Map) TestUtil.readJsonObject(json);
		assertTrue(map != null);
		assertTrue(map.size() == 1);
		TestObject bb = (TestObject) map.get(new TestObject("A"));
		assertTrue(bb._other.equals(new TestObject("C")));
		TestObject aa = (TestObject) map.keySet().toArray()[0];
		assertTrue(aa._other == bb);
	}

	@Test
	public void testMap3() throws Exception
	{
		Map map = new HashMap();
		map.put("a", "b");
		String json = TestUtil.getJsonString(map);
		TestUtil.printLine("json = " + json);
		map = (Map) TestUtil.readJsonObject(json);
		assertTrue(map != null);
		assertTrue(map.size() == 1);
	}

	@Test
	public void testCustom() throws Exception
	{

	}

	static public class A
	{
		public String a;

		class B
		{

			public String b;

			public B()
			{
				// No args constructor for B
			}
		}
	}

	@Test
	public void testInner() throws Exception
	{
		A a = new A();
		a.a = "aaa";

		String json = TestUtil.getJsonString(a);
		TestUtil.printLine("json = " + json);
		A o1 = (A) TestUtil.readJsonObject(json);
		assertTrue(o1.a.equals("aaa"));

		TestJsonReaderWriter.A.B b = a.new B();
		b.b = "bbb";
		json = TestUtil.getJsonString(b);
		TestUtil.printLine("json = " + json);
		TestJsonReaderWriter.A.B o2 = (TestJsonReaderWriter.A.B) TestUtil.readJsonObject(json);
		assertTrue(o2.b.equals("bbb"));
	}

	@Test
	public void testForwardRefs() throws Exception
	{
		TestObject one = new TestObject("One");
		TestObject two = new TestObject("Two");
		one._other = two;
		two._other = one;
		String pkg = TestObject.class.getName();
		String fwdRef = "[[{\"@id\":2,\"@type\":\"" + pkg + "\",\"_name\":\"Two\",\"_other\":{\"@ref\":1}}],[{\n" + "\"@id\":1,\"@type\":\"" + pkg + "\",\"_name\":\"One\",\"_other\":{\"@ref\":2}}]]";
		Object[] foo = (Object[]) TestUtil.readJsonObject(fwdRef);
		Object[] first = (Object[]) foo[0];
		Object[] second = (Object[]) foo[1];
		assertTrue(one.equals(second[0]));
		assertTrue(two.equals(first[0]));

		String json = "[{\"@ref\":2},{\"@id\":2,\"@type\":\"int\",\"value\":5}]";
		Object[] ints = (Object[]) TestUtil.readJsonObject(json);
		assertTrue((Integer)ints[0] == 5);
		assertTrue((Integer) ints[1] == 5);

		json ="{\"@type\":\"java.util.ArrayList\",\"@items\":[{\"@ref\":2},{\"@id\":2,\"@type\":\"int\",\"value\":5}]}";
		List list = (List) JsonReader.jsonToJava(json);
		assertTrue((Integer)list.get(0) == 5);
		assertTrue((Integer)list.get(1) == 5);

		json = "{\"@type\":\"java.util.TreeSet\",\"@items\":[{\"@type\":\"int\",\"value\":9},{\"@ref\":16},{\"@type\":\"int\",\"value\":4},{\"@id\":16,\"@type\":\"int\",\"value\":5}]}";
		Set set = (Set) TestUtil.readJsonObject(json);
		assertTrue(set.size() == 3);
		Iterator i = set.iterator();
		assertTrue((Integer)i.next() == 4);
		assertTrue((Integer)i.next() == 5);
		assertTrue((Integer)i.next() == 9);

		json = "{\"@type\":\"java.util.HashMap\",\"@keys\":[1,2,3,4],\n" + "\"@items\":[{\"@type\":\"int\",\"value\":9},{\"@ref\":16},{\"@type\":\"int\",\"value\":4},{\"@id\":16,\"@type\":\"int\",\"value\":5}]}";
		Map map = (Map) TestUtil.readJsonObject(json);
		assertTrue(map.size() == 4);
		assertTrue((Integer)map.get(1L) == 9);
		assertTrue((Integer)map.get(2L) == 5);
		assertTrue((Integer)map.get(3L) == 4);
		assertTrue((Integer)map.get(4L) == 5);
	}

	@Test
	public void testNull() throws Exception
	{
		String json = JsonWriter.objectToJson(null);
		TestUtil.printLine("json=" + json);
		assertTrue("null".equals(json));
	}

	/**
	 * Although a String cannot really be a root in JSON,
	 * json-io returns the JSON utf8 string.  This test
	 * exists to catch if this decision ever changes.
	 * Currently, Google's Gson does the same thing.
	 */
	@Test
	public void testStringRoot() throws Exception
	{
		Gson gson = new Gson();
		String g = gson.toJson("root should not be a string");
		String j = JsonWriter.objectToJson("root should not be a string");
		assertEquals(g, j);
	}

	@Test
	public void testReferencedEmptyArray() throws Exception
	{
		String[] array = new String[]{};
		Object[] refArray = new Object[]{array};
		String json = JsonWriter.objectToJson(refArray);
		TestUtil.printLine("json=" + json);
		Object[] oa = (Object[]) JsonReader.jsonToJava(json);
		assertTrue(oa[0].getClass().equals(String[].class));
		assertTrue(((String[])oa[0]).length == 0);
	}

	@Test
	public void testBadJson() throws Exception
	{
		Object o = null;

		try
		{
			o = TestUtil.readJsonObject("[\"bad JSON input\"");
			fail("should not make it here");
		}
		catch(Exception e)
		{
			assertTrue(e.getMessage().contains("inside"));
		}
		assertTrue(o == null);
	}

	@Test
	public void testParseMissingQuote() throws Exception
	{
		try
		{
			String json = "{\n" +
					"  \"array\": [\n" +
					"    1,\n" +
					"    2,\n" +
					"    3\n" +
					"  ],\n" +
					"  \"boolean\": true,\n" +
					"  \"null\": null,\n" +
					"  \"number\": 123,\n" +
					"  \"object\": {\n" +
					"    \"a\": \"b\",\n" +
					"    \"c\": \"d\",\n" +
					"    \"e\": \"f\"\n" +
					"  },\n" +
					"  \"string: \"Hello World\"\n" +
					"}";
			JsonReader.jsonToJava(json);
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("Expected ':' between string field and value"));
		}
	}
	@Test
	public void testParseInvalid1stChar() throws IOException
	{
		try
		{
			String json = "\n" +
					"  \"array\": [\n" +
					"    1,\n" +
					"    2,\n" +
					"    3\n" +
					"  ],\n" +
					"  \"boolean\": true,\n" +
					"  \"null\": null,\n" +
					"  \"number\": 123,\n" +
					"  \"object\": {\n" +
					"    \"a\": \"b\",\n" +
					"    \"c\": \"d\",\n" +
					"    \"e\": \"f\"\n" +
					"  },\n" +
					"  \"string:\" \"Hello World\"\n" +
					"}";
			JsonReader.jsonToJava(json);
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("nknown JSON value type"));
		}
	}

	@Test
	public void testParseMissingLastBrace() throws IOException
	{
		try
		{
			String json = "{\n" +
                    "  \"array\": [\n" +
                    "    1,\n" +
                    "    2,\n" +
                    "    3\n" +
                    "  ],\n" +
                    "  \"boolean\": true,\n" +
                    "  \"null\": null,\n" +
                    "  \"number\": 123,\n" +
                    "  \"object\": {\n" +
                    "    \"a\": \"b\",\n" +
                    "    \"c\": \"d\",\n" +
                    "    \"e\": \"f\"\n" +
                    "  },\n" +
                    "  \"string\": \"Hello World\"\n" +
                    "";
			JsonReader.jsonToJava(json);
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("EOF reached before closing '}'"));
		}
	}

	@Test
	public void testParseMissingLastBracket() throws IOException
	{
		String json = "[true, \"bunch of ints\", 1,2, 3 , 4, 5 , 6,7,8,9,10]";
		JsonReader.jsonToJava(json);

		try
		{
			json = "[true, \"bunch of ints\", 1,2, 3 , 4, 5 , 6,7,8,9,10";
			JsonReader.jsonToJava(json);
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("xpected ',' or ']' inside array"));
		}
	}

	@Test
	public void testParseBadValueInArray() throws IOException
	{
		String json;

		try
		{
			json = "[true, \"bunch of ints\", 1,2, 3 , 4, 5 , 6,7,8,9,'a']";
			JsonReader.jsonToJava(json);
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("nknown JSON value type"));
		}
	}

	@Test
	public void testParseObjectWithoutClosingBrace() throws IOException
	{
		try
		{
			String json = "{\n" +
					"  \"key\": true\n" +
					"{";
			JsonReader.jsonToJava(json);
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("Object not ended with '}'"));
		}
	}

	@Test
	public void testParseBadHex()
	{
		try
		{
			String json = "\"\\u5h1t\"";
			JsonReader.jsonToJava(json);
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("Expected hexadecimal digits"));
		}
	}

	@Test
	public void testParseBadEscapeChar()
	{
		try
		{
			String json = "\"What if I try to escape incorrectly \\L1CK\"";
			JsonReader.jsonToJava(json);
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("nvalid character escape sequence specified"));
		}
	}

	@Test
	public void testParseUnfinishedString()
	{
		try
		{
			String json = "\"This is an unfinished string...";
			JsonReader.jsonToJava(json);
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("EOF reached while reading JSON string"));
		}
	}

	@Test
	public void testParseEOFInToken()
	{
		try
		{
			String json = "falsz";
			JsonReader.jsonToJava(json);
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("xpected token: false"));
		}
	}

	@Test
	public void testParseEOFReadingToken()
	{
		try
		{
			String json = "tru";
			JsonReader.jsonToJava(json);
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("EOF reached while reading token"));
		}
	}

	@Test
	public void testParseEOFinArray()
	{
		try
		{
			String json = "[true, false,";
			JsonReader.jsonToJava(json);
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("EOF reached prematurely"));
		}
	}

	@Test
	public void testToMaps() throws Exception
	{
		JsonObject map = (JsonObject) JsonReader.jsonToMaps("{\"num\":0,\"nullValue\":null,\"string\":\"yo\"}");
		assertTrue(map != null);
		assertTrue(map.size() == 3);
		assertTrue(map.get("num").equals(0L));
		assertTrue(map.get("nullValue") == null);
		assertTrue(map.get("string").equals("yo"));

		assertTrue(map.getCol() > 0);
		assertTrue(map.getLine() > 0);
	}

	@Test
	public void testEmptyArray() throws Exception
	{
		String json = "{\"@type\":\"[Ljava.lang.String;\"}";
		String[] s = (String[])JsonReader.jsonToJava(json);
		assertTrue(s != null);
		assertTrue(s.length == 0);
	}

	@Test
	public void testLocale() throws Exception
	{
		Locale locale = new Locale(Locale.ENGLISH.getLanguage(), Locale.US.getCountry());
		String json = TestUtil.getJsonString(locale);
		TestUtil.printLine("json=" + json);
		Locale us = (Locale) TestUtil.readJsonObject(json);
		assertTrue(locale.equals(us));

		locale = new Locale(Locale.ENGLISH.getLanguage(), Locale.US.getCountry(), "johnson");
		json = TestUtil.getJsonString(locale);
		TestUtil.printLine("json=" + json);
		us = (Locale) TestUtil.readJsonObject(json);
		assertTrue(locale.equals(us));

		try
		{
			String noProps = "{\"@type\":\"java.util.Locale\"}";
			TestUtil.readJsonObject(noProps);
			assertTrue("Should never get here.", false);
		}
		catch(Exception e) {}

		json = "{\"@type\":\"java.util.Locale\",\"language\":\"en\"}";
		locale = (Locale) TestUtil.readJsonObject(json);
		assertTrue("en".equals(locale.getLanguage()));
		assertTrue("".equals(locale.getCountry()));
		assertTrue("".equals(locale.getVariant()));

		json = "{\"@type\":\"java.util.Locale\",\"language\":\"en\",\"country\":\"US\"}";
		locale = (Locale) TestUtil.readJsonObject(json);
		assertTrue("en".equals(locale.getLanguage()));
		assertTrue("US".equals(locale.getCountry()));
		assertTrue("".equals(locale.getVariant()));
	}

	@Test
	public void testLocaleArray() throws Exception
	{
		Locale locale = new Locale(Locale.ENGLISH.getLanguage(), Locale.US.getCountry());
		String json = TestUtil.getJsonString(new Object[] {locale});
		TestUtil.printLine("json=" + json);
		Object[] oArray = (Object[]) TestUtil.readJsonObject(json);
		assertTrue(oArray.length == 1);
		Locale us = (Locale) oArray[0];
		assertTrue(locale.equals(us));

		json = TestUtil.getJsonString(new Locale[] {locale});
		TestUtil.printLine("json=" + json);
		Locale[] lArray = (Locale[]) TestUtil.readJsonObject(json);
		assertTrue(lArray.length == 1);
		us = lArray[0];
		assertTrue(locale.equals(us));
	}

	@Test
	public void testLocaleInMapValue() throws Exception
	{
		Locale locale = new Locale(Locale.ENGLISH.getLanguage(), Locale.US.getCountry());
		Map map = new HashMap();
		map.put("us", locale);
		String json = TestUtil.getJsonString(map);
		TestUtil.printLine("json=" + json);
		map = (Map) TestUtil.readJsonObject(json);
		assertTrue(map.size() == 1);
		assertTrue(map.get("us").equals(locale));
	}

	@Test
	public void testLocaleInMapKey() throws Exception
	{
		Locale locale = new Locale(Locale.ENGLISH.getLanguage(), Locale.US.getCountry());
		Map map = new HashMap();
		map.put(locale, "us");
		String json = TestUtil.getJsonString(map);
		TestUtil.printLine("json=" + json);
		map = (Map) TestUtil.readJsonObject(json);
		assertTrue(map.size() == 1);
		Iterator i = map.keySet().iterator();
		assertTrue(i.next().equals(locale));
	}

	@Test
	public void testLocaleInMapOfMaps() throws Exception
	{
		Locale locale = new Locale(Locale.ENGLISH.getLanguage(), Locale.US.getCountry());
		String json = TestUtil.getJsonString(locale);
		TestUtil.printLine("json=" + json);
		Map map = JsonReader.jsonToMaps(json);
		assertTrue("en".equals(map.get("language")));
		assertTrue("US".equals(map.get("country")));
	}

	@Test
	public void testLocaleRef() throws Exception
	{
		Locale locale = new Locale(Locale.ENGLISH.getLanguage(), Locale.US.getCountry());
		String json = TestUtil.getJsonString(new Object[] {locale, locale});
		TestUtil.printLine("json=" + json);
		Object[] oArray = (Object[]) TestUtil.readJsonObject(json);
		assertTrue(oArray.length == 2);
		Locale us = (Locale) oArray[0];
		assertTrue(locale.equals(us));
		assertTrue(oArray[0] == oArray[1]);
	}

	@Test
	public void testEmptyPrimitives() throws Exception
	{
		String json = "{\"@type\":\"byte\"}";
		Byte b = (Byte) JsonReader.jsonToJava(json);
		assertTrue(b.getClass().equals(Byte.class));
		assertTrue(b == 0);

		json = "{\"@type\":\"short\"}";
		Short s = (Short) JsonReader.jsonToJava(json);
		assertTrue(s.getClass().equals(Short.class));
		assertTrue(s == 0);

		json = "{\"@type\":\"int\"}";
		Integer i = (Integer) JsonReader.jsonToJava(json);
		assertTrue(i.getClass().equals(Integer.class));
		assertTrue(i == 0);

		json = "{\"@type\":\"long\"}";
		Long l = (Long) JsonReader.jsonToJava(json);
		assertTrue(l.getClass().equals(Long.class));
		assertTrue(l == 0);

		json = "{\"@type\":\"float\"}";
		Float f = (Float) JsonReader.jsonToJava(json);
		assertTrue(f.getClass().equals(Float.class));
		assertTrue(f == 0.0f);

		json = "{\"@type\":\"double\"}";
		Double d = (Double) JsonReader.jsonToJava(json);
		assertTrue(d.getClass().equals(Double.class));
		assertTrue(d == 0.0d);

		json = "{\"@type\":\"char\"}";
		Character c = (Character) JsonReader.jsonToJava(json);
		assertTrue(c.getClass().equals(Character.class));
		assertTrue(c == '\u0000');

		json = "{\"@type\":\"boolean\"}";
		Boolean bool = (Boolean) JsonReader.jsonToJava(json);
		assertTrue(bool == Boolean.FALSE);

		json = "{\"@type\":\"string\"}";
		String str = null;
		try
		{
			str = (String) JsonReader.jsonToJava(json);
			fail("should not make it here");
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("'value'"));
		}
		assertTrue(str == null);
	}

	@Test
	public void testMalformedJson() throws Exception
	{
		String json;

		try
		{
			json = "{\"field\";0}";  // colon expected between fields
			JsonReader.jsonToMaps(json);
			assertTrue("malformed JSON should not parse 2", false);
		}
		catch (IOException e) { }

		try
		{
			json = "{field:0}";  // not quoted field name
			JsonReader.jsonToMaps(json);
			assertTrue("malformed JSON should not parse 3", false);
		}
		catch (IOException e) { }

		try
		{
			json = "{\"field\":0";  // object not terminated correctly (ending in number)
			JsonReader.jsonToMaps(json);
			assertTrue("malformed JSON should not parse 4", false);
		}
		catch (IOException e) { }

		try
		{
			json = "{\"field\":true";  // object not terminated correctly (ending in token)
			JsonReader.jsonToMaps(json);
			assertTrue("malformed JSON should not parse 5", false);
		}
		catch (IOException e) { }

		try
		{
			json = "{\"field\":\"test\"";  // object not terminated correctly (ending in string)
			JsonReader.jsonToMaps(json);
			assertTrue("malformed JSON should not parse 6", false);
		}
		catch (IOException e) { }

		try
		{
			json = "{\"field\":{}";  // object not terminated correctly (ending in another object)
			JsonReader.jsonToMaps(json);
			assertTrue("malformed JSON should not parse 7", false);
		}
		catch (IOException e) { }

		try
		{
			json = "{\"field\":[]";  // object not terminated correctly (ending in an array)
			JsonReader.jsonToMaps(json);
			assertTrue("malformed JSON should not parse 8", false);
		}
		catch (IOException e) { }

		try
		{
			json = "{\"field\":3.14";  // object not terminated correctly (ending in double precision number)
			JsonReader.jsonToMaps(json);
			assertTrue("malformed JSON should not parse 9", false);
		}
		catch (IOException e) { }

		try
		{
			json = "[1,2,3";
			JsonReader.jsonToMaps(json);
			assertTrue("malformed JSON should not parse 10", false);
		}
		catch (IOException e)  { }

		try
		{
			json = "[false,true,false";
			JsonReader.jsonToMaps(json);
			assertTrue("malformed JSON should not parse 11", false);
		}
		catch (IOException e) { }

		try
		{
			json = "[\"unclosed string]";
			JsonReader.jsonToMaps(json);
			assertTrue("malformed JSON should not parse 12", false);
		}
		catch (IOException e) { }
	}

	@Test
	public void testBadType() throws Exception
	{
		try
		{
			String json = "{\"@type\":\"non.existent.class.Non\"}";
			TestUtil.readJsonObject(json);
			assertTrue("should not parse", false);
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("class"));
			assertTrue(e.getMessage().contains("not"));
			assertTrue(e.getMessage().contains("created"));
		}

		// Bad class inside a Collection
		try
		{
			String json = "{\"@type\":\"java.util.ArrayList\",\"@items\":[null, true, {\"@type\":\"bogus.class.Name\"}]}";
			TestUtil.readJsonObject(json);
			assertTrue("should not parse", false);
		}
		catch (IOException e)
		{
			assertTrue(e instanceof IOException);
		}
	}

	@Test
	public void testOddMaps() throws Exception
	{
		String json = "{\"@type\":\"java.util.HashMap\",\"@keys\":null,\"@items\":null}";
		Map map = (Map)TestUtil.readJsonObject(json);
		assertTrue(map instanceof HashMap);
		assertTrue(map.isEmpty());

		json = "{\"@type\":\"java.util.HashMap\"}";
		map = (Map)TestUtil.readJsonObject(json);
		assertTrue(map instanceof HashMap);
		assertTrue(map.isEmpty());

		try
		{
			json = "{\"@type\":\"java.util.HashMap\",\"@keys\":null,\"@items\":[]}";
			map = (Map)TestUtil.readJsonObject(json);
			assertTrue("Should not parse into Java", false);
		}
		catch (IOException e) { }

		try
		{
			json = "{\"@type\":\"java.util.HashMap\",\"@keys\":[1,2],\"@items\":[true]}";
			map = (Map)TestUtil.readJsonObject(json);
			assertTrue("Should not parse into Java", false);
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("different size"));
		}
	}

	@Test
	public void testBadHexNumber() throws Exception
	{
		StringBuilder str = new StringBuilder();
		str.append("[\"\\");
		str.append("u000r\"]");
		try
		{
			TestUtil.readJsonObject(str.toString());
			assertTrue("Should not make it here", false);
		}
		catch (IOException e) { }
	}

	@Test
	public void testBadValue() throws Exception
	{
		try
		{
			String json = "{\"field\":19;}";
			TestUtil.readJsonObject(json);
			assertTrue("Should not make it here", false);
		}
		catch (IOException e) { }

		try
		{
			String json = "{\"field\":";
			TestUtil.readJsonObject(json);
			assertTrue("Should not make it here", false);
		}
		catch (IOException e) { }

		try
		{
			String json = "{\"field\":joe";
			TestUtil.readJsonObject(json);
			assertTrue("Should not make it here", false);
		}
		catch (IOException e) { }

		try
		{
			String json = "{\"field\":trux}";
			TestUtil.readJsonObject(json);
			assertTrue("Should not make it here", false);
		}
		catch (IOException e) { }

		try
		{
			String json = "{\"field\":tru";
			TestUtil.readJsonObject(json);
			assertTrue("Should not make it here", false);
		}
		catch (IOException e) { }
	}

	@Test
	public void testStringEscape() throws Exception
	{
		String json = "[\"escaped slash \\' should result in a single /\"]";
		TestUtil.readJsonObject(json);

		json = "[\"escaped slash \\/ should result in a single /\"]";
		TestUtil.readJsonObject(json);

		try
		{
			json = "[\"escaped slash \\x should result in a single /\"]";
			TestUtil.readJsonObject(json);
			assertTrue("Should not reach this point", false);
		}
		catch (IOException e) { }
	}

	@Test
	public void testClassMissingValue() throws Exception
	{
		try
		{
			TestUtil.readJsonObject("[{\"@type\":\"class\"}]");
			assertTrue("Should not reach this line", false);
		}
		catch (IOException e) { }
	}

	@Test
	public void testCalendarMissingValue() throws Exception
	{
		try
		{
			TestUtil.readJsonObject("[{\"@type\":\"java.util.Calendar\"}]");
			assertTrue("Should not reach this line", false);
		}
		catch (IOException e) { }
	}

	@Test
	public void testBadFormattedCalendar() throws Exception
	{
		try
		{
			TestUtil.readJsonObject("[{\"@type\":\"java.util.GregorianCalendar\",\"value\":\"2012-05-03T12:39:45.1X5-0400\"}]");
			assertTrue("Should not reach this line", false);
		}
		catch (IOException e) { }
	}

	@Test
	public void testEmptyClassName() throws Exception
	{
		try
		{
			TestUtil.readJsonObject("{\"@type\":\"\"}");
		}
		catch(Exception e) { }
	}

	@Test
	public void testBadBackRef() throws Exception
	{
		try
		{
			TestUtil.readJsonObject("{\"@type\":\"java.util.ArrayList\",\"@items\":[{\"@ref\":1}]}");
			assertTrue("Should not reach this point", false);
		}
		catch(Exception e) { }
	}

	@Test
	public void testBadInputForMapAPI() throws Exception
	{
		Object o = null;
		try
		{
			o = JsonReader.jsonToMaps("[This is not quoted]");
			fail("should not make it here");
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage().contains("token"));
		}
		assertTrue(o == null);
	}

	@Test
	public void testWriterObjectAPI() throws Exception
	{
		String json = "[1,true,null,3.14,[]]";
		Object o = JsonReader.jsonToJava(json);
		assertTrue(TestUtil.getJsonString(o).equals(json));

		ByteArrayOutputStream ba = new ByteArrayOutputStream();
		JsonWriter writer = new JsonWriter(ba);
		writer.write(o);
		writer.close();
		String s = new String(ba.toByteArray(), "UTF-8");
		assertTrue(json.equals(s));
	}

	@Test
	public void writeJsonObjectMapWithStringKeys() throws Exception
	{
		String json = "{\n  \"@type\":\"java.util.HashMap\",\n  \"age\":\"36\",\n  \"name\":\"chris\"\n}";
		Map map = new HashMap();
		map.put("name", "chris");
		map.put("age", "36");

		//in formatJson, the json will be parsed into a map, which checks both jsonReader and writeJsonObjectMap
		String jsonGenerated = JsonWriter.formatJson(JsonWriter.objectToJson(map));
		assertTrue(json.equals(jsonGenerated));

		Map clone = (Map) JsonReader.jsonToJava(jsonGenerated);
		assertTrue(map.equals(clone));
	}

	@Test
	public void writeMapWithStringKeys() throws Exception
	{
		String json = "{\"@type\":\"java.util.HashMap\",\"age\":\"36\",\"name\":\"chris\"}";
		Map map = new HashMap();
		map.put("name", "chris");
		map.put("age", "36");

		String jsonGenerated = JsonWriter.objectToJson(map);
		assertTrue(json.equals(jsonGenerated));

		Map clone = (Map) JsonReader.jsonToJava(jsonGenerated);
		assertTrue(map.equals(clone));
	}

	@Test
	public void testUntyped() throws Exception
	{
		String json ="{\"age\":46,\"name\":\"jack\",\"married\":false,\"salary\":125000.07,\"notes\":null,\"address1\":{\"@ref\":77},\"address2\":{\"@id\":77,\"street\":\"1212 Pennsylvania ave\",\"city\":\"Washington\"}}";
		Map map = JsonReader.jsonToMaps(json);
		TestUtil.printLine("map=" + map);
		assertTrue(map.get("age").equals(46L));
		assertTrue(map.get("name").equals("jack"));
		assertTrue(map.get("married") == Boolean.FALSE);
		assertTrue(map.get("salary").equals(125000.07d));
		assertTrue(map.get("notes") == null);
		Map address1 = (Map) map.get("address1");
		Map address2 = (Map) map.get("address2");
		assertTrue(address1 == address2);
		assertTrue(address2.get("street").equals("1212 Pennsylvania ave"));
		assertTrue(address2.get("city").equals("Washington"));
	}

	@Test
	public void testUntypedArray() throws Exception
	{
		Object[] args = (Object[]) TestUtil.readJsonObject("[\"string\",17, null, true, false, [], -1273123,32131, 1e6, 3.14159, -9223372036854775808, 9223372036854775807]");

		for (int i=0; i < args.length; i++)
		{
			TestUtil.printLine("args[" + i + "]=" + args[i]);
			if (args[i] != null)
			{
				TestUtil.printLine("args[" + i + "]=" + args[i].getClass().getName());
			}
		}

		assertTrue(args[0].equals("string"));
		assertTrue(args[1].equals(17L));
		assertTrue(args[2] == null);
		assertTrue(args[3].equals(Boolean.TRUE));
		assertTrue(args[4].equals(Boolean.FALSE));
		assertTrue(args[5].getClass().isArray());
		assertTrue(args[6].equals(-1273123L));
		assertTrue(args[7].equals(32131L));
		assertTrue(args[8].equals(new Double(1000000)));
		assertTrue(args[9].equals(new Double(3.14159)));
		assertTrue(args[10].equals(Long.MIN_VALUE));
		assertTrue(args[11].equals(Long.MAX_VALUE));
	}

	private enum TestEnum1 { A, B, C }

	private enum TestEnum2
	{
		A() {},
		B() {},
		C() {}
	}

	private enum TestEnum3
	{
		A("Foo")
				{
					@Override
					void doXX() {}
				},
		B("Bar")
				{
					@Override
					void doXX() {}
				},
		C(null)
				{
					@Override
					void doXX() {}
				};

		private final String val;

		private TestEnum3(String val)
		{
			this.val = val;
		}

		abstract void doXX();
	}

	@Test
	public void testEnums() throws Exception
	{

		Collection<Object> collection = new LinkedList<Object>();
		collection.addAll(Arrays.asList(TestEnum1.values()));
		collection.addAll(Arrays.asList(TestEnum2.values()));
		collection.addAll(Arrays.asList(TestEnum3.values()));

		for (Object o : collection)
		{
			String json = TestUtil.getJsonString(o);
			TestUtil.printLine("json=" + json);
			Object read = TestUtil.readJsonObject(json);
			assertTrue(o == read);
		}

		String json = TestUtil.getJsonString(collection);
		Collection<Object> collection2 = (Collection<Object>) TestUtil.readJsonObject(json);
		assertTrue(collection.equals(collection2));

		TestEnum1[] array11 = TestEnum1.values();
		json = TestUtil.getJsonString(array11);
		TestUtil.printLine("json=" + json);
		TestEnum1[] array12 = (TestEnum1[]) TestUtil.readJsonObject(json);
		assertTrue(Arrays.equals(array11, array12));

		TestEnum2[] array21 = TestEnum2.values();
		json = TestUtil.getJsonString(array21);
		TestUtil.printLine("json=" + json);
		TestEnum2[] array22 = (TestEnum2[]) TestUtil.readJsonObject(json);
		assertTrue(Arrays.equals(array21, array22));

		TestEnum3[] array31 = TestEnum3.values();
		json = TestUtil.getJsonString(array31);
		TestUtil.printLine("json=" + json);
		TestEnum3[] array32 = (TestEnum3[]) TestUtil.readJsonObject(json);
		assertTrue(Arrays.equals(array31, array32));
	}

	private static enum TestEnum4
	{
		A, B, C;

		private int internal = 6;
		protected long age = 21;
		String foo = "bar";
	}

	@Test
	public void testEnumWithPrivateMembersAsField() throws Exception
	{
		TestEnum4 x = TestEnum4.B;
		String json = TestUtil.getJsonString(x);
		TestUtil.printLine(json);
		assertEquals("{\"@type\":\"com.cedarsoftware.util.io.TestJsonReaderWriter$TestEnum4\",\"internal\":6,\"age\":21,\"foo\":\"bar\",\"name\":\"B\",\"ordinal\":1}", json);

		ByteArrayOutputStream ba = new ByteArrayOutputStream();
		JsonWriter writer = new JsonWriter(ba);
		writer.getArgs().put(JsonWriter.ENUM_PUBLIC_ONLY, true);
		writer.write(x);
		json = new String(ba.toByteArray());
		TestUtil.printLine(json);
		assertEquals("{\"@type\":\"com.cedarsoftware.util.io.TestJsonReaderWriter$TestEnum4\",\"name\":\"B\",\"ordinal\":1}", json);
	}

	@Test
	public void testEmptyObject() throws Exception
	{

		Object o = TestUtil.readJsonObject("{}");
		assertTrue(JsonObject.class.equals(o.getClass()));

		Object[] oa = (Object[]) TestUtil.readJsonObject("[{},{}]");
		assertTrue(oa.length == 2);
		assertTrue(Object.class.equals(oa[0].getClass()));
		assertTrue(Object.class.equals(oa[1].getClass()));
	}

	@Test
	public void testJsonReaderConstructor() throws Exception
	{
		String json = "{\"@type\":\"sun.util.calendar.ZoneInfo\",\"zone\":\"EST\"}";
		JsonReader jr = new JsonReader(new ByteArrayInputStream(json.getBytes()));
		TimeZone tz = (TimeZone) jr.readObject();
		assertTrue(tz != null);
		assertTrue("EST".equals(tz.getID()));
	}

	public static class WeirdDate extends Date
	{
		public WeirdDate(Date date) { super(date.getTime()); }
		public WeirdDate(long millis) { super(millis); }
	}

	public class WeirdDateWriter implements JsonWriter.JsonClassWriter
	{
		@Override
		public void write(Object o, boolean showType, Writer out) throws IOException
		{
			String value = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format((Date) o);
			out.write("\"value\":\"");
			out.write(value);
			out.write('"');
		}

		@Override
		public boolean hasPrimitiveForm()
		{
			return true;
		}

		@Override
		public void writePrimitiveForm(Object o, Writer out) throws IOException
		{
			String value = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format((Date)o);
			out.write('"');
			out.write(value);
			out.write('"');
		}
	}

	public class WeirdDateReader implements JsonReader.JsonClassReader
	{
		public Object read(Object o, Deque<JsonObject<String, Object>> stack) throws IOException
		{
			if (o instanceof String)
			{
				try
				{
					return new WeirdDate(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse((String) o));
				}
				catch (ParseException e)
				{
					throw new IOException("Date format incorrect");
				}
			}

			JsonObject jObj = (JsonObject) o;
			if (jObj.containsKey("value"))
			{
				try
				{
					return jObj.target = new WeirdDate(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse((String) jObj.get("value")));
				}
				catch (ParseException e)
				{
					throw new IOException("Date format incorrect");
				}
			}
			throw new IOException("Date missing 'value' field");

		}
	}

	@Test
	public void testCustomClassReaderWriter() throws Exception
	{
		JsonWriter.addWriter(WeirdDate.class, new WeirdDateWriter());
		JsonReader.addReader(WeirdDate.class, new WeirdDateReader());

		WeirdDate now = new WeirdDate(System.currentTimeMillis());
		String json = TestUtil.getJsonString(now);
		TestUtil.printLine("json=" + json);
		WeirdDate date = (WeirdDate)TestUtil.readJsonObject(json);
		assertTrue(now.equals(date));

		JsonWriter.addNotCustomWriter(WeirdDate.class);
		JsonReader.addNotCustomReader(WeirdDate.class);
		json = TestUtil.getJsonString(now);
		TestUtil.printLine("json=" + json);
		assertTrue(now.equals(date));
	}

	@Test
	public void testReconstituteObjectArray() throws Exception
	{
		Date now = new Date();
		TestObject to = new TestObject("football");
		TimeZone tz = TimeZone.getTimeZone("EST");
		Collection col = new ArrayList();
		col.add("Collection inside array");
		col.add(tz);
		col.add(now);
		Object[] objs = new Object[] { now, 123.45, 0.04f, "This is a string", null,  true, to, tz, col, (short) 7, (byte) -127};
		Object[] two = new Object[] {objs, "bella", objs};
		String json0 = TestUtil.getJsonString(two);
		TestUtil.printLine("json0=" + json0);
		Map map = JsonReader.jsonToMaps(json0);
		map.toString(); // Necessary
		String json1 = TestUtil.getJsonString(map);
		TestUtil.printLine("json1=" + json1);

		// Read back into typed Java objects, the Maps of Maps versus that was dumped out
		Object[] result = (Object[]) TestUtil.readJsonObject(json1);
		assertTrue(result.length == 3);
		Object[] arr1 = (Object[]) result[0];
		assertTrue(arr1.length == 11);
		String bella = (String) result[1];
		Object[] arr2 = (Object[]) result[2];
		assertTrue(arr1 == arr2);
		assertTrue("bella".equals(bella));
		assertTrue(now.equals(arr1[0]));
		assertTrue(arr1[1].equals(123.45));
		assertTrue(arr1[2].equals(0.04f));
		assertTrue("This is a string".equals(arr1[3]));
		assertTrue(arr1[4] == null);
		assertTrue(arr1[5] == Boolean.TRUE);
		assertTrue(to.equals(arr1[6]));
		assertTrue(tz.equals(arr1[7]));
		List c = (List) arr1[8];
		assertTrue("Collection inside array".equals(c.get(0)));
		assertTrue(tz.equals(c.get(1)));
		assertTrue(now.equals(c.get(2)));
		assertTrue(7== (Short)arr1[9]);
		assertTrue(-127 == (Byte) arr1[10]);
		assertTrue(json0.equals(json1));

		TestArray ta = new TestArray();
		ta.init();
		json0 = TestUtil.getJsonString(ta);
		TestUtil.printLine("json0=" + json0);

		map = JsonReader.jsonToMaps(json0);
		map.toString();
		json1 = TestUtil.getJsonString(map);
		TestUtil.printLine("json1=" + json1);

		assertTrue(json0.equals(json1));
	}

	@Test
	public void testReconstituteObjectArrayTypes() throws Exception
	{
		TestUtil.printLine("testReconstituteObjectArrayTypes");
		Object[] bytes = new Object[]{_CONST_BYTE,  _CONST_BYTE};
		testReconstituteArrayHelper(bytes);
		Byte[] Bytes = new Byte[]{_CONST_BYTE,  _CONST_BYTE};
		testReconstituteArrayHelper(Bytes);
		byte[] bites = new byte[]{_CONST_BYTE,  _CONST_BYTE};
		testReconstituteArrayHelper(bites);

		Object[] shorts = new Object[]{_CONST_SHORT,  _CONST_SHORT};
		testReconstituteArrayHelper(shorts);
		Short[] Shorts = new Short[]{_CONST_SHORT,  _CONST_SHORT};
		testReconstituteArrayHelper(Shorts);
		short[] shortz = new short[]{_CONST_SHORT,  _CONST_SHORT};
		testReconstituteArrayHelper(shortz);

		Object[] ints = new Object[]{_CONST_INT,  _CONST_INT};
		testReconstituteArrayHelper(ints);
		Integer[] Ints = new Integer[]{_CONST_INT,  _CONST_INT};
		testReconstituteArrayHelper(Ints);
		int[] intz = new int[]{_CONST_INT,  _CONST_INT};
		testReconstituteArrayHelper(intz);

		Object[] longs = new Object[]{_CONST_LONG,  _CONST_LONG};
		testReconstituteArrayHelper(longs);
		Long[] Longs = new Long[]{_CONST_LONG,  _CONST_LONG};
		testReconstituteArrayHelper(Longs);
		long[] longz = new long[]{_CONST_LONG,  _CONST_LONG};
		testReconstituteArrayHelper(longz);

		Object[] floats = new Object[]{_CONST_FLOAT,  _CONST_FLOAT};
		testReconstituteArrayHelper(floats);
		Float[] Floats = new Float[]{_CONST_FLOAT,  _CONST_FLOAT};
		testReconstituteArrayHelper(Floats);
		float[] floatz = new float[]{_CONST_FLOAT,  _CONST_FLOAT};
		testReconstituteArrayHelper(floatz);

		Object[] doubles = new Object[]{_CONST_DOUBLE,  _CONST_DOUBLE};
		testReconstituteArrayHelper(doubles);
		Double[] Doubles = new Double[]{_CONST_DOUBLE,  _CONST_DOUBLE};
		testReconstituteArrayHelper(Doubles);
		double[] doublez = new double[]{_CONST_DOUBLE,  _CONST_DOUBLE};
		testReconstituteArrayHelper(doublez);

		Object[] booleans = new Object[]{Boolean.TRUE, Boolean.TRUE};
		testReconstituteArrayHelper(booleans);
		Boolean[] Booleans = new Boolean[]{Boolean.TRUE,  Boolean.TRUE};
		testReconstituteArrayHelper(Booleans);
		boolean[] booleanz = new boolean[]{true, true};
		testReconstituteArrayHelper(booleanz);

		Object[] chars = new Object[]{'J', 'J'};
		testReconstituteArrayHelper(chars);
		Character[] Chars = new Character[]{'S', 'S'};
		testReconstituteArrayHelper(Chars);
		char[] charz = new char[]{'R', 'R'};
		testReconstituteArrayHelper(charz);

		Object[] classes = new Object[]{LinkedList.class, LinkedList.class};
		testReconstituteArrayHelper(classes);
		Class[] Classes = new Class[]{LinkedList.class, LinkedList.class};
		testReconstituteArrayHelper(Classes);

		Date now = new Date();
		Object[] dates = new Object[]{now, now};
		testReconstituteArrayHelper(dates);
		Date[] Dates = new Date[]{now, now};
		testReconstituteArrayHelper(Dates);

		BigDecimal pi = new BigDecimal(3.1415926535897932384626433);
		Object[] bigDecimals = new Object[]{pi, pi};
		testReconstituteArrayHelper(bigDecimals);
		BigDecimal[] BigDecimals = new BigDecimal[]{pi, pi};
		testReconstituteArrayHelper(BigDecimals);

		String s = "json-io";
		Object[] strings = new Object[]{s, s};
		testReconstituteArrayHelper(strings);
		String[] Strings = new String[]{s, s};
		testReconstituteArrayHelper(Strings);

		GregorianCalendar cal = (GregorianCalendar) Calendar.getInstance(TimeZone.getTimeZone("EST"));
		Object[] calendars = new Object[]{cal, cal};
		testReconstituteArrayHelper(calendars);
		Calendar[] Calendars = new Calendar[]{cal, cal};
		testReconstituteArrayHelper(Calendars);
		GregorianCalendar[] calendarz = new GregorianCalendar[]{cal, cal};
		testReconstituteArrayHelper(calendarz);
	}

	private void testReconstituteArrayHelper(Object foo) throws Exception
	{
		assertTrue(Array.getLength(foo) == 2);
		String json0 = TestUtil.getJsonString(foo);
		TestUtil.printLine("json0=" + json0);

		Map map = JsonReader.jsonToMaps(json0);
		map.toString(); // called to prevent compiler optimization that could eliminate map local variable.
		String json1 = TestUtil.getJsonString(map);
		TestUtil.printLine("json1=" + json1);
		assertTrue(json0.equals(json1));
	}

	@Test
	public void testReconstituteEmptyArray() throws Exception
	{
		Object[] empty = new Object[]{};
		String json0 = TestUtil.getJsonString(empty);
		TestUtil.printLine("json0=" + json0);

		Map map = JsonReader.jsonToMaps(json0);
		assertTrue(map != null);
		empty = (Object[]) map.get("@items");
		assertTrue(empty != null);
		assertTrue(empty.length == 0);
		String json1 = TestUtil.getJsonString(map);
		TestUtil.printLine("json1=" + json1);

		assertTrue(json0.equals(json1));

		Object[] list = new Object[] {empty, empty};
		json0 = TestUtil.getJsonString(list);
		TestUtil.printLine("json0=" + json0);

		map = JsonReader.jsonToMaps(json0);
		assertTrue(map != null);
		list = (Object[]) map.get("@items");
		assertTrue(list.length == 2);
		Map e1 = (Map) list[0];
		Map e2 = (Map) list[1];
		assertTrue(e1.get("@items") == e2.get("@items"));
		assertTrue(((Object[])e1.get("@items")).length == 0);

		json1 = TestUtil.getJsonString(map);
		TestUtil.printLine("json1=" + json1);
		assertTrue(json0.equals(json1));
	}

	@Test
	public void testReconstituteTypedArray() throws Exception
	{
		String[] strs = new String[] {"tom", "dick", "harry"};
		Object[] objs = new Object[] {strs, "a", strs};
		String json0 = TestUtil.getJsonString(objs);
		TestUtil.printLine("json0=" + json0);
		Map map = JsonReader.jsonToMaps(json0);
		map.toString();     // Necessary
		String json1 = TestUtil.getJsonString(map);
		TestUtil.printLine("json1=" + json1);

		Object[] result = (Object[]) TestUtil.readJsonObject(json1);
		assertTrue(result.length == 3);
		assertTrue(result[0] == result[2]);
		assertTrue("a".equals(result[1]));
		String[] sa = (String[]) result[0];
		assertTrue(sa.length == 3);
		assertTrue("tom".equals(sa[0]));
		assertTrue("dick".equals(sa[1]));
		assertTrue("harry".equals(sa[2]));
		String json2 = TestUtil.getJsonString(result);
		TestUtil.printLine("json2=" + json2);
		assertTrue(json0.equals(json1));
		assertTrue(json1.equals(json2));
	}

	@Test
	public void testReconstituteArray() throws Exception
	{
		TestArray testArray = new TestArray();
		testArray.init();
		String json0 = TestUtil.getJsonString(testArray);
		TestUtil.printLine("json0=" + json0);
		Map testArray2 = JsonReader.jsonToMaps(json0);

		String json1 = TestUtil.getJsonString(testArray2);
		TestUtil.printLine("json1=" + json1);

		TestArray testArray3 = (TestArray) TestUtil.readJsonObject(json1);
		assertArray(testArray3);    // Re-written from Map of Maps and re-loaded correctly
		assertTrue(json0.equals(json1));
	}

	@Test
	public void testReconstituteEmptyObject() throws Exception
	{
		Empty empty = new Empty();
		String json0 = TestUtil.getJsonString(empty);
		TestUtil.printLine("json0=" + json0);

		Map m = JsonReader.jsonToMaps(json0);
		assertTrue(m.isEmpty());

		String json1 = TestUtil.getJsonString(m);
		TestUtil.printLine("json1=" + json1);
		assertTrue(json0.equals(json1));
	}

	@Test
	public void testReconstituteMap() throws Exception
	{
		TestMap testMap = new TestMap();
		testMap.init();
		String json0 = TestUtil.getJsonString(testMap);
		TestUtil.printLine("json0=" + json0);
		Map testMap2 = JsonReader.jsonToMaps(json0);

		String json1 = TestUtil.getJsonString(testMap2);
		TestUtil.printLine("json1=" + json1);

		TestMap testMap3 = (TestMap) TestUtil.readJsonObject(json1);
		assertMap(testMap3);   // Re-written from Map of Maps and re-loaded correctly
		assertTrue(json0.equals(json1));
	}

	static class SimpleMapTest
	{
		HashMap map = new HashMap();
	}

	@Test
	public void testReconstituteMapSimple() throws Exception
	{
		SimpleMapTest smt = new SimpleMapTest();
		smt.map.put("a", "alpha");
		String json0 = TestUtil.getJsonString(smt);
		TestUtil.printLine("json0=" + json0);

		Map result = JsonReader.jsonToMaps(json0);
		String json1 = TestUtil.getJsonString(result);
		TestUtil.printLine("json1=" + json1);

		SimpleMapTest mapTest = (SimpleMapTest) TestUtil.readJsonObject(json1);
		assertTrue(mapTest.map.equals(smt.map));
		assertTrue(json0.equals(json1));
	}

	@Test
	public void testReconstituteMapEmpty() throws Exception
	{
		Map map = new LinkedHashMap();
		String json0 = TestUtil.getJsonString(map);
		TestUtil.printLine("json0=" + json0);

		map = JsonReader.jsonToMaps(json0);
		String json1 = TestUtil.getJsonString(map);
		TestUtil.printLine("json1=" + json1);

		map = (Map) TestUtil.readJsonObject(json1);
		assertTrue(map instanceof LinkedHashMap);
		assertTrue(map.isEmpty());
		assertTrue(json0.equals(json1));
	}

	@Test
	public void testReconstituteRefMap() throws Exception
	{
		Map m1 = new HashMap();
		Object[] root = new Object[]{ m1, m1};
		String json0 = TestUtil.getJsonString(root);
		TestUtil.printLine("json0=" + json0);

		Map map = JsonReader.jsonToMaps(json0);
		String json1 = TestUtil.getJsonString(map);
		TestUtil.printLine("json1=" + json1);

		root = (Object[]) TestUtil.readJsonObject(json1);
		assertTrue(root.length == 2);
		assertTrue(root[0] instanceof Map);
		assertTrue(((Map)root[0]).isEmpty());
		assertTrue(root[1] instanceof Map);
		assertTrue(((Map) root[1]).isEmpty());
		assertTrue(json0.equals(json1));
	}

	@Test
	public void testReconstitutePrimitives() throws Exception
	{
		Object foo = new TestJsonNoDefaultOrPublicConstructor("Hello, World.", new Date(), (byte) 1, new Byte((byte)11), (short) 2, new Short((short)22), 3, new Integer(33), 4L, new Long(44L), 5.0f, new Float(55.0f), 6.0, new Double(66.0), true, Boolean.TRUE,'J', new Character('K'), new String[] {"john","adams"}, new int[] {2,6}, new BigDecimal("2.71828"));
		String json0 = TestUtil.getJsonString(foo);
		TestUtil.printLine("json0=" + json0);

		Map map = JsonReader.jsonToMaps(json0);
		assertEquals((byte)1, map.get("_byte") );
		assertEquals((short)2, map.get("_short"));
		assertEquals(3, map.get("_int"));
		assertEquals(4L, map.get("_long"));
		assertEquals(5.0f, map.get("_float"));
		assertEquals(6.0d, map.get("_double"));
		assertEquals(true, map.get("_boolean"));
		assertEquals('J', map.get("_char"));

		assertEquals((byte)11, map.get("_Byte"));
		assertEquals((short)22, map.get("_Short"));
		assertEquals(33, map.get("_Integer"));
		assertEquals(44L, map.get("_Long"));
		assertEquals(55.0f, map.get("_Float"));
		assertEquals(66.0d, map.get("_Double"));
		assertEquals(true, map.get("_Boolean"));
		assertEquals('K', map.get("_Char"));
		BigDecimal num = (BigDecimal) map.get("_bigD");
		assertEquals(new BigDecimal("2.71828"), num);

		String json1 = TestUtil.getJsonString(map);
		TestUtil.printLine("json1=" + json1);
		assertTrue(json0.equals(json1));
	}

	@Test
	public void testReconstituteNullablePrimitives() throws Exception
	{
		Object foo = new TestJsonNoDefaultOrPublicConstructor("Hello, World.", new Date(), (byte) 1, null, (short) 2, null, 3, null, 4L, null, 5.0f, null, 6.0, null, true, null,'J', null, new String[] {"john","adams"}, new int[] {2,6}, null);
		String json = TestUtil.getJsonString(foo);
		TestUtil.printLine("json0=" + json);

		Map map = JsonReader.jsonToMaps(json);
		assertEquals((byte)1, map.get("_byte"));
		assertEquals((short)2, map.get("_short"));
		assertEquals((int)3, map.get("_int"));
		assertEquals(4L, map.get("_long"));
		assertEquals(5.0f, map.get("_float"));
		assertEquals(6.0d, map.get("_double"));
		assertEquals(true, map.get("_boolean"));
		assertEquals((char)'J', map.get("_char"));

		Map prim = (Map) map.get("_Byte");
		assertNull(prim);
		prim = (Map) map.get("_Short");
		assertNull(prim);
		prim = (Map) map.get("_Integer");
		assertNull(prim);
		prim = (Map) map.get("_Long");
		assertNull(prim);
		prim = (Map) map.get("_Float");
		assertNull(prim);
		prim = (Map) map.get("_Double");
		assertNull(prim);
		prim = (Map) map.get("_Boolean");
		assertNull(prim);
		prim = (Map) map.get("_Char");
		assertNull(prim);
		prim = (Map) map.get("_bigD");
		assertNull(prim);

		String json1 = TestUtil.getJsonString(map);
		TestUtil.printLine("json1=" + json1);
		assertTrue(json.equals(json1));

		map = JsonReader.jsonToMaps(json1);
		json = TestUtil.getJsonString(map);
		TestUtil.printLine("json2=" + json);
		assertTrue(json.equals(json1));
	}

	@Test
	public void testArraysAsList() throws Exception
	{
		Object[] strings = new Object[] { "alpha", "bravo", "charlie" };
		List strs = Arrays.asList(strings);
		List foo = (List) TestUtil.readJsonObject(TestUtil.getJsonString(strs));
		assertTrue(foo.size() == 3);
		assertTrue("alpha".equals(foo.get(0)));
		assertTrue("charlie".equals(foo.get(2)));
	}

	@Test
	public void testLocaleInMap() throws Exception
	{
		Map<Locale, String> map = new HashMap<Locale, String>();
		map.put(Locale.US, "United States of America");
		map.put(Locale.CANADA, "Canada");
		map.put(Locale.UK, "United Kingdom");

		String json = TestUtil.getJsonString(map);
		Map map2 = (Map) TestUtil.readJsonObject(json);
		assertTrue(map.equals(map2));
	}

	@Test
	public void testMultiDimensionalArrays() throws Exception
	{
		int[][][][] x = new int[][][][]{{{{0,1},{0,1}},{{0,1},{0,1}}},{{{0,1},{0,1}},{{0,1},{0,1}}}};
		for (int a=0; a < 2; a++)
		{
			for (int b=0; b < 2; b++)
			{
				for (int c=0; c < 2; c++)
				{
					for (int d=0; d < 2; d++)
					{
						x[a][b][c][d] = a + b + c + d;
					}
				}
			}
		}

		String json = TestUtil.getJsonString(x);
		int[][][][] y = (int[][][][]) TestUtil.readJsonObject(json);


		for (int a=0; a < 2; a++)
		{
			for (int b=0; b < 2; b++)
			{
				for (int c=0; c < 2; c++)
				{
					for (int d=0; d < 2; d++)
					{
						assertTrue(y[a][b][c][d] == a + b + c + d);
					}
				}
			}
		}

		Integer[][][][] xx = new Integer[][][][]{{{{0,1},{0,1}},{{0,1},{0,1}}},{{{0,1},{0,1}},{{0,1},{0,1}}}};
		for (int a=0; a < 2; a++)
		{
			for (int b=0; b < 2; b++)
			{
				for (int c=0; c < 2; c++)
				{
					for (int d=0; d < 2; d++)
					{
						xx[a][b][c][d] = a + b + c + d;
					}
				}
			}
		}

		json = TestUtil.getJsonString(xx);
		Integer[][][][] yy = (Integer[][][][]) TestUtil.readJsonObject(json);


		for (int a=0; a < 2; a++)
		{
			for (int b=0; b < 2; b++)
			{
				for (int c=0; c < 2; c++)
				{
					for (int d=0; d < 2; d++)
					{
						assertTrue(yy[a][b][c][d] == a + b + c + d);
					}
				}
			}
		}
	}

	static class MyBooleanTesting
	{
		private boolean myBoolean = false;
	}

	static class MyBoolean2Testing
	{
		private Boolean myBoolean = false;
	}

	@Test
	public void testBooleanCompatibility() throws Exception
	{
		MyBooleanTesting testObject = new MyBooleanTesting();
		MyBoolean2Testing testObject2 = new MyBoolean2Testing();
		String json0 = TestUtil.getJsonString(testObject);
		String json1 = TestUtil.getJsonString(testObject2);

		TestUtil.printLine("json0=" + json0);
		TestUtil.printLine("json1=" + json1);

		assertTrue(json0.contains("\"myBoolean\":false"));
		assertTrue(json1.contains("\"myBoolean\":false"));
	}

	@Test
	public void testMapToMapCompatibility() throws Exception
	{
		String json0 = "{\"rows\":[{\"columns\":[{\"name\":\"FOO\",\"value\":\"9000\"},{\"name\":\"VON\",\"value\":\"0001-01-01\"},{\"name\":\"BAR\",\"value\":\"0001-01-01\"}]},{\"columns\":[{\"name\":\"FOO\",\"value\":\"9713\"},{\"name\":\"VON\",\"value\":\"0001-01-01\"},{\"name\":\"BAR\",\"value\":\"0001-01-01\"}]}],\"selectedRows\":\"110\"}";
		JsonObject root = (JsonObject) JsonReader.jsonToMaps(json0);
		String json1 = TestUtil.getJsonString(root);
		JsonObject root2 = (JsonObject) JsonReader.jsonToMaps(json1);
		assertTrue(DeepEquals.deepEquals(root, root2));

		// Will be different because @keys and @items get inserted during processing
		TestUtil.printLine("json0=" + json0);
		TestUtil.printLine("json1=" + json1);
	}

	@Test
	public void testJsonObjectToJava() throws Exception
	{
		TestObject test = new TestObject("T.O.");
		TestObject child = new TestObject("child");
		test._other = child;
		String json = TestUtil.getJsonString(test);
		TestUtil.printLine("json=" + json);
		JsonObject root = (JsonObject) JsonReader.jsonToMaps(json);
		JsonReader reader = new JsonReader();
		TestObject test2 = (TestObject) reader.jsonObjectsToJava(root);
		assertTrue(test2.equals(test));
		assertTrue(test2._other.equals(child));
	}

	@Test
	public void testInnerInstance() throws Exception
	{
		Dog dog = new Dog();
		dog.x = 10;
		Dog.Leg leg = dog.new Leg();
		leg.y = 20;
		String json0 = TestUtil.getJsonString(dog);
		TestUtil.printLine("json0=" + json0);

		String json1 = TestUtil.getJsonString(leg);
		TestUtil.printLine("json1=" + json1);
		Dog.Leg go = (Dog.Leg) TestUtil.readJsonObject(json1);
		assertTrue(go.y == 20);
		assertTrue(go.getParentX() == 10);
	}

	@Test
	public void testChangedClass() throws Exception
	{
		Dog dog = new Dog();
		dog.x = 10;
		Dog.Leg leg = dog.new Leg();
		leg.y = 20;
		String json0 = TestUtil.getJsonString(dog);
		TestUtil.printLine("json0=" + json0);
		JsonObject job = (JsonObject) JsonReader.jsonToMaps(json0);
		job.put("phantom", new TestObject("Eddie"));
		String json1 = TestUtil.getJsonString(job);
		TestUtil.printLine("json1=" + json1);
		assertTrue(json1.contains("phantom"));
		assertTrue(json1.contains("TestObject"));
		assertTrue(json1.contains("_other"));
	}

	static class Transient1
	{
		String fname;
		String lname;
		transient String fullname;

		void setFname(String f) { fname = f; buildFull(); }
		void setLname(String l) { lname = l; buildFull(); }
		void buildFull()        { fullname = fname + " " + lname; }
	}

	@Test
	public void testTransient1() throws Exception
	{
		Transient1 person = new Transient1();
		person.setFname("John");
		person.setLname("DeRegnaucourt");

		String json = TestUtil.getJsonString(person);
		TestUtil.printLine("json = " + json);
		assertFalse(json.contains("fullname"));

		person = (Transient1) TestUtil.readJsonObject(json);
		assertTrue(person.fullname == null);
	}

	public static class Transient2
	{
		transient TestObject backup;
		TestObject main;
	}

	@Test
	public void testTransient2() throws Exception
	{
		Transient2 trans = new Transient2();
		trans.backup = new TestObject("Roswell");
		trans.main = trans.backup;

		String json = TestUtil.getJsonString(trans);
		TestUtil.printLine("json = " + json);
		assertFalse(json.contains("backup"));

		trans = (Transient2) TestUtil.readJsonObject(json);
		assertEquals(trans.main._name, "Roswell");
	}

	// Currently allows , at end.  Future, may drop this support.
	@Test
	public void testBadArray() throws Exception
	{
		String json = "[1, 10, 100,]";
		Object[] array = (Object[]) TestUtil.readJsonObject(json);
		assertTrue(array.length == 3);
		assertEquals(array[0], 1L);
		assertEquals(array[1], 10L);
		assertEquals(array[2], 100L);
	}

	static class NoNullConstructor
	{
		List list;
		Map map;
		String string;
		Date date;

		private NoNullConstructor(List list, Map map, String string, Date date)
		{
			if (list == null || map == null || string == null || date == null)
			{
				throw new RuntimeException("Constructor arguments cannot be null");
			}
			this.list = list;
			this.map = map;
			this.string = string;
			this.date = date;
		}
	}

	@Test
	public void testNoNullConstructor() throws Exception
	{
		NoNullConstructor noNull = new NoNullConstructor(new ArrayList(), new HashMap(), "", new Date());
		noNull.list = null;
		noNull.map = null;
		noNull.string = null;
		noNull.date = null;

		String json = TestUtil.getJsonString(noNull);
		TestUtil.printLine(json);
		NoNullConstructor foo = (NoNullConstructor) TestUtil.readJsonObject(json);
		assertNull(foo.list);
		assertNull(foo.map);
		assertNull(foo.string);
		assertNull(foo.date);
	}

	static class EmptyArrayList
	{
		ArrayList<String> list = new ArrayList<String>();
	}

	@Test
	public void testEmptyArrayList() throws Exception
	{
		EmptyArrayList x = new EmptyArrayList();
		String json = TestUtil.getJsonString(x);
		TestUtil.printLine(json);
		assertTrue(json.contains("list\":[]"));

		Map obj = JsonReader.jsonToMaps(json);
		json = TestUtil.getJsonString(obj);
		TestUtil.printLine(json);
		assertTrue(json.contains("list\":[]"));
	}

	static class Outer
	{
		String name;
		class Inner
		{
			String name;
		}
		Inner foo;
	}

	@Test
	public void testNestedWithSameMemberName() throws Exception
	{
		Outer outer = new Outer();
		outer.name = "Joe Outer";

		Outer.Inner inner = outer.new Inner();
		inner.name = "Jane Inner";
		outer.foo = inner;

		String json = TestUtil.getJsonString(outer);
		TestUtil.printLine("json = " + json);

		Outer x = (Outer) TestUtil.readJsonObject(json);
		assertEquals(x.name, "Joe Outer");
		assertEquals(x.foo.name, "Jane Inner");
	}

	public static class Parent
	{
		private String name;

		public String getParentName()
		{
			return name;
		}

		public void setParentName(String name)
		{
			this.name = name;
		}
	}

	public static class Child extends Parent
	{
		private String name;

		public String getChildName()
		{
			return name;
		}

		public void setChildName(String name)
		{
			this.name = name;
		}
	}

	@Test
	public void testSameMemberName() throws Exception
	{
		Child child = new Child();
		child.setChildName("child");
		child.setParentName("parent");

		String json = TestUtil.getJsonString(child);
		TestUtil.printLine(json);
		Child roundTrip = (Child) TestUtil.readJsonObject(json);

		assertEquals(child.getParentName(), roundTrip.getParentName());
		assertEquals(child.getChildName(), roundTrip.getChildName());

		JsonObject jObj = (JsonObject)JsonReader.jsonToMaps(json);
		String json1 = TestUtil.getJsonString(jObj);
		TestUtil.printLine(json1);
		assertEquals(json, json1);
	}

	@Test
	public void testErrorReporting() throws IOException
	{
		String json = "[{\"@type\":\"funky\"},\n{\"field:\"value\"]";
		try
		{
			JsonReader.jsonToJava(json);
			fail("Should not make it here");
		}
		catch (IOException e)
		{
		}
	}

	@Test
	public void testAlwaysShowType() throws Exception
	{
		TestObject btc = new TestObject("Bitcoin");
		TestObject sat = new TestObject("Satoshi");
		btc._other = sat;
		Map args = new HashMap();
		args.put(JsonWriter.TYPE, true);
		String json0 = JsonWriter.objectToJson(btc, args);
		TestObject thatBtc = (TestObject) TestUtil.readJsonObject(json0);
		assertTrue(DeepEquals.deepEquals(btc, thatBtc));
		String json1 = JsonWriter.objectToJson(btc);
		assertTrue(json0.length() > json1.length());

		TestArray ta = new TestArray();
		ta.init();
		json0 = JsonWriter.objectToJson(ta, args);
		TestArray thatTa = (TestArray) TestUtil.readJsonObject(json0);
		assertTrue(DeepEquals.deepEquals(ta, thatTa));
		json1 = JsonWriter.objectToJson(ta);
		TestUtil.printLine("json0 = " + json0);
		TestUtil.printLine("json1 = " + json1);
		assertTrue(json0.length() > json1.length());
	}

	@Test
	public void testFunnyChars() throws Exception
	{
		String json = "{\"@type\":\"[C\",\"@items\":[\"a\\t\\u0004\"]}";
		char[] chars = (char[]) TestUtil.readJsonObject(json);
		assertEquals(chars.length, 3);
		assertEquals(chars[0], 'a');
		assertEquals(chars[1], '\t');
		assertEquals(chars[2], '\u0004');
	}

	static class CharArrayTest
	{
		char[] chars_a;
		Character[] chars_b;
	}

	@Test
	public void testCharArray() throws Exception
	{
		CharArrayTest cat = new CharArrayTest();
		cat.chars_a = new char[]{'a', '\t', '\u0005'};
		cat.chars_b = new Character[]{'b', '\t', '\u0002'};

		Map args = new HashMap();
		args.put(JsonWriter.TYPE, true);
		String json0 = JsonWriter.objectToJson(cat, args);
		TestUtil.printLine(json0);

		CharArrayTest cat2 = (CharArrayTest) TestUtil.readJsonObject(json0);
		char[] chars_a = cat2.chars_a;
		assertEquals(chars_a.length, 3);
		assertEquals(chars_a[0], 'a');
		assertEquals(chars_a[1], '\t');
		assertEquals(chars_a[2], '\u0005');

		Character[] chars_b = cat2.chars_b;
		assertEquals(chars_b.length, 3);
		assertTrue(chars_b[0] == 'b');
		assertTrue(chars_b[1] == '\t');
		assertTrue(chars_b[2] =='\u0002');

		String json1 = JsonWriter.objectToJson(cat);
		TestUtil.printLine(json1);

		cat2 = (CharArrayTest) TestUtil.readJsonObject(json0);
		chars_a = cat2.chars_a;
		assertEquals(chars_a.length, 3);
		assertEquals(chars_a[0], 'a');
		assertEquals(chars_a[1], '\t');
		assertEquals(chars_a[2], '\u0005');

		chars_b = cat2.chars_b;
		assertEquals(chars_b.length, 3);
		assertTrue(chars_b[0] == 'b');
		assertTrue(chars_b[1] == '\t');
		assertTrue(chars_b[2] =='\u0002');
	}

	static class ParameterizedMap
	{
		Map<String, Map<String, Point>> content = new LinkedHashMap<>();
	}

	@Test
	public void testMapWithParameterizedTypes() throws Exception
	{
		String json = "{\"@type\":\"" + ParameterizedMap.class.getName() + "\", \"content\":{\"foo\":{\"one\":{\"x\":1,\"y\":2},\"two\":{\"x\":10,\"y\":20}},\"bar\":{\"ten\":{\"x\":3,\"y\":4},\"twenty\":{\"x\":30,\"y\":40}}}}";
		ParameterizedMap pCol = (ParameterizedMap) JsonReader.jsonToJava(json);
		Map<String, Point> points = pCol.content.get("foo");
		assertNotNull(points);
		assertEquals(2, points.size());
		assertEquals(new Point(1,2), points.get("one"));
		assertEquals(new Point(10,20), points.get("two"));

		points = pCol.content.get("bar");
		assertNotNull(points);
		assertEquals(2, points.size());
		assertEquals(new Point(3, 4), points.get("ten"));
		assertEquals(new Point(30, 40), points.get("twenty"));
	}

	static class ThreeType<T, U, V>
	{
		T t;
		U u;
		V v;

		public ThreeType(T tt, U uu, V vv)
		{
			t = tt;
			u = uu;
			v = vv;
		}
	}

	static class GenericHolder
	{
		ThreeType<Point, String, Point> a;
	}

	@Test
	public void test3TypeGeneric() throws Exception
	{
		String json = "{\"@type\":\"" + GenericHolder.class.getName() + "\",\"a\":{\"t\":{\"x\":1,\"y\":2},\"u\":\"Sochi\",\"v\":{\"x\":10,\"y\":20}}}";
		GenericHolder gen = (GenericHolder) JsonReader.jsonToJava(json);
		assertEquals(new Point(1, 2), gen.a.t);
		assertEquals("Sochi", gen.a.u);
		assertEquals(new Point(10, 20), gen.a.v);

		json = "{\"@type\":\"" + GenericHolder.class.getName() + "\",\"a\":{\"t\":null,\"u\":null,\"v\":null}}";
		gen = (GenericHolder) JsonReader.jsonToJava(json);
		assertNull(gen.a.t);
		assertNull(gen.a.u);
		assertNull(gen.a.v);
	}

	static class MapSetKey
	{
		Map<Set<Point>, String> content;
	}

	@Test
	public void testMapSetKey() throws Exception
	{
		MapSetKey m = new MapSetKey();
		m.content = new LinkedHashMap<>();
		Set<Point> setA = new LinkedHashSet<>();
		setA.add(new Point(1, 2));
		setA.add(new Point(10, 20));
		m.content.put(setA, "foo");
		Set<Point> setB = new LinkedHashSet<>();
		setB.add(new Point(3, 4));
		setB.add(new Point(30, 40));
		m.content.put(setB, "bar");
		String json = TestUtil.getJsonString(m);
		MapSetKey x = (MapSetKey) TestUtil.readJsonObject(json);

		assertEquals("foo", x.content.get(setA));
		assertEquals("bar", x.content.get(setB));

		m = new MapSetKey();
		m.content = new LinkedHashMap<>();
		m.content.put(null, null);
		json = TestUtil.getJsonString(m);
		x = (MapSetKey) TestUtil.readJsonObject(json);
		assertNull(x.content.get(null));

		m = new MapSetKey();
		m.content = new LinkedHashMap<>();
		m.content.put(new LinkedHashSet(), "Fargo");
		json = TestUtil.getJsonString(m);
		x = (MapSetKey) TestUtil.readJsonObject(json);
		assertEquals("Fargo", x.content.get(new LinkedHashSet<Point>()));
	}

	static class MapArrayKey
	{
		Map<Object[], String> content;
	}

	@Test
	public void testMapArrayKey() throws Exception
	{
		MapArrayKey m = new MapArrayKey();
		m.content = new LinkedHashMap<>();
		Object[] arrayA = new Object[2];
		arrayA[0] = new Point(1, 2);
		arrayA[1] = new Point(10, 20);
		m.content.put(arrayA, "foo");
		Object[] arrayB = new Object[2];
		arrayB[0] = new Point(3, 4);
		arrayB[1] = new Point(30, 40);
		m.content.put(arrayB, "bar");
		String json = TestUtil.getJsonString(m);
		MapArrayKey x = (MapArrayKey) TestUtil.readJsonObject(json);

		Iterator<Map.Entry<Object[], String>> i = x.content.entrySet().iterator();
		Map.Entry<Object[], String> entry = i.next();

		assertEquals("foo", entry.getValue());
		arrayA = entry.getKey();
		assertEquals(new Point(1, 2), arrayA[0]);
		assertEquals(new Point(10, 20), arrayA[1]);

		entry = i.next();
		assertEquals("bar", entry.getValue());
		arrayB = entry.getKey();
		assertEquals(new Point(3, 4), arrayB[0]);
		assertEquals(new Point(30, 40), arrayB[1]);
	}

	class AllPrimitives
	{
		boolean b;
		Boolean bb;
		byte by;
		Byte bby;
		char c;
		Character cc;
		double d;
		double dd;
		float f;
		Float ff;
		int i;
		Integer ii;
		long l;
		Long ll;
		short s;
		Short ss;
	}

	@Test
	public void testPrimitivesSetWithStrings() throws Exception
	{
		String json = "{\"@type\":\"" + AllPrimitives.class.getName() + "\",\"b\":\"true\",\"bb\":\"true\",\"by\":\"9\",\"bby\":\"9\",\"c\":\"B\",\"cc\":\"B\",\"d\":\"9.0\",\"dd\":\"9.0\",\"f\":\"9.0\",\"ff\":\"9.0\",\"i\":\"9\",\"ii\":\"9\",\"l\":\"9\",\"ll\":\"9\",\"s\":\"9\",\"ss\":\"9\"}";
		AllPrimitives ap = (AllPrimitives) TestUtil.readJsonObject(json);
		assertTrue(ap.b);
		assertTrue(ap.bb);
		assertTrue(ap.by == 9);
		assertTrue(ap.bby == 9);
		assertTrue(ap.c == 'B');
		assertTrue(ap.cc == 'B');
		assertTrue(ap.d == 9);
		assertTrue(ap.dd == 9);
		assertTrue(ap.f == 9);
		assertTrue(ap.ff == 9);
		assertTrue(ap.i == 9);
		assertTrue(ap.ii == 9);
		assertTrue(ap.l == 9);
		assertTrue(ap.ll == 9);
		assertTrue(ap.s == 9);
		assertTrue(ap.ss == 9);
	}

	class TestStringField
	{
		String intField;
		String booleanField;
		String doubleField;
		String nullField;
		String[] values;
	}

	@Test
	public void testAssignPrimitiveToString() throws Exception
	{
		String json = "{\"@type\":\"" + TestStringField.class.getName() + "\",\"intField\":16,\"booleanField\":true,\"doubleField\":345.12321,\"nullField\":null,\"values\":[10,true,3.14159,null]}";
		TestStringField tsf = (TestStringField) TestUtil.readJsonObject(json);
		assertEquals("16", tsf.intField);
		assertEquals("true", tsf.booleanField);
		assertEquals("345.12321", tsf.doubleField);
		assertNull(tsf.nullField);
		assertEquals("10", tsf.values[0]);
		assertEquals("true", tsf.values[1]);
		assertEquals("3.14159", tsf.values[2]);
		assertEquals(null, tsf.values[3]);
	}

	static class DerivedWriter extends JsonWriter
	{
		public DerivedWriter(OutputStream out) throws IOException
		{
			super(out);
		}
	}

	@Test
	public void testProtectedAPIs() throws Exception
	{
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		DerivedWriter writer = new DerivedWriter(bao);
		Map ref = writer.getObjectsReferenced();
		Map vis = writer.getObjectsVisited();
		Map args = DerivedWriter.getArgs();
		assertNotNull(ref);
		assertNotNull(vis);
		assertNotNull(args);
	}

	@Test
	public void testClassForName2() throws Exception
	{
		try
		{
			JsonReader.classForName(null);
			fail();
		}
		catch (IOException e)
		{
		}
		try
		{
			JsonReader.classForName("Smith&Wesson");
			fail();
		}
		catch (IOException e)
		{
		}
	}

	@Test
	public void testCleanString()
	{
		String s = JsonReader.removeLeadingAndTrailingQuotes("\"Foo\"");
		assertEquals("Foo", s);
		s = JsonReader.removeLeadingAndTrailingQuotes("Foo");
		assertEquals("Foo", s);
		s = JsonReader.removeLeadingAndTrailingQuotes("\"Foo");
		assertEquals("Foo", s);
		s = JsonReader.removeLeadingAndTrailingQuotes("Foo\"");
		assertEquals("Foo", s);
		s = JsonReader.removeLeadingAndTrailingQuotes("\"\"Foo\"\"");
		assertEquals("Foo", s);
	}

	static class Canine
	{
		String name;
		Canine(Object nm)
		{
			name = nm.toString();     // intentionally causes NPE when reflective constructor tries 'null' as arg
		}
	}

	@Test
	public void testConstructorWithObjectArg() throws Exception
	{
		Canine bella = new Canine("Bella");
		String json = TestUtil.getJsonString(bella);
		TestUtil.printLine("json = " + json);
		Canine dog = (Canine) TestUtil.readJsonObject(json);
		assertEquals("Bella", dog.name);
	}

	static class Web
	{
		URL url;
		Web(URL u)
		{
			url = u;
		}
	}

	@Test
	public void testUrlInConstructor() throws Exception
	{
		Web addr = new Web(new URL("http://acme.com"));
		String json = TestUtil.getJsonString(addr);
		TestUtil.printLine("json = " + json);
		Web addr2 = (Web) TestUtil.readJsonObject(json);
		assertEquals(new URL("http://acme.com"), addr2.url);
	}

	@Test
	public void testRootTypes() throws Exception
	{
		assertEquals(25L, TestUtil.readJsonObject("25"));
		assertEquals(25.0, TestUtil.readJsonObject("25.0"));
		assertEquals(true, TestUtil.readJsonObject("true"));
		assertEquals(false, TestUtil.readJsonObject("false"));
		assertEquals("foo", TestUtil.readJsonObject("\"foo\""));
	}

	static class AssignToList
	{
		Map assignTo;
	}

	@Test
	public void testMapWithAtType() throws Exception
	{
		AssignToList atl = new AssignToList();
		String json = "{\"@id\":1,\"@type\":\"java.util.LinkedHashMap\",\"@keys\":[\"1000004947\",\"0000020985\",\"0000029443\",\"0000020994\"],\"@items\":[\"Me\",\"Fox, James\",\"Renewals, CORE\",\"Gade, Raja\"]}";
		Map assignTo = (Map) JsonReader.jsonToJava(json);
		atl.assignTo = assignTo;
		json = JsonWriter.objectToJson(atl);
		TestUtil.printLine(json);
	}

	@Test
	public void testDistanceToInterface() throws Exception
	{
		assertEquals(1, JsonWriter.getDistanceToInterface(Serializable.class, LinkedList.class));
		assertEquals(3, JsonWriter.getDistanceToInterface(Iterable.class, LinkedList.class));
		assertEquals(2, JsonWriter.getDistanceToInterface(Serializable.class, BigInteger.class));
	}

	@Test
	public void testNoAnalysisForCustomWriter() throws Exception
	{
		JsonWriter.addWriter(Dog.class, new JsonClassWriter()
		{
			@Override
			public void writePrimitiveForm(Object o, Writer out)  throws IOException
			{ }

			@Override
			public void write(Object o, boolean showType, Writer out)  throws IOException
			{ }

			@Override
			public boolean hasPrimitiveForm()
			{
				return false;
			}
		});
	}

	class Single<T>
	{
		private T field1;
		private T field2;

		public Single(T field1, T field2)
		{
			this.field1 = field1;
			this.field2 = field2;
		}
	}

	class UseSingle
	{
		private Single<String> single;

		public UseSingle(Single<String> single)
		{
			this.single = single;
		}
	}

	@Test
	public void testSingle() throws IOException
	{
		UseSingle useSingle = new UseSingle(new Single<String>("Steel", "Wood"));
		String json = JsonWriter.objectToJson(useSingle);
		UseSingle other = (UseSingle) JsonReader.jsonToJava(json);

		assertEquals("Steel", other.single.field1);
		assertEquals("Wood", other.single.field2);
	}

	class TwoParam<T, V>
	{
		private T field1;
		private T field2;
		private V field3;

		public TwoParam(T field1, T field2, V field3)
		{
			this.field1 = field1;
			this.field2 = field2;
			this.field3 = field3;
		}
	}

	class UseTwoParam
	{
		private TwoParam twoParam;

		public UseTwoParam(TwoParam twoParam)
		{
			this.twoParam = twoParam;
		}
	}

	@Test
	public void testTwoParam() throws IOException
	{
		UseTwoParam useTwoParam = new UseTwoParam(new TwoParam("Hello", "Goodbye", new Point(20, 40)));
		String json = JsonWriter.objectToJson(useTwoParam);
		UseTwoParam other = (UseTwoParam) JsonReader.jsonToJava(json);

		assertEquals("Hello", other.twoParam.field1);
		assertEquals("Goodbye", other.twoParam.field2);
		assertEquals(new Point(20, 40), other.twoParam.field3);

		useTwoParam = new UseTwoParam(new TwoParam(new Point(10, 30), new Point(20, 40), "Hello"));
		json = JsonWriter.objectToJson(useTwoParam);
		other = (UseTwoParam) JsonReader.jsonToJava(json);

		assertEquals(new Point(10, 30), other.twoParam.field1);
		assertEquals(new Point(20, 40), other.twoParam.field2);
		assertEquals("Hello", other.twoParam.field3);

		useTwoParam = new UseTwoParam(new TwoParam(50, 100, "Hello"));
		json = JsonWriter.objectToJson(useTwoParam);
		other = (UseTwoParam) JsonReader.jsonToJava(json);

		assertEquals(50, other.twoParam.field1);
		assertEquals(100, other.twoParam.field2);
		assertEquals("Hello", other.twoParam.field3);

		useTwoParam = new UseTwoParam(new TwoParam(new Point(10, 30), new Point(20, 40), new TestObject("Hello")));
		json = JsonWriter.objectToJson(useTwoParam);
		other = (UseTwoParam) JsonReader.jsonToJava(json);

		assertEquals(new Point(10, 30), other.twoParam.field1);
		assertEquals(new Point(20, 40), other.twoParam.field2);
		assertEquals(new TestObject("Hello"), other.twoParam.field3);
	}

	static class StaticSingle<T>
	{
		private T field1;

		public StaticSingle(T field1)
		{
			this.field1 = field1;
		}
	}

	static class StaticUseSingle
	{
		private StaticSingle<String> single;

		public StaticUseSingle(StaticSingle<String> single)
		{
			this.single = single;
		}
	}

	@Test
	public void testStaticSingle() throws IOException
	{
		StaticUseSingle useSingle = new StaticUseSingle(new StaticSingle<>("Boonies"));

		String json = JsonWriter.objectToJson(useSingle);
		//this will crash on ArrayIndexOutOfBoundsException
		StaticUseSingle other = (StaticUseSingle) JsonReader.jsonToJava(json);

		assertEquals("Boonies", other.single.field1);
	}

	@Test
	public void testZTimings()
	{
		TestUtil.getTimings();
	}

	static class TestNoId
	{
		protected Class<?> cls = LinkedList.class;
	}

	@Test
	public void testShouldNotNeedId() throws Exception
	{
		TestNoId testNoId = new TestNoId();
		String json = JsonWriter.objectToJson(testNoId);
		assertFalse(json.contains("@id"));
	}
}