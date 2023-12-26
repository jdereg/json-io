package com.cedarsoftware.util.io;

import java.time.Year;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static com.cedarsoftware.util.io.TestUtil.toObjects;
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
    protected boolean isReferenceable() {
        return false;
    }

    @Override
    protected Object provideNestedInObject_withNoDuplicates() {
        return new NestedYear(
                provideT1(),
                provideT2());
    }

    @Override
    protected Year[] extractNestedInObject(Object o) {
        NestedYear nested = (NestedYear) o;

        return new Year[]{
                nested.dateTime1,
                nested.dateTime2
        };
    }

    @Override
    protected Object provideNestedInObject_withDuplicates() {
        return new NestedYear(provideT1());
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsJsonTypes(Year expected, Object actual) {
        assertThat(actual).isEqualTo(1950L);
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
        Year date = toObjects(json, null);
        assertThat(date.getValue()).isEqualTo(1970);
    }

    @Test
    void testOldFormat_nestedObject() {
        String json = "{\"@type\":\"com.cedarsoftware.util.io.YearTests$NestedYear\",\"dateTime1\":{\"@id\":1,\"year\":1970},\"dateTime2\":{\"@ref\":1}}";
        NestedYear date = toObjects(json, null);
        assertThat(date.dateTime1.getValue()).isEqualTo(1970);
        assertThat(date.dateTime1).isSameAs(date.dateTime2);
    }

    @Test
    void testTopLevel_serializesAsISODate() {
        Year date = Year.of(2014);
        String json = TestUtil.toJson(date);
        Year result = toObjects(json, null);
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
