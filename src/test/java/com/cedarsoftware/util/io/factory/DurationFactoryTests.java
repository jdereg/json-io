package com.cedarsoftware.util.io.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.cedarsoftware.util.io.JsonObject;

class DurationFactoryTests {

    private static Stream<Arguments> patterns() {
        return Stream.of(
                Arguments.of("PT20.345S", 0, 0, 0, 20, 20345L, 20345000000L),
                Arguments.of("PT15M", 0, 0, 15, 900, 900000L, 900000000000L),
                Arguments.of("PT10H", 0, 10, 600, 36000, 36000000L, 36000000000000L),
                Arguments.of("P2D", 2, 48, 2880, 172800, 172800000L, 172800000000000L),
                Arguments.of("P2DT3H4M", 2, 51, 3064L, 183840, 183840000L, 183840000000000L),
                Arguments.of("PT-6H3M", 0, -5, -357, -21420, -21420000, -21420000000000L),
                Arguments.of("-PT6H3M", 0, -6, -363, -21780, -21780000, -21780000000000L),
                Arguments.of("-PT-6H+3M", 0, 5, 357, 21420, 21420000, 21420000000000L)
        );
    }

    @ParameterizedTest
    @MethodSource("patterns")
    void patternTests(String pattern, long days, long hours, long minutes, long seconds, long millis, long nanos) {
        JsonObject jObject = new JsonObject();
        jObject.put("value", pattern);

        DurationFactory factory = new DurationFactory();
        Duration d = (Duration) factory.newInstance(Duration.class, jObject, null);

        assertThat(d)
                .hasDays(days)
                .hasHours(hours)
                .hasMinutes(minutes)
                .hasSeconds(seconds)
                .hasMillis(millis)
                .hasNanos(nanos);
    }
}
