package com.cedarsoftware.util.io

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotSame
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertTrue

import static com.cedarsoftware.util.io.JsonObject.REF
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
class TestBigDecimal
{
    static class TestBigDecimalField
    {
        BigDecimal fromString
        BigDecimal fromLong
        BigDecimal fromDouble
        BigDecimal fromBoolean
        BigDecimal fromStringObj
        BigDecimal fromLongObj
        BigDecimal fromDoubleObj
        BigDecimal fromBooleanObj
        BigDecimal fromBigIntObj
        BigDecimal fromBigDecObj
        BigDecimal[] values
    }

    @Test
    void testAssignBigDecimal()
    {
        String json = '{"@type":"' + TestBigDecimalField.class.name + '","fromString":"3.14159","fromLong":314159,"fromDouble":3.14159,"fromBoolean":true,"fromStringObj":{"@type":"java.math.BigDecimal","value":"3.14159"},"fromLongObj":{"@type":"java.math.BigDecimal","value":314159},"fromDoubleObj":{"@type":"java.math.BigDecimal","value":3.14159},"fromBooleanObj":{"@type":"java.math.BigDecimal","value":false},"fromBigIntObj":{"@type":"java.math.BigDecimal","value":{"@type":"java.math.BigInteger","value":72}},"fromBigDecObj":{"@type":"java.math.BigDecimal","value":{"@type":"java.math.BigDecimal","value":72.1}},"values":["3.14159",314159,3.14159,true,{"@type":"java.math.BigDecimal","value":"3.14159"},{"@type":"java.math.BigDecimal","value":314159},{"@type":"java.math.BigDecimal","value":3.14159},{"@type":"java.math.BigDecimal","value":true},{"@type":"java.math.BigDecimal","value":{"@type":"java.math.BigDecimal","value":72.72}}]}'
        TestBigDecimalField tbd = (TestBigDecimalField) TestUtil.readJsonObject(json)
        assertEquals((Object)new BigDecimal("3.14159"), tbd.fromString)
        assertEquals((Object)new BigDecimal(314159), tbd.fromLong)
        assertEquals((Object)new BigDecimal("3.14159"), tbd.fromDouble)
        assertEquals((Object)new BigDecimal(1), tbd.fromBoolean)
        assertEquals((Object)new BigDecimal("3.14159"), tbd.fromStringObj)
        assertEquals((Object)new BigDecimal(314159), tbd.fromLongObj)
        assertEquals((Object)new BigDecimal("3.14159"), tbd.fromDoubleObj)
        assertEquals((Object)new BigDecimal(0), tbd.fromBooleanObj)
        assertEquals((Object)new BigDecimal(72), tbd.fromBigIntObj)
        assertEquals((Object)new BigDecimal("72.1"), tbd.fromBigDecObj)

        assertEquals((Object)new BigDecimal("3.14159"), tbd.values[0])
        assertEquals((Object)new BigDecimal(314159), tbd.values[1])
        assertEquals((Object)new BigDecimal("3.14159"), tbd.values[2])
        assertEquals((Object)new BigDecimal(1), tbd.values[3])
        assertEquals((Object)new BigDecimal("3.14159"), tbd.values[4])
        assertEquals((Object)new BigDecimal(314159), tbd.values[5])
        assertEquals((Object)new BigDecimal("3.14159"), tbd.values[6])
        assertEquals((Object)new BigDecimal(1), tbd.values[7])
        assertEquals((Object)new BigDecimal("72.72"), tbd.values[8])

        Map map = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        json = TestUtil.getJsonString(map)
        tbd = (TestBigDecimalField) TestUtil.readJsonObject(json)
        assertEquals((Object)new BigDecimal("3.14159"), tbd.fromString)
        assertEquals((Object)new BigDecimal(314159), tbd.fromLong)
        assertEquals((Object)new BigDecimal("3.14159"), tbd.fromDouble)
        assertEquals((Object)new BigDecimal(1), tbd.fromBoolean)
        assertEquals((Object)new BigDecimal("3.14159"), tbd.fromStringObj)
        assertEquals((Object)new BigDecimal(314159), tbd.fromLongObj)
        assertEquals((Object)new BigDecimal("3.14159"), tbd.fromDoubleObj)
        assertEquals((Object)new BigDecimal(0), tbd.fromBooleanObj)
        assertEquals((Object)new BigDecimal(72), tbd.fromBigIntObj)
        assertEquals((Object)new BigDecimal("72.1"), tbd.fromBigDecObj)

        assertEquals((Object)new BigDecimal("3.14159"), tbd.values[0])
        assertEquals((Object)new BigDecimal(314159), tbd.values[1])
        assertEquals((Object)new BigDecimal("3.14159"), tbd.values[2])
        assertEquals((Object)new BigDecimal(1), tbd.values[3])
        assertEquals((Object)new BigDecimal("3.14159"), tbd.values[4])
        assertEquals((Object)new BigDecimal(314159), tbd.values[5])
        assertEquals((Object)new BigDecimal("3.14159"), tbd.values[6])
        assertEquals((Object)new BigDecimal(1), tbd.values[7])
        assertEquals((Object)new BigDecimal("72.72"), tbd.values[8])

        json = '{"@type":"' + TestBigDecimalField.class.name + '","fromString":""}'
        tbd = (TestBigDecimalField) TestUtil.readJsonObject(json)
        assertNull(tbd.fromString)
    }

    @Test
    void testBigDecimal0()
    {
        TestBigDecimalField bigDecs = new TestBigDecimalField()
        bigDecs.fromString = new BigDecimal("123.12")
        bigDecs.fromLong = new BigDecimal("0")
        bigDecs.fromDouble = new BigDecimal("0.0")
        bigDecs.fromBoolean = bigDecs.fromDouble;
        String json = TestUtil.getJsonString(bigDecs)
        assertFalse(json.contains(REF))
    }

    @Test
    void testBigDecimal()
    {
        String s = "123456789012345678901234567890.123456789012345678901234567890"
        BigDecimal bigDec = new BigDecimal(s)
        String json = TestUtil.getJsonString(bigDec)
        TestUtil.printLine("json=" + json)
        bigDec = (BigDecimal) TestUtil.readJsonObject(json)
        assertTrue(bigDec.equals(new BigDecimal(s)))
    }

    @Test
    void testBigDecimalInArray()
    {
        String s = "123456789012345678901234567890.123456789012345678901234567890"
        BigDecimal bigDec = new BigDecimal(s)
        Object[] bigDecs = [bigDec, bigDec] as Object[]
        BigDecimal[] typedBigDecs = [bigDec, bigDec] as BigDecimal[]
        String json = TestUtil.getJsonString(bigDecs)
        TestUtil.printLine("json=" + json)

        bigDecs = (Object[]) TestUtil.readJsonObject(json)
        assertTrue(bigDecs.length == 2)
        assertNotSame(bigDecs[0], bigDecs[1])
        assertTrue(new BigDecimal(s).equals(bigDecs[0]))
        json = TestUtil.getJsonString(typedBigDecs)
        TestUtil.printLine("json=" + json)
        assertTrue(typedBigDecs.length == 2)
        assertTrue(typedBigDecs[0] == typedBigDecs[1])
        assertTrue(new BigDecimal(s).equals(typedBigDecs[0]))
    }

    @Test
    void testBigDecimalInCollection()
    {
        String s = "-123456789012345678901234567890.123456789012345678901234567890"
        BigDecimal bigDec = new BigDecimal(s)
        List list = new ArrayList()
        list.add(bigDec)
        list.add(bigDec)
        String json = TestUtil.getJsonString(list)
        TestUtil.printLine("json=" + json)
        list = (List) TestUtil.readJsonObject(json)
        assertTrue(list.size() == 2)
        assertTrue(list.get(0).equals(new BigDecimal(s)))
        // BigDecimal is treated like primitives (immutables), instances are not shared
        // (safe to do because the immutable object cannot change, friendly to the JSON file)
        assertNotSame(list.get(0), list.get(1))
    }
}
