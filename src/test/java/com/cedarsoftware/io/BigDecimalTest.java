package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.io.JsonObject.REF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

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
class BigDecimalTest
{
    static class TestBigDecimalField
    {
        BigDecimal fromString;
        BigDecimal fromLong;
        BigDecimal fromDouble;
        BigDecimal fromBoolean;
        BigDecimal fromStringObj;
        BigDecimal fromLongObj;
        BigDecimal fromDoubleObj;
        BigDecimal fromBooleanObj;
        BigDecimal fromBigIntObj;
        BigDecimal fromBigDecObj;
        BigDecimal[] values;
    }

    @Test
    void testAssignBigDecimal()
    {
        String json = "{\"@type\":\"" + TestBigDecimalField.class.getName() + "\",\"fromString\":\"3.14159\",\"fromLong\":314159,\"fromDouble\":3.14159,\"fromBoolean\":true,\"fromStringObj\":{\"@type\":\"java.math.BigDecimal\",\"value\":\"3.14159\"},\"fromLongObj\":{\"@type\":\"java.math.BigDecimal\",\"value\":314159},\"fromDoubleObj\":{\"@type\":\"java.math.BigDecimal\",\"value\":3.14159},\"fromBooleanObj\":{\"@type\":\"java.math.BigDecimal\",\"value\":false},\"fromBigIntObj\":{\"@type\":\"java.math.BigDecimal\",\"value\":{\"@type\":\"java.math.BigInteger\",\"value\":72}},\"fromBigDecObj\":{\"@type\":\"java.math.BigDecimal\",\"value\":{\"@type\":\"java.math.BigDecimal\",\"value\":72.1}},\"values\":[\"3.14159\",314159,3.14159,true,{\"@type\":\"java.math.BigDecimal\",\"value\":\"3.14159\"},{\"@type\":\"java.math.BigDecimal\",\"value\":314159},{\"@type\":\"java.math.BigDecimal\",\"value\":3.14159},{\"@type\":\"java.math.BigDecimal\",\"value\":true},{\"@type\":\"java.math.BigDecimal\",\"value\":{\"@type\":\"java.math.BigDecimal\",\"value\":72.72}}]}";
        TestBigDecimalField tbd = TestUtil.toObjects(json, null);
        assertEquals(new BigDecimal("3.14159"), tbd.fromString);
        assertEquals(new BigDecimal(314159), tbd.fromLong);
        assertEquals(new BigDecimal("3.14159"), tbd.fromDouble);
        assertEquals(new BigDecimal(1), tbd.fromBoolean);
        assertEquals(new BigDecimal("3.14159"), tbd.fromStringObj);
        assertEquals(new BigDecimal(314159), tbd.fromLongObj);
        assertEquals(new BigDecimal("3.14159"), tbd.fromDoubleObj);
        assertEquals(new BigDecimal(0), tbd.fromBooleanObj);
        assertEquals(new BigDecimal(72), tbd.fromBigIntObj);
        assertEquals(new BigDecimal("72.1"), tbd.fromBigDecObj);

        assertEquals(new BigDecimal("3.14159"), tbd.values[0]);
        assertEquals(new BigDecimal(314159), tbd.values[1]);
        assertEquals(new BigDecimal("3.14159"), tbd.values[2]);
        assertEquals(new BigDecimal(1), tbd.values[3]);
        assertEquals(new BigDecimal("3.14159"), tbd.values[4]);
        assertEquals(new BigDecimal(314159), tbd.values[5]);
        assertEquals(new BigDecimal("3.14159"), tbd.values[6]);
        assertEquals(new BigDecimal(1), tbd.values[7]);
        assertEquals(new BigDecimal("72.72"), tbd.values[8]);

        Map map = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        json = TestUtil.toJson(map);
        tbd = TestUtil.toObjects(json, null);
        assertEquals(new BigDecimal("3.14159"), tbd.fromString);
        assertEquals(new BigDecimal(314159), tbd.fromLong);
        assertEquals(new BigDecimal("3.14159"), tbd.fromDouble);
        assertEquals(new BigDecimal(1), tbd.fromBoolean);
        assertEquals(new BigDecimal("3.14159"), tbd.fromStringObj);
        assertEquals(new BigDecimal(314159), tbd.fromLongObj);
        assertEquals(new BigDecimal("3.14159"), tbd.fromDoubleObj);
        assertEquals(new BigDecimal(0), tbd.fromBooleanObj);
        assertEquals(new BigDecimal(72), tbd.fromBigIntObj);
        assertEquals(new BigDecimal("72.1"), tbd.fromBigDecObj);

        assertEquals(new BigDecimal("3.14159"), tbd.values[0]);
        assertEquals(new BigDecimal(314159), tbd.values[1]);
        assertEquals(new BigDecimal("3.14159"), tbd.values[2]);
        assertEquals(new BigDecimal(1), tbd.values[3]);
        assertEquals(new BigDecimal("3.14159"), tbd.values[4]);
        assertEquals(new BigDecimal(314159), tbd.values[5]);
        assertEquals(new BigDecimal("3.14159"), tbd.values[6]);
        assertEquals(new BigDecimal(1), tbd.values[7]);
        assertEquals(new BigDecimal("72.72"), tbd.values[8]);

        json = "{\"@type\":\"" + TestBigDecimalField.class.getName() + "\",\"fromString\":\"\"}";
        tbd = TestUtil.toObjects(json, null);
        assert tbd.fromString.longValue() == 0;
    }

    @Test
    void testBigDecimal0()
    {
        TestBigDecimalField bigDecs = new TestBigDecimalField();
        bigDecs.fromString = new BigDecimal("123.12");
        bigDecs.fromLong = new BigDecimal("0");
        bigDecs.fromDouble = new BigDecimal("0.0");
        bigDecs.fromBoolean = bigDecs.fromDouble;
        String json = TestUtil.toJson(bigDecs);
        assertFalse(json.contains(REF));
    }

    @Test
    void testBigDecimal()
    {
        String s = "123456789012345678901234567890.123456789012345678901234567890";
        BigDecimal bigDec = new BigDecimal(s);
        BigDecimal actual = TestUtil.serializeDeserialize(bigDec);
        assertThat(actual).isEqualTo(bigDec);
    }

    @Test
    void testBigDecimalInArray()
    {
        String s = "123456789012345678901234567890.123456789012345678901234567890";
        BigDecimal bigDec = new BigDecimal(s);
        Object[] bigDecs = new Object[] {bigDec, bigDec};
        BigDecimal[] typedBigDecs = new BigDecimal[] {bigDec, bigDec};
        String json = TestUtil.toJson(bigDecs);
        TestUtil.printLine("json=" + json);

        bigDecs = TestUtil.toObjects(json, null);
        assertEquals(2, bigDecs.length);
        assertNotSame(bigDecs[0], bigDecs[1]);
        assertEquals(new BigDecimal(s), bigDecs[0]);
        json = TestUtil.toJson(typedBigDecs);
        TestUtil.printLine("json=" + json);
        assertEquals(2, typedBigDecs.length);
        assertSame(typedBigDecs[0], typedBigDecs[1]);
        assertEquals(new BigDecimal(s), typedBigDecs[0]);
    }

    @Test
    void testBigDecimalInCollection()
    {
        String s = "-123456789012345678901234567890.123456789012345678901234567890";
        BigDecimal bigDec = new BigDecimal(s);
        List<BigDecimal> list = new ArrayList<>();
        list.add(bigDec);
        list.add(bigDec);
        String json = TestUtil.toJson(list);
        TestUtil.printLine("json=" + json);
        list = TestUtil.toObjects(json, null);
        assertEquals(2, list.size());
        assertEquals(list.get(0), new BigDecimal(s));
        // BigDecimal is treated like primitives (immutables), instances are not shared
        // (safe to do because the immutable object cannot change, friendly to the JSON file)
        assertNotSame(list.get(0), list.get(1));
    }
}
