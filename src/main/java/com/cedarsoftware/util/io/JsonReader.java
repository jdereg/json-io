package com.cedarsoftware.util.io;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import lombok.Getter;
import lombok.Setter;

import static com.cedarsoftware.util.io.JsonObject.ITEMS;

/**
 * Read an object graph in JSON format and make it available in Java objects, or
 * in a "Map of Maps." (untyped representation).  This code handles cyclic references
 * and can deserialize any Object graph without requiring a class to be 'Serializable'
 * or have any specific methods on it.  It will handle classes with non-public constructors.
 * <br><br>
 * Usages:
 * <ul><li>
 * Call the static method: {@code JsonReader.jsonToJava(String json)}.  This will
 * return a typed Java object graph.</li>
 * <li>
 * Call the static method: {@code JsonReader.jsonToMaps(String json)}.  This will
 * return an untyped object representation of the JSON String as a Map of Maps, where
 * the fields are the Map keys, and the field values are the associated Map's values.  You can
 * call the JsonWriter.objectToJson() method with the returned Map, and it will serialize
 * the Graph into the equivalent JSON stream from which it was read.
 * <li>
 * Instantiate the JsonReader with an InputStream: {@code JsonReader(InputStream in)} and then call
 * {@code readObject()}.  Cast the return value of readObject() to the Java class that was the root of
 * the graph.
 * </li>
 * <li>
 * Instantiate the JsonReader with an InputStream: {@code JsonReader(InputStream in, true)} and then call
 * {@code readObject()}.  The return value will be a Map of Maps.
 * </li></ul><br>
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
public class JsonReader implements Closeable, ReaderContext
{
    /** Once we've fully moved to read options, these static finals can be deleted **/
    /** If set, this maps class ==> CustomReader */
    public static final String CUSTOM_READER_MAP = "CUSTOM_READERS";
    /** If set, this indicates that no custom reader should be used for the specified class ==> CustomReader */
    public static final String NOT_CUSTOM_READER_MAP = "NOT_CUSTOM_READERS";
    /** If set, the read-in JSON will be turned into a Map of Maps (JsonObject) representation */
    public static final String USE_MAPS = "USE_MAPS";
    /** What to do when an object is found and 'type' cannot be determined. */
    public static final String UNKNOWN_OBJECT = "UNKNOWN_OBJECT";
    /** Will fail JSON parsing if 'type' class defined but is not on classpath. */
    public static final String FAIL_ON_UNKNOWN_TYPE = "FAIL_ON_UNKNOWN_TYPE";
    /** Pointer to 'this' (automatically placed in the Map) */
    public static final String JSON_READER = "JSON_READER";
    /** Pointer to the current ObjectResolver (automatically placed in the Map) */
    public static final String OBJECT_RESOLVER = "OBJECT_RESOLVER";
    /** If set, this map will be used when reading @type values - allows shorthand abbreviations type names */
    public static final String TYPE_NAME_MAP = "TYPE_NAME_MAP";
    /** If set, this object will be called when a field is present in the JSON but missing from the corresponding class */
    public static final String MISSING_FIELD_HANDLER = "MISSING_FIELD_HANDLER";
    /** If set, use the specified ClassLoader */
    public static final String CLASSLOADER = "CLASSLOADER";
    /** This map is the reverse of the TYPE_NAME_MAP (value ==> key) */
    static final String TYPE_NAME_MAP_REVERSE = "TYPE_NAME_MAP_REVERSE";
    /**
     * If set, this map will contain Factory classes for creating hard to instantiate objects.
     */
    public static final String FACTORIES = "FACTORIES";
    static final int DEFAULT_MAX_PARSE_DEPTH = 1000;

    private final FastReader input;
    // Note:  this is not thread local.
    /**
     * _args is using ThreadLocal so that static inner classes can have access to them
     * -- GETTER --
     *
     * @return The arguments used to configure the JsonReader.  These are thread local.
     */
    @Getter
    @Deprecated
    private final Map<String, Object> args = new HashMap<>();

    /**
     * -- GETTER --
     *
     * @return boolean the allowNanAndInfinity setting
     */
    @Setter
    @Getter
    private static volatile boolean allowNanAndInfinity = false;

    @Getter
    private final Resolver resolver;

    @Getter
    private final ReadOptions readOptions;

    private final JsonParser parser;

    /**
     * Subclass this interface and create a class that will return a new instance of the
     * passed in Class (c).  Your factory subclass will be called when json-io encounters an
     * instance of (c) which you register with JsonReader.assignInstantiator().
     * Use the passed in JsonObject o which is a JsonObject to source values for the construction
     * of your class.
     */
    public interface ClassFactory
    {
        /**
         * Implement this method to return a new instance of the passed in Class.  Use the passed
         * in JsonObject to supply values to the construction of the object.
         * @param c Class of the object that needs to be created
         * @param jObj JsonObject (if primitive type do jObj.getPrimitiveValue();
         * @param context ReaderContext for creating objects an getting read options
         * @return a new instance of C.  If you completely fill the new instance using
         * the value(s) from object, and no further work is needed for construction, then
         * override the isObjectFinal() method below and return true.
         */
        default Object newInstance(Class<?> c, JsonObject jObj, ReaderContext context) {
            return this.newInstance(c, jObj);
        }

        /**
         * Implement this method to return a new instance of the passed in Class.  Use the passed
         * in JsonObject to supply values to the construction of the object.
         *
         * @param c       Class of the object that needs to be created
         * @param jObj    JsonObject (if primitive type do jObj.getPrimitiveValue();
         * @return a new instance of C.  If you completely fill the new instance using
         * the value(s) from object, and no further work is needed for construction, then
         * override the isObjectFinal() method below and return true.
         */
        default Object newInstance(Class<?> c, JsonObject jObj)
        {
            return MetaUtils.newInstance(c, null);  // can add constructor arg values (pull from jObj)
        }

        /**
         * @return true if this object is instantiated and completely filled using the contents
         * from the Object [a JsonObject or value].  In this case, no further processing
         * will be performed on the instance.  If the object has sub-objects (complex fields),
         * then return false so that the JsonReader will continue on filling out the remaining
         * portion of the object.
         */
        default boolean isObjectFinal() {
            return false;
        }

        default void gatherRemainingValues(ReaderContext context, JsonObject jObj, List<Object> arguments, Set<String> excludedFields) {
            Convention.throwIfNull(jObj, "JsonObject cannot be null");

            for (Map.Entry<Object, Object> entry : jObj.entrySet()) {
                if (!excludedFields.contains(entry.getKey().toString())) {
                    Object o = entry.getValue();

                    if (o instanceof JsonObject) {
                        JsonObject sub = (JsonObject) o;
                        Object value = context.reentrantConvertParsedMapsToJava(sub, MetaUtils.classForName(sub.getType(), context.getReadOptions().getClassLoader()));

                        if (value != null) {
                            if (sub.getType() != null || sub.getTargetClass() != null) {
                                arguments.add(value);
                            }
                        }
                    } else if (o != null) {
                        arguments.add(o);
                    }
                }
            }
        }
    }

    /**
     * Used to react to fields missing when reading an object. This method will be called after all deserialization has
     * occurred to allow all ref to be resolved.
     * <p>
     * Used in conjunction with {@link JsonReader#MISSING_FIELD_HANDLER}.
     */
    public interface MissingFieldHandler
    {
        /**
         * Notify that a field is missing. <br>
         * Warning : not every type can be deserialized upon missing fields. Arrays and Object type that do not have
         * serialized @type definition will be ignored.
         *
         * @param object the object that contains the missing field
         * @param fieldName name of the field to be replaced
         * @param value current value of the field
         */
        void fieldMissing(Object object, String fieldName, Object value);
    }

    /**
     * Common ancestor for JsonClassReader
     */
    @Deprecated
    public interface JsonClassReaderBase  {
        /**
         * @param jOb Object being read.  Could be a fundamental JSON type (String, long, boolean, double, null, or JsonObject)
         * @param stack Deque of objects that have been read (Map of Maps view).
         * @param args Map of argument settings that were passed to JsonReader when instantiated.
         * @return Java Object you wish to convert the passed in jOb into.
         */
        @Deprecated
        default Object read(Object jOb, Deque<JsonObject> stack, Map<String, Object> args) {
            return null;
        }
    }

    /**
     * Implement this interface to add a custom JSON reader.
     */
    public interface JsonClassReader extends JsonClassReaderBase
    {
        /**
         * @param jOb         Object being read.  Could be a fundamental JSON type (String, long, boolean, double, null, or JsonObject)
         * @param stack       Deque of objects that have been read (Map of Maps view).
         * @param context  reader context to provide assistance reading the object
         * @return Java Object you wish to convert the passed in jOb into.
         */
        default Object read(Object jOb, Deque<JsonObject> stack, ReaderContext context) {
            // The toMap is expensive, but only if you do not override this default method....and the other
            // methods are soon to be deprecated.
            return this.read(jOb, stack);
        }

        /**
         * @param jOb Object being read.  Could be a fundamental JSON type (String, long, boolean, double, null, or JsonObject)
         * @param stack Deque of objects that have been read (Map of Maps view).
         * @return Object you wish to convert the jOb value into.
         */
        default Object read(Object jOb, Deque<JsonObject> stack) {
            return null;
        }
    }


    /**
     * Use to create new instances of collection interfaces (needed for empty collections)
     */
    public static class CollectionFactory implements ClassFactory {
        @Override
        public Object newInstance(Class<?> c, JsonObject jObj, ReaderContext context)
        {
            if (List.class.isAssignableFrom(c))
            {
                return new ArrayList<>();
            }
            else if (SortedSet.class.isAssignableFrom(c))
            {
                return new TreeSet<>();
            }
            else if (Set.class.isAssignableFrom(c))
            {
                return new LinkedHashSet<>();
            }
            else if (Collection.class.isAssignableFrom(c))
            {
                return new ArrayList<>();
            }
            throw new JsonIoException("CollectionFactory handed Class for which it was not expecting: " + c.getName());
        }
    }

    /**
     * Use to create new instances of Map interfaces (needed for empty Maps).  Used
     * internally to handle Map, SortedMap when they are within parameterized types.
     */
    public static class MapFactory implements ClassFactory
    {
        /**
         * @param c       Map interface that was requested for instantiation.
         * @param jObj JsonObject
         * @param context ReaderContext
         * @return a concrete Map type.
         */
        @Override
        public Object newInstance(Class<?> c, JsonObject jObj, ReaderContext context)
        {
            if (SortedMap.class.isAssignableFrom(c))
            {
                return new TreeMap<>();
            }
            else if (Map.class.isAssignableFrom(c))
            {
                return new LinkedHashMap<>();
            }
            throw new JsonIoException("MapFactory handed Class for which it was not expecting: " + c.getName());
        }
    }

    /**
     * Deprecated - use ReadOptionsBuilder.assignInstantiator(String, ClassFactory)
     *
     * @param className Class name to assign an ClassFactory to
     * @param factory ClassFactory that will create 'c' instances
     */
    @Deprecated
    public static void assignInstantiator(String className, ClassFactory factory)
    {
        ReadOptionsBuilder.assignInstantiator(className, factory);
    }

    /**
     * Deprecated - use ReadOptionsBuilder.assignInstantiator(Class, ClassFactory)
     *
     * @param c Class to assign on ClassFactory
     * @param factory ClassFactory that wil create instance of that class.
     */
    @Deprecated
    public static void assignInstantiator(Class<?> c, ClassFactory factory)
    {
        ReadOptionsBuilder.assignInstantiator(c, factory);
    }

    /**
     * Call this method to add a custom JSON reader to json-io.  It will
     * associate the Class 'c' to the reader you pass in.  The readers are
     * found with isAssignableFrom().  If this is too broad, causing too
     * many classes to be associated to the custom reader, you can indicate
     * that json-io should not use a custom reader for a particular class,
     * by calling the addNotCustomReader() method.
     * @param c Class to assign a custom JSON reader to
     * @param reader The JsonClassReader which will read the custom JSON format of 'c'
     * @deprecated use ReadOptionsBuilder.withCustomReader() to create any additional readers you'll need.
     */
    @Deprecated
    public void addReader(Class<?> c, JsonClassReader reader)
    {
        this.readOptions.addReader(c, reader);
    }

    /**
     * Force json-io to use it's internal generic approach to writing the
     * passed in class, even if a Custom JSON reader is specified for its
     * parent class.
     * @param c Class to which to force no custom JSON reading to occur.
     * @deprecated use ReadOptionsBuilder.withNonCustomizableClass()
     */
    @Deprecated
    public void addNotCustomReader(Class<?> c)
    {
        this.readOptions.addNonCustomizableClass(c);
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     *
     * @param input   Input stream of UTF-8 json string data
     * @param options Read options
     * @return Java object graph matching JSON input
     * @param root Class that indicates what the root class of the JSON is that is being written out.  You can
     *             specify null, however, it will not have the Class's field information to make inferences about
     *             the root object, so you may have to parse into Maps, rather than specific DTO classes.
     */
    @SuppressWarnings("unchecked")
    public static <T> T toObjects(InputStream input, ReadOptions options, Class<?> root) {
        try (JsonReader jr = new JsonReader(input, options)) {
            return (T) jr.readObject(root);
        }
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     *
     * @param jsonString String JSON input
     * @param options    Read options
     * @param root Class that indicates what the root class of the JSON is that is being written out.  You can
     *             specify null, however, it will not have the Class's field information to make inferences about
     *             the root object, so you may have to parse into Maps, rather than specific DTO classes.
     * @return Java object graph matching JSON input
     */
    public static <T> T toObjects(String jsonString, ReadOptions options, Class<?> root) {
        if (jsonString == null) {
            return null;
        }
        return toObjects(new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8)), options, root);
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     * Uses the default read options
     *
     * @param json String JSON input
     * @param root Class that indicates what the root class of the JSON is that is being written out.  You can
     *             specify null, however, it will not have the Class's field information to make inferences about
     *             the root object, so you may have to parse into Maps, rather than specific DTO classes.
     * @return Java object graph matching JSON input
     */
    public static <T> T toObjects(String json, Class<?> root) {
        return toObjects(json, new ReadOptionsBuilder().build(), root);
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     * Uses the default read options and null for Class root.
     * @param json String JSON input
     * @return Java object graph matching JSON input
     */
    public static <T> T toObjects(String json) {
        return toObjects(json, new ReadOptionsBuilder().build(), null);
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     *
     * @param json String JSON input
     * @return Java object graph matching JSON input
     */
    @Deprecated
    public static <T> T jsonToJava(String json)
    {
        return toObjects(json, null);
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     *
     * @param json String JSON input
     * @param optionalArgs Map of optional parameters to control parsing.  See readme file for details.
     * @param maxDepth Maximum parsing depth.
     * @return Java object graph matching JSON input
     */
    @Deprecated
    public static Object jsonToJava(String json, Map<String, Object> optionalArgs, int maxDepth)
    {
        if (json == null) {
            return null;
        }
        return jsonToJava(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), optionalArgs, maxDepth);
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     *
     * @param json String JSON input
     * @param optionalArgs Map of optional parameters to control parsing.  See readme file for details.
     * @return Java object graph matching JSON input
     */
    @Deprecated
    public static <T> T jsonToJava(String json, Map<String, Object> optionalArgs)
    {
        return (T) jsonToJava(json, optionalArgs, DEFAULT_MAX_PARSE_DEPTH);
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     *
     * @param inputStream InputStream containing JSON input
     * @param optionalArgs Map of optional parameters to control parsing.  See readme file for details.
     * @param maxDepth Maximum parsing depth.
     * @return Java object graph matching JSON input
     */
    @Deprecated
    public static <T> T jsonToJava(InputStream inputStream, Map<String, Object> optionalArgs, int maxDepth)
    {
        if (optionalArgs == null)
        {
            optionalArgs = new HashMap<>();
            optionalArgs.put(USE_MAPS, false);
        }
        if (!optionalArgs.containsKey(USE_MAPS))
        {
            optionalArgs.put(USE_MAPS, false);
        }

        try (JsonReader jr = new JsonReader(inputStream, optionalArgs, maxDepth)) {
            return (T) jr.readObject(Object.class);
        }
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     *
     * @param inputStream InputStream containing JSON input
     * @param optionalArgs Map of optional parameters to control parsing.  See readme file for details.
     * @return Java object graph matching JSON input
     */
    @Deprecated
    public static <T> T jsonToJava(InputStream inputStream, Map<String, Object> optionalArgs)
    {
        return jsonToJava(inputStream, optionalArgs, DEFAULT_MAX_PARSE_DEPTH);
    }

    /**
     * Deprecated = use toMaps(String, ReadOptions);
     *
     * Map args = ["USE_MAPS": true]
     * Use JsonReader.jsonToJava(String json, args)
     * Note that the return type will match the JSON type (array, object, string, long, boolean, or null).
     * No longer recommended: Use jsonToJava with USE_MAPS:true
     * @param json String of JSON content
     * @param maxDepth Maximum parsing depth.
     * @return Map representing JSON content.  Each object is represented by a Map.
     */
    @Deprecated
    public static Map jsonToMaps(String json, int maxDepth) {
        return jsonToMaps(json, new HashMap<>(), maxDepth);
    }

    /**
     * Deprecated = use toMaps(String, ReadOptions);
     *
     * Map args = ["USE_MAPS": true]
     * Use JsonReader.jsonToJava(String json, args)
     * Note that the return type will match the JSON type (array, object, string, long, boolean, or null).
     * No longer recommended: Use jsonToJava with USE_MAPS:true
     * @param json String of JSON content
     * @return Map representing JSON content.  Each object is represented by a Map.
     */
    @Deprecated
    public static Map jsonToMaps(String json) {
        return jsonToMaps(json, new HashMap<>(), DEFAULT_MAX_PARSE_DEPTH);
    }

    /**
     * Deprecated = use toMaps(String, ReadOptions);
     *
     * Map args = ["USE_MAPS": true]
     * Use JsonReader.jsonToJava(String json, args)
     * Note that the return type will match the JSON type (array, object, string, long, boolean, or null).
     * No longer recommended: Use jsonToJava with USE_MAPS:true
     * @param json String of JSON content
     * @param optionalArgs Map of optional arguments to control customization.  See readme file for
     * details on these options.
     * @param maxDepth Maximum parsing depth.
     * @return Map where each Map represents an object in the JSON input.
     */
    @Deprecated
    public static Map jsonToMaps(String json, Map<String, Object> optionalArgs, int maxDepth)
    {
        if (json == null) {
            return null;
        }
        return jsonToMaps(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), optionalArgs, maxDepth);
    }

    /**
     * Deprecated = use toMaps(String, ReadOptions);
     *
     * Map args = ["USE_MAPS": true]
     * Use JsonReader.jsonToJava(String json, args)
     * Note that the return type will match the JSON type (array, object, string, long, boolean, or null).
     * No longer recommended: Use jsonToJava with USE_MAPS:true
     * @param json String of JSON content
     * @param optionalArgs Map of optional arguments to control customization.  See readme file for
     * details on these options.
     * @return Map where each Map represents an object in the JSON input.
     */
    @Deprecated
    public static Map jsonToMaps(String json, Map<String, Object> optionalArgs)
    {
        return jsonToMaps(json, optionalArgs, DEFAULT_MAX_PARSE_DEPTH);
    }

    /**
     * Deprecated = use toMaps(InputStream, ReadOptions);
     *
     * Map args = ["USE_MAPS": true]
     * Use JsonReader.jsonToJava(inputStream, args)
     * Note that the return type will match the JSON type (array, object, string, long, boolean, or null).
     * No longer recommended: Use jsonToJava with USE_MAPS:true
     * @param inputStream containing JSON content
     * @param optionalArgs Map of optional arguments to control customization.  See readme file for
     * details on these options.
     * @param maxDepth Maximum parsing depth.
     * @return Map containing the content from the JSON input.  Each Map represents an object from the input.
     */
    @Deprecated
    public static Map jsonToMaps(InputStream inputStream, Map<String, Object> optionalArgs, int maxDepth)
    {
        if (optionalArgs == null)
        {
            optionalArgs = new HashMap<>();
        }
        optionalArgs.put(USE_MAPS, true);

        try (JsonReader jr = new JsonReader(inputStream, optionalArgs, maxDepth)) {
            return adjustOutputMap(jr.readObject(Map.class));
        }
    }

    /**
     * Deprecated = use toMaps(InputStream, ReadOptions);
     *
     * Map args = ["USE_MAPS": true]
     * Use JsonReader.jsonToJava(inputStream, args)
     * Note that the return type will match the JSON type (array, object, string, long, boolean, or null).
     * No longer recommended: Use jsonToJava with USE_MAPS:true
     * @param inputStream containing JSON content
     * @param optionalArgs Map of optional arguments to control customization.  See readme file for
     * details on these options.
     * @return Map containing the content from the JSON input.  Each Map represents an object from the input.
     */
    @Deprecated
    public static Map jsonToMaps(InputStream inputStream, Map<String, Object> optionalArgs)
    {
        return jsonToMaps(inputStream, optionalArgs, DEFAULT_MAX_PARSE_DEPTH);
    }

    /**
     * Note that the return type will match one of these JSON types (array, Map, string, long, boolean, or null).
     *
     * @param json json string
     * @return Map containing the content from the JSON input.  Each Map represents an object from the input.
     */
    public static <T> T toMaps(String json) {
        return toMaps(json, new ReadOptionsBuilder().build());
    }


    /**
     * Note that the return type will match one of these JSON types (array, Map, string, long, boolean, or null).
     *
     * @param json        json string
     * @param readOptions options to use when reading
     * @return Map containing the content from the JSON input.  Each Map represents an object from the input.
     */
    public static <T> T toMaps(String json, ReadOptions readOptions) {
        if (json == null) {
            return null;
        }
        return toMaps(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), readOptions);
    }

    /**
     * Note that the return type will match one of these JSON types (array, Map, string, long, boolean, or null).
     *
     * @param inputStream bytes representing UTF-8 string
     * @param readOptions options to use when reading
     * @return Map containing the content from the JSON input.  Each Map represents an object from the input.
     */
    public static <T> T toMaps(InputStream inputStream, ReadOptions readOptions) {
        Convention.throwIfNull(inputStream, "inputStream cannot be null");
        Convention.throwIfNull(readOptions, "readOptions cannot be null");

        try (JsonReader jr = new JsonReader(inputStream, readOptions.ensureUsingMaps(), null)) {
            return (T) jr.readObject(Map.class);
        }
    }

    @Deprecated
    private static Map adjustOutputMap(Object ret)
    {
        if (ret instanceof Map)
        {
            return (Map) ret;
        }

        if (ret != null && ret.getClass().isArray())
        {
            JsonObject retMap = new JsonObject();
            retMap.put(ITEMS, ret);
            return retMap;
        }
        JsonObject retMap = new JsonObject();
        retMap.put(ITEMS, new Object[]{ret});
        return retMap;
    }

    /**
     * Deprecated - use JsonReader(ReadOptions)
     *
     * @param maxDepth - maximum recursion depth
     */
    @Deprecated
    public JsonReader(int maxDepth) {
        args.put(USE_MAPS, false);
        args.put(CLASSLOADER, JsonReader.class.getClassLoader());
        args.put(JSON_READER, this);

        this.readOptions = ReadOptionsBuilder.fromMap(args).withMaxDepth(maxDepth).build();
        this.input = null;
        this.parser = null;


        ReferenceTracker references = new DefaultReferenceTracker();
        this.resolver = readOptions.isUsingMaps() ? new MapResolver(readOptions, references) : new ObjectResolver(readOptions, references);
        args.put(JsonReader.OBJECT_RESOLVER, this.resolver);
    }

    /**
     * Deprecated - use JsonReader(ReadOptions)
     */
    @Deprecated
    public JsonReader()
    {
        this(DEFAULT_MAX_PARSE_DEPTH);
    }


    /**
     * Deprecated - use JsonReader(InputStream, ReadOptions)
     *
     * @param inputStream - input stream to supply json
     * @param maxDepth    - maximum recursion depth
     */
    @Deprecated
    public JsonReader(InputStream inputStream, int maxDepth) {
        this(inputStream, false, maxDepth);
    }

    /**
     * Deprecated - use JsonReader(InputStream, ReadOptions)
     *
     * @param inputStream input stream
     */
    @Deprecated
    public JsonReader(InputStream inputStream) {
        this(inputStream, DEFAULT_MAX_PARSE_DEPTH);
    }

    /**
     * Deprecated - use JsonReader(ReadOptions)
     * Use this constructor if you already have a JsonObject graph and want to parse it into
     * Java objects by calling jsonReader.jsonObjectsToJava(rootJsonObject) after constructing
     * the JsonReader.
     * @param optionalArgs Map of optional arguments for the JsonReader.
     * @param maxDepth Maximum parsing depth.
     */
    @Deprecated
    public JsonReader(Map<String, Object> optionalArgs, int maxDepth)
    {
        this(new ByteArrayInputStream(new byte[]{}), optionalArgs, maxDepth);
    }

    /**
     * Deprecated - use JsonReader(ReadOptions)
     * Use this constructor if you already have a JsonObject graph and want to parse it into
     * Java objects by calling jsonReader.jsonObjectsToJava(rootJsonObject) after constructing
     * the JsonReader.
     * @param optionalArgs Map of optional arguments for the JsonReader.
     */
    @Deprecated
    public JsonReader(Map<String, Object> optionalArgs)
    {
        this(optionalArgs, DEFAULT_MAX_PARSE_DEPTH);
    }

    // This method is needed to get around the fact that 'this()' has to be the first method of a constructor.
    @Deprecated
    static Map makeArgMap(Map<String, Object> args, boolean useMaps)
    {
        args.put(USE_MAPS, useMaps);
        return args;
    }

    protected FastReader getReader(InputStream inputStream)
    {
        return new FastReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8), 8192, 10);
    }

    /**
     * Deprecated - use JsonReader(InputStream, ReadOptions)
     * @param inputStream - json String
     * @param useMaps     - return data in map of maps
     * @param maxDepth    - maximum recursion depth
     */
    @Deprecated
    public JsonReader(InputStream inputStream, boolean useMaps, int maxDepth) {
        this(inputStream, makeArgMap(new HashMap<>(), useMaps), maxDepth);
    }

    /**
     * Deprecated - use JsonReader(InputStream, ReadOptions)
     * @param inputStream - json String
     * @param useMaps     - return data in map of maps
     */
    @Deprecated
    public JsonReader(InputStream inputStream, boolean useMaps) {
        this(inputStream, useMaps, DEFAULT_MAX_PARSE_DEPTH);
    }

    /**
     * Deprecated - use JsonReader(InputStream, ReadOptions)
     * @param inputStream  - json String
     * @param optionalArgs - old way of passing reader arguments
     * @param maxDepth     - max recursion depth
     */
    @Deprecated
    public JsonReader(InputStream inputStream, Map<String, Object> optionalArgs, int maxDepth) {
        args.putAll(optionalArgs == null ? new HashMap<>() : optionalArgs);
        args.put(JSON_READER, this);

        this.readOptions = ReadOptionsBuilder.fromMap(optionalArgs).withMaxDepth(maxDepth).build();

        ReferenceTracker references = new DefaultReferenceTracker();
        this.resolver = readOptions.isUsingMaps() ? new MapResolver(readOptions, references) : new ObjectResolver(readOptions, references);
        this.input = getReader(inputStream);
        this.parser = new JsonParser(input, this.readOptions, references);

        args.put(JsonReader.OBJECT_RESOLVER, this.resolver);
    }

    /**
     * Deprecated - use JsonReader(InputStream, ReadOptions)
     * @param inputStream  - json String
     * @param optionalArgs - old way of passing reader arguments
     */
    @Deprecated
    public JsonReader(InputStream inputStream, Map<String, Object> optionalArgs) {
        this(inputStream, optionalArgs, DEFAULT_MAX_PARSE_DEPTH);
    }

    /**
     * Deprecated - use JsonReader(String, ReadOptions)
     * @param json         - json String
     * @param optionalArgs - old way of passing reader arguments
     * @param maxDepth     - max recursion depth
     */
    @Deprecated
    public JsonReader(String json, Map<String, Object> optionalArgs, int maxDepth) {
        this(json.getBytes(StandardCharsets.UTF_8), optionalArgs, maxDepth);
    }

    /**
     * Deprecated - use JsonReader(String, ReadOptions)
     * @param json         - json String
     * @param optionalArgs - old way of passing reader arguments
     */
    @Deprecated
    public JsonReader(String json, Map<String, Object> optionalArgs) {
        this(json, optionalArgs, DEFAULT_MAX_PARSE_DEPTH);
    }

    /**
     * Deprecated - use JsonReader(byte[], ReadOptions)
     * @param inp          - byte array with json data in it.
     * @param optionalArgs - deprecated options.
     * @param maxDepth     - max recursion depth
     */
    @Deprecated
    public JsonReader(byte[] inp, Map<String, Object> optionalArgs, int maxDepth) {
        this(new ByteArrayInputStream(inp), optionalArgs, maxDepth);
    }

    /**
     * Deprecated - use JsonReader(byte[], ReadOptions)
     * @param inp          - byte array with json data in it.
     * @param optionalArgs - deprecated options.
     */
    @Deprecated
    public JsonReader(byte[] inp, Map<String, Object> optionalArgs)
    {
        this(inp, optionalArgs, DEFAULT_MAX_PARSE_DEPTH);
    }


    /**
     * Creates a json reader using custom read options
     * @param json        json String
     * @param readOptions Read Options to turn on/off various feature options, or supply additional ClassFactory data,
     *                    etc. If null, readOptions will use all defaults.
     */
    public JsonReader(String json, ReadOptions readOptions) {
        this(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), readOptions);
    }


    /**
     * Creates a json reader using custom read options
     * @param input         InputStream of utf-encoded json
     * @param readOptions Read Options to turn on/off various feature options, or supply additional ClassFactory data,
     *                    etc. If null, readOptions will use all defaults.
     */
    public JsonReader(InputStream input, ReadOptions readOptions) {
        this(input, readOptions, new DefaultReferenceTracker());
    }

    public JsonReader(InputStream input, ReadOptions readOptions, ReferenceTracker references) {
        this.readOptions = readOptions == null ? new ReadOptionsBuilder().build() : readOptions;
        this.args.putAll(readOptions.toMap());  // TODO: Do we still need to support the older args?
        this.args.put(JSON_READER, this);
        this.input = getReader(input);
        this.resolver = useMaps() ? new MapResolver(readOptions, references) : new ObjectResolver(readOptions, references);
        this.parser = new JsonParser(this.input, this.readOptions, references);
    }

    /**
     * Use this constructor if you already have a JsonObject graph and want to parse it into
     * Java objects by calling jsonReader.jsonObjectsToJava(rootJsonObject) after constructing
     * the JsonReader.
     *
     * @param readOptions Read Options to turn on/off various feature options, or supply additional ClassFactory data,
     *                    etc. If null, readOptions will use all defaults.
     */
    public JsonReader(ReadOptions readOptions) {
        this(new ByteArrayInputStream(new byte[]{}), readOptions);
    }

    public <T> T readObject(Class<T> root)
    {
        JsonObject rootObj = new JsonObject();
        T o;
        try
        {
            o = (T) parser.readValue(rootObj, true);
        }
        catch (JsonIoException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new JsonIoException("error parsing JSON value", e);
        }

        T graph;
        if (o instanceof Object[])
        {
            rootObj.setType(Object[].class.getName());
            rootObj.setTarget(o);
            rootObj.put(ITEMS, o);
            graph = convertParsedMapsToJava(rootObj, root);
        }
        else
        {
            graph = o instanceof JsonObject ? convertParsedMapsToJava((JsonObject) o, root) : o;
        }

        // Allow a complete 'Map' return (Javascript style)
        if (useMaps())
        {
            return o;
        }
        return graph;
    }

    /**
     * Read JSON input from the stream that was set up in the constructor, turning it into
     * Java Maps (JsonObject's).  Then, if requested, the JsonObjects can be converted
     * into Java instances.
     *
     * @return Java Object graph constructed from InputStream supplying
     *         JSON serialized content.
     */
    @Deprecated
    public Object readObject() {
        return readObject(Object.class);
    }

    /**
     * Convert a root JsonObject that represents parsed JSON, into
     * an actual Java object.
     * @param root JsonObject instance that was the root object from the
     * JSON input that was parsed in an earlier call to JsonReader.
     * @return a typed Java instance that was serialized into JSON.
     */
    @Deprecated
    public Object jsonObjectsToJava(JsonObject root)
    {
        getArgs().put(USE_MAPS, false);
        this.readOptions.ensureUsingObjects();
        return convertParsedMapsToJava(root, null);
    }

    protected boolean useMaps()
    {
        return this.readOptions.isUsingMaps();
    }

    /**
     * @return ClassLoader to be used by Custom Writers
     */
    public ClassLoader getClassLoader()
    {
        return this.readOptions.getClassLoader();
    }

    @Override
    public ReferenceTracker getReferences() {
        return this.resolver.getReferences();
    }

    /**
     * This method converts a rootObj Map, (which contains nested Maps
     * and so forth representing a Java Object graph), to a Java
     * object instance.  The rootObj map came from using the JsonReader
     * to parse a JSON graph (using the API that puts the graph
     * into Maps, not the typed representation).
     * @param rootObj JsonObject instance that was the rootObj object from the
     * @param root When you know the type you will be returning.  Can be null (effectively Map.class)
     * JSON input that was parsed in an earlier call to JsonReader.
     * @return a typed Java instance that was serialized into JSON.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T reentrantConvertParsedMapsToJava(JsonObject rootObj, Class<T> root)
    {
        return this.resolver.reentrantConvertParsedMapsToJava(rootObj, root);
    }

    /**
     * Walk the Java object fields and copy them from the JSON object to the Java object, performing
     * any necessary conversions on primitives, or deep traversals for field assignments to other objects,
     * arrays, Collections, or Maps.
     * @param stack   Stack (Deque) used for graph traversal.
     * @param jsonObj a Map-of-Map representation of the current object being examined (containing all fields).
     */
    @SuppressWarnings("unchecked")
    @Override
    public void traverseFields(Deque<JsonObject> stack, JsonObject jsonObj) {
        this.resolver.traverseFields(stack, jsonObj);
    }


    /**
     * This method converts a rootObj Map, (which contains nested Maps
     * and so forth representing a Java Object graph), to a Java
     * object instance.  The rootObj map came from using the JsonReader
     * to parse a JSON graph (using the API that puts the graph
     * into Maps, not the typed representation).
     *
     * @param rootObj JsonObject instance that was the rootObj object from the
     *             JSON input that was parsed in an earlier call to JsonReader.
     * @return a typed Java instance that was serialized into JSON.
     */
    @SuppressWarnings("unchecked")
    protected <T> T convertParsedMapsToJava(JsonObject rootObj, Class<T> root) {
        try {
            root = root == null ? (Class<T>)Object.class : root;
            return reentrantConvertParsedMapsToJava(rootObj, root);
        } catch (Exception e) {
            MetaUtils.safelyIgnoreException(this::close);
            if (e instanceof JsonIoException) {
                throw (JsonIoException) e;
            }
            throw new JsonIoException(getErrorMessage(e.getMessage()), e);
        } finally {
            //  In case we decide to only go with Hinted Types (passing class in here),
            //  we'll need to rename and make sure that this cleanup only happens
            //  from the outer (initial) JsonReader and not from class factories.
            resolver.cleanup();
        }
    }

    @Override
    public void close()
    {
        try
        {
            if (input != null)
            {
                input.close();
            }
        }
        catch (Exception e)
        {
            throw new JsonIoException("Unable to close input", e);
        }
    }

    private String getErrorMessage(String msg)
    {
        if (input != null)
        {
            return msg + "\nLast read: " + input.getLastSnippet() + "\nline: " + input.getLine() + ", col: " + input.getCol();
        }
        return msg;
    }

    /**
     * Implementation of ReferenceTracker
     */
    private static class DefaultReferenceTracker implements ReferenceTracker {

        final Map<Long, JsonObject> references = new HashMap<>();

        public JsonObject put(Long l, JsonObject o) {
            return this.references.put(l, o);
        }

        public void clear() {
            this.references.clear();
        }

        public int size() {
            return this.references.size();
        }

        public JsonObject get(JsonObject jObj) {
            if (!jObj.isReference()) {
                return jObj;
            }

            return get(jObj.getReferenceId());
        }

        public JsonObject get(Long id) {
            JsonObject target = references.get(id);
            if (target == null) {
                throw new JsonIoException("Forward reference @ref: " + id + ", but no object defined (@id) with that value");
            }

            while (target.isReference()) {
                id = target.getReferenceId();
                target = references.get(id);
                if (target == null) {
                    throw new JsonIoException("Forward reference @ref: " + id + ", but no object defined (@id) with that value");
                }
            }

            return target;
        }
    }
}
