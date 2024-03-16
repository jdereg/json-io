package com.cedarsoftware.util.io;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.ReadOptionsBuilder;
import com.cedarsoftware.util.ClassUtilities;

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
    /** Pointer to the current ObjectResolver (automatically placed in the Map) */
    public static final String OBJECT_RESOLVER = "OBJECT_RESOLVER";
    /** If set, this map will be used when writing @type values - allows short-hand abbreviations type names */
    public static final String TYPE_NAME_MAP = "TYPE_NAME_MAP";
    /** If set, this object will be called when a field is present in the JSON but missing from the corresponding class */
    public static final String MISSING_FIELD_HANDLER = "MISSING_FIELD_HANDLER";
    /** If set, use the specified ClassLoader */
    public static final String CLASSLOADER = "CLASSLOADER";
    /** This map is the reverse of the TYPE_NAME_MAP (value ==> key) */
    static final String TYPE_NAME_MAP_REVERSE = "TYPE_NAME_MAP_REVERSE";
    /** Default maximum parsing depth */
    static final int DEFAULT_MAX_PARSE_DEPTH = 1000;
    
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
        optionalArgs.put("USE_MAPS", true);
        ReadOptionsBuilder builder = getReadOptionsBuilder(optionalArgs, maxDepth);
        return JsonIo.toObjects(json, builder.build(), null);
    }

    private static ReadOptionsBuilder getReadOptionsBuilder(Map<String, Object> optionalArgs, int maxDepth) {
        if (optionalArgs == null) {
            optionalArgs = new HashMap<>();
        }

        ReadOptionsBuilder builder = new ReadOptionsBuilder().maxDepth(maxDepth);
        boolean useMaps = com.cedarsoftware.util.Converter.convert(optionalArgs.get("USE_MAPS"), boolean.class);

        if (useMaps) {
            builder.returnAsNativeJsonObjects();
        } else {
            builder.returnAsJavaObjects();
        }

        boolean failOnUnknownType = com.cedarsoftware.util.Converter.convert(optionalArgs.get("FAIL_ON_UNKNOWN_TYPE"), boolean.class);
        builder.failOnUnknownType(failOnUnknownType);

        Object loader = optionalArgs.get("CLASSLOADER");
        ClassLoader classLoader;
        if (loader instanceof ClassLoader) {
            classLoader = (ClassLoader) loader;
            builder.classLoader(classLoader);
        } else {
            classLoader = com.cedarsoftware.io.JsonReader.class.getClassLoader();
        }

        Object type = optionalArgs.get("UNKNOWN_TYPE");
        if (type instanceof Boolean) {
            builder.failOnUnknownType(true);
        } else if (type instanceof String) {
            Class<?> unknownType = ClassUtilities.forName((String) type, classLoader);
            builder.unknownTypeClass(unknownType);
            builder.failOnUnknownType(false);
        }

        Object aliasMap = optionalArgs.get("TYPE_NAME_MAP");
        if (aliasMap instanceof Map) {
            Map<String, String> aliases = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                builder.aliasTypeName(entry.getKey(), entry.getValue());
            }
        }

        // TODO
        Object customReaderMap = optionalArgs.get("CUSTOM_READER_MAP");
        if (customReaderMap instanceof Map) {
//            Map<Class<?>, JsonClassReader> customReaders = new LinkedHashMap<>();
//            for (Map.Entry<Class<?>, JsonClassReader> entry : customReaders.entrySet()) {
//                builder.aliasTypeName(entry.getKey(), entry.getValue());
//            }
            throw new JsonIoException("CUSTOM_READER option not supported in this version of json-io");
        }

        // TODO
        Object notCustomReaderMap = optionalArgs.get("NOT_CUSTOM_READER_MAP");
        if (notCustomReaderMap instanceof Map) {
//            Map<Class<?>, JsonClassReader> customReaders = new LinkedHashMap<>();
//            for (Map.Entry<Class<?>, JsonClassReader> entry : customReaders.entrySet()) {
//                builder.aliasTypeName(entry.getKey(), entry.getValue());
//            }
            throw new JsonIoException("NOT_CUSTOM_READER option not supported in this version of json-io");
        }

        return builder;
    }

}
