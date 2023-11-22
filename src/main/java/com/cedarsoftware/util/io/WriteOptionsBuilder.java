package com.cedarsoftware.util.io;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.cedarsoftware.util.io.JsonWriter.*;

public class WriteOptionsBuilder {

    private final ConcurrentMap<String, Object> writeOptions;

    public WriteOptionsBuilder() {
        this.writeOptions = new ConcurrentHashMap<>();
    }

    public WriteOptionsBuilder withDefaultOptimizations() {
        return withIsoDateTimeFormat()
                .withShortMetaKeys()
                .skipNullFields();
    }

    public WriteOptionsBuilder skipNullFields() {
        writeOptions.put(JsonWriter.SKIP_NULL_FIELDS, true);
        return this;
    }

    public WriteOptionsBuilder withPrettyPrint() {
        writeOptions.put(JsonWriter.PRETTY_PRINT, true);
        return this;
    }

    public WriteOptionsBuilder writeLongsAsStrings() {
        writeOptions.put(JsonWriter.WRITE_LONGS_AS_STRINGS, true);
        return this;
    }

    /**
     * This option only applies when you're writing enums as objects so we
     * force the enum write to be ENUMS_AS_OBJECTS
     *
     * @return
     */
    public WriteOptionsBuilder doNotWritePrivateEnumFields() {
        writeOptions.put(JsonWriter.ENUM_PUBLIC_ONLY, true);
        writeOptions.put(JsonWriter.WRITE_ENUMS_AS_OBJECTS, true);
        return this;
    }

    public WriteOptionsBuilder writeEnumsAsObjects() {
        writeOptions.put(JsonWriter.WRITE_ENUMS_AS_OBJECTS, true);
        return this;
    }

    // Map with all String keys, will still output in the @keys/@items approach
    public WriteOptionsBuilder forceMapOutputAsKeysAndItems() {
        writeOptions.put(JsonWriter.FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS, true);
        return this;
    }

    // Map with all String keys, will output as a JSON object, with the keys of the Map being the keys of the JSON
    // Object, which is the default and more natural.
    public WriteOptionsBuilder doNotForceMapOutputAsKeysAndItems() {
        writeOptions.put(JsonWriter.FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS, false);
        return this;
    }

    public WriteOptionsBuilder withClassLoader(ClassLoader classLoader) {
        writeOptions.put(JsonWriter.CLASSLOADER, classLoader);
        return this;
    }

    public WriteOptionsBuilder writeLocalDateAsTimeStamp() {
        return withCustomWriter(LocalDate.class, new Writers.LocalDateAsTimestamp());
    }

    public WriteOptionsBuilder withIsoDateTimeFormat() {
        return withDateFormat(JsonWriter.ISO_DATE_TIME_FORMAT);
    }

    public WriteOptionsBuilder withIsoDateFormat() {
        return withDateFormat(JsonWriter.ISO_DATE_FORMAT);
    }

    public WriteOptionsBuilder withDateFormat(String format) {
        writeOptions.put(JsonWriter.DATE_FORMAT, format);
        return this;
    }

    public WriteOptionsBuilder withShortMetaKeys() {
        writeOptions.put(JsonWriter.SHORT_META_KEYS, true);
        return this;
    }

    public WriteOptionsBuilder noTypeInfo() {
        writeOptions.put(TYPE, false);
        return this;
    }

    public WriteOptionsBuilder forceTypeInfo() {
        writeOptions.put(TYPE, Boolean.TRUE);
        return this;
    }

    public WriteOptionsBuilder minTypeInfo() {
        writeOptions.remove(TYPE);
        return this;
    }

    public WriteOptionsBuilder withFieldNameBlackList(Class<?> c, List<String> fields) {
        MetaUtilsMap.computeMapIfAbsent(writeOptions, FIELD_NAME_BLACK_LIST).put(c, fields);
        return this;
    }

    public WriteOptionsBuilder withFieldNameBlackListMap(Map<? extends Class<?>, List<String>> map) {
        MetaUtilsMap.computeMapIfAbsent(writeOptions, FIELD_NAME_BLACK_LIST).putAll(map);
        return this;
    }

    public WriteOptionsBuilder withFieldSpecifier(Class<?> c, List<String> fields) {
        MetaUtilsMap.computeMapIfAbsent(writeOptions, FIELD_SPECIFIERS).put(c, fields);
        return this;
    }

    public WriteOptionsBuilder withFieldSpecifiersMap(Map<? extends Class<?>, List<String>> map) {
        MetaUtilsMap.computeMapIfAbsent(writeOptions, FIELD_SPECIFIERS).putAll(map);
        return this;
    }

    public WriteOptionsBuilder withCustomTypeName(Class<?> type, String newTypeName) {
        return withCustomTypeName(type.getName(), newTypeName);
    }

    public WriteOptionsBuilder withCustomTypeName(String type, String newTypeName) {
        assertTypesAreBeingOutput();
        MetaUtilsMap.computeMapIfAbsent(writeOptions, TYPE_NAME_MAP).put(type, newTypeName);
        return this;
    }

    public WriteOptionsBuilder withCustomTypeNameMap(Map<String, String> map) {
        assertTypesAreBeingOutput();
        MetaUtilsMap.computeMapIfAbsent(writeOptions, TYPE_NAME_MAP).putAll(map);
        return this;
    }

    public WriteOptionsBuilder withCustomWriter(Class<?> c, JsonWriter.JsonClassWriter writer) {
        MetaUtilsMap.computeMapIfAbsent(writeOptions, CUSTOM_WRITER_MAP).put(c, writer);
        return this;
    }

    public WriteOptionsBuilder withCustomWriterMap(Map<? extends Class<?>, ? extends JsonWriter.JsonClassWriter> map) {
        MetaUtilsMap.computeMapIfAbsent(writeOptions, CUSTOM_WRITER_MAP).putAll(map);
        return this;
    }

    public WriteOptionsBuilder withNoCustomizationFor(Class<?> c) {
        MetaUtilsMap.computeSetIfAbsent(writeOptions, NOT_CUSTOM_WRITER_MAP).add(c);
        return this;
    }

    public WriteOptionsBuilder withNoCustomizationsFor(Collection<Class<?>> collection) {
        MetaUtilsMap.computeSetIfAbsent(writeOptions, NOT_CUSTOM_WRITER_MAP).addAll(collection);
        return this;
    }

    public Map<String, Object> build() {
        return writeOptions;
    }

    private void assertTypesAreBeingOutput() {
        Boolean setting = (Boolean)writeOptions.get(TYPE);
        if (setting != null && !setting) {
            throw new IllegalStateException(TYPE_NAME_MAP + " is not needed when types are not going to be output");
        }
    }
}
