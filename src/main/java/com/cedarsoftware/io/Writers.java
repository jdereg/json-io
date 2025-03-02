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
import java.util.Currency;
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
            if (context.getWriteOptions().isAllowNanAndInfinity() || !isNanOrInfinity((T) o)) {
                output.write(o.toString());
            } else {
                output.write("null");
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

        public void writePrimitiveForm(Object o, Writer output, WriterContext writerContext) throws IOException {
            JsonWriter.writeJsonUtf8String(output, extractString(o));
        }
    }

    /**
     * Used as a template to write out primitive String types.
     * Uses default key of "value" and encodes the string.
     */
    public static class CharacterWriter extends PrimitiveTypeWriter {
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            JsonWriter.writeJsonUtf8String(output, "" + (char) o);
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

    public static class CalendarWriter implements JsonWriter.JsonClassWriter {
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

    public static class DateWriter implements JsonWriter.JsonClassWriter {
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            if (o instanceof java.sql.Date) {
                // Write just the date portion - no time, no timezone
                String formatted = ((java.sql.Date) o).toLocalDate().toString();
                JsonWriter.writeBasicString(output, formatted);
            } else {
                // Regular Date uses the converter's string format
                String formatted = Converter.convert(o, String.class);
                JsonWriter.writeBasicString(output, formatted);
            }
        }

        public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
            if (showType) {
                String key = (obj instanceof java.sql.Date) ? "sqlDate" : "date";
                JsonWriter.writeBasicString(output, key);
                output.write(':');
            }

            writePrimitiveForm(obj, output, context);
        }

        public boolean hasPrimitiveForm(WriterContext context) {
            return true;
        }
    }

    public static class DateAsLongWriter extends DateWriter {
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            if (o instanceof java.sql.Date) {
                // Same pure date format for sql.Date in both writers
                String formatted = ((java.sql.Date) o).toLocalDate().toString();
                JsonWriter.writeBasicString(output, formatted);
            } else {
                // Regular Date uses milliseconds
                output.write(Long.toString(((java.util.Date) o).getTime()));
            }
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

    public static class LocalDateWriter extends PrimitiveTypeWriter {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .toFormatter();

        protected String getKey() {
            return "localDate";
        }

        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            LocalDate ld = (LocalDate) o;
            JsonWriter.writeJsonUtf8String(output, ld == null ? null : FORMATTER.format(ld));
        }
    }

    public static class LocalTimeWriter extends PrimitiveTypeWriter {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .toFormatter();

        protected String getKey() {
            return "localTime";
        }

        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            LocalTime lt = (LocalTime) o;
            JsonWriter.writeJsonUtf8String(output, lt == null ? null : FORMATTER.format(lt));
        }
    }

    public static class LocalDateTimeWriter extends PrimitiveTypeWriter {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .toFormatter();

        protected String getKey() {
            return "localDateTime";
        }

        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            LocalDateTime ldt = (LocalDateTime) o;
            JsonWriter.writeJsonUtf8String(output, ldt == null ? null : FORMATTER.format(ldt));
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

        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            ZonedDateTime zdt = (ZonedDateTime) o;
            if (zdt == null) {
                JsonWriter.writeBasicString(output, null);
                return;
            }
            // If it's UTC/Z, convert to explicit UTC zone
            if (zdt.getZone().equals(ZoneOffset.UTC) || zdt.getZone().getId().equals("Z")) {
                zdt = zdt.withZoneSameInstant(ZoneId.of("UTC"));
            }
            JsonWriter.writeJsonUtf8String(output, FORMATTER.format(zdt));
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

        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            YearMonth ym = (YearMonth) o;
            JsonWriter.writeJsonUtf8String(output, ym == null ? null : FORMATTER.format(ym));
        }
    }

    public static class MonthDayWriter extends PrimitiveTypeWriter {
        protected String getKey() {
            return "monthDay";
        }

        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            MonthDay md = (MonthDay) o;
            JsonWriter.writeJsonUtf8String(output, md == null ? null : md.toString());
        }
    }

    public static class OffsetTimeWriter extends PrimitiveTypeWriter {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_OFFSET_TIME)
                .toFormatter();

        protected String getKey() {
            return "offsetTime";
        }

        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            OffsetTime ot = (OffsetTime) o;
            JsonWriter.writeJsonUtf8String(output, ot == null ? null : FORMATTER.format(ot));
        }
    }

    public static class OffsetDateTimeWriter extends PrimitiveTypeWriter {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toFormatter();

        protected String getKey() {
            return "offsetDateTime";
        }

        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            OffsetDateTime odt = (OffsetDateTime) o;
            JsonWriter.writeJsonUtf8String(output, odt == null ? null : FORMATTER.format(odt));
        }
    }

    public static class InstantWriter extends PrimitiveTypeWriter {
        protected String getKey() {
            return "instant";
        }

        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            Instant instant = (Instant) o;
            JsonWriter.writeJsonUtf8String(output, instant == null ? null : instant.toString());
        }
    }

    public static class ZoneOffsetWriter extends PrimitiveTypeWriter {
        protected String getKey() {
            return "zoneOffset";
        }

        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            ZoneOffset zo = (ZoneOffset) o;
            JsonWriter.writeJsonUtf8String(output, zo == null ? null : zo.toString());
        }
    }

    public static class DurationWriter extends PrimitiveTypeWriter {
        public String getKey() {
            return "duration";
        }

        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            Duration d = (Duration) o;
            JsonWriter.writeJsonUtf8String(output, d == null ? null : d.toString());
        }
    }

    public static class PeriodWriter extends PrimitiveTypeWriter {
        public String getKey() {
            return "period";
        }

        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            Period p = (Period) o;
            JsonWriter.writeJsonUtf8String(output, p == null ? null : p.toString());
        }
    }

    public static class YearWriter extends PrimitiveTypeWriter {
        protected String getKey() {
            return "year";
        }

        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            String formatted = Converter.convert(o, String.class);
            JsonWriter.writeJsonUtf8String(output, formatted);
        }
    }

    public static class TimestampWriter extends PrimitiveTypeWriter {
        protected String getKey() {
            return "timestamp";
        }

        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            String formatted = Converter.convert(o, String.class);
            JsonWriter.writeJsonUtf8String(output, formatted);
        }
    }

    public static class JsonStringWriter extends PrimitiveUtf8StringWriter {
    }

    public static class LocaleWriter extends PrimitiveTypeWriter {
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            Locale locale = (Locale) o;
            JsonWriter.writeJsonUtf8String(output, locale.toLanguageTag());
        }
    }

    public static class BigIntegerWriter extends PrimitiveTypeWriter {
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            BigInteger big = (BigInteger) o;
            JsonWriter.writeBasicString(output, big.toString(10));
        }
    }

    public static class PatternWriter extends PrimitiveTypeWriter {
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            Pattern pattern = (Pattern) o;
            JsonWriter.writeJsonUtf8String(output, pattern.pattern());
        }
    }

    public static class CurrencyWriter extends PrimitiveTypeWriter {
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            Currency currency = (Currency) o;
            JsonWriter.writeJsonUtf8String(output, currency.getCurrencyCode());
        }
    }

    public static class BigDecimalWriter extends PrimitiveTypeWriter {
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            BigDecimal big = (BigDecimal) o;
            JsonWriter.writeBasicString(output, big.toPlainString());
        }
    }

    public static class UUIDWriter extends PrimitiveTypeWriter {
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            UUID uuid = (UUID) o;
            JsonWriter.writeBasicString(output, uuid.toString());
        }
    }
}