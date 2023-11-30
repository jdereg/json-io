package com.cedarsoftware.util.io;

import java.time.LocalTime;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class LocalTimeTests extends SerializationDeserializationMinimumTests<LocalTime> {

    @Override
    protected LocalTime provideT1() {
        return LocalTime.of(14, 20);
    }

    @Override
    protected LocalTime provideT2() {
        return LocalTime.of(0, 0, 0, 0);
    }

    @Override
    protected LocalTime provideT3() {
        return LocalTime.of(1, 1, 1, 1);
    }

    @Override
    protected LocalTime provideT4() {
        return LocalTime.of(23, 59, 59, 999999999);
    }

    @Override
    protected Object provideNestedInObject() {
        return new NestedLocalTime(
                provideT1(),
                provideT2());
    }

    @Override
    protected void assertNestedInObject(Object expected, Object actual) {
        NestedLocalTime expectedDate = (NestedLocalTime) expected;
        NestedLocalTime actualDate = (NestedLocalTime) actual;

        assertThat(actualDate.time1)
                .isEqualTo(expectedDate.time1)
                .isNotSameAs(actualDate.time2);

        assertThat(actualDate.time1).isEqualTo(expectedDate.time1);
        assertThat(actualDate.holiday).isEqualTo(expectedDate.holiday);
        assertThat(actualDate.value).isEqualTo(expectedDate.value);
    }

    @Override
    protected Object provideDuplicatesNestedInObject() {
        return new NestedLocalTime(provideT1());
    }

    @Override
    protected void assertDuplicatesNestedInObject(Object expected, Object actual) {
        NestedLocalTime expectedDate = (NestedLocalTime) expected;
        NestedLocalTime actualDate = (NestedLocalTime) actual;

        assertThat(actualDate.time1)
                .isEqualTo(expectedDate.time1)
                .isSameAs(actualDate.time2);

        assertThat(actualDate.time1).isEqualTo(expectedDate.time1);
        assertThat(actualDate.holiday).isEqualTo(expectedDate.holiday);
        assertThat(actualDate.value).isEqualTo(expectedDate.value);
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsMaps(LocalTime expected, Object actual) {
        assertThat(actual).isEqualTo("23:59:59.999999999");
    }

    private static Stream<Arguments> checkDifferentFormatsByFile() {
        return Stream.of(
                Arguments.of("old-format-top-level.json", 17, 15, 16, 78998),
                Arguments.of("old-format-top-level-missing-nano.json", 17, 15, 16, 0),
                Arguments.of("old-format-top-level-missing-nano-and-second.json", 17, 15, 0, 0)
        );
    }

    @ParameterizedTest
    @MethodSource("checkDifferentFormatsByFile")
    void testOldFormat_topLevel_withType(String fileName, int hour, int minute, int second, int nano) {
        String json = loadJsonForTest(fileName);
        LocalTime localDate = TestUtil.toObjects(json, null);

        assertLocalTime(localDate, hour, minute, second, nano);
    }

    @Test
    void testOldFormat_nestedLevel() {
        String json = loadJsonForTest("old-format-nested-in-object.json");
        NestedLocalTime nested = TestUtil.toObjects(json, null);

        assertLocalTime(nested.time1, 9, 12, 15, 999999);
    }

    private void assertLocalTime(LocalTime time, int hour, int minute, int second, int nano) {
        assertThat(time.getHour()).isEqualTo(hour);
        assertThat(time.getMinute()).isEqualTo(minute);
        assertThat(time.getSecond()).isEqualTo(second);
        assertThat(time.getNano()).isEqualTo(nano);
    }

    private String loadJsonForTest(String fileName) {
        return MetaUtils.loadResourceAsString("localtime/" + fileName);
    }

    private static class NestedLocalTime {
        public LocalTime time1;
        public LocalTime time2;
        public String holiday;
        public Long value;

        public NestedLocalTime(LocalTime time1, LocalTime time2) {
            this.holiday = "Festivus";
            this.value = 999L;
            this.time1 = time1;
            this.time2 = time2;
        }

        public NestedLocalTime(LocalTime time1) {
            this(time1, time1);
        }
    }
}
