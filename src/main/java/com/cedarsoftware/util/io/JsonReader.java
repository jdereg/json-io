package com.cedarsoftware.util.io;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
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
import java.util.concurrent.ConcurrentHashMap;

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
    public static final String USE_MAPS = "USE_MAPS";               // If set, the read-in JSON will be turned into a Map of Maps (JsonObject) representation
    public static final String TYPE_NAME_MAP = "TYPE_NAME_MAP";     // If set, this map will be used when writing @type values - allows short-hand abbreviations type names
    static final String TYPE_NAME_MAP_REVERSE = "TYPE_NAME_MAP_REVERSE"; // This map is the reverse of the TYPE_NAME_MAP (value -> key)
    protected static final Map<Class, JsonClassReader> readers = new ConcurrentHashMap<>();
    protected static final Set<Class> notCustom = new HashSet<>();
    private static final Map<Class, ClassFactory> factory = new ConcurrentHashMap<>();
    private final Map<Long, JsonObject> objsRead = new HashMap<>();
    private final FastPushbackReader input;
    private boolean useMaps = false;
    static final ThreadLocal<FastPushbackReader> threadInput = new ThreadLocal<>();
    // _args is using ThreadLocal so that static inner classes can have access to them
    static final ThreadLocal<Map<String, Object>> _args = new ThreadLocal<Map<String, Object>>()
    {
        public Map<String, Object> initialValue()
        {
            return new HashMap<>();
        }
    };

    static
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

        ClassFactory colFactory = new CollectionFactory();
        assignInstantiator(Collection.class, colFactory);
        assignInstantiator(List.class, colFactory);
        assignInstantiator(Set.class, colFactory);
        assignInstantiator(SortedSet.class, colFactory);

        ClassFactory mapFactory = new MapFactory();
        assignInstantiator(Map.class, mapFactory);
        assignInstantiator(SortedMap.class, mapFactory);
    }

    public interface ClassFactory
    {
        Object newInstance(Class c);
    }

    /**
     * Implement this interface to add a custom JSON reader.
     */
    public interface JsonClassReader
    {
        Object read(Object jOb, Deque<JsonObject<String, Object>> stack);
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
     * which will be called when the passed in class 'c' is encountered.  Your
     * ClassFactory will be called with newInstance(c) and your factory is expected
     * to return a new instance of 'c'.
     *
     * This API is an 'escape hatch' to allow ANY object to be instantiated by JsonReader
     * and is useful when you encounter a class that JsonReader cannot instantiate using its
     * internal exhausting attempts (trying all constructors, varying arguments to them, etc.)
     * @param c Class to assign an ClassFactory to
     * @param f ClassFactory that will create 'c' instances
     */
    public static void assignInstantiator(Class c, ClassFactory f)
    {
        factory.put(c, f);
    }

    /**
     * Call this method to add your custom JSON reader to json-io.  It will
     * associate the Class 'c' to the reader you pass in.  The readers are
     * found with isAssignableFrom().  If this is too broad, causing too
     * many classes to be associated to the custom reader, you can indicate
     * that json-io should not use a custom reader, by calling the
     * addNotCustomReader() method.
     * @param c Class to assign a custom JSON reader to
     * @param reader The JsonClassReader which will read the custom JSON format of 'c'
     */
    public static void addReader(Class c, JsonClassReader reader)
    {
        for (Map.Entry<Class, JsonClassReader> entry : readers.entrySet())
        {
            Class clz = entry.getKey();
            if (clz == c)
            {
                entry.setValue(reader);
                return;
            }
        }
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
    public static void addNotCustomReader(Class c)
    {
        notCustom.add(c);
    }

    /**
     * @return The arguments used to configure the JsonReader.  These are thread local.
     */
    protected static Map getArgs()
    {
        return _args.get();
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     *
     * @param json String JSON input
     * @return Java object graph matching JSON input
     */
    public static Object jsonToJava(String json)
    {
        return jsonToJava(json, new HashMap<String, Object>());
    }

    /**
     * Convert the passed in JSON string into a Java object graph.
     *
     * @param json String JSON input
     * @return Java object graph matching JSON input
     */
    public static Object jsonToJava(String json, Map<String, Object> optionalArgs)
    {
        try
        {
            optionalArgs.put(USE_MAPS, false);
            ByteArrayInputStream ba = new ByteArrayInputStream(json.getBytes("UTF-8"));
            JsonReader jr = new JsonReader(ba, optionalArgs);
            Object obj = jr.readObject();
            jr.close();
            return obj;
        }
        catch (UnsupportedEncodingException e)
        {
            throw new JsonIoException("Could not convert JSON to Java because your JVM does not support UTF-8", e);
        }
    }

    /**
     * Convert the passed in JSON string into a Java object graph
     * that consists solely of Java Maps where the keys are the
     * fields and the values are primitives or other Maps (in the
     * case of objects).
     *
     * @param json String JSON input
     * @return Java object graph of Maps matching JSON input,
     *         or null if an error occurred.
     */
    public static Map jsonToMaps(String json)
    {
        return jsonToMaps(json, new HashMap<String, Object>());
    }

    /**
     * Convert the passed in JSON string into a Java object graph
     * that consists solely of Java Maps where the keys are the
     * fields and the values are primitives or other Maps (in the
     * case of objects).
     *
     * @param json String JSON input
     * @param optionalArgs Map used to turn on / off additional features.
     * @return Java object graph of Maps matching JSON input,
     *         or null if an error occurred.
     */
    public static Map jsonToMaps(String json, Map<String, Object> optionalArgs)
    {
        try
        {
            optionalArgs.put(USE_MAPS, true);
            ByteArrayInputStream ba = new ByteArrayInputStream(json.getBytes("UTF-8"));
            JsonReader jr = new JsonReader(ba, optionalArgs);
            Object ret = jr.readObject();
            jr.close();

            if (ret instanceof Map)
            {
                return (Map) ret;
            }

            if (ret != null && ret.getClass().isArray())
            {
                JsonObject<String, Object> retMap = new JsonObject<>();
                retMap.put("@items", ret);
                return retMap;
            }
            JsonObject<String, Object> retMap = new JsonObject<>();
            retMap.put("@items", new Object[]{ret});
            return retMap;
        }
        catch (UnsupportedEncodingException e)
        {
            throw new JsonIoException("Could not convert JSON to Maps because your JVM does not support UTF-8", e);
        }
    }

    public JsonReader()
    {
        useMaps = false;
        input = null;
    }

    public JsonReader(InputStream inp)
    {
        this(inp, false);
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
        Map<String, Object> args = getArgs();
        args.clear();
        args.putAll(optionalArgs);
        Map<String, String> typeNames = (Map<String, String>) args.get(TYPE_NAME_MAP);
        if (typeNames != null)
        {   // Reverse the Map (this allows the users to only have a Map from type to short-hand name,
            // and not keep a 2nd map from short-hand name to type.
            Map<String, String> typeNameMap = new HashMap<>();
            for (Map.Entry<String, String> entry : typeNames.entrySet())
            {
                typeNameMap.put(entry.getValue(), entry.getKey());
            }
            args.put(TYPE_NAME_MAP_REVERSE, typeNameMap);   // replace with our reversed Map.
        }

        this.useMaps = Boolean.TRUE.equals(args.get(USE_MAPS));

        try
        {
            input = new FastPushbackReader(new BufferedReader(new InputStreamReader(inp, "UTF-8")));
            threadInput.set(input);
        }
        catch (UnsupportedEncodingException e)
        {
            throw new JsonIoException("Your JVM does not support UTF-8.  Get a better JVM.", e);
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
        if (useMaps)
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
        useMaps = false;
        return convertParsedMapsToJava(root);
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
        Resolver resolver = useMaps ? new MapResolver(objsRead, true) : new ObjectResolver(objsRead, false);
        resolver.createJavaObjectInstance(Object.class, root);
        Object graph = resolver.convertMapsToObjects((JsonObject<String, Object>) root);
        resolver.cleanup();
        return graph;
    }

    public static Object newInstance(Class c)
    {
        if (factory.containsKey(c))
        {
            return factory.get(c).newInstance(c);
        }
        return MetaUtils.newInstance(c);
    }

    public void close()
    {
        try
        {
            threadInput.remove();
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

    private static String getErrorMessage(String msg)
    {
        if (threadInput.get() != null)
        {
            return msg + "\nLast read: " + getLastReadSnippet() + "\nline: " + threadInput.get().line + ", col: " + threadInput.get().col;
        }
        return msg;
    }

    static Object error(String msg)
    {
        throw new JsonIoException(getErrorMessage(msg));
    }

    static Object error(String msg, Exception e)
    {
        throw new JsonIoException(getErrorMessage(msg), e);
    }

    private static String getLastReadSnippet()
    {
        if (threadInput.get() != null)
        {
            return threadInput.get().getLastSnippet();
        }
        return "";
    }

    /**
     * @param c Class to check
     * @return boolean true if the passed in class is a Java primitive.
     * @deprecated Use MetaUtils.isPrimitive(c) instead.
     */
    @Deprecated
    public static boolean isPrimitive(Class c)
    {
        return MetaUtils.isPrimitive(c);
    }

    /**
     * @param c Class to check
     * @return boolean true if the passed in class is a Logical Java primitive.  This adds
     * Class, Date, String, and the primitive wrappers to the list.
     * @deprecated Use MetaUtils.isLogicalPrimitive(c) instead.
     */
    @Deprecated
    public static boolean isLogicalPrimitive(Class c)
    {
        return MetaUtils.isLogicalPrimitive(c);
    }
}
