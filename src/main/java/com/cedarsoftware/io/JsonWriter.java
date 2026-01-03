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
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import com.cedarsoftware.io.reflect.Accessor;
import com.cedarsoftware.util.ArrayUtilities;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.CompactMap;
import com.cedarsoftware.util.CompactSet;
import com.cedarsoftware.util.FastWriter;
import com.cedarsoftware.util.IOUtilities;
import com.cedarsoftware.util.TypeUtilities;

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
public class JsonWriter implements WriterContext, Closeable, Flushable {
    private static final Logger LOG = Logger.getLogger(JsonWriter.class.getName());
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
    // Lookup table for fast ASCII escape detection (128 entries for chars 0-127)
    private static final boolean[] NEEDS_ESCAPE = new boolean[128];
    private final WriteOptions writeOptions;
    private final Map<Object, Long> objVisited = new IdentityHashMap<>(256); // Pre-size for better performance
    private final Map<Object, Long> objsReferenced = new IdentityHashMap<>(256); // Pre-size for better performance
    private final Writer out;
    private long identity = 1;
    private int depth = 0;

    // Element type context: when writing Collection/Map fields, this tracks the declared element type
    // from the field's generic type (e.g., List<NestedData> -> NestedData). Used to eliminate
    // redundant @type output when element instance type == declared element type.
    // For Maps with non-String keys written as @keys/@items arrays, declaredKeyType tracks the key type.
    private Class<?> declaredElementType = null;
    private Class<?> declaredKeyType = null;

    // Context tracking for automatic comma management
    private final Deque<WriteContext> contextStack = new ArrayDeque<>();

    /**
     * Tracks the current write context to enable automatic comma insertion.
     * This eliminates the need for manual "boolean first" tracking in custom writers.
     */
    enum WriteContext {
        ROOT,          // At document root
        OBJECT_EMPTY,  // Inside object, no fields written yet
        OBJECT_FIELD,  // Inside object, field(s) written
        ARRAY_EMPTY,   // Inside array, no elements yet
        ARRAY_ELEMENT  // Inside array, element(s) written
    }

    static {
        for (short i = -128; i <= 127; i++) {
            char[] chars = Integer.toString(i).toCharArray();
            byteStrings[i + 128] = chars;
        }

        // Initialize escape lookup table for fast ASCII escape detection
        // Mark all control characters (0x00-0x1F) as needing escape
        for (int i = 0; i < 0x20; i++) {
            NEEDS_ESCAPE[i] = true;
        }
        // Mark specific characters that need escaping
        NEEDS_ESCAPE['"'] = true;   // Quote
        NEEDS_ESCAPE['\\'] = true;  // Backslash
        NEEDS_ESCAPE[0x7F] = true;  // DEL control character
    }

    /**
     * @deprecated Use top-level {@link com.cedarsoftware.io.JsonClassWriter} instead.
     *             This nested interface is kept for backward compatibility.
     */
    @Deprecated
    public interface JsonClassWriter<T> extends com.cedarsoftware.io.JsonClassWriter<T> {
    }

    /**
     * @param out OutputStream to which the JSON will be written.  Uses the default WriteOptions.
     * @see WriteOptions
     */
    public JsonWriter(OutputStream out) {
        this(out, null);
    }

    /**
     * @param out          OutputStream to which the JSON output will be written.
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
     * Map containing all objects that were referenced within input object graph.
     */
    public Map<Object, Long> getObjsReferenced() {
        return objsReferenced;
    }

    /**
     * Tab the output left (less indented)
     *
     * @throws IOException
     */
    public void tabIn() throws IOException {
        tab(out, 1);
    }

    /**
     * Add newline (\n) to output
     *
     * @throws IOException
     */
    public void newLine() throws IOException {
        tab(out, 0);
    }

    /**
     * Tab the output right (more indented)
     *
     * @throws IOException
     */
    public void tabOut() throws IOException {
        tab(out, -1);
    }

    /**
     * tab the JSON output by the given number of characters specified by delta.
     *
     * @param output Writer being used for JSON output.
     * @param delta  int number of characters to tab.
     * @throws IOException
     */
    private void tab(Writer output, int delta) throws IOException {
        if (!writeOptions.isPrettyPrint()) {
            return;
        }
        output.write(NEW_LINE);
        depth += delta;

        // Optimized indentation - build spaces once instead of multiple writes
        if (depth > 0) {
            // Prevent excessive indentation to avoid memory issues using configurable limit
            final int maxDepth = writeOptions.getMaxIndentationDepth();
            final int actualDepth = Math.min(depth, maxDepth);

            // Pre-compute indentation string for efficiency using configurable values
            final int indentationThreshold = writeOptions.getIndentationThreshold();
            final int indentationSize = writeOptions.getIndentationSize();

            if (actualDepth <= indentationThreshold) {
                // For small depths, use simple repeated writes (faster for small depths)
                for (int i = 0; i < actualDepth; i++) {
                    for (int j = 0; j < indentationSize; j++) {
                        output.write(' ');
                    }
                }
            } else {
                // For larger depths, build the string once and write it
                char[] spaces = new char[actualDepth * indentationSize];
                Arrays.fill(spaces, ' ');
                output.write(spaces);
            }

            if (depth > maxDepth) {
                // Warn about excessive depth to help detect issues
                output.write("... (depth=" + depth + ")");
            }
        }
    }

    /**
     * Write the passed in object (o) to the JSON output stream, if and only if, there is a custom
     * writer associated to the Class of object (o).
     *
     * @param o        Object to be (potentially written)
     * @param showType boolean indicating whether to show @type.
     * @param output   Writer where the actual JSON is being written to.
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
     *
     * @param arrayComponentClass Class type of the array
     * @param o                   Object instance to write
     * @param showType            boolean indicating whether @type should be output.
     * @param output              Writer to write the JSON to (if there is a custom writer for o's Class).
     * @return true if the array element was written, false otherwise.
     */
    public boolean writeArrayElementIfMatching(Class<?> arrayComponentClass, Object o, boolean showType, Writer output) {
        if (!arrayComponentClass.isInstance(o)) {
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
     *
     * @param clazz    Class type of the array
     * @param o        Object instance to write
     * @param showType boolean indicating whether @type should be output.
     * @param output   Writer to write the JSON to (if there is a custom writer for o's Class).
     * @return true if the array element was written, false otherwise.
     */
    protected boolean writeCustom(Class<?> clazz, Object o, boolean showType, Writer output) throws IOException {
        if (!isCustomWrittenClass(clazz, o)) {
            return false;
        }
        if (writeOptions.isNeverShowingType()) {
            showType = false;
        }

        com.cedarsoftware.io.JsonClassWriter closestWriter = writeOptions.getCustomWriter(o.getClass());

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
     *
     * @param obj Object any Java Object or JsonObject.
     */
    public void write(Object obj) {
        traceReferences(obj);
        objVisited.clear();
        try {
            boolean showType = true;
            if (obj != null) {
                if (writeOptions.isNeverShowingType()) {
                    showType = false;
                } else if (!writeOptions.isAlwaysShowingType()) {
                    com.cedarsoftware.io.JsonClassWriter writer = writeOptions.getCustomWriter(obj.getClass());
                    if (writer instanceof Writers.EnumsAsStringWriter) {
                        int fieldCount = writeOptions.getAccessorsForClass(obj.getClass()).size();
                        if (fieldCount <= 2) {
                            String alias = writeOptions.getTypeNameAlias(obj.getClass().getName());
                            if (alias.equals(obj.getClass().getName())) {
                                showType = false;
                            }
                        }
                    }
                }
            }
            writeImpl(obj, showType);
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
     *
     * @param root Object to be deeply traced.  The objVisited and objsReferenced Maps will be written to
     *             during the trace.
     */
    protected void traceReferences(Object root) {
        if (root == null) {
            return;
        }

        // Use two parallel stacks to avoid Object[] allocation per push (performance optimization)
        final Deque<Object> objectStack = new ArrayDeque<>(256);
        final Deque<Integer> depthStack = new ArrayDeque<>(256);
        objectStack.addFirst(root);
        depthStack.addFirst(0);

        final Map<Object, Long> visited = objVisited;
        final Map<Object, Long> referenced = objsReferenced;

        // Fix memory leak - prevent stack overflow and unbounded memory growth using configurable limits
        final int MAX_DEPTH = writeOptions.getMaxObjectGraphDepth();
        final int MAX_OBJECTS = writeOptions.getMaxObjectCount();
        int processedCount = 0;

        while (!objectStack.isEmpty() && processedCount < MAX_OBJECTS) {
            final Object obj = objectStack.removeFirst();
            final int currentDepth = depthStack.removeFirst();
            processedCount++;

            // Check actual depth, not stack size
            if (currentDepth > MAX_DEPTH) {
                throw new JsonIoException("Object graph too deep (>" + MAX_DEPTH + " levels). This may indicate a circular reference or excessively nested structure.");
            }

            // Cache class once - avoid duplicate getClass() calls (performance optimization)
            final Class<?> clazz = obj.getClass();
            final boolean isNonReferenceable = writeOptions.isNonReferenceableClass(clazz);

            if (!isNonReferenceable) {
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

            final int nextDepth = currentDepth + 1;

            // Use instanceof instead of isAssignableFrom (30x faster)
            if (obj instanceof Object[]) {
                processArray((Object[]) obj, objectStack, depthStack, nextDepth);
            } else if (obj instanceof JsonObject) {
                processJsonObject((JsonObject) obj, objectStack, depthStack, nextDepth);
            } else if (obj instanceof Map) {
                processMap((Map<?, ?>) obj, objectStack, depthStack, nextDepth);
            } else if (obj instanceof Collection) {
                processCollection((Collection<?>) obj, objectStack, depthStack, nextDepth);
            } else if (!isNonReferenceable) {
                // Reuse cached isNonReferenceable result
                processFields(objectStack, depthStack, obj, nextDepth);
            }
        }

        // Check if we hit the object limit
        if (processedCount >= MAX_OBJECTS) {
            throw new JsonIoException("Object graph too large (>" + MAX_OBJECTS + " objects). This may indicate excessive nesting or a memory leak.");
        }
    }

    private void processArray(Object[] array, Deque<Object> objectStack, Deque<Integer> depthStack, int depth) {
        final Class<?> componentType = array.getClass().getComponentType();
        if (!writeOptions.isNonReferenceableClass(componentType)) {
            // Iterate directly over Object[] - primitive arrays are filtered out by isNonReferenceableClass check
            for (final Object element : array) {
                if (element != null) {
                    objectStack.addFirst(element);
                    depthStack.addFirst(depth);
                }
            }
        }
    }

    private void processJsonObject(JsonObject jsonObj, Deque<Object> objectStack, Deque<Integer> depthStack, int depth) {
        // Traverse items (array elements)
        Object[] items = jsonObj.getItems();
        if (!ArrayUtilities.isEmpty(items)) {
            processArray(items, objectStack, depthStack, depth);
        }

        // Traverse keys (for JsonObject representing maps)
        Object[] keys = jsonObj.getKeys();
        if (!ArrayUtilities.isEmpty(keys)) {
            processArray(keys, objectStack, depthStack, depth);
        }

        // Traverse other entries in jsonStore (allows for Collections to have properties)
        processMap(jsonObj, objectStack, depthStack, depth);
    }

    private void processMap(Map<?, ?> map, Deque<Object> objectStack, Deque<Integer> depthStack, int depth) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key != null) {
                objectStack.addFirst(key);
                depthStack.addFirst(depth);
            }
            if (value != null) {
                objectStack.addFirst(value);
                depthStack.addFirst(depth);
            }
        }
    }

    private void processCollection(Collection<?> collection, Deque<Object> objectStack, Deque<Integer> depthStack, int depth) {
        for (Object item : collection) {
            if (item != null) {
                objectStack.addFirst(item);
                depthStack.addFirst(depth);
            }
        }
    }

    /**
     * Reach-ability trace to visit all objects within the graph to be written.
     * This API will handle any object, using either reflection APIs or by
     * consulting a specified includedFields map if provided.
     *
     * @param objectStack Deque of objects used to manage descent into graph (rather than using Java stack)
     * @param depthStack  Deque of depths corresponding to each object (parallel stack)
     * @param obj         Object root of graph
     * @param depth       Current depth in the object graph
     */
    protected void processFields(final Deque<Object> objectStack, final Deque<Integer> depthStack, final Object obj, int depth) {
        // If caller has special Field specifier for a given class
        // then use it, otherwise use reflection.
        Collection<Accessor> fields = writeOptions.getAccessorsForClass(obj.getClass());

        for (final Accessor accessor : fields) {
            final Object o = accessor.retrieve(obj);
            if (o != null) {   // Trace through objects that can reference other objects
                objectStack.addFirst(o);
                depthStack.addFirst(depth);
            }
        }
    }

    private boolean writeOptionalReference(Object obj) throws IOException {
        if (obj == null || writeOptions.isNonReferenceableClass(obj.getClass())) {
            return false;
        }

        final Writer output = this.out;
        if (objVisited.containsKey(obj)) {    // Only write (define) an object once in the JSON stream, otherwise emit a @ref
            String id = getId(obj);
            if (id == null) {   // Test for null because of Weak/Soft references being gc'd during serialization.
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
     *
     * @param obj      Object to be written
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

        // Custom writers and references are checked first to preserve type information
        if (writeUsingCustomWriter(obj, showType, out) || writeOptionalReference(obj)) {
            return;
        }

        final Class<?> objClass = obj.getClass();
        if (objClass.isArray()) {
            if (objClass.getComponentType().isPrimitive()) {
                writePrimitiveArray(obj, objClass, showType);
            } else {
                writeObjectArray((Object[]) obj, objClass, showType);
            }
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

    private void writePrimitive(final Object obj, boolean showType) throws IOException {
        if (writeOptions.isNeverShowingType()) {
            showType = false;
        }
        if (obj instanceof Long && getWriteOptions().isWriteLongsAsStrings()) {
            if (showType) {
                out.write('{');
                writeType("long", out);
                out.write(',');
            }

            com.cedarsoftware.io.JsonClassWriter writer = getWriteOptions().getCustomWriter(Long.class);
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

    private void writeObjectArray(final Object[] array, final Class<?> arrayType, boolean showType) throws IOException {
        if (writeOptions.isNeverShowingType()) {
            showType = false;
        }
        final int len = array.length;  // Direct access - no reflection needed
        boolean referenced = objsReferenced.containsKey(array);
        boolean typeWritten = showType && !(arrayType.equals(Object[].class));
        final Writer output = this.out;

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
        final Class<?> componentClass = arrayType.getComponentType();

        // Write array elements with direct array access - no reflection
        for (int i = 0; i < len; i++) {
            final Object value = array[i];

            if (value == null) {
                output.write("null");
            } else {
                final boolean forceType = isForceType(value.getClass(), componentClass);
                if (!writeArrayElementIfMatching(componentClass, value, forceType, output)) {
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

    private void writePrimitiveArray(final Object array, final Class<?> arrayType, boolean showType) throws IOException {
        if (writeOptions.isNeverShowingType()) {
            showType = false;
        }
        final int len = ArrayUtilities.getLength(array);
        boolean referenced = objsReferenced.containsKey(array);
        boolean typeWritten = showType && !(arrayType.equals(Object[].class));
        final Writer output = this.out;

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

        // Handle each primitive array type with optimized loops
        if (byte[].class == arrayType) {
            writeByteArray((byte[]) array, lenMinus1);
        } else if (char[].class == arrayType) {
            writeJsonUtf8String(output, new String((char[]) array), writeOptions.getMaxStringLength());
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
        }

        tabOut();
        output.write(']');
        if (typeWritten || referenced) {
            tabOut();
            output.write('}');
        }
    }

    private void writeBooleanArray(boolean[] booleans, int lenMinus1) throws IOException {
        final Writer output = this.out;
        for (int i = 0; i < lenMinus1; i++) {
            output.write(booleans[i] ? "true," : "false,");
        }
        output.write(Boolean.toString(booleans[lenMinus1]));
    }

    private void writeDoubleArray(double[] doubles, int lenMinus1) throws IOException {
        final Writer output = this.out;
        final com.cedarsoftware.io.JsonClassWriter writer = getWriteOptions().getCustomWriter(Double.class);
        for (int i = 0; i < lenMinus1; i++) {
            writer.write(doubles[i], false, output, this);
            output.write(',');
        }
        writer.write(doubles[lenMinus1], false, output, this);
    }

    private void writeFloatArray(float[] floats, int lenMinus1) throws IOException {
        final Writer output = this.out;
        final com.cedarsoftware.io.JsonClassWriter writer = getWriteOptions().getCustomWriter(Float.class);
        for (int i = 0; i < lenMinus1; i++) {
            writer.write(floats[i], false, output, this);
            output.write(',');
        }

        writer.write(floats[lenMinus1], false, output, this);
    }

    private void writeLongArray(long[] longs, int lenMinus1) throws IOException {
        final Writer output = this.out;

        com.cedarsoftware.io.JsonClassWriter writer = getWriteOptions().getCustomWriter(long.class);
        for (int i = 0; i < lenMinus1; i++) {
            writer.write(longs[i], false, output, this);
            output.write(',');
        }
        writer.write(longs[lenMinus1], false, output, this);

    }

    private void writeIntArray(int[] ints, int lenMinus1) throws IOException {
        // Add bounds checking for safety
        if (ints == null) {
            throw new JsonIoException("Int array cannot be null");
        }
        if (lenMinus1 < 0 || lenMinus1 >= ints.length) {
            throw new JsonIoException("Invalid array bounds: lenMinus1=" + lenMinus1 + ", array.length=" + ints.length);
        }

        final Writer output = this.out;
        for (int i = 0; i < lenMinus1; i++) {
            output.write(Integer.toString(ints[i]));
            output.write(',');
        }
        output.write(Integer.toString(ints[lenMinus1]));
    }

    private void writeShortArray(short[] shorts, int lenMinus1) throws IOException {
        final Writer output = this.out;
        for (int i = 0; i < lenMinus1; i++) {
            output.write(Integer.toString(shorts[i]));
            output.write(',');
        }
        output.write(Integer.toString(shorts[lenMinus1]));
    }

    private void writeByteArray(byte[] bytes, int lenMinus1) throws IOException {
        // Add bounds checking for safety
        if (bytes == null) {
            throw new JsonIoException("Byte array cannot be null");
        }
        if (lenMinus1 < 0 || lenMinus1 >= bytes.length) {
            throw new JsonIoException("Invalid array bounds: lenMinus1=" + lenMinus1 + ", array.length=" + bytes.length);
        }

        final Writer output = this.out;
        final Object[] byteStrs = byteStrings;
        for (int i = 0; i < lenMinus1; i++) {
            int index = bytes[i] + 128;
            // Bounds check for byteStrings array access
            if (index < 0 || index >= byteStrs.length) {
                throw new JsonIoException("Byte value out of range: " + bytes[i]);
            }
            output.write((char[]) byteStrs[index]);
            output.write(',');
        }
        int finalIndex = bytes[lenMinus1] + 128;
        if (finalIndex < 0 || finalIndex >= byteStrs.length) {
            throw new JsonIoException("Byte value out of range: " + bytes[lenMinus1]);
        }
        output.write((char[]) byteStrs[finalIndex]);
    }

    private void writeCollection(Collection<?> col, boolean showType) throws IOException {
        if (writeOptions.isNeverShowingType()) {
            showType = false;
        }
        final Writer output = this.out;
        boolean referenced = this.objsReferenced.containsKey(col);
        boolean isEmpty = col.isEmpty();

        if (referenced || showType) {
            output.write('{');
            tabIn();
        } else if (isEmpty) {
            output.write('[');
        }

        writeIdAndTypeIfNeeded(col, showType, referenced);

        if (isEmpty) {
            if (referenced || showType) {
                tabOut();
                output.write('}');
            } else {
                output.write(']');
            }
            return;
        }

        beginCollection(showType, referenced);
        Iterator<?> i = col.iterator();

        writeElements(output, i);

        tabOut();
        output.write(']');
        if (showType || referenced) {   // Finished object, as it was output as an object if @id or @type was output
            tabOut();
            output.write("}");
        }
    }

    private void writeElements(Writer output, Iterator<?> i) throws IOException {
        while (i.hasNext()) {
            writeCollectionElement(i.next());

            if (i.hasNext()) {
                output.write(',');
                newLine();
            }
        }
    }

    /**
     * Determines the type name to write for an object, preferring preserved typeString
     * over actual class name for middleware safety.
     *
     * This handles the case where JSON is parsed on a system without the original class
     * (e.g., com.example.House), stored as a fallback type (LinkedHashMap), and needs to
     * be re-serialized preserving the original @type for downstream systems.
     *
     * @param obj the object to get the type name for
     * @return the type name to write in the JSON @type field
     */
    private String getTypeNameForOutput(Object obj) {
        // Check if this is a JsonObject with a preserved typeString (middleware case)
        if (obj instanceof JsonObject) {
            JsonObject jsonObj = (JsonObject) obj;
            String typeString = jsonObj.getTypeString();

            // If typeString exists and differs from actual class, use it
            // This handles the middleware case where class wasn't available
            if (typeString != null &&
                !typeString.isEmpty() &&
                !typeString.equals(obj.getClass().getName())) {
                return typeString;  // Use preserved original @type
            }
        }

        // Normal case: use actual class name
        return obj.getClass().getName();
    }

    private void writeIdAndTypeIfNeeded(Object col, boolean showType, boolean referenced) throws IOException {
        if (writeOptions.isNeverShowingType()) {
            showType = false;
        }
        if (referenced) {
            writeId(getId(col));
        }

        if (showType) {
            if (referenced) {
                out.write(',');
                newLine();
            }
            writeType(getTypeNameForOutput(col), out);
        }
    }

    private void beginCollection(boolean showType, boolean referenced) throws IOException {
        if (showType || referenced) {
            out.write(',');
            newLine();
            out.write(writeOptions.isShortMetaKeys() ? "\"@e\":[" : "\"@items\":[");
        } else {
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
                    writeJsonUtf8String(output, (String) value, writeOptions.getMaxStringLength());
                } else if (value instanceof String) {
                    writeJsonUtf8String(output, (String) value, writeOptions.getMaxStringLength());
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

    private void writeJsonObjectMap(JsonObject jObj, boolean showType) throws IOException {
        if (writeOptions.isNeverShowingType()) {
            showType = false;
        }
        final Writer output = this.out;
        showType = emitIdAndTypeIfNeeded(jObj, showType, output);

        if (jObj.isEmpty()) {   // Empty
            tabOut();
            output.write('}');
            return;
        }

        if (showType) {
            output.write(',');
            newLine();
        }

        writeMapToEnd(jObj, output);
    }

    private boolean writeJsonObjectMapWithStringKeys(JsonObject jObj, boolean showType) throws IOException {
        if (writeOptions.isNeverShowingType()) {
            showType = false;
        }

        if (writeOptions.isForceMapOutputAsTwoArrays() || !ensureJsonPrimitiveKeys(jObj)) {
            return false;
        }

        Writer output = this.out;
        emitIdAndTypeIfNeeded(jObj, showType, output);

        if (jObj.isEmpty()) { // Empty
            tabOut();
            output.write('}');
            return true;
        }

        if (showType) {
            output.write(',');
            newLine();
        }

        return writeMapBody(jObj.entrySet().iterator());
    }

    private boolean emitIdAndTypeIfNeeded(JsonObject jObj, boolean showType, Writer output) throws IOException {
        boolean referenced = adjustIfReferenced(jObj);
        output.write('{');
        tabIn();

        if (referenced) {
            writeId(String.valueOf(jObj.getId()));
        }

        if (showType) {
            if (referenced) {
                output.write(',');
                newLine();
            }
            String type = getTypeNameForOutput(jObj);
            if (type != null) {
                writeType(type, output);
            } else {   // type not displayed
                showType = false;
            }
        }
        return showType;
    }

    /**
     * Write fields of an Object (JsonObject)
     */
    private void writeJsonObjectObject(JsonObject jObj, boolean showType) throws IOException {
        if (writeOptions.isNeverShowingType()) {
            showType = false;
        }
        final Writer output = this.out;
        boolean referenced = adjustIfReferenced(jObj);
        showType = showType && jObj.getType() != null;

        output.write('{');
        tabIn();
        if (referenced) {
            writeId(String.valueOf(jObj.id));
        }

        Class<?> type = null;
        if (showType) {
            if (referenced) {
                output.write(',');
                newLine();
            }
            writeType(getTypeNameForOutput(jObj), output);
            type = jObj.getRawType();
        }

        if (jObj.isEmpty()) {
            tabOut();
            output.write('}');
            return;
        }

        if (showType || referenced) {
            output.write(',');
            newLine();
        }

        Iterator<Map.Entry<Object, Object>> i = jObj.entrySet().iterator();
        boolean first = true;

        while (i.hasNext()) {
            Map.Entry<Object, Object> entry = i.next();
            if (writeOptions.isSkipNullFields() && entry.getValue() == null) {
                continue;
            }

            if (!first) {
                output.write(',');
                newLine();
            }
            first = false;
            final String fieldName = (String) entry.getKey();
            output.write('"');
            output.write(fieldName);
            output.write("\":");
            Object value = entry.getValue();

            if (value == null) {
                output.write("null");
            } else if (value instanceof BigDecimal || value instanceof BigInteger) {
                writeImpl(value, !doesValueTypeMatchFieldType(type, fieldName, value));
            } else if (value instanceof Number || value instanceof Boolean) {
                output.write(value.toString());
            } else if (value instanceof String) {
                writeJsonUtf8String(output, (String) value, writeOptions.getMaxStringLength());
            } else if (value instanceof Character) {
                writeJsonUtf8String(output, String.valueOf(value), writeOptions.getMaxStringLength());
            } else {
                writeImpl(value, !doesValueTypeMatchFieldType(type, fieldName, value));
            }
        }
        tabOut();
        output.write('}');
    }

    private boolean adjustIfReferenced(JsonObject jObj) {
        Long idx = objsReferenced.get(jObj);
        if (!jObj.hasId() && idx != null && idx > 0) {   // Referenced object that needs an ID copied to it.
            jObj.id = idx;
        }
        return objsReferenced.containsKey(jObj) && jObj.hasId();
    }

    // Hopefully this method goes away when the converters are done.
    // We're asking for a DeepDeclaredFields to check One Field Type
    // Its cached, but we shouldn't have to do that here.  I would think if its a known converter type
    // that we avoid this section all together.
    private boolean doesValueTypeMatchFieldType(Class<?> type, String fieldName, Object value) {
        if (type != null) {
            Map<String, Field> fieldMap = writeOptions.getDeepDeclaredFields(type);
            Field field = fieldMap.get(fieldName);
            return field != null && field.getType().equals(value.getClass());
        }
        return false;
    }

    private void writeMap(Map map, boolean showType) throws IOException {
        if (writeOptions.isNeverShowingType()) {
            showType = false;
        }
        final Writer output = this.out;
        boolean referenced = this.objsReferenced.containsKey(map);

        output.write('{');
        tabIn();
        if (referenced) {
            writeId(getId(map));
        }

        if (showType) {
            if (referenced) {
                output.write(',');
                newLine();
            }
            writeType(getTypeNameForOutput(map), output);
        }

        if (map.isEmpty()) {
            tabOut();
            output.write('}');
            return;
        }

        if (showType || referenced) {
            output.write(',');
            newLine();
        }

        writeMapToEnd(map, output);
    }

    private void writeMapToEnd(Map map, Writer output) throws IOException {
        // Save current element type (Map value type) and switch to key type for @keys array
        Class<?> savedValueType = declaredElementType;

        output.write(writeOptions.isShortMetaKeys() ? "\"@k\":[" : "\"@keys\":[");
        tabIn();
        Iterator<?> i = map.keySet().iterator();

        // Use key type context for writing @keys array elements
        declaredElementType = declaredKeyType;
        writeElements(output, i);

        tabOut();
        output.write("],");
        newLine();
        output.write(writeOptions.isShortMetaKeys() ? "\"@e\":[" : "\"@items\":[");
        tabIn();
        i = map.values().iterator();

        // Restore value type context for writing @items array elements
        declaredElementType = savedValueType;
        writeElements(output, i);

        tabOut();
        output.write(']');
        tabOut();
        output.write('}');
    }

    private boolean writeMapWithStringKeys(Map map, boolean showType) throws IOException {
        if (writeOptions.isNeverShowingType()) {
            showType = false;
        }
        if (writeOptions.isForceMapOutputAsTwoArrays() || !ensureJsonPrimitiveKeys(map)) {
            return false;
        }

        boolean referenced = this.objsReferenced.containsKey(map);

        out.write('{');
        tabIn();
        writeIdAndTypeIfNeeded(map, showType, referenced);

        if (map.isEmpty()) {
            tabOut();
            out.write('}');
            return true;
        }

        if (showType || referenced) {
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

            writeJsonUtf8String(output, (String) att2value.getKey(), writeOptions.getMaxStringLength());
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
     *
     * @param map Map to inspect that all keys are primitive.  This allows the output JSON
     *            to be optimized into {"key1":value1, "key2": value2} format if all the
     *            keys of the Map are Strings.  If not, then a Map is written as two
     *            arrays, a @keys array and an @items array.  This allows support for Maps
     *            with non-String keys.
     */
    public static boolean ensureJsonPrimitiveKeys(Map map) {
        for (Object o : map.keySet()) {
            if (!(o instanceof String)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Write an element that is contained in some type of Collection or Map.
     *
     * @param o Collection element to output in JSON format.
     * @throws IOException if an error occurs writing to the output stream.
     */
    private void writeCollectionElement(Object o) throws IOException {
        if (o == null) {
            out.write("null");
        } else if (o instanceof Boolean || o instanceof Double) {
            writePrimitive(o, false);
        } else if (o instanceof Long) {
            writePrimitive(o, getWriteOptions().isWriteLongsAsStrings());
        } else if (o instanceof String) {   // Never do an @ref to a String (they are treated as logical primitives and intern'ed on read)
            writeJsonUtf8String(out, (String) o, writeOptions.getMaxStringLength());
        } else if (getWriteOptions().isNeverShowingType() && ClassUtilities.isPrimitive(o.getClass())) {   // If neverShowType, then force primitives (and primitive wrappers)
            // to be output with toString() - prevents {"value":6} for example
            writePrimitive(o, false);
        } else {
            // Use declaredElementType to determine if @type is needed - eliminates redundant @type
            // when element instance type == declared element type from field generic info
            boolean showType = shouldShowTypeForElement(o.getClass());
            writeImpl(o, showType);
        }
    }

    /**
     * Determines whether @type should be written for a collection/map element.
     * If declaredElementType is set (from a field with generic info like List&lt;Foo&gt;),
     * and the element's class exactly matches (==) the declared type, @type is not needed.
     *
     * This optimization works because JsonParser propagates element type context when
     * parsing @items arrays - see JsonParser.pushArrayFrame() which receives element type,
     * and pushNestedContainerFrame() which passes it to pushObjectFrame().
     *
     * @param elementClass the actual class of the element being written
     * @return true if @type should be written, false if it can be omitted
     */
    private boolean shouldShowTypeForElement(Class<?> elementClass) {
        // If no declared element type context, default to showing type
        if (declaredElementType == null) {
            return true;
        }
        // Enums always need @type for proper deserialization (written as String, need type to convert back)
        if (elementClass.isEnum()) {
            return true;
        }
        // Per user guidance: must be exact match (==), not isAssignableFrom
        // This ensures the Resolver can instantiate the correct concrete type
        return elementClass != declaredElementType;
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
                    writeJsonUtf8String(out, e.name(), writeOptions.getMaxStringLength());
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
    public void writeObject(final Object obj, boolean showType, boolean bodyOnly) throws IOException {
        if (writeOptions.isNeverShowingType()) {
            showType = false;
        }
        final boolean referenced = this.objsReferenced.containsKey(obj);
        if (!bodyOnly) {
            out.write('{');
            tabIn();
            if (referenced) {
                writeId(getId(obj));
            }

            if (referenced && showType) {
                out.write(',');
                newLine();
            }

            if (showType) {
                writeType(obj.getClass().getName(), out);
            }
        }

        boolean first = !showType;
        if (referenced && !showType) {
            first = false;
        }

        Collection<Accessor> accessors = writeOptions.getAccessorsForClass(obj.getClass());

        for (final Accessor accessor : accessors) {
            final String fieldName = accessor.getUniqueFieldName();
            first = writeField(obj, first, fieldName, accessor);
        }

        if (!bodyOnly) {
            tabOut();
            out.write('}');
        }
    }

    private Object getValueByReflect(Object obj, Field field) {
        // Fix unsafe reflection access - add comprehensive null and security checks
        if (field == null) {
            return null;
        }

        // Allow static field access even with null object
        if (obj == null && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            return null;
        }

        try {
            // Security enhancement - only allow access to public fields or already accessible fields
            if (!field.isAccessible() && !java.lang.reflect.Modifier.isPublic(field.getModifiers())) {
                // Private/protected fields are not made accessible for security
                return null;
            }

            // Ensure field is accessible before attempting access (Java 8 compatible)
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }

            return field.get(obj);
        } catch (IllegalAccessException e) {
            // Field access denied - return null rather than exposing internal error
            return null;
        } catch (SecurityException e) {
            // Security manager denied access - fail safely
            return null;
        } catch (Exception e) {
            // Any other reflection-related exception - fail safely without exposing details
            return null;
        }
    }

    private boolean writeField(Object obj, boolean first, String fieldName, Accessor accessor) throws IOException {
        final Class<?> fieldDeclaringClass = accessor.getDeclaringClass();
        if (Enum.class.isAssignableFrom(fieldDeclaringClass) && !accessor.isPublic() && writeOptions.isEnumPublicFieldsOnly()) {
            return first;
        }
        Object o = accessor.retrieve(obj);

        if (writeOptions.isSkipNullFields() && o == null) {   // If skip null, skip field and return the same status on first field written indicator
            return first;
        }

        if (!first) {
            out.write(',');
            newLine();
        }

        out.write("\"");
        out.write(fieldName);   // Not using slower UTF String writer for field names
        out.write("\":");

        if (o == null) {    // don't quote null
            out.write("null");
            return false;
        }

        // check to see if type needs to be written.
        Class<?> type = accessor.getFieldType();

        // For Collection/Map fields, extract the element type from generic type to eliminate redundant @type on elements
        // Skip this optimization for Throwables - ThrowableFactory uses ClassFactory which needs @type info
        Class<?> savedElementType = declaredElementType;
        Class<?> savedKeyType = declaredKeyType;
        try {
            if ((o instanceof Collection || o instanceof Map) && !Throwable.class.isAssignableFrom(fieldDeclaringClass)) {
                Type genericType = accessor.getGenericType();
                if (genericType != null) {
                    // For Maps, extract both key type and value type
                    if (o instanceof Map) {
                        Type keyType = extractMapKeyType(genericType);
                        if (keyType instanceof Class) {
                            declaredKeyType = (Class<?>) keyType;
                        }
                    }
                    // inferElementType returns value type for Maps, element type for Collections
                    Type elementType = TypeUtilities.inferElementType(genericType, null);
                    if (elementType instanceof Class) {
                        declaredElementType = (Class<?>) elementType;
                    }
                }
            }
            writeImpl(o, isForceType(o.getClass(), type));
        } finally {
            declaredElementType = savedElementType;
            declaredKeyType = savedKeyType;
        }
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

    /**
     * Extract the key type from a Map type (e.g., Map<Building, Person> -> Building).
     * Used to eliminate redundant @type on Map keys when key type is known from field generics.
     */
    private Type extractMapKeyType(Type mapType) {
        if (mapType instanceof java.lang.reflect.ParameterizedType) {
            java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) mapType;
            java.lang.reflect.Type[] typeArgs = pt.getActualTypeArguments();
            if (typeArgs.length >= 1) {
                return typeArgs[0];  // Key type is at index 0
            }
        }
        return null;
    }

    public void flush() {
        if (out != null) {
            IOUtilities.flush(out);
        }
    }

    public void close() {
        if (out != null) {
            IOUtilities.close(out);
        }
    }

    private String getId(Object o) {
        if (o instanceof JsonObject) {
            long id = ((JsonObject) o).id;
            if (id > 0) {
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
     * Uses default string length limit for backward compatibility.
     *
     * @param output The Writer to write to
     * @param s      The string to be written as a JSON string value
     * @throws IOException If an I/O error occurs
     */
    public static void writeJsonUtf8String(final Writer output, String s) throws IOException {
        writeJsonUtf8String(output, s, 1000000); // Use default 1MB limit for backward compatibility
    }

    /**
     * Writes a JSON string value to the output, properly escaped according to JSON specifications.
     * Handles control characters, quotes, backslashes, and properly processes Unicode code points.
     * <p>
     * OPTIMIZED VERSION: Uses batch scanning to write runs of safe characters in one operation,
     * significantly reducing write() calls and improving performance.
     *
     * @param output          The Writer to write to
     * @param s               The string to be written as a JSON string value
     * @param maxStringLength Maximum allowed string length to prevent memory issues
     * @throws IOException If an I/O error occurs
     */
    public static void writeJsonUtf8String(final Writer output, String s, int maxStringLength) throws IOException {
        // Enhanced input validation for null safety
        if (output == null) {
            throw new JsonIoException("Output writer cannot be null");
        }

        if (s == null) {
            output.write("null");
            return;
        }

        output.write('\"');
        final int len = s.length();

        // Add safety check for extremely large strings to prevent memory issues using configurable limit
        if (len > maxStringLength) {
            throw new JsonIoException("String too large for JSON serialization: " + len + " characters. Maximum allowed: " + maxStringLength);
        }

        int start = 0;  // Start of current safe run

        for (int i = 0; i < len; ) {
            char ch = s.charAt(i);

            // Fast path: ASCII characters that don't need escaping
            if (ch < 128 && !NEEDS_ESCAPE[ch]) {
                i++;
                continue;  // Keep scanning for safe characters
            }

            // Found a character that needs special handling
            // First, write any accumulated safe characters
            if (i > start) {
                output.write(s, start, i - start);
            }

            // Now handle the special character
            int codePoint = s.codePointAt(i);

            if (codePoint < 0x20 || codePoint == 0x7F) {
                // Control characters - use efficient switch
                switch (codePoint) {
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
                        output.write(String.format("\\u%04x", codePoint));
                }
            } else if (codePoint == '"') {
                output.write("\\\"");
            } else if (codePoint == '\\') {
                output.write("\\\\");
            } else if (codePoint >= 0x80 && codePoint <= 0xFFFF) {
                // Non-ASCII characters - write directly as UTF-8
                output.write(s, i, Character.charCount(codePoint));
            } else if (codePoint > 0xFFFF) {
                // Supplementary characters (beyond BMP)
                output.write(s, i, Character.charCount(codePoint));
            }

            i += Character.charCount(codePoint);
            start = i;  // Next safe run starts after this character
        }

        // Write any remaining safe characters
        if (start < len) {
            output.write(s, start, len - start);
        }

        output.write('\"');
    }

    // ======================== Context Stack Management ========================

    /**
     * Writes a comma if the current context requires it.
     * This is called before writing array elements or object start/array start in array context.
     *
     * @throws IOException if an error occurs writing to the output stream
     */
    private void writeCommaIfNeeded() throws IOException {
        WriteContext ctx = contextStack.peek();
        if (ctx == WriteContext.OBJECT_FIELD || ctx == WriteContext.ARRAY_ELEMENT) {
            out.write(',');
        }
    }

    /**
     * Updates the context after writing a value in an array.
     * Transitions ARRAY_EMPTY to ARRAY_ELEMENT.
     */
    private void markArrayElementWritten() {
        if (!contextStack.isEmpty()) {
            WriteContext ctx = contextStack.peek();
            if (ctx == WriteContext.ARRAY_EMPTY) {
                contextStack.pop();
                contextStack.push(WriteContext.ARRAY_ELEMENT);
            }
        }
    }

    /**
     * Updates the context after writing a field in an object.
     * Transitions OBJECT_EMPTY to OBJECT_FIELD.
     */
    private void markObjectFieldWritten() {
        if (!contextStack.isEmpty()) {
            WriteContext ctx = contextStack.peek();
            if (ctx == WriteContext.OBJECT_EMPTY) {
                contextStack.pop();
                contextStack.push(WriteContext.OBJECT_FIELD);
            }
        }
    }

    // ======================== Semantic Write API Implementation ========================

    /**
     * Writes a JSON field name followed by a colon.
     * Example: writeFieldName("name") produces "name":
     * Automatically writes a comma before the field name if this is not the first field.
     */
    @Override
    public void writeFieldName(String name) throws IOException {
        WriteContext ctx = contextStack.peek();
        if (ctx == WriteContext.OBJECT_FIELD) {
            out.write(',');
        }
        out.write('\"');
        out.write(name);
        out.write("\":");
        markObjectFieldWritten();
    }

    /**
     * Writes a complete JSON string field with automatic comma handling.
     * Example: writeStringField("name", "John") produces ,"name":"John"
     * <p>
     * This method writes a LEADING comma, making it suitable for fields after the first field.
     * For the first field in an object, either omit the comma manually or use writeFieldName()
     * followed by the value.
     */
    @Override
    public void writeStringField(String name, String value) throws IOException {
        out.write(',');
        out.write('\"');
        out.write(name);
        out.write("\":");
        if (value == null) {
            out.write("null");
        } else {
            writeJsonUtf8String(out, value, writeOptions.getMaxStringLength());
        }
        markObjectFieldWritten();
    }

    /**
     * Writes a complete JSON object field with automatic serialization and comma handling.
     * Example: writeObjectField("address", addressObj) produces ,"address":{...}
     * <p>
     * This method writes a LEADING comma, making it suitable for fields after the first field.
     * For the first field in an object, either omit the comma manually or use writeFieldName()
     * followed by writeImpl().
     */
    @Override
    public void writeObjectField(String name, Object value) throws IOException {
        out.write(',');
        out.write('\"');
        out.write(name);
        out.write("\":");
        writeImpl(value, true);
        markObjectFieldWritten();
    }

    /**
     * Writes a JSON object opening brace.
     * Automatically writes a comma if needed based on context.
     */
    @Override
    public void writeStartObject() throws IOException {
        writeCommaIfNeeded();
        out.write('{');
        markArrayElementWritten();
        contextStack.push(WriteContext.OBJECT_EMPTY);
    }

    /**
     * Writes a JSON object closing brace.
     * Pops the object context from the stack.
     */
    @Override
    public void writeEndObject() throws IOException {
        out.write('}');
        if (!contextStack.isEmpty()) {
            contextStack.pop();
        }
    }

    /**
     * Writes a JSON array opening bracket.
     * Automatically writes a comma if needed based on context.
     */
    @Override
    public void writeStartArray() throws IOException {
        writeCommaIfNeeded();
        out.write('[');
        markArrayElementWritten();
        contextStack.push(WriteContext.ARRAY_EMPTY);
    }

    /**
     * Writes a JSON array closing bracket.
     * Pops the array context from the stack.
     */
    @Override
    public void writeEndArray() throws IOException {
        out.write(']');
        if (!contextStack.isEmpty()) {
            contextStack.pop();
        }
    }

    /**
     * Writes a JSON string value with proper quote escaping.
     * Example: writeValue("Hello") produces "Hello"
     * Automatically writes a comma if this is an array element and not the first element.
     */
    @Override
    public void writeValue(String value) throws IOException {
        writeCommaIfNeeded();
        if (value == null) {
            out.write("null");
        } else {
            writeJsonUtf8String(out, value, writeOptions.getMaxStringLength());
        }
        markArrayElementWritten();
    }

    /**
     * Writes a JSON value by serializing the given object.
     * Example: writeValue(myObject) produces the full JSON representation
     * Automatically writes a comma if this is an array element and not the first element.
     */
    @Override
    public void writeValue(Object value) throws IOException {
        writeCommaIfNeeded();
        writeImpl(value, true);
        markArrayElementWritten();
    }

    /**
     * Writes a complete JSON array field start with automatic comma handling.
     * Example: writeArrayFieldStart("items") produces ,"items":[
     * Manages context by marking the object field written and pushing array context.
     */
    @Override
    public void writeArrayFieldStart(String name) throws IOException {
        out.write(',');
        out.write('\"');
        out.write(name);
        out.write("\":[");
        markObjectFieldWritten();
        contextStack.push(WriteContext.ARRAY_EMPTY);
    }

    /**
     * Writes a complete JSON object field start with automatic comma handling.
     * Example: writeObjectFieldStart("config") produces ,"config":{
     * Manages context by marking the object field written and pushing object context.
     */
    @Override
    public void writeObjectFieldStart(String name) throws IOException {
        out.write(',');
        out.write('\"');
        out.write(name);
        out.write("\":{");
        markObjectFieldWritten();
        contextStack.push(WriteContext.OBJECT_EMPTY);
    }

    /**
     * Writes a complete JSON number field with automatic comma handling.
     * Example: writeNumberField("count", 42) produces ,"count":42
     */
    @Override
    public void writeNumberField(String name, Number value) throws IOException {
        out.write(',');
        out.write('\"');
        out.write(name);
        out.write("\":");
        if (value == null) {
            out.write("null");
        } else {
            out.write(value.toString());
        }
        markObjectFieldWritten();
    }

    /**
     * Writes a complete JSON boolean field with automatic comma handling.
     * Example: writeBooleanField("active", true) produces ,"active":true
     */
    @Override
    public void writeBooleanField(String name, boolean value) throws IOException {
        out.write(',');
        out.write('\"');
        out.write(name);
        out.write("\":");
        out.write(value ? "true" : "false");
        markObjectFieldWritten();
    }
}
