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
public class ArgumentHelper {
    /**
     * @param setting Object setting value from JsonWriter args map. Can be null.
     * @return boolean true if the value is (boolean) true, Boolean.TRUE, "true" (any case), 
     *         or non-zero if a Number. Returns false for null or unsupported types.
     */
    public static boolean isTrue(Object setting)
    {
        if (setting == null)
        {
            return false;
        }

        if (setting instanceof Boolean)
        {
            return Boolean.TRUE.equals(setting);
        }

        if (setting instanceof String)
        {
            return "true".equalsIgnoreCase((String) setting);
        }

        if (setting instanceof Number)
        {
            return ((Number)setting).doubleValue() != 0.0;
        }

        return false;
    }

    /**
     * Gets a Number from an Object with a default fallback.
     * @param o Object that should be a Number, or null
     * @param def Default Number to return if o is null
     * @return The Number value of o, or def if o is null
     * @throws IllegalArgumentException if o is not null and not a Number
     */
    public static Number getNumberWithDefault(Object o, Number def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Number) {
            return (Number) o;
        }
        throw new IllegalArgumentException("Expected Number but got: " + o.getClass().getSimpleName());
    }

}
