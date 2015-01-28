package com.cedarsoftware.util.io;

import com.cedarsoftware.util.DeepEquals;
import com.cedarsoftware.util.io.JsonWriter.JsonClassWriter;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.Writer;
import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
	public void testNull() throws Exception
	{
		String json = JsonWriter.objectToJson(null);
		TestUtil.printLine("json=" + json);
		assertTrue("null".equals(json));
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
			public void writePrimitiveForm(Object o, Writer out)  throws IOException
			{ }

			public void write(Object o, boolean showType, Writer out)  throws IOException
			{ }

			public boolean hasPrimitiveForm()
			{
				return false;
			}
		});
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