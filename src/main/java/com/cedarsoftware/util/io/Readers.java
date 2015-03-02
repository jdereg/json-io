package com.cedarsoftware.util.io;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * All special readers for json-io are stored here.  Special readers are not needed for handling
 * user-defined classes.  However, special readers are built/supplied by json-io for many of the
 * primitive types and other JDK classes simply to allow for a more concise form.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.*
 */
public class Readers
{
    private static final String days = "(monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thur|thu|friday|fri|saturday|sat|sunday|sun)"; // longer before shorter matters
    private static final String mos = "(January|Jan|February|Feb|March|Mar|April|Apr|May|June|Jun|July|Jul|August|Aug|September|Sept|Sep|October|Oct|November|Nov|December|Dec)";
    private static final Pattern datePattern1 = Pattern.compile("(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})");
    private static final Pattern datePattern2 = Pattern.compile("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})");
    private static final Pattern datePattern3 = Pattern.compile(mos + "[ ]*[,]?[ ]*(\\d{1,2})(st|nd|rd|th|)[ ]*[,]?[ ]*(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern datePattern4 = Pattern.compile("(\\d{1,2})(st|nd|rd|th|)[ ]*[,]?[ ]*" + mos + "[ ]*[,]?[ ]*(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern datePattern5 = Pattern.compile("(\\d{4})[ ]*[,]?[ ]*" + mos + "[ ]*[,]?[ ]*(\\d{1,2})(st|nd|rd|th|)", Pattern.CASE_INSENSITIVE);
    private static final Pattern datePattern6 = Pattern.compile(days + "[ ]+" + mos + "[ ]+(\\d{1,2})[ ]+(\\d{2}:\\d{2}:\\d{2})[ ]+[A-Z]{1,3}\\s+(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern timePattern1 = Pattern.compile("(\\d{2})[.:](\\d{2})[.:](\\d{2})[.](\\d{1,10})([+-]\\d{2}[:]?\\d{2}|Z)?");
    private static final Pattern timePattern2 = Pattern.compile("(\\d{2})[.:](\\d{2})[.:](\\d{2})([+-]\\d{2}[:]?\\d{2}|Z)?");
    private static final Pattern timePattern3 = Pattern.compile("(\\d{2})[.:](\\d{2})([+-]\\d{2}[:]?\\d{2}|Z)?");
    private static final Pattern dayPattern = Pattern.compile(days, Pattern.CASE_INSENSITIVE);
    private static ErrorHandler errorHandler;
    private static final Map<String, String> months = new LinkedHashMap<>();

    static
    {
        // Month name to number map
        months.put("jan", "1");
        months.put("january", "1");
        months.put("feb", "2");
        months.put("february", "2");
        months.put("mar", "3");
        months.put("march", "3");
        months.put("apr", "4");
        months.put("april", "4");
        months.put("may", "5");
        months.put("jun", "6");
        months.put("june", "6");
        months.put("jul", "7");
        months.put("july", "7");
        months.put("aug", "8");
        months.put("august", "8");
        months.put("sep", "9");
        months.put("sept", "9");
        months.put("september", "9");
        months.put("oct", "10");
        months.put("october", "10");
        months.put("nov", "11");
        months.put("november", "11");
        months.put("dec", "12");
        months.put("december", "12");
    }

    public static void setErrorHandler(ErrorHandler handler)
    {
        errorHandler = handler;
    }

    public static class TimeZoneReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack) throws IOException
        {
            JsonObject jObj = (JsonObject)o;
            Object zone = jObj.get("zone");
            if (zone == null)
            {
                errorHandler.error("java.util.TimeZone must specify 'zone' field");
            }
            return jObj.target = TimeZone.getTimeZone((String) zone);
        }
    }

    public static class LocaleReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack) throws IOException
        {
            JsonObject jObj = (JsonObject) o;
            Object language = jObj.get("language");
            if (language == null)
            {
                errorHandler.error("java.util.Locale must specify 'language' field");
            }
            Object country = jObj.get("country");
            Object variant = jObj.get("variant");
            if (country == null)
            {
                return jObj.target = new Locale((String) language);
            }
            if (variant == null)
            {
                return jObj.target = new Locale((String) language, (String) country);
            }

            return jObj.target = new Locale((String) language, (String) country, (String) variant);
        }
    }

    public static class CalendarReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack) throws IOException
        {
            String time = null;
            try
            {
                JsonObject jObj = (JsonObject) o;
                time = (String) jObj.get("time");
                if (time == null)
                {
                    errorHandler.error("Calendar missing 'time' field");
                }
                Date date = MetaUtils.dateFormat.get().parse(time);
                Class c;
                if (jObj.getTarget() != null)
                {
                    c = jObj.getTarget().getClass();
                }
                else
                {
                    Object type = jObj.type;
                    c = JsonReader.classForName((String) type);
                }

                Calendar calendar = (Calendar) JsonReader.newInstance(c);
                calendar.setTime(date);
                jObj.setTarget(calendar);
                String zone = (String) jObj.get("zone");
                if (zone != null)
                {
                    calendar.setTimeZone(TimeZone.getTimeZone(zone));
                }
                return calendar;
            }
            catch(Exception e)
            {
                return errorHandler.error("Failed to parse calendar, time: " + time);
            }
        }
    }

    public static class DateReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack) throws IOException
        {
            if (o instanceof Long)
            {
                return new Date((Long) o);
            }
            else if (o instanceof String)
            {
                return parseDate((String) o);
            }
            else if (o instanceof JsonObject)
            {
                JsonObject jObj = (JsonObject) o;
                Object val = jObj.get("value");
                if (val instanceof Long)
                {
                    return new Date((Long) val);
                }
                else if (val instanceof String)
                {
                    return parseDate((String) val);
                }
                return errorHandler.error("Unable to parse date: " + o);
            }
            else
            {
                return errorHandler.error("Unable to parse date, encountered unknown object: " + o);
            }
        }

        private static Date parseDate(String dateStr) throws IOException
        {
            if (dateStr == null)
            {
                return null;
            }
            dateStr = dateStr.trim();
            if (dateStr.isEmpty())
            {
                return null;
            }

            // Determine which date pattern (Matcher) to use
            Matcher matcher = datePattern1.matcher(dateStr);

            String year, month = null, day, mon = null, remains;

            if (matcher.find())
            {
                year = matcher.group(1);
                month = matcher.group(2);
                day = matcher.group(3);
                remains = matcher.replaceFirst("");
            }
            else
            {
                matcher = datePattern2.matcher(dateStr);
                if (matcher.find())
                {
                    month = matcher.group(1);
                    day = matcher.group(2);
                    year = matcher.group(3);
                    remains = matcher.replaceFirst("");
                }
                else
                {
                    matcher = datePattern3.matcher(dateStr);
                    if (matcher.find())
                    {
                        mon = matcher.group(1);
                        day = matcher.group(2);
                        year = matcher.group(4);
                        remains = matcher.replaceFirst("");
                    }
                    else
                    {
                        matcher = datePattern4.matcher(dateStr);
                        if (matcher.find())
                        {
                            day = matcher.group(1);
                            mon = matcher.group(3);
                            year = matcher.group(4);
                            remains = matcher.replaceFirst("");
                        }
                        else
                        {
                            matcher = datePattern5.matcher(dateStr);
                            if (matcher.find())
                            {
                                year = matcher.group(1);
                                mon = matcher.group(2);
                                day = matcher.group(3);
                                remains = matcher.replaceFirst("");
                            }
                            else
                            {
                                matcher = datePattern6.matcher(dateStr);
                                if (!matcher.find())
                                {
                                    errorHandler.error("Unable to parse: " + dateStr);
                                }
                                year = matcher.group(5);
                                mon = matcher.group(2);
                                day = matcher.group(3);
                                remains = matcher.group(4);
                            }
                        }
                    }
                }
            }

            if (mon != null)
            {   // Month will always be in Map, because regex forces this.
                month = months.get(mon.trim().toLowerCase());
            }

            // Determine which date pattern (Matcher) to use
            String hour = null, min = null, sec = "00", milli = "0", tz = null;
            remains = remains.trim();
            matcher = timePattern1.matcher(remains);
            if (matcher.find())
            {
                hour = matcher.group(1);
                min = matcher.group(2);
                sec = matcher.group(3);
                milli = matcher.group(4);
                if (matcher.groupCount() > 4)
                {
                    tz = matcher.group(5);
                }
            }
            else
            {
                matcher = timePattern2.matcher(remains);
                if (matcher.find())
                {
                    hour = matcher.group(1);
                    min = matcher.group(2);
                    sec = matcher.group(3);
                    if (matcher.groupCount() > 3)
                    {
                        tz = matcher.group(4);
                    }
                }
                else
                {
                    matcher = timePattern3.matcher(remains);
                    if (matcher.find())
                    {
                        hour = matcher.group(1);
                        min = matcher.group(2);
                        if (matcher.groupCount() > 2)
                        {
                            tz = matcher.group(3);
                        }
                    }
                    else
                    {
                        matcher = null;
                    }
                }
            }

            if (matcher != null)
            {
                remains = matcher.replaceFirst("");
            }

            // Clear out day of week (mon, tue, wed, ...)
            if (remains != null && remains.length() > 0)
            {
                Matcher dayMatcher = dayPattern.matcher(remains);
                if (dayMatcher.find())
                {
                    remains = dayMatcher.replaceFirst("").trim();
                }
            }
            if (remains != null && remains.length() > 0)
            {
                remains = remains.trim();
                if (!remains.equals(",") && (!remains.equals("T")))
                {
                    errorHandler.error("Issue parsing data/time, other characters present: " + remains);
                }
            }

            Calendar c = Calendar.getInstance();
            c.clear();
            if (tz != null)
            {
                if ("z".equalsIgnoreCase(tz))
                {
                    c.setTimeZone(TimeZone.getTimeZone("GMT"));
                }
                else
                {
                    c.setTimeZone(TimeZone.getTimeZone("GMT" + tz));
                }
            }

            // Regex prevents these from ever failing to parse
            int y = Integer.parseInt(year);
            int m = Integer.parseInt(month) - 1;    // months are 0-based
            int d = Integer.parseInt(day);

            if (m < 0 || m > 11)
            {
                errorHandler.error("Month must be between 1 and 12 inclusive, date: " + dateStr);
            }
            if (d < 1 || d > 31)
            {
                errorHandler.error("Day must be between 1 and 31 inclusive, date: " + dateStr);
            }

            if (matcher == null)
            {   // no [valid] time portion
                c.set(y, m, d);
            }
            else
            {
                // Regex prevents these from ever failing to parse.
                int h = Integer.parseInt(hour);
                int mn = Integer.parseInt(min);
                int s = Integer.parseInt(sec);
                int ms = Integer.parseInt(milli);

                if (h > 23)
                {
                    errorHandler.error("Hour must be between 0 and 23 inclusive, time: " + dateStr);
                }
                if (mn > 59)
                {
                    errorHandler.error("Minute must be between 0 and 59 inclusive, time: " + dateStr);
                }
                if (s > 59)
                {
                    errorHandler.error("Second must be between 0 and 59 inclusive, time: " + dateStr);
                }

                // regex enforces millis to number
                c.set(y, m, d, h, mn, s);
                c.set(Calendar.MILLISECOND, ms);
            }
            return c.getTime();
        }
    }

    public static class SqlDateReader extends DateReader
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack) throws IOException
        {
            return new java.sql.Date(((Date) super.read(o, stack)).getTime());
        }
    }

    public static class StringReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack) throws IOException
        {
            if (o instanceof String)
            {
                return o;
            }

            if (MetaUtils.isPrimitive(o.getClass()))
            {
                return o.toString();
            }

            JsonObject jObj = (JsonObject) o;
            if (jObj.containsKey("value"))
            {
                return jObj.target = jObj.get("value");
            }
            return errorHandler.error("String missing 'value' field");
        }
    }

    public static class ClassReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack) throws IOException
        {
            if (o instanceof String)
            {
                return JsonReader.classForName((String) o);
            }

            JsonObject jObj = (JsonObject) o;
            if (jObj.containsKey("value"))
            {
                return jObj.target = JsonReader.classForName((String) jObj.get("value"));
            }
            return errorHandler.error("Class missing 'value' field");
        }
    }

    public static class BigIntegerReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack) throws IOException
        {
            JsonObject jObj = null;
            Object value = o;
            if (o instanceof JsonObject)
            {
                jObj = (JsonObject) o;
                if (jObj.containsKey("value"))
                {
                    value = jObj.get("value");
                }
                else
                {
                    return errorHandler.error("BigInteger missing 'value' field");
                }
            }

            if (value instanceof JsonObject)
            {
                JsonObject valueObj = (JsonObject)value;
                if ("java.math.BigDecimal".equals(valueObj.type))
                {
                    BigDecimalReader reader = new BigDecimalReader();
                    value = reader.read(value, stack);
                }
                else if ("java.math.BigInteger".equals(valueObj.type))
                {
                    value = read(value, stack);
                }
                else
                {
                    return bigIntegerFrom(valueObj.get("value"));
                }
            }

            BigInteger x = bigIntegerFrom(value);
            if (jObj != null)
            {
                jObj.target = x;
            }

            return x;
        }
    }

    /**
     * @return a BigInteger from the given input.  A best attempt will be made to support
     * as many input types as possible.  For example, if the input is a Boolean, a BigInteger of
     * 1 or 0 will be returned.  If the input is a String "", a null will be returned.  If the
     * input is a Double, Float, or BigDecimal, a BigInteger will be returned that retains the
     * integer portion (fractional part is dropped).  The input can be a Byte, Short, Integer,
     * or Long.
     * @throws IOException if the input is something that cannot be converted to a BigInteger.
     */
    public static BigInteger bigIntegerFrom(Object value) throws IOException
    {
        if (value == null)
        {
            return null;
        }
        else if (value instanceof BigInteger)
        {
            return (BigInteger) value;
        }
        else if (value instanceof String)
        {
            String s = (String) value;
            if ("".equals(s.trim()))
            {   // Allows "" to be used to assign null to BigInteger field.
                return null;
            }
            try
            {
                return new BigInteger(MetaUtils.removeLeadingAndTrailingQuotes(s));
            }
            catch (Exception e)
            {
                return (BigInteger) errorHandler.error("Could not parse '" + value + "' as BigInteger.", e);
            }
        }
        else if (value instanceof BigDecimal)
        {
            BigDecimal bd = (BigDecimal) value;
            return bd.toBigInteger();
        }
        else if (value instanceof Boolean)
        {
            return (Boolean) value ? BigInteger.ONE : BigInteger.ZERO;
        }
        else if (value instanceof Double || value instanceof Float)
        {
            return new BigDecimal(((Number)value).doubleValue()).toBigInteger();
        }
        else if (value instanceof Long || value instanceof Integer ||
                value instanceof Short || value instanceof Byte)
        {
            return new BigInteger(value.toString());
        }
        return (BigInteger) errorHandler.error("Could not convert value: " + value.toString() + " to BigInteger.");
    }

    public static class BigDecimalReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack) throws IOException
        {
            JsonObject jObj = null;
            Object value = o;
            if (o instanceof JsonObject)
            {
                jObj = (JsonObject) o;
                if (jObj.containsKey("value"))
                {
                    value = jObj.get("value");
                }
                else
                {
                    return errorHandler.error("BigDecimal missing 'value' field");
                }
            }

            if (value instanceof JsonObject)
            {
                JsonObject valueObj = (JsonObject)value;
                if ("java.math.BigInteger".equals(valueObj.type))
                {
                    BigIntegerReader reader = new BigIntegerReader();
                    value = reader.read(value, stack);
                }
                else if ("java.math.BigDecimal".equals(valueObj.type))
                {
                    value = read(value, stack);
                }
                else
                {
                    return bigDecimalFrom(valueObj.get("value"));
                }
            }

            BigDecimal x = bigDecimalFrom(value);
            if (jObj != null)
            {
                jObj.target = x;
            }
            return x;
        }
    }

    /**
     * @return a BigDecimal from the given input.  A best attempt will be made to support
     * as many input types as possible.  For example, if the input is a Boolean, a BigDecimal of
     * 1 or 0 will be returned.  If the input is a String "", a null will be returned. The input
     * can be a Byte, Short, Integer, Long, or BigInteger.
     * @throws IOException if the input is something that cannot be converted to a BigDecimal.
     */
    public static BigDecimal bigDecimalFrom(Object value) throws IOException
    {
        if (value == null)
        {
            return null;
        }
        else if (value instanceof BigDecimal)
        {
            return (BigDecimal) value;
        }
        else if (value instanceof String)
        {
            String s = (String) value;
            if ("".equals(s.trim()))
            {
                return null;
            }
            try
            {
                return new BigDecimal(MetaUtils.removeLeadingAndTrailingQuotes(s));
            }
            catch (Exception e)
            {
                return (BigDecimal) errorHandler.error("Could not parse '" + s + "' as BigDecimal.", e);
            }
        }
        else if (value instanceof BigInteger)
        {
            return new BigDecimal((BigInteger) value);
        }
        else if (value instanceof Boolean)
        {
            return (Boolean) value ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        else if (value instanceof Long || value instanceof Integer || value instanceof Double ||
                value instanceof Short || value instanceof Byte || value instanceof Float)
        {
            return new BigDecimal(value.toString());
        }
        return (BigDecimal) errorHandler.error("Could not convert value: " + value.toString() + " to BigInteger.");
    }

    public static class StringBuilderReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack) throws IOException
        {
            if (o instanceof String)
            {
                return new StringBuilder((String) o);
            }

            JsonObject jObj = (JsonObject) o;
            if (jObj.containsKey("value"))
            {
                return jObj.target = new StringBuilder((String) jObj.get("value"));
            }
            return errorHandler.error("StringBuilder missing 'value' field");
        }
    }

    public static class StringBufferReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack) throws IOException
        {
            if (o instanceof String)
            {
                return new StringBuffer((String) o);
            }

            JsonObject jObj = (JsonObject) o;
            if (jObj.containsKey("value"))
            {
                return jObj.target = new StringBuffer((String) jObj.get("value"));
            }
            return errorHandler.error("StringBuffer missing 'value' field");
        }
    }

    public static class TimestampReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack) throws IOException
        {
            JsonObject jObj = (JsonObject) o;
            Object time = jObj.get("time");
            if (time == null)
            {
                errorHandler.error("java.sql.Timestamp must specify 'time' field");
            }
            Object nanos = jObj.get("nanos");
            if (nanos == null)
            {
                return jObj.target = new Timestamp(Long.valueOf((String) time));
            }

            Timestamp tstamp = new Timestamp(Long.valueOf((String) time));
            tstamp.setNanos(Integer.valueOf((String) nanos));
            return jObj.target = tstamp;
        }
    }
}
