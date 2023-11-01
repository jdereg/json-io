package com.cedarsoftware.util.io;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.cedarsoftware.util.io.JsonReader.*;
import static com.cedarsoftware.util.io.JsonWriter.TYPE_NAME_MAP;
/**
 * @author Kenny Partlow (kpartlow@gmail.com)
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
 *         limitations under the License.*
 */
public class ReadOptionsBuilder {

    private final ConcurrentMap<String, Object> readOptions = new ConcurrentHashMap<>();

    public ReadOptionsBuilder setUnknownTypeClass(Class<?> c) {
        readOptions.put(JsonReader.UNKNOWN_OBJECT, c.getName());
        return this;
    }

    public ReadOptionsBuilder setMissingFieldHandler(MissingFieldHandler missingFieldHandler)
    {
        readOptions.put(MISSING_FIELD_HANDLER, missingFieldHandler);
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

    public ReadOptionsBuilder withCustomTypeName(Class<?> type, String newTypeName) {
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

    public ReadOptionsBuilder withCustomReader(Class<?> c, JsonReader.JsonClassReader reader) {
        MetaUtils.computeMapIfAbsent(readOptions, CUSTOM_READER_MAP).put(c, reader);
        return this;
    }

    public ReadOptionsBuilder withCustomReaders(Map<? extends Class<?>, ? extends JsonReader.JsonClassReader> map) {
        MetaUtils.computeMapIfAbsent(readOptions, CUSTOM_READER_MAP).putAll(map);
        return this;
    }

    public ReadOptionsBuilder withNonCustomizableClass(Class<?> c) {
        MetaUtils.computeSetIfAbsent(readOptions, NOT_CUSTOM_READER_MAP).add(c);
        return this;
    }

    public ReadOptionsBuilder withNonCustomizableClasses(Collection<Class<?>> collection) {
        MetaUtils.computeSetIfAbsent(readOptions, NOT_CUSTOM_READER_MAP).addAll(collection);
        return this;
    }

    public ReadOptionsBuilder withClassFactory(Class<?> type, JsonReader.ClassFactory factory) {
        return withClassFactory(type.getName(), factory);
    }

    public ReadOptionsBuilder withClassFactories(Map<String, ? extends JsonReader.ClassFactory> factories) {
        MetaUtils.computeMapIfAbsent(readOptions, FACTORIES).putAll(factories);
        return this;
    }

    public ReadOptionsBuilder withClassFactory(String type, JsonReader.ClassFactory factory) {
        MetaUtils.computeMapIfAbsent(readOptions, FACTORIES).put(type, factory);
        return this;
    }

    public Map<String, Object> build() {
        return readOptions;
    }
}