package com.cedarsoftware.util.io

import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotSame
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

/**
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
class TestBigInteger
{
    class TestBigIntegerField
    {
        BigInteger fromString
        BigInteger fromLong
        BigInteger fromBoolean
        BigInteger fromStringObj
        BigInteger fromLongObj
        BigInteger fromBooleanObj
        BigInteger fromBigDecObj
        BigInteger fromBigIntObj
        BigInteger[] values
    }

    @Test
    public void testAssignBigInteger() throws Exception
    {
        String json = '{"@type":"' + TestBigIntegerField.class.name + '","fromString":"314159","fromLong":314159,"fromBoolean":true,"fromStringObj":{"@type":"java.math.BigInteger","value":"314159"},"fromLongObj":{"@type":"java.math.BigInteger","value":314159},"fromBooleanObj":{"@type":"java.math.BigInteger","value":false},"fromBigDecObj":{"@type":"java.math.BigInteger","value":{"@type":"java.math.BigDecimal","value":9}},"fromBigIntObj":{"@type":"java.math.BigInteger","value":{"@type":"java.math.BigInteger","value":99}},"values":["314159",314159,true,{"@type":"java.math.BigInteger","value":"314159"},{"@type":"java.math.BigInteger","value":314159},{"@type":"java.math.BigInteger","value":true},{"@type":"java.math.BigInteger","value":{"@type":"java.math.BigInteger","value":999}}]}';
        TestBigIntegerField tbi = (TestBigIntegerField) TestUtil.readJsonObject(json)
        assertEquals(new BigInteger("314159"), tbi.fromString)
        assertEquals(new BigInteger("314159"), tbi.fromLong)
        assertEquals(new BigInteger("1"), tbi.fromBoolean)
        assertEquals(new BigInteger("314159"), tbi.fromStringObj)
        assertEquals(new BigInteger("314159"), tbi.fromLongObj)
        assertEquals(new BigInteger("0"), tbi.fromBooleanObj)
        assertEquals(new BigInteger("9"), tbi.fromBigDecObj)
        assertEquals(new BigInteger("99"), tbi.fromBigIntObj)

        assertEquals(new BigInteger("314159"), tbi.values[0])
        assertEquals(new BigInteger("314159"), tbi.values[1])
        assertEquals(new BigInteger("1"), tbi.values[2])
        assertEquals(new BigInteger("314159"), tbi.values[3])
        assertEquals(new BigInteger("314159"), tbi.values[4])
        assertEquals(new BigInteger("1"), tbi.values[5])
        assertEquals(new BigInteger("999"), tbi.values[6])

        Map map = JsonReader.jsonToMaps(json)
        json = TestUtil.getJsonString(map)
        tbi = (TestBigIntegerField) TestUtil.readJsonObject(json)
        assertEquals(new BigInteger("314159"), tbi.fromString)
        assertEquals(new BigInteger("314159"), tbi.fromLong)
        assertEquals(new BigInteger("1"), tbi.fromBoolean)
        assertEquals(new BigInteger("314159"), tbi.fromStringObj)
        assertEquals(new BigInteger("314159"), tbi.fromLongObj)
        assertEquals(new BigInteger("0"), tbi.fromBooleanObj)
        assertEquals(new BigInteger("9"), tbi.fromBigDecObj)
        assertEquals(new BigInteger("99"), tbi.fromBigIntObj)

        assertEquals(new BigInteger("314159"), tbi.values[0])
        assertEquals(new BigInteger("314159"), tbi.values[1])
        assertEquals(new BigInteger("1"), tbi.values[2])
        assertEquals(new BigInteger("314159"), tbi.values[3])
        assertEquals(new BigInteger("314159"), tbi.values[4])
        assertEquals(new BigInteger("1"), tbi.values[5])
        assertEquals(new BigInteger("999"), tbi.values[6])

        json = '{"@type":"' + TestBigIntegerField.class.name + '","fromString":""}'
        tbi = (TestBigIntegerField) TestUtil.readJsonObject(json)
        assertNull(tbi.fromString)
    }

    @Test
    public void testAssignBigIntegerStringToMaps() throws Exception
    {
        String json = '{"@type":"' + TestBigIntegerField.class.name + '","fromString":""}'
        Map map = JsonReader.jsonToMaps(json)
        assertNull(map.fromString)      // allowing "" to null out non-primitive fields in map-of-map mode
    }

    @Test
    public void testBigInteger() throws Exception
    {
        String s = "123456789012345678901234567890"
        BigInteger bigInt = new BigInteger(s)
        String json = TestUtil.getJsonString(bigInt)
        TestUtil.printLine("json=" + json)
        bigInt = (BigInteger) TestUtil.readJsonObject(json)
        assertTrue(bigInt.equals(new BigInteger(s)))
    }

    @Test
    public void testBigIntegerInArray() throws Exception
    {
        String s = "123456789012345678901234567890"
        BigInteger bigInt = new BigInteger(s)
        Object[] bigInts = [bigInt, bigInt] as Object[]
        BigInteger[] typedBigInts = [bigInt, bigInt] as BigInteger[]
        String json = TestUtil.getJsonString(bigInts)
        TestUtil.printLine("json=" + json)
        bigInts = (Object[]) TestUtil.readJsonObject(json)
        assertTrue(bigInts.length == 2)
        assertNotSame(bigInts[0], bigInts[1])
        assertTrue(new BigInteger(s).equals(bigInts[0]))
        json = TestUtil.getJsonString(typedBigInts)
        TestUtil.printLine("json=" + json)
        assertTrue(typedBigInts.length == 2)
        assertTrue(typedBigInts[0] == typedBigInts[1])
        assertTrue(new BigInteger(s).equals(typedBigInts[0]))
    }

    @Test
    public void testBigIntegerInCollection() throws Exception
    {
        String s = "123456789012345678901234567890"
        BigInteger bigInt = new BigInteger(s)
        List list = new ArrayList()
        list.add(bigInt)
        list.add(bigInt)
        String json = TestUtil.getJsonString(list)
        TestUtil.printLine("json=" + json)
        list = (List) TestUtil.readJsonObject(json)
        assertTrue(list.size() == 2)
        assertTrue(list.get(0).equals(new BigInteger(s)))
        assertNotSame(list.get(0), list.get(1))
    }

    @Test
    public void testNumTooBig() throws Exception
    {
        String json = '1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890' +
                '1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890' +
                '1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890'
        try
        {
            TestUtil.readJsonObject(json)
        }
        catch (IOException e)
        {
            assert e.message.toLowerCase().contains("too many digits")
        }
    }

    @Test
    public void testHugeBigInteger() throws Exception
    {
        String json = '{"@type":"java.math.BigInteger","value":"' +
                '1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890' +
                '1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"}'
        BigInteger x = (BigInteger) TestUtil.readJsonObject(json)
        assertEquals(new BigInteger('1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890' +
                '1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890'), x)
    }

    @Test
    public void testBigNumberParsers() throws Exception
    {
        assertNull(JsonReader.bigIntegerFrom(null))
        assertNull(JsonReader.bigDecimalFrom(null))

        try
        {
            JsonReader.bigIntegerFrom("Glock")
            fail()
        }
        catch(Exception ignored)
        { }

        try
        {
            JsonReader.bigDecimalFrom("Glock")
            fail()
        }
        catch(Exception ignored)
        { }

        try
        {
            JsonReader.bigIntegerFrom(new Date())
            fail()
        }
        catch(Exception ignored)
        { }

        try
        {
            JsonReader.bigDecimalFrom(new Date())
            fail()
        }
        catch(Exception ignored)
        { }

        BigInteger bi = JsonReader.bigIntegerFrom(3.14)
        assertEquals(3, bi.intValue())
    }
}
