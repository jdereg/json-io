package com.cedarsoftware.util.reflect.factories;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.AccessorFactory;

/**
 * @author Kenny Partlow (kpartlow@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.*
 */
public class MethodAccessorFactory implements AccessorFactory {
    @Override
    public Accessor createAccessor(Field field, Map<Class<?>, Map<String, String>> mappings, Map<String, Method> possibleAccessors, String key) throws Throwable {
        String fieldName = field.getName();

        Optional<String> possibleMethod = getMapping(mappings, field.getDeclaringClass(), fieldName);

        Method method = possibleAccessors.get(possibleMethod.orElse(createGetterName(fieldName)));

        final Class<?> c = field.getType();
        if (method == null && (c == Boolean.class || c == boolean.class)) {
            method = possibleAccessors.get(createIsName(fieldName));
        }

        if (method == null || !method.getReturnType().isAssignableFrom(field.getType())) {
            return null;
        }

        try {
            return new Accessor(field, method, key);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Creates the common name for a get Method
     *
     * @param fieldName - String representing the field name
     * @return String - returns the appropriate method name to access this fieldName.
     */
    private static String createGetterName(String fieldName) {
        return "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
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

    @Override
    public AccessorFactory createCopy() {
        return new MethodAccessorFactory();
    }
}
