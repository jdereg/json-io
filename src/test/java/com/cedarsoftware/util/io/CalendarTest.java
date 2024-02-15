package com.cedarsoftware.util.io;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.cedarsoftware.util.io.factory.ConvertableFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class CalendarTest
{
    static class Calendars
    {
        GregorianCalendar[] calendars1;
        Calendar[] calendars2;
        Object[] calendars3;

        Calendars()
        {
            Calendar now = Calendar.getInstance();
            calendars1 = new GregorianCalendar[] {(GregorianCalendar) now, (GregorianCalendar) now};
            calendars2 = new Calendar[] {now, now};
            calendars3 = new Object[] {now, now};
        }
    }
    
    @Test
    void testCalendarArray()
    {
        Calendars cals = new Calendars();
        String json = TestUtil.toJson(cals);
        Calendars calsEx = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);

        assert calsEx.calendars1.length == 2;
        assert calsEx.calendars2.length == 2;
        assert calsEx.calendars3.length == 2;

        assert cals.calendars1[0].equals(calsEx.calendars1[0]);
        assert cals.calendars1[1].equals(calsEx.calendars1[1]);
        assertNotSame(calsEx.calendars1[0], calsEx.calendars1[1]);

        assert cals.calendars2[0].equals(calsEx.calendars2[0]);
        assert cals.calendars2[1].equals(calsEx.calendars2[1]);
        assertNotSame(calsEx.calendars2[0], calsEx.calendars2[1]);

        assert cals.calendars3[0].equals(calsEx.calendars3[0]);
        assert cals.calendars3[1].equals(calsEx.calendars3[1]);
        assertNotSame(calsEx.calendars3[0], calsEx.calendars3[1]);

        assertNotSame(calsEx.calendars1[0], calsEx.calendars2[0]);
        assertNotSame(calsEx.calendars2[0], calsEx.calendars3[0]);

        assertNotSame(calsEx.calendars1[1], calsEx.calendars2[1]);
        assertNotSame(calsEx.calendars2[1], calsEx.calendars3[1]);
    }

    @Test
    void testCalendarAsField()
    {
        Calendar greg = new GregorianCalendar();
        greg.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
        greg.set(1965, 11, 17, 14, 30, 16);
        greg.set(Calendar.MILLISECOND, 227);

        TestCalendar tc = new TestCalendar();
        tc._greg = (GregorianCalendar) greg;
        Calendar now = Calendar.getInstance();
        tc._cal = now;
        String json = TestUtil.toJson(tc);
        TestUtil.printLine("json=" + json);

        tc = TestUtil.toObjects(json, null);
        Assertions.assertEquals(now, tc._cal);
        Assertions.assertEquals(greg, tc._greg);
    }

    @Test
    void testCalendarTypedArray()
    {
        GregorianCalendar[] gregs = new GregorianCalendar[]{new GregorianCalendar()};
        String json = TestUtil.toJson(gregs);
        TestUtil.printLine("json=" + json);
        GregorianCalendar[] gregs2 = (GregorianCalendar[]) TestUtil.toObjects(json, null);
        Assertions.assertEquals(gregs2[0], gregs[0]);
    }

    @Test
    void testCalendarUntypedArray()
    {
        Calendar estCal = TestUtil.toObjects("{\"@type\":\"java.util.GregorianCalendar\",\"time\":\"1965-12-17T09:30:16.623-0500\",\"zone\":\"EST\"}", null);
        Calendar utcCal = TestUtil.toObjects("{\"@type\":\"java.util.GregorianCalendar\",\"time\":\"1965-12-17T14:30:16.623-0000\"}", null);
        String json = TestUtil.toJson(new Object[]{estCal, utcCal});
        TestUtil.printLine("json=" + json);
        Object[] oa = TestUtil.toObjects(json, null);
        Assertions.assertEquals(2, oa.length);
        Assertions.assertEquals((oa[0]), estCal);
        Assertions.assertEquals((oa[1]), utcCal);
    }

    @Test
    void testCalendarCollection()
    {
        List<Calendar> gregs = new ArrayList<>();
        gregs.add(new GregorianCalendar());
        String json = TestUtil.toJson(gregs);
        TestUtil.printLine("json=" + json);
        List<Calendar> gregs2 = TestUtil.toObjects(json, null);
        Assertions.assertEquals(1, gregs2.size());
        Assertions.assertEquals(gregs2.get(0), gregs.get(0));
    }

    @Test
    void testCalendarInMapValue()
    {
        Calendar now = Calendar.getInstance();
        Map<String, Calendar> map = new LinkedHashMap<>();
        map.put("c", now);
        String json = TestUtil.toJson(map);
        TestUtil.printLine("json=" + json);

        Calendar cal = map.get("c");
        Assertions.assertEquals(cal, now);
    }

    @Test
    void testCalendarInMapKey()
    {
        Calendar now = Calendar.getInstance();
        Map<Calendar, String> map = new LinkedHashMap<>();
        map.put(now, "c");
        String json = TestUtil.toJson(map);
        TestUtil.printLine("json=" + json);

        Iterator<Calendar> i = map.keySet().iterator();
        Calendar cal = i.next();
        Assertions.assertEquals(cal, now);
    }

    @Test
    void testCalendarInObjectArray()
    {
        Calendar now = Calendar.getInstance();
        String json = TestUtil.toJson(new Object[]{now});
        TestUtil.printLine("json=" + json);

        Object[] items = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsNativeJsonObjects().build(), null);
        Map item = (Map) items[0];
        Assertions.assertTrue(item.containsKey("time"));
        Assertions.assertTrue(item.containsKey("zone"));
    }

    @Test
    void testOldFormatInMap() {
        ReadOptions options = createOldOptionsFormat("America/New_York");
        String json = loadJsonForTest("old-format-nested-in-map.json");

        LinkedHashMap map = TestUtil.toObjects(json, options, LinkedHashMap.class);

        Calendar calendar = (Calendar) map.get("c");
        //assertCalendar(calendar, "America/New_York", 2023, 11, 19, 17, 20, 38, 79);
    }

    @Test
    void testOldFormatNestedInObject() {
        Calendar expected1 = new GregorianCalendar();
        expected1.setTimeZone(TimeZone.getTimeZone("PST"));
        expected1.set(1965, 11, 17, 14, 30, 16);
        expected1.set(Calendar.MILLISECOND, 257);

        ReadOptions options = createOldOptionsFormat("America/New_York");
        String json = loadJsonForTest("old-format-nested-in-object.json");

        TestCalendar object = TestUtil.toObjects(json, options, TestCalendar.class);

        assertThat(object._greg).isEqualTo(expected1);


        assertCalendar(object._cal, "America/New_York", 2023, 11, 19, 17, 31, 9, 257);
        assertCalendar(object._greg, "PST", 1965, 12, 17, 14, 30, 16, 257);
    }

    @Test
    void testOldFormatInArray() {
        ReadOptions options = createOldOptionsFormat("America/New_York");
        String json = loadJsonForTest("old-format-nested-in-array.json");

        Object[] object = TestUtil.toObjects(json, options, null);

        assertCalendar((Calendar) object[0], "America/New_York", 2023, 11, 19, 18, 19, 15, 476);
    }

    private static Stream<Arguments> varyingZones() {
        return Stream.of(
                Arguments.of(TimeZone.getTimeZone("America/New_York")),
                Arguments.of(TimeZone.getTimeZone("America/Los_Angeles")),
                Arguments.of(TimeZone.getTimeZone("EST")),
                Arguments.of(TimeZone.getTimeZone("Asia/Tapei"))
        );
    }

    @Test
    void testOldFormatInUntypedArray() {
        ReadOptions options = createOldOptionsFormat("America/Los_Angeles");
        String json = loadJsonForTest("old-format-nested-in-untyped-array.json");

        Object[] object = TestUtil.toObjects(json, options, null);

        assertCalendar((Calendar) object[0], "EST", 1965, 12, 17, 9, 30, 16, 623);
        assertCalendar((Calendar) object[1], "America/New_York", 1965, 12, 17, 9, 30, 16, 623);
    }

    @ParameterizedTest
    @MethodSource("varyingZones")
    void testOldFormat_withDifferentTimeZoneThanDefault_returnsSameValueBecauseTimeZoneIsIncluded(TimeZone zone) {
        Calendar expected = new GregorianCalendar();
        expected.setTimeZone(TimeZone.getTimeZone("PST"));
        expected.set(1965, 11, 17, 14, 30, 16);
        expected.set(Calendar.MILLISECOND, 228);

        ReadOptions options = createOldOptionsFormat(zone.getID());
        String json = loadJsonForTest("old-format-different-timezone-than-default.json");

        GregorianCalendar actual = TestUtil.toObjects(json, options, null);
        assertThat(actual.getTimeZone()).isEqualTo(expected.getTimeZone());
        assertThat(actual).isEqualTo(expected);

        assertCalendar(actual, "PST", 1965, 12, 17, 14, 30, 16, 228);
    }

    @ParameterizedTest
    @MethodSource("varyingZones")
    void testOldFormat_noTimeZoneSpecified(TimeZone zone) {
        Calendar expected = new GregorianCalendar();
        expected.setTimeZone(zone);
        expected.set(1965, 11, 17, 14, 30, 16);
        expected.set(Calendar.MILLISECOND, 228);

        ReadOptions options = createOldOptionsFormat(zone.getID());
        String json = loadJsonForTest("old-format-with-no-zone-specified-uses-default.json");

        GregorianCalendar actual = TestUtil.toObjects(json, options, null);
        assertThat(actual.getTimeZone()).isEqualTo(TimeZone.getDefault());
        assertThat(actual.get(Calendar.YEAR)).isEqualTo(1965);
        assertThat(actual.get(Calendar.MONTH)).isEqualTo(11);
        // TODO: do better asserts here.
    }

    private ReadOptions createOldOptionsFormat(String timeZone) {
        TimeZone zone = TimeZone.getTimeZone(timeZone);

        return new ReadOptionsBuilder()
                .addClassFactory(Calendar.class, new ConvertableFactory<>(Calendar.class))
                .addClassFactory(GregorianCalendar.class, new ConvertableFactory<>(GregorianCalendar.class))
                .build();
    }

    @Test
    void testBadCalendar() {
        try {
            TestUtil.toObjects("{\"@type\":\"java.util.GregorianCalendar\",\"time\":\"2011-12-08X13:29:58.822-0500\",\"zone\":\"bad zone\"}", null);
            fail();
        }
        catch (Exception e) {
            TestUtil.assertContainsIgnoreCase("could not be converted to a 'gregoriancalendar'");
        }
    }

    @Test
    void testCalendar()
    {
        Calendar greg = new GregorianCalendar();
        greg.setTimeZone(TimeZone.getTimeZone("PST"));
        greg.set(1965, 11, 17, 14, 30, 16);
        String json = TestUtil.toJson(greg);
        TestUtil.printLine("json = " + json);

        Calendar cal = TestUtil.toObjects(json, null);
        Assertions.assertEquals(cal, greg);

        greg = new GregorianCalendar();
        greg.setTimeZone(TimeZone.getTimeZone("EST"));
        greg.set(2011, 11, 8, 13, 29, 48);
        json = TestUtil.toJson(greg);
        TestUtil.printLine("json=" + json);

        Calendar[] cals = new Calendar[]{greg};
        json = TestUtil.toJson(cals);
        TestUtil.printLine("json=" + json);
        cals = TestUtil.toObjects(json, null);
        Assertions.assertEquals(cals[0], greg);
        TestUtil.printLine("json=" + json);

        TestCalendar testCal = new TestCalendar();
        testCal._cal = cal;
        testCal._greg = (GregorianCalendar) greg;
        json = TestUtil.toJson(testCal);
        TestUtil.printLine("json=" + json);

        testCal = TestUtil.toObjects(json, null);
        Assertions.assertEquals(testCal._cal, cal);
        Assertions.assertEquals(testCal._greg, greg);

        Calendar estCal = TestUtil.toObjects("{\"@type\":\"java.util.GregorianCalendar\",\"time\":\"1965-12-17T09:30:16.623-0500\"}", null);
        Calendar utcCal = TestUtil.toObjects("{\"@type\":\"java.util.GregorianCalendar\",\"time\":\"1965-12-17T14:30:16.623-0000\"}", null);
        Assertions.assertEquals(estCal, utcCal);

        json = TestUtil.toJson(new Object[]{estCal, utcCal});
        Object[] oa = TestUtil.toObjects(json, null);
        Assertions.assertEquals(2, oa.length);
        Assertions.assertEquals((oa[0]), estCal);
        Assertions.assertEquals((oa[1]), utcCal);
    }

    static class TestCalendar implements Serializable
    {
        private Calendar _cal;
        private GregorianCalendar _greg;
    }

    private String loadJsonForTest(String fileName) {
        return MetaUtils.loadResourceAsString("calendar/" + fileName);
    }

    private static void assertCalendar(Calendar calendar, String zoneId, int year, int month, int day, int hour, int minute, int second, int millis) {
        assertThat(calendar.getTimeZone().getID()).isEqualTo(zoneId);
        assertThat(calendar.get(Calendar.YEAR)).isEqualTo(year);
        assertThat(calendar.get(Calendar.MONTH)).isEqualTo(month - 1);
        assertThat(calendar.get(Calendar.DAY_OF_MONTH)).isEqualTo(day);
        assertThat(calendar.get(Calendar.HOUR_OF_DAY)).isEqualTo(hour);
        assertThat(calendar.get(Calendar.MINUTE)).isEqualTo(minute);
        assertThat(calendar.get(Calendar.SECOND)).isEqualTo(second);
        assertThat(calendar.get(Calendar.MILLISECOND)).isEqualTo(millis);
    }
}
