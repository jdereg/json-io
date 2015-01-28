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
import java.io.Writer;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;

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
		assertEquals(3, map.get("_int"));
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