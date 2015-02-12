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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

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
public class JsonWriter implements Closeable, Flushable
{
    public static final String DATE_FORMAT = "DATE_FORMAT";         // Set the date format to use within the JSON output
    public static final String ISO_DATE_FORMAT = "yyyy-MM-dd";      // Constant for use as DATE_FORMAT value
    public static final String ISO_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";  // Constant for use as DATE_FORMAT value
    public static final String TYPE = "TYPE";                       // Force @type always
    public static final String PRETTY_PRINT = "PRETTY_PRINT";       // Force nicely formatted JSON output
    public static final String FIELD_SPECIFIERS = "FIELD_SPECIFIERS";   // Set value to a Map<Class, List<String>> which will be used to control which fields on a class are output
    public static final String ENUM_PUBLIC_ONLY = "ENUM_PUBLIC_ONLY"; // If set, indicates that private variables of ENUMs are not to be serialized
    private static final Map<String, ClassMeta> classMetaCache = new ConcurrentHashMap<>();
    private static final List<Object[]> writers = new ArrayList<>();
    private static final Set<Class> notCustom = new HashSet<>();
    private static final Object[] byteStrings = new Object[256];
    private static final String newLine = System.getProperty("line.separator");
    private static final Long ZERO = 0L;
    private final Map<Object, Long> objVisited = new IdentityHashMap<>();
    private final Map<Object, Long> objsReferenced = new IdentityHashMap<>();
    private final Writer out;
    long identity = 1;
    private int depth = 0;
    // _args is using ThreadLocal so that static inner classes can have access to them
    static final ThreadLocal<Map<String, Object>> _args = new ThreadLocal<Map<String, Object>>()
    {
        public Map<String, Object> initialValue()
        {
            return new HashMap<>();
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
            byteStrings[i + 128] = chars;
        }
    }

    static class ClassMeta extends LinkedHashMap<String, Field>
    {
    }

    @Deprecated
    public static String toJson(Object item)
    {
        throw new RuntimeException("Use com.cedarsoftware.util.JsonWriter.objectToJson()");
    }

    @Deprecated
    public static String toJson(Object item, Map<String, Object> optionalArgs)
    {
        throw new RuntimeException("Use com.cedarsoftware.util.JsonWriter.objectToJson()");
    }

    /**
     * @see JsonWriter#objectToJson(Object, java.util.Map)
     */
    public static String objectToJson(Object item) throws IOException
    {
        return objectToJson(item, new HashMap<String, Object>());
    }

    /**
     * @return The arguments used to configure the JsonWriter.  These are thread local.
     */
    protected static Map getArgs()
    {
        return _args.get();
    }

    /**
     * Provide access to subclasses.
     */
    protected Map getObjectsReferenced()
    {
        return objsReferenced;
    }

    /**
     * Provide access to subclasses.
     */
    protected Map getObjectsVisited()
    {
        return objVisited;
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
     * @return String containing JSON representation of passed
     *         in object.
     * @throws java.io.IOException If an I/O error occurs
     */
    public static String objectToJson(Object item, Map<String, Object> optionalArgs) throws IOException
    {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(stream, optionalArgs);
        writer.write(item);
        writer.close();
        return new String(stream.toByteArray(), "UTF-8");
    }

    /**
     * Format the passed in JSON string in a nice, human readable format.
     * @param json String input JSON
     * @return String containing equivalent JSON, formatted nicely for human readability.
     */
    public static String formatJson(String json) throws IOException
    {
        Map map = JsonReader.jsonToMaps(json);
        Map args = new HashMap();
        args.put(PRETTY_PRINT, true);
        return objectToJson(map, args);
    }

    /**
     * @see JsonWriter#JsonWriter(java.io.OutputStream, java.util.Map)
     */
    public JsonWriter(OutputStream out) throws IOException
    {
        this(out, new HashMap<String, Object>());
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
     * @throws IOException
     */
    public JsonWriter(OutputStream out, Map<String, Object> optionalArgs) throws IOException
    {
        Map args = _args.get();
        args.clear();
        args.putAll(optionalArgs);

        if (!optionalArgs.containsKey(FIELD_SPECIFIERS))
        {   // Ensure that at least an empty Map is in the FIELD_SPECIFIERS entry
            args.put(FIELD_SPECIFIERS, new HashMap());
        }
        else
        {   // Convert String field names to Java Field instances (makes it easier for user to set this up)
            Map<Class, List<String>> specifiers = (Map<Class, List<String>>) args.get(FIELD_SPECIFIERS);
            Map<Class, List<Field>> copy = new HashMap<>();
            for (Map.Entry<Class, List<String>> entry : specifiers.entrySet())
            {
                Class c = entry.getKey();
                List<String> fields = entry.getValue();
                List<Field> newList = new ArrayList(fields.size());

                ClassMeta meta = getDeepDeclaredFields(c);

                for (String field : fields)
                {
                    Field f = meta.get(field);
                    if (f == null)
                    {
                        throw new IllegalArgumentException("Unable to locate field: " + field + " on class: " + c.getName() + ". Make sure the fields in the FIELD_SPECIFIERS map existing on the associated class.");
                    }
                    newList.add(f);
                }
                copy.put(c, newList);
            }
            args.put(FIELD_SPECIFIERS, copy);
        }

        try
        {
            this.out = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
        }
        catch (UnsupportedEncodingException e)
        {
            throw new IOException("Unsupported encoding.  Get a JVM that supports UTF-8", e);
        }
    }

    public interface JsonClassWriter
    {
        void write(Object o, boolean showType, Writer output) throws IOException;
        boolean hasPrimitiveForm();
        void writePrimitiveForm(Object o, Writer output) throws IOException;
    }

    public static boolean isPublicEnumsOnly()
    {
        return isTrue(_args.get().get(ENUM_PUBLIC_ONLY));
    }

    public static boolean isPrettyPrint()
    {
        return isTrue(_args.get().get(PRETTY_PRINT));
    }

    private static boolean isTrue(Object setting)
    {
        if (setting instanceof Boolean)
        {
            return Boolean.TRUE.equals(setting);
        }
        else if (setting instanceof String)
        {
            return "true".equalsIgnoreCase((String) setting);
        }
        else if (setting instanceof Number)
        {
            return ((Number)setting).intValue() != 0;
        }

        return false;
    }

    protected void tabIn() throws IOException
    {
        tab(out, 1);
    }

    protected void newLine() throws IOException
    {
        tab(out, 0);
    }

    protected void tabOut() throws IOException
    {
        tab(out, -1);
    }

    private void tab(Writer output, int delta) throws IOException
    {
        if (!isPrettyPrint())
        {
            return;
        }
        output.write(newLine);
        depth += delta;
        for (int i=0; i < depth; i++)
        {
            output.write("  ");
        }
    }

    public static int getDistance(Class a, Class b)
    {
		if (a.isInterface())
        {
			return getDistanceToInterface(a, b);
		}
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

    static int getDistanceToInterface(Class<?> to, Class<?> from)
    {
        Set<Class<?>> possibleCandidates = new LinkedHashSet<>();

        Class<?>[] interfaces = from.getInterfaces();
        // is the interface direct inherited or via interfaces extends interface?
        for (Class<?> interfase : interfaces)
        {
            if (to.equals(interfase))
            {
                return 1;
            }
            // because of multi-inheritance from interfaces
            if (to.isAssignableFrom(interfase))
            {
                possibleCandidates.add(interfase);
            }
        }

        // it is also possible, that the interface is included in superclasses
        if (from.getSuperclass() != null  && to.isAssignableFrom(from.getSuperclass()))
        {
            possibleCandidates.add(from.getSuperclass());
        }

        int minimum = Integer.MAX_VALUE;
        for (Class<?> candidate : possibleCandidates)
        {
            // Could do that in a non recursive way later
            int distance = getDistanceToInterface(to, candidate);
            if (distance < minimum)
            {
                minimum = ++distance;
            }
        }
        return minimum;
    }

    public boolean writeIfMatching(Object o, boolean showType, Writer output) throws IOException
    {
        Class c = o.getClass();
        if (notCustom.contains(c))
        {
            return false;
        }

        return writeCustom(c, o, showType, output);
    }

    public boolean writeArrayElementIfMatching(Class arrayComponentClass, Object o, boolean showType, Writer output) throws IOException
    {
        if (!o.getClass().isAssignableFrom(arrayComponentClass) || notCustom.contains(o.getClass()))
        {
            return false;
        }

        return writeCustom(arrayComponentClass, o, showType, output);
    }

    private boolean writeCustom(Class arrayComponentClass, Object o, boolean showType, Writer output) throws IOException
    {
		JsonClassWriter closestWriter = getCustomJSonWriter(arrayComponentClass);

        if (closestWriter == null)
        {
            return false;
        }

        if (writeOptionalReference(o))
        {
            return true;
        }

        boolean referenced = objsReferenced.containsKey(o);

        if ((!referenced && !showType && closestWriter.hasPrimitiveForm()) || closestWriter instanceof JsonStringWriter)
        {
            closestWriter.writePrimitiveForm(o, output);
            return true;
        }

        output.write('{');
        tabIn();
        if (referenced)
        {
            writeId(getId(o));
            if (showType)
            {
                output.write(',');
                newLine();
            }
        }

        if (showType)
        {
            writeType(o, output);
        }

        if (referenced || showType)
        {
            output.write(',');
            newLine();
        }

        closestWriter.write(o, showType || referenced, output);
        tabOut();
        output.write('}');
        return true;
    }

	private static JsonClassWriter getCustomJSonWriter(Class classToWrite)
    {
		JsonClassWriter closestWriter = null;
		int minDistance = Integer.MAX_VALUE;

		for (Object[] item : writers)
        {
			Class clz = (Class) item[0];
			if (clz == classToWrite)
            {
				closestWriter = (JsonClassWriter) item[1];
				break;
			}
			int distance = getDistance(clz, classToWrite);
			if (distance < minDistance)
            {
				minDistance = distance;
				closestWriter = (JsonClassWriter) item[1];
			}
		}

		return closestWriter;
	}

    public static void addWriter(Class c, JsonClassWriter writer)
    {
        for (Object[] item : writers)
        {
            Class clz = (Class)item[0];
            if (clz == c)
            {
                item[1] = writer;   // Replace writer
                return;
            }
        }
        writers.add(new Object[]{c, writer});
    }

    public static void addNotCustomWriter(Class c)
    {
        notCustom.add(c);
    }

    public static class TimeZoneWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            TimeZone cal = (TimeZone) obj;
            output.write("\"zone\":\"");
            output.write(cal.getID());
            output.write('"');
        }

        public boolean hasPrimitiveForm() { return false; }
        public void writePrimitiveForm(Object o, Writer output) throws IOException {}
    }

    public static class CalendarWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            Calendar cal = (Calendar) obj;
            _dateFormat.get().setTimeZone(cal.getTimeZone());
            output.write("\"time\":\"");
            output.write(_dateFormat.get().format(cal.getTime()));
            output.write("\",\"zone\":\"");
            output.write(cal.getTimeZone().getID());
            output.write('"');
        }

        public boolean hasPrimitiveForm() { return false; }
        public void writePrimitiveForm(Object o, Writer output) throws IOException {}
    }

    public static class DateWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            Date date = (Date)obj;
            Object dateFormat = _args.get().get(DATE_FORMAT);
            if (dateFormat instanceof String)
            {   // Passed in as String, turn into a SimpleDateFormat instance to be used throughout this stream write.
                dateFormat = new SimpleDateFormat((String) dateFormat, Locale.ENGLISH);
                _args.get().put(DATE_FORMAT, dateFormat);
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

        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            if (_args.get().containsKey(DATE_FORMAT))
            {
                write(o, false, output);
            }
            else
            {
                output.write(Long.toString(((Date) o).getTime()));
            }
        }
    }

    public static class TimestampWriter implements JsonClassWriter
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

        public boolean hasPrimitiveForm() { return false; }

        public void writePrimitiveForm(Object o, Writer output) throws IOException { }
    }

    public static class ClassWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            String value = ((Class) obj).getName();
            output.write("\"value\":");
            writeJsonUtf8String(value, output);
        }

        public boolean hasPrimitiveForm() { return true; }

        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            writeJsonUtf8String(((Class)o).getName(), output);
        }
    }

    public static class JsonStringWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            output.write("\"value\":");
            writeJsonUtf8String((String) obj, output);
        }

        public boolean hasPrimitiveForm() { return true; }

        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            writeJsonUtf8String((String) o, output);
        }
    }

    public static class LocaleWriter implements JsonClassWriter
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
        public boolean hasPrimitiveForm() { return false; }
        public void writePrimitiveForm(Object o, Writer output) throws IOException { }
    }

    public static class BigIntegerWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            BigInteger big = (BigInteger) obj;
            output.write("\"value\":\"");
            output.write(big.toString(10));
            output.write('"');
        }

        public boolean hasPrimitiveForm() { return true; }

        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            BigInteger big = (BigInteger) o;
            output.write('"');
            output.write(big.toString(10));
            output.write('"');
        }
    }

    public static class BigDecimalWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            BigDecimal big = (BigDecimal) obj;
            output.write("\"value\":\"");
            output.write(big.toPlainString());
            output.write('"');
        }

        public boolean hasPrimitiveForm() { return true; }

        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            BigDecimal big = (BigDecimal) o;
            output.write('"');
            output.write(big.toPlainString());
            output.write('"');
        }
    }

    public static class StringBuilderWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            StringBuilder builder = (StringBuilder) obj;
            output.write("\"value\":\"");
            output.write(builder.toString());
            output.write('"');
        }

        public boolean hasPrimitiveForm() { return true; }

        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            StringBuilder builder = (StringBuilder) o;
            output.write('"');
            output.write(builder.toString());
            output.write('"');
        }
    }

    public static class StringBufferWriter implements JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            StringBuffer buffer = (StringBuffer) obj;
            output.write("\"value\":\"");
            output.write(buffer.toString());
            output.write('"');
        }

        public boolean hasPrimitiveForm() { return true; }

        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            StringBuffer buffer = (StringBuffer) o;
            output.write('"');
            output.write(buffer.toString());
            output.write('"');
        }
    }

    public void write(Object obj) throws IOException
    {
        traceReferences(obj);
        objVisited.clear();
        writeImpl(obj, true);
        flush();
        objVisited.clear();
        objsReferenced.clear();
        _args.get().clear();
        _args.remove();
    }

    /**
     * Walk object graph and visit each instance, following each field, each Collection, Map and so on.
     * Tracks visited count to handle cycles and to determine if an item is referenced elsewhere.  If an
     * object is never referenced more than once, no @id field needs to be emitted for it.
     * @param root Object to be deeply traced.  The objVisited and objsReferenced Maps will be written to
     * during the trace.
     */
    protected void traceReferences(Object root)
    {
        if (root == null)
        {
            return;
        }
        Deque<Object> stack = new ArrayDeque<>();
        stack.addFirst(root);
        final Map<Object, Long> visited = objVisited;
        final Map<Object, Long> referenced = objsReferenced;

        while (!stack.isEmpty())
        {
            Object obj = stack.removeFirst();

            if (!JsonReader.isLogicalPrimitive(obj.getClass()))
            {
                Long id = visited.get(obj);
                if (id != null)
                {   // Only write an object once.
                    if (id == ZERO)
                    {   // 2nd time this object has been seen, so give it a unique ID and mark it referenced
                        id = identity++;
                        visited.put(obj, id);
                        referenced.put(obj, id);
                    }
                    continue;
                }
                else
                {   // Initially, mark an object with 0 as the ID, in case it is never referenced,
                    // we don't waste the memory to store a Long instance that is never used.
                    visited.put(obj, ZERO);
                }
            }

            final Class clazz = obj.getClass();

            if (clazz.isArray())
            {
                Class compType = clazz.getComponentType();
                if (!JsonReader.isLogicalPrimitive(compType))
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
            else if (Map.class.isAssignableFrom(clazz))
            {   // Speed up - logically walk maps, as opposed to following their internal structure.
                Map map = (Map) obj;
                for (Object item : map.entrySet())
                {
                    Map.Entry entry = (Map.Entry) item;
                    if (entry.getValue() != null)
                    {
                        stack.addFirst(entry.getValue());
                    }
                    if (entry.getKey() != null)
                    {
                        stack.addFirst(entry.getKey());
                    }
                }
            }
            else if (Collection.class.isAssignableFrom(clazz))
            {
                for (Object item : (Collection)obj)
                {
                    if (item != null)
                    {
                        stack.addFirst(item);
                    }
                }
            }
            else
            {
				// Only trace fields if no custom writer is present
				if (getCustomJSonWriter(obj.getClass()) == null)
                {
					traceFields(stack, obj);
				}
            }
        }
    }

    /**
     * Reach-ability trace to visit all objects within the graph to be written.
     * This API will handle any object, using either reflection APIs or by
     * consulting a specified FIELD_SPECIFIERS map if provided.
     */
    protected void traceFields(Deque<Object> stack, Object obj)
    {
        Map<Class, List<Field>> fieldSpecifiers = (Map) _args.get().get(FIELD_SPECIFIERS);

        // If caller has special Field specifier for a given class
        // then use it, otherwise use reflection.
        Collection<Field> fields = getFieldsUsingSpecifier(obj.getClass(), fieldSpecifiers);
        if (fields == null)
        {   // Trace fields using reflection
            fields = getDeepDeclaredFields(obj.getClass()).values();
        }
        for (Field field : fields)
        {
            traceField(stack, obj, field);
        }
    }

    /**
     * Push object associated to field onto stack for further tracing.  If object was a primitive,
     * Date, String, or null, no further tracing is done.
     */
    protected void traceField(Deque<Object> stack, Object obj, Field field)
    {
        try
        {
            final Class<?> type = field.getType();
            if (JsonReader.isLogicalPrimitive(type))
            {    // speed up: primitives (and Dates/Strings/Numbers considered logical primitive by json-io) cannot reference another object
                return;
            }

            final Object o = field.get(obj);
            if (o != null)
            {
                stack.addFirst(o);
            }
        }
        catch (Exception ignored) { }
    }

    private static List<Field> getFieldsUsingSpecifier(Class classBeingWritten, Map<Class, List<Field>> fieldSpecifiers)
    {
        Iterator<Map.Entry<Class, List<Field>>> i = fieldSpecifiers.entrySet().iterator();
        int minDistance = Integer.MAX_VALUE;
        List<Field> fields = null;

        while (i.hasNext())
        {
            Map.Entry<Class, List<Field>> entry = i.next();
            Class c = entry.getKey();
            if (c == classBeingWritten)
            {
                return entry.getValue();
            }

            int distance = getDistance(c, classBeingWritten);

            if (distance < minDistance)
            {
                minDistance = distance;
                fields = entry.getValue();
            }
        }

        return fields;
    }

    private boolean writeOptionalReference(Object obj) throws IOException
    {
        if (obj != null && JsonReader.isLogicalPrimitive(obj.getClass()))
        {
            return false;
        }
        final Writer output = this.out;
        if (objVisited.containsKey(obj))
        {    // Only write (define) an object once in the JSON stream, otherwise emit a @ref
            String id = getId(obj);
            if (id == null)
            {   // Test for null because of Weak/Soft references being gc'd during serialization.
                return false;
            }
            output.write("{\"@ref\":");
            output.write(id);
            output.write('}');
            return true;
        }

        // Mark the object as visited by putting it in the Map (this map is re-used / clear()'d after walk()).
        objVisited.put(obj, null);
        return false;
    }

    protected void writeImpl(Object obj, boolean showType) throws IOException
    {
        if (obj == null)
        {
            out.write("null");
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
        {   // symmetric support for writing Map of Maps representation back as equivalent JSON format.
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
                if (!writeJsonObjectMapWithStringKeys(jObj, showType))
                {
                    writeJsonObjectMap(jObj, showType);
                }
            }
            else
            {
                writeJsonObjectObject(jObj, showType);
            }
        }
        else if (obj instanceof Map)
        {
            if (!writeMapWithStringKeys((Map) obj, showType))
            {
                writeMap((Map) obj, showType);
            }
        }
        else
        {
            writeObject(obj, showType);
        }
    }

    private void writeId(final String id) throws IOException
    {
        out.write("\"@id\":");
        out.write(id == null ? "0" : id);
    }

    private static void writeType(Object obj, Writer output) throws IOException
    {
        output.write("\"@type\":\"");
        final Class c = obj.getClass();
        switch (c.getName())
        {
            case "java.lang.Boolean":
                output.write("boolean");
                break;
            case "java.lang.Byte":
                output.write("byte");
                break;
            case "java.lang.Character":
                output.write("char");
                break;
            case "java.lang.Class":
                output.write("class");
                break;
            case "java.lang.Double":
                output.write("double");
                break;
            case "java.lang.Float":
                output.write("float");
                break;
            case "java.lang.Integer":
                output.write("int");
                break;
            case "java.lang.Long":
                output.write("long");
                break;
            case "java.lang.Short":
                output.write("short");
                break;
            case "java.lang.String":
                output.write("string");
                break;
            case "java.util.Date":
                output.write("date");
                break;
            default:
                output.write(c.getName());
                break;
        }

        output.write('"');
    }

    private void writePrimitive(final Object obj) throws IOException
    {
        if (obj instanceof Character)
        {
            writeJsonUtf8String(String.valueOf(obj), out);
        }
        else
        {
            out.write(obj.toString());
        }
    }

    private void writeArray(final Object array, final boolean showType) throws IOException
    {
        if (writeOptionalReference(array))
        {
            return;
        }

        Class arrayType = array.getClass();
        int len = Array.getLength(array);
        boolean referenced = objsReferenced.containsKey(array);
//        boolean typeWritten = showType && !(Object[].class == arrayType);    // causes IDE warning in NetBeans 7/4 Java 1.7
        boolean typeWritten = showType && !(arrayType.equals(Object[].class));

        final Writer output = this.out; // performance opt: place in final local for quicker access
        if (typeWritten || referenced)
        {
            output.write('{');
            tabIn();
        }

        if (referenced)
        {
            writeId(getId(array));
            output.write(',');
            newLine();
        }

        if (typeWritten)
        {
            writeType(array, output);
            output.write(',');
            newLine();
        }

        if (len == 0)
        {
            if (typeWritten || referenced)
            {
                output.write("\"@items\":[]");
                tabOut();
                output.write('}');
            }
            else
            {
                output.write("[]");
            }
            return;
        }

        if (typeWritten || referenced)
        {
            output.write("\"@items\":[");
        }
        else
        {
            output.write('[');
        }
        tabIn();

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
            writeJsonUtf8String(new String((char[]) array), output);
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
                    output.write("null");
                }
                else if (isPrimitiveArray || value instanceof Boolean || value instanceof Long || value instanceof Double)
                {
                    writePrimitive(value);
                }
                else if (writeArrayElementIfMatching(componentClass, value, false, output)) { }
                else if (isObjectArray)
                {
                    if (writeIfMatching(value, true, output)) { }
                    else
                    {
                        writeImpl(value, true);
                    }
                }
                else
                {   // Specific Class-type arrays - only force type when
                    // the instance is derived from array base class.
                    boolean forceType = !(value.getClass() == componentClass);
                    writeImpl(value, forceType || alwaysShowType());
                }

                if (i != lenMinus1)
                {
                    output.write(',');
                    newLine();
                }
            }
        }

        tabOut();
        output.write(']');
        if (typeWritten || referenced)
        {
            tabOut();
            output.write('}');
        }
    }

    /**
     * @return true if the user set the 'TYPE' flag to true, indicating to always show type.
     */
    private static boolean alwaysShowType()
    {
        return Boolean.TRUE.equals(_args.get().containsKey(TYPE));
    }

    private void writeBooleanArray(boolean[] booleans, int lenMinus1) throws IOException
    {
        final Writer output = this.out;
        for (int i = 0; i < lenMinus1; i++)
        {
            output.write(booleans[i] ? "true," : "false,");
        }
        output.write(Boolean.toString(booleans[lenMinus1]));
    }

    private void writeDoubleArray(double[] doubles, int lenMinus1) throws IOException
    {
        final Writer output = this.out;
        for (int i = 0; i < lenMinus1; i++)
        {
            output.write(Double.toString(doubles[i]));
            output.write(',');
        }
        output.write(Double.toString(doubles[lenMinus1]));
    }

    private void writeFloatArray(float[] floats, int lenMinus1) throws IOException
    {
        final Writer output = this.out;
        for (int i = 0; i < lenMinus1; i++)
        {
            output.write(Double.toString(floats[i]));
            output.write(',');
        }
        output.write(Float.toString(floats[lenMinus1]));
    }

    private void writeLongArray(long[] longs, int lenMinus1) throws IOException
    {
        final Writer output = this.out;
        for (int i = 0; i < lenMinus1; i++)
        {
            output.write(Long.toString(longs[i]));
            output.write(',');
        }
        output.write(Long.toString(longs[lenMinus1]));
    }

    private void writeIntArray(int[] ints, int lenMinus1) throws IOException
    {
        final Writer output = this.out;
        for (int i = 0; i < lenMinus1; i++)
        {
            output.write(Integer.toString(ints[i]));
            output.write(',');
        }
        output.write(Integer.toString(ints[lenMinus1]));
    }

    private void writeShortArray(short[] shorts, int lenMinus1) throws IOException
    {
        final Writer output = this.out;
        for (int i = 0; i < lenMinus1; i++)
        {
            output.write(Integer.toString(shorts[i]));
            output.write(',');
        }
        output.write(Integer.toString(shorts[lenMinus1]));
    }

    private void writeByteArray(byte[] bytes, int lenMinus1) throws IOException
    {
        final Writer output = this.out;
        final Object[] byteStrs = byteStrings;
        for (int i = 0; i < lenMinus1; i++)
        {
            output.write((char[]) byteStrs[bytes[i] + 128]);
            output.write(',');
        }
        output.write((char[]) byteStrs[bytes[lenMinus1] + 128]);
    }

    private void writeCollection(Collection col, boolean showType) throws IOException
    {
        if (writeOptionalReference(col))
        {
            return;
        }

        final Writer output = this.out;
        boolean referenced = objsReferenced.containsKey(col);
        boolean isEmpty = col.isEmpty();

        if (referenced || showType)
        {
            output.write('{');
            tabIn();
        }
        else if (isEmpty)
        {
            output.write('[');
        }

        writeIdAndTypeIfNeeded(col, showType, referenced);

        if (isEmpty)
        {
            if (referenced || showType)
            {
                tabOut();
                output.write('}');
            }
            else
            {
                output.write(']');
            }
            return;
        }

        beginCollection(showType, referenced);
        Iterator i = col.iterator();

        while (i.hasNext())
        {
            writeCollectionElement(i.next());

            if (i.hasNext())
            {
                output.write(',');
                newLine();
            }
        }

        tabOut();
        output.write(']');
        if (showType || referenced)
        {   // Finished object, as it was output as an object if @id or @type was output
            tabOut();
            output.write("}");
        }
    }

    private void writeIdAndTypeIfNeeded(Object col, boolean showType, boolean referenced) throws IOException
    {
        if (referenced)
        {
            writeId(getId(col));
        }

        if (showType)
        {
            if (referenced)
            {
                out.write(',');
                newLine();
            }
            writeType(col, out);
        }
    }

    private void beginCollection(boolean showType, boolean referenced) throws IOException
    {
        if (showType || referenced)
        {
            out.write(',');
            newLine();
            out.write("\"@items\":[");
        }
        else
        {
            out.write('[');
        }
        tabIn();
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
            arrayClass = JsonReader.classForName(type);
        }

        final Writer output = this.out;
        final boolean isObjectArray = Object[].class == arrayClass;
        final Class componentClass = arrayClass.getComponentType();
        boolean referenced = objsReferenced.containsKey(jObj) && jObj.hasId();
        boolean typeWritten = showType && !isObjectArray;

        if (typeWritten || referenced)
        {
            output.write('{');
            tabIn();
        }

        if (referenced)
        {
            writeId(Long.toString(jObj.id));
            output.write(',');
            newLine();
        }

        if (typeWritten)
        {
            output.write("\"@type\":\"");
            output.write(arrayClass.getName());
            output.write("\",");
            newLine();
        }

        if (len == 0)
        {
            if (typeWritten || referenced)
            {
                output.write("\"@items\":[]");
                tabOut();
                output.write("}");
            }
            else
            {
                output.write("[]");
            }
            return;
        }

        if (typeWritten || referenced)
        {
            output.write("\"@items\":[");
        }
        else
        {
            output.write('[');
        }
        tabIn();

        Object[] items = (Object[]) jObj.get("@items");
        final int lenMinus1 = len - 1;

        for (int i = 0; i < len; i++)
        {
            final Object value = items[i];

            if (value == null)
            {
                output.write("null");
            }
            else if (Character.class == componentClass || char.class == componentClass)
            {
                writeJsonUtf8String((String) value, output);
            }
            else if (value instanceof Boolean || value instanceof Long || value instanceof Double)
            {
                writePrimitive(value);
            }
            else if (value instanceof String)
            {   // Have to specially treat String because it could be referenced, but we still want inline (no @type, value:)
                writeJsonUtf8String((String) value, output);
            }
            else if (isObjectArray)
            {
                if (writeIfMatching(value, true, output)) { }
                else
                {
                    writeImpl(value, true);
                }
            }
            else if (writeArrayElementIfMatching(componentClass, value, false, output)) { }
            else
            {   // Specific Class-type arrays - only force type when
                // the instance is derived from array base class.
                boolean forceType = !(value.getClass() == componentClass);
                writeImpl(value, forceType || alwaysShowType());
            }

            if (i != lenMinus1)
            {
                output.write(',');
                newLine();
            }
        }

        tabOut();
        output.write(']');
        if (typeWritten || referenced)
        {
            tabOut();
            output.write('}');
        }
    }

    private void writeJsonObjectCollection(JsonObject jObj, boolean showType) throws IOException
    {
        if (writeOptionalReference(jObj))
        {
            return;
        }

        String type = jObj.type;
        Class colClass = JsonReader.classForName(type);
        boolean referenced = objsReferenced.containsKey(jObj) && jObj.hasId();
        final Writer output = this.out;
        int len = jObj.getLength();

        if (referenced || showType || len == 0)
        {
            output.write('{');
            tabIn();
        }

        if (referenced)
        {
            writeId(String.valueOf(jObj.id));
        }

        if (showType)
        {
            if (referenced)
            {
                output.write(',');
                newLine();
            }
            output.write("\"@type\":\"");
            output.write(colClass.getName());
            output.write('"');
        }

        if (len == 0)
        {
            tabOut();
            output.write('}');
            return;
        }

        beginCollection(showType, referenced);

        Object[] items = (Object[]) jObj.get("@items");
        final int itemsLen = items.length;
        final int itemsLenMinus1 = itemsLen - 1;

        for (int i=0; i < itemsLen; i++)
        {
            writeCollectionElement(items[i]);

            if (i != itemsLenMinus1)
            {
                output.write(',');
                newLine();
            }
        }

        tabOut();
        output.write("]");
        if (showType || referenced)
        {
            tabOut();
            output.write('}');
        }
    }

    private void writeJsonObjectMap(JsonObject jObj, boolean showType) throws IOException
    {
        if (writeOptionalReference(jObj))
        {
            return;
        }

        boolean referenced = objsReferenced.containsKey(jObj) && jObj.hasId();
        final Writer output = this.out;

        output.write('{');
        tabIn();
        if (referenced)
        {
            writeId(String.valueOf(jObj.getId()));
        }

        if (showType)
        {
            if (referenced)
            {
                output.write(',');
                newLine();
            }
            String type = jObj.getType();
            if (type != null)
            {
                Class mapClass = JsonReader.classForName(type);
                output.write("\"@type\":\"");
                output.write(mapClass.getName());
                output.write('"');
            }
            else
            {   // type not displayed
                showType = false;
            }
        }

        if (jObj.isEmpty())
        {   // Empty
            tabOut();
            output.write('}');
            return;
        }

        if (showType)
        {
            output.write(',');
            newLine();
        }

        output.write("\"@keys\":[");
        tabIn();
        Iterator i = jObj.keySet().iterator();

        while (i.hasNext())
        {
            writeCollectionElement(i.next());

            if (i.hasNext())
            {
                output.write(',');
                newLine();
            }
        }

        tabOut();
        output.write("],");
        newLine();
        output.write("\"@items\":[");
        tabIn();
        i =jObj.values().iterator();

        while (i.hasNext())
        {
            writeCollectionElement(i.next());

            if (i.hasNext())
            {
                output.write(',');
                newLine();
            }
        }

        tabOut();
        output.write(']');
        tabOut();
        output.write('}');
    }


    private boolean writeJsonObjectMapWithStringKeys(JsonObject jObj, boolean showType) throws IOException
    {
        if (!ensureJsonPrimitiveKeys(jObj))
        {
            return false;
        }

        if (writeOptionalReference(jObj))
        {
            return true;
        }

        boolean referenced = objsReferenced.containsKey(jObj) && jObj.hasId();
        final Writer output = this.out;
        output.write('{');
        tabIn();

        if (referenced)
        {
            writeId(String.valueOf(jObj.getId()));
        }

        if (showType)
        {
            if(referenced)
            {
                output.write(',');
                newLine();
            }
            String type = jObj.getType();
            if (type != null)
            {
                Class mapClass = JsonReader.classForName(type);
                output.write("\"@type\":\"");
                output.write(mapClass.getName());
                output.write('"');
            }
            else
            { // type not displayed
                showType = false;
            }
        }

        if (jObj.isEmpty())
        { // Empty
            tabOut();
            output.write('}');
            return true;
        }

        if (showType)
        {
            output.write(',');
            newLine();
        }

        return writeMapBody(jObj.entrySet().iterator());
    }


    private void writeJsonObjectObject(JsonObject jObj, boolean showType) throws IOException
    {
        if (writeOptionalReference(jObj))
        {
            return;
        }

        final Writer output = this.out;
        boolean referenced = objsReferenced.containsKey(jObj) && jObj.hasId();
        showType = showType && jObj.type != null;
        Class type = null;

        output.write('{');
        tabIn();
        if (referenced)
        {
            writeId(String.valueOf(jObj.id));
        }

        if (showType)
        {
            if (referenced)
            {
                output.write(',');
                newLine();
            }
            output.write("\"@type\":\"");
            output.write(jObj.type);
            output.write('"');
            try  { type = JsonReader.classForName(jObj.type); } catch(Exception ignored) { type = null; }
        }

        if (jObj.isEmpty())
        {
            tabOut();
            output.write('}');
            return;
        }

        if (showType || referenced)
        {
            output.write(',');
            newLine();
        }

        Iterator<Map.Entry<String,Object>> i = jObj.entrySet().iterator();

        while (i.hasNext())
        {
            Map.Entry<String, Object>entry = i.next();
            final String fieldName = entry.getKey();
            output.write('"');
            output.write(fieldName);
            output.write("\":");
            Object value = entry.getValue();

            if (value == null)
            {
                output.write("null");
            }
            else if (value instanceof BigDecimal || value instanceof BigInteger)
            {
                writeImpl(value, !doesValueTypeMatchFieldType(type, fieldName, value));
            }
            else if (value instanceof Number || value instanceof Boolean)
            {
                output.write(value.toString());
            }
            else if (value instanceof String)
            {
                writeJsonUtf8String((String) value, output);
            }
            else if (value instanceof Character)
            {
                writeJsonUtf8String(String.valueOf(value), output);
            }
            else
            {
                writeImpl(value, !doesValueTypeMatchFieldType(type, fieldName, value));
            }
            if (i.hasNext())
            {
                output.write(',');
                newLine();
            }
        }
        tabOut();
        output.write('}');
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

        final Writer output = this.out;
        boolean referenced = objsReferenced.containsKey(map);

        output.write('{');
        tabIn();
        if (referenced)
        {
            writeId(getId(map));
        }

        if (showType)
        {
            if (referenced)
            {
                output.write(',');
                newLine();
            }
            writeType(map, output);
        }

        if (map.isEmpty())
        {
            tabOut();
            output.write('}');
            return;
        }

        if (showType || referenced)
        {
            output.write(',');
            newLine();
        }

        output.write("\"@keys\":[");
        tabIn();
        Iterator i = map.keySet().iterator();

        while (i.hasNext())
        {
            writeCollectionElement(i.next());

            if (i.hasNext())
            {
                output.write(',');
                newLine();
            }
        }

        tabOut();
        output.write("],");
        newLine();
        output.write("\"@items\":[");
        tabIn();
        i = map.values().iterator();

        while (i.hasNext())
        {
            writeCollectionElement(i.next());

            if (i.hasNext())
            {
                output.write(',');
                newLine();
            }
        }

        tabOut();
        output.write(']');
        tabOut();
        output.write('}');
    }


    private boolean writeMapWithStringKeys(Map map, boolean showType) throws IOException
    {
        if (!ensureJsonPrimitiveKeys(map))
        {
            return false;
        }

        if (writeOptionalReference(map))
        {
            return true;
        }

        boolean referenced = objsReferenced.containsKey(map);

        out.write('{');
        tabIn();
        writeIdAndTypeIfNeeded(map, showType, referenced);

        if (map.isEmpty())
        {
            tabOut();
            out.write('}');
            return true;
        }

        if (showType || referenced)
        {
            out.write(',');
            newLine();
        }

        return writeMapBody(map.entrySet().iterator());
    }

    private boolean writeMapBody(final Iterator i) throws IOException
    {
        final Writer output = out;
        while (i.hasNext())
        {
            Entry att2value = (Entry) i.next();
            output.write("\"");
            output.write((String) att2value.getKey());
            output.write("\":");

            writeCollectionElement(att2value.getValue());

            if (i.hasNext())
            {
                output.write(',');
                newLine();
            }
        }

        tabOut();
        output.write('}');
        return true;
    }

    // Ensure that all keys within the Map are String instances
    public static boolean ensureJsonPrimitiveKeys(Map map)
    {
        for (Object o : map.keySet())
        {
            if (!(o instanceof String))
            {
                return false;
            }
        }
        return true;
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
            out.write("null");
        }
        else if (o instanceof Boolean || o instanceof Long || o instanceof Double)
        {
            out.write(o.toString());
        }
        else if (o instanceof String)
        {
            writeJsonUtf8String((String) o, out);
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
    private void writeObject(final Object obj, final boolean showType) throws IOException
    {
        if (writeIfMatching(obj, showType, out))
        {
            return;
        }

        if (writeOptionalReference(obj))
        {
            return;
        }

        out.write('{');
        tabIn();
        final boolean referenced = objsReferenced.containsKey(obj);
        if (referenced)
        {
            writeId(getId(obj));
        }

        if (referenced && showType)
        {
            out.write(',');
            newLine();
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

        final Map<Class, List<Field>> fieldSpecifiers = (Map) _args.get().get(FIELD_SPECIFIERS);
        final List<Field> externallySpecifiedFields = getFieldsUsingSpecifier(obj.getClass(), fieldSpecifiers);
        if (externallySpecifiedFields != null)
        {   // Caller is using associating a class name to a set of fields for the given class (allows field reductions)
            for (Field field : externallySpecifiedFields)
            {   // Not currently supporting overwritten field names in hierarchy when using external field specifier
                first = writeField(obj, first, field.getName(), field, true);
            }
        }
        else
        {   // Reflectively use fields, skipping transient and static fields
            final Map<String, Field> classInfo = getDeepDeclaredFields(obj.getClass());
            for (Map.Entry<String, Field> entry : classInfo.entrySet())
            {
                final String fieldName = entry.getKey();
                final Field field = entry.getValue();
                first = writeField(obj, first, fieldName, field, false);
            }
        }

        tabOut();
        out.write('}');
    }

    private boolean writeField(Object obj, boolean first, String fieldName, Field field, boolean allowTransient) throws IOException
    {
        if (!allowTransient && (field.getModifiers() & Modifier.TRANSIENT) != 0)
        {   // Do not write transient fields
            return first;
        }

        int modifiers = field.getModifiers();
        if (field.getDeclaringClass().isEnum() && !Modifier.isPublic(modifiers) && isPublicEnumsOnly())
        {
            return first;
        }

        if (!first)
        {
            out.write(',');
            newLine();
        }

        writeJsonUtf8String(fieldName, out);
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
            return false;
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
            writeImpl(o, forceType || alwaysShowType());
        }
        return false;
    }

    /**
     * Write out special characters "\b, \f, \t, \n, \r", as such, backslash as \\
     * quote as \" and values less than an ASCII space (20hex) as "\\u00xx" format,
     * characters in the range of ASCII space to a '~' as ASCII, and anything higher in UTF-8.
     *
     * @param s String to be written in utf8 format on the output stream.
     * @throws IOException if an error occurs writing to the output stream.
     */
    public static void writeJsonUtf8String(String s, final Writer output) throws IOException
    {
        output.write('\"');
        final int len = s.length();

        for (int i = 0; i < len; i++)
        {
            char c = s.charAt(i);

            if (c < ' ')
            {    // Anything less than ASCII space, write either in \\u00xx form, or the special \t, \n, etc. form
                switch (c)
                {
                    case '\b':
                        output.write("\\b");
                        break;
                    case '\f':
                        output.write("\\f");
                        break;
                    case '\n':
                        output.write("\\n");
                        break;
                    case '\r':
                        output.write("\\r");
                        break;
                    case '\t':
                        output.write("\\t");
                        break;
                    default:
                        String hex = Integer.toHexString(c);
                        output.write("\\u");
                        final int pad = 4 - hex.length();
                        for (int k = 0; k < pad; k++)
                        {
                            output.write('0');
                        }
                        output.write(hex);
                        break;
                }
            }
            else if (c == '\\' || c == '"')
            {
                output.write('\\');
                output.write(c);
            }
            else
            {   // Anything else - write in UTF-8 form (multi-byte encoded) (OutputStreamWriter is UTF-8)
                output.write(c);
            }
        }
        output.write('\"');
    }

    /**
     * @param c Class instance
     * @return ClassMeta which contains fields of class.  The results are cached internally for performance
     *         when called again with same Class.
     */
    static ClassMeta getDeepDeclaredFields(Class c)
    {
        ClassMeta classInfo = classMetaCache.get(c.getName());
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
                    {   // speed up: do not process static fields.
                        if ("metaClass".equals(field.getName()) && "groovy.lang.MetaClass".equals(field.getType().getName()))
                        {   // Skip Groovy metaClass field if present
                            continue;
                        }

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

        classMetaCache.put(c.getName(), classInfo);
        return classInfo;
    }

    public void flush()
    {
        try
        {
            if (out != null)
            {
                out.flush();
            }
        }
        catch (Exception ignored) { }
    }

    public void close()
    {
        try
        {
            out.close();
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
        Long id = objsReferenced.get(o);
        return id == null ? null : Long.toString(id);
    }
}
