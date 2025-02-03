package com.cedarsoftware.io;

import java.time.Period;
import java.util.stream.Stream;

import com.cedarsoftware.util.ClassUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodTests {

    @Test
    void testPeriod() {
        Period period = Period.of(5, 4, 6);
        Period actual = TestUtil.serializeDeserialize(period);

        assertThat(actual)
                .hasYears(5)
                .hasMonths(4)
                .hasDays(6);
    }

    @Test
    void testPeriod_dayOnly() {
        Period initial = Period.ofDays(7);
        Period actual = TestUtil.serializeDeserialize(initial);

        assertThat(actual)
                .hasYears(0)
                .hasMonths(0)
                .hasDays(7);
    }

    @Test
    void testPeriod_monthOnly() {
        Period initial = Period.ofMonths(7);
        Period actual = TestUtil.serializeDeserialize(initial);

        assertThat(actual)
                .hasYears(0)
                .hasMonths(7)
                .hasDays(0);
    }

    @Test
    void testPeriod_yearOnly() {
        Period initial = Period.ofYears(7);
        Period actual = TestUtil.serializeDeserialize(initial);

        assertThat(actual)
                .hasYears(7)
                .hasMonths(0)
                .hasDays(0);
    }

    private static Stream<Arguments> oldFormats() {
        return Stream.of(
                Arguments.of("old-format-all-fields.json", 5, 4, 6),
                Arguments.of("old-format-all-nulls.json", 0, 0, 0),
                Arguments.of("old-format-days.json", 0, 0, 7),
                Arguments.of("old-format-month.json", 0, 7, 0),
                Arguments.of("old-format-years.json", 7, 0, 0)
        );
    }

    @ParameterizedTest
    @MethodSource("oldFormats")
    void oldFormatTests(String fileName, int years, int months, int days) {
        String json = loadJsonForTest(fileName);
        Period d = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);

        assertThat(d)
                .hasYears(years)
                .hasMonths(months)
                .hasDays(days);
    }

    private String loadJsonForTest(String fileName) {
        return ClassUtilities.loadResourceAsString("period/" + fileName);
    }

}
