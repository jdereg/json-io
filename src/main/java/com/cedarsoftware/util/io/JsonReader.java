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
    /**
     * "Jumper" APIs to support past API usage.
     */
    @Deprecated
    public static <T> T jsonToJava(String json) {
        return jsonToJava(json, new HashMap<>());
    }

    @Deprecated
    public static <T> T jsonToJava(String json, Map<String, Object> optionalArgs) {
        return (T)jsonToJava(json, optionalArgs, 1000);
    }

    @Deprecated
    public static Object jsonToJava(String json, Map<String, Object> optionalArgs, int maxDepth) {
        ReadOptionsBuilder builder = getReadOptionsBuilder(optionalArgs, maxDepth);
        return JsonIo.toObjects(json, builder.build(), null);
    }

    @Deprecated
    public static <T> T jsonToJava(InputStream inputStream, Map<String, Object> optionalArgs) {
        return jsonToJava(inputStream, optionalArgs, 1000);
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
        return jsonToMaps(json, 1000);
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
