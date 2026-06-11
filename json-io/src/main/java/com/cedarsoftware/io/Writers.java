package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.TemporalAccessor;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.Currency;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Pattern;

import com.cedarsoftware.util.Converter;

import static com.cedarsoftware.io.JsonValue.VALUE;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

/**
 * All custom writers for json-io subclass this class.  Special writers are not needed for handling
 * user-defined classes.  However, special writers are built/supplied by json-io for many of the
 * primitive types and other JDK classes simply to allow for a more concise form.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class Writers {

    private Writers() {
    }

    // Shared cache for DateTimeFormatter instances created from @IoFormat / @JsonFormat patterns
    private static final Map<String, DateTimeFormatter> PATTERN_CACHE = new ConcurrentHashMap<>();

    /**
     * Get or create a DateTimeFormatter for the given pattern.
     */
    static DateTimeFormatter getFormatter(String pattern) {
        return PATTERN_CACHE.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
    }

    /**
     * <b>Deprecated</b> — use {@link #writeWithFieldFormat(Object, JsonGenerator, WriterContext)}.
     * If the WriterContext has a field format pattern and the value is a TemporalAccessor,
     * write it using the custom pattern and return true. Otherwise return false.
     */
    @Deprecated
    static boolean writeWithFieldFormat(Object o, Writer output, WriterContext context) throws IOException {
        String pat = context.getFieldFormatPattern();
        if (pat != null && o instanceof TemporalAccessor) {
            DateTimeFormatter fmt = getFormatter(pat);
            CharStreamGenerator.writeJsonUtf8String(output, fmt.format((TemporalAccessor) o));
            return true;
        }
        return false;
    }

    /**
     * {@link JsonGenerator}-based form of {@link #writeWithFieldFormat(Object, Writer, WriterContext)}.
     * If the WriterContext has a field format pattern and the value is a TemporalAccessor,
     * write it using the custom pattern and return true. Otherwise return false.
     *
     * @since 4.103.0
     */
    static boolean writeWithFieldFormat(Object o, JsonGenerator gen, WriterContext context) throws IOException {
        String pat = context.getFieldFormatPattern();
        if (pat != null && o instanceof TemporalAccessor) {
            DateTimeFormatter fmt = getFormatter(pat);
            gen.writeString(fmt.format((TemporalAccessor) o));
            return true;
        }
        return false;
    }

    /**
     * <b>Deprecated</b> — use {@link #writeWithStringFormat(Object, JsonGenerator, WriterContext)}.
     * If the WriterContext has a field format pattern containing '%' (C-style String.format),
     * format the value using String.format() and write as a quoted JSON string.
     * Returns true if handled, false otherwise.
     */
    @Deprecated
    public static boolean writeWithStringFormat(Object o, Writer output, WriterContext context) throws IOException {
        String pat = context.getFieldFormatPattern();
        if (pat != null && pat.indexOf('%') >= 0) {
            CharStreamGenerator.writeBasicString(output, String.format(pat, o));
            return true;
        }
        return false;
    }

    /**
     * {@link JsonGenerator}-based form of {@link #writeWithStringFormat(Object, Writer, WriterContext)}.
     * If the WriterContext has a field format pattern containing '%' (C-style String.format),
     * format the value using String.format() and write as a quoted JSON string.
     * Returns true if handled, false otherwise.
     *
     * @since 4.103.0
     */
    public static boolean writeWithStringFormat(Object o, JsonGenerator gen, WriterContext context) throws IOException {
        String pat = context.getFieldFormatPattern();
        if (pat != null && pat.indexOf('%') >= 0) {
            gen.writeString(String.format(pat, o));
            return true;
        }
        return false;
    }

    /**
     * <b>Deprecated</b> — use {@link #writeNumericWithFieldFormat(Object, JsonGenerator, WriterContext)}.
     * If the WriterContext has a field format pattern and the value is a Number,
     * format it using DecimalFormat and write as a quoted JSON string. Returns true if handled.
     */
    @Deprecated
    public static boolean writeNumericWithFieldFormat(Object o, Writer output, WriterContext context) throws IOException {
        String pat = context.getFieldFormatPattern();
        if (pat != null && o instanceof Number) {
            DecimalFormat df = new DecimalFormat(pat);
            CharStreamGenerator.writeBasicString(output, df.format(o));
            return true;
        }
        return false;
    }

    /**
     * {@link JsonGenerator}-based form of {@link #writeNumericWithFieldFormat(Object, Writer, WriterContext)}.
     * If the WriterContext has a field format pattern and the value is a Number,
     * format it using DecimalFormat and write as a quoted JSON string. Returns true if handled.
     *
     * @since 4.103.0
     */
    public static boolean writeNumericWithFieldFormat(Object o, JsonGenerator gen, WriterContext context) throws IOException {
        String pat = context.getFieldFormatPattern();
        if (pat != null && o instanceof Number) {
            DecimalFormat df = new DecimalFormat(pat);
            gen.writeString(df.format(o));
            return true;
        }
        return false;
    }

    /**
     * Used as a template to write out types that will have a primitive form.
     * Uses the default key of "value" unless overridden
     */
    public static class PrimitiveTypeWriter implements JsonClassWriter {
        protected String getKey() {
            return VALUE;
        }

        /**
         * Migrated in 4.103.0. Every {@code PrimitiveTypeWriter} descendant now
         * exposes a {@link JsonGenerator}-based {@code writePrimitiveForm(...)},
         * so this method dispatches through the generator and benefits from
         * auto-comma / structural state tracking instead of writing punctuation
         * directly. The deprecated {@link Writer}-based override below delegates
         * here via the appropriate bridge generator based on {@code showType}.
         */
        public void write(Object obj, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
            if (showType) {
                gen.writeFieldName(getKey());
            }
            writePrimitiveForm(obj, gen, context);
        }

        @Override
        @Deprecated
        public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
            // Caller's structural position depends on showType:
            //   * showType=true  → JsonWriter has opened a {...} envelope and emitted the
            //     @type/@id prelude; we're inside an open object body and need to emit
            //     "key":primitiveValue.
            //   * showType=false → JsonWriter is calling us directly from a value slot
            //     (e.g. writePrimitive's longBoxedWriter case for writeLongsAsStrings,
            //     or the primitive-array element writers in writeObjectArray); we emit
            //     just the bare primitive value at that value slot.
            // The legacy Writer-based path tolerated both positions because raw
            // output.write() is state-machine-free; the bridge generator is stricter,
            // so pick the right factory.
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = showType
                    ? JsonGenerator.deprecatedWriterBridge_insideObjectBody(output, options)
                    : JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            write(obj, showType, bridge, context);
        }

        public boolean hasPrimitiveForm(WriterContext writerContext) {
            return true;
        }
    }

    /**
     * Used as a template to write out primitive value types such as int, boolean, etc. that we extract as a String,
     * but we do not put in quotes.  Uses the default key of "value" unless overridden
     */
    public static class PrimitiveValueWriter extends PrimitiveTypeWriter {
        public String extractString(Object o) {
            return o.toString();
        }

        /**
         * Writes out a basic value type via the {@link JsonGenerator}, no quotes.
         * To write strings use {@link PrimitiveUtf8StringWriter}.
         * <p>
         * Migrated in 4.103.0. The deprecated {@link Writer}-based override below
         * delegates to this one via a value-slot bridge generator.
         */
        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            if (writeWithStringFormat(o, gen, context)) { return; }
            if (writeNumericWithFieldFormat(o, gen, context)) { return; }
            gen.writeNumber(extractString(o));
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(
                    output, context.getWriteOptions());
            writePrimitiveForm(o, bridge, context);
        }
    }

    /**
     * Used as a template to write out primitive value types such as int, boolean, etc. that we extract as a String,
     * but we do not put in quotes.  Uses the default key of "value" unless overridden
     */
    public abstract static class FloatingPointWriter<T> extends PrimitiveTypeWriter {
        /**
         * Writes out a floating-point type via the {@link JsonGenerator}, honoring
         * the active NaN/Infinity policy.
         * <p>
         * Migrated in 4.103.0. The deprecated {@link Writer}-based override below
         * delegates to this one via a value-slot bridge generator.
         */
        @SuppressWarnings("unchecked")
        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            if (writeWithStringFormat(o, gen, context)) { return; }
            if (writeNumericWithFieldFormat(o, gen, context)) { return; }
            WriteOptions options = context.getWriteOptions();
            boolean allowNanInfinity = options.isAllowNanAndInfinity() || options.isJson5InfinityNaN();
            if (allowNanInfinity || !isNanOrInfinity((T) o)) {
                gen.writeNumber(o.toString());
            } else {
                gen.writeNull();
            }
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(
                    output, context.getWriteOptions());
            writePrimitiveForm(o, bridge, context);
        }

        abstract boolean isNanOrInfinity(T value);
    }

    /**
     * Used as a template to write out primitive value types such as int, boolean, etc. that we extract as a String,
     * but we do not put in quotes.  Uses the default key of "value" unless overridden
     */
    public static class FloatWriter extends FloatingPointWriter<Float> {
        /**
         * Writes out Float point type.
         */
        boolean isNanOrInfinity(Float value) {
            return value.isNaN() || value.isInfinite();
        }
    }

    /**
     * Used as a template to write out primitive value types such as int, boolean, etc. that we extract as a String,
     * but we do not put in quotes.  Uses the default key of "value" unless overridden
     */
    public static class DoubleWriter extends FloatingPointWriter<Double> {
        /**
         * Writes out Double types.
         */
        boolean isNanOrInfinity(Double value) {
            return value.isNaN() || value.isInfinite();
        }
    }

    /**
     * Used as a template to write out primitive String types.
     * Uses default key of "value" and encodes the string.
     */
    public static class PrimitiveUtf8StringWriter extends PrimitiveTypeWriter {
        public String extractString(Object o) {
            return o == null ? null : o.toString();
        }

        /**
         * Writes the extracted string via the {@link JsonGenerator}, honoring the
         * active JSON5 / escape policy through {@link JsonGenerator#writeString(String)}.
         * <p>
         * Migrated in 4.103.0. The deprecated {@link Writer}-based override below
         * delegates to this one via a value-slot bridge generator.
         */
        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext writerContext) throws IOException {
            if (writerContext != null && writeWithStringFormat(o, gen, writerContext)) { return; }
            gen.writeString(extractString(o));
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext writerContext) throws IOException {
            WriteOptions options = writerContext != null ? writerContext.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, writerContext);
        }
    }

    /**
     * Used as a template to write out primitive String types.
     * Uses default key of "value" and encodes the string.
     */
    public static class CharacterWriter extends PrimitiveTypeWriter {
        /**
         * Migrated in 4.103.0. The deprecated {@link Writer}-based override below
         * delegates to this one via a value-slot bridge generator.
         */
        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            gen.writeString("" + (char) o);
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(
                    output, context.getWriteOptions());
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class TimeZoneWriter extends PrimitiveUtf8StringWriter {
        public String extractString(Object o) {
            return ((TimeZone) o).getID();
        }
    }

    public static class ClassWriter extends PrimitiveUtf8StringWriter {
        public String extractString(Object o) {
            return ((Class<?>) o).getName();
        }
    }

    public static class EnumsAsStringWriter extends PrimitiveUtf8StringWriter {
        protected String getKey() {
            return "name";
        }

        public String extractString(Object o) {
            return ((Enum<?>) o).name();
        }
    }

    public static class CalendarWriter implements JsonClassWriter {
        /**
         * Migrated in 4.103.0. The deprecated {@link Writer}-based override below
         * delegates via a value-slot bridge generator.
         */
        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            gen.writeStringUnescaped(Converter.convert(o, String.class));
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }

        /**
         * Migrated in 4.103.0. The deprecated {@link Writer}-based override below
         * delegates via an inside-object-body bridge generator.
         */
        public void write(Object obj, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
            if (showType) {
                gen.writeFieldName("calendar");
            }
            writePrimitiveForm(obj, gen, context);
        }

        @Override
        @Deprecated
        public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_insideObjectBody(output, options);
            write(obj, showType, bridge, context);
        }

        public boolean hasPrimitiveForm(WriterContext context) {
            return true;
        }
    }

    public static class DateWriter implements JsonClassWriter {
        /**
         * Migrated in 4.103.0. The deprecated {@link Writer}-based override below
         * delegates via a value-slot bridge generator.
         */
        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            String pat = (context != null) ? context.getFieldFormatPattern() : null;
            if (pat != null) {
                SimpleDateFormat sdf = new SimpleDateFormat(pat);
                gen.writeString(sdf.format((java.util.Date) o));
                return;
            }
            if (o instanceof java.sql.Date) {
                // Write just the date portion - no time, no timezone
                gen.writeStringUnescaped(((java.sql.Date) o).toLocalDate().toString());
            } else {
                // Regular Date uses the converter's string format
                gen.writeStringUnescaped(Converter.convert(o, String.class));
            }
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }

        /**
         * Migrated in 4.103.0. The deprecated {@link Writer}-based override below
         * delegates via an inside-object-body bridge generator.
         */
        public void write(Object obj, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
            if (showType) {
                String key = (obj instanceof java.sql.Date) ? "sqlDate" : "date";
                gen.writeFieldName(key);
            }
            writePrimitiveForm(obj, gen, context);
        }

        @Override
        @Deprecated
        public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_insideObjectBody(output, options);
            write(obj, showType, bridge, context);
        }

        public boolean hasPrimitiveForm(WriterContext context) {
            return true;
        }
    }

    public static class DateAsLongWriter extends DateWriter {
        /**
         * Migrated in 4.103.0. The deprecated {@link Writer}-based override below
         * delegates via a value-slot bridge generator. The {@code java.util.Date}
         * branch now emits the millisecond {@code long} as a JSON number literal
         * (previously a bare {@code Long.toString(...)} written directly).
         */
        @Override
        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            if (writeWithStringFormat(o, gen, context)) { return; }
            String pat = (context != null) ? context.getFieldFormatPattern() : null;
            if (pat != null) {
                SimpleDateFormat sdf = new SimpleDateFormat(pat);
                gen.writeString(sdf.format((java.util.Date) o));
                return;
            }
            if (o instanceof java.sql.Date) {
                // Same pure date format for sql.Date in both writers
                gen.writeStringUnescaped(((java.sql.Date) o).toLocalDate().toString());
            } else {
                // Regular Date uses milliseconds
                gen.writeNumber(((java.util.Date) o).getTime());
            }
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class LocalDateAsLong extends PrimitiveTypeWriter {
        private final ZoneId zoneId;

        public LocalDateAsLong(ZoneId zoneId) {
            this.zoneId = zoneId;
        }

        public LocalDateAsLong() {
            this(ZoneId.systemDefault());
        }

        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext writerContext) throws IOException {
            if (writeWithStringFormat(o, gen, writerContext)) { return; }
            if (writeWithFieldFormat(o, gen, writerContext)) { return; }
            LocalDate localDate = (LocalDate) o;
            ZonedDateTime zonedDateTime = localDate.atStartOfDay(zoneId);
            Instant instant = zonedDateTime.toInstant();
            gen.writeNumber(instant.toEpochMilli());
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext writerContext) throws IOException {
            WriteOptions options = (writerContext != null) ? writerContext.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, writerContext);
        }
    }

    public static class LocalDateWriter extends PrimitiveTypeWriter {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .toFormatter();

        protected String getKey() {
            return "localDate";
        }

        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            if (writeWithStringFormat(o, gen, context)) { return; }
            if (writeWithFieldFormat(o, gen, context)) { return; }
            LocalDate ld = (LocalDate) o;
            if (ld == null) {
                gen.writeStringUnescaped((String) null);
                return;
            }
            char[] buf = new char[10];
            int n = TemporalChars.localDate(buf, 0, ld);
            if (n < 0) {   // year outside 0..9999 — formatter fallback
                gen.writeStringUnescaped(FORMATTER.format(ld));
                return;
            }
            gen.writeStringUnescaped(buf, n);
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class LocalTimeWriter extends PrimitiveTypeWriter {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .toFormatter();

        protected String getKey() {
            return "localTime";
        }

        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            if (writeWithStringFormat(o, gen, context)) { return; }
            if (writeWithFieldFormat(o, gen, context)) { return; }
            LocalTime lt = (LocalTime) o;
            if (lt == null) {
                gen.writeStringUnescaped((String) null);
                return;
            }
            char[] buf = new char[18];
            gen.writeStringUnescaped(buf, TemporalChars.localTime(buf, 0, lt));
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class LocalDateTimeWriter extends PrimitiveTypeWriter {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .toFormatter();

        protected String getKey() {
            return "localDateTime";
        }

        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            if (writeWithStringFormat(o, gen, context)) { return; }
            if (writeWithFieldFormat(o, gen, context)) { return; }
            LocalDateTime ldt = (LocalDateTime) o;
            if (ldt == null) {
                gen.writeStringUnescaped((String) null);
                return;
            }
            char[] buf = new char[29];
            int n = TemporalChars.localDateTime(buf, 0, ldt);
            if (n < 0) {
                gen.writeStringUnescaped(FORMATTER.format(ldt));
                return;
            }
            gen.writeStringUnescaped(buf, n);
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class ZonedDateTimeWriter extends PrimitiveTypeWriter {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .appendLiteral('[')
                .appendZoneId()
                .appendLiteral(']')
                .toFormatter();

        protected String getKey() {
            return "zonedDateTime";
        }

        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            if (writeWithStringFormat(o, gen, context)) { return; }
            if (writeWithFieldFormat(o, gen, context)) { return; }
            ZonedDateTime zdt = (ZonedDateTime) o;
            if (zdt == null) {
                gen.writeString(null);
                return;
            }
            // If it's UTC/Z, convert to explicit UTC zone
            if (zdt.getZone().equals(ZoneOffset.UTC) || zdt.getZone().getId().equals("Z")) {
                zdt = zdt.withZoneSameInstant(ZoneId.of("UTC"));
            }
            // IANA zone ids ([A-Za-z0-9_/+-]) and the ISO offset form never need escaping
            char[] buf = new char[40 + zdt.getZone().getId().length()];
            int n = TemporalChars.zonedDateTime(buf, 0, zdt);
            if (n < 0) {
                gen.writeStringUnescaped(FORMATTER.format(zdt));
                return;
            }
            gen.writeStringUnescaped(buf, n);
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class YearMonthWriter extends PrimitiveTypeWriter {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .appendValue(YEAR, 4, 19, SignStyle.EXCEEDS_PAD)  // Support negative years and up to 19 digits
                .appendLiteral('-')
                .appendValue(MONTH_OF_YEAR, 2)
                .toFormatter();

        protected String getKey() {
            return "yearMonth";
        }

        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            YearMonth ym = (YearMonth) o;
            gen.writeStringUnescaped(ym == null ? null : FORMATTER.format(ym));
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class MonthDayWriter extends PrimitiveTypeWriter {
        protected String getKey() {
            return "monthDay";
        }

        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            MonthDay md = (MonthDay) o;
            gen.writeStringUnescaped(md == null ? null : md.toString());
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class OffsetTimeWriter extends PrimitiveTypeWriter {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_OFFSET_TIME)
                .toFormatter();

        protected String getKey() {
            return "offsetTime";
        }

        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            if (writeWithStringFormat(o, gen, context)) { return; }
            if (writeWithFieldFormat(o, gen, context)) { return; }
            OffsetTime ot = (OffsetTime) o;
            if (ot == null) {
                gen.writeStringUnescaped((String) null);
                return;
            }
            char[] buf = new char[28];
            gen.writeStringUnescaped(buf, TemporalChars.offsetTime(buf, 0, ot));
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class OffsetDateTimeWriter extends PrimitiveTypeWriter {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toFormatter();

        protected String getKey() {
            return "offsetDateTime";
        }

        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            if (writeWithStringFormat(o, gen, context)) { return; }
            if (writeWithFieldFormat(o, gen, context)) { return; }
            OffsetDateTime odt = (OffsetDateTime) o;
            if (odt == null) {
                gen.writeStringUnescaped((String) null);
                return;
            }
            char[] buf = new char[39];
            int n = TemporalChars.offsetDateTime(buf, 0, odt);
            if (n < 0) {
                gen.writeStringUnescaped(FORMATTER.format(odt));
                return;
            }
            gen.writeStringUnescaped(buf, n);
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class InstantWriter extends PrimitiveTypeWriter {
        protected String getKey() {
            return "instant";
        }

        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            if (writeWithStringFormat(o, gen, context)) { return; }
            String pat = (context != null) ? context.getFieldFormatPattern() : null;
            if (pat != null) {
                DateTimeFormatter fmt = getFormatter(pat).withZone(ZoneOffset.UTC);
                gen.writeString(fmt.format((Instant) o));
                return;
            }
            Instant instant = (Instant) o;
            if (instant == null) {
                gen.writeStringUnescaped((String) null);
                return;
            }
            char[] buf = new char[31];
            int n = TemporalChars.instant(buf, 0, instant);
            if (n < 0) {   // year outside 0..9999 — Instant.toString fallback
                gen.writeStringUnescaped(instant.toString());
                return;
            }
            gen.writeStringUnescaped(buf, n);
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class ZoneOffsetWriter extends PrimitiveTypeWriter {
        protected String getKey() {
            return "zoneOffset";
        }

        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            ZoneOffset zo = (ZoneOffset) o;
            gen.writeStringUnescaped(zo == null ? null : zo.toString());
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class DurationWriter extends PrimitiveTypeWriter {
        public String getKey() {
            return "duration";
        }

        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            Duration d = (Duration) o;
            gen.writeStringUnescaped(d == null ? null : d.toString());
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class PeriodWriter extends PrimitiveTypeWriter {
        public String getKey() {
            return "period";
        }

        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            Period p = (Period) o;
            gen.writeStringUnescaped(p == null ? null : p.toString());
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class YearWriter extends PrimitiveTypeWriter {
        protected String getKey() {
            return "year";
        }

        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            gen.writeStringUnescaped(Converter.convert(o, String.class));
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class TimestampWriter extends PrimitiveTypeWriter {
        protected String getKey() {
            return "timestamp";
        }

        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            gen.writeStringUnescaped(Converter.convert(o, String.class));
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class JsonStringWriter extends PrimitiveUtf8StringWriter {
    }

    public static class LocaleWriter extends PrimitiveTypeWriter {
        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            Locale locale = (Locale) o;
            gen.writeStringUnescaped(locale.toLanguageTag());
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class BigIntegerWriter extends PrimitiveTypeWriter {
        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            if (writeWithStringFormat(o, gen, context)) { return; }
            if (writeNumericWithFieldFormat(o, gen, context)) { return; }
            BigInteger big = (BigInteger) o;
            // Emit as a quoted JSON string (not a number literal) so JS / Jackson clients
            // that store numbers as doubles do not lose precision on 19+ digit BigInteger.
            gen.writeStringUnescaped(big.toString(10));
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class PatternWriter extends PrimitiveTypeWriter {
        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            Pattern pattern = (Pattern) o;
            gen.writeString(pattern.pattern());
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class CurrencyWriter extends PrimitiveTypeWriter {
        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            Currency currency = (Currency) o;
            gen.writeStringUnescaped(currency.getCurrencyCode());
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class BigDecimalWriter extends PrimitiveTypeWriter {
        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            if (writeWithStringFormat(o, gen, context)) { return; }
            if (writeNumericWithFieldFormat(o, gen, context)) { return; }
            BigDecimal big = (BigDecimal) o;
            // Emit as a quoted JSON string (not a number literal) so JS / Jackson clients
            // that store numbers as doubles do not lose precision on arbitrary-scale BigDecimal.
            gen.writeStringUnescaped(big.toPlainString());
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }

    public static class UUIDWriter extends PrimitiveTypeWriter {
        public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
            UUID uuid = (UUID) o;
            gen.writeStringUnescaped(uuid.toString());
        }

        @Override
        @Deprecated
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            WriteOptions options = (context != null) ? context.getWriteOptions() : null;
            JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(output, options);
            writePrimitiveForm(o, bridge, context);
        }
    }
}