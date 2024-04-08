package com.cedarsoftware.io.reflect.factories;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

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
    @Override
    public Injector createInjector(Field field, Map<Class<?>, Map<String, String>> nonStandardNames, String uniqueName) {

        final String fieldName = field.getName();
        final Optional<String> possibleMethod = getMapping(nonStandardNames, field.getDeclaringClass(), fieldName);
        final String method = possibleMethod.orElse(createSetterName(fieldName));

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
