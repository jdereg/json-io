package com.cedarsoftware.util.io.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;

class YearMonthFactoryTest extends HandWrittenDateFactoryTests<YearMonth> {
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

    @Override
    protected JsonReader.ClassFactory createFactory() {
        return new YearMonthFactory();
    }

    @Override
    protected Class<YearMonth> getClassForFactory() {
        return YearMonth.class;
    }

    @Override
    protected void assert_handWrittenDate_withNoZone(YearMonth dt) {
        assertThat(dt.getYear()).isEqualTo(2011);
        assertThat(dt.getMonthValue()).isEqualTo(12);
    }

    @Override
    protected void assert_handWrittenDate_withNoTime(YearMonth dt) {
        assertThat(dt.getYear()).isEqualTo(2011);
        assertThat(dt.getMonthValue()).isEqualTo(2);
    }

    @Override
    protected void assert_handWrittenDate_withTime(YearMonth dt) {
        assertThat(dt.getYear()).isEqualTo(2011);
        assertThat(dt.getMonthValue()).isEqualTo(2);
    }

    @Override
    protected void assert_handWrittenDate_withMilliseconds(YearMonth dt) {

    }
}
