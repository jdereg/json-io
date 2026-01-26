package com.cedarsoftware.io.reflect.filters.field;

import java.lang.reflect.Field;

import com.cedarsoftware.io.reflect.filters.FieldFilter;

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
public class EnumFieldFilter implements FieldFilter {
    public boolean filter(Field field) {
        Class<?> declaringClass = field.getDeclaringClass();
        if (!declaringClass.isEnum()) {
            return false;  // Early exit for non-enums (99% of fields)
        }

        // Only compute field name if we have an enum field
        String fieldName = field.getName();

        // Filter these fields out (return true for them)
        // "$VALUES" is the synthetic field on standard JVMs, "ENUM$VALUES" on Windows JVM
        return "internal".equals(fieldName) || "$VALUES".equals(fieldName) ||
               "ENUM$VALUES".equalsIgnoreCase(fieldName) || "hash".equals(fieldName) ||
               "ordinal".equals(fieldName);
    }
}