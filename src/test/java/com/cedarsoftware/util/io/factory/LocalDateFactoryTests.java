package com.cedarsoftware.util.io.factory;

import java.time.LocalDate;
import java.util.stream.Stream;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.MetaUtils;
import com.cedarsoftware.util.io.ReadOptions;
import com.cedarsoftware.util.io.TestUtil;
import com.cedarsoftware.util.io.models.NestedLocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
        LocalDateFactory factory = new LocalDateFactory();
        JsonObject jsonObject = buildJsonObject(year, month, day);

        JsonReader reader = new JsonReader(new ReadOptions());
        LocalDate time = factory.newInstance(LocalDate.class, jsonObject, reader);

        assertThat(time).hasYear(year)
                .hasMonthValue(month)
                .hasDayOfMonth(day);
    }

    @Test
    void newInstance_formattedDateTest() {
        LocalDateFactory factory = new LocalDateFactory();
        JsonObject jsonObject = new JsonObject();
        jsonObject.setValue("2023-09-05");

        JsonReader reader = new JsonReader(new ReadOptions());
        LocalDate time = factory.newInstance(LocalDate.class, jsonObject, reader);

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
        LocalDate localDate = TestUtil.toObjects(json, null);

        assertThat(localDate)
                .hasYear(year)
                .hasMonthValue(month)
                .hasDayOfMonth(day);
    }

    @Test
    void testOldFormat_nestedLevel() {

        String json = loadJsonForTest("old-format-nested-level.json");
        NestedLocalDate nested = TestUtil.toObjects(json, null);

        assertThat(nested.getDate1())
                .hasYear(2014)
                .hasMonthValue(6)
                .hasDayOfMonth(13);

        assertThat(nested.getDate2())
                .hasYear(2024)
                .hasMonthValue(9)
                .hasDayOfMonth(12);
    }

    private String loadJsonForTest(String fileName) {
        return MetaUtils.loadResourceAsString("localdate/" + fileName);
    }


    private JsonObject buildJsonObject(Integer year, Integer month, Integer day) {
        JsonObject object = new JsonObject();
        object.put("year", year);
        object.put("month", month);
        object.put("day", day);
        return object;
    }
}
