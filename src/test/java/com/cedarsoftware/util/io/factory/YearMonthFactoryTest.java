package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.YearMonth;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class YearMonthFactoryTest {
    private static Stream<Arguments> nonValueVariants() {
        return Stream.of(
                Arguments.of(2023, 12),
                Arguments.of(1998, 12)
        );
    }

    @ParameterizedTest
    @MethodSource("nonValueVariants")
    void newInstance_testNonValueVariants(Integer year, Integer month) {
        YearMonthFactory factory = new YearMonthFactory();
        JsonObject jsonObject = buildJsonObject(year, month);

        YearMonth time = factory.newInstance(YearMonth.class, jsonObject, null);

        assertThat(time.getYear()).isEqualTo(year);
        assertThat(time.getMonthValue()).isEqualTo(month.intValue());
    }

    @Test
    void newInstance_formattedDateTest() {
        YearMonthFactory factory = new YearMonthFactory();
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("value", "2023-09");

        YearMonth time = factory.newInstance(YearMonth.class, jsonObject, null);

        assertThat(time.getYear()).isEqualTo(2023);
        assertThat(time.getMonthValue()).isEqualTo(9L);
    }

    private JsonObject buildJsonObject(Integer year, Integer month) {
        JsonObject object = new JsonObject();
        object.put("year", year);
        object.put("month", month);
        return object;
    }

}
