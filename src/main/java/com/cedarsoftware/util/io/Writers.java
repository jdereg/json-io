package com.cedarsoftware.util.io;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
    
    public static class TimeZoneWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, final StringBuilder output) throws IOException
        {
            TimeZone cal = (TimeZone) obj;
            output.append("\"zone\":\"");
            output.append(cal.getID());
            output.append('"');
        }

        @Override
        public boolean hasPrimitiveForm() { return false; }
        @Override
        public void writePrimitiveForm(Object o, final StringBuilder output) throws IOException {}
    }

    public static class CalendarWriter implements JsonWriter.JsonClassWriter
    {
        @Override
		  public void write(Object obj, boolean showType, final StringBuilder output) throws IOException
        {
            Calendar cal = (Calendar) obj;
            MetaUtils.dateFormat.get().setTimeZone(cal.getTimeZone());
            output.append("\"time\":\"");
            output.append(MetaUtils.dateFormat.get().format(cal.getTime()));
            output.append("\",\"zone\":\"");
            output.append(cal.getTimeZone().getID());
            output.append('"');
        }

        @Override
		  public boolean hasPrimitiveForm() { return false; }
        @Override
		  public void writePrimitiveForm(Object o, final StringBuilder output) throws IOException {}
    }

    public static class DateWriter implements JsonWriter.JsonClassWriter, JsonWriter.JsonClassWriterEx
    {
        @Override
        public void write(Object obj, boolean showType, final StringBuilder output) throws IOException
        {
            throw new JsonIoException("Should never be called.");
        }

        @Override
        public void write(Object obj, boolean showType, final StringBuilder output, Map args) throws IOException
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
                output.append("\"value\":");
            }

            if (dateFormat instanceof Format)
            {
                output.append("\"");
                output.append(((Format) dateFormat).format(date));
                output.append("\"");
            }
            else
            {
                output.append(Long.toString(((Date) obj).getTime()));
            }
        }

        @Override
        public boolean hasPrimitiveForm() { return true; }

        @Override
        public void writePrimitiveForm(Object o, final StringBuilder output) throws IOException
        {
            throw new JsonIoException("Should never be called.");
        }

        public void writePrimitiveForm(Object o, final StringBuilder output, Map args) throws IOException
        {
            if (args.containsKey(DATE_FORMAT))
            {
                write(o, false, output, args);
            }
            else
            {
                output.append(Long.toString(((Date) o).getTime()));
            }
        }
    }

    public static class TimestampWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object o, boolean showType, final StringBuilder output) throws IOException
        {
            Timestamp tstamp = (Timestamp) o;
            output.append("\"time\":\"");
            output.append(Long.toString((tstamp.getTime() / 1000) * 1000));
            output.append("\",\"nanos\":\"");
            output.append(Integer.toString(tstamp.getNanos()));
            output.append('"');
        }

        @Override
        public boolean hasPrimitiveForm() { return false; }

        @Override
        public void writePrimitiveForm(Object o, final StringBuilder output) throws IOException { }
    }

    public static class ClassWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, final StringBuilder output) throws IOException
        {
            String value = ((Class) obj).getName();
            output.append("\"value\":");
            writeJsonUtf8String(value, output);
        }

        @Override
		  public boolean hasPrimitiveForm() { return true; }

        @Override
        public void writePrimitiveForm(Object o, final StringBuilder output) throws IOException
        {
            writeJsonUtf8String(((Class)o).getName(), output);
        }
    }

    public static class JsonStringWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, final StringBuilder output) throws IOException
        {
            output.append("\"value\":");
            writeJsonUtf8String((String) obj, output);
        }

        @Override
        public boolean hasPrimitiveForm() { return true; }

        @Override
        public void writePrimitiveForm(Object o, final StringBuilder output) throws IOException
        {
            writeJsonUtf8String((String) o, output);
        }
    }

    public static class LocaleWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, final StringBuilder output) throws IOException
        {
            Locale locale = (Locale) obj;

            output.append("\"language\":\"");
            output.append(locale.getLanguage());
            output.append("\",\"country\":\"");
            output.append(locale.getCountry());
            output.append("\",\"variant\":\"");
            output.append(locale.getVariant());
            output.append('"');
        }
        @Override
        public boolean hasPrimitiveForm() { return false; }
        @Override
        public void writePrimitiveForm(Object o, final StringBuilder output) throws IOException { }
    }

    public static class BigIntegerWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, final StringBuilder output) throws IOException
        {
            if (showType)
            {
                BigInteger big = (BigInteger) obj;
                output.append("\"value\":\"");
                output.append(big.toString(10));
                output.append('"');
            }
            else
            {
                writePrimitiveForm(obj, output);
            }
        }

        @Override
        public boolean hasPrimitiveForm() { return true; }

        @Override
        public void writePrimitiveForm(Object o, final StringBuilder output) throws IOException
        {
            BigInteger big = (BigInteger) o;
            output.append('"');
            output.append(big.toString(10));
            output.append('"');
        }
    }

    public static class AtomicBooleanWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, final StringBuilder output) throws IOException
        {
            if (showType)
            {
                AtomicBoolean value = (AtomicBoolean) obj;
                output.append("\"value\":");
                output.append(value.toString());
            }
            else
            {
                writePrimitiveForm(obj, output);
            }
        }

        @Override
        public boolean hasPrimitiveForm() { return true; }

        @Override
        public void writePrimitiveForm(Object o, final StringBuilder output) throws IOException
        {
            AtomicBoolean value = (AtomicBoolean) o;
            output.append(value.toString());
        }
    }

    public static class AtomicIntegerWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, final StringBuilder output) throws IOException
        {
            if (showType)
            {
                AtomicInteger value = (AtomicInteger) obj;
                output.append("\"value\":");
                output.append(value.toString());
            }
            else
            {
                writePrimitiveForm(obj, output);
            }
        }

        @Override
        public boolean hasPrimitiveForm() { return true; }

        @Override
        public void writePrimitiveForm(Object o, final StringBuilder output) throws IOException
        {
            AtomicInteger value = (AtomicInteger) o;
            output.append(value.toString());
        }
    }

    public static class AtomicLongWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, final StringBuilder output) throws IOException
        {
            if (showType)
            {
                AtomicLong value = (AtomicLong) obj;
                output.append("\"value\":");
                output.append(value.toString());
            }
            else
            {
                writePrimitiveForm(obj, output);
            }
        }

        @Override
        public boolean hasPrimitiveForm() { return true; }

        @Override
        public void writePrimitiveForm(Object o, final StringBuilder output) throws IOException
        {
            AtomicLong value = (AtomicLong) o;
            output.append(value.toString());
        }
    }

    public static class BigDecimalWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, final StringBuilder output) throws IOException
        {
            if (showType)
            {
                BigDecimal big = (BigDecimal) obj;
                output.append("\"value\":\"");
                output.append(big.toPlainString());
                output.append('"');
            }
            else
            {
                writePrimitiveForm(obj, output);
            }
        }

        @Override
        public boolean hasPrimitiveForm() { return true; }

        @Override
        public void writePrimitiveForm(Object o, final StringBuilder output) throws IOException
        {
            BigDecimal big = (BigDecimal) o;
            output.append('"');
            output.append(big.toPlainString());
            output.append('"');
        }
    }

    public static class StringBuilderWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, final StringBuilder output) throws IOException
        {
            StringBuilder builder = (StringBuilder) obj;
            output.append("\"value\":\"");
            output.append(builder.toString());
            output.append('"');
        }

        @Override
        public boolean hasPrimitiveForm() { return true; }

        @Override
        public void writePrimitiveForm(Object o, final StringBuilder output) throws IOException
        {
            StringBuilder builder = (StringBuilder) o;
            output.append('"');
            output.append(builder.toString());
            output.append('"');
        }
    }

    public static class StringBufferWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, final StringBuilder output) throws IOException
        {
            StringBuffer buffer = (StringBuffer) obj;
            output.append("\"value\":\"");
            output.append(buffer.toString());
            output.append('"');
        }

        @Override
        public boolean hasPrimitiveForm() { return true; }

        @Override
        public void writePrimitiveForm(Object o, final StringBuilder output) throws IOException
        {
            StringBuffer buffer = (StringBuffer) o;
            output.append('"');
            output.append(buffer.toString());
            output.append('"');
        }
    }

    // ========== Maintain knowledge about relationships below this line ==========
    static final String DATE_FORMAT = JsonWriter.DATE_FORMAT;

    protected static void writeJsonUtf8String(String s, final StringBuilder output) throws IOException
    {
        JsonWriter.writeJsonUtf8String(s, output);
    }
}
