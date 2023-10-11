package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDateTests {

    private static class NestedLocalDate
    {
        private LocalDate date;

        public NestedLocalDate(LocalDate date) {
            this.date = date;
        }
    }

    private static Stream<Arguments> checkDifferentFormatsByFile() {
        return Stream.of(
                Arguments.of("old-format-top-level.json", 2023, 4, 5)
        );
    }

    @ParameterizedTest
    @MethodSource("checkDifferentFormatsByFile")
    void testOldFormat_topLevel_withType(String fileName, int year, int month, int day) {
        String json = loadJsonForTest(fileName);
        LocalDate localDate = (LocalDate)TestUtil.readJsonObject(json);

        assertLocalDate(localDate, year, month, day);
    }

    @Test
    void testOldFormat_nestedLevel() {

        String json = loadJsonForTest("old-format-nested-level.json");
        NestedLocalDate nested = TestUtil.readJsonObject(json);

        assertLocalDate(nested.date, 2014, 6, 13);
    }

    @Test
    void testLocalDate_nested() {
        var date = new NestedLocalDate(LocalDate.of(2022, 10, 17));
        String json = TestUtil.getJsonString(date);
        var result = (NestedLocalDate) TestUtil.readJsonObject(json);
        assertThat(result.date).isEqualTo(date.date);
    }

    @Test
    void testTopLevel_serializesAsISODate() {
        var date = LocalDate.of(2014, 10, 17);
        String json = TestUtil.getJsonString(date);
        var result = (LocalDate) TestUtil.readJsonObject(json);
        assertThat(result).isEqualTo(date);

    }

    private void assertLocalDate(LocalDate date, int year, int month, int dayOfMonth) {
        assertThat(date.getYear()).isEqualTo(year);
        assertThat(date.getMonthValue()).isEqualTo(month);
        assertThat(date.getDayOfMonth()).isEqualTo(dayOfMonth);
    }

    private String loadJsonForTest(String fileName) {
        return TestUtil.fetchResource("localdate/" + fileName);
    }
}
