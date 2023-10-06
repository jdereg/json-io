package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

import java.awt.*
import java.util.List

import static org.assertj.core.api.Assertions.assertThat
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotSame
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertSame
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Test cases for JsonReader / JsonWriter
 *
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
class TestMapOfMaps
{
    static class PointMap
    {
        Map<Point, Point> points;
    }

    static class Person
    {
        String name
        BigDecimal age
        BigInteger iq
        int birthYear
    }

    @Test
    void testMapOfMapsWithUnknownClasses()
    {
        String json = '{"@type":"com.foo.bar.baz.Qux","_name":"Hello","_other":null}'
        Map stuff = JsonReader.jsonToJava(json)
        assert stuff.size() == 2
        assert stuff._name == 'Hello'
        assert stuff._other == null

        Map map = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        assertEquals('Hello', map._name)
        assertNull(map._other)

        // 2nd attempt
        String testObjectClassName = TestObject.class.name
        json = '{"@type":"' + testObjectClassName + '","_name":"alpha","_other":{"@type":"com.baz.Qux","_name":"beta","_other":null}}'


        Exception e = assertThrows(Exception.class, { JsonReader.jsonToJava(json)})
        assertThat(e.getMessage().toLowerCase()).contains('setting field \'_other\'')

        map = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        assertEquals('alpha', map._name)
        assertTrue(map._other instanceof JsonObject)
        JsonObject other = (JsonObject) map._other
        assertEquals('beta', other._name)
        assertNull(other._other)
    }

    @Test
    void testForwardRefNegId()
    {
        Object doc = JsonReader.jsonToJava(TestUtil.fetchResource("forwardRefNegId.json"), [(JsonReader.USE_MAPS):true] as Map)
        Object[] items = (Object[]) doc
        assertEquals(2, items.length)
        Map male = items[0]
        Map female = items[1]
        assertEquals('John', male.name)
        assertEquals('Sonya', female.name)
        assertSame(male.friend, female)
        assertSame(female.friend, male)

        String json = JsonWriter.objectToJson(doc) // Neat trick json-io does - rewrites proper json from Map of Maps input
        Object doc2 = JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)      // Read in this map of maps to JSON string and make sure it's right

        Object[] peeps1 = items
        Object[] peeps2 = (Object[]) doc2

        assert peeps1[0].name == 'John'
        assert peeps2[0].name == 'John'
        assert peeps1[1].name == 'Sonya'
        assert peeps2[1].name == 'Sonya'
        assert peeps1[0].friend == peeps1[1]
        assert peeps2[0].friend == peeps2[1]
        assert peeps1[1].friend == peeps1[0]
        assert peeps2[1].friend == peeps2[0]
        assertNotSame(peeps1[0], peeps2[0])
        assertNotSame(peeps1[1], peeps2[1])
    }

    @Test
    void testGenericInfoMap()
    {
        String className = PointMap.class.name
        String json = '{"@type":"' + className + '","points":{"@type":"java.util.HashMap","@keys":[{"x":10,"y":20}],"@items":[{"x":1,"y":2}]}}'
        PointMap pointMap = (PointMap) TestUtil.readJsonObject(json)
        assertTrue(pointMap.points.size() == 1)
        Point p1 = pointMap.points.get(new Point(10, 20))
        assertTrue(p1.x == 1 && p1.y == 2)

        // Comes in as a Map [[x:20, y:20]:[x:1, y:2]] when read as Map of maps.  This is due to a Point (non simple type)
        // being the key of the map.
        Map map = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        assertTrue(map.points.size() == 1)
        Map points = map.points;
        Map ten20 = points.keySet().iterator().next()
        assert ten20 instanceof Map;
        assert ten20.x == 10
        assert ten20.y == 20

        Map one2 = points.values().iterator().next()
        assert one2 instanceof Map;
        assert one2.x == 1
        assert one2.y == 2

    }

    @Test
    void testGenericMap()
    {
        String json = '{"traits":{"ui:attributes":{"type":"text","label":"Risk Type","maxlength":"30"},"v:max":"1","v:min":"1","v:regex":"[[0-9][a-z][A-Z]]","db:attributes":{"selectColumn":"QR.RISK_TYPE_REF_ID","table":"QUOTE_RISK","tableAlias":"QR","column":"QUOTE_ID","columnName":"QUOTE_ID","columnAlias":"c:riskType","joinTable":"QUOTE","joinAlias":"Q","joinColumn":"QUOTE_ID"},"r:exists":true,"r:value":"risk"}}'
        TestUtil.printLine("json = " + json)
        Map root = (Map) JsonReader.jsonToJava(json)
        Map traits = (Map) root.traits
        Map uiAttributes = (Map) traits['ui:attributes']
        String label = (String) uiAttributes['label']
        assertEquals("Risk Type", label)
        Map dbAttributes = (Map) traits['db:attributes']
        String col = (String) dbAttributes.column
        assertEquals("QUOTE_ID", col)
        String value = (String) traits['r:value']
        assertEquals("risk", value)
    }

    @Test
    void testGenericArrayWithMap()
    {
        String json = '[{"traits":{"ui:attributes":{"type":"text","label":"Risk Type","maxlength":"30"},"v:max":"1","v:min":"1","v:regex":"[[0-9][a-z][A-Z]]","db:attributes":{"selectColumn":"QR.RISK_TYPE_REF_ID","table":"QUOTE_RISK","tableAlias":"QR","column":"QUOTE_ID","columnName":"QUOTE_ID","columnAlias":"c:riskType","joinTable":"QUOTE","joinAlias":"Q","joinColumn":"QUOTE_ID"},"r:exists":true,"r:value":"risk"}},{"key1":1,"key2":2}]'
        TestUtil.printLine("json = " + json)
        Object[] root = (Object[]) JsonReader.jsonToJava(json)
        Map traits = (Map) root[0]
        traits = (Map) traits.traits
        Map uiAttributes = (Map) traits['ui:attributes']
        String label = (String) uiAttributes.label
        assertEquals("Risk Type", label)
        Map dbAttributes = (Map) traits['db:attributes']
        String col = (String) dbAttributes.column
        assertEquals("QUOTE_ID", col)
        String value = (String) traits['r:value']
        assertEquals("risk", value)

        Map two = (Map) root[1]
        assertEquals(two.size(), 2)
        assertEquals(two.get("key1"), 1L)
        assertEquals(two.get("key2"), 2L)
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
        Person p = new Person()
        p.name = 'Sarah'
        p.age = 33
        p.iq = 125
        p.birthYear = 1981

        String json = JsonWriter.objectToJson(p)
        Map map = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)

        def age = map.age
        assert age instanceof BigDecimal
        assert age.equals(new BigDecimal("33"))

        def iq = map.iq
        assert iq instanceof BigInteger
        assert iq.equals(125g)

        def year = map.birthYear
        assert year instanceof Integer
        assert year.equals(1981)
    }

    @Test
    void testMapOfMapsSimpleArray()
    {
        String s = '[{"@ref":1},{"name":"Jack","age":21,"@id":1}]'
        Object[] list = (Object[]) JsonReader.jsonToJava(s, [(JsonReader.USE_MAPS):true] as Map)
        assertTrue(list[0] == list[1])
    }

    @Test
    void testMapOfMapsWithFieldAndArray()
    {
        String s = '''[
 {"name":"Jack","age":21,"@id":1},
 {"@ref":1},
 {"@ref":2},
 {"husband":{"@ref":1}},
 {"wife":{"@ref":2}},
 {"attendees":[{"@ref":1},{"@ref":2}]},
 {"name":"Jill","age":18,"@id":2},
 {"witnesses":[{"@ref":1},{"@ref":2}]}
]'''

        TestUtil.printLine("json=" + s)
        Object[] items = (Object[]) JsonReader.jsonToJava(s, [(JsonReader.USE_MAPS):true] as Map)
        assertTrue(items.length == 8)
        Map husband = (Map) items[0]
        Map wife = (Map) items[6]
        assertTrue(items[1] == husband)
        assertTrue(items[2] == wife)
        Map map = (Map) items[3]
        assertTrue(map.get("husband") == husband)
        map = (Map) items[4]
        assertTrue(map.get("wife") == wife)
        map = (Map) items[5]
        Object[] attendees = map.attendees
        assertTrue(attendees.length == 2)
        assertTrue(attendees[0] == husband)
        assertTrue(attendees[1] == wife)
        map = (Map) items[7]
        Object[] witnesses = map.witnesses
        assertTrue(witnesses.length == 2)
        assertTrue(witnesses[0] == husband)
        assertTrue(witnesses[1] == wife)
    }

    @Test
    void testMapOfMapsMap()
    {
        Map stuff = new TreeMap()
        stuff.put("a", "alpha")
        Object testObj = new TestObject("test object")
        stuff.put("b", testObj)
        stuff.put("c", testObj)
        stuff.put(testObj, 1.0f)
        String json = TestUtil.getJsonString(stuff)
        TestUtil.printLine("json=" + json)

        Map map = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        TestUtil.printLine("map=" + map)
        Object aa = map.get("a")
        Map bb = (Map) map.get("b")
        Map cc = (Map) map.get("c")

        assertTrue(aa.equals("alpha"))
        assertTrue(bb.get("_name").equals("test object"))
        assertTrue(bb.get("_other") == null)
        assertTrue(bb == cc)
        assertTrue(map.size() == 4)    // contains @type entry
    }

    @Test
    void testMapOfMapsPrimitivesInArray()
    {
        Date date = new Date()
        Calendar cal = Calendar.instance
        TestUtil.printLine("cal=" + cal)
        Class strClass = String.class
        Object[] prims = [true, Boolean.TRUE, (byte)8, (short)1024, 131072, 16777216, 3.14, 3.14f, 'x', "hello", date, cal, strClass] as Object[]
        String json = TestUtil.getJsonString(prims)
        TestUtil.printLine("json=" + json)
        Object[] javaObjs = (Object[]) TestUtil.readJsonObject(json)
        assertTrue(prims.length == javaObjs.length)

        for (int i=0; i < javaObjs.length; i ++)
        {
            assertTrue(javaObjs[i].equals(prims[i]))
        }
    }

    @Test
    void testBadInputForMapAPI()
    {
        Object o = null;
        try
        {
            o = JsonReader.jsonToJava("[This is not quoted]", [(JsonReader.USE_MAPS):true] as Map)
            fail()
        }
        catch (Exception e)
        {
            assertTrue(e.message.toLowerCase().contains("expected token: true"))
        }
        assertTrue(o == null)
    }

    @Test
    void testToMaps()
    {
        JsonObject map = (JsonObject) JsonReader.jsonToJava('{"num":0,"nullValue":null,"string":"yo"}', [(JsonReader.USE_MAPS):true] as Map)
        assertTrue(map != null)
        assertTrue(map.size() == 3)
        assertTrue(map.get("num").equals(0L))
        assertTrue(map.get("nullValue") == null)
        assertTrue(map.get("string").equals("yo"))

        assertTrue(map.getCol() > 0)
        assertTrue(map.getLine() > 0)
    }

    @Test
    void testUntyped()
    {
        String json = '{"age":46,"name":"jack","married":false,"salary":125000.07,"notes":null,"address1":{"@ref":77},"address2":{"@id":77,"street":"1212 Pennsylvania ave","city":"Washington"}}'
        Map map = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        TestUtil.printLine('map=' + map)
        assertTrue(map.age.equals(46L))
        assertTrue(map.name.equals('jack'))
        assertTrue(map.married.is(Boolean.FALSE))
        assertTrue(map.salary.equals(125000.07d))
        assertTrue(map.notes == null)
        Map address1 = (Map) map.address1
        Map address2 = (Map) map.address2
        assert address1.is(address2)
        assert address2.street == '1212 Pennsylvania ave'
        assert address2.city == 'Washington'
    }

    @Test
    void writeJsonObjectMapWithStringKeys()
    {
        String json = '{\n  "@type":"java.util.LinkedHashMap",\n  "age":"36",\n  "name":"chris"\n}'
        Map map = [age:'36', name:'chris']

        //in formatJson, the json will be parsed into a map, which checks both jsonReader and writeJsonObjectMap
        String jsonGenerated = JsonWriter.formatJson(JsonWriter.objectToJson(map))
        jsonGenerated = jsonGenerated.replaceAll("[\\r]","");
        assert json == jsonGenerated

        Map clone = (Map) JsonReader.jsonToJava(jsonGenerated)
        assert map.equals(clone)
    }

    @Test
    void writeMapWithStringKeys()
    {
        String json = '{"@type":"java.util.LinkedHashMap","age":"36","name":"chris"}'
        Map map = [age:'36', name:'chris']

        String jsonGenerated = JsonWriter.objectToJson(map)
        assert json == jsonGenerated

        Map clone = (Map) JsonReader.jsonToJava(jsonGenerated)
        assert map.equals(clone)
    }

    @Test
    void testJsonObjectToJava()
    {
        TestObject test = new TestObject("T.O.")
        TestObject child = new TestObject("child")
        test._other = child
        String json = TestUtil.getJsonString(test)
        TestUtil.printLine("json=" + json)
        Map root = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        JsonReader reader = new JsonReader()
        TestObject test2 = (TestObject) reader.jsonObjectsToJava(root)
        assertTrue(test2.equals(test))
        assertTrue(test2._other.equals(child))
    }

    @Test
    void testLongKeyedMap()
    {
        Map simple = [
                0L:'alpha',
                1L:'beta',
                2L:'charlie',
                3L:'delta',
                4L:'echo'
        ]

        String json = TestUtil.getJsonString(simple)
        Map back = TestUtil.readJsonObject(json)
        assert back[0L] == 'alpha'
        assert back[4L] == 'echo'
    }

    @Test
    void testBooleanKeyedMap()
    {
        Map simple = [
                (false):'alpha',
                (true):'beta'
        ]

        String json = TestUtil.getJsonString(simple)
        Map back = TestUtil.readJsonObject(json)
        assert back[(false)] == 'alpha'
        assert back[(true)] == 'beta'
    }

    @Test
    void testDoubleKeyedMap()
    {
        Map simple = [
                0.0d:'alpha',
                1.0d:'beta',
                2.0d:'charlie',
        ]

        String json = TestUtil.getJsonString(simple)
        Map back = TestUtil.readJsonObject(json)
        assert back[0.0d] == 'alpha'
        assert back[1.0d] == 'beta'
        assert back[2.0d] == 'charlie'
    }

    @Test
    void testStringKeyedMap()
    {
        Map simple = [
                alpha:0L,
                beta:1L,
                charlie:2L
        ]

        String json = TestUtil.getJsonString(simple)
        assert !json.contains(JsonObject.KEYS)
        assert !json.contains('@items')
        Map back = TestUtil.readJsonObject(json)
        assert back.alpha == 0L
        assert back.beta == 1L
        assert back.charlie == 2L
    }

    @Test
    void testCircularReference()
    {
        TestObject a = new TestObject("a")
        TestObject b = new TestObject("b")
        a._other = b
        b._other = a

        String json = JsonWriter.objectToJson(a)
        Map aa = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        assert aa._name == 'a'
        Map bb = aa._other
        assert bb._name == 'b'
        assert bb._other.is(aa)
        assert aa._other.is(bb)

        String json1 = JsonWriter.objectToJson(aa)
        assert json == json1
    }

    @Test
    void testRefsInMapOfMaps()
    {
        Person p = new Person()
        p.name = 'Charlize Theron'
        p.age = 39
        p.birthYear = 1975
        p.iq = 140

        Person pCopy = new Person()
        pCopy.name = 'Charlize Theron'
        pCopy.age = 39
        pCopy.birthYear = 1975
        pCopy.iq = 140

        List list = [p, p, pCopy]
        String json = JsonWriter.objectToJson(list, [(JsonWriter.TYPE):false])

        Object[] array = (Object[]) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        assert array[0].is(array[1])
        assert !array[0].is(array[2])   // identical object
        assert array[2] == array[1]     // contents match
    }

    @Test
    void testRefToArrayInMapOfMaps()
    {
        Person p = new Person()
        p.name = 'Charlize Theron'
        p.age = 39
        p.birthYear = 1975
        p.iq = 140

        Person pCopy = new Person()
        pCopy.name = 'Charlize Theron'
        pCopy.age = 39
        pCopy.birthYear = 1975
        pCopy.iq = 140

        List list = [p, p, pCopy]
        List holder = [list, list]
        String json = JsonWriter.objectToJson(holder, [(JsonWriter.TYPE):false])

        Object[] array = (Object[]) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        assert array[0] == array[1]     // Identical array
        Map objList1 = (Map) array[0]
        List list1 = (List) objList1['@items']
        assert list1[0].is(list1[1])
        assert !list1[0].is(list1[2])   // identical object
        assert list1[2] == list1[1]

        Map objList2 = (Map) array[1]
        assert objList2.is(objList1)
    }

    @Test
    void testSkipNullFieldsMapOfMaps()
    {
        String json = '''\
{
   "first":"Sam",
   "middle":null,
   "last":"Adams"
}
'''
        Map person = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        json = JsonWriter.objectToJson(person)

        Map map = JsonReader.jsonToJava(json)
        assert map.size() == 3
        assert map.first == 'Sam'
        assert map.middle == null
        assert map.last == 'Adams'

        json = JsonWriter.objectToJson(person, [(JsonWriter.SKIP_NULL_FIELDS):true])

        map = JsonReader.jsonToJava(json)
        assert map.size() == 2
        assert map.first == 'Sam'
        assert map.last == 'Adams'
    }

    @Test
    void testSkipNullFieldsTyped()
    {
        Person p = new Person()
        p.name = "Sam Adams"
        p.age = null
        p.iq = null
        p.birthYear = 1984

        String json = JsonWriter.objectToJson(p)
        Person p1 = JsonReader.jsonToJava(json)
        assert p.name == 'Sam Adams'
        assert p.age == null
        assert p.iq == null
        assert p.birthYear == 1984

        json = JsonWriter.objectToJson(p, [(JsonWriter.SKIP_NULL_FIELDS):true])
        assert !json.contains('age')
        assert !json.contains('iq')
        p1 = JsonReader.jsonToJava(json)
        assert p1.name == 'Sam Adams'
        assert p1.birthYear == 1984
    }
}
