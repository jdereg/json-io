package com.cedarsoftware.io.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Type;

import com.cedarsoftware.io.JsonIoException;

import static java.lang.reflect.Modifier.isPublic;

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
public class Injector {

    private final Field field;

    private final String displayName;

    private final String uniqueFieldName;

    private MethodHandle injector;


    public Injector(Field field, MethodHandle handle, String uniqueFieldName, String displayName) {
        this.field = field;
        this.displayName = displayName;
        this.uniqueFieldName = uniqueFieldName;
        this.injector = handle;
    }

    public static Injector create(Field field, String uniqueFieldName) {
        if (!isPublic(field.getModifiers()) || !isPublic(field.getDeclaringClass().getModifiers())) {
            try {
                field.setAccessible(true);
            } catch (Exception ioe) {
                // If object could not be set accessible let's escape here instead
                // of continuing on with more reflection and possible exceptions being thrown.
                // this will speed things up a bit to short circuit.
                // Anything that shows up in System.out.println() above should either be added to ignored
                // fields if there is no method that can access it or add a nonstandard mapping to the method.
                return null;
            }
        }

        try {
            MethodHandle handle = MethodHandles.lookup().unreflectSetter(field);
            return new Injector(field, handle, uniqueFieldName, field.getName());
        } catch (IllegalAccessException ignore) {
            return null;
        }
    }


    public static Injector create(Field field, String methodName, String uniqueFieldName) {
        try {
            MethodType methodType = MethodType.methodType(Void.class, field.getType());
            MethodHandle handle = MethodHandles.publicLookup().findVirtual(field.getDeclaringClass(), methodName, methodType);
            return new Injector(field, handle, uniqueFieldName, methodName);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            // ignore
            return null;
        }
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

    public Class<?> getType() {
        return this.field.getType();
    }

    public String getName() {
        return this.field.getName();
    }

    public Type getGenericType() {
        return this.field.getGenericType();
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getUniqueFieldName() {
        return this.uniqueFieldName;
    }
}
