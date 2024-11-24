package com.cedarsoftware.io.reflect.factories;

import java.lang.reflect.Field;
import java.util.Map;

import com.cedarsoftware.io.reflect.Injector;
import com.cedarsoftware.io.reflect.InjectorFactory;

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
public class MethodInjectorFactory implements InjectorFactory {
    /**
     * Creates an {@link Injector} for the specified field, using optional custom method mappings and a unique name.
     *
     * <p>
     * This method determines the appropriate method name to use when injecting a value into the given field.
     * It first checks for a custom method name mapping in the provided {@code nonStandardNames} map.
     * If a custom method name is found for the field, it uses that method name.
     * Otherwise, it generates a default setter method name based on the field name.
     * </p>
     *
     * @param field             the {@link Field} for which the injector is to be created
     * @param nonStandardNames  a map of classes to field-to-method name mappings, used for custom method names
     * @param uniqueName        a unique identifier for the injector, used to distinguish it from others
     * @return an {@link Injector} configured with the appropriate method to inject values into the field
     */
    public Injector createInjector(Field field, Map<Class<?>, Map<String, String>> nonStandardNames, String uniqueName) {
        final String fieldName = field.getName();
        final String possibleMethod = getMapping(nonStandardNames, field.getDeclaringClass(), fieldName);
        final String method = (possibleMethod != null) ? possibleMethod : createSetterName(fieldName);

        return Injector.create(field, method, uniqueName);
    }

    /**
     * Creates the common name for a get Method
     *
     * @param fieldName - String representing the field name
     * @return String - returns the appropriate method name to access this fieldName.
     */
    private static String createSetterName(String fieldName) {
        return "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }
}
