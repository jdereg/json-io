package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDateTimeTests extends SerializationDeserializationMinimumTests<LocalDateTime> {

    @Override
    protected LocalDateTime provideT1() {
        return LocalDateTime.of(1970, 6, 24, 0, 19, 15, 999);
    }

    @Override
    protected LocalDateTime provideT2() {
        return LocalDateTime.of(1971, 7, 14, 23, 59, 59, 999999999);
    }

    @Override
    protected LocalDateTime provideT3() {
        return LocalDateTime.of(1973, 12, 23, 5, 9, 5, 0);
    }

    @Override
    protected LocalDateTime provideT4() {
        return LocalDateTime.of(1950, 1, 27, 11, 11, 11, 0);
    }

    @Override
    protected Object provideNestedInObject() {
        return new NestedLocalDateTime(
                provideT1(),
                provideT2());
    }

    @Override
    protected void assertNestedInObject(Object expected, Object actual) {
        NestedLocalDateTime expectedDate = (NestedLocalDateTime) expected;
        NestedLocalDateTime actualDate = (NestedLocalDateTime) actual;

        assertThat(actualDate.dateTime1)
                .isEqualTo(expectedDate.dateTime1)
                .isNotSameAs(actualDate.dateTime2);

        assertThat(actualDate.dateTime1).isEqualTo(expectedDate.dateTime1);
        assertThat(actualDate.holiday).isEqualTo(expectedDate.holiday);
        assertThat(actualDate.value).isEqualTo(expectedDate.value);
    }

    @Override
    protected Object provideDuplicatesNestedInObject() {
        return new NestedLocalDateTime(provideT1());
    }

    @Override
    protected void assertDuplicatesNestedInObject(Object expected, Object actual) {
        NestedLocalDateTime expectedDate = (NestedLocalDateTime) expected;
        NestedLocalDateTime actualDate = (NestedLocalDateTime) actual;

        assertThat(actualDate.dateTime1)
                .isEqualTo(expectedDate.dateTime1)
                .isSameAs(actualDate.dateTime2);

        assertThat(actualDate.dateTime1).isEqualTo(expectedDate.dateTime1);
        assertThat(actualDate.holiday).isEqualTo(expectedDate.holiday);
        assertThat(actualDate.value).isEqualTo(expectedDate.value);
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsMaps(LocalDateTime expected, Object actual) {
        assertThat(actual).isEqualTo("1970-06-24T00:19:15.000000999");
    }

    private static Stream<Arguments> checkDifferentFormatsByFile() {
        return Stream.of(
                Arguments.of("old-format-top-level.json", 2023, 4, 5),
                Arguments.of("old-format-long.json", 2023, 4, 5)
        );
    }

    /*
    @ParameterizedTest
    @MethodSource("checkDifferentFormatsByFile")
    void testOldFormat_topLevel_withType(String fileName, int year, int month, int day) {
        String json = loadJsonForTest(fileName);
        LocalDateTime localDate = TestUtil.readJsonObject(json);

        assertLocalDateTime(localDate, year, month, day);
    }

    @Test
    void testOldFormat_nestedLevel() {

        String json = loadJsonForTest("old-format-nested-level.json");
        NestedLocalDateTime nested = TestUtil.readJsonObject(json);

        assertLocalDateTime(nested.dateTime1, 2014, 6, 13);
    }
    */


    @Test
    void testTopLevel_serializesAsISODate() {
        LocalDateTime date = LocalDateTime.of(2014, 10, 17, 9, 15, 16, 99999);
        String json = TestUtil.toJson(date);
        LocalDateTime result = TestUtil.toJava(json);
        assertThat(result).isEqualTo(date);
    }

    @Test
    void testLocalDateTime_inArray() {
        LocalDateTime[] initial = new LocalDateTime[]{
                LocalDateTime.of(2014, 10, 9, 11, 11, 11, 11),
                LocalDateTime.of(2023, 6, 24, 11, 11, 11, 1000)
        };

        LocalDateTime[] actual = TestUtil.serializeDeserialize(initial);

        assertThat(actual).isEqualTo(initial);
    }

    private void assertLocalDateTime(LocalDateTime date, int year, int month, int dayOfMonth) {
        assertThat(date.getYear()).isEqualTo(year);
        assertThat(date.getMonthValue()).isEqualTo(month);
        assertThat(date.getDayOfMonth()).isEqualTo(dayOfMonth);
    }

    private String loadJsonForTest(String fileName) {
        return TestUtil.fetchResource("localdatetime/" + fileName);
    }

    private static class NestedLocalDateTime {
        public LocalDateTime dateTime1;
        public LocalDateTime dateTime2;
        public String holiday;
        public Long value;

        public NestedLocalDateTime(LocalDateTime dateTime1, LocalDateTime dateTime2) {
            this.holiday = "Festivus";
            this.value = 999L;
            this.dateTime1 = dateTime1;
            this.dateTime2 = dateTime2;
        }

        public NestedLocalDateTime(LocalDateTime date) {
            this(date, date);
        }
    }
}
