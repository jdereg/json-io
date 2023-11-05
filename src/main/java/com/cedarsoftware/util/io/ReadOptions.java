package com.cedarsoftware.util.io;

import java.util.Map;
import java.util.Optional;

public interface ReadOptions {

    boolean isUsingMaps();

    boolean isFailOnUnknownType();

    int getMaxDepth();

    ClassLoader getClassLoader();

    JsonReader.MissingFieldHandler getMissingFieldHandler();

    JsonReader.JsonClassReader getReader(Class<?> c);

    JsonReader.ClassFactory getClassFactory(Class<?> c);

    Optional<JsonReader.JsonClassReader> getClosestReader(Class<?> c);

    String getTypeName(String name);

    <T> T getCustomArgument(String name);

    Class<?> getUnknownTypeClass();

    Class<?> getCoercedType(String fqName);

    boolean isNonCustomizable(Class<?> c);

    ReadOptions ensureUsingMaps();

    ReadOptions ensureUsingObjects();

    // Note: These 2 methods cannot become unmodifiable until we fully deprecate JsonReader.addReader() and JsonReader.addNotCustomReader()
    void addReader(Class<?> c, JsonReader.JsonClassReader reader);

    void addNonCustomizableClass(Class<?> c);

    Map toMap();
}
