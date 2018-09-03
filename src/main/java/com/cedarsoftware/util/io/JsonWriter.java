package com.cedarsoftware.util.io;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
 * graph using the above code.  Use the JsonWriter PRETTY_PRINT option to
 * format the the JSON to be human readable.
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
    /** If set, use the specified ClassLoader */
    public static final String CLASSLOADER = "CLASSLOADER";
    /** If set, writes the JSON in the form of a flat, non-nested structure */
    public static final String FLAT_STRUCTURE = "FLAT_MAP";

    private static Map<Class, JsonClassWriterBase> BASE_WRITERS;
    private final Map<Class, JsonClassWriterBase> writers = new HashMap<Class, JsonClassWriterBase>(BASE_WRITERS);  // Add customer writers (these make common classes more succinct)
    private final Map<Class, JsonClassWriterBase> writerCache = new HashMap<Class, JsonClassWriterBase>();
    private final Set<Class> notCustom = new HashSet<Class>();
    private static final Object[] byteStrings = new Object[256];
    private static final String NEW_LINE = System.getProperty("line.separator");
    private static final Long ZERO = 0L;
    private static final NullClass nullWriter = new NullClass();
    private final Map<Object, Long> objVisited = new IdentityHashMap<Object, Long>();
    private final Map<Object, Long> objsReferenced = new IdentityHashMap<Object, Long>();
    private final StringBuilder out;
	 // If we want a flat structure, we enclose the map in a JSON object body,
	 // We need a different ouput to prevent objects from being written in reverse discovering order :
    private final StringBuilder outFlat;
    private Map<String, String> typeNameMap = null;
    private boolean shortMetaKeys = false;
    private boolean neverShowType = false;
    private boolean alwaysShowType = false;
    private boolean isPrettyPrint = false;
    private boolean isEnumPublicOnly = false;
    private boolean writeLongsAsStrings = false;
    private boolean skipNullFields = false;
    private boolean isFlatStructure = false;
    private long identity = 1;
    private int depth = 0;
    /** _args is using ThreadLocal so that static inner classes can have access to them */
    final Map<String, Object> args = new HashMap<String, Object>();

    static
    {
        for (short i = -128; i <= 127; i++)
        {
            char[] chars = Integer.toString(i).toCharArray();
            byteStrings[i + 128] = chars;
        }

        Map<Class, JsonClassWriterBase> temp = new HashMap<Class, JsonClassWriterBase>();
        temp.put(String.class, new Writers.JsonStringWriter());
        temp.put(Date.class, new Writers.DateWriter());
        temp.put(AtomicBoolean.class, new Writers.AtomicBooleanWriter());
        temp.put(AtomicInteger.class, new Writers.AtomicIntegerWriter());
        temp.put(AtomicLong.class, new Writers.AtomicLongWriter());
        temp.put(BigInteger.class, new Writers.BigIntegerWriter());
        temp.put(BigDecimal.class, new Writers.BigDecimalWriter());
        temp.put(java.sql.Date.class, new Writers.DateWriter());
        temp.put(Timestamp.class, new Writers.TimestampWriter());
        temp.put(Calendar.class, new Writers.CalendarWriter());
        temp.put(TimeZone.class, new Writers.TimeZoneWriter());
        temp.put(Locale.class, new Writers.LocaleWriter());
        temp.put(Class.class, new Writers.ClassWriter());
        temp.put(StringBuilder.class, new Writers.StringBuilderWriter());
        temp.put(StringBuffer.class, new Writers.StringBufferWriter());
        BASE_WRITERS = temp;
    }

    /**
     * Common ancestor for JsonClassWriter and JsonClassWriterEx.
     */
    public interface JsonClassWriterBase
    { }

    /**
     * Implement this interface to customize the JSON output for a given class.
     */
    public interface JsonClassWriter extends JsonClassWriterBase
    {
        /**
         * When write() is called, it is expected that subclasses will write the appropriate JSON
         * to the passed in Writer.
         * @param o Object to be written in JSON format.
         * @param showType boolean indicating whether to show @type.
         * @param output Writer destination to where the actual JSON is written.
         * @throws IOException if thrown by the writer.  Will be caught at a higher level and wrapped in JsonIoException.
         */
        void write(Object o, boolean showType, final StringBuilder output) throws IOException;

        /**
         * @return boolean true if the class being written has a primitive (non-object) form.
         */
        boolean hasPrimitiveForm();

        /**
         * This method will be called to write the item in primitive form (if the response to hasPrimitiveForm()
         * was true).
         * @param o Object to be written
         * @param output Writer destination to where the actual JSON is written.
         * @throws IOException if thrown by the writer.  Will be caught at a higher level and wrapped in JsonIoException.
         */
        void writePrimitiveForm(Object o, final StringBuilder output) throws IOException;
    }

    /**
     * Implement this interface to customize the JSON output for a given class.
     */
    public interface JsonClassWriterEx extends JsonClassWriterBase
    {
        String JSON_WRITER = "JSON_WRITER";

        /**
         * When write() is called, it is expected that subclasses will write the appropriate JSON
         * to the passed in Writer.
         * @param o Object to be written in JSON format.
         * @param showType boolean indicating whether to show @type.
         * @param output Writer destination to where the actual JSON is written.
         * @param args Map of 'settings' arguments initially passed into the JsonWriter.
         * @throws IOException if thrown by the writer.  Will be caught at a higher level and wrapped in JsonIoException.
         */
        void write(Object o, boolean showType, final StringBuilder output, Map<String, Object> args) throws IOException;

        /**
         * If access to the JsonWriter is needed, JsonClassWriter's can access it by accessing Support.getWriter(args).
         * The args are the same arguments passed into the write(o, showType, args) method of JsonClassWriterEx.
         */
        class Support
        {
            /**
             * This method will return the JsonWriter instance performing the overall work.
             * @param args Map of settings initially passed to JsonWriter.
             * @return JsonWriter instance performing the work.
             */
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

    /**
     * Used internally to substitute type names.  For example, 'java.util.ArrayList, could have a substitute
     * type name of 'alist'.  Set substitute type names using the TYPE_NAME_MAP option.
     * @param typeName String name of type to substitute.
     * @return String substituted name, or null if there is no substitute.
     */
    protected String getSubstituteTypeNameIfExists(String typeName)
    {
        if (typeNameMap == null)
        {
            return null;
        }
        return typeNameMap.get(typeName);
    }

    /**
     * Used internally to substitute type names.  For example, 'java.util.ArrayList, could have a substitute
     * type name of 'alist'.  Set substitute type names using the TYPE_NAME_MAP option.
     * @param typeName String name of type to substitute.
     * @return String substituted type name.
     */
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
//          ByteArrayOutputStream stream = new ByteArrayOutputStream();
//            JsonWriter writer = new JsonWriter(stream, optionalArgs);
//          writer.write(item);
//          writer.close();
//          return new String(stream.toByteArray(), "UTF-8");
            
      	  	return new JsonWriter(optionalArgs).write(item);
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

//    /**
//     * @see JsonWriter#JsonWriter(OutputStream, Map)
//     * @param out OutputStream to which the JSON will be written.
//     */
//    public JsonWriter(OutputStream out)
//    {
//        this(out, null);
//    }

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
    public JsonWriter(
//   		 OutputStream out,
   		 Map<String, Object> optionalArgs)
    {
        if (optionalArgs == null)
        {
            optionalArgs = new HashMap<String, Object>();
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
        isFlatStructure = isTrue(args.get(FLAT_STRUCTURE));
        
        if (!args.containsKey(CLASSLOADER))
        {
            args.put(CLASSLOADER, JsonWriter.class.getClassLoader());
        }

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
                List<Field> newList = new ArrayList<Field>(fields.size());

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

//       try
//       {
//           this.out = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
//       }
//       catch (UnsupportedEncodingException e)
//       {
//           throw new JsonIoException("UTF-8 not supported on your JVM.  Unable to convert object to JSON.", e);
//       }

		this.out = new StringBuilder();
		this.outFlat = new StringBuilder();
    }

    /**
     * @return ClassLoader to be used by Custom Writers
     */
    ClassLoader getClassLoader()
    {
        return (ClassLoader) args.get(CLASSLOADER);
    }

    /**
     * @param setting Object setting value from JsonWriter args map.
     * @return boolean true if the value is (boolean) true, Boolean.TRUE, "true" (any case), or non-zero if a Number.
     */
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

    /**
     * Tab the output left (less indented)
     * @throws IOException
     */
    public void tabIn(final StringBuilder output) throws IOException
    {
        tab(output, 1);
    }

    /**
     * Add newline (\n) to output
     * @throws IOException
     */
    public void newLine(final StringBuilder output) throws IOException
    {
        tab(output, 0);
    }

    /**
     * Tab the output right (more indented)
     * @throws IOException
     */
    public void tabOut(final StringBuilder output) throws IOException
    {
        tab(output, -1);
    }

    /**
     * tab the JSON output by the given number of characters specified by delta.
     * @param output Writer being used for JSON outpt.
     * @param delta int number of characters to tab.
     * @throws IOException
     */
    private void tab(final StringBuilder output, int delta) throws IOException
    {
        if (!isPrettyPrint)
        {
            return;
        }
        output.append(NEW_LINE);
        depth += delta;
        for (int i=0; i < depth; i++)
        {
            output.append("  ");
        }
    }

    /**
     * Write the passed in object (o) to the JSON output stream, if and only if, there is a custom
     * writer associated to the Class of object (o).
     * @param o Object to be (potentially written)
     * @param showType boolean indicating whether or not to show @type.
     * @param output Writer where the actual JSON is being written to.
     * @return boolean true if written, false is there is no custom writer for the passed in object.
     */
    public boolean writeIfMatching(Object o, boolean showType, final StringBuilder output)
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

    /**
     * Write the passed in array element to the JSON output, if any only if, there is a customer writer
     * for the class of the instance 'o'.
     * @param arrayComponentClass Class type of the array
     * @param o Object instance to write
     * @param showType boolean indicating whether or not @type should be output.
     * @param output Writer to write the JSON to (if there is a custom writer for o's Class).
     * @return true if the array element was written, false otherwise.
     */
    public boolean writeArrayElementIfMatching(Class arrayComponentClass, Object o, boolean showType, final StringBuilder output)
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

    /**
     * Perform the actual custom writing for an array element that has a custom writer.
     * @param arrayComponentClass Class type of the array
     * @param o Object instance to write
     * @param showType boolean indicating whether or not @type should be output.
     * @param output Writer to write the JSON to (if there is a custom writer for o's Class).
     * @return true if the array element was written, false otherwise.
     */
    protected boolean writeCustom(Class arrayComponentClass, Object o, boolean showType, final StringBuilder output) throws IOException
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

        if (writeOptionalReference(o, output))
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

        output.append('{');
        tabIn(output);
        if (referenced)
        {
            writeId(getId(o), output);
            if (showType)
            {
                output.append(',');
                newLine(output);
            }
        }

        if (showType)
        {
            writeType(o, output);
        }

        if (referenced || showType)
        {
            output.append(',');
            newLine(output);
        }

        if (closestWriter instanceof JsonClassWriterEx)
        {
            ((JsonClassWriterEx)closestWriter).write(o, showType || referenced, output, args);
        }
        else
        {
            ((JsonClassWriter)closestWriter).write(o, showType || referenced, output);
        }
        tabOut(output);
        output.append('}');
        return true;
    }

    /**
     * Dummy place-holder class exists only because ConcurrentHashMap cannot contain a
     * null value.  Instead, singleton instance of this class is placed where null values
     * are needed.
     */
    static final class NullClass implements JsonClassWriterBase { }

    /**
     * Fetch the customer writer for the passed in Class.  If it is cached (already associated to the
     * passed in Class), return the same instance, otherwise, make a call to get the custom writer
     * and store that result.
     * @param c Class of object for which fetch a custom writer
     * @return JsonClassWriter/JsonClassWriterEx for the custom class (if one exists), null otherwise.
     */
    private JsonClassWriterBase getCustomWriter(Class c)
    {
        JsonClassWriterBase writer = writerCache.get(c);
        if (writer == null)
        {
            writer = forceGetCustomWriter(c);
            writerCache.put(c, writer);
        }
        return writer == nullWriter ? null : writer;
    }

    /**
     * Fetch the customer writer for the passed in Class.  This method always fetches the custom writer, doing
     * the complicated inheritance distance checking.  This method is only called when a cache miss has happened.
     * A sentinal 'nullWriter' is returned when no custom writer is found.  This prevents future cache misses
     * from re-attempting to find custom writers for classes that do not have a custom writer.
     * @param c Class of object for which fetch a custom writer
     * @return JsonClassWriter/JsonClassWriterEx for the custom class (if one exists), nullWriter otherwise.
     */
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
     * Add a permanent Customer Writer (Lifetime of JVM)
     * @param c Class to associate a custom JSON writer too
     * @param writer JsonClassWriterBase which implements the appropriate
     * subclass of JsonClassWriterBase (JsonClassWriter or JsonClassWriterEx).
     */
    public static void addWriterPermanent(Class c, JsonClassWriterBase writer)
    {
        BASE_WRITERS.put(c, writer);
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
    public String write(Object obj)
    {
   	 
   	  final StringBuilder output = this.out;
   	 
        traceReferences(obj);
        objVisited.clear();
        try
        {
      	  writeImpl(obj, true, output);
      	  
      	  if(isFlatStructure){
      		  // If we want a flat structure, we enclose the map in a JSON object body :
      		  // To prevent objects from being written in reverse discovering order :
      		  output.append(this.outFlat);
      		  output.insert(0, '{').append('}');
      	  }
        }
        catch (Exception e)
        {
            throw new JsonIoException("Error writing object to JSON:", e);
        }
        flush();
        objVisited.clear();
        objsReferenced.clear();
        
        
        return output.toString();
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
            	
            	if(!isFlatStructure){ // Default case
            	
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
          		
            	}else{
            		
            		// In the flat structure case, we give an id to al and every non-primitive object :
                  Long id = visited.get(obj);
                  if (id != null)
                  {   // Only write an object once.
                  	continue;
                  }else{
                  	// 1st time this object has been seen, we give it a unique ID and mark it referenced
                  	id = identity++;
                  	visited.put(obj, id);
                  	referenced.put(obj, id);
                  }
            		
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

    private boolean writeOptionalReference(Object obj, final StringBuilder output) throws IOException
    {

   	 if (obj == null)
        {
            return false;
        }

        if (MetaUtils.isLogicalPrimitive(obj.getClass()))
        {
            return false;
        }

        if (objVisited.containsKey(obj))
        {    // Only write (define) an object once in the JSON stream, otherwise emit a @ref
            String id = getId(obj);
            if (id == null)
            {   // Test for null because of Weak/Soft references being gc'd during serialization.
                return false;
            }
            output.append(shortMetaKeys ? "{\"@r\":" : "{\"@ref\":");
            output.append(id);
            output.append('}');
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
    public void writeImpl(Object obj, boolean showType, final StringBuilder output) throws IOException
    {
        writeImpl(obj, showType, true, true, output);
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
    public void writeImpl(Object obj, boolean showType, boolean allowRef, boolean allowCustom, final StringBuilder output) throws IOException
    {
        if (neverShowType)
        {
            showType = false;
        }
        if (obj == null)
        {
            output.append("null");
            return;
        }

        if (allowCustom && writeIfMatching(obj, showType, output))
        {
            return;
        }

        if (allowRef && writeOptionalReference(obj, output))
        {
            return;
        }

        if (obj.getClass().isArray())
        {
            writeArray(obj, showType, output);
        }
        else if (obj instanceof Collection)
        {
            writeCollection((Collection) obj, showType, output);
        }
        else if (obj instanceof JsonObject)
        {   // symmetric support for writing Map of Maps representation back as equivalent JSON format.
            JsonObject jObj = (JsonObject) obj;
            if (jObj.isArray())
            {
                writeJsonObjectArray(jObj, showType, output);
            }
            else if (jObj.isCollection())
            {
                writeJsonObjectCollection(jObj, showType, output);
            }
            else if (jObj.isMap())
            {
                if (!writeJsonObjectMapWithStringKeys(jObj, showType, output))
                {
                    writeJsonObjectMap(jObj, showType, output);
                }
            }
            else
            {
                writeJsonObjectObject(jObj, showType, output);
            }
        }
        else if (obj instanceof Map)
        {
            if (!writeMapWithStringKeys((Map) obj, showType, output))
            {
                writeMap((Map) obj, showType, output);
            }
        }
        else
        {
      	  
            StringBuilder sb = getWrittenObject(obj, showType, false);
            if(!isFlatStructure){
            	 // Default behavior :
            	output.append(sb);
            }else{
            	// If we want a flat structure :
            	// To prevent objects from being written in reverse discovering order :
             	if(output.length() == 0){
             		this.outFlat.insert(0, sb);
            	}else{
            		this.outFlat.insert(0, sb.insert(0,','));
            		writeOptionalReference(obj, output);
            	}
            }
            
        }
    }

    private void writeId(final String id, final StringBuilder output) throws IOException
    {
        output.append(shortMetaKeys ? "\"@i\":" : "\"@id\":");
        output.append(id == null ? "0" : id);
    }

    private void writeType(Object obj, final StringBuilder output) throws IOException
    {
        if (neverShowType)
        {
            return;
        }
        output.append(shortMetaKeys ? "\"@t\":\"" : "\"@type\":\"");
        final Class c = obj.getClass();
        String typeName = c.getName();
        String shortName = getSubstituteTypeNameIfExists(typeName);

        if (shortName != null)
        {
            output.append(shortName);
            output.append('"');
            return;
        }

        String s = c.getName();
        if (s.equals("java.lang.Boolean"))
        {
            output.append("boolean");
        }
        else if (s.equals("java.lang.Byte"))
        {
            output.append("byte");
        }
        else if (s.equals("java.lang.Character"))
        {
            output.append("char");
        }
        else if (s.equals("java.lang.Class"))
        {
            output.append("class");
        }
        else if (s.equals("java.lang.Double"))
        {
            output.append("double");
        }
        else if (s.equals("java.lang.Float"))
        {
            output.append("float");
        }
        else if (s.equals("java.lang.Integer"))
        {
            output.append("int");
        }
        else if (s.equals("java.lang.Long"))
        {
            output.append("long");
        }
        else if (s.equals("java.lang.Short"))
        {
            output.append("short");
        }
        else if (s.equals("java.lang.String"))
        {
            output.append("string");
        }
        else if (s.equals("java.util.Date"))
        {
            output.append("date");
        }
        else
        {
            output.append(c.getName());
        }

        output.append('"');
    }

    private void writePrimitive(final Object obj, boolean showType, final StringBuilder output) throws IOException
    {

        if (neverShowType)
        {
            showType = false;
        }
        if (obj instanceof Character)
        {
            writeJsonUtf8String(String.valueOf(obj), output);
        }
        else
        {
            if (obj instanceof Long && writeLongsAsStrings)
            {
                if (showType)
                {
                    output.append(shortMetaKeys ? "{\"@t\":\"" : "{\"@type\":\"");
                    output.append(getSubstituteTypeName("long"));
                    output.append("\",\"value\":\"");
                    output.append(obj.toString());
                    output.append("\"}");
                }
                else
                {
                    output.append('"');
                    output.append(obj.toString());
                    output.append('"');
                }
            }
            else if (obj instanceof Double && (Double.isNaN((Double) obj) || Double.isInfinite((Double) obj)))
            {
            	output.append("null");
            }
            else if (obj instanceof Float && (Float.isNaN((Float) obj) || Float.isInfinite((Float) obj)))
            {
                output.append("null");
            }
            else
            {
                output.append(obj.toString());
            }
        }
    }

    private void writeArray(final Object array, boolean showType, final StringBuilder output) throws IOException
    {
//   	  final StringBuilder output = this.out; // performance opt: place in final local for quicker access
        
   	  if (neverShowType)
        {
            showType = false;
        }
        Class arrayType = array.getClass();
        int len = Array.getLength(array);
        boolean referenced = objsReferenced.containsKey(array);
//        boolean typeWritten = showType && !(Object[].class == arrayType);    // causes IDE warning in NetBeans 7/4 Java 1.7
        boolean typeWritten = showType && !(arrayType.equals(Object[].class));

        if (typeWritten || referenced)
        {
            output.append('{');
            tabIn(output);
        }

        if (referenced)
        {
            writeId(getId(array), output);
            output.append(',');
            newLine(output);
        }

        if (typeWritten)
        {
            writeType(array, output);
            output.append(',');
            newLine(output);
        }

        if (len == 0)
        {
            if (typeWritten || referenced)
            {
                output.append(shortMetaKeys ? "\"@e\":[]" : "\"@items\":[]");
                tabOut(output);
                output.append('}');
            }
            else
            {
                output.append("[]");
            }
            return;
        }

        if (typeWritten || referenced)
        {
            output.append(shortMetaKeys ? "\"@i\":[" : "\"@items\":[");
        }
        else
        {
            output.append('[');
        }
        tabIn(output);

        final int lenMinus1 = len - 1;

        // Intentionally processing each primitive array type in separate
        // custom loop for speed. All of them could be handled using
        // reflective Array.get() but it is slower.  I chose speed over code length.
        if (byte[].class == arrayType)
        {
            writeByteArray((byte[]) array, lenMinus1, output);
        }
        else if (char[].class == arrayType)
        {
            writeJsonUtf8String(new String((char[]) array), output);
        }
        else if (short[].class == arrayType)
        {
            writeShortArray((short[]) array, lenMinus1, output);
        }
        else if (int[].class == arrayType)
        {
            writeIntArray((int[]) array, lenMinus1, output);
        }
        else if (long[].class == arrayType)
        {
            writeLongArray((long[]) array, lenMinus1, output);
        }
        else if (float[].class == arrayType)
        {
            writeFloatArray((float[]) array, lenMinus1, output);
        }
        else if (double[].class == arrayType)
        {
            writeDoubleArray((double[]) array, lenMinus1, output);
        }
        else if (boolean[].class == arrayType)
        {
            writeBooleanArray((boolean[]) array, lenMinus1, output);
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
                    output.append("null");
                }
                else if (writeArrayElementIfMatching(componentClass, value, false, output)) { }
                else if (isPrimitiveArray || value instanceof Boolean || value instanceof Long || value instanceof Double)
                {
                    writePrimitive(value, value.getClass() != componentClass, output);
                }
                else if (neverShowType && MetaUtils.isPrimitive(value.getClass()))
                {   // When neverShowType specified, do not allow primitives to show up as {"value":6} for example.
                    writePrimitive(value, false, output);
                }
                else
                {   // Specific Class-type arrays - only force type when
                    // the instance is derived from array base class.
                    boolean forceType = !(value.getClass() == componentClass);
                    writeImpl(value, forceType || alwaysShowType, output);
                }

                if (i != lenMinus1)
                {
                    output.append(',');
                    newLine(output);
                }
            }
        }

        tabOut(output);
        output.append(']');
        if (typeWritten || referenced)
        {
            tabOut(output);
            output.append('}');
        }
    }

    private void writeBooleanArray(boolean[] booleans, int lenMinus1, final StringBuilder output) throws IOException
    {
        for (int i = 0; i < lenMinus1; i++)
        {
            output.append(booleans[i] ? "true," : "false,");
        }
        output.append(Boolean.toString(booleans[lenMinus1]));
    }

    private void writeDoubleArray(double[] doubles, int lenMinus1, final StringBuilder output) throws IOException
    {
        for (int i = 0; i < lenMinus1; i++)
        {
            output.append(doubleToString(doubles[i]));
            output.append(',');
        }
        output.append(doubleToString(doubles[lenMinus1]));
    }

    private void writeFloatArray(float[] floats, int lenMinus1, final StringBuilder output) throws IOException
    {
        for (int i = 0; i < lenMinus1; i++)
        {
            output.append(floatToString(floats[i]));
            output.append(',');
        }
        output.append(floatToString(floats[lenMinus1]));
    }

    private String doubleToString(double d)
    {
    	return (Double.isNaN(d) || Double.isInfinite(d)) ? "null" : Double.toString(d);
    }

    private String floatToString(float d)
    {
    	return (Float.isNaN(d) || Float.isInfinite(d)) ? "null" : Float.toString(d);
    }

    private void writeLongArray(long[] longs, int lenMinus1, final StringBuilder output) throws IOException
    {
        if (writeLongsAsStrings)
        {
            for (int i = 0; i < lenMinus1; i++)
            {
                output.append('"');
                output.append(Long.toString(longs[i]));
                output.append('"');
                output.append(',');
            }
            output.append('"');
            output.append(Long.toString(longs[lenMinus1]));
            output.append('"');
        }
        else
        {
            for (int i = 0; i < lenMinus1; i++)
            {
                output.append(Long.toString(longs[i]));
                output.append(',');
            }
            output.append(Long.toString(longs[lenMinus1]));
        }
    }

    private void writeIntArray(int[] ints, int lenMinus1, final StringBuilder output) throws IOException
    {
        for (int i = 0; i < lenMinus1; i++)
        {
            output.append(Integer.toString(ints[i]));
            output.append(',');
        }
        output.append(Integer.toString(ints[lenMinus1]));
    }

    private void writeShortArray(short[] shorts, int lenMinus1, final StringBuilder output) throws IOException
    {
        for (int i = 0; i < lenMinus1; i++)
        {
            output.append(Integer.toString(shorts[i]));
            output.append(',');
        }
        output.append(Integer.toString(shorts[lenMinus1]));
    }

    private void writeByteArray(byte[] bytes, int lenMinus1, final StringBuilder output) throws IOException
    {
        final Object[] byteStrs = byteStrings;
        for (int i = 0; i < lenMinus1; i++)
        {
            output.append((char[]) byteStrs[bytes[i] + 128]);
            output.append(',');
        }
        output.append((char[]) byteStrs[bytes[lenMinus1] + 128]);
    }

    private void writeCollection(Collection col, boolean showType, final StringBuilder output) throws IOException
    {
   	  if (neverShowType)
        {
            showType = false;
        }
        boolean referenced = objsReferenced.containsKey(col);
        boolean isEmpty = col.isEmpty();

        if (referenced || showType)
        {
            output.append('{');
            tabIn(output);
        }
        else if (isEmpty)
        {
            output.append('[');
        }

        writeIdAndTypeIfNeeded(col, showType, referenced, output);

        if (isEmpty)
        {
            if (referenced || showType)
            {
                tabOut(output);
                output.append('}');
            }
            else
            {
                output.append(']');
            }
            return;
        }

        beginCollection(showType, referenced, output);
        Iterator i = col.iterator();

        writeElements(output, i);

        tabOut(output);
        output.append(']');
        if (showType || referenced)
        {   // Finished object, as it was output as an object if @id or @type was output
            tabOut(output);
            output.append("}");
        }
    }

    private void writeElements(final StringBuilder output, Iterator i) throws IOException
    {
        while (i.hasNext())
        {
            writeCollectionElement(i.next(), output);

            if (i.hasNext())
            {
                output.append(',');
                newLine(output);
            }
        }
    }

    private void writeIdAndTypeIfNeeded(Object col, boolean showType, boolean referenced, final StringBuilder output) throws IOException
    {
        if (neverShowType)
        {
            showType = false;
        }
        if (referenced)
        {
            writeId(getId(col), output);
        }

        if (showType)
        {
            if (referenced)
            {
            	output.append(',');
                newLine(output);
            }
            writeType(col, output);
        }
    }

    private void beginCollection(boolean showType, boolean referenced, final StringBuilder output) throws IOException
    {
        if (showType || referenced)
        {
      	  output.append(',');
           newLine(output);
           output.append(shortMetaKeys ? "\"@e\":[" : "\"@items\":[");
        }
        else
        {
      	  output.append('[');
        }
        tabIn(output);
    }

    private void writeJsonObjectArray(JsonObject jObj, boolean showType, final StringBuilder output) throws IOException
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
            arrayClass = MetaUtils.classForName(type, getClassLoader());
        }

        final boolean isObjectArray = Object[].class == arrayClass;
        final Class componentClass = arrayClass.getComponentType();
        boolean referenced = objsReferenced.containsKey(jObj) && jObj.hasId();
        boolean typeWritten = showType && !isObjectArray;

        if (typeWritten || referenced)
        {
            output.append('{');
            tabIn(output);
        }

        if (referenced)
        {
            writeId(Long.toString(jObj.id), output);
            output.append(',');
            newLine(output);
        }

        if (typeWritten)
        {
            output.append(shortMetaKeys ? "\"@t\":\"" : "\"@type\":\"");
            output.append(getSubstituteTypeName(arrayClass.getName()));
            output.append("\",");
            newLine(output);
        }

        if (len == 0)
        {
            if (typeWritten || referenced)
            {
                output.append(shortMetaKeys ? "\"@e\":[]" : "\"@items\":[]");
                tabOut(output);
                output.append("}");
            }
            else
            {
                output.append("[]");
            }
            return;
        }

        if (typeWritten || referenced)
        {
            output.append(shortMetaKeys ? "\"@e\":[" : "\"@items\":[");
        }
        else
        {
            output.append('[');
        }
        tabIn(output);

        Object[] items = (Object[]) jObj.get("@items");
        final int lenMinus1 = len - 1;

        for (int i = 0; i < len; i++)
        {
            final Object value = items[i];

            if (value == null)
            {
                output.append("null");
            }
            else if (Character.class == componentClass || char.class == componentClass)
            {
                writeJsonUtf8String((String) value, output);
            }
            else if (value instanceof Boolean || value instanceof Long || value instanceof Double)
            {
                writePrimitive(value, value.getClass() != componentClass, output);
            }
            else if (neverShowType && MetaUtils.isPrimitive(value.getClass()))
            {
                writePrimitive(value, false, output);
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
                writeImpl(value, forceType || alwaysShowType, output);
            }

            if (i != lenMinus1)
            {
                output.append(',');
                newLine(output);
            }
        }

        tabOut(output);
        output.append(']');
        if (typeWritten || referenced)
        {
            tabOut(output);
            output.append('}');
        }
    }

    private void writeJsonObjectCollection(JsonObject jObj, boolean showType, final StringBuilder output) throws IOException
    {
        if (neverShowType)
        {
            showType = false;
        }
        String type = jObj.type;
        Class colClass = MetaUtils.classForName(type, getClassLoader());
        boolean referenced = objsReferenced.containsKey(jObj) && jObj.hasId();
        int len = jObj.getLength();

        if (referenced || showType || len == 0)
        {
            output.append('{');
            tabIn(output);
        }

        if (referenced)
        {
            writeId(String.valueOf(jObj.id), output);
        }

        if (showType)
        {
            if (referenced)
            {
                output.append(',');
                newLine(output);
            }
            output.append(shortMetaKeys ? "\"@t\":\"" : "\"@type\":\"");
            output.append(getSubstituteTypeName(colClass.getName()));
            output.append('"');
        }

        if (len == 0)
        {
            tabOut(output);
            output.append('}');
            return;
        }

        beginCollection(showType, referenced, output);

        Object[] items = (Object[]) jObj.get("@items");
        final int itemsLen = items.length;
        final int itemsLenMinus1 = itemsLen - 1;

        for (int i=0; i < itemsLen; i++)
        {
            writeCollectionElement(items[i], output);

            if (i != itemsLenMinus1)
            {
                output.append(',');
                newLine(output);
            }
        }

        tabOut(output);
        output.append("]");
        if (showType || referenced)
        {
            tabOut(output);
            output.append('}');
        }
    }

    private void writeJsonObjectMap(JsonObject jObj, boolean showType, final StringBuilder output) throws IOException
    {
        if (neverShowType)
        {
            showType = false;
        }
        boolean referenced = objsReferenced.containsKey(jObj) && jObj.hasId();

        output.append('{');
        tabIn(output);
        if (referenced)
        {
            writeId(String.valueOf(jObj.getId()), output);
        }

        if (showType)
        {
            if (referenced)
            {
                output.append(',');
                newLine(output);
            }
            String type = jObj.getType();
            if (type != null)
            {
                Class mapClass = MetaUtils.classForName(type, getClassLoader());
                output.append(shortMetaKeys ? "\"@t\":\"" : "\"@type\":\"");
                output.append(getSubstituteTypeName(mapClass.getName()));
                output.append('"');
            }
            else
            {   // type not displayed
                showType = false;
            }
        }

        if (jObj.isEmpty())
        {   // Empty
            tabOut(output);
            output.append('}');
            return;
        }

        if (showType)
        {
            output.append(',');
            newLine(output);
        }

        output.append(shortMetaKeys ? "\"@k\":[" : "\"@keys\":[");
        tabIn(output);
        Iterator i = jObj.keySet().iterator();

        writeElements(output, i);

        tabOut(output);
        output.append("],");
        newLine(output);
        output.append(shortMetaKeys ? "\"@e\":[" : "\"@items\":[");
        tabIn(output);
        i =jObj.values().iterator();

        writeElements(output, i);

        tabOut(output);
        output.append(']');
        tabOut(output);
        output.append('}');
    }


    private boolean writeJsonObjectMapWithStringKeys(JsonObject jObj, boolean showType, final StringBuilder output) throws IOException
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
        output.append('{');
        tabIn(output);

        if (referenced)
        {
            writeId(String.valueOf(jObj.getId()), output);
        }

        if (showType)
        {
            if(referenced)
            {
                output.append(',');
                newLine(output);
            }
            String type = jObj.getType();
            if (type != null)
            {
                Class mapClass = MetaUtils.classForName(type, getClassLoader());
                output.append(shortMetaKeys ? "\"@t\":\"" : "\"@type\":\"");
                output.append(getSubstituteTypeName(mapClass.getName()));
                output.append('"');
            }
            else
            { // type not displayed
                showType = false;
            }
        }

        if (jObj.isEmpty())
        { // Empty
            tabOut(output);
            output.append('}');
            return true;
        }

        if (showType)
        {
            output.append(',');
            newLine(output);
        }

        return writeMapBody(jObj.entrySet().iterator(), output);
    }

    /**
     * Write fields of an Object (JsonObject)
     */
    private void writeJsonObjectObject(JsonObject jObj, boolean showType, final StringBuilder output) throws IOException
    {
        if (neverShowType)
        {
            showType = false;
        }
        boolean referenced = objsReferenced.containsKey(jObj) && jObj.hasId();
        showType = showType && jObj.type != null;
        Class type = null;

        output.append('{');
        tabIn(output);
        if (referenced)
        {
            writeId(String.valueOf(jObj.id), output);
        }

        if (showType)
        {
            if (referenced)
            {
                output.append(',');
                newLine(output);
            }
            output.append(shortMetaKeys ? "\"@t\":\"" : "\"@type\":\"");
            output.append(getSubstituteTypeName(jObj.type));
            output.append('"');
            try  { type = MetaUtils.classForName(jObj.type, getClassLoader()); } catch(Exception ignored) { type = null; }
        }

        if (jObj.isEmpty())
        {
            tabOut(output);
            output.append('}');
            return;
        }

        if (showType || referenced)
        {
            output.append(',');
            newLine(output);
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
                output.append(',');
                newLine(output);
            }
            first = false;
            final String fieldName = entry.getKey();
            output.append('"');
            output.append(fieldName);
            output.append("\":");
            Object value = entry.getValue();

            if (value == null)
            {
                output.append("null");
            }
            else if (neverShowType && MetaUtils.isPrimitive(value.getClass()))
            {
                writePrimitive(value, false, output);
            }
            else if (value instanceof BigDecimal || value instanceof BigInteger)
            {
                writeImpl(value, !doesValueTypeMatchFieldType(type, fieldName, value), output);
            }
            else if (value instanceof Number || value instanceof Boolean)
            {
                output.append(value.toString());
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
                writeImpl(value, !doesValueTypeMatchFieldType(type, fieldName, value), output);
            }
        }
        tabOut(output);
        output.append('}');
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

    private void writeMap(Map map, boolean showType, final StringBuilder output) throws IOException
    {
        if (neverShowType)
        {
            showType = false;
        }
        boolean referenced = objsReferenced.containsKey(map);

        output.append('{');
        tabIn(output);
        if (referenced)
        {
            writeId(getId(map), output);
        }

        if (showType)
        {
            if (referenced)
            {
                output.append(',');
                newLine(output);
            }
            writeType(map, output);
        }

        if (map.isEmpty())
        {
            tabOut(output);
            output.append('}');
            return;
        }

        if (showType || referenced)
        {
            output.append(',');
            newLine(output);
        }

        output.append(shortMetaKeys ? "\"@k\":[" : "\"@keys\":[");
        tabIn(output);
        Iterator i = map.keySet().iterator();

        writeElements(output, i);

        tabOut(output);
        output.append("],");
        newLine(output);
        output.append(shortMetaKeys ? "\"@e\":[" : "\"@items\":[");
        tabIn(output);
        i = map.values().iterator();

        writeElements(output, i);

        tabOut(output);
        output.append(']');
        tabOut(output);
        output.append('}');
    }


    private boolean writeMapWithStringKeys(Map map, boolean showType, final StringBuilder output) throws IOException
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

        output.append('{');
        tabIn(output);
        writeIdAndTypeIfNeeded(map, showType, referenced, output);

        if (map.isEmpty())
        {
            tabOut(output);
            output.append('}');
            return true;
        }

        if (showType || referenced)
        {
            output.append(',');
            newLine(output);
        }

       return writeMapBody(map.entrySet().iterator(), output);
    }

    private boolean writeMapBody(final Iterator i, final StringBuilder output) throws IOException
    {
        while (i.hasNext())
        {
            Entry att2value = (Entry) i.next();
            writeJsonUtf8String((String)att2value.getKey(), output);
            output.append(":");

            writeCollectionElement(att2value.getValue(), output);

            if (i.hasNext())
            {
                output.append(',');
                newLine(output);
            }
        }

        tabOut(output);
        output.append('}');
        return true;
    }

    /**
     * Ensure that all keys within the Map are String instances
     * @param map Map to inspect that all keys are primitive.  This allows the output JSON
     *            to be optimized into {"key1":value1, "key2": value2} format if all the
     *            keys of the Map are Strings.  If not, then a Map is written as two
     *            arrays, an @keys array and an @items array.  This allows support for Maps
     *            with non-String keys.
     */
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
    private void writeCollectionElement(Object o, final StringBuilder output) throws IOException
    {
        if (o == null)
        {
            output.append("null");
        }
        else if (o instanceof Boolean || o instanceof Double)
        {
            writePrimitive(o, false, output);
        }
        else if (o instanceof Long)
        {
            writePrimitive(o, writeLongsAsStrings, output);
        }
        else if (o instanceof String)
        {   // Never do an @ref to a String (they are treated as logical primitives and intern'ed on read)
            writeJsonUtf8String((String) o, output);
        }
        else if (neverShowType && MetaUtils.isPrimitive(o.getClass()))
        {   // If neverShowType, then force primitives (and primitive wrappers)
            // to be output with toString() - prevents {"value":6} for example
            writePrimitive(o, false, output);
        }
        else
        {
            writeImpl(o, true, output);
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
    public StringBuilder getWrittenObject(final Object obj, boolean showType, boolean bodyOnly) throws IOException
    {
   	  final StringBuilder output = new StringBuilder();
//   	  final StringBuilder output = this.out;

        if (neverShowType)
        {
            showType = false;
        }
        final boolean referenced = objsReferenced.containsKey(obj);
        if (!bodyOnly)
        {
      	
      	  	if(isFlatStructure){
      	  		// If we want a flat structure JSON output :
		        	Long idObj = this.objsReferenced.get(obj);
		        	String idStr = String.format("\"%d\":", idObj);
		        	output.insert(0, idStr);
      	  	}
      	  
            output.append('{');
            tabIn(output);
            if (referenced)
            {
                writeId(getId(obj), output);
            }

            if (referenced && showType)
            {
                output.append(',');
                newLine(output);
            }

            if (showType)
            {
                writeType(obj, output);
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
                    first = writeField(obj, first, field.getName(), field, true, output);
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
                    first = writeField(obj, first, fieldName, field, false, output);
                }//else field is black listed.
            }
        }

        if (!bodyOnly)
        {
            tabOut(output);
            output.append('}');
        }
        
        
        return output;
    }

    private boolean writeField(Object obj, boolean first, String fieldName, Field field, boolean allowTransient, final StringBuilder output) throws IOException
    {
   	 
        if (!allowTransient && (field.getModifiers() & Modifier.TRANSIENT) != 0)
        {   // Do not write transient fields
            return first;
        }

        int modifiers = field.getModifiers();
        if (Enum.class.isAssignableFrom(field.getDeclaringClass()))
        {
            if (!"name".equals(field.getName()))
            {
                if (!Modifier.isPublic(modifiers) && isEnumPublicOnly)
                {
                    return first;
                }
                if ("ordinal".equals(field.getName()) || "internal".equals(field.getName()))
                {
                    return first;
                }
            }
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
            output.append(',');
            newLine(output);
        }

        writeJsonUtf8String(fieldName, output);
        output.append(':');


        if (o == null)
        {    // don't quote null
            output.append("null");
            return false;
        }

        Class type = field.getType();
        boolean forceType = o.getClass() != type;     // If types are not exactly the same, write "@type" field

        //When no type is written we can check the Object itself not the declaration
        if (MetaUtils.isPrimitive(type) || (neverShowType && MetaUtils.isPrimitive(o.getClass())))
        {
            writePrimitive(o, false, output);
        }
        else
        {
            writeImpl(o, forceType || alwaysShowType, true, true, output);
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
    public static void writeJsonUtf8String(String s, final StringBuilder output) throws IOException
    {
        output.append('\"');
        final int len = s.length();

        for (int i = 0; i < len; i++)
        {
            char c = s.charAt(i);

            if (c < ' ')
            {    // Anything less than ASCII space, write either in \\u00xx form, or the special \t, \n, etc. form
                switch (c)
                {
                    case '\b':
                        output.append("\\b");
                        break;
                    case '\f':
                        output.append("\\f");
                        break;
                    case '\n':
                        output.append("\\n");
                        break;
                    case '\r':
                        output.append("\\r");
                        break;
                    case '\t':
                        output.append("\\t");
                        break;
                    default:
                        output.append(String.format("\\u%04X", (int)c));
                        break;
                }
            }
            else if (c == '\\' || c == '"')
            {
                output.append('\\');
                output.append(c);
            }
            else
            {   // Anything else - write in UTF-8 form (multi-byte encoded) (OutputStreamWriter is UTF-8)
                output.append(c);
            }
        }
        output.append('\"');
    }

    @Override
    public void flush()
    {
//        try
//        {
//            if (out != null)
//            {
//                out.flush();
//            }
//        }
//        catch (Exception ignored) { }
    }

    @Override
	public void close()
    {
//        try
//        {
//            out.close();
//        }
//        catch (Exception ignore) { }
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
