package com.cedarsoftware.util.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Objects;

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

    @Getter
    private final String fieldName;

    private final Field field;
    @Getter
    private final String displayName;
    private MethodHandle methodHandle;

    @Getter
    private final boolean isPublic;

    public Accessor(Field field, String fieldName) throws Throwable {
        this.field = field;
        this.fieldName = fieldName;

        this.displayName = field.getName();
        this.isPublic = Modifier.isPublic(field.getModifiers());

        try {
            this.methodHandle = MethodHandles.lookup().unreflectGetter(field);
        } catch (Exception e) {
            if (!(Modifier.isPublic(field.getModifiers()) && Modifier.isPublic(field.getDeclaringClass().getModifiers()))) {
                MetaUtils.trySetAccessible(field);
            }
            this.methodHandle = null;
        }
    }

    public Accessor(Field field, Method method, String fieldName) throws Throwable {
        this.fieldName = fieldName;
        this.field = field;

        this.displayName = method.getName();
        this.isPublic = Modifier.isPublic(method.getModifiers());

        this.methodHandle = MethodHandles.lookup().unreflect(method);
    }

    @Override
    public boolean equals(Object o) {
        if (o != null && o.getClass() == getClass()) {
            Accessor other = (Accessor) o;
            return (field.equals(other.field)) &&
                    fieldName.equals(other.fieldName) &&
                    displayName.equals(other.displayName);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName, displayName, field, getClass());
    }


    public Object retrieve(Object o) throws Throwable {
        if (methodHandle == null) {
            return field.get(o);
        } else {
            return methodHandle.invoke(o);
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

    public boolean isTransient() {
        return Modifier.isTransient(this.field.getModifiers());
    }

    public String getActualFieldName() {
        return field.getName();
    }
}
