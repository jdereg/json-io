package com.cedarsoftware.io;

import java.time.Duration;
import java.util.Date;
import java.util.stream.Stream;

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
class DurationTests {

    @Test
    void testDuration_ofNanos() {
        Duration duration = Duration.ofNanos(500);
        String json = TestUtil.toJson(duration);
        Duration actual = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);
        
        assertThat(actual).hasNanos(500);
    }

    @Test
    void testDuration_ofMillis() {
        Duration duration = Duration.ofMillis(9000);
        String json = TestUtil.toJson(duration);
        Duration actual = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);

        assertThat(actual)
                .hasMillis(9000)
                .hasNanos(9000000000L);
    }

    @Test
    void testDuration_ofSecondsAndNanos() {
        Duration duration = Duration.ofSeconds(9000, 9000);
        String json = TestUtil.toJson(duration);
        Duration actual = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);
        
        assertThat(actual)
                .hasSeconds(9000)
                .hasNanos(9000000009000L);

    }

    @Test
    void testDuration_ofDays() {
        Duration duration = Duration.ofDays(9);
        String json = TestUtil.toJson(duration);
        Duration actual = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);

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
        Duration d = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);

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

    @Test
    void testDurationInArray()
    {
        String s = "PT2M1.000000999S";
        Duration duration = Duration.ofSeconds(121, 999);
        Object[] durations = new Object[] {duration, duration};
        Duration[] typedDurations = new Duration[] {duration, duration};
        String json = TestUtil.toJson(durations);
        TestUtil.printLine("json=" + json);

        durations = TestUtil.toObjects(json, null);
        assertEquals(2, durations.length);
        assertNotSame(durations[0], durations[1]);
        assertEquals(Duration.parse(s), durations[0]);
        json = TestUtil.toJson(typedDurations);
        TestUtil.printLine("json=" + json);
        assertEquals(2, typedDurations.length);
        assertSame(typedDurations[0], typedDurations[1]);
        assertEquals(Duration.parse(s), typedDurations[0]);
    }

    static class DurationArray
    {
        Duration[] durations;
        Object[] otherDurations;
    }

    @Test
    void testDurationArray()
    {
        DurationTests.DurationArray da = new DurationTests.DurationArray();
        Duration durr = Duration.ofSeconds(121, 999);
        da.durations = new Duration[] {durr, durr};
        da.otherDurations = new Object[] {durr, new Date(System.currentTimeMillis()), durr};
        String json = TestUtil.toJson(da);
        DurationTests.DurationArray da2 = TestUtil.toObjects(json, null);
        assert da.durations.length == 2;
        assert da.otherDurations.length == 3;

        assert DeepEquals.deepEquals(da, da2);
    }

    private String loadJsonForTest(String fileName) {
        return ClassUtilities.loadResourceAsString("duration/" + fileName);
    }
}