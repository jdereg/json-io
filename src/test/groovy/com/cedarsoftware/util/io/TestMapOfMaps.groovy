package com.cedarsoftware.util.io

import org.junit.Test

import java.awt.Point

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotSame
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertSame
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

/**
 * Test cases for JsonReader / JsonWriter
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License")
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
    void testMapOfMapsWithUnknownClasses() throws Exception
    {
        String json = '{"@type":"com.foo.bar.baz.Qux","_name":"Hello","_other":null}'

        try
        {
            JsonReader.jsonToJava(json)
            fail()
        }
        catch (IOException e)
        {
            assertTrue(e.message.toLowerCase().contains("class"))
            assertTrue(e.message.toLowerCase().contains("not"))
            assertTrue(e.message.toLowerCase().contains("created"))
        }
        Map map = JsonReader.jsonToMaps(json)
        assertEquals('Hello', map._name)
        assertNull(map._other)

        // 2nd attempt
        String testObjectClassName = TestObject.class.name
        json = '{"@type":"' + testObjectClassName + '","_name":"alpha","_other":{"@type":"com.baz.Qux","_name":"beta","_other":null}}'

        try
        {
            JsonReader.jsonToJava(json)
            fail()
        }
        catch (IOException e)
        {
            assertTrue(e.message.toLowerCase().contains('ioexception setting field'))
        }

        map = JsonReader.jsonToMaps(json)
        assertEquals('alpha', map._name)
        assertTrue(map._other instanceof JsonObject)
        JsonObject other = (JsonObject) map._other
        assertEquals('beta', other._name)
        assertNull(other._other)
    }

    @Test
    void testForwardRefNegId() throws Exception
    {
        Map doc = JsonReader.jsonToMaps(TestUtil.getResource("forwardRefNegId.json"))
        Object[] items = doc['@items']
        assertEquals(2, items.length)
        Map male = items[0]
        Map female = items[1]
        assertEquals('John', male.name)
        assertEquals('Sonya', female.name)
        assertSame(male.friend, female)
        assertSame(female.friend, male)

        String json = JsonWriter.objectToJson(doc) // Neat trick json-io does - rewrites proper json from Map of Maps input
        Map doc2 = JsonReader.jsonToMaps(json)      // Read in this map of maps to JSON string and make sure it's right

        Object[] peeps1 = doc['@items']
        Object[] peeps2 = doc2['@items']

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
    public void testGenericInfoMap() throws Exception
    {
        String className = PointMap.class.name
        String json = '{"@type":"' + className + '","points":{"@type":"java.util.HashMap","@keys":[{"x":10,"y":20}],"@items":[{"x":1,"y":2}]}}';
        PointMap pointMap = (PointMap) TestUtil.readJsonObject(json)
        assertTrue(pointMap.points.size() == 1)
        Point p1 = pointMap.points.get(new Point(10, 20))
        assertTrue(p1.x == 1 && p1.y == 2)

        Map map = JsonReader.jsonToMaps(json)
        println map
        println map.size()
    }

    @Test
    public void testGenericMap() throws Exception
    {
        String json = '{"traits":{"ui:attributes":{"type":"text","label":"Risk Type","maxlength":"30"},"v:max":"1","v:min":"1","v:regex":"[[0-9][a-z][A-Z]]","db:attributes":{"selectColumn":"QR.RISK_TYPE_REF_ID","table":"QUOTE_RISK","tableAlias":"QR","column":"QUOTE_ID","columnName":"QUOTE_ID","columnAlias":"c:riskType","joinTable":"QUOTE","joinAlias":"Q","joinColumn":"QUOTE_ID"},"r:exists":true,"r:value":"risk"}}';
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
    public void testGenericArrayWithMap() throws Exception
    {
        String json = '[{"traits":{"ui:attributes":{"type":"text","label":"Risk Type","maxlength":"30"},"v:max":"1","v:min":"1","v:regex":"[[0-9][a-z][A-Z]]","db:attributes":{"selectColumn":"QR.RISK_TYPE_REF_ID","table":"QUOTE_RISK","tableAlias":"QR","column":"QUOTE_ID","columnName":"QUOTE_ID","columnAlias":"c:riskType","joinTable":"QUOTE","joinAlias":"Q","joinColumn":"QUOTE_ID"},"r:exists":true,"r:value":"risk"}},{"key1":1,"key2":2}]';
        TestUtil.printLine("json = " + json)
        Object[] root = (Object[]) JsonReader.jsonToJava(json)
        Map traits = (Map) root[0];
        traits = (Map) traits.traits
        Map uiAttributes = (Map) traits['ui:attributes']
        String label = (String) uiAttributes.label
        assertEquals("Risk Type", label)
        Map dbAttributes = (Map) traits['db:attributes']
        String col = (String) dbAttributes.column
        assertEquals("QUOTE_ID", col)
        String value = (String) traits['r:value']
        assertEquals("risk", value)

        Map two = (Map) root[1];
        assertEquals(two.size(), 2)
        assertEquals(two.get("key1"), 1L)
        assertEquals(two.get("key2"), 2L)
    }

    @Test
    void testFoo()
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
        println json
        JsonObject map = JsonReader.jsonToMaps(json)

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
}
