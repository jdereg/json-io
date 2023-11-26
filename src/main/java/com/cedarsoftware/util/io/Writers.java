package com.cedarsoftware.util.io;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import com.cedarsoftware.util.io.factory.YearMonthFactory;

import static com.cedarsoftware.util.io.JsonIo.writeBasicString;

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
 *         limitations under the License.*
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

        @Override
        public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException
        {
            if (showType)
            {
                writeBasicString(output, getKey());
                output.write(':');
            }

            writePrimitiveForm(obj, output, context);
        }

        @Override
        public boolean hasPrimitiveForm() { return true; }
    }

    /**
     * Used for Native JSON primitives that never need to write their type.
     */
    public static class NativeJsonPrimitive implements JsonWriter.JsonClassWriter {
        @Override
        public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
            writePrimitiveForm(obj, output, context);
        }

        @Override
        public boolean hasPrimitiveForm() {
            return true;
        }
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
        public void writePrimitiveForm(Object o, Writer output) throws IOException {
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

        @Override
        public void writePrimitiveForm(Object o, Writer output) throws IOException {
            JsonIo.writeJsonUtf8String(output, extractString(o));
        }
    }

    /**
     * Used as a template to write out primitive String types.
     * Uses default key of "value" and encodes the string.
     */
    public static class CharacterWriter extends PrimitiveTypeWriter {

        @Override
        public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
            JsonIo.writeJsonUtf8String(output, "" + (char) o);
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
        public void writePrimitiveForm(Object o, Writer output) throws IOException {
            JsonIo.writeBasicString(output, extractString(o));
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

    public static class ZoneOffsetWriter extends PrimitiveUtf8StringWriter {

        @Override
        public String extractString(Object o) {
            return ((ZoneOffset) o).getId();
        }
    }

    public static class CalendarWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            Calendar cal = (Calendar) obj;
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
        @Override
        public void writePrimitiveForm(Object o, Writer output) throws IOException {
            LocalDate localDate = (LocalDate) o;
            output.write(Long.toString(localDate.toEpochDay()));
        }
    }

    public static class TemporalWriter<T extends TemporalAccessor> extends PrimitiveTypeWriter {
        protected final DateTimeFormatter formatter;

        public TemporalWriter(DateTimeFormatter formatter) {
            this.formatter = formatter;
        }

        @SuppressWarnings("unchecked")
        public void writePrimitiveForm(Object obj, Writer output) throws IOException {
            this.writePrimitiveForm((T) obj, output);
        }

        protected void writePrimitiveForm(T temporal, Writer output) throws IOException {
            writeBasicString(output, this.formatter.format(temporal));
        }

    }

    public static class LocalDateWriter extends TemporalWriter<LocalDate>
    {
        public LocalDateWriter(DateTimeFormatter formatter) {
            super(formatter);
        }

        public LocalDateWriter() {
            this(DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    public static class LocalTimeWriter extends TemporalWriter<LocalTime> {
        public LocalTimeWriter(DateTimeFormatter formatter) {
            super(formatter);
        }

        public LocalTimeWriter() {
            this(DateTimeFormatter.ISO_LOCAL_TIME);
        }
    }

    public static class LocalDateTimeWriter extends TemporalWriter<LocalDateTime> {
        public LocalDateTimeWriter(DateTimeFormatter formatter) {
            super(formatter);
        }

        public LocalDateTimeWriter() {
            this(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    public static class ZonedDateTimeWriter extends TemporalWriter<ZonedDateTime> {
        public ZonedDateTimeWriter(DateTimeFormatter formatter) {
            super(formatter);
        }

        public ZonedDateTimeWriter() {
            this(DateTimeFormatter.ISO_ZONED_DATE_TIME);
        }
    }

    public static class YearMonthWriter extends TemporalWriter<YearMonth> {

        public YearMonthWriter(DateTimeFormatter formatter) {
            super(formatter);
        }

        public YearMonthWriter() {
            this(YearMonthFactory.FORMATTER);
        }
    }

    public static class YearWriter extends PrimitiveValueWriter {
        @Override
        public String extractString(Object o) {
            return Integer.toString(((Year) o).getValue());
        }
    }

    public static class OffsetTimeWriter extends TemporalWriter<OffsetTime> {

        public OffsetTimeWriter(DateTimeFormatter formatter) {
            super(formatter);
        }

        public OffsetTimeWriter() {
            this(DateTimeFormatter.ISO_OFFSET_TIME);
        }
    }

    public static class OffsetDateTimeWriter extends TemporalWriter<OffsetDateTime> {

        public OffsetDateTimeWriter(DateTimeFormatter formatter) {
            super(formatter);
        }

        public OffsetDateTimeWriter() {
            this(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }

    public static class TimestampWriter implements JsonWriter.JsonClassWriter
    {
        public void write(Object o, boolean showType, Writer output) throws IOException
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

    public static class LocaleWriter implements JsonWriter.JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            Locale locale = (Locale) obj;

            output.write("\"language\":\"");
            output.write(locale.getLanguage());
            output.write("\",\"country\":\"");
            output.write(locale.getCountry());
            output.write("\",\"variant\":\"");
            output.write(locale.getVariant());
            output.write('"');
        }
    }

    public static class BigIntegerWriter extends PrimitiveValueWriter
    {
        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            BigInteger big = (BigInteger) o;
            writeBasicString(output, big.toString(10));
        }
    }

    public static class BigDecimalWriter extends PrimitiveValueWriter
    {
        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            BigDecimal big = (BigDecimal) o;
            writeBasicString(output, big.toPlainString());
        }
    }

    public static class UUIDWriter implements JsonWriter.JsonClassWriter
    {
        /**
         * To preserve backward compatibility with previous serialized format the internal fields must be stored as longs
         */
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            UUID uuid = (UUID) obj;
            output.write("\"mostSigBits\": ");
            output.write(Long.toString(uuid.getMostSignificantBits()));
            output.write(",\"leastSigBits\":");
            output.write(Long.toString(uuid.getLeastSignificantBits()));
        }

        public boolean hasPrimitiveForm() { return true; }

        /**
         * We can use the String representation for easier handling, but this may break backwards compatibility
         * if an earlier library version is used
         */
        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            UUID buffer = (UUID) o;
            writeBasicString(output, buffer.toString());
        }
    }
}
