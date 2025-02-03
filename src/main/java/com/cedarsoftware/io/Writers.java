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
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import com.cedarsoftware.util.Converter;

import static com.cedarsoftware.io.JsonValue.VALUE;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
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

    /**
     * Used as a template to write out types that will have a primitive form.
     * Uses the default key of "value" unless overridden
     */
    public static class PrimitiveTypeWriter implements JsonWriter.JsonClassWriter {
        protected String getKey() {
            return VALUE;
        }


        public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
            if (showType) {
                JsonWriter.writeBasicString(output, getKey());
                output.write(':');
            }

            writePrimitiveForm(obj, output, context);
        }

        @Override
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
         * Writes out a basic value type, no quotes.  to write strings use PrimitiveUtf8StringWriter.
         */
        @Override
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            output.write(extractString(o));
        }
    }

    /**
     * Used as a template to write out primitive value types such as int, boolean, etc. that we extract as a String,
     * but we do not put in quotes.  Uses the default key of "value" unless overridden
     */
    public abstract static class FloatingPointWriter<T> extends PrimitiveTypeWriter {
        /**
         * Writes out Float point type.
         */
        @SuppressWarnings("unchecked")
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            if (!context.getWriteOptions().isAllowNanAndInfinity() && isNanOrInfinity((T) o)) {
                output.write("null");
            } else {
                output.write(o.toString());
            }
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
        @Override
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
        @Override
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
            return o.toString();
        }

        public void writePrimitiveForm(Object o, Writer output, WriterContext writerContext) throws IOException {
            JsonWriter.writeJsonUtf8String(output, extractString(o));
        }
    }

    /**
     * Used as a template to write out primitive String types.
     * Uses default key of "value" and encodes the string.
     */
    public static class CharacterWriter extends PrimitiveTypeWriter {

        @Override
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            JsonWriter.writeJsonUtf8String(output, "" + (char) o);
        }
    }

    public static class TimeZoneWriter extends PrimitiveUtf8StringWriter {
        @Override
        public String extractString(Object o) {
            return ((TimeZone) o).getID();
        }
    }

    public static class ClassWriter extends PrimitiveUtf8StringWriter {
        @Override
        public String extractString(Object o) {
            return ((Class<?>) o).getName();
        }
    }

    public static class EnumsAsStringWriter extends PrimitiveUtf8StringWriter {
        @Override
        protected String getKey() {
            return "name";
        }

        @Override
        public String extractString(Object o) {
            return ((Enum<?>) o).name();
        }
    }

    public static class CalendarWriter implements JsonWriter.JsonClassWriter {
        @Override
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            String formatted = Converter.convert(o, String.class);
            JsonWriter.writeBasicString(output, formatted);
        }

        public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
            if (showType) {
                JsonWriter.writeBasicString(output, "calendar");
                output.write(':');
            }

            writePrimitiveForm(obj, output, context);
        }

        public boolean hasPrimitiveForm(WriterContext context) {
            return true;
        }
    }

    public static class DateAsLongWriter extends DateWriter {
        @Override
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            String formatted;
            if (o instanceof java.sql.Date) {
                // Just use the date's built-in toString - it's already in JDBC format
                formatted = o.toString();
                JsonWriter.writeBasicString(output, formatted);
            } else {
                formatted = Long.toString(((java.util.Date) o).getTime());
                output.write(formatted);
            }
        }
    }
    
    public static class DateWriter implements JsonWriter.JsonClassWriter {
        @Override
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            String formatted = Converter.convert(o, String.class);
            JsonWriter.writeBasicString(output, formatted);
        }

        public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
            if (showType) {
                String key;
                if (obj instanceof java.sql.Date) {
                    key = "sqlDate";
                } else {
                    key = "date";
                }
                JsonWriter.writeBasicString(output, key);
                output.write(':');
            }

            writePrimitiveForm(obj, output, context);
        }

        public boolean hasPrimitiveForm(WriterContext context) {
            return true;
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

        public void writePrimitiveForm(Object o, Writer output, WriterContext writerContext) throws IOException {
            LocalDate localDate = (LocalDate) o;
            ZonedDateTime zonedDateTime = localDate.atStartOfDay(zoneId);

            // Convert LocalDateTime to Instant using UTC offset
            Instant instant = zonedDateTime.toInstant();

            // Get epoch milliseconds from the Instant
            long epochMilli = instant.toEpochMilli();
            output.write(Long.toString(epochMilli));
        }
    }

    public abstract static class TemporalWriter<T extends TemporalAccessor> extends PrimitiveTypeWriter {
        protected abstract DateTimeFormatter getFormatter();

        @SuppressWarnings("unchecked")
        public void writePrimitiveForm(Object obj, Writer output, WriterContext writerContext) throws IOException {
            this.writePrimitiveForm((T) obj, output);
        }

        protected void writePrimitiveForm(T temporal, Writer output) throws IOException {
            JsonWriter.writeBasicString(output, getFormatter().format(temporal));
        }
    }

    public static class LocalDateWriter extends TemporalWriter<LocalDate> {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .toFormatter();

        @Override
        protected DateTimeFormatter getFormatter() {
            return FORMATTER;
        }

        @Override
        protected String getKey() {
            return "localDate";
        }
    }

    public static class LocalTimeWriter extends TemporalWriter<LocalTime> {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .toFormatter();

        @Override
        protected DateTimeFormatter getFormatter() {
            return FORMATTER;
        }

        @Override
        protected String getKey() {
            return "localTime";
        }
    }

    public static class LocalDateTimeWriter extends TemporalWriter<LocalDateTime> {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .toFormatter();

        @Override
        protected DateTimeFormatter getFormatter() {
            return FORMATTER;
        }

        @Override
        protected String getKey() {
            return "localDateTime";
        }
    }

    public static class ZonedDateTimeWriter extends TemporalWriter<ZonedDateTime> {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .appendLiteral('[')
                .appendZoneId()
                .appendLiteral(']')
                .toFormatter();

        @Override
        protected DateTimeFormatter getFormatter() {
            return FORMATTER;
        }

        @Override
        protected String getKey() {
            return "zonedDateTime";
        }

        @Override
        protected void writePrimitiveForm(ZonedDateTime temporal, Writer output) throws IOException {
            // If it's UTC/Z, convert to explicit UTC zone
            if (temporal.getZone().equals(ZoneOffset.UTC) || temporal.getZone().getId().equals("Z")) {
                temporal = temporal.withZoneSameInstant(ZoneId.of("UTC"));
            }
            JsonWriter.writeBasicString(output, this.getFormatter().format(temporal));
        }
    }

    public static class YearMonthWriter extends TemporalWriter<YearMonth> {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .appendValue(YEAR, 4, 19, SignStyle.EXCEEDS_PAD)  // Support negative years and up to 19 digits
                .appendLiteral('-')
                .appendValue(MONTH_OF_YEAR, 2)
                .toFormatter();

        @Override
        protected DateTimeFormatter getFormatter() {
            return FORMATTER;
        }

        @Override
        protected String getKey() {
            return "yearMonth";
        }
    }

    public static class MonthDayWriter extends TemporalWriter<MonthDay> {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .appendLiteral("--")
                .appendValue(MONTH_OF_YEAR, 2)
                .appendLiteral('-')
                .appendValue(DAY_OF_MONTH, 2)
                .toFormatter();

        @Override
        protected DateTimeFormatter getFormatter() {
            return FORMATTER;
        }

        @Override
        protected String getKey() {
            return "monthDay";
        }
    }
    
    public static class OffsetTimeWriter extends TemporalWriter<OffsetTime> {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_OFFSET_TIME)
                .toFormatter();

        @Override
        protected DateTimeFormatter getFormatter() {
            return FORMATTER;
        }

        @Override
        protected String getKey() {
            return "offsetTime";
        }
    }
    
    public static class OffsetDateTimeWriter extends TemporalWriter<OffsetDateTime> {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toFormatter();

        @Override
        protected DateTimeFormatter getFormatter() {
            return FORMATTER;
        }

        @Override
        protected String getKey() {
            return "offsetDateTime";
        }
    }

    public static class InstantWriter extends TemporalWriter<Instant> {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_INSTANT)
                .toFormatter();

        @Override
        protected DateTimeFormatter getFormatter() {
            return FORMATTER;
        }

        @Override
        protected String getKey() {
            return "instant";
        }
    }

    public static class ZoneOffsetWriter extends TemporalWriter<ZoneOffset> {
        @Override
        protected DateTimeFormatter getFormatter() {
            return null;  // Not used for ZoneOffset
        }

        @Override
        protected String getKey() {
            return "zoneOffset";
        }

        @Override
        protected void writePrimitiveForm(ZoneOffset offset, Writer output) throws IOException {
            JsonWriter.writeBasicString(output, offset.getId());  // Uses ISO-8601 format
        }
    }
    
    public static class DurationWriter implements JsonWriter.JsonClassWriter {
        public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
            Duration duration = (Duration) obj;
            output.write("\"seconds\":");
            output.write("" + duration.getSeconds());
            output.write(",\"nanos\":");
            output.write("" + duration.getNano());
        }
    }

    public static class PeriodWriter extends Writers.PrimitiveUtf8StringWriter {
        public String getKey() {
            return "period";
        }

        @Override
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            Period d = (Period) o;
            JsonWriter.writeBasicString(output, d.toString());
        }
    }

    public static class YearWriter extends TemporalWriter<Year> {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .appendValue(YEAR, 4, 19, SignStyle.EXCEEDS_PAD)  // Support negative years and up to 19 digits
                .toFormatter();

        @Override
        protected DateTimeFormatter getFormatter() {
            return FORMATTER;
        }

        @Override
        protected String getKey() {
            return "year";
        }
    }

    public static class TimestampWriter implements JsonWriter.JsonClassWriter {
        @Override
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            String formatted = Converter.convert(o, String.class);
            JsonWriter.writeBasicString(output, formatted);
        }

        public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
            if (showType) {
                JsonWriter.writeBasicString(output, "timestamp");
                output.write(':');
            }

            writePrimitiveForm(obj, output, context);
        }

        public boolean hasPrimitiveForm(WriterContext context) {
            return true;
        }
    }

    public static class JsonStringWriter extends PrimitiveUtf8StringWriter {
    }

    public static class LocaleWriter extends PrimitiveValueWriter {
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            Locale locale = (Locale) o;
            JsonWriter.writeBasicString(output, locale.toLanguageTag());
        }
    }

    public static class BigIntegerWriter extends PrimitiveValueWriter {
        @Override
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            BigInteger big = (BigInteger) o;
            JsonWriter.writeBasicString(output, big.toString(10));
        }
    }

    public static class BigDecimalWriter extends PrimitiveValueWriter {
        @Override
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            BigDecimal big = (BigDecimal) o;
            JsonWriter.writeBasicString(output, big.toPlainString());
        }
    }

    public static class UUIDWriter implements JsonWriter.JsonClassWriter {
        /**
         * To preserve backward compatibility with previous serialized format the internal fields must be stored as longs
         */
        @Override
        public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
            UUID uuid = (UUID) obj;
            output.write("\"value\":\"");
            output.write(uuid.toString());
            output.write("\"");
        }

        @Override
        public boolean hasPrimitiveForm(WriterContext writerContext) {
            return true;
        }

        /**
         * We can use the String representation for easier handling, but this may break backwards compatibility
         * if an earlier library version is used
         */
        @Override
        public void writePrimitiveForm(Object o, Writer writer, WriterContext writerContext) throws IOException {
            UUID buffer = (UUID) o;
            JsonWriter.writeBasicString(writer, buffer.toString());
        }
    }
}