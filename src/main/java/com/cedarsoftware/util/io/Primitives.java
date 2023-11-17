package com.cedarsoftware.util.io;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class Primitives {
    static final Set<Class<?>> PRIMITIVE_WRAPPERS = new HashSet<>();

    static {
        PRIMITIVE_WRAPPERS.add(Byte.class);
        PRIMITIVE_WRAPPERS.add(Integer.class);
        PRIMITIVE_WRAPPERS.add(Long.class);
        PRIMITIVE_WRAPPERS.add(Double.class);
        PRIMITIVE_WRAPPERS.add(Character.class);
        PRIMITIVE_WRAPPERS.add(Float.class);
        PRIMITIVE_WRAPPERS.add(Boolean.class);
        PRIMITIVE_WRAPPERS.add(Short.class);
    }

    public static boolean contains(Class<?> c) {
        return PRIMITIVE_WRAPPERS.contains(c);
    }

    /**
     * @param c Class to test
     * @return boolean true if the passed in class is a Java primitive, false otherwise.  The Wrapper classes
     * Integer, Long, Boolean, etc. are considered primitives by this method.
     */
    public static boolean isPrimitive(Class<?> c) {
        return c.isPrimitive() || PRIMITIVE_WRAPPERS.contains(c);
    }

    /**
     * @param c Class to test
     * @return boolean true if the passed in class is a 'logical' primitive.  A logical primitive is defined
     * as all Java primitives, the primitive wrapper classes, String, Number, and Date.  This covers BigDecimal,
     * BigInteger, AtomicInteger, AtomicLong, as these are 'Number instances. The reason these are considered
     * 'logical' primitives is that they are immutable and therefore can be written without references in JSON
     * content (making the JSON more readable - less @id / @ref), without breaking the semantics (shape) of the
     * object graph being written.
     */
    public static boolean isLogicalPrimitive(Class<?> c) {
        return isLogicalPrimitive(c, PRIMITIVE_WRAPPERS);
    }

    /**
     * @param c                 Class to test
     * @param logicalPrimitives - set of logical primitives
     * @return boolean true if the passed in class is a 'logical' primitive.  A logical primitive is defined
     * as all Java primitives, the primitive wrapper classes, String, Number, and Date.  This covers BigDecimal,
     * BigInteger, AtomicInteger, AtomicLong, as these are 'Number instances. The reason these are considered
     * 'logical' primitives is that they are immutable and therefore can be written without references in JSON
     * content (making the JSON more readable - less @id / @ref), without breaking the semantics (shape) of the
     * object graph being written.
     */
    public static boolean isLogicalPrimitive(Class<?> c, Set<Class<?>> logicalPrimitives) {
        return c.isPrimitive() ||
                logicalPrimitives.contains(c) ||
                String.class.isAssignableFrom(c) ||
                Number.class.isAssignableFrom(c) ||
                Date.class.isAssignableFrom(c) ||
                c.isEnum() ||
                c.equals(Class.class);
    }

}
