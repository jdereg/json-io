package com.cedarsoftware.io;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.Convention;
import com.cedarsoftware.util.FastByteArrayInputStream;
import com.cedarsoftware.util.FastByteArrayOutputStream;
import com.cedarsoftware.util.convert.Converter;
import com.cedarsoftware.util.convert.DefaultConverterOptions;

/**
 * This is the main API for json-io.  Use these methods to convert:<br/>
 * <ul>
 * <li><b>1. Input</b>: Java root | JsonObject root <b>Output</b>: JSON<pre>String json = JsonIo.toJson(JavaObject | JsonObject root, writeOptions)</pre></li>
 * <li><b>2. Input</b>: Java root | JsonObject root, <b>Output</b>: JSON -> outputStream<pre>JsonIo.toJson(OutputStream, JavaObject | JsonObject root, writeOptions)</pre></li>
 * <li><b>3. Input</b>: JSON, <b>Output</b>: Java objects | JsonObject<pre>BillingInfo billInfo = JsonIo.toObjects(String | InputStream, readOptions, BillingInfo.class)</pre></li>
 * <li><b>4. Input</b>: JsonObject root, <b>Output</b>: Java objects<pre>BillingInfo billInfo = JsonIo.toObjects(JsonObject, readOptions, BillingInfo.class)</pre></li>
 * Often, the goal is to get JSON to Java objects and from Java objects to JSON.  That is #1 and #3 above. <br/>
 * <br/>
 * For approaches #1 and #2 above, json-io will check the root object type (regular Java class or JsonObject (Map)
 * instance) to know which type of Object Graph it is serializing to JSON.<br/>
 * <br/>
 * There are occasions where you may just want the raw JSON data, without anchoring it to a set of "DTO" Java objects.
 * For example, you may have an extreme amount of data, and you want to process it as fast as possible, and in
 * streaming mode.  The JSON specification has great primitives which are universally useful in many languages. In
 * Java that is boolean, null, long [or BigInteger], and double [or BigDecimal], and String.<br/>
 * <br/>
 * When JsonObject is returned [option #3 or #4 above with readOptions.returnType(ReturnType.JSON_VALUES)], your root
 * value will represent one of:
 * <ul>JSON object {...}<br/>
 * JSON array [...]<br/>
 * JSON primitive (boolean true/false, null, long, double, String).</ul>
 * <br/>
 * <b>{...} JsonObject</b> implements the Map interface and represents any JSON object {...}.<br/>
 * <br/>
 * <b>[...] JsonObject</b> implements the Map interface and the value associated to the @items key will represent the
 * JSON array [...].<br/>
 * <br/>
 * <b>Primitive or JsonObject</b> If the root of the JSON is a String, Number (Long or Double), Boolean, or null, not an
 * object { ... } nor an array { ... }, then it will be a String, long, true, false, null, double or BigDecimal.  If
 * the primitive value is wrapped in a JSON object {"value": 10} then it will be returned as a Map (JsonObject).<br/>
 * <br/>
 * If you have a return object graph of JsonObject (Map-of-Maps) and want to turn these into Java (DTO) objects, use #4.
 * To turn the JsonObject graph back into JSON, use option #1 or #2.<br/>
 * <br/>
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * @author Kenny Partlow (kpartlow@gmail.com)
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
public class JsonIo {

    /**
     * Statically accessed class.
     */
    private JsonIo() {
    }

    /**
     * Convert the passed in Java source object to JSON.
     * @param srcObject Java instance to convert to JSON format.  Can be a JsonObject that was loaded earlier
     *                  via .toObjects() with readOptions.returnAsNativeJsonObjects().
     * @param writeOptions Feature options settings to control the JSON output.  Can be null,
     *                     in which case, default settings will be used.
     * @return String of JSON that represents the srcObject in JSON format.
     * @throws JsonIoException A runtime exception thrown if any errors happen during serialization
     */
    public static String toJson(Object srcObject, WriteOptions writeOptions) {
        FastByteArrayOutputStream out = new FastByteArrayOutputStream();
        try (JsonWriter writer = new JsonWriter(out, writeOptions)) {
            writer.write(srcObject);
            return out.toString();
        } catch (JsonIoException je) {
            throw je;
        } catch (Exception e) {
            throw new JsonIoException("Unable to convert object to JSON", e);
        }
    }

    /**
     * Convert the passed in Java source object to JSON.  If you want a copy of the JSON that was written to the
     * OutputStream, you can wrap the output stream before calling this method, like this:<br/>
     * <br/>
     * <code>ByteArrayOutputStream baos = new ByteArrayOutputStream(originalOutputStream);<br/>
     * JsonIo.toJson(baos, source, writeOptions);<br/>
     * baos.flush();<br/>
     * String json = new String(baos.toByteArray(), StandardCharsets.UTF_8);<br/>
     * </code><br/>
     * @param out OutputStream destination for the JSON output.  The OutputStream will be closed by default.  If
     *            you don't want this, set writeOptions.closeStream(false).  This is useful for creating NDJSON,
     *            where multiple JSON objects are written to the stream, separated by a newline.
     * @param source Java instance to convert to JSON format.  Can be a JsonObject that was loaded earlier
     *                  via .toObjects() with readOptions.returnAsNativeJsonObjects().
     * @param writeOptions Feature options settings to control the JSON output.  Can be null,
     *                     in which case, default settings will be used.
     * @throws JsonIoException A runtime exception thrown if any errors happen during serialization
     */
    public static void toJson(OutputStream out, Object source, WriteOptions writeOptions) {
        Convention.throwIfNull(out, "OutputStream cannot be null");
        JsonWriter writer = null;
        try {
            writer = new JsonWriter(out, writeOptions);
            writer.write(source);
        } catch (Exception e) {
            throw new JsonIoException("Unable to convert object and send in JSON format to OutputStream.", e);
        }
        finally {
            if (writeOptions.isCloseStream()) {
                if (writer != null) {
                    writer.close();
                }
            }
        }
    }

    /**
     * Convert the passed in JSON to Java Objects.
     * @param json String containing JSON content.
     * @param readOptions Feature options settings to control the JSON processing.  Can be null,
     *                     in which case, default settings will be used.
     * @param rootType Class of the root type of object that will be returned. Can be null, in which
     *                 case a best-guess will be made for the Class type of the return object.  If it
     *                 has an @type meta-property that will be used, otherwise the JSON types { ... }
     *                 will return a Map, [...] will return Object[] or Collection, and the primtive
     *                 types will be returned (String, long, Double, boolean, or null).
     * @return rootType Java instance that represents the Java equivalent of the passed in JSON string.
     * @throws JsonIoException A runtime exception thrown if any errors happen during serialization
     */
    public static <T> T toObjects(String json, ReadOptions readOptions, Class<T> rootType) {
        if (json == null) {
            json = "";
        }
        return toObjects(new FastByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), readOptions, rootType);
    }

    /**
     * Convert the passed in JSON to Java Objects.
     * @param in InputStream bringing JSON content.  By default, it will be closed.  If you don't want
     *           it closed after reading, set readOptions.closeStream(false).
     * @param readOptions Feature options settings to control the JSON processing.  Can be null,
     *                     in which case, default settings will be used.
     * @param rootType Class of the root type of object that will be returned. Can be null, in which
     *                 case a best-guess will be made for the Class type of the return object.  If it
     *                 has a @type meta-property that will be used, otherwise a JsonObject will be returned.
     * @return rootType Java instance that represents the Java equivalent of the JSON input.  If the returnType() on
     * ReadOptions is set to ReturnType.JSON_OBJECTS, then the root return value will be a JsonObject, which can
     * represent a JSON object {...}, a JSON array [...], or a JSON primitive.  JsonObject has .is*() methods on
     * it to determine the type of object represented.  If the type is a JSON primitive, use .getValue() on JSON
     * object to obtain the primitive value.
     * @throws JsonIoException A runtime exception thrown if any errors happen during serialization
     */
    public static <T> T toObjects(InputStream in, ReadOptions readOptions, Class<T> rootType) {
        Convention.throwIfNull(in, "InputStream cannot be null");

        JsonReader jr = null;
        try  {
            jr = new JsonReader(in, readOptions);
            T root = jr.readObject(rootType);
            return root;
        } catch (JsonIoException je) {
            throw je;
        } catch (Exception e) {
            throw new JsonIoException(e);
        }
        finally {
            if (readOptions != null && readOptions.isCloseStream()) {
                if (jr != null) {
                    jr.close();
                }
            }
        }
    }

    /**
     * Convert a root JsonObject (Map) that represents parsed JSON, into an actual Java object.  This Map-of-Map roots
     * would have come from a prior API call to JsonIo.toObjects(String) or JsonIo.toObjects(InputStream) with the
     * new ReadOptionBuilder().returnAsJsonObjects() option set.  This option allows you to read any JSON because it
     * is only stuffing it into Maps, not Java objects.  This API can take this Map-of-Maps representation of JSON,
     * and then recreate the Java objects from it.  It can do this, because the Map-of-Map's it returns are subclasses
     * of Java's Map that contain a 'type' field, and it will use this to recreate the correct Java object when written
     * via toJson and the Map (JsonObject) is passed as the root.
     * @param readOptions ReadOptions to control the feature options. Can be null to take the defaults.
     * @param rootType The class that represents, in Java, the root of the underlying JSON from which the JsonObject
     *                 was loaded.
     * @return a typed Java instance object graph.
     */
    public static <T> T toObjects(JsonObject jsonObject, ReadOptions readOptions, Class<T> rootType) {
        if (readOptions == null || !readOptions.isReturningJavaObjects()) {
            readOptions = new ReadOptionsBuilder(readOptions).returnAsJavaObjects().build();
        }
        JsonReader reader = new JsonReader(readOptions);
        return reader.toJavaObjects(jsonObject, rootType);
    }

    /**
     * Format the passed in JSON into multi-line, indented format, commonly used in JSON online editors.
     * @param readOptions ReadOptions to control the feature options. Can be null to take the defaults.
     * @param writeOptions WriteOptions to control the feature options. Can be null to take the defaults.
     * @param json String JSON content.
     * @return String JSON formatted in human-readable, standard multi-line, indented format.
     */
    public static String formatJson(String json, ReadOptions readOptions, WriteOptions writeOptions) {
        if (writeOptions == null || !writeOptions.isPrettyPrint()) {
            writeOptions = new WriteOptionsBuilder(writeOptions).prettyPrint(true).build();
        }

        if (readOptions == null || !readOptions.isReturningJavaObjects()) {
            readOptions = new ReadOptionsBuilder(readOptions).returnAsJavaObjects().build();
        }

        Object object = toObjects(json, readOptions, null);
        return toJson(object, writeOptions);
    }

    /**
     * Format the passed in JSON into multi-line, indented format, commonly used in JSON online editors.
     * @param json String JSON content.
     * @return String JSON formatted in human readable, standard multi-line, indented format.
     */
    public static String formatJson(String json) {
        return formatJson(json, null, null);
    }

    /**
     * Copy an object graph using JSON.
     * @param source Object root object to copy
     * @param readOptions ReadOptions feature settings. Can be null for default ReadOptions.
     * @param writeOptions WriteOptions feature settings. Can be null for default WriteOptions.
     * @return A new, duplicate instance of the original.
     */
    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(Object source, ReadOptions readOptions, WriteOptions writeOptions) {
        if (source == null) {
            // They asked to copy null.  The copy of null is null.
            return null;
        }

        writeOptions = new WriteOptionsBuilder(writeOptions).showTypeInfoMinimal().shortMetaKeys(true).withExtendedAliases().build();
        readOptions = new ReadOptionsBuilder(readOptions).withExtendedAliases().build();

        String json = toJson(source, writeOptions);
        return (T) toObjects(json, readOptions, source.getClass());
    }

    /**
     * Call this method to see all the conversions offered.
     * @param args
     */
    public static void main(String[] args) {
        String json = toJson(new Converter(new DefaultConverterOptions()).getSupportedConversions(), new WriteOptionsBuilder().prettyPrint(true).showTypeInfoNever().build());
        System.out.println("json-io supported conversions (source type to target types):");
        System.out.println(json);
    }

    /**
     * Convert an old-style Map of options to a ReadOptionsBuilder. It is not recommended to use this API long term,
     * however, this API will be the fastest route to bridge an old installation using json-io to the new API.
     * @param optionalArgs Map of old json-io options
     * @return ReadOptionsBuilder
     */
    public static ReadOptionsBuilder getReadOptionsBuilder(Map<String, Object> optionalArgs) {
        if (optionalArgs == null) {
            optionalArgs = new HashMap<>();
        }
        ReadOptionsBuilder builder = new ReadOptionsBuilder();

        int maxParseDepth = 1000;
        if (optionalArgs.containsKey(MAX_PARSE_DEPTH)) {
            maxParseDepth = com.cedarsoftware.util.Converter.convert(optionalArgs.get(MAX_PARSE_DEPTH), int.class);
        }
        builder.maxDepth(maxParseDepth);
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
        if (missingFieldHandler instanceof com.cedarsoftware.io.JsonReader.MissingFieldHandler) {
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
                    throw new com.cedarsoftware.io.JsonIoException(message, e);
                } catch (ClassCastException e) {
                    String message = "Custom json-io reader for: " + entry.getKey() + " must be an instance of com.cedarsoftware.io.JsonReader.JsonClassReader.";
                    throw new com.cedarsoftware.io.JsonIoException(message, e);
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
            if (OPTIONAL_READ_KEYS.contains(entry.getKey())) {
                continue;
            }
            builder.addCustomOption(entry.getKey(), entry.getValue());
        }

        return builder;
    }

    /**
     * Convert an old-style Map of options to a WriteOptionsBuilder. It is not recommended to use this API long term,
     * however, this API will be the fastest route to bridge an old installation using json-io to the new API.
     * @param optionalArgs Map of old json-io options
     * @return WriteOptionsBuilder
     */
    public static WriteOptionsBuilder getWriteOptionsBuilder(Map<String, Object> optionalArgs) {
        if (optionalArgs == null) {
            optionalArgs = new HashMap<>();
        }

        WriteOptionsBuilder builder = new WriteOptionsBuilder();

        Object dateFormat = optionalArgs.get(DATE_FORMAT);
        if (dateFormat instanceof String)
        {
            builder.dateTimeFormat((String) dateFormat);
        } else if (dateFormat instanceof SimpleDateFormat) {
            builder.dateTimeFormat(((SimpleDateFormat) dateFormat).toPattern());
        }

        Boolean showType = com.cedarsoftware.util.Converter.convert(optionalArgs.get(TYPE), Boolean.class);
        if (showType == null) {
            builder.showTypeInfoMinimal();
        } else if (showType) {
            builder.showTypeInfoAlways();
        } else {
            builder.showTypeInfoNever();
        }

        boolean prettyPrint = com.cedarsoftware.util.Converter.convert(optionalArgs.get(PRETTY_PRINT), boolean.class);
        builder.prettyPrint(prettyPrint);

        boolean writeLongsAsStrings = com.cedarsoftware.util.Converter.convert(optionalArgs.get(WRITE_LONGS_AS_STRINGS), boolean.class);
        builder.writeLongsAsStrings(writeLongsAsStrings);

        boolean shortMetaKeys = com.cedarsoftware.util.Converter.convert(optionalArgs.get(SHORT_META_KEYS), boolean.class);
        builder.shortMetaKeys(shortMetaKeys);

        boolean skipNullFields = com.cedarsoftware.util.Converter.convert(optionalArgs.get(SKIP_NULL_FIELDS), boolean.class);
        builder.skipNullFields(skipNullFields);

        boolean forceMapOutputAsTwoArrays = com.cedarsoftware.util.Converter.convert(optionalArgs.get(FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS), boolean.class);
        builder.forceMapOutputAsTwoArrays(forceMapOutputAsTwoArrays);

        boolean writeEnumsAsJsonObject = com.cedarsoftware.util.Converter.convert(optionalArgs.get(ENUM_PUBLIC_ONLY), boolean.class);
        builder.writeEnumAsJsonObject(writeEnumsAsJsonObject);

        Object loader = optionalArgs.get(CLASSLOADER);
        ClassLoader classLoader;
        if (loader instanceof ClassLoader) {
            classLoader = (ClassLoader) loader;
        } else {
            classLoader = com.cedarsoftware.io.JsonReader.class.getClassLoader();
        }
        builder.classLoader(classLoader);

        Object aliasMap = optionalArgs.get(TYPE_NAME_MAP);
        if (aliasMap instanceof Map) {
            Map<String, String> aliases = (Map<String, String>) aliasMap;
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                builder.aliasTypeName(entry.getKey(), entry.getValue());
            }
        }

        Object customWriterMap = optionalArgs.get(CUSTOM_WRITER_MAP);
        if (customWriterMap instanceof Map) {
            Map<String, Object> customWriters = (Map<String, Object>) customWriterMap;
            for (Map.Entry<String, Object> entry : customWriters.entrySet()) {
                try {
                    Class<?> clazz = Class.forName(entry.getKey());
                    builder.addCustomWrittenClass(clazz, (com.cedarsoftware.io.JsonWriter.JsonClassWriter) entry.getValue());
                } catch (ClassNotFoundException e) {
                    String message = "Custom json-io writer class: " + entry.getKey() + " not found.";
                    throw new com.cedarsoftware.io.JsonIoException(message, e);
                } catch (ClassCastException e) {
                    String message = "Custom json-io writer for: " + entry.getKey() + " must be an instance of com.cedarsoftware.io.JsonWriter.JsonClassWriter.";
                    throw new com.cedarsoftware.io.JsonIoException(message, e);
                }
            }
        }

        Object notCustomWritersObject = optionalArgs.get(NOT_CUSTOM_WRITER_MAP);
        if (notCustomWritersObject instanceof Iterable) {
            Iterable<Class<?>> notCustomWriters = (Iterable<Class<?>>) notCustomWritersObject;
            for (Class<?> notCustomWriter : notCustomWriters)
            {
                builder.addNotCustomWrittenClass(notCustomWriter);
            }
        }

        Object fieldSpecifiers = optionalArgs.get(FIELD_SPECIFIERS);
        if (fieldSpecifiers instanceof Map) {
            Map<Class<?>, Collection<String>> includedFields = (Map<Class<?>, Collection<String>>) fieldSpecifiers;
            for (Map.Entry<Class<?>, Collection<String>> entry : includedFields.entrySet()) {
                for (String fieldName : entry.getValue()) {
                    builder.addIncludedField(entry.getKey(), fieldName);
                }
            }
        }

        Object fieldBlackList = optionalArgs.get(FIELD_NAME_BLACK_LIST);
        if (fieldBlackList instanceof Map) {
            Map<Class<?>, Collection<String>> excludedFields = (Map<Class<?>, Collection<String>>) fieldBlackList;
            for (Map.Entry<Class<?>, Collection<String>> entry : excludedFields.entrySet()) {
                for (String fieldName : entry.getValue()) {
                    builder.addExcludedField(entry.getKey(), fieldName);
                }
            }
        }

        for (Map.Entry<String, Object> entry : optionalArgs.entrySet())
        {
            if (OPTIONAL_WRITE_KEYS.contains(entry.getKey())) {
                continue;
            }
            builder.addCustomOption(entry.getKey(), entry.getValue());
        }

        return builder;
    }

    //
    // READ Option Keys (older method of specifying options) -----------------------------------------------------------
    //
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
    /** If set, this map will be used when writing @type values - allows short-hand abbreviations type names */
    public static final String TYPE_NAME_MAP = "TYPE_NAME_MAP";
    /** If set, this object will be called when a field is present in the JSON but missing from the corresponding class */
    public static final String MISSING_FIELD_HANDLER = "MISSING_FIELD_HANDLER";
    /** If set, use the specified ClassLoader */
    public static final String CLASSLOADER = "CLASSLOADER";
    /** Default maximum parsing depth */
    public static final String MAX_PARSE_DEPTH = "MAX_PARSE_DEPTH";
    private static final Set<String> OPTIONAL_READ_KEYS = new HashSet<>(Arrays.asList(
            CUSTOM_READER_MAP, NOT_CUSTOM_READER_MAP, USE_MAPS, UNKNOWN_OBJECT, "UNKNOWN_TYPE", FAIL_ON_UNKNOWN_TYPE,
            TYPE_NAME_MAP, MISSING_FIELD_HANDLER, CLASSLOADER));
    
    //
    // WRITE Option Keys (older method of specifying options) -----------------------------------------------------------
    //
    /** If set, this maps class ==> CustomWriter */
    public static final String CUSTOM_WRITER_MAP = "CUSTOM_WRITERS";
    /** If set, this maps class ==> CustomWriter */
    public static final String NOT_CUSTOM_WRITER_MAP = "NOT_CUSTOM_WRITERS";
    /** Set the date format to use within the JSON output */
    public static final String DATE_FORMAT = "DATE_FORMAT";
    /** Constant for use as DATE_FORMAT value */
    public static final String ISO_DATE_FORMAT = "yyyy-MM-dd";
    /** Constant for use as DATE_FORMAT value */
    public static final String ISO_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    /** Force @type always */
    public static final String TYPE = "TYPE";
    /** Force nicely formatted JSON output */
    public static final String PRETTY_PRINT = "PRETTY_PRINT";
    /** Set value to a {@code Map<Class, List<String>>} which will be used to control which fields on a class are output */
    public static final String FIELD_SPECIFIERS = "FIELD_SPECIFIERS";
    /** Set value to a {@code Map<Class, List<String>>} which will be used to control which fields on a class are not output. Black list has always priority to FIELD_SPECIFIERS */
    public static final String FIELD_NAME_BLACK_LIST = "FIELD_NAME_BLACK_LIST";
    /** If set, indicates that private variables of ENUMs are not to be serialized */
    public static final String ENUM_PUBLIC_ONLY = "ENUM_PUBLIC_ONLY";
    /** If set, longs are written in quotes (Javascript safe) */
    public static final String WRITE_LONGS_AS_STRINGS = "WLAS";
    /** If set, then @type -> @t, @keys -> @k, @items -> @i */
    public static final String SHORT_META_KEYS = "SHORT_META_KEYS";
    /** If set, null fields are not written */
    public static final String SKIP_NULL_FIELDS = "SKIP_NULL";
    /** If set to true all maps are transferred to the format @keys[],@items[] regardless of the key_type */
    public static final String FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS = "FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS";
    private static final Set<String> OPTIONAL_WRITE_KEYS = new HashSet<>(Arrays.asList(
            CUSTOM_WRITER_MAP, NOT_CUSTOM_WRITER_MAP, DATE_FORMAT, TYPE, PRETTY_PRINT, ENUM_PUBLIC_ONLY, WRITE_LONGS_AS_STRINGS,
            TYPE_NAME_MAP, SHORT_META_KEYS, SKIP_NULL_FIELDS, CLASSLOADER, FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS));
}
