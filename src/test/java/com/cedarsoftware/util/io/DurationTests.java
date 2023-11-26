package com.cedarsoftware.util.io;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class DurationTests {

    @Test
    void testDuration_ofNanos() {
        Duration duration = Duration.ofNanos(500);
        String json = TestUtil.toJson(duration);
        Duration actual = TestUtil.toObjects(json, new ReadOptions(), null);
        
        assertThat(actual).hasNanos(500);
    }

    @Test
    void testDuration_ofMillis() {
        Duration duration = Duration.ofMillis(9000);
        String json = TestUtil.toJson(duration);
        Duration actual = TestUtil.toObjects(json, new ReadOptions(), null);

        assertThat(actual)
                .hasMillis(9000)
                .hasNanos(9000000000L);
    }

    @Test
    void testDuration_ofSecondsAndNanos() {
        Duration duration = Duration.ofSeconds(9000, 9000);
        String json = TestUtil.toJson(duration);
        Duration actual = TestUtil.toObjects(json, new ReadOptions(), null);
        
        assertThat(actual)
                .hasSeconds(9000)
                .hasNanos(9000000009000L);

    }

    @Test
    void testDuration_ofDays() {
        Duration duration = Duration.ofDays(9);
        String json = TestUtil.toJson(duration);
        Duration actual = TestUtil.toObjects(json, new ReadOptions(), null);

        assertThat(actual).hasDays(9);
    }

    @Test
    void testDuration_ofHours() {
        Duration initial = Duration.ofHours(7);
        Duration actual = TestUtil.serializeDeserialize(initial);

        assertThat(actual)
                .hasDays(0)
                .hasHours(7)
                .hasMinutes(420)
                .hasSeconds(25200);
    }

    @Test
    void testDuration_ofMinutes() {
        Duration initial = Duration.ofMinutes(7);
        Duration actual = TestUtil.serializeDeserialize(initial);

        assertThat(actual)
                .hasDays(0)
                .hasHours(0)
                .hasMinutes(7)
                .hasSeconds(420)
                .hasMillis(420000)
                .hasNanos(420000000000L);
    }

    @Test
    void testDuration_ofSeconds() {
        Duration initial = Duration.ofSeconds(7);
        Duration actual = TestUtil.serializeDeserialize(initial);

        assertThat(actual)
                .hasDays(0);
    }

    private static Stream<Arguments> oldFormats() {
        return Stream.of(
                Arguments.of("old-format-1.json", 0, 0, 7, 420, 420000L, 420000000000L),
                Arguments.of("old-format-2.json", 0, 7, 420, 25200L, 25200000L, 25200000002000L),
                Arguments.of("old-format-3.json", 9, 216, 12960, 777600, 777600000, 777600000000000L),
                Arguments.of("old-format-4.json", 0, 2, 150, 9000, 9000000, 9000000009000L)
        );
    }

    @ParameterizedTest
    @MethodSource("oldFormats")
    void oldFormatTests(String fileName, long days, long hours, long minutes, long seconds, long millis, long nanos) {
        String json = loadJsonForTest(fileName);
        Duration d = TestUtil.toObjects(json, new ReadOptions(), null);

        assertThat(d)
                .hasDays(days)
                .hasHours(hours)
                .hasMinutes(minutes)
                .hasSeconds(seconds)
                .hasMillis(millis)
                .hasNanos(nanos);
    }


    private static Stream<Arguments> patterns() {
        return Stream.of(
                Arguments.of(0, 0, 7, 420, 420000L, 420000000000L),
                Arguments.of(0, 0, 0, 15, 15000L, 15000000000L),
                Arguments.of(9, 216, 12960, 777600, 777600000, 777600000000000L),
                Arguments.of(0, 2, 150, 9000, 9000000, 9000000000000L),
                Arguments.of(0, 0, 7, 420, 420000L, 420000000000L),
                Arguments.of(0, 7, 420, 25200L, 25200000L, 25200000000000L),
                Arguments.of(9, 216, 12960, 777600, 777600000, 777600000000000L),
                Arguments.of(0, 2, 150, 9000, 9000000, 9000000000000L)
        );
    }

    @ParameterizedTest
    @MethodSource("patterns")
    void patternTests(long days, long hours, long minutes, long seconds, long millis, long nanos) {
        Duration initial = Duration.ofSeconds(seconds);
        Duration actual = TestUtil.serializeDeserialize(initial);

        assertThat(actual)
                .hasDays(days)
                .hasHours(hours)
                .hasMinutes(minutes)
                .hasSeconds(seconds)
                .hasMillis(millis)
                .hasNanos(nanos);
    }


    private String loadJsonForTest(String fileName) {
        return TestUtil.fetchResource("duration/" + fileName);
    }
}
