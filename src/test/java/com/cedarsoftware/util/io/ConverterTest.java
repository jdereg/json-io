package com.cedarsoftware.util.io;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.io.Converter.convert;
import static com.cedarsoftware.util.io.Converter.localDateTimeToMillis;
import static com.cedarsoftware.util.io.Converter.localDateToMillis;
import static com.cedarsoftware.util.io.Converter.zonedDateTimeToMillis;
import static com.cedarsoftware.util.io.ConverterTest.fubar.bar;
import static com.cedarsoftware.util.io.ConverterTest.fubar.foo;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com) & Ken Partlow
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
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
class ConverterTest
{
    enum fubar
    {
        foo, bar, baz, quz
    }

    @Test
    void testConstructorIsPrivateAndClassIsFinal() throws Exception
    {
        Class<?> c = Converter.class;
        assertEquals(Modifier.FINAL, c.getModifiers() & Modifier.FINAL);

        Constructor con = c.getDeclaredConstructor();
        assertEquals(Modifier.PRIVATE, con.getModifiers() & Modifier.PRIVATE);
        con.setAccessible(true);

        assertNotNull(con.newInstance());
    }

    @Test
    void testByte()
    {
        Byte x = convert("-25", byte.class);
        assert -25 == x;
        x = convert("24", Byte.class);
        assert 24 == x;

        x = convert((byte) 100, byte.class);
        assert 100 == x;
        x = convert((byte) 120, Byte.class);
        assert 120 == x;

        x = convert(new BigDecimal("100"), byte.class);
        assert 100 == x;
        x = convert(new BigInteger("120"), Byte.class);
        assert 120 == x;

        Byte value = convert(true, Byte.class);
        assert value == 1;
        assert (byte)1 == convert(true, Byte.class);
        assert (byte)0 == convert(false, byte.class);

        assert (byte)25 == convert(new AtomicInteger(25), byte.class);
        assert (byte)100 == convert(new AtomicLong(100L), byte.class);
        assert (byte)1 == convert(new AtomicBoolean(true), byte.class);
        assert (byte)0 == convert(new AtomicBoolean(false), byte.class);

        assertThatThrownBy(() -> convert("11.5", byte.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value: 11.5 not parseable as a byte value or outside -128");

        try
        {
            convert(TimeZone.getDefault(), byte.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("unsupported target type 'byte'"));
        }

        try
        {
            convert("45badNumber", byte.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("value: 45badnumber not parseable as a byte value or outside -128"));
        }

        try
        {
            convert("257", byte.class);
            fail();
        }
        catch (IllegalArgumentException e) { }
    }

    @Test
    void testShort()
    {
        Short x = convert("-25000", short.class);
        assert -25000 == x;
        x = convert("24000", Short.class);
        assert 24000 == x;

        x = convert((short) 10000, short.class);
        assert 10000 == x;
        x = convert((short) 20000, Short.class);
        assert 20000 == x;

        x = convert(new BigDecimal("10000"), short.class);
        assert 10000 == x;
        x = convert(new BigInteger("20000"), Short.class);
        assert 20000 == x;

        assert (short)1 == convert(true, short.class);
        assert (short)0 == convert(false, Short.class);

        assert (short)25 == convert(new AtomicInteger(25), short.class);
        assert (short)100 == convert(new AtomicLong(100L), Short.class);
        assert (short)1 == convert(new AtomicBoolean(true), Short.class);
        assert (short)0 == convert(new AtomicBoolean(false), Short.class);

        assertThatThrownBy(() -> convert("11.5", short.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value: 11.5 not parseable as a short value or outside -32768 to 32767");

        try
        {
            convert(TimeZone.getDefault(), short.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("unsupported target type 'short'"));
        }

        try
        {
            convert("45badNumber", short.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("value: 45badnumber not parseable as a short value or outside -32768"));
        }

        try
        {
            convert("33000", short.class);
            fail();
        }
        catch (IllegalArgumentException e) { }

    }

    @Test
    void testInt()
    {
        Integer x = convert("-450000", int.class);
        assertEquals((Object) (-450000), x);
        x = convert("550000", Integer.class);
        assertEquals((Object) 550000, x);

        x = convert(100000, int.class);
        assertEquals((Object) 100000, x);
        x = convert(200000, Integer.class);
        assertEquals((Object) 200000, x);

        x = convert(new BigDecimal("100000"), int.class);
        assertEquals((Object) 100000, x);
        x = convert(new BigInteger("200000"), Integer.class);
        assertEquals((Object) 200000, x);

        assert 1 == convert(true, Integer.class);
        assert 0 == convert(false, int.class);

        assert 25 == convert(new AtomicInteger(25), int.class);
        assert 100 == convert(new AtomicLong(100L), Integer.class);
        assert 1 == convert(new AtomicBoolean(true), Integer.class);
        assert 0 == convert(new AtomicBoolean(false), Integer.class);

        assertThatThrownBy(() -> convert("11.5", int.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value: 11.5 not parseable as an integer value or outside -214");
        try
        {
            convert(TimeZone.getDefault(), int.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("unsupported target type 'integer'"));
        }

        try
        {
            convert("45badNumber", int.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("value: 45badnumber not parseable as an integer value or outside -214"));
        }

        try
        {
            convert("2147483649", int.class);
            fail();
        }
        catch (IllegalArgumentException e) { }
    }

    @Test
    void testLong()
    {
        Long x = convert("-450000", long.class);
        assertEquals((Object)(-450000L), x);
        x = convert("550000", Long.class);
        assertEquals((Object)550000L, x);

        x = convert(100000L, long.class);
        assertEquals((Object)100000L, x);
        x = convert(200000L, Long.class);
        assertEquals((Object)200000L, x);

        x = convert(new BigDecimal("100000"), long.class);
        assertEquals((Object)100000L, x);
        x = convert(new BigInteger("200000"), Long.class);
        assertEquals((Object)200000L, x);

        assert (long)1 == convert(true, long.class);
        assert (long)0 == convert(false, Long.class);

        Date now = new Date();
        long now70 = now.getTime();
        assert now70 == convert(now, long.class);

        Calendar today = Calendar.getInstance();
        now70 = today.getTime().getTime();
        assert now70 == convert(today, Long.class);

        LocalDate localDate = LocalDate.now();
        now70 = localDate.toEpochDay();
        assert now70 == convert(localDate, long.class);

        assert 25L == convert(new AtomicInteger(25), long.class);
        assert 100L == convert(new AtomicLong(100L), Long.class);
        assert 1L == convert(new AtomicBoolean(true), Long.class);
        assert 0L == convert(new AtomicBoolean(false), Long.class);

        assertThatThrownBy(() -> convert("11.5", long.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value: 11.5 not parseable as a long value or outside -922");

        try
        {
            convert(TimeZone.getDefault(), long.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("unsupported target type 'long'"));
        }

        try
        {
            convert("45badNumber", long.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("value: 45badnumber not parseable as a long value or outside -922"));
        }
    }

    @Test
    void testAtomicLong()
    {
        AtomicLong x = convert("-450000", AtomicLong.class);
        assertEquals(-450000L, x.get());
        x = convert("550000", AtomicLong.class);
        assertEquals(550000L, x.get());

        x = convert(100000L, AtomicLong.class);
        assertEquals(100000L, x.get());
        x = convert(200000L, AtomicLong.class);
        assertEquals(200000L, x.get());

        x = convert(new BigDecimal("100000"), AtomicLong.class);
        assertEquals(100000L, x.get());
        x = convert(new BigInteger("200000"), AtomicLong.class);
        assertEquals(200000L, x.get());

        x = convert(true, AtomicLong.class);
        assertEquals((long)1, x.get());
        x = convert(false, AtomicLong.class);
        assertEquals((long)0, x.get());

        Date now = new Date();
        long now70 = now.getTime();
        x =  convert(now, AtomicLong.class);
        assertEquals(now70, x.get());

        Calendar today = Calendar.getInstance();
        now70 = today.getTime().getTime();
        x =  convert(today, AtomicLong.class);
        assertEquals(now70, x.get());

        x = convert(new AtomicInteger(25), AtomicLong.class);
        assertEquals(25L, x.get());
        x = convert(new AtomicLong(100L), AtomicLong.class);
        assertEquals(100L, x.get());
        x = convert(new AtomicBoolean(true), AtomicLong.class);
        assertEquals(1L, x.get());
        x = convert(new AtomicBoolean(false), AtomicLong.class);
        assertEquals(0L, x.get());

        try
        {
            convert(TimeZone.getDefault(), AtomicLong.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("unsupported target type 'atomiclong'"));
        }

        try
        {
            convert("45badNumber", AtomicLong.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().contains("Value: 45badNumber not parseable as a AtomicLong value or outside -922"));
        }
    }

    @Test
    void testString()
    {
        assertEquals("Hello", convert("Hello", String.class));
        assertEquals("25", convert(25.0d, String.class));
        assertEquals("3141592653589793300", convert(3.1415926535897932384626433e18, String.class));
        assertEquals("true", convert(true, String.class));
        assertEquals("J", convert('J', String.class));
        assertEquals("3.1415926535897932384626433", convert(new BigDecimal("3.1415926535897932384626433"), String.class));
        assertEquals("123456789012345678901234567890", convert(new BigInteger("123456789012345678901234567890"), String.class));
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(2015, 0, 17, 8, 34, 49);
        assertEquals("2015-01-17T08:34:49", convert(cal.getTime(), String.class));
        assertEquals("2015-01-17T08:34:49", convert(cal, String.class));

        assertEquals("25", convert(new AtomicInteger(25), String.class));
        assertEquals("100", convert(new AtomicLong(100L), String.class));
        assertEquals("true", convert(new AtomicBoolean(true), String.class));

        assertEquals("1.23456789", convert(1.23456789d, String.class));

        int x = 8;
        String s = convert(x, String.class);
        assert s.equals("8");
        // TODO: Add following test once we have preferred method of removing exponential notation, yet retain decimal separator
//        assertEquals("123456789.12345", convert(123456789.12345, String.class));

        try
        {
            convert(TimeZone.getDefault(), String.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("unsupported conversion, source type"));
        }

        try
        {
            convert(new HashMap<>(), HashMap.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("unsupported target type"));
        }

        try
        {
            convert(ZoneId.systemDefault(), String.class);
            fail();
        }
        catch (Exception e)
        {
            TestUtil.assertContainsIgnoreCase(e.getMessage(), "unsupported conversion, source type");
        }
    }

    @Test
    void testBigDecimal()
    {
        BigDecimal x = convert("-450000", BigDecimal.class);
        assertEquals(new BigDecimal("-450000"), x);

        assertEquals(new BigDecimal("3.14"), convert(new BigDecimal("3.14"), BigDecimal.class));
        assertEquals(new BigDecimal("8675309"), convert(new BigInteger("8675309"), BigDecimal.class));
        assertEquals(new BigDecimal("75"), convert((short) 75, BigDecimal.class));
        assertEquals(BigDecimal.ONE, convert(true, BigDecimal.class));
        assertSame(BigDecimal.ONE, convert(true, BigDecimal.class));
        assertEquals(BigDecimal.ZERO, convert(false, BigDecimal.class));
        assertSame(BigDecimal.ZERO, convert(false, BigDecimal.class));

        Date now = new Date();
        BigDecimal now70 = new BigDecimal(now.getTime());
        assertEquals(now70, convert(now, BigDecimal.class));

        Calendar today = Calendar.getInstance();
        now70 = new BigDecimal(today.getTime().getTime());
        assertEquals(now70, convert(today, BigDecimal.class));

        assertEquals(new BigDecimal(25), convert(new AtomicInteger(25), BigDecimal.class));
        assertEquals(new BigDecimal(100), convert(new AtomicLong(100L), BigDecimal.class));
        assertEquals(BigDecimal.ONE, convert(new AtomicBoolean(true), BigDecimal.class));
        assertEquals(BigDecimal.ZERO, convert(new AtomicBoolean(false), BigDecimal.class));

        try
        {
            convert(TimeZone.getDefault(), BigDecimal.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("unsupported target type 'bigdecimal"));
        }

        try
        {
            convert("45badNumber", BigDecimal.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("value: 45badnumber not parseable as a bigdecimal value"));
        }
    }

    @Test
    void testBigInteger()
    {
        BigInteger x = convert("-450000", BigInteger.class);
        assertEquals(new BigInteger("-450000"), x);

        assertEquals(new BigInteger("3"), convert(new BigDecimal("3.14"), BigInteger.class));
        assertEquals(new BigInteger("8675309"), convert(new BigInteger("8675309"), BigInteger.class));
        assertEquals(new BigInteger("75"), convert((short) 75, BigInteger.class));
        assertEquals(BigInteger.ONE, convert(true, BigInteger.class));
        assertSame(BigInteger.ONE, convert(true, BigInteger.class));
        assertEquals(BigInteger.ZERO, convert(false, BigInteger.class));
        assertSame(BigInteger.ZERO, convert(false, BigInteger.class));

        Date now = new Date();
        BigInteger now70 = new BigInteger(Long.toString(now.getTime()));
        assertEquals(now70, convert(now, BigInteger.class));

        Calendar today = Calendar.getInstance();
        now70 = new BigInteger(Long.toString(today.getTime().getTime()));
        assertEquals(now70, convert(today, BigInteger.class));

        assertEquals(new BigInteger("25"), convert(new AtomicInteger(25), BigInteger.class));
        assertEquals(new BigInteger("100"), convert(new AtomicLong(100L), BigInteger.class));
        assertEquals(BigInteger.ONE, convert(new AtomicBoolean(true), BigInteger.class));
        assertEquals(BigInteger.ZERO, convert(new AtomicBoolean(false), BigInteger.class));

        try {
            convert(TimeZone.getDefault(), BigInteger.class);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().toLowerCase().contains("unsupported target type 'biginteger'"));
        }

        try {
            convert("45badNumber", BigInteger.class);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().toLowerCase().contains("value: 45badnumber not parseable as a biginteger value"));
        }
    }

    @Test
    void testAtomicInteger()
    {
        AtomicInteger x =  convert("-450000", AtomicInteger.class);
        assertEquals(-450000, x.get());

        assertEquals(3, ( convert(new BigDecimal("3.14"), AtomicInteger.class)).get());
        assertEquals(8675309, (convert(new BigInteger("8675309"), AtomicInteger.class)).get());
        assertEquals(75, (convert((short) 75, AtomicInteger.class)).get());
        assertEquals(1, (convert(true, AtomicInteger.class)).get());
        assertEquals(0, (convert(false, AtomicInteger.class)).get());

        assertEquals(25, (convert(new AtomicInteger(25), AtomicInteger.class)).get());
        assertEquals(100, (convert(new AtomicLong(100L), AtomicInteger.class)).get());
        assertEquals(1, (convert(new AtomicBoolean(true), AtomicInteger.class)).get());
        assertEquals(0, (convert(new AtomicBoolean(false), AtomicInteger.class)).get());

        try
        {
            convert(TimeZone.getDefault(), AtomicInteger.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("unsupported target type 'atomicinteger'"));
        }

        try
        {
            convert("45badNumber", AtomicInteger.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("45badnumber"));
        }
    }

    @Test
    void testDate()
    {
        // Date to Date
        Date utilNow = new Date();
        Date coerced = convert(utilNow, Date.class);
        assertEquals(utilNow, coerced);
        assertFalse(coerced instanceof java.sql.Date);
        assert coerced != utilNow;

        // Date to java.sql.Date
        java.sql.Date sqlCoerced = convert(utilNow, java.sql.Date.class);
        assertEquals(utilNow, sqlCoerced);

        // java.sql.Date to java.sql.Date
        java.sql.Date sqlNow = new java.sql.Date(utilNow.getTime());
        sqlCoerced = convert(sqlNow, java.sql.Date.class);
        assertEquals(sqlNow, sqlCoerced);

        // java.sql.Date to Date
        coerced = convert(sqlNow, Date.class);
        assertEquals(sqlNow, coerced);
        assertFalse(coerced instanceof java.sql.Date);

        // Date to Timestamp
        Timestamp tstamp = convert(utilNow, Timestamp.class);
        assertEquals(utilNow, tstamp);

        // Timestamp to Date
        Date someDate = convert(tstamp, Date.class);
        assertEquals(utilNow, tstamp);
        assertFalse(someDate instanceof Timestamp);

        // java.sql.Date to Timestamp
        tstamp = convert(sqlCoerced, Timestamp.class);
        assertEquals(sqlCoerced, tstamp);

        // Timestamp to java.sql.Date
        java.sql.Date someDate1 = convert(tstamp, java.sql.Date.class);
        assertEquals(someDate1, utilNow);

        // String to Date
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(2015, 0, 17, 9, 54);
        Date date = convert("2015-01-17 09:54", Date.class);
        assertEquals(cal.getTime(), date);
        assert date != null;
        assertFalse(date instanceof java.sql.Date);

        // String to java.sql.Date
        java.sql.Date sqlDate = convert("2015-01-17 09:54", java.sql.Date.class);
        assertEquals(cal.getTime(), sqlDate);
        assert sqlDate != null;

        // Calendar to Date
        date = convert(cal, Date.class);
        assertEquals(date, cal.getTime());
        assert date != null;
        assertFalse(date instanceof java.sql.Date);

        // Calendar to java.sql.Date
        sqlDate = convert(cal, java.sql.Date.class);
        assertEquals(sqlDate, cal.getTime());
        assert sqlDate != null;

        // long to Date
        long now = System.currentTimeMillis();
        Date dateNow = new Date(now);
        Date converted = convert(now, Date.class);
        assert converted != null;
        assertEquals(dateNow, converted);
        assertFalse(converted instanceof java.sql.Date);

        // long to java.sql.Date
        Date sqlConverted = convert(now, java.sql.Date.class);
        assertEquals(dateNow, sqlConverted);
        assert sqlConverted != null;

        // AtomicLong to Date
        now = System.currentTimeMillis();
        dateNow = new Date(now);
        converted = convert(new AtomicLong(now), Date.class);
        assert converted != null;
        assertEquals(dateNow, converted);
        assertFalse(converted instanceof java.sql.Date);

        // long to java.sql.Date
        dateNow = new java.sql.Date(now);
        sqlConverted = convert(new AtomicLong(now), java.sql.Date.class);
        assert sqlConverted != null;
        assertEquals(dateNow, sqlConverted);

        // BigInteger to java.sql.Date
        BigInteger bigInt = new BigInteger("" + now);
        sqlDate = convert(bigInt, java.sql.Date.class);
        assert sqlDate.getTime() == now;

        // BigDecimal to java.sql.Date
        BigDecimal bigDec = new BigDecimal(now);
        sqlDate = convert(bigDec, java.sql.Date.class);
        assert sqlDate.getTime() == now;

        // BigInteger to Timestamp
        bigInt = new BigInteger("" + now);
        tstamp = convert(bigInt, Timestamp.class);
        assert tstamp.getTime() == now;

        // BigDecimal to TimeStamp
        bigDec = new BigDecimal(now);
        tstamp = convert(bigDec, Timestamp.class);
        assert tstamp.getTime() == now;

        // Invalid source type for Date
        try
        {
            convert(TimeZone.getDefault(), Date.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("unsupported target type 'date'"));
        }

        // Invalid source type for java.sql.Date
        try
        {
            convert(TimeZone.getDefault(), java.sql.Date.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("unsupported target type 'java.sql.date"));
        }

        // Invalid source date for Date
        try
        {
            convert("2015/01/33", Date.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().contains("Day must be between 1 and 31 inclusive, date: 2015/01/33"));
        }

        // Invalid source date for java.sql.Date
        try
        {
            convert("2015/01/33", java.sql.Date.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("day must be between 1 and 31"));
        }
    }

    @Test
    void testBogusSqlDate2()
    {
        assertThatThrownBy(() -> Converter.convert(true, java.sql.Date.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported target type 'java.sql.Date' requested for conversion from [Boolean (true)]");
    }

    @Test
    void testCalendar()
    {
        // Date to Calendar
        Date now = new Date();
        Calendar calendar = convert(new Date(), Calendar.class);
        assertEquals(calendar.getTime(), now);

        // SqlDate to Calendar
        java.sql.Date sqlDate = convert(now, java.sql.Date.class);
        calendar = convert(sqlDate, Calendar.class);
        assertEquals(calendar.getTime(), sqlDate);

        // Timestamp to Calendar
        Timestamp timestamp = convert(now, Timestamp.class);
        calendar = convert(timestamp, Calendar.class);
        assertEquals(calendar.getTime(), timestamp);

        // Long to Calendar
        calendar = convert(now.getTime(), Calendar.class);
        assertEquals(calendar.getTime(), now);

        // AtomicLong to Calendar
        AtomicLong atomicLong = new AtomicLong(now.getTime());
        calendar = convert(atomicLong, Calendar.class);
        assertEquals(calendar.getTime(), now);

        // String to Calendar
        String strDate = convert(now, String.class);
        calendar = convert(strDate, Calendar.class);
        String strDate2 = convert(calendar, String.class);
        assertEquals(strDate, strDate2);

        // BigInteger to Calendar
        BigInteger bigInt = new BigInteger("" + now.getTime());
        calendar = convert(bigInt, Calendar.class);
        assertEquals(calendar.getTime(), now);

        // BigDecimal to Calendar
        BigDecimal bigDec = new BigDecimal(now.getTime());
        calendar = convert(bigDec, Calendar.class);
        assertEquals(calendar.getTime(), now);

        // Other direction --> Calendar to other date types

        // Calendar to Date
        calendar = convert(now, Calendar.class);
        Date date = convert(calendar, Date.class);
        assertEquals(calendar.getTime(), date);

        // Calendar to SqlDate
        sqlDate = convert(calendar, java.sql.Date.class);
        assertEquals(calendar.getTime().getTime(), sqlDate.getTime());

        // Calendar to Timestamp
        timestamp = convert(calendar, Timestamp.class);
        assertEquals(calendar.getTime().getTime(), timestamp.getTime());

        // Calendar to Long
        long tnow = convert(calendar, long.class);
        assertEquals(calendar.getTime().getTime(), tnow);

        // Calendar to AtomicLong
        atomicLong = convert(calendar, AtomicLong.class);
        assertEquals(calendar.getTime().getTime(), atomicLong.get());

        // Calendar to String
        strDate = convert(calendar, String.class);
        strDate2 = convert(now, String.class);
        assertEquals(strDate, strDate2);

        // Calendar to BigInteger
        bigInt = convert(calendar, BigInteger.class);
        assertEquals(now.getTime(), bigInt.longValue());

        // Calendar to BigDecimal
        bigDec = convert(calendar, BigDecimal.class);
        assertEquals(now.getTime(), bigDec.longValue());
    }

    @Test
    void testLocalDateToOthers()
    {
        // Date to LocalDate
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2020, 8, 30, 0, 0, 0);
        Date now = calendar.getTime();
        LocalDate localDate = convert(now, LocalDate.class);
        assertEquals(localDateToMillis(localDate), now.getTime());

        // LocalDate to LocalDate - identity check
        LocalDate x = convert(localDate, LocalDate.class);
        assert localDate == x;

        // LocalDateTime to LocalDate
        LocalDateTime ldt = LocalDateTime.of(2020, 8, 30, 0, 0, 0);
        x = convert(ldt, LocalDate.class);
        assert localDateTimeToMillis(ldt) == localDateToMillis(x);

        // ZonedDateTime to LocalDate
        ZonedDateTime zdt = ZonedDateTime.of(2020, 8, 30, 0, 0, 0, 0, ZoneId.systemDefault());
        x = convert(zdt, LocalDate.class);
        assert zonedDateTimeToMillis(zdt) == localDateToMillis(x);

        // Calendar to LocalDate
        x = convert(calendar, LocalDate.class);
        assert localDateToMillis(localDate) == calendar.getTime().getTime();

        // SqlDate to LocalDate
        java.sql.Date sqlDate = convert(now, java.sql.Date.class);
        localDate = convert(sqlDate, LocalDate.class);
        assertEquals(localDateToMillis(localDate), sqlDate.getTime());

        // Timestamp to LocalDate
        Timestamp timestamp = convert(now, Timestamp.class);
        localDate = convert(timestamp, LocalDate.class);
        assertEquals(localDateToMillis(localDate), timestamp.getTime());

        LocalDate nowDate = LocalDate.now();
        // Long to LocalDate
        localDate = convert(nowDate.toEpochDay(), LocalDate.class);
        assertEquals(localDate, nowDate);

        // AtomicLong to LocalDate
        AtomicLong atomicLong = new AtomicLong(nowDate.toEpochDay());
        localDate = convert(atomicLong, LocalDate.class);
        assertEquals(localDate, nowDate);

        // String to LocalDate
        String strDate = convert(now, String.class);
        localDate = convert(strDate, LocalDate.class);
        String strDate2 = convert(localDate, String.class);
        assert strDate.startsWith(strDate2);

        // BigInteger to LocalDate
        BigInteger bigInt = new BigInteger("" + nowDate.toEpochDay());
        localDate = convert(bigInt, LocalDate.class);
        assertEquals(localDate, nowDate);

        // BigDecimal to LocalDate
        BigDecimal bigDec = new BigDecimal(nowDate.toEpochDay());
        localDate = convert(bigDec, LocalDate.class);
        assertEquals(localDate, nowDate);

        // Other direction --> LocalDate to other date types

        // LocalDate to Date
        localDate = convert(now, LocalDate.class);
        Date date = convert(localDate, Date.class);
        assertEquals(localDateToMillis(localDate), date.getTime());

        // LocalDate to SqlDate
        sqlDate = convert(localDate, java.sql.Date.class);
        assertEquals(localDateToMillis(localDate), sqlDate.getTime());

        // LocalDate to Timestamp
        timestamp = convert(localDate, Timestamp.class);
        assertEquals(localDateToMillis(localDate), timestamp.getTime());

        // LocalDate to Long
        long tnow = convert(localDate, long.class);
        assertEquals(localDate.toEpochDay(), tnow);

        // LocalDate to AtomicLong
        atomicLong = convert(localDate, AtomicLong.class);
        assertEquals(localDate.toEpochDay(), atomicLong.get());

        // LocalDate to String
        strDate = convert(localDate, String.class);
        strDate2 = convert(now, String.class);
        assert strDate2.startsWith(strDate);

        // LocalDate to BigInteger
        bigInt = convert(localDate, BigInteger.class);
        LocalDate nd = LocalDate.ofEpochDay(bigInt.longValue());
        assertEquals(localDate, nd);

        // LocalDate to BigDecimal
        bigDec = convert(localDate, BigDecimal.class);
        nd = LocalDate.ofEpochDay(bigDec.longValue());
        assertEquals(localDate, nd);

        // Error handling
        try {
            convert("2020-12-40", LocalDate.class);
            fail();
        }
        catch (IllegalArgumentException e) {
            TestUtil.assertContainsIgnoreCase(e.getMessage(), "day must be between 1 and 31");
        }

        assert convert(null, LocalDate.class) == null;
    }

    @Test
    void testStringToLocalDate()
    {
        String dec23rd2023 = "19714";
        LocalDate ld = convert(dec23rd2023, LocalDate.class);
        assert ld.getYear() == 2023;
        assert ld.getMonthValue() == 12;
        assert ld.getDayOfMonth() == 23;

        dec23rd2023 = "2023-12-23";
        ld = convert(dec23rd2023, LocalDate.class);
        assert ld.getYear() == 2023;
        assert ld.getMonthValue() == 12;
        assert ld.getDayOfMonth() == 23;

        dec23rd2023 = "2023/12/23";
        ld = convert(dec23rd2023, LocalDate.class);
        assert ld.getYear() == 2023;
        assert ld.getMonthValue() == 12;
        assert ld.getDayOfMonth() == 23;

        dec23rd2023 = "12/23/2023";
        ld = convert(dec23rd2023, LocalDate.class);
        assert ld.getYear() == 2023;
        assert ld.getMonthValue() == 12;
        assert ld.getDayOfMonth() == 23;
    }

    @Test
    void testStringOnMapToLocalDate()
    {
        Map<String, Object> map = new HashMap<>();
        String dec23Epoch = "19714";
        map.put("value", dec23Epoch);
        LocalDate ld = convert(map, LocalDate.class);
        assert ld.getYear() == 2023;
        assert ld.getMonthValue() == 12;
        assert ld.getDayOfMonth() == 23;

        dec23Epoch = "2023-12-23";
        map.put("value", dec23Epoch);
        ld = convert(map, LocalDate.class);
        assert ld.getYear() == 2023;
        assert ld.getMonthValue() == 12;
        assert ld.getDayOfMonth() == 23;

        dec23Epoch = "2023/12/23";
        map.put("value", dec23Epoch);
        ld = convert(map, LocalDate.class);
        assert ld.getYear() == 2023;
        assert ld.getMonthValue() == 12;
        assert ld.getDayOfMonth() == 23;

        dec23Epoch = "12/23/2023";
        map.put("value", dec23Epoch);
        ld = convert(map, LocalDate.class);
        assert ld.getYear() == 2023;
        assert ld.getMonthValue() == 12;
        assert ld.getDayOfMonth() == 23;
    }

    @Test
    void testStringKeysOnMapToLocalDate()
    {
        Map<String, Object> map = new HashMap<>();
        map.put("day", "23");
        map.put("month", "12");
        map.put("year", "2023");
        LocalDate ld = convert(map, LocalDate.class);
        assert ld.getYear() == 2023;
        assert ld.getMonthValue() == 12;
        assert ld.getDayOfMonth() == 23;

        map.put("day", 23);
        map.put("month", 12);
        map.put("year", 2023);
        ld = convert(map, LocalDate.class);
        assert ld.getYear() == 2023;
        assert ld.getMonthValue() == 12;
        assert ld.getDayOfMonth() == 23;
    }

    @Test
    void testLocalDateTimeToOthers()
    {
        // Date to LocalDateTime
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2020, 8, 30, 13, 1, 11);
        Date now = calendar.getTime();
        LocalDateTime localDateTime = convert(now, LocalDateTime.class);
        assertEquals(localDateTimeToMillis(localDateTime), now.getTime());

        // LocalDateTime to LocalDateTime - identity check
        LocalDateTime x = convert(localDateTime, LocalDateTime.class);
        assert localDateTime == x;

        // LocalDate to LocalDateTime
        LocalDate ld = LocalDate.of(2020, 8, 30);
        x = convert(ld, LocalDateTime.class);
        assert localDateToMillis(ld) == localDateTimeToMillis(x);

        // ZonedDateTime to LocalDateTime
        ZonedDateTime zdt = ZonedDateTime.of(2020, 8, 30, 13, 1, 11, 0, ZoneId.systemDefault());
        x = convert(zdt, LocalDateTime.class);
        assert zonedDateTimeToMillis(zdt) == localDateTimeToMillis(x);

        // Calendar to LocalDateTime
        x = convert(calendar, LocalDateTime.class);
        assert localDateTimeToMillis(localDateTime) == calendar.getTime().getTime();

        // SqlDate to LocalDateTime
        java.sql.Date sqlDate = convert(now, java.sql.Date.class);
        localDateTime = convert(sqlDate, LocalDateTime.class);
        assertEquals(localDateTimeToMillis(localDateTime), localDateToMillis(sqlDate.toLocalDate()));

        // Timestamp to LocalDateTime
        Timestamp timestamp = convert(now, Timestamp.class);
        localDateTime = convert(timestamp, LocalDateTime.class);
        assertEquals(localDateTimeToMillis(localDateTime), timestamp.getTime());

        // Long to LocalDateTime
        localDateTime = convert(now.getTime(), LocalDateTime.class);
        assertEquals(localDateTimeToMillis(localDateTime), now.getTime());

        // AtomicLong to LocalDateTime
        AtomicLong atomicLong = new AtomicLong(now.getTime());
        localDateTime = convert(atomicLong, LocalDateTime.class);
        assertEquals(localDateTimeToMillis(localDateTime), now.getTime());

        // String to LocalDateTime
        String strDate = convert(now, String.class);
        localDateTime = convert(strDate, LocalDateTime.class);
        String strDate2 = convert(localDateTime, String.class);
        assert strDate.startsWith(strDate2);

        // BigInteger to LocalDateTime
        BigInteger bigInt = new BigInteger("" + now.getTime());
        localDateTime = convert(bigInt, LocalDateTime.class);
        assertEquals(localDateTimeToMillis(localDateTime), now.getTime());

        // BigDecimal to LocalDateTime
        BigDecimal bigDec = new BigDecimal(now.getTime());
        localDateTime = convert(bigDec, LocalDateTime.class);
        assertEquals(localDateTimeToMillis(localDateTime), now.getTime());

        // Other direction --> LocalDateTime to other date types

        // LocalDateTime to Date
        localDateTime = convert(now, LocalDateTime.class);
        Date date = convert(localDateTime, Date.class);
        assertEquals(localDateTimeToMillis(localDateTime), date.getTime());

        // LocalDateTime to SqlDate
        sqlDate = convert(localDateTime, java.sql.Date.class);
        assertEquals(localDateTimeToMillis(localDateTime), sqlDate.getTime());

        // LocalDateTime to Timestamp
        timestamp = convert(localDateTime, Timestamp.class);
        assertEquals(localDateTimeToMillis(localDateTime), timestamp.getTime());

        // LocalDateTime to Long
        long tnow = convert(localDateTime, long.class);
        assertEquals(localDateTimeToMillis(localDateTime), tnow);

        // LocalDateTime to AtomicLong
        atomicLong = convert(localDateTime, AtomicLong.class);
        assertEquals(localDateTimeToMillis(localDateTime), atomicLong.get());

        // LocalDateTime to String
        strDate = convert(localDateTime, String.class);
        strDate2 = convert(now, String.class);
        assert strDate2.startsWith(strDate);

        // LocalDateTime to BigInteger
        bigInt = convert(localDateTime, BigInteger.class);
        assertEquals(now.getTime(), bigInt.longValue());

        // LocalDateTime to BigDecimal
        bigDec = convert(localDateTime, BigDecimal.class);
        assertEquals(now.getTime(), bigDec.longValue());

        // Error handling
        try
        {
            convert("2020-12-40", LocalDateTime.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            TestUtil.assertContainsIgnoreCase(e.getMessage(), "day must be between 1 and 31");
        }

        assert convert(null, LocalDateTime.class) == null;
    }

    @Test
    void testZonedDateTimeToOthers()
    {
        // Date to ZonedDateTime
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2020, 8, 30, 13, 1, 11);
        Date now = calendar.getTime();
        ZonedDateTime zonedDateTime = convert(now, ZonedDateTime.class);
        assertEquals(zonedDateTimeToMillis(zonedDateTime), now.getTime());

        // ZonedDateTime to ZonedDateTime - identity check
        ZonedDateTime x = convert(zonedDateTime, ZonedDateTime.class);
        assert zonedDateTime == x;

        // LocalDate to ZonedDateTime
        LocalDate ld = LocalDate.of(2020, 8, 30);
        x = convert(ld, ZonedDateTime.class);
        assert localDateToMillis(ld) == zonedDateTimeToMillis(x);

        // LocalDateTime to ZonedDateTime
        LocalDateTime ldt = LocalDateTime.of(2020, 8, 30, 13, 1, 11);
        x = convert(ldt, ZonedDateTime.class);
        assert localDateTimeToMillis(ldt) == zonedDateTimeToMillis(x);

        // ZonedDateTime to ZonedDateTime
        ZonedDateTime zdt = ZonedDateTime.of(2020, 8, 30, 13, 1, 11, 0, ZoneId.systemDefault());
        x = convert(zdt, ZonedDateTime.class);
        assert zonedDateTimeToMillis(zdt) == zonedDateTimeToMillis(x);

        // Calendar to ZonedDateTime
        x = convert(calendar, ZonedDateTime.class);
        assert zonedDateTimeToMillis(zonedDateTime) == calendar.getTime().getTime();

        // SqlDate to ZonedDateTime
        java.sql.Date sqlDate = convert(now, java.sql.Date.class);
        zonedDateTime = convert(sqlDate, ZonedDateTime.class);
        assertEquals(zonedDateTimeToMillis(zonedDateTime), localDateToMillis(sqlDate.toLocalDate()));

        // Timestamp to ZonedDateTime
        Timestamp timestamp = convert(now, Timestamp.class);
        zonedDateTime = convert(timestamp, ZonedDateTime.class);
        assertEquals(zonedDateTimeToMillis(zonedDateTime), timestamp.getTime());

        // Long to ZonedDateTime
        zonedDateTime = convert(now.getTime(), ZonedDateTime.class);
        assertEquals(zonedDateTimeToMillis(zonedDateTime), now.getTime());

        // AtomicLong to ZonedDateTime
        AtomicLong atomicLong = new AtomicLong(now.getTime());
        zonedDateTime = convert(atomicLong, ZonedDateTime.class);
        assertEquals(zonedDateTimeToMillis(zonedDateTime), now.getTime());

        // String to ZonedDateTime
        String strDate = convert(now, String.class);
        zonedDateTime = convert(strDate, ZonedDateTime.class);
        String strDate2 = convert(zonedDateTime, String.class);
        assert strDate2.startsWith(strDate);

        // BigInteger to ZonedDateTime
        BigInteger bigInt = new BigInteger("" + now.getTime());
        zonedDateTime = convert(bigInt, ZonedDateTime.class);
        assertEquals(zonedDateTimeToMillis(zonedDateTime), now.getTime());

        // BigDecimal to ZonedDateTime
        BigDecimal bigDec = new BigDecimal(now.getTime());
        zonedDateTime = convert(bigDec, ZonedDateTime.class);
        assertEquals(zonedDateTimeToMillis(zonedDateTime), now.getTime());

        // Other direction --> ZonedDateTime to other date types

        // ZonedDateTime to Date
        zonedDateTime = convert(now, ZonedDateTime.class);
        Date date = convert(zonedDateTime, Date.class);
        assertEquals(zonedDateTimeToMillis(zonedDateTime), date.getTime());

        // ZonedDateTime to SqlDate
        sqlDate = convert(zonedDateTime, java.sql.Date.class);
        assertEquals(zonedDateTimeToMillis(zonedDateTime), sqlDate.getTime());

        // ZonedDateTime to Timestamp
        timestamp = convert(zonedDateTime, Timestamp.class);
        assertEquals(zonedDateTimeToMillis(zonedDateTime), timestamp.getTime());

        // ZonedDateTime to Long
        long tnow = convert(zonedDateTime, long.class);
        assertEquals(zonedDateTimeToMillis(zonedDateTime), tnow);

        // ZonedDateTime to AtomicLong
        atomicLong = convert(zonedDateTime, AtomicLong.class);
        assertEquals(zonedDateTimeToMillis(zonedDateTime), atomicLong.get());

        // ZonedDateTime to String
        strDate = convert(zonedDateTime, String.class);
        strDate2 = convert(now, String.class);
        assert strDate.startsWith(strDate2);

        // ZonedDateTime to BigInteger
        bigInt = convert(zonedDateTime, BigInteger.class);
        assertEquals(now.getTime(), bigInt.longValue());

        // ZonedDateTime to BigDecimal
        bigDec = convert(zonedDateTime, BigDecimal.class);
        assertEquals(now.getTime(), bigDec.longValue());

        // Error handling
        try {
            convert("2020-12-40", ZonedDateTime.class);
            fail();
        }
        catch (IllegalArgumentException e) {
            TestUtil.assertContainsIgnoreCase(e.getCause().getMessage(), "day must be between 1 and 31");
        }

        assert convert(null, ZonedDateTime.class) == null;
    }

    @Test
    void testDateErrorHandlingBadInput()
    {
        assertNull(convert(" ", java.util.Date.class));
        assertNull(convert("", java.util.Date.class));
        assertNull(convert(null, java.util.Date.class));

        assertNull(convert(" ", Date.class));
        assertNull(convert("", Date.class));
        assertNull(convert(null, Date.class));

        assertNull(convert(" ", java.sql.Date.class));
        assertNull(convert("", java.sql.Date.class));
        assertNull(convert(null, java.sql.Date.class));

        assertNull(convert(" ", java.sql.Date.class));
        assertNull(convert("", java.sql.Date.class));
        assertNull(convert(null, java.sql.Date.class));

        assertNull(convert(" ", java.sql.Timestamp.class));
        assertNull(convert("", java.sql.Timestamp.class));
        assertNull(convert(null, java.sql.Timestamp.class));

        assertNull(convert(" ", Timestamp.class));
        assertNull(convert("", Timestamp.class));
        assertNull(convert(null, Timestamp.class));
    }

    @Test
    void testTimestamp()
    {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        assertEquals(now, convert(now, Timestamp.class));
        assert convert(now, Timestamp.class) instanceof Timestamp;

        Timestamp christmas = convert("2015/12/25", Timestamp.class);
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2015, 11, 25);
        assert christmas.getTime() == c.getTime().getTime();

        Timestamp christmas2 = convert(c, Timestamp.class);

        assertEquals(christmas, christmas2);
        assertEquals(christmas2, convert(christmas.getTime(), Timestamp.class));

        AtomicLong al = new AtomicLong(christmas.getTime());
        assertEquals(christmas2, convert(al, Timestamp.class));

        ZonedDateTime zdt = ZonedDateTime.of(2020, 8, 30, 13, 11, 17, 0, ZoneId.systemDefault());
        Timestamp alexaBirthday = convert(zdt, Timestamp.class);
        assert alexaBirthday.getTime() == zonedDateTimeToMillis(zdt);
        try
        {
            convert(Boolean.TRUE, Timestamp.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assert e.getMessage().toLowerCase().contains("unsupported target type 'timestamp'");
        }

        try
        {
            convert("123dhksdk", Timestamp.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assert e.getMessage().toLowerCase().contains("unable to parse: 123");
        }
    }

    @Test
    void testFloat()
    {
        assert -3.14f == convert(-3.14f, float.class);
        assert -3.14f == convert(-3.14f, Float.class);
        assert -3.14f == convert("-3.14", float.class);
        assert -3.14f == convert("-3.14", Float.class);
        assert -3.14f == convert(-3.14d, float.class);
        assert -3.14f == convert(-3.14d, Float.class);
        assert 1.0f == convert(true, float.class);
        assert 1.0f == convert(true, Float.class);
        assert 0.0f == convert(false, float.class);
        assert 0.0f == convert(false, Float.class);

        assert 0.0f == convert(new AtomicInteger(0), Float.class);
        assert 0.0f == convert(new AtomicLong(0), Float.class);
        assert 0.0f == convert(new AtomicBoolean(false), Float.class);
        assert 1.0f == convert(new AtomicBoolean(true), Float.class);

        try
        {
            convert(TimeZone.getDefault(), float.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("unsupported target type 'float'"));
        }

        try
        {
            convert("45.6badNumber", Float.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("45.6badnumber"));
        }
    }

    @Test
    void testDouble()
    {
        assert -3.14d == convert(-3.14d, double.class);
        assert -3.14d == convert(-3.14d, Double.class);
        assert -3.14d == convert("-3.14", double.class);
        assert -3.14d == convert("-3.14", Double.class);
        assert -3.14d == convert(new BigDecimal("-3.14"), double.class);
        assert -3.14d == convert(new BigDecimal("-3.14"), Double.class);
        assert 1.0d == convert(true, double.class);
        assert 1.0d == convert(true, Double.class);
        assert 0.0d == convert(false, double.class);
        assert 0.0d == convert(false, Double.class);

        assert 0.0d == convert(new AtomicInteger(0), double.class);
        assert 0.0d == convert(new AtomicLong(0), double.class);
        assert 0.0d == convert(new AtomicBoolean(false), Double.class);
        assert 1.0d == convert(new AtomicBoolean(true), Double.class);

        try
        {
            convert(TimeZone.getDefault(), double.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("unsupported target type 'double'"));
        }

        try
        {
            convert("45.6badNumber", Double.class);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("45.6badnumber"));
        }
    }

    @Test
    void testBoolean()
    {
        assertEquals(true, convert(-3.14d, boolean.class));
        assertEquals(false, convert(0.0d, boolean.class));
        assertEquals(true, convert(-3.14f, Boolean.class));
        assertEquals(false, convert(0.0f, Boolean.class));

        assertEquals(false, convert(new AtomicInteger(0), boolean.class));
        assertEquals(false, convert(new AtomicLong(0), boolean.class));
        assertEquals(false, convert(new AtomicBoolean(false), Boolean.class));
        assertEquals(true, convert(new AtomicBoolean(true), Boolean.class));

        assertEquals(true, convert("TRue", Boolean.class));
        assertEquals(true, convert("true", Boolean.class));
        assertEquals(false, convert("fALse", Boolean.class));
        assertEquals(false, convert("false", Boolean.class));
        assertEquals(false, convert("john", Boolean.class));

        assertEquals(true, convert(true, Boolean.class));
        assertEquals(true, convert(Boolean.TRUE, Boolean.class));
        assertEquals(false, convert(false, Boolean.class));
        assertEquals(false, convert(Boolean.FALSE, Boolean.class));

        try
        {
            convert(new Date(), Boolean.class);
            fail();
        }
        catch (Exception e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("unsupported target type 'boolean'"));
        }
    }

    @Test
    void testAtomicBoolean()
    {
        assert (convert(-3.14d, AtomicBoolean.class)).get();
        assert !(convert(0.0d, AtomicBoolean.class)).get();
        assert (convert(-3.14f, AtomicBoolean.class)).get();
        assert !(convert(0.0f, AtomicBoolean.class)).get();

        assert !(convert(new AtomicInteger(0), AtomicBoolean.class)).get();
        assert !(convert(new AtomicLong(0), AtomicBoolean.class)).get();
        assert !(convert(new AtomicBoolean(false), AtomicBoolean.class)).get();
        assert (convert(new AtomicBoolean(true), AtomicBoolean.class)).get();

        assert (convert("TRue", AtomicBoolean.class)).get();
        assert !(convert("fALse", AtomicBoolean.class)).get();
        assert !(convert("john", AtomicBoolean.class)).get();

        assert (convert(true, AtomicBoolean.class)).get();
        assert (convert(Boolean.TRUE, AtomicBoolean.class)).get();
        assert !(convert(false, AtomicBoolean.class)).get();
        assert !(convert(Boolean.FALSE, AtomicBoolean.class)).get();

        AtomicBoolean b1 = new AtomicBoolean(true);
        AtomicBoolean b2 = convert(b1, AtomicBoolean.class);
        assert b1 != b2; // ensure that it returns a different but equivalent instance
        assert b1.get() == b2.get();

        try {
            convert(new Date(), AtomicBoolean.class);
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().toLowerCase().contains("unsupported target type 'atomicboolean'"));
        }
    }

    @Test
    void testMapToAtomicBoolean()
    {
        final Map map = new HashMap();
        map.put("value", 57);
        AtomicBoolean ab = convert(map, AtomicBoolean.class);
        assert ab.get();

        map.clear();
        map.put("value", "");
        ab = convert(map, AtomicBoolean.class);
        assert null == ab;

        map.clear();
        map.put("value", null);
        assert null == convert(map, AtomicBoolean.class);

        map.clear();
        assertThatThrownBy(() -> convert(map, AtomicBoolean.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("the map must include keys: '_v' or 'value'");
    }

    @Test
    void testMapToAtomicInteger()
    {
        final Map map = new HashMap();
        map.put("value", 58);
        AtomicInteger ai = convert(map, AtomicInteger.class);
        assert 58 == ai.get();

        map.clear();
        map.put("value", "");
        ai = convert(map, AtomicInteger.class);
        assert null == ai;

        map.clear();
        map.put("value", null);
        assert null == convert(map, AtomicInteger.class);

        map.clear();
        assertThatThrownBy(() -> convert(map, AtomicInteger.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("the map must include keys: '_v' or 'value'");
    }

    @Test
    void testMapToAtomicLong()
    {
        final Map map = new HashMap();
        map.put("value", 58);
        AtomicLong al = convert(map, AtomicLong.class);
        assert 58 == al.get();

        map.clear();
        map.put("value", "");
        al = convert(map, AtomicLong.class);
        assert null == al;

        map.clear();
        map.put("value", null);
        assert null == convert(map, AtomicLong.class);

        map.clear();
        assertThatThrownBy(() -> convert(map, AtomicLong.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("the map must include keys: '_v' or 'value'");
    }

    @Test
    void testMapToCalendar()
    {
        long now = System.currentTimeMillis();
        final Map map = new HashMap();
        map.put("value", new Date(now));
        Calendar cal = convert(map, Calendar.class);
        assert now == cal.getTimeInMillis();

        map.clear();
        map.put("value", "");
        assert null == convert(map, Calendar.class);

        map.clear();
        map.put("value", null);
        assert null == convert(map, Calendar.class);

        map.clear();
        assertThatThrownBy(() -> convert(map, Calendar.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("the map must include keys: [time, zone], or '_v' or 'value'");
    }

    @Test
    void testMapToCalendarWithTimeZone()
    {
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
        cal.setTimeInMillis(now);

        final Map map = new HashMap();
        map.put("time", cal.getTimeInMillis());
        map.put("zone", cal.getTimeZone().getID());

        Calendar newCal = convert(map, Calendar.class);
        assert cal.equals(newCal);
        assert DeepEquals.deepEquals(cal, newCal);
    }

    @Test
    void testMapToCalendarWithTimeNoZone()
    {
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.setTimeZone(TimeZone.getDefault());
        cal.setTimeInMillis(now);

        final Map map = new HashMap();
        map.put("time", cal.getTimeInMillis());

        Calendar newCal = convert(map, Calendar.class);
        assert cal.equals(newCal);
        assert DeepEquals.deepEquals(cal, newCal);
    }

    @Test
    void testMapToGregCalendar()
    {
        long now = System.currentTimeMillis();
        final Map map = new HashMap();
        map.put("value", new Date(now));
        GregorianCalendar cal = convert(map, GregorianCalendar.class);
        assert now == cal.getTimeInMillis();

        map.clear();
        map.put("value", "");
        assert null == convert(map, GregorianCalendar.class);

        map.clear();
        map.put("value", null);
        assert null == convert(map, GregorianCalendar.class);

        map.clear();
        assertThatThrownBy(() -> convert(map, GregorianCalendar.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("To convert from Map to Calendar, the map must include keys: [time, zone], or '_v' or 'value'");
    }

    @Test
    void testMapToDate() {
        long now = System.currentTimeMillis();
        final Map map = new HashMap();
        map.put("value", now);
        Date date = convert(map, Date.class);
        assert now == date.getTime();

        map.clear();
        map.put("value", "");
        assert null == convert(map, Date.class);

        map.clear();
        map.put("value", null);
        assert null == convert(map, Date.class);

        map.clear();
        assertThatThrownBy(() -> convert(map, Date.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("the map must include keys: [time], or '_v' or 'value'");
    }

    @Test
    void testMapToSqlDate()
    {
        long now = System.currentTimeMillis();
        final Map map = new HashMap();
        map.put("value", now);
        java.sql.Date date = convert(map, java.sql.Date.class);
        assert now == date.getTime();

        map.clear();
        map.put("value", "");
        assert null == convert(map, java.sql.Date.class);

        map.clear();
        map.put("value", null);
        assert null == convert(map, java.sql.Date.class);

        map.clear();
        assertThatThrownBy(() -> convert(map, java.sql.Date.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("the map must include keys: [time], or '_v' or 'value'");
    }

    @Test
    void testMapToTimestamp()
    {
        long now = System.currentTimeMillis();
        final Map map = new HashMap();
        map.put("value", now);
        Timestamp date = convert(map, Timestamp.class);
        assert now == date.getTime();

        map.clear();
        map.put("value", "");
        assert null == convert(map, Timestamp.class);

        map.clear();
        map.put("value", null);
        assert null == convert(map, Timestamp.class);

        map.clear();
        assertThatThrownBy(() -> convert(map, Timestamp.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("the map must include keys: [time, nanos], or '_v' or 'value'");
    }

    @Test
    void testMapToLocalDate()
    {
        LocalDate today = LocalDate.now();
        long now = today.toEpochDay();
        final Map map = new HashMap();
        map.put("value", now);
        LocalDate date = convert(map, LocalDate.class);
        assert date.equals(today);

        map.clear();
        map.put("value", "");
        assert null == convert(map, LocalDate.class);

        map.clear();
        map.put("value", null);
        assert null == convert(map, LocalDate.class);

        map.clear();
        assertThatThrownBy(() -> convert(map, LocalDate.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Map to LocalDate, the map must include keys: [year, month, day], or '_v' or 'value'");
    }

    @Test
    void testMapToLocalDateTime()
    {
        long now = System.currentTimeMillis();
        final Map map = new HashMap();
        map.put("value", now);
        LocalDateTime ld = convert(map, LocalDateTime.class);
        assert ld.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() == now;

        map.clear();
        map.put("value", "");
        assert null == convert(map, LocalDateTime.class);

        map.clear();
        map.put("value", null);
        assert null == convert(map, LocalDateTime.class);

        map.clear();
        assertThatThrownBy(() -> convert(map, LocalDateTime.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Map to LocalDateTime, the map must include keys: '_v' or 'value'");
    }

    @Test
    void testMapToZonedDateTime()
    {
        long now = System.currentTimeMillis();
        final Map map = new HashMap();
        map.put("value", now);
        ZonedDateTime zd = convert(map, ZonedDateTime.class);
        assert zd.toInstant().toEpochMilli() == now;

        map.clear();
        map.put("value", "");
        assert null == convert(map, ZonedDateTime.class);


        map.clear();
        assert null == convert(map, ZonedDateTime.class);
    }

    @Test
    void testUnsupportedType()
    {
        try
        {
            convert("Lamb", TimeZone.class);
            fail();
        }
        catch (Exception e)
        {
            assertTrue(e.getMessage().toLowerCase().contains("unsupported target type"));
        }
    }

    @Test
    void testNullInstance()
    {
        assert 0L == convert(null, long.class);
        assert !convert(null, boolean.class);
        assert null == convert(null, Boolean.class);
        assert 0 == convert(null, byte.class);
        assert null == convert(null, Byte.class);
        assert 0 == convert(null, short.class);
        assert null == convert(null, Short.class);
        assert 0 == convert(null, int.class);
        assert null == convert(null, Integer.class);
        assert null == convert(null, Long.class);
        assert 0.0f == convert(null, float.class);
        assert null == convert(null, Float.class);
        assert 0.0d == convert(null, double.class);
        assert null == convert(null, Double.class);
        assert (char)0 == convert(null, char.class);
        assert null == convert(null, Character.class);

        assert null == convert(null, Date.class);
        assert null == convert(null, java.sql.Date.class);
        assert null == convert(null, Timestamp.class);
        assert null == convert(null, Calendar.class);
        assert null == convert(null, String.class);
        assert null == convert(null, BigInteger.class);
        assert null == convert(null, BigDecimal.class);
        assert null == convert(null, AtomicBoolean.class);
        assert null == convert(null, AtomicInteger.class);
        assert null == convert(null, AtomicLong.class);

        assert null == convert(null, Byte.class);
        assert null == convert(null, Integer.class);
        assert null == convert(null, Short.class);
        assert null == convert(null, Long.class);
        assert null == convert(null, Float.class);
        assert null == convert(null, Double.class);
        assert null == convert(null, Character.class);
        assert null == convert(null, Date.class);
        assert null == convert(null, java.sql.Date.class);
        assert null == convert(null, Timestamp.class);
        assert null == convert(null, AtomicBoolean.class);
        assert null == convert(null, AtomicInteger.class);
        assert null == convert(null, AtomicLong.class);
        assert null == convert(null, String.class);

        assert false == convert(null, boolean.class);
        assert 0 == convert(null, byte.class);
        assert 0 == convert(null, int.class);
        assert 0 == convert(null, short.class);
        assert 0 == convert(null, long.class);
        assert 0.0f == convert(null, float.class);
        assert 0.0d == convert(null, double.class);
        assert (char)0 == convert(null, char.class);
        assert null == convert(null, BigInteger.class);
        assert null == convert(null, BigDecimal.class);
        assert null == convert(null, AtomicBoolean.class);
        assert null == convert(null, AtomicInteger.class);
        assert null == convert(null, AtomicLong.class);
        assert null == convert(null, String.class);
    }

    @Test
    void testConvert2()
    {
        assert !convert(null, boolean.class);
        assert convert("true", boolean.class);
        assert convert("true", Boolean.class);
        assert !convert("false", boolean.class);
        assert !convert("false", Boolean.class);
        assert !convert("", boolean.class);
        assert !convert("", Boolean.class);
        assert null == convert(null, Boolean.class);
        assert -8 == convert("-8", byte.class);
        assert -8 == convert("-8", int.class);
        assert -8 == convert("-8", short.class);
        assert -8 == convert("-8", long.class);
        assert -8.0f == convert("-8", float.class);
        assert -8.0d == convert("-8", double.class);
        assert 'A' == convert(65, char.class);
        assert new BigInteger("-8").equals(convert("-8", BigInteger.class));
        assert new BigDecimal(-8.0d).equals(convert("-8", BigDecimal.class));
        assert convert("true", AtomicBoolean.class).get();
        assert -8 == convert("-8", AtomicInteger.class).get();
        assert -8L == convert("-8", AtomicLong.class).get();
        assert "-8".equals(convert(-8, String.class));
    }

    @Test
    void testNullType()
    {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> convert("123", null))
                // No Message was coming through here and receiving NullPointerException -- changed to convention over in convert -- hopefully that's what you had in mind.
                .withMessageContaining("toType cannot be null");
    }

    @Test
    void testEmptyString()
    {
        assertEquals(false, convert("", boolean.class));
        assertEquals(false, convert("", boolean.class));
        assert (byte) 0 == convert("", byte.class);
        assert (short) 0 == convert("", short.class);
        assert 0 == convert("", int.class);
        assert (long) 0 == convert("", long.class);
        assert 0.0f == convert("", float.class);
        assert 0.0d == convert("", double.class);
        assertEquals(null, convert("", BigDecimal.class));
        assertEquals(null, convert("", BigInteger.class));
        assertEquals(null, convert("", AtomicBoolean.class));
        assertEquals(null, convert("", AtomicInteger.class));
        assertEquals(null, convert("", AtomicLong.class));
    }

    @Test
    void testEnumSupport()
    {
        assertEquals("foo", convert(foo, String.class));
        assertEquals("bar", convert(bar, String.class));
    }

    @Test
    void testCharacterSupport()
    {
        assert 65 == convert('A', Byte.class);
        assert 65 == convert('A', byte.class);
        assert 65 == convert('A', Short.class);
        assert 65 == convert('A', short.class);
        assert 65 == convert('A', Integer.class);
        assert 65 == convert('A', int.class);
        assert 65 == convert('A', Long.class);
        assert 65 == convert('A', long.class);
        assert 65 == convert('A', BigInteger.class).longValue();
        assert 65 == convert('A', BigDecimal.class).longValue();

        assert '1' == convert(true, char.class);
        assert '0' == convert(false, char.class);
        assert '1' == convert(new AtomicBoolean(true), char.class);
        assert '0' == convert(new AtomicBoolean(false), char.class);
        assert 'z' == convert('z', char.class);
        assert 0 == convert("", char.class);
        assert 0 == convert("", Character.class);
        assert 'A' == convert("65", char.class);
        assert 'A' == convert("65", Character.class);
        try
        {
            convert("This is not a number", char.class);
            fail();
        }
        catch (IllegalArgumentException e) { }
        try
        {
            convert(new Date(), char.class);
            fail();
        }
        catch (IllegalArgumentException e) { }

        assertThatThrownBy(() -> convert(Long.MAX_VALUE, char.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value: 9223372036854775807 out of range to be converted to character");
    }

    @Test
    void testConvertUnknown()
    {
        try
        {
            convert(TimeZone.getDefault(), String.class);
            fail();
        }
        catch (IllegalArgumentException e) { }
    }

    @Test
    void testLongToBigDecimal()
    {
        BigDecimal big = convert(7L, BigDecimal.class);
        assert big instanceof BigDecimal;
        assert big.longValue() == 7L;

        big = convert(null, BigDecimal.class);
        assert big == null;
    }

    @Test
    void testLocalDate()
    {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(2020, 8, 4);   // 0-based for month

        BigDecimal big = convert(LocalDate.of(2020, 9, 4), BigDecimal.class);
        LocalDate out = LocalDate.ofEpochDay(big.longValue());
        assert out.getYear() == 2020;
        assert out.getMonthValue() == 9;
        assert out.getDayOfMonth() == 4;

        BigInteger bigI = convert(LocalDate.of(2020, 9, 4), BigInteger.class);
        out = LocalDate.ofEpochDay(bigI.longValue());
        assert out.getYear() == 2020;
        assert out.getMonthValue() == 9;
        assert out.getDayOfMonth() == 4;

        java.sql.Date sqlDate = convert(LocalDate.of(2020, 9, 4), java.sql.Date.class);
        assert sqlDate.getTime() == cal.getTime().getTime();

        Timestamp timestamp = convert(LocalDate.of(2020, 9, 4), Timestamp.class);
        assert timestamp.getTime() == cal.getTime().getTime();

        Date date = convert(LocalDate.of(2020, 9, 4), Date.class);
        assert date.getTime() == cal.getTime().getTime();

        LocalDate particular = LocalDate.of(2020, 9, 4);
        Long lng = convert(LocalDate.of(2020, 9, 4), Long.class);
        LocalDate xyz = LocalDate.ofEpochDay(lng);
        assertEquals(xyz, particular);

        AtomicLong atomicLong = convert(LocalDate.of(2020, 9, 4), AtomicLong.class);
        out = LocalDate.ofEpochDay(atomicLong.longValue());
        assert out.getYear() == 2020;
        assert out.getMonthValue() == 9;
        assert out.getDayOfMonth() == 4;
    }

    @Test
    void testLocalDateTimeToBig()
    {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(2020, 8, 8, 13, 11, 1);   // 0-based for month

        BigDecimal big = convert(LocalDateTime.of(2020, 9, 8, 13, 11, 1), BigDecimal.class);
        assert big.longValue() == cal.getTime().getTime();

        BigInteger bigI = convert(LocalDateTime.of(2020, 9, 8, 13, 11, 1), BigInteger.class);
        assert bigI.longValue() == cal.getTime().getTime();

        java.sql.Date sqlDate = convert(LocalDateTime.of(2020, 9, 8, 13, 11, 1), java.sql.Date.class);
        assert sqlDate.getTime() == cal.getTime().getTime();

        Timestamp timestamp = convert(LocalDateTime.of(2020, 9, 8, 13, 11, 1), Timestamp.class);
        assert timestamp.getTime() == cal.getTime().getTime();

        Date date = convert(LocalDateTime.of(2020, 9, 8, 13, 11, 1), Date.class);
        assert date.getTime() == cal.getTime().getTime();

        Long lng = convert(LocalDateTime.of(2020, 9, 8, 13, 11, 1), Long.class);
        assert lng == cal.getTime().getTime();

        AtomicLong atomicLong = convert(LocalDateTime.of(2020, 9, 8, 13, 11, 1), AtomicLong.class);
        assert atomicLong.get() == cal.getTime().getTime();
    }

    @Test
    void testLocalZonedDateTimeToBig()
    {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(2020, 8, 8, 13, 11, 1);   // 0-based for month

        BigDecimal big = convert(ZonedDateTime.of(2020, 9, 8, 13, 11, 1, 0, ZoneId.systemDefault()), BigDecimal.class);
        assert big.longValue() == cal.getTime().getTime();

        BigInteger bigI = convert(ZonedDateTime.of(2020, 9, 8, 13, 11, 1, 0, ZoneId.systemDefault()), BigInteger.class);
        assert bigI.longValue() == cal.getTime().getTime();

        java.sql.Date sqlDate = convert(ZonedDateTime.of(2020, 9, 8, 13, 11, 1, 0, ZoneId.systemDefault()), java.sql.Date.class);
        assert sqlDate.getTime() == cal.getTime().getTime();

        Date date = convert(ZonedDateTime.of(2020, 9, 8, 13, 11, 1, 0, ZoneId.systemDefault()), Date.class);
        assert date.getTime() == cal.getTime().getTime();

        AtomicLong atomicLong = convert(ZonedDateTime.of(2020, 9, 8, 13, 11, 1, 0, ZoneId.systemDefault()), AtomicLong.class);
        assert atomicLong.get() == cal.getTime().getTime();
    }

    @Test
    void testStringToClass()
    {
        Class<?> clazz = convert("java.math.BigInteger", Class.class);
        assert clazz.getName().equals("java.math.BigInteger");

        assertThatThrownBy(() -> convert("foo.bar.baz.Qux", Class.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot convert String 'foo.bar.baz.Qux' to class.  Class not found");

        assertNull(convert(null, Class.class));

        assertThatThrownBy(() -> convert(16.0, Class.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported target type 'Class' requested for conversion from [Double (16.0)]");
    }

    @Test
    void testClassToClass()
    {
        Class<?> clazz = convert(ConverterTest.class, Class.class);
        assert clazz.getName() == ConverterTest.class.getName();
    }

    @Test
    void testStringToUUID()
    {
        UUID uuid = Converter.convert("00000000-0000-0000-0000-000000000064", UUID.class);
        BigInteger bigInt = Converter.convert(uuid, BigInteger.class);
        assert bigInt.intValue() == 100;

        assertThatThrownBy(() -> Converter.convert("00000000", UUID.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value [String (00000000)] could not be converted to a 'UUID'");
    }

    @Test
    void testUUIDToUUID()
    {
        UUID uuid = Converter.convert("00000007-0000-0000-0000-000000000064", UUID.class);
        UUID uuid2 = Converter.convert(uuid, UUID.class);
        assert uuid.equals(uuid2);
    }

    @Test
    void testBogusToUUID()
    {
        assertThatThrownBy(() -> Converter.convert((short)77, UUID.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported conversion, source type [Short (77)] target type 'UUID'");
    }

    @Test
    void testBigIntegerToUUID()
    {
        UUID uuid = convert(new BigInteger("100"), UUID.class);
        BigInteger hundred = convert(uuid, BigInteger.class);
        assert hundred.intValue() == 100;
    }

    @Test
    void testBigDecimalToUUID()
    {
        UUID uuid = convert(new BigDecimal("100"), UUID.class);
        BigDecimal hundred = convert(uuid, BigDecimal.class);
        assert hundred.intValue() == 100;

        uuid = convert(new BigDecimal("100.4"), UUID.class);
        hundred = convert(uuid, BigDecimal.class);
        assert hundred.intValue() == 100;
    }

    @Test
    void testUUIDToBigInteger()
    {
        BigInteger bigInt = Converter.convert(UUID.fromString("00000000-0000-0000-0000-000000000064"), BigInteger.class);
        assert bigInt.intValue() == 100;

        bigInt = Converter.convert(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), BigInteger.class);
        assert bigInt.toString().equals("-18446744073709551617");

        bigInt = Converter.convert(UUID.fromString("00000000-0000-0000-0000-000000000000"), BigInteger.class);
        assert bigInt.intValue() == 0;

        assertThatThrownBy(() -> convert(16.0, Class.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported target type 'Class' requested for conversion from [Double (16.0)]");
    }

    @Test
    void testUUIDToBigDecimal()
    {
        BigDecimal bigDec = Converter.convert(UUID.fromString("00000000-0000-0000-0000-000000000064"), BigDecimal.class);
        assert bigDec.intValue() == 100;

        bigDec = Converter.convert(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), BigDecimal.class);
        assert bigDec.toString().equals("-18446744073709551617");

        bigDec = Converter.convert(UUID.fromString("00000000-0000-0000-0000-000000000000"), BigDecimal.class);
        assert bigDec.intValue() == 0;
    }

    @Test
    void testMapToUUID()
    {
        UUID uuid = convert(new BigInteger("100"), UUID.class);
        Map<String, Object> map = new HashMap<>();
        map.put("mostSigBits", uuid.getMostSignificantBits());
        map.put("leastSigBits", uuid.getLeastSignificantBits());
        UUID hundred = convert(map, UUID.class);
        assertEquals("00000000-0000-0000-0000-000000000064", hundred.toString());
    }

    @Test
    void testBadMapToUUID()
    {
        UUID uuid = convert(new BigInteger("100"), UUID.class);
        Map<String, Object> map = new HashMap<>();
        map.put("leastSigBits", uuid.getLeastSignificantBits());
        assertThatThrownBy(() -> convert(map, UUID.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value [HashMap ({leastSigBits=100})] could not be converted to a 'UUID'");
    }

    @Test
    void testClassToString()
    {
        String str = Converter.convert(BigInteger.class, String.class);
        assert str.equals("java.math.BigInteger");
        
        str = Converter.convert(null, String.class);
        assert str == null;
    }

    @Test
    void testSqlDateToString()
    {
        long now = System.currentTimeMillis();
        java.sql.Date date = new java.sql.Date(now);
        String strDate = Converter.convert(date, String.class);
        Date x = Converter.convert(strDate, Date.class);
        LocalDate l1 = Converter.convert(date, LocalDate.class);
        LocalDate l2 = Converter.convert(x, LocalDate.class);
        assertEquals(l1, l2);
    }

    @Test
    void tesTimestampToString()
    {
        long now = System.currentTimeMillis();
        Timestamp date = new Timestamp(now);
        String strDate = Converter.convert(date, String.class);
        Date x = Converter.convert(strDate, Date.class);
        String str2Date = Converter.convert(x, String.class);
        assertEquals(str2Date, strDate);
    }

    @Test
    void testByteToMap()
    {
        byte b1 = (byte) 16;
        Map<?, ?> map = Converter.convert(b1, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), (byte)16);
        assert map.get(Converter.VALUE).getClass().equals(Byte.class);

        Byte b2 = (byte) 16;
        map = Converter.convert(b2, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), (byte)16);
        assert map.get(Converter.VALUE).getClass().equals(Byte.class);
    }

    @Test
    void testShortToMap()
    {
        short s1 = (short) 1600;
        Map<?, ?> map = Converter.convert(s1, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), (short)1600);
        assert map.get(Converter.VALUE).getClass().equals(Short.class);

        Short s2 = (short) 1600;
        map = Converter.convert(s2, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), (short)1600);
        assert map.get(Converter.VALUE).getClass().equals(Short.class);
    }

    @Test
    void testIntegerToMap()
    {
        int s1 = 1234567;
        Map<?, ?> map = Converter.convert(s1, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), 1234567);
        assert map.get(Converter.VALUE).getClass().equals(Integer.class);

        Integer s2 = 1234567;
        map = Converter.convert(s2, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), 1234567);
        assert map.get(Converter.VALUE).getClass().equals(Integer.class);
    }    
    
    @Test
    void testLongToMap()
    {
        long s1 = 123456789012345L;
        Map<?, ?> map = Converter.convert(s1, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), 123456789012345L);
        assert map.get(Converter.VALUE).getClass().equals(Long.class);

        Long s2 = 123456789012345L;
        map = Converter.convert(s2, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), 123456789012345L);
        assert map.get(Converter.VALUE).getClass().equals(Long.class);
    }

    @Test
    void testFloatToMap()
    {
        float s1 = 3.141592f;
        Map<?, ?> map = Converter.convert(s1, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), 3.141592f);
        assert map.get(Converter.VALUE).getClass().equals(Float.class);

        Float s2 = 3.141592f;
        map = Converter.convert(s2, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), 3.141592f);
        assert map.get(Converter.VALUE).getClass().equals(Float.class);
    }    
    
    @Test
    void testDoubleToMap()
    {
        double s1 = 3.14159265358979d;
        Map<?, ?> map = Converter.convert(s1, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), 3.14159265358979d);
        assert map.get(Converter.VALUE).getClass().equals(Double.class);

        Double s2 = 3.14159265358979d;
        map = Converter.convert(s2, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), 3.14159265358979d);
        assert map.get(Converter.VALUE).getClass().equals(Double.class);
    }

    @Test
    void testBooleanToMap()
    {
        boolean s1 = true;
        Map<?, ?> map = Converter.convert(s1, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), true);
        assert map.get(Converter.VALUE).getClass().equals(Boolean.class);

        Boolean s2 = true;
        map = Converter.convert(s2, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), true);
        assert map.get(Converter.VALUE).getClass().equals(Boolean.class);
    }

    @Test
    void testCharacterToMap()
    {
        char s1 = 'e';
        Map<?, ?> map = Converter.convert(s1, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), 'e');
        assert map.get(Converter.VALUE).getClass().equals(Character.class);

        Character s2 = 'e';
        map = Converter.convert(s2, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), 'e');
        assert map.get(Converter.VALUE).getClass().equals(Character.class);
    }
    
    @Test
    void testBigIntegerToMap()
    {
        BigInteger bi = BigInteger.valueOf(1234567890123456L);
        Map<?, ?> map = Converter.convert(bi, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), bi);
        assert map.get(Converter.VALUE).getClass().equals(BigInteger.class);
    }

    @Test
    void testBigDecimalToMap()
    {
        BigDecimal bd = new BigDecimal("3.1415926535897932384626433");
        Map<?, ?> map = Converter.convert(bd, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), bd);
        assert map.get(Converter.VALUE).getClass().equals(BigDecimal.class);
    }

    @Test
    void testAtomicBooleanToMap()
    {
        AtomicBoolean ab = new AtomicBoolean(true);
        Map<?, ?> map = Converter.convert(ab, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), ab);
        assert map.get(Converter.VALUE).getClass().equals(AtomicBoolean.class);
    }

    @Test
    void testAtomicIntegerToMap()
    {
        AtomicInteger ai = new AtomicInteger(123456789);
        Map<?, ?> map = Converter.convert(ai, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), ai);
        assert map.get(Converter.VALUE).getClass().equals(AtomicInteger.class);
    }

    @Test
    void testAtomicLongToMap()
    {
        AtomicLong al = new AtomicLong(12345678901234567L);
        Map<?, ?> map = Converter.convert(al, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), al);
        assert map.get(Converter.VALUE).getClass().equals(AtomicLong.class);
    }

    @Test
    void testClassToMap()
    {
        Class<?> clazz = ConverterTest.class;
        Map<?, ?> map = Converter.convert(clazz, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), clazz);
    }

    @Test
    void testUUIDToMap()
    {
        UUID uuid = new UUID(1L, 2L);
        Map<?, ?> map = Converter.convert(uuid, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), uuid);
        assert map.get(Converter.VALUE).getClass().equals(UUID.class);
    }

    @Test
    void testCalendarToMap()
    {
        Calendar cal = Calendar.getInstance();
        Map<?, ?> map = Converter.convert(cal, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), cal);
        assert map.get(Converter.VALUE) instanceof Calendar;
    }

    @Test
    void testDateToMap()
    {
        Date now = new Date();
        Map<?, ?> map = Converter.convert(now, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), now);
        assert map.get(Converter.VALUE).getClass().equals(Date.class);
    }

    @Test
    void testSqlDateToMap()
    {
        java.sql.Date now = new java.sql.Date(System.currentTimeMillis());
        Map<?, ?> map = Converter.convert(now, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), now);
        assert map.get(Converter.VALUE).getClass().equals(java.sql.Date.class);
    }

    @Test
    void testTimestampToMap()
    {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Map<?, ?> map = Converter.convert(now, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), now);
        assert map.get(Converter.VALUE).getClass().equals(Timestamp.class);
    }

    @Test
    void testLocalDateToMap()
    {
        LocalDate now = LocalDate.now();
        Map<?, ?> map = Converter.convert(now, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), now);
        assert map.get(Converter.VALUE).getClass().equals(LocalDate.class);
    }

    @Test
    void testLocalDateTimeToMap()
    {
        LocalDateTime now = LocalDateTime.now();
        Map<?, ?> map = Converter.convert(now, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), now);
        assert map.get(Converter.VALUE).getClass().equals(LocalDateTime.class);
    }

    @Test
    void testZonedDateTimeToMap()
    {
        ZonedDateTime now = ZonedDateTime.now();
        Map<?, ?> map = Converter.convert(now, Map.class);
        assert map.size() == 1;
        assertEquals(map.get(Converter.VALUE), now);
        assert map.get(Converter.VALUE).getClass().equals(ZonedDateTime.class);
    }

    @Test
    void testUnknownType()
    {
        assertThatThrownBy(() -> convert(null, Collection.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported target type 'Collection' requested for conversion");
    }

    @Test
    @Disabled
    void testGetSupportedConversions()
    {
        Map map = Converter.getSupportedConversions();
        assert map.size() > 10;
    }

    @Test
    @Disabled
    void testAllSupportedConversions()
    {
        Map map = Converter.allSupportedConversions();
        assert map.size() > 10;
    }

    @Test
    @Disabled // will be coming back shortly
    void testIsConversionSupport()
    {
        assert Converter.isConversionSupportedFor(int.class, LocalDate.class);
        assert Converter.isConversionSupportedFor(Integer.class, LocalDate.class);
        assert Converter.isConversionSupportedFor(LocalDate.class, int.class);
        assert Converter.isConversionSupportedFor(LocalDate.class, Integer.class);

        assert !Converter.isConversionSupportedFor(byte.class, LocalDate.class);
        assert !Converter.isConversionSupportedFor(Byte.class, LocalDate.class);
        assert !Converter.isConversionSupportedFor(LocalDate.class, byte.class);
        assert !Converter.isConversionSupportedFor(LocalDate.class, Byte.class);

        assert Converter.isConversionSupportedFor(UUID.class, String.class);
        assert Converter.isConversionSupportedFor(UUID.class, Map.class);
        assert Converter.isConversionSupportedFor(UUID.class, BigDecimal.class);
        assert Converter.isConversionSupportedFor(UUID.class, BigInteger.class);
        assert !Converter.isConversionSupportedFor(UUID.class, long.class);
        assert !Converter.isConversionSupportedFor(UUID.class, Long.class);

        assert Converter.isConversionSupportedFor(String.class, UUID.class);
        assert Converter.isConversionSupportedFor(Map.class, UUID.class);
        assert Converter.isConversionSupportedFor(BigDecimal.class, UUID.class);
        assert Converter.isConversionSupportedFor(BigInteger.class, UUID.class);
    }

    static class DumbNumber extends BigInteger
    {
        DumbNumber(String val) {
            super(val);
        }

        public String toString() {
            return super.toString();
        }
    }

    @Test
    void testDumbNumberToByte()
    {
        DumbNumber dn = new DumbNumber("25");
        byte x = Converter.convert(dn, byte.class);
        assert x == 25;
    }

    @Test
    void testDumbNumberToShort()
    {
        DumbNumber dn = new DumbNumber("25");
        short x = Converter.convert(dn, short.class);
        assert x == 25;
    }

    @Test
    void testDumbNumberToShort2()
    {
        DumbNumber dn = new DumbNumber("25");
        Short x = Converter.convert(dn, Short.class);
        assert x == 25;
    }

    @Test
    void testDumbNumberToInt()
    {
        DumbNumber dn = new DumbNumber("25");
        int x = Converter.convert(dn, int.class);
        assert x == 25;
    }

    @Test
    void testDumbNumberToLong()
    {
        DumbNumber dn = new DumbNumber("25");
        long x = Converter.convert(dn, long.class);
        assert x == 25;
    }

    @Test
    void testDumbNumberToFloat()
    {
        DumbNumber dn = new DumbNumber("3");
        float x = Converter.convert(dn, float.class);
        assert x == 3;
    }

    @Test
    void testDumbNumberToDouble()
    {
        DumbNumber dn = new DumbNumber("3");
        double x = Converter.convert(dn, double.class);
        assert x == 3;
    }

    @Test
    void testDumbNumberToBoolean()
    {
        DumbNumber dn = new DumbNumber("3");
        boolean x = Converter.convert(dn, boolean.class);
        assert x;
    }

    @Test
    void testDumbNumberToCharacter()
    {
        DumbNumber dn = new DumbNumber("3");
        char x = Converter.convert(dn, char.class);
        assert x == '\u0003';
    }

    @Test
    void testDumbNumberToBigInteger()
    {
        DumbNumber dn = new DumbNumber("12345678901234567890");
        BigInteger x = Converter.convert(dn, BigInteger.class);
        assert x.toString().equals(dn.toString());
    }

    @Test
    void testDumbNumberToBigDecimal()
    {
        DumbNumber dn = new DumbNumber("12345678901234567890");
        BigDecimal x = Converter.convert(dn, BigDecimal.class);
        assert x.toString().equals(dn.toString());
    }

    @Test
    void testDumbNumberToString()
    {
        DumbNumber dn = new DumbNumber("12345678901234567890");
        String x = Converter.convert(dn, String.class);
        assert x.toString().equals("12345678901234567890");
    }

    @Test
    void testDumbNumberToUUID()
    {
        DumbNumber dn = new DumbNumber("1000");

        // Fails to convert
        assertThatThrownBy(() -> Converter.convert(dn, UUID.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported conversion, source type [DumbNumber (1000)] target type 'UUID'");

        // Add in conversion
        Converter.addConversion(DumbNumber.class, UUID.class, fromInstance -> {
            DumbNumber bigDummy = (DumbNumber) fromInstance;
            BigInteger mask = BigInteger.valueOf(Long.MAX_VALUE);
            long mostSignificantBits = bigDummy.shiftRight(64).and(mask).longValue();
            long leastSignificantBits = bigDummy.and(mask).longValue();
            return new UUID(mostSignificantBits, leastSignificantBits);
        });

        // Converts!
        UUID uuid = Converter.convert(dn, UUID.class);
        assert uuid.toString().equals("00000000-0000-0000-0000-0000000003e8");

        assert Converter.isConversionSupportedFor(DumbNumber.class, UUID.class);
    }

    @Test
    void testUUIDtoDumbNumber()
    {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-0000000003e8");

        Object o = Converter.convert(uuid, DumbNumber.class);
        assert o instanceof BigInteger;
        assert 1000L == ((Number) o).longValue();

        // Add in conversion
        Converter.addConversion(UUID.class, DumbNumber.class, fromInstance -> {
            UUID uuid1 = (UUID) fromInstance;
            BigInteger mostSignificant = BigInteger.valueOf(uuid1.getMostSignificantBits());
            BigInteger leastSignificant = BigInteger.valueOf(uuid1.getLeastSignificantBits());
            // Shift the most significant bits to the left and add the least significant bits
            return new DumbNumber(mostSignificant.shiftLeft(64).add(leastSignificant).toString());
        });

        // Converts!
        DumbNumber dn = Converter.convert(uuid, DumbNumber.class);
        assert dn.toString().equals("1000");

        assert Converter.isConversionSupportedFor(UUID.class, DumbNumber.class);
    }

    static class Normie
    {
        String name;

        Normie(String name) {
            this.name = name;
        }

        void setName(String name)
        {
            this.name = name;
        }
    }

    static class Weirdo
    {
        String name;

        Weirdo(String name)
        {
            this.name = reverseString(name);
        }

        void setName(String name)
        {
            this.name = reverseString(name);
        }
    }

    static String reverseString(String in)
    {
        StringBuilder reversed = new StringBuilder();
        for (int i = in.length() - 1; i >= 0; i--) {
            reversed.append(in.charAt(i));
        }
        return reversed.toString();
    }

    @Test
    void testNormieToWeirdoAndBack()
    {
        Converter.addConversion(Normie.class, Weirdo.class, fromInstance -> {
            Normie normie = (Normie) fromInstance;
            Weirdo weirdo = new Weirdo(normie.name);
            return weirdo;
        });

        Converter.addConversion(Weirdo.class, Normie.class, fromInstance -> {
            Weirdo weirdo = (Weirdo) fromInstance;
            Normie normie = new Normie(reverseString(weirdo.name));
            return normie;
        });
        
        Normie normie = new Normie("Joe");
        Weirdo weirdo = Converter.convert(normie, Weirdo.class);
        assertEquals(weirdo.name, "eoJ");

        weirdo = new Weirdo("Jacob");
        assertEquals(weirdo.name, "bocaJ");
        normie = Converter.convert(weirdo, Normie.class);
        assertEquals(normie.name, "Jacob");

        assert Converter.isConversionSupportedFor(Normie.class, Weirdo.class);
        assert Converter.isConversionSupportedFor(Weirdo.class, Normie.class);
    }
}