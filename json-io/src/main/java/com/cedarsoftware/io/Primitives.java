package com.cedarsoftware.io;

import java.util.Set;

import com.cedarsoftware.util.IdentitySet;

/**
 * @author Kenny Partlow (kpartlow@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class Primitives {
    private static final Set<Class<?>> PRIMITIVE_WRAPPERS = new IdentitySet<>();
    private static final Set<Class<?>> NATIVE_JSON_TYPES = new IdentitySet<>();

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
