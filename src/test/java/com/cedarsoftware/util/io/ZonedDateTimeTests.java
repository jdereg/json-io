package com.cedarsoftware.util.io;

import com.cedarsoftware.util.io.models.NestedZonedDateTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ZonedDateTimeTests extends SerializationDeserializationMinimumTests<ZonedDateTime> {
    private static final ZoneId Z1 = ZoneId.of("America/Chicago");

    private static final ZoneId Z2 = ZoneId.of("America/Anchorage");

    private static final ZoneId Z3 = ZoneId.of("America/Los_Angeles");

    @Test
    void testSimpleCase() {
        var date = ZonedDateTime.of(LocalDate.now(), LocalTime.now(), ZoneId.of(ZoneId.getAvailableZoneIds().iterator().next()));
        var date2 = ZonedDateTime.of(LocalDate.of(2022, 12, 23), LocalTime.now(), ZoneId.of(ZoneId.getAvailableZoneIds().iterator().next()));
        var expected = new NestedZonedDateTime(date, date2);
        String json = TestUtil.getJsonString(expected);
        var result = (NestedZonedDateTime) TestUtil.readJsonObject(json);
        assertThat(result.date1).isEqualTo(date);
    }

    @Test
    void testOldFormat_nested() {
        String json = loadJsonForTest("old-format-nested.json");
        NestedZonedDateTime zonedDateTime = TestUtil.readJsonObject(json);

        assertZonedDateTime(zonedDateTime.date1, 2023, 10, 22, 12, 03, 00, 4539375 * 100, "Asia/Aden");
        assertZonedDateTime(zonedDateTime.date2, 2022, 12, 23, 12, 03, 00, 4549357 * 100, "Asia/Aden");
    }

    @Test
    void testOldFormat_topLevel() {
        String json = loadJsonForTest("old-format-simple-case.json");
        ZonedDateTime zonedDateTime = TestUtil.readJsonObject(json);

        assertZonedDateTime(zonedDateTime, 2023, 10, 22, 11, 39, 27, 2496504 * 100, "Asia/Aden");
    }

    private void assertZonedDateTime(ZonedDateTime zonedDateTime, int year, int month, int day, int hour, int min, int sec, int nano, String zone) {
        assertThat(zonedDateTime.getYear()).isEqualTo(year);
        assertThat(zonedDateTime.getMonthValue()).isEqualTo(month);
        assertThat(zonedDateTime.getDayOfMonth()).isEqualTo(day);
        assertThat(zonedDateTime.getHour()).isEqualTo(hour);
        assertThat(zonedDateTime.getMinute()).isEqualTo(min);
        assertThat(zonedDateTime.getSecond()).isEqualTo(sec);
        assertThat(zonedDateTime.getNano()).isEqualTo(nano);
        assertThat(zonedDateTime.getZone()).isEqualTo(ZoneId.of(zone));
    }

    private String loadJsonForTest(String fileName) {
        return TestUtil.fetchResource("zoneddatetime/" + fileName);
    }

    @Override
    protected ZonedDateTime provideT1() {
        var localDateTime = LocalDateTime.of(2019, 12, 15, 9, 7, 16, 2000);
        return ZonedDateTime.of(localDateTime, Z1);
    }

    @Override
    protected ZonedDateTime provideT2() {
        var localDateTime = LocalDateTime.of(2027, 12, 23, 9, 7, 16, 2000);
        return ZonedDateTime.of(localDateTime, Z2);
    }

    @Override
    protected ZonedDateTime provideT3() {
        var localDateTime = LocalDateTime.of(2027, 12, 23, 6, 7, 16, 2000);
        return ZonedDateTime.of(localDateTime, Z3);
    }

    @Override
    protected ZonedDateTime provideT4() {
        var localDateTime = LocalDateTime.of(2027, 12, 23, 6, 7, 16, 2000);
        return ZonedDateTime.of(localDateTime, Z1);
    }

    @Override
    protected NestedZonedDateTime provideNestedInObject() {
        var localDateTime1 = LocalDateTime.of(2027, 12, 23, 6, 7, 16, 2000);
        var localDateTime2 = LocalDateTime.of(2027, 12, 23, 6, 7, 16, 2000);
        return new NestedZonedDateTime(
                ZonedDateTime.of(localDateTime1, Z1),
                ZonedDateTime.of(localDateTime2, Z2));
    }

    @Override
    protected void assertNestedInObject(Object expected, Object actual) {
        var nestedExpected = (NestedZonedDateTime) expected;
        var nestedActual = (NestedZonedDateTime) actual;

        assertThat(nestedActual.date1).isEqualTo(nestedExpected.date1);
        assertThat(nestedActual.date2).isEqualTo(nestedExpected.date2);
    }

    @Override
    protected Object provideDuplicatesNestedInObject() {
        var localDateTime1 = LocalDateTime.of(2027, 12, 23, 6, 7, 16, 2000);
        return new NestedZonedDateTime(
                ZonedDateTime.of(localDateTime1, Z1));
    }

    @Override
    protected void assertDuplicatesNestedInObject(Object expected, Object actual) {
        var nestedExpected = (NestedZonedDateTime) expected;
        var nestedActual = (NestedZonedDateTime) actual;

        assertThat(nestedActual.date1).isEqualTo(nestedExpected.date1);
        assertThat(nestedActual.date2).isEqualTo(nestedExpected.date2);
        assertThat(nestedActual.date2).isSameAs(nestedActual.date1);
    }
}
