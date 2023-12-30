package com.cedarsoftware.util.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

import com.cedarsoftware.util.io.MetaUtils;
import lombok.Getter;

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
 *         limitations under the License.*
 */
public class Accessor {

    /**
     * The unique field name if two fields have the same name in the same class structure,
     * the more parent field will be qualified with the ShortName of the Declaring class
     */
    @Getter
    private final String uniqueFieldName;

    /**
     * The field we are trying to access with this method handle
     */
    private final Field field;

    /**
     * The display name will be either the underlying field name or the underlying
     * method name from which the method handle was created.
     */
    @Getter
    private final String displayName;
    private final MethodHandle methodHandle;

    /**
     * This will be the modifiers of the field or method that defines this MethodHandle
     * (or Field) itself if we had to fall back to field access.
     */
    @Getter
    private final boolean isPublic;

    private Accessor(Field field, MethodHandle methodHandle, String uniqueFieldName, String displayName, boolean isPublic) {
        this.field = field;
        this.methodHandle = methodHandle;
        this.uniqueFieldName = uniqueFieldName;
        this.displayName = displayName;
        this.isPublic = isPublic;
    }


    public static Accessor create(Field field, String uniqueFieldName) {
        if (!(Modifier.isPublic(field.getModifiers()) && Modifier.isPublic(field.getDeclaringClass().getModifiers()))) {
            MetaUtils.trySetAccessible(field);
        }

        try {
            MethodHandle handle = MethodHandles.lookup().unreflectGetter(field);
            return new Accessor(field, handle, uniqueFieldName, field.getName(), Modifier.isPublic(field.getModifiers()));
        } catch (IllegalAccessException ex) {
            return null;
        }
    }

    public static Accessor create(Field field, String method, String uniqueFieldName) {
        try {
            MethodType type = MethodType.methodType(field.getType());
            MethodHandle handle = MethodHandles.publicLookup().findVirtual(field.getDeclaringClass(), method, type);
            return new Accessor(field, handle, uniqueFieldName, method, true);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            // ignore
            return null;
        }
    }

    public Object retrieve(Object o) {
        try {
            if (methodHandle == null) {
                return field.get(o);
            } else {
                return methodHandle.invoke(o);
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable t) {
            return null;
        }
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

    public boolean isMethodHandlePresent() {
        return methodHandle != null;
    }
}
