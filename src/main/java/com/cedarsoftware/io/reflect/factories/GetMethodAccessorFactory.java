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
public class GetMethodAccessorFactory implements AccessorFactory {
    public Accessor buildAccessor(Field field, Map<Class<?>, Map<String, String>> nonStandardGetters, String uniqueFieldName) {
        final String fieldName = field.getName();
        String possibleMethodName = getPossibleMethodName(nonStandardGetters, field.getDeclaringClass(), fieldName);
        String methodName = possibleMethodName == null ? createGetterName(fieldName) : possibleMethodName;
        return Accessor.createMethodAccessor(field, methodName, uniqueFieldName);
    }

    /**
     * Creates the common name for a get Method
     *
     * @param fieldName - String representing the field name
     * @return String - returns the appropriate method name to access this fieldName.
     */
    private static String createGetterName(String fieldName) {
        // Performance: Use char array to eliminate intermediate string allocations
        // Previous implementation created 3 intermediate objects via string concatenation
        if (fieldName.isEmpty()) {
            return "get";
        }
        char[] chars = new char[fieldName.length() + 3];
        chars[0] = 'g';
        chars[1] = 'e';
        chars[2] = 't';
        chars[3] = Character.toUpperCase(fieldName.charAt(0));
        fieldName.getChars(1, fieldName.length(), chars, 4);
        return new String(chars);
    }
}
