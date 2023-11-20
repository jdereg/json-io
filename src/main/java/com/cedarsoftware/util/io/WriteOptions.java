package com.cedarsoftware.util.io;

import java.util.Collection;
import java.util.Map;

import com.cedarsoftware.util.reflect.Accessor;

public interface WriteOptions {

    String getCustomNameOrDefault(String name, String def);

    boolean isUsingShortMetaKeys();

    boolean isAlwaysShowingType();

    boolean isNeverShowingType();

    boolean isPrettyPrint();

    boolean isWritingLongsAsStrings();

    boolean isSkippingNullFields();

    boolean isForcingMapFormatWithKeyArrays();

    boolean isEnumPublicOnly();

    void clearWriterCache();

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

    JsonWriter.JsonClassWriter getCustomWriter(Class<?> c);

    ClassLoader getClassLoader();

    boolean isNonCustomClass(Class<?> c);

    WriteOptions ensurePrettyPrint();

    Collection<Accessor> getIncludedAccessors(final Class<?> c);

    Collection<Accessor> getExcludedAccessors(final Class<?> c);

    @Deprecated
    Map toMap();
}
