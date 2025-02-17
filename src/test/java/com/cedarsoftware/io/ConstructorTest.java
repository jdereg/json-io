package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.cedarsoftware.util.FastByteArrayInputStream;
import com.cedarsoftware.util.FastByteArrayOutputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class ConstructorTest
{
    @Test
    public void testNoDefaultConstructor()
    {
        Calendar c = Calendar.getInstance();
        c.set(2010, 5, 5, 5, 5, 5);
        String[] strings = new String[]{"C", "C++", "Java"};
        Integer[] ints = new Integer[]{1, 2, 4, 8, 16, 32, 64, 128};
        Object foo = new TestJsonNoDefaultOrPublicConstructor("Hello, World.", c.getTime(), (byte) 1, (byte) 11, (short) 2, (short) 22, 3, 33, 4L, 44L, 5.0f, 55.0f, 6.0d, 66.0d, true, Boolean.TRUE, 'J', 'K', strings, ints, new BigDecimal("1.1"));
        String jsonOut = TestUtil.toJson(foo);
        TestUtil.printLine(jsonOut);

        TestJsonNoDefaultOrPublicConstructor bar = TestUtil.toObjects(jsonOut, null);

        assertEquals("Hello, World.", bar.getString());
        assertEquals(bar.getDate(), c.getTime());
        assertEquals(1, bar.getByte());
        assertEquals(2, bar.getShort());
        assertEquals(3, bar.getInt());
        assertEquals(4, bar.getLong());
        assertEquals(5.0f, bar.getFloat());
        assertEquals(6.0, bar.getDouble());
        assertTrue(bar.getBoolean());
        assertEquals('J', bar.getChar());
        assertNotNull(bar.getStrings());
        assertEquals(bar.getStrings().length, strings.length);
        assertNotNull(bar.getInts());
        assertEquals(bar.getInts().length, ints.length);
        assertEquals(bar._bigD, new BigDecimal("1.1"));
    }

    @Test
    public void testReconstitutePrimitives()
    {
        Object foo = new TestJsonNoDefaultOrPublicConstructor("Hello, World.", new Date(), (byte) 1, (byte) 11, (short) 2, (short) 22, 3, 33, 4L, 44L, 5.0f, 55.0f, 6.0d, 66.0d, true, Boolean.TRUE, 'J', 'K', new String[]{"john", "adams"}, new Integer[]{2, 6}, new BigDecimal("2.71828"));
        String json0 = TestUtil.toJson(foo);
        TestUtil.printLine("json0=" + json0);

        Map<String, Object> map = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assertEquals((byte) 1, map.get("_byte"));
        assertEquals((short) 2, map.get("_short"));
        assertEquals(3, map.get("_int"));
        assertEquals(4L, map.get("_long"));
        assertEquals(5.0f, (float) map.get("_float"), 0.00001f);
        assertEquals(6.0d, (double) map.get("_double"), 0.00001d);
        assertEquals(true, map.get("_boolean"));
        assertEquals('J', map.get("_char"));

        assertEquals((byte) 11, map.get("_Byte"));
        assertEquals((short) 22, map.get("_Short"));
        assertEquals(33, map.get("_Integer"));
        assertEquals(44L, map.get("_Long"));
        assertEquals(55.0f, (float) map.get("_Float"), 0.0001f);
        assertEquals(66.0d, (double) map.get("_Double"), 0.0001d);
        assertEquals(true, map.get("_Boolean"));
        assertEquals('K', map.get("_Char"));
        BigDecimal num = (BigDecimal) map.get("_bigD");
        assertEquals(new BigDecimal("2.71828"), num);

        String json1 = TestUtil.toJson(map);
        TestUtil.printLine("json1=" + json1);
        assertEquals(json0, json1);
    }

    @Test
    public void testReconstituteNullablePrimitives()
    {
        Object foo = new TestJsonNoDefaultOrPublicConstructor("Hello, World.", new Date(), (byte) 1, null, (short) 2, null, 3, null, 4L, null, 5.0f, null, 6.0d, null, true, null, 'J', null, new String[]{"john", "adams"}, new Integer[]{2, 6}, null);
        String json = TestUtil.toJson(foo);
        TestUtil.printLine("json0=" + json);

        Map<String, Object> map = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assertEquals((byte) 1, map.get("_byte"));
        assertEquals((short) 2, map.get("_short"));
        assertEquals(3, map.get("_int"));
        assertEquals(4L, map.get("_long"));
        assertEquals(5.0f, (float) map.get("_float"), 0.0001f);
        assertEquals(6.0d, (double) map.get("_double"), 0.0001d);
        assertEquals(true, map.get("_boolean"));
        assertEquals('J', map.get("_char"));

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

        String json1 = TestUtil.toJson(map);
        TestUtil.printLine("json1=" + json1);
        assertEquals(json, json1);

        map = TestUtil.toObjects(json1, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        json = TestUtil.toJson(map);
        TestUtil.printLine("json2=" + json);
        assertEquals(json, json1);
    }

    @Test
    public void testConstructorWithObjectArg()
    {
        Canine bella = new Canine("Bella");
        String json = TestUtil.toJson(bella);
        TestUtil.printLine("json = " + json);
        Canine dog = TestUtil.toObjects(json, null);
        assertEquals("Bella", dog.getName());
    }

    @Test
    public void testNoNullConstructor()
    {
        NoNullConstructor noNull = new NoNullConstructor(new ArrayList<>(), new LinkedHashMap<>(), "", new Date());
        noNull.setList(null);
        noNull.setMap(null);
        noNull.setString(null);
        noNull.setDate(null);

        String json = TestUtil.toJson(noNull);
        TestUtil.printLine(json);
        NoNullConstructor foo = TestUtil.toObjects(json, null);
        assertNull(foo.getList());
        assertNull(foo.getMap());
        assertNull(foo.getString());
        assertNull(foo.getDate());
    }

    @Test
    public void testJsonReaderConstructor()
    {
        String json = "{\"@type\":\"sun.util.calendar.ZoneInfo\",\"zone\":\"EST\"}";
        TimeZone tz = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), TimeZone.class);
        assertNotNull(tz);
        assertEquals("EST", tz.getID());
    }

    @Test
    public void testWriterObjectAPI()
    {
        String json = "[1,true,null,3.14,[]]";
        Object o = TestUtil.toObjects(json, null);
        assert TestUtil.toJson(o).equals(json);

        FastByteArrayOutputStream fbao = new FastByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(fbao);
        writer.write(o);
        writer.close();
        String s = fbao.toString();
        assert json.equals(s);
    }

    @Test
    public void testUrlInConstructor() throws MalformedURLException
    {
        Web addr = new Web(new URL("http://acme.com"));
        String json = TestUtil.toJson(addr);
        TestUtil.printLine("json = " + json);
        Web addr2 = TestUtil.toObjects(json, null);
        assertEquals(new URL("http://acme.com"), addr2.getUrl());
    }

    @Test
    public void testMapConstructor()
    {
        String json = TestUtil.toJson(new Canine("Bella"));
        Map root = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);

        Canine bella = JsonIo.toObjects((JsonObject) root, new ReadOptionsBuilder().build(), Canine.class);
        assert bella.getName().equals("Bella");
    }

    @Test
    public void testReaderInputStreamConstructor()
    {
        Canine dog = new Canine("Eddie");
        String json = TestUtil.toJson(dog);
        FastByteArrayInputStream inputStream = new FastByteArrayInputStream(json.getBytes());
        Canine eddie = JsonIo.toObjects(inputStream, new ReadOptionsBuilder().build(), null);
        assert eddie.getName().equals("Eddie");

        inputStream = new FastByteArrayInputStream(json.getBytes());
        Map<String, Object> dogMap = (Map) TestUtil.toObjects(inputStream, new ReadOptionsBuilder().returnAsJsonObjects().build());
        assert dogMap.get("name").equals("Eddie");
    }

    public static class Canine
    {
        Canine(Object nm)
        {
            name = nm.toString();// intentionally causes NPE when reflective constructor tries 'null' as arg
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        private String name;
    }

    public static class NoNullConstructor
    {
        private NoNullConstructor(List list, Map map, String string, Date date)
        {
            if (list == null || map == null || string == null || date == null)
            {
                throw new JsonIoException("Constructor arguments cannot be null");
            }

            this.list = list;
            this.map = map;
            this.string = string;
            this.date = date;
        }

        public List getList()
        {
            return list;
        }

        public void setList(List list)
        {
            this.list = list;
        }

        public Map getMap()
        {
            return map;
        }

        public void setMap(Map map)
        {
            this.map = map;
        }

        public String getString()
        {
            return string;
        }

        public void setString(String string)
        {
            this.string = string;
        }

        public Date getDate()
        {
            return date;
        }

        public void setDate(Date date)
        {
            this.date = date;
        }

        private List list;
        private Map map;
        private String string;
        private Date date;
    }

    public static class Web
    {
        Web(URL u)
        {
            url = u;
        }

        public URL getUrl()
        {
            return url;
        }

        public void setUrl(URL url)
        {
            this.url = url;
        }

        private URL url;
    }

    private static class TestJsonNoDefaultOrPublicConstructor
    {
        private TestJsonNoDefaultOrPublicConstructor(String string, Date date, byte b, Byte B, short s, Short S, int i, Integer I, long l, Long L, float f, Float F, double d, Double D, boolean bool, Boolean Bool, char c, Character C, String[] strings, Integer[] ints, BigDecimal bigD)
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

        char getChar()
        {
            return _char;
        }

        String[] getStrings()
        {
            return _strings;
        }

        Integer[] getInts()
        {
            return _ints;
        }

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
        private final Integer[] _ints;
        private final BigDecimal _bigD;
    }
}
