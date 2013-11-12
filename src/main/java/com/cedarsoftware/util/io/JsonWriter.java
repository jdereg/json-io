package com.cedarsoftware.util.io;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Output a Java object graph in JSON format.  This code handles cyclic
 * references and can serialize any Object graph without requiring a class
 * to be 'Serializeable' or have any specific methods on it.
 * <br/><ul><li>
 * Call the static method: {@code JsonWriter.toJson(employee)}.  This will
 * convert the passed in 'employee' instance into a JSON String.</li>
 * <li>Using streams:
 * <pre>     JsonWriter writer = new JsonWriter(stream);
 *     writer.write(employee);
 *     writer.close();</pre>
 * This will write the 'employee' object to the passed in OutputStream.
 * </li>
 * <p>That's it.  This can be used as a debugging tool.  Output an object
 * graph using the above code.  You can copy that JSON output into this site
 * which formats it with a lot of whitespace to make it human readable:
 * http://jsonformatter.curiousconcept.com
 * <br/><br/>
 * <p>This will output any object graph deeply (or null).  Object references are
 * properly handled.  For example, if you had A->B, B->C, and C->A, then
 * A will be serialized with a B object in it, B will be serialized with a C
 * object in it, and then C will be serialized with a reference to A (ref), not a
 * redefinition of A.</p>
 * <br/>
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) John DeRegnaucourt
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
public class JsonWriter implements Closeable, Flushable
{
    public static final String DATE_FORMAT = "DATE_FORMAT";
    public static final String ISO_DATE_FORMAT = "yyyy-MM-dd";
    public static final String ISO_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    private final Map<Object, Long> _objVisited = new IdentityHashMap<Object, Long>();
    private final Map<Object, Long> _objsReferenced = new IdentityHashMap<Object, Long>();
    private static final Map<String, ClassMeta> _classMetaCache = new HashMap<String, ClassMeta>();
    private static final List<Object[]> _writers  = new ArrayList<Object[]>();
    private static final Set<Class> _notCustom = new HashSet<Class>();
    private static Object[] _byteStrings = new Object[256];
    private final Writer _out;
    private long _identity = 1;
    static final ThreadLocal<Map> _args = new ThreadLocal<Map>()
    {
        public Map initialValue()
        {
            return new HashMap();
        }
    };
    static final ThreadLocal<SimpleDateFormat> _dateFormat = new ThreadLocal<SimpleDateFormat>()
    {
        public SimpleDateFormat initialValue()
        {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        }
    };

    static
    {   // Add customer writers (these make common classes more succinct)
        addWriter(String.class, new JsonStringWriter());
        addWriter(Date.class, new DateWriter());
        addWriter(BigInteger.class, new BigIntegerWriter());
        addWriter(BigDecimal.class, new BigDecimalWriter());
        addWriter(java.sql.Date.class, new DateWriter());
        addWriter(Timestamp.class, new TimestampWriter());
        addWriter(Calendar.class, new CalendarWriter());
        addWriter(TimeZone.class, new TimeZoneWriter());
        addWriter(Locale.class, new LocaleWriter());
        addWriter(Class.class, new ClassWriter());
        addWriter(StringBuilder.class, new StringBuilderWriter());
        addWriter(StringBuffer.class, new StringBufferWriter());
    }

    static
    {
        for (short i = -128; i <= 127; i++)
        {
            char[] chars = Integer.toString(i).toCharArray();
            _byteStrings[i + 128] = chars;
        }
    }

    static class ClassMeta extends LinkedHashMap<String, Field>
    {
    }

    /**
     * @see JsonWriter#toJson(Object, java.util.Map[])
     */
    public static String toJson(Object item)
    {
        return toJson(item, new HashMap());
    }

    /**
     * Convert a Java Object to a JSON String.  This is the
     * easy-to-use API - it returns null if there was an error.
     * @param item Object to convert to a JSON String.
     * @param optionalArgs (optional) Map of extra arguments indicating how dates are formatted,
     * what fields are written out (optional).  For Date parameters, use the public static
     * DATE_TIME key, and then use the ISO_DATE or ISO_DATE_TIME indicators.  Also,
     * you can specify your own custom SimpleDateFormat String, or you can associate a
     * SimpleDateFormat object, in which case it will be used.  This setting is for both
     * java.util.Date and java.sql.Date. If the DATE_FORMAT key is not used, then dates will
     * be formatted as longs.  This long can be turned back into a date by using 'new Date(longValue)'.
     * <p>
     * If you want to change what fields are written to the JSON format, you can set
     * the key FIELDS to the constants ALL_FIELDS (includes transient), NON_TRANSIENT_FIELDS,
     * which is all but transient fields, PUBLIC_FIELDS, in which case only public fields
     * will be written out, or CUSTOM_FIELDS, in which case you supply an implementation of
     * the of the FieldGrabber interface, and return the field Set for the passed in class.
     * </p>
     * @return String containing JSON representation of passed
     *         in object, or null if an error occurred.
     */
    public static String toJson(Object item, Map optionalArgs)
    {
        try
        {
            return objectToJson(item, optionalArgs);
        }
        catch (IOException ignored)
        {
            return null;
        }
    }

    /**
     * @see JsonWriter#objectToJson(Object, java.util.Map)
     */
    public static String objectToJson(Object item) throws IOException
    {
        return objectToJson(item, new HashMap());
    }

    /**
     * Convert a Java Object to a JSON String.
     *
     * @param item Object to convert to a JSON String.
     * @param optionalArgs (optional) Map of extra arguments indicating how dates are formatted,
     * what fields are written out (optional).  For Date parameters, use the public static
     * DATE_TIME key, and then use the ISO_DATE or ISO_DATE_TIME indicators.  Or you can specify
     * your own custom SimpleDateFormat String, or you can associate a SimpleDateFormat object,
     * in which case it will be used.  This setting is for both java.util.Date and java.sql.Date.
     * If the DATE_FORMAT key is not used, then dates will be formatted as longs.  This long can
     * be turned back into a date by using 'new Date(longValue)'.
     * <p>
     * If you want to change what fields are written to the JSON format, you can set
     * the key FIELDS to the constants ALL_FIELDS (includes transient), NON_TRANSIENT_FIELDS,
     * which is all but transient fields, PUBLIC_FIELDS, in which case only public fields
     * will be written out, or CUSTOM_FIELDS, in which case you supply an implementation of
     * the of the FieldGrabber interface, and return the field Set for the passed in class.
     * </p>
     * @return String containing JSON representation of passed
     *         in object.
     * @throws java.io.IOException If an I/O error occurs
     */
    public static String objectToJson(Object item, Map optionalArgs) throws IOException
    {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(stream, optionalArgs);
        writer.write(item);
        writer.close();
        return new String(stream.toByteArray(), "UTF-8");
    }

    /**
     * @see JsonWriter#JsonWriter(java.io.OutputStream, java.util.Map)
     */
    public JsonWriter(OutputStream out) throws IOException
    {
        this(out, new HashMap());
    }

    /**
     * @param out OutputStream to which the JSON output will be written.
     * @param optionalArgs (optional) Map of extra arguments indicating how dates are formatted,
     * what fields are written out (optional).  For Date parameters, use the public static
     * DATE_TIME key, and then use the ISO_DATE or ISO_DATE_TIME indicators.  Or you can specify
     * your own custom SimpleDateFormat String, or you can associate a SimpleDateFormat object,
     * in which case it will be used.  This setting is for both java.util.Date and java.sql.Date.
     * If the DATE_FORMAT key is not used, then dates will be formatted as longs.  This long can
     * be turned back into a date by using 'new Date(longValue)'.
     * <p>
     * If you want to change what fields are written to the JSON format, you can set
     * the key FIELDS to the constants ALL_FIELDS (includes transient), NON_TRANSIENT_FIELDS,
     * which is all but transient fields, PUBLIC_FIELDS, in which case only public fields
     * will be written out, or CUSTOM_FIELDS, in which case you supply an implementation of
     * the of the FieldGrabber interface, and return the field Set for the passed in class.
     * </p>
     * @throws IOException
     */
    public JsonWriter(OutputStream out, Map optionalArgs) throws IOException
    {
        _args.get().clear();
        _args.get().putAll(optionalArgs);

        try
        {
            _out = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
        }
        catch (UnsupportedEncodingException e)
        {
            throw new IOException("Unsupported encoding.  Get a JVM that supports UTF-8", e);
        }
    }

    public interface JsonClassWriter
    {
        void write(Object o, boolean showType, Writer out) throws IOException;
        boolean hasPrimitiveForm();
        void writePrimitiveForm(Object o, Writer out) throws IOException;
    }

    public static int getDistance(Class a, Class b)
    {
        Class curr = b;
        int distance = 0;

        while (curr != a)
        {
            distance++;
            curr = curr.getSuperclass();
            if (curr == null)
            {
                return Integer.MAX_VALUE;
            }
        }

        return distance;
    }

    public boolean writeIfMatching(Object o, boolean showType, Writer out) throws IOException
    {
        Class c = o.getClass();
        if (_notCustom.contains(c))
        {
            return false;
        }

        return writeCustom(c, o, showType, out);
    }

    public boolean writeArrayElementIfMatching(Class arrayComponentClass, Object o, boolean showType, Writer out) throws IOException
    {
        if (!o.getClass().isAssignableFrom(arrayComponentClass) || _notCustom.contains(o.getClass()))
        {
            return false;
        }

        return writeCustom(arrayComponentClass, o, showType, out);
    }

    private boolean writeCustom(Class arrayComponentClass, Object o, boolean showType, Writer out) throws IOException
    {
        JsonClassWriter closestWriter = null;
        int minDistance = Integer.MAX_VALUE;

        for (Object[] item : _writers)
        {
            Class clz = (Class)item[0];
            if (clz == arrayComponentClass)
            {
                closestWriter = (JsonClassWriter)item[1];
                break;
            }
            int distance = getDistance(clz, arrayComponentClass);
            if (distance < minDistance)
            {
                minDistance = distance;
                closestWriter = (JsonClassWriter)item[1];
            }
        }

        if (closestWriter == null)
        {
            return false;
        }

        if (writeOptionalReference(o))
        {
            return true;
        }

        boolean referenced = _objsReferenced.containsKey(o);

        if (!referenced && !showType && closestWriter.hasPrimitiveForm())
        {
            closestWriter.writePrimitiveForm(o, out);
            return true;
        }

        out.write('{');
        if (referenced)
        {
            writeId(getId(o));
            if (showType)
            {
                out.write(',');
            }
        }

        if (showType)
        {
            writeType(o, out);
        }

        if (referenced || showType)
        {
            out.write(',');
        }

        closestWriter.write(o, showType || referenced, out);
        out.write('}');
        return true;
    }

    public static void addWriter(Class c, JsonClassWriter writer)
    {
        for (Object[] item : _writers)
        {
            Class clz = (Class)item[0];
            if (clz == c)
            {
                item[1] = writer;   // Replace writer
                return;
            }
        }
        _writers.add(new Object[] {c, writer});
    }

    public static void addNotCustomWriter(Class c)
    {
        _notCustom.add(c);
    }

    public static class TimeZoneWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer out) throws IOException
        {
            TimeZone cal = (TimeZone) obj;
            out.write("\"zone\":\"");
            out.write(cal.getID());
            out.write('"');
        }

        public boolean hasPrimitiveForm() { return false; }
        public void writePrimitiveForm(Object o, Writer out) throws IOException {}
    }

    public static class CalendarWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer out) throws IOException
        {
            Calendar cal = (Calendar) obj;
            _dateFormat.get().setTimeZone(cal.getTimeZone());
            out.write("\"time\":\"");
            out.write(_dateFormat.get().format(cal.getTime()));
            out.write("\",\"zone\":\"");
            out.write(cal.getTimeZone().getID());
            out.write('"');
        }

        public boolean hasPrimitiveForm() { return false; }
        public void writePrimitiveForm(Object o, Writer out) throws IOException {}
    }

    public static class DateWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer out) throws IOException
        {
            Date date = (Date)obj;
            Object dateFormat = _args.get().get(DATE_FORMAT);
            if (dateFormat instanceof String)
            {   // Passed in as String, turn into a SimpleDateFormat instance to be used throughout this stream write.
                dateFormat = new SimpleDateFormat((String) dateFormat);
                _args.get().put(DATE_FORMAT, dateFormat);
            }
            if (showType)
            {
                out.write("\"value\":");
            }

            if (dateFormat instanceof Format)
            {
                out.write("\"");
                out.write(((Format)dateFormat).format(date));
                out.write("\"");
            }
            else
            {
                out.write(Long.toString(((Date) obj).getTime()));
            }
        }

        public boolean hasPrimitiveForm() { return true; }

        public void writePrimitiveForm(Object o, Writer out) throws IOException
        {
            if (_args.get().containsKey(DATE_FORMAT))
            {
                write(o, false, out);
            }
            else
            {
                out.write(Long.toString(((Date) o).getTime()));
            }
        }
    }

    public static class TimestampWriter implements JsonClassWriter
    {
        public void write(Object o, boolean showType, Writer out) throws IOException
        {
            Timestamp tstamp = (Timestamp) o;
            out.write("\"time\":\"");
            out.write(Long.toString((tstamp.getTime() / 1000) * 1000));
            out.write("\",\"nanos\":\"");
            out.write(Integer.toString(tstamp.getNanos()));
            out.write('"');
        }

        public boolean hasPrimitiveForm() { return false; }

        public void writePrimitiveForm(Object o, Writer out) throws IOException { }
    }

    public static class ClassWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer out) throws IOException
        {
            String value = ((Class) obj).getName();
            out.write("\"value\":");
            writeJsonUtf8String(value, out);
        }

        public boolean hasPrimitiveForm() { return true; }

        public void writePrimitiveForm(Object o, Writer out) throws IOException
        {
            writeJsonUtf8String(((Class)o).getName(), out);
        }
    }

    public static class JsonStringWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer out) throws IOException
        {
            out.write("\"value\":");
            writeJsonUtf8String((String) obj, out);
        }

        public boolean hasPrimitiveForm() { return true; }

        public void writePrimitiveForm(Object o, Writer out) throws IOException
        {
            writeJsonUtf8String((String) o, out);
        }
    }

    public static class LocaleWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer out) throws IOException
        {
            Locale locale = (Locale) obj;

            out.write("\"language\":\"");
            out.write(locale.getLanguage());
            out.write("\",\"country\":\"");
            out.write(locale.getCountry());
            out.write("\",\"variant\":\"");
            out.write(locale.getVariant());
            out.write('"');
        }
        public boolean hasPrimitiveForm() { return false; }
        public void writePrimitiveForm(Object o, Writer out) throws IOException { }
    }

    public static class BigIntegerWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer out) throws IOException
        {
            BigInteger big = (BigInteger) obj;
            out.write("\"value\":\"");
            out.write(big.toString(10));
            out.write('"');
        }

        public boolean hasPrimitiveForm() { return true; }

        public void writePrimitiveForm(Object o, Writer out) throws IOException
        {
            BigInteger big = (BigInteger) o;
            out.write('"');
            out.write(big.toString(10));
            out.write('"');
        }
    }

    public static class BigDecimalWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer out) throws IOException
        {
            BigDecimal big = (BigDecimal) obj;
            out.write("\"value\":\"");
            out.write(big.toPlainString());
            out.write('"');
        }

        public boolean hasPrimitiveForm() { return true; }

        public void writePrimitiveForm(Object o, Writer out) throws IOException
        {
            BigDecimal big = (BigDecimal) o;
            out.write('"');
            out.write(big.toPlainString());
            out.write('"');
        }
    }

    public static class StringBuilderWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer out) throws IOException
        {
            StringBuilder builder = (StringBuilder) obj;
            out.write("\"value\":\"");
            out.write(builder.toString());
            out.write('"');
        }

        public boolean hasPrimitiveForm() { return true; }

        public void writePrimitiveForm(Object o, Writer out) throws IOException
        {
            StringBuilder builder = (StringBuilder) o;
            out.write('"');
            out.write(builder.toString());
            out.write('"');
        }
    }

    public static class StringBufferWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer out) throws IOException
        {
            StringBuffer buffer = (StringBuffer) obj;
            out.write("\"value\":\"");
            out.write(buffer.toString());
            out.write('"');
        }

        public boolean hasPrimitiveForm() { return true; }

        public void writePrimitiveForm(Object o, Writer out) throws IOException
        {
            StringBuffer buffer = (StringBuffer) o;
            out.write('"');
            out.write(buffer.toString());
            out.write('"');
        }
    }

    public void write(Object obj) throws IOException
    {
        traceReferences(obj);
        _objVisited.clear();
        writeImpl(obj, true);
        flush();
        _objVisited.clear();
        _objsReferenced.clear();
        _args.get().clear();
        _args.remove();
    }

    private void traceReferences(Object root)
    {
        LinkedList<Object> stack = new LinkedList<Object>();
        stack.addFirst(root);
        final Map<Object, Long> visited = _objVisited;
        final Map<Object, Long> referenced = _objsReferenced;

        while (!stack.isEmpty())
        {
            Object obj = stack.removeFirst();
            if (obj == null)
            {
                continue;
            }

            if (!JsonReader.isPrimitive(obj.getClass()) && !(obj instanceof String) && !(obj instanceof Date))
            {
                Long id = visited.get(obj);
                if (id != null)
                {   // Only write an object once.
                    referenced.put(obj, id);
                    continue;
                }
                visited.put(obj, _identity++);
            }

            final Class clazz = obj.getClass();

            if (clazz.isArray())
            {
                Class compType = clazz.getComponentType();
                if (!JsonReader.isPrimitive(compType) && compType != String.class)
                {   // Speed up: do not traceReferences of primitives, they cannot reference anything
                    final int len = Array.getLength(obj);

                    for (int i = 0; i < len; i++)
                    {
                        Object o = Array.get(obj, i);
                        if (o != null)
                        {   // Slight perf gain (null is legal)
                            stack.addFirst(o);
                        }
                    }
                }
            }
            else
            {
                traceFields(stack, obj);
            }
        }
    }

    private static void traceFields(LinkedList<Object> stack, Object obj)
    {
        final ClassMeta fields = getDeepDeclaredFields(obj.getClass());

        for (Field field : fields.values())
        {
            try
            {
                if (JsonReader.isPrimitive(field.getType()))
                {    // speed up: primitives cannot reference another object
                    continue;
                }

                Object o = field.get(obj);
                if (o != null)
                {
                    stack.addFirst(o);
                }
            }
            catch (Exception ignored) { }
        }
    }

    private boolean writeOptionalReference(Object obj) throws IOException
    {
        final Writer out = _out;
        if (_objVisited.containsKey(obj))
        {    // Only write (define) an object once in the JSON stream, otherwise emit a @ref
            String id = getId(obj);
            if (id == null)
            {   // Test for null because of Weak/Soft references being gc'd during serialization.
                return false;
            }
            out.write("{\"@ref\":");
            out.write(id);
            out.write('}');
            return true;
        }

        // Mark the object as visited by putting it in the Map (this map is re-used / clear()'d after walk()).
        _objVisited.put(obj, null);
        return false;
    }

    private void writeImpl(Object obj, boolean showType) throws IOException
    {
        if (obj == null)
        {
            _out.write("null");
            return;
        }

        if (obj.getClass().isArray())
        {
            writeArray(obj, showType);
        }
        else if (obj instanceof Collection)
        {
            writeCollection((Collection) obj, showType);
        }
        else if (obj instanceof JsonObject)
        {   // symmetric support for writing Map of Maps representation back as identical JSON format.
            JsonObject jObj = (JsonObject) obj;
            if (jObj.isArray())
            {
                writeJsonObjectArray(jObj, showType);
            }
            else if (jObj.isCollection())
            {
                writeJsonObjectCollection(jObj, showType);
            }
            else if (jObj.isMap())
            {
                writeJsonObjectMap(jObj, showType);
            }
            else
            {
                writeJsonObjectObject(jObj, showType);
            }
        }
        else if (obj instanceof Map)
        {
            writeMap((Map) obj, showType);
        }
        else
        {
            writeObject(obj, showType);
        }
    }

    private void writeId(String id) throws IOException
    {
        _out.write("\"@id\":");
        _out.write(id == null ? "0" : id);
    }

    private static void writeType(Object obj, Writer out) throws IOException
    {
        out.write("\"@type\":\"");
        Class c = obj.getClass();

        if (Boolean.class == c)
        {
            out.write("boolean");
        }
        else if (Byte.class == c)
        {
            out.write("byte");
        }
        else if (Short.class == c)
        {
            out.write("short");
        }
        else if (Integer.class == c)
        {
            out.write("int");
        }
        else if (Long.class == c)
        {
            out.write("long");
        }
        else if (Double.class == c)
        {
            out.write("double");
        }
        else if (Float.class == c)
        {
            out.write("float");
        }
        else if (Character.class == c)
        {
            out.write("char");
        }
        else if (Date.class == c)
        {
            out.write("date");
        }
        else if (Class.class == c)
        {
            out.write("class");
        }
        else if (String.class == c)
        {
            out.write("string");
        }
        else
        {
            out.write(c.getName());
        }
        out.write('"');
    }

    private void writePrimitive(Object obj) throws IOException
    {
        if (obj instanceof Character)
        {
            writeJsonUtf8String(String.valueOf(obj), _out);
        }
        else
        {
            _out.write(obj.toString());
        }
    }

    private void writeArray(Object array, boolean showType) throws IOException
    {
        if (writeOptionalReference(array))
        {
            return;
        }

        Class arrayType = array.getClass();
        int len = Array.getLength(array);
        boolean referenced = _objsReferenced.containsKey(array);
        boolean typeWritten = showType && !(Object[].class == arrayType);

        final Writer out = _out; // performance opt: place in final local for quicker access
        if (typeWritten || referenced)
        {
            out.write('{');
        }

        if (referenced)
        {
            writeId(getId(array));
            out.write(',');
        }

        if (typeWritten)
        {
            writeType(array, out);
            out.write(',');
        }

        if (len == 0)
        {
            if (typeWritten || referenced)
            {
                out.write("\"@items\":[]}");
            }
            else
            {
                out.write("[]");
            }
            return;
        }

        if (typeWritten || referenced)
        {
            out.write("\"@items\":[");
        }
        else
        {
            out.write('[');
        }

        final int lenMinus1 = len - 1;

        // Intentionally processing each primitive array type in separate
        // custom loop for speed. All of them could be handled using
        // reflective Array.get() but it is slower.  I chose speed over code length.
        if (byte[].class == arrayType)
        {
            writeByteArray((byte[]) array, lenMinus1);
        }
        else if (char[].class == arrayType)
        {
            writeJsonUtf8String(new String((char[]) array), out);
        }
        else if (short[].class == arrayType)
        {
            writeShortArray((short[]) array, lenMinus1);
        }
        else if (int[].class == arrayType)
        {
            writeIntArray((int[]) array, lenMinus1);
        }
        else if (long[].class == arrayType)
        {
            writeLongArray((long[]) array, lenMinus1);
        }
        else if (float[].class == arrayType)
        {
            writeFloatArray((float[]) array, lenMinus1);
        }
        else if (double[].class == arrayType)
        {
            writeDoubleArray((double[]) array, lenMinus1);
        }
        else if (boolean[].class == arrayType)
        {
            writeBooleanArray((boolean[]) array, lenMinus1);
        }
        else
        {
            final Class componentClass = array.getClass().getComponentType();
            final boolean isPrimitiveArray = JsonReader.isPrimitive(componentClass);
            final boolean isObjectArray = Object[].class == arrayType;

            for (int i = 0; i < len; i++)
            {
                final Object value = Array.get(array, i);

                if (value == null)
                {
                    out.write("null");
                }
                else if (isPrimitiveArray || value instanceof Boolean || value instanceof Long || value instanceof Double)
                {
                    writePrimitive(value);
                }
                else if (value instanceof String)
                {   // Have to specially treat String because it could be referenced, but we still want inline (no @type, value:)
                    writeJsonUtf8String((String) value, out);
                }
                else if (writeArrayElementIfMatching(componentClass, value, false, out)) { }
                else if (isObjectArray)
                {
                    if (writeIfMatching(value, true, out)) { }
                    else
                    {
                        writeImpl(value, true);
                    }
                }
                else
                {   // Specific Class-type arrays - only force type when
                    // the instance is derived from array base class.
                    boolean forceType = !(value.getClass() == componentClass);
                    writeImpl(value, forceType);
                }

                if (i != lenMinus1)
                {
                    out.write(',');
                }
            }
        }

        out.write(']');
        if (typeWritten || referenced)
        {
            out.write('}');
        }
    }

    private void writeBooleanArray(boolean[] booleans, int lenMinus1) throws IOException
    {
        final Writer out = _out;
        for (int i = 0; i < lenMinus1; i++)
        {
            out.write(booleans[i] ? "true," : "false,");
        }
        out.write(Boolean.toString(booleans[lenMinus1]));
    }

    private void writeDoubleArray(double[] doubles, int lenMinus1) throws IOException
    {
        final Writer out = _out;
        for (int i = 0; i < lenMinus1; i++)
        {
            out.write(Double.toString(doubles[i]));
            out.write(',');
        }
        out.write(Double.toString(doubles[lenMinus1]));
    }

    private void writeFloatArray(float[] floats, int lenMinus1) throws IOException
    {
        final Writer out = _out;
        for (int i = 0; i < lenMinus1; i++)
        {
            out.write(Double.toString(floats[i]));
            out.write(',');
        }
        out.write(Float.toString(floats[lenMinus1]));
    }

    private void writeLongArray(long[] longs, int lenMinus1) throws IOException
    {
        final Writer out = _out;
        for (int i = 0; i < lenMinus1; i++)
        {
            out.write(Long.toString(longs[i]));
            out.write(',');
        }
        out.write(Long.toString(longs[lenMinus1]));
    }

    private void writeIntArray(int[] ints, int lenMinus1) throws IOException
    {
        final Writer out = _out;
        for (int i = 0; i < lenMinus1; i++)
        {
            out.write(Integer.toString(ints[i]));
            out.write(',');
        }
        out.write(Integer.toString(ints[lenMinus1]));
    }

    private void writeShortArray(short[] shorts, int lenMinus1) throws IOException
    {
        final Writer out = _out;
        for (int i = 0; i < lenMinus1; i++)
        {
            out.write(Integer.toString(shorts[i]));
            out.write(',');
        }
        out.write(Integer.toString(shorts[lenMinus1]));
    }

    private void writeByteArray(byte[] bytes, int lenMinus1) throws IOException
    {
        final Writer out = _out;
        final Object[] byteStrs = _byteStrings;
        for (int i = 0; i < lenMinus1; i++)
        {
            out.write((char[]) byteStrs[bytes[i] + 128]);
            out.write(',');
        }
        out.write((char[]) byteStrs[bytes[lenMinus1] + 128]);
    }

    private void writeCollection(Collection col, boolean showType) throws IOException
    {
        if (writeOptionalReference(col))
        {
            return;
        }

        final Writer out = _out;
        boolean referenced = _objsReferenced.containsKey(col);
        boolean isEmpty = col.isEmpty();

        if (referenced || showType)
        {
            out.write('{');
        }
        else if (isEmpty)
        {
            out.write('[');
        }

        if (referenced)
        {
            writeId(getId(col));
        }

        if (showType)
        {
            if (referenced)
            {
                out.write(',');
            }
            writeType(col, out);
        }

        if (isEmpty)
        {
            if (referenced || showType)
            {
                out.write('}');
            }
            else
            {
                out.write(']');
            }
            return;
        }

        if (showType || referenced)
        {
            out.write(",\"@items\":[");
        }
        else
        {
            out.write('[');
        }

        Iterator i = col.iterator();

        while (i.hasNext())
        {
            writeCollectionElement(i.next());

            if (i.hasNext())
            {
                out.write(',');
            }

        }

        out.write(']');
        if (showType || referenced)
        {   // Finished object, as it was output as an object if @id or @type was output
            out.write("}");
        }
    }

    private void writeJsonObjectArray(JsonObject jObj, boolean showType) throws IOException
    {
        if (writeOptionalReference(jObj))
        {
            return;
        }

        int len = jObj.getLength();
        String type = jObj.type;
        Class arrayClass;

        if (type == null || Object[].class.getName().equals(type))
        {
            arrayClass = Object[].class;
        }
        else
        {
            arrayClass = JsonReader.classForName2(type);
        }

        final Writer out = _out;
        final boolean isObjectArray = Object[].class == arrayClass;
        final Class componentClass = arrayClass.getComponentType();
        boolean referenced = _objsReferenced.containsKey(jObj) && jObj.hasId();
        boolean typeWritten = showType && !isObjectArray;

        if (typeWritten || referenced)
        {
            out.write('{');
        }

        if (referenced)
        {
            writeId(Long.toString(jObj.id));
            out.write(',');
        }

        if (typeWritten)
        {
            out.write("\"@type\":\"");
            out.write(arrayClass.getName());
            out.write("\",");
        }

        if (len == 0)
        {
            if (typeWritten || referenced)
            {
                out.write("\"@items\":[]}");
            }
            else
            {
                out.write("[]");
            }
            return;
        }

        if (typeWritten || referenced)
        {
            out.write("\"@items\":[");
        }
        else
        {
            out.write('[');
        }

        Object[] items = (Object[]) jObj.get("@items");
        final int lenMinus1 = len - 1;

        for (int i = 0; i < len; i++)
        {
            final Object value = items[i];

            if (value == null)
            {
                out.write("null");
            }
            else if (Character.class == componentClass || char.class == componentClass)
            {
                writeJsonUtf8String((String) value, out);
            }
            else if (value instanceof Boolean || value instanceof Long || value instanceof Double)
            {
                writePrimitive(value);
            }
            else if (value instanceof String)
            {   // Have to specially treat String because it could be referenced, but we still want inline (no @type, value:)
                writeJsonUtf8String((String) value, out);
            }
            else if (isObjectArray)
            {
                if (writeIfMatching(value, true, out)) { }
                else
                {
                    writeImpl(value, true);
                }
            }
            else if (writeArrayElementIfMatching(componentClass, value, false, out)) { }
            else
            {   // Specific Class-type arrays - only force type when
                // the instance is derived from array base class.
                boolean forceType = !(value.getClass() == componentClass);
                writeImpl(value, forceType);
            }

            if (i != lenMinus1)
            {
                out.write(',');
            }
        }

        out.write(']');
        if (typeWritten || referenced)
        {
            out.write('}');
        }
    }

    private void writeJsonObjectCollection(JsonObject jObj, boolean showType) throws IOException
    {
        if (writeOptionalReference(jObj))
        {
            return;
        }

        String type = jObj.type;
        Class colClass = JsonReader.classForName2(type);
        boolean referenced = _objsReferenced.containsKey(jObj) && jObj.hasId();
        final Writer out = _out;
        int len = jObj.getLength();

        if (referenced || showType || len == 0)
        {
            out.write('{');
        }

        if (referenced)
        {
            writeId(String.valueOf(jObj.id));
        }

        if (showType)
        {
            if (referenced)
            {
                out.write(',');
            }
            out.write("\"@type\":\"");
            out.write(colClass.getName());
            out.write('"');
        }

        if (len == 0)
        {
            out.write('}');
            return;
        }

        if (showType || referenced)
        {
            out.write(",\"@items\":[");
        }
        else
        {
            out.write('[');
        }

        Object[] items = (Object[]) jObj.get("@items");
        final int itemsLen = items.length;
        final int itemsLenMinus1 = itemsLen - 1;

        for (int i=0; i < itemsLen; i++)
        {
            writeCollectionElement(items[i]);

            if (i != itemsLenMinus1)
            {
                out.write(',');
            }

        }
        out.write("]");
        if (showType || referenced)
        {
            out.write('}');
        }
    }

    private void writeJsonObjectMap(JsonObject jObj, boolean showType) throws IOException
    {
        if (writeOptionalReference(jObj))
        {
            return;
        }

        boolean referenced = _objsReferenced.containsKey(jObj) && jObj.hasId();
        final Writer out = _out;

        out.write('{');
        if (referenced)
        {
            writeId(String.valueOf(jObj.getId()));
        }

        if (showType)
        {
            if (referenced)
            {
                out.write(',');
            }
            String type = jObj.getType();
            if (type != null)
            {
                Class mapClass = JsonReader.classForName2(type);
                out.write("\"@type\":\"");
                out.write(mapClass.getName());
                out.write('"');
            }
            else
            {   // type not displayed
                showType = false;
            }
        }

        if (jObj.isEmpty())
        {   // Empty
            out.write('}');
            return;
        }

        if (showType)
        {
            out.write(',');
        }

        out.write("\"@keys\":[");
        Iterator i = jObj.keySet().iterator();

        while (i.hasNext())
        {
            writeCollectionElement(i.next());

            if (i.hasNext())
            {
                out.write(',');
            }
        }

        out.write("],\"@items\":[");
        i =jObj.values().iterator();

        while (i.hasNext())
        {
            writeCollectionElement(i.next());

            if (i.hasNext())
            {
                out.write(',');
            }
        }

        out.write("]}");
    }

    private void writeJsonObjectObject(JsonObject jObj, boolean showType) throws IOException
    {
        if (writeOptionalReference(jObj))
        {
            return;
        }

        final Writer out = _out;
        boolean referenced = _objsReferenced.containsKey(jObj) && jObj.hasId();
        showType = showType && jObj.type != null;
        Class type = null;

        out.write('{');
        if (referenced)
        {
            writeId(String.valueOf(jObj.id));
        }

        if (showType)
        {
            if (referenced)
            {
                out.write(',');
            }
            out.write("\"@type\":\"");
            out.write(jObj.type);
            out.write('"');
            try  { type = JsonReader.classForName2(jObj.type); } catch(Exception ignored) { type = null; }
        }

        if (jObj.isEmpty())
        {
            out.write('}');
            return;
        }

        if (showType || referenced)
        {
            out.write(',');
        }

        Iterator<Map.Entry<String,Object>> i = jObj.entrySet().iterator();

        while (i.hasNext())
        {
            Map.Entry<String, Object>entry = i.next();
            final String fieldName = entry.getKey();
            out.write('"');
            out.write(fieldName);
            out.write("\":");
            Object value = entry.getValue();

            if (value == null)
            {
                out.write("null");
            }
            else if (value instanceof BigDecimal || value instanceof BigInteger)
            {
                writeImpl(value, !doesValueTypeMatchFieldType(type, fieldName, value));
            }
            else if (value instanceof Number || value instanceof Boolean)
            {
                out.write(value.toString());
            }
            else if (value instanceof String)
            {
                writeJsonUtf8String((String) value, out);
            }
            else if (value instanceof Character)
            {
                writeJsonUtf8String(String.valueOf(value), out);
            }
            else
            {
                writeImpl(value, !doesValueTypeMatchFieldType(type, fieldName, value));
            }
            if (i.hasNext())
            {
                out.write(',');
            }
        }
        out.write('}');
    }

    private static boolean doesValueTypeMatchFieldType(Class type, String fieldName, Object value)
    {
        if (type != null)
        {
            ClassMeta meta = getDeepDeclaredFields(type);
            Field field = meta.get(fieldName);
            return field != null && (value.getClass() == field.getType());
        }
        return false;
    }

    private void writeMap(Map map, boolean showType) throws IOException
    {
        if (writeOptionalReference(map))
        {
            return;
        }

        final Writer out = _out;
        boolean referenced = _objsReferenced.containsKey(map);

        out.write('{');
        if (referenced)
        {
            writeId(getId(map));
        }

        if (showType)
        {
            if (referenced)
            {
                out.write(',');
            }
            writeType(map, out);
        }

        if (map.isEmpty())
        {
            out.write('}');
            return;
        }

        if (showType || referenced)
        {
            out.write(',');
        }

        out.write("\"@keys\":[");
        Iterator i = map.keySet().iterator();

        while (i.hasNext())
        {
            writeCollectionElement(i.next());

            if (i.hasNext())
            {
                out.write(',');
            }
        }

        out.write("],\"@items\":[");
        i = map.values().iterator();

        while (i.hasNext())
        {
            writeCollectionElement(i.next());

            if (i.hasNext())
            {
                out.write(',');
            }
        }

        out.write("]}");
    }

    /**
     * Write an element that is contained in some type of collection or Map.
     * @param o Collection element to output in JSON format.
     * @throws IOException if an error occurs writing to the output stream.
     */
    private void writeCollectionElement(Object o) throws IOException
    {
        if (o == null)
        {
            _out.write("null");
        }
        else if (o instanceof Boolean || o instanceof Long || o instanceof Double)
        {
            _out.write(o.toString());
        }
        else if (o instanceof String)
        {
            writeJsonUtf8String((String) o, _out);
        }
        else
        {
            writeImpl(o, true);
        }
    }

    /**
     * @param obj      Object to be written in JSON format
     * @param showType boolean true means show the "@type" field, false
     *                 eliminates it.  Many times the type can be dropped because it can be
     *                 inferred from the field or array type.
     * @throws IOException if an error occurs writing to the output stream.
     */
    private void writeObject(Object obj, boolean showType) throws IOException
    {
        if (writeIfMatching(obj, showType, _out))
        {
            return;
        }

        if (writeOptionalReference(obj))
        {
            return;
        }

        final Writer out = _out;

        out.write('{');
        boolean referenced = _objsReferenced.containsKey(obj);
        if (referenced)
        {
            writeId(getId(obj));
        }

        ClassMeta classInfo = getDeepDeclaredFields(obj.getClass());

        if (referenced && showType)
        {
            out.write(',');
        }

        if (showType)
        {
            writeType(obj, out);
        }

        boolean first = !showType;
        if (referenced && !showType)
        {
            first = false;
        }

        for (Map.Entry<String, Field> entry : classInfo.entrySet())
        {
            Field field = entry.getValue();
            if ((field.getModifiers() & Modifier.TRANSIENT) != 0)
            {   // Do not write transient fields
                continue;
            }
            if (first)
            {
                first = false;
            }
            else
            {
                out.write(',');
            }

            writeJsonUtf8String(entry.getKey(), out);
            out.write(':');

            Object o;
            try
            {
                o = field.get(obj);
            }
            catch (Exception ignored)
            {
                o = null;
            }

            if (o == null)
            {    // don't quote null
                out.write("null");
                continue;
            }

            Class type = field.getType();
            boolean forceType = o.getClass() != type;     // If types are not exactly the same, write "@type" field

            if (JsonReader.isPrimitive(type))
            {
                writePrimitive(o);
            }
            else if (writeIfMatching(o, forceType, out)) { }
            else
            {
                writeImpl(o, forceType);
            }
        }

        out.write('}');
    }

    /**
     * Write out special characters "\b, \f, \t, \n, \r", as such, backslash as \\
     * quote as \" and values less than an ASCII space (20hex) as "\\u00xx" format,
     * characters in the range of ASCII space to a '~' as ASCII, and anything higher in UTF-8.
     *
     * @param s String to be written in utf8 format on the output stream.
     * @throws IOException if an error occurs writing to the output stream.
     */
    public static void writeJsonUtf8String(String s, Writer out) throws IOException
    {
        out.write('\"');
        int len = s.length();

        for (int i = 0; i < len; i++)
        {
            char c = s.charAt(i);

            if (c < ' ')
            {    // Anything less than ASCII space, write either in \\u00xx form, or the special \t, \n, etc. form
                if (c == '\b')
                {
                    out.write("\\b");
                }
                else if (c == '\t')
                {
                    out.write("\\t");
                }
                else if (c == '\n')
                {
                    out.write("\\n");
                }
                else if (c == '\f')
                {
                    out.write("\\f");
                }
                else if (c == '\r')
                {
                    out.write("\\r");
                }
                else
                {
                    String hex = Integer.toHexString(c);
                    out.write("\\u");
                    int pad = 4 - hex.length();
                    for (int k = 0; k < pad; k++)
                    {
                        out.write('0');
                    }
                    out.write(hex);
                }
            }
            else if (c == '\\' || c == '"')
            {
                out.write('\\');
                out.write(c);
            }
            else
            {   // Anything else - write in UTF-8 form (multi-byte encoded) (OutputStreamWriter is UTF-8)
                out.write(c);
            }
        }
        out.write('\"');
    }

    /**
     * @param c Class instance
     * @return ClassMeta which contains fields of class.  The results are cached internally for performance
     *         when called again with same Class.
     */
    static ClassMeta getDeepDeclaredFields(Class c)
    {
        ClassMeta classInfo = _classMetaCache.get(c.getName());
        if (classInfo != null)
        {
            return classInfo;
        }

        classInfo = new ClassMeta();
        Class curr = c;

        while (curr != null)
        {
            try
            {
                Field[] local = curr.getDeclaredFields();

                for (Field field : local)
                {
                    if ((field.getModifiers() & Modifier.STATIC) == 0)
                    {    // speed up: do not process static fields.
                        if (!field.isAccessible())
                        {
                            try
                            {
                                field.setAccessible(true);
                            }
                            catch (Exception ignored) { }
                        }
                        if (classInfo.containsKey(field.getName()))
                        {
                            classInfo.put(curr.getName() + '.' + field.getName(), field);
                        }
                        else
                        {
                            classInfo.put(field.getName(), field);
                        }
                    }
                }
            }
            catch (ThreadDeath t)
            {
                throw t;
            }
            catch (Throwable ignored) { }

            curr = curr.getSuperclass();
        }

        _classMetaCache.put(c.getName(), classInfo);
        return classInfo;
    }

    public void flush()
    {
        try
        {
            if (_out != null)
            {
                _out.flush();
            }
        }
        catch (Exception ignored) { }
    }

    public void close()
    {
        try
        {
            _out.close();
        }
        catch (Exception ignore) { }
    }

    private String getId(Object o)
    {
        if (o instanceof JsonObject)
        {
            long id = ((JsonObject) o).id;
            if (id != -1)
            {
                return String.valueOf(id);
            }
        }
        Long id = _objsReferenced.get(o);
        return id == null ? null : Long.toString(id);
    }
}
