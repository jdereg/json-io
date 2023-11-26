package com.cedarsoftware.util.io;

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
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.ClassDescriptors;
import lombok.Getter;
import lombok.Setter;

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
public class JsonWriter implements WriterContext, Closeable, Flushable
{
    protected static final Set<String> EMPTY_SET = new HashSet<>();
    private static final Object[] byteStrings = new Object[256];
    private static final String NEW_LINE = System.getProperty("line.separator");
    private static final Long ZERO = 0L;

    @Getter
    private final WriteOptions writeOptions;

    /**
     * Map containing all objects that were visited within input object graph
     */
    @Getter
    private final Map<Object, Long> objVisited = new IdentityHashMap<>();

    /**
     *  Map containing all objects that were referenced within input object graph.
     */
    @Getter
    private final Map<Object, Long> objsReferenced = new IdentityHashMap<>();

    private final Writer out;

    private long identity = 1;
    private int depth = 0;

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
     * @deprecated use WriteOptions.allowNanAndInfinity()
     */
    @Setter
    @Getter
    @Deprecated
    private static volatile boolean allowNanAndInfinity = false;

    /**
     * Implement this interface to customize the JSON output for a given class.
     */
    public interface JsonClassWriter
    {
        /**
         * When write() is called, it is expected that subclasses will write the appropriate JSON
         * to the passed in Writer.
         *
         * @param o        Object to be written in JSON format.
         * @param showType boolean indicating whether to show @type.
         * @param output   Writer destination to where the actual JSON is written.
         * @param context  WriterContext to get writeOptions and other write access
         * @throws IOException if thrown by the writer.  Will be caught at a higher level and wrapped in JsonIoException.
         */
        default void write(Object o, boolean showType, Writer output, WriterContext context) throws IOException {
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
        default boolean hasPrimitiveForm(WriterContext context) {
            return hasPrimitiveForm();
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
         * @param context  WriterContext to get access to writeOptions and writing tools
         * @throws IOException if thrown by the writer.  Will be caught at a higher level and wrapped in JsonIoException.
         */
        default void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
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
            JsonWriter writer = new JsonWriter(stream, writeOptions);
            writer.write(item);
            writer.close();
            return new String(stream.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new JsonIoException("Unable to convert object to JSON", e);
        }
    }

    /**
     * Convert a Java Object to a JSON String.
     * @param stream Outputstream that the generated JSON will be sent to.
     * @param item Object root object from which to generate JSON .
     * @param writeOptions (optional can be null for defaults) Map of extra arguments indicating how the JSON is
     *                     formatted.  From date formats, to special treatment for longs, null fields, etc.
     */
    public static void toJson(OutputStream stream, Object item, WriteOptions writeOptions) {
        try {
            JsonWriter writer = new JsonWriter(stream, writeOptions);
            writer.write(item);
            writer.close();
        } catch (Exception e) {
            throw new JsonIoException("Unable to convert object and send in JSON format to OutputStream.", e);
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
        return JsonIo.formatJson(json);
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
        this.out = new FastWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        this.writeOptions = writeOptions == null ? new WriteOptions().build() : writeOptions.build();
    }

    /**
     * @return ClassLoader to be used by Custom Writers
     */
    ClassLoader getClassLoader()
    {
        return writeOptions.getClassLoader();
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
        if (!writeOptions.isPrettyPrint())
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
        if (!o.getClass().isAssignableFrom(arrayComponentClass) || writeOptions.isNotCustomWrittenClass(o.getClass()))
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
        if (writeOptions.isNeverShowingType())
        {
            showType = false;
        }
        JsonClassWriter closestWriter = writeOptions.getCustomWriter(arrayComponentClass);

        if (closestWriter == null)
        {
            return false;
        }

        if (writeOptionalReference(o))
        {
            return true;
        }

        boolean referenced = objsReferenced.containsKey(o);

        if (closestWriter.hasPrimitiveForm(this)) {
            if ((!referenced && !showType) || closestWriter instanceof Writers.JsonStringWriter) {
                closestWriter.writePrimitiveForm(o, output, this);
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
            writeType(o.getClass().getName(), output);
        }

        if (referenced || showType)
        {
            output.write(',');
            newLine();
        }

        closestWriter.write(o, showType || referenced, output, this);

        tabOut();
        output.write('}');
        return true;
    }

    /**
     * For no custom writing to occur for the passed in Class.
     * @param c Class which should NOT have any custom writer associated to it.  Use this
     * to prevent a custom writer from being used due to inheritance.
     */
    @Deprecated
    public void addNotCustomWriter(Class<?> c)
    {
        writeOptions.addNotCustomWrittenClass(c);
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

        final Deque<Object> stack = new ArrayDeque<>();
        stack.addFirst(root);
        final Map<Object, Long> visited = this.objVisited;
        final Map<Object, Long> referenced = this.objsReferenced;

        while (!stack.isEmpty())
        {
            final Object obj = stack.removeFirst();

            if (!writeOptions.isNonReferenceableClass(obj.getClass()))
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
                if (!writeOptions.isNonReferenceableClass(clazz.getComponentType()))
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
                        if (value != null && !writeOptions.isNonReferenceableClass(value.getClass()))
                        {
                            stack.addFirst(value);
                        }
                        if (key != null && !writeOptions.isNonReferenceableClass(key.getClass()))
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
                    if (item != null && !writeOptions.isNonReferenceableClass(item.getClass()))
                    {
                        stack.addFirst(item);
                    }
                }
            }
            else
            {   // Speed up: do not traceReferences of non-referenceable classes
                if (!writeOptions.isNonReferenceableClass(obj.getClass()))
                {
                    traceFields(stack, obj);
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
     * the JDK reflection operations.  This allows a subset of the actual fields on an object to be serialized.
     */
    protected void traceFields(final Deque<Object> stack, final Object obj)
    {
        // If caller has special Field specifier for a given class
        // then use it, otherwise use reflection.
        Collection<Accessor> fields = writeOptions.getIncludedAccessorsForClass(obj.getClass());
        Collection<Accessor> fieldsBySpec = fields;

        if (fields.isEmpty())
        {   // Trace fields using reflection, could filter this with excluded list here
            fields = ClassDescriptors.instance().getDeepAccessors(obj.getClass());
        }

        final Collection<Accessor> excludedFields = writeOptions.getExcludedAccessorsForClass(obj.getClass());

        for (final Accessor accessor : fields)
        {
            if (accessor.isTransient())
            {
                if (!fieldsBySpec.contains(accessor))
                {   // Skip tracing transient fields EXCEPT when the field is listed explicitly by using the fieldSpecifiers Map.
                    // In that case, the field must be traced, even though it is transient.
                    continue;
                }
            }
            try
            {
                // make sure excluded fieldss don't get added to the stack.  If a field is proxied, such as
                // by Hibernate then accessing the item in any way can throw an exception.
                if (!excludedFields.contains(accessor))
                {
                    final Object o = getValueByReflect(obj, accessor);
                    if (o != null && !writeOptions.isNonReferenceableClass(o.getClass())) {   // Trace through objects that can reference other objects
                        stack.addFirst(o);
                    }
                }
            }
            catch (Exception ignored) { }
        }
    }

    private boolean writeOptionalReference(Object obj) throws IOException
    {
        if (obj == null || writeOptions.isNonReferenceableClass(obj.getClass()))
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
            output.write(writeOptions.isShortMetaKeys() ? "{\"@r\":" : "{\"@ref\":");
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
        if (writeOptions.isNeverShowingType())
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
            writeEnumSet((EnumSet<?>)obj);
        }
        else if (obj instanceof Collection)
        {
            writeCollection((Collection<?>) obj, showType);
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
        out.write(writeOptions.isShortMetaKeys() ? "\"@i\":" : "\"@id\":");
        out.write(id == null ? "0" : id);
    }

    private void writeType(String name, Writer output) throws IOException {
        if (writeOptions.isNeverShowingType()) {
            return;
        }
        output.write(writeOptions.isShortMetaKeys() ? "\"@t\":\"" : "\"@type\":\"");
        String alias = writeOptions.getTypeNameAlias(name);
        output.write(alias);
        output.write('"');
    }

    private void writePrimitive(final Object obj, boolean showType) throws IOException
    {
        if (writeOptions.isNeverShowingType())
        {
            showType = false;
        }


        if (obj instanceof Long && writeOptions.isWriteLongsAsStrings()) {
            if (showType) {
                out.write('{');
                writeType("long", out);
                out.write(',');
            }

            JsonClassWriter writer = getWriteOptions().getCustomWriter(Long.class);
            writer.write(obj, showType, out, this);

            if (showType) {
                out.write('}');
            }
        } else {
            out.write(obj.toString());
        }
    }

    private void writeArray(final Object array, boolean showType) throws IOException
    {
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
            writeType(arrayType.getName(), output);
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
        } else if (char[].class == arrayType) {
            JsonIo.writeJsonUtf8String(output, new String((char[]) array));
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
                } else if (writeOptions.isNeverShowingType() && Primitives.isPrimitive(value.getClass()))
                {   // When neverShowType specified, do not allow primitives to show up as {"value":6} for example.
                    writeImpl(value, false);
                }
                else
                {   // Specific Class-type arrays - only force type when
                    // the instance is derived from array base class.
                    boolean forceType = isForceType(value.getClass(), componentClass);
                    writeImpl(value, forceType || writeOptions.isAlwaysShowingType());
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
        final JsonClassWriter writer = getWriteOptions().getCustomWriter(Double.class);
        for (int i = 0; i < lenMinus1; i++)
        {
            writer.write(doubles[i], false, output, this);
            output.write(',');
        }
        writer.write(doubles[lenMinus1], false, output, this);
    }

    private void writeFloatArray(float[] floats, int lenMinus1) throws IOException
    {
        final Writer output = this.out;
        final JsonClassWriter writer = getWriteOptions().getCustomWriter(Float.class);
        for (int i = 0; i < lenMinus1; i++)
        {
            writer.write(floats[i], false, output, this);
            output.write(',');
        }

        writer.write(floats[lenMinus1], false, output, this);
    }

    private void writeLongArray(long[] longs, int lenMinus1) throws IOException
    {
        final Writer output = this.out;

        JsonClassWriter writer = getWriteOptions().getCustomWriter(long.class);
        for (int i = 0; i < lenMinus1; i++) {
            writer.write(longs[i], false, output, this);
            output.write(',');
        }
        writer.write(longs[lenMinus1], false, output, this);

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

    private void writeCollection(Collection<?> col, boolean showType) throws IOException
    {
        if (writeOptions.isNeverShowingType())
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
        Iterator<?> i = col.iterator();

        writeElements(output, i);

        tabOut();
        output.write(']');
        if (showType || referenced)
        {   // Finished object, as it was output as an object if @id or @type was output
            tabOut();
            output.write("}");
        }
    }

    private void writeElements(Writer output, Iterator<?> i) throws IOException
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
        if (writeOptions.isNeverShowingType())
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
            writeType(col.getClass().getName(), out);
        }
    }

    private void beginCollection(boolean showType, boolean referenced) throws IOException
    {
        if (showType || referenced)
        {
            out.write(',');
            newLine();
            out.write(writeOptions.isShortMetaKeys() ? "\"@e\":[" : "\"@items\":[");
        }
        else
        {
            out.write('[');
        }
        tabIn();
    }

    private void writeJsonObjectArray(JsonObject jObj, boolean showType) throws IOException
    {
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
            arrayClass = MetaUtilsHelper.classForName(type, getClassLoader());
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
            writeType(arrayClass.getName(), output);
            output.write(',');
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

            if (value == null) {
                output.write("null");
            } else if (!writeArrayElementIfMatching(componentClass, value, false, output)) {
                // do nothing
                if (value instanceof Boolean || value instanceof Long || value instanceof Double) {
                    writePrimitive(value, value.getClass() != componentClass);
                } else {
                    boolean doNotShowType =
                            (writeOptions.isNeverShowingType() && Primitives.isPrimitive(value.getClass()) ||
                                    value instanceof String ||
                                    Character.class == componentClass ||
                                    char.class == componentClass) ||
                                    !(isForceType(value.getClass(), componentClass) || writeOptions.isAlwaysShowingType());

                    writeImpl(value, !doNotShowType);
                }
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
        if (writeOptions.isNeverShowingType())
        {
            showType = false;
        }
        String type = jObj.type;
        Class<?> colClass = MetaUtilsHelper.classForName(type, getClassLoader());
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

            writeType(colClass.getName(), output);
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
        if (writeOptions.isNeverShowingType())
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
                writeType(type, output);
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
        if (writeOptions.isNeverShowingType())
        {
            showType = false;
        }
        final Writer output = this.out;

        boolean referenced = adjustIfReferenced(jObj);

        showType = showType && jObj.type != null;

        output.write('{');
        tabIn();
        if (referenced)
        {
            writeId(String.valueOf(jObj.id));
        }

        Class<?> type = null;
        if (showType)
        {
            if (referenced)
            {
                output.write(',');
                newLine();
            }
            writeType(jObj.type, output);
            type = MetaUtilsHelper.classForName(jObj.type, getClassLoader());
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
            }
            else if (value instanceof BigDecimal || value instanceof BigInteger)
            {
                writeImpl(value, !doesValueTypeMatchFieldType(type, fieldName, value));
            } else if (value instanceof Number || value instanceof Boolean || value instanceof String || value instanceof Character) {
                writeImpl(value, false);
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
        if (writeOptions.isNeverShowingType())
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
            writeType(map.getClass().getName(), output);
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
            JsonIo.writeJsonUtf8String(output, (String) att2value.getKey());
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
        if (o == null)
        {
            out.write("null");
            return;
        }

        boolean doNotShowType = o instanceof Boolean ||
                o instanceof Double ||
                o instanceof String ||
                (o instanceof Long && !writeOptions.isWriteLongsAsStrings()) ||
                writeOptions.isNeverShowingType() && Primitives.isPrimitive(o.getClass());

        writeImpl(o, !doNotShowType);
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

        out.write("\"@enum\":");

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
        JsonIo.writeBasicString(out, elementType.getName());

        if (!enumSet.isEmpty())
        {
            Map<String, Accessor> mapOfFields = ClassDescriptors.instance().getDeepAccessorMap(elementType);
            int enumFieldsCount = mapOfFields.size();

            out.write(",");
            newLine();

            JsonIo.writeBasicString(out, "@items");
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

                if (enumFieldsCount <= 2) {
                    JsonIo.writeJsonUtf8String(out, e.name());
                }
                else
                {
                    boolean firstInEntry = true;
                    out.write('{');
                    for (Accessor f : mapOfFields.values())
                    {
                        firstInEntry = writeField(e, firstInEntry, f.getFieldName(), f, false);
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
        if (writeOptions.isNeverShowingType())
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
                writeType(obj.getClass().getName(), out);
            }
        }

        boolean first = !showType;
        if (referenced && !showType)
        {
            first = false;
        }

        Class<?> clazz = obj.getClass();
        final Collection<Accessor> excludedAccessors = writeOptions.getExcludedAccessorsForClass(clazz);
        final Collection<Accessor> includedAccessors = writeOptions.getIncludedAccessorsForClass(clazz);
        // TODO: Is 'allowTransient' being handle correctly below?
        if (!includedAccessors.isEmpty())
        {
            for (Accessor accessor : includedAccessors)
            {   //output field if not excluded
                String fieldName = accessor.getFieldName();
                if (!excludedAccessors.contains(accessor)) {
                    // Not currently supporting overwritten field names in hierarchy when using external field specifier
                    first = writeField(obj, first, fieldName, accessor, true);
                }//else field is blacklisted.
            }
        }
        else
        {   // Reflectively use fields, skipping transient and static fields
            final Map<String, Accessor> classFields = ClassDescriptors.instance().getDeepAccessorMap(clazz);
            for (Map.Entry<String, Accessor> entry : classFields.entrySet())
            {
                final String fieldName = entry.getKey();
                final Accessor field = entry.getValue();
                //output field if not excluded
                if (!excludedAccessors.contains(field))
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
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean writeField(Object obj, boolean first, String fieldName, Accessor accessor, boolean allowTransient) throws IOException
    {
        if (!allowTransient && accessor.isTransient())
        {   // Do not write transient fields
            return first;
        }

        final Class<?> fieldDeclaringClass = accessor.getDeclaringClass();
        Object o;

        //  Only here for enumAsObject writing,.
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

        out.write("\"");
        out.write(fieldName);   // Not using slower UTF String writer for field names
        out.write("\":");

        if (o == null)
        {    // don't quote null
            out.write("null");
            return false;
        }

        Class<?> type = accessor.getFieldType();
        boolean forceType = isForceType(o.getClass(), type);     // If types are not exactly the same, write "@type" field

        // When no type is written we can check the Object itself not the declaration
        if (Primitives.isPrimitive(type) || (writeOptions.isNeverShowingType() && Primitives.isPrimitive(o.getClass())))
        {
            writeImpl(o, false);
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

        return writeOptions.getCustomWriter(declaredType) == null;
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
            if (id > 0)
            {
                return String.valueOf(id);
            }
        }
        Long id = this.objsReferenced.get(o);
        return id == null ? null : Long.toString(id);
    }
}
