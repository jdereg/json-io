package com.cedarsoftware.io;

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
    // Two small fixed sets, tested by identity in rough order of frequency: cheaper than a ClassValue lookup on these
    // classes (String carries ~15 ClassValues once json-io and java-util have seen it, which crowds its small cache)
    // and than a hash-based set.

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
        return c.isPrimitive() || c == Integer.class || c == Long.class || c == Double.class || c == Boolean.class
                || c == Character.class || c == Float.class || c == Short.class || c == Byte.class;
    }

    /**
     * @param c Class to test
     * @return boolean true if the passed in class is a Java primitive, false otherwise.  The Wrapper classes
     * Integer, Long, Boolean, etc. are considered primitives by this method.
     */
    public static boolean isNativeJsonType(Class<?> c) {
        // Native JSON types: the ones whose type can be left off no matter what
        return c == String.class || c == Long.class || c == Double.class || c == Boolean.class || c == Object[].class
                || c == long.class || c == double.class || c == boolean.class;
    }
}
