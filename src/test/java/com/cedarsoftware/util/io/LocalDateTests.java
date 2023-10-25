package com.cedarsoftware.util.io;

import com.cedarsoftware.util.io.models.NestedLocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDateTests extends SerializationDeserializationMinimumTests<LocalDate> {

    @Override
    protected LocalDate provideT1() {
        return LocalDate.of(1970, 6, 24);
    }

    @Override
    protected LocalDate provideT2() {
        return LocalDate.of(1971, 7, 14);
    }

    @Override
    protected LocalDate provideT3() {
        return LocalDate.of(1973, 12, 23);
    }

    @Override
    protected LocalDate provideT4() {
        return LocalDate.of(1950, 1, 27);
    }

    @Override
    protected Object provideNestedInObject() {
        return new NestedLocalDate(
                provideT1(),
                provideT2());
    }

    @Override
    protected void assertNestedInObject(Object expected, Object actual) {
        NestedLocalDate expectedDate = (NestedLocalDate) expected;
        NestedLocalDate actualDate = (NestedLocalDate) actual;

        assertThat(actualDate.date1)
                .isEqualTo(expectedDate.date1)
                .isNotSameAs(actualDate.date2);

        assertThat(actualDate.date1).isEqualTo(expectedDate.date1);
        assertThat(actualDate.holiday).isEqualTo(expectedDate.holiday);
        assertThat(actualDate.value).isEqualTo(expectedDate.value);
    }

    @Override
    protected Object provideDuplicatesNestedInObject() {
        return new NestedLocalDate(provideT1());
    }

    @Override
    protected void assertDuplicatesNestedInObject(Object expected, Object actual) {
        NestedLocalDate expectedDate = (NestedLocalDate) expected;
        NestedLocalDate actualDate = (NestedLocalDate) actual;

        assertThat(actualDate.date1)
                .isEqualTo(expectedDate.date1)
                .isSameAs(actualDate.date2);

        assertThat(actualDate.date1).isEqualTo(expectedDate.date1);
        assertThat(actualDate.holiday).isEqualTo(expectedDate.holiday);
        assertThat(actualDate.value).isEqualTo(expectedDate.value);
    }

    @Test
    void testTopLevel_serializesAsISODate() {
        var date = LocalDate.of(2014, 10, 17);
        String json = TestUtil.toJson(date);
        var result = (LocalDate) TestUtil.toJava(json);
        assertThat(result).isEqualTo(date);
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
        LocalDate localDate = TestUtil.toJava(json);

        assertThat(localDate)
                .hasYear(year)
                .hasMonthValue(month)
                .hasDayOfMonth(day);
    }

    @Test
    void testOldFormat_nestedLevel() {

        String json = loadJsonForTest("old-format-nested-level.json");
        NestedLocalDate nested = TestUtil.toJava(json);

        assertThat(nested.date1)
                .hasYear(2014)
                .hasMonthValue(6)
                .hasDayOfMonth(13);

        assertThat(nested.date2)
                .hasYear(2024)
                .hasMonthValue(9)
                .hasDayOfMonth(12);
    }

    private String loadJsonForTest(String fileName) {
        return TestUtil.fetchResource("localdate/" + fileName);
    }
}
