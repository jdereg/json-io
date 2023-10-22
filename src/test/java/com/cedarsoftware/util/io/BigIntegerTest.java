package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

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
class BigIntegerTest
{
    static class TestBigIntegerField
    {
        BigInteger fromString;
        BigInteger fromLong;
        BigInteger fromBoolean;
        BigInteger fromStringObj;
        BigInteger fromLongObj;
        BigInteger fromBooleanObj;
        BigInteger fromBigDecObj;
        BigInteger fromBigIntObj;
        BigInteger[] values;
    }

    @Test
    void testAssignBigInteger()
    {
        String json = "{\"@type\":\"" + TestBigIntegerField.class.getName() + "\",\"fromString\":\"314159\",\"fromLong\":314159,\"fromBoolean\":true,\"fromStringObj\":{\"@type\":\"java.math.BigInteger\",\"value\":\"314159\"},\"fromLongObj\":{\"@type\":\"java.math.BigInteger\",\"value\":314159},\"fromBooleanObj\":{\"@type\":\"java.math.BigInteger\",\"value\":false},\"fromBigDecObj\":{\"@type\":\"java.math.BigInteger\",\"value\":{\"@type\":\"java.math.BigDecimal\",\"value\":9}},\"fromBigIntObj\":{\"@type\":\"java.math.BigInteger\",\"value\":{\"@type\":\"java.math.BigInteger\",\"value\":99}},\"values\":[\"314159\",314159,true,{\"@type\":\"java.math.BigInteger\",\"value\":\"314159\"},{\"@type\":\"java.math.BigInteger\",\"value\":314159},{\"@type\":\"java.math.BigInteger\",\"value\":true},{\"@type\":\"java.math.BigInteger\",\"value\":{\"@type\":\"java.math.BigInteger\",\"value\":999}}]}";
        TestBigIntegerField tbi = TestUtil.readJsonObject(json);
        assertEquals(new BigInteger("314159"), tbi.fromString);
        assertEquals(new BigInteger("314159"), tbi.fromLong);
        assertEquals(new BigInteger("1"), tbi.fromBoolean);
        assertEquals(new BigInteger("314159"), tbi.fromStringObj);
        assertEquals(new BigInteger("314159"), tbi.fromLongObj);
        assertEquals(new BigInteger("0"), tbi.fromBooleanObj);
        assertEquals(new BigInteger("9"), tbi.fromBigDecObj);
        assertEquals(new BigInteger("99"), tbi.fromBigIntObj);

        assertEquals(new BigInteger("314159"), tbi.values[0]);
        assertEquals(new BigInteger("314159"), tbi.values[1]);
        assertEquals(new BigInteger("1"), tbi.values[2]);
        assertEquals(new BigInteger("314159"), tbi.values[3]);
        assertEquals(new BigInteger("314159"), tbi.values[4]);
        assertEquals(new BigInteger("1"), tbi.values[5]);
        assertEquals(new BigInteger("999"), tbi.values[6]);

        Map<String, Object> args = new HashMap<>();
        args.put(JsonReader.USE_MAPS, true);
        Map map = JsonReader.jsonToJava(json, args);
        json = TestUtil.getJsonString(map);
        tbi = TestUtil.readJsonObject(json);
        assertEquals(new BigInteger("314159"), tbi.fromString);
        assertEquals(new BigInteger("314159"), tbi.fromLong);
        assertEquals(new BigInteger("1"), tbi.fromBoolean);
        assertEquals(new BigInteger("314159"), tbi.fromStringObj);
        assertEquals(new BigInteger("314159"), tbi.fromLongObj);
        assertEquals(new BigInteger("0"), tbi.fromBooleanObj);
        assertEquals(new BigInteger("9"), tbi.fromBigDecObj);
        assertEquals(new BigInteger("99"), tbi.fromBigIntObj);

        assertEquals(new BigInteger("314159"), tbi.values[0]);
        assertEquals(new BigInteger("314159"), tbi.values[1]);
        assertEquals(new BigInteger("1"), tbi.values[2]);
        assertEquals(new BigInteger("314159"), tbi.values[3]);
        assertEquals(new BigInteger("314159"), tbi.values[4]);
        assertEquals(new BigInteger("1"), tbi.values[5]);
        assertEquals(new BigInteger("999"), tbi.values[6]);

        json = "{\"@type\":\"" + TestBigIntegerField.class.getName() + "\",\"fromString\":\"\"}";
        tbi = TestUtil.readJsonObject(json);
        assertNull(tbi.fromString);
    }

    @Test
    void testAssignBigIntegerStringToMaps()
    {
        String json = "{\"@type\":\"" + TestBigIntegerField.class.getName() + "\",\"fromString\":\"\"}";
        Map<String, Object> args = new HashMap<>();
        args.put(JsonReader.USE_MAPS, true);
        Map map = JsonReader.jsonToJava(json, args);
        assertNull(map.get("fromString"));      // allowing "" to null out non-primitive fields in map-of-map mode
    }

    @Test
    void testBigInteger()
    {
        String s = "123456789012345678901234567890";
        BigInteger bigInt = new BigInteger(s);
        String json = TestUtil.getJsonString(bigInt);
        TestUtil.printLine("json=" + json);
        bigInt = TestUtil.readJsonObject(json);
        assertEquals(bigInt, new BigInteger(s));
    }

    @Test
    void testBigIntegerInArray()
    {
        String s = "123456789012345678901234567890";
        BigInteger bigInt = new BigInteger(s);
        Object[] bigInts = new Object[] {bigInt, bigInt};
        BigInteger[] typedBigInts = new BigInteger[] {bigInt, bigInt};
        String json = TestUtil.getJsonString(bigInts);
        TestUtil.printLine("json=" + json);
        bigInts = TestUtil.readJsonObject(json);
        assertEquals(2, bigInts.length);
        assertNotSame(bigInts[0], bigInts[1]);
        assertEquals(new BigInteger(s), bigInts[0]);
        json = TestUtil.getJsonString(typedBigInts);
        TestUtil.printLine("json=" + json);
        assertEquals(2, typedBigInts.length);
        assertSame(typedBigInts[0], typedBigInts[1]);
        assertEquals(new BigInteger(s), typedBigInts[0]);
    }

    @Test
    void testBigIntegerInCollection()
    {
        String s = "123456789012345678901234567890";
        BigInteger bigInt = new BigInteger(s);
        List<BigInteger> list = new ArrayList<>();
        list.add(bigInt);
        list.add(bigInt);
        String json = TestUtil.getJsonString(list);
        TestUtil.printLine("json=" + json);
        list = TestUtil.readJsonObject(json);
        assertEquals(2, list.size());
        assertEquals(list.get(0), new BigInteger(s));
        assertNotSame(list.get(0), list.get(1));
    }

    @Test
    void testNumTooBig()
    {
        String json = "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890";
        try
        {
            TestUtil.readJsonObject(json);
            fail();
        }
        catch (JsonIoException e)
        {
            assert e.getCause() instanceof NumberFormatException;
        }
    }

    @Test
    void testHugeBigInteger()
    {
        String json = "{\"@type\":\"java.math.BigInteger\",\"value\":\"" +
                "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890\"}";
        BigInteger x = TestUtil.readJsonObject(json);
        assertEquals(new BigInteger("1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"), x);
    }

    @Test
    void testBigNumberParsers()
    {
        assertNull(Readers.bigIntegerFrom(null));
        assertNull(Readers.bigDecimalFrom(null));

        try
        {
            Readers.bigIntegerFrom("Glock");
            fail();
        }
        catch(Exception ignore)
        { }

        try
        {
            Readers.bigDecimalFrom("Glock");
            fail();
        }
        catch (Exception ignore)
        { }

        try
        {
            Readers.bigIntegerFrom(new Date());
            fail();
        }
        catch (Exception ignore)
        { }

        BigInteger bi = Readers.bigIntegerFrom(3.14);
        assertEquals(3, bi.intValue());
    }
}
