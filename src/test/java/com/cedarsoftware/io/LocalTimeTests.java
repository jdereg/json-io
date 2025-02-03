package com.cedarsoftware.io;

import java.time.LocalTime;
import java.util.stream.Stream;

import com.cedarsoftware.util.ClassUtilities;
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
    protected Class<LocalTime> getTestClass() {
        return LocalTime.class;
    }

    @Override
    protected boolean isReferenceable() {
        return false;
    }

    @Override
    protected LocalTime[] extractNestedInObject_withMatchingFieldTypes(Object o) {
        NestedLocalTime time = (NestedLocalTime) o;
        return new LocalTime[]{time.one, time.two};
    }

    @Override
    protected Object provideNestedInObject_withNoDuplicates_andFieldTypeMatchesObjectType() {
        return new NestedLocalTime(provideT1(), provideT2());
    }

    @Override
    protected Object provideNestedInObject_withDuplicates_andFieldTypeMatchesObjectType() {
        LocalTime now = LocalTime.now();
        return new NestedLocalTime(now, now);
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsJsonTypes(LocalTime expected, Object actual) {
        assertThat(actual).isEqualTo("23:59:59.999999999");
    }

    private static Stream<Arguments> checkDifferentFormatsByFile() {
        return Stream.of(
                Arguments.of("old-format-top-level.json", 17, 15, 16, 789980000),
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
        LocalTime lt = nested.getOne();
        assertLocalTime(nested.one, 9, 12, 15, 999999000);
    }

    private void assertLocalTime(LocalTime time, int hour, int minute, int second, int nano) {
        assertThat(time.getHour()).isEqualTo(hour);
        assertThat(time.getMinute()).isEqualTo(minute);
        assertThat(time.getSecond()).isEqualTo(second);
        assertThat(time.getNano()).isEqualTo(nano);
    }

    private String loadJsonForTest(String fileName) {
        return ClassUtilities.loadResourceAsString("localtime/" + fileName);
    }

    public static class NestedLocalTime {
        public LocalTime one;
        public LocalTime two;

        public NestedLocalTime(LocalTime one, LocalTime two) {
            this.one = one;
            this.two = two;
        }

        public LocalTime getOne() {
            return this.one;
        }

        public LocalTime getTwo() {
            return this.two;
        }
    }
}
