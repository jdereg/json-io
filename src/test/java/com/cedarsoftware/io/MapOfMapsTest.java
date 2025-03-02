package com.cedarsoftware.io;

import java.awt.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
class MapOfMapsTest
{
    @SuppressWarnings("unchecked")
    @Test
    void testToObjects_withUnknownClass_returnsALinkedHashMap() {
        String json = "{\"@type\":\"com.foo.bar.baz.Qux\",\"_name\":\"Hello\",\"_other\":null}";
        Map stuff = TestUtil.toObjects(json, new ReadOptionsBuilder().failOnUnknownType(false).build(), null);

        assertThat(stuff)
                .isInstanceOf(LinkedHashMap.class)
                .hasSize(2)
                .containsEntry("_name", "Hello")
                .containsEntry("_other", null);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testToObjects_asMaps_returnsALinkedHashMap()
    {
        String json = "{\"@type\":\"com.foo.bar.baz.Qux\",\"_name\":\"Hello\",\"_other\":null}";
        Map map = TestUtil.toObjects(json, new ReadOptionsBuilder()
                .returnAsJsonObjects()
                .failOnUnknownType(false)
                .build(), null);

        assertThat(map)
                .isInstanceOf(Map.class)
                .containsEntry("_name", "Hello")
                .containsEntry("_other", null);
    }

    @Test
    void testForwardRefNegId()
    {
        Object doc = TestUtil.toObjects(ClassUtilities.loadResourceAsString("references/forwardRefNegId.json"), new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        Object[] items = (Object[]) doc;
        assertEquals(2, items.length);
        Map male = (Map) items[0];
        Map female = (Map) items[1];
        assertEquals("John", male.get("name"));
        assertEquals("Sonya", female.get("name"));
        assertSame(male.get("friend"), female);
        assertSame(female.get("friend"), male);

        String json = TestUtil.toJson(doc);// Neat trick json-io does - rewrites proper json from Map of Maps input
        Object doc2 = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);// Read in this map of maps to JSON string and make sure it's right

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
        assertNotSame(peeps1[0], peeps2[0]);
        assertNotSame(peeps1[1], peeps2[1]);
    }

    @Test
    void testGenericInfoMap()
    {
        String className = PointMap.class.getName();
        String json = "{\"@type\":\"" + className + "\",\"points\":{\"@type\":\"java.util.HashMap\",\"@keys\":[{\"x\":10,\"y\":20}],\"@items\":[{\"x\":1,\"y\":2}]}}";
        PointMap pointMap = TestUtil.toObjects(json, null);
        assertEquals(1, pointMap.getPoints().size());
        Point p1 = pointMap.getPoints().get(new Point(10, 20));
        assertTrue(p1.getX() == 1 && p1.getY() == 2);

        // Comes in as a Map [[x:20, y:20]:[x:1, y:2]] when read as Map of maps.  This is due to a Point (non simple type)
        // being the key of the map.
        Map map = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        Map points = (Map) map.get("points");
        assertEquals(1, map.size());
        Map ten20 = (Map) points.keySet().iterator().next();
        assert ten20 instanceof Map;
        assert ten20.get("x").equals(10L);
        assert ten20.get("y").equals(20L);

        Map one2 = (Map) points.values().iterator().next();
        assert one2 instanceof Map;
        assert one2.get("x").equals(1L);
        assert one2.get("y").equals(2L);
    }

    @Test
    void testGenericMap()
    {
        String json = "{\"traits\":{\"ui:attributes\":{\"type\":\"text\",\"label\":\"Risk Type\",\"maxlength\":\"30\"},\"v:max\":\"1\",\"v:min\":\"1\",\"v:regex\":\"[[0-9][a-z][A-Z]]\",\"db:attributes\":{\"selectColumn\":\"QR.RISK_TYPE_REF_ID\",\"table\":\"QUOTE_RISK\",\"tableAlias\":\"QR\",\"column\":\"QUOTE_ID\",\"columnName\":\"QUOTE_ID\",\"columnAlias\":\"c:riskType\",\"joinTable\":\"QUOTE\",\"joinAlias\":\"Q\",\"joinColumn\":\"QUOTE_ID\"},\"r:exists\":true,\"r:value\":\"risk\"}}";
        TestUtil.printLine("json = " + json);
        Map root = TestUtil.toObjects(json, null);
        Map traits = (Map) root.get("traits");
        Map uiAttributes = (Map) traits.get("ui:attributes");
        String label = (String) uiAttributes.get("label");
        assertEquals("Risk Type", label);
        Map dbAttributes = (Map) traits.get("db:attributes");
        String col = (String) dbAttributes.get("column");
        assertEquals("QUOTE_ID", col);
        String value = (String) traits.get("r:value");
        assertEquals("risk", value);
    }

    @Test
    void testGenericArrayWithMap()
    {
        String json = "[{\"traits\":{\"ui:attributes\":{\"type\":\"text\",\"label\":\"Risk Type\",\"maxlength\":\"30\"},\"v:max\":\"1\",\"v:min\":\"1\",\"v:regex\":\"[[0-9][a-z][A-Z]]\",\"db:attributes\":{\"selectColumn\":\"QR.RISK_TYPE_REF_ID\",\"table\":\"QUOTE_RISK\",\"tableAlias\":\"QR\",\"column\":\"QUOTE_ID\",\"columnName\":\"QUOTE_ID\",\"columnAlias\":\"c:riskType\",\"joinTable\":\"QUOTE\",\"joinAlias\":\"Q\",\"joinColumn\":\"QUOTE_ID\"},\"r:exists\":true,\"r:value\":\"risk\"}},{\"key1\":1,\"key2\":2}]";
        TestUtil.printLine("json = " + json);
        Object[] root = TestUtil.toObjects(json, null);
        Map traits = (Map) root[0];
        traits = (Map) traits.get("traits");
        Map uiAttributes = (Map) traits.get("ui:attributes");
        String label = (String) uiAttributes.get("label");
        assertEquals("Risk Type", label);
        Map dbAttributes = (Map) traits.get("db:attributes");
        String col = (String) dbAttributes.get("column");
        assertEquals("QUOTE_ID", col);
        String value = (String) traits.get("r:value");
        assertEquals("risk", value);

        Map two = (Map) root[1];
        assertEquals(two.size(), 2);
        assertEquals(two.get("key1"), 1L);
        assertEquals(two.get("key2"), 2L);
    }

    @Test
    void testRhsPrimitiveTypesAreCoercedWhenTypeIsPresent()
    {
        // This test ensures that if @type information is written into the JSON, even if it is read
        // using jsonToMaps(), the type info will be used to correct the RHS values from default
        // JSON values of String, Integer, Double, Boolean, or null, to the proper type of the field,
        // for example, allowing the Map value to be a Short, for example, if the original field was
        // of type short.  This is a 'logical' primitive's concern.  Sub-objects are obviously created
        // as sub maps.
        Person p = new Person();
        p.setName("Sarah");
        p.setAge(new BigDecimal("33"));
        p.setIq(new BigInteger("125"));
        p.setBirthYear(1981);

        String json = TestUtil.toJson(p);
        Map map = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);

        Object age = map.get("age");
        assert age instanceof BigDecimal;
        assert ((BigDecimal) age).intValue() == 33;

        Object iq = map.get("iq");
        assert iq instanceof BigInteger;
        assert ((BigInteger) iq).intValue() == 125;

        Object year = map.get("birthYear");
        assert year instanceof Integer;
        assert year.equals(1981);
    }

    @Test
    void testMapOfMapsSimpleArray()
    {
        String s = "[{\"@ref\":1},{\"name\":\"Jack\",\"age\":21,\"@id\":1}]";
        Object[] list = TestUtil.toObjects(s, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assertTrue(list[0].equals(list[1]));
    }

    @Test
    void testMapOfMapsWithFieldAndArray()
    {
        String s = "[{\"name\":\"Jack\",\"age\":21,\"@id\":1},{\"@ref\":1},{\"@ref\":2},{\"husband\":{\"@ref\"" +
                ":1}},{\"wife\":{\"@ref\":2}},{\"attendees\":[{\"@ref\":1},{\"@ref\":2}]},{\"name\":\"Jill\",\"age\"" +
                ":18,\"@id\":2},{\"witnesses\":[{\"@ref\":1},{\"@ref\":2}]}]";

        TestUtil.printLine("json=" + s);
        Object[] items = TestUtil.toObjects(s, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assertEquals(8, items.length);
        Map husband = (Map) items[0];
        Map wife = (Map) items[6];
        assertEquals(items[1], husband);
        assertEquals(items[2], wife);
        Map map = (Map) items[3];
        assertEquals(map.get("husband"), husband);
        map = (Map) items[4];
        assertEquals(map.get("wife"), wife);
        map = (Map) items[5];
        Object[] attendees = (Object[]) map.get("attendees");
        assertEquals(2, attendees.length);
        assertEquals(attendees[0], husband);
        assertEquals(attendees[1], wife);
        map = (Map) items[7];
        Object[] witnesses = (Object[]) map.get("witnesses");
        assertEquals(2, witnesses.length);
        assertEquals(witnesses[0], husband);
        assertEquals(witnesses[1], wife);
    }

    @Test
    void testMapOfMapsMap()
    {
        Map<Object, Object> stuff = new TreeMap<>();
        stuff.put("a", "alpha");
        Object testObj = new TestObject("test object");
        stuff.put("b", testObj);
        stuff.put("c", testObj);
        stuff.put(testObj, 1.0f);
        String json = TestUtil.toJson(stuff);

        Map<Object, Object> map = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);

        Object aa = map.get("a");
        Map bb = (Map) map.get("b");
        Map cc = (Map) map.get("c");

        assertEquals("alpha", aa);
        assertEquals("test object", bb.get("_name"));
        assertNull(bb.get("_other"));
        assertEquals(bb, cc);
        assertEquals(4, map.size());// contains a, b, c, and TestObject as keys
    }

    @Test
    void testMapOfMapsSet()
    {
        Comparator<Object> mixedTypeComparator = (o1, o2) -> {
            if (o1 == o2) {
                return 0;
            }
            if (o1 == null) {
                return -1;
            }
            if (o2 == null) {
                return 1;
            }

            // Compare based on class names to group different types together
            String className1 = o1.getClass().getName();
            String className2 = o2.getClass().getName();
            int classComparison = className1.compareTo(className2);
            if (classComparison != 0) {
                return classComparison;
            }

            // If both objects are of the same type and implement Comparable, use their natural ordering
            if (o1 instanceof Comparable && o2 instanceof Comparable) {
                try {
                    return ((Comparable<Object>) o1).compareTo(o2);
                } catch (ClassCastException e) {
                    // If objects are not mutually comparable, fallback to class name comparison
                    return classComparison;
                }
            }

            // Fallback to class name comparison if natural ordering is not possible
            return classComparison;
        };
        Set<Object> stuff = new TreeSet<>(mixedTypeComparator);
        stuff.add("a");
        Object testObj = new TestObject("b");
        stuff.add(testObj);
        stuff.add("c");
        String json = TestUtil.toJson(stuff);

        Map map = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        Object[] items = ((JsonObject)map).getItems();
        assert items.length == 3;
        assertEquals(((JsonObject)items[0]).get("_name"), "b");
        assertEquals("a", items[1]);
        assertEquals("c", items[2]);
    }

    @Test
    void testReturnJsonObjectsDoesNotConvertRootWhenRootTypeIsNull()
    {
        TestObject kid = new TestObject("kid");
        Object testObj = new TestObject("test object", kid);
        String json = TestUtil.toJson(testObj);
        
        Map map = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assert map instanceof JsonObject;   // Not TestObject
        assertEquals("test object", map.get("_name"));
        assert map.get("_other") instanceof Map;

        assertThrows(JsonIoException.class, () -> {TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), TestObject.class); });

        TestObject testObj1 = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);
        assert DeepEquals.deepEquals(testObj, testObj1);
        assert testObj1._other instanceof TestObject;

        testObj1 = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), TestObject.class);
        assert DeepEquals.deepEquals(testObj, testObj1);
        assert testObj1._other instanceof TestObject;
    }

    @Test
    void testMapOfMapsPrimitivesInArray()
    {
        Date date = new Date();
        Calendar cal = Calendar.getInstance();
        TestUtil.printLine("cal=" + cal);
        Class<?> strClass = String.class;
        Object[] prims = new Object[]{true, Boolean.TRUE, (byte) 8, (short) 1024, 131072, 16777216, 3.14, 3.14f, "x", "hello", date, cal, strClass};
        String json = TestUtil.toJson(prims);
        TestUtil.printLine("json=" + json);
        Object[] javaObjs = TestUtil.toObjects(json, null);
        assertEquals(prims.length, javaObjs.length);

        for (int i = 0; i < javaObjs.length ; i++)
        {
            assertEquals(javaObjs[i], prims[i]);
        }
    }

    @Test
    void testBadInputForMapAPI()
    {
        Object o = null;
        try
        {
            o = TestUtil.toObjects("[This is not quoted]", new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
            fail();
        }
        catch (Exception e)
        {
            assert e.getMessage().toLowerCase().contains("expected token: true");
        }

        assert o == null;
    }

    @Test
    void testToMaps()
    {
        JsonObject map = TestUtil.toObjects("{\"num\":0,\"nullValue\":null,\"string\":\"yo\"}", new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assertNotNull(map);
        assertEquals(3, map.size());
        assertEquals(0L, map.get("num"));
        assertNull(map.get("nullValue"));
        assertEquals("yo", map.get("string"));

        assertTrue(map.getCol() > 0);
        assertTrue(map.getLine() > 0);
    }

    @Test
    void testUntyped()
    {
        String json = "{\"age\":46,\"name\":\"jack\",\"married\":false,\"salary\":125000.07,\"notes\":null,\"address1\":{\"@ref\":77},\"address2\":{\"@id\":77,\"street\":\"1212 Pennsylvania ave\",\"city\":\"Washington\"}}";
        Map map = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        TestUtil.printLine("map=" + map);
        assertEquals(46L, map.get("age"));
        assertEquals("jack", map.get("name"));
        assert map.get("married").equals(false);
        assertEquals(125000.07d, map.get("salary"));
        assertNull(map.get("notes"));
        Map address1 = (Map) map.get("address1");
        Map address2 = (Map) map.get("address2");
        assert address1 == address2;
        assert address2.get("street").equals("1212 Pennsylvania ave");
        assert address2.get("city").equals("Washington");
    }

    @Test
    void writeJsonObjectMapWithStringKeys()
    {
        String json = "{\n  \"@type\":\"LinkedHashMap\",\n  \"age\":\"36\",\n  \"name\":\"chris\"\n}";
        Map<String, String> map1 = new LinkedHashMap<>(2);
        map1.put("age", "36");
        map1.put("name", "chris");
        Map map = map1;

        //in formatJson, the json will be parsed into a map, which checks both jsonReader and writeJsonObjectMap
        String jsonGenerated = JsonIo.formatJson(TestUtil.toJson(map));
        jsonGenerated = jsonGenerated.replaceAll("[\\r]", "");
        assert json.equals(jsonGenerated);

        Map clone = TestUtil.toObjects(jsonGenerated, null);
        assert map.equals(clone);
        assert DeepEquals.deepEquals(map, clone);
    }

    @Test
    void writeMapWithStringKeys()
    {
        String json = "{\"@type\":\"LinkedHashMap\",\"age\":\"36\",\"name\":\"chris\"}";
        Map<String, String> map1 = new LinkedHashMap<>(2);
        map1.put("age", "36");
        map1.put("name", "chris");
        Map map = map1;

        String jsonGenerated = TestUtil.toJson(map);
        assert json.equals(jsonGenerated);

        Map clone = TestUtil.toObjects(jsonGenerated, null);
        assert map.equals(clone);
    }

    @Test
    void testJsonObjectToJava()
    {
        TestObject test = new TestObject("T.O.");
        TestObject child = new TestObject("child");
        test._other = child;
        String json = TestUtil.toJson(test);
        TestUtil.printLine("json=" + json);
        JsonObject root = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        TestObject test2 = JsonIo.toObjects(root, new ReadOptionsBuilder().build(), TestObject.class);
        assertEquals(test2, test);
        assertEquals(test2._other, child);
    }

    @Test
    void testLongKeyedMap()
    {
        Map<Long, String> map = new LinkedHashMap<>(5);
        map.put(0L, "alpha");
        map.put(1L, "beta");
        map.put(2L, "charlie");
        map.put(3L, "delta");
        map.put(4L, "echo");
        Map simple = map;

        String json = TestUtil.toJson(simple);
        Map back = TestUtil.toObjects(json, null);
        assert back.get(0L).equals("alpha");
        assert back.get(4L).equals("echo");
    }

    @Test
    void testBooleanKeyedMap()
    {
        Map<Boolean, String> map = new LinkedHashMap<>(2);
        map.put(false, "alpha");
        map.put(true, "beta");
        Map simple = map;

        String json = TestUtil.toJson(simple);
        Map back = TestUtil.toObjects(json, null);
        assert back.get((false)).equals("alpha");
        assert back.get((true)).equals("beta");
    }

    @Test
    void testDoubleKeyedMap()
    {
        Map<Double, String> map = new LinkedHashMap<>(3);
        map.put(0.0d, "alpha");
        map.put(1.0d, "beta");
        map.put(2.0d, "charlie");
        Map simple = map;

        String json = TestUtil.toJson(simple);
        Map back = TestUtil.toObjects(json, null);
        assert back.get(0.0d).equals("alpha");
        assert back.get(1.0d).equals("beta");
        assert back.get(2.0d).equals("charlie");
    }

    @Test
    void testStringKeyedMap()
    {
        Map<String, Long> map = new LinkedHashMap<>(3);
        map.put("alpha", 0L);
        map.put("beta", 1L);
        map.put("charlie", 2L);
        Map simple = map;

        String json = TestUtil.toJson(simple);
        assert !json.contains(JsonObject.KEYS);
        assert !json.contains("@items");
        Map back = TestUtil.toObjects(json, null);
        assert back.get("alpha").equals(0L);
        assert back.get("beta").equals(1L);
        assert back.get("charlie").equals(2L);
    }

    @Test
    void testCircularReference()
    {
        TestObject a = new TestObject("a");
        TestObject b = new TestObject("b");
        a._other = b;
        b._other = a;

        String json = TestUtil.toJson(a);
        Map aa = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assert aa.get("_name").equals("a");
        Map bb = (Map) aa.get("_other");
        assert bb.get("_name").equals("b");

        assert bb.get("_other").equals(aa);
        assert aa.get("_other").equals(bb);
        
        String json1 = TestUtil.toJson(aa);
        assert json.equals(json1);
    }

    @Test
    void testRefsInMapOfMaps()
    {
        Person p = new Person();
        p.setName("Charlize Theron");
        p.setAge(new BigDecimal("39"));
        p.setBirthYear(1975);
        p.setIq(new BigInteger("140"));

        Person pCopy = new Person();
        pCopy.setName("Charlize Theron");
        pCopy.setAge(new BigDecimal("39"));
        pCopy.setBirthYear(1975);
        pCopy.setIq(new BigInteger("140"));
        List<Person> list = listOf(p, p, pCopy);

        String json = TestUtil.toJson(list, new WriteOptionsBuilder().showTypeInfoNever().build());
        Object[] array = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);

        assert array[0] == array[1];    // same instance
        assert array[0] != array[2];    // not same instance
        assert DeepEquals.deepEquals(array[2], array[1]);  // contents match even though not same instance
    }

    @Test
    void testRefToArrayInMapOfMaps()
    {
        Person p = new Person();
        p.setName("Charlize Theron");
        p.setAge(new BigDecimal("39"));
        p.setBirthYear(1975);
        p.setIq(new BigInteger("140"));

        Person pCopy = new Person();
        pCopy.setName("Charlize Theron");
        pCopy.setAge(new BigDecimal("39"));
        pCopy.setBirthYear(1975);
        pCopy.setIq(new BigInteger("140"));

        List<Person> list = listOf(p, p, pCopy);
        List<List<Person>> holder = listOf(list, list);

        String json = TestUtil.toJson(holder, new WriteOptionsBuilder().showTypeInfoNever().build());
        Object[] array = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        
        assert array[0] == array[1];                // Same instance of List
        JsonObject objList1 = (JsonObject) array[0];
        assert objList1.isArray();
        Object[] list1 = objList1.getItems();
        assert list1[0] == list1[1];                // Same Person instance
        assert list1[0] != list1[2];                // Not same Person instance
        assert DeepEquals.deepEquals(list1[2], list1[1]);  // Although difference instance, same contents

        Map objList2 = (Map) array[1];
        assert objList1 == objList2;                // Same JsonObject instance
    }

    @Test
    void testSkipNullFieldsMapOfMaps()
    {
        String json = "{\"first\":\"Sam\",\"middle\":null,\"last\":\"Adams\"}";
        Map person = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), Map.class);
        json = TestUtil.toJson(person);

        Map map = TestUtil.toObjects(json, null);
        assert map.size() == 3;
        assert map.get("first").equals("Sam");
        assert map.get("middle") == null;
        assert map.get("last").equals("Adams");

        json = TestUtil.toJson(person, new WriteOptionsBuilder().skipNullFields(true).build());

        map = TestUtil.toObjects(json, null);
        assert map.size() == 2;
        assert map.get("first").equals("Sam");
        assert map.get("last").equals("Adams");
    }

    @Test
    void testSkipNullFieldsMapOfMaps2()
    {
        String json = "{\"first\":\"Sam\",\"middle\":null,\"last\":\"Adams\"}";
        Map person = TestUtil.toObjects(json, new ReadOptionsBuilder()
                .returnAsJsonObjects()
                .build(), Map.class);
        json = TestUtil.toJson(person);

        Map map = TestUtil.toObjects(json, null);
        assert map.size() == 3;
        assert map.get("first").equals("Sam");
        assert map.get("middle") == null;
        assert map.get("last").equals("Adams");
    }

    @Test
    void testSkipNullFieldsTyped()
    {
        Person p = new Person();
        p.setName("Sam Adams");
        p.setAge(null);
        p.setIq(null);
        p.setBirthYear(1984);

        String json = TestUtil.toJson(p);
        Person p1 = TestUtil.toObjects(json, null);
        assert p1.getName().equals("Sam Adams");
        assert p1.getAge() == null;
        assert p1.getIq() == null;
        assert p1.getBirthYear() == 1984;

        json = TestUtil.toJson(p, new WriteOptionsBuilder().skipNullFields(true).build());
        assert !json.contains("age");
        assert !json.contains("iq");
        p1 = TestUtil.toObjects(json, null);
        assert p1.getName().equals("Sam Adams");
        assert p1.getBirthYear() == 1984;
    }

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
