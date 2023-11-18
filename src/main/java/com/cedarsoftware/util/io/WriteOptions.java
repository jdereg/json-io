package com.cedarsoftware.util.io;

import com.cedarsoftware.util.reflect.Accessor;

import java.util.Collection;
import java.util.Map;

public interface WriteOptions {

    Map<String, String> getCustomTypeMap();

    boolean isUsingShortMetaKeys();

    boolean isAlwaysShowingType();

    boolean isNeverShowingType();

    boolean isPrettyPrint();

    boolean isWritingLongsAsStrings();

    boolean isSkippingNullFields();

    boolean isForcingMapFormatWithKeyArrays();

    boolean isEnumPublicOnly();

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

    JsonWriter.JsonClassWriter getEnumWriter();

    String getDateFormat();

    Map<Class<?>, JsonWriter.JsonClassWriter> getCustomWriters();

    ClassLoader getClassLoader();

    Collection<Class<?>> getNonCustomClasses();
    
    Map<Class<?>, Collection<Accessor>> getIncludedFields();

    Map<Class<?>, Collection<Accessor>> getExcludedFields();

    WriteOptions ensurePrettyPrint();
}
