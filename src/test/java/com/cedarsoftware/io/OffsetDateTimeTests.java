package com.cedarsoftware.io;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.cedarsoftware.util.ClassUtilities;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class OffsetDateTimeTests extends SerializationDeserializationMinimumTests<OffsetDateTime> {
    private static final ZoneOffset Z1 = ZoneOffset.UTC;

    private static final ZoneOffset Z2 = ZoneOffset.of("+05:00");

    private static final ZoneOffset Z3 = ZoneOffset.of("Z");

    private static final ZoneOffset Z4 = ZoneOffset.of("+07:00");


    @Override
    protected boolean isReferenceable() {
        return false;
    }

    @Override
    protected OffsetDateTime[] extractNestedInObject_withMatchingFieldTypes(Object o) {
        NestedOffsetDateTime dateTime = (NestedOffsetDateTime) o;
        return new OffsetDateTime[]{dateTime.one, dateTime.two};
    }


    @Test
    void testOldFormat_nested_withRef() {
        String json = loadJsonForTest("old-format-with-ref.json");
        NestedOffsetDateTime offsetDateTime = TestUtil.toObjects(json, null);

        assertOffsetDateTime(offsetDateTime.one, 2019, 12, 15, 9, 7, 16, 20 * 100, "Z");
        assertOffsetDateTime(offsetDateTime.two, 2019, 12, 15, 9, 7, 16, 20 * 100, "Z");
        assertSame(offsetDateTime.one.getOffset(), offsetDateTime.two.getOffset());
    }

    @Test
    void testOldFormat_nested() {
        String json = loadJsonForTest("old-format-nested.json");
        NestedOffsetDateTime offsetDateTime = TestUtil.toObjects(json, null);
        assertOffsetDateTime(offsetDateTime.one, 2027, 12, 23, 6, 7, 16, 20 * 100, "+05:00");
        assertNotSame(offsetDateTime.one.getOffset(), offsetDateTime.two.getOffset());
    }

    @Test
    void testOldFormat_simple() {
        String json = loadJsonForTest("old-format-simple.json");
        OffsetDateTime offsetDateTime = TestUtil.toObjects(json, null);
        assertOffsetDateTime(offsetDateTime, 2019, 12, 15, 9, 7, 16, 20 * 100, "Z");
    }

    private void assertOffsetDateTime(OffsetDateTime offsetDateTime, int year, int month, int day, int hour, int min, int sec, int nano, String zone) {
        assertEquals(offsetDateTime.getYear(), (year));
        assertEquals(offsetDateTime.getMonthValue(), (month));
        assertEquals(offsetDateTime.getDayOfMonth(), (day));
        assertEquals(offsetDateTime.getHour(), (hour));
        assertEquals(offsetDateTime.getMinute(), (min));
        assertEquals(offsetDateTime.getSecond(), (sec));
        assertEquals(offsetDateTime.getNano(), (nano));
        assertEquals(offsetDateTime.getOffset(), (ZoneOffset.of("Z")));
    }

    private String loadJsonForTest(String fileName) {
        return ClassUtilities.loadResourceAsString("offsetdatetime/" + fileName);
    }

    @Override
    protected OffsetDateTime provideT1() {
        LocalDateTime localDateTime = LocalDateTime.of(2019, 12, 15, 9, 7, 16, 2000);
        return OffsetDateTime.of(localDateTime, Z1);
    }

    @Override
    protected OffsetDateTime provideT2() {
        LocalDateTime localDateTime = LocalDateTime.of(2027, 12, 23, 9, 7, 16, 2000);
        return OffsetDateTime.of(localDateTime, Z2);
    }

    @Override
    protected OffsetDateTime provideT3() {
        LocalDateTime localDateTime = LocalDateTime.of(2027, 12, 23, 6, 7, 16, 2000);
        return OffsetDateTime.of(localDateTime, Z3);
    }

    @Override
    protected OffsetDateTime provideT4() {
        LocalDateTime localDateTime = LocalDateTime.of(2027, 12, 23, 6, 7, 16, 999999999);
        return OffsetDateTime.of(localDateTime, Z4);
    }

    @Override
    protected Class<OffsetDateTime> getTestClass() {
        return OffsetDateTime.class;
    }

    @Override
    protected NestedOffsetDateTime provideNestedInObject_withNoDuplicates_andFieldTypeMatchesObjectType() {
        return new NestedOffsetDateTime(provideT1(), provideT2());
    }

    @Override
    protected NestedOffsetDateTime provideNestedInObject_withDuplicates_andFieldTypeMatchesObjectType() {
        OffsetDateTime now = OffsetDateTime.now();
        return new NestedOffsetDateTime(now, now);
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsJsonTypes(OffsetDateTime expected, Object actual) {
        String value = (String) actual;
        assertThat(value).isEqualTo("2027-12-23T06:07:16.999999999+07:00");
    }

    public static class NestedOffsetDateTime {
        public OffsetDateTime one;
        public OffsetDateTime two;

        public NestedOffsetDateTime(OffsetDateTime one, OffsetDateTime two) {
            this.one = one;
            this.two = two;
        }

        public OffsetDateTime getOne() {
            return this.one;
        }

        public OffsetDateTime getTwo() {
            return this.two;
        }
    }
}
