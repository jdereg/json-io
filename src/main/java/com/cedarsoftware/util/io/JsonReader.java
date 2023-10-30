package com.cedarsoftware.util.io;

import com.cedarsoftware.util.io.factory.LocalDateFactory;
import com.cedarsoftware.util.io.factory.LocalDateTimeFactory;
import com.cedarsoftware.util.io.factory.LocalTimeFactory;
import com.cedarsoftware.util.io.factory.TimeZoneFactory;
import com.cedarsoftware.util.io.factory.ZonedDateTimeFactory;
import lombok.Getter;
import lombok.Setter;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static com.cedarsoftware.util.io.JsonObject.ITEMS;

/**
 * Read an object graph in JSON format and make it available in Java objects, or
 * in a "Map of Maps." (untyped representation).  This code handles cyclic references
 * and can deserialize any Object graph without requiring a class to be 'Serializeable'
 * or have any specific methods on it.  It will handle classes with non public constructors.
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
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class JsonReader implements Closeable
{
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
    /** If set, this map will be used when reading @type values - allows short-hand abbreviations type names */
    public static final String TYPE_NAME_MAP = "TYPE_NAME_MAP";
    /** If set, this object will be called when a field is present in the JSON but missing from the corresponding class */
    public static final String MISSING_FIELD_HANDLER = "MISSING_FIELD_HANDLER";
    /** If set, use the specified ClassLoader */
    public static final String CLASSLOADER = "CLASSLOADER";
    /** This map is the reverse of the TYPE_NAME_MAP (value ==> key) */
    static final String TYPE_NAME_MAP_REVERSE = "TYPE_NAME_MAP_REVERSE";
    /** Default maximum parsing depth */

    /**
     * If set, this map will contain Factory classes for creating hard to instantiate objects.
     */
    public static final String FACTORIES = "FACTORIES";
    static final int DEFAULT_MAX_PARSE_DEPTH = 1000;

    protected static Map<Class<?>, JsonClassReader> BASE_READERS;
    protected final Map<Class<?>, JsonClassReader> readers = new HashMap<>(BASE_READERS);
    protected final Map<String, ClassFactory> classFactories = new HashMap<>(BASE_CLASS_FACTORIES);


    @Setter
    @Getter(lombok.AccessLevel.PACKAGE)
    protected MissingFieldHandler missingFieldHandler;

    protected final Set<Class<?>> notCustom = new HashSet<>();
    protected static final Map<String, ClassFactory> BASE_CLASS_FACTORIES = new ConcurrentHashMap<>();

    private final FastPushbackReader input;
    // Note:  this is not thread local.
    /**
     * _args is using ThreadLocal so that static inner classes can have access to them
     * -- GETTER --
     *
     * @return The arguments used to configure the JsonReader.  These are thread local.
     */
    @Getter
    private final Map<String, Object> args = new HashMap<>();
    private final int maxParseDepth;

    /**
     * -- GETTER --
     *
     * @return boolean the allowNanAndInfinity setting
     */
    @Setter
    @Getter
    private static volatile boolean allowNanAndInfinity = false;

    static
    {
        ClassFactory mapFactory = new MapFactory();
        assignInstantiator(Map.class, mapFactory);
        assignInstantiator(SortedMap.class, mapFactory);

        ClassFactory colFactory = new CollectionFactory();

        assignInstantiator(Collection.class, colFactory);
        assignInstantiator(List.class, colFactory);
        assignInstantiator(Set.class, colFactory);
        assignInstantiator(SortedSet.class, colFactory);

        assignInstantiator(LocalDate.class, new LocalDateFactory());
        assignInstantiator(LocalTime.class, new LocalTimeFactory());
        assignInstantiator(LocalDateTime.class, new LocalDateTimeFactory());
        assignInstantiator(ZonedDateTime.class, new ZonedDateTimeFactory());

        var timeZoneFactory = new TimeZoneFactory();
        assignInstantiator(TimeZone.class, timeZoneFactory);
        assignInstantiator("sun.util.calendar.ZoneInfo", timeZoneFactory);

        // jvm specific types
        Map<Class<?>, JsonClassReader> temp = new HashMap<>();
        temp.put(String.class, new Readers.StringReader());
        temp.put(Date.class, new Readers.DateReader());
        temp.put(AtomicBoolean.class, new Readers.AtomicBooleanReader());
        temp.put(AtomicInteger.class, new Readers.AtomicIntegerReader());
        temp.put(AtomicLong.class, new Readers.AtomicLongReader());
        temp.put(BigInteger.class, new Readers.BigIntegerReader());
        temp.put(BigDecimal.class, new Readers.BigDecimalReader());
        temp.put(java.sql.Date.class, new Readers.SqlDateReader());
        temp.put(Timestamp.class, new Readers.TimestampReader());
        temp.put(Calendar.class, new Readers.CalendarReader());

        temp.put(Locale.class, new Readers.LocaleReader());
        temp.put(Class.class, new Readers.ClassReader());
        temp.put(StringBuilder.class, new Readers.StringBuilderReader());
        temp.put(StringBuffer.class, new Readers.StringBufferReader());
        temp.put(UUID.class, new Readers.UUIDReader());
        temp.put(URL.class, new Readers.URLReader());
        temp.put(Enum.class, new Readers.EnumReader());

        // we can just ignore it - we are at java < 16 now. This is for code compatibility Java<16
        addPossibleReader(temp, "java.lang.Record", Readers.RecordReader::new);

        // why'd we do this for the readers, but not for the ClassFactories?
        BASE_READERS = temp;
    }

    private static void addPossibleReader(Map map, String fqClassName, Supplier<JsonReader.JsonClassReader> reader) {
        try {
            map.put(Class.forName(fqClassName), reader.get());
        } catch (ClassNotFoundException e) {
            // we can just ignore it - this class is not in this jvm or possibly expected to not be found
        }
    }

    /**
     * Subclass this interface and create a class that will return a new instance of the
     * passed in Class (c).  Your subclass will be called when json-io encounters an
     * the new to instantiate an instance of (c). Use the passed in Object o which will
     * be a JsonObject or value to source values for the construction or your class.
     *
     * Make json-io aware that it needs to call your class by calling the public
     * JsonReader.assignInstantiator() API.
     */
    public interface ClassFactory
    {
        /**
         * Implement this method to return a new instance of the passed in Class.  Use the passed
         * in Object to supply values to the construction of the object.
         * @param c Class of the object that needs to be created
         * @param job JsonObject (if primitive type do job.getPrimitiveValue();
         * @return a new instance of C.  If you completely fill the new instance using
         * the value(s) from object, and no further work is needed for construction, then
         * override the isObjectFinal() method below and return true.
         */
        default Object newInstance(Class<?> c, JsonObject job) {
            // passing on args is for backwards compatibility.
            // also, once we remove the other new instances we
            // can probably remove args from the parameters.
            Map args = new HashMap();
            args.put("jsonObj", job);

            return this.newInstance(c, args);
        }

        default Object setTarget(JsonObject job, Object o) {
            return job.setFinishedTarget(o, isObjectFinal());
        }

        @Deprecated(since = "4.14.0")
        default Object newInstance(Class<?> c, Map args) { return this.newInstance(c); }

        @Deprecated(since = "4.14.0")
        default Object newInstance(Class<?> c) { return MetaUtils.newInstance(c); }

        /**
         * @return true if this object is instantiated and completely filled using the contents
         * from the Object object [a JsonOject or value].  In this case, no further processing
         * will be performed on the instance.  If the object has sub-objects (complex fields),
         * then return false so that the JsonReader will continue on filling out the remaining
         * portion of the object.
         */
        default boolean isObjectFinal() {
            return false;
        }
    }

    /**
     * Used to react to fields missing when reading an object. This method will be called after all deserialization has
     * occured to allow all ref to be resolved.
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
    public interface JsonClassReaderBase  {
        /**
         * @param jOb Object being read.  Could be a fundamental JSON type (String, long, boolean, double, null, or JsonObject)
         * @param stack Deque of objects that have been read (Map of Maps view).
         * @param args Map of argument settings that were passed to JsonReader when instantiated.
         * @return Java Object you wish to convert the the passed in jOb into.
         */
        Object read(Object jOb, Deque<JsonObject> stack, Map<String, Object> args);
    }

    /**
     * Implement this interface to add a custom JSON reader.
     */
    public interface JsonClassReader extends JsonClassReaderBase
    {
        /**
         * @param jOb   Object being read.  Could be a fundamental JSON type (String, long, boolean, double, null, or JsonObject)
         * @param stack Deque of objects that have been read (Map of Maps view).
         * @param args  Map of argument settings that were passed to JsonReader when instantiated.
         * @return Java Object you wish to convert the the passed in jOb into.
         */
        default Object read(Object jOb, Deque<JsonObject> stack, Map<String, Object> args, JsonReader reader) {
            return this.read(jOb, stack, args);
        }

        /**
         * @param jOb Object being read.  Could be a fundamental JSON type (String, long, boolean, double, null, or JsonObject)
         * @param stack Deque of objects that have been read (Map of Maps view).
         * @param args Map of argument settings that were passed to JsonReader when instantiated.
         * @return Java Object you wish to convert the the passed in jOb into.
         */
        default Object read(Object jOb, Deque<JsonObject> stack, Map<String, Object> args) {
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
     * Implement this interface to add a custom JSON reader.
     */
    public interface JsonClassReaderEx extends JsonClassReader
    {
        /**
         * Allow custom readers to have access to the JsonReader
         */
        class Support
        {
            /**
             * Call this method to get an instance of the JsonReader (if needed) inside your custom reader.
             * @param args Map that was passed to your read(jOb, stack, args) method.
             * @return JsonReader instance
             */
            public static JsonReader getReader(Map<String, Object> args)
            {
                return (JsonReader) args.get(JSON_READER);
            }
        }
    }

    /**
     * Use to create new instances of collection interfaces (needed for empty collections)
     */
    public static class CollectionFactory implements ClassFactory {
        public Object newInstance(Class<?> c, JsonObject job)
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
         * @param c Map interface that was requested for instantiation.
         * @return a concrete Map type.
         */
        public Object newInstance(Class<?> c, JsonObject job)
        {
            if (SortedMap.class.isAssignableFrom(c))
            {
                return new TreeMap();
            }
            else if (Map.class.isAssignableFrom(c))
            {
                return new LinkedHashMap<>();
            }
            throw new JsonIoException("MapFactory handed Class for which it was not expecting: " + c.getName());
        }
    }

    /**
     * For difficult to instantiate classes, you can add your own ClassFactory
     * which will be called when the passed in class 'c' is encountered.  Your ClassFactory
     * will be called with newInstance(class, Object) and your factory is expected to return
     * a new instance of 'c' and can use values from Object (JsonMap or simple value) to
     * initialize the created class with the appropriate values.
     * <p>
     * This API is an 'escape hatch' to allow ANY object to be instantiated by JsonReader
     * and is useful when you encounter a class that JsonReader cannot instantiate using its
     * internal exhausting attempts (trying all constructors, varying arguments to them, etc.)
     * @param className Class name to assign an ClassFactory to
     * @param factory ClassFactory that will create 'c' instances
     */
    public static void assignInstantiator(String className, ClassFactory factory)
    {
        BASE_CLASS_FACTORIES.put(className, factory);
    }

    /**
     * See comment on method JsonReader.assignInstantiator(String, ClassFactory)
     */
    public static void assignInstantiator(Class c, ClassFactory factory)
    {
        BASE_CLASS_FACTORIES.put(c.getName(), factory);
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
     * @deprecated use ReadOptionsBuilder to create any additional readers you'll need.
     *
     */
    @Deprecated(since = "4.14.0")
    public void addReader(Class c, JsonClassReader reader)
    {
        readers.put(c, reader);
    }

    /**
     * Call this method to add a custom JSON reader to json-io.  It will
     * associate the Class 'c' to the reader you pass in.  The readers are
     * found with isAssignableFrom().  If this is too broad, causing too
     * many classes to be associated to the custom reader, you can indicate
     * that json-io should not use a custom reader for a particular class,
     * by calling the addNotCustomReader() method.  This method will add
     * the customer reader such that it will be there permanently, for the
     * life of the JVM (static).
     * @param c Class to assign a custom JSON reader to
     * @param reader The JsonClassReader which will read the custom JSON format of 'c'
     */
    public static void addReaderPermanent(Class c, JsonClassReader reader)
    {
        BASE_READERS.put(c, reader);
    }

    /**
     * Force json-io to use it's internal generic approach to writing the
     * passed in class, even if a Custom JSON reader is specified for its
     * parent class.
     * @param c Class to which to force no custom JSON reading to occur.
     * Normally, this is not needed, however, if a reader is assigned to a
     * parent class of 'c', then calling this method on 'c' will prevent
     * any custom reader from processing class 'c'
     */
    public void addNotCustomReader(Class c)
    {
        notCustom.add(c);
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     *
     * @param json String JSON input
     * @return Java object graph matching JSON input
     */
    public static <T> T jsonToJava(String json)
    {
        return jsonToJava(json, null);
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     *
     * @param json String JSON input
     * @param optionalArgs Map of optional parameters to control parsing.  See readme file for details.
     * @param maxDepth Maximum parsing depth.
     * @return Java object graph matching JSON input
     */
    public static Object jsonToJava(String json, Map<String, Object> optionalArgs, int maxDepth)
    {
        if (json == null || "".equals(json.trim()))
        {
            return null;
        }
        if (optionalArgs == null)
        {
            optionalArgs = new HashMap<>();
            optionalArgs.put(USE_MAPS, false);
        }
        if (!optionalArgs.containsKey(USE_MAPS))
        {
            optionalArgs.put(USE_MAPS, false);
        }
        JsonReader jr = new JsonReader(json, optionalArgs, maxDepth);
        Object obj = jr.readObject();
        jr.close();
        return obj;
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     *
     * @param json String JSON input
     * @param optionalArgs Map of optional parameters to control parsing.  See readme file for details.
     * @return Java object graph matching JSON input
     */
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
    public static <T> T jsonToJava(InputStream inputStream, Map<String, Object> optionalArgs, int maxDepth)
    {
        if (optionalArgs == null)
        {
            optionalArgs = new HashMap<String, Object>();
            optionalArgs.put(USE_MAPS, false);
        }
        if (!optionalArgs.containsKey(USE_MAPS))
        {
            optionalArgs.put(USE_MAPS, false);
        }
        JsonReader jr = new JsonReader(inputStream, optionalArgs, maxDepth);
        Object obj = jr.readObject();
        jr.close();
        return (T) obj;
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     *
     * @param inputStream InputStream containing JSON input
     * @param optionalArgs Map of optional parameters to control parsing.  See readme file for details.
     * @return Java object graph matching JSON input
     */
    public static <T> T jsonToJava(InputStream inputStream, Map<String, Object> optionalArgs)
    {
        return jsonToJava(inputStream, optionalArgs, DEFAULT_MAX_PARSE_DEPTH);
    }

    /**
     * Map args = ["USE_MAPS": true]
     * Use JsonReader.jsonToJava(String json, args)
     * Note that the return type will match the JSON type (array, object, string, long, boolean, or null).
     * No longer recommended: Use jsonToJava with USE_MAPS:true
     * @param json String of JSON content
     * @param maxDepth Maximum parsing depth.
     * @return Map representing JSON content.  Each object is represented by a Map.
     */
    public static Map jsonToMaps(String json, int maxDepth)
    {
        return jsonToMaps(json, null, maxDepth);
    }

    /**
     * Map args = ["USE_MAPS": true]
     * Use JsonReader.jsonToJava(String json, args)
     * Note that the return type will match the JSON type (array, object, string, long, boolean, or null).
     * No longer recommended: Use jsonToJava with USE_MAPS:true
     * @param json String of JSON content
     * @return Map representing JSON content.  Each object is represented by a Map.
     */
    public static Map jsonToMaps(String json)
    {
        return jsonToMaps(json, DEFAULT_MAX_PARSE_DEPTH);
    }

    /**
     * Map args = ["USE_MAPS": true]
     * Use JsonReader.jsonToJava(String json, args)
     * Note that the return type will match the JSON type (array, object, string, long, boolean, or null).
     * No longer recommended: Use jsonToJava with USE_MAPS:true
     * @param json String of JSON content
     * @param optionalArgs Map of optional arguments to control customization.  See readme file for
     * details on these options.
     * @param maxDepth Maximum parsing depth.
     * @return Map where each Map representa an object in the JSON input.
     */
    public static Map jsonToMaps(String json, Map<String, Object> optionalArgs, int maxDepth)
    {
        if (optionalArgs == null)
        {
            optionalArgs = new HashMap<>();
        }
        optionalArgs.put(USE_MAPS, true);
        ByteArrayInputStream ba = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        JsonReader jr = new JsonReader(ba, optionalArgs, maxDepth);
        Object ret = jr.readObject();
        jr.close();

        return adjustOutputMap(ret);
    }

    /**
     * Map args = ["USE_MAPS": true]
     * Use JsonReader.jsonToJava(String json, args)
     * Note that the return type will match the JSON type (array, object, string, long, boolean, or null).
     * No longer recommended: Use jsonToJava with USE_MAPS:true
     * @param json String of JSON content
     * @param optionalArgs Map of optional arguments to control customization.  See readme file for
     * details on these options.
     * @return Map where each Map representa an object in the JSON input.
     */
    public static Map jsonToMaps(String json, Map<String, Object> optionalArgs)
    {
        return jsonToMaps(json, optionalArgs, DEFAULT_MAX_PARSE_DEPTH);
    }

    /**
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
    public static Map jsonToMaps(InputStream inputStream, Map<String, Object> optionalArgs, int maxDepth)
    {
        if (optionalArgs == null)
        {
            optionalArgs = new HashMap<String, Object>();
        }
        optionalArgs.put(USE_MAPS, true);
        JsonReader jr = new JsonReader(inputStream, optionalArgs, maxDepth);
        Object ret = jr.readObject();
        jr.close();

        return adjustOutputMap(ret);
    }

    /**
     * Map args = ["USE_MAPS": true]
     * Use JsonReader.jsonToJava(inputStream, args)
     * Note that the return type will match the JSON type (array, object, string, long, boolean, or null).
     * No longer recommended: Use jsonToJava with USE_MAPS:true
     * @param inputStream containing JSON content
     * @param optionalArgs Map of optional arguments to control customization.  See readme file for
     * details on these options.
     * @return Map containing the content from the JSON input.  Each Map represents an object from the input.
     */
    public static Map jsonToMaps(InputStream inputStream, Map<String, Object> optionalArgs)
    {
        return jsonToMaps(inputStream, optionalArgs, DEFAULT_MAX_PARSE_DEPTH);
    }

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

    public JsonReader(int maxDepth)
    {
        input = null;
        getArgs().put(USE_MAPS, false);
        getArgs().put(CLASSLOADER, JsonReader.class.getClassLoader());
        maxParseDepth = maxDepth;
    }

    public JsonReader()
    {
        this(DEFAULT_MAX_PARSE_DEPTH);
    }

    public JsonReader(InputStream inp, int maxDepth)
    {
        this(inp, false, maxDepth);
    }

    public JsonReader(InputStream inp)
    {
        this(inp, DEFAULT_MAX_PARSE_DEPTH);
    }

    /**
     * Use this constructor if you already have a JsonObject graph and want to parse it into
     * Java objects by calling jsonReader.jsonObjectsToJava(rootJsonObject) after constructing
     * the JsonReader.
     * @param optionalArgs Map of optional arguments for the JsonReader.
     * @param maxDepth Maximum parsing depth.
     */
    public JsonReader(Map<String, Object> optionalArgs, int maxDepth)
    {
        this(new ByteArrayInputStream(new byte[]{}), optionalArgs, maxDepth);
    }

    /**
     * Use this constructor if you already have a JsonObject graph and want to parse it into
     * Java objects by calling jsonReader.jsonObjectsToJava(rootJsonObject) after constructing
     * the JsonReader.
     * @param optionalArgs Map of optional arguments for the JsonReader.
     */
    public JsonReader(Map<String, Object> optionalArgs)
    {
        this(optionalArgs, DEFAULT_MAX_PARSE_DEPTH);
    }

    // This method is needed to get around the fact that 'this()' has to be the first method of a constructor.
    static Map makeArgMap(Map<String, Object> args, boolean useMaps)
    {
        args.put(USE_MAPS, useMaps);
        return args;
    }

    public JsonReader(InputStream inp, boolean useMaps, int maxDepth)
    {
        this(inp, makeArgMap(new HashMap<String, Object>(), useMaps), maxDepth);
    }

    public JsonReader(InputStream inp, boolean useMaps)
    {
        this(inp, useMaps, DEFAULT_MAX_PARSE_DEPTH);
    }

    public JsonReader(InputStream inp, Map<String, Object> optionalArgs, int maxDepth)
    {
        initializeFromArgs(optionalArgs);

        input = new FastPushbackBufferedReader(new InputStreamReader(inp, StandardCharsets.UTF_8));
        maxParseDepth = maxDepth;
    }

    public JsonReader(InputStream inp, Map<String, Object> optionalArgs)
    {
        this(inp, optionalArgs, DEFAULT_MAX_PARSE_DEPTH);
    }

    public JsonReader(String inp, Map<String, Object> optionalArgs, int maxDepth)
    {
        initializeFromArgs(optionalArgs);
        byte[] bytes = inp.getBytes(StandardCharsets.UTF_8);
        input = new FastPushbackBufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
        maxParseDepth = maxDepth;
    }

    public JsonReader(String inp, Map<String, Object> optionalArgs)
    {
        this(inp, optionalArgs, DEFAULT_MAX_PARSE_DEPTH);
    }

    public JsonReader(byte[] inp, Map<String, Object> optionalArgs, int maxDepth)
    {
        initializeFromArgs(optionalArgs);
        input = new FastPushbackBufferedReader(new InputStreamReader(new ByteArrayInputStream(inp), StandardCharsets.UTF_8));
        maxParseDepth = maxDepth;
    }

    public JsonReader(byte[] inp, Map<String, Object> optionalArgs)
    {
        this(inp, optionalArgs, DEFAULT_MAX_PARSE_DEPTH);
    }

    private void initializeFromArgs(Map<String, Object> optionalArgs)
    {
        if (optionalArgs == null)
        {
            optionalArgs = new HashMap<>();
        }
        Map<String, Object> args = getArgs();
        args.putAll(optionalArgs);
        args.put(JSON_READER, this);
        if (!args.containsKey(CLASSLOADER))
        {
            args.put(CLASSLOADER, JsonReader.class.getClassLoader());
        }
        Map<String, String> typeNames = (Map<String, String>) args.get(TYPE_NAME_MAP);

        if (typeNames != null)
        { // Reverse the Map (this allows the users to only have a Map from type to short-hand name,
            // and not keep a 2nd map from short-hand name to type.
            Map<String, String> typeNameMap = new HashMap<String, String>();
            for (Map.Entry<String, String> entry : typeNames.entrySet())
            {
                typeNameMap.put(entry.getValue(), entry.getKey());
            }
            args.put(TYPE_NAME_MAP_REVERSE, typeNameMap); // replace with our reversed Map.
        }

        setMissingFieldHandler((MissingFieldHandler) args.get(MISSING_FIELD_HANDLER));


        var customReaders = (Map<Class<?>, JsonClassReader>) args.get(CUSTOM_READER_MAP);
        if (customReaders != null) {
            this.readers.putAll(customReaders);
        }

        var notCustomReaders = (Collection<Class<?>>) args.get(NOT_CUSTOM_READER_MAP);
        if (notCustomReaders != null) {
            this.notCustom.addAll(notCustomReaders);
        }

        // pull all factories out and add them to the already added
        // global class factories.  The global class factories are
        // replaced by and ith the same name in this object.
        var factories = (Map<String, ClassFactory>) args.get(FACTORIES);
        if (factories != null) {
            this.classFactories.putAll(factories);
        }
    }

    /**
     * Read JSON input from the stream that was set up in the constructor, turning it into
     * Java Maps (JsonObject's).  Then, if requested, the JsonObjects can be converted
     * into Java instances.
     *
     * @return Java Object graph constructed from InputStream supplying
     *         JSON serialized content.
     */
    public Object readObject()
    {
        JsonParser parser = new JsonParser(input, getArgs(), maxParseDepth);
        JsonObject root = new JsonObject();
        Object o;
        try
        {
            o = parser.readValue(root);
            if (o == JsonParser.EMPTY_OBJECT)
            {
                return new JsonObject();
            }
        }
        catch (JsonIoException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new JsonIoException("error parsing JSON value", e);
        }

        Object graph;
        if (o instanceof Object[])
        {
            root.setType(Object[].class.getName());
            root.setTarget(o);
            root.put(ITEMS, o);
            graph = convertParsedMapsToJava(root);
        }
        else
        {
            graph = o instanceof JsonObject ? convertParsedMapsToJava((JsonObject) o) : o;
        }

        // Allow a complete 'Map' return (Javascript style)
        if (useMaps())
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
    public Object jsonObjectsToJava(JsonObject root)
    {
        getArgs().put(USE_MAPS, false);
        return convertParsedMapsToJava(root);
    }

    protected boolean useMaps()
    {
        return Boolean.TRUE.equals(getArgs().get(USE_MAPS));
    }

    /**
     * @return ClassLoader to be used by Custom Writers
     */
    ClassLoader getClassLoader()
    {
        return (ClassLoader) args.get(CLASSLOADER);
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
    protected Object convertParsedMapsToJava(JsonObject root)
    {
        try
        {
            Object graph;
            if (root.isFinished)
            {   // Called on a JsonObject that has already been converted
                graph = root.target;
            }
            else
            {
                Resolver resolver = useMaps() ? new MapResolver(this) : new ObjectResolver(this, (ClassLoader) args.get(CLASSLOADER));
                Object instance = resolver.createJavaObjectInstance(Object.class, root);
                if (root.isFinished)
                {   // Factory method instantiated and completely loaded the object.
                    graph = instance;
                }
                else
                {
                    graph = resolver.convertMapsToObjects(root);
                }
                resolver.cleanup();
                readers.clear();
                classFactories.clear();
            }
            return graph;
        }
        catch (Exception e)
        {
            try
            {
                close();
            }
            catch (Exception ignored)
            {   // Exception handled in close()
            }
            if (e instanceof JsonIoException)
            {
                throw (JsonIoException)e;
            }
            throw new JsonIoException(getErrorMessage(e.getMessage()), e);
        }
    }

    public Object newInstance(Class<?> c, JsonObject jsonObject) {
        ClassFactory classFactory = classFactories.get(c.getName());

        if (classFactory != null) {
            Object o = classFactory.newInstance(c, jsonObject);
            return jsonObject.setFinishedTarget(o, classFactory.isObjectFinal());
        }

        return MetaUtils.newInstance(c);
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

}
