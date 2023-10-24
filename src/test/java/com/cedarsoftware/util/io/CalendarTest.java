package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.*;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class CalendarTest
{
    @Test
    public void testCalendarAsField()
    {
        Calendar greg = new GregorianCalendar();
        greg.setTimeZone(TimeZone.getTimeZone("PST"));
        greg.set(1965, 11, 17, 14, 30, 16);
        TestCalendar tc = new TestCalendar();
        tc._greg = (GregorianCalendar) greg;
        Calendar now = Calendar.getInstance();
        tc._cal = now;
        String json = TestUtil.getJsonString(tc);
        TestUtil.printLine("json=" + json);

        tc = TestUtil.readJsonObject(json);
        Assertions.assertEquals(now, tc._cal);
        Assertions.assertEquals(greg, tc._greg);
    }

    @Test
    public void testCalendarTypedArray()
    {
        GregorianCalendar[] gregs = new GregorianCalendar[]{new GregorianCalendar()};
        String json = TestUtil.getJsonString(gregs);
        TestUtil.printLine("json=" + json);
        GregorianCalendar[] gregs2 = (GregorianCalendar[]) TestUtil.readJsonObject(json);
        Assertions.assertEquals(gregs2[0], gregs[0]);
    }

    @Test
    public void testCalendarUntypedArray()
    {
        Calendar estCal = TestUtil.readJsonObject("{\"@type\":\"java.util.GregorianCalendar\",\"time\":\"1965-12-17T09:30:16.623-0500\",\"zone\":\"EST\"}");
        Calendar utcCal = TestUtil.readJsonObject("{\"@type\":\"java.util.GregorianCalendar\",\"time\":\"1965-12-17T14:30:16.623-0000\"}");
        String json = TestUtil.getJsonString(new Object[]{estCal, utcCal});
        TestUtil.printLine("json=" + json);
        Object[] oa = TestUtil.readJsonObject(json);
        Assertions.assertEquals(2, oa.length);
        Assertions.assertEquals((oa[0]), estCal);
        Assertions.assertEquals((oa[1]), utcCal);
    }

    @Test
    public void testCalendarCollection()
    {
        List<Calendar> gregs = new ArrayList<>();
        gregs.add(new GregorianCalendar());
        String json = TestUtil.getJsonString(gregs);
        TestUtil.printLine("json=" + json);
        List<Calendar> gregs2 = TestUtil.readJsonObject(json);
        Assertions.assertEquals(1, gregs2.size());
        Assertions.assertEquals(gregs2.get(0), gregs.get(0));
    }

    @Test
    public void testCalendarInMapValue()
    {
        Calendar now = Calendar.getInstance();
        Map<String, Calendar> map = new LinkedHashMap<>();
        map.put("c", now);
        String json = TestUtil.getJsonString(map);
        TestUtil.printLine("json=" + json);

        Calendar cal = map.get("c");
        Assertions.assertEquals(cal, now);
    }

    @Test
    public void testCalendarInMapKey()
    {
        Calendar now = Calendar.getInstance();
        Map<Calendar, String> map = new LinkedHashMap<>();
        map.put(now, "c");
        String json = TestUtil.getJsonString(map);
        TestUtil.printLine("json=" + json);

        Iterator<Calendar> i = map.keySet().iterator();
        Calendar cal = i.next();
        Assertions.assertEquals(cal, now);
    }

    @Test
    public void testCalendarInObjectArray()
    {
        Calendar now = Calendar.getInstance();
        String json = TestUtil.getJsonString(new Object[]{now});
        TestUtil.printLine("json=" + json);

        Map<String, Object> args = new HashMap<>();
        args.put(JsonReader.USE_MAPS, true);
        Object[] items = JsonReader.jsonToJava(json, args);
        Map item = (Map) items[0];
        Assertions.assertTrue(item.containsKey("time"));
        Assertions.assertTrue(item.containsKey("zone"));
    }

    @Test
    public void testBadCalendar()
    {
        try
        {
            TestUtil.readJsonObject("{\"@type\":\"java.util.GregorianCalendar\",\"time\":\"2011-12-08X13:29:58.822-0500\",\"zone\":\"bad zone\"}");
            fail();
        }
        catch (Exception ignored)
        { }
    }

    @Test
    public void testCalendar()
    {
        Calendar greg = new GregorianCalendar();
        greg.setTimeZone(TimeZone.getTimeZone("PST"));
        greg.set(1965, 11, 17, 14, 30, 16);
        String json = TestUtil.getJsonString(greg);
        TestUtil.printLine("json = " + json);

        Calendar cal = (Calendar) TestUtil.readJsonObject(json);
        Assertions.assertEquals(cal, greg);

        greg = new GregorianCalendar();
        greg.setTimeZone(TimeZone.getTimeZone("EST"));
        greg.set(2011, 11, 8, 13, 29, 48);
        json = TestUtil.getJsonString(greg);
        TestUtil.printLine("json=" + json);

        Calendar[] cals = new Calendar[]{greg};
        json = TestUtil.getJsonString(cals);
        TestUtil.printLine("json=" + json);
        cals = TestUtil.readJsonObject(json);
        Assertions.assertEquals(cals[0], greg);
        TestUtil.printLine("json=" + json);

        TestCalendar testCal = new TestCalendar();
        testCal._cal = cal;
        testCal._greg = (GregorianCalendar) greg;
        json = TestUtil.getJsonString(testCal);
        TestUtil.printLine("json=" + json);

        testCal = TestUtil.readJsonObject(json);
        Assertions.assertEquals(testCal._cal, cal);
        Assertions.assertEquals(testCal._greg, greg);

        Calendar estCal = TestUtil.readJsonObject("{\"@type\":\"java.util.GregorianCalendar\",\"time\":\"1965-12-17T09:30:16.623-0500\"}");
        Calendar utcCal = TestUtil.readJsonObject("{\"@type\":\"java.util.GregorianCalendar\",\"time\":\"1965-12-17T14:30:16.623-0000\"}");
        Assertions.assertEquals(estCal, utcCal);

        json = TestUtil.getJsonString(new Object[]{estCal, utcCal});
        Object[] oa = TestUtil.readJsonObject(json);
        Assertions.assertEquals(2, oa.length);
        Assertions.assertEquals((oa[0]), estCal);
        Assertions.assertEquals((oa[1]), utcCal);
    }

    public static class TestCalendar implements Serializable
    {
        private Calendar _cal;
        private GregorianCalendar _greg;
    }
}
