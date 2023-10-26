package com.cedarsoftware.util.io;

import java.util.Collection;
import java.util.Map;

public class ReadOptions {
    // should we hold onto JsonReader here?  or in ReadContext?

    private boolean useMaps;

    private boolean failOnUnknownType;

    private ClassLoader classLoader = ReadOptions.class.getClassLoader();

    private Map<String, String> typeNameMap;

    private JsonReader.MissingFieldHandler missingFieldHandler;

    private Map<Class<?>, JsonReader.JsonClassReader> customReaderMap;

    private Collection<Class<?>> notCustomReaders;

    private Map<String, JsonReader.ClassFactory> classFactoryMap;

    private Map<String, Object> customArguments;
}
