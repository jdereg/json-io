package com.cedarsoftware.io;

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
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.cedarsoftware.io.reflect.Accessor;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.CompactMap;
import com.cedarsoftware.util.CompactSet;
import com.cedarsoftware.util.FastWriter;

import static com.cedarsoftware.io.JsonValue.ENUM;
import static com.cedarsoftware.io.JsonValue.ITEMS;
import static com.cedarsoftware.io.JsonValue.TYPE;

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
    // Add these as class fields
    private static final String ID_SHORT = "\"@i\":";
    private static final String ID_LONG = "\"@id\":";
    private static final String TYPE_SHORT = "\"@t\":\"";
    private static final String TYPE_LONG = "\"@type\":\"";
    private final String idPrefix;
    private final String typePrefix;
    private static final Object[] byteStrings = new Object[256];
    private static final String NEW_LINE = System.lineSeparator();
    private static final Long ZERO = 0L;
    private final WriteOptions writeOptions;
    private final Map<Object, Long> objVisited = new IdentityHashMap<>();
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
        }
        
        /**
         * @param context  WriterContext to get writeOptions and other write access
         * @return boolean true if the class being written has a primitive (non-object) form.  Default is false since
         * most custom writers will not have a primitive form.
         */
        default boolean hasPrimitiveForm(WriterContext context) {
            return false;
        }

        /**
         * This default implementation will call the more basic writePrimitiveForm that does not take arguments.  No need
         * to override this method unless you need access to the args.
         * @param o Object to be written
         * @param output Writer destination to where the actual JSON is written.
         * @param context  WriterContext to get access to writeOptions and writing tools
         * @throws IOException if thrown by the writer.  Will be caught at a higher level and wrapped in JsonIoException.
         */
        default void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
        }

        default String getTypeName(Object o) {
            return o.getClass().getName();
        }
    }

    /**
     * @param out OutputStream to which the JSON will be written.  Uses the default WriteOptions.
     * @see WriteOptions
     */
    public JsonWriter(OutputStream out)
    {
        this(out, null);
    }

    /**
     * @param out OutputStream to which the JSON output will be written.
     * @param writeOptions WriteOptions containing many feature options to control the JSON output.  Can be null,
     *                     in which case the default WriteOptions will be used.
     * @see WriteOptions Javadoc.
     */
    public JsonWriter(OutputStream out, WriteOptions writeOptions) {
        this.out = new FastWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        this.writeOptions = writeOptions == null ? WriteOptionsBuilder.getDefaultWriteOptions() : writeOptions;
        
        // Pre-compute based on options
        this.idPrefix = this.writeOptions.isShortMetaKeys() ? ID_SHORT : ID_LONG;
        this.typePrefix = this.writeOptions.isShortMetaKeys() ? TYPE_SHORT : TYPE_LONG;
    }

    public WriteOptions getWriteOptions() {
        return writeOptions;
    }

    /**
     * Map containing all objects that were visited within input object graph
     */
    protected Map<Object, Long> getObjVisited() {
        return objVisited;
    }

    /**
     *  Map containing all objects that were referenced within input object graph.
     */
    public Map<Object, Long> getObjsReferenced() {
        return objsReferenced;
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
    public boolean writeUsingCustomWriter(Object o, boolean showType, Writer output) {
        Class<?> c = o.getClass();

        try {
            return writeCustom(c, o, !writeOptions.isNeverShowingType() && showType, output);
        } catch (Exception e) {
            throw new JsonIoException("Unable to write custom formatted object:", e);
        }
    }

    private boolean isCustomWrittenClass(Class<?> c, Object o) {
        // Exit early if the class is explicitly marked as not-custom-written.
        if (writeOptions.isNotCustomWrittenClass(c)) {
            return false;
        }

        // Exit early if there is no custom writer.
        if (writeOptions.getCustomWriter(c) == null) {
            return false;
        }

        // Exit early if 'o' is a CompactSet, and it is the default CompactSet.
        if ((o instanceof CompactSet) && ((CompactSet<?>) o).isDefaultCompactSet()) {
            return false;
        }

        // Exit early if 'o' is a CompactMap, and it is the default CompactMap.
        if ((o instanceof CompactMap) && ((CompactMap<?, ?>) o).isDefaultCompactMap()) {
            return false;
        }
        return true;
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
        if (!arrayComponentClass.isAssignableFrom(o.getClass())) {
            return false;
        }

        try {
            return writeCustom(arrayComponentClass, o, showType, output);
        } catch (IOException e) {
            throw new JsonIoException("Unable to write custom formatted object as array element:", e);
        }
    }

    /**
     * Perform the actual custom writing for an array element that has a custom writer.
     * @param clazz Class type of the array
     * @param o Object instance to write
     * @param showType boolean indicating whether @type should be output.
     * @param output Writer to write the JSON to (if there is a custom writer for o's Class).
     * @return true if the array element was written, false otherwise.
     */
    protected boolean writeCustom(Class<?> clazz, Object o, boolean showType, Writer output) throws IOException {
        if (!isCustomWrittenClass(clazz, o)) {
            return false;
        }
        if (writeOptions.isNeverShowingType()) {
            showType = false;
        }

        JsonClassWriter closestWriter = writeOptions.getCustomWriter(o.getClass());

        if (writeOptionalReference(o)) {
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
        if (referenced) {
            writeId(getId(o));
            if (showType) {
                output.write(',');
                newLine();
            }
        }

        if (showType) {
            String typeName = closestWriter.getTypeName(o);
            writeType(typeName, output);
        }

        if (referenced || showType) {
            output.write(',');
            newLine();
        }

        closestWriter.write(o, showType || referenced, output, this);

        tabOut();
        output.write('}');
        return true;
    }

    /**
     * Write the passed in Java object in JSON format.
     * @param obj Object any Java Object or JsonObject.
     */
    public void write(Object obj)
    {
        traceReferences(obj);
        objVisited.clear();
        try {
            writeImpl(obj, true);
        } catch (JsonIoException e) {
            throw e;
        } catch (Exception e) {
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
    protected void traceReferences(Object root) {
        if (root == null) {
            return;
        }

        final Deque<Object> stack = new ArrayDeque<>();
        stack.addFirst(root);
        final Map<Object, Long> visited = objVisited;
        final Map<Object, Long> referenced = objsReferenced;

        while (!stack.isEmpty()) {
            final Object obj = stack.removeFirst();

            if (!writeOptions.isNonReferenceableClass(obj.getClass())) {
                Long id = visited.get(obj);
                if (id != null) {   // Object has been seen before
                    if (id.equals(ZERO)) {
                        id = identity++;
                        visited.put(obj, id);
                        referenced.put(obj, id);
                    }
                    continue;
                } else {
                    visited.put(obj, ZERO);
                }
            }

            final Class<?> clazz = obj.getClass();

            if (clazz.isArray()) {
                processArray(obj, stack);
            } else if (obj instanceof JsonObject) {
                processJsonObject((JsonObject) obj, stack);
            } else if (Map.class.isAssignableFrom(clazz)) {
                processMap((Map<?, ?>) obj, stack);
            } else if (Collection.class.isAssignableFrom(clazz)) {
                processCollection((Collection<?>) obj, stack);
            } else {
                if (!writeOptions.isNonReferenceableClass(clazz)) {
                    processFields(stack, obj);
                }
            }
        }
    }

    private void processArray(Object array, Deque<Object> stack) {
        final Class<?> componentType = array.getClass().getComponentType();
        if (!writeOptions.isNonReferenceableClass(componentType)) {
            // If Object[], access it using [] for speed, versus Array.get() (reflection)
            if (componentType == Object.class) {
                for (final Object element : (Object[]) array) {
                    if (element != null) {
                        stack.addFirst(element);
                    }
                }
            }
            else {
                final int len = Array.getLength(array);
                for (int i = 0; i < len; i++) {
                    final Object element = Array.get(array, i);
                    if (element != null) {
                        stack.addFirst(element);
                    }
                }
            }
        }
    }

    private void processJsonObject(JsonObject jsonObj, Deque<Object> stack) {
        // Traverse items (array elements)
        Object[] items = jsonObj.getItems();
        if (items != null) {
            processArray(items, stack);
        }

        // Traverse keys (for JsonObject representing maps)
        Object[] keys = jsonObj.getKeys();
        if (keys != null) {
            processArray(keys, stack);
        }

        // Traverse other entries in jsonStore (allows for Collections to have properties)
        processMap(jsonObj, stack);
    }

    private void processMap(Map<?, ?> map, Deque<Object> stack) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key != null) {
                stack.addFirst(key);
            }
            if (value != null) {
                stack.addFirst(value);
            }
        }
    }

    private void processCollection(Collection<?> collection, Deque<Object> stack) {
        for (Object item : collection) {
            if (item != null) {
                stack.addFirst(item);
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
    protected void processFields(final Deque<Object> stack, final Object obj)
    {
        // If caller has special Field specifier for a given class
        // then use it, otherwise use reflection.
        Collection<Accessor> fields = writeOptions.getAccessorsForClass(obj.getClass());

        for (final Accessor accessor : fields) {
            final Object o = accessor.retrieve(obj);
            if (o != null) {   // Trace through objects that can reference other objects
                stack.addFirst(o);
            }
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
     * @param showType if set to true, the @type tag will be output. 
     * @throws IOException if one occurs on the underlying output stream.
     */
    public void writeImpl(Object obj, boolean showType) throws IOException {
        // For security - write instances of these classes out as null
        if (obj == null ||
                obj instanceof ProcessBuilder ||
                obj instanceof Process ||
                obj instanceof ClassLoader ||
                obj instanceof Constructor ||
                obj instanceof Method ||
                obj instanceof Field) {
            out.write("null");
            return;
        }

        if (writeOptions.isNeverShowingType()) {
            showType = false;
        }

        if (writeUsingCustomWriter(obj, showType, out) || writeOptionalReference(obj)) {
            return;
        }

        if (obj.getClass().isArray()) {
            writeArray(obj, showType);
        } else if (obj instanceof EnumSet) {
            writeEnumSet((EnumSet<?>) obj);
        } else if (obj instanceof Collection) {
            writeCollection((Collection<?>) obj, showType);
        } else if (obj instanceof JsonObject) {   // symmetric support for writing Map of Maps representation back as equivalent JSON format.
            JsonObject jObj = (JsonObject) obj;
            if (jObj.isArray()) {
                writeJsonObjectArray(jObj, showType);
            } else if (jObj.isCollection()) {
                writeJsonObjectCollection(jObj, showType);
            } else if (jObj.isMap()) {
                if (!writeJsonObjectMapWithStringKeys(jObj, showType)) {
                    writeJsonObjectMap(jObj, showType);
                }
            } else {
                writeJsonObjectObject(jObj, showType);
            }
        } else if (obj instanceof Map) {   // Map instanceof check must be after JsonObject check above (JsonObject is a subclass of Map)
            if (!writeMapWithStringKeys((Map) obj, showType)) {
                writeMap((Map) obj, showType);
            }
        } else {
            writeObject(obj, showType, false);
        }
    }

    private void writeId(final String id) throws IOException {
        out.write(idPrefix);
        out.write(id == null ? "0" : id);
    }

    // Optimized writeType method
    private void writeType(String name, Writer output) throws IOException {
        if (writeOptions.isNeverShowingType()) {
            return;
        }
        output.write(typePrefix);
        String alias = writeOptions.getTypeNameAlias(name);
        output.write(alias);
        output.write('"');
    }

    private void writePrimitive(final Object obj, boolean showType) throws IOException
    {
        if (writeOptions.isNeverShowingType()) {
            showType = false;
        }
        if (obj instanceof Long && getWriteOptions().isWriteLongsAsStrings()) {
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
        } else if (!writeOptions.isAllowNanAndInfinity() && obj instanceof Double && (Double.isNaN((Double) obj) || Double.isInfinite((Double) obj))) {
            out.write("null");
        } else if (!writeOptions.isAllowNanAndInfinity() && obj instanceof Float && (Float.isNaN((Float) obj) || Float.isInfinite((Float) obj))) {
            out.write("null");
        } else {
            out.write(obj.toString());
        }
    }

    private void writeArray(final Object array, boolean showType) throws IOException {
        if (writeOptions.isNeverShowingType()) {
            showType = false;
        }
        Class<?> arrayType = array.getClass();
        int len = Array.getLength(array);
        boolean referenced = objsReferenced.containsKey(array);
        boolean typeWritten = showType && !(arrayType.equals(Object[].class));
        final Writer output = this.out; // performance opt: place in final local for quicker access

        if (typeWritten || referenced) {
            output.write('{');
            tabIn();
        }

        if (referenced) {
            writeId(getId(array));
            output.write(',');
            newLine();
        }

        if (typeWritten) {
            writeType(arrayType.getName(), output);
            output.write(',');
            newLine();
        }

        if (len == 0) {
            if (typeWritten || referenced) {
                output.write(writeOptions.isShortMetaKeys() ? "\"@e\":[]" : "\"@items\":[]");
                tabOut();
                output.write('}');
            } else {
                output.write("[]");
            }
            return;
        }


        if (typeWritten || referenced) {
            output.write(writeOptions.isShortMetaKeys() ? "\"@e\":[" : "\"@items\":[");
        } else {
            output.write('[');
        }
        tabIn();

        final int lenMinus1 = len - 1;

        // Intentionally processing each primitive array type in separate
        // custom loop for speed. All of them could be handled using
        // reflective Array.get() but it is slower.  I chose speed over code length.


        if (byte[].class == arrayType) {
            writeByteArray((byte[]) array, lenMinus1);
        } else if (char[].class == arrayType) {
            writeJsonUtf8String(output, new String((char[]) array));
        } else if (short[].class == arrayType) {
            writeShortArray((short[]) array, lenMinus1);
        } else if (int[].class == arrayType) {
            writeIntArray((int[]) array, lenMinus1);
        } else if (long[].class == arrayType) {
            writeLongArray((long[]) array, lenMinus1);
        } else if (float[].class == arrayType) {
            writeFloatArray((float[]) array, lenMinus1);
        } else if (double[].class == arrayType) {
            writeDoubleArray((double[]) array, lenMinus1);
        } else if (boolean[].class == arrayType) {
            writeBooleanArray((boolean[]) array, lenMinus1);
        } else {
            final Class<?> componentClass = array.getClass().getComponentType();
            for (int i = 0; i < len; i++) {
                final Object value = Array.get(array, i);

                if (value == null) {
                    output.write("null");
                } else {
                    final boolean forceType = isForceType(value.getClass(), componentClass);
                    if (!writeArrayElementIfMatching(componentClass, value, forceType, output)) {
                        writeImpl(value, forceType);
                    }
                }

                if (i != lenMinus1) {   // Make sure no bogus comma at the end of the array
                    output.write(',');
                    newLine();
                }
            }
        }

        tabOut();
        output.write(']');
        if (typeWritten || referenced) {
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

    private void writeJsonObjectArray(JsonObject jObj, boolean showType) throws IOException {
        if (writeOptions.isNeverShowingType()) {
            showType = false;
        }
        int len = jObj.size();
        Class<?> arrayClass;
        Class<?> jsonObjectType = jObj.getRawType();
        
        if (jsonObjectType == null || Object[].class.equals(jsonObjectType)) {
            arrayClass = Object[].class;
        } else {
            arrayClass = jsonObjectType;
        }

        final Writer output = this.out;
        final boolean isObjectArray = Object[].class == arrayClass;
        final Class<?> componentClass = arrayClass.getComponentType();
        boolean referenced = adjustIfReferenced(jObj);
        boolean typeWritten = showType && !isObjectArray;

        if (typeWritten || referenced) {
            output.write('{');
            tabIn();
        }

        if (referenced) {
            writeId(Long.toString(jObj.id));
            output.write(',');
            newLine();
        }

        if (typeWritten) {
            writeType(arrayClass.getName(), output);
            output.write(',');
            newLine();
        }

        if (len == 0) {
            if (typeWritten || referenced) {
                output.write(writeOptions.isShortMetaKeys() ? "\"@e\":[]" : "\"@items\":[]");
                tabOut();
                output.write("}");
            } else {
                output.write("[]");
            }
            return;
        }

        if (typeWritten || referenced) {
            output.write(writeOptions.isShortMetaKeys() ? "\"@e\":[" : "\"@items\":[");
        } else {
            output.write('[');
        }
        tabIn();

        Object[] items = jObj.getItems();
        final int lenMinus1 = len - 1;

        for (int i = 0; i < len; i++) {
            final Object value = items[i];

            if (value == null) {
                output.write("null");
            } else {
                final boolean forceType = isForceType(value.getClass(), componentClass);
                if (writeArrayElementIfMatching(componentClass, value, forceType, output)) {
                } else if (Character.class == componentClass || char.class == componentClass) {
                    writeJsonUtf8String(output, (String) value);
                } else if (value instanceof String) {
                    writeJsonUtf8String(output, (String) value);
                } else if (value instanceof Boolean || value instanceof Long || value instanceof Double) {
                    writePrimitive(value, forceType);
                } else {   // Specific Class-type arrays - only force type when
                    // the instance is derived from array base class.
                    writeImpl(value, forceType);
                }
            }

            if (i != lenMinus1) {
                output.write(',');
                newLine();
            }
        }

        tabOut();
        output.write(']');
        if (typeWritten || referenced) {
            tabOut();
            output.write('}');
        }
    }

    private void writeJsonObjectCollection(JsonObject jObj, boolean showType) throws IOException {
        if (writeOptions.isNeverShowingType()) {
            showType = false;
        }
        Class<?> colClass = jObj.getRawType();
        boolean referenced = adjustIfReferenced(jObj);
        final Writer output = this.out;
        int len = jObj.size();

        if (referenced || showType || len == 0) {
            output.write('{');
            tabIn();
        }

        if (referenced) {
            writeId(String.valueOf(jObj.id));
        }

        if (showType) {
            if (referenced) {
                output.write(',');
                newLine();
            }

            writeType(colClass.getName(), output);
        }

        if (len == 0) {
            tabOut();
            output.write('}');
            return;
        }

        beginCollection(showType, referenced);

        Object[] items = jObj.getItems();
        final int itemsLen = items.length;
        final int itemsLenMinus1 = itemsLen - 1;

        for (int i = 0; i < itemsLen; i++) {
            writeCollectionElement(items[i]);

            if (i != itemsLenMinus1) {
                output.write(',');
                newLine();
            }
        }

        tabOut();
        output.write("]");
        if (showType || referenced) {
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
            String type = jObj.getRawTypeName();
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
        showType = showType && jObj.getType() != null;

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
            writeType(jObj.getRawTypeName(), output);
            type = jObj.getRawType();
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
            } else if (value instanceof Number || value instanceof Boolean) {
                output.write(value.toString());
            } else if (value instanceof String) {
                writeJsonUtf8String(output, (String) value);
            } else if (value instanceof Character) {
                writeJsonUtf8String(output, String.valueOf(value));
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

    // Hopefully this method goes away when the converters are done.
    // We're asking for a DeepDeclaredFields to check One Field Type
    // Its cached, but we shouldn't have to do that here.  I would think if its a known converter type
    // that we avoid this section all together.
    private boolean doesValueTypeMatchFieldType(Class<?> type, String fieldName, Object value)
    {
        if (type != null)
        {
            Map<String, Field> fieldMap = writeOptions.getDeepDeclaredFields(type);
            Field field = fieldMap.get(fieldName);
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

    private boolean writeMapBody(final Iterator i) throws IOException {
        final Writer output = out;
        while (i.hasNext()) {
            Entry att2value = (Entry) i.next();
            Object value = att2value.getValue();
            
            if (writeOptions.isSkipNullFields() && value == null) {
                continue;
            }
            
            writeJsonUtf8String(output, (String) att2value.getKey());
            output.write(":");
            writeCollectionElement(value);

            if (i.hasNext()) {
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
        for (Object o : map.keySet()) {
            if (!(o instanceof String)) {
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
        if (o == null) {
            out.write("null");
        } else if (o instanceof Boolean || o instanceof Double) {
            writePrimitive(o, false);
        } else if (o instanceof Long) {
            writePrimitive(o, getWriteOptions().isWriteLongsAsStrings());
        } else if (o instanceof String) {   // Never do an @ref to a String (they are treated as logical primitives and intern'ed on read)
            writeJsonUtf8String(out, (String) o);
        } else if (getWriteOptions().isNeverShowingType() && ClassUtilities.isPrimitive(o.getClass())) {   // If neverShowType, then force primitives (and primitive wrappers)
            // to be output with toString() - prevents {"value":6} for example
            writePrimitive(o, false);
        } else {
            writeImpl(o, true);
        }
    }

    private void writeEnumSet(final EnumSet<?> enumSet) throws IOException {
        out.write('{');
        tabIn();

        boolean referenced = this.objsReferenced.containsKey(enumSet);
        if (referenced) {
            writeId(getId(enumSet));
            out.write(',');
            newLine();
        }

        String typeKey = writeOptions.isEnumSetWrittenOldWay() ? ENUM : TYPE;
        out.write('\"');
        out.write(typeKey);
        out.write("\":");

        // Obtain the actual Enum class
        Class<?> enumClass = null;

        // Attempt to get the enum class from the 'elementType' field of EnumSet
        Field elementTypeField = writeOptions.getDeepDeclaredFields(EnumSet.class).get("elementType");
        if (elementTypeField != null) {
            enumClass = (Class<?>) getValueByReflect(enumSet, elementTypeField);
            // Ensure we get the actual enum class, not an anonymous subclass
            Class<?> actualEnumClass = ClassUtilities.getClassIfEnum(enumClass);
            enumClass = (actualEnumClass != null) ? actualEnumClass : enumClass;
        }

        // If we couldn't get it from 'elementType', try to get from the first enum constant
        if (enumClass == null) {
            if (!enumSet.isEmpty()) {
                Enum<?> e = enumSet.iterator().next();
                Class<?> actualEnumClass = ClassUtilities.getClassIfEnum(e.getClass());
                enumClass = (actualEnumClass != null) ? actualEnumClass : e.getClass();
            } else {
                // EnumSet is empty; try to get the enum class from the complement
                EnumSet<?> complement = EnumSet.complementOf(enumSet);
                if (!complement.isEmpty()) {
                    Enum<?> e = complement.iterator().next();
                    Class<?> actualEnumClass = ClassUtilities.getClassIfEnum(e.getClass());
                    enumClass = (actualEnumClass != null) ? actualEnumClass : e.getClass();
                } else {
                    // Cannot determine the enum class; use a placeholder
                    enumClass = MetaUtils.Dumpty.class;
                }
            }
        }

        // Write the @type field with the actual enum class name
        writeBasicString(out, enumClass.getName());

        // EnumSets are always written with an @items key
        out.write(",");
        newLine();
        writeBasicString(out, ITEMS);
        out.write(":[");
        boolean hasItems = !enumSet.isEmpty();

        if (hasItems) {
            newLine();
            tabIn();

            boolean firstInSet = true;
            for (Enum<?> e : enumSet) {
                if (!firstInSet) {
                    out.write(",");
                    newLine();
                }
                firstInSet = false;

                // Determine whether to write the full enum object or just the name
                Collection<Accessor> mapOfFields = writeOptions.getAccessorsForClass(e.getClass());
                int enumFieldsCount = mapOfFields.size();

                if (enumFieldsCount <= 2) {
                    // Write the enum name as a string
                    writeJsonUtf8String(out, e.name());
                } else {
                    // Write the enum as a JSON object with its fields
                    out.write('{');
                    boolean firstInEntry = true;
                    for (Accessor f : mapOfFields) {
                        firstInEntry = writeField(e, firstInEntry, f.getUniqueFieldName(), f);
                    }
                    out.write('}');
                }
            }

            tabOut();
            newLine();
        }

        out.write(']');
        tabOut();
        newLine();
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

        Collection<Accessor> accessors = writeOptions.getAccessorsForClass(obj.getClass());

        for (final Accessor accessor : accessors) {
            final String fieldName = accessor.getUniqueFieldName();
            first = writeField(obj, first, fieldName, accessor);
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

    private boolean writeField(Object obj, boolean first, String fieldName, Accessor accessor) throws IOException
    {
        final Class<?> fieldDeclaringClass = accessor.getDeclaringClass();
        Object o;

        //  Only here for enumAsObject writing
        if (Enum.class.isAssignableFrom(fieldDeclaringClass)) {
            if (!accessor.isPublic() && writeOptions.isEnumPublicFieldsOnly()) {
                return first;
            }

            o = accessor.retrieve(obj);
        } else {
            o = accessor.retrieve(obj);
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

        // check to see if type needs to be written.
        Class<?> type = accessor.getFieldType();
        writeImpl(o, isForceType(o.getClass(), type));
        return false;
    }

    private boolean isForceType(Class<?> objectClass, Class<?> declaredType) {
        // When no type is written we can check the Object itself not the declaration
        final boolean writeLongsAsStrings = writeOptions.isWriteLongsAsStrings();
        final boolean objectClassIsLongWrittenAsString = (objectClass == Long.class || objectClass == long.class) && writeLongsAsStrings;
        final boolean declaredClassIsLongWrittenAsString = (declaredType == Long.class || objectClass == long.class) && writeLongsAsStrings;

        if (Primitives.isNativeJsonType(objectClass) && !objectClassIsLongWrittenAsString) {
            return false;
        }

        if (Primitives.isPrimitive(declaredType) && !declaredClassIsLongWrittenAsString) {
            return false;
        }

        if (writeOptions.isNeverShowingType() && Primitives.isPrimitive(objectClass) && !objectClassIsLongWrittenAsString) {
            return false;
        }

        if (writeOptions.isAlwaysShowingType()) {
            return true;
        }

        if (objectClass == declaredType) {
            return false;
        }

        if (declaredType.isEnum() && declaredType.isAssignableFrom(objectClass)) {
            Class<?> enumClass = ClassUtilities.getClassIfEnum(objectClass);
            return !declaredType.equals(enumClass);
        }

        return true;
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

    /**
     * Writes out a string without special characters. Use for labels, etc. when you know you
     * will not need extra formattting for UTF-8 or tabs, quotes and newlines in the string
     *
     * @param writer Writer to which the UTF-8 string will be written to
     * @param s      String to be written in UTF-8 format on the output stream.
     * @throws IOException if an error occurs writing to the output stream.
     */
    public static void writeBasicString(final Writer writer, String s) throws IOException {
        writer.write('\"');
        writer.write(s);
        writer.write('\"');
    }

    /**
     * Writes a JSON string value to the output, properly escaped according to JSON specifications.
     * Handles control characters, quotes, backslashes, and properly processes Unicode code points.
     *
     * @param output The Writer to write to
     * @param s The string to be written as a JSON string value
     * @throws IOException If an I/O error occurs
     */
    public static void writeJsonUtf8String(final Writer output, String s) throws IOException {
        if (s == null) {
            output.write("null");
            return;
        }

        output.write('\"');
        final int len = s.length();

        for (int i = 0; i < len; ) {
            int codePoint = s.codePointAt(i);

            if (codePoint < 0x20 || codePoint == 0x7F) {  // Add DEL (0x7F) control character
                // Control characters
                switch (codePoint) {
                    case '\b': output.write("\\b"); break;
                    case '\f': output.write("\\f"); break;
                    case '\n': output.write("\\n"); break;
                    case '\r': output.write("\\r"); break;
                    case '\t': output.write("\\t"); break;
                    default: output.write(String.format("\\u%04x", codePoint));
                }
            }
            else if (codePoint == '"') {
                output.write("\\\"");
            }
            else if (codePoint == '\\') {
                output.write("\\\\");
            }
            // Optional: Handle forward slash escaping for </script> prevention
            else if (codePoint == '/') {
                // Some JSON encoders escape '/' to prevent issues with </script> in HTML
                // Uncomment if you want this behavior
                // output.write("\\/");
                output.write('/');
            }
            else if (codePoint >= 0x80 && codePoint <= 0xFFFF) {
                // Non-ASCII characters below surrogate range
                // For maximum compatibility, you might want to escape these
                // However, direct output is also valid with proper UTF-8 encoding
                // Choosing one approach:
                // 1. Direct output (works with proper UTF-8, more compact)
                output.write(s, i, Character.charCount(codePoint));
                // Alternatively, if you want to escape:
                // 2. Always escape (maximum compatibility, especially for older parsers)
                // output.write(String.format("\\u%04x", codePoint));
            }
            else if (codePoint > 0xFFFF) {
                // Supplementary characters (beyond BMP)
                output.write(s, i, Character.charCount(codePoint));
            }
            else {
                // ASCII characters (except control chars, quotes, backslashes)
                output.write(s, i, Character.charCount(codePoint));
            }

            i += Character.charCount(codePoint);
        }

        output.write('\"');
    }
}