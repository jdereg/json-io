package com.cedarsoftware.util.io;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class Primitives {

    static final Set<Class<?>> PRIMITIVE_WRAPPERS = new HashSet<>();

    static final Set<Class<?>> NATIVE_JSON_TYPES = new HashSet<>();

    static {
        PRIMITIVE_WRAPPERS.add(Byte.class);
        PRIMITIVE_WRAPPERS.add(Integer.class);
        PRIMITIVE_WRAPPERS.add(Long.class);
        PRIMITIVE_WRAPPERS.add(Double.class);
        PRIMITIVE_WRAPPERS.add(Character.class);
        PRIMITIVE_WRAPPERS.add(Float.class);
        PRIMITIVE_WRAPPERS.add(Boolean.class);
        PRIMITIVE_WRAPPERS.add(Short.class);

        NATIVE_JSON_TYPES.add(Long.class);
        NATIVE_JSON_TYPES.add(long.class);
        NATIVE_JSON_TYPES.add(Double.class);
        NATIVE_JSON_TYPES.add(double.class);
        NATIVE_JSON_TYPES.add(String.class);
        NATIVE_JSON_TYPES.add(Boolean.class);
        NATIVE_JSON_TYPES.add(boolean.class);
        NATIVE_JSON_TYPES.add(Object[].class);
        //NATIVE_JSON_TYPES.add(LinkedHashMap.class);
    }

    /**
     * Statically accessed class, no need for Construction
     */
    private Primitives() {
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
     * @return boolean true if the passed in class is a Java primitive, false otherwise.  The Wrapper classes
     * Integer, Long, Boolean, etc. are considered primitives by this method.
     */
    public static boolean isJsonType(Class<?> c) {
        return NATIVE_JSON_TYPES.contains(c);
    }

    /**
     * @param c Class to test
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
