package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

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
        String json = TestUtil.getJsonString(date);
        var result = (LocalDate) TestUtil.readJsonObject(json);
        assertThat(result).isEqualTo(date);
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
}
