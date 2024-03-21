package com.cedarsoftware.util.io;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.WriteOptionsBuilder;

@Deprecated
public class JsonWriter
{
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
    /** same as above only internal for storing Field instances instead of strings. This avoid the initial argument content to be modified. */
    private static final String FIELD_BLACK_LIST = "FIELD_BLACK_LIST";
    /** If set, indicates that private variables of ENUMs are not to be serialized */
    public static final String ENUM_PUBLIC_ONLY = "ENUM_PUBLIC_ONLY";
    /** If set, longs are written in quotes (Javascript safe) */
    public static final String WRITE_LONGS_AS_STRINGS = "WLAS";
    /** If set, this map will be used when writing @type values - allows short-hand abbreviations type names */
    public static final String TYPE_NAME_MAP = "TYPE_NAME_MAP";
    /** If set, then @type -> @t, @keys -> @k, @items -> @i */
    public static final String SHORT_META_KEYS = "SHORT_META_KEYS";
    /** If set, null fields are not written */
    public static final String SKIP_NULL_FIELDS = "SKIP_NULL";
    /** If set, use the specified ClassLoader */
    public static final String CLASSLOADER = "CLASSLOADER";
    /** If set to true all maps are transferred to the format @keys[],@items[] regardless of the key_type */
    public static final String FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS = "FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS";
    private static final Set<String> OPTIONAL_KEYS = new HashSet<>(Arrays.asList(
            CUSTOM_WRITER_MAP, NOT_CUSTOM_WRITER_MAP, DATE_FORMAT, TYPE, PRETTY_PRINT, ENUM_PUBLIC_ONLY, WRITE_LONGS_AS_STRINGS,
            TYPE_NAME_MAP, SHORT_META_KEYS, SKIP_NULL_FIELDS, CLASSLOADER, FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS));

    /**
     * @see JsonWriter#objectToJson(Object, java.util.Map)
     * @param item Object (root) to serialized to JSON String.
     * @return String of JSON format representing complete object graph rooted by item.
     */
    @Deprecated
    public static String objectToJson(Object item)
    {
        return objectToJson(item, null);
    }

    /**
     * Convert a Java Object to a JSON String.
     *
     * @param item Object to convert to a JSON String.
     * @param optionalArgs (optional) Map of extra arguments indicating how dates are formatted,
     * what fields are written out (optional).  For Date parameters, use the public static
     * DATE_TIME key, and then use the ISO_DATE or ISO_DATE_TIME indicators.  Or you can specify
     * your own custom SimpleDateFormat String, or you can associate a SimpleDateFormat object,
     * in which case it will be used.  This setting is for both java.util.Date and java.sql.Date.
     * If the DATE_FORMAT key is not used, then dates will be formatted as longs.  This long can
     * be turned back into a date by using 'new Date(longValue)'.
     * @return String containing JSON representation of passed in object root.
     */
    @Deprecated
    public static String objectToJson(Object item, Map<String, Object> optionalArgs)
    {
        WriteOptionsBuilder builder = getWriteOptionsBuilder(optionalArgs);
        return JsonIo.toJson(item, builder.build());
    }

    private static WriteOptionsBuilder getWriteOptionsBuilder(Map<String, Object> optionalArgs) {
        if (optionalArgs == null) {
            optionalArgs = new HashMap<>();
        }

        WriteOptionsBuilder builder = new WriteOptionsBuilder();

        Object dateFormat = optionalArgs.get(DATE_FORMAT);
        if (dateFormat instanceof String)
        {
            builder.dateTimeFormat((String) dateFormat);
        } else if (dateFormat instanceof SimpleDateFormat)
        {
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
                    throw new JsonIoException(message, e);
                } catch (ClassCastException e) {
                    String message = "Custom json-io writer for: " + entry.getKey() + " must be an instance of com.cedarsoftware.io.JsonWriter.JsonClassWriter.";
                    throw new JsonIoException(message, e);
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
            if (OPTIONAL_KEYS.contains(entry.getKey())) {
                continue;
            }
            builder.addCustomOption(entry.getKey(), entry.getValue());
        }

        return builder;
    }

    /**
     * Common ancestor for JsonClassWriter and JsonClassWriterEx.
     */
    @Deprecated
    public interface JsonClassWriterBase
    { }

    /**
     * Implement this interface to customize the JSON output for a given class.
     */
    @Deprecated
    public interface JsonClassWriter extends JsonClassWriterBase
    {
        /**
         * When write() is called, it is expected that subclasses will write the appropriate JSON
         * to the passed in Writer.
         * @param o Object to be written in JSON format.
         * @param showType boolean indicating whether to show @type.
         * @param output Writer destination to where the actual JSON is written.
         * @throws IOException if thrown by the writer.  Will be caught at a higher level and wrapped in JsonIoException.
         */
        @Deprecated
        void write(Object o, boolean showType, Writer output) throws IOException;

        /**
         * @return boolean true if the class being written has a primitive (non-object) form.
         */
        @Deprecated
        boolean hasPrimitiveForm();

        /**
         * This method will be called to write the item in primitive form (if the response to hasPrimitiveForm()
         * was true).
         * @param o Object to be written
         * @param output Writer destination to where the actual JSON is written.
         * @throws IOException if thrown by the writer.  Will be caught at a higher level and wrapped in JsonIoException.
         */
        @Deprecated
        void writePrimitiveForm(Object o, Writer output) throws IOException;
    }

    /**
     * Implement this interface to customize the JSON output for a given class.
     */
    @Deprecated
    public interface JsonClassWriterEx extends JsonClassWriterBase
    {
        /**
         * When write() is called, it is expected that subclasses will write the appropriate JSON
         * to the passed in Writer.
         * @param o Object to be written in JSON format.
         * @param showType boolean indicating whether to show @type.
         * @param output Writer destination to where the actual JSON is written.
         * @param args Map of 'settings' arguments initially passed into the JsonWriter.
         * @throws IOException if thrown by the writer.  Will be caught at a higher level and wrapped in JsonIoException.
         */
        @Deprecated
        void write(Object o, boolean showType, Writer output, Map<String, Object> args) throws IOException;
    }
}
