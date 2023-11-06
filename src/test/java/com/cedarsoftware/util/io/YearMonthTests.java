package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static com.cedarsoftware.util.io.TestUtil.toJava;
import static com.cedarsoftware.util.io.TestUtil.toJson;
import static org.assertj.core.api.Assertions.assertThat;

class YearMonthTests extends SerializationDeserializationMinimumTests<YearMonth> {

    @Override
    protected YearMonth provideT1() {
        return YearMonth.of(1970, 6);
    }

    @Override
    protected YearMonth provideT2() {
        return YearMonth.of(1971, 7);
    }

    @Override
    protected YearMonth provideT3() {
        return YearMonth.of(1973, 12);
    }

    @Override
    protected YearMonth provideT4() {
        return YearMonth.of(1950, 1);
    }

    @Override
    protected Object provideNestedInObject() {
        return new NestedYearMonth(
                provideT1(),
                provideT2());
    }

    @Override
    protected void assertNestedInObject(Object expected, Object actual) {
        NestedYearMonth expectedDate = (NestedYearMonth) expected;
        NestedYearMonth actualDate = (NestedYearMonth) actual;

        assertThat(actualDate.dateTime1)
                .isEqualTo(expectedDate.dateTime1)
                .isNotSameAs(actualDate.dateTime2);

        assertThat(actualDate.dateTime1).isEqualTo(expectedDate.dateTime1);
    }

    @Override
    protected Object provideDuplicatesNestedInObject() {
        return new NestedYearMonth(provideT1());
    }

    @Override
    protected void assertDuplicatesNestedInObject(Object expected, Object actual) {
        NestedYearMonth expectedDate = (NestedYearMonth) expected;
        NestedYearMonth actualDate = (NestedYearMonth) actual;

        String json = toJson(expected);

        assertThat(actualDate.dateTime1)
                .isEqualTo(expectedDate.dateTime1)
                .isSameAs(actualDate.dateTime2);

        assertThat(actualDate.dateTime1).isEqualTo(expectedDate.dateTime1);
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsMaps(YearMonth expected, Object actual) {
        assertThat(actual).isEqualTo("1970-06");
    }

    @Test
    void testOldFormat_objectType() {
        String json = "{\"@type\":\"java.time.YearMonth\",\"year\":1970,\"month\":6}";
        YearMonth date = toJava(json);
        assertThat(date.getYear()).isEqualTo(1970);
        assertThat(date.getMonthValue()).isEqualTo(6);
    }

    @Test
    void testOldFormat_nestedObject() {
        String json = "{\"@type\":\"com.cedarsoftware.util.io.YearMonthTests$NestedYearMonth\",\"dateTime1\":{\"@id\":1,\"year\":1970,\"month\":6},\"dateTime2\":{\"@ref\":1}}";
        NestedYearMonth date = toJava(json);
        assertThat(date.dateTime1.getYear()).isEqualTo(1970);
        assertThat(date.dateTime1.getMonthValue()).isEqualTo(6);
        assertThat(date.dateTime1).isSameAs(date.dateTime2);
    }

    @Test
    void testTopLevel_serializesAsISODate() {
        YearMonth date = YearMonth.of(2014, 10);
        String json = TestUtil.toJson(date);
        YearMonth result = toJava(json);
        assertThat(result).isEqualTo(date);
    }

    @Test
    void testYearMonth_inArray() {
        YearMonth[] initial = new YearMonth[]{
                YearMonth.of(2014, 10),
                YearMonth.of(2023, 6)
        };

        YearMonth[] actual = TestUtil.serializeDeserialize(initial);

        assertThat(actual).isEqualTo(initial);
    }

    private void assertYearMonth(YearMonth date, int year, int month) {
        assertThat(date.getYear()).isEqualTo(year);
        assertThat(date.getMonthValue()).isEqualTo(month);
    }

    private static class NestedYearMonth {
        public YearMonth dateTime1;
        public YearMonth dateTime2;

        public NestedYearMonth(YearMonth dateTime1, YearMonth dateTime2) {
            this.dateTime1 = dateTime1;
            this.dateTime2 = dateTime2;
        }

        public NestedYearMonth(YearMonth date) {
            this(date, date);
        }
    }
}
