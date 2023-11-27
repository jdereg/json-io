package com.cedarsoftware.util.io;

import static org.assertj.core.api.Assertions.assertThat;

import static com.cedarsoftware.util.io.TestUtil.toObjects;

import java.time.MonthDay;

import org.junit.jupiter.api.Test;

class MonthDayTests extends SerializationDeserializationMinimumTests<MonthDay> {

    @Override
    protected MonthDay provideT1() {
        return MonthDay.of(12, 6);
    }

    @Override
    protected MonthDay provideT2() {
        return MonthDay.of(1, 7);
    }

    @Override
    protected MonthDay provideT3() {
        return MonthDay.of(3, 12);
    }

    @Override
    protected MonthDay provideT4() {
        return MonthDay.of(9, 1);
    }

    @Override
    protected Object provideNestedInObject() {
        return new NestedMonthDay(
                provideT1(),
                provideT2());
    }

    @Override
    protected void assertNestedInObject(Object expected, Object actual) {
        NestedMonthDay expectedDate = (NestedMonthDay) expected;
        NestedMonthDay actualDate = (NestedMonthDay) actual;

        assertThat(actualDate.one)
                .isEqualTo(expectedDate.one)
                .isNotSameAs(actualDate.two);

        assertThat(actualDate.one).isEqualTo(expectedDate.one);
    }

    @Override
    protected Object provideDuplicatesNestedInObject() {
        return new NestedMonthDay(provideT1());
    }

    @Override
    protected void assertDuplicatesNestedInObject(Object expected, Object actual) {
        NestedMonthDay expectedDate = (NestedMonthDay) expected;
        NestedMonthDay actualDate = (NestedMonthDay) actual;

        assertThat(actualDate.one)
                .isEqualTo(expectedDate.one)
                .isSameAs(actualDate.two);

        assertThat(actualDate.two).isEqualTo(expectedDate.two);
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsMaps(MonthDay expected, Object actual) {
        assertThat(actual).isEqualTo("09-01");
    }

    @Test
    void testOldFormat_objectType() {
        String json = "{\"@type\":\"java.time.MonthDay\",\"day\":30,\"month\":6}";
        MonthDay date = toObjects(json, null);
        assertThat(date.getDayOfMonth()).isEqualTo(30);
        assertThat(date.getMonthValue()).isEqualTo(6);
    }

    @Test
    void testOldFormat_nestedObject() {
        String json = "{\"@type\":\"com.cedarsoftware.util.io.MonthDayTests$NestedMonthDay\",\"one\":{\"@id\":1,\"day\":30,\"month\":6},\"two\":{\"@ref\":1}}";
        NestedMonthDay date = toObjects(json, null);
        assertThat(date.one.getDayOfMonth()).isEqualTo(30);
        assertThat(date.one.getMonthValue()).isEqualTo(6);
        assertThat(date.one).isSameAs(date.two);
    }

    @Test
    void testTopLevel_serializesAsISODate() {
        MonthDay date = MonthDay.of(10, 10);
        String json = TestUtil.toJson(date);
        MonthDay result = toObjects(json, null);
        assertThat(result).isEqualTo(date);
    }

    @Test
    void testMonthDay_inArray() {
        MonthDay[] initial = new MonthDay[]{
                MonthDay.of(10, 10),
                MonthDay.of(1, 6)
        };

        MonthDay[] actual = TestUtil.serializeDeserialize(initial);

        assertThat(actual).isEqualTo(initial);
    }

    private static class NestedMonthDay {
        public MonthDay one;
        public MonthDay two;

        public NestedMonthDay(MonthDay one, MonthDay two) {
            this.one = one;
            this.two = two;
        }

        public NestedMonthDay(MonthDay date) {
            this(date, date);
        }
    }
}
