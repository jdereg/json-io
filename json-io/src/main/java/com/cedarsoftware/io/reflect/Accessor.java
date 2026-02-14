package com.cedarsoftware.io.reflect;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.function.Function;

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
    // VarHandle infrastructure (JDK 9+)
    private static final Object LOOKUP;
    private static final Method PRIVATE_LOOKUP_IN_METHOD;
    private static final Method FIND_VAR_HANDLE_METHOD;
    private static final MethodHandle VAR_HANDLE_GET_METHOD;

    static {
        int javaVersion = SystemUtilities.currentJdkMajorVersion();

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
    private Function<Object, Object> function;  // LambdaMetafactory-generated fast getter (JIT-inlinable)
    private volatile boolean functionFailed;
    private volatile boolean varHandleFailed;
    private volatile boolean methodHandleFailed;

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

    // Private constructor for LambdaMetafactory-accelerated access
    private Accessor(Field field, MethodHandle methodHandle, String uniqueFieldName, String fieldOrMethodName,
                     boolean isPublic, boolean isMethod, Function<Object, Object> function) {
        this.field = field;
        this.methodHandle = methodHandle;
        this.varHandle = null;
        this.uniqueFieldName = uniqueFieldName;
        this.fieldOrMethodName = fieldOrMethodName;
        this.isPublic = isPublic;
        this.isMethod = isMethod;
        this.function = function;
    }

    /**
     * Attempt to create a LambdaMetafactory-generated Function for the given getter MethodHandle.
     * The generated Function is JIT-inlinable, providing near-direct field access speed.
     * Returns null if creation fails (caller should fall back to existing mechanisms).
     */
    @SuppressWarnings("unchecked")
    private static Function<Object, Object> createLambdaAccessor(MethodHandles.Lookup lookup, MethodHandle getter) {
        try {
            Class<?> targetClass = getter.type().parameterType(0);
            MethodHandles.Lookup lambdaLookup = tryPrivateLookup(targetClass, lookup);

            // If we couldn't get a private lookup and the target class is not public,
            // the generated lambda would fail at runtime with IllegalAccessError
            if (lambdaLookup == lookup && !Modifier.isPublic(targetClass.getModifiers())) {
                return null;
            }

            CallSite callSite = LambdaMetafactory.metafactory(
                    lambdaLookup,
                    "apply",
                    MethodType.methodType(Function.class),
                    MethodType.methodType(Object.class, Object.class),  // SAM erased type
                    getter,
                    getter.type()  // e.g. (Foo) -> int — LambdaMetafactory handles auto-boxing
            );
            return (Function<Object, Object>) callSite.getTarget().invokeExact();
        } catch (Throwable t) {
            return null;  // Fall back to existing mechanisms
        }
    }

    /**
     * Try to get a Lookup with private access to the target class via privateLookupIn (JDK 9+).
     * Returns the fallback Lookup if privateLookupIn is unavailable or fails.
     */
    private static MethodHandles.Lookup tryPrivateLookup(Class<?> targetClass, MethodHandles.Lookup fallback) {
        if (PRIVATE_LOOKUP_IN_METHOD != null && LOOKUP != null) {
            try {
                Object result = PRIVATE_LOOKUP_IN_METHOD.invoke(null, targetClass, LOOKUP);
                if (result instanceof MethodHandles.Lookup) {
                    return (MethodHandles.Lookup) result;
                }
            } catch (Exception e) {
                // privateLookupIn failed, use fallback
            }
        }
        return fallback;
    }

    public static Accessor createFieldAccessor(Field field, String uniqueFieldName) {
        boolean isPublicField = Modifier.isPublic(field.getModifiers()) && Modifier.isPublic(field.getDeclaringClass().getModifiers());

        // ── PHASE 1: Modern path (JDK 9+) ──
        // Try privateLookupIn-based paths FIRST — no setAccessible needed.
        // This is future-proof for Java 25+ where setAccessible is increasingly restricted.
        if (PRIVATE_LOOKUP_IN_METHOD != null) {
            Accessor accessor = createWithVarHandle(field, uniqueFieldName);
            if (accessor != null) {
                return accessor;
            }
        }

        // ── PHASE 2: Legacy path (JDK 8, or JDK 9+ where modern path failed) ──
        if (!isPublicField) {
            ExceptionUtilities.safelyIgnoreException(() -> field.setAccessible(true));
        }

        try {
            MethodHandles.Lookup fieldLookup = MethodHandles.lookup();
            MethodHandle handle = fieldLookup.unreflectGetter(field);
            Function<Object, Object> lambda = createLambdaAccessor(fieldLookup, handle);
            if (lambda != null) {
                return new Accessor(field, handle, uniqueFieldName, field.getName(), Modifier.isPublic(field.getModifiers()), false, lambda);
            }
            return new Accessor(field, handle, uniqueFieldName, field.getName(), Modifier.isPublic(field.getModifiers()), false);
        } catch (IllegalAccessException ex) {
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
            Object privateLookupObj = PRIVATE_LOOKUP_IN_METHOD.invoke(null, declaringClass, LOOKUP);
            if (privateLookupObj == null) {
                return null;
            }

            // First try: MethodHandle + LambdaMetafactory via privateLookup (fastest path)
            if (privateLookupObj instanceof MethodHandles.Lookup) {
                MethodHandles.Lookup privateLookup = (MethodHandles.Lookup) privateLookupObj;
                try {
                    MethodHandle handle = privateLookup.unreflectGetter(field);
                    Function<Object, Object> lambda = createLambdaAccessor(privateLookup, handle);
                    if (lambda != null) {
                        return new Accessor(field, handle, uniqueFieldName, field.getName(), Modifier.isPublic(field.getModifiers()), false, lambda);
                    }
                    // Lambda failed but handle works — use MethodHandle path
                    return new Accessor(field, handle, uniqueFieldName, field.getName(), Modifier.isPublic(field.getModifiers()), false);
                } catch (IllegalAccessException e) {
                    // unreflectGetter failed — fall through to VarHandle
                }
            }

            // Second try: VarHandle (existing fallback path)
            Object varHandle = FIND_VAR_HANDLE_METHOD.invoke(privateLookupObj, declaringClass, field.getName(), field.getType());
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
            MethodHandles.Lookup methodLookup = MethodHandles.publicLookup();
            MethodType type = MethodType.methodType(field.getType());
            MethodHandle handle = methodLookup.findVirtual(field.getDeclaringClass(), methodName, type);
            Function<Object, Object> lambda = createLambdaAccessor(methodLookup, handle);
            if (lambda != null) {
                return new Accessor(field, handle, uniqueFieldName, methodName, true, true, lambda);
            }
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
            // LambdaMetafactory path: JIT-inlinable, near-direct field access speed
            if (function != null && !functionFailed) {
                try {
                    return function.apply(o);
                } catch (Throwable t) {
                    functionFailed = true;
                }
            }

            // Try VarHandle (JDK 17+ fallback for final fields)
            if (varHandle != null && !varHandleFailed) {
                try {
                    return VAR_HANDLE_GET_METHOD.invoke(varHandle, o);
                } catch (Throwable t) {
                    varHandleFailed = true;
                }
            }

            // Try MethodHandle (JDK 8-16 or method accessor)
            if (methodHandle != null && !methodHandleFailed) {
                try {
                    return methodHandle.invoke(o);
                } catch (Throwable t) {
                    methodHandleFailed = true;
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
