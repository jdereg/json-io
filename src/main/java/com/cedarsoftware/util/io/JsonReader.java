package com.cedarsoftware.util.io;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.sql.Timestamp;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read an object graph in JSON format and make it available in Java objects, or
 * in a "Map of Maps." (untyped representation).  This code handles cyclic references
 * and can deserialize any Object graph without requiring a class to be 'Serializeable'
 * or have any specific methods on it.  It will handle classes with non public constructors.
 * <br/><br/>
 * Usages:
 * <ul><li>
 * Call the static method: {@code JsonReader.jsonToJava(String json)}.  This will
 * return a typed Java object graph.</li>
 * <li>
 * Call the static method: {@code JsonReader.jsonToMaps(String json)}.  This will
 * return an untyped object representation of the JSON String as a Map of Maps, where
 * the fields are the Map keys, and the field values are the associated Map's values.  You can
 * call the JsonWriter.objectToJson() method with the returned Map, and it will serialize
 * the Graph into the identical JSON stream from which it was read.
 * <li>
 * Instantiate the JsonReader with an InputStream: {@code JsonReader(InputStream in)} and then call
 * {@code readObject()}.  Cast the return value of readObject() to the Java class that was the root of
 * the graph.
 * </li>
 * <li>
 * Instantiate the JsonReader with an InputStream: {@code JsonReader(InputStream in, true)} and then call
 * {@code readObject()}.  The return value will be a Map of Maps.
 * </li></ul><br/>
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
 *         limitations under the License.
 */
public class JsonReader implements Closeable
{
    private static final int STATE_READ_START_OBJECT = 0;
    private static final int STATE_READ_FIELD = 1;
    private static final int STATE_READ_VALUE = 2;
    private static final int STATE_READ_POST_VALUE = 3;
    private static final int SNIPPET_LENGTH = 200;
    private static final String EMPTY_ARRAY = "~!a~";  // compared with ==
    private static final String EMPTY_OBJECT = "~!o~";  // compared with ==
    private static final Character[] charCache = new Character[128];
    private static final Byte[] byteCache = new Byte[256];
    private static final Map<String, String> stringCache = new HashMap<>();
    private static final Set<Class> prims = new HashSet<>();
    private static final Map<Class, Object[]> constructors = new HashMap<>();
    private static final Map<String, Class> nameToClass = new HashMap<>();
    private static final Class[] emptyClassArray = new Class[]{};
    private static final List<Object[]> readers = new ArrayList<>();
    private static final Set<Class> notCustom = new HashSet<>();
    private static final Map<String, String> months = new LinkedHashMap<>();
    private static final Map<Class, ClassFactory> factory = new LinkedHashMap<>();
    private static final String days = "(monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thur|thu|friday|fri|saturday|sat|sunday|sun)"; // longer before shorter matters
    private static final String mos = "(January|Jan|February|Feb|March|Mar|April|Apr|May|June|Jun|July|Jul|August|Aug|September|Sept|Sep|October|Oct|November|Nov|December|Dec)";
    private static final Pattern datePattern1 = Pattern.compile("(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})");
    private static final Pattern datePattern2 = Pattern.compile("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})");
    private static final Pattern datePattern3 = Pattern.compile(mos + "[ ]*[,]?[ ]*(\\d{1,2})(st|nd|rd|th|)[ ]*[,]?[ ]*(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern datePattern4 = Pattern.compile("(\\d{1,2})(st|nd|rd|th|)[ ]*[,]?[ ]*" + mos + "[ ]*[,]?[ ]*(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern datePattern5 = Pattern.compile("(\\d{4})[ ]*[,]?[ ]*" + mos + "[ ]*[,]?[ ]*(\\d{1,2})(st|nd|rd|th|)", Pattern.CASE_INSENSITIVE);
    private static final Pattern datePattern6 = Pattern.compile(days+"[ ]+" + mos + "[ ]+(\\d{1,2})[ ]+(\\d{2}:\\d{2}:\\d{2})[ ]+[A-Z]{1,3}\\s+(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern timePattern1 = Pattern.compile("(\\d{2})[:.](\\d{2})[:.](\\d{2})[.](\\d{1,10})([+-]\\d{2}[:]?\\d{2}|Z)?");
    private static final Pattern timePattern2 = Pattern.compile("(\\d{2})[:.](\\d{2})[:.](\\d{2})([+-]\\d{2}[:]?\\d{2}|Z)?");
    private static final Pattern timePattern3 = Pattern.compile("(\\d{2})[:.](\\d{2})([+-]\\d{2}[:]?\\d{2}|Z)?");
    private static final Pattern dayPattern = Pattern.compile(days, Pattern.CASE_INSENSITIVE);
    private static final Pattern extraQuotes = Pattern.compile("([\"]*)([^\"]*)([\"]*)");
    private static final Collection unmodifiableCollection = Collections.unmodifiableCollection(new ArrayList());
    private static final Collection unmodifiableSet = Collections.unmodifiableSet(new HashSet());
    private static final Collection unmodifiableSortedSet = Collections.unmodifiableSortedSet(new TreeSet());
    private static final Map unmodifiableMap = Collections.unmodifiableMap(new HashMap());
    private static final Map unmodifiableSortedMap = Collections.unmodifiableSortedMap(new TreeMap());

    private final Map<Long, JsonObject> _objsRead = new LinkedHashMap<>();
    private final Collection<UnresolvedReference> unresolvedRefs = new ArrayList<>();
    private final Collection<Object[]> prettyMaps = new ArrayList<>();
    private final FastPushbackReader input;
    private boolean noObjects = false;
    private final char[] numBuf = new char[256];
    private final StringBuilder strBuf = new StringBuilder();

    static final ThreadLocal<FastPushbackReader> threadInput = new ThreadLocal<>();

	private static boolean useUnsafe = false;
	private static Unsafe unsafe;

    public static void setUseUnsafe(boolean state)
    {
        useUnsafe = state;
        if (state)
        {
            try
            {
                unsafe = new Unsafe();
            }
            catch (ReflectiveOperationException e)
            {
                useUnsafe = false;
            }
        }
    }

    static
    {
        // Save memory by re-using common Characters (Characters are immutable)
        for (int i = 0; i < charCache.length; i++)
        {
            charCache[i] = (char) i;
        }

        // Save memory by re-using all byte instances (Bytes are immutable)
        for (int i = 0; i < byteCache.length; i++)
        {
            byteCache[i] = (byte) (i - 128);
        }

        // Save heap memory by re-using common strings (String's immutable)
        stringCache.put("", "");
        stringCache.put("true", "true");
        stringCache.put("True", "True");
        stringCache.put("TRUE", "TRUE");
        stringCache.put("false", "false");
        stringCache.put("False", "False");
        stringCache.put("FALSE", "FALSE");
        stringCache.put("null", "null");
        stringCache.put("yes", "yes");
        stringCache.put("Yes", "Yes");
        stringCache.put("YES", "YES");
        stringCache.put("no", "no");
        stringCache.put("No", "No");
        stringCache.put("NO", "NO");
        stringCache.put("on", "on");
        stringCache.put("On", "On");
        stringCache.put("ON", "ON");
        stringCache.put("off", "off");
        stringCache.put("Off", "Off");
        stringCache.put("OFF", "OFF");
        stringCache.put("@id", "@id");
        stringCache.put("@ref", "@ref");
        stringCache.put("@items", "@items");
        stringCache.put("@type", "@type");
        stringCache.put("@keys", "@keys");
        stringCache.put("0", "0");
        stringCache.put("1", "1");
        stringCache.put("2", "2");
        stringCache.put("3", "3");
        stringCache.put("4", "4");
        stringCache.put("5", "5");
        stringCache.put("6", "6");
        stringCache.put("7", "7");
        stringCache.put("8", "8");
        stringCache.put("9", "9");

        prims.add(Byte.class);
        prims.add(Integer.class);
        prims.add(Long.class);
        prims.add(Double.class);
        prims.add(Character.class);
        prims.add(Float.class);
        prims.add(Boolean.class);
        prims.add(Short.class);

        nameToClass.put("string", String.class);
        nameToClass.put("boolean", boolean.class);
        nameToClass.put("char", char.class);
        nameToClass.put("byte", byte.class);
        nameToClass.put("short", short.class);
        nameToClass.put("int", int.class);
        nameToClass.put("long", long.class);
        nameToClass.put("float", float.class);
        nameToClass.put("double", double.class);
        nameToClass.put("date", Date.class);
        nameToClass.put("class", Class.class);

        addReader(String.class, new StringReader());
        addReader(Date.class, new DateReader());
        addReader(BigInteger.class, new BigIntegerReader());
        addReader(BigDecimal.class, new BigDecimalReader());
        addReader(java.sql.Date.class, new SqlDateReader());
        addReader(Timestamp.class, new TimestampReader());
        addReader(Calendar.class, new CalendarReader());
        addReader(TimeZone.class, new TimeZoneReader());
        addReader(Locale.class, new LocaleReader());
        addReader(Class.class, new ClassReader());
        addReader(StringBuilder.class, new StringBuilderReader());
        addReader(StringBuffer.class, new StringBufferReader());

        ClassFactory colFactory = new CollectionFactory();
        assignInstantiator(Collection.class, colFactory);
        assignInstantiator(List.class, colFactory);
        assignInstantiator(Set.class, colFactory);
        assignInstantiator(SortedSet.class, colFactory);

        ClassFactory mapFactory = new MapFactory();
        assignInstantiator(Map.class, mapFactory);
        assignInstantiator(SortedMap.class, mapFactory);

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

    public interface JsonClassReader
    {
        Object read(Object jOb, Deque<JsonObject<String, Object>> stack) throws IOException;
    }

    public interface ClassFactory
    {
        Object newInstance(Class c);
    }

    /**
     * For difficult to instantiate classes, you can add your own ClassFactory
     * which will be called when the passed in class 'c' is encountered.  Your
     * ClassFactory will be called with newInstance(c) and your factory is expected
     * to return a new instance of 'c'.
     *
     * This API is an 'escape hatch' to allow ANY object to be instantiated by JsonReader
     * and is useful when you encounter a class that JsonReader cannot instantiate using its
     * internal exhausting attempts (trying all constructors, varying arguments to them, etc.)
     */
    public static void assignInstantiator(Class c, ClassFactory f)
    {
        factory.put(c, f);
    }

    /**
     * Use to create new instances of collection interfaces (needed for empty collections)
     */
    public static class CollectionFactory implements ClassFactory
    {
        public Object newInstance(Class c)
        {
            if (List.class.isAssignableFrom(c))
            {
                return new ArrayList();
            }
            else if (SortedSet.class.isAssignableFrom(c))
            {
                return new TreeSet();
            }
            else if (Set.class.isAssignableFrom(c))
            {
                return new LinkedHashSet();
            }
            else if (Collection.class.isAssignableFrom(c))
            {
                return new ArrayList();
            }
            throw new RuntimeException("CollectionFactory handed Class for which it was not expecting: " + c.getName());
        }
    }

    /**
     * Use to create new instances of Map interfaces (needed for empty Maps)
     */
    public static class MapFactory implements ClassFactory
    {
        public Object newInstance(Class c)
        {
            if (SortedMap.class.isAssignableFrom(c))
            {
                return new TreeMap();
            }
            else if (Map.class.isAssignableFrom(c))
            {
                return new LinkedHashMap();
            }
            throw new RuntimeException("MapFactory handed Class for which it was not expecting: " + c.getName());
        }
    }

    public static class TimeZoneReader implements JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack) throws IOException
        {
            JsonObject jObj = (JsonObject)o;
            Object zone = jObj.get("zone");
            if (zone == null)
            {
                error("java.util.TimeZone must specify 'zone' field");
            }
            return jObj.target = TimeZone.getTimeZone((String) zone);
        }
    }

    public static class LocaleReader implements JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack) throws IOException
        {
            JsonObject jObj = (JsonObject) o;
            Object language = jObj.get("language");
            if (language == null)
            {
                error("java.util.Locale must specify 'language' field");
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

    public static class CalendarReader implements JsonClassReader
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
                    error("Calendar missing 'time' field");
                }
                Date date = JsonWriter._dateFormat.get().parse(time);
                Class c;
                if (jObj.getTarget() != null)
                {
                    c = jObj.getTarget().getClass();
                }
                else
                {
                    Object type = jObj.type;
                    c = classForName((String) type);
                }

                Calendar calendar = (Calendar) newInstance(c);
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
                return error("Failed to parse calendar, time: " + time);
            }
        }
    }

    public static class DateReader implements JsonClassReader
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
                return error("Unable to parse date: " + o);
            }
            else
            {
                return error("Unable to parse date, encountered unknown object: " + o);
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
                                    error("Unable to parse: " + dateStr);
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
                    error("Issue parsing data/time, other characters present: " + remains);
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
                error("Month must be between 1 and 12 inclusive, date: " + dateStr);
            }
            if (d < 1 || d > 31)
            {
                error("Day must be between 1 and 31 inclusive, date: " + dateStr);
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
                    error("Hour must be between 0 and 23 inclusive, time: " + dateStr);
                }
                if (mn > 59)
                {
                    error("Minute must be between 0 and 59 inclusive, time: " + dateStr);
                }
                if (s > 59)
                {
                    error("Second must be between 0 and 59 inclusive, time: " + dateStr);
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

    public static class StringReader implements JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack) throws IOException
        {
            if (o instanceof String)
            {
                return o;
            }

            if (isPrimitive(o.getClass()))
            {
                return o.toString();
            }

            JsonObject jObj = (JsonObject) o;
            if (jObj.containsKey("value"))
            {
                return jObj.target = jObj.get("value");
            }
            return error("String missing 'value' field");
        }
    }

    public static class ClassReader implements JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack) throws IOException
        {
            if (o instanceof String)
            {
                return classForName((String)o);
            }

            JsonObject jObj = (JsonObject) o;
            if (jObj.containsKey("value"))
            {
                return jObj.target = classForName((String) jObj.get("value"));
            }
            return error("Class missing 'value' field");
        }
    }

    public static class BigIntegerReader implements JsonClassReader
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
                    return error("BigInteger missing 'value' field");
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
                    return error("Unknown object type attempted to be assigned to BigInteger field: " + value);
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
                return new BigInteger(removeLeadingAndTrailingQuotes(s));
            }
            catch (Exception e)
            {
                return (BigInteger) error("Could not parse '" + value + "' as BigInteger.", e);
            }
        }
        else if (value instanceof BigDecimal)
        {
            BigDecimal bd = (BigDecimal) value;
            return bd.toBigInteger();
        }
        else if (value instanceof Boolean)
        {
            return new BigInteger(((Boolean) value) ? "1" : "0");
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
        return (BigInteger) error("Could not convert value: " + value.toString() + " to BigInteger.");
    }

    public static class BigDecimalReader implements JsonClassReader
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
                    return error("BigDecimal missing 'value' field");
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
                    return error("Unknown object type attempted to be assigned to BigInteger field: " + value);
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
                return new BigDecimal(removeLeadingAndTrailingQuotes(s));
            }
            catch (Exception e)
            {
                return (BigDecimal) error("Could not parse '" + s + "' as BigDecimal.", e);
            }
        }
        else if (value instanceof BigInteger)
        {
            return new BigDecimal((BigInteger) value);
        }
        else if (value instanceof Boolean)
        {
            return new BigDecimal(((Boolean) value) ? "1" : "0");
        }
        else if (value instanceof Long || value instanceof Integer || value instanceof Double ||
                value instanceof Short || value instanceof Byte || value instanceof Float)
        {
            return new BigDecimal(value.toString());
        }
        return (BigDecimal) error("Could not convert value: " + value.toString() + " to BigInteger.");
    }

    public static class StringBuilderReader implements JsonClassReader
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
            return error("StringBuilder missing 'value' field");
        }
    }

    public static class StringBufferReader implements JsonClassReader
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
            return error("StringBuffer missing 'value' field");
        }
    }

    public static class TimestampReader implements JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack) throws IOException
        {
            JsonObject jObj = (JsonObject) o;
            Object time = jObj.get("time");
            if (time == null)
            {
                error("java.sql.Timestamp must specify 'time' field");
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

    public static void addReader(Class c, JsonClassReader reader)
    {
        for (Object[] item : readers)
        {
            Class clz = (Class)item[0];
            if (clz == c)
            {
                item[1] = reader;   // Replace reader
                return;
            }
        }
        readers.add(new Object[]{c, reader});
    }

    public static void addNotCustomReader(Class c)
    {
        notCustom.add(c);
    }

    protected static Object readIfMatching(Object o, Class compType, Deque<JsonObject<String, Object>> stack) throws IOException
    {
        if (o == null)
        {
            error("Bug in json-io, null must be checked before calling this method.");
        }

        if (notCustom.contains(o.getClass()))
        {
            return null;
        }

        if (compType != null)
        {
            if (notCustom.contains(compType))
            {
                return null;
            }
        }

        boolean isJsonObject = o instanceof JsonObject;
        if (!isJsonObject && compType == null)
        {   // If not a JsonObject (like a Long that represents a date, then compType must be set)
            return null;
        }

        Class c;
        boolean needsType = false;

        // Set up class type to check against reader classes (specified as @type, or jObj.target, or compType)
        if (isJsonObject)
        {
            JsonObject jObj = (JsonObject) o;
            if (jObj.containsKey("@ref"))
            {
                return null;
            }

            if (jObj.target == null)
            {   // '@type' parameter used
                String typeStr = null;
                try
                {
                    Object type =  jObj.type;
                    if (type != null)
                    {
                        typeStr = (String) type;
                        c = classForName((String) type);
                    }
                    else
                    {
                        if (compType != null)
                        {
                            c = compType;
                            needsType = true;
                        }
                        else
                        {
                            return null;
                        }
                    }
                }
                catch(Exception e)
                {
                    return error("Class listed in @type [" + typeStr + "] is not found", e);
                }
            }
            else
            {   // Type inferred from target object
                c = jObj.target.getClass();
            }
        }
        else
        {
            c = compType;
        }

        JsonClassReader closestReader = getCustomReader(c);

        if (closestReader == null)
        {
            return null;
        }

        if (needsType && isJsonObject)
        {
            ((JsonObject)o).setType(c.getName());
        }
        return closestReader.read(o, stack);
    }

	private static JsonClassReader getCustomReader(Class c) {
		JsonClassReader closestReader = null;
        int minDistance = Integer.MAX_VALUE;

        for (Object[] item : readers)
        {
            Class clz = (Class)item[0];
            if (clz == c)
            {
                closestReader = (JsonClassReader)item[1];
                break;
            }
            int distance = JsonWriter.getDistance(clz, c);
            if (distance < minDistance)
            {
                minDistance = distance;
                closestReader = (JsonClassReader)item[1];
            }
        }
		return closestReader;
	}

    /**
     * UnresolvedReference is created to hold a logical pointer to a reference that
     * could not yet be loaded, as the @ref appears ahead of the referenced object's
     * definition.  This can point to a field reference or an array/Collection element reference.
     */
    private static final class UnresolvedReference
    {
        private final JsonObject referencingObj;
        private String field;
        private final long refId;
        private int index = -1;

        private UnresolvedReference(JsonObject referrer, String fld, long id)
        {
            referencingObj = referrer;
            field = fld;
            refId = id;
        }

        private UnresolvedReference(JsonObject referrer, int idx, long id)
        {
            referencingObj = referrer;
            index = idx;
            refId = id;
        }
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     *
     * @param json String JSON input
     * @return Java object graph matching JSON input, or null if an
     *         error occurred.
     */
    @Deprecated
    public static Object toJava(String json)
    {
        throw new RuntimeException("Use com.cedarsoftware.util.JsonReader.jsonToJava()");
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     *
     * @param json String JSON input
     * @return Java object graph matching JSON input
     * @throws java.io.IOException If an I/O error occurs
     */
    public static Object jsonToJava(String json) throws IOException
    {
        ByteArrayInputStream ba = new ByteArrayInputStream(json.getBytes("UTF-8"));
        JsonReader jr = new JsonReader(ba, false);
        Object obj = jr.readObject();
        jr.close();
        return obj;
    }

    /**
     * Convert the passed in JSON string into a Java object graph
     * that consists solely of Java Maps where the keys are the
     * fields and the values are primitives or other Maps (in the
     * case of objects).
     *
     * @param json String JSON input
     * @return Java object graph of Maps matching JSON input,
     *         or null if an error occurred.
     */
    @Deprecated
    public static Map toMaps(String json)
    {
        throw new RuntimeException("Use com.cedarsoftware.util.JsonReader.jsonToMaps()");
    }

    /**
     * Convert the passed in JSON string into a Java object graph
     * that consists solely of Java Maps where the keys are the
     * fields and the values are primitives or other Maps (in the
     * case of objects).
     *
     * @param json String JSON input
     * @return Java object graph of Maps matching JSON input,
     *         or null if an error occurred.
     * @throws java.io.IOException If an I/O error occurs
     */
    public static Map jsonToMaps(String json) throws IOException
    {
        ByteArrayInputStream ba = new ByteArrayInputStream(json.getBytes("UTF-8"));
        JsonReader jr = new JsonReader(ba, true);
        Object ret = jr.readObject();
        jr.close();

        if (ret instanceof Map)
        {
            return (Map) ret;
        }

        if (ret != null && ret.getClass().isArray())
        {
            JsonObject retMap = new JsonObject();
            retMap.put("@items", ret);
            return retMap;

        }
        JsonObject retMap = new JsonObject();
        retMap.put("@items", new Object[]{ret});
        return retMap;
    }

    public JsonReader()
    {
        noObjects = false;
        input = null;
    }

    public JsonReader(InputStream in)
    {
        this(in, false);
    }

    public JsonReader(InputStream in, boolean noObjects)
    {
        this.noObjects = noObjects;
        try
        {
            input = new FastPushbackReader(new BufferedReader(new InputStreamReader(in, "UTF-8")));
            threadInput.set(input);
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("Your JVM does not support UTF-8.  Get a new JVM.", e);
        }
    }

    /**
     * Finite State Machine (FSM) used to parse the JSON input into
     * JsonObject's (Maps).  Then, if requested, the JsonObjects are
     * converted into Java instances.
     *
     * @return Java Object graph constructed from InputStream supplying
     *         JSON serialized content.
     * @throws IOException for stream errors or parsing errors.
     */
    public Object readObject() throws IOException
    {
        JsonObject root = new JsonObject();
        Object o = readValue(root);
        if (o == EMPTY_OBJECT)
        {
            return new JsonObject();
        }

        Object graph;
        if (o instanceof Object[])
        {
            root.setType(Object[].class.getName());
            root.setTarget(o);
            root.put("@items", o);
            graph = convertParsedMapsToJava(root);
        }
        else if (o instanceof JsonObject)
        {
            graph = convertParsedMapsToJava((JsonObject) o);
        }
        else
        {
            graph = o;
        }
        // Allow a complete 'Map' return (Javascript style)
        if (noObjects)
        {
            return o;
        }
        return graph;
    }

    /**
     * Convert a root JsonObject that represents parsed JSON, into
     * an actual Java object.
     * @param root JsonObject instance that was the root object from the
     * JSON input that was parsed in an earlier call to JsonReader.
     * @return a typed Java instance that was serialized into JSON.
     */
    public Object jsonObjectsToJava(JsonObject root) throws IOException
    {
        noObjects = false;
        return convertParsedMapsToJava(root);
    }

    /**
     * This method converts a root Map, (which contains nested Maps
     * and so forth representing a Java Object graph), to a Java
     * object instance.  The root map came from using the JsonReader
     * to parse a JSON graph (using the API that puts the graph
     * into Maps, not the typed representation).
     * @param root JsonObject instance that was the root object from the
     * JSON input that was parsed in an earlier call to JsonReader.
     * @return a typed Java instance that was serialized into JSON.
     */
    protected Object convertParsedMapsToJava(JsonObject root) throws IOException
    {
        createJavaObjectInstance(Object.class, root);
        Object graph = convertMapsToObjects((JsonObject<String, Object>) root);
        patchUnresolvedReferences();
        rehashMaps();
        _objsRead.clear();
        unresolvedRefs.clear();
        prettyMaps.clear();
        return graph;
    }

    /**
     * Walk a JsonObject (Map of String keys to values) and return the
     * Java object equivalent filled in as best as possible (everything
     * except unresolved reference fields or unresolved array/collection elements).
     *
     * @param root JsonObject reference to a Map-of-Maps representation of the JSON
     *             input after it has been completely read.
     * @return Properly constructed, typed, Java object graph built from a Map
     *         of Maps representation (JsonObject root).
     * @throws IOException for stream errors or parsing errors.
     */
    protected Object convertMapsToObjects(JsonObject<String, Object> root) throws IOException
    {
        Deque<JsonObject<String, Object>> stack = new ArrayDeque<>();
        stack.addFirst(root);
        final boolean useMaps = noObjects;

        while (!stack.isEmpty())
        {
            JsonObject<String, Object> jsonObj = stack.removeFirst();

            if (useMaps)
            {
                if (jsonObj.isArray() || jsonObj.isCollection())
                {
                    traverseCollectionNoObj(stack, jsonObj);
                }
                else if (jsonObj.isMap())
                {
                    traverseMap(stack, jsonObj);
                }
                else
                {
                    traverseFieldsNoObj(stack, jsonObj);
                }
            }
            else
            {
                if (jsonObj.isArray())
                {
                    traverseArray(stack, jsonObj);
                }
                else if (jsonObj.isCollection())
                {
                    traverseCollection(stack, jsonObj);
                }
                else if (jsonObj.isMap())
                {
                    traverseMap(stack, jsonObj);
                }
                else
                {
                    traverseFields(stack, jsonObj);
                }

                // Reduce heap footprint during processing
                jsonObj.clear();
            }
        }
        return root.target;
    }

    /**
     * Traverse the JsonObject associated to an array (of any type).  Convert and
     * assign the list of items in the JsonObject (stored in the @items field)
     * to each array element.  All array elements are processed excluding elements
     * that reference an unresolved object.  These are filled in later.
     *
     * @param stack   a Stack (Deque) used to support graph traversal.
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     * @throws IOException for stream errors or parsing errors.
     */
    protected void traverseArray(Deque<JsonObject<String, Object>> stack, JsonObject<String, Object> jsonObj) throws IOException
    {
        final int len = jsonObj.getLength();
        if (len == 0)
        {
            return;
        }

        final Class compType = jsonObj.getComponentType();

        if (char.class == compType)
        {
            return;
        }

        if (byte.class == compType)
        {   // Handle byte[] special for performance boost.
            jsonObj.moveBytesToMate();
            jsonObj.clearArray();
            return;
        }

        final boolean isPrimitive = isPrimitive(compType);
        final Object array = jsonObj.target;
        final Object[] items =  jsonObj.getArray();

        for (int i=0; i < len; i++)
        {
            final Object element = items[i];

            Object special;
            if (element == null)
            {
                Array.set(array, i, null);
            }
            else if (element == EMPTY_OBJECT)
            {    // Use either explicitly defined type in ObjectMap associated to JSON, or array component type.
                Object arrayElement = createJavaObjectInstance(compType, new JsonObject());
                Array.set(array, i, arrayElement);
            }
            else if ((special = readIfMatching(element, compType, stack)) != null)
            {
                Array.set(array, i, special);
            }
            else if (isPrimitive)
            {   // Primitive component type array
                Array.set(array, i, newPrimitiveWrapper(compType, element));
            }
            else if (element.getClass().isArray())
            {   // Array of arrays
                if (char[].class == compType)
                {   // Specially handle char[] because we are writing these
                    // out as UTF-8 strings for compactness and speed.
                    Object[] jsonArray = (Object[]) element;
                    if (jsonArray.length == 0)
                    {
                        Array.set(array, i, new char[]{});
                    }
                    else
                    {
                        final String value = (String) jsonArray[0];
                        final int numChars = value.length();
                        final char[] chars = new char[numChars];
                        for (int j = 0; j < numChars; j++)
                        {
                            chars[j] = value.charAt(j);
                        }
                        Array.set(array, i, chars);
                    }
                }
                else
                {
                    JsonObject<String, Object> jsonObject = new JsonObject<>();
                    jsonObject.put("@items", element);
                    Array.set(array, i, createJavaObjectInstance(compType, jsonObject));
                    stack.addFirst(jsonObject);
                }
            }
            else if (element instanceof JsonObject)
            {
                JsonObject<String, Object> jsonObject = (JsonObject<String, Object>) element;
                Long ref = (Long) jsonObject.get("@ref");

                if (ref != null)
                {    // Connect reference
                    JsonObject refObject = getReferencedObj(ref);
                    if (refObject.target != null)
                    {   // Array element with @ref to existing object
                        Array.set(array, i, refObject.target);
                    }
                    else
                    {    // Array with a forward @ref as an element
                        unresolvedRefs.add(new UnresolvedReference(jsonObj, i, ref));
                    }
                }
                else
                {    // Convert JSON HashMap to Java Object instance and assign values
                    Object arrayElement = createJavaObjectInstance(compType, jsonObject);
                    Array.set(array, i, arrayElement);
                    if (!isLogicalPrimitive(arrayElement.getClass()))
                    {    // Skip walking primitives, primitive wrapper classes, Strings, and Classes
                        stack.addFirst(jsonObject);
                    }
                }
            }
            else
            {
                if (element instanceof String && "".equals(((String) element).trim()) && compType != String.class && compType != Object.class)
                {   // Allow an entry of "" in the array to set the array element to null, *if* the array type is NOT String[] and NOT Object[]
                    Array.set(array, i, null);
                }
                else
                {
                    Array.set(array, i, element);
                }
            }
        }
        jsonObj.clearArray();
    }

    private JsonObject getReferencedObj(Long ref) throws IOException
    {
        JsonObject refObject = _objsRead.get(ref);
        if (refObject == null)
        {
            error("Forward reference @ref: " + ref + ", but no object defined (@id) with that value");
        }
        return refObject;
    }

    /**
     * Process java.util.Collection and it's derivatives.  Collections are written specially
     * so that the serialization does not expose the Collection's internal structure, for
     * example a TreeSet.  All entries are processed, except unresolved references, which
     * are filled in later.  For an indexable collection, the unresolved references are set
     * back into the proper element location.  For non-indexable collections (Sets), the
     * unresolved references are added via .add().
     * @param stack   a Stack (Deque) used to support graph traversal.
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     * @throws IOException for stream errors or parsing errors.
     */
    protected void traverseCollectionNoObj(Deque<JsonObject<String, Object>> stack, JsonObject jsonObj) throws IOException
    {
        Object[] items = jsonObj.getArray();
        if (items == null || items.length == 0)
        {
            return;
        }

        int idx = 0;
        List copy = new ArrayList(items.length);

        for (Object element : items)
        {
            if (element == EMPTY_OBJECT)
            {
                copy.add(new JsonObject());
                continue;
            }

            copy.add(element);

            if (element instanceof Object[])
            {   // array element inside Collection
                JsonObject<String, Object> jsonObject = new JsonObject<>();
                jsonObject.put("@items", element);
                stack.addFirst(jsonObject);
            }
            else if (element instanceof JsonObject)
            {
                JsonObject<String, Object> jsonObject = (JsonObject<String, Object>) element;
                Long ref = (Long) jsonObject.get("@ref");

                if (ref != null)
                {    // connect reference
                    JsonObject refObject = getReferencedObj(ref);
                    copy.set(idx, refObject);
                }
                else
                {
                    stack.addFirst(jsonObject);
                }
            }
            idx++;
        }
        jsonObj.target = null;  // don't waste space (used for typed return, not generic Map return)

        for (int i=0; i < items.length; i++)
        {
            items[i] = copy.get(i);
        }
    }

    /**
     * Process java.util.Collection and it's derivatives.  Collections are written specially
     * so that the serialization does not expose the Collection's internal structure, for
     * example a TreeSet.  All entries are processed, except unresolved references, which
     * are filled in later.  For an indexable collection, the unresolved references are set
     * back into the proper element location.  For non-indexable collections (Sets), the
     * unresolved references are added via .add().
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     * @throws IOException for stream errors or parsing errors.
     */
    protected void traverseCollection(Deque<JsonObject<String, Object>> stack, JsonObject jsonObj) throws IOException
    {
        Object[] items = jsonObj.getArray();
        if (items == null || items.length == 0)
        {
            return;
        }
        Collection col = (Collection) jsonObj.target;
        boolean isList = col instanceof List;
        int idx = 0;

        for (Object element : items)
        {
            Object special;
            if (element == null)
            {
                col.add(null);
            }
            else if (element == EMPTY_OBJECT)
            {   // Handles {}
                col.add(new JsonObject());
            }
            else if ((special = readIfMatching(element, null, stack)) != null)
            {
                col.add(special);
            }
            else if (element instanceof String || element instanceof Boolean || element instanceof Double || element instanceof Long)
            {    // Allow Strings, Booleans, Longs, and Doubles to be "inline" without Java object decoration (@id, @type, etc.)
                col.add(element);
            }
            else if (element.getClass().isArray())
            {
                JsonObject jObj = new JsonObject();
                jObj.put("@items", element);
                createJavaObjectInstance(Object.class, jObj);
                col.add(jObj.target);
                convertMapsToObjects(jObj);
            }
            else // if (element instanceof JsonObject)
            {
                JsonObject jObj = (JsonObject) element;
                Long ref = (Long) jObj.get("@ref");

                if (ref != null)
                {
                    JsonObject refObject = getReferencedObj(ref);

                    if (refObject.target != null)
                    {
                        col.add(refObject.target);
                    }
                    else
                    {
                        unresolvedRefs.add(new UnresolvedReference(jsonObj, idx, ref));
                        if (isList)
                        {   // Indexable collection, so set 'null' as element for now - will be patched in later.
                            col.add(null);
                        }
                    }
                }
                else
                {
                    createJavaObjectInstance(Object.class, jObj);

                    if (!isLogicalPrimitive(jObj.getTargetClass()))
                    {
                        convertMapsToObjects(jObj);
                    }
                    col.add(jObj.target);
                }
            }
            idx++;
        }

        jsonObj.remove("@items");   // Reduce memory required during processing
    }

    /**
     * Process java.util.Map and it's derivatives.  These can be written specially
     * so that the serialization would not expose the derivative class internals
     * (internal fields of TreeMap for example).
     * @param stack   a Stack (Deque) used to support graph traversal.
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     * @throws IOException for stream errors or parsing errors.
     */
    protected void traverseMap(Deque<JsonObject<String, Object>> stack, JsonObject<String, Object> jsonObj) throws IOException
    {
        // Convert @keys to a Collection of Java objects.
        convertMapToKeysItems(jsonObj);
        Object[] keys = (Object[]) jsonObj.get("@keys");
        Object[] items = jsonObj.getArray();

        if (keys == null || items == null)
        {
            if (keys != items)
            {
                error("Map written where one of @keys or @items is empty");
            }
            return;
        }

        int size = keys.length;
        if (size != items.length)
        {
            error("Map written with @keys and @items entries of different sizes");
        }

        Object[] mapKeys = buildCollection(stack, keys, size);
        Object[] mapValues = buildCollection(stack, items, size);

        // Save these for later so that unresolved references inside keys or values
        // get patched first, and then build the Maps.
        prettyMaps.add(new Object[]{jsonObj, mapKeys, mapValues});
    }

    private static Object[] buildCollection(Deque<JsonObject<String, Object>> stack, Object[] items, int size)
    {
        JsonObject jsonCollection = new JsonObject();
        jsonCollection.put("@items", items);
        Object[] javaKeys = new Object[size];
        jsonCollection.target = javaKeys;
        stack.addFirst(jsonCollection);
        return javaKeys;
    }

    protected void traverseFieldsNoObj(Deque<JsonObject<String, Object>> stack, JsonObject<String, Object> jsonObj) throws IOException
    {
        final Object target = jsonObj.target;
        for (Map.Entry<String, Object> e : jsonObj.entrySet())
        {
            String key = e.getKey();

            if (key.charAt(0) == '@')
            {   // Skip our own meta fields
                continue;
            }

            Field field = null;
            if (target != null)
            {
                field = getDeclaredField(target.getClass(), key);
            }

            Object value = e.getValue();

            if (value == null)
            {
                jsonObj.put(key, null);
            }
            else if (value == EMPTY_OBJECT)
            {
                jsonObj.put(key, new JsonObject());
            }
            else if (value.getClass().isArray())
            {    // LHS of assignment is an [] field or RHS is an array and LHS is Object (Map)
                JsonObject<String, Object> jsonArray = new JsonObject<>();
                jsonArray.put("@items", value);
                stack.addFirst(jsonArray);
                jsonObj.put(key, jsonArray);
            }
            else if (value instanceof JsonObject)
            {
                JsonObject<String, Object> jObj = (JsonObject) value;
                if (field != null && JsonObject.isPrimitiveWrapper(field.getType()))
                {
                    jObj.put("value", newPrimitiveWrapper(field.getType(),jObj.get("value")));
                    continue;
                }
                Long ref = (Long) jObj.get("@ref");

                if (ref != null)
                {    // Correct field references
                    JsonObject refObject = getReferencedObj(ref);
                    jsonObj.put(key, refObject);    // Update Map-of-Maps reference
                }
                else
                {
                    stack.addFirst(jObj);
                }
            }
            else if (field != null)
            {   // The code below is 'upgrading' the RHS values in the passed in JsonObject Map
                // by using the @type class name (when specified and exists), to coerce the vanilla
                // JSON values into the proper types defined by the class listed in @type.  This is
                // a cool feature of json-io, that even when reading a map-of-maps JSON file, it will
                // improve the final types of values in the maps RHS, to be of the field type that
                // was optionally specified in @type.
                final Class fieldType = field.getType();
                if (isPrimitive(fieldType))
                {
                    jsonObj.put(key, newPrimitiveWrapper(fieldType, value));
                }
                else if (BigDecimal.class == fieldType)
                {
                    jsonObj.put(key, bigDecimalFrom(value));
                }
                else if (BigInteger.class == fieldType)
                {
                    jsonObj.put(key, bigIntegerFrom(value));
                }
                else if (value instanceof String)
                {
                    if (fieldType != String.class && fieldType != StringBuilder.class && fieldType != StringBuffer.class)
                    {
                        if ("".equals(((String)value).trim()))
                        {   // Allow "" to null out a non-String field on the inbound JSON
                            jsonObj.put(key, null);
                        }
                    }
                }
            }
        }
        jsonObj.target = null;  // don't waste space (used for typed return, not for Map return)
    }

    /**
     * Walk the Java object fields and copy them from the JSON object to the Java object, performing
     * any necessary conversions on primitives, or deep traversals for field assignments to other objects,
     * arrays, Collections, or Maps.
     * @param stack   Stack (Deque) used for graph traversal.
     * @param jsonObj a Map-of-Map representation of the current object being examined (containing all fields).
     * @throws IOException
     */
    protected void traverseFields(Deque<JsonObject<String, Object>> stack, JsonObject<String, Object> jsonObj) throws IOException
    {
        Object special;
        if ((special = readIfMatching(jsonObj, null, stack)) != null)
        {
            jsonObj.target = special;
            return;
        }

        final Object javaMate = jsonObj.target;
        Iterator<Map.Entry<String, Object>> i = jsonObj.entrySet().iterator();
        Class cls = javaMate.getClass();

        while (i.hasNext())
        {
            Map.Entry<String, Object> e = i.next();
            String key = e.getKey();
            Field field = getDeclaredField(cls, key);
            Object rhs = e.getValue();
            if (field != null)
            {
                assignField(stack, jsonObj, field, rhs);
            }
        }
        jsonObj.clear();    // Reduce memory required during processing
    }

    /**
     * Map Json Map object field to Java object field.
     *
     * @param stack   Stack (Deque) used for graph traversal.
     * @param jsonObj a Map-of-Map representation of the current object being examined (containing all fields).
     * @param field   a Java Field object representing where the jsonObj should be converted and stored.
     * @param rhs     the JSON value that will be converted and stored in the 'field' on the associated
     *                Java target object.
     * @throws IOException for stream errors or parsing errors.
     */
    protected void assignField(Deque<JsonObject<String, Object>> stack, JsonObject jsonObj, Field field, Object rhs) throws IOException
    {
        Object target = jsonObj.target;
        try
        {
            Class fieldType = field.getType();

            // If there is a "tree" of objects (e.g, Map<String, List<Person>>), the subobjects may not have an
            // @type on them, if the source of the JSON is from JSON.stringify().  Deep traverse the args and
            // mark @type on the items within the Maps and Collections, based on the parameterized type (if it
            // exists).
            if (rhs instanceof JsonObject && field.getGenericType() instanceof ParameterizedType)
            {   // Only JsonObject instances could contain unmarked objects.
                markUntypedObjects(field.getGenericType(), rhs);
            }

            if (rhs instanceof JsonObject)
            {   // Ensure .type field set on JsonObject
                JsonObject job = (JsonObject) rhs;
                String type = job.type;
                if (type == null || type.isEmpty())
                {
                    job.setType(fieldType.getName());
                }
            }

            Object special;
            if (rhs == null)
            {
                field.set(target, null);
            }
            else if (rhs == EMPTY_OBJECT)
            {
                JsonObject jObj = new JsonObject();
                jObj.type = fieldType.getName();
                Object value = createJavaObjectInstance(fieldType, jObj);
                field.set(target, value);
            }
            else if ((special = readIfMatching(rhs, field.getType(), stack)) != null)
            {
                field.set(target, special);
            }
            else if (rhs.getClass().isArray())
            {    // LHS of assignment is an [] field or RHS is an array and LHS is Object
                Object[] elements = (Object[]) rhs;
                JsonObject<String, Object> jsonArray = new JsonObject<>();
                if (char[].class == fieldType)
                {   // Specially handle char[] because we are writing these
                    // out as UTF8 strings for compactness and speed.
                    if (elements.length == 0)
                    {
                        field.set(target, new char[]{});
                    }
                    else
                    {
                        field.set(target, ((String) elements[0]).toCharArray());
                    }
                }
                else
                {
                    jsonArray.put("@items", elements);
                    createJavaObjectInstance(fieldType, jsonArray);
                    field.set(target, jsonArray.target);
                    stack.addFirst(jsonArray);
                }
            }
            else if (rhs instanceof JsonObject)
            {
                JsonObject<String, Object> jObj = (JsonObject) rhs;
                Long ref = (Long) jObj.get("@ref");

                if (ref != null)
                {    // Correct field references
                    JsonObject refObject = getReferencedObj(ref);

                    if (refObject.target != null)
                    {
                        field.set(target, refObject.target);
                    }
                    else
                    {
                        unresolvedRefs.add(new UnresolvedReference(jsonObj, field.getName(), ref));
                    }
                }
                else
                {    // Assign ObjectMap's to Object (or derived) fields
                    field.set(target, createJavaObjectInstance(fieldType, jObj));
                    if (!isLogicalPrimitive(jObj.getTargetClass()))
                    {
                        stack.addFirst((JsonObject) rhs);
                    }
                }
            }
            else
            {
                if (isPrimitive(fieldType))
                {
                    field.set(target, newPrimitiveWrapper(fieldType, rhs));
                }
                else if (rhs instanceof String && "".equals(((String) rhs).trim()) && field.getType() != String.class)
                {   // Allow "" to null out a non-String field
                    field.set(target, null);
                }
                else
                {
                    field.set(target, rhs);
                }
            }
        }
        catch (Exception e)
        {
            error(e.getClass().getSimpleName() + " setting field '" + field.getName() + "' on target: " + safeToString(target) + " with value: " + rhs, e);
        }
    }

    private static String safeToString(Object o)
    {
        if (o == null)
        {
            return "null";
        }
        try
        {
            return o.toString();
        }
        catch (Exception e)
        {
            return o.getClass().toString();
        }
    }

    private static void markUntypedObjects(Type type, Object rhs)
    {
        Deque<Object[]> stack = new ArrayDeque<>();
        stack.addFirst(new Object[] {type, rhs});

        while (!stack.isEmpty())
        {
            Object[] item = stack.removeFirst();
            Type t = (Type) item[0];
            Object instance = item[1];
            if (t instanceof ParameterizedType)
            {
                Class clazz = getRawType(t);
                ParameterizedType pType = (ParameterizedType)t;
                Type[] typeArgs = pType.getActualTypeArguments();

                if (typeArgs == null || typeArgs.length < 1 || clazz == null)
                {
                    continue;
                }

                stampTypeOnJsonObject(instance, t);

                if (Map.class.isAssignableFrom(clazz))
                {
                    Map map = (Map) instance;
                    if (!map.containsKey("@keys") && !map.containsKey("@items") && map instanceof JsonObject)
                    {   // Maps created in Javascript will come over without @keys / @items.
                        convertMapToKeysItems((JsonObject) map);
                    }

                    Object[] keys = (Object[])map.get("@keys");
                    getTemplateTraverseWorkItem(stack, keys, typeArgs[0]);

                    Object[] items = (Object[])map.get("@items");
                    getTemplateTraverseWorkItem(stack, items, typeArgs[1]);
                }
                else if (Collection.class.isAssignableFrom(clazz))
                {
                    if (instance instanceof Object[])
                    {
                        Object[] array = (Object[]) instance;
                        for (int i=0; i < array.length; i++)
                        {
                            Object vals = array[i];
                            stack.addFirst(new Object[]{t, vals});

                            if (vals instanceof JsonObject)
                            {
                                stack.addFirst(new Object[]{t, vals});
                            }
                            else if (vals instanceof Object[])
                            {
                                JsonObject coll = new JsonObject();
                                coll.type = clazz.getName();
                                List items = Arrays.asList((Object[]) vals);
                                coll.put("@items", items.toArray());
                                stack.addFirst(new Object[]{t, items});
                                array[i] = coll;
                            }
                            else
                            {
                                stack.addFirst(new Object[]{t, vals});
                            }
                        }
                    }
                    else if (instance instanceof Collection)
                    {
                        Collection col = (Collection)instance;
                        for (Object o : col)
                        {
                            stack.addFirst(new Object[]{typeArgs[0], o});
                        }
                    }
                    else if (instance instanceof JsonObject)
                    {
                        JsonObject jObj = (JsonObject) instance;
                        Object[] array = jObj.getArray();
                        if (array != null)
                        {
                            for (Object o : array)
                            {
                                stack.addFirst(new Object[]{typeArgs[0], o});
                            }
                        }
                    }
                }
                else
                {
                    if (instance instanceof JsonObject)
                    {
                        JsonObject<String, Object> jObj = (JsonObject) instance;

                        for (Map.Entry<String, Object> entry : jObj.entrySet())
                        {
                            if (!entry.getKey().startsWith("this$"))
                            {
                                // TODO: If more than one type, need to associate correct typeArgs entry to value
                                stack.addFirst(new Object[]{typeArgs[0], entry.getValue()});
                            }
                        }
                    }
                }
            }
            else
            {
                stampTypeOnJsonObject(instance, t);
            }
        }
    }

    private static void getTemplateTraverseWorkItem(Deque<Object[]> stack, Object[] items, Type type)
    {
        if (items == null || items.length < 1)
        {
            return;
        }
        Class rawType = getRawType(type);
        if (rawType != null && Collection.class.isAssignableFrom(rawType))
        {
            stack.add(new Object[]{type, items});
        }
        else
        {
            for (Object o : items)
            {
                stack.add(new Object[]{type, o});
            }
        }
    }

    // Mark 'type' on JsonObject when the type is missing and it is a 'leaf'
    // node (no further subtypes in it's parameterized type definition)
    private static void stampTypeOnJsonObject(Object o, Type t)
    {
        Class clazz = t instanceof Class ? (Class)t : getRawType(t);

        if (o instanceof JsonObject && clazz != null)
        {
            JsonObject jObj = (JsonObject) o;
            if ((jObj.type == null || jObj.type.isEmpty()) && jObj.target == null)
            {
                jObj.type = clazz.getName();
            }
        }
    }

    public static Class getRawType(Type t)
    {
        if (t instanceof ParameterizedType)
        {
            ParameterizedType pType = ((ParameterizedType) t);

            if (pType.getRawType() instanceof Class)
            {
                return (Class) pType.getRawType();
            }
        }
        return null;
    }

    /**
     * Convert an input JsonObject map (known to represent a Map.class or derivative) that has regular keys and values
     * to have its keys placed into @keys, and its values placed into @items.
     * @param map Map to convert
     */
    private static void convertMapToKeysItems(JsonObject map)
    {
        if (!map.containsKey("@keys") && !map.containsKey("@ref"))
        {
            Object[] keys = new Object[map.keySet().size()];
            Object[] values = new Object[map.keySet().size()];
            int i=0;
            for (Object e : map.entrySet())
            {
                Map.Entry entry = (Map.Entry)e;
                keys[i] = entry.getKey();
                values[i] = entry.getValue();
                i++;
            }
            String saveType = map.getType();
            map.clear();
            map.setType(saveType);
            map.put("@keys", keys);
            map.put("@items", values);
        }
    }

    /**
     * This method creates a Java Object instance based on the passed in parameters.
     * If the JsonObject contains a key '@type' then that is used, as the type was explicitly
     * set in the JSON stream.  If the key '@type' does not exist, then the passed in Class
     * is used to create the instance, handling creating an Array or regular Object
     * instance.
     * <p/>
     * The '@type' is not often specified in the JSON input stream, as in many
     * cases it can be inferred from a field reference or array component type.
     *
     * @param clazz   Instance will be create of this class.
     * @param jsonObj Map-of-Map representation of object to create.
     * @return a new Java object of the appropriate type (clazz) using the jsonObj to provide
     *         enough hints to get the right class instantiated.  It is not populated when returned.
     * @throws IOException for stream errors or parsing errors.
     */
    protected Object createJavaObjectInstance(Class clazz, JsonObject jsonObj) throws IOException
    {
        final boolean useMaps = noObjects;
        final String type = jsonObj.type;
        Object mate;

        // @type always takes precedence over inferred Java (clazz) type.
        if (type != null)
        {    // @type is explicitly set, use that as it always takes precedence
            Class c;
            try
            {
                c = classForName(type);
            }
            catch (IOException e)
            {
                if (useMaps)
                {
                    jsonObj.type = null;
                    jsonObj.target = null;
                    return jsonObj;
                }
                else
                {
                    throw e;
                }
            }
            if (c.isArray())
            {    // Handle []
                Object[] items = jsonObj.getArray();
                int size = (items == null) ? 0 : items.length;
                if (c == char[].class)
                {
                    jsonObj.moveCharsToMate();
                    mate = jsonObj.target;
                }
                else
                {
                    mate = Array.newInstance(c.getComponentType(), size);
                }
            }
            else
            {    // Handle regular field.object reference
                if (isPrimitive(c))
                {
                    mate = newPrimitiveWrapper(c, jsonObj.get("value"));
                }
                else if (c == Class.class)
                {
                    mate = classForName((String) jsonObj.get("value"));
                }
                else if (c.isEnum())
                {
                    mate = getEnum(c, jsonObj);
                }
                else if (Enum.class.isAssignableFrom(c)) // anonymous subclass of an enum
                {
                    mate = getEnum(c.getSuperclass(), jsonObj);
                }
                else if ("java.util.Arrays$ArrayList".equals(c.getName()))
                {	// Special case: Arrays$ArrayList does not allow .add() to be called on it.
                    mate = new ArrayList();
                }
                else
                {
					JsonClassReader customReader = getCustomReader(c);
					if (customReader != null)
                    {
						mate = customReader.read(jsonObj,  new ArrayDeque<JsonObject<String, Object>>());
					}
                    else
                    {
						mate = newInstance(c);
					}
                }
            }
        }
        else
        {    // @type, not specified, figure out appropriate type
            Object[] items = jsonObj.getArray();

            // if @items is specified, it must be an [] type.
            // if clazz.isArray(), then it must be an [] type.
            if (clazz.isArray() || (items != null && clazz == Object.class && !jsonObj.containsKey("@keys")))
            {
                int size = (items == null) ? 0 : items.length;
                mate = Array.newInstance(clazz.isArray() ? clazz.getComponentType() : Object.class, size);
            }
            else if (clazz.isEnum())
            {
                mate = getEnum(clazz, jsonObj);
            }
            else if (Enum.class.isAssignableFrom(clazz)) // anonymous subclass of an enum
            {
                mate = getEnum(clazz.getSuperclass(), jsonObj);
            }
            else if ("java.util.Arrays$ArrayList".equals(clazz.getName()))
            {	// Special case: Arrays$ArrayList does not allow .add() to be called on it.
                mate = new ArrayList();
            }
            else if (clazz == Object.class && !useMaps)
            {
                if (jsonObj.isMap() || jsonObj.size() > 0)
                {
                    mate = new JsonObject();
                    ((JsonObject)mate).type = Map.class.getName();
                }
                else
                {   // Dunno
                    mate = newInstance(clazz);
                }
            }
            else
            {
                mate = newInstance(clazz);
            }
        }
        return jsonObj.target = mate;
    }

    /**
     * Fetch enum value (may need to try twice, due to potential 'name' field shadowing by enum subclasses
     */
    private static Object getEnum(Class c, JsonObject jsonObj)
    {
        try
        {
            return Enum.valueOf(c, (String) jsonObj.get("name"));
        }
        catch (Exception e)
        {   // In case the enum class has it's own 'name' member variable (shadowing the 'name' variable on Enum)
            return Enum.valueOf(c, (String) jsonObj.get("java.lang.Enum.name"));
        }
    }

    // Parser code

    private Object readJsonObject() throws IOException
    {
        boolean done = false;
        String field = null;
        JsonObject<String, Object> object = new JsonObject<>();
        int state = STATE_READ_START_OBJECT;
        final FastPushbackReader in = input;

        while (!done)
        {
            int c;
            switch (state)
            {
                case STATE_READ_START_OBJECT:
                    c = skipWhitespaceRead();
                    if (c == '{')
                    {
                        object.line = in.line;
                        object.col = in.col;
                        c = skipWhitespaceRead();
                        if (c == '}')
                        {    // empty object
                            return EMPTY_OBJECT;
                        }
                        in.unread(c);
                        state = STATE_READ_FIELD;
                    }
                    else
                    {
                        error("Input is invalid JSON; object does not start with '{', c=" + c);
                    }
                    break;

                case STATE_READ_FIELD:
                    c = skipWhitespaceRead();
                    if (c == '"')
                    {
                        field = readString();
                        c = skipWhitespaceRead();
                        if (c != ':')
                        {
                            error("Expected ':' between string field and value");
                        }
                        skipWhitespace();
                        state = STATE_READ_VALUE;
                    }
                    else
                    {
                        error("Expected quote");
                    }
                    break;

                case STATE_READ_VALUE:
                    if (field == null)
                    {	// field is null when you have an untyped Object[], so we place
                        // the JsonArray on the @items field.
                        field = "@items";
                    }

                    Object value = readValue(object);
                    object.put(field, value);

                    // If object is referenced (has @id), then put it in the _objsRead table.
                    if ("@id".equals(field))
                    {
                        _objsRead.put((Long) value, object);
                    }
                    state = STATE_READ_POST_VALUE;
                    break;

                case STATE_READ_POST_VALUE:
                    c = skipWhitespaceRead();
                    if (c == -1)
                    {
                        error("EOF reached before closing '}'");
                    }
                    if (c == '}')
                    {
                        done = true;
                    }
                    else if (c == ',')
                    {
                        state = STATE_READ_FIELD;
                    }
                    else
                    {
                        error("Object not ended with '}'");
                    }
                    break;
            }
        }

        if (noObjects && object.isPrimitive())
        {
            return object.getPrimitiveValue();
        }

        return object;
    }

    private Object readValue(JsonObject object) throws IOException
    {
        final int c = input.read();
        switch(c)
        {
            case '"':
                return readString();
            case '{':
                input.unread('{');
                return readJsonObject();
            case '[':
                return readArray(object);
            case ']':   // empty array
                input.unread(']');
                return EMPTY_ARRAY;
            case 'f':
            case 'F':
                input.unread(c);
                readToken("false");
                return Boolean.FALSE;
            case 'n':
            case 'N':
                input.unread(c);
                readToken("null");
                return null;
            case 't':
            case 'T':
                input.unread(c);
                readToken("true");
                return Boolean.TRUE;
            case -1:
                error("EOF reached prematurely");
        }

        if (isDigit(c) || c == '-')
        {
            return readNumber(c);
        }
        return error("Unknown JSON value type");
    }

    /**
     * Read a JSON array
     */
    private Object readArray(JsonObject object) throws IOException
    {
        final Collection array = new ArrayList();

        while (true)
        {
            skipWhitespace();
            final Object o = readValue(object);
            if (o != EMPTY_ARRAY)
            {
                array.add(o);
            }
            final int c = skipWhitespaceRead();

            if (c == ']')
            {
                break;
            }
            else if (c != ',')
            {
                error("Expected ',' or ']' inside array");
            }
        }

        return array.toArray();
    }

    /**
     * Return the specified token from the reader.  If it is not found,
     * throw an IOException indicating that.  Converting to c to
     * (char) c is acceptable because the 'tokens' allowed in a
     * JSON input stream (true, false, null) are all ASCII.
     */
    private String readToken(String token) throws IOException
    {
        final int len = token.length();

        for (int i = 0; i < len; i++)
        {
            int c = input.read();
            if (c == -1)
            {
                error("EOF reached while reading token: " + token);
            }
            c = Character.toLowerCase((char) c);
            int loTokenChar = token.charAt(i);

            if (loTokenChar != c)
            {
                error("Expected token: " + token);
            }
        }

        return token;
    }

    /**
     * Read a JSON number
     *
     * @param c int a character representing the first digit of the number that
     *          was already read.
     * @return a Number (a Long or a Double) depending on whether the number is
     *         a decimal number or integer.  This choice allows all smaller types (Float, int, short, byte)
     *         to be represented as well.
     * @throws IOException for stream errors or parsing errors.
     */
    private Number readNumber(int c) throws IOException
    {
        final FastPushbackReader in = input;
        final char[] buffer = this.numBuf;
        buffer[0] = (char) c;
        int len = 1;
        boolean isFloat = false;

        try
        {
            while (true)
            {
                c = in.read();
                if ((c >= '0' && c <= '9') || c == '-' || c == '+')     // isDigit() inlined for speed here
                {
                    buffer[len++] = (char) c;
                }
                else if (c == '.' || c == 'e' || c == 'E')
                {
                    buffer[len++] = (char) c;
                    isFloat = true;
                }
                else if (c == -1)
                {
                    break;
                }
                else
                {
                    in.unread(c);
                    break;
                }
            }
        }
        catch (ArrayIndexOutOfBoundsException e)
        {
            error("Too many digits in number: " + new String(buffer));
        }

        if (isFloat)
        {   // Floating point number needed
            String num = new String(buffer, 0, len);
            try
            {
                return Double.parseDouble(num);
            }
            catch (NumberFormatException e)
            {
                error("Invalid floating point number: " + num, e);
            }
        }
        boolean isNeg = buffer[0] == '-';
        long n = 0;
        for (int i = (isNeg ? 1 : 0); i < len; i++)
        {
            n = (buffer[i] - '0') + n * 10;
        }
        return isNeg ? -n : n;
    }

    /**
     * Read a JSON string
     * This method assumes the initial quote has already been read.
     *
     * @return String read from JSON input stream.
     * @throws IOException for stream errors or parsing errors.
     */
    private String readString() throws IOException
    {
        final StringBuilder str = this.strBuf;
        str.setLength(0);
        StringBuilder hex = new StringBuilder();
        boolean done = false;
        final int STATE_STRING_START = 0;
        final int STATE_STRING_SLASH = 1;
        final int STATE_HEX_DIGITS = 2;
        int state = STATE_STRING_START;

        while (!done)
        {
            final int c = input.read();
            if (c == -1)
            {
                error("EOF reached while reading JSON string");
            }

            switch (state)
            {
                case STATE_STRING_START:
                    if (c == '\\')
                    {
                        state = STATE_STRING_SLASH;
                    }
                    else if (c == '"')
                    {
                        done = true;
                    }
                    else
                    {
                        str.append(toChars(c));
                    }
                    break;

                case STATE_STRING_SLASH:
                    switch(c)
                    {
                        case '\\':
                            str.append('\\');
                            break;
                        case '/':
                            str.append('/');
                            break;
                        case '"':
                            str.append('"');
                            break;
                        case '\'':
                            str.append('\'');
                            break;
                        case 'b':
                            str.append('\b');
                            break;
                        case 'f':
                            str.append('\f');
                            break;
                        case 'n':
                            str.append('\n');
                            break;
                        case 'r':
                            str.append('\r');
                            break;
                        case 't':
                            str.append('\t');
                            break;
                        case 'u':
                            state = STATE_HEX_DIGITS;
                            hex.setLength(0);
                            break;
                        default:
                            error("Invalid character escape sequence specified: " + c);
                    }

                    if (c != 'u')
                    {
                        state = STATE_STRING_START;
                    }
                    break;

                case STATE_HEX_DIGITS:
                    switch(c)
                    {
                        case '0':
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                        case '8':
                        case '9':
                        case 'A':
                        case 'B':
                        case 'C':
                        case 'D':
                        case 'E':
                        case 'F':
                        case 'a':
                        case 'b':
                        case 'c':
                        case 'd':
                        case 'e':
                        case 'f':
                            hex.append((char) c);
                            if (hex.length() == 4)
                            {
                                int value = Integer.parseInt(hex.toString(), 16);
                                str.append(valueOf((char) value));
                                state = STATE_STRING_START;
                            }
                            break;
                        default:
                            error("Expected hexadecimal digits");
                    }
                    break;
            }
        }

        String s = str.toString();
        String cacheHit = stringCache.get(s);
        return cacheHit == null ? s : cacheHit;
    }

    private static Object newInstance(Class c) throws IOException
    {
        if (factory.containsKey(c))
        {
            return factory.get(c).newInstance(c);
        }
        if (unmodifiableSortedMap.getClass().isAssignableFrom(c))
        {
            return new TreeMap();
        }
        if (unmodifiableMap.getClass().isAssignableFrom(c))
        {
            return new LinkedHashMap();
        }
        if (unmodifiableSortedSet.getClass().isAssignableFrom(c))
        {
            return new TreeSet();
        }
        if (unmodifiableSet.getClass().isAssignableFrom(c))
        {
            return new LinkedHashSet();
        }
        if (unmodifiableCollection.getClass().isAssignableFrom(c))
        {
            return new ArrayList();
        }

        // Constructor not cached, go find a constructor
        Object[] constructorInfo = constructors.get(c);
        if (constructorInfo != null)
        {   // Constructor was cached
            Constructor constructor = (Constructor) constructorInfo[0];

            if (constructor == null && useUnsafe)
            {   // null constructor --> set to null when object instantiated with unsafe.allocateInstance()
                try
                {
                    return unsafe.allocateInstance(c);
                }
                catch (Exception e)
                {
                    // Should never happen, as the code that fetched the constructor was able to instantiate it once already
                    error("Could not instantiate " + c.getName(), e);
                }
            }

            Boolean useNull = (Boolean) constructorInfo[1];
            Class[] paramTypes = constructor.getParameterTypes();
            if (paramTypes == null || paramTypes.length == 0)
            {
                try
                {
                    return constructor.newInstance();
                }
                catch (Exception e)
                {   // Should never happen, as the code that fetched the constructor was able to instantiate it once already
                    error("Could not instantiate " + c.getName(), e);
                }
            }
            Object[] values = fillArgs(paramTypes, useNull);
            try
            {
                return constructor.newInstance(values);
            }
            catch (Exception e)
            {   // Should never happen, as the code that fetched the constructor was able to instantiate it once already
                error("Could not instantiate " + c.getName(), e);
            }
        }

        Object[] ret = newInstanceEx(c);
        constructors.put(c, new Object[]{ret[1], ret[2]});
        return ret[0];
    }

    /**
     * Return constructor and instance as elements 0 and 1, respectively.
     */
    private static Object[] newInstanceEx(Class c) throws IOException
    {
        try
        {
            Constructor constructor = c.getConstructor(emptyClassArray);
            if (constructor != null)
            {
                return new Object[] {constructor.newInstance(), constructor, true};
            }
            return tryOtherConstruction(c);
        }
        catch (Exception e)
        {
            // OK, this class does not have a public no-arg constructor.  Instantiate with
            // first constructor found, filling in constructor values with null or
            // defaults for primitives.
            return tryOtherConstruction(c);
        }
    }

    private static Object[] tryOtherConstruction(Class c) throws IOException
    {
        Constructor[] constructors = c.getDeclaredConstructors();
        if (constructors.length == 0)
        {
            error("Cannot instantiate '" + c.getName() + "' - Primitive, interface, array[] or void");
        }

        // Try each constructor (private, protected, or public) with null values for non-primitives.
        for (Constructor constructor : constructors)
        {
            constructor.setAccessible(true);
            Class[] argTypes = constructor.getParameterTypes();
            Object[] values = fillArgs(argTypes, true);
            try
            {
                return new Object[] {constructor.newInstance(values), constructor, true};
            }
            catch (Exception ignored)
            { }
        }

        // Try each constructor (private, protected, or public) with non-null values for primitives.
        for (Constructor constructor : constructors)
        {
            constructor.setAccessible(true);
            Class[] argTypes = constructor.getParameterTypes();
            Object[] values = fillArgs(argTypes, false);
            try
            {
                return new Object[] {constructor.newInstance(values), constructor, false};
            }
            catch (Exception ignored)
            { }
        }

        // Try instantiation via unsafe
        // This may result in heapdumps for e.g. ConcurrentHashMap or can cause problems when the class is not initialized
        // Thats why we try ordinary constructors first
        if (useUnsafe)
        {
            try
            {
                return new Object[]{unsafe.allocateInstance(c), null, null};
            }
            catch (Exception ignored)
            { }
        }

        error("Could not instantiate " + c.getName() + " using any constructor");
        return null;
    }

    private static Object[] fillArgs(Class[] argTypes, boolean useNull) throws IOException
    {
        final Object[] values = new Object[argTypes.length];
        for (int i = 0; i < argTypes.length; i++)
        {
            final Class argType = argTypes[i];
            if (isPrimitive(argType))
            {
                values[i] = newPrimitiveWrapper(argType, null);
            }
            else if (useNull)
            {
                values[i] = null;
            }
            else
            {
                if (argType == String.class)
                {
                    values[i] = "";
                }
                else if (argType == Date.class)
                {
                    values[i] = new Date();
                }
                else if (List.class.isAssignableFrom(argType))
                {
                    values[i] = new ArrayList();
                }
                else if (SortedSet.class.isAssignableFrom(argType))
                {
                    values[i] = new TreeSet();
                }
                else if (Set.class.isAssignableFrom(argType))
                {
                    values[i] = new LinkedHashSet();
                }
                else if (SortedMap.class.isAssignableFrom(argType))
                {
                    values[i] = new TreeMap();
                }
                else if (Map.class.isAssignableFrom(argType))
                {
                    values[i] = new LinkedHashMap();
                }
                else if (Collection.class.isAssignableFrom(argType))
                {
                    values[i] = new ArrayList();
                }
                else if (Calendar.class.isAssignableFrom(argType))
                {
                    values[i] = Calendar.getInstance();
                }
                else if (TimeZone.class.isAssignableFrom(argType))
                {
                    values[i] = TimeZone.getDefault();
                }
                else if (argType == BigInteger.class)
                {
                    values[i] = BigInteger.TEN;
                }
                else if (argType == BigDecimal.class)
                {
                    values[i] = BigDecimal.TEN;
                }
                else if (argType == StringBuilder.class)
                {
                    values[i] = new StringBuilder();
                }
                else if (argType == StringBuffer.class)
                {
                    values[i] = new StringBuffer();
                }
                else if (argType == Locale.class)
                {
                    values[i] = Locale.FRANCE;  // overwritten
                }
                else if (argType == Class.class)
                {
                    values[i] = String.class;
                }
                else if (argType == java.sql.Timestamp.class)
                {
                    values[i] = new Timestamp(System.currentTimeMillis());
                }
                else if (argType == java.sql.Date.class)
                {
                    values[i] = new java.sql.Date(System.currentTimeMillis());
                }
                else if (argType == java.net.URL.class)
                {
                    values[i] = new URL("http://localhost"); // overwritten
                }
                else if (argType == Object.class)
                {
                    values[i] = new Object();
                }
                else
                {
                    values[i] = null;
                }
            }
        }

        return values;
    }

    public static boolean isPrimitive(Class c)
    {
        return c.isPrimitive() || prims.contains(c);
    }

    public static boolean isLogicalPrimitive(Class c)
    {
        return isPrimitive(c) || String.class.isAssignableFrom(c) || Number.class.isAssignableFrom(c) || Date.class.isAssignableFrom(c);
    }

    private static Object newPrimitiveWrapper(Class c, Object rhs) throws IOException
    {

        final String cname = c.getName();
        switch(cname)
        {
            case "boolean":
            case "java.lang.Boolean":
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "false";
                    }
                    return Boolean.parseBoolean((String)rhs);
                }
                return rhs != null ? rhs : Boolean.FALSE;
            case "byte":
            case "java.lang.Byte":
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "0";
                    }
                    return Byte.parseByte((String)rhs);
                }
                return rhs != null ? byteCache[((Number) rhs).byteValue() + 128] : (byte) 0;
            case "char":
            case "java.lang.Character":
                if (rhs == null)
                {
                    return '\u0000';
                }
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "\u0000";
                    }
                    return valueOf(((String) rhs).charAt(0));
                }
                if (rhs instanceof Character)
                {
                    return rhs;
                }
                break;
            case "double":
            case "java.lang.Double":
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "0.0";
                    }
                    return Double.parseDouble((String)rhs);
                }
                return rhs != null ? rhs : 0.0d;
            case "float":
            case "java.lang.Float":
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "0.0f";
                    }
                    return Float.parseFloat((String)rhs);
                }
                return rhs != null ? ((Number) rhs).floatValue() : 0.0f;
            case "int":
            case "java.lang.Integer":
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "0";
                    }
                    return Integer.parseInt((String)rhs);
                }
                return rhs != null ? ((Number) rhs).intValue() : 0;
            case "long":
            case "java.lang.Long":
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "0";
                    }
                    return Long.parseLong((String)rhs);
                }
                return rhs != null ? rhs : 0L;
            case "short":
            case "java.lang.Short":
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "0";
                    }
                    return Short.parseShort((String)rhs);
                }
                return rhs != null ? ((Number) rhs).shortValue() : (short) 0;
        }

        return error("Class '" + cname + "' requested for special instantiation - isPrimitive() does not match newPrimitiveWrapper()");
    }

    static String removeLeadingAndTrailingQuotes(String s)
    {
        Matcher m = extraQuotes.matcher(s);
        if (m.find())
        {
            s = m.group(2);
        }
        return s;
    }

    private static boolean isDigit(int c)
    {
        return c >= '0' && c <= '9';
    }

    static Class classForName(String name) throws IOException
    {
        if (name == null || name.isEmpty())
        {
            error("Empty class name");
        }
        try
        {
            Class c = nameToClass.get(name);
            return c == null ? loadClass(name) : c;
        }
        catch (ClassNotFoundException e)
        {
            return (Class) error("Class instance '" + name + "' could not be created", e);
        }
    }

    // loadClass() provided by: Thomas Margreiter
    private static Class loadClass(String name) throws ClassNotFoundException
    {
        String className = name;
        boolean arrayType = false;
        Class primitiveArray = null;

        while (className.startsWith("["))
        {
            arrayType = true;
            if (className.endsWith(";"))
            {
                className = className.substring(0, className.length() - 1);
            }
            switch (className)
            {
                case "[B":
                    primitiveArray = byte[].class;
                    break;
                case "[S":
                    primitiveArray = short[].class;
                    break;
                case "[I":
                    primitiveArray = int[].class;
                    break;
                case "[J":
                    primitiveArray = long[].class;
                    break;
                case "[F":
                    primitiveArray = float[].class;
                    break;
                case "[D":
                    primitiveArray = double[].class;
                    break;
                case "[Z":
                    primitiveArray = boolean[].class;
                    break;
                case "[C":
                    primitiveArray = char[].class;
                    break;
            }
            int startpos = className.startsWith("[L") ? 2 : 1;
            className = className.substring(startpos);
        }
        Class currentClass = null;
        if (null == primitiveArray)
        {
            currentClass = Thread.currentThread().getContextClassLoader().loadClass(className);
        }

        if (arrayType)
        {
            currentClass = (null != primitiveArray) ? primitiveArray : Array.newInstance(currentClass, 0).getClass();
            while (name.startsWith("[["))
            {
                currentClass = Array.newInstance(currentClass, 0).getClass();
                name = name.substring(1);
            }
        }
        return currentClass;
    }

    /**
     * Get a Field object using a String field name and a Class instance.  This
     * method will start on the Class passed in, and if not found there, will
     * walk up super classes until it finds the field, or throws an IOException
     * if it cannot find the field.
     *
     * @param c Class containing the desired field.
     * @param fieldName String name of the desired field.
     * @return Field object obtained from the passed in class (by name).  The Field
     *         returned is cached so that it is only obtained via reflection once.
     */
    private static Field getDeclaredField(Class c, String fieldName)
    {
        return JsonWriter.getDeepDeclaredFields(c).get(fieldName);
    }

    /**
     * Read until non-whitespace character and then return it.
     * This saves extra read/pushback.
     *
     * @return int representing the next non-whitespace character in the stream.
     * @throws IOException for stream errors or parsing errors.
     */
    private int skipWhitespaceRead() throws IOException
    {
        final FastPushbackReader in = input;
        int c = in.read();
        while (true)
        {
            switch (c)
            {
                case '\t':
                case '\n':
                case '\r':
                case ' ':
                    break;
                default:
                    return c;
            }

            c = in.read();
        }
    }

    private void skipWhitespace() throws IOException
    {
        input.unread(skipWhitespaceRead());
    }

    public void close()
    {
        try
        {
            threadInput.remove();
            if (input != null)
            {
                input.close();
            }
        }
        catch (IOException ignored) { }
    }

    /**
     * For all fields where the value was "@ref":"n" where 'n' was the id of an object
     * that had not yet been encountered in the stream, make the final substitution.
     * @throws IOException
     */
    private void patchUnresolvedReferences() throws IOException
    {
        Iterator i = unresolvedRefs.iterator();
        while (i.hasNext())
        {
            UnresolvedReference ref = (UnresolvedReference) i.next();
            Object objToFix = ref.referencingObj.target;
            JsonObject objReferenced = _objsRead.get(ref.refId);

            if (objReferenced == null)
            {
                // System.err.println("Back reference (" + ref.refId + ") does not match any object id in input, field '" + ref.field + '\'');
                continue;
            }

            if (objReferenced.target == null)
            {
                // System.err.println("Back referenced object does not exist,  @ref " + ref.refId + ", field '" + ref.field + '\'');
                continue;
            }

            if (objToFix == null)
            {
                // System.err.println("Referencing object is null, back reference, @ref " + ref.refId + ", field '" + ref.field + '\'');
                continue;
            }

            if (ref.index >= 0)
            {    // Fix []'s and Collections containing a forward reference.
                if (objToFix instanceof List)
                {   // Patch up Indexable Collections
                    List list = (List) objToFix;
                    list.set(ref.index, objReferenced.target);
                }
                else if (objToFix instanceof Collection)
                {   // Add element (since it was not indexable, add it to collection)
                    Collection col = (Collection) objToFix;
                    col.add(objReferenced.target);
                }
                else
                {
                    Array.set(objToFix, ref.index, objReferenced.target);        // patch array element here
                }
            }
            else
            {    // Fix field forward reference
                Field field = getDeclaredField(objToFix.getClass(), ref.field);
                if (field != null)
                {
                    try
                    {
                        field.set(objToFix, objReferenced.target);               // patch field here
                    }
                    catch (Exception e)
                    {
                        error("Error setting field while resolving references '" + field.getName() + "', @ref = " + ref.refId, e);
                    }
                }
            }

            i.remove();
        }

        int count = unresolvedRefs.size();
        if (count > 0)
        {
            StringBuilder out = new StringBuilder();
            out.append(count);
            out.append(" unresolved references:\n");
            i = unresolvedRefs.iterator();
            count = 1;

            while (i.hasNext())
            {
                UnresolvedReference ref = (UnresolvedReference) i.next();
                out.append("    Unresolved reference ");
                out.append(count);
                out.append('\n');
                out.append("        @ref ");
                out.append(ref.refId);
                out.append('\n');
                out.append("        field ");
                out.append(ref.field);
                out.append("\n\n");
                count++;
            }
            error(out.toString());
        }
    }

    /**
     * Process Maps/Sets (fix up their internal indexing structure)
     * This is required because Maps hash items using hashCode(), which will
     * change between VMs.  Rehashing the map fixes this.
     *
     * If noObjects==true, then move @keys to keys and @items to values
     * and then drop these two entries from the map.
     *
     * This hashes both Sets and Maps because the JDK sets are implemented
     * as Maps.  If you have a custom built Set, this would not 'treat' it
     * and you would need to provider a custom reader for that set.
     */
    private void rehashMaps()
    {
        final boolean useMaps = noObjects;
        for (Object[] mapPieces : prettyMaps)
        {
            JsonObject jObj = (JsonObject)  mapPieces[0];
            Object[] javaKeys, javaValues;
            Map map;

            if (useMaps)
            {   // Make the @keys be the actual keys of the map.
                map = jObj;
                javaKeys = (Object[]) jObj.remove("@keys");
                javaValues = (Object[]) jObj.remove("@items");
            }
            else
            {
                map = (Map) jObj.target;
                javaKeys = (Object[]) mapPieces[1];
                javaValues = (Object[]) mapPieces[2];
                jObj.clear();
            }

            int j=0;

            while (javaKeys != null && j < javaKeys.length)
            {
                map.put(javaKeys[j], javaValues[j]);
                j++;
            }
        }
    }

    private static String getErrorMessage(String msg)
    {
        if (threadInput.get() != null)
        {
            return msg + "\nLast read: " + getLastReadSnippet() + "\nline: " + threadInput.get().line + ", col: " + threadInput.get().col;
        }
        return msg;
    }

    static Object error(String msg) throws IOException
    {
        throw new IOException(getErrorMessage(msg));
    }

    static Object error(String msg, Exception e) throws IOException
    {
        throw new IOException(getErrorMessage(msg), e);
    }

    private static String getLastReadSnippet()
    {
        if (threadInput.get() != null)
        {
            return threadInput.get().getLastSnippet();
        }
        return "";
    }

    /**
     * This is a performance optimization.  The lowest 128 characters are re-used.
     *
     * @param c char to match to a Character.
     * @return a Character that matches the passed in char.  If the value is
     *         less than 127, then the same Character instances are re-used.
     */
    private static Character valueOf(char c)
    {
        return c <= 127 ? charCache[(int) c] : c;
    }

    public static final int MAX_CODE_POINT = 0x10ffff;
    public static final int MIN_SUPPLEMENTARY_CODE_POINT = 0x010000;
    public static final char MIN_LOW_SURROGATE = '\uDC00';
    public static final char MIN_HIGH_SURROGATE = '\uD800';

    private static char[] toChars(final int codePoint)
    {
        if (codePoint < 0 || codePoint > MAX_CODE_POINT)
        {    // int UTF-8 char must be in range
            throw new IllegalArgumentException("value ' + codePoint + ' outside UTF-8 range");
        }

        if (codePoint < MIN_SUPPLEMENTARY_CODE_POINT)
        {    // if the int character fits in two bytes...
            return new char[]{(char) codePoint};
        }

        final char[] result = new char[2];
        final int offset = codePoint - MIN_SUPPLEMENTARY_CODE_POINT;
        result[1] = (char) ((offset & 0x3ff) + MIN_LOW_SURROGATE);
        result[0] = (char) ((offset >>> 10) + MIN_HIGH_SURROGATE);
        return result;
    }

    /**
     * Wrapper for unsafe, decouples direct usage of sun.misc.* package.
     * @author Kai Hufenback
     */
    static final class Unsafe
    {
    	private final Object sunUnsafe;
    	private final Method allocateInstance;

    	/**
    	 * Constructs unsafe object, acting as a wrapper.
    	 * @throws ReflectiveOperationException
    	 */
    	public Unsafe() throws ReflectiveOperationException
        {
    		try
            {
    			Constructor<Unsafe> unsafeConstructor = JsonReader.classForName("sun.misc.Unsafe").getDeclaredConstructor();
    			unsafeConstructor.setAccessible(true);
                sunUnsafe = unsafeConstructor.newInstance();
    			allocateInstance = sunUnsafe.getClass().getMethod("allocateInstance", Class.class);
    			allocateInstance.setAccessible(true);
    		}
            catch(Exception e)
            {
    			throw new ReflectiveOperationException(e);
    		}
    	}

    	/**
    	 * Creates an object without invoking constructor or initializing variables.
    	 * <b>Be careful using this with JDK objects, like URL or ConcurrentHashMap this may bring your VM into troubles.</b>
    	 * @param clazz to instantiate
    	 * @return allocated Object
    	 */
        public Object allocateInstance(Class clazz)
        {
            try
            {
                return allocateInstance.invoke(sunUnsafe, clazz);
            }
            catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * This class adds significant performance increase over using the JDK
     * PushbackReader.  This is due to this class not using synchronization
     * as it is not needed.
     */
    private static final class FastPushbackReader extends FilterReader
    {
        private final int[] buf;
        private final int[] snippet;
        private int idx;
        private int line;
        private int col;
        private int snippetLoc = 0;

        private FastPushbackReader(Reader reader, int size)
        {
            super(reader);
            if (size <= 0)
            {
                throw new IllegalArgumentException("size <= 0");
            }
            buf = new int[size];
            idx = size;
            snippet = new int[SNIPPET_LENGTH];
            line = 1;
            col = 0;
        }

        private FastPushbackReader(Reader r)
        {
            this(r, 1);
        }

        private String getLastSnippet()
        {
            StringBuilder s = new StringBuilder();
            for (int i=snippetLoc; i < SNIPPET_LENGTH; i++)
            {
                if (addCharToSnippet(s, i))
                {
                    break;
                }
            }
            for (int i=0; i < snippetLoc; i++)
            {
                if (addCharToSnippet(s, i))
                {
                    break;
                }
            }
            return s.toString();
        }

        private boolean addCharToSnippet(StringBuilder s, int i)
        {
            char[] character;
            try
            {
                character = toChars(snippet[i]);
            }
            catch (Exception e)
            {
                return true;
            }
            if (snippet[i] != 0)
            {
                s.append(character);
            }
            else
            {
                return true;
            }
            return false;
        }

        public int read() throws IOException
        {
            final int ch = idx < buf.length ? buf[idx++] : super.read();
            if (ch >= 0)
            {
                if (ch == 0x0a)
                {
                    line++;
                    col = 0;
                }
                else
                {
                    col++;
                }
                snippet[snippetLoc++] = ch;
                if (snippetLoc >= SNIPPET_LENGTH)
                {
                    snippetLoc = 0;
                }
            }
            return ch;
        }

        public void unread(int c) throws IOException
        {
            if (idx == 0)
            {
                error("unread(int c) called more than buffer size (" + buf.length + ")");
            }
            if (c == 0x0a)
            {
                line--;
            }
            else
            {
                col--;
            }
            buf[--idx] = c;
            snippetLoc--;
            if (snippetLoc < 0)
            {
                snippetLoc = SNIPPET_LENGTH - 1;
            }
            snippet[snippetLoc] = c;
        }
    }
}
