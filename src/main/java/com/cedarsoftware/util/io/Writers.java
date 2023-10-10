package com.cedarsoftware.util.io;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

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
 *         http://www.apache.org/licenses/LICENSE-2.0
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

    public static class PrimitiveTypeWriter implements JsonWriter.JsonClassWriter
    {
        protected String getKey() { return "value"; }

        @Override
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            if (showType)
            {
                output.write('\"');
                output.write(getKey());
                output.write("\":");
            }

            writePrimitiveForm(obj, output);
        }

        @Override
        public boolean hasPrimitiveForm() { return true; }
    }


    /**
     * Used as a template to write out value types such as int, boolean, etc.
     * Uses the default key of "value"
     */
    public static class PrimitiveValueWriter extends PrimitiveTypeWriter
    {
        public String extractString(Object o) { return o.toString(); }

        @Override
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


    public static class URLWriter extends PrimitiveUtf8StringWriter {}

    public static class TimeZoneWriter extends PrimitiveUtf8StringWriter
    {
        @Override
        protected String getKey() {
           return "zone";
        }

        @Override
        public String extractString(Object o) { return ((TimeZone)o).getID(); }
    }

    public static class ClassWriter extends PrimitiveUtf8StringWriter
    {
        @Override
        public String extractString(Object o) { return ((Class)o).getName(); }
    }

    public static class EnumAsPrimitiveWriter extends PrimitiveUtf8StringWriter
    {
        @Override
        protected String getKey() {
            return "name";
        }
        @Override
        public String extractString(Object o) { return ((Enum)o).name(); }
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

    public static class DateWriter implements JsonWriter.JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer output, Map args) throws IOException
        {
            Date date = (Date)obj;
            Object dateFormat = args.get(DATE_FORMAT);
            if (dateFormat instanceof String)
            {   // Passed in as String, turn into a SimpleDateFormat instance to be used throughout this stream write.
                dateFormat = new SimpleDateFormat((String) dateFormat, Locale.ENGLISH);
                args.put(DATE_FORMAT, dateFormat);
            }
            if (showType)
            {
                output.write("\"value\":");
            }

            if (dateFormat instanceof Format)
            {
                output.write("\"");
                output.write(((Format) dateFormat).format(date));
                output.write("\"");
            }
            else
            {
                output.write(Long.toString(((Date) obj).getTime()));
            }
        }

        public boolean hasPrimitiveForm() { return true; }

        public void writePrimitiveForm(Object o, Writer output, Map args) throws IOException
        {
            if (args.containsKey(DATE_FORMAT))
            {
                write(o, false, output, args);
            }
            else
            {
                output.write(Long.toString(((Date) o).getTime()));
            }
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

    public static class DefaultEnumWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, Writer output, Map<String, Object> args) throws IOException
        {
            output.write("\"name\":");
            writeJsonUtf8String(((Enum)obj).name(), output);
            JsonWriter writer = getWriter(args);
            writer.writeObject(obj, true, true, Set.of("name", "ordinal"));
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
            output.write('"');
            output.write(big.toString(10));
            output.write('"');
        }
    }

    public static class AtomicBooleanWriter extends PrimitiveValueWriter {}

    public static class AtomicIntegerWriter extends PrimitiveValueWriter {}

    public static class AtomicLongWriter extends PrimitiveValueWriter {}

    public static class BigDecimalWriter extends PrimitiveValueWriter
    {
        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            BigDecimal big = (BigDecimal) o;
            output.write('"');
            output.write(big.toPlainString());
            output.write('"');
        }
    }

    public static class StringBuilderWriter extends PrimitiveUtf8StringWriter {}

    public static class StringBufferWriter extends PrimitiveUtf8StringWriter {}

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
            output.write('"');
            output.write(buffer.toString());
            output.write('"');
        }
    }

    // ========== Maintain knowledge about relationships below this line ==========
    static final String DATE_FORMAT = JsonWriter.DATE_FORMAT;

    protected static void writeJsonUtf8String(String s, final Writer output) throws IOException
    {
        JsonWriter.writeJsonUtf8String(s, output);
    }
}
