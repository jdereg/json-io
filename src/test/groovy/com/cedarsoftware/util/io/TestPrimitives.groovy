package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
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
class TestPrimitives
{
    class AllPrimitives
    {
        boolean b;
        Boolean bb;
        byte by;
        Byte bby;
        char c;
        Character cc;
        double d;
        Double dd;
        float f;
        Float ff;
        int i;
        Integer ii;
        long l;
        Long ll;
        short s;
        Short ss;
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
    void testPrimitivesSetWithStrings()
    {
        String json = '{"@type":"' + AllPrimitives.class.getName() + '","b":"true","bb":"true","by":"9","bby":"9","c":"B","cc":"B","d":"9.0","dd":"9.0","f":"9.0","ff":"9.0","i":"9","ii":"9","l":"9","ll":"9","s":"9","ss":"9"}'
        AllPrimitives ap = (AllPrimitives) TestUtil.readJsonObject(json)
        assertTrue(ap.b)
        assertTrue(ap.bb)
        assertTrue(ap.by == 9)
        assertTrue(ap.bby == 9)
        assertTrue(ap.c == 'B')
        assertTrue(ap.cc == 'B')
        assertTrue(ap.d == 9)
        assertTrue(ap.dd == 9)
        assertTrue(ap.f == 9)
        assertTrue(ap.ff == 9)
        assertTrue(ap.i == 9)
        assertTrue(ap.ii == 9)
        assertTrue(ap.l == 9)
        assertTrue(ap.ll == 9)
        assertTrue(ap.s == 9)
        assertTrue(ap.ss == 9)
    }

    @Test
    void testAbilityToNullPrimitivesWithEmptyString()
    {
        String json = '{"@type":"' + AllPrimitives.class.getName() + '","b":"","bb":"","by":"","bby":"","c":"","cc":"","d":"","dd":"","f":"","ff":"","i":"","ii":"","l":"","ll":"","s":"","ss":""}'
        AllPrimitives ap = (AllPrimitives) TestUtil.readJsonObject(json)
        assertFalse(ap.b)
        assertFalse(ap.bb)
        assertTrue(ap.by == 0)
        assertTrue(ap.bby == 0)
        assertTrue(ap.c == 0)
        assertTrue(ap.cc == 0)
        assertTrue(ap.d == 0)
        assertTrue(ap.dd == 0)
        assertTrue(ap.f == 0)
        assertTrue(ap.ff == 0)
        assertTrue(ap.i == 0)
        assertTrue(ap.ii == 0)
        assertTrue(ap.l == 0)
        assertTrue(ap.ll == 0)
        assertTrue(ap.s == 0)
        assertTrue(ap.ss == 0)
    }

    @Test
    void testEmptyPrimitives()
    {
        String json = '{"@type":"byte"}'
        Byte b = (Byte) JsonReader.jsonToJava(json)
        assertTrue(b.getClass().equals(Byte.class))
        assertTrue(b == 0)

        json = '{"@type":"short"}'
        Short s = (Short) JsonReader.jsonToJava(json)
        assertTrue(s.getClass().equals(Short.class))
        assertTrue(s == 0)

        json = '{"@type":"int"}'
        Integer i = (Integer) JsonReader.jsonToJava(json)
        assertTrue(i.getClass().equals(Integer.class))
        assertTrue(i == 0)

        json = '{"@type":"long"}'
        Long l = (Long) JsonReader.jsonToJava(json)
        assertTrue(l.getClass().equals(Long.class))
        assertTrue(l == 0)

        json = '{"@type":"float"}'
        Float f = (Float) JsonReader.jsonToJava(json)
        assertTrue(f.getClass().equals(Float.class))
        assertTrue(f == 0.0f)

        json = '{"@type":"double"}'
        Double d = (Double) JsonReader.jsonToJava(json)
        assertTrue(d.getClass().equals(Double.class))
        assertTrue(d == 0.0d)

        json = '{"@type":"char"}'
        Character c = (Character) JsonReader.jsonToJava(json)
        assertTrue(c.getClass().equals(Character.class))
        assertTrue(c == '\u0000')

        json = '{"@type":"boolean"}'
        Boolean bool = (Boolean) JsonReader.jsonToJava(json)
        assertTrue(bool == Boolean.FALSE)

        json = '{"@type":"string"}'
        String str = null;
        try
        {
            str = (String) JsonReader.jsonToJava(json)
            fail()
        }
        catch (Exception e)
        {
            assertTrue(e.message.toLowerCase().contains("'value'"))
        }
        assertTrue(str == null)
    }

    @Test
    void testAssignPrimitiveToString()
    {
        String json = '{"@type":"' + TestStringField.class.getName() + '","intField":16,"booleanField":true,"doubleField":345.12321,"nullField":null,"values":[10,true,3.14159,null]}'
        TestStringField tsf = (TestStringField) TestUtil.readJsonObject(json)
        assertEquals("16", tsf.intField)
        assertEquals("true", tsf.booleanField)
        assertEquals("345.12321", tsf.doubleField)
        assertNull(tsf.nullField)
        assertEquals("10", tsf.values[0])
        assertEquals("true", tsf.values[1])
        assertEquals("3.14159", tsf.values[2])
        assertEquals(null, tsf.values[3])
    }

}
