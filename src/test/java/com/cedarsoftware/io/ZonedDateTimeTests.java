package com.cedarsoftware.io;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.cedarsoftware.io.models.NestedZonedDateTime;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
class ZonedDateTimeTests extends SerializationDeserializationMinimumTests<ZonedDateTime> {
    private static final ZoneId Z1 = ZoneId.of("America/Chicago");

    private static final ZoneId Z2 = ZoneId.of("America/Anchorage");

    private static final ZoneId Z3 = ZoneId.of("America/Los_Angeles");

    private static final ZoneId Z4 = ZoneId.of("Asia/Saigon");


    @Override
    protected boolean isReferenceable() {
        return false;
    }

    @Test
    void testSimpleCase() {
        LocalTime now = LocalTime.parse("12:34:01");
        ZonedDateTime date = ZonedDateTime.of(LocalDate.now(), now, ZoneId.of(ZoneId.getAvailableZoneIds().iterator().next()));
        ZonedDateTime date2 = ZonedDateTime.of(LocalDate.of(2022, 12, 23), now, ZoneId.of(ZoneId.getAvailableZoneIds().iterator().next()));
        NestedZonedDateTime expected = new NestedZonedDateTime(date, date2);
        String json = TestUtil.toJson(expected);
        NestedZonedDateTime result = TestUtil.toObjects(json, null);
        assertEquals(result.date1, date);
    }

    @Test
    void testOldFormat_nested_withRef() {
        String json = loadJsonForTest("old-format-nested-with-ref.json");
        NestedZonedDateTime zonedDateTime = TestUtil.toObjects(json, null);

        assertZonedDateTime(zonedDateTime.date1, 2023, 10, 22, 12, 03, 01, 4539375 * 100, "Asia/Aden", 10800);
        assertZonedDateTime(zonedDateTime.date2, 2022, 12, 23, 12, 03, 00, 4549357 * 100, "Asia/Aden", 10800);
        assertSame(zonedDateTime.date1.getOffset(), zonedDateTime.date2.getOffset());
    }

    @Test
    void testOldFormat_nested() {
        String json = loadJsonForTest("old-format-nested.json");
        NestedZonedDateTime zonedDateTime = TestUtil.toObjects(json, null);

        assertZonedDateTime(zonedDateTime.date1, 2023, 10, 22, 12, 03, 01, 4539375 * 100, "Asia/Aden", 10800);
        assertZonedDateTime(zonedDateTime.date2, 2022, 12, 23, 12, 03, 00, 4549357 * 100, "Asia/Aden", 10800);
        assertSame(zonedDateTime.date1.getOffset(), zonedDateTime.date2.getOffset());
    }

    @Test
    void testOldFormat_topLevel() {
        String json = loadJsonForTest("old-format-simple-case.json");
        ZonedDateTime zonedDateTime = TestUtil.toObjects(json, null);

        assertZonedDateTime(zonedDateTime, 2023, 10, 22, 11, 39, 27, 2496504 * 100, "Asia/Aden", 10800);
    }

    private void assertZonedDateTime(ZonedDateTime zonedDateTime, int year, int month, int day, int hour, int min, int sec, int nano, String zone, Number totalOffset) {
        assertThat(zonedDateTime.getYear()).isEqualTo(year);
        assertThat(zonedDateTime.getMonthValue()).isEqualTo(month);
        assertThat(zonedDateTime.getDayOfMonth()).isEqualTo(day);
        assertThat(zonedDateTime.getHour()).isEqualTo(hour);
        assertThat(zonedDateTime.getMinute()).isEqualTo(min);
        assertThat(zonedDateTime.getSecond()).isEqualTo(sec);
        assertThat(zonedDateTime.getNano()).isEqualTo(nano);
        assertThat(zonedDateTime.getZone()).isEqualTo(ZoneId.of(zone));
        assertThat(zonedDateTime.getOffset()).isEqualTo(ZoneOffset.ofTotalSeconds(totalOffset.intValue()));
    }

    private String loadJsonForTest(String fileName) {
        return ClassUtilities.loadResourceAsString("zoneddatetime/" + fileName);
    }

    @Override
    protected ZonedDateTime provideT1() {
        LocalDateTime localDateTime = LocalDateTime.of(2019, 12, 15, 9, 7, 16, 2000);
        return ZonedDateTime.of(localDateTime, Z1);
    }

    @Override
    protected ZonedDateTime provideT2() {
        LocalDateTime localDateTime = LocalDateTime.of(2027, 12, 23, 9, 7, 16, 2000);
        return ZonedDateTime.of(localDateTime, Z2);
    }

    @Override
    protected ZonedDateTime provideT3() {
        LocalDateTime localDateTime = LocalDateTime.of(2027, 12, 23, 6, 7, 16, 2000);
        return ZonedDateTime.of(localDateTime, Z3);
    }

    @Override
    protected ZonedDateTime provideT4() {
        LocalDateTime localDateTime = LocalDateTime.of(2027, 12, 23, 6, 7, 16, 2000);
        return ZonedDateTime.of(localDateTime, Z4);
    }

    @Override
    protected Class<ZonedDateTime> getTestClass() {
        return ZonedDateTime.class;
    }

    @Override
    protected NestedZonedDateTime provideNestedInObject_withNoDuplicates_andFieldTypeMatchesObjectType() {
        LocalDateTime localDateTime1 = LocalDateTime.of(2027, 12, 23, 6, 7, 16, 2000);
        LocalDateTime localDateTime2 = LocalDateTime.of(2027, 12, 23, 6, 7, 16, 2000);
        return new NestedZonedDateTime(
                ZonedDateTime.of(localDateTime1, Z1),
                ZonedDateTime.of(localDateTime2, Z2));
    }

    @Override
    protected ZonedDateTime[] extractNestedInObject_withMatchingFieldTypes(Object o) {
        NestedZonedDateTime nested = (NestedZonedDateTime) o;

        return new ZonedDateTime[]{
                nested.date1,
                nested.date2
        };
    }

    @Override
    protected Object provideNestedInObject_withDuplicates_andFieldTypeMatchesObjectType() {
        LocalDateTime localDateTime1 = LocalDateTime.of(2027, 12, 23, 6, 7, 16, 2000);
        return new NestedZonedDateTime(
                ZonedDateTime.of(localDateTime1, Z1));
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsJsonTypes(ZonedDateTime expected, Object actual) {
        String value = (String) actual;
        assertThat(value).isEqualTo("2027-12-23T06:07:16.000002+07:00[Asia/Saigon]");
    }

    private static Stream<Arguments> roundTripInstants() {
        return Stream.of(
                Arguments.of(ZonedDateTime.parse("9999-12-23T06:07:16.999999999+07:00[Asia/Saigon]"), DateTimeFormatter.ISO_ZONED_DATE_TIME),
                Arguments.of(ZonedDateTime.parse("2011-12-23T06:07:16.0-05:00[America/New_York]"), DateTimeFormatter.ISO_ZONED_DATE_TIME),
                Arguments.of(ZonedDateTime.ofInstant(Instant.parse("2023-11-22T15:56:12.135Z"), ZoneId.of("America/New_York"))),
                Arguments.of(ZonedDateTime.ofInstant(Instant.parse("2023-11-22T15:56:12Z"), ZoneId.of("Europe/Paris"))),
                Arguments.of(ZonedDateTime.ofInstant(Instant.parse("2023-11-22T15:56:12.1Z"), ZoneId.of("Asia/Saigon"))),
                Arguments.of(ZonedDateTime.ofInstant(Instant.parse("9999-12-31T23:59:59.999999999Z"), ZoneId.of("Etc/GMT"))),
                Arguments.of(ZonedDateTime.ofInstant(Instant.ofEpochMilli(1700668272163L), ZoneId.of("America/Los_Angeles"))),
                Arguments.of(ZonedDateTime.ofInstant(Instant.ofEpochSecond(1700668272163L/ 1000), ZoneId.of("UTC"))),   // Year is beyond YYYY format (> 4 digit year) if we don't divide by 1000
                Arguments.of(ZonedDateTime.ofInstant(Instant.ofEpochSecond(((146097L * 5L) - (30L * 365L + 7L)) * 86400L, 999999999L), ZoneId.of("UTC"))),
                Arguments.of(ZonedDateTime.ofInstant(Instant.ofEpochSecond(1700668272163L / 1000, 99999999999999L), ZoneId.of("Europe/London"))), // Year is beyond YYYY (> 4 digit year) if we don't divide by 1000
                Arguments.of(ZonedDateTime.of(LocalDateTime.of(2011, 12, 11, 9, 5, 7, 999999999), ZoneId.of("UTC"))),
                Arguments.of(ZonedDateTime.of(LocalDateTime.of(2011, 12, 11, 9, 5, 7, 999999999), ZoneId.of("UTC"))),
                Arguments.of(ZonedDateTime.of(LocalDateTime.of(2011, 12, 11, 9, 5, 7, 999999999), ZoneId.of("UTC"))),
                Arguments.of(ZonedDateTime.of(LocalDate.of(2011, 12, 11), LocalTime.of(9, 5, 7, 999999999), ZoneId.of("America/New_York")))
        );
    }

    @ParameterizedTest
    @MethodSource("roundTripInstants")
    void roundTripTests(ZonedDateTime expected) {
        String json = TestUtil.toJson(expected, new WriteOptionsBuilder().build());
        ZonedDateTime actual = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), ZonedDateTime.class);
        Map<String, Object> options = new HashMap<>();
        assert DeepEquals.deepEquals(expected, actual, options);
    }
}
