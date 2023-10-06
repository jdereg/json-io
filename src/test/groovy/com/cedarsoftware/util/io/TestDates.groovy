package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

import java.sql.Timestamp

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotSame
import static org.junit.jupiter.api.Assertions.assertNull
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
class TestDates
{
    static class DateTest
    {
        Date birthDay
        Date anniversary
        java.sql.Date christmas
    }

    static class TestDateField
    {
        Date fromString
        Date[] dates
    }

    static class DateTrick
    {
        private Date _userDate
    }

    static class LongTrick
    {
        private long _userDate
    }

    static class ObjectDateField
    {
        private Object date
        private ObjectDateField(Object date)
        {
            this.date = date
        }
    }

    private static class DateField
    {
        private Date date
        private DateField(Date date)
        {
            this.date = date
        }
    }

    static class SqlDateField
    {
        private java.sql.Date date
        private SqlDateField(java.sql.Date date)
        {
            this.date = date
        }
    }
    static class TimestampField
    {
        private Timestamp date
        private TimestampField(Timestamp date)
        {
            this.date = date
        }
    }

    static class TestDate implements Serializable
    {
        private final Date _arrayElement
        private final Date[] _typeArray
        private final Object[] _objArray
        private final Object _polyRefTarget
        private final Object _polyRef
        private final Object _polyNotRef
        private final Date _min
        private final Date _max
        private final Date _null

        private TestDate()
        {
            _arrayElement = new Date(-1)
            _polyRefTarget = new Date(71)
            _polyRef = _polyRefTarget;
            _polyNotRef = new Date(71)
            Date local = new Date(75)
            _null  = null;
            _typeArray = [_arrayElement, new Date(69), local, _null, null, new Date(69)] as Date[]
            _objArray = [_arrayElement, new Date(69), local, _null, null, new Date(69)] as Object[]
            _min = new Date(Long.MIN_VALUE)
            _max = new Date(Long.MAX_VALUE)
        }
    }

    private static void compareTimePortion(Calendar exp, Calendar act)
    {
        assertEquals(exp.get(Calendar.HOUR_OF_DAY), act.get(Calendar.HOUR_OF_DAY))
        assertEquals(exp.get(Calendar.MINUTE), act.get(Calendar.MINUTE))
        assertEquals(exp.get(Calendar.SECOND), act.get(Calendar.SECOND))
        assertEquals(exp.get(Calendar.MILLISECOND), act.get(Calendar.MILLISECOND))
    }

    private static void compareDatePortion(Calendar exp, Calendar act)
    {
        assertEquals(exp.get(Calendar.YEAR), act.get(Calendar.YEAR))
        assertEquals(exp.get(Calendar.MONTH), act.get(Calendar.MONTH))
        assertEquals(exp.get(Calendar.DAY_OF_MONTH), act.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    void testAssignDateFromEmptyString()
    {
        String thisClass = TestDateField.class.name
        String json = '{"@type":"' + thisClass + '","fromString":""}'
        TestDateField tdf = (TestDateField) TestUtil.readJsonObject(json)
        assertNull(tdf.fromString)

        Map jObj = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        assertNull(jObj.fromString)

        json = '{"@type":"' + thisClass + '","fromString":null,"dates":[""]}'
        tdf = (TestDateField) TestUtil.readJsonObject(json)
        assertNull(tdf.dates[0])

        jObj = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        json = TestUtil.getJsonString(jObj)
        tdf = (TestDateField) TestUtil.readJsonObject(json)
        assertNull(tdf.dates[0])

        json = '{"@type":"' + thisClass + '","fromString":1391875635941}'
        tdf = (TestDateField) TestUtil.readJsonObject(json)
        assertEquals(new Date(1391875635941L), tdf.fromString)
    }

    @Test
    void testDateParse()
    {
        String json = '{"@type":"date","value":"2014 July 9"}'
        Date date = (Date) JsonReader.jsonToJava(json)
        Calendar cal = Calendar.instance;
        cal.clear()
        cal.time = date;
        assertEquals(2014, cal.get(Calendar.YEAR))
        assertEquals(6, cal.get(Calendar.MONTH))
        assertEquals(9, cal.get(Calendar.DAY_OF_MONTH))

        json = '{"@type":"date","value":"2014 Juggler 9"}'
        assertThrows(Exception.class, { JsonReader.jsonToJava(json) })
    }

    @Test
    void testDate()
    {
        TestDate test = new TestDate()
        String json = TestUtil.getJsonString(test)
        TestUtil.printLine('json = ' + json)
        TestDate that = (TestDate) TestUtil.readJsonObject(json)

        assertTrue(that._arrayElement.equals(new Date(-1)))
        assertTrue(that._polyRefTarget.equals(new Date(71)))
        assertTrue(that._polyRef.equals(new Date(71)))
        assertTrue(that._polyNotRef.equals(new Date(71)))
        assertNotSame(that._polyRef, that._polyRefTarget)  // not same because Date's are treated as immutable primitives
        assertNotSame(that._polyNotRef, that._polyRef)

        assertTrue(that._typeArray.length == 6)
        assertNotSame(that._typeArray[0], that._arrayElement)
        assertTrue(that._typeArray[1] instanceof Date)
        assertTrue(that._typeArray[1] instanceof Date)
        assertTrue(that._typeArray[1].equals(new Date(69)))
        assertTrue(that._objArray.length == 6)
        assertNotSame(that._objArray[0], that._arrayElement)
        assertTrue(that._objArray[1] instanceof Date)
        assertTrue(that._objArray[1].equals(new Date(69)))
        assertTrue(that._polyRefTarget instanceof Date)
        assertTrue(that._polyNotRef instanceof Date)
        assertEquals(that._typeArray[1], that._typeArray[5])
        assertNotSame(that._typeArray[1], that._typeArray[5])
        assertEquals(that._objArray[1], that._objArray[5])
        assertNotSame(that._objArray[1], that._objArray[5])
        assertEquals(that._typeArray[1], that._objArray[1])
        assertNotSame(that._typeArray[1], that._objArray[1])
        assertEquals(that._typeArray[5], that._objArray[5])
        assertNotSame(that._typeArray[5], that._objArray[5])

        assertNotSame(that._objArray[2], that._typeArray[2])
        assertTrue(that._objArray[2].equals(new Date(75)))

        assertTrue(that._null == null)
        assertTrue(that._typeArray[3] == null)
        assertTrue(that._typeArray[4] == null)
        assertTrue(that._objArray[3] == null)
        assertTrue(that._objArray[4] == null)

        assertTrue(that._min.equals(new Date(Long.MIN_VALUE)))
        assertTrue(that._max.equals(new Date(Long.MAX_VALUE)))
    }

    @Test
    void testCustomDateFormat()
    {
        DateTest dt = new DateTest()
        Calendar c = Calendar.instance
        c.clear()
        c.set(1965, Calendar.DECEMBER, 17, 14, 01, 30)
        dt.birthDay = c.time
        c.clear()
        c.set(1991, Calendar.OCTOBER, 5, 1, 1, 30)
        dt.anniversary = new java.sql.Date(c.time.time)
        c.clear()
        c.set(2013, Calendar.DECEMBER, 25, 1, 2, 34)
        dt.christmas = new java.sql.Date(c.time.time)

        // Custom writer that only outputs ISO date portion
        Map args = new HashMap()
        args.put(JsonWriter.DATE_FORMAT, JsonWriter.ISO_DATE_FORMAT)
        String json = JsonWriter.objectToJson(dt, args)
        TestUtil.printLine('json = ' + json)

        // Read it back in
        DateTest readDt = (DateTest) TestUtil.readJsonObject(json)

        Calendar exp = Calendar.instance
        exp.setTime(dt.birthDay)
        Calendar act = Calendar.instance
        act.setTime(readDt.birthDay)
        compareDatePortion(exp, act)

        exp.setTime(dt.anniversary)
        act.setTime(readDt.anniversary)
        compareDatePortion(exp, act)

        exp.setTime(dt.christmas)
        act.setTime(readDt.christmas)
        compareDatePortion(exp, act)

        // Custom writer that outputs date and time portion in ISO format
        args = new HashMap()
        args.put(JsonWriter.DATE_FORMAT, JsonWriter.ISO_DATE_TIME_FORMAT)
        json = JsonWriter.objectToJson(dt, args)
        TestUtil.printLine('json = ' + json)

        // Read it back in
        readDt = (DateTest) TestUtil.readJsonObject(json)

        exp.setTime(dt.birthDay)
        act.setTime(readDt.birthDay)
        compareDatePortion(exp, act)
        compareTimePortion(exp, act)

        exp.setTime(dt.anniversary)
        act.setTime(readDt.anniversary)
        compareDatePortion(exp, act)
        compareTimePortion(exp, act)

        exp.setTime(dt.christmas)
        act.setTime(readDt.christmas)
        compareDatePortion(exp, act)
        compareTimePortion(exp, act)

        // Write out dates as long (standard behavior)
        json = TestUtil.getJsonString(dt)
        readDt = (DateTest) TestUtil.readJsonObject(json)

        exp.setTime(dt.birthDay)
        act.setTime(readDt.birthDay)
        compareDatePortion(exp, act)
        compareTimePortion(exp, act)

        exp.setTime(dt.anniversary)
        act.setTime(readDt.anniversary)
        compareDatePortion(exp, act)
        compareTimePortion(exp, act)

        exp.setTime(dt.christmas)
        act.setTime(readDt.christmas)
        compareDatePortion(exp, act)
        compareTimePortion(exp, act)

        // Version with milliseconds
        args.clear()
        args.put(JsonWriter.DATE_FORMAT, "yyyy-MM-dd'T'HH:mm:ss.SSS")
        json = JsonWriter.objectToJson(dt, args)
        readDt = (DateTest) TestUtil.readJsonObject(json)

        exp.setTime(dt.birthDay)
        act.setTime(readDt.birthDay)
        compareDatePortion(exp, act)
        compareTimePortion(exp, act)

        exp.setTime(dt.anniversary)
        act.setTime(readDt.anniversary)
        compareDatePortion(exp, act)
        compareTimePortion(exp, act)

        exp.setTime(dt.christmas)
        act.setTime(readDt.christmas)
        compareDatePortion(exp, act)
        compareTimePortion(exp, act)

        // MM/DD/YYYY format
        args.clear()
        args.put(JsonWriter.DATE_FORMAT, 'MM/dd/yyyy HH:mm:ss')
        json = JsonWriter.objectToJson(dt, args)
        readDt = (DateTest) TestUtil.readJsonObject(json)

        exp.setTime(dt.birthDay)
        act.setTime(readDt.birthDay)
        compareDatePortion(exp, act)
        compareTimePortion(exp, act)

        exp.setTime(dt.anniversary)
        act.setTime(readDt.anniversary)
        compareDatePortion(exp, act)
        compareTimePortion(exp, act)

        exp.setTime(dt.christmas)
        act.setTime(readDt.christmas)
        compareDatePortion(exp, act)
        compareTimePortion(exp, act)

        // Nov 15, 2013 format
        args.clear()
        args.put(JsonWriter.DATE_FORMAT, 'MMM dd, yyyy HH:mm.ss')
        json = JsonWriter.objectToJson(dt, args)
        TestUtil.printLine("json = " + json)
        readDt = (DateTest) TestUtil.readJsonObject(json)

        exp.setTime(dt.birthDay)
        act.setTime(readDt.birthDay)
        compareDatePortion(exp, act)
        compareTimePortion(exp, act)

        exp.setTime(dt.anniversary)
        act.setTime(readDt.anniversary)
        compareDatePortion(exp, act)
        compareTimePortion(exp, act)

        exp.setTime(dt.christmas)
        act.setTime(readDt.christmas)
        compareDatePortion(exp, act)
        compareTimePortion(exp, act)

        // 15 Nov 2013 format
        args.clear()
        args.put(JsonWriter.DATE_FORMAT, 'dd MMM yyyy HH:mm.ss')
        json = JsonWriter.objectToJson(dt, args)
        TestUtil.printLine('json = ' + json)
        readDt = (DateTest) TestUtil.readJsonObject(json)

        exp.setTime(dt.birthDay)
        act.setTime(readDt.birthDay)
        compareDatePortion(exp, act)
        compareTimePortion(exp, act)

        exp.setTime(dt.anniversary)
        act.setTime(readDt.anniversary)
        compareDatePortion(exp, act)
        compareTimePortion(exp, act)

        exp.setTime(dt.christmas)
        act.setTime(readDt.christmas)
        compareDatePortion(exp, act)
        compareTimePortion(exp, act)
    }

    /**
     * Instantiate off of each others JSON String, proving Date for long substitution works.  This will work on any
     * field that is of type Date or Long.  It will not work when the Dates are inside a Collection, for example.
     * <p/>
     * This substitution trick allows Date fields to be converted to long in order to save memory 16 bytes of memory
     * per date.  (Date's are more than 8 bytes, longs are 8).
     */
    @Test
    void testDateLongSubstitution()
    {
        long now = System.currentTimeMillis()
        DateTrick d = new DateTrick()
        d._userDate = new Date(now)
        LongTrick l = new LongTrick()
        l._userDate = now
        String jsonOut1 = TestUtil.getJsonString(d)
        TestUtil.printLine(jsonOut1)
        String jsonOut2 = TestUtil.getJsonString(l)
        TestUtil.printLine(jsonOut2)
        jsonOut1 = jsonOut1.replace('$Date', '$Long')
        jsonOut2 = jsonOut2.replace('$Long', '$Date')
        l = (LongTrick) TestUtil.readJsonObject(jsonOut1)
        d = (DateTrick) TestUtil.readJsonObject(jsonOut2)
        assert d._userDate.time == l._userDate
    }

    @Test
    void testDateMissingValue()
    {
        try
        {
            TestUtil.readJsonObject('[{"@type":"date"}]')
            fail()
        }
        catch (Exception ignored) { }
    }

    @Test
    void testDates()
    {
        // As root
        long now = System.currentTimeMillis()
        Date utilDate = new Date(now)
        java.sql.Date sqlDate = new java.sql.Date(now)
        Timestamp sqlTimestamp = new Timestamp(now)

        String json = TestUtil.getJsonString(utilDate)
        TestUtil.printLine(json)
        Date checkDate = (Date) TestUtil.readJsonObject(json)
        assertTrue(checkDate instanceof Date)
        assertEquals(checkDate, utilDate)

        json = TestUtil.getJsonString(sqlDate)
        TestUtil.printLine(json)
        java.sql.Date checkSqlDate = (java.sql.Date)TestUtil.readJsonObject(json)
        assertTrue(checkSqlDate instanceof java.sql.Date)
        assertEquals(checkSqlDate, sqlDate)

        json = TestUtil.getJsonString(sqlTimestamp)
        TestUtil.printLine(json)
        Timestamp checkSqlTimestamp = (Timestamp)TestUtil.readJsonObject(json)
        assertTrue(checkSqlTimestamp instanceof Timestamp)
        assertEquals(checkSqlTimestamp, sqlTimestamp)

        // In Object[]
        Object[] dates = [utilDate, sqlDate, sqlTimestamp] as Object[];
        json = TestUtil.getJsonString(dates)
        TestUtil.printLine(json)
        Object[] checkDates = (Object[]) TestUtil.readJsonObject(json)
        assertTrue(checkDates.length == 3)
        assertTrue(checkDates[0] instanceof Date)
        assertTrue(checkDates[1] instanceof java.sql.Date)
        assertTrue(checkDates[2] instanceof Timestamp)
        assertEquals(checkDates[0], utilDate)
        assertEquals(checkDates[1], sqlDate)
        assertEquals(checkDates[2], sqlTimestamp)

        // In Typed[]
        Date[] utilDates = [utilDate] as Date[];
        json = TestUtil.getJsonString(utilDates)
        TestUtil.printLine(json)
        Date[] checkUtilDates = (Date[]) TestUtil.readJsonObject(json)
        assertTrue(checkUtilDates.length == 1)
        assertTrue(checkUtilDates[0] instanceof Date)
        assertEquals(checkUtilDates[0], utilDate)

        java.sql.Date[] sqlDates = [sqlDate] as java.sql.Date[]
        json = TestUtil.getJsonString(sqlDates)
        TestUtil.printLine(json)
        java.sql.Date[] checkSqlDates = (java.sql.Date[]) TestUtil.readJsonObject(json)
        assertTrue(checkSqlDates.length == 1)
        assertTrue(checkSqlDates[0] instanceof java.sql.Date)
        assertEquals(checkSqlDates[0], sqlDate)

        Timestamp[] sqlTimestamps = [sqlTimestamp] as Timestamp[]
        json = TestUtil.getJsonString(sqlTimestamps)
        TestUtil.printLine(json)
        Timestamp[] checkTimestamps = (Timestamp[]) TestUtil.readJsonObject(json)
        assertTrue(checkTimestamps.length == 1)
        assertTrue(checkTimestamps[0] instanceof Timestamp)
        assertEquals(checkTimestamps[0], sqlTimestamp)

        // as Object field
        ObjectDateField dateField = new ObjectDateField(utilDate)
        json = TestUtil.getJsonString(dateField)
        TestUtil.printLine(json)
        ObjectDateField readDateField = (ObjectDateField) TestUtil.readJsonObject(json)
        assertTrue(readDateField.date instanceof Date)
        assertEquals(readDateField.date, utilDate)

        dateField = new ObjectDateField(sqlDate)
        json = TestUtil.getJsonString(dateField)
        TestUtil.printLine(json)
        readDateField = (ObjectDateField) TestUtil.readJsonObject(json)
        assertTrue(readDateField.date instanceof java.sql.Date)
        assertEquals(readDateField.date, sqlDate)

        dateField = new ObjectDateField(sqlTimestamp)
        json = TestUtil.getJsonString(dateField)
        TestUtil.printLine(json)
        readDateField = (ObjectDateField) TestUtil.readJsonObject(json)
        assertTrue(readDateField.date instanceof Timestamp)
        assertEquals(readDateField.date, sqlTimestamp)

        // as Typed field
        DateField typedDateField = new DateField(utilDate)
        json = TestUtil.getJsonString(typedDateField)
        TestUtil.printLine(json)
        DateField readTypeDateField = (DateField) TestUtil.readJsonObject(json)
        assertTrue(readTypeDateField.date instanceof Date)
        assertEquals(readTypeDateField.date, utilDate)

        SqlDateField sqlDateField = new SqlDateField(sqlDate)
        json = TestUtil.getJsonString(sqlDateField)
        TestUtil.printLine(json)
        SqlDateField readSqlDateField = (SqlDateField) TestUtil.readJsonObject(json)
        assertTrue(readSqlDateField.date instanceof java.sql.Date)
        assertEquals(readSqlDateField.date, sqlDate)

        TimestampField timestampField = new TimestampField(sqlTimestamp)
        json = TestUtil.getJsonString(timestampField)
        TestUtil.printLine(json)
        TimestampField readTimestampField = (TimestampField) TestUtil.readJsonObject(json)
        assertTrue(readTimestampField.date instanceof Timestamp)
        assertEquals(readTimestampField.date, sqlTimestamp)
    }

    @Test
    void testSqlDate()
    {
        long now = System.currentTimeMillis()
        Date[] dates = [new Date(now), new java.sql.Date(now), new Timestamp(now) ] as Date[]
        String json = TestUtil.getJsonString(dates)
        TestUtil.printLine('json=' + json)
        Date[] dates2 = (Date[]) TestUtil.readJsonObject(json)
        assertTrue(dates2.length == 3)
        assertTrue(dates2[0].equals(new Date(now)))
        assertTrue(dates2[1].equals(new java.sql.Date(now)))
        Timestamp stamp = (Timestamp) dates2[2]
        assertTrue(stamp.time == dates[0].time)
        assertTrue(stamp.time == now)
    }

    @Test
    void testNullDateHandling()
    {
        DateField dateField = new DateField(new Date())
        dateField.date = null
        String json = TestUtil.getJsonString(dateField)
        DateField df = TestUtil.readJsonObject(json)
        assert df.date == null
    }

    @Test
    void testDateReaderNullHandling()
    {
        Readers.DateReader reader = new Readers.DateReader()
        try
        {
            reader.read(null, new ArrayDeque<JsonObject<String,Object>>(), [:])
            fail()
        }
        catch (JsonIoException e)
        {
            assert e.message.toLowerCase().contains('unable to parse')
            assert e.message.toLowerCase().contains('null')
        }
    }

    @Test
    void testJavaDefaultTimeFormatParsing()
    {
        Date now = new Date()
        String nowStr = now.toString()
        Readers.DateReader reader = new Readers.DateReader()
        Date now2 = reader.read(nowStr, new ArrayDeque<JsonObject<String,Object>>(), [:])
        assert nowStr == now2.toString()
    }

    @Test
    void testDateWithTimeZoneOffsetParsing()
    {
        String date = "9 July 1930 11:02-05:00"
        Readers.DateReader reader = new Readers.DateReader()
        Date then = reader.read(date, new ArrayDeque<JsonObject<String,Object>>(), [:])

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone('UTC'))
        cal.clear()
        cal.set(1930, 6, 9, 16, 2, 0)
        assert then == cal.getTime()
    }

    @Test
    void testDayRemainderRemoval()
    {
        String date = "sat 6 Jun 2015"
        Readers.DateReader reader = new Readers.DateReader()
        Date date1 = reader.read(date, new ArrayDeque<JsonObject<String, Object>>(), [:])

        Calendar c = Calendar.getInstance()
        c.setTime(date1)
        assert c.get(Calendar.YEAR) == 2015
        assert c.get(Calendar.MONTH) == 5
        assert c.get(Calendar.DAY_OF_MONTH) == 6
    }

    @Test
    void testBadDayOfWeek()
    {
        String date = "crunchy 6 Jun 2015"
        Readers.DateReader reader = new Readers.DateReader()
        try
        {
            reader.read(date, new ArrayDeque<JsonObject<String, Object>>(), [:])
            fail()
        }
        catch (JsonIoException e)
        {
            assert e.message.toLowerCase().contains('crunchy')
        }
    }

    @Test
    void testBadMonth()
    {
        String date = "2015/13/1"
        Readers.DateReader reader = new Readers.DateReader()
        try
        {
            reader.read(date, new ArrayDeque<JsonObject<String, Object>>(), [:])
            fail()
        }
        catch (JsonIoException e)
        {
            assert e.message.toLowerCase().contains('between 1 and 12')
        }
    }

    @Test
    void testBadDay()
    {
        String date = "2015/9/34"
        Readers.DateReader reader = new Readers.DateReader()
        try
        {
            reader.read(date, new ArrayDeque<JsonObject<String, Object>>(), [:])
            fail()
        }
        catch (JsonIoException e)
        {
            assert e.message.toLowerCase().contains('between 1 and 31')
        }
    }

    @Test
    void testBadHour()
    {
        String date = "2015/9/30 25:30"
        Readers.DateReader reader = new Readers.DateReader()
        try
        {
            reader.read(date, new ArrayDeque<JsonObject<String, Object>>(), [:])
            fail()
        }
        catch (JsonIoException e)
        {
            assert e.message.toLowerCase().contains('between 0 and 23')
        }
    }

    @Test
    void testBadMinute()
    {
        String date = "2015/9/30 20:65"
        Readers.DateReader reader = new Readers.DateReader()
        try
        {
            reader.read(date, new ArrayDeque<JsonObject<String, Object>>(), [:])
            fail()
        }
        catch (JsonIoException e)
        {
            assert e.message.toLowerCase().contains('between 0 and 59')
        }
    }

    @Test
    void testBadSecond()
    {
        String date = "2015/9/30 20:55:70"
        Readers.DateReader reader = new Readers.DateReader()
        try
        {
            reader.read(date, new ArrayDeque<JsonObject<String, Object>>(), [:])
            fail()
        }
        catch (JsonIoException e)
        {
            assert e.message.toLowerCase().contains('between 0 and 59')
        }
    }

    @Test
    void testZForGMT()
    {
        String date = "2015/9/30 20:55Z"
        Readers.DateReader reader = new Readers.DateReader()
        Date date1 = reader.read(date, new ArrayDeque<JsonObject<String, Object>>(), [:])
        // Not having exception while including the 'Z' is the test (no assertion)
    }
}
