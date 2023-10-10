package com.cedarsoftware.util.io;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.cedarsoftware.util.io.JsonWriter.CUSTOM_WRITER_MAP;
import static com.cedarsoftware.util.io.JsonWriter.FIELD_NAME_BLACK_LIST;
import static com.cedarsoftware.util.io.JsonWriter.FIELD_SPECIFIERS;
import static com.cedarsoftware.util.io.JsonWriter.NOT_CUSTOM_WRITER_MAP;
import static com.cedarsoftware.util.io.JsonWriter.TYPE;
import static com.cedarsoftware.util.io.JsonWriter.TYPE_NAME_MAP;

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
        writeOptions.put(JsonWriter.SKIP_NULL_FIELDS, Boolean.TRUE);
        return this;
    }

    public WriteOptionsBuilder withPrettyPrint() {
        writeOptions.put(JsonWriter.PRETTY_PRINT, Boolean.TRUE);
        return this;
    }

    public WriteOptionsBuilder writeLongsAsStrings() {
        writeOptions.put(JsonWriter.WRITE_LONGS_AS_STRINGS, Boolean.TRUE);
        return this;
    }

    public WriteOptionsBuilder doNotWritePrivateEnumFields() {
        writeOptions.put(JsonWriter.ENUM_PUBLIC_ONLY, Boolean.TRUE);
        return this;
    }

    public WriteOptionsBuilder writeEnumsAsPrimitive() {
        writeOptions.put(JsonWriter.WRITE_ENUMS_AS_PRIMITIVE, Boolean.TRUE);
        return this;
    }

    public WriteOptionsBuilder forceMapOutputAsKeysAndItems() {
        writeOptions.put(JsonWriter.FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS, Boolean.TRUE);
        return this;
    }

    public WriteOptionsBuilder withClassLoader(ClassLoader classLoader) {
        writeOptions.put(JsonWriter.CLASSLOADER, classLoader);
        return this;
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

    public WriteOptionsBuilder withNoTypeInformation() {
        writeOptions.put(TYPE, Boolean.FALSE);
        return this;
    }

    public WriteOptionsBuilder forceTypeInformation() {
        writeOptions.put(TYPE, Boolean.TRUE);
        return this;
    }

    public WriteOptionsBuilder withFieldNameBlackList(Class c, List<String> fields) {
        MetaUtils.computeMapIfAbsent(writeOptions, FIELD_NAME_BLACK_LIST).put(c, fields);
        return this;
    }

    public WriteOptionsBuilder withFieldNameBlackListMap(Map<Class, List<String>> map) {
        MetaUtils.computeMapIfAbsent(writeOptions, FIELD_NAME_BLACK_LIST).putAll(map);
        return this;
    }

    public WriteOptionsBuilder withFieldSpecifier(Class c, List<String> fields) {
        MetaUtils.computeMapIfAbsent(writeOptions, FIELD_SPECIFIERS).put(c, fields);
        return this;
    }

    public WriteOptionsBuilder withFieldSpecifiersMap(Map<Class, List<String>> map) {
        MetaUtils.computeMapIfAbsent(writeOptions, FIELD_SPECIFIERS).putAll(map);
        return this;
    }

    public WriteOptionsBuilder withCustomTypeName(Class type, String newTypeName) {
        return withCustomTypeName(type.getName(), newTypeName);
    }

    public WriteOptionsBuilder withCustomTypeName(String type, String newTypeName) {
        assertTypesAreBeingOutput();
        MetaUtils.computeMapIfAbsent(writeOptions, TYPE_NAME_MAP).put(type, newTypeName);
        return this;
    }

    public WriteOptionsBuilder withCustomTypeNameMap(Map<String, String> map) {
        assertTypesAreBeingOutput();
        MetaUtils.computeMapIfAbsent(writeOptions, TYPE_NAME_MAP).putAll(map);
        return this;
    }

    public WriteOptionsBuilder withCustomWriter(Class c, JsonWriter.JsonClassWriter writer) {
        MetaUtils.computeMapIfAbsent(writeOptions, CUSTOM_WRITER_MAP).put(c, writer);
        return this;
    }

    public WriteOptionsBuilder withCustomWriterMap(Map<Class, JsonWriter.JsonClassWriter> map) {
        MetaUtils.computeMapIfAbsent(writeOptions, CUSTOM_WRITER_MAP).putAll(map);
        return this;
    }

    public WriteOptionsBuilder withNonCustomizableClass(Class c) {
        MetaUtils.computeSetIfAbsent(writeOptions, NOT_CUSTOM_WRITER_MAP).add(c);
        return this;
    }

    public WriteOptionsBuilder withNonCustomizableClasses(Collection<Class> collection) {
        MetaUtils.computeSetIfAbsent(writeOptions, NOT_CUSTOM_WRITER_MAP).addAll(collection);
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
