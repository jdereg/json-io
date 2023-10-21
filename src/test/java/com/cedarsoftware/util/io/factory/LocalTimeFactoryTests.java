package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LocalTimeFactoryTests {
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
        var factory = new LocalTimeFactory();
        var jsonObject = buildJsonObject(hour, minute, second, nano);

        LocalTime time = (LocalTime) factory.newInstance(LocalTime.class, jsonObject);

        assertThat(time).hasHour(hour)
                .hasMinute(minute)
                .hasSecond(second == null ? 0 : second)
                .hasNano(nano == null ? 0 : nano);
    }

    @Test
    void newInstance_formattedTimeTEst() {
        var jsonReader = new JsonReader();
        var factory = new LocalTimeFactory();
        var jsonObject = new JsonObject();
        jsonObject.put("value", "09:27:39");

        LocalTime time = (LocalTime) factory.newInstance(LocalTime.class, jsonObject);

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
}
