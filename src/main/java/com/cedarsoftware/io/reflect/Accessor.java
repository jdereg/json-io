package com.cedarsoftware.io.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.util.ExceptionUtilities;
import com.cedarsoftware.util.ReflectionUtils;
import com.cedarsoftware.util.SystemUtilities;

/**
 * High-performance field accessor utility that automatically adapts to different JDK versions
 * for optimal performance and compatibility.
 *
 * <p>This class uses the {@code java.version} system property to automatically detect the
 * JDK version and select the most appropriate field access strategy:</p>
 *
 * <ul>
 * <li><strong>JDK 8-16:</strong> Uses {@code MethodHandle} for field access with {@code Field.get()} fallback</li>
 * <li><strong>JDK 17+:</strong> Uses {@code VarHandle} for improved performance and module system compatibility</li>
 * </ul>
 *
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
    // JDK version detection and VarHandle infrastructure (JDK 9+)
    private static final boolean IS_JDK17_OR_HIGHER;
    private static final Object LOOKUP;
    private static final Method PRIVATE_LOOKUP_IN_METHOD;
    private static final Method FIND_VAR_HANDLE_METHOD;
    private static final MethodHandle VAR_HANDLE_GET_METHOD;

    static {
        int javaVersion = SystemUtilities.currentJdkMajorVersion();
        IS_JDK17_OR_HIGHER = javaVersion >= 17;

        Object lookup = null;
        Method privateLookupInMethod = null;
        Method findVarHandleMethod = null;
        MethodHandle varHandleGetMethod = null;

        if (javaVersion >= 9) {
            try {
                Class<?> methodHandlesClass = Class.forName("java.lang.invoke.MethodHandles");
                Class<?> lookupClass = Class.forName("java.lang.invoke.MethodHandles$Lookup");

                Method lookupMethod = ReflectionUtils.getMethod(methodHandlesClass, "lookup");
                lookup = lookupMethod.invoke(null);

                privateLookupInMethod = ReflectionUtils.getMethod(methodHandlesClass,
                        "privateLookupIn", Class.class, lookupClass);

                Class<?> varHandleClass = Class.forName("java.lang.invoke.VarHandle");
                findVarHandleMethod = ReflectionUtils.getMethod(lookupClass,
                        "findVarHandle", Class.class, String.class, Class.class);

                // VarHandle.get(Object) returns Object
                MethodType getType = MethodType.methodType(Object.class, Object.class);
                varHandleGetMethod = MethodHandles.publicLookup().findVirtual(varHandleClass, "get", getType);
            } catch (Exception e) {
                // VarHandle reflection setup failed - will use MethodHandle/Field.get() fallback
                lookup = null;
                privateLookupInMethod = null;
                findVarHandleMethod = null;
                varHandleGetMethod = null;
            }
        }

        LOOKUP = lookup;
        PRIVATE_LOOKUP_IN_METHOD = privateLookupInMethod;
        FIND_VAR_HANDLE_METHOD = findVarHandleMethod;
        VAR_HANDLE_GET_METHOD = varHandleGetMethod;
    }

    private final String uniqueFieldName;
    private final Field field;
    private final boolean isMethod;
    private final String fieldOrMethodName;
    private final MethodHandle methodHandle;
    private final Object varHandle;  // For JDK 17+ VarHandle-based access
    private final boolean isPublic;

    // Private constructor for MethodHandle-based access
    private Accessor(Field field, MethodHandle methodHandle, String uniqueFieldName, String fieldOrMethodName, boolean isPublic, boolean isMethod) {
        this.field = field;
        this.methodHandle = methodHandle;
        this.varHandle = null;
        this.uniqueFieldName = uniqueFieldName;
        this.fieldOrMethodName = fieldOrMethodName;
        this.isPublic = isPublic;
        this.isMethod = isMethod;
    }

    // Private constructor for VarHandle-based access (JDK 17+)
    private Accessor(Field field, Object varHandle, String uniqueFieldName, String fieldOrMethodName, boolean isPublic) {
        this.field = field;
        this.methodHandle = null;
        this.varHandle = varHandle;
        this.uniqueFieldName = uniqueFieldName;
        this.fieldOrMethodName = fieldOrMethodName;
        this.isPublic = isPublic;
        this.isMethod = false;
    }

    public static Accessor createFieldAccessor(Field field, String uniqueFieldName) {
        boolean isPublicField = Modifier.isPublic(field.getModifiers()) && Modifier.isPublic(field.getDeclaringClass().getModifiers());

        // Ensure field is accessible if needed
        if (!isPublicField) {
            ExceptionUtilities.safelyIgnoreException(() -> field.setAccessible(true));
        }

        // Try MethodHandle first (maintains getMethodHandle() API compatibility)
        try {
            MethodHandle handle = MethodHandles.lookup().unreflectGetter(field);
            return new Accessor(field, handle, uniqueFieldName, field.getName(), Modifier.isPublic(field.getModifiers()), false);
        } catch (IllegalAccessException ex) {
            // MethodHandle failed - try VarHandle on JDK 17+ for module system compatibility
            if (IS_JDK17_OR_HIGHER) {
                Accessor varHandleAccessor = createWithVarHandle(field, uniqueFieldName);
                if (varHandleAccessor != null) {
                    return varHandleAccessor;
                }
            }
            // Final fallback: create an accessor that uses field.get() directly
            return new Accessor(field, (MethodHandle) null, uniqueFieldName, field.getName(), Modifier.isPublic(field.getModifiers()), false);
        }
    }

    private static Accessor createWithVarHandle(Field field, String uniqueFieldName) {
        if (PRIVATE_LOOKUP_IN_METHOD == null || FIND_VAR_HANDLE_METHOD == null ||
                VAR_HANDLE_GET_METHOD == null || LOOKUP == null) {
            return null;
        }

        try {
            Class<?> declaringClass = field.getDeclaringClass();
            Object privateLookup = PRIVATE_LOOKUP_IN_METHOD.invoke(null, declaringClass, LOOKUP);
            if (privateLookup == null) {
                return null;
            }

            Object varHandle = FIND_VAR_HANDLE_METHOD.invoke(privateLookup, declaringClass, field.getName(), field.getType());
            if (varHandle == null) {
                return null;
            }

            return new Accessor(field, varHandle, uniqueFieldName, field.getName(), Modifier.isPublic(field.getModifiers()));
        } catch (Exception e) {
            // VarHandle creation failed - allow fallback to MethodHandle/Field.get()
            return null;
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
        if (o == null) {
            throw new JsonIoException("Cannot retrieve field value from null object for field: " + getActualFieldName());
        }

        try {
            // Try VarHandle first (JDK 17+)
            if (varHandle != null) {
                try {
                    return VAR_HANDLE_GET_METHOD.invoke(varHandle, o);
                } catch (Throwable t) {
                    // Fallback to field.get() if VarHandle fails
                    return field.get(o);
                }
            }

            // Try MethodHandle (JDK 8-16 or method accessor)
            if (methodHandle != null) {
                try {
                    return methodHandle.invoke(o);
                } catch (Throwable t) {
                    // Fallback to field.get() if MethodHandle fails
                    return field.get(o);
                }
            }

            // Final fallback: direct field access
            return field.get(o);
        } catch (IllegalAccessException e) {
            Class<?> dc = field.getDeclaringClass();
            if (isJdkInternalClass(dc)) {
                return handleInaccessibleJdkField(dc, getActualFieldName());
            }
            throw new JsonIoException("Failed to retrieve field value: " + getActualFieldName() + " in class: " + dc.getName(), e);
        } catch (Throwable t) {
            throw new JsonIoException("Failed to retrieve field value: " + getActualFieldName() + " in class: " + field.getDeclaringClass().getName(), t);
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

    /**
     * Check if a class is a JDK internal class that may have module access restrictions
     */
    private static boolean isJdkInternalClass(Class<?> clazz) {
        String className = clazz.getName();
        return className.startsWith("java.") ||
                className.startsWith("javax.") ||
                className.startsWith("jdk.") ||
                className.startsWith("sun.") ||
                className.startsWith("com.sun.") ||
                className.contains(".internal.");
    }

    /**
     * Handle inaccessible JDK fields gracefully by returning safe defaults.
     * This prevents JsonIoException for JDK internal fields that can't be accessed
     * due to Java module system restrictions.
     */
    private static Object handleInaccessibleJdkField(Class<?> declaringClass, String fieldName) {
        // For JDK internal fields, return null to skip them safely
        // This allows serialization to continue without the restricted field
        return null;
    }
}
