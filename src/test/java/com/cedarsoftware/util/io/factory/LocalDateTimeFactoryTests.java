package com.cedarsoftware.util.io.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.TestUtil;

class LocalDateTimeFactoryTests extends HandWrittenDateFactoryTests<LocalDateTime> {
    private static Stream<Arguments> nonValueVariants() {
        return Stream.of(
                Arguments.of(2023, 12, 19, 17, 12, 59, null),
                Arguments.of(1998, 12, 18, 0, 0, 0, 999999),
                Arguments.of(2000, 5, 1, 9, 7, null, null)
        );
    }

    @ParameterizedTest
    @MethodSource("nonValueVariants")
    void newInstance_testNonValueVariants(Integer year, Integer month, Integer day, Integer hour, Integer minute, Integer second, Integer nano) {
        LocalDateTimeFactory factory = new LocalDateTimeFactory();
        JsonObject jsonObject = buildJsonObject(year, month, day, hour, minute, second, nano);

        LocalDateTime time = factory.newInstance(LocalDateTime.class, jsonObject, null);

        assertThat(time).hasYear(year)
                .hasMonthValue(month)
                .hasDayOfMonth(day)
                .hasHour(hour)
                .hasMinute(minute)
                .hasSecond(second == null ? 0 : second)
                .hasNano(nano == null ? 0 : nano);
    }

    @Test
    void newInstance_formattedDateTest() {
        LocalDateTimeFactory factory = new LocalDateTimeFactory();
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("value", "2011-12-03T10:15:30");

        LocalDateTime time = factory.newInstance(LocalDateTime.class, jsonObject, null);

        assertThat(time)
                .hasYear(2011)
                .hasMonthValue(12)
                .hasDayOfMonth(3)
                .hasHour(10)
                .hasMinute(15)
                .hasSecond(30);
    }

    private JsonObject buildJsonObject(Integer year, Integer month, Integer day, Integer hour, Integer minute, Integer second, Integer nano) {

        JsonObject object = new JsonObject();
        LocalDate date = LocalDate.of(year, month, day);
        LocalTime time = LocalTime.of(hour, minute, second == null ? 0 : second, nano == null ? 0 : nano);

        object.put("date", DateTimeFormatter.ISO_LOCAL_DATE.format(date));
        object.put("time", DateTimeFormatter.ISO_LOCAL_TIME.format(time));
        return object;
    }

    @Test
    public void testToughFormatToParse()
    {
        String json = TestUtil.fetchResource("localdatetime/wideFormatSupport.json");
        LocalDateTime dt = TestUtil.toObjects(json, null);
        assert dt.getYear() == 2023;
        assert dt.getMonthValue() == 12;
        assert dt.getDayOfMonth() == 25;
        assert dt.getHour() == 15;
        assert dt.getMinute() == 0;
        assert dt.getSecond() == 0;
    }

    @Override
    protected JsonReader.ClassFactory createFactory() {
        return new LocalDateTimeFactory();
    }

    @Override
    protected Class<LocalDateTime> getClassForFactory() {
        return LocalDateTime.class;
    }

    @Override
    protected void assert_handWrittenDate_withNoZone(LocalDateTime dt) {
        assertThat(dt).hasYear(2011)
                .hasMonthValue(12)
                .hasDayOfMonth(3)
                .hasHour(10)
                .hasMinute(15)
                .hasSecond(30)
                .hasNano(0);
    }

    @Override
    protected void assert_handWrittenDate_withNoTime(LocalDateTime dt) {
        assertThat(dt).hasYear(2011)
                .hasMonthValue(2)
                .hasDayOfMonth(3)
                .hasHour(0)
                .hasMinute(0)
                .hasSecond(0)
                .hasNano(0);
    }

    @Override
    protected void assert_handWrittenDate_withTime(LocalDateTime dt) {
        assertThat(dt).hasYear(2011)
                .hasMonthValue(2)
                .hasDayOfMonth(3)
                .hasHour(8)
                .hasMinute(9)
                .hasSecond(3)
                .hasNano(0);
    }

    @Override
    protected void assert_handWrittenDate_withMilliseconds(LocalDateTime dt) {
        assertThat(dt).hasYear(2011)
                .hasMonthValue(12)
                .hasDayOfMonth(3)
                .hasHour(10)
                .hasMinute(15)
                .hasSecond(30)
                .hasNano(50000000);
    }
}
