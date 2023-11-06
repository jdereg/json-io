package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Year;
import java.util.stream.Stream;

import static com.cedarsoftware.util.io.TestUtil.toJava;
import static com.cedarsoftware.util.io.TestUtil.toJson;
import static org.assertj.core.api.Assertions.assertThat;

class YearTests extends SerializationDeserializationMinimumTests<Year> {

    @Override
    protected Year provideT1() {
        return Year.of(1970);
    }

    @Override
    protected Year provideT2() {
        return Year.of(1971);
    }

    @Override
    protected Year provideT3() {
        return Year.of(1973);
    }

    @Override
    protected Year provideT4() {
        return Year.of(1950);
    }

    @Override
    protected Object provideNestedInObject() {
        return new NestedYear(
                provideT1(),
                provideT2());
    }

    @Override
    protected void assertNestedInObject(Object expected, Object actual) {
        NestedYear expectedDate = (NestedYear) expected;
        NestedYear actualDate = (NestedYear) actual;

        assertThat(actualDate.dateTime1)
                .isEqualTo(expectedDate.dateTime1)
                .isNotSameAs(actualDate.dateTime2);

        assertThat(actualDate.dateTime1).isEqualTo(expectedDate.dateTime1);
    }

    @Override
    protected Object provideDuplicatesNestedInObject() {
        return new NestedYear(provideT1());
    }

    @Override
    protected void assertDuplicatesNestedInObject(Object expected, Object actual) {
        NestedYear expectedDate = (NestedYear) expected;
        NestedYear actualDate = (NestedYear) actual;

        String json = toJson(expected);

        assertThat(actualDate.dateTime1)
                .isEqualTo(expectedDate.dateTime1)
                .isSameAs(actualDate.dateTime2);

        assertThat(actualDate.dateTime1).isEqualTo(expectedDate.dateTime1);
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsMaps(Year expected, Object actual) {
        assertThat(actual).isEqualTo(1970L);
    }

    private static Stream<Arguments> argumentsForOldFormat() {
        return Stream.of(
                Arguments.of("{\"@type\":\"java.time.Year\",\"year\":1970}"),
                Arguments.of("{\"@type\":\"java.time.Year\",\"value\":1970}")
        );
    }

    @ParameterizedTest
    @MethodSource("argumentsForOldFormat")
    void testOldFormat_objectType(String json) {
        Year date = toJava(json);
        assertThat(date.getValue()).isEqualTo(1970);
    }

    @Test
    void testOldFormat_nestedObject() {
        String json = "{\"@type\":\"com.cedarsoftware.util.io.YearTests$NestedYear\",\"dateTime1\":{\"@id\":1,\"year\":1970},\"dateTime2\":{\"@ref\":1}}";
        NestedYear date = toJava(json);
        assertThat(date.dateTime1.getValue()).isEqualTo(1970);
        assertThat(date.dateTime1).isSameAs(date.dateTime2);
    }

    @Test
    void testTopLevel_serializesAsISODate() {
        Year date = Year.of(2014);
        String json = TestUtil.toJson(date);
        Year result = toJava(json);
        assertThat(result).isEqualTo(date);
    }

    @Test
    void testYear_inArray() {
        Year[] initial = new Year[]{
                Year.of(2014),
                Year.of(2023)
        };

        Year[] actual = TestUtil.serializeDeserialize(initial);

        assertThat(actual).isEqualTo(initial);
    }

    private void assertYear(Year date, int year) {
        assertThat(date.getValue()).isEqualTo(year);
    }

    private static class NestedYear {
        public Year dateTime1;
        public Year dateTime2;

        public NestedYear(Year dateTime1, Year dateTime2) {
            this.dateTime1 = dateTime1;
            this.dateTime2 = dateTime2;
        }

        public NestedYear(Year date) {
            this(date, date);
        }
    }
}
