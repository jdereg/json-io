package com.cedarsoftware.io.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

import com.cedarsoftware.util.ExceptionUtilities;

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
public class Accessor {
    private final String uniqueFieldName;
    private final Field field;
    private final boolean isMethod;
    private final String fieldOrMethodName;
    private final MethodHandle methodHandle;
    private final boolean isPublic;

    private Accessor(Field field, MethodHandle methodHandle, String uniqueFieldName, String fieldOrMethodName, boolean isPublic, boolean isMethod) {
        this.field = field;
        this.methodHandle = methodHandle;
        this.uniqueFieldName = uniqueFieldName;
        this.fieldOrMethodName = fieldOrMethodName;
        this.isPublic = isPublic;
        this.isMethod = isMethod;
    }

    public static Accessor createFieldAccessor(Field field, String uniqueFieldName) {
        // Ensure field is accessible if needed.
        if (!(Modifier.isPublic(field.getModifiers()) && Modifier.isPublic(field.getDeclaringClass().getModifiers()))) {
            ExceptionUtilities.safelyIgnoreException(() -> field.setAccessible(true));
        }
        try {
            // Try creating a MethodHandle-based accessor.
            MethodHandle handle = MethodHandles.lookup().unreflectGetter(field);
            return new Accessor(field, handle, uniqueFieldName, field.getName(), Modifier.isPublic(field.getModifiers()), false);
        } catch (IllegalAccessException ex) {
            // Fallback: create an accessor that uses field.get() directly.
            return new Accessor(field, null, uniqueFieldName, field.getName(), Modifier.isPublic(field.getModifiers()), false);
        }
    }

    public static Accessor createMethodAccessor(Field field, String methodName, String uniqueFieldName) {
        try {
            MethodType type = MethodType.methodType(field.getType());
            MethodHandle handle = MethodHandles.publicLookup().findVirtual(field.getDeclaringClass(), methodName, type);
            return new Accessor(field, handle, uniqueFieldName, methodName, true, true);
        } catch (Exception ignore) {
            return null;
        }
    }

    public Object retrieve(Object o) {
        try {
            if (methodHandle != null) {
                try {
                    return methodHandle.invoke(o);
                } catch (Throwable t) {
                    // Fallback: if the method handle invocation fails, try using field.get()
                    return field.get(o);
                }
            } else {
                return field.get(o);
            }
        } catch (Throwable t) {
            // On failure, return null.
            return null;
        }
    }

    public MethodHandle getMethodHandle() {
        return methodHandle;
    }

    public boolean isMethod() {
        return isMethod;
    }

    public Class<?> getFieldType() {
        return this.field.getType();
    }

    public Class<?> getDeclaringClass() {
        return this.field.getDeclaringClass();
    }

    public Type getGenericType() {
        return this.field.getGenericType();
    }

    public String getActualFieldName() {
        return field.getName();
    }

    /**
     * The unique field name if two fields have the same name in the same class structure,
     * the more parent field will be qualified with the ShortName of the Declaring class
     */
    public String getUniqueFieldName() {
        return uniqueFieldName;
    }

    /**
     * The display name will be either the underlying field name or the underlying
     * method name from which the method handle was created.
     */
    public String getFieldOrMethodName() {
        return fieldOrMethodName;
    }

    public boolean isPublic() {
        return isPublic;
    }
}
