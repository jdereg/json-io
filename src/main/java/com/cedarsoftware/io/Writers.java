package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

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
public class Writers
{
    private Writers () {}

    /**
     * Used as a template to write out types that will have a primitive form.
     * Uses the default key of "value" unless overridden
     */
    public static class PrimitiveTypeWriter implements JsonWriter.JsonClassWriter
    {
        protected String getKey() { return "value"; }


        public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException
        {
            if (showType)
            {
                JsonWriter.writeBasicString(output, getKey());
                output.write(':');
            }

            writePrimitiveForm(obj, output, context);
        }

        @Override
        public boolean hasPrimitiveForm(WriterContext writerContext) { return true; }
    }

    /**
     * Used as a template to write out primitive value types such as int, boolean, etc. that we extract as a String,
     * but we do not put in quotes.  Uses the default key of "value" unless overridden
     */
    public static class PrimitiveValueWriter extends PrimitiveTypeWriter
    {
        public String extractString(Object o) { return o.toString(); }


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
    public static class PrimitiveUtf8StringWriter extends PrimitiveTypeWriter
    {
        public String extractString(Object o) { return o.toString(); }

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

    /**
     * This can be used when you know your objects are going to be represented as strings,
     * but won't need any UTF-8 escaping.  This saves a little time on the write.
     */
    public static class PrimitiveBasicStringWriter extends PrimitiveTypeWriter {
        public String extractString(Object o) {
            return o.toString();
        }

        @Override
        public void writePrimitiveForm(Object o, Writer output, WriterContext writerContext) throws IOException {
            JsonWriter.writeBasicString(output, extractString(o));
        }
    }

    public static class TimeZoneWriter extends PrimitiveUtf8StringWriter
    {
        @Override
        public String extractString(Object o) { return ((TimeZone)o).getID(); }
    }

    public static class ClassWriter extends PrimitiveUtf8StringWriter
    {
        @Override
        public String extractString(Object o) { return ((Class<?>)o).getName(); }
    }

    public static class EnumsAsStringWriter extends PrimitiveUtf8StringWriter
    {
        @Override
        protected String getKey() {
            return "name";
        }

        @Override
        public String extractString(Object o) { return ((Enum<?>)o).name(); }
    }

    public static class CalendarWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException
        {
            Calendar cal = (Calendar) obj;
            // TODO:  shouldn't this be the one inside the WriterContext?  and shouldn't there be a back up of parseDate() here?
            MetaUtils.dateFormat.get().setTimeZone(cal.getTimeZone());
            output.write("\"time\":\"");
            output.write(MetaUtils.dateFormat.get().format(cal.getTime()));
            output.write("\",\"zone\":\"");
            output.write(cal.getTimeZone().getID());
            output.write('"');
        }
    }

    public static class DateAsLongWriter extends PrimitiveValueWriter {
        @Override
        public String extractString(Object o) {
            return Long.toString(((Date) o).getTime());
        }
    }

    public static class DateWriter extends PrimitiveUtf8StringWriter {
        // could change to DateFormatter.ofPattern to keep from creating new objects
        private final String dateFormat;

        public DateWriter(String format) {
            this.dateFormat = format;
        }

        public String extractString(Object o) {
            Date date = (Date) o;
            return new SimpleDateFormat(dateFormat).format(date);
        }
        
        String getDateFormat()
        {
            return dateFormat;
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

            //TODO:  Change to using converter and having the writeOptions provide a zoneId;
            //TODO:  If we're going to provide a LocalDateAsLong we should also provide a LocalDateTimeAsLong
            LocalDate localDate = (LocalDate) o;
            ZonedDateTime zonedDateTime = localDate.atStartOfDay(zoneId);

            // Convert LocalDateTime to Instant using UTC offset
            Instant instant = zonedDateTime.toInstant();

            // Get epoch milliseconds from the Instant
            long epochMilli = instant.toEpochMilli();
            output.write(Long.toString(epochMilli));
        }
    }

    public static class TemporalWriter<T extends TemporalAccessor> extends PrimitiveTypeWriter {
        protected DateTimeFormatter formatter;

        public void setFormatter(DateTimeFormatter formatter) {
            this.formatter = formatter;
        }

        @SuppressWarnings("unchecked")
        public void writePrimitiveForm(Object obj, Writer output, WriterContext writerContext) throws IOException {
            this.writePrimitiveForm((T) obj, output);
        }

        protected void writePrimitiveForm(T temporal, Writer output) throws IOException {
            JsonWriter.writeBasicString(output, this.formatter.format(temporal));
        }
    }

    public static class LocalDateWriter extends TemporalWriter<LocalDate> {
        public LocalDateWriter() {
            setFormatter(DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    public static class LocalTimeWriter extends TemporalWriter<LocalTime> {
        public LocalTimeWriter() {
            setFormatter(DateTimeFormatter.ISO_LOCAL_TIME);
        }
    }

    public static class LocalDateTimeWriter extends TemporalWriter<LocalDateTime> {
        public LocalDateTimeWriter() {
            setFormatter(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    public static class ZonedDateTimeWriter extends TemporalWriter<ZonedDateTime> {
        public ZonedDateTimeWriter() {
            setFormatter(DateTimeFormatter.ISO_ZONED_DATE_TIME);
        }
    }

    public static class YearMonthWriter extends TemporalWriter<YearMonth> {

        public static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
                .appendLiteral('-')
                .appendValue(MONTH_OF_YEAR, 2)
                .toFormatter();

        public YearMonthWriter() {
            setFormatter(FORMATTER);
        }
    }

    public static class MonthDayWriter extends TemporalWriter<YearMonth> {

        public static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .appendValue(MONTH_OF_YEAR, 2)
                .appendLiteral('-')
                .appendValue(DAY_OF_MONTH, 2)
                .toFormatter();

        public MonthDayWriter() {
            setFormatter(FORMATTER);
        }
    }

    public static class YearWriter extends PrimitiveValueWriter {

        @Override
        public String extractString(Object o) {
            return Integer.toString(((Year) o).getValue());
        }
    }

    public static class OffsetTimeWriter extends TemporalWriter<OffsetTime> {
        public OffsetTimeWriter() {
            setFormatter(DateTimeFormatter.ISO_OFFSET_TIME);
        }
    }

    public static class OffsetDateTimeWriter extends TemporalWriter<OffsetDateTime> {
        public OffsetDateTimeWriter() {
            setFormatter(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }

    public static class TimestampWriter implements JsonWriter.JsonClassWriter
    {
        public void write(Object o, boolean showType, Writer output, WriterContext writerContext) throws IOException
        {
            Timestamp tstamp = (Timestamp) o;
            output.write("\"time\":\"");
            output.write(Long.toString((tstamp.getTime() / 1000) * 1000));
            output.write("\",\"nanos\":\"");
            output.write(Integer.toString(tstamp.getNanos()));
            output.write('"');
        }
    }

    public static class JsonStringWriter extends PrimitiveUtf8StringWriter {}

    public static class LocaleWriter extends PrimitiveValueWriter
    {
//        public void write(Object obj, boolean showType, Writer output) throws IOException
//        {
//            Locale locale = (Locale) obj;
//
//            output.write("\"language\":\"");
//            output.write(locale.getLanguage());
//            output.write("\",\"country\":\"");
//            output.write(locale.getCountry());
//            output.write("\",\"variant\":\"");
//            output.write(locale.getVariant());
//            output.write('"');
//        }

        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException
        {
            Locale locale = (Locale) o;
            JsonWriter.writeBasicString(output, locale.toLanguageTag());
        }
    }

    public static class BigIntegerWriter extends PrimitiveValueWriter
    {
        @Override
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException
        {
            BigInteger big = (BigInteger) o;
            JsonWriter.writeBasicString(output, big.toString(10));
        }
    }

    public static class BigDecimalWriter extends PrimitiveValueWriter
    {
        @Override
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException
        {
            BigDecimal big = (BigDecimal) o;
            JsonWriter.writeBasicString(output, big.toPlainString());
        }
    }

    public static class UUIDWriter implements JsonWriter.JsonClassWriter
    {
        /**
         * To preserve backward compatibility with previous serialized format the internal fields must be stored as longs
         */
        @Override
        public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException
        {
            UUID uuid = (UUID) obj;
            output.write("\"value\":\"");
            output.write(uuid.toString());
            output.write("\"");
        }

        @Override
        public boolean hasPrimitiveForm(WriterContext writerContext) { return true; }

        /**
         * We can use the String representation for easier handling, but this may break backwards compatibility
         * if an earlier library version is used
         */
        @Override
        public void writePrimitiveForm(Object o, Writer writer, WriterContext writerContext) throws IOException
        {
            UUID buffer = (UUID) o;
            JsonWriter.writeBasicString(writer, buffer.toString());
        }
    }
}
