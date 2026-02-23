package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies a format pattern for date/time fields during serialization and deserialization.
 * <p>
 * When a field is annotated with {@code @IoFormat("pattern")}, json-io will use the specified
 * pattern to format the value on write and parse it on read, instead of the default ISO format.
 * </p>
 * <p>
 * Supported types: {@code LocalDate}, {@code LocalTime}, {@code LocalDateTime},
 * {@code ZonedDateTime}, {@code OffsetDateTime}, {@code OffsetTime}, {@code Instant},
 * {@code java.util.Date}, {@code java.sql.Date}, {@code Timestamp}.
 * </p>
 * <p>
 * Equivalent to Jackson's {@code @JsonFormat(pattern = "...")}. When both are present,
 * {@code @IoFormat} takes priority.
 * </p>
 *
 * <pre>{@code
 * public class Event {
 *     @IoFormat("dd/MM/yyyy")
 *     private LocalDate eventDate;
 *
 *     @IoFormat("yyyy-MM-dd HH:mm")
 *     private LocalDateTime createdAt;
 * }
 * // eventDate serializes as: "23/02/2026" instead of "2026-02-23"
 * }</pre>
 *
 * @see com.cedarsoftware.io.WriteOptionsBuilder
 * @see com.cedarsoftware.io.ReadOptionsBuilder
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IoFormat {
    /**
     * The format pattern to use (e.g., "yyyy-MM-dd", "HH:mm:ss", "MM/dd/yyyy HH:mm:ss").
     * Uses {@link java.time.format.DateTimeFormatter} patterns for java.time types
     * and {@link java.text.SimpleDateFormat} patterns for {@code java.util.Date}.
     */
    String value();
}
