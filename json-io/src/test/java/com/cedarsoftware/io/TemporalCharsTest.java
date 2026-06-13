package com.cedarsoftware.io;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Byte-for-byte parity between the hand-rolled ISO emitters (TemporalChars, 4.104.0)
 * and the DateTimeFormatter / toString paths they replace in the built-in writers.
 * Randomized sweeps plus the fraction-trimming and year-boundary edges.
 */
class TemporalCharsTest {

    private static String emitLocalDate(LocalDate ld) {
        char[] b = new char[10];
        int n = TemporalChars.localDate(b, 0, ld);
        return n < 0 ? null : new String(b, 0, n);
    }

    private static String emitLocalTime(LocalTime lt) {
        char[] b = new char[18];
        return new String(b, 0, TemporalChars.localTime(b, 0, lt));
    }

    private static String emitLocalDateTime(LocalDateTime ldt) {
        char[] b = new char[29];
        int n = TemporalChars.localDateTime(b, 0, ldt);
        return n < 0 ? null : new String(b, 0, n);
    }

    private static String emitInstant(Instant i) {
        char[] b = new char[31];
        int n = TemporalChars.instant(b, 0, i);
        return n < 0 ? null : new String(b, 0, n);
    }

    private static String emitZdt(ZonedDateTime z) {
        char[] b = new char[40 + z.getZone().getId().length()];
        int n = TemporalChars.zonedDateTime(b, 0, z);
        return n < 0 ? null : new String(b, 0, n);
    }

    private static String emitOdt(OffsetDateTime o) {
        char[] b = new char[39];
        int n = TemporalChars.offsetDateTime(b, 0, o);
        return n < 0 ? null : new String(b, 0, n);
    }

    private static String emitOt(OffsetTime o) {
        char[] b = new char[28];
        return new String(b, 0, TemporalChars.offsetTime(b, 0, o));
    }

    @Test
    void randomizedParityWithFormatters() {
        DateTimeFormatter isoLocalDate = DateTimeFormatter.ISO_LOCAL_DATE;
        DateTimeFormatter isoLocalTime = DateTimeFormatter.ISO_LOCAL_TIME;
        DateTimeFormatter isoLocalDateTime = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        DateTimeFormatter isoOffsetDateTime = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        DateTimeFormatter isoOffsetTime = DateTimeFormatter.ISO_OFFSET_TIME;

        String[] zones = {"America/New_York", "UTC", "Asia/Kolkata", "Europe/London", "Australia/Lord_Howe"};
        Random r = new Random(20260610);
        for (int i = 0; i < 20_000; i++) {
            int year = r.nextInt(10000);                  // fast-range years only
            LocalDate ld = LocalDate.of(year, 1 + r.nextInt(12), 1 + r.nextInt(28));
            // Bias nanos toward interesting forms: 0, millis, micros, full nanos
            int nano;
            switch (r.nextInt(5)) {
                case 0: nano = 0; break;
                case 1: nano = (1 + r.nextInt(999)) * 1_000_000; break;
                case 2: nano = (1 + r.nextInt(999_999)) * 1_000; break;
                case 3: nano = 1 + r.nextInt(999_999_999); break;
                default: nano = 100_000_000 * (1 + r.nextInt(9)); break;   // single-digit fraction
            }
            LocalTime lt = LocalTime.of(r.nextInt(24), r.nextInt(60), r.nextInt(60), nano);
            LocalDateTime ldt = LocalDateTime.of(ld, lt);
            ZoneOffset off = ZoneOffset.ofTotalSeconds((r.nextInt(2 * 18 * 60) - 18 * 60) * 60);

            assertEquals(isoLocalDate.format(ld), emitLocalDate(ld));
            assertEquals(isoLocalTime.format(lt), emitLocalTime(lt));
            assertEquals(isoLocalDateTime.format(ldt), emitLocalDateTime(ldt));
            assertEquals(isoOffsetDateTime.format(OffsetDateTime.of(ldt, off)), emitOdt(OffsetDateTime.of(ldt, off)));
            assertEquals(isoOffsetTime.format(OffsetTime.of(lt, off)), emitOt(OffsetTime.of(lt, off)));

            ZonedDateTime zdt = ZonedDateTime.of(ldt, ZoneId.of(zones[r.nextInt(zones.length)]));
            String expectedZdt = isoOffsetDateTime.format(zdt) + "[" + zdt.getZone().getId() + "]";
            assertEquals(expectedZdt, emitZdt(zdt));
        }
    }

    @Test
    void randomizedInstantParityWithToString() {
        Random r = new Random(42);
        for (int i = 0; i < 20_000; i++) {
            // epoch seconds across 0001..9999 roughly: +/- ~62 billion sec covers it;
            // constrain to the fast range and let edges be checked separately
            long sec = (long) (r.nextDouble() * 2.5e11) - 62_000_000_000L;
            int nano;
            switch (r.nextInt(4)) {
                case 0: nano = 0; break;
                case 1: nano = (1 + r.nextInt(999)) * 1_000_000; break;
                case 2: nano = (1 + r.nextInt(999_999)) * 1_000; break;
                default: nano = 1 + r.nextInt(999_999_999); break;
            }
            Instant inst = Instant.ofEpochSecond(sec, nano);
            String emitted = emitInstant(inst);
            if (emitted != null) {
                assertEquals(inst.toString(), emitted, "epochSec=" + sec + " nano=" + nano);
            } else {
                // fallback only fires outside year 0..9999
                int y = inst.atZone(ZoneOffset.UTC).getYear();
                assertTrue(y < 0 || y > 9999, "unnecessary fallback for year " + y);
            }
        }
    }

    @Test
    void edgeCases() {
        assertEquals("0000-01-01", emitLocalDate(LocalDate.of(0, 1, 1)));
        assertEquals("9999-12-31", emitLocalDate(LocalDate.of(9999, 12, 31)));
        assertEquals(null, emitLocalDate(LocalDate.of(10000, 1, 1)));    // fallback
        assertEquals(null, emitLocalDate(LocalDate.of(-1, 1, 1)));       // fallback

        assertEquals("00:00:00", emitLocalTime(LocalTime.MIDNIGHT));     // ISO_LOCAL_TIME always prints seconds
        assertEquals("13:45:00", emitLocalTime(LocalTime.of(13, 45)));
        assertEquals("13:45:01", emitLocalTime(LocalTime.of(13, 45, 1)));
        assertEquals("13:45:00.5", emitLocalTime(LocalTime.of(13, 45, 0, 500_000_000)));
        assertEquals("23:59:59.999999999", emitLocalTime(LocalTime.MAX));

        assertEquals("1970-01-01T00:00:00Z", emitInstant(Instant.EPOCH));
        assertEquals("1969-12-31T23:59:59.999Z", emitInstant(Instant.ofEpochMilli(-1)));
        assertEquals("2026-06-10T17:45:30.000000001Z",
                emitInstant(Instant.parse("2026-06-10T17:45:30.000000001Z")));
        assertEquals(null, emitInstant(Instant.MAX));                    // fallback
        assertEquals(null, emitInstant(Instant.MIN));                    // fallback
    }

    @Test
    void toIsoStringMatchesConverterOutput() {
        // The TOON CONVERTER_SUPPORTED fast path must produce the exact string
        // java-util's Converter produces — including ISO_ZONED_DATE_TIME's
        // bracket-only-for-region-zones rule.
        com.cedarsoftware.util.convert.Converter converter =
                new com.cedarsoftware.util.convert.Converter(
                        new com.cedarsoftware.util.convert.DefaultConverterOptions());

        Object[] values = {
                LocalDate.of(2026, 6, 12),
                LocalDateTime.of(2026, 6, 12, 13, 45, 30, 123_000_000),
                LocalTime.of(13, 45, 30, 500_000_000),
                Instant.parse("2026-06-12T17:45:30.123456789Z"),
                OffsetDateTime.of(2026, 6, 12, 13, 45, 30, 0, ZoneOffset.ofHoursMinutes(5, 30)),
                OffsetTime.of(13, 45, 30, 0, ZoneOffset.ofHours(-8)),
                ZonedDateTime.of(2026, 6, 12, 13, 45, 30, 0, ZoneId.of("America/New_York")),  // region → bracketed
                ZonedDateTime.of(2026, 6, 12, 13, 45, 30, 0, ZoneOffset.ofHoursMinutes(5, 30)), // offset → NOT bracketed
                ZonedDateTime.of(2026, 6, 12, 13, 45, 30, 0, ZoneId.of("GMT+05:30")),         // GMT-region → bracketed
                ZonedDateTime.of(2026, 6, 12, 13, 45, 30, 0, ZoneOffset.UTC),                  // Z offset → NOT bracketed
        };
        for (Object v : values) {
            assertEquals(converter.convert(v, String.class), TemporalChars.toIsoString(v),
                    "mismatch for " + v.getClass().getSimpleName() + ": " + v);
        }

        // Non-temporals and exotic years return null (Converter fallback)
        assertEquals(null, TemporalChars.toIsoString("not a temporal"));
        assertEquals(null, TemporalChars.toIsoString(java.util.UUID.randomUUID()));
        assertEquals(null, TemporalChars.toIsoString(LocalDate.of(10000, 1, 1)));
    }

    @Test
    void mapsModeReadsIsoTemporalsViaFastPath() {
        // MapResolver hook: maps-mode reads with @type coerce ISO strings into the
        // declared field types — must produce identical values to the Converter path,
        // and flexible formats must still work via fallback.
        java.util.Map<String, Object> result = JsonIo.toJava(
                "{\"@type\":\"com.cedarsoftware.io.TemporalCharsTest$TemporalHolder\","
                        + "\"when\":\"2026-06-12T17:45:30Z\",\"day\":\"2026-06-12\"}",
                new ReadOptionsBuilder().returnAsJsonObjects().build())
                .asClass(java.util.Map.class);
        assertEquals(Instant.parse("2026-06-12T17:45:30Z"), result.get("when"));
        assertEquals(LocalDate.of(2026, 6, 12), result.get("day"));

        java.util.Map<String, Object> flexible = JsonIo.toJava(
                "{\"@type\":\"com.cedarsoftware.io.TemporalCharsTest$TemporalHolder\","
                        + "\"when\":\"2026-06-12T17:45:30Z\",\"day\":\"June 12, 2026\"}",
                new ReadOptionsBuilder().returnAsJsonObjects().build())
                .asClass(java.util.Map.class);
        assertEquals(LocalDate.of(2026, 6, 12), flexible.get("day"));
    }

    @SuppressWarnings("unused")
    private static class TemporalHolder {
        Instant when;
        LocalDate day;
    }

    @Test
    void writersEndToEndUnchanged() {
        // Through the real writers: serialized forms equal the pre-change wire format.
        LocalDateTime ldt = LocalDateTime.of(2026, 6, 10, 13, 45, 30, 123_000_000);
        assertTrue(JsonIo.toJson(ldt, null).contains("\"2026-06-10T13:45:30.123\""));

        ZonedDateTime zdt = ZonedDateTime.of(2026, 6, 10, 13, 45, 30, 0, ZoneId.of("America/New_York"));
        assertTrue(JsonIo.toJson(zdt, null).contains("\"2026-06-10T13:45:30-04:00[America/New_York]\""));

        Instant inst = Instant.parse("2026-06-10T17:45:30Z");
        assertEquals(inst, JsonIo.toJava(JsonIo.toJson(inst, null), null).asClass(Instant.class));

        // Fallback path end-to-end: far-future instant
        Instant far = Instant.parse("+25000-01-01T00:00:00Z");
        assertEquals(far, JsonIo.toJava(JsonIo.toJson(far, null), null).asClass(Instant.class));
    }
}
