package com.cedarsoftware.util.io;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Output a Java object graph in JSON format.  This code handles cyclic
 * references and can serialize any Object graph without requiring a class
 * to be 'Serializeable' or have any specific methods on it.
 * <br><ul><li>
 * Call the static method: {@code JsonWriter.objectToJson(employee)}.  This will
 * convert the passed in 'employee' instance into a JSON String.</li>
 * <li>Using streams:
 * <pre>     JsonWriter writer = new JsonWriter(stream);
 *     writer.write(employee);
 *     writer.close();</pre>
 * This will write the 'employee' object to the passed in OutputStream.
 * </li></ul>
 * <p>That's it.  This can be used as a debugging tool.  Output an object
 * graph using the above code.  You can copy that JSON output into this site
 * which formats it with a lot of whitespace to make it human readable:
 * http://jsonformatter.curiousconcept.com
 * <br>
 * <p>This will output any object graph deeply (or null).  Object references are
 * properly handled.  For example, if you had A-&gt;B, B-&gt;C, and C-&gt;A, then
 * A will be serialized with a B object in it, B will be serialized with a C
 * object in it, and then C will be serialized with a reference to A (ref), not a
 * redefinition of A.</p>
 * <br>
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
 *         limitations under the License.
 */
public class JsonWriter implements Closeable, Flushable
{
    /** If set, this maps class ==> CustomWriter */
    public static final String CUSTOM_WRITER_MAP = "CUSTOM_WRITERS";
    /** If set, this maps class ==> CustomWriter */
    public static final String NOT_CUSTOM_WRITER_MAP = "NOT_CUSTOM_WRITERS";
    /** Set the date format to use within the JSON output */
    public static final String DATE_FORMAT = "DATE_FORMAT";
    /** Constant for use as DATE_FORMAT value */
    public static final String ISO_DATE_FORMAT = "yyyy-MM-dd";
    /** Constant for use as DATE_FORMAT value */
    public static final String ISO_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    /** Force @type always */
    public static final String TYPE = "TYPE";
    /** Force nicely formatted JSON output */
    public static final String PRETTY_PRINT = "PRETTY_PRINT";
    /** Set value to a Map<Class, List<String>> which will be used to control which fields on a class are output */
    public static final String FIELD_SPECIFIERS = "FIELD_SPECIFIERS";
    /** Set value to a Map<Class, List<String>> which will be used to control which fields on a class are not output. Black list has always priority to FIELD_SPECIFIERS */
    public static final String FIELD_NAME_BLACK_LIST = "FIELD_NAME_BLACK_LIST";
    /** same as above only internal for storing Field instances instead of strings. This avoid the initial argument content to be modified. */
    private static final String FIELD_BLACK_LIST = "FIELD_BLACK_LIST";
    /** If set, indicates that private variables of ENUMs are not to be serialized */
    public static final String ENUM_PUBLIC_ONLY = "ENUM_PUBLIC_ONLY";
    /** If set, longs are written in quotes (Javascript safe) */
    public static final String WRITE_LONGS_AS_STRINGS = "WLAS";
    /** If set, this map will be used when writing @type values - allows short-hand abbreviations type names */
    public static final String TYPE_NAME_MAP = "TYPE_NAME_MAP";
    /** If set, then @type -> @t, @keys -> @k, @items -> @i */
    public static final String SHORT_META_KEYS = "SHORT_META_KEYS";
    /** If set, null fields are not written */
    public static final String SKIP_NULL_FIELDS = "SKIP_NULL";

    private final ConcurrentMap<Class, JsonClassWriterBase> writers = new ConcurrentHashMap<Class, JsonClassWriterBase>();
    private final ConcurrentMap<Class, JsonClassWriterBase> writerCache = new ConcurrentHashMap<Class, JsonClassWriterBase>();
    private final Set<Class> notCustom = new HashSet<Class>();
    private static final Object[] byteStrings = new Object[256];
    private static final String NEW_LINE = System.getProperty("line.separator");
    private static final Long ZERO = 0L;
    private static final NullClass nullWriter = new NullClass();
    private final Map<Object, Long> objVisited = new IdentityHashMap<Object, Long>();
    private final Map<Object, Long> objsReferenced = new IdentityHashMap<Object, Long>();
    private final Writer out;
    private Map<String, String> typeNameMap = null;
    private boolean shortMetaKeys = false;
    private boolean neverShowType = false;
    private boolean alwaysShowType = false;
    private boolean isPrettyPrint = false;
    private boolean isEnumPublicOnly = false;
    private boolean writeLongsAsStrings = false;
    private boolean skipNullFields = false;
    private long identity = 1;
    private int depth = 0;
    /** _args is using ThreadLocal so that static inner classes can have access to them */
    final Map<String, Object> args = new HashMap<String, Object>();

    {   // Add customer writers (these make common classes more succinct)
        addWriter(String.class, new Writers.JsonStringWriter());
        addWriter(Date.class, new Writers.DateWriter());
        addWriter(BigInteger.class, new Writers.BigIntegerWriter());
        addWriter(BigDecimal.class, new Writers.BigDecimalWriter());
        addWriter(java.sql.Date.class, new Writers.DateWriter());
        addWriter(Timestamp.class, new Writers.TimestampWriter());
        addWriter(Calendar.class, new Writers.CalendarWriter());
        addWriter(TimeZone.class, new Writers.TimeZoneWriter());
        addWriter(Locale.class, new Writers.LocaleWriter());
        addWriter(Class.class, new Writers.ClassWriter());
        addWriter(StringBuilder.class, new Writers.StringBuilderWriter());
        addWriter(StringBuffer.class, new Writers.StringBufferWriter());
    }

    static
    {
        for (short i = -128; i <= 127; i++)
        {
            char[] chars = Integer.toString(i).toCharArray();
            byteStrings[i + 128] = chars;
        }
    }

    public interface JsonClassWriterBase
    { }

    /**
     * Implement this interface to customize the JSON output for a given class.
     */
    public interface JsonClassWriter extends JsonClassWriterBase
    {
        void write(Object o, boolean showType, Writer output) throws IOException;
        boolean hasPrimitiveForm();
        void writePrimitiveForm(Object o, Writer output) throws IOException;
    }

    /**
     * Implement this interface to customize the JSON output for a given class.
     */
    public interface JsonClassWriterEx extends JsonClassWriterBase
    {
        String JSON_WRITER = "JSON_WRITER";

        void write(Object o, boolean showType, Writer output, Map<String, Object> args) throws IOException;

        class Support
        {
            public static JsonWriter getWriter(Map<String, Object> args)
            {
                return (JsonWriter) args.get(JSON_WRITER);
            }
        }
    }

    /**
     * Provide access to subclasses.
     * @return Map containing all objects that were referenced within input object graph.
     */
    public Map getObjectsReferenced()
    {
        return objsReferenced;
    }

    /**
     * Provide access to subclasses.
     * @return Map containing all objects that were visited within input object graph
     */
    public Map getObjectsVisited()
    {
        return objVisited;
    }

    protected String getSubstituteTypeNameIfExists(String typeName)
    {
        if (typeNameMap == null)
        {
            return null;
        }
        return typeNameMap.get(typeName);
    }

    protected String getSubstituteTypeName(String typeName)
    {
        if (typeNameMap == null)
        {
            return typeName;
        }
        String shortName = typeNameMap.get(typeName);
        return shortName == null ? typeName : shortName;
    }

    /**
     * @see JsonWriter#objectToJson(Object, java.util.Map)
     * @param item Object (root) to serialized to JSON String.
     * @return String of JSON format representing complete object graph rooted by item.
     */
    public static String objectToJson(Object item)
    {
        return objectToJson(item, null);
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
     * @return String containing JSON representation of passed in object root.
     */
    public static String objectToJson(Object item, Map<String, Object> optionalArgs)
    {
        try
        {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            JsonWriter writer = new JsonWriter(stream, optionalArgs);
            writer.write(item);
            writer.close();
            return new String(stream.toByteArray(), "UTF-8");
        }
        catch (Exception e)
        {
            throw new JsonIoException("Unable to convert object to JSON", e);
        }
    }

    /**
     * Format the passed in JSON string in a nice, human readable format.
     * @param json String input JSON
     * @return String containing equivalent JSON, formatted nicely for human readability.
     */
    public static String formatJson(String json)
    {
        return formatJson(json, null, null);
    }
    
    /**
     * Format the passed in JSON string in a nice, human readable format.
     * @param json String input JSON
     * @param readingArgs (optional) Map of extra arguments for parsing json.  Can be null.
     * @param writingArgs (optional) Map of extra arguments for writing out json.  Can be null.
     * @return String containing equivalent JSON, formatted nicely for human readability.
     */
    public static String formatJson(String json, Map readingArgs, Map writingArgs)
    {
        Map args = new HashMap();
        if (readingArgs != null)
        {
            args.putAll(readingArgs);
        }
        args.put(JsonReader.USE_MAPS, true);
        Object obj = JsonReader.jsonToJava(json, args);
        args.clear();
        if (writingArgs != null)
        {
            args.putAll(writingArgs);
        }
        args.put(PRETTY_PRINT, true);
        return objectToJson(obj, args);
    }

    /**
     * @see JsonWriter#JsonWriter(java.io.OutputStream, java.util.Map)
     * @param out OutputStream to which the JSON will be written.
     */
    public JsonWriter(OutputStream out)
    {
        this(out, null);
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
     */
    public JsonWriter(OutputStream out, Map<String, Object> optionalArgs)
    {
        if (optionalArgs == null)
        {
            optionalArgs = new HashMap();
        }
        args.putAll(optionalArgs);
        args.put(JsonClassWriterEx.JSON_WRITER, this);
        typeNameMap = (Map<String, String>) args.get(TYPE_NAME_MAP);
        shortMetaKeys = isTrue(args.get(SHORT_META_KEYS));
        alwaysShowType = isTrue(args.get(TYPE));
        neverShowType = Boolean.FALSE.equals(args.get(TYPE)) || "false".equals(args.get(TYPE));
        isPrettyPrint = isTrue(args.get(PRETTY_PRINT));
        isEnumPublicOnly = isTrue(args.get(ENUM_PUBLIC_ONLY));
        writeLongsAsStrings = isTrue(args.get(WRITE_LONGS_AS_STRINGS));
        writeLongsAsStrings = isTrue(args.get(WRITE_LONGS_AS_STRINGS));
        skipNullFields = isTrue(args.get(SKIP_NULL_FIELDS));

        Map<Class, JsonClassWriterBase> customWriters = (Map<Class, JsonClassWriterBase>) args.get(CUSTOM_WRITER_MAP);
        if (customWriters != null)
        {
            for (Map.Entry<Class, JsonClassWriterBase> entry : customWriters.entrySet())
            {
                addWriter(entry.getKey(), entry.getValue());
            }
        }

        Collection<Class> notCustomClasses = (Collection<Class>) args.get(NOT_CUSTOM_WRITER_MAP);
        if (notCustomClasses != null)
        {
            for (Class c : notCustomClasses)
            {
                addNotCustomWriter(c);
            }
        }

        if (optionalArgs.containsKey(FIELD_SPECIFIERS))
        {   // Convert String field names to Java Field instances (makes it easier for user to set this up)
            Map<Class, List<String>> specifiers = (Map<Class, List<String>>) args.get(FIELD_SPECIFIERS);
            Map<Class, List<Field>> copy = new HashMap<Class, List<Field>>();
            for (Entry<Class, List<String>> entry : specifiers.entrySet())
            {
                Class c = entry.getKey();
                List<String> fields = entry.getValue();
                List<Field> newList = new ArrayList(fields.size());

                Map<String, Field> classFields = MetaUtils.getDeepDeclaredFields(c);

                for (String field : fields)
                {
                    Field f = classFields.get(field);
                    if (f == null)
                    {
                        throw new JsonIoException("Unable to locate field: " + field + " on class: " + c.getName() + ". Make sure the fields in the FIELD_SPECIFIERS map existing on the associated class.");
                    }
                    newList.add(f);
                }
                copy.put(c, newList);
            }
            args.put(FIELD_SPECIFIERS, copy);
        }
        else
        {   // Ensure that at least an empty Map is in the FIELD_SPECIFIERS entry
            args.put(FIELD_SPECIFIERS, new HashMap());
        }
        if (optionalArgs.containsKey(FIELD_NAME_BLACK_LIST))
        {   // Convert String field names to Java Field instances (makes it easier for user to set this up)
            Map<Class, List<String>> blackList = (Map<Class, List<String>>) args.get(FIELD_NAME_BLACK_LIST);
            Map<Class, List<Field>> copy = new HashMap<Class, List<Field>>();
            for (Entry<Class, List<String>> entry : blackList.entrySet())
            {
                Class c = entry.getKey();
                List<String> fields = entry.getValue();
                List<Field> newList = new ArrayList(fields.size());

                Map<String, Field> classFields = MetaUtils.getDeepDeclaredFields(c);

                for (String field : fields)
                {
                    Field f = classFields.get(field);
                    if (f == null)
                    {
                        throw new JsonIoException("Unable to locate field: " + field + " on class: " + c.getName() + ". Make sure the fields in the FIELD_NAME_BLACK_LIST map existing on the associated class.");
                    }
                    newList.add(f);
                }
                copy.put(c, newList);
            }
            args.put(FIELD_BLACK_LIST, copy);
        }
        else
        {   // Ensure that at least an empty Map is in the FIELD_SPECIFIERS entry
            args.put(FIELD_BLACK_LIST, new HashMap());
        }

        try
        {
            this.out = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
        }
        catch (UnsupportedEncodingException e)
        {
            throw new JsonIoException("UTF-8 not supported on your JVM.  Unable to convert object to JSON.", e);
        }
    }

    static boolean isTrue(Object setting)
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

    public void tabIn() throws IOException
    {
        tab(out, 1);
    }

    public void newLine() throws IOException
    {
        tab(out, 0);
    }

    public void tabOut() throws IOException
    {
        tab(out, -1);
    }

    private void tab(Writer output, int delta) throws IOException
    {
        if (!isPrettyPrint)
        {
            return;
        }
        output.write(NEW_LINE);
        depth += delta;
        for (int i=0; i < depth; i++)
        {
            output.write("  ");
        }
    }

    public boolean writeIfMatching(Object o, boolean showType, Writer output)
    {
        if (neverShowType)
        {
            showType = false;
        }
        Class c = o.getClass();
        if (notCustom.contains(c))
        {
            return false;
        }

        try
        {
            return writeCustom(c, o, showType, output);
        }
        catch (IOException e)
        {
            throw new JsonIoException("Unable to write custom formatted object:", e);
        }
    }

    public boolean writeArrayElementIfMatching(Class arrayComponentClass, Object o, boolean showType, Writer output)
    {
        if (!o.getClass().isAssignableFrom(arrayComponentClass) || notCustom.contains(o.getClass()))
        {
            return false;
        }

        try
        {
            return writeCustom(arrayComponentClass, o, showType, output);
        }
        catch (IOException e)
        {
            throw new JsonIoException("Unable to write custom formatted object as array element:", e);
        }
    }

    protected boolean writeCustom(Class arrayComponentClass, Object o, boolean showType, Writer output) throws IOException
    {
        if (neverShowType)
        {
            showType = false;
        }
		JsonClassWriterBase closestWriter = getCustomWriter(arrayComponentClass);

        if (closestWriter == null)
        {
            return false;
        }

        if (writeOptionalReference(o))
        {
            return true;
        }

        boolean referenced = objsReferenced.containsKey(o);

        if (closestWriter instanceof JsonClassWriter)
        {
            JsonClassWriter writer = (JsonClassWriter) closestWriter;
            if (writer.hasPrimitiveForm())
            {
                if ((!referenced && !showType) || closestWriter instanceof Writers.JsonStringWriter)
                {
                    if (writer instanceof Writers.DateWriter)
                    {
                        ((Writers.DateWriter)writer).writePrimitiveForm(o, output, args);
                    }
                    else
                    {
                        writer.writePrimitiveForm(o, output);
                    }
                    return true;
                }
            }
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

        if (closestWriter instanceof JsonClassWriterEx)
        {
            ((JsonClassWriterEx)closestWriter).write(o, showType || referenced, output, args);
        }
        else
        {
            ((JsonClassWriter)closestWriter).write(o, showType || referenced, output);
        }
        tabOut();
        output.write('}');
        return true;
    }

    /**
     * Dummy place-holder class exists only because ConcurrentHashMap cannot contain a
     * null value.  Instead, singleton instance of this class is placed where null values
     * are needed.
     */
    static class NullClass implements JsonClassWriterBase { }

    private JsonClassWriterBase getCustomWriter(Class c)
    {
        JsonClassWriterBase writer = writerCache.get(c);
        if (writer == null)
        {
            writer = forceGetCustomWriter(c);
            JsonClassWriterBase writerRef = writerCache.putIfAbsent(c, writer);
            if (writerRef != null)
            {
                writer = writerRef;
            }
        }
        return writer == nullWriter ? null : writer;
    }

    private JsonClassWriterBase forceGetCustomWriter(Class c)
    {
        JsonClassWriterBase closestWriter = nullWriter;
        int minDistance = Integer.MAX_VALUE;

        for (Map.Entry<Class, JsonClassWriterBase> entry : writers.entrySet())
        {
            Class clz = entry.getKey();
            if (clz == c)
            {
                return entry.getValue();
            }
            int distance = MetaUtils.getDistance(clz, c);
            if (distance < minDistance)
            {
                minDistance = distance;
                closestWriter = entry.getValue();
            }
        }
        return closestWriter;
    }

    /**
     * Add a custom writer which will manage writing objects of the
     * passed in Class in JSON format.  The custom writer will be
     * called for objects of the passed in class, including subclasses.
     * If this is not desired, call addNotCustomWriter(c) which will
     * force objects of the passed in Class to be written by the standard
     * JSON writer.
     * @param c Class to associate a custom JSON writer too
     * @param writer JsonClassWriterBase which implements the appropriate
     * subclass of JsonClassWriterBase (JsonClassWriter or JsonClassWriterEx).
     */
    public void addWriter(Class c, JsonClassWriterBase writer)
    {
        writers.put(c, writer);
    }

    /**
     * For no custom writing to occur for the passed in Class.
     * @param c Class which should NOT have any custom writer associated to it.  Use this
     * to prevent a custom writer from being used due to inheritance.
     */
    public void addNotCustomWriter(Class c)
    {
        notCustom.add(c);
    }

    /**
     * Write the passed in Java object in JSON format.
     * @param obj Object any Java Object or JsonObject.
     */
    public void write(Object obj)
    {
        traceReferences(obj);
        objVisited.clear();
        try
        {
            writeImpl(obj, true);
        }
        catch (Exception e)
        {
            throw new JsonIoException("Error writing object to JSON:", e);
        }
        flush();
        objVisited.clear();
        objsReferenced.clear();
    }

    /**
     * Walk object graph and visit each instance, following each field, each Collection, Map and so on.
     * Tracks visited to handle cycles and to determine if an item is referenced elsewhere.  If an
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
        Map<Class, List<Field>> fieldSpecifiers = (Map) args.get(FIELD_SPECIFIERS);
        final Deque<Object> stack = new ArrayDeque<Object>();
        stack.addFirst(root);
        final Map<Object, Long> visited = objVisited;
        final Map<Object, Long> referenced = objsReferenced;

        while (!stack.isEmpty())
        {
            final Object obj = stack.removeFirst();

            if (!MetaUtils.isLogicalPrimitive(obj.getClass()))
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
                if (!MetaUtils.isLogicalPrimitive(clazz.getComponentType()))
                {   // Speed up: do not traceReferences of primitives, they cannot reference anything
                    final int len = Array.getLength(obj);

                    for (int i = 0; i < len; i++)
                    {
                        final Object o = Array.get(obj, i);
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
                for (final Object item : map.entrySet())
                {
                    final Map.Entry entry = (Map.Entry) item;
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
                for (final Object item : (Collection)obj)
                {
                    if (item != null)
                    {
                        stack.addFirst(item);
                    }
                }
            }
            else
            {   // Speed up: do not traceReferences of primitives, they cannot reference anything
				if (!MetaUtils.isLogicalPrimitive(obj.getClass()))
                {
					traceFields(stack, obj, fieldSpecifiers);
				}
            }
        }
    }

    /**
     * Reach-ability trace to visit all objects within the graph to be written.
     * This API will handle any object, using either reflection APIs or by
     * consulting a specified FIELD_SPECIFIERS map if provided.
     * @param stack Deque used to manage descent into graph (rather than using Java stack.) This allows for
     * much larger graph processing.
     * @param obj Object root of graph
     * @param fieldSpecifiers Map of optional field specifiers, which are used to override the field list returned by
     * the JDK reflection operations.  This allows a subset of the actual fields on an object to be serialized.
     */
    protected void traceFields(final Deque<Object> stack, final Object obj, final Map<Class, List<Field>> fieldSpecifiers)
    {
        // If caller has special Field specifier for a given class
        // then use it, otherwise use reflection.
        Collection<Field> fields = getFieldsUsingSpecifier(obj.getClass(), fieldSpecifiers);
        Collection<Field> fieldsBySpec = fields;
        if (fields == null)
        {   // Trace fields using reflection
            fields = MetaUtils.getDeepDeclaredFields(obj.getClass()).values();
        }
        for (final Field field : fields)
        {
            if ((field.getModifiers() & Modifier.TRANSIENT) != 0)
            {
                if (fieldsBySpec == null || !fieldsBySpec.contains(field))
                {   // Skip tracing transient fields EXCEPT when the field is listed explicitly by using the fieldSpecifiers Map.
                    // In that case, the field must be traced, even though it is transient.
                    continue;
                }
            }
            try
            {
                final Object o = field.get(obj);
                if (o != null && !MetaUtils.isLogicalPrimitive(o.getClass()))
                {   // Trace through objects that can reference other objects
                    stack.addFirst(o);
                }
            }
            catch (Exception ignored) { }
        }
    }

    private static List<Field> getFieldsUsingSpecifier(final Class classBeingWritten, final Map<Class, List<Field>> fieldSpecifiers)
    {
        final Iterator<Map.Entry<Class, List<Field>>> i = fieldSpecifiers.entrySet().iterator();
        int minDistance = Integer.MAX_VALUE;
        List<Field> fields = null;

        while (i.hasNext())
        {
            final Map.Entry<Class, List<Field>> entry = i.next();
            final Class c = entry.getKey();

            if (c == classBeingWritten)
            {
                return entry.getValue();
            }

            int distance = MetaUtils.getDistance(c, classBeingWritten);

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
        if (obj == null)
        {
            return false;
        }

        if (MetaUtils.isLogicalPrimitive(obj.getClass()))
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
            output.write(shortMetaKeys ? "{\"@r\":" : "{\"@ref\":");
            output.write(id);
            output.write('}');
            return true;
        }

        // Mark the object as visited by putting it in the Map (this map is re-used / clear()'d after walk()).
        objVisited.put(obj, null);
        return false;
    }

    /**
     * Main entry point (mostly used internally, but may be called from a Custom JSON writer).
     * This method will write out whatever object type it is given, including JsonObject's.
     * It will handle null, detecting if a custom writer should be called, array, array of
     * JsonObject, Map, Map of JsonObjects, Collection, Collection of JsonObject, any regular
     * object, or a JsonObject representing a regular object.
     * @param obj Object to be written
     * @param showType if set to true, the @type tag will be output.  If false, it will be
     * dropped.
     * @throws IOException if one occurs on the underlying output stream.
     */
    public void writeImpl(Object obj, boolean showType) throws IOException
    {
        writeImpl(obj, showType, true, true);
    }

    /**
     * Main entry point (mostly used internally, but may be called from a Custom JSON writer).
     * This method will write out whatever object type it is given, including JsonObject's.
     * It will handle null, detecting if a custom writer should be called, array, array of
     * JsonObject, Map, Map of JsonObjects, Collection, Collection of JsonObject, any regular
     * object, or a JsonObject representing a regular object.
     * @param obj Object to be written
     * @param showType if set to true, the @type tag will be output.  If false, it will be
     * @param allowRef if set to true, @ref will be used, otherwise 2+ occurrence will be
     * output as full object.
     * @param allowCustom if set to true, the object being called will allowed to be checked for a matching
     * custom writer to be used. This does not affect subobjects, just the top-level 'obj'
     * being passed in.
     * @throws IOException if one occurs on the underlying output stream.
     */
    public void writeImpl(Object obj, boolean showType, boolean allowRef, boolean allowCustom) throws IOException
    {
        if (neverShowType)
        {
            showType = false;
        }
        if (obj == null)
        {
            out.write("null");
            return;
        }

        if (allowCustom && writeIfMatching(obj, showType, out))
        {
            return;
        }

        if (allowRef && writeOptionalReference(obj))
        {
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
            writeObject(obj, showType, false);
        }
    }

    private void writeId(final String id) throws IOException
    {
        out.write(shortMetaKeys ? "\"@i\":" : "\"@id\":");
        out.write(id == null ? "0" : id);
    }

    private void writeType(Object obj, Writer output) throws IOException
    {
        if (neverShowType)
        {
            return;
        }
        output.write(shortMetaKeys ? "\"@t\":\"" : "\"@type\":\"");
        final Class c = obj.getClass();
        String typeName = c.getName();
        String shortName = getSubstituteTypeNameIfExists(typeName);

        if (shortName != null)
        {
            output.write(shortName);
            output.write('"');
            return;
        }

        String s = c.getName();
        if (s.equals("java.lang.Boolean"))
        {
            output.write("boolean");

        }
        else if (s.equals("java.lang.Byte"))
        {
            output.write("byte");

        }
        else if (s.equals("java.lang.Character"))
        {
            output.write("char");

        }
        else if (s.equals("java.lang.Class"))
        {
            output.write("class");

        }
        else if (s.equals("java.lang.Double"))
        {
            output.write("double");

        }
        else if (s.equals("java.lang.Float"))
        {
            output.write("float");

        }
        else if (s.equals("java.lang.Integer"))
        {
            output.write("int");

        }
        else if (s.equals("java.lang.Long"))
        {
            output.write("long");

        }
        else if (s.equals("java.lang.Short"))
        {
            output.write("short");

        }
        else if (s.equals("java.lang.String"))
        {
            output.write("string");

        }
        else if (s.equals("java.util.Date"))
        {
            output.write("date");

        }
        else
        {
            output.write(c.getName());

        }

        output.write('"');
    }

    private void writePrimitive(final Object obj, boolean showType) throws IOException
    {
        if (neverShowType)
        {
            showType = false;
        }
        if (obj instanceof Character)
        {
            writeJsonUtf8String(String.valueOf(obj), out);
        }
        else
        {
            if (obj instanceof Long && writeLongsAsStrings)
            {
                if (showType)
                {
                    out.write(shortMetaKeys ? "{\"@t\":\"" : "{\"@type\":\"");
                    out.write(getSubstituteTypeName("long"));
                    out.write("\",\"value\":\"");
                    out.write(obj.toString());
                    out.write("\"}");
                }
                else
                {
                    out.write('"');
                    out.write(obj.toString());
                    out.write('"');
                }
            }
            else if (obj instanceof Double && (Double.isNaN((Double) obj) || Double.isInfinite((Double) obj)))
            {
            	out.write("null");
            }
            else if (obj instanceof Float && (Float.isNaN((Float) obj) || Float.isInfinite((Float) obj)))
            {
                out.write("null");
            }
            else
            {
                out.write(obj.toString());
            }
        }
    }

    private void writeArray(final Object array, boolean showType) throws IOException
    {
        if (neverShowType)
        {
            showType = false;
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
                output.write(shortMetaKeys ? "\"@e\":[]" : "\"@items\":[]");
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
            output.write(shortMetaKeys ? "\"@i\":[" : "\"@items\":[");
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
            final boolean isPrimitiveArray = MetaUtils.isPrimitive(componentClass);

            for (int i = 0; i < len; i++)
            {
                final Object value = Array.get(array, i);

                if (value == null)
                {
                    output.write("null");
                }
                else if (writeArrayElementIfMatching(componentClass, value, false, output)) { }
                else if (isPrimitiveArray || value instanceof Boolean || value instanceof Long || value instanceof Double)
                {
                    writePrimitive(value, value.getClass() != componentClass);
                }
                else if (neverShowType && MetaUtils.isPrimitive(value.getClass()))
                {   // When neverShowType specified, do not allow primitives to show up as {"value":6} for example.
                    writePrimitive(value, false);
                }
                else
                {   // Specific Class-type arrays - only force type when
                    // the instance is derived from array base class.
                    boolean forceType = !(value.getClass() == componentClass);
                    writeImpl(value, forceType || alwaysShowType);
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
            output.write(Float.toString(floats[i]));
            output.write(',');
        }
        output.write(Float.toString(floats[lenMinus1]));
    }

    private void writeLongArray(long[] longs, int lenMinus1) throws IOException
    {
        final Writer output = this.out;
        if (writeLongsAsStrings)
        {
            for (int i = 0; i < lenMinus1; i++)
            {
                output.write('"');
                output.write(Long.toString(longs[i]));
                output.write('"');
                output.write(',');
            }
            output.write('"');
            output.write(Long.toString(longs[lenMinus1]));
            output.write('"');
        }
        else
        {
            for (int i = 0; i < lenMinus1; i++)
            {
                output.write(Long.toString(longs[i]));
                output.write(',');
            }
            output.write(Long.toString(longs[lenMinus1]));
        }
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
        if (neverShowType)
        {
            showType = false;
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
        if (neverShowType)
        {
            showType = false;
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
            out.write(shortMetaKeys ? "\"@e\":[" : "\"@items\":[");
        }
        else
        {
            out.write('[');
        }
        tabIn();
    }

    private void writeJsonObjectArray(JsonObject jObj, boolean showType) throws IOException
    {
        if (neverShowType)
        {
            showType = false;
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
            arrayClass = MetaUtils.classForName(type);
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
            output.write(shortMetaKeys ? "\"@t\":\"" : "\"@type\":\"");
            output.write(getSubstituteTypeName(arrayClass.getName()));
            output.write("\",");
            newLine();
        }

        if (len == 0)
        {
            if (typeWritten || referenced)
            {
                output.write(shortMetaKeys ? "\"@e\":[]" : "\"@items\":[]");
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
            output.write(shortMetaKeys ? "\"@e\":[" : "\"@items\":[");
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
                writePrimitive(value, value.getClass() != componentClass);
            }
            else if (neverShowType && MetaUtils.isPrimitive(value.getClass()))
            {
                writePrimitive(value, false);
            }
            else if (value instanceof String)
            {   // Have to specially treat String because it could be referenced, but we still want inline (no @type, value:)
                writeJsonUtf8String((String) value, output);
            }
            else if (writeArrayElementIfMatching(componentClass, value, false, output)) { }
            else
            {   // Specific Class-type arrays - only force type when
                // the instance is derived from array base class.
                boolean forceType = !(value.getClass() == componentClass);
                writeImpl(value, forceType || alwaysShowType);
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
        if (neverShowType)
        {
            showType = false;
        }
        String type = jObj.type;
        Class colClass = MetaUtils.classForName(type);
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
            output.write(shortMetaKeys ? "\"@t\":\"" : "\"@type\":\"");
            output.write(getSubstituteTypeName(colClass.getName()));
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
        if (neverShowType)
        {
            showType = false;
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
                Class mapClass = MetaUtils.classForName(type);
                output.write(shortMetaKeys ? "\"@t\":\"" : "\"@type\":\"");
                output.write(getSubstituteTypeName(mapClass.getName()));
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

        output.write(shortMetaKeys ? "\"@k\":[" : "\"@keys\":[");
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
        output.write(shortMetaKeys ? "\"@e\":[" : "\"@items\":[");
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
        if (neverShowType)
        {
            showType = false;
        }

        if (!ensureJsonPrimitiveKeys(jObj))
        {
            return false;
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
                Class mapClass = MetaUtils.classForName(type);
                output.write(shortMetaKeys ? "\"@t\":\"" : "\"@type\":\"");
                output.write(getSubstituteTypeName(mapClass.getName()));
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

    /**
     * Write fields of an Object (JsonObject)
     */
    private void writeJsonObjectObject(JsonObject jObj, boolean showType) throws IOException
    {
        if (neverShowType)
        {
            showType = false;
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
            output.write(shortMetaKeys ? "\"@t\":\"" : "\"@type\":\"");
            output.write(getSubstituteTypeName(jObj.type));
            output.write('"');
            try  { type = MetaUtils.classForName(jObj.type); } catch(Exception ignored) { type = null; }
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
        boolean first = true;

        while (i.hasNext())
        {
            Map.Entry<String, Object>entry = i.next();
            if (skipNullFields && entry.getValue() == null)
            {
                continue;
            }

            if (!first)
            {
                output.write(',');
                newLine();
            }
            first = false;
            final String fieldName = entry.getKey();
            output.write('"');
            output.write(fieldName);
            output.write("\":");
            Object value = entry.getValue();

            if (value == null)
            {
                output.write("null");
            }
            else if (neverShowType && MetaUtils.isPrimitive(value.getClass()))
            {
                writePrimitive(value, false);
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
        }
        tabOut();
        output.write('}');
    }

    private static boolean doesValueTypeMatchFieldType(Class type, String fieldName, Object value)
    {
        if (type != null)
        {
            Map<String, Field> classFields = MetaUtils.getDeepDeclaredFields(type);
            Field field = classFields.get(fieldName);
            return field != null && (value.getClass() == field.getType());
        }
        return false;
    }

    private void writeMap(Map map, boolean showType) throws IOException
    {
        if (neverShowType)
        {
            showType = false;
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

        output.write(shortMetaKeys ? "\"@k\":[" : "\"@keys\":[");
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
        output.write(shortMetaKeys ? "\"@e\":[" : "\"@items\":[");
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
        if (neverShowType)
        {
            showType = false;
        }
        if (!ensureJsonPrimitiveKeys(map))
        {
            return false;
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
            writeJsonUtf8String((String)att2value.getKey(), output);
            output.write(":");

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
     * Write an element that is contained in some type of Collection or Map.
     * @param o Collection element to output in JSON format.
     * @throws IOException if an error occurs writing to the output stream.
     */
    private void writeCollectionElement(Object o) throws IOException
    {
        if (o == null)
        {
            out.write("null");
        }
        else if (o instanceof Boolean || o instanceof Double)
        {
            writePrimitive(o, false);
        }
        else if (o instanceof Long)
        {
            writePrimitive(o, writeLongsAsStrings);
        }
        else if (o instanceof String)
        {   // Never do an @ref to a String (they are treated as logical primitives and intern'ed on read)
            writeJsonUtf8String((String) o, out);
        }
        else if (neverShowType && MetaUtils.isPrimitive(o.getClass()))
        {   // If neverShowType, then force primitives (and primitive wrappers)
            // to be output with toString() - prevents {"value":6} for example
            writePrimitive(o, false);
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
     * @param bodyOnly write only the body of the object
     * @throws IOException if an error occurs writing to the output stream.
     */
    public void writeObject(final Object obj, boolean showType, boolean bodyOnly) throws IOException
    {
        if (neverShowType)
        {
            showType = false;
        }
        final boolean referenced = objsReferenced.containsKey(obj);
        if (!bodyOnly)
        {
            out.write('{');
            tabIn();
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
        }

        boolean first = !showType;
        if (referenced && !showType)
        {
            first = false;
        }

        final Map<Class, List<Field>> fieldSpecifiers = (Map) args.get(FIELD_SPECIFIERS);
        final List<Field> fieldBlackListForClass = getFieldsUsingSpecifier(obj.getClass(), (Map) args.get(FIELD_BLACK_LIST));
        final List<Field> externallySpecifiedFields = getFieldsUsingSpecifier(obj.getClass(), fieldSpecifiers);
        if (externallySpecifiedFields != null)
        {   
            for (Field field : externallySpecifiedFields)
            {   //output field if not on the blacklist
                if (fieldBlackListForClass == null || !fieldBlackListForClass.contains(field)){
                    // Not currently supporting overwritten field names in hierarchy when using external field specifier
                    first = writeField(obj, first, field.getName(), field, true);
                }//else field is black listed.
            }
        }
        else
        {   // Reflectively use fields, skipping transient and static fields
            final Map<String, Field> classFields = MetaUtils.getDeepDeclaredFields(obj.getClass());
            for (Map.Entry<String, Field> entry : classFields.entrySet())
            {
                final String fieldName = entry.getKey();
                final Field field = entry.getValue();
                //output field if not on the blacklist
                if (fieldBlackListForClass == null || !fieldBlackListForClass.contains(field)){
                    first = writeField(obj, first, fieldName, field, false);
                }//else field is black listed.
            }
        }

        if (!bodyOnly)
        {
            tabOut();
            out.write('}');
        }
    }

    private boolean writeField(Object obj, boolean first, String fieldName, Field field, boolean allowTransient) throws IOException
    {
        if (!allowTransient && (field.getModifiers() & Modifier.TRANSIENT) != 0)
        {   // Do not write transient fields
            return first;
        }

        int modifiers = field.getModifiers();
        if (field.getDeclaringClass().isEnum() && !Modifier.isPublic(modifiers) && isEnumPublicOnly)
        {
            return first;
        }

        Object o;
        try
        {
            o = field.get(obj);
        }
        catch (Exception ignored)
        {
            o = null;
        }

        if (skipNullFields && o == null)
        {   // If skip null, skip field and return the same status on first field written indicator
            return first;
        }

        if (!first)
        {
            out.write(',');
            newLine();
        }

        writeJsonUtf8String(fieldName, out);
        out.write(':');


        if (o == null)
        {    // don't quote null
            out.write("null");
            return false;
        }

        Class type = field.getType();
        boolean forceType = o.getClass() != type;     // If types are not exactly the same, write "@type" field

        //When no type is written we can check the Object itself not the declaration
        if (MetaUtils.isPrimitive(type) || (neverShowType && MetaUtils.isPrimitive(o.getClass())))
        {
            writePrimitive(o, false);
        }
        else
        {
            writeImpl(o, forceType || alwaysShowType, true, true);
        }
        return false;
    }

    /**
     * Write out special characters "\b, \f, \t, \n, \r", as such, backslash as \\
     * quote as \" and values less than an ASCII space (20hex) as "\\u00xx" format,
     * characters in the range of ASCII space to a '~' as ASCII, and anything higher in UTF-8.
     *
     * @param s String to be written in UTF-8 format on the output stream.
     * @param output Writer to which the UTF-8 string will be written to
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
        writerCache.clear();
        writers.clear();
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
