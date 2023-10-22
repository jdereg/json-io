package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.TestUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDateFactoryTests {
    private static Stream<Arguments> nonValueVariants() {
        return Stream.of(
                Arguments.of(2023, 12, 19),
                Arguments.of(1998, 12, 18)
        );
    }

    @ParameterizedTest
    @MethodSource("nonValueVariants")
    void newInstance_testNonValueVariants(Integer year, Integer month, Integer day) {
        var factory = new LocalDateFactory();
        var jsonObject = buildJsonObject(year, month, day);

        LocalDate time = (LocalDate) factory.newInstance(LocalDate.class, jsonObject, null);

        assertThat(time).hasYear(year)
                .hasMonthValue(month)
                .hasDayOfMonth(day);
    }

    @Test
    void newInstance_formattedDateTest() {
        var factory = new LocalDateFactory();
        var jsonObject = new JsonObject();
        jsonObject.put("value", "2023-09-05");

        LocalDate time = (LocalDate) factory.newInstance(LocalDate.class, jsonObject, null);

        assertThat(time)
                .hasYear(2023)
                .hasMonthValue(9)
                .hasDayOfMonth(5);
    }

    private static Stream<Arguments> checkDifferentFormatsByFile() {
        return Stream.of(
                Arguments.of("old-format-top-level.json", 2023, 4, 5),
                Arguments.of("old-format-long.json", 2023, 4, 5)
        );
    }

    @ParameterizedTest
    @MethodSource("checkDifferentFormatsByFile")
    void testOldFormat_topLevel_withType(String fileName, int year, int month, int day) {
        String json = loadJsonForTest(fileName);
        LocalDate localDate = TestUtil.readJsonObject(json);

        assertThat(localDate)
                .hasYear(year)
                .hasMonthValue(month)
                .hasDayOfMonth(day);
    }

    @Test
    void testOldFormat_nestedLevel() {

        String json = loadJsonForTest("old-format-nested-level.json");
        LocalDateFactoryTests.NestedLocalDate nested = TestUtil.readJsonObject(json);

        assertThat(nested.date1)
                .hasYear(2014)
                .hasMonthValue(6)
                .hasDayOfMonth(13);

        assertThat(nested.date2)
                .hasYear(2024)
                .hasMonthValue(9)
                .hasDayOfMonth(12);
    }

    public static class NestedLocalDate {
        public LocalDate date1;
        public LocalDate date2;
        public String holiday;
        public Long value;

        public NestedLocalDate(LocalDate date1, LocalDate date2) {
            this.holiday = "Festivus";
            this.value = 999L;
            this.date1 = date1;
            this.date2 = date2;
        }

        public NestedLocalDate(LocalDate date) {
            this(date, date);
        }
    }


    private String loadJsonForTest(String fileName) {
        return TestUtil.fetchResource("localdate/" + fileName);
    }


    private JsonObject buildJsonObject(Integer year, Integer month, Integer day) {
        JsonObject object = new JsonObject();
        object.put("year", year);
        object.put("month", month);
        object.put("day", day);
        return object;
    }
}
