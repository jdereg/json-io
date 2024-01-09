package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.Converter;
import com.cedarsoftware.util.DateUtilities;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LocalTimeFactoryTests extends HandWrittenDateFactoryTests<LocalTime> {
    private static Stream<Arguments> nonValueVariants() {
        return Stream.of(
                Arguments.of(11, 23, 58, 900000),
                Arguments.of(11, 23, 58, null),
                Arguments.of(11, 23, null, null)
        );
    }

    @ParameterizedTest
    @MethodSource("nonValueVariants")
    void newInstance_testNonValueVariants(Integer hour, Integer minute, Integer second, Integer nano) {
        JsonObject jsonObject = buildJsonObject(hour, minute, second, nano);

        LocalTime time = Converter.convert(jsonObject, LocalTime.class);

        assertThat(time).hasHour(hour)
                .hasMinute(minute)
                .hasSecond(second == null ? 0 : second)
                .hasNano(nano == null ? 0 : nano);
    }

    @Test
    void newInstance_formattedTimeTest() {
        LocalTimeFactory factory = new LocalTimeFactory();
        JsonObject jsonObject = new JsonObject();
        jsonObject.setValue("09:27:39");

        LocalTime time = Converter.convert(jsonObject, LocalTime.class);

        assertThat(time).hasHour(9)
                .hasMinute(27)
                .hasSecond(39)
                .hasNano(0);
    }

    @Test
    @Disabled
    void newInstance_formatTimeUsingIsoOffsetFormat() {
//        LocalTimeFactory factory = new LocalTimeFactory(DateTimeFormatter.ISO_OFFSET_TIME, ZoneId.of("Z"));
        JsonObject jsonObject = new JsonObject();
        jsonObject.setValue("09:27:39+01:00");

        LocalTime time = Converter.convert(jsonObject, LocalTime.class);

        assertThat(time).hasHour(9)
                .hasMinute(27)
                .hasSecond(39)
                .hasNano(0);
    }

    private JsonObject buildJsonObject(Integer hour, Integer minute, Integer second, Integer nano) {
        JsonObject object = new JsonObject();
        object.put("hour", hour);
        object.put("minute", minute);
        object.put("second", second);
        object.put("nano", nano);
        return object;
    }

    @Override
    protected JsonReader.ClassFactory createFactory() {
        return new LocalTimeFactory();
    }

    @Override
    protected JsonReader.ClassFactory createFactory(ZoneId zoneId) {
//        return new LocalTimeFactory(DateTimeFormatter.ISO_LOCAL_TIME, zoneId);
        return new ConvertableFactory() {
            @Override
            public Class<?> getType() {
                return LocalTime.class;
            }
        };

    }

    @Override
    protected Class<LocalTime> getClassForFactory() {
        return LocalTime.class;
    }

    @Override
    protected void assert_handWrittenDate_withNoZone(LocalTime dt) {
        assertThat(dt)
                .hasHour(10)
                .hasMinute(15)
                .hasSecond(30)
                .hasNano(0);
    }

    @Override
    protected void assert_handWrittenDate_withNoTime(LocalTime dt) {
        assertThat(dt)
                .hasHour(0)
                .hasMinute(0)
                .hasSecond(0)
                .hasNano(0);
    }

    @Override
    protected void assert_handWrittenDate_withTime(LocalTime dt) {
        assertThat(dt)
                .hasHour(8)
                .hasMinute(9)
                .hasSecond(3)
                .hasNano(0);
    }

    @Override
    protected void assert_handWrittenDate_withMilliseconds(LocalTime dt) {
        Date date = DateUtilities.parseDate("2011-12-03T10:15:30.050-0500");
        assertThat(date).isNotNull();

        LocalTime actual = date.toInstant().atZone(ZoneId.systemDefault()).toLocalTime();
        assertThat(actual).isEqualTo(dt);
    }

    @Override
    protected void assert_handWrittenDate_inSaigon(LocalTime dt) {
        Date date = DateUtilities.parseDate("2011-02-03 20:09:03");
        assertThat(date).isNotNull();

        LocalTime actual = date.toInstant().atZone(SAIGON_ZONE_ID).toLocalTime();
        assertThat(actual).isEqualTo(dt);
    }
}
