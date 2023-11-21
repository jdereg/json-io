package com.cedarsoftware.util.io;

import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.ClassDescriptors;
import lombok.Getter;
import lombok.Setter;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import static com.cedarsoftware.util.io.JsonObject.ITEMS;

/**
 * Output a Java object graph in JSON format.  This code handles cyclic
 * references and can serialize any Object graph without requiring a class
 * to be 'Serializable' or have any specific methods on it.
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
 * format the JSON to be human-readable.
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
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class JsonWriter implements Closeable, Flushable
{
    private final Map<Class<?>, JsonClassWriter> writerCache = new HashMap<>();
    public static final Set<String> EMPTY_SET = new HashSet<>();
    private static final Object[] byteStrings = new Object[256];
    private static final String NEW_LINE = System.getProperty("line.separator");
    private static final Long ZERO = 0L;
    public static final NullClass nullWriter = new NullClass();

    private WriteOptions optionsCache = null;
    /**
     * @return Map containing all objects that were visited within input object graph
     */
    @Getter
    private final Map<Object, Long> objVisited = new IdentityHashMap<>();

    /**
     * @return Map containing all objects that were referenced within input object graph.
     */
    @Getter
    private final Map<Object, Long> objsReferenced = new IdentityHashMap<>();

    private final Writer out;

    private long identity = 1;
    private int depth = 0;

    /** _args is using ThreadLocal so that static inner classes can have access to them */
    final Map<String, Object> args = new HashMap<>();

    static
    {
        for (short i = -128; i <= 127; i++)
        {
            char[] chars = Integer.toString(i).toCharArray();
            byteStrings[i + 128] = chars;
        }
    }

    /**
     * -- GETTER --
     *
     * @return boolean the allowsNanAndInfinity flag
     */
    @Setter
    @Getter
    private static volatile boolean allowNanAndInfinity = false;

    /**
     * Common ancestor for JsonClassWriter and JsonClassWriter.
     */
    @Deprecated
    public interface JsonClassWriterBase
    {
    }

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
         * @param writeOptions Map of 'settings' arguments initially passed into the JsonWriter.
         * @throws IOException if thrown by the writer.  Will be caught at a higher level and wrapped in JsonIoException.
         */
        default void write(Object o, boolean showType, Writer output, WriteOptions writeOptions) throws IOException {
            this.write(o, showType, output);
        }

        /**
         * When write() is called, it is expected that subclasses will write the appropriate JSON
         * to the passed in Writer.
         * @param o Object to be written in JSON format.
         * @param showType boolean indicating whether to show @type.
         * @param output Writer destination to where the actual JSON is written.
         * @throws IOException if thrown by the writer.  Will be caught at a higher level and wrapped in JsonIoException.
         */
        default void write(Object o, boolean showType, Writer output) throws IOException {
        }

        /**
         * @return boolean true if the class being written has a primitive (non-object) form.  Default is false since
         * most custom writers will not have a primitive form.
         */
        default boolean hasPrimitiveForm() { return false; }

        /**
         * This default implementation will call the more basic writePrimitiveForm that does not take arguments.  No need
         * to override this method unless you need access to the args.
         * @param o Object to be written
         * @param output Writer destination to where the actual JSON is written.
         * @param writeOptions Map of 'settings' arguments initially passed into the JsonWriter.
         * @throws IOException if thrown by the writer.  Will be caught at a higher level and wrapped in JsonIoException.
         */
        default void writePrimitiveForm(Object o, Writer output, WriteOptions writeOptions) throws IOException {
            this.writePrimitiveForm(o, output);
        }

        /**
         * This method will be called to write the item in primitive form (if the response to hasPrimitiveForm()
         * was true).  Override this method if you have a primitive form and need to access the arguments that kicked
         * off the JsonWriter.
         * @param o Object to be written
         * @param output Writer destination to where the actual JSON is written.
         * @throws IOException if thrown by the writer.  Will be caught at a higher level and wrapped in JsonIoException.
         */
        default void writePrimitiveForm(Object o, Writer output) throws IOException {}
    }

    /**
     * Implement this interface to customize the JSON output for a given class.
     */
    @Deprecated
    public interface JsonClassWriterEx extends JsonClassWriter
    {
        /**
         * If access to the JsonWriter is needed, JsonClassWriter's can access it by accessing Support.getWriter(args).
         * The args are the same arguments passed into the write(o, showType, args) method of JsonClassWriterEx.
         */
        @Deprecated
        class Support
        {
            /**
             * This method will return the JsonWriter instance performing the overall work.
             * @param args Map of settings initially passed to JsonWriter.
             * @return JsonWriter instance performing the work.
             */
            public static JsonWriter getWriter(Map<String, Object> args)
            {
                // proper way to get JsonWriter now.
                return WriterContext.instance().getWriter();
            }
        }
    }

    /**
     * Used internally to substitute type names.  For example, 'java.util.ArrayList, could have a substitute
     * type name of 'alist'.  Set substitute type names using the TYPE_NAME_MAP option.
     * @param typeName String name of type to substitute.
     * @return String substituted name, or null if there is no substitute.
     */
    protected String getSubstituteTypeNameIfExists(String typeName)
    {
        if (getWriteOptions().aliasTypeNames().isEmpty()) {
            return null;
        }

        return getWriteOptions().aliasTypeNames().get(typeName);
    }

    /**
     * Used internally to substitute type names.  For example, 'java.util.ArrayList, could have a substitute
     * type name of 'alist'.  Set substitute type names using the TYPE_NAME_MAP option.
     * @param typeName String name of type to substitute.
     * @return String substituted type name.
     */
    protected String getSubstituteTypeName(String typeName)
    {
        return getWriteOptions().getTypeNameAlias(typeName);
    }

    /**
     * @see JsonWriter#toJson(Object, WriteOptions)
     * @param item Object (root) to serialized to JSON String.
     * @return String of JSON format representing complete object graph rooted by item.
     */
    @Deprecated
    public static String objectToJson(Object item)
    {
        return toJson(item, new WriteOptions());
    }

    /**
     * Convert a Java Object to a JSON String.
     *
     * @param item Object to convert to a JSON String.
     * @param writeOptions Feature arguments indicating how dates are formatted,
     * what fields are written out (optional).
     * @see WriteOptions
     * @return String containing JSON representation of passed in object root.
     */
    @Deprecated
    public static String objectToJson(Object item, WriteOptions writeOptions)
    {
        try
        {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            JsonWriter writer = new JsonWriter(stream, writeOptions);
            writer.write(item);
            writer.close();
            return new String(stream.toByteArray(), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            throw new JsonIoException("Unable to convert object to JSON", e);
        }
    }

    /**
     * Convert a Java Object to a JSON String.
     *
     * @param item Object to convert to a JSON String.
     * @param writeOptions (optional can be null for defaults) Map of extra arguments indicating how dates are
     * formatted and what fields are written out (optional).  For Date parameters, use the public
     * static DATE_TIME key, and then use the ISO_DATE or ISO_DATE_TIME indicators.  Or you can specify
     * your own custom SimpleDateFormat String, or you can associate a SimpleDateFormat object,
     * in which case it will be used.  This setting is for both java.util.Date and java.sql.Date.
     * If the DATE_FORMAT key is not used, then dates will be formatted as longs.  This long can
     * be turned back into a date by using 'new Date(longValue)'.
     * @return String containing JSON representation of passed in object root.
     */
    public static String toJson(Object item, WriteOptions writeOptions) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            JsonWriter writer = new JsonWriter(stream, WriteOptions.copyIfNeeded(writeOptions));
            writer.write(item);
            writer.close();
            return new String(stream.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new JsonIoException("Unable to convert object to JSON", e);
        }
    }

    /**
     * Format the passed in JSON string in a nice, human-readable format.
     * @param json String input JSON
     * @return String containing equivalent JSON, formatted nicely for human readability.
     * @deprecated use JsonUtilities.formatJson(json);
     */
    @Deprecated
    public static String formatJson(String json)
    {
        return JsonUtilities.formatJson(json);
    }

    /**
     * Format the passed in JSON string in a nice, human-readable format.
     *
     * @param json String input JSON
     * @param readingArgs (optional) Map of extra arguments for parsing json.  Can be null.
     * @param writeOptions (optional) Map of extra arguments for writing out json.  Can be null.
     * @return String containing equivalent JSON, formatted nicely for human readability.
     * @deprecated use JsonUtilities.formatJson(json, readOption, writeOptions);
     */
    @Deprecated
    public static String formatJson(String json, Map readingArgs, WriteOptions writeOptions)
    {
        ReadOptions readOptions = ReadOptionsBuilder.fromMap(readingArgs)
                .returnAsMaps()
                .build();

        return JsonUtilities.formatJson(json, readOptions, WriteOptions.copyIfNeeded(writeOptions).prettyPrint(true));
    }

    /**
     * @param out OutputStream to which the JSON will be written.  Uses the default WriteOptions.
     * @see WriteOptions
     */
    public JsonWriter(OutputStream out)
    {
        this(out, new WriteOptions());
    }

    /**
     * @param out OutputStream to which the JSON output will be written.
     * @param writeOptions WriteOptions containing many feature options to control the JSON output.  Can be null,
     *                     in which case the default WriteOptions will be used.
     * @see WriteOptions Javadoc.
     */
    public JsonWriter(OutputStream out, WriteOptions writeOptions) {
        if (writeOptions == null)
        {
            writeOptions = new WriteOptions();
        }
        this.out = new FastWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        WriterContext.instance().initialize(writeOptions, this);
    }

    /**
     * Get a copy of the writeOptions that are in effect for this instance of the JsonWriter.
     */
    public WriteOptions getWriteOptions()
    {
        if (optionsCache == null)
        {
            optionsCache = WriterContext.instance().getWriteOptions();
        }
        return optionsCache;
    }

    /**
     * @return ClassLoader to be used by Custom Writers
     */
    ClassLoader getClassLoader()
    {
        return getWriteOptions().getClassLoader();
    }

    /**
     * Tab the output left (less indented)
     * @throws IOException
     */
    public void tabIn() throws IOException
    {
        tab(out, 1);
    }

    /**
     * Add newline (\n) to output
     * @throws IOException
     */
    public void newLine() throws IOException
    {
        tab(out, 0);
    }

    /**
     * Tab the output right (more indented)
     * @throws IOException
     */
    public void tabOut() throws IOException
    {
        tab(out, -1);
    }

    /**
     * tab the JSON output by the given number of characters specified by delta.
     * @param output Writer being used for JSON output.
     * @param delta int number of characters to tab.
     * @throws IOException
     */
    private void tab(Writer output, int delta) throws IOException
    {
        if (!getWriteOptions().isPrettyPrint())
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

    /**
     * Write the passed in object (o) to the JSON output stream, if and only if, there is a custom
     * writer associated to the Class of object (o).
     * @param o Object to be (potentially written)
     * @param showType boolean indicating whether to show @type.
     * @param output Writer where the actual JSON is being written to.
     * @return boolean true if written, false is there is no custom writer for the passed in object.
     */
    public boolean writeUsingCustomWriter(Object o, boolean showType, Writer output)
    {
        WriteOptions writeOptions = getWriteOptions();
        if (writeOptions.isNeverShowingType())
        {
            showType = false;
        }
        Class<?> c = o.getClass();

        if (writeOptions.isNotCustomWrittenClass(c))
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
     * Write the passed in array element to the JSON output, if any only if, there is a custom writer
     * for the class of the instance 'o'.
     * @param arrayComponentClass Class type of the array
     * @param o Object instance to write
     * @param showType boolean indicating whether @type should be output.
     * @param output Writer to write the JSON to (if there is a custom writer for o's Class).
     * @return true if the array element was written, false otherwise.
     */
    public boolean writeArrayElementIfMatching(Class<?> arrayComponentClass, Object o, boolean showType, Writer output)
    {
        if (!o.getClass().isAssignableFrom(arrayComponentClass) || getWriteOptions().isNotCustomWrittenClass(o.getClass()))
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
     * @param showType boolean indicating whether @type should be output.
     * @param output Writer to write the JSON to (if there is a custom writer for o's Class).
     * @return true if the array element was written, false otherwise.
     */
    protected boolean writeCustom(Class<?> arrayComponentClass, Object o, boolean showType, Writer output) throws IOException
    {
        if (getWriteOptions().isNeverShowingType())
        {
            showType = false;
        }
        JsonClassWriter closestWriter = getCustomWriter(arrayComponentClass);

        if (closestWriter == null)
        {
            return false;
        }

        if (writeOptionalReference(o))
        {
            return true;
        }

        boolean referenced = objsReferenced.containsKey(o);

        if (closestWriter.hasPrimitiveForm())
        {
            if ((!referenced && !showType) || closestWriter instanceof Writers.JsonStringWriter)
            {
                closestWriter.writePrimitiveForm(o, output, getWriteOptions());
                return true;
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

        closestWriter.write(o, showType || referenced, output, getWriteOptions());

        tabOut();
        output.write('}');
        return true;
    }

    /**
     * Dummy place-holder class exists only because ConcurrentHashMap cannot contain a
     * null value.  Instead, singleton instance of this class is placed where null values
     * are needed.
     */
    static final class NullClass implements JsonWriter.JsonClassWriter {}

    /**
     * Fetch the custom writer for the passed in Class.  If it is cached (already associated to the
     * passed in Class), return the same instance, otherwise, make a call to get the custom writer
     * and store that result.
     * @param c Class of object for which fetch a custom writer
     * @return JsonClassWriter for the custom class (if one exists), null otherwise.
     */
    private JsonClassWriter getCustomWriter(Class<?> c)
    {
        JsonClassWriter writer = writerCache.get(c);
        if (writer == null)
        {
            writer = forceGetCustomWriter(c);
            writerCache.put(c, writer);        // TODO: redundant, this is done again later.
        }

        if (writer != nullWriter) {
            return writer;
        }

        JsonClassWriter enumWriter;
        if (getWriteOptions().isWriteEnumAsJsonObject()) {
            enumWriter = new Writers.EnumAsObjectWriter();
        } else {
            enumWriter = new Writers.EnumsAsStringWriter();
        }

        writer = MetaUtils.getClassIfEnum(c).isPresent() ? enumWriter : nullWriter;
        writerCache.put(c, writer);

        return writer == nullWriter ? null : writer;
    }

    /**
     * Fetch the custom writer for the passed in Class.  This method always fetches the custom writer, doing
     * the complicated inheritance distance checking.  This method is only called when a cache miss has happened.
     * A sentinel 'nullWriter' is returned when no custom writer is found.  This prevents future cache misses
     * from re-attempting to find custom writers for classes that do not have a custom writer.
     * @param c Class of object for which fetch a custom writer
     * @return JsonClassWriter for the custom class (if one exists), nullWriter otherwise.
     */
    private JsonClassWriter forceGetCustomWriter(Class<?> c)
    {
        JsonClassWriter closestWriter = nullWriter;
        int minDistance = Integer.MAX_VALUE;

        for (Map.Entry<Class<?>, JsonClassWriter> entry : getWriteOptions().getCustomWrittenClasses().entrySet())
        {
            Class<?> clz = entry.getKey();
            if (clz == c)
            {
                return entry.getValue();
            }
            int distance = MetaUtils.computeInheritanceDistance(c, clz);
            if (distance != -1 && distance < minDistance)
            {
                minDistance = distance;
                closestWriter = entry.getValue();
            }
        }
        return closestWriter;
    }

    /**
     * For no custom writing to occur for the passed in Class.
     * @param c Class which should NOT have any custom writer associated to it.  Use this
     * to prevent a custom writer from being used due to inheritance.
     */
    @Deprecated
    public void addNotCustomWriter(Class<?> c)
    {
        getWriteOptions().addNotCustomWrittenClass(c);
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
        this.objsReferenced.clear();
    }

    /**
     * Walk object graph and visit each instance, following each field, each Collection, Map and so on.
     * Tracks visited to handle cycles and to determine if an item is referenced elsewhere.  If an
     * object is never referenced more than once, no @id field needs to be emitted for it.
     * @param root Object to be deeply traced.  The objVisited and objsReferenced Maps will be written to
     * during the trace.
     */
    protected void traceReferences(Object root) {
        if (root == null) {
            return;
        }
        
        WriteOptions writeOptions = getWriteOptions();
        final Deque<Object> stack = new ArrayDeque<>();
        stack.addFirst(root);
        final Map<Object, Long> visited = this.objVisited;
        final Map<Object, Long> referenced = this.objsReferenced;

        while (!stack.isEmpty())
        {
            final Object obj = stack.removeFirst();

            if (!writeOptions.isLogicalPrimitive(obj.getClass()))
            {
                Long id = visited.get(obj);
                if (id != null)
                {   // Only write an object once.
                    if (id.equals(ZERO))
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

            final Class<?> clazz = obj.getClass();

            if (clazz.isArray())
            {
                if (!writeOptions.isLogicalPrimitive(clazz.getComponentType()))
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
                try
                {
                    Map map = (Map) obj;
                    for (final Object item : map.entrySet())
                    {
                        final Entry entry = (Entry) item;
                        Object key = entry.getKey();
                        Object value = entry.getValue();
                        if (value != null && !writeOptions.isLogicalPrimitive(value.getClass()))
                        {
                            stack.addFirst(value);
                        }
                        if (key != null && !writeOptions.isLogicalPrimitive(key.getClass()))
                        {
                            stack.addFirst(key);
                        }
                    }
                }
                catch (UnsupportedOperationException e)
                {
                    // Some kind of Map that does not support .entrySet() - some Maps throw UnsupportedOperation for
                    // this API.  Do not attempt any further tracing of references.  Likely a ClassLoader field or
                    // something unusual like that.
                }
            }
            else if (Collection.class.isAssignableFrom(clazz))
            {
                for (final Object item : (Collection)obj)
                {
                    if (item != null && !writeOptions.isLogicalPrimitive(item.getClass()))
                    {
                        stack.addFirst(item);
                    }
                }
            }
            else
            {   // Speed up: do not traceReferences of primitives, they cannot reference anything
                if (!writeOptions.isLogicalPrimitive(obj.getClass()))
                {
                    final Map<Class<?>, Set<Accessor>> fieldSpecifiers = writeOptions.getIncludedAccessorsPerAllClasses();
                    traceFields(stack, obj, fieldSpecifiers);
                }
            }
        }
    }

    /**
     * Reach-ability trace to visit all objects within the graph to be written.
     * This API will handle any object, using either reflection APIs or by
     * consulting a specified includedFields map if provided.
     * @param stack Deque used to manage descent into graph (rather than using Java stack.) This allows for
     * much larger graph processing.
     * @param obj Object root of graph
     * @param specifiers Map of optional field specifiers, which are used to override the field list returned by
     * the JDK reflection operations.  This allows a subset of the actual fields on an object to be serialized.
     */
    protected void traceFields(final Deque<Object> stack, final Object obj, final Map<Class<?>, ? extends Collection<Accessor>> specifiers)
    {
        // If caller has special Field specifier for a given class
        // then use it, otherwise use reflection.
        Collection<Accessor> fields = getAccessorsUsingSpecifiers(obj.getClass(), specifiers);
        Collection<Accessor> fieldsBySpec = fields;

        if (fields == null)
        {   // Trace fields using reflection, could filter this with excluded list here
            fields = ClassDescriptors.instance().getDeepAccessorsForClass(obj.getClass());
        }

        final Collection<Accessor> excludedFields = getAccessorsUsingSpecifiers(obj.getClass(), getWriteOptions().getExcludedAccessorsPerAllClasses());

        for (final Accessor accessor : fields)
        {
            if (accessor.isTransient())
            {
                if (fieldsBySpec == null || !fieldsBySpec.contains(accessor))
                {   // Skip tracing transient fields EXCEPT when the field is listed explicitly by using the fieldSpecifiers Map.
                    // In that case, the field must be traced, even though it is transient.
                    continue;
                }
            }
            try
            {
                // make sure excluded fieldss don't get added to the stack.  If a field is proxied, such as
                // by Hibernate then accessing the item in any way can throw an exception.
                if (excludedFields == null || !excludedFields.contains(accessor))
                {
                    final Object o = accessor.retrieve(obj);
                    if (o != null && !getWriteOptions().isLogicalPrimitive(o.getClass()))
                    {   // Trace through objects that can reference other objects
                        stack.addFirst(o);
                    }
                }
            }
            catch (Exception ignored) { }
        }
    }

    // This is replacing the reverse walking system that compared all cases for distance
    // since we're caching all classes and their sub-objects correctly we should be ok removing
    // the distance check since we walk up the chain of the class being written.
    private static Collection<Accessor> getAccessorsUsingSpecifiers(final Class<?> classBeingWritten, final Map<Class<?>, ? extends Collection<Accessor>> specifiers)
    {
        Class<?> curr = classBeingWritten;
        List<Collection<Accessor>> accessorLists = new ArrayList<>();
        
        while (curr != null) {
            Collection<Accessor> accessorList = specifiers.get(curr);

            if (accessorList != null) {
                accessorLists.add(accessorList);
            }

            curr = curr.getSuperclass();
        }

        if (accessorLists.isEmpty()) {
            return null;
        }

        Collection<Accessor> accessors = new ArrayList<>();
        accessorLists.forEach(accessors::addAll);
        return accessors;
    }

    private boolean writeOptionalReference(Object obj) throws IOException
    {
        if (obj == null || getWriteOptions().isLogicalPrimitive(obj.getClass()))
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
            output.write(getWriteOptions().isShortMetaKeys() ? "{\"@r\":" : "{\"@ref\":");
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
     * @param allowCustom if set to true, the object being called will be checked for a matching
     * custom writer to be used. This does not affect sub-objects, just the top-level 'obj'
     * being passed in.
     * @throws IOException if one occurs on the underlying output stream.
     */
    public void writeImpl(Object obj, boolean showType, boolean allowRef, boolean allowCustom) throws IOException
    {
        if (getWriteOptions().isNeverShowingType())
        {
            showType = false;
        }

        // For security - write instances of these classes out as null
        if (obj instanceof ProcessBuilder ||
            obj instanceof Process ||
            obj instanceof ClassLoader ||
            obj instanceof Constructor ||
            obj instanceof Method ||
            obj instanceof Field)
        {
            out.write("null");
            return;
        }

        if (obj == null)
        {
            out.write("null");
            return;
        }

        if (allowCustom && writeUsingCustomWriter(obj, showType, out))
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
        else if (obj instanceof EnumSet)
        {
            writeEnumSet((EnumSet)obj);
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
        {   // Map instanceof check must be after JsonObject check above (JsonObject is a subclass of Map)
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
        out.write(getWriteOptions().isShortMetaKeys() ? "\"@i\":" : "\"@id\":");
        out.write(id == null ? "0" : id);
    }

    private void writeType(Object obj, Writer output) throws IOException
    {
        if (getWriteOptions().isNeverShowingType())
        {
            return;
        }
        output.write(getWriteOptions().isShortMetaKeys() ? "\"@t\":\"" : "\"@type\":\"");
        final Class<?> c = obj.getClass();
        String typeName = c.getName();
        String shortName = getSubstituteTypeNameIfExists(typeName);

        if (shortName != null)
        {
            output.write(shortName);
            output.write('"');
            return;
        }

        String s = c.getName();
        switch (s)
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

    private void writePrimitive(final Object obj, boolean showType) throws IOException
    {
        WriteOptions writeOptions = getWriteOptions();
        if (writeOptions.isNeverShowingType())
        {
            showType = false;
        }
        if (obj instanceof Character)
        {
            writeJsonUtf8String(String.valueOf(obj), out);
        }
        else
        {
            if (obj instanceof Long && writeOptions.isWriteLongsAsStrings())
            {
                if (showType)
                {
                    out.write(writeOptions.isShortMetaKeys() ? "{\"@t\":\"" : "{\"@type\":\"");
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
            else if (!isAllowNanAndInfinity() && obj instanceof Double && (Double.isNaN((Double) obj) || Double.isInfinite((Double) obj)))
            {
                out.write("null");
            }
            else if (!isAllowNanAndInfinity() && obj instanceof Float && (Float.isNaN((Float) obj) || Float.isInfinite((Float) obj)))
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
        WriteOptions writeOptions = getWriteOptions();
        if (writeOptions.isNeverShowingType())
        {
            showType = false;
        }
        Class<?> arrayType = array.getClass();
        int len = Array.getLength(array);
        boolean referenced = objsReferenced.containsKey(array);
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
                output.write(writeOptions.isShortMetaKeys() ? "\"@e\":[]" : "\"@items\":[]");
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
            output.write(writeOptions.isShortMetaKeys() ? "\"@e\":[" : "\"@items\":[");
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
            final Class<?> componentClass = array.getClass().getComponentType();
            final boolean isPrimitiveArray = Primitives.isPrimitive(componentClass);

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
                } else if (getWriteOptions().isNeverShowingType() && Primitives.isPrimitive(value.getClass()))
                {   // When neverShowType specified, do not allow primitives to show up as {"value":6} for example.
                    writePrimitive(value, false);
                }
                else
                {   // Specific Class-type arrays - only force type when
                    // the instance is derived from array base class.
                    boolean forceType = isForceType(value.getClass(), componentClass);
                    writeImpl(value, forceType || getWriteOptions().isAlwaysShowingType());
                }

                if (i != lenMinus1)
                {   // Make sure no bogus comma at the end of the array
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
            output.write(doubleToString(doubles[i]));
            output.write(',');
        }
        output.write(doubleToString(doubles[lenMinus1]));
    }

    private void writeFloatArray(float[] floats, int lenMinus1) throws IOException
    {
        final Writer output = this.out;
        for (int i = 0; i < lenMinus1; i++)
        {
            output.write(floatToString(floats[i]));
            output.write(',');
        }
        output.write(floatToString(floats[lenMinus1]));
    }

    private String doubleToString(double d)
    {
        if (isAllowNanAndInfinity()) {
            return Double.toString(d);
        }
        return (Double.isNaN(d) || Double.isInfinite(d)) ? "null" : Double.toString(d);
    }

    private String floatToString(float d)
    {
        if (isAllowNanAndInfinity()) {
            return Float.toString(d);
        }
        return (Float.isNaN(d) || Float.isInfinite(d)) ? "null" : Float.toString(d);
    }

    private void writeLongArray(long[] longs, int lenMinus1) throws IOException
    {
        final Writer output = this.out;
        if (getWriteOptions().isWriteLongsAsStrings())
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
        if (getWriteOptions().isNeverShowingType())
        {
            showType = false;
        }
        final Writer output = this.out;
        boolean referenced = this.objsReferenced.containsKey(col);
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

        writeElements(output, i);

        tabOut();
        output.write(']');
        if (showType || referenced)
        {   // Finished object, as it was output as an object if @id or @type was output
            tabOut();
            output.write("}");
        }
    }

    private void writeElements(Writer output, Iterator i) throws IOException
    {
        while (i.hasNext())
        {
            writeCollectionElement(i.next());

            if (i.hasNext())
            {
                output.write(',');
                newLine();
            }
        }
    }

    private void writeIdAndTypeIfNeeded(Object col, boolean showType, boolean referenced) throws IOException
    {
        if (getWriteOptions().isNeverShowingType())
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
            out.write(getWriteOptions().isShortMetaKeys() ? "\"@e\":[" : "\"@items\":[");
        }
        else
        {
            out.write('[');
        }
        tabIn();
    }

    private void writeJsonObjectArray(JsonObject jObj, boolean showType) throws IOException
    {
        WriteOptions writeOptions = getWriteOptions();
        if (writeOptions.isNeverShowingType())
        {
            showType = false;
        }
        int len = jObj.getLength();
        String type = jObj.type;
        Class<?> arrayClass;

        if (type == null || Object[].class.getName().equals(type))
        {
            arrayClass = Object[].class;
        }
        else
        {
            arrayClass = MetaUtils.classForName(type, getClassLoader());
        }

        final Writer output = this.out;
        final boolean isObjectArray = Object[].class == arrayClass;
        final Class<?> componentClass = arrayClass.getComponentType();
        boolean referenced = adjustIfReferenced(jObj);
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
            output.write(writeOptions.isShortMetaKeys() ? "\"@t\":\"" : "\"@type\":\"");
            output.write(getSubstituteTypeName(arrayClass.getName()));
            output.write("\",");
            newLine();
        }

        if (len == 0)
        {
            if (typeWritten || referenced)
            {
                output.write(writeOptions.isShortMetaKeys() ? "\"@e\":[]" : "\"@items\":[]");
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
            output.write(writeOptions.isShortMetaKeys() ? "\"@e\":[" : "\"@items\":[");
        }
        else
        {
            output.write('[');
        }
        tabIn();

        Object[] items = (Object[]) jObj.get(ITEMS);
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
            } else if (getWriteOptions().isNeverShowingType() && Primitives.isPrimitive(value.getClass()))
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
                boolean forceType = isForceType(value.getClass(), componentClass);
                writeImpl(value, forceType || getWriteOptions().isAlwaysShowingType());
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
        WriteOptions writeOptions = getWriteOptions();
        if (writeOptions.isNeverShowingType())
        {
            showType = false;
        }
        String type = jObj.type;
        Class<?> colClass = MetaUtils.classForName(type, getClassLoader());
        boolean referenced = adjustIfReferenced(jObj);
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
            output.write(writeOptions.isShortMetaKeys() ? "\"@t\":\"" : "\"@type\":\"");
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

        Object[] items = (Object[]) jObj.get(ITEMS);
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
        if (getWriteOptions().isNeverShowingType())
        {
            showType = false;
        }
        final Writer output = this.out;
        showType = emitIdAndTypeIfNeeded(jObj, showType, output);

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

        writeMapToEnd(jObj, output);
    }

    private boolean writeJsonObjectMapWithStringKeys(JsonObject jObj, boolean showType) throws IOException
    {
        WriteOptions writeOptions = getWriteOptions();
        if (writeOptions.isNeverShowingType())
        {
            showType = false;
        }

        if (writeOptions.isForceMapOutputAsTwoArrays() || !ensureJsonPrimitiveKeys(jObj))
        {
            return false;
        }

        Writer output = this.out;
        emitIdAndTypeIfNeeded(jObj, showType, output);

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

    private boolean emitIdAndTypeIfNeeded(JsonObject jObj, boolean showType, Writer output) throws IOException
    {
        boolean referenced = adjustIfReferenced(jObj);
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
                Class<?> mapClass = MetaUtils.classForName(type, getClassLoader());
                output.write(getWriteOptions().isShortMetaKeys() ? "\"@t\":\"" : "\"@type\":\"");
                output.write(getSubstituteTypeName(mapClass.getName()));
                output.write('"');
            }
            else
            {   // type not displayed
                showType = false;
            }
        }
        return showType;
    }

    /**
     * Write fields of an Object (JsonObject)
     */
    private void writeJsonObjectObject(JsonObject jObj, boolean showType) throws IOException
    {
        WriteOptions writeOptions = getWriteOptions();
        if (writeOptions.isNeverShowingType())
        {
            showType = false;
        }
        final Writer output = this.out;

        boolean referenced = adjustIfReferenced(jObj);

        showType = showType && jObj.type != null;
        Class<?> type = null;

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
            output.write(writeOptions.isShortMetaKeys() ? "\"@t\":\"" : "\"@type\":\"");
            output.write(getSubstituteTypeName(jObj.type));
            output.write('"');
            try  { type = MetaUtils.classForName(jObj.type, getClassLoader()); } catch(Exception ignored) { }
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

        Iterator<Map.Entry<Object, Object>> i = jObj.entrySet().iterator();
        boolean first = true;

        while (i.hasNext())
        {
            Map.Entry<Object, Object> entry = i.next();
            if (writeOptions.isSkipNullFields() && entry.getValue() == null)
            {
                continue;
            }

            if (!first)
            {
                output.write(',');
                newLine();
            }
            first = false;
            final String fieldName = (String) entry.getKey();
            output.write('"');
            output.write(fieldName);
            output.write("\":");
            Object value = entry.getValue();

            if (value == null)
            {
                output.write("null");
            } else if (writeOptions.isNeverShowingType() && Primitives.isPrimitive(value.getClass()))
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

    private boolean adjustIfReferenced(JsonObject jObj)
    {
        Long idx = objsReferenced.get(jObj);
        if (!jObj.hasId() && idx != null && idx > 0)
        {   // Referenced object that needs an ID copied to it.
            jObj.id = idx;
        }
        return objsReferenced.containsKey(jObj) && jObj.hasId();
    }

    private static boolean doesValueTypeMatchFieldType(Class<?> type, String fieldName, Object value)
    {
        if (type != null)
        {
            Map<String, Field> classFields = MetaUtils.getDeepDeclaredFields(type);
            Field field = classFields.get(fieldName);
            return field != null && field.getType().equals(value.getClass());
        }
        return false;
    }

    private void writeMap(Map map, boolean showType) throws IOException
    {
        if (getWriteOptions().isNeverShowingType())
        {
            showType = false;
        }
        final Writer output = this.out;
        boolean referenced = this.objsReferenced.containsKey(map);

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

        writeMapToEnd(map, output);
    }

    private void writeMapToEnd(Map map, Writer output) throws IOException
    {
        WriteOptions writeOptions = getWriteOptions();
        output.write(writeOptions.isShortMetaKeys() ? "\"@k\":[" : "\"@keys\":[");
        tabIn();
        Iterator<?> i = map.keySet().iterator();

        writeElements(output, i);

        tabOut();
        output.write("],");
        newLine();
        output.write(writeOptions.isShortMetaKeys() ? "\"@e\":[" : "\"@items\":[");
        tabIn();
        i = map.values().iterator();

        writeElements(output, i);

        tabOut();
        output.write(']');
        tabOut();
        output.write('}');
    }
    
    private boolean writeMapWithStringKeys(Map map, boolean showType) throws IOException
    {
        WriteOptions writeOptions = getWriteOptions();
        if (writeOptions.isNeverShowingType())
        {
            showType = false;
        }
        if (writeOptions.isForceMapOutputAsTwoArrays() || !ensureJsonPrimitiveKeys(map))
        {
            return false;
        }

        boolean referenced = this.objsReferenced.containsKey(map);

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

    /**
     * Ensure that all keys within the Map are String instances
     * @param map Map to inspect that all keys are primitive.  This allows the output JSON
     *            to be optimized into {"key1":value1, "key2": value2} format if all the
     *            keys of the Map are Strings.  If not, then a Map is written as two
     *            arrays, a @keys array and an @items array.  This allows support for Maps
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
    private void writeCollectionElement(Object o) throws IOException
    {
        WriteOptions writeOptions = getWriteOptions();
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
            writePrimitive(o, writeOptions.isWriteLongsAsStrings());
        }
        else if (o instanceof String)
        {   // Never do a @ref to a String (they are treated as logical primitives and interned on read)
            writeJsonUtf8String((String) o, out);
        } else if (writeOptions.isNeverShowingType() && Primitives.isPrimitive(o.getClass()))
        {   // If neverShowType, then force primitives (and primitive wrappers)
            // to be output with toString() - prevents {"value":6} for example
            writePrimitive(o, false);
        }
        else
        {
            writeImpl(o, true);
        }
    }

    private void writeEnumSet(final EnumSet<?> enumSet) throws IOException
    {
        out.write('{');
        tabIn();

        boolean referenced = this.objsReferenced.containsKey(enumSet);
        if (referenced)
        {
            writeId(getId(enumSet));
            out.write(',');
            newLine();
        }

        writeJsonUtf8String("@enum", out);
        out.write(':');

        Enum<? extends Enum<?>> ee = null;
        if (!enumSet.isEmpty())
        {
            ee = enumSet.iterator().next();
        }
        else
        {
            EnumSet<? extends Enum<?>> complement = EnumSet.complementOf(enumSet);
            if (!complement.isEmpty())
            {
                ee = complement.iterator().next();
            }
        }

        Field elementTypeField = MetaUtils.getField(EnumSet.class, "elementType");
        Class<?> elementType = (Class<?>) getValueByReflect(enumSet, elementTypeField);
        if ( elementType != null)
        {
            // nice we got the right to sneak into
        }
        else if (ee == null)
        {
            elementType = MetaUtils.Dumpty.class;
        }
        else
        {
            elementType = ee.getClass();
        }
        writeJsonUtf8String(elementType.getName(), out);

        if (!enumSet.isEmpty())
        {
            Map<String, Accessor> mapOfFields = ClassDescriptors.instance().getDeepAccessorMap(elementType);
            int enumFieldsCount = mapOfFields.size();

            out.write(",");
            newLine();

            writeJsonUtf8String("@items", out);
            out.write(":[");
            if (enumFieldsCount > 2)
            {
                newLine();
            }

            boolean firstInSet = true;
            for (Enum e : enumSet)
            {
                if (!firstInSet)
                {
                    out.write(",");
                    if (enumFieldsCount > 2)
                    {
                        newLine();
                    }
                }
                firstInSet = false;

                if (enumFieldsCount <= 2)
                {
                    writeJsonUtf8String(e.name(), out);
                }
                else
                {
                    boolean firstInEntry = true;
                    out.write('{');
                    for (Accessor f : mapOfFields.values())
                    {
                        firstInEntry = writeField(e, firstInEntry, f.getName(), f, false);
                    }
                    out.write('}');
                }
            }

            out.write("]");
        }
        
        tabOut();
        out.write('}');
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
        this.writeObject(obj, showType, bodyOnly, EMPTY_SET);
    }

    /**
     * @param obj      Object to be written in JSON format
     * @param showType boolean true means show the "@type" field, false
     *                 eliminates it.  Many times the type can be dropped because it can be
     *                 inferred from the field or array type.
     * @param bodyOnly write only the body of the object
     * @param fieldsToExclude field that should be excluded when writing out object (per class bases for custom writers)
     * @throws IOException if an error occurs writing to the output stream.
     */
    public void writeObject(final Object obj, boolean showType, boolean bodyOnly, Set<String> fieldsToExclude) throws IOException
    {
        if (getWriteOptions().isNeverShowingType())
        {
            showType = false;
        }
        final boolean referenced = this.objsReferenced.containsKey(obj);
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

        final Map<Class<?>, Set<Accessor>> includedFields = getWriteOptions().getIncludedAccessorsPerAllClasses();
        final Collection<Accessor> excludedFields = getAccessorsUsingSpecifiers(obj.getClass(), getWriteOptions().getExcludedAccessorsPerAllClasses());
        final Collection<Accessor> externallySpecifiedFields = getAccessorsUsingSpecifiers(obj.getClass(), includedFields);
        if (externallySpecifiedFields != null)
        {
            for (Accessor accessor : externallySpecifiedFields)
            {   //output field if not excluded
                if ((excludedFields == null || !excludedFields.contains(accessor)) && !fieldsToExclude.contains(accessor.getName())) {
                    // Not currently supporting overwritten field names in hierarchy when using external field specifier
                    first = writeField(obj, first, accessor.getName(), accessor, true);
                }//else field is blacklisted.
            }
        }
        else
        {   // Reflectively use fields, skipping transient and static fields
            final Map<String, Accessor> classFields = ClassDescriptors.instance().getDeepAccessorMap(obj.getClass());
            for (Map.Entry<String, Accessor> entry : classFields.entrySet())
            {
                final String fieldName = entry.getKey();
                final Accessor field = entry.getValue();
                //output field if not excluded
                if ((excludedFields == null || !excludedFields.contains(field)) && !fieldsToExclude.contains(field.getName()))
                {
                    first = writeField(obj, first, fieldName, field, false);
                }//else field is excluded.
            }
        }

        if (!bodyOnly)
        {
            tabOut();
            out.write('}');
        }
    }

    private Object getValueByReflect(Object obj, Field field) {
        try {
            return field.get(obj);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object getValueByReflect(Object obj, Accessor accessor) {
        try {
            return accessor.retrieve(obj);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean writeField(Object obj, boolean first, String fieldName, Accessor accessor, boolean allowTransient) throws IOException
    {
        if (!allowTransient && accessor.isTransient())
        {   // Do not write transient fields
            return first;
        }

        WriteOptions writeOptions = getWriteOptions();
        final Class<?> fieldDeclaringClass = accessor.getDeclaringClass();
        Object o;

        if (Enum.class.isAssignableFrom(fieldDeclaringClass))
        {
            if (!accessor.isPublic() && writeOptions.isEnumPublicFieldsOnly()) {
                return first;
            }

            o = getValueByReflect(obj, accessor);
        }
        else if (ObjectResolver.isBasicWrapperType(fieldDeclaringClass))
        {
            o = obj;
        }
        else
        {
            o = getValueByReflect(obj, accessor);
        }

        if (writeOptions.isSkipNullFields() && o == null)
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

        Class<?> type = accessor.getType();
        boolean forceType = isForceType(o.getClass(), type);     // If types are not exactly the same, write "@type" field

        // When no type is written we can check the Object itself not the declaration
        if (Primitives.isPrimitive(type) || (writeOptions.isNeverShowingType() && Primitives.isPrimitive(o.getClass())))
        {
            writePrimitive(o, false);
        }
        else
        {
            writeImpl(o, forceType || writeOptions.isAlwaysShowingType(), true, true);
        }
        return false;
    }

    private boolean isForceType(Class<?> objectClass, Class<?> declaredType) {
        if (objectClass == declaredType) {
            return false;
        }

        if (declaredType.isEnum() && declaredType.isAssignableFrom(objectClass)) {
            Optional<Class> optionalClass = MetaUtils.getClassIfEnum(objectClass);
            return declaredType != optionalClass.orElse(null);
        }

        return this.getCustomWriter(declaredType) == null;
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
                        output.write(String.format("\\u%04X", (int)c));
                        break;
                }
            }
            else if (c == '\\' || c == '"')
            {
                output.write('\\');
                output.write(c);
            }
            else
            {   // Anything else - write in UTF-8 form (multibyte encoded) (OutputStreamWriter is UTF-8)
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
    }

    private String getId(Object o)
    {
        if (o instanceof JsonObject)
        {
            long id = ((JsonObject) o).id;
            if (id > 0)
            {
                return String.valueOf(id);
            }
        }
        Long id = this.objsReferenced.get(o);
        return id == null ? null : Long.toString(id);
    }
}
