package com.cedarsoftware.io;

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

        // Native json types are the types we can leave the type value off of now matter what
        // and it will always come in correct, so even if its in an object array it will be the correct type
        // after deserialization.  I had to leave JsonObject, even though that is our default
        // map type because we use it to build the object so it shows up for every object, not just types that could be JsonObject.
        // If we had our default type be LinkedHashMap instead of JsonObject we could always convert to that type when no type
        // data was present.  that requires more conversion, though, and could take longer over all when working with JSON_OBJECTS.
        // but we are doing something similar with Object[] because we build it with an ArrayList type and then convert to array.
        // we could have our default there be array list when representing the array type in JSON.
        NATIVE_JSON_TYPES.add(Long.class);
        NATIVE_JSON_TYPES.add(long.class);
        NATIVE_JSON_TYPES.add(Double.class);
        NATIVE_JSON_TYPES.add(double.class);
        NATIVE_JSON_TYPES.add(String.class);
        NATIVE_JSON_TYPES.add(Boolean.class);
        NATIVE_JSON_TYPES.add(boolean.class);
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

    /**
     * @param c Class to test
     * @return boolean true if the passed in class is a Java primitive, false otherwise.  The Wrapper classes
     * Integer, Long, Boolean, etc. are considered primitives by this method.
     */
    public static boolean isNativeJsonType(Class<?> c) {
        return NATIVE_JSON_TYPES.contains(c);
    }
}
