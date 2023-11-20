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
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import com.cedarsoftware.util.io.factory.YearMonthFactory;

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
        public void write(Object obj, boolean showType, Writer output, Map args) throws IOException
        {
            if (showType)
            {
                writeBasicString(output, getKey());
                output.write(':');
            }

            writePrimitiveForm(obj, output, args);
        }

        @Override
        public boolean hasPrimitiveForm() { return true; }
    }


    /**
     * Used as a template to write out primitive value types such as int, boolean, etc. that we extract as a String,
     * but we do not put in quotes.  Uses the default key of "value" unless overridden
     */
    public static class PrimitiveValueWriter extends PrimitiveTypeWriter
    {
        public String extractString(Object o) { return o.toString(); }

        @Override
        /**
         * Writes out a basic value type, no quotes.  to write strings use PrimitiveUtf8StringWriter.
         */
        public void writePrimitiveForm(Object o, Writer output) throws IOException {
            output.write(extractString(o));
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
            writeJsonUtf8String(extractString(o), output);
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
        public String extractString(Object o) { return ((Class)o).getName(); }
    }

    public static class EnumsAsStringWriter extends PrimitiveUtf8StringWriter
    {
        @Override
        protected String getKey() {
            return "name";
        }
        @Override
        public String extractString(Object o) { return ((Enum)o).name(); }
    }

    public static class ZoneOffsetWriter extends PrimitiveUtf8StringWriter {

        @Override
        public String extractString(Object o) {
            return ((ZoneOffset) o).getId();
        }
    }

    public static class CalendarWriter implements JsonWriter.JsonClassWriter
    {
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

    public static class DateAsTimestampWriter extends PrimitiveValueWriter {
        @Override
        public String extractString(Object o) {
            return Long.toString(((Date) o).getTime());
        }
    }

    public static class DateWriter extends PrimitiveUtf8StringWriter
    {
        // could change to DateFormatter.ofPattern to keep from creating new objects
        private final String dateFormat;

        public DateWriter(String format) {
            this.dateFormat = format;
        }

        public DateWriter() {
            this(JsonWriter.ISO_DATE_TIME_FORMAT);
        }

        @Override
        public String extractString(Object o) {
            Date date = (Date) o;
            return new SimpleDateFormat(dateFormat).format(date);
        }
    }

    public static class LocalDateAsTimestamp extends PrimitiveTypeWriter {
        @Override
        public void writePrimitiveForm(Object o, Writer output, Map args) throws IOException {
            LocalDate localDate = (LocalDate) o;
            output.write(Long.toString(localDate.toEpochDay()));
        }
    }

    public static class TemporalWriter<T extends TemporalAccessor> extends PrimitiveTypeWriter {
        protected final DateTimeFormatter formatter;

        public TemporalWriter(DateTimeFormatter formatter) {
            this.formatter = formatter;
        }

        @Override
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


    public static class EnumAsObjectWriter implements JsonWriter.JsonClassWriter {
        // putting here to allow this to be the full enum object writer.
        // write now we're just calling back to the JsonWriter
        @Override
        public void write(Object obj, boolean showType, Writer output, Map<String, Object> args) throws IOException
        {
            output.write("\"name\":");
            writeJsonUtf8String(((Enum)obj).name(), output);
            JsonWriter writer = getWriter(args);
            writer.writeObject(obj, true, true, new HashSet<>());
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

    // ========== Maintain knowledge about relationships below this line ==========
    protected static void writeJsonUtf8String(String s, final Writer output) throws IOException
    {
        JsonWriter.writeJsonUtf8String(s, output);
    }

    protected static void writeBasicString(Writer output, String string) throws IOException {
        JsonWriter.writeJsonUtf8String(string, output);
    }
}
