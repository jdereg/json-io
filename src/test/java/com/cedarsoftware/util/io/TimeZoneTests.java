package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
public class TimeZoneTests
{
    public static class TestTimeZone implements Serializable
    {
        private TimeZone _zone;
    }

    @Test
    void testTimeZoneAsField()
    {
        // changed away from default because that could change per user and break the test.
        var expected = TimeZone.getTimeZone("Africa/Casablanca");
        var tz = new TestTimeZone();
        tz._zone = expected;
        var json = TestUtil.getJsonString(tz);
        TestUtil.printLine("json=" + json);

        var actual = (TestTimeZone) TestUtil.readJsonObject(json);
        assertThat(actual._zone).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "America/New_York",
            "America/Los_Angeles",
            "Africa/Casablanca",
            "EST", // deprecated - support for this will soon disappear
            "PST", // deprecated - support for this will soon disappear
    })
    void testTimeZone(String zone)
    {
        // arrange
        var expected = TimeZone.getTimeZone(zone);

        // act
        var json = TestUtil.getJsonString(expected);
        TestUtil.printLine("json=" + json);
        var actual = (TimeZone) TestUtil.readJsonObject(json);

        // assert
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void testWithNoZone() {
        String noZone = "{\"@type\":\"java.util.TimeZone\"}";

        assertThatExceptionOfType(JsonIoException.class)
                .isThrownBy(() -> TestUtil.readJsonObject(noZone));
    }


    @Test
    void testTwoTimezones_InObjectArray()
    {
        // arrange
        var expected = new Object[] { getEST(), getPST() };

        // act
        var json = TestUtil.getJsonString(expected);
        TestUtil.printLine("json=" + json);

        var actual = (Object[]) TestUtil.readJsonObject(json);

        // assert
        assertThat(actual).hasSize(2)
                .hasOnlyElementsOfType(TimeZone.class);

        assertThat(actual[0]).isEqualTo(expected[0]);
        assertThat(actual[1]).isEqualTo(expected[1]);
    }

    @Test
    void testTwoOfSameTimeZone_inObjectArray()
    {
        // arrange
        var est = getEST();
        var objectArray = new Object[] { est, est };

        // act
        var json = TestUtil.getJsonString(objectArray);

        TestUtil.printLine("json=" + json);

        var actual = (Object[]) TestUtil.readJsonObject(json);

        // assert
        assertThat(json).containsIgnoringCase("@id")
                .containsIgnoringCase("@ref");

        assertThat(actual).hasSize(2)
                .hasOnlyElementsOfType(TimeZone.class);

        assertThat(actual[0]).isSameAs(actual[1]);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTimeZone_inCollection()
    {
        // arrange
        var expected = List.of(getEST(), getPST());

        // act
        var json = TestUtil.getJsonString(expected);
        TestUtil.printLine("json=" + json);

        var actual = (List<TimeZone>) TestUtil.readJsonObject(json);

        // assert
        assertThat(actual).hasSize(2)
                .hasSameElementsAs(expected);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTimeZone_inMap_asValue()
    {
        var expected = Map.of("p", getPST());

        var json = TestUtil.getJsonString(expected);
        TestUtil.printLine("json=" + json);

        var actual = (Map<String, TimeZone>) TestUtil.readJsonObject(json);

        assertThat(actual).hasSize(1)
                .containsAllEntriesOf(expected);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTimeZone_inMap_asKey()
    {
        var expected = Map.of(getPST(), "p");

        var json = TestUtil.getJsonString(expected);
        TestUtil.printLine("json=" + json);


        var actual = (Map<TimeZone, String>) TestUtil.readJsonObject(json);

        assertThat(actual).hasSize(1)
                .containsAllEntriesOf(expected);

    }

    @Test
    @SuppressWarnings("unchecked")
    void testTimeZone_inMapofMaps()
    {
        // arrange
        var pst = getPST();
        var expected = new Object[] { pst };

        // act
        var json = TestUtil.getJsonString(expected);
        TestUtil.printLine("json=" + json);

        var options = new ReadOptionsBuilder().returnAsMaps().build();
        Object[] items = (Object[]) JsonReader.jsonToJava(json, options);

        // assert
        Map actual = (Map) items[0];

        assertThat(actual)
                .hasSize(1)
                .containsEntry("zone", "America/Los_Angeles");
    }

    @Test
    void testTimeZone_withRefs()
    {
        // arrange
        var pst = getPST();
        var expected = new Object[] { pst, pst };

        // act
        var json = TestUtil.getJsonString(expected);
        TestUtil.printLine("json=" + json);

        var actual = (Object[]) TestUtil.readJsonObject(json);

        // assert
        assertThat(json)
                .contains("@id")
                .contains("@ref");

        assertThat(actual).hasSize(2);
        assertThat(actual[0])
                .isEqualTo(pst)
                .isSameAs(actual[1]);
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
        var json = TestUtil.fetchResource("timezone/" + fileName);
        var actual = (TimeZone) TestUtil.readJsonObject(json);

        assertThat(actual.getID()).isEqualTo(expectedTimeZone);
    }


    @Test
    void testTimeZone_inGenericSubobject_serializeBackCorrectly() throws Exception
    {
        var initial = new GenericSubObject<>(getPST());
        var json = TestUtil.getJsonString(initial);

        TestUtil.printLine("json=" + json);
        var actual = (GenericSubObject)TestUtil.readJsonObject(json);
        assertThat(actual.getObject()).isEqualTo(initial.getObject());
    }

    @Test
    void testTimeZone_inNestedObject_serializeBackCorrectly() throws Exception {
        // arrange
        var timeZone = getPST();
        var expected = new TimeZoneTests.NestedOnce(timeZone);

        // act
        var json = TestUtil.getJsonString(expected);

        TestUtil.printLine("json=" + json);
        var actual = (TimeZoneTests.NestedOnce)TestUtil.readJsonObject(json);

        // assert
        assertThat(actual.getTimeZone()).isEqualTo(expected.getTimeZone());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTimeZone_referencedInList() throws Exception {
        // arrange
        var tz =  getPST();
        var list = List.of(tz, tz, tz, tz, tz);

        // act
        var json = TestUtil.getJsonString(list);

        var actual = (List<TimeZone>)TestUtil.readJsonObject(json);

        // assert
        assertThat(json).contains("@id").contains("@ref");
        assertThat(actual)
                .containsAll(list)
                .hasSize(5);

        assertThat(actual.get(0))
                .isSameAs(actual.get(1))
                .isSameAs(actual.get(2))
                .isSameAs(actual.get(3))
                .isSameAs(actual.get(4));
    }

    @Test
    void testTimeZone_referencedInsideObject() throws Exception {
        // arrange
        NestedTwice expected = new NestedTwice(getPST());

        // act
        String json = TestUtil.getJsonString(expected);

        NestedTwice actual = (NestedTwice)TestUtil.readJsonObject(json);

        // assert
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
            return timeZone1;
        }

        TimeZone getTwo() {
            return timeZone2;
        }
    }

    private TimeZone getEST() {
        return TimeZone.getTimeZone("America/New_York");
    }

    private TimeZone getPST() {
        return TimeZone.getTimeZone("America/Los_Angeles");
    }
}
