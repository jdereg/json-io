package com.cedarsoftware.util.io;

import java.io.InputStream;
import java.util.*;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.ReadOptionsBuilder;
import com.cedarsoftware.util.ClassUtilities;

@Deprecated
public class JsonReader {
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
    /** If set, this map will be used when writing @type values - allows short-hand abbreviations type names */
    public static final String TYPE_NAME_MAP = "TYPE_NAME_MAP";
    /** If set, this object will be called when a field is present in the JSON but missing from the corresponding class */
    public static final String MISSING_FIELD_HANDLER = "MISSING_FIELD_HANDLER";
    /** If set, use the specified ClassLoader */
    public static final String CLASSLOADER = "CLASSLOADER";
    /** Default maximum parsing depth */
    private static final int DEFAULT_MAX_PARSE_DEPTH = 1000;
    private final Map<String, Object> args = new HashMap<>();
    private static final Set<String> OPTIONAL_KEYS = new HashSet<>(Arrays.asList(
            CUSTOM_READER_MAP, NOT_CUSTOM_READER_MAP, USE_MAPS, UNKNOWN_OBJECT, "UNKNOWN_TYPE", FAIL_ON_UNKNOWN_TYPE,
            TYPE_NAME_MAP, MISSING_FIELD_HANDLER, CLASSLOADER));

    /**
     * "Jumper" APIs to support past API usage.
     */
    @Deprecated
    public static <T> T jsonToJava(String json) {
        return jsonToJava(json, new HashMap<>());
    }

    @Deprecated
    public static <T> T jsonToJava(String json, Map<String, Object> optionalArgs) {
        return (T)jsonToJava(json, optionalArgs, DEFAULT_MAX_PARSE_DEPTH);
    }

    @Deprecated
    public static Object jsonToJava(String json, Map<String, Object> optionalArgs, int maxDepth) {
        ReadOptionsBuilder builder = getReadOptionsBuilder(optionalArgs, maxDepth);
        return JsonIo.toObjects(json, builder.build(), null);
    }

    @Deprecated
    public static <T> T jsonToJava(InputStream inputStream, Map<String, Object> optionalArgs) {
        return jsonToJava(inputStream, optionalArgs, DEFAULT_MAX_PARSE_DEPTH);
    }

    @Deprecated
    public static <T> T jsonToJava(InputStream inputStream, Map<String, Object> optionalArgs, int maxDepth) {
        ReadOptionsBuilder builder = getReadOptionsBuilder(optionalArgs, maxDepth);
        return JsonIo.toObjects(inputStream, builder.build(), null);
    }

    @Deprecated
    public Object jsonObjectsToJava(JsonObject root) {
        ReadOptionsBuilder builder = new ReadOptionsBuilder().returnAsJavaObjects();
        return JsonIo.toObjects(root, builder.build(), null);
    }

    @Deprecated
    public static Map jsonToMaps(String json) {
        return jsonToMaps(json, DEFAULT_MAX_PARSE_DEPTH);
    }

    @Deprecated
    public static Map jsonToMaps(String json, int maxDepth) {
        return jsonToMaps(json, new HashMap<>(), maxDepth);
    }

    @Deprecated
    public static Map jsonToMaps(String json, Map<String, Object> optionalArgs, int maxDepth) {
        optionalArgs.put(USE_MAPS, true);
        ReadOptionsBuilder builder = getReadOptionsBuilder(optionalArgs, maxDepth);
        return JsonIo.toObjects(json, builder.build(), null);
    }

    /**
     * @return The arguments used to configure the JsonReader.  These are thread local.
     */
    @Deprecated
    public Map<String, Object> getArgs()
    {
        return args;
    }

    private static ReadOptionsBuilder getReadOptionsBuilder(Map<String, Object> optionalArgs, int maxDepth) {
        if (optionalArgs == null) {
            optionalArgs = new HashMap<>();
        }

        ReadOptionsBuilder builder = new ReadOptionsBuilder().maxDepth(maxDepth);
        boolean useMaps = com.cedarsoftware.util.Converter.convert(optionalArgs.get(USE_MAPS), boolean.class);

        if (useMaps) {
            builder.returnAsNativeJsonObjects();
        } else {
            builder.returnAsJavaObjects();
        }

        boolean failOnUnknownType = com.cedarsoftware.util.Converter.convert(optionalArgs.get(FAIL_ON_UNKNOWN_TYPE), boolean.class);
        builder.failOnUnknownType(failOnUnknownType);

        Object loader = optionalArgs.get(CLASSLOADER);
        ClassLoader classLoader;
        if (loader instanceof ClassLoader) {
            classLoader = (ClassLoader) loader;
        } else {
            classLoader = com.cedarsoftware.io.JsonReader.class.getClassLoader();
        }
        builder.classLoader(classLoader);

        Object type = optionalArgs.get("UNKNOWN_TYPE");
        if (type == null) {
            type = optionalArgs.get(UNKNOWN_OBJECT);
        }
        if (type instanceof Boolean) {
            builder.failOnUnknownType(true);
        } else if (type instanceof String) {
            Class<?> unknownType = ClassUtilities.forName((String) type, classLoader);
            builder.unknownTypeClass(unknownType);
            builder.failOnUnknownType(false);
        }

        Object aliasMap = optionalArgs.get(TYPE_NAME_MAP);
        if (aliasMap instanceof Map) {
            Map<String, String> aliases = (Map<String, String>) aliasMap;
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                builder.aliasTypeName(entry.getKey(), entry.getValue());
            }
        }

        Object missingFieldHandler = optionalArgs.get(MISSING_FIELD_HANDLER);
        if (missingFieldHandler instanceof com.cedarsoftware.io.JsonReader.MissingFieldHandler)
        {
            builder.missingFieldHandler((com.cedarsoftware.io.JsonReader.MissingFieldHandler) missingFieldHandler);
        }

        Object customReaderMap = optionalArgs.get(CUSTOM_READER_MAP);
        if (customReaderMap instanceof Map) {
            Map<String, Object> customReaders = (Map<String, Object>) customReaderMap;
            for (Map.Entry<String, Object> entry : customReaders.entrySet()) {
                try {
                    Class<?> clazz = Class.forName(entry.getKey());
                    builder.addCustomReaderClass(clazz, (com.cedarsoftware.io.JsonReader.JsonClassReader) entry.getValue());
                } catch (ClassNotFoundException e) {
                    String message = "Custom json-io reader class: " + entry.getKey() + " not found.";
                    throw new JsonIoException(message, e);
                } catch (ClassCastException e) {
                    String message = "Custom json-io reader for: " + entry.getKey() + " must be an instance of com.cedarsoftware.io.JsonReader.JsonClassReader.";
                    throw new JsonIoException(message, e);
                }
            }
        }

        Object notCustomReadersObject = optionalArgs.get(NOT_CUSTOM_READER_MAP);
        if (notCustomReadersObject instanceof Iterable) {
            Iterable<Class<?>> notCustomReaders = (Iterable<Class<?>>) notCustomReadersObject;
            for (Class<?> notCustomReader : notCustomReaders)
            {
                builder.addNotCustomReaderClass(notCustomReader);
            }
        }

        for (Map.Entry<String, Object> entry : optionalArgs.entrySet()) {
            if (OPTIONAL_KEYS.contains(entry.getKey())) {
                continue;
            }
            builder.addCustomOption(entry.getKey(), entry.getValue());
        }

        return builder;
    }

    /**
     * Common ancestor for JsonClassReader and JsonClassReaderEx.
     */
    @Deprecated
    public interface JsonClassReaderBase  { }

    /**
     * Implement this interface to add a custom JSON reader.
     */
    @Deprecated
    public interface JsonClassReader extends JsonClassReaderBase
    {
        /**
         * @param jOb Object being read.  Could be a fundamental JSON type (String, long, boolean, double, null, or JsonObject)
         * @param stack Deque of objects that have been read (Map of Maps view).
         * @return Object you wish to convert the jOb value into.
         */
        Object read(Object jOb, Deque<JsonObject> stack);
    }

    /**
     * Implement this interface to add a custom JSON reader.
     */
    @Deprecated
    public interface JsonClassReaderEx extends JsonClassReaderBase
    {
        /**
         * @param jOb Object being read.  Could be a fundamental JSON type (String, long, boolean, double, null, or JsonObject)
         * @param stack Deque of objects that have been read (Map of Maps view).
         * @param args Map of argument settings that were passed to JsonReader when instantiated.
         * @return Java Object you wish to convert the the passed in jOb into.
         */
        Object read(Object jOb, Deque<JsonObject> stack, Map<String, Object> args);
    }
}
