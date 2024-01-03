package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.MetaUtils;
import com.cedarsoftware.util.io.ReadOptionsBuilder;
import com.cedarsoftware.util.io.TestUtil;
import com.cedarsoftware.util.io.models.NestedZonedDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;

class ZonedDateTimeFactoryTests {
    private static Stream<Arguments> oldVariants() {
        return Stream.of(
                Arguments.of("2011-10-24T22:16:08.413722", "America/Los_Angeles", -25200, 2011, 10, 24, 22, 16, 8, 413722000),
                Arguments.of("2023-10-25T07:16:08.4157276", "Europe/Paris", +7200, 2023, 10, 25, 7, 16, 8, 415727600),
                Arguments.of("2023-10-25T01:16:08.4467241", "America/New_York", -14400, 2023, 10, 25, 1, 16, 8, 446724100)
        );
    }

    @ParameterizedTest
    @MethodSource("oldVariants")
    void newInstance_testObjectVariant(String dateTime, String zone, Number totalSeconds, Integer year, Integer month, Integer dayOfMonth, Integer hour, Integer minute, Integer second, Integer nano) {

        JsonObject jsonObject = buildJsonObject(dateTime, zone, totalSeconds);

        JsonReader reader = new JsonReader(new ReadOptionsBuilder().build());


        ZonedDateTimeFactory factory = new ZonedDateTimeFactory();
        ZonedDateTime zonedDateTime = factory.newInstance(ZonedDateTime.class, jsonObject, reader);

        assertZonedDateTime(zonedDateTime, year, month, dayOfMonth, hour, minute, second, nano, zone, totalSeconds);
    }

    private static Stream<Arguments> primitiveVariants() {
        return Stream.of(
                Arguments.of("2011-12-03T10:15:30+01:00[Europe/Paris]", "Europe/Paris", 2011, 12, 3, 10, 15, 30, 0, 3600),
                Arguments.of("2023-10-24T22:43:10.1083446-04:00[America/New_York]", "America/New_York", 2023, 10, 24, 22, 43, 10, 108344600, -14400)
        );
    }

    @ParameterizedTest
    @MethodSource("primitiveVariants")
    void newInstance_primitiveVariants(String dateTime, String zone, int year, int month, int day, int hour, int min, int sec, int nano, int offset) {
        JsonObject jsonObject = buildJsonObject(dateTime);

        ZonedDateTimeFactory factory = new ZonedDateTimeFactory();
        ZonedDateTime zonedDateTime = factory.newInstance(ZonedDateTime.class, jsonObject, null);

        assertZonedDateTime(zonedDateTime, year, month, day, hour, min, sec, nano, zone, offset);
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
        return MetaUtils.loadResourceAsString("zoneddatetime/" + fileName);
    }

    private JsonObject buildJsonObject(String localDateTime, String zoneId, Number totalSeconds) {
        JsonObject jsonObject = new JsonObject();
        JsonObject zone = new JsonObject();
        zone.put("id", zoneId);

        JsonObject offset = new JsonObject();
        offset.put("totalSeconds", totalSeconds == null ? 0 : totalSeconds.intValue());

        jsonObject.put("dateTime", localDateTime);
        jsonObject.put("zone", zone);
        jsonObject.put("offset", offset);
        return jsonObject;
    }

    private JsonObject buildJsonObject(String zonedDateTime) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.setValue(zonedDateTime);
        return jsonObject;
    }
}
