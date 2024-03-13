package com.cedarsoftware.io;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OddDateFormatHandlingTests {

    private static Stream<Arguments> getDifferentDateTypes() {
        return Stream.of(
                Arguments.of(Date.class),
                Arguments.of(LocalDate.class),
                Arguments.of(LocalDateTime.class),
                Arguments.of(LocalTime.class),
                Arguments.of(ZonedDateTime.class),
                Arguments.of(OffsetDateTime.class),
                Arguments.of(OffsetTime.class),
                Arguments.of(YearMonth.class),
                Arguments.of(Year.class)
        );
    }

    @ParameterizedTest
    @MethodSource("getDifferentDateTypes")
    <T> void testFormat1_AllTimeTypesPass(Class<T> c) {
        String oddDate = "'{'\"@type\": \"{0}\",\"value\": \"2023/12/25 15:00\"'}'";
        String json = MessageFormat.format(oddDate, c.getName());
        T dt = TestUtil.toObjects(json, null);

        assertNotNull(dt);
    }

    @ParameterizedTest
    @MethodSource("getDifferentDateTypes")
    <T> void testFormat2_AllTimeTypesPass(Class<T> c) {
        String oddDate = "'{'\"@type\": \"{0}\",\"value\": \"2023-12-25\"'}'";
        String json = MessageFormat.format(oddDate, c.getName());
        T dt = TestUtil.toObjects(json, null);

        assertNotNull(dt);
    }
}
