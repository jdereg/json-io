package com.cedarsoftware.util.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
        NATIVE_JSON_TYPES.add(AtomicBoolean.class);
        NATIVE_JSON_TYPES.add(AtomicInteger.class);
        NATIVE_JSON_TYPES.add(AtomicLong.class);
        NATIVE_JSON_TYPES.add(BigInteger.class);
        NATIVE_JSON_TYPES.add(BigDecimal.class);
        NATIVE_JSON_TYPES.add(Object[].class);
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
}
