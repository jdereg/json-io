package com.cedarsoftware.util.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.MetaUtils;
import lombok.Getter;

import static java.lang.reflect.Modifier.isPublic;

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
public class Injector {

    @Getter
    private final Class<?> type;

    @Getter
    private final Class<?> declaringClass;

    @Getter
    private final Type genericType;

    @Getter
    private final String name;

    @Getter
    private final String displayName;

    private MethodHandle injector;

    public Injector(Field field) throws Throwable {
        this.name = field.getName();
        this.displayName = field.getName();
        this.type = field.getType();
        this.declaringClass = field.getDeclaringClass();
        this.genericType = field.getGenericType();

        if (!(isPublic(field.getModifiers()) && isPublic(field.getDeclaringClass().getModifiers()))) {
            MetaUtils.trySetAccessible(field);
        }

        try {
            this.injector = MethodHandles.lookup().unreflectSetter(field);
        } catch (Exception e) {
            this.injector = null;
        }
    }

    public Injector(Field field, Method method) throws Throwable {
        this.name = field.getName();
        this.type = field.getType();
        this.declaringClass = field.getDeclaringClass();
        this.genericType = field.getGenericType();
        this.displayName = method.getName();

        if (!isPublic(method.getDeclaringClass().getModifiers())) {
            MetaUtils.trySetAccessible(method);
        }

        this.injector = MethodHandles.lookup().unreflect(method);
    }

    public void inject(Object object, Object value) {
        if (object == null) {
            throw new JsonIoException("Attempting to set field: " + this.getName() + " on null object.");
        }

        try {
            // TODO: value should be "cleaned up" using Converter.convert(value, Class of field) before injecting.
            // TODO: This will make the value to best match the destination type.
            // TODO: This is logical primitive types (all primitives, wrappers, Date, java.sql.Date, LocalDate, LocalTime, ZonedDateTime, Atomic*, Big*, Class, String, etc.)
            // TODO: This should be performed with there is reflection, and accessor, Method Handle, etc.
            this.injector.invoke(object, value);
        } catch (Throwable t) {
            throw new JsonIoException("Attempting to set field: " + this.getName() + " using " + this.getDisplayName(), t);
        }
    }
}
