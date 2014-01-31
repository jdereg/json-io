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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
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
 * Call the static method: {@code JsonReader.objectToJava(String json)}.  This will
 * return a typed Java object graph.</li>
 * <li>
 * Call the static method: {@code JsonReader.jsonToMaps(String json)}.  This will
 * return an untyped object representation of the JSON String as a Map of Maps, where
 * the fields are the Map keys, and the field values are the associated Map's values.  You can
 * call the JsonWriter.objectToJava() method with the returned Map, and it will serialize
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
    private static final String EMPTY_ARRAY = "~!a~";  // compared with ==
    private static final String EMPTY_OBJECT = "~!o~";  // compared with ==
    private static final Character[] _charCache = new Character[128];
    private static final Byte[] _byteCache = new Byte[256];
    private static final Map<String, String> _stringCache = new HashMap<String, String>();
    private static final Set<Class> _prims = new HashSet<Class>();
    private static final Map<Class, Object[]> _constructors = new HashMap<Class, Object[]>();
    private static final Map<String, Class> _nameToClass = new HashMap<String, Class>();
    private static final Class[] _emptyClassArray = new Class[]{};
    private static final List<Object[]> _readers  = new ArrayList<Object[]>();
    private static final Set<Class> _notCustom = new HashSet<Class>();
    private static final Map<String, String> _months = new LinkedHashMap<String, String>();
    private static final Map<Class, ClassFactory> _factory = new LinkedHashMap<Class, ClassFactory>();
    private static final String _mos = "(Jan|January|Feb|February|Mar|March|Apr|April|May|Jun|June|Jul|July|Aug|August|Sep|Sept|September|Oct|October|Nov|November|Dec|December)";
    private static final Pattern _datePattern1 = Pattern.compile("^(\\d{4})[\\./-](\\d{1,2})[\\./-](\\d{1,2})");
    private static final Pattern _datePattern2 = Pattern.compile("^(\\d{1,2})[\\./-](\\d{1,2})[\\./-](\\d{4})");
    private static final Pattern _datePattern3 = Pattern.compile(_mos + "[ ,]+(\\d{1,2})[ ,]+(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern _datePattern4 = Pattern.compile("(\\d{1,2})[ ,]" + _mos + "[ ,]+(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern _datePattern5 = Pattern.compile("(\\d{4})[ ,]" + _mos + "[ ,]+(\\d{1,2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern _timePattern1 = Pattern.compile("(\\d{2})[:.](\\d{2})[:.](\\d{2})[.](\\d{1,3})");
    private static final Pattern _timePattern2 = Pattern.compile("(\\d{2})[:.](\\d{2})[:.](\\d{2})");
    private static final Pattern _timePattern3 = Pattern.compile("(\\d{2})[:.](\\d{2})");
    private static final Pattern _extraQuotes = Pattern.compile("([\"]*)([^\"]*)([\"]*)");

    private final Map<Long, JsonObject> _objsRead = new LinkedHashMap<Long, JsonObject>();
    private final Collection<UnresolvedReference> _unresolvedRefs = new ArrayList<UnresolvedReference>();
    private final Collection<Object[]> _prettyMaps = new ArrayList<Object[]>();
    private final FastPushbackReader _in;
    private boolean _noObjects = false;
    private final char[] _numBuf = new char[256];
    private final StringBuilder _strBuf = new StringBuilder();

    static final ThreadLocal<Deque<char[]>> _snippet = new ThreadLocal<Deque<char[]>>()
    {
        public Deque<char[]> initialValue()
        {
            return new ArrayDeque<char[]>(128);
        }
    };
    static final ThreadLocal<Integer> _line = new ThreadLocal<Integer>()
    {
        public Integer initialValue()
        {
            return 1;
        }
    };

    static final ThreadLocal<Integer> _col = new ThreadLocal<Integer>()
    {
        public Integer initialValue()
        {
            return 1;
        }
    };

    static
    {
        // Save memory by re-using common Characters (Characters are immutable)
        for (int i = 0; i < _charCache.length; i++)
        {
            _charCache[i] = (char) i;
        }

        // Save memory by re-using all byte instances (Bytes are immutable)
        for (int i = 0; i < _byteCache.length; i++)
        {
            _byteCache[i] = (byte) (i - 128);
        }

        // Save heap memory by re-using common strings (String's immutable)
        _stringCache.put("", "");
        _stringCache.put("true", "true");
        _stringCache.put("True", "True");
        _stringCache.put("TRUE", "TRUE");
        _stringCache.put("false", "false");
        _stringCache.put("False", "False");
        _stringCache.put("FALSE", "FALSE");
        _stringCache.put("null", "null");
        _stringCache.put("yes", "yes");
        _stringCache.put("Yes", "Yes");
        _stringCache.put("YES", "YES");
        _stringCache.put("no", "no");
        _stringCache.put("No", "No");
        _stringCache.put("NO", "NO");
        _stringCache.put("on", "on");
        _stringCache.put("On", "On");
        _stringCache.put("ON", "ON");
        _stringCache.put("off", "off");
        _stringCache.put("Off", "Off");
        _stringCache.put("OFF", "OFF");
        _stringCache.put("@id", "@id");
        _stringCache.put("@ref", "@ref");
        _stringCache.put("@items", "@items");
        _stringCache.put("@type", "@type");
        _stringCache.put("@keys", "@keys");
        _stringCache.put("0", "0");
        _stringCache.put("1", "1");
        _stringCache.put("2", "2");
        _stringCache.put("3", "3");
        _stringCache.put("4", "4");
        _stringCache.put("5", "5");
        _stringCache.put("6", "6");
        _stringCache.put("7", "7");
        _stringCache.put("8", "8");
        _stringCache.put("9", "9");

        _prims.add(Byte.class);
        _prims.add(Integer.class);
        _prims.add(Long.class);
        _prims.add(Double.class);
        _prims.add(Character.class);
        _prims.add(Float.class);
        _prims.add(Boolean.class);
        _prims.add(Short.class);

        _nameToClass.put("string", String.class);
        _nameToClass.put("boolean", boolean.class);
        _nameToClass.put("char", char.class);
        _nameToClass.put("byte", byte.class);
        _nameToClass.put("short", short.class);
        _nameToClass.put("int", int.class);
        _nameToClass.put("long", long.class);
        _nameToClass.put("float", float.class);
        _nameToClass.put("double", double.class);
        _nameToClass.put("date", Date.class);
        _nameToClass.put("class", Class.class);

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
        assignInstantiator(Collection.class, colFactory);

        ClassFactory mapFactory = new MapFactory();
        assignInstantiator(Map.class, mapFactory);
        assignInstantiator(SortedMap.class, mapFactory);

        // Month name to number map
        _months.put("jan", "1");
        _months.put("january", "1");
        _months.put("feb", "2");
        _months.put("february", "2");
        _months.put("mar", "3");
        _months.put("march", "3");
        _months.put("apr", "4");
        _months.put("april", "4");
        _months.put("may", "5");
        _months.put("jun", "6");
        _months.put("june", "6");
        _months.put("jul", "7");
        _months.put("july", "7");
        _months.put("aug", "8");
        _months.put("august", "8");
        _months.put("sep", "9");
        _months.put("sept", "9");
        _months.put("september", "9");
        _months.put("oct", "10");
        _months.put("october", "10");
        _months.put("nov", "11");
        _months.put("november", "11");
        _months.put("dec", "12");
        _months.put("december", "12");
    }

    public interface JsonClassReader
    {
        Object read(Object jOb, LinkedList<JsonObject<String, Object>> stack) throws IOException;
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
    public static void assignInstantiator(Class c, ClassFactory factory)
    {
        _factory.put(c, factory);
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
        public Object read(Object o, LinkedList<JsonObject<String, Object>> stack) throws IOException
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
        public Object read(Object o, LinkedList<JsonObject<String, Object>> stack) throws IOException
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
        public Object read(Object o, LinkedList<JsonObject<String, Object>> stack) throws IOException
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
                    c = classForName2((String) type);
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
        public Object read(Object o, LinkedList<JsonObject<String, Object>> stack) throws IOException
        {
            if (o instanceof Long)
            {
                return new Date((Long) o);
            }
            else if (o instanceof String)
            {
                return parseDate((String)o);
            }
            else if (o instanceof JsonObject)
            {
                JsonObject jObj = (JsonObject)o;
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
            dateStr = dateStr.trim();

            // Determine which date pattern (Matcher) to use
            Matcher matcher = _datePattern1.matcher(dateStr);

            String year, month = null, day, mon = null;

            if (matcher.find())
            {
                year = matcher.group(1);
                month = matcher.group(2);
                day = matcher.group(3);
            }
            else
            {
                matcher = _datePattern2.matcher(dateStr);
                if (matcher.find())
                {
                    month = matcher.group(1);
                    day = matcher.group(2);
                    year = matcher.group(3);
                }
                else
                {
                    matcher = _datePattern3.matcher(dateStr);
                    if (matcher.find())
                    {
                        mon = matcher.group(1);
                        day = matcher.group(2);
                        year = matcher.group(3);
                    }
                    else
                    {
                        matcher = _datePattern4.matcher(dateStr);
                        if (matcher.find())
                        {
                            day = matcher.group(1);
                            mon = matcher.group(2);
                            year = matcher.group(3);
                        }
                        else
                        {
                            matcher = _datePattern5.matcher(dateStr);
                            if (!matcher.find())
                            {
                                error("Unable to parse: " + dateStr);
                            }
                            year = matcher.group(1);
                            mon = matcher.group(2);
                            day = matcher.group(3);
                        }
                    }
                }
            }

            if (mon != null)
            {
                month = _months.get(mon.trim().toLowerCase());
                if (month == null)
                {
                    error("Unable to parse month portion of date: " + dateStr);
                }
            }

            // Determine which date pattern (Matcher) to use
            matcher = _timePattern1.matcher(dateStr);
            if (!matcher.find())
            {
                matcher = _timePattern2.matcher(dateStr);
                if (!matcher.find())
                {
                    matcher = _timePattern3.matcher(dateStr);
                    if (!matcher.find())
                    {
                        matcher = null;
                    }
                }
            }

            Calendar c = Calendar.getInstance();
            c.clear();

            // Always parses correctly because of regex.
            int y = Integer.parseInt(year);
            int m = Integer.parseInt(month) - 1;    // months are 0-based
            int d = Integer.parseInt(day);

            if (m < 0 || m > 11)
            {
                error("Month must be between 1 and 12, date: " + dateStr);
            }
            if (d < 0 || d > 31)
            {
                error("Day cannot be > 31, date: " + dateStr);
            }

            if (matcher == null)
            {   // no [valid] time portion
                c.set(y, m, d);
            }
            else
            {
                String hour = matcher.group(1);
                String min = matcher.group(2);
                String sec = "00";
                String milli = "000";
                if (matcher.groupCount() > 2)
                {
                    sec = matcher.group(3);
                }
                if (matcher.groupCount() > 3)
                {
                    milli = matcher.group(4);
                }

                int h = Integer.parseInt(hour);
                int mn = Integer.parseInt(min);
                int s = Integer.parseInt(sec);
                int ms = Integer.parseInt(milli);

                if (h < 0 || h > 23)
                {
                    error("Hour must be between 0 and 23, time: " + dateStr);
                }
                if (mn < 0 || mn > 59)
                {
                    error("Minute must be between 0 and 59, time: " + dateStr);
                }
                if (s < 0 || s > 59)
                {
                    error("Second must be between 0 and 59, time: " + dateStr);
                }

                c.set(y, m, d, h, mn, s);
                c.set(Calendar.MILLISECOND, ms);
            }
            return c.getTime();
        }
    }

    public static class SqlDateReader extends DateReader
    {
        public Object read(Object o, LinkedList<JsonObject<String, Object>> stack) throws IOException
        {
            return new java.sql.Date(((Date) super.read(o, stack)).getTime());
        }
    }

    public static class StringReader implements JsonClassReader
    {
        public Object read(Object o, LinkedList<JsonObject<String, Object>> stack) throws IOException
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
        public Object read(Object o, LinkedList<JsonObject<String, Object>> stack) throws IOException
        {
            if (o instanceof String)
            {
                return classForName2((String)o);
            }

            JsonObject jObj = (JsonObject) o;
            if (jObj.containsKey("value"))
            {
                return jObj.target = classForName2((String) jObj.get("value"));
            }
            return error("Class missing 'value' field");
        }
    }

    public static class BigIntegerReader implements JsonClassReader
    {
        public Object read(Object o, LinkedList<JsonObject<String, Object>> stack) throws IOException
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

            BigInteger x = null;

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

            if (value instanceof String)
            {
                String s = (String) value;
                if ("".equals(s.trim()))
                {
                    return jObj != null ? jObj.target = null : null;
                }
                else
                {
                    x = new BigInteger(removeLeadingAndTrailingQuotes((String) value));
                }
            }

            if (value instanceof BigInteger)
            {
                x = (BigInteger) value;
            }

            if (value instanceof BigDecimal)
            {
                BigDecimal bd = (BigDecimal) value;
                String str = bd.toPlainString();
                if (str.contains("."))
                {
                    return error("Cannot assign BigDecimal to BigInteger if BigDecimal has fractional part: " + value);
                }
                x = new BigInteger(str);
            }

            if (value instanceof Boolean)
            {
                x = new BigInteger(((Boolean) value) ? "1" : "0");
            }

            if (value instanceof Double || value instanceof Float)
            {
                return error("Cannot assign floating point value to a BigInteger: " + value);
            }

            if (value instanceof Long || value instanceof Integer ||
                value instanceof Short || value instanceof Byte)
            {
                x = new BigInteger(value.toString());
            }

            if (jObj != null)
            {
                jObj.target = x;
            }

            return x != null ? x : error("BigInteger 'value' convertible to a BigInteger: " + value);
        }
    }

    public static class BigDecimalReader implements JsonClassReader
    {
        public Object read(Object o, LinkedList<JsonObject<String, Object>> stack) throws IOException
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

            BigDecimal x = null;

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

            if (value instanceof String)
            {
                String s = (String) value;
                if ("".equals(s.trim()))
                {
                    return jObj != null ? jObj.target = null : null;
                }
                else
                {
                    x = new BigDecimal(removeLeadingAndTrailingQuotes((String) value));
                }
            }

            if (value instanceof BigDecimal)
            {
                x = (BigDecimal) value;
            }

            if (value instanceof BigInteger)
            {
                x = new BigDecimal((BigInteger)value);
            }

            if (value instanceof Boolean)
            {
                x = new BigDecimal(((Boolean) value) ? "1" : "0");
            }

            if (value instanceof Long || value instanceof Double || value instanceof Integer ||
                value instanceof Short || value instanceof Byte || value instanceof Float || value instanceof BigInteger)
            {
                x = new BigDecimal(value.toString());
            }

            if (jObj != null)
            {
                jObj.target = x;
            }
            return x != null ? x : error("BigDecimal missing 'value' field not convertible to a BigDecimal: " + value);
        }
    }

    public static class StringBuilderReader implements JsonClassReader
    {
        public Object read(Object o, LinkedList<JsonObject<String, Object>> stack) throws IOException
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
        public Object read(Object o, LinkedList<JsonObject<String, Object>> stack) throws IOException
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
        public Object read(Object o, LinkedList<JsonObject<String, Object>> stack) throws IOException
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
        for (Object[] item : _readers)
        {
            Class clz = (Class)item[0];
            if (clz == c)
            {
                item[1] = reader;   // Replace reader
                return;
            }
        }
        _readers.add(new Object[] {c, reader});
    }

    public static void addNotCustomReader(Class c)
    {
        _notCustom.add(c);
    }

    protected Object readIfMatching(Object o, Class compType, LinkedList<JsonObject<String, Object>> stack) throws IOException
    {
        if (o == null)
        {
            error("Bug in json-io, null must be checked before calling this method.");
        }

        if (_notCustom.contains(o.getClass()))
        {
            return null;
        }

        if (compType != null)
        {
            if (_notCustom.contains(compType))
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

        JsonClassReader closestReader = null;
        int minDistance = Integer.MAX_VALUE;

        for (Object[] item : _readers)
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

    /**
     * UnresolvedReference is created to hold a logical pointer to a reference that
     * could not yet be loaded, as the @ref appears ahead of the referenced object's
     * definition.  This can point to a field reference or an array/Collection element reference.
     */
    private static class UnresolvedReference
    {
        private JsonObject referencingObj;
        private String field;
        private long refId;
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
        throw new RuntimeException("Use com.cedarsoftware.util.JsonReader");
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
        try
        {
            return jsonToMaps(json);
        }
        catch (Exception ignored)
        {
            return null;
        }
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
        Map map = (Map) jr.readObject();
        jr.close();
        return map;
    }

    public JsonReader()
    {
        _noObjects = false;
        _in = null;
    }

    public JsonReader(InputStream in)
    {
        this(in, false);
    }

    public JsonReader(InputStream in, boolean noObjects)
    {
        _noObjects = noObjects;
        try
        {
            _in = new FastPushbackReader(new BufferedReader(new InputStreamReader(in, "UTF-8")));
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
        Object o = readJsonObject();
        if (o == EMPTY_OBJECT)
        {
            return new JsonObject();
        }

        Object graph = convertParsedMapsToJava((JsonObject) o);

        // Allow a complete 'Map' return (Javascript style)
        if (_noObjects)
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
        _noObjects = false;
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
    private Object convertParsedMapsToJava(JsonObject root) throws IOException
    {
        createJavaObjectInstance(Object.class, root);
        Object graph = convertMapsToObjects((JsonObject<String, Object>) root);
        patchUnresolvedReferences();
        rehashMaps();
        _objsRead.clear();
        _unresolvedRefs.clear();
        _prettyMaps.clear();
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
    private Object convertMapsToObjects(JsonObject<String, Object> root) throws IOException
    {
        LinkedList<JsonObject<String, Object>> stack = new LinkedList<JsonObject<String, Object>>();
        stack.addFirst(root);
        final boolean useMaps = _noObjects;

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
     * @param stack   a Stack (LinkedList) used to support graph traversal.
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     * @throws IOException for stream errors or parsing errors.
     */
    private void traverseArray(LinkedList<JsonObject<String, Object>> stack, JsonObject<String, Object> jsonObj) throws IOException
    {
        int len = jsonObj.getLength();
        if (len == 0)
        {
            return;
        }

        Class compType = jsonObj.getComponentType();

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

        boolean isPrimitive = isPrimitive(compType);
        Object array = jsonObj.target;
        Object[] items =  jsonObj.getArray();

        for (int i = 0; i < len; i++)
        {
            Object element = items[i];

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
                        String value = (String) jsonArray[0];
                        int numChars = value.length();
                        char[] chars = new char[numChars];
                        for (int j = 0; j < numChars; j++)
                        {
                            chars[j] = value.charAt(j);
                        }
                        Array.set(array, i, chars);
                    }
                }
                else
                {
                    JsonObject<String, Object> jsonObject = new JsonObject<String, Object>();
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
                    JsonObject refObject = _objsRead.get(ref);
                    if (refObject == null)
                    {
                        error("Forward reference @ref: " + ref + ", but no object defined (@id) with that value");
                    }
                    if (refObject.target != null)
                    {   // Array element with @ref to existing object
                        Array.set(array, i, refObject.target);
                    }
                    else
                    {    // Array with a forward @ref as an element
                        _unresolvedRefs.add(new UnresolvedReference(jsonObj, i, ref));
                    }
                }
                else
                {    // Convert JSON HashMap to Java Object instance and assign values
                    Object arrayElement = createJavaObjectInstance(compType, jsonObject);
                    Array.set(array, i, arrayElement);
                    if (!isPrimitive(arrayElement.getClass()))
                    {    // Skip walking primitives, primitive wrapper classes,, Strings, and Classes
                        stack.addFirst(jsonObject);
                    }
                }
            }
            else
            {   // Setting primitive values into an Object[]
                Array.set(array, i, element);
            }
        }
        jsonObj.clearArray();
    }

    /**
     * Process java.util.Collection and it's derivatives.  Collections are written specially
     * so that the serialization does not expose the Collection's internal structure, for
     * example a TreeSet.  All entries are processed, except unresolved references, which
     * are filled in later.  For an indexable collection, the unresolved references are set
     * back into the proper element location.  For non-indexable collections (Sets), the
     * unresolved references are added via .add().
     * @param stack   a Stack (LinkedList) used to support graph traversal.
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     * @throws IOException for stream errors or parsing errors.
     */
    private void traverseCollectionNoObj(LinkedList<JsonObject<String, Object>> stack, JsonObject jsonObj) throws IOException
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
                JsonObject<String, Object> jsonObject = new JsonObject<String, Object>();
                jsonObject.put("@items", element);
                stack.addFirst(jsonObject);
            }
            else if (element instanceof JsonObject)
            {
                JsonObject<String, Object> jsonObject = (JsonObject<String, Object>) element;
                Long ref = (Long) jsonObject.get("@ref");

                if (ref != null)
                {    // connect reference
                    JsonObject refObject = _objsRead.get(ref);
                    if (refObject == null)
                    {
                        error("Forward reference @ref: " + ref + ", but no object defined (@id) with that value");
                    }
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
    private void traverseCollection(LinkedList<JsonObject<String, Object>> stack, JsonObject jsonObj) throws IOException
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
                    JsonObject refObject = _objsRead.get(ref);
                    if (refObject == null)
                    {
                        error("Forward reference @ref: " + ref + ", but no object defined (@id) with that value");
                    }

                    if (refObject.target != null)
                    {
                        col.add(refObject.target);
                    }
                    else
                    {
                        _unresolvedRefs.add(new UnresolvedReference(jsonObj, idx, ref));
                        if (isList)
                        {   // Indexable collection, so set 'null' as element for now - will be patched in later.
                            col.add(null);
                        }
                    }
                }
                else
                {
                    createJavaObjectInstance(Object.class, jObj);

                    if (!isPrimitive(jObj.getTargetClass()))
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
     * @param stack   a Stack (LinkedList) used to support graph traversal.
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     * @throws IOException for stream errors or parsing errors.
     */
    private void traverseMap(LinkedList<JsonObject<String, Object>> stack, JsonObject<String, Object> jsonObj) throws IOException
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

        JsonObject jsonKeyCollection = new JsonObject();
        jsonKeyCollection.put("@items", keys);
        Object[] javaKeys = new Object[size];
        jsonKeyCollection.target = javaKeys;
        stack.addFirst(jsonKeyCollection);

        // Convert @items to a Collection of Java objects.
        JsonObject jsonItemCollection = new JsonObject();
        jsonItemCollection.put("@items", items);
        Object[] javaValues = new Object[size];
        jsonItemCollection.target = javaValues;
        stack.addFirst(jsonItemCollection);

        // Save these for later so that unresolved references inside keys or values
        // get patched first, and then build the Maps.
        _prettyMaps.add(new Object[] {jsonObj, javaKeys, javaValues});
    }

    private void traverseFieldsNoObj(LinkedList<JsonObject<String, Object>> stack, JsonObject<String, Object> jsonObj) throws IOException
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
                JsonObject<String, Object> jsonArray = new JsonObject<String, Object>();
                jsonArray.put("@items", value);
                stack.addFirst(jsonArray);
                jsonObj.put(key, jsonArray);
            }
            else if (value instanceof JsonObject)
            {
                JsonObject<String, Object> jObj = (JsonObject) value;
                if (field != null && jObj.isPrimitiveWrapper(field.getType()))
                {
                    jObj.put("value", newPrimitiveWrapper(field.getType(),jObj.get("value")));
                    continue;
                }
                Long ref = (Long) jObj.get("@ref");

                if (ref != null)
                {    // Correct field references
                    JsonObject refObject = _objsRead.get(ref);
                    if (refObject == null)
                    {
                        error("Forward reference @ref: " + ref + ", but no object defined (@id) with that value");
                    }
                    jsonObj.put(key, refObject);    // Update Map-of-Maps reference
                }
                else
                {
                    stack.addFirst(jObj);
                }
            }
            else if (field != null)
            {
                Class fieldType = field.getType();
                if (isPrimitive(fieldType))
                {
                    jsonObj.put(key, newPrimitiveWrapper(fieldType, value));
                }
                else if (BigDecimal.class == fieldType)
                {
                    jsonObj.put(key, new BigDecimal(value.toString()));
                }
                else if (BigInteger.class == fieldType)
                {
                    jsonObj.put(key, new BigInteger(value.toString()));
                }
            }
        }
        jsonObj.target = null;  // don't waste space (used for typed return, not for Map return)
    }

    /**
     * Walk the Java object fields and copy them from the JSON object to the Java object, performing
     * any necessary conversions on primitives, or deep traversals for field assignments to other objects,
     * arrays, Collections, or Maps.
     * @param stack   Stack (LinkedList) used for graph traversal.
     * @param jsonObj a Map-of-Map representation of the current object being examined (containing all fields).
     * @throws IOException
     */
    private void traverseFields(LinkedList<JsonObject<String, Object>> stack, JsonObject<String, Object> jsonObj) throws IOException
    {
        Object special;
        if ((special = readIfMatching(jsonObj, null, stack)) != null)
        {
            jsonObj.target = special;
            return;
        }

        Object javaMate = jsonObj.target;
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
     * @param stack   Stack (LinkedList) used for graph traversal.
     * @param jsonObj a Map-of-Map representation of the current object being examined (containing all fields).
     * @param field   a Java Field object representing where the jsonObj should be converted and stored.
     * @param rhs     the JSON value that will be converted and stored in the 'field' on the associated
     *                Java target object.
     * @throws IOException for stream errors or parsing errors.
     */
    private void assignField(LinkedList<JsonObject<String, Object>> stack, JsonObject jsonObj, Field field, Object rhs) throws IOException
    {
        Object target = jsonObj.target;
        try
        {
            Class fieldType = field.getType();

            if (Collection.class.isAssignableFrom(fieldType))
            {   // Process Collection (and potentially generic objects within it) by marketing contained items with
                // template info if it exists, and the items do not have @type specified on them.
                if (rhs instanceof JsonObject)
                {
                    JsonObject col = (JsonObject) rhs;
                    Object[] items = col.getArray();
                    markSubobjectTypes(field, items, 0);
                }
            }
            else if (Map.class.isAssignableFrom(fieldType))
            {
                if (rhs instanceof JsonObject)
                {
                    JsonObject map = (JsonObject) rhs;
                    convertMapToKeysItems(map);

                    if (map.get("@keys") instanceof Object[])
                    {
                        Object[] keys = (Object[]) map.get("@keys");
                        markSubobjectTypes(field, keys, 0);
                    }
                    if (map.get("@items") instanceof Object[])
                    {
                        Object[] values = (Object[]) map.get("@items");
                        markSubobjectTypes(field, values, 1);
                    }
                }
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
                JsonObject<String, Object> jsonArray = new JsonObject<String, Object>();
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
                    JsonObject refObject = _objsRead.get(ref);
                    if (refObject == null)
                    {
                        error("Forward reference @ref: " + ref + ", but no object defined (@id) with that value");
                    }

                    if (refObject.target != null)
                    {
                        field.set(target, refObject.target);
                    }
                    else
                    {
                        _unresolvedRefs.add(new UnresolvedReference(jsonObj, field.getName(), ref));
                    }
                }
                else
                {    // Assign ObjectMap's to Object (or derived) fields
                    field.set(target, createJavaObjectInstance(fieldType, jObj));
                    if (!isPrimitive(jObj.getTargetClass()))
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
                {
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
            error("IllegalAccessException setting field '" + field.getName() + "' on target: " + target + " with value: " + rhs, e);
        }
    }

    /**
     * Convert an input JsonObject map (known to represent a Map.class or derivative) that has regular keys and values
     * to have its keys placed into @keys, and its values placed into @items.
     * @param map Map to convert
     */
    private void convertMapToKeysItems(JsonObject map)
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
     * Mark the @type field on JsonObject's that have no type information,
     * no target, but where the Field generic type information contains the
     * type of the enclosed objects.
     * @param field Field instance containing the Generic info of the Collection that holds the enclosed objects.
     * @param items Object[] of JsonObjects that may be type-less.
     * @param typeArg Used to indicate whether to use type argument 0 or 1 (Collection or Map).
     */
    private void markSubobjectTypes(Field field, Object[] items, int typeArg)
    {
        if (items == null || items.length == 0)
        {
            return;
        }
        for (Object o : items)
        {
            if (o instanceof JsonObject)
            {
                JsonObject item = (JsonObject) o;
                String type = item.getType();
                if (type == null || type.isEmpty())
                {
                    if (field.getGenericType() instanceof ParameterizedType)
                    {
                        ParameterizedType paramType = (ParameterizedType) field.getGenericType();
                        Type[] typeArgs = paramType.getActualTypeArguments();
                        if (typeArgs != null && typeArgs.length > typeArg && typeArgs[typeArg] instanceof Class)
                        {
                            Class c = (Class) typeArgs[typeArg];
                            item.setType(c.getName());
                        }
                    }
                }
            }
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
    private Object createJavaObjectInstance(Class clazz, JsonObject jsonObj) throws IOException
    {
        String type = jsonObj.type;
        Object mate;

        // @type always takes precedence over inferred Java (clazz) type.
        if (type != null)
        {    // @type is explicitly set, use that as it always takes precedence
            Class c = classForName(type);
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
                    mate = newInstance(c);
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
            else if (clazz == Object.class && !_noObjects)
            {
                if (jsonObj.isMap() || jsonObj.size() > 0)
                {   // Map-Like (has @keys and @items, or it has entries) but either way, type and class are not set.
                    mate = new JsonObject();
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
        JsonObject<String, Object> object = new JsonObject<String, Object>();
        int state = STATE_READ_START_OBJECT;
        boolean objectRead = false;
        final FastPushbackReader in = _in;

        while (!done)
        {
            int c;
            switch (state)
            {
                case STATE_READ_START_OBJECT:
                    c = skipWhitespaceRead();
                    if (c == '{')
                    {
                        objectRead = true;
                        object.line = _line.get();
                        object.col = _col.get();
                        c = skipWhitespaceRead();
                        if (c == '}')
                        {    // empty object
                            return EMPTY_OBJECT;
                        }
                        in.unread(c);
                        state = STATE_READ_FIELD;
                    }
                    else if (c == '[')
                    {
                        in.unread('[');
                        state = STATE_READ_VALUE;
                    }
                    else
                    {
                        error("Input is invalid JSON; does not start with '{' or '[', c=" + c);
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
                        _objsRead.put((Long)value, object);
                    }
                    state = STATE_READ_POST_VALUE;
                    break;

                case STATE_READ_POST_VALUE:
                    c = skipWhitespaceRead();
                    if (c == -1 && objectRead)
                    {
                        error("EOF reached before closing '}'");
                    }
                    if (c == '}' || c == -1)
                    {
                        done = true;
                    }
                    else if (c == ',')
                    {
                        state = STATE_READ_FIELD;
                    }
                    else
                    {
                        error("Object not ended with '}' or ']'");
                    }
                    break;
            }
        }

        if (_noObjects && object.isPrimitive())
        {
            return object.getPrimitiveValue();
        }

        return object;
    }

    private Object readValue(JsonObject object) throws IOException
    {
        int c = _in.read();

        if (c == '"')
        {
            return readString();
        }
        if (isDigit(c) || c == '-')
        {
            return readNumber(c);
        }
        if (c == '{')
        {
            _in.unread('{');
            return readJsonObject();
        }
        if (c == 't' || c == 'T')
        {
            _in.unread(c);
            readToken("true");
            return Boolean.TRUE;
        }
        if (c == 'f' || c == 'F')
        {
            _in.unread(c);
            readToken("false");
            return Boolean.FALSE;
        }
        if (c == 'n' || c == 'N')
        {
            _in.unread(c);
            readToken("null");
            return null;
        }
        if (c == '[')
        {
            return readArray(object);
        }
        if (c == ']')
        {    // [] empty array
            _in.unread(']');
            return EMPTY_ARRAY;
        }
        if (c == -1)
        {
            error("EOF reached prematurely");
        }
        return error("Unknown JSON value type");
    }

    /**
     * Read a JSON array
     */
    private Object readArray(JsonObject object) throws IOException
    {
        Collection array = new ArrayList();

        while (true)
        {
            skipWhitespace();
            Object o = readValue(object);
            if (o != EMPTY_ARRAY)
            {
                array.add(o);
            }
            int c = skipWhitespaceRead();

            if (c == ']')
            {
                break;
            }
            if (c != ',')
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
        int len = token.length();

        for (int i = 0; i < len; i++)
        {
            int c = _in.read();
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
        final FastPushbackReader in = _in;
        final char[] numBuf = _numBuf;
        numBuf[0] = (char) c;
        int len = 1;
        boolean isFloat = false;

        try
        {
            while (true)
            {
                c = in.read();
                if ((c >= '0' && c <= '9') || c == '-' || c == '+')     // isDigit() inlined for speed here
                {
                    numBuf[len++] = (char) c;
                }
                else if (c == '.' || c == 'e' || c == 'E')
                {
                    numBuf[len++] = (char) c;
                    isFloat = true;
                }
                else if (c == -1)
                {
                    error("Reached EOF while reading number");
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
            error("Too many digits in number");
        }

        if (isFloat)
        {   // Floating point number needed
            String num = new String(numBuf, 0, len);
            try
            {
                return Double.parseDouble(num);
            }
            catch (NumberFormatException e)
            {
                error("Invalid floating point number: " + num, e);
            }
        }
        boolean isNeg = numBuf[0] == '-';
        long n = 0;
        for (int i = (isNeg ? 1 : 0); i < len; i++)
        {
            n = (numBuf[i] - '0') + n * 10;
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
        final StringBuilder strBuf = _strBuf;
        strBuf.setLength(0);
        StringBuilder hex = new StringBuilder();
        boolean done = false;
        final int STATE_STRING_START = 0;
        final int STATE_STRING_SLASH = 1;
        final int STATE_HEX_DIGITS = 2;
        int state = STATE_STRING_START;

        while (!done)
        {
            int c = _in.read();
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
                        strBuf.append(toChars(c));
                    }
                    break;

                case STATE_STRING_SLASH:
                    if (c == 'n')
                    {
                        strBuf.append('\n');
                    }
                    else if (c == 'r')
                    {
                        strBuf.append('\r');
                    }
                    else if (c == 't')
                    {
                        strBuf.append('\t');
                    }
                    else if (c == 'f')
                    {
                        strBuf.append('\f');
                    }
                    else if (c == 'b')
                    {
                        strBuf.append('\b');
                    }
                    else if (c == '\\')
                    {
                        strBuf.append('\\');
                    }
                    else if (c == '/')
                    {
                        strBuf.append('/');
                    }
                    else if (c == '"')
                    {
                        strBuf.append('"');
                    }
                    else if (c == '\'')
                    {
                        strBuf.append('\'');
                    }
                    else if (c == 'u')
                    {
                        state = STATE_HEX_DIGITS;
                        hex.setLength(0);
                        break;
                    }
                    else
                    {
                        error("Invalid character escape sequence specified");
                    }
                    state = STATE_STRING_START;
                    break;

                case STATE_HEX_DIGITS:
                    if (c == 'a' || c == 'A' || c == 'b' || c == 'B' || c == 'c' || c == 'C' || c == 'd' || c == 'D' || c == 'e' || c == 'E' || c == 'f' || c == 'F' || isDigit(c))
                    {
                        hex.append((char) c);
                        if (hex.length() == 4)
                        {
                            int value = Integer.parseInt(hex.toString(), 16);
                            strBuf.append(valueOf((char) value));
                            state = STATE_STRING_START;
                        }
                    }
                    else
                    {
                        error("Expected hexadecimal digits");
                    }
                    break;
            }
        }

        String s = strBuf.toString();
        String cacheHit = _stringCache.get(s);
        return cacheHit == null ? s : cacheHit;
    }

    private static Object newInstance(Class c) throws IOException
    {
        if (_factory.containsKey(c))
        {
            return _factory.get(c).newInstance(c);
        }

        // Constructor not cached, go find a constructor
        Object[] constructorInfo = _constructors.get(c);
        if (constructorInfo != null)
        {   // Constructor was cached
            Constructor constructor = (Constructor) constructorInfo[0];
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
        _constructors.put(c, new Object[] {ret[1], ret[2]});
        return ret[0];
    }

    /**
     * Return constructor and instance as elements 0 and 1, respectively.
     */
    private static Object[] newInstanceEx(Class c) throws IOException
    {
        try
        {
            Constructor constructor = c.getConstructor(_emptyClassArray);
            if (constructor != null)
            {
                return new Object[] {constructor.newInstance(), constructor, true};
            }
            return tryOtherConstructors(c);
        }
        catch (Exception e)
        {
            // OK, this class does not have a public no-arg constructor.  Instantiate with
            // first constructor found, filling in constructor values with null or
            // defaults for primitives.
            return tryOtherConstructors(c);
        }
    }

    private static Object[] tryOtherConstructors(Class c) throws IOException
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

        error("Could not instantiate " + c.getName() + " using any constructor");
        return null;
    }

    private static Object[] fillArgs(Class[] argTypes, boolean useNull) throws IOException
    {
        Object[] values = new Object[argTypes.length];
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
        return c.isPrimitive() || _prims.contains(c);
    }

    private static Object newPrimitiveWrapper(Class c, Object rhs) throws IOException
    {
        if (c == Byte.class || c == byte.class)
        {
            if (rhs instanceof String)
            {
                rhs = removeLeadingAndTrailingQuotes((String) rhs);
                if ("".equals(rhs))
                {
                    rhs = "0";
                }
                return Byte.parseByte((String)rhs);
            }
            return rhs != null ? _byteCache[((Number) rhs).byteValue() + 128] : (byte) 0;
        }
        if (c == Boolean.class || c == boolean.class)
        {    // Booleans are tokenized into Boolean.TRUE or Boolean.FALSE
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
        }
        if (c == Integer.class || c == int.class)
        {
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
        }
        if (c == Long.class || c == long.class || c == Number.class)
        {
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
        }
        if (c == Double.class || c == double.class)
        {
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
        }
        if (c == Character.class || c == char.class)
        {
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
        }
        if (c == Short.class || c == short.class)
        {
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
        if (c == Float.class || c == float.class)
        {
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
        }

        return error("Class '" + c.getName() + "' requested for special instantiation - isPrimitive() does not match newPrimitiveWrapper()");
    }

    static String removeLeadingAndTrailingQuotes(String s)
    {
        Matcher m = _extraQuotes.matcher(s);
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

    private static boolean isWhitespace(int c)
    {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    private static Class classForName(String name) throws IOException
    {
        if (name == null || name.isEmpty())
        {
            error("Invalid class name specified");
        }
        try
        {
            Class c = _nameToClass.get(name);
            return c == null ? loadClass(name) : c;
        }
        catch (ClassNotFoundException e)
        {
            return (Class) error("Class instance '" + name + "' could not be created", e);
        }
    }

    static Class classForName2(String name) throws IOException
    {
        if (name == null || name.isEmpty())
        {
            error("Empty class name.");
        }
        try
        {
            Class c = _nameToClass.get(name);
            return c == null ? loadClass(name) : c;
        }
        catch (ClassNotFoundException e)
        {
            error("Class instance '" + name + "' could not be created.", e);
            return null;
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
            if (className.endsWith(";")) className = className.substring(0,className.length()-1);
            if (className.equals("[B")) primitiveArray = byte[].class;
            else if (className.equals("[S")) primitiveArray = short[].class;
            else if (className.equals("[I")) primitiveArray = int[].class;
            else if (className.equals("[J")) primitiveArray = long[].class;
            else if (className.equals("[F")) primitiveArray = float[].class;
            else if (className.equals("[D")) primitiveArray = double[].class;
            else if (className.equals("[Z")) primitiveArray = boolean[].class;
            else if (className.equals("[C")) primitiveArray = char[].class;
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
     * @throws IOException for stream errors or parsing errors.
     */
    private static Field getDeclaredField(Class c, String fieldName) throws IOException
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
        final FastPushbackReader in = _in;
        int c = in.read();
        while (isWhitespace(c))
        {
            c = in.read();
        }

        return c;
    }

    private void skipWhitespace() throws IOException
    {
        _in.unread(skipWhitespaceRead());
    }

    public void close()
    {
        try
        {
            if (_in != null)
            {
                _in.close();
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
        Iterator i = _unresolvedRefs.iterator();
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

        int count = _unresolvedRefs.size();
        if (count > 0)
        {
            StringBuilder out = new StringBuilder();
            out.append(count);
            out.append(" unresolved references:\n");
            i = _unresolvedRefs.iterator();
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
     * If _noObjects==true, then move @keys to keys and @items to values
     * and then drop these two entries from the map.
     */
    private void rehashMaps()
    {
        final boolean useMaps = _noObjects;
        for (Object[] mapPieces : _prettyMaps)
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
        return msg + "\nLast read: " + getLastReadSnippet() + "\nline: " + _line.get() + ", col: " + _col.get();
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
        StringBuilder s = new StringBuilder();
        for (char[] chars : _snippet.get())
        {
            s.append(chars);
        }
        return s.toString();
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
        return c <= 127 ? _charCache[(int) c] : c;
    }

    public static final int MAX_CODE_POINT = 0x10ffff;
    public static final int MIN_SUPPLEMENTARY_CODE_POINT = 0x010000;
    public static final char MIN_LOW_SURROGATE = '\uDC00';
    public static final char MIN_HIGH_SURROGATE = '\uD800';

    private static char[] toChars(int codePoint)
    {
        if (codePoint < 0 || codePoint > MAX_CODE_POINT)
        {    // int UTF-8 char must be in range
            throw new IllegalArgumentException("value ' + codePoint + ' outside UTF-8 range");
        }

        if (codePoint < MIN_SUPPLEMENTARY_CODE_POINT)
        {    // if the int character fits in two bytes...
            return new char[]{(char) codePoint};
        }

        char[] result = new char[2];
        int offset = codePoint - MIN_SUPPLEMENTARY_CODE_POINT;
        result[1] = (char) ((offset & 0x3ff) + MIN_LOW_SURROGATE);
        result[0] = (char) ((offset >>> 10) + MIN_HIGH_SURROGATE);
        return result;
    }

    /**
     * This class adds significant performance increase over using the JDK
     * PushbackReader.  This is due to this class not using synchronization
     * as it is not needed.
     */
    private static class FastPushbackReader extends FilterReader
    {
        private final int[] _buf;
        private int _idx;

        private FastPushbackReader(Reader reader, int size)
        {
            super(reader);
            _snippet.get().clear();
            _line.set(1);
            _col.set(1);
            if (size <= 0)
            {
                throw new IllegalArgumentException("size <= 0");
            }
            _buf = new int[size];
            _idx = size;
        }

        private FastPushbackReader(Reader r)
        {
            this(r, 1);
        }

        public int read() throws IOException
        {
            int ch;

            if (_idx < _buf.length)
            {   // read from push-back buffer
                ch = _buf[_idx++];
            }
            else
            {
                ch = super.read();
            }
            if (ch >= 0)
            {
                if (ch == 0x0a)
                {
                    _line.set(_line.get() + 1);
                    _col.set(0);
                }
                else
                {
                    _col.set(_col.get() + 1);
                }
                Deque<char[]> buffer = _snippet.get();
                buffer.addLast(toChars(ch));
                if (buffer.size() > 100)
                {
                    buffer.removeFirst();
                }
            }
            return ch;
        }

        public void unread(int c) throws IOException
        {
            if (_idx == 0)
            {
                error("unread(int c) called more than buffer size (" + _buf.length + ")");
            }
            if (c == 0x0a)
            {
                _line.set(_line.get() - 1);
            }
            else
            {
                _col.set(_col.get() - 1);
            }
            _buf[--_idx] = c;
            _snippet.get().removeLast();
        }

        /**
         * Closes the stream and releases any system resources associated with
         * it. Once the stream has been closed, further read(),
         * unread(), ready(), or skip() invocations will throw an IOException.
         * Closing a previously closed stream has no effect.
         *
         * @throws java.io.IOException If an I/O error occurs
         */
        public void close() throws IOException
        {
            super.close();
            _snippet.remove();
            _line.remove();
            _col.remove();
        }
    }
}
