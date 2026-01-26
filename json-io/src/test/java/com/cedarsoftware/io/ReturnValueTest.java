package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
class ReturnValueTest
{
    @Test
    void testReturnAsJsonObjectWithDoubleJsonPrimitive()
    {
        String json = "45.7";
        Object x = TestUtil.toMaps(json, null).asClass(null);
        assert x instanceof Double;
        assertEquals(45.7d, x);

        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(null);
        assert x instanceof Double;
        assertEquals(45.7d, x);

        json = "\"45.7\"";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(double.class);
        assert x instanceof Double;
        assertEquals(45.7d, x);

        json = "true";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(Double.class);
        assert x instanceof Double;
        assertEquals(1.0d, x);
    }

    @Test
    void testReturnAsJsonObjectWithLongJsonPrimitive()
    {
        String json = "1234567890123456";
        Object x = TestUtil.toMaps(json, null).asClass(null);
        assert x instanceof Long;
        assertEquals(1234567890123456L, x);

        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(null);
        assert x instanceof Long;
        assertEquals(1234567890123456L, x);

        json = "\"" + json + "\"";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(long.class);
        assert x instanceof Long;
        assertEquals(1234567890123456L, x);

        json = "true";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(Long.class);
        assert x instanceof Long;
        assertEquals(1L, x);
    }

    @Test
    void testReturnAsJsonObjectWithAtomicLongJsonPrimitive()
    {
        String json = "1234567890123456";
        Object x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJsonObjects().build()).asClass(AtomicLong.class);
        assert x instanceof AtomicLong;
        assertEquals(1234567890123456L, ((AtomicLong)x).get());

        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(AtomicLong.class);
        assert x instanceof AtomicLong;
        assertEquals(1234567890123456L, ((AtomicLong)x).get());

        json = "\"" + json + "\"";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(AtomicLong.class);
        assert x instanceof AtomicLong;
        assertEquals(1234567890123456L, ((AtomicLong)x).get());

        json = "true";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(AtomicLong.class);
        assert x instanceof AtomicLong;
        assertEquals(1L, ((AtomicLong)x).get());
    }

    @Test
    void testReturnAsJsonObjectWithStringJsonPrimitive()
    {
        String json = "\"quick brown fox\"";
        Object x = TestUtil.toMaps(json, null).asClass(null);
        assert x instanceof String;
        assertEquals("quick brown fox", x);

        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(null);
        assert x instanceof String;
        assertEquals("quick brown fox", x);

        json = "42";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(String.class);
        assert x instanceof String;
        assertEquals("42", x);

        json = "true";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(String.class);
        assert x instanceof String;
        assertEquals("true", x);
    }

    @Test
    void testReturnAsJsonObjectWithBooleanJsonPrimitive()
    {
        String json = "true";
        Object x = TestUtil.toMaps(json, null).asClass(null);
        assert x instanceof Boolean;
        assertEquals(true, x);

        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(null);
        assert x instanceof Boolean;
        assertEquals(true, x);

        json = "\"true\"";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(boolean.class);
        assert x instanceof Boolean;
        assertEquals(true, x);

        json = "17.3";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(Boolean.class);
        assert x instanceof Boolean;
        assertEquals(true, x);

        json = "0.0";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(Boolean.class);
        assert x instanceof Boolean;
        assertEquals(false, x);
    }

    @Test
    void testReturnAsJsonObjectWithAtomicBooleanJsonPrimitive()
    {
        String json = "true";
        Object x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJsonObjects().build()).asClass(AtomicBoolean.class);
        assert x instanceof AtomicBoolean;
        assertEquals(true, ((AtomicBoolean)x).get());

        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(AtomicBoolean.class);
        assert x instanceof AtomicBoolean;
        assertEquals(true, ((AtomicBoolean)x).get());

        json = "\"true\"";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(AtomicBoolean.class);
        assert x instanceof AtomicBoolean;
        assertEquals(true, ((AtomicBoolean)x).get());

        json = "17.3";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(AtomicBoolean.class);
        assert x instanceof AtomicBoolean;
        assertEquals(true, ((AtomicBoolean)x).get());

        json = "0.0";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(AtomicBoolean.class);
        assert x instanceof AtomicBoolean;
        assertEquals(false, ((AtomicBoolean)x).get());
    }

    @Test
    void testReturnAsJsonObjectWithBigIntegerJsonPrimitive()
    {
        String json = "12345678901235678901234567890";
        Object x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJsonObjects().integerTypeBoth().build()).asClass(null);
        assert x instanceof BigInteger;
        assertEquals(new BigInteger(json), x);

        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().integerTypeBoth().build()).asClass(null);
        assert x instanceof BigInteger;
        assertEquals(new BigInteger(json), x);

        json = "\"" + json + "\"";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().integerTypeBigInteger().build()).asClass(BigInteger.class);
        assert x instanceof BigInteger;
        assertEquals(new BigInteger("12345678901235678901234567890"), x);

        json = "true";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(BigInteger.class);
        assert x instanceof BigInteger;
        assertEquals(BigInteger.valueOf(1), x);

        json = "7.7";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(BigInteger.class);
        assert x instanceof BigInteger;
        assertEquals(BigInteger.valueOf(7), x);
    }

    @Test
    void testReturnAsJsonObjectWithBigIntegerJsonPrimitiveForceToLong()
    {
        String json = "12345678901235678901234567890";
        Object x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJsonObjects().integerTypeLong().build()).asClass(Long.class);
        assert x instanceof Long;
        assertEquals(5098844603236747986L, x);    // Wrap around because forced to Long

        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().integerTypeLong().build()).asClass(Long.class);
        assert x instanceof Long;
        assertEquals(5098844603236747986L, x);  // Wrap around because forced to Long
    }

    @Test
    void testReturnAsJsonObjectWithBigDecimalJsonPrimitive()
    {
        String json = "12345678901235678901234567890.12345678901235678901234567890";
        Object x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJsonObjects().floatPointBoth().build()).asClass(null);
        assert x instanceof BigDecimal;
        assertEquals(new BigDecimal(json), x);

        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().floatPointBigDecimal().build()).asClass(null);
        assert x instanceof BigDecimal;
        assertEquals(new BigDecimal(json), x);

        String quotedNum = "\"" + json + "\"";
        x = TestUtil.toJava(quotedNum, new ReadOptionsBuilder().returnAsJavaObjects().floatPointBoth().build()).asClass(BigDecimal.class);
        assert x instanceof BigDecimal;
        assertEquals(new BigDecimal(json), x);

        json = "true";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(BigDecimal.class);
        assert x instanceof BigDecimal;
        assertEquals(new BigDecimal(1), x);
    }

    @Test
    void testReturnAsJsonObjectWithBigDecimalJsonPrimitiveForcedToDouble()
    {
        String json = "12345678901235678901234567890.12345678901235678901234567890";
        Object x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJsonObjects().build()).asClass(double.class);
        assert x instanceof Double;
        assertEquals(1.2345678901235679E28, x);      // Approximated because of Force to Double

        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(double.class);
        assert x instanceof Double;
        assertEquals(1.2345678901235679E28, x);     // Approximated because of Force to Double

        json = "\"" + json + "\"";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(double.class);
        assert x instanceof Double;
        assertEquals(1.2345678901235679E28, x);     // Approximated because of Force to Double

        json = "true";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(double.class);
        assert x instanceof Double;
        assertEquals(1.0, x);     // Approximated because of Force to Double
    }

    @Test
    void testReturnAsJsonObjectWithIntegerJsonPrimitive()
    {
        String json = "1234.7";
        Object x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJsonObjects().build()).asClass(Integer.class);
        assert x instanceof Integer;
        assertEquals(1234, x);

        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(int.class);
        assert x instanceof Integer;
        assertEquals(1234, x);

        json = "\"" + json + "\"";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(int.class);
        assert x instanceof Integer;
        assertEquals(1234, x);

        json = "true";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(Integer.class);
        assert x instanceof Integer;
        assertEquals(1, x);
    }

    @Test
    void testReturnAsJsonObjectWithAtomicIntegerJsonPrimitive()
    {
        String json = "1234.7";
        Object x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJsonObjects().build()).asClass(AtomicInteger.class);
        assert x instanceof AtomicInteger;
        assertEquals(1234, ((AtomicInteger)x).get());

        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(AtomicInteger.class);
        assert x instanceof AtomicInteger;
        assertEquals(1234, ((AtomicInteger)x).get());

        json = "\"" + json + "\"";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(AtomicInteger.class);
        assert x instanceof AtomicInteger;
        assertEquals(1234, ((AtomicInteger)x).get());

        json = "true";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(AtomicInteger.class);
        assert x instanceof AtomicInteger;
        assertEquals(1, ((AtomicInteger)x).get());
    }

    @Test
    void testReturnAsJsonObjectWithShortJsonPrimitive()
    {
        String json = "1234.7";
        Object x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJsonObjects().build()).asClass(short.class);
        assert x instanceof Short;
        assertEquals((short) 1234, x);

        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(Short.class);
        assert x instanceof Short;
        assertEquals((short) 1234, x);

        json = "\"" + json + "\"";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(Short.class);
        assert x instanceof Short;
        assertEquals((short) 1234, x);

        json = "true";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(short.class);
        assert x instanceof Short;
        assertEquals((short) 1, x);
    }

    @Test
    void testReturnAsJsonObjectWithByteJsonPrimitive()
    {
        String json = "16.9";
        Object x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJsonObjects().build()).asClass(byte.class);
        assert x instanceof Byte;
        assertEquals((byte) 16, x);

        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(byte.class);
        assert x instanceof Byte;
        assertEquals((byte) 16, x);

        json = "\"" + json + "\"";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(byte.class);
        assert x instanceof Byte;
        assertEquals((byte) 16, x);

        json = "true";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(byte.class);
        assert x instanceof Byte;
        assertEquals((byte) 1, x);
    }

    @Test
    void testReturnAsJsonObjectWithFloatJsonPrimitive()
    {
        String json = "16.9";
        Object x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJsonObjects().build()).asClass(float.class);
        assert x instanceof Float;
        assertEquals(16.9f, x);

        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(float.class);
        assert x instanceof Float;
        assertEquals(16.9f, x);

        json = "\"" + json + "\"";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(Float.class);
        assert x instanceof Float;
        assertEquals(16.9f, x);

        json = "true";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(Float.class);
        assert x instanceof Float;
        assertEquals(1.0f, x);
    }

    @Test
    void testReturnAsJsonObjectWithCharacterJsonPrimitive()
    {
        String json = "\"j\"";
        Object x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJsonObjects().build()).asClass(char.class);
        assert x instanceof Character;
        assertEquals('j', x);

        json = "65";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(char.class);
        assert x instanceof Character;
        assertEquals('A', x);

        json = "\"\\uDE4A\"";

        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(Character.class);
        assert x instanceof Character;
        assertEquals('\uDE4A', x);

        json = "true";
        x = TestUtil.toJava(json, new ReadOptionsBuilder().returnAsJavaObjects().build()).asClass(Character.class);
        assert x instanceof Character;
        assertEquals((char) 1, x);
    }
}
