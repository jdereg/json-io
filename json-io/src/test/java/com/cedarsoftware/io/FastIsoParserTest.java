package com.cedarsoftware.io;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for the strict-ISO-first temporal fast path (4.104.0). The fast path must
 * (a) parse every ISO form json-io itself writes, (b) return null for anything
 * non-ISO so the Converter/DateUtilities flexible path still handles it, and
 * (c) leave end-to-end flexible-format reads working.
 */
class FastIsoParserTest {

    @Test
    void parsesAllSupportedIsoForms() {
        assertEquals(Instant.parse("2026-06-10T17:45:30.123456789Z"),
                FastIsoParser.parse("2026-06-10T17:45:30.123456789Z", Instant.class));
        assertEquals(LocalDate.of(2026, 6, 10), FastIsoParser.parse("2026-06-10", LocalDate.class));
        assertEquals(LocalDateTime.of(2026, 6, 10, 13, 45, 30),
                FastIsoParser.parse("2026-06-10T13:45:30", LocalDateTime.class));
        assertEquals(LocalTime.of(13, 45, 30), FastIsoParser.parse("13:45:30", LocalTime.class));
        assertEquals(ZonedDateTime.parse("2026-06-10T13:45:30-04:00[America/New_York]"),
                FastIsoParser.parse("2026-06-10T13:45:30-04:00[America/New_York]", ZonedDateTime.class));
        assertEquals(OffsetDateTime.parse("2026-06-10T13:45:30+05:30"),
                FastIsoParser.parse("2026-06-10T13:45:30+05:30", OffsetDateTime.class));
        assertEquals(OffsetTime.parse("13:45:30-08:00"),
                FastIsoParser.parse("13:45:30-08:00", OffsetTime.class));
        assertEquals(Duration.parse("PT2H30M"), FastIsoParser.parse("PT2H30M", Duration.class));
        assertEquals(Period.parse("P1Y2M3D"), FastIsoParser.parse("P1Y2M3D", Period.class));
        assertEquals(Year.of(2026), FastIsoParser.parse("2026", Year.class));
        assertEquals(YearMonth.of(2026, 6), FastIsoParser.parse("2026-06", YearMonth.class));
        assertEquals(MonthDay.of(12, 25), FastIsoParser.parse("--12-25", MonthDay.class));
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), FastIsoParser.parse("+05:30", ZoneOffset.class));
        assertEquals(ZoneId.of("America/Chicago"), FastIsoParser.parse("America/Chicago", ZoneId.class));
    }

    @Test
    void nonIsoFormsReturnNullForConverterFallback() {
        assertNull(FastIsoParser.parse("January 5, 2024", LocalDate.class));
        assertNull(FastIsoParser.parse("01/05/2024", LocalDate.class));
        assertNull(FastIsoParser.parse("garbage", Instant.class));
        assertNull(FastIsoParser.parse("1700000000000", ZonedDateTime.class));
        assertNull(FastIsoParser.parse("", Duration.class));
        // Unhandled target types are not intercepted at all
        assertNull(FastIsoParser.parse("2026-06-10", String.class));
        assertNull(FastIsoParser.parse("2026-06-10", java.util.Date.class));
    }

    @Test
    void flexibleFormatsStillWorkEndToEnd() {
        // Non-ISO strings must still reach Converter/DateUtilities through the fallback.
        LocalDate ld = JsonIo.toJava("\"January 5, 2024\"", null).asClass(LocalDate.class);
        assertEquals(LocalDate.of(2024, 1, 5), ld);

        Instant epoch = JsonIo.toJava("\"2024-01-05T10:30:00Z\"", null).asClass(Instant.class);
        assertEquals(Instant.parse("2024-01-05T10:30:00Z"), epoch);
    }

    @Test
    void temporalCollectionsRoundTripThroughFastPath() {
        // List<Instant> exercises the readWithFactoryIfExists hook (element conversion).
        List<Instant> instants = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            instants.add(Instant.parse("2026-06-10T17:45:30Z").plusSeconds(i * 86400L));
        }
        String json = JsonIo.toJson(instants, null);
        List<Instant> back = JsonIo.toJava(json, null).asType(new TypeHolder<List<Instant>>() {});
        assertEquals(instants, back);

        List<LocalDate> dates = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            dates.add(LocalDate.of(2026, 6, i));
        }
        json = JsonIo.toJson(dates, null);
        List<LocalDate> datesBack = JsonIo.toJava(json, null).asType(new TypeHolder<List<LocalDate>>() {});
        assertEquals(dates, datesBack);
    }
}
