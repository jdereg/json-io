package com.cedarsoftware.util.io

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNull
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
class TestConstructor
{
    static class Canine
    {
        String name;
        Canine(Object nm)
        {
            name = nm.toString()     // intentionally causes NPE when reflective constructor tries 'null' as arg
        }
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
                throw new JsonIoException("Constructor arguments cannot be null")
            }
            this.list = list;
            this.map = map;
            this.string = string;
            this.date = date;
        }
    }

    static class Web
    {
        URL url;
        Web(URL u)
        {
            url = u;
        }
    }

    private static class TestJsonNoDefaultOrPublicConstructor
    {
        private final String _str
        private final Date _date
        private final byte _byte
        private final Byte _Byte
        private final short _short
        private final Short _Short
        private final int _int
        private final Integer _Integer
        private final long _long
        private final Long _Long
        private final float _float
        private final Float _Float
        private final double _double
        private final Double _Double
        private final boolean _boolean
        private final Boolean _Boolean
        private final char _char
        private final Character _Char
        private final String[] _strings
        private final int[] _ints
        private final BigDecimal _bigD

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
    void testNoDefaultConstructor()
    {
        Calendar c = Calendar.instance
        c.set(2010, 5, 5, 5, 5, 5)
        String[] strings = ["C", "C++", "Java"] as String[]
        int[] ints = [1, 2, 4, 8, 16, 32, 64, 128] as int[]
        Object foo = new TestJsonNoDefaultOrPublicConstructor("Hello, World.", c.getTime(), (byte) 1, new Byte((byte)11), (short) 2, new Short((short)22), 3, new Integer(33), 4L, new Long(44L), 5.0f, new Float(55.0f), 6.0d, new Double(66.0d), true, Boolean.TRUE,'J' as char, new Character('K' as char), strings, ints, new BigDecimal(1.1d))
        String jsonOut = TestUtil.getJsonString(foo)
        TestUtil.printLine(jsonOut)

        TestJsonNoDefaultOrPublicConstructor bar = (TestJsonNoDefaultOrPublicConstructor) TestUtil.readJsonObject(jsonOut)

        assertTrue("Hello, World.".equals(bar.getString()))
        assertTrue(bar.getDate().equals(c.getTime()))
        assertTrue(bar.getByte() == 1)
        assertTrue(bar.getShort() == 2)
        assertTrue(bar.getInt() == 3)
        assertTrue(bar.getLong() == 4)
        assertTrue(bar.getFloat() == 5.0f)
        assertTrue(bar.getDouble() == 6.0)
        assertTrue(bar.getBoolean())
        assertTrue(bar.getChar() == 'J')
        assertTrue(bar.getStrings() != null)
        assertTrue(bar.getStrings().length == strings.length)
        assertTrue(bar.getInts() != null)
        assertTrue(bar.getInts().length == ints.length)
        assertTrue(bar._bigD.equals(new BigDecimal(1.1d)))
    }

    @Test
    void testReconstitutePrimitives()
    {
        Object foo = new TestJsonNoDefaultOrPublicConstructor("Hello, World.", new Date(), (byte) 1, new Byte((byte)11), (short) 2, new Short((short)22), 3, new Integer(33), 4L, new Long(44L), 5.0f, new Float(55.0f), 6.0d, new Double(66.0d), true, Boolean.TRUE,'J' as char, new Character('K' as char), ["john","adams"] as String[], [2,6] as int[], new BigDecimal("2.71828"))
        String json0 = TestUtil.getJsonString(foo)
        TestUtil.printLine("json0=" + json0)

        Map map = (Map) JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS):true] as Map)
        assertEquals((byte)1, map.get("_byte") )
        assertEquals((short)2, map.get("_short"))
        assertEquals(3, map.get("_int"))
        assertEquals(4L, map.get("_long"))
        assertEquals(5.0f, (float)map.get("_float"), 0.00001f)
        assertEquals(6.0d, (double)map.get("_double"), 0.00001d)
        assertEquals(true, map.get("_boolean"))
        assertEquals('J' as char, map.get("_char"))

        assertEquals((byte)11, map.get("_Byte"))
        assertEquals((short)22, map.get("_Short"))
        assertEquals(33, map.get("_Integer"))
        assertEquals(44L, map.get("_Long"))
        assertEquals(55.0f, (float)map.get("_Float"), 0.0001f)
        assertEquals(66.0d, (double)map.get("_Double"), 0.0001d)
        assertEquals(true, map.get("_Boolean"))
        assertEquals('K' as char, map.get("_Char"))
        BigDecimal num = (BigDecimal) map.get("_bigD")
        assertEquals(new BigDecimal("2.71828"), num)

        String json1 = TestUtil.getJsonString(map)
        TestUtil.printLine("json1=" + json1)
        assertTrue(json0.equals(json1))
    }

    @Test
    void testReconstituteNullablePrimitives()
    {
        Object foo = new TestJsonNoDefaultOrPublicConstructor("Hello, World.", new Date(), (byte) 1, null, (short) 2, null, 3, null, 4L, null, 5.0f, null, 6.0d, null, true, null,'J' as char, null, ["john","adams"] as String[], [2,6] as int[], null)
        String json = TestUtil.getJsonString(foo)
        TestUtil.printLine("json0=" + json)

        Map map = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        assertEquals((byte)1, map.get("_byte"))
        assertEquals((short)2, map.get("_short"))
        assertEquals(3, map.get("_int"))
        assertEquals(4L, map.get("_long"))
        assertEquals(5.0f, (float)map.get("_float"), 0.0001f)
        assertEquals(6.0d, (double)map.get("_double"), 0.0001d)
        assertEquals(true, map.get("_boolean"))
        assertEquals((char)'J', map.get("_char"))

        Map prim = (Map) map.get("_Byte")
        assertNull(prim)
        prim = (Map) map.get("_Short")
        assertNull(prim)
        prim = (Map) map.get("_Integer")
        assertNull(prim)
        prim = (Map) map.get("_Long")
        assertNull(prim)
        prim = (Map) map.get("_Float")
        assertNull(prim)
        prim = (Map) map.get("_Double")
        assertNull(prim)
        prim = (Map) map.get("_Boolean")
        assertNull(prim)
        prim = (Map) map.get("_Char")
        assertNull(prim)
        prim = (Map) map.get("_bigD")
        assertNull(prim)

        String json1 = TestUtil.getJsonString(map)
        TestUtil.printLine("json1=" + json1)
        assertTrue(json.equals(json1))

        map = (Map) JsonReader.jsonToJava(json1, [(JsonReader.USE_MAPS):true] as Map)
        json = TestUtil.getJsonString(map)
        TestUtil.printLine("json2=" + json)
        assertTrue(json.equals(json1))
    }

    @Test
    void testConstructorWithObjectArg()
    {
        Canine bella = new Canine('Bella')
        String json = TestUtil.getJsonString(bella)
        TestUtil.printLine("json = " + json)
        Canine dog = (Canine) TestUtil.readJsonObject(json)
        assertEquals('Bella', dog.name)
    }

    @Test
    void testNoNullConstructor()
    {
        NoNullConstructor noNull = new NoNullConstructor(new ArrayList(), [:], "", new Date())
        noNull.list = null;
        noNull.map = null;
        noNull.string = null;
        noNull.date = null;

        String json = TestUtil.getJsonString(noNull)
        TestUtil.printLine(json)
        NoNullConstructor foo = (NoNullConstructor) TestUtil.readJsonObject(json)
        assertNull(foo.list)
        assertNull(foo.map)
        assertNull(foo.string)
        assertNull(foo.date)
    }

    @Test
    void testJsonReaderConstructor()
    {
        String json = '{"@type":"sun.util.calendar.ZoneInfo","zone":"EST"}'
        JsonReader jr = new JsonReader(new ByteArrayInputStream(json.bytes))
        TimeZone tz = (TimeZone) jr.readObject()
        assertTrue(tz != null)
        assertTrue("EST".equals(tz.ID))
    }

    @Test
    void testWriterObjectAPI()
    {
        String json = "[1,true,null,3.14,[]]"
        Object o = JsonReader.jsonToJava(json)
        assert TestUtil.getJsonString(o) == json

        ByteArrayOutputStream ba = new ByteArrayOutputStream()
        JsonWriter writer = new JsonWriter(ba)
        writer.write(o)
        writer.close()
        String s = new String(ba.toByteArray(), "UTF-8")
        assert json == s
    }

    @Test
    void testUrlInConstructor()
    {
        Web addr = new Web(new URL("http://acme.com"))
        String json = TestUtil.getJsonString(addr)
        TestUtil.printLine("json = " + json)
        Web addr2 = (Web) TestUtil.readJsonObject(json)
        assertEquals(new URL("http://acme.com"), addr2.url)
    }

    @Test
    void testMapConstructor()
    {
        String json = JsonWriter.objectToJson(new Canine('Bella'))
        Map root = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)

        JsonReader reader = new JsonReader([:])
        Canine bella = (Canine) reader.jsonObjectsToJava(root as JsonObject)
        assert bella.name == 'Bella'
    }

    @Test
    void testReaderInputStreamConstructor()
    {
        Canine dog = new Canine('Eddie')
        String json = JsonWriter.objectToJson(dog)
        ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes())
        Canine eddie = (Canine)JsonReader.jsonToJava(inputStream, null)
        assert eddie.name == 'Eddie'

        inputStream = new ByteArrayInputStream(json.getBytes())
        Map dogMap = (Map)JsonReader.jsonToJava(inputStream, [(JsonReader.USE_MAPS):true] as Map)
        assert dogMap.name == 'Eddie'
    }
}
