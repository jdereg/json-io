package com.cedarsoftware.io;

import java.time.MonthDay;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.io.TestUtil.toObjects;
import static org.assertj.core.api.Assertions.assertThat;

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
    protected Class<MonthDay> getTestClass() {
        return MonthDay.class;
    }


    @Override
    protected boolean isReferenceable() {
        return false;
    }

    @Override
    protected MonthDay[] extractNestedInObject_withMatchingFieldTypes(Object o) {
        NestedMonthDay date = (NestedMonthDay) o;
        return new MonthDay[]{date.one, date.two};
    }

    @Override
    protected Object provideNestedInObject_withNoDuplicates_andFieldTypeMatchesObjectType() {
        return new NestedMonthDay(
                provideT1(),
                provideT2());
    }

    @Override
    protected Object provideNestedInObject_withDuplicates_andFieldTypeMatchesObjectType() {
        MonthDay date = provideT1();
        return new NestedMonthDay(date, date);
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsJsonTypes(MonthDay expected, Object actual) {
        assertThat(actual).isEqualTo("--09-01");
    }

    @Test
    void testOldFormat_objectType() {
        String json = "{\"@type\":\"java.time.MonthDay\",\"monthDay\":\"6-30\"}";
        MonthDay date = toObjects(json, null);
        assertThat(date.getDayOfMonth()).isEqualTo(30);
        assertThat(date.getMonthValue()).isEqualTo(6);
    }

    @Test
    void testOldFormat_nestedObject() {
        String json = "{\"@type\":\"com.cedarsoftware.io.MonthDayTests$NestedMonthDay\",\"one\":{\"@id\":1,\"monthDay\":\"--06-30\"},\"two\":{\"@ref\":1}}";
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
        private final MonthDay one;
        private final MonthDay two;

        public NestedMonthDay(MonthDay one, MonthDay two) {
            this.one = one;
            this.two = two;
        }

        public MonthDay getOne() {
            return this.one;
        }

        public MonthDay getTwo() {
            return this.two;
        }
    }
}
