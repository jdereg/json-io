package com.cedarsoftware.util.io;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * All custom readers for json-io subclass this class.  Special readers are not needed for handling
 * user-defined classes.  However, special readers are built/supplied by json-io for many of the
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
public class Readers
{
    private Readers () {}
    
    private static final String DAYS = "(monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thur|thu|friday|fri|saturday|sat|sunday|sun)"; // longer before shorter matters
    private static final String MOS = "(January|Jan|February|Feb|March|Mar|April|Apr|May|June|Jun|July|Jul|August|Aug|September|Sept|Sep|October|Oct|November|Nov|December|Dec)";
    private static final Pattern datePattern1 = Pattern.compile("(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})");
    private static final Pattern datePattern2 = Pattern.compile("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})");
    private static final Pattern datePattern3 = Pattern.compile(MOS + "[ ]*[,]?[ ]*(\\d{1,2})(st|nd|rd|th|)[ ]*[,]?[ ]*(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern datePattern4 = Pattern.compile("(\\d{1,2})(st|nd|rd|th|)[ ]*[,]?[ ]*" + MOS + "[ ]*[,]?[ ]*(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern datePattern5 = Pattern.compile("(\\d{4})[ ]*[,]?[ ]*" + MOS + "[ ]*[,]?[ ]*(\\d{1,2})(st|nd|rd|th|)", Pattern.CASE_INSENSITIVE);
    private static final Pattern datePattern6 = Pattern.compile(DAYS + "[ ]+" + MOS + "[ ]+(\\d{1,2})[ ]+(\\d{2}:\\d{2}:\\d{2})[ ]+[A-Z]{1,4}\\s+(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern timePattern1 = Pattern.compile("(\\d{2})[.:](\\d{2})[.:](\\d{2})[.](\\d{1,10})([+-]\\d{2}[:]?\\d{2}|Z)?");
    private static final Pattern timePattern2 = Pattern.compile("(\\d{2})[.:](\\d{2})[.:](\\d{2})([+-]\\d{2}[:]?\\d{2}|Z)?");
    private static final Pattern timePattern3 = Pattern.compile("(\\d{2})[.:](\\d{2})([+-]\\d{2}[:]?\\d{2}|Z)?");
    private static final Pattern dayPattern = Pattern.compile(DAYS, Pattern.CASE_INSENSITIVE);
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

    public static class URLReader implements JsonReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            boolean isString = o instanceof String;

            try
            {
                if (isString)
                {
                    URI uri = URI.create((String)o);
                    return uri.toURL();
                }

                return createURLFromJsonObject((JsonObject)o);
            }
            catch(MalformedURLException e)
            {
                throw new JsonIoException("java.net.URL malformed URL:  " + ((o instanceof String) ? o : e.getMessage()));
            }
        }

        URL createURLFromJsonObject(JsonObject jObj) throws MalformedURLException {
            if (jObj.containsKey("value")) {
                jObj.target = createUrlNewWay(jObj);
            } else {
                jObj.target = createUrlOldWay(jObj);
            }
            return (URL)jObj.target;
        }

        URL createUrlNewWay(JsonObject jObj) throws MalformedURLException
        {
            return URI.create((String)jObj.get("value")).toURL();
        }

        URL createUrlOldWay(JsonObject jObj) throws MalformedURLException {
            String protocol = (String)jObj.get("protocol");
            String host = (String)jObj.get("host");
            String file = (String)jObj.get("file");
            String authority = (String)jObj.get("authority");
            String ref = (String)jObj.get("ref");
            Long port = (Long)jObj.get("port");

            StringBuilder builder = new StringBuilder(protocol + ":");
            if (!protocol.equalsIgnoreCase("jar")) {
                builder.append("//");
            }
            if (authority != null && !authority.isEmpty()) {
                builder.append(authority);
            } else {
                if (host != null && !host.isEmpty()) {
                    builder.append(host);
                }
                if (!port.equals(-1L)) {
                    builder.append(":" + port);
                }
            }
            if (file != null && !file.isEmpty()) {
                builder.append(file);
            }
            if (ref != null && !ref.isEmpty()) {
                builder.append("#" + ref);
            }
            return new URL(builder.toString());
        }
    }

    public static class EnumReader implements JsonReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            boolean isString = o instanceof String;

            try
            {
                if (isString)
                {
                    return (String)o;
                }

                return createEnumFromJsonObject((JsonObject)o, stack, args);
            }
            catch(Exception e)
            {
                throw new JsonIoException("Exception loading Enum:  " + ((o instanceof String) ? o : e.getMessage()));
            }
        }

        Object createEnumFromJsonObject(JsonObject jObj, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            String type = jObj.type;
            ClassLoader loader = (ClassLoader)args.get(JsonReader.CLASSLOADER);
            Class c = classForName(type, loader);

            Optional<Class> cls = MetaUtils.getClassIfEnum(c, loader);

            jObj.target = Enum.valueOf(cls.orElse(c), (String)jObj.get("name"));

            ObjectResolver resolver = (ObjectResolver) args.get(JsonReader.OBJECT_RESOLVER);
            resolver.traverseFields(stack, (JsonObject<String, Object>) jObj, Set.of("name", "ordinal"));
            Object target = ((JsonObject<String, Object>) jObj).getTarget();
            return target;
        }
    }

    public static class TimeZoneReader implements JsonReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            if (o instanceof String)
            {
                return TimeZone.getTimeZone((String)o);
            }

            JsonObject jObj = (JsonObject)o;
            Object zone = jObj.get("zone");
            if (zone == null)
            {
                throw new JsonIoException("java.util.TimeZone must specify 'zone' field");
            }
            jObj.target = TimeZone.getTimeZone((String) zone);

            return jObj.target;
        }
    }

    public static class LocaleReader implements JsonReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            JsonObject jObj = (JsonObject) o;
            Object language = jObj.get("language");
            if (language == null)
            {
                throw new JsonIoException("java.util.Locale must specify 'language' field");
            }
            Object country = jObj.get("country");
            Object variant = jObj.get("variant");
            if (country == null)
            {
                jObj.target = new Locale((String) language);
                return jObj.target;
            }
            if (variant == null)
            {
                jObj.target = new Locale((String) language, (String) country);
                return jObj.target;
            }

            jObj.target = new Locale((String) language, (String) country, (String) variant);
            return jObj.target;
        }
    }

    public static class CalendarReader implements JsonReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            String time = null;
            try
            {
                JsonObject jObj = (JsonObject) o;
                time = (String) jObj.get("time");
                if (time == null)
                {
                    throw new JsonIoException("Calendar missing 'time' field");
                }
                Date date = MetaUtils.dateFormat.get().parse(time);
                Class<?> c;
                if (jObj.getTarget() != null)
                {
                    c = jObj.getTarget().getClass();
                }
                else
                {
                    Object type = jObj.type;
                    c = classForName((String) type, (ClassLoader)args.get(JsonReader.CLASSLOADER));
                }

                Calendar calendar = (Calendar) newInstance(c, jObj);
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
                throw new JsonIoException("Failed to parse calendar, time: " + time);
            }
        }
    }

    public static class DateReader implements JsonReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
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
                throw new JsonIoException("Unable to parse date: " + o);
            }
            else
            {
                throw new JsonIoException("Unable to parse date, encountered unknown object: " + o);
            }
        }

        static Date parseDate(String dateStr)
        {
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
                                    throw new JsonIoException("Unable to parse: " + dateStr);
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
                    throw new JsonIoException("Issue parsing data/time, other characters present: " + remains);
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
                throw new JsonIoException("Month must be between 1 and 12 inclusive, date: " + dateStr);
            }
            if (d < 1 || d > 31)
            {
                throw new JsonIoException("Day must be between 1 and 31 inclusive, date: " + dateStr);
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
                    throw new JsonIoException("Hour must be between 0 and 23 inclusive, time: " + dateStr);
                }
                if (mn > 59)
                {
                    throw new JsonIoException("Minute must be between 0 and 59 inclusive, time: " + dateStr);
                }
                if (s > 59)
                {
                    throw new JsonIoException("Second must be between 0 and 59 inclusive, time: " + dateStr);
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
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            return new java.sql.Date(((Date) super.read(o, stack, args)).getTime());
        }
    }

    public static class StringReader implements JsonReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
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
                jObj.target = jObj.get("value");
                return jObj.target;
            }
            throw new JsonIoException("String missing 'value' field");
        }
    }

    public static class ClassReader implements JsonReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            if (o instanceof String)
            {
                return classForName((String) o, (ClassLoader)args.get(JsonReader.CLASSLOADER));
            }

            JsonObject jObj = (JsonObject) o;
            if (jObj.containsKey("value"))
            {
                jObj.target = classForName((String) jObj.get("value"), (ClassLoader)args.get(JsonReader.CLASSLOADER));
                return jObj.target;
            }
            throw new JsonIoException("Class missing 'value' field");
        }
    }

    public static class AtomicBooleanReader implements JsonReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            Object value = o;
            value = getValueFromJsonObject(o, value, "AtomicBoolean");

            if (value instanceof String)
            {
                String state = (String) value;
                if ("".equals(state.trim()))
                {   // special case
                    return null;
                }
                return new AtomicBoolean("true".equalsIgnoreCase(state));
            }
            else if (value instanceof Boolean)
            {
                return new AtomicBoolean((Boolean) value);
            }
            else if (value instanceof Number && !(value instanceof Double) && !(value instanceof Float))
            {
                return new AtomicBoolean(((Number)value).longValue() != 0);
            }
            throw new JsonIoException("Unknown value in JSON assigned to AtomicBoolean, value type = " + value.getClass().getName());
        }
    }

    public static class AtomicIntegerReader implements JsonReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            Object value = o;
            value = getValueFromJsonObject(o, value, "AtomicInteger");

            if (value instanceof String)
            {
                String num = (String) value;
                if ("".equals(num.trim()))
                {   // special case
                    return null;
                }
                return new AtomicInteger(Integer.parseInt(MetaUtils.removeLeadingAndTrailingQuotes(num)));
            }
            else if (value instanceof Number && !(value instanceof Double) && !(value instanceof Float))
            {
                return new AtomicInteger(((Number)value).intValue());
            }
            throw new JsonIoException("Unknown value in JSON assigned to AtomicInteger, value type = " + value.getClass().getName());
        }
    }

    public static class AtomicLongReader implements JsonReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            Object value = o;
            value = getValueFromJsonObject(o, value, "AtomicLong");

            if (value instanceof String)
            {
                String num = (String) value;
                if ("".equals(num.trim()))
                {   // special case
                    return null;
                }
                return new AtomicLong(Long.parseLong(MetaUtils.removeLeadingAndTrailingQuotes(num)));
            }
            else if (value instanceof Number && !(value instanceof Double) && !(value instanceof Float))
            {
                return new AtomicLong(((Number)value).longValue());
            }
            throw new JsonIoException("Unknown value in JSON assigned to AtomicLong, value type = " + value.getClass().getName());
        }
    }

    private static Object getValueFromJsonObject(Object o, Object value, String typeName)
    {
        if (o instanceof JsonObject)
        {
            JsonObject jObj = (JsonObject) o;
            if (jObj.containsKey("value"))
            {
                value = jObj.get("value");
            }
            else
            {
                throw new JsonIoException(typeName + " defined as JSON {} object, missing 'value' field");
            }
        }
        return value;
    }

    public static class BigIntegerReader implements JsonReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
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
                    throw new JsonIoException("BigInteger missing 'value' field");
                }
            }

            if (value instanceof JsonObject)
            {
                JsonObject valueObj = (JsonObject)value;
                if ("java.math.BigDecimal".equals(valueObj.type))
                {
                    BigDecimalReader reader = new BigDecimalReader();
                    value = reader.read(value, stack, args);
                }
                else if ("java.math.BigInteger".equals(valueObj.type))
                {
                    value = read(value, stack, args);
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
     * @param value to be converted to BigInteger.  Can be a null which will return null.  Can be
     * BigInteger in which case it will be returned as-is.  Can also be String, BigDecimal,
     * Boolean (which will be returned as BigInteger.ZERO or .ONE), byte, short, int, long, float,
     * or double.  If an unknown type is passed in, an exception will be thrown.
     * @return a BigInteger from the given input.  A best attempt will be made to support
     * as many input types as possible.  For example, if the input is a Boolean, a BigInteger of
     * 1 or 0 will be returned.  If the input is a String "", a null will be returned.  If the
     * input is a Double, Float, or BigDecimal, a BigInteger will be returned that retains the
     * integer portion (fractional part is dropped).  The input can be a Byte, Short, Integer,
     * or Long.
     */
    public static BigInteger bigIntegerFrom(Object value)
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
                throw new JsonIoException("Could not parse '" + value + "' as BigInteger.", e);
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
        throw new JsonIoException("Could not convert value: " + value.toString() + " to BigInteger.");
    }

    public static class BigDecimalReader implements JsonReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
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
                    throw new JsonIoException("BigDecimal missing 'value' field");
                }
            }

            if (value instanceof JsonObject)
            {
                JsonObject valueObj = (JsonObject)value;
                if ("java.math.BigInteger".equals(valueObj.type))
                {
                    BigIntegerReader reader = new BigIntegerReader();
                    value = reader.read(value, stack, args);
                }
                else if ("java.math.BigDecimal".equals(valueObj.type))
                {
                    value = read(value, stack, args);
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
     * @param value to be converted to BigDecimal.  Can be a null which will return null.  Can be
     * BigDecimal in which case it will be returned as-is.  Can also be String, BigInteger,
     * Boolean (which will be returned as BigDecimal.ZERO or .ONE), byte, short, int, long, float,
     * or double.  If an unknown type is passed in, an exception will be thrown.
     *
     * @return a BigDecimal from the given input.  A best attempt will be made to support
     * as many input types as possible.  For example, if the input is a Boolean, a BigDecimal of
     * 1 or 0 will be returned.  If the input is a String "", a null will be returned. The input
     * can be a Byte, Short, Integer, Long, or BigInteger.
     */
    public static BigDecimal bigDecimalFrom(Object value)
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
                throw new JsonIoException("Could not parse '" + s + "' as BigDecimal.", e);
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
        throw new JsonIoException("Could not convert value: " + value.toString() + " to BigInteger.");
    }

    public static class StringBuilderReader implements JsonReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            if (o instanceof String)
            {
                return new StringBuilder((String) o);
            }

            JsonObject jObj = (JsonObject) o;
            if (jObj.containsKey("value"))
            {
                jObj.target = new StringBuilder((String) jObj.get("value"));
                return jObj.target;
            }
            throw new JsonIoException("StringBuilder missing 'value' field");
        }
    }

    public static class StringBufferReader implements JsonReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            if (o instanceof String)
            {
                return new StringBuffer((String) o);
            }

            JsonObject jObj = (JsonObject) o;
            if (jObj.containsKey("value"))
            {
                jObj.target = new StringBuffer((String) jObj.get("value"));
                return jObj.target;
            }
            throw new JsonIoException("StringBuffer missing 'value' field");
        }
    }

    public static class TimestampReader implements JsonReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            JsonObject jObj = (JsonObject) o;
            Object time = jObj.get("time");
            if (time == null)
            {
                throw new JsonIoException("java.sql.Timestamp must specify 'time' field");
            }
            Object nanos = jObj.get("nanos");
            if (nanos == null)
            {
                jObj.target = new Timestamp(Long.parseLong((String) time));
                return jObj.target;
            }

            Timestamp tstamp = new Timestamp(Long.parseLong((String) time));
            tstamp.setNanos(Integer.parseInt((String) nanos));
            jObj.target = tstamp;
            return jObj.target;
        }
    }

    public static class UUIDReader implements JsonReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {

            // to use the String representation
            if (o instanceof String)
            {
                return UUID.fromString((String) o);
            }

            JsonObject jObj = (JsonObject) o;
            Long mostSigBits = (Long) jObj.get("mostSigBits");
            if (mostSigBits == null)
            {
                throw new JsonIoException("java.util.UUID must specify 'mostSigBits' field");
            }
            Long leastSigBits = (Long) jObj.get("leastSigBits");
            if (leastSigBits == null)
            {
                throw new JsonIoException("java.util.UUID must specify 'leastSigBits' field");
            }

            UUID uuid = new UUID(mostSigBits, leastSigBits);

            jObj.setTarget(uuid);
            return jObj.getTarget();
        }
    }

    public static class RecordReader implements JsonReader.JsonClassReaderEx
    {
        @Override
        public Object read(Object o, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            try
            {
                JsonObject jsonObj = (JsonObject) o;

                ArrayList<Class<?>> lParameterTypes = new ArrayList<>(jsonObj.size());
                ArrayList<Object> lParameterValues = new ArrayList<>(jsonObj.size());

                Class<?> c = Class.forName(jsonObj.getType());
                // the record components are per definition in the constructor parameter order
                // we implement this with reflection due to code compatibility Java<16
                Method getRecordComponents = Class.class.getMethod("getRecordComponents");
                Object[] recordComponents = (Object[]) getRecordComponents.invoke(c);
                for (Object recordComponent : recordComponents)
                {
                    Class<?> type = (Class<?>) recordComponent.getClass().getMethod("getType").invoke(recordComponent);
                    lParameterTypes.add(type);

                    String parameterName = (String) recordComponent.getClass().getMethod("getName").invoke(recordComponent);
                    JsonObject parameterValueJsonObj = new JsonObject();
                    parameterValueJsonObj.setType(type.getName());
                    parameterValueJsonObj.put("value", jsonObj.get(parameterName));

                    if(parameterValueJsonObj.isLogicalPrimitive())
                        lParameterValues.add(parameterValueJsonObj.getPrimitiveValue());
                    else
                        lParameterValues.add(parameterValueJsonObj.get("value"));
                }

                Constructor<?> constructor = c.getDeclaredConstructor(lParameterTypes.toArray(new Class[0]));
                constructor.trySetAccessible();

                return constructor.newInstance(lParameterValues.toArray(new Object[0]));

            } catch (NoSuchMethodException e)
            {
                throw new RuntimeException("Record de-serialization only works with java>=16.", e);
            } catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    // ========== Maintain dependency knowledge in once place, down here =========
    static Class<?> classForName(String name, ClassLoader classLoader)
    {
        return MetaUtils.classForName(name, classLoader);
    }

    static Object newInstance(Class<?> c, JsonObject jsonObject)
    {
        return JsonReader.newInstance(c, jsonObject);
    }
}
