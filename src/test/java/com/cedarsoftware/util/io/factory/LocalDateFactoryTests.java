package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;
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

    private JsonObject buildJsonObject(Integer year, Integer month, Integer day) {
        JsonObject object = new JsonObject();
        object.put("year", year);
        object.put("month", month);
        object.put("day", day);
        return object;
    }
}
