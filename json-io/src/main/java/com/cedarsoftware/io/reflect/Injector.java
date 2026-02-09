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
import java.util.function.BiConsumer;
import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.util.Converter;
import com.cedarsoftware.util.ReflectionUtils;
import com.cedarsoftware.util.StringUtilities;
import com.cedarsoftware.util.SystemUtilities;

/**
 * High-performance field injection utility that automatically adapts to different JDK versions
 * for optimal performance and compatibility.
 * 
 * <p>This class uses the {@code java.version} system property to automatically detect the
 * JDK version and select the most appropriate field injection strategy:</p>
 * 
 * <ul>
 * <li><strong>JDK 8-16:</strong> Uses {@code Field.set()} for final fields and {@code MethodHandle} for regular fields</li>
 * <li><strong>JDK 17+:</strong> Uses {@code VarHandle} for improved performance and module system compatibility</li>
 * </ul>
 * 
 * <p>The JDK version detection and strategy selection is completely automatic and requires no
 * user configuration. This ensures optimal performance across all supported JDK versions while
 * maintaining compatibility with the module system introduced in JDK 9+.</p>
 * 
 * <h3>System Properties Used:</h3>
 * <ul>
 * <li>{@code java.version} - Automatically detected by the JVM to determine injection strategy</li>
 * </ul>
 * 
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
    private static final MethodHandle VAR_HANDLE_SET_METHOD;
    private static final Class<?> VAR_HANDLE_CLASS;        // although appears unused, it is intentional for caching

    static {
        int javaVersion = SystemUtilities.currentJdkMajorVersion();
        IS_JDK17_OR_HIGHER = javaVersion >= 17;

        Object lookup = null;
        Method privateLookupInMethod = null;
        Method findVarHandleMethod = null;
        MethodHandle varHandleSetMethod = null;
        Class<?> varHandleClass = null;

        if (javaVersion >= 9) {
            try {
                Class<?> methodHandlesClass = Class.forName("java.lang.invoke.MethodHandles");
                Class<?> lookupClass = Class.forName("java.lang.invoke.MethodHandles$Lookup");

                Method lookupMethod = ReflectionUtils.getMethod(methodHandlesClass, "lookup");
                lookup = lookupMethod.invoke(null);

                privateLookupInMethod = ReflectionUtils.getMethod(methodHandlesClass,
                        "privateLookupIn", Class.class, lookupClass);

                varHandleClass = Class.forName("java.lang.invoke.VarHandle");
                findVarHandleMethod = ReflectionUtils.getMethod(lookupClass,
                        "findVarHandle", Class.class, String.class, Class.class);
                MethodType setType = MethodType.methodType(void.class, Object.class, Object.class);
                varHandleSetMethod = MethodHandles.publicLookup().findVirtual(varHandleClass, "set", setType);
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
    private BiConsumer<Object, Object> consumer; // LambdaMetafactory-generated fast setter (JIT-inlinable)

    // Cached values for performance - computed once at construction
    private final Class<?> fieldType;
    private final String fieldName;

    // Constructor for MethodHandle injection
    private Injector(Field field, MethodHandle handle, String uniqueFieldName, String displayName) {
        this.field = field;
        this.displayName = displayName;
        this.uniqueFieldName = uniqueFieldName;
        this.injector = handle;
        this.useFieldSet = false;

        // Cache values for performance
        this.fieldType = field.getType();
        this.fieldName = field.getName();
    }

    // Constructor for Field.set() fallback injection
    private Injector(Field field, String uniqueFieldName, String displayName, boolean useFieldSet) {
        this.field = field;
        this.displayName = displayName;
        this.uniqueFieldName = uniqueFieldName;
        this.useFieldSet = useFieldSet;

        // Cache values for performance
        this.fieldType = field.getType();
        this.fieldName = field.getName();
    }

    // Constructor for VarHandle-based injection (JDK 17+)
    private Injector(Field field, Object varHandle, String uniqueFieldName, String displayName) {
        this.field = field;
        this.displayName = displayName;
        this.uniqueFieldName = uniqueFieldName;
        this.varHandle = varHandle;
        this.useFieldSet = false;

        // Cache values for performance
        this.fieldType = field.getType();
        this.fieldName = field.getName();
    }

    // Constructor for LambdaMetafactory-accelerated injection
    private Injector(Field field, MethodHandle handle, String uniqueFieldName, String displayName, BiConsumer<Object, Object> consumer) {
        this.field = field;
        this.displayName = displayName;
        this.uniqueFieldName = uniqueFieldName;
        this.injector = handle;
        this.useFieldSet = false;
        this.consumer = consumer;

        // Cache values for performance
        this.fieldType = field.getType();
        this.fieldName = field.getName();
    }

    /**
     * Attempt to create a LambdaMetafactory-generated BiConsumer for the given MethodHandle.
     * The generated BiConsumer is JIT-inlinable, providing near-direct field access speed.
     * Returns null if creation fails (caller should fall back to existing mechanisms).
     *
     * <p>The generated lambda class needs access to the target class (the setter's declaring class).
     * This method uses {@code privateLookupIn} (JDK 9+) to obtain a Lookup with proper access.
     * For non-public classes on JDK 8 (where privateLookupIn is unavailable), returns null
     * to avoid IllegalAccessError at invocation time.</p>
     */
    @SuppressWarnings("unchecked")
    private static BiConsumer<Object, Object> createLambdaConsumer(MethodHandles.Lookup lookup, MethodHandle setter) {
        try {
            Class<?> targetClass = setter.type().parameterType(0);
            MethodHandles.Lookup lambdaLookup = tryPrivateLookup(targetClass, lookup);

            // If we couldn't get a private lookup and the target class is not public,
            // the generated lambda would fail at runtime with IllegalAccessError
            if (lambdaLookup == lookup && !Modifier.isPublic(targetClass.getModifiers())) {
                return null;
            }

            CallSite callSite = LambdaMetafactory.metafactory(
                    lambdaLookup,
                    "accept",
                    MethodType.methodType(BiConsumer.class),
                    MethodType.methodType(void.class, Object.class, Object.class),  // SAM erased type
                    setter,
                    setter.type()  // e.g. (Foo, int)void — LambdaMetafactory handles auto-unboxing
            );
            return (BiConsumer<Object, Object>) callSite.getTarget().invokeExact();
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

    public static Injector create(Field field, String uniqueFieldName) {
        // Security: Validate input parameters
        if (field == null) {
            throw new JsonIoException("Field cannot be null");
        }

        if (StringUtilities.isEmpty(uniqueFieldName)) {
            throw new JsonIoException("Unique field name cannot be null or empty");
        }

        // ── PHASE 1: Modern path (JDK 9+) ──
        // Try privateLookupIn-based paths FIRST — no setAccessible needed.
        // This is future-proof for Java 25+ where setAccessible is increasingly restricted.
        if (PRIVATE_LOOKUP_IN_METHOD != null) {
            Injector injector = createWithVarHandle(field, uniqueFieldName);
            if (injector != null) {
                return injector;
            }
        }

        // ── PHASE 2: Legacy path (JDK 8, or JDK 9+ where modern path failed) ──
        if (!field.isAccessible()) {
            try {
                field.setAccessible(true);
            } catch (Exception e) {
                // setAccessible failed — return Field.set() fallback (last resort)
                return new Injector(field, uniqueFieldName, field.getName(), true);
            }
        }

        // For JDK 8-16, if the field is final, use Field.set() (and try to remove the final modifier)
        boolean isFinal = Modifier.isFinal(field.getModifiers());
        if (isFinal && !IS_JDK17_OR_HIGHER) {
            try {
                Field modifiersField = ReflectionUtils.getField(Field.class, "modifiers");
                if (modifiersField == null) {
                    throw new JsonIoException("Unable to access modifiers field - possible security restriction");
                }
                modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            } catch (Exception ex) {
                throw new JsonIoException("Failed to remove final modifier from field: " + field.getName() +
                        " in class: " + field.getDeclaringClass().getName(), ex);
            }
            return new Injector(field, uniqueFieldName, field.getName(), true);
        }

        // setAccessible succeeded — try MethodHandle → LambdaMetafactory
        try {
            MethodHandles.Lookup fieldLookup = MethodHandles.lookup();
            MethodHandle handle = fieldLookup.unreflectSetter(field);
            BiConsumer<Object, Object> lambda = createLambdaConsumer(fieldLookup, handle);
            if (lambda != null) {
                return new Injector(field, handle, uniqueFieldName, field.getName(), lambda);
            }
            return new Injector(field, handle, uniqueFieldName, field.getName());
        } catch (IllegalAccessException e) {
            // Fallback to Field.set() injection
            return new Injector(field, uniqueFieldName, field.getName(), true);
        }
    }

    private static Injector createWithVarHandle(Field field, String uniqueFieldName) {
        if (PRIVATE_LOOKUP_IN_METHOD == null || FIND_VAR_HANDLE_METHOD == null ||
                VAR_HANDLE_SET_METHOD == null || LOOKUP == null) {
            return null; // Return null to allow fallback to Field.set()
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
                    MethodHandle handle = privateLookup.unreflectSetter(field);
                    BiConsumer<Object, Object> lambda = createLambdaConsumer(privateLookup, handle);
                    if (lambda != null) {
                        return new Injector(field, handle, uniqueFieldName, field.getName(), lambda);
                    }
                    // Lambda failed but handle works — use MethodHandle path
                    return new Injector(field, handle, uniqueFieldName, field.getName());
                } catch (IllegalAccessException e) {
                    // unreflectSetter failed (e.g. final field) — fall through to VarHandle
                }
            }

            // Second try: VarHandle (existing fallback path)
            Object varHandle = FIND_VAR_HANDLE_METHOD.invoke(privateLookupObj, declaringClass, field.getName(), field.getType());
            if (varHandle == null) {
                return null;
            }

            return new Injector(field, varHandle, uniqueFieldName, field.getName());
        } catch (Exception e) {
            // VarHandle creation failed - allow fallback to Field.set()
            return null;
        }
    }

    public static Injector create(Field field, String methodName, String uniqueFieldName) {
        // Security: Validate input parameters
        if (field == null) {
            throw new JsonIoException("Field cannot be null");
        }
        if (StringUtilities.isEmpty(methodName)) {
            throw new JsonIoException("Method name cannot be null or empty");
        }
        if (StringUtilities.isEmpty(uniqueFieldName)) {
            throw new JsonIoException("Unique field name cannot be null or empty");
        }

        try {
            MethodHandles.Lookup methodLookup = MethodHandles.lookup();
            MethodType methodType = MethodType.methodType(void.class, field.getType());
            MethodHandle handle = methodLookup.findVirtual(field.getDeclaringClass(), methodName, methodType);
            BiConsumer<Object, Object> lambda = createLambdaConsumer(methodLookup, handle);
            if (lambda != null) {
                return new Injector(field, handle, uniqueFieldName, methodName, lambda);
            }
            return new Injector(field, handle, uniqueFieldName, methodName);
        } catch (NoSuchMethodException e) {
            throw new JsonIoException("Method not found: " + methodName + " in class: " + field.getDeclaringClass().getName(), e);
        } catch (IllegalAccessException e) {
            throw new JsonIoException("Access denied to method: " + methodName + " in class: " + field.getDeclaringClass().getName(), e);
        }
    }

    public void inject(Object object, Object value) {
        if (object == null) {
            throw new JsonIoException("Attempting to set field: " + fieldName + " on null object.");
        }

        try {
            if (consumer != null) {
                // LambdaMetafactory path: JIT-inlinable, near-direct field access speed
                consumer.accept(object, value);
            } else if (varHandle != null) {
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
                throw new JsonIoException("Unable to set field: " + fieldName + " using " + displayName + ".", e);
            }
            try {
                Object convertedValue = Converter.convert(value, fieldType);
                if (consumer != null) {
                    consumer.accept(object, convertedValue);
                } else if (varHandle != null) {
                    injectWithVarHandle(object, convertedValue);
                } else if (useFieldSet) {
                    field.set(object, convertedValue);
                } else {
                    injector.invoke(object, convertedValue);
                }
            } catch (Throwable t) {
                throw new JsonIoException("Unable to set field: " + fieldName + " using " + displayName + ". Getting a ClassCastException.", e);
            }
        } catch (Throwable t) {
            if (t instanceof JsonIoException) {
                throw (JsonIoException) t;
            }
            throw new JsonIoException("Unable to set field: " + fieldName + " using " + displayName, t);
        }
    }

    private void injectWithVarHandle(Object object, Object value) throws Throwable {
        // Security: Validate VarHandle infrastructure
        if (varHandle == null || VAR_HANDLE_SET_METHOD == null) {
            throw new JsonIoException("Unable to set field: " + getName() + " - VarHandle not available");
        }

        // Security: Validate arguments before VarHandle invocation
        if (object == null) {
            throw new JsonIoException("Cannot inject into null object using VarHandle for field: " + getName());
        }

        try {
            // Performance: Use direct invoke() instead of invokeWithArguments() + Arrays.asList()
            // Eliminates array and list allocation overhead (25-35% faster)
            VAR_HANDLE_SET_METHOD.invoke(varHandle, object, value);
        } catch (Exception e) {
            throw new JsonIoException("VarHandle injection failed for field: " + getName() + " in class: " + field.getDeclaringClass().getName(), e);
        }
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
}
