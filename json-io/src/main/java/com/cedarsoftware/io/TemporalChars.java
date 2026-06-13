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

/**
 * Hand-rolled ISO-8601 emitters for the common java.time types — the write-side
 * counterpart of the strict-ISO read fast path. The built-in temporal writers
 * previously formatted through {@link java.time.format.DateTimeFormatter}, whose
 * generalized field-resolution machinery ({@code DateTimePrintContext.getValue} etc.)
 * showed at ~4% of write-phase CPU in JFR. These emitters write digits directly into
 * a caller-supplied {@code char[]} using the same digit arithmetic as the JDK's own
 * {@code toString} implementations.
 * <p>
 * Output contracts match the exact wire forms the writers have always produced:
 * <ul>
 *   <li>{@code localDate}: {@code yyyy-MM-dd} (ISO_LOCAL_DATE)</li>
 *   <li>{@code localTime}: {@code HH:mm[:ss[.fraction]]} — seconds omitted when
 *       second and nano are both zero; fraction trims trailing zeros to minimal
 *       length (ISO_LOCAL_TIME / {@code appendFraction(0,9,true)} semantics)</li>
 *   <li>{@code instant}: seconds always present; fraction in groups of 3
 *       ({@code Instant.toString} / ISO_INSTANT semantics)</li>
 * </ul>
 * Each emitter returns the new offset, or {@code -1} when the value is outside the
 * fast range (year not in {@code 0..9999}) — callers fall back to the original
 * formatter path, so exotic values keep byte-identical output.
 */
final class TemporalChars {

    private TemporalChars() {
    }

    private static void write2(char[] b, int p, int v) {
        b[p] = (char) ('0' + v / 10);
        b[p + 1] = (char) ('0' + v % 10);
    }

    private static void write4(char[] b, int p, int v) {
        b[p] = (char) ('0' + v / 1000);
        b[p + 1] = (char) ('0' + (v / 100) % 10);
        b[p + 2] = (char) ('0' + (v / 10) % 10);
        b[p + 3] = (char) ('0' + v % 10);
    }

    /** {@code yyyy-MM-dd}; returns -1 when year outside 0..9999. */
    static int localDate(char[] b, int p, int year, int month, int day) {
        if (year < 0 || year > 9999) {
            return -1;
        }
        write4(b, p, year);
        b[p + 4] = '-';
        write2(b, p + 5, month);
        b[p + 7] = '-';
        write2(b, p + 8, day);
        return p + 10;
    }

    static int localDate(char[] b, int p, LocalDate ld) {
        return localDate(b, p, ld.getYear(), ld.getMonthValue(), ld.getDayOfMonth());
    }

    /**
     * {@code HH:mm:ss[.fraction]} — ISO_LOCAL_TIME formatting semantics: seconds are
     * ALWAYS printed (formatter optional-sections affect parsing, not formatting);
     * the fraction appears only for nonzero nanos, with trailing zeros trimmed
     * ({@code appendFraction(0,9,true)} behavior). Never fails (no year involved).
     */
    static int localTime(char[] b, int p, int hour, int minute, int second, int nano) {
        write2(b, p, hour);
        b[p + 2] = ':';
        write2(b, p + 3, minute);
        b[p + 5] = ':';
        write2(b, p + 6, second);
        p += 8;
        if (nano > 0) {
            b[p++] = '.';
            int digits = 9;
            while (nano % 10 == 0) {
                nano /= 10;
                digits--;
            }
            for (int i = digits - 1; i >= 0; i--) {
                b[p + i] = (char) ('0' + nano % 10);
                nano /= 10;
            }
            p += digits;
        }
        return p;
    }

    static int localTime(char[] b, int p, LocalTime lt) {
        return localTime(b, p, lt.getHour(), lt.getMinute(), lt.getSecond(), lt.getNano());
    }

    /** {@code yyyy-MM-ddTHH:mm:ss[.fraction]}; returns -1 when year outside 0..9999. */
    static int localDateTime(char[] b, int p, LocalDateTime ldt) {
        p = localDate(b, p, ldt.getYear(), ldt.getMonthValue(), ldt.getDayOfMonth());
        if (p < 0) {
            return -1;
        }
        b[p] = 'T';
        return localTime(b, p + 1, ldt.getHour(), ldt.getMinute(), ldt.getSecond(), ldt.getNano());
    }

    /**
     * Append an ISO offset id ({@code Z}, {@code +05:30}, {@code -08:00}, possibly
     * with seconds) — {@link java.time.ZoneOffset#getId()} is a cached String with
     * exactly the formatter's output form.
     */
    private static int offsetId(char[] b, int p, String id) {
        int n = id.length();
        id.getChars(0, n, b, p);
        return p + n;
    }

    /** ISO_OFFSET_DATE_TIME: localDateTime + offset id; -1 on out-of-range year. */
    static int offsetDateTime(char[] b, int p, OffsetDateTime odt) {
        p = localDateTime(b, p, odt.toLocalDateTime());
        if (p < 0) {
            return -1;
        }
        return offsetId(b, p, odt.getOffset().getId());
    }

    /** ISO_OFFSET_TIME: localTime + offset id. Never fails. */
    static int offsetTime(char[] b, int p, OffsetTime ot) {
        p = localTime(b, p, ot.toLocalTime());
        return offsetId(b, p, ot.getOffset().getId());
    }

    /**
     * The ZonedDateTimeWriter wire form: ISO_OFFSET_DATE_TIME + {@code [zoneId]}.
     * Returns -1 on out-of-range year. Caller sizes the buffer for the zone id.
     */
    static int zonedDateTime(char[] b, int p, ZonedDateTime zdt) {
        p = localDateTime(b, p, zdt.toLocalDateTime());
        if (p < 0) {
            return -1;
        }
        p = offsetId(b, p, zdt.getOffset().getId());
        b[p++] = '[';
        String zid = zdt.getZone().getId();
        int n = zid.length();
        zid.getChars(0, n, b, p);
        p += n;
        b[p++] = ']';
        return p;
    }

    /**
     * ISO_ZONED_DATE_TIME semantics — the form java-util's Converter produces for
     * ZonedDateTime → String: offset always printed; bracketed zone id only when the
     * zone is a region id, NOT a plain {@link ZoneOffset}. (Contrast with
     * {@link #zonedDateTime}, the JSON ZonedDateTimeWriter form, which always
     * brackets.) Returns -1 when the year falls outside 0..9999.
     */
    static int zonedDateTimeIsoZoned(char[] b, int p, ZonedDateTime zdt) {
        p = localDateTime(b, p, zdt.toLocalDateTime());
        if (p < 0) {
            return -1;
        }
        p = offsetId(b, p, zdt.getOffset().getId());
        ZoneId zone = zdt.getZone();
        if (!(zone instanceof ZoneOffset)) {
            b[p++] = '[';
            String zid = zone.getId();
            int n = zid.length();
            zid.getChars(0, n, b, p);
            p += n;
            b[p++] = ']';
        }
        return p;
    }

    /**
     * String-producing dispatcher for the TOON writer's CONVERTER_SUPPORTED case —
     * returns the exact string java-util's Converter produces for the common
     * java.time types (parity pinned by TemporalCharsTest), or {@code null} for
     * non-temporal values and years outside 0..9999, where callers fall back to
     * the Converter path.
     */
    static String toIsoString(Object value) {
        final char[] b;
        final int n;
        if (value instanceof LocalDate) {
            b = new char[10];
            n = localDate(b, 0, (LocalDate) value);
        } else if (value instanceof LocalDateTime) {
            b = new char[29];
            n = localDateTime(b, 0, (LocalDateTime) value);
        } else if (value instanceof Instant) {
            b = new char[31];
            n = instant(b, 0, (Instant) value);
        } else if (value instanceof ZonedDateTime) {
            ZonedDateTime zdt = (ZonedDateTime) value;
            b = new char[40 + zdt.getZone().getId().length()];
            n = zonedDateTimeIsoZoned(b, 0, zdt);
        } else if (value instanceof OffsetDateTime) {
            b = new char[39];
            n = offsetDateTime(b, 0, (OffsetDateTime) value);
        } else if (value instanceof LocalTime) {
            b = new char[18];
            n = localTime(b, 0, (LocalTime) value);
        } else if (value instanceof OffsetTime) {
            b = new char[28];
            n = offsetTime(b, 0, (OffsetTime) value);
        } else {
            return null;
        }
        return n < 0 ? null : new String(b, 0, n);
    }

    /**
     * {@code Instant.toString()} / ISO_INSTANT semantics: civil date from epoch day
     * (Howard Hinnant's algorithm), seconds always emitted, fraction in groups of 3.
     * Returns -1 when the year falls outside 0..9999 (caller falls back to
     * {@code instant.toString()}).
     */
    static int instant(char[] b, int p, Instant inst) {
        long epochSecond = inst.getEpochSecond();
        int nano = inst.getNano();

        long epochDay = Math.floorDiv(epochSecond, 86400L);
        int secsOfDay = (int) Math.floorMod(epochSecond, 86400L);

        // Civil-from-days
        long z = epochDay + 719468L;
        long era = Math.floorDiv(z, 146097L);
        long doe = z - era * 146097L;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long y = yoe + era * 400L;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long day = doy - (153 * mp + 2) / 5 + 1;
        long month = mp < 10 ? mp + 3 : mp - 9;
        if (month <= 2) {
            y++;
        }
        if (y < 0 || y > 9999) {
            return -1;
        }

        p = localDate(b, p, (int) y, (int) month, (int) day);
        b[p] = 'T';
        p++;
        write2(b, p, secsOfDay / 3600);
        b[p + 2] = ':';
        write2(b, p + 3, (secsOfDay / 60) % 60);
        b[p + 5] = ':';
        write2(b, p + 6, secsOfDay % 60);
        p += 8;
        if (nano > 0) {
            b[p++] = '.';
            int digits;
            int frac;
            if (nano % 1_000_000 == 0) {
                digits = 3;
                frac = nano / 1_000_000;
            } else if (nano % 1_000 == 0) {
                digits = 6;
                frac = nano / 1_000;
            } else {
                digits = 9;
                frac = nano;
            }
            for (int i = digits - 1; i >= 0; i--) {
                b[p + i] = (char) ('0' + frac % 10);
                frac /= 10;
            }
            p += digits;
        }
        b[p++] = 'Z';
        return p;
    }
}
