package com.cedarsoftware.util.io;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test cases for JsonReader / JsonWriter
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class TestMapOfMaps
{
    @Test
    public void testMapOfMapsWithUnknownClasses()
    {
        String json = "{\"@type\":\"com.foo.bar.baz.Qux\",\"_name\":\"Hello\",\"_other\":null}";
        Map stuff = TestUtil.toJava(json);
        assert stuff.size() == 2;
        assert stuff.get("_name").equals("Hello");
        assert stuff.get("_other") == null;

        Map map = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsMaps().build());
        Assertions.assertEquals("Hello", map.get("_name"));
        Assertions.assertNull(map.get("_other"));

        // 2nd attempt
        String testObjectClassName = TestObject.class.getName();
        final String json2 = "{\"@type\":\"" + testObjectClassName + "\",\"_name\":\"alpha\",\"_other\":{\"@type\":\"com.baz.Qux\",\"_name\":\"beta\",\"_other\":null}}";

        Exception e = assertThrows(Exception.class, () -> { TestUtil.toJava(json2);});
        org.assertj.core.api.Assertions.assertThat(e.getMessage().toLowerCase()).contains("setting field \'_other\'");

        map = TestUtil.toJava(json2, new ReadOptionsBuilder().returnAsMaps().build());
        Assertions.assertEquals("alpha", map.get("_name"));
        Assertions.assertTrue(map.get("_other") instanceof JsonObject);
        JsonObject other = (JsonObject) map.get("_other");
        Assertions.assertEquals("beta", other.get("_name"));
        Assertions.assertNull(other.get("_other"));
    }

    @Test
    public void testForwardRefNegId()
    {
        Object doc = TestUtil.toJava(TestUtil.fetchResource("forwardRefNegId.json"), new ReadOptionsBuilder().returnAsMaps().build());
        Object[] items = (Object[]) doc;
        Assertions.assertEquals(2, items.length);
        Map male = (Map) items[0];
        Map female = (Map) items[1];
        Assertions.assertEquals("John", male.get("name"));
        Assertions.assertEquals("Sonya", female.get("name"));
        Assertions.assertSame(male.get("friend"), female);
        Assertions.assertSame(female.get("friend"), male);

        String json = TestUtil.toJson(doc);// Neat trick json-io does - rewrites proper json from Map of Maps input
        Object doc2 = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsMaps().build());// Read in this map of maps to JSON string and make sure it's right

        Object[] peeps1 = items;
        Object[] peeps2 = (Object[]) doc2;

        Map peep10 = (Map)peeps1[0];
        Map peep11 = (Map)peeps1[1];
        Map peep20 = (Map)peeps2[0];
        Map peep21 = (Map)peeps2[1];
        assert peep10.get("name").equals("John");
        assert peep20.get("name").equals("John");
        assert peep11.get("name").equals("Sonya");
        assert peep21.get("name").equals("Sonya");
        assert peep10.get("friend").equals(peeps1[1]);
        assert peep20.get("friend").equals(peeps2[1]);
        assert peep11.get("friend").equals(peeps1[0]);
        assert peep21.get("friend").equals(peeps2[0]);
        Assertions.assertNotSame(peeps1[0], peeps2[0]);
        Assertions.assertNotSame(peeps1[1], peeps2[1]);
    }
    /*
    @Test
    public void testGenericInfoMap()
    {
        String className = PointMap.class.getName();
        String json = "{\"@type\":\"" + className + "\",\"points\":{\"@type\":\"java.util.HashMap\",\"@keys\":[{\"x\":10,\"y\":20}],\"@items\":[{\"x\":1,\"y\":2}]}}";
        PointMap pointMap = (PointMap) TestUtil.toJava(json);
        Assertions.assertTrue(pointMap.getPoints().size() == 1);
        Point p1 = pointMap.getPoints().get(new Point(10, 20));
        Assertions.assertTrue(p1.getX() == 1 && p1.getY() == 2);

        // Comes in as a Map [[x:20, y:20]:[x:1, y:2]] when read as Map of maps.  This is due to a Point (non simple type)
        // being the key of the map.
        Map map = (Map) TestUtil.toJava(json, new ReadOptionsBuilder().returnAsMaps().build());
        Assertions.assertTrue(DefaultGroovyMethods.invokeMethod(map.points, "size", new Object[0]).equals(1));
        Map points = (Map) map.points;
        Map ten20 = (Map) points.keySet().iterator().next();
        assert ten20 instanceof Map;
        assert ten20.x.equals(10);
        assert ten20.y.equals(20);

        Map one2 = (Map) points.values().iterator().next();
        assert one2 instanceof Map;
        assert one2.x.equals(1);
        assert one2.y.equals(2);

    }

    @Test
    public void testGenericMap()
    {
        String json = "{\"traits\":{\"ui:attributes\":{\"type\":\"text\",\"label\":\"Risk Type\",\"maxlength\":\"30\"},\"v:max\":\"1\",\"v:min\":\"1\",\"v:regex\":\"[[0-9][a-z][A-Z]]\",\"db:attributes\":{\"selectColumn\":\"QR.RISK_TYPE_REF_ID\",\"table\":\"QUOTE_RISK\",\"tableAlias\":\"QR\",\"column\":\"QUOTE_ID\",\"columnName\":\"QUOTE_ID\",\"columnAlias\":\"c:riskType\",\"joinTable\":\"QUOTE\",\"joinAlias\":\"Q\",\"joinColumn\":\"QUOTE_ID\"},\"r:exists\":true,\"r:value\":\"risk\"}}";
        TestUtil.printLine("json = " + json);
        Map root = (Map) TestUtil.toJava(json);
        Map traits = (Map) root.traits;
        Map uiAttributes = (Map) traits.get("ui:attributes");
        String label = (String) uiAttributes.get("label");
        Assertions.assertEquals("Risk Type", label);
        Map dbAttributes = (Map) traits.get("db:attributes");
        String col = (String) dbAttributes.column;
        Assertions.assertEquals("QUOTE_ID", col);
        String value = (String) traits.get("r:value");
        Assertions.assertEquals("risk", value);
    }

    @Test
    public void testGenericArrayWithMap()
    {
        String json = "[{\"traits\":{\"ui:attributes\":{\"type\":\"text\",\"label\":\"Risk Type\",\"maxlength\":\"30\"},\"v:max\":\"1\",\"v:min\":\"1\",\"v:regex\":\"[[0-9][a-z][A-Z]]\",\"db:attributes\":{\"selectColumn\":\"QR.RISK_TYPE_REF_ID\",\"table\":\"QUOTE_RISK\",\"tableAlias\":\"QR\",\"column\":\"QUOTE_ID\",\"columnName\":\"QUOTE_ID\",\"columnAlias\":\"c:riskType\",\"joinTable\":\"QUOTE\",\"joinAlias\":\"Q\",\"joinColumn\":\"QUOTE_ID\"},\"r:exists\":true,\"r:value\":\"risk\"}},{\"key1\":1,\"key2\":2}]";
        TestUtil.printLine("json = " + json);
        Object[] root = (Object[]) TestUtil.toJava(json);
        Map traits = (Map) root[0];
        traits = (Map) traits.traits;
        Map uiAttributes = (Map) traits.get("ui:attributes");
        String label = (String) uiAttributes.label;
        Assertions.assertEquals("Risk Type", label);
        Map dbAttributes = (Map) traits.get("db:attributes");
        String col = (String) dbAttributes.column;
        Assertions.assertEquals("QUOTE_ID", col);
        String value = (String) traits.get("r:value");
        Assertions.assertEquals("risk", value);

        Map two = (Map) root[1];
        Assertions.assertEquals(two.size(), 2);
        Assertions.assertEquals(two.get("key1"), 1L);
        Assertions.assertEquals(two.get("key2"), 2L);
    }

    @Test
    public void testRhsPrimitiveTypesAreCoercedWhenTypeIsPresent()
    {
        // This test ensures that if @type information is written into the JSON, even if it is read
        // using jsonToMaps(), the type info will be used to correct the RHS values from default
        // JSON values of String, Integer, Double, Boolean, or null, to the proper type of the field,
        // for example, allowing the Map value to be a Short, for example, if the original field was
        // of type short.  This is a 'logical' primitive's concern.  Sub-objects are obviously created
        // as sub maps.
        Person p = new Person();
        p.setName("Sarah");
        p.setAge(33);
        p.setIq(125);
        p.setBirthYear(1981);

        String json = TestUtil.toJson(p);
        Map map = (Map) TestUtil.toJava(json, new ReadOptionsBuilder().returnAsMaps().build());

        Object age = map.age;
        assert age instanceof BigDecimal;
        assert age.equals(new BigDecimal("33"));

        Object iq = map.iq;
        assert iq instanceof BigInteger;
        assert iq.equals(125g);

        Object year = map.birthYear;
        assert year instanceof Integer;
        assert year.equals(1981);
    }

    @Test
    public void testMapOfMapsSimpleArray()
    {
        String s = "[{\"@ref\":1},{\"name\":\"Jack\",\"age\":21,\"@id\":1}]";
        Object[] list = (Object[]) TestUtil.toJava(s, new ReadOptionsBuilder().returnAsMaps().build());
        Assertions.assertTrue(list[0].equals(list[1]));
    }

    @Test
    public void testMapOfMapsWithFieldAndArray()
    {
        String s = "[\n {" name ":" Jack "," age ":21," @id ":1},\n {" @ref ":1},\n {" @ref ":2},\n {" husband ":{" @ref
        ":1}},\n {" wife ":{" @ref ":2}},\n {" attendees ":[{" @ref ":1},{" @ref ":2}]},\n {" name ":" Jill "," age
        ":18," @id ":2},\n {" witnesses ":[{" @ref ":1},{" @ref ":2}]}\n]";

        TestUtil.printLine("json=" + s);
        Object[] items = (Object[]) TestUtil.toJava(s, new ReadOptionsBuilder().returnAsMaps().build());
        Assertions.assertTrue(items.length == 8);
        Map husband = (Map) items[0];
        Map wife = (Map) items[6];
        Assertions.assertTrue(items[1].equals(husband));
        Assertions.assertTrue(items[2].equals(wife));
        Map map = (Map) items[3];
        Assertions.assertTrue(map.get("husband").equals(husband));
        map = (Map) items[4];
        Assertions.assertTrue(map.get("wife").equals(wife));
        map = (Map) items[5];
        Object[] attendees = (Object[]) map.attendees;
        Assertions.assertTrue(attendees.length == 2);
        Assertions.assertTrue(attendees[0].equals(husband));
        Assertions.assertTrue(attendees[1].equals(wife));
        map = (Map) items[7];
        Object[] witnesses = (Object[]) map.witnesses;
        Assertions.assertTrue(witnesses.length == 2);
        Assertions.assertTrue(witnesses[0].equals(husband));
        Assertions.assertTrue(witnesses[1].equals(wife));
    }

    @Test
    public void testMapOfMapsMap()
    {
        Map stuff = new TreeMap();
        stuff.put("a", "alpha");
        Object testObj = new TestObject("test object");
        stuff.put("b", testObj);
        stuff.put("c", testObj);
        stuff.put(testObj, 1.0f);
        String json = TestUtil.toJson(stuff);
        TestUtil.printLine("json=" + json);

        Map map = (Map) TestUtil.toJava(json, new ReadOptionsBuilder().returnAsMaps().build());
        TestUtil.printLine("map=" + map);
        Object aa = map.get("a");
        Map bb = (Map) map.get("b");
        Map cc = (Map) map.get("c");

        Assertions.assertTrue(aa.equals("alpha"));
        Assertions.assertTrue(bb.get("_name").equals("test object"));
        Assertions.assertTrue(bb.get("_other") == null);
        Assertions.assertTrue(DefaultGroovyMethods.equals(bb, cc));
        Assertions.assertTrue(map.size() == 4);// contains @type entry
    }

    @Test
    public void testMapOfMapsPrimitivesInArray()
    {
        Date date = new Date();
        Calendar cal = Calendar.getInstance();
        TestUtil.printLine("cal=" + cal);
        Class strClass = String.class;
        Object[] prims = new Object[]{true, Boolean.TRUE, (byte) 8, (short) 1024, 131072, 16777216, 3.14, 3.14f, "x", "hello", date, cal, strClass};
        String json = TestUtil.toJson(prims);
        TestUtil.printLine("json=" + json);
        Object[] javaObjs = (Object[]) TestUtil.toJava(json);
        Assertions.assertTrue(prims.length == javaObjs.length);

        for (int i = 0; ; i < javaObjs.length ;){
        Assertions.assertTrue(javaObjs[i].equals(prims[i]));
    }

    }

    @Test
    public void testBadInputForMapAPI()
    {
        Object o = null;
        try
        {
            o = TestUtil.toJava("[This is not quoted]", new ReadOptionsBuilder().returnAsMaps().build());
            invokeMethod("fail", new Object[0]);
        }
        catch (Exception e)
        {
            Assertions.assertTrue(e.getMessage().toLowerCase().contains("expected token: true"));
        }

        Assertions.assertTrue(o == null);
    }

    @Test
    public void testToMaps()
    {
        JsonObject map = (JsonObject) TestUtil.toJava("{\"num\":0,\"nullValue\":null,\"string\":\"yo\"}", new ReadOptionsBuilder().returnAsMaps().build());
        Assertions.assertTrue(map != null);
        Assertions.assertTrue(map.size() == 3);
        Assertions.assertTrue(map.get("num").equals(0L));
        Assertions.assertTrue(map.get("nullValue") == null);
        Assertions.assertTrue(map.get("string").equals("yo"));

        Assertions.assertTrue(map.getCol() > 0);
        Assertions.assertTrue(map.getLine() > 0);
    }

    @Test
    public void testUntyped()
    {
        String json = "{\"age\":46,\"name\":\"jack\",\"married\":false,\"salary\":125000.07,\"notes\":null,\"address1\":{\"@ref\":77},\"address2\":{\"@id\":77,\"street\":\"1212 Pennsylvania ave\",\"city\":\"Washington\"}}";
        Map map = (Map) TestUtil.toJava(json, new ReadOptionsBuilder().returnAsMaps().build());
        TestUtil.printLine("map=" + map);
        Assertions.assertTrue(map.age.equals(46L));
        Assertions.assertTrue(map.name.equals("jack"));
        Assertions.assertTrue(DefaultGroovyMethods.is(map.married, Boolean.FALSE));
        Assertions.assertTrue(map.salary.equals(125000.07d));
        Assertions.assertTrue(map.notes == null);
        Map address1 = (Map) map.address1;
        Map address2 = (Map) map.address2;
        assert DefaultGroovyMethods.is(address1, address2);
        assert address2.street.equals("1212 Pennsylvania ave");
        assert address2.city.equals("Washington");
    }

    @Test
    public void writeJsonObjectMapWithStringKeys()
    {
        String json = "{\n  \"@type\":\"java.util.LinkedHashMap\",\n  \"age\":\"36\",\n  \"name\":\"chris\"\n}";
        LinkedHashMap<String, String> map1 = new LinkedHashMap<String, String>(2);
        map1.put("age", "36");
        map1.put("name", "chris");
        Map map = map1;

        //in formatJson, the json will be parsed into a map, which checks both jsonReader and writeJsonObjectMap
        String jsonGenerated = JsonWriter.formatJson(TestUtil.toJson(map));
        jsonGenerated = jsonGenerated.replaceAll("[\\r]", "");
        assert json.equals(jsonGenerated);

        Map clone = (Map) TestUtil.toJava(jsonGenerated);
        assert DefaultGroovyMethods.equals(map, clone);
    }

    @Test
    public void writeMapWithStringKeys()
    {
        String json = "{\"@type\":\"java.util.LinkedHashMap\",\"age\":\"36\",\"name\":\"chris\"}";
        LinkedHashMap<String, String> map1 = new LinkedHashMap<String, String>(2);
        map1.put("age", "36");
        map1.put("name", "chris");
        Map map = map1;

        String jsonGenerated = TestUtil.toJson(map);
        assert json.equals(jsonGenerated);

        Map clone = (Map) TestUtil.toJava(jsonGenerated);
        assert DefaultGroovyMethods.equals(map, clone);
    }

    @Test
    public void testJsonObjectToJava()
    {
        TestObject test = new TestObject("T.O.");
        TestObject child = new TestObject("child");
        test._other = child;
        String json = TestUtil.toJson(test);
        TestUtil.printLine("json=" + json);
        Map root = (Map) TestUtil.toJava(json, new ReadOptionsBuilder().returnAsMaps().build());
        JsonReader reader = new JsonReader();
        TestObject test2 = (TestObject) reader.jsonObjectsToJava(root);
        Assertions.assertTrue(test2.equals(test));
        Assertions.assertTrue(test2._other.equals(child));
    }

    @Test
    public void testLongKeyedMap()
    {
        LinkedHashMap<Long, String> map = new LinkedHashMap<Long, String>(5);
        map.put(0L, "alpha");
        map.put(1L, "beta");
        map.put(2L, "charlie");
        map.put(3L, "delta");
        map.put(4L, "echo");
        Map simple = map;

        String json = TestUtil.toJson(simple);
        Map back = TestUtil.toJava(json);
        assert back.get(0L).equals("alpha");
        assert back.get(4L).equals("echo");
    }

    @Test
    public void testBooleanKeyedMap()
    {
        LinkedHashMap<Boolean, String> map = new LinkedHashMap<Boolean, String>(2);
        map.put(false, "alpha");
        map.put(true, "beta");
        Map simple = map;

        String json = TestUtil.toJson(simple);
        Map back = TestUtil.toJava(json);
        assert back.get((false)).equals("alpha");
        assert back.get((true)).equals("beta");
    }

    @Test
    public void testDoubleKeyedMap()
    {
        LinkedHashMap<Double, String> map = new LinkedHashMap<Double, String>(3);
        map.put(0.0d, "alpha");
        map.put(1.0d, "beta");
        map.put(2.0d, "charlie");
        Map simple = map;

        String json = TestUtil.toJson(simple);
        Map back = TestUtil.toJava(json);
        assert back.get(0.0d).equals("alpha");
        assert back.get(1.0d).equals("beta");
        assert back.get(2.0d).equals("charlie");
    }

    @Test
    public void testStringKeyedMap()
    {
        LinkedHashMap<String, Long> map = new LinkedHashMap<String, Long>(3);
        map.put("alpha", 0L);
        map.put("beta", 1L);
        map.put("charlie", 2L);
        Map simple = map;

        String json = TestUtil.toJson(simple);
        assert !json.contains(JsonObject.KEYS);
        assert !json.contains("@items");
        Map back = TestUtil.toJava(json);
        assert back.alpha.equals(0L);
        assert back.beta.equals(1L);
        assert back.charlie.equals(2L);
    }

    @Test
    public void testCircularReference()
    {
        TestObject a = new TestObject("a");
        TestObject b = new TestObject("b");
        a._other = b;
        b._other = a;

        String json = TestUtil.toJson(a);
        Map aa = (Map) TestUtil.toJava(json, new ReadOptionsBuilder().returnAsMaps().build());
        assert aa._name.equals("a");
        Map bb = (Map) aa._other;
        assert bb._name.equals("b");
        assert DefaultGroovyMethods.is(bb._other, aa);
        assert DefaultGroovyMethods.is(aa._other, bb);

        String json1 = TestUtil.toJson(aa);
        assert json.equals(json1);
    }

    @Test
    public void testRefsInMapOfMaps()
    {
        Person p = new Person();
        p.setName("Charlize Theron");
        p.setAge(39);
        p.setBirthYear(1975);
        p.setIq(140);

        Person pCopy = new Person();
        pCopy.setName("Charlize Theron");
        pCopy.setAge(39);
        pCopy.setBirthYear(1975);
        pCopy.setIq(140);

        List list = new ArrayList<Person>(Arrays.asList(p, p, pCopy));
        String json = TestUtil.toJson(list, new WriteOptionsBuilder().noTypeInfo().build());

        Object[] array = (Object[]) TestUtil.toJava(json, new ReadOptionsBuilder().returnAsMaps().build());
        assert DefaultGroovyMethods.is(array[0], array[1]);
        assert !DefaultGroovyMethods.is(array[0], array[2]);// identical object
        assert array[2].equals(array[1]);// contents match
    }

    @Test
    public void testRefToArrayInMapOfMaps()
    {
        Person p = new Person();
        p.setName("Charlize Theron");
        p.setAge(39);
        p.setBirthYear(1975);
        p.setIq(140);

        Person pCopy = new Person();
        pCopy.setName("Charlize Theron");
        pCopy.setAge(39);
        pCopy.setBirthYear(1975);
        pCopy.setIq(140);

        List list = new ArrayList<Person>(Arrays.asList(p, p, pCopy));
        List holder = new ArrayList<List<Person>>(Arrays.asList(list, list));
        String json = TestUtil.toJson(holder, new WriteOptionsBuilder().noTypeInfo().build());

        Object[] array = (Object[]) TestUtil.toJava(json, new ReadOptionsBuilder().returnAsMaps().build());
        assert array[0].equals(array[1]);// Identical array
        Map objList1 = (Map) array[0];
        List list1 = (List) objList1.get("@items");
        assert DefaultGroovyMethods.is(list1.get(0), list1.get(1));
        assert !DefaultGroovyMethods.is(list1.get(0), list1.get(2));// identical object
        assert list1.get(2).equals(list1.get(1));

        Map objList2 = (Map) array[1];
        assert DefaultGroovyMethods.is(objList2, objList1);
    }

    @Test
    public void testSkipNullFieldsMapOfMaps()
    {
        String json = "\\n{\n   " first ":" Sam ",\n   " middle ":null,\n   " last ":" Adams "\n}\n";
        Map person = (Map) TestUtil.toJava(json, new ReadOptionsBuilder().returnAsMaps().build());
        json = TestUtil.toJson(person);

        Map map = TestUtil.toJava(json);
        assert map.size() == 3;
        assert map.first.equals("Sam");
        assert map.middle == null;
        assert map.last.equals("Adams");

        json = TestUtil.toJson(person, new WriteOptionsBuilder().skipNullFields().build());

        map = TestUtil.toJava(json);
        assert map.size() == 2;
        assert map.first.equals("Sam");
        assert map.last.equals("Adams");
    }

    @Test
    public void testSkipNullFieldsTyped()
    {
        Person p = new Person();
        p.setName("Sam Adams");
        p.setAge(null);
        p.setIq(null);
        p.setBirthYear(1984);

        String json = TestUtil.toJson(p);
        Person p1 = TestUtil.toJava(json);
        assert p.getName().equals("Sam Adams");
        assert p.getAge() == null;
        assert p.getIq() == null;
        assert p.getBirthYear() == 1984;

        json = TestUtil.toJson(p, new WriteOptionsBuilder().skipNullFields().build());
        assert !json.contains("age");
        assert !json.contains("iq");
        p1 = TestUtil.toJava(json);
        assert p1.getName().equals("Sam Adams");
        assert p1.getBirthYear() == 1984;
    }
*/
    public static class PointMap
    {
        public Map<Point, Point> getPoints()
        {
            return points;
        }

        public void setPoints(Map<Point, Point> points)
        {
            this.points = points;
        }

        private Map<Point, Point> points;
    }

    public static class Person
    {
        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public BigDecimal getAge()
        {
            return age;
        }

        public void setAge(BigDecimal age)
        {
            this.age = age;
        }

        public BigInteger getIq()
        {
            return iq;
        }

        public void setIq(BigInteger iq)
        {
            this.iq = iq;
        }

        public int getBirthYear()
        {
            return birthYear;
        }

        public void setBirthYear(int birthYear)
        {
            this.birthYear = birthYear;
        }

        private String name;
        private BigDecimal age;
        private BigInteger iq;
        private int birthYear;
    }
}
