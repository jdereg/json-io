package com.cedarsoftware.util.io;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    public static final String CUSTOM_READER_MAP = "CUSTOM_READERS";    // If set, this map specifies Class to CustomReader
    public static final String NOT_CUSTOM_READER_MAP = "NOT_CUSTOM_READERS";    // If set, this map specifies Class to CustomReader
    public static final String USE_MAPS = "USE_MAPS";                   // If set, the read-in JSON will be turned into a Map of Maps (JsonObject) representation
    public static final String UNKNOWN_OBJECT = "UNKNOWN_OBJECT";       // What to do when an object is found and 'type' cannot be determined.
    public static final String JSON_READER = "JSON_READER";             // Pointer to 'this' (automatically placed in the Map)
    public static final String OBJECT_RESOLVER = "OBJECT_RESOLVER";     // Pointer to the current ObjectResolver (automatically placed in the Map)
    public static final String TYPE_NAME_MAP = "TYPE_NAME_MAP";         // If set, this map will be used when writing @type values - allows short-hand abbreviations type names
    public static final String MISSING_FIELD_HANDLER = "MISSING_FIELD_HANDLER";         // If set, this object will be called when a field is present in the JSON but missing from the corresponding class
    static final String TYPE_NAME_MAP_REVERSE = "TYPE_NAME_MAP_REVERSE";// This map is the reverse of the TYPE_NAME_MAP (value -> key)
    protected final ConcurrentMap<Class, JsonClassReaderBase> readers = new ConcurrentHashMap<Class, JsonClassReaderBase>();
    protected MissingFieldHandler missingFieldHandler;
    protected final Set<Class> notCustom = new HashSet<Class>();
    private static final Map<Class, Factory> factory = new ConcurrentHashMap<Class, Factory>();
    private final Map<Long, JsonObject> objsRead = new HashMap<Long, JsonObject>();
    private final FastPushbackReader input;
    // _args is using ThreadLocal so that static inner classes can have access to them
    private final Map<String, Object> args = new HashMap<String, Object>();

    {
        addReader(String.class, new Readers.StringReader());
        addReader(Date.class, new Readers.DateReader());
        addReader(BigInteger.class, new Readers.BigIntegerReader());
        addReader(BigDecimal.class, new Readers.BigDecimalReader());
        addReader(java.sql.Date.class, new Readers.SqlDateReader());
        addReader(Timestamp.class, new Readers.TimestampReader());
        addReader(Calendar.class, new Readers.CalendarReader());
        addReader(TimeZone.class, new Readers.TimeZoneReader());
        addReader(Locale.class, new Readers.LocaleReader());
        addReader(Class.class, new Readers.ClassReader());
        addReader(StringBuilder.class, new Readers.StringBuilderReader());
        addReader(StringBuffer.class, new Readers.StringBufferReader());
    }

    static
    {
        Factory colFactory = new CollectionFactory();
        assignInstantiator(Collection.class, colFactory);
        assignInstantiator(List.class, colFactory);
        assignInstantiator(Set.class, colFactory);
        assignInstantiator(SortedSet.class, colFactory);

        Factory mapFactory = new MapFactory();
        assignInstantiator(Map.class, mapFactory);
        assignInstantiator(SortedMap.class, mapFactory);
    }

    public interface Factory
    {
    }

    /**
     * Subclass this interface and create a class that will return a new instance of the
     * passed in Class (c).  Your subclass will be called when json-io encounters an
     * the new to instantiate an instance of (c).
     *
     * Make json-io aware that it needs to call your class by calling the public
     * JsonReader.assignInstantiator() API.
     */
    public interface ClassFactory extends Factory
    {
        Object newInstance(Class c);
    }

    /**
     * Subclass this interface and create a class that will return a new instance of the
     * passed in Class (c).  Your subclass will be called when json-io encounters an
     * the new to instantiate an instance of (c).  The 'args' Map passed in will
     * contain a 'jsonObj' key that holds the JsonObject (Map) representing the
     * object being converted.  If you need values from the fields of this object
     * in order to instantiate your class, you can grab them from the JsonObject (Map).
     *
     * Make json-io aware that it needs to call your class by calling the public
     * JsonReader.assignInstantiator() API.
     */
    public interface ClassFactoryEx extends Factory
    {
        Object newInstance(Class c, Map args);
    }

    /**
     * Used to react to fields missing when reading an object.
     * <p>
     * Used in conjunction with {@link JsonReader#MISSING_FIELD_HANDLER}.
     */
    public interface MissingFieldHandler
    {
        /**
         * Notify that a field is missing
         * @param object the object that contains the missing field
         * @param fieldName name of the field to be replaced
         * @param value current value of the field
          */
        void fieldMissing(Object object, String fieldName, Object value);

    }

    /**
     * Implement this interface to add a custom JSON reader.
     */
    public interface JsonClassReaderBase  { }

    /**
     * Implement this interface to add a custom JSON reader.
     */
    public interface JsonClassReader extends JsonClassReaderBase
    {
        Object read(Object jOb, Deque<JsonObject<String, Object>> stack);
    }

    /**
     * Implement this interface to add a custom JSON reader.
     */
    public interface JsonClassReaderEx extends JsonClassReaderBase
    {
        Object read(Object jOb, Deque<JsonObject<String, Object>> stack, Map<String, Object> args);

        class Support
        {
            public static JsonReader getReader(Map<String, Object> args)
            {
                return (JsonReader) args.get(JSON_READER);
            }
        }
    }

    /**
     * Use to create new instances of collection interfaces (needed for empty collections)
     */
    public static class CollectionFactory implements ClassFactory
    {
        public Object newInstance(Class c)
        {
            if (List.class.isAssignableFrom(c))
            {
                return new ArrayList();
            }
            else if (SortedSet.class.isAssignableFrom(c))
            {
                return new TreeSet();
            }
            else if (Set.class.isAssignableFrom(c))
            {
                return new LinkedHashSet();
            }
            else if (Collection.class.isAssignableFrom(c))
            {
                return new ArrayList();
            }
            throw new JsonIoException("CollectionFactory handed Class for which it was not expecting: " + c.getName());
        }
    }

    /**
     * Use to create new instances of Map interfaces (needed for empty Maps)
     */
    public static class MapFactory implements ClassFactory
    {
        public Object newInstance(Class c)
        {
            if (SortedMap.class.isAssignableFrom(c))
            {
                return new TreeMap();
            }
            else if (Map.class.isAssignableFrom(c))
            {
                return new LinkedHashMap();
            }
            throw new JsonIoException("MapFactory handed Class for which it was not expecting: " + c.getName());
        }
    }

    /**
     * For difficult to instantiate classes, you can add your own ClassFactory
     * or ClassFactoryEx which will be called when the passed in class 'c' is
     * encountered.  Your ClassFactory will be called with newInstance(c) and
     * your factory is expected to return a new instance of 'c'.
     *
     * This API is an 'escape hatch' to allow ANY object to be instantiated by JsonReader
     * and is useful when you encounter a class that JsonReader cannot instantiate using its
     * internal exhausting attempts (trying all constructors, varying arguments to them, etc.)
     * @param c Class to assign an ClassFactory to
     * @param f ClassFactory that will create 'c' instances
     */
    public static void assignInstantiator(Class c, Factory f)
    {
        factory.put(c, f);
    }

    /**
     * Call this method to add your custom JSON reader to json-io.  It will
     * associate the Class 'c' to the reader you pass in.  The readers are
     * found with isAssignableFrom().  If this is too broad, causing too
     * many classes to be associated to the custom reader, you can indicate
     * that json-io should not use a custom reader for a particular class,
     * by calling the addNotCustomReader() method.
     * @param c Class to assign a custom JSON reader to
     * @param reader The JsonClassReader which will read the custom JSON format of 'c'
     */
    public void addReader(Class c, JsonClassReaderBase reader)
    {
        readers.put(c, reader);
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

    MissingFieldHandler getMissingFieldHandler()
    {
        return missingFieldHandler;
    }

    public void setMissingFieldHandler(MissingFieldHandler handler)
    {
        missingFieldHandler = handler;
    }

    /**
     * @return The arguments used to configure the JsonReader.  These are thread local.
     */
    public Map getArgs()
    {
        return args;
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     *
     * @param json String JSON input
     * @return Java object graph matching JSON input
     */
    public static Object jsonToJava(String json)
    {
        return jsonToJava(json, null);
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     *
     * @param json String JSON input
     * @param optionalArgs Map of optional parameters to control parsing.  See readme file for details.
     * @return Java object graph matching JSON input
     */
    public static Object jsonToJava(String json, Map<String, Object> optionalArgs)
    {
        if (optionalArgs == null)
        {
            optionalArgs = new HashMap();
            optionalArgs.put(USE_MAPS, false);
        }
        if (!optionalArgs.containsKey(USE_MAPS))
        {
            optionalArgs.put(USE_MAPS, false);
        }
        ByteArrayInputStream ba;
        try
        {
            ba = new ByteArrayInputStream(json.getBytes("UTF-8"));
        }
        catch (UnsupportedEncodingException e)
        {
            throw new JsonIoException("Could not convert JSON to Maps because your JVM does not support UTF-8", e);
        }
        JsonReader jr = new JsonReader(ba, optionalArgs);
        Object obj = jr.readObject();
        jr.close();
        return obj;
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     *
     * @param inputStream InputStream containing JSON input
     * @param optionalArgs Map of optional parameters to control parsing.  See readme file for details.
     * @return Java object graph matching JSON input
     */
    public static Object jsonToJava(InputStream inputStream, Map<String, Object> optionalArgs)
    {
        if (optionalArgs == null)
        {
            optionalArgs = new HashMap();
            optionalArgs.put(USE_MAPS, false);
        }
        if (!optionalArgs.containsKey(USE_MAPS))
        {
            optionalArgs.put(USE_MAPS, false);
        }
        JsonReader jr = new JsonReader(inputStream, optionalArgs);
        Object obj = jr.readObject();
        jr.close();
        return obj;
    }

    /**
     * Map args = ["USE_MAPS": true]
     * Use JsonReader.jsonToJava(String json, args)
     * Note that the return type will match the JSON type (array, object, string, long, boolean, or null).
     * No longer recommended: Use jsonToJava with USE_MAPS:true
     */
    public static Map jsonToMaps(String json)
    {
        return jsonToMaps(json, null);
    }

    /**
     * Map args = ["USE_MAPS": true]
     * Use JsonReader.jsonToJava(String json, args)
     * Note that the return type will match the JSON type (array, object, string, long, boolean, or null).
     * No longer recommended: Use jsonToJava with USE_MAPS:true
     */
    public static Map jsonToMaps(String json, Map<String, Object> optionalArgs)
    {
        try
        {
            if (optionalArgs == null)
            {
                optionalArgs = new HashMap();
            }
            optionalArgs.put(USE_MAPS, true);
            ByteArrayInputStream ba = new ByteArrayInputStream(json.getBytes("UTF-8"));
            JsonReader jr = new JsonReader(ba, optionalArgs);
            Object ret = jr.readObject();
            jr.close();

            return adjustOutputMap(ret);
        }
        catch (UnsupportedEncodingException e)
        {
            throw new JsonIoException("Could not convert JSON to Maps because your JVM does not support UTF-8", e);
        }
    }

    /**
     * Map args = ["USE_MAPS": true]
     * Use JsonReader.jsonToJava(inputStream, args)
     * Note that the return type will match the JSON type (array, object, string, long, boolean, or null).
     * No longer recommended: Use jsonToJava with USE_MAPS:true
     */
    public static Map jsonToMaps(InputStream inputStream, Map<String, Object> optionalArgs)
    {
        if (optionalArgs == null)
        {
            optionalArgs = new HashMap();
        }
        optionalArgs.put(USE_MAPS, true);
        JsonReader jr = new JsonReader(inputStream, optionalArgs);
        Object ret = jr.readObject();
        jr.close();

        return adjustOutputMap(ret);
    }

    private static Map adjustOutputMap(Object ret)
    {
        if (ret instanceof Map)
        {
            return (Map) ret;
        }

        if (ret != null && ret.getClass().isArray())
        {
            JsonObject<String, Object> retMap = new JsonObject<String, Object>();
            retMap.put("@items", ret);
            return retMap;
        }
        JsonObject<String, Object> retMap = new JsonObject<String, Object>();
        retMap.put("@items", new Object[]{ret});
        return retMap;
    }

    public JsonReader()
    {
        input = null;
        getArgs().put(USE_MAPS, false);
    }

    public JsonReader(InputStream inp)
    {
        this(inp, false);
    }

    /**
     * Use this constructor if you already have a JsonObject graph and want to parse it into
     * Java objects by calling jsonReader.jsonObjectsToJava(rootJsonObject) after constructing
     * the JsonReader.
     * @param optionalArgs Map of optional arguments for the JsonReader.
     */
    public JsonReader(Map<String, Object> optionalArgs)
    {
        this(new ByteArrayInputStream(new byte[]{}), optionalArgs);
    }

    // This method is needed to get around the fact that 'this()' has to be the first method of a constructor.
    static Map makeArgMap(Map<String, Object> args, boolean useMaps)
    {
        args.put(USE_MAPS, useMaps);
        return args;
    }

    public JsonReader(InputStream inp, boolean useMaps)
    {
        this(inp, makeArgMap(new HashMap<String, Object>(), useMaps));
    }

    public JsonReader(InputStream inp, Map<String, Object> optionalArgs)
    {
        if (optionalArgs == null)
        {
            optionalArgs = new HashMap();
        }
        Map<String, Object> args = getArgs();
        args.putAll(optionalArgs);
        args.put(JSON_READER, this);
        Map<String, String> typeNames = (Map<String, String>) args.get(TYPE_NAME_MAP);

        if (typeNames != null)
        {   // Reverse the Map (this allows the users to only have a Map from type to short-hand name,
            // and not keep a 2nd map from short-hand name to type.
            Map<String, String> typeNameMap = new HashMap<String, String>();
            for (Map.Entry<String, String> entry : typeNames.entrySet())
            {
                typeNameMap.put(entry.getValue(), entry.getKey());
            }
            args.put(TYPE_NAME_MAP_REVERSE, typeNameMap);   // replace with our reversed Map.
        }

        setMissingFieldHandler((MissingFieldHandler) args.get(MISSING_FIELD_HANDLER));

        Map<Class, JsonClassReaderBase> customReaders = (Map<Class, JsonClassReaderBase>) args.get(CUSTOM_READER_MAP);
        if (customReaders != null)
        {
            for (Map.Entry<Class, JsonClassReaderBase> entry : customReaders.entrySet())
            {
                addReader(entry.getKey(), entry.getValue());
            }
        }

        Iterable<Class> notCustomReaders = (Iterable<Class>) args.get(NOT_CUSTOM_READER_MAP);
        if (notCustomReaders != null)
        {
            for (Class c : notCustomReaders)
            {
                addNotCustomReader(c);
            }
        }

        try
        {
            input = new FastPushbackReader(new BufferedReader(new InputStreamReader(inp, "UTF-8")));
        }
        catch (UnsupportedEncodingException e)
        {
            throw new JsonIoException("Your JVM does not support UTF-8.  Get a better JVM.", e);
        }
    }

    public Map<Long, JsonObject> getObjectsRead()
    {
        return objsRead;
    }

    public Object getRefTarget(JsonObject jObj)
    {
        if (!jObj.isReference())
        {
            return jObj;
        }

        Long id = jObj.getReferenceId();
        JsonObject target = objsRead.get(id);
        if (target == null)
        {
            throw new IllegalStateException("The JSON input had an @ref to an object that does not exist.");
        }
        return getRefTarget(target);
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
        JsonParser parser = new JsonParser(input, objsRead, getArgs());
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
            root.put("@items", o);
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
            Resolver resolver = useMaps() ? new MapResolver(this) : new ObjectResolver(this);
            resolver.createJavaObjectInstance(Object.class, root);
            Object graph = resolver.convertMapsToObjects((JsonObject<String, Object>) root);
            resolver.cleanup();
            readers.clear();
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

    public static Object newInstance(Class c)
    {
        if (factory.containsKey(c))
        {
            ClassFactory cf = (ClassFactory) factory.get(c);
            return cf.newInstance(c);
        }
        return MetaUtils.newInstance(c);
    }

    public static Object newInstance(Class c, JsonObject jsonObject)
    {
        if (factory.containsKey(c))
        {
            Factory cf = factory.get(c);
            if (cf instanceof ClassFactoryEx)
            {
                Map args = new HashMap();
                args.put("jsonObj", jsonObject);
                return ((ClassFactoryEx)cf).newInstance(c, args);
            }
            if (cf instanceof ClassFactory)
            {
                return ((ClassFactory)cf).newInstance(c);
            }
            throw new JsonIoException("Unknown instantiator (Factory) class.  Must subclass ClassFactoryEx or ClassFactory, found: " + cf.getClass().getName());
        }
        return MetaUtils.newInstance(c);
    }

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
            return msg + "\nLast read: " + input.getLastSnippet() + "\nline: " + input.line + ", col: " + input.col;
        }
        return msg;
    }
}
