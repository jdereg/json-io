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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;


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
    // Filled once below, then read through PARSERS: each class's answer is kept on the class (a ClassValue)
    private static final Map<Class<?>, Function<String, Object>> PARSER_TABLE = new HashMap<>();
    private static final ClassValue<Function<String, Object>> PARSERS = new ClassValue<Function<String, Object>>() {
        @Override
        protected Function<String, Object> computeValue(Class<?> c) {
            return PARSER_TABLE.get(c);
        }
    };
    static {
        PARSER_TABLE.put(Instant.class, Instant::parse);
        PARSER_TABLE.put(LocalDate.class, LocalDate::parse);
        PARSER_TABLE.put(LocalDateTime.class, LocalDateTime::parse);
        PARSER_TABLE.put(LocalTime.class, LocalTime::parse);
        PARSER_TABLE.put(ZonedDateTime.class, ZonedDateTime::parse);
        PARSER_TABLE.put(OffsetDateTime.class, OffsetDateTime::parse);
        PARSER_TABLE.put(OffsetTime.class, OffsetTime::parse);
        PARSER_TABLE.put(Duration.class, Duration::parse);
        PARSER_TABLE.put(Period.class, Period::parse);
        PARSER_TABLE.put(Year.class, Year::parse);
        PARSER_TABLE.put(YearMonth.class, YearMonth::parse);
        PARSER_TABLE.put(MonthDay.class, MonthDay::parse);
        PARSER_TABLE.put(ZoneOffset.class, ZoneOffset::of);
        PARSER_TABLE.put(ZoneId.class, ZoneId::of);
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
        Function<String, Object> parser = target == null ? null : PARSERS.get(target);
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
