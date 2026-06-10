package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
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
import java.util.Calendar;
import java.util.Currency;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip coverage for the {@code writeStringUnescaped} fast path (4.104.0) —
 * built-in writers whose output alphabet (ISO-8601, hex, digits, identifier chars)
 * never needs JSON escaping now skip the escape scan. Each converted type must
 * serialize to the same string form as before and round-trip back to an equal value.
 * Also pins the Primitives ClassValueSet swap behavior.
 */
class UnescapedStringWriterTest {

    private static <T> T roundTrip(T value, Class<T> type) {
        String json = JsonIo.toJson(value, null);
        return JsonIo.toJava(json, null).asClass(type);
    }

    @Test
    void temporalTypesRoundTrip() {
        LocalDate ld = LocalDate.of(2026, 6, 10);
        assertEquals(ld, roundTrip(ld, LocalDate.class));

        LocalTime lt = LocalTime.of(13, 45, 30, 123456789);
        assertEquals(lt, roundTrip(lt, LocalTime.class));

        LocalDateTime ldt = LocalDateTime.of(2026, 6, 10, 13, 45, 30, 999999999);
        assertEquals(ldt, roundTrip(ldt, LocalDateTime.class));

        Instant instant = Instant.parse("2026-06-10T17:45:30.123456789Z");
        assertEquals(instant, roundTrip(instant, Instant.class));

        ZonedDateTime zdt = ZonedDateTime.of(2026, 6, 10, 13, 45, 30, 123000000,
                ZoneId.of("America/New_York"));
        assertEquals(zdt, roundTrip(zdt, ZonedDateTime.class));

        ZonedDateTime zdtUtc = ZonedDateTime.of(2026, 6, 10, 13, 45, 30, 0, ZoneOffset.UTC);
        assertEquals(zdtUtc.toInstant(), roundTrip(zdtUtc, ZonedDateTime.class).toInstant());

        OffsetDateTime odt = OffsetDateTime.of(2026, 6, 10, 13, 45, 30, 0, ZoneOffset.ofHoursMinutes(5, 30));
        assertEquals(odt, roundTrip(odt, OffsetDateTime.class));

        OffsetTime ot = OffsetTime.of(13, 45, 30, 0, ZoneOffset.ofHours(-8));
        assertEquals(ot, roundTrip(ot, OffsetTime.class));

        assertEquals(Duration.ofHours(2).plusMinutes(30).plusNanos(123456789),
                roundTrip(Duration.ofHours(2).plusMinutes(30).plusNanos(123456789), Duration.class));
        assertEquals(Period.of(1, 2, 3), roundTrip(Period.of(1, 2, 3), Period.class));
        assertEquals(Year.of(2026), roundTrip(Year.of(2026), Year.class));
        assertEquals(Year.of(-50), roundTrip(Year.of(-50), Year.class));
        assertEquals(YearMonth.of(2026, 6), roundTrip(YearMonth.of(2026, 6), YearMonth.class));
        assertEquals(MonthDay.of(12, 25), roundTrip(MonthDay.of(12, 25), MonthDay.class));
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30),
                roundTrip(ZoneOffset.ofHoursMinutes(5, 30), ZoneOffset.class));
    }

    @Test
    void dateFamilyRoundTrips() {
        Date date = new Date(1765432100000L);
        assertEquals(date, roundTrip(date, Date.class));

        java.sql.Date sqlDate = java.sql.Date.valueOf("2026-06-10");
        assertEquals(sqlDate.toLocalDate(), roundTrip(sqlDate, java.sql.Date.class).toLocalDate());

        Timestamp ts = new Timestamp(1765432100000L);
        ts.setNanos(123456789);
        assertEquals(ts, roundTrip(ts, Timestamp.class));

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/Chicago"));
        cal.setTimeInMillis(1765432100000L);
        Calendar back = roundTrip(cal, Calendar.class);
        assertEquals(cal.getTimeInMillis(), back.getTimeInMillis());
    }

    @Test
    void identifierAlphabetTypesRoundTrip() {
        UUID uuid = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
        assertEquals(uuid, roundTrip(uuid, UUID.class));

        BigInteger bigInt = new BigInteger("-123456789012345678901234567890");
        assertEquals(bigInt, roundTrip(bigInt, BigInteger.class));

        BigDecimal bigDec = new BigDecimal("-12345678901234567890.123456789");
        assertEquals(bigDec, roundTrip(bigDec, BigDecimal.class));

        Currency usd = Currency.getInstance("USD");
        assertEquals(usd, roundTrip(usd, Currency.class));

        Locale locale = Locale.forLanguageTag("en-US");
        assertEquals(locale, roundTrip(locale, Locale.class));
    }

    @Test
    void serializedFormUnchanged() {
        // The unescaped fast path must produce byte-identical output to the escaped path
        // for these alphabets — spot-check the raw JSON.
        String json = JsonIo.toJson(LocalDate.of(2026, 6, 10), null);
        assertTrue(json.contains("\"2026-06-10\""), json);

        json = JsonIo.toJson(UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479"), null);
        assertTrue(json.contains("\"f47ac10b-58cc-4372-a567-0e02b2c3d479\""), json);

        json = JsonIo.toJson(ZonedDateTime.of(2026, 6, 10, 13, 45, 30, 0,
                ZoneId.of("America/New_York")), null);
        assertTrue(json.contains("[America/New_York]\""), json);
    }

    @Test
    void worksInsidePrettyPrintAndJson5Modes() {
        WriteOptions pretty = new WriteOptionsBuilder().prettyPrint(true).build();
        String json = JsonIo.toJson(LocalDate.of(2026, 6, 10), pretty);
        assertTrue(json.contains("\"2026-06-10\""), json);

        WriteOptions json5 = new WriteOptionsBuilder().json5().build();
        json = JsonIo.toJson(LocalDate.of(2026, 6, 10), json5);
        LocalDate back = JsonIo.toJava(json, null).asClass(LocalDate.class);
        assertEquals(LocalDate.of(2026, 6, 10), back);
    }

    @Test
    void primitivesClassificationBehaviorPinned() {
        // ClassValueSet swap in Primitives must preserve exact contains() semantics.
        assertTrue(Primitives.isPrimitive(Integer.class));
        assertTrue(Primitives.isPrimitive(int.class));
        assertTrue(Primitives.isPrimitive(Boolean.class));
        assertFalse(Primitives.isPrimitive(String.class));
        assertFalse(Primitives.isPrimitive(Object.class));

        assertTrue(Primitives.isNativeJsonType(Long.class));
        assertTrue(Primitives.isNativeJsonType(String.class));
        assertTrue(Primitives.isNativeJsonType(Object[].class));
        assertFalse(Primitives.isNativeJsonType(Integer.class));
        assertFalse(Primitives.isNativeJsonType(null));
    }
}
