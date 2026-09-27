package com.cedarsoftware.io;

import java.util.HashSet;
import java.util.Set;


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
    // Filled once below, then read through ClassValues: each class's answer is kept on the class (no hash probe)
    // -- faster than a hash-based set on hot dispatch paths like JsonWriter.isForceType, which JFR showed at ~5%
    // of write-phase CPU.
    private static final Set<Class<?>> PRIMITIVE_WRAPPER_TABLE = new HashSet<>();
    private static final Set<Class<?>> NATIVE_JSON_TYPE_TABLE = new HashSet<>();
    private static final ClassValue<Boolean> PRIMITIVE_WRAPPERS = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            return PRIMITIVE_WRAPPER_TABLE.contains(c);
        }
    };
    private static final ClassValue<Boolean> NATIVE_JSON_TYPES = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            return NATIVE_JSON_TYPE_TABLE.contains(c);
        }
    };

    static {
        PRIMITIVE_WRAPPER_TABLE.add(Byte.class);
        PRIMITIVE_WRAPPER_TABLE.add(Integer.class);
        PRIMITIVE_WRAPPER_TABLE.add(Long.class);
        PRIMITIVE_WRAPPER_TABLE.add(Double.class);
        PRIMITIVE_WRAPPER_TABLE.add(Character.class);
        PRIMITIVE_WRAPPER_TABLE.add(Float.class);
        PRIMITIVE_WRAPPER_TABLE.add(Boolean.class);
        PRIMITIVE_WRAPPER_TABLE.add(Short.class);

        // Native json types are the types we can leave the type value off of now matter what
        NATIVE_JSON_TYPE_TABLE.add(Long.class);
        NATIVE_JSON_TYPE_TABLE.add(long.class);
        NATIVE_JSON_TYPE_TABLE.add(Double.class);
        NATIVE_JSON_TYPE_TABLE.add(double.class);
        NATIVE_JSON_TYPE_TABLE.add(String.class);
        NATIVE_JSON_TYPE_TABLE.add(Boolean.class);
        NATIVE_JSON_TYPE_TABLE.add(boolean.class);
        NATIVE_JSON_TYPE_TABLE.add(Object[].class);
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
        return c.isPrimitive() || PRIMITIVE_WRAPPERS.get(c);
    }

    /**
     * @param c Class to test
     * @return boolean true if the passed in class is a Java primitive, false otherwise.  The Wrapper classes
     * Integer, Long, Boolean, etc. are considered primitives by this method.
     */
    public static boolean isNativeJsonType(Class<?> c) {
        return c != null && NATIVE_JSON_TYPES.get(c);
    }
}
