package com.cedarsoftware.util.io;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.cedarsoftware.util.io.JsonReader.CLASSLOADER;
import static com.cedarsoftware.util.io.JsonReader.CUSTOM_READER_MAP;
import static com.cedarsoftware.util.io.JsonReader.NOT_CUSTOM_READER_MAP;
import static com.cedarsoftware.util.io.JsonReader.USE_MAPS;
import static com.cedarsoftware.util.io.JsonWriter.CUSTOM_WRITER_MAP;
import static com.cedarsoftware.util.io.JsonWriter.NOT_CUSTOM_WRITER_MAP;
import static com.cedarsoftware.util.io.JsonWriter.TYPE_NAME_MAP;

public class ReadOptionsBuilder {

    ConcurrentMap<String, Object> readOptions = new ConcurrentHashMap<>();

    public ReadOptionsBuilder setUnknownTypeClass(Class c) {
        readOptions.put(JsonReader.UNKNOWN_OBJECT, c.getName());
        return this;
    }

    public ReadOptionsBuilder failOnUnknownType() {
        readOptions.put(JsonReader.FAIL_ON_UNKNOWN_TYPE, Boolean.TRUE);
        return this;
    }

    public ReadOptionsBuilder withClassLoader(ClassLoader classLoader) {
        readOptions.put(CLASSLOADER, classLoader);
        return this;
    }

    public ReadOptionsBuilder returnAsMaps() {
        readOptions.put(USE_MAPS, Boolean.TRUE);
        return this;
    }

    public ReadOptionsBuilder withCustomTypeName(Class type, String newTypeName) {
        return withCustomTypeName(type.getName(), newTypeName);
    }

    public ReadOptionsBuilder withCustomTypeName(String type, String newTypeName) {
        MetaUtils.computeMapIfAbsent(readOptions, TYPE_NAME_MAP).put(type, newTypeName);
        return this;
    }

    public ReadOptionsBuilder withCustomTypeNameMap(Map<String, String> map) {
        MetaUtils.computeMapIfAbsent(readOptions, TYPE_NAME_MAP).putAll(map);
        return this;
    }

    public ReadOptionsBuilder withCustomReader(Class c, JsonReader.JsonClassReaderEx writer) {
        MetaUtils.computeMapIfAbsent(readOptions, CUSTOM_READER_MAP).put(c, writer);
        return this;
    }

    public ReadOptionsBuilder withCustomReaderMap(Map<Class, JsonReader.JsonClassReaderEx> map) {
        MetaUtils.computeMapIfAbsent(readOptions, CUSTOM_READER_MAP).putAll(map);
        return this;
    }

    public ReadOptionsBuilder withNonCustomizableClass(Class c) {
        MetaUtils.computeSetIfAbsent(readOptions, NOT_CUSTOM_READER_MAP).add(c);
        return this;
    }

    public ReadOptionsBuilder withNonCustomizableClasses(Collection<Class> collection) {
        MetaUtils.computeSetIfAbsent(readOptions, NOT_CUSTOM_READER_MAP).addAll(collection);
        return this;
    }




    public Map<String, Object> build() {
        return readOptions;
    }
}
