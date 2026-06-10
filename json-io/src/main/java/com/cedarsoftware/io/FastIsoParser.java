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
import java.util.function.Function;

import com.cedarsoftware.util.ClassValueMap;

/**
 * Strict-ISO-first fast path for String → java.time conversion on the read side.
 * <p>
 * json-io writes java.time values in strict ISO-8601 form, but reading them back
 * through {@code Converter} routes into {@code DateUtilities.parseDate} — a flexible,
 * regex-driven parser that JFR showed at ~13% of read-phase CPU on temporal-heavy
 * payloads. The strict {@code java.time} parsers handle the ISO wire format in a
 * fraction of the time. This helper tries the strict parser first; any
 * {@code DateTimeParseException} (or other runtime failure) returns {@code null} and
 * the caller falls back to the Converter path, so every flexible format DateUtilities
 * accepts ("January 5, 2024", epoch millis, etc.) still works exactly as before.
 * <p>
 * ISO-8601 is unambiguous: when the strict parse succeeds, the flexible parser would
 * produce the same value, so the fast path is behavior-preserving for ISO input.
 * Same precedent as {@code ObjectResolver}'s fastPrimitiveCoercion — a targeted
 * bypass of Converter for the dominant wire formats.
 */
final class FastIsoParser {
    private static final ClassValueMap<Function<String, Object>> PARSERS = new ClassValueMap<>();
    static {
        PARSERS.put(Instant.class, Instant::parse);
        PARSERS.put(LocalDate.class, LocalDate::parse);
        PARSERS.put(LocalDateTime.class, LocalDateTime::parse);
        PARSERS.put(LocalTime.class, LocalTime::parse);
        PARSERS.put(ZonedDateTime.class, ZonedDateTime::parse);
        PARSERS.put(OffsetDateTime.class, OffsetDateTime::parse);
        PARSERS.put(OffsetTime.class, OffsetTime::parse);
        PARSERS.put(Duration.class, Duration::parse);
        PARSERS.put(Period.class, Period::parse);
        PARSERS.put(Year.class, Year::parse);
        PARSERS.put(YearMonth.class, YearMonth::parse);
        PARSERS.put(MonthDay.class, MonthDay::parse);
        PARSERS.put(ZoneOffset.class, ZoneOffset::of);
        PARSERS.put(ZoneId.class, ZoneId::of);
    }

    private FastIsoParser() {
    }

    /**
     * Attempt a strict java.time parse of {@code value} as {@code target}.
     *
     * @return the parsed value, or {@code null} when {@code target} is not a handled
     *         java.time type OR the string is not in the strict ISO form — callers
     *         fall back to the Converter path on null.
     */
    static Object parse(String value, Class<?> target) {
        Function<String, Object> parser = PARSERS.get(target);
        if (parser == null) {
            return null;
        }
        try {
            return parser.apply(value);
        } catch (RuntimeException notStrictIso) {
            return null;
        }
    }
}
