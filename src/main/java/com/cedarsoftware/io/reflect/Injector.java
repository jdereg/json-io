package com.cedarsoftware.io.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.util.Converter;
import com.cedarsoftware.util.StringUtilities;

/**
 * @author Ken Partlow (kpartlow@gmail.com)
 *         John DeRegnaucourt (jereg@gmail.com)
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
public class Injector {

    private static final boolean IS_JDK17_OR_HIGHER;
    private static final Object LOOKUP;
    private static final Method PRIVATE_LOOKUP_IN_METHOD;
    private static final Method FIND_VAR_HANDLE_METHOD;
    private static final Method VAR_HANDLE_SET_METHOD;
    private static final Class<?> VAR_HANDLE_CLASS;        // although appears unused, it is intentional for caching

    static {
        int javaVersion = getJavaVersion();
        IS_JDK17_OR_HIGHER = javaVersion >= 17;

        Object lookup = null;
        Method privateLookupInMethod = null;
        Method findVarHandleMethod = null;
        Method varHandleSetMethod = null;
        Class<?> varHandleClass = null;

        if (javaVersion >= 9) {
            try {
                Class<?> methodHandlesClass = Class.forName("java.lang.invoke.MethodHandles");
                Class<?> lookupClass = Class.forName("java.lang.invoke.MethodHandles$Lookup");

                Method lookupMethod = methodHandlesClass.getMethod("lookup");
                lookup = lookupMethod.invoke(null);

                privateLookupInMethod = methodHandlesClass.getMethod("privateLookupIn", Class.class, lookupClass);

                varHandleClass = Class.forName("java.lang.invoke.VarHandle");
                findVarHandleMethod = lookupClass.getMethod("findVarHandle", Class.class, String.class, Class.class);
                varHandleSetMethod = varHandleClass.getMethod("set", Object.class, Object.class);
            } catch (Exception e) {
                // VarHandle reflection setup failed.
                lookup = null;
                privateLookupInMethod = null;
                findVarHandleMethod = null;
                varHandleSetMethod = null;
                varHandleClass = null;
            }
        }

        LOOKUP = lookup;
        PRIVATE_LOOKUP_IN_METHOD = privateLookupInMethod;
        FIND_VAR_HANDLE_METHOD = findVarHandleMethod;
        VAR_HANDLE_SET_METHOD = varHandleSetMethod;
        VAR_HANDLE_CLASS = varHandleClass;
    }

    private final Field field;
    private final String displayName;
    private final String uniqueFieldName;
    private MethodHandle injector;
    private Object varHandle; // For JDK 17+ VarHandle-based injection
    private final boolean useFieldSet; // flag to use Field.set() instead of MethodHandle

    // Constructor for MethodHandle injection
    public Injector(Field field, MethodHandle handle, String uniqueFieldName, String displayName) {
        this.field = field;
        this.displayName = displayName;
        this.uniqueFieldName = uniqueFieldName;
        this.injector = handle;
        this.useFieldSet = false;
    }

    // Constructor for Field.set() fallback injection
    private Injector(Field field, String uniqueFieldName, String displayName, boolean useFieldSet) {
        this.field = field;
        this.displayName = displayName;
        this.uniqueFieldName = uniqueFieldName;
        this.useFieldSet = useFieldSet;
    }

    // Constructor for VarHandle-based injection (JDK 17+)
    private Injector(Field field, Object varHandle, String uniqueFieldName, String displayName) {
        this.field = field;
        this.displayName = displayName;
        this.uniqueFieldName = uniqueFieldName;
        this.varHandle = varHandle;
        this.useFieldSet = false;
    }

    public static Injector create(Field field, String uniqueFieldName) {
        // Always try to make the field accessible, regardless of whether it is static.
        if (!field.isAccessible()) {
            try {
                field.setAccessible(true);
            } catch (Exception ioe) {
                if (IS_JDK17_OR_HIGHER) {
                    return createWithVarHandle(field, uniqueFieldName);
                }
                return null;
            }
        }

        // For JDK 8-16, if the field is final, use Field.set() (and try to remove the final modifier)
        boolean isFinal = Modifier.isFinal(field.getModifiers());
        if (isFinal && !IS_JDK17_OR_HIGHER) {
            try {
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            } catch (Exception ex) {
                // If removal fails, still fall back to Field.set() injection.
                return new Injector(field, uniqueFieldName, field.getName(), true);
            }
            return new Injector(field, uniqueFieldName, field.getName(), true);
        }

        try {
            MethodHandle handle = MethodHandles.lookup().unreflectSetter(field);
            return new Injector(field, handle, uniqueFieldName, field.getName());
        } catch (IllegalAccessException e) {
            if (IS_JDK17_OR_HIGHER) {
                return createWithVarHandle(field, uniqueFieldName);
            }
            // Fallback to Field.set() injection if we cannot get a MethodHandle.
            return new Injector(field, uniqueFieldName, field.getName(), true);
        }
    }

    private static Injector createWithVarHandle(Field field, String uniqueFieldName) {
        if (PRIVATE_LOOKUP_IN_METHOD == null || FIND_VAR_HANDLE_METHOD == null ||
                VAR_HANDLE_SET_METHOD == null || LOOKUP == null) {
            return null;
        }

        try {
            Object privateLookup = PRIVATE_LOOKUP_IN_METHOD.invoke(null, field.getDeclaringClass(), LOOKUP);
            Object varHandle = FIND_VAR_HANDLE_METHOD.invoke(privateLookup, field.getDeclaringClass(),
                    field.getName(), field.getType());

            return new Injector(field, varHandle, uniqueFieldName, field.getName());
        } catch (Exception e) {
            return null;
        }
    }

    public static Injector create(Field field, String methodName, String uniqueFieldName) {
        // find method that returns void
        try {
            MethodType methodType = MethodType.methodType(Void.class, field.getType());
            MethodHandle handle = MethodHandles.publicLookup().findVirtual(field.getDeclaringClass(), methodName, methodType);
            return new Injector(field, handle, uniqueFieldName, methodName);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            return null;
        }
    }

    public void inject(Object object, Object value) {
        if (object == null) {
            throw new JsonIoException("Attempting to set field: " + getName() + " on null object.");
        }

        try {
            if (varHandle != null) {
                // Use VarHandle-based injection if available (JDK 17+)
                injectWithVarHandle(object, value);
            } else if (useFieldSet) {
                // For JDK 8-16, fallback to using Field.set()
                field.set(object, value);
            } else {
                // Otherwise, use the MethodHandle-based injection.
                injector.invoke(object, value);
            }
        } catch (ClassCastException e) {
            String msg = e.getMessage();
            if (StringUtilities.hasContent(msg) && msg.contains("LinkedHashMap")) {
                throw new JsonIoException("Unable to set field: " + getName() + " using " + getDisplayName() + ".", e);
            }
            try {
                Object convertedValue = Converter.convert(value, field.getType());
                if (varHandle != null) {
                    injectWithVarHandle(object, convertedValue);
                } else if (useFieldSet) {
                    field.set(object, convertedValue);
                } else {
                    injector.invoke(object, convertedValue);
                }
            } catch (Throwable t) {
                throw new JsonIoException("Unable to set field: " + getName() + " using " + getDisplayName() + ". Getting a ClassCastException.", e);
            }
        } catch (Throwable t) {
            if (t instanceof JsonIoException) {
                throw (JsonIoException) t;
            }
            throw new JsonIoException("Unable to set field: " + getName() + " using " + getDisplayName(), t);
        }
    }

    private void injectWithVarHandle(Object object, Object value) throws Exception {
        if (varHandle == null || VAR_HANDLE_SET_METHOD == null) {
            throw new JsonIoException("Unable to set field: " + getName() + " - VarHandle not available");
        }
        VAR_HANDLE_SET_METHOD.invoke(varHandle, object, value);
    }

    public Class<?> getType() {
        return field.getType();
    }

    public String getName() {
        return field.getName();
    }

    public Type getGenericType() {
        return field.getGenericType();
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUniqueFieldName() {
        return uniqueFieldName;
    }

    private static int getJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            return Integer.parseInt(version.substring(2, 3));
        }
        int dot = version.indexOf('.');
        if (dot != -1) {
            return Integer.parseInt(version.substring(0, dot));
        }
        return Integer.parseInt(version);
    }
}
