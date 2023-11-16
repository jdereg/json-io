package com.cedarsoftware.util.io;

import com.cedarsoftware.util.io.models.NestedOffsetDateTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class OffsetDateTimeTests extends SerializationDeserializationMinimumTests<OffsetDateTime> {
    private static final ZoneOffset Z1 = ZoneOffset.UTC;

    private static final ZoneOffset Z2 = ZoneOffset.of("+05:00");

    private static final ZoneOffset Z3 = ZoneOffset.of("Z");
    
    @Test
    void testOldFormat_nested_withRef() {
        String json = loadJsonForTest("old-format-with-ref.json");
        NestedOffsetDateTime offsetDateTime = TestUtil.toJava(json);

        assertOffsetDateTime(offsetDateTime.date1, 2019, 12, 15, 9, 7, 16, 20 * 100, "Z");
        assertOffsetDateTime(offsetDateTime.date2, 2019, 12, 15, 9, 7, 16, 20 * 100, "Z");
        assertSame(offsetDateTime.date1.getOffset(), offsetDateTime.date2.getOffset());
    }

    @Test
    void testOldFormat_nested() {
        String json = loadJsonForTest("old-format-nested.json");
        NestedOffsetDateTime offsetDateTime = TestUtil.toJava(json);
        assertOffsetDateTime(offsetDateTime.date1, 2027, 12, 23, 6, 7, 16, 20 * 100, "+05:00");
        assertNotSame(offsetDateTime.date1.getOffset(), offsetDateTime.date2.getOffset());
    }

    @Test
    void testOldFormat_simple() {
        String json = loadJsonForTest("old-format-simple.json");
        OffsetDateTime offsetDateTime = TestUtil.toJava(json);
        assertOffsetDateTime(offsetDateTime, 2019, 12, 15, 9, 7, 16, 20 * 100, "Z");
    }

    private void assertOffsetDateTime(OffsetDateTime offsetDateTime, int year, int month, int day, int hour, int min, int sec, int nano, String zone) {
        assertThat(offsetDateTime.getYear()).isEqualTo(year);
        assertThat(offsetDateTime.getMonthValue()).isEqualTo(month);
        assertThat(offsetDateTime.getDayOfMonth()).isEqualTo(day);
        assertThat(offsetDateTime.getHour()).isEqualTo(hour);
        assertThat(offsetDateTime.getMinute()).isEqualTo(min);
        assertThat(offsetDateTime.getSecond()).isEqualTo(sec);
        assertThat(offsetDateTime.getNano()).isEqualTo(nano);
        assertThat(offsetDateTime.getOffset()).isEqualTo(ZoneOffset.of("Z"));
    }

    private String loadJsonForTest(String fileName) {
        return TestUtil.fetchResource("offsetdatetime/" + fileName);
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
        LocalDateTime localDateTime = LocalDateTime.of(2027, 12, 23, 6, 7, 16, 2000);
        return OffsetDateTime.of(localDateTime, Z1);
    }

    @Override
    protected NestedOffsetDateTime provideNestedInObject() {
        LocalDateTime localDateTime1 = LocalDateTime.of(2027, 12, 23, 6, 7, 16, 2000);
        LocalDateTime localDateTime2 = LocalDateTime.of(2027, 12, 23, 6, 7, 16, 2000);
        return new NestedOffsetDateTime(
                OffsetDateTime.of(localDateTime1, Z1),
                OffsetDateTime.of(localDateTime2, Z2));
    }

    @Override
    protected void assertNestedInObject(Object expected, Object actual) {
        NestedOffsetDateTime nestedExpected = (NestedOffsetDateTime) expected;
        NestedOffsetDateTime nestedActual = (NestedOffsetDateTime) actual;

        assertThat(nestedActual.date1).isEqualTo(nestedExpected.date1);
        assertThat(nestedActual.date2).isEqualTo(nestedExpected.date2);
    }

    @Override
    protected Object provideDuplicatesNestedInObject() {
        LocalDateTime localDateTime1 = LocalDateTime.of(2027, 12, 23, 6, 7, 16, 2000);
        return new NestedOffsetDateTime(
                OffsetDateTime.of(localDateTime1, Z1));
    }

    @Override
    protected void assertDuplicatesNestedInObject(Object expected, Object actual) {
        NestedOffsetDateTime nestedExpected = (NestedOffsetDateTime) expected;
        NestedOffsetDateTime nestedActual = (NestedOffsetDateTime) actual;

        assertThat(nestedActual.date1).isEqualTo(nestedExpected.date1);
        assertThat(nestedActual.date2).isEqualTo(nestedExpected.date2);
        assertThat(nestedActual.date2).isSameAs(nestedActual.date1);
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsMaps(OffsetDateTime expected, Object actual) {
        String value = (String) actual;
        assertThat(value).isEqualTo("2019-12-15T09:07:16.000002Z");
    }
}
