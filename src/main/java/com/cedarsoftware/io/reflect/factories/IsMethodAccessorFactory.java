package com.cedarsoftware.io.reflect.factories;

import java.lang.reflect.Field;
import java.util.Map;

import com.cedarsoftware.io.reflect.Accessor;
import com.cedarsoftware.io.reflect.AccessorFactory;

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
public class IsMethodAccessorFactory implements AccessorFactory {
    public Accessor buildAccessor(Field field, Map<Class<?>, Map<String, String>> nonStandardGetters, String uniqueFieldName) {
        final Class<?> c = field.getType();
        if (c != Boolean.class && c != boolean.class) {
            return null;
        }

        return Accessor.createMethodAccessor(field, createIsName(field.getName()), uniqueFieldName);
    }

    /**
     * Creates one of the names for boolean accessor
     *
     * @param fieldName - String representing the field name
     * @return String - returns the appropriate method name to access this fieldName.
     */
    public static String createIsName(String fieldName) {
        return "is" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }
}
