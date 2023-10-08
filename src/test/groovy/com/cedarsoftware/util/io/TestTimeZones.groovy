package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

import java.util.stream.Stream

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatExceptionOfType
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
class TestTimeZones
{
    public static class TestTimeZone implements Serializable
    {
        private TimeZone _zone
    }

    @Test
    void testTimeZoneAsField()
    {
        // changed away from default because that could change per user and break the test.
        TimeZone zone = TimeZone.getTimeZone("Africa/Casablanca")
        TestTimeZone tz = new TestTimeZone()
        tz._zone = zone
        String json = TestUtil.getJsonString(tz)
        TestUtil.printLine("json=" + json)

        tz = (TestTimeZone) TestUtil.readJsonObject(json)
        assertTrue(zone.equals(tz._zone))
    }

    @Test
    void testTimeZone()
    {
        TimeZone est = TimeZone.getTimeZone("EST")
        String json = TestUtil.getJsonString(est)
        TestUtil.printLine("json=" + json)
        TimeZone tz = (TimeZone) TestUtil.readJsonObject(json)
        assertTrue(tz.equals(est))

        TimeZone pst = TimeZone.getTimeZone("PST")
        json = TestUtil.getJsonString(pst)
        TestUtil.printLine("json=" + json)
        tz = (TimeZone) TestUtil.readJsonObject(json)
        assertTrue(tz.equals(pst))
    }

    @Test
    void testWithNoZone() {
        String noZone = '{"@type":"sun.util.calendar.ZoneInfo"}'

        assertThatExceptionOfType(JsonIoException.class)
                .isThrownBy({ TestUtil.readJsonObject(noZone)})
        .withMessageContaining("Could not instantiate");
    }

    @Test
    void testTimeZoneInArray()
    {
        TimeZone pst = TimeZone.getTimeZone("PST")
        String json = TestUtil.getJsonString([pst] as Object[])
        TestUtil.printLine("json=" + json)

        Object[] oArray = (Object[]) TestUtil.readJsonObject(json)
        assertTrue(oArray.length == 1)
        TimeZone tz = (TimeZone)oArray[0]
        assertTrue(tz.equals(pst))

        json = TestUtil.getJsonString([pst] as TimeZone[])
        TestUtil.printLine("json=" + json)

        Object[] tzArray = (Object[]) TestUtil.readJsonObject(json)
        assertTrue(tzArray.length == 1)
        tz = (TimeZone)tzArray[0]
        assertTrue(tz.equals(pst))
    }

    @Test
    void testTimeZoneInCollection()
    {
        TimeZone pst = TimeZone.getTimeZone("PST")
        List col = new ArrayList()
        col.add(pst)
        String json = TestUtil.getJsonString(col)
        TestUtil.printLine("json=" + json)

        col = (List) TestUtil.readJsonObject(json)
        assertTrue(col.size() == 1)
        TimeZone tz = (TimeZone) col.get(0)
        assertTrue(tz.equals(pst))
    }

    @Test
    void testTimeZoneInMapValue()
    {
        TimeZone pst = TimeZone.getTimeZone("PST")
        Map map = new HashMap()
        map.put("p", pst)
        String json = TestUtil.getJsonString(map)
        TestUtil.printLine("json=" + json)

        TimeZone tz = (TimeZone) map.get("p")
        assertTrue(tz.equals(pst))
    }

    @Test
    void testTimeZoneInMapKey()
    {
        TimeZone pst = TimeZone.getTimeZone("PST")
        Map map = new HashMap()
        map.put(pst, "p")
        String json = TestUtil.getJsonString(map)
        TestUtil.printLine("json=" + json)

        Iterator i = map.keySet().iterator()
        TimeZone tz = (TimeZone) i.next()
        assertTrue(tz.equals(pst))
    }

    @Test
    void testTimeZoneInMapofMaps()
    {
        TimeZone pst = TimeZone.getTimeZone("PST")
        String json = TestUtil.getJsonString([pst] as Object[])
        TestUtil.printLine("json=" + json)

        Object[] items = (Object[]) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        Map item = (Map) items[0]
        assertTrue(item.containsKey("zone"))
        assertTrue("PST".equals(item.zone))
    }

    @Test
    void testTimeZoneRef()
    {
        TimeZone pst = TimeZone.getTimeZone("PST")
        String json = TestUtil.getJsonString([pst, pst] as Object[])
        TestUtil.printLine("json=" + json)

        Object[] oArray = (Object[]) TestUtil.readJsonObject(json)
        assertTrue(oArray.length == 2)
        TimeZone tz = (TimeZone)oArray[0]
        assertTrue(tz.equals(pst))
        assertTrue(oArray[0] == oArray[1])
    }

    private static Stream<Arguments> argumentsForOldFormatValidation() {
        return Stream.of(
                Arguments.of("timezone-away-from-est.json", "Africa/Casablanca"),
                Arguments.of("zoneinfo-with-type-and-zone.json", "America/New_York"),
                Arguments.of("zoneinfo-zone.json", "EST")
        );
    }

    @ParameterizedTest
    @MethodSource("argumentsForOldFormatValidation")
    void testTimezone_readingJsonWithOldFormat_stillWorks(String fileName, String expectedTimeZone) throws Exception
    {
        String json = TestUtil.fetchResource("timezone/" + fileName);
        TimeZone actual = (TimeZone) TestUtil.readJsonObject(json);

        assertThat(actual.getID()).isEqualTo(expectedTimeZone);
    }


    @Test
    void testTimeZone_inGenericSubobject_serializeBackCorrectly() throws Exception {
        TimeZone url = TimeZone.getTimeZone("PST");
        GenericSubObject initial = new GenericSubObject<>(url);
        String json = TestUtil.getJsonString(initial);

        TestUtil.printLine("json=" + json);
        GenericSubObject actual = (GenericSubObject)TestUtil.readJsonObject(json);
        assertThat(actual.getObject()).isEqualTo(initial.getObject());
    }

    @Test
    void testTimeZone_inNestedObject_serializeBackCorrectly() throws Exception {
        TimeZone timeZone = TimeZone.getTimeZone("PST");
        TestTimeZones.NestedOnce expected = new TestTimeZones.NestedOnce(timeZone);
        String json = TestUtil.getJsonString(expected);
        //assertThatJsonIsNewStyle(json);

        TestUtil.printLine("json=" + json);
        TestTimeZones.NestedOnce actual = (TestTimeZones.NestedOnce)TestUtil.readJsonObject(json);

        assertThat(actual.getTimeZone()).isEqualTo(expected.getTimeZone());
    }

    @Test
    void testTimeZone_referencedInArray() throws Exception {
        TimeZone tz =  TimeZone.getTimeZone("PST")
        List<TimeZone> list = List.of(tz, tz, tz, tz, tz);
        String json = TestUtil.getJsonString(list);

        List<TimeZone> actual = (List<TimeZone>)TestUtil.readJsonObject(json);

        assertThat(actual).containsAll(list);
    }

    @Test
    void testTimeZone_referencedInObject() throws Exception {
        NestedTwice expected = new NestedTwice(TimeZone.getTimeZone("PST"));

        String json = TestUtil.getJsonString(expected);

        NestedTwice actual = (NestedTwice)TestUtil.readJsonObject(json);

        assertThat(expected.getOne()).isEqualTo(actual.getOne());
        assertThat(expected.getTwo()).isEqualTo(actual.getTwo());
    }

    private static class NestedOnce {
        private final TimeZone timeZone;

        NestedOnce(TimeZone timeZone) {
            this.timeZone = timeZone;
        }

        TimeZone getTimeZone() {
            return this.timeZone;
        }
    }

    private static class NestedTwice {
        private final TimeZone timeZone1;
        private final TimeZone timeZone2;

        NestedTwice(TimeZone initial) {
            this.timeZone1 = initial;
            this.timeZone2 = initial;
        }

        TimeZone getOne() {
            return timeZone2;
        }

        TimeZone getTwo() {
            return timeZone2;
        }
    }


}
