package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

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
class TestCalendars
{
    public static class TestCalendar implements Serializable
    {
        private Calendar _cal;
        private GregorianCalendar _greg;
    }

    @Test
    void testCalendarAsField()
    {
        Calendar greg = new GregorianCalendar()
        greg.setTimeZone(TimeZone.getTimeZone("PST"))
        greg.set(1965, 11, 17, 14, 30, 16)
        TestCalendar tc = new TestCalendar()
        tc._greg = (GregorianCalendar) greg
        Calendar now = Calendar.instance
        tc._cal = now;
        String json = TestUtil.getJsonString(tc)
        TestUtil.printLine("json=" + json)

        tc = (TestCalendar) TestUtil.readJsonObject(json)
        assertTrue(now.equals(tc._cal))
        assertTrue(greg.equals(tc._greg))
    }

    @Test
    void testCalendarTypedArray()
    {
        GregorianCalendar[] gregs = [new GregorianCalendar()] as GregorianCalendar[]
        String json = TestUtil.getJsonString(gregs)
        TestUtil.printLine("json=" + json)
        GregorianCalendar[] gregs2 = (GregorianCalendar[]) TestUtil.readJsonObject(json)
        assertTrue(gregs2[0].equals(gregs[0]))
    }

    @Test
    void testCalendarUntypedArray()
    {
        Calendar estCal = (Calendar) TestUtil.readJsonObject('{"@type":"java.util.GregorianCalendar","time":"1965-12-17T09:30:16.623-0500","zone":"EST"}')
        Calendar utcCal = (Calendar) TestUtil.readJsonObject('{"@type":"java.util.GregorianCalendar","time":"1965-12-17T14:30:16.623-0000"}')
        String json = TestUtil.getJsonString([estCal, utcCal] as Object[])
        TestUtil.printLine("json=" + json)
        Object[] oa = (Object[]) TestUtil.readJsonObject(json)
        assertTrue(oa.length == 2)
        assertTrue((oa[0]).equals(estCal))
        assertTrue((oa[1]).equals(utcCal))
    }

    @Test
    void testCalendarCollection()
    {
        List gregs = new ArrayList()
        gregs.add(new GregorianCalendar())
        String json = TestUtil.getJsonString(gregs)
        TestUtil.printLine("json=" + json)
        List gregs2 = (List) TestUtil.readJsonObject(json)
        assertTrue(gregs2.size() == 1)
        assertTrue(gregs2.get(0).equals(gregs.get(0)))
    }

    @Test
    void testCalendarInMapValue()
    {
        Calendar now = Calendar.instance
        Map map = [:]
        map.c = now
        String json = TestUtil.getJsonString(map)
        TestUtil.printLine("json=" + json)

        Calendar cal = (Calendar) map.c
        assertTrue(cal.equals(now))
    }

    @Test
    void testCalendarInMapKey()
    {
        Calendar now = Calendar.instance
        Map map = [:]
        map[now] = "c"
        String json = TestUtil.getJsonString(map)
        TestUtil.printLine("json=" + json)

        Iterator i = map.keySet().iterator()
        Calendar cal = (Calendar) i.next()
        assertTrue(cal.equals(now))
    }

    @Test
    void testCalendarInMapofMaps()
    {
        Calendar now = Calendar.instance
        String json = TestUtil.getJsonString([now] as Object[])
        TestUtil.printLine("json=" + json)

        Object[] items = (Object[]) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        Map item = (Map) items[0]
        assertTrue(item.containsKey("time"))
        assertTrue(item.containsKey("zone"))
    }

    @Test
    void testBadCalendar()
    {
        try
        {
            TestUtil.readJsonObject('{"@type":"java.util.GregorianCalendar","time":"2011-12-08X13:29:58.822-0500","zone":"bad zone"}')
            fail()
        }
        catch(Exception ignored) { }
    }

    @Test
    void testCalendar()
    {
        Calendar greg = new GregorianCalendar()
        greg.setTimeZone(TimeZone.getTimeZone("PST"))
        greg.set(1965, 11, 17, 14, 30, 16)
        String json = TestUtil.getJsonString(greg)
        TestUtil.printLine("json = " + json)

        Calendar cal = (Calendar) TestUtil.readJsonObject(json)
        assertTrue(cal.equals(greg))

        greg = new GregorianCalendar()
        greg.setTimeZone(TimeZone.getTimeZone("EST"))
        greg.set(2011, 11, 8, 13, 29, 48)
        json = TestUtil.getJsonString(greg)
        TestUtil.printLine("json=" + json)

        Calendar[] cals = [greg] as Calendar[]
        json = TestUtil.getJsonString(cals)
        TestUtil.printLine("json=" + json)
        cals = (Calendar[]) TestUtil.readJsonObject(json)
        assertTrue(cals[0].equals(greg))
        TestUtil.printLine("json=" + json)

        TestCalendar testCal = new TestCalendar()
        testCal._cal = cal;
        testCal._greg = (GregorianCalendar) greg;
        json = TestUtil.getJsonString(testCal)
        TestUtil.printLine("json=" + json)

        testCal = (TestCalendar) TestUtil.readJsonObject(json)
        assertTrue(testCal._cal.equals(cal))
        assertTrue(testCal._greg.equals(greg))

        Calendar estCal = (Calendar) TestUtil.readJsonObject('{"@type":"java.util.GregorianCalendar","time":"1965-12-17T09:30:16.623-0500"}')
        Calendar utcCal = (Calendar) TestUtil.readJsonObject('{"@type":"java.util.GregorianCalendar","time":"1965-12-17T14:30:16.623-0000"}')
        assertTrue(estCal.equals(utcCal))

        json = TestUtil.getJsonString([estCal, utcCal] as Object[])
        Object[] oa = (Object[]) TestUtil.readJsonObject(json)
        assertTrue(oa.length == 2)
        assertTrue((oa[0]).equals(estCal))
        assertTrue((oa[1]).equals(utcCal))
    }
}
