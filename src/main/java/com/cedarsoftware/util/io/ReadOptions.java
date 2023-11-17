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

    @Deprecated
    Map toMap();

    /**
     * @param c Class to test
     * @return boolean true if the passed in class is a 'logical' primitive.  A logical primitive is defined
     * as all Java primitives, the primitive wrapper classes, String, Number, and Date.  This covers BigDecimal,
     * BigInteger, AtomicInteger, AtomicLong, as these are 'Number instances. The reason these are considered
     * 'logical' primitives is that they are immutable and therefore can be written without references in JSON
     * content (making the JSON more readable - less @id / @ref), without breaking the semantics (shape) of the
     * object graph being written.
     */
    boolean isLogicalPrimitive(Class<?> c);
}
