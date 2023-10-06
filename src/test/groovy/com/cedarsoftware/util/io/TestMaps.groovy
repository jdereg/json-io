package com.cedarsoftware.util.io

import com.cedarsoftware.util.DeepEquals
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import java.awt.*
import java.util.List
import java.util.concurrent.ConcurrentHashMap

import static org.assertj.core.api.Fail.fail
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertThrows
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
class TestMaps
{
    static class MapArrayKey
    {
        Map<Object[], String> content;
    }

    static class MapSetKey
    {
        Map<Set<Point>, String> content
    }

    static class ParameterizedMap
    {
        Map<String, Map<String, Point>> content = new LinkedHashMap<>()
    }

    static class AssignToList
    {
        Map assignTo
    }

    static class SimpleMapTest
    {
        HashMap map = new HashMap()
    }

    private static class ManyMaps implements Serializable
    {
        private HashMap _strings_a
        private Map _strings_b
        private Map _strings_c
        private Map _testobjs_a
        private Map _map_col
        private Map _map_col_2
        private Map _map_col_3
        private Map _map_obj
        private Map _map_con
        private TreeMap _typedMap

        private void init()
        {
            _strings_a = new HashMap()
            _strings_b = new HashMap()
            _strings_c = null;
            _testobjs_a = new TreeMap()
            _map_col = new HashMap()
            _map_col_2 = new TreeMap()
            _map_col_3 = new HashMap()
            _map_obj = new HashMap()
            _map_con = new ConcurrentHashMap()

            _strings_a.put("woods", "tiger")
            _strings_a.put("mickleson", "phil")
            _strings_a.put("garcia", "sergio")

            _testobjs_a.put(new TestObject("one"), new TestObject("alpha"))
            _testobjs_a.put(new TestObject("two"), new TestObject("bravo"))

            List l = new LinkedList()
            l.add("andromeda")
            _map_col.put([new TestObject("earth"), new TestObject("jupiter")] as TestObject[], l)
            _map_col_2.put("cat", ["tiger", "lion", "cheetah", "jaguar"] as Object[])
            _map_col_3.put(["composite", "key"] as Object[], "value")

            _map_obj.put(new Integer(99), new Double(0.123d))
            _map_obj.put(null, null)

            _map_con.put(new TestObject("alpha"), new TestObject("one"))
            _map_con.put(new TestObject("bravo"), new TestObject("two"))

            _typedMap = new TreeMap()
            _typedMap.put("a", "alpha")
            _typedMap.put("b", "bravo")
        }
    }

    @Test
    void testMap()
    {
        ManyMaps obj = new ManyMaps()
        obj.init()
        String jsonOut = TestUtil.getJsonString(obj)
        TestUtil.printLine(jsonOut)

        ManyMaps root = (ManyMaps) TestUtil.readJsonObject(jsonOut)
        assertMap(root)
    }

    private static void assertMap(ManyMaps root)
    {
        assertTrue(root._strings_a.size() == 3)
        assertTrue(root._strings_a.get("woods").equals("tiger"))
        assertTrue(root._strings_a.get("mickleson").equals("phil"))
        assertTrue(root._strings_a.get("garcia").equals("sergio"))
        assertTrue(root._strings_b.isEmpty())
        assertNull(root._strings_c)

        assertTrue(root._testobjs_a.size() == 2)
        assertTrue(root._testobjs_a.get(new TestObject("one")).equals(new TestObject("alpha")))
        assertTrue(root._testobjs_a.get(new TestObject("two")).equals(new TestObject("bravo")))

        assertTrue(root._map_col.size() == 1)
        Iterator i = root._map_col.keySet().iterator()
        TestObject[] key = (TestObject[]) i.next()
        key[0]._name.equals("earth")
        key[1]._name.equals("jupiter")
        i = root._map_col.values().iterator()
        Collection list = (Collection) i.next()
        list.contains("andromeda")

        // Check value-side of Map with Object[] (special case because Object[]'s @type is never written)
        Object[] catTypes = (Object[]) root._map_col_2.get("cat")
        assertTrue(catTypes[0].equals("tiger"))
        assertTrue(catTypes[1].equals("lion"))
        assertTrue(catTypes[2].equals("cheetah"))
        assertTrue(catTypes[3].equals("jaguar"))

        assertTrue(root._map_col_3.size() == 1)
        i = root._map_col_3.keySet().iterator()
        Object[] key_a = (Object[]) i.next()
        key_a[0].equals("composite")
        key_a[1].equals("key")
        String value = (String) root._map_col_3.get(key_a)
        assertTrue("value".equals(value))

        assertTrue(root._map_obj.size() == 2)
        assertTrue(root._map_obj.get(new Integer(99)).equals(new Double(0.123d)))
        assertNull(root._map_obj.get(null))

        assertTrue(root._map_con.size() == 2)
        assertTrue(root._map_con instanceof ConcurrentHashMap)
        i = root._map_con.entrySet().iterator()
        while (i.hasNext())
        {
            Map.Entry e = (Map.Entry) i.next()
            TestObject key1 = (TestObject) e.key
            TestObject value1 = (TestObject) e.value
            if (key1.equals(new TestObject("alpha")))
            {
                assertTrue(value1.getName().equals("one"))
            }
            else if (key1.equals(new TestObject("bravo")))
            {
                assertTrue(value1.getName().equals("two"))
            }
            else
            {
                fail("Unknown Key");
            }
        }

        assertTrue(root._typedMap != null)
        assertTrue(root._typedMap.size() == 2)
        assertTrue(root._typedMap.get("a").equals("alpha"))
        assertTrue(root._typedMap.get("b").equals("bravo"))
    }

    @Test
    void testReconstituteMap()
    {
        ManyMaps testMap = new ManyMaps()
        testMap.init()
        String json0 = TestUtil.getJsonString(testMap)
        TestUtil.printLine("json0=" + json0)
        Map testMap2 = (Map) JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS):true] as Map)

        String json1 = TestUtil.getJsonString(testMap2)
        TestUtil.printLine("json1=" + json1)

        ManyMaps testMap3 = (ManyMaps) TestUtil.readJsonObject(json1)
        assertMap(testMap3)   // Re-written from Map of Maps and re-loaded correctly
        assertTrue(json0.equals(json1))
    }

    @Test
    void testMap2()
    {
        TestObject a = new TestObject("A")
        TestObject b = new TestObject("B")
        TestObject c = new TestObject("C")
        a._other = b;
        b._other = c;
        c._other = a;

        Map map = new HashMap()
        map.put(a, b)
        String json = TestUtil.getJsonString(map)
        TestUtil.printLine("json = " + json)
        map = (Map) TestUtil.readJsonObject(json)
        assertTrue(map != null)
        assertTrue(map.size() == 1)
        TestObject bb = (TestObject) map.get(new TestObject("A"))
        assertTrue(bb._other.equals(new TestObject("C")))
        TestObject aa = (TestObject) map.keySet().toArray()[0];
        assertTrue(aa._other == bb)
    }

    @Test
    void testMap3()
    {
        Map map = new HashMap()
        map.put("a", "b")
        String json = TestUtil.getJsonString(map)
        TestUtil.printLine("json = " + json)
        map = (Map) TestUtil.readJsonObject(json)
        assertTrue(map != null)
        assertTrue(map.size() == 1)
    }

    @Test
    void testMapArrayKey()
    {
        MapArrayKey m = new MapArrayKey()
        m.content = new LinkedHashMap<>()
        Object[] arrayA = new Object[2];
        arrayA[0] = new Point(1, 2)
        arrayA[1] = new Point(10, 20)
        m.content.put(arrayA, "foo")
        Object[] arrayB = new Object[2];
        arrayB[0] = new Point(3, 4)
        arrayB[1] = new Point(30, 40)
        m.content.put(arrayB, "bar")
        String json = TestUtil.getJsonString(m)
        MapArrayKey x = (MapArrayKey) TestUtil.readJsonObject(json)

        Iterator<Map.Entry<Object[], String>> i = x.content.entrySet().iterator()
        Map.Entry<Object[], String> entry = i.next()

        assertEquals("foo", entry.value)
        arrayA = entry.key
        assertEquals(new Point(1, 2), arrayA[0])
        assertEquals(new Point(10, 20), arrayA[1])

        entry = i.next()
        assertEquals("bar", entry.value)
        arrayB = entry.key
        assertEquals(new Point(3, 4), arrayB[0])
        assertEquals(new Point(30, 40), arrayB[1])
    }

    @Test
    void testMapSetKey()
    {
        MapSetKey m = new MapSetKey()
        m.content = new LinkedHashMap<>()
        Set<Point> setA = new LinkedHashSet<>()
        setA.add(new Point(1, 2))
        setA.add(new Point(10, 20))
        m.content.put(setA, "foo")
        Set<Point> setB = new LinkedHashSet<>()
        setB.add(new Point(3, 4))
        setB.add(new Point(30, 40))
        m.content.put(setB, "bar")
        String json = TestUtil.getJsonString(m)
        MapSetKey x = (MapSetKey) TestUtil.readJsonObject(json)

        assertEquals("foo", x.content.get(setA))
        assertEquals("bar", x.content.get(setB))

        m = new MapSetKey()
        m.content = new LinkedHashMap<>()
        m.content.put(null, null)
        json = TestUtil.getJsonString(m)
        x = (MapSetKey) TestUtil.readJsonObject(json)
        assertNull(x.content.get(null))

        m = new MapSetKey()
        m.content = new LinkedHashMap<>()
        m.content.put(new LinkedHashSet(), "Fargo")
        json = TestUtil.getJsonString(m)
        x = (MapSetKey) TestUtil.readJsonObject(json)
        assertEquals("Fargo", x.content.get(new LinkedHashSet<Point>()))
    }

    @Test
    void testMapToMapCompatibility()
    {
        String json0 = '{"rows":[{"columns":[{"name":"FOO","value":"9000"},{"name":"VON","value":"0001-01-01"},{"name":"BAR","value":"0001-01-01"}]},{"columns":[{"name":"FOO","value":"9713"},{"name":"VON","value":"0001-01-01"},{"name":"BAR","value":"0001-01-01"}]}],"selectedRows":"110"}'
        Map root = (Map) JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS):true] as Map)
        String json1 = TestUtil.getJsonString(root)
        Map root2 = (Map) JsonReader.jsonToJava(json1, [(JsonReader.USE_MAPS):true] as Map)
        assertTrue(DeepEquals.deepEquals(root, root2))

        // Will be different because @keys and @items get inserted during processing
        TestUtil.printLine("json0=" + json0)
        TestUtil.printLine("json1=" + json1)
    }

    @Test
    void testMapWithAtType()
    {
        AssignToList atl = new AssignToList()
        String json = '{"@id":1,"@type":"java.util.LinkedHashMap","@keys":["1000004947","0000020985","0000029443","0000020994"],"@items":["Me","Fox, James","Renewals, CORE","Gade, Raja"]}'
        Map assignTo = (Map) JsonReader.jsonToJava(json)
        assert assignTo instanceof LinkedHashMap
        atl.assignTo = assignTo;
        json = JsonWriter.objectToJson(atl)
        TestUtil.printLine(json)
    }

    @Test
    void testMapWithParameterizedTypes()
    {
        String json = '{"@type":"' + ParameterizedMap.class.getName() + '", "content":{"foo":{"one":{"x":1,"y":2},"two":{"x":10,"y":20}},"bar":{"ten":{"x":3,"y":4},"twenty":{"x":30,"y":40}}}}'
        ParameterizedMap pCol = (ParameterizedMap) JsonReader.jsonToJava(json)
        Map<String, Point> points = pCol.content.get("foo")
        assertNotNull(points)
        assertEquals(2, points.size())
        assertEquals(new Point(1,2), points.get("one"))
        assertEquals(new Point(10,20), points.get("two"))

        points = pCol.content.get("bar")
        assertNotNull(points)
        assertEquals(2, points.size())
        assertEquals(new Point(3, 4), points.get("ten"))
        assertEquals(new Point(30, 40), points.get("twenty"))
    }

    @Test
    void testOddMaps()
    {
        String json = '{"@type":"java.util.HashMap","@keys":null,"@items":null}'
        Map map = (Map)TestUtil.readJsonObject(json)
        assertTrue(map instanceof HashMap)
        assertTrue(map.isEmpty())

        json = '{"@type":"java.util.HashMap"}'
        map = (Map)TestUtil.readJsonObject(json)
        assertTrue(map instanceof HashMap)
        assertTrue(map.isEmpty())


        json = '{"@type":"java.util.HashMap","@keys":null,"@items":[]}'
        Exception e = assertThrows(Exception.class, { TestUtil.readJsonObject(json)})
        assert e.message.toLowerCase().contains('@keys or @items')
        assert e.message.toLowerCase().contains('empty')

        json = '{"@type":"java.util.HashMap","@keys":[1,2],"@items":[true]}'
        e = assertThrows(Exception.class, { TestUtil.readJsonObject(json) })
        assert e.message.toLowerCase().contains("different size")
    }

    @Test
    void testReconstituteMapEmpty()
    {
        Map map = new LinkedHashMap()
        String json0 = TestUtil.getJsonString(map)
        TestUtil.printLine("json0=" + json0)

        map = (Map) JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS):true] as Map)
        String json1 = TestUtil.getJsonString(map)
        TestUtil.printLine("json1=" + json1)

        map = (Map) TestUtil.readJsonObject(json1)
        assertTrue(map instanceof LinkedHashMap)
        assertTrue(map.isEmpty())
        assertTrue(json0.equals(json1))
    }

    @Test
    void testReconstituteRefMap()
    {
        Map m1 = new HashMap()
        Object[] root = [m1, m1] as Object[]
        String json0 = TestUtil.getJsonString(root)
        TestUtil.printLine("json0=" + json0)

        Object[] array = (Object[]) JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS):true] as Map)
        String json1 = TestUtil.getJsonString(array)
        TestUtil.printLine("json1=" + json1)

        root = (Object[]) TestUtil.readJsonObject(json1)
        assertTrue(root.length == 2)
        assertTrue(root[0] instanceof Map)
        assertTrue(((Map)root[0]).isEmpty())
        assertTrue(root[1] instanceof Map)
        assertTrue(((Map) root[1]).isEmpty())
        assertTrue(json0.equals(json1))
    }

    @Test
    void testReconstituteMapSimple()
    {
        SimpleMapTest smt = new SimpleMapTest()
        smt.map.put("a", "alpha")
        String json0 = TestUtil.getJsonString(smt)
        TestUtil.printLine("json0=" + json0)

        Map result = (Map) JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS):true] as Map)
        String json1 = TestUtil.getJsonString(result)
        TestUtil.printLine("json1=" + json1)

        SimpleMapTest mapTest = (SimpleMapTest) TestUtil.readJsonObject(json1)
        assertTrue(mapTest.map.equals(smt.map))
        assertTrue(json0.equals(json1))
    }

    @Test
    void testMapFromUnknown()
    {
        Map map = (Map) JsonReader.jsonToJava('{"a":"alpha", "b":"beta"}', [(JsonReader.UNKNOWN_OBJECT):(Object)"java.util.concurrent.ConcurrentHashMap"]);
        assert map instanceof ConcurrentHashMap
        assert map.size() == 2
        assert map.a == 'alpha'
        assert map.b == 'beta'

        map = (Map) JsonReader.jsonToJava('{"a":"alpha", "b":"beta"}');
        assert map instanceof JsonObject
        assert map.size() == 2
        assert map.a == 'alpha'
        assert map.b == 'beta'

        assertThrows(JsonIoException.class, {JsonReader.jsonToJava('{"a":"alpha", "b":"beta"}', [(JsonReader.UNKNOWN_OBJECT):(Object)Boolean.FALSE]) })
    }

    @Test
    void testMapWithQuoteInKey()
    {
        Map variableKeys = [0L: 0L, '"one"':1L, '"two"':2L]
        String json = TestUtil.getJsonString(variableKeys)
        Map ret = TestUtil.readJsonMap(json, [:])
        assert ret.size() == 3

        assert ret[0L] == 0L
        assert ret['"one"'] == 1L
        assert ret['"two"'] == 2L

        Map stringKeys = ['"zero"': 0L, '"one"':1L, '"two"':2L]
        json = TestUtil.getJsonString(stringKeys)
        ret = TestUtil.readJsonMap(json, [:])
        assert ret.size() == 3

        assert ret['"zero"'] == 0L
        assert ret['"one"'] == 1L
        assert ret['"two"'] == 2L
    }

    @Test
    void testMapWithPrimitiveValues()
    {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone('GMT'))
        cal.clear()
        cal.set(2017, 5, 10,8, 1, 32)
        final Map<String, Object> map = [:]
        map['Byte'] = Byte.valueOf((byte)79)
        map['Integer'] = Integer.valueOf(179)
        map['Short'] = Short.valueOf((short)179)
        map['Float'] = Float.valueOf(179.0f)
        map['date'] = cal.time
        map['Long'] = Long.valueOf(179)
        map['Double'] = Double.valueOf(179)
        map['Character'] = Character.valueOf('z' as char)
        map['Boolean'] = Boolean.valueOf(false)
        map['BigInteger'] = new BigInteger("55")
        map['BigDecimal'] = new BigDecimal("3.33333")

        final Map params = [(JsonWriter.DATE_FORMAT):JsonWriter.ISO_DATE_TIME_FORMAT] as Map
        final String str = JsonWriter.objectToJson(map, params)
        
        // for debugging
        System.out.println("${str}\n")

        final Map<String, Object> map2 = (Map) JsonReader.jsonToMaps(str)

        // for debugging
        for (Map.Entry<String, Object> entry : map2.entrySet())
        {
            System.out.println("${entry.key} : ${entry.value} {${entry.value.class.simpleName}}")
        }

        assert map2['Boolean'] instanceof Boolean
        assert map2['Boolean'] == false

        assert map2['Byte'] instanceof Byte
        assert map2['Byte'] == 79

        assert map2['Short'] instanceof Short
        assert map2['Short'] == 179

        assert map2['Integer'] instanceof Integer
        assert map2['Integer'] == 179

        assert map2['Long'] instanceof Long
        assert map2['Long'] == 179

        assert map2['Float'] instanceof Float
        assert map2['Float'] == 179

        assert map2['Double'] instanceof Double
        assert map2['Double'] == 179

        assert map2['Character'] instanceof Character
        assert map2['Character'] == 'z'

        assert map2['date'] instanceof Date
        assert map2['date'] == cal.time

        assert map2['BigInteger'] instanceof BigInteger
        assert map2['BigInteger'] == 55

        assert map2['BigDecimal'] instanceof BigDecimal
        assert map2['BigDecimal'] == 3.33333
    }

    @Test
    void testSingletonMap()
    {
        // SingleTon Maps are simple one key, one value Maps (inner class to Collections) and must be reconstituted
        // in a special way.  Make sure that works.
        Map root1 = Collections.singletonMap( "testCfgKey", "testCfgValue" )
        String json = JsonWriter.objectToJson(root1)
        Map root2 = (Map) JsonReader.jsonToJava(json)
        assert root2.get('testCfgKey') == 'testCfgValue'
        assert root1.get('testCfgKey') == root2.get('testCfgKey')
    }
}
