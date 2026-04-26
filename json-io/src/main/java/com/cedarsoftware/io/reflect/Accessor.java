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
    private static final byte PRIMITIVE_NONE = 0;
    private static final byte PRIMITIVE_BOOLEAN = 1;
    private static final byte PRIMITIVE_BYTE = 2;
    private static final byte PRIMITIVE_CHAR = 3;
    private static final byte PRIMITIVE_SHORT = 4;
    private static final byte PRIMITIVE_INT = 5;
    private static final byte PRIMITIVE_LONG = 6;
    private static final byte PRIMITIVE_FLOAT = 7;
    private static final byte PRIMITIVE_DOUBLE = 8;

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
    private final byte primitiveKind;
    private final Object primitiveFunction;
    private Function<Object, Object> function;  // LambdaMetafactory-generated fast getter (JIT-inlinable)
    private volatile boolean primitiveFunctionFailed;
    private volatile boolean functionFailed;
    private volatile boolean varHandleFailed;
    private volatile boolean methodHandleFailed;

    /** Primitive boolean getter used by the LambdaMetafactory fast path. */
    @FunctionalInterface
    public interface BooleanGetter {
        boolean get(Object target);
    }

    /** Primitive byte getter used by the LambdaMetafactory fast path. */
    @FunctionalInterface
    public interface ByteGetter {
        byte get(Object target);
    }

    /** Primitive char getter used by the LambdaMetafactory fast path. */
    @FunctionalInterface
    public interface CharGetter {
        char get(Object target);
    }

    /** Primitive short getter used by the LambdaMetafactory fast path. */
    @FunctionalInterface
    public interface ShortGetter {
        short get(Object target);
    }

    /** Primitive int getter used by the LambdaMetafactory fast path. */
    @FunctionalInterface
    public interface IntGetter {
        int get(Object target);
    }

    /** Primitive long getter used by the LambdaMetafactory fast path. */
    @FunctionalInterface
    public interface LongGetter {
        long get(Object target);
    }

    /** Primitive float getter used by the LambdaMetafactory fast path. */
    @FunctionalInterface
    public interface FloatGetter {
        float get(Object target);
    }

    /** Primitive double getter used by the LambdaMetafactory fast path. */
    @FunctionalInterface
    public interface DoubleGetter {
        double get(Object target);
    }

    // Private constructor for MethodHandle-based access
    private Accessor(Field field, MethodHandle methodHandle, String uniqueFieldName, String fieldOrMethodName, boolean isPublic, boolean isMethod) {
        this(field, methodHandle, null, uniqueFieldName, fieldOrMethodName, isPublic, isMethod, null, null);
    }

    // Private constructor for VarHandle-based access (JDK 17+)
    private Accessor(Field field, Object varHandle, String uniqueFieldName, String fieldOrMethodName, boolean isPublic) {
        this(field, null, varHandle, uniqueFieldName, fieldOrMethodName, isPublic, false, null, null);
    }

    // Private constructor for LambdaMetafactory-accelerated access
    private Accessor(Field field, MethodHandle methodHandle, String uniqueFieldName, String fieldOrMethodName,
                     boolean isPublic, boolean isMethod, Function<Object, Object> function) {
        this(field, methodHandle, null, uniqueFieldName, fieldOrMethodName, isPublic, isMethod, function, null);
    }

    // Private constructor for primitive LambdaMetafactory-accelerated access
    private Accessor(Field field, MethodHandle methodHandle, String uniqueFieldName, String fieldOrMethodName,
                     boolean isPublic, boolean isMethod, Object primitiveFunction) {
        this(field, methodHandle, null, uniqueFieldName, fieldOrMethodName, isPublic, isMethod, null, primitiveFunction);
    }

    private Accessor(Field field, MethodHandle methodHandle, Object varHandle, String uniqueFieldName, String fieldOrMethodName,
                     boolean isPublic, boolean isMethod, Function<Object, Object> function, Object primitiveFunction) {
        this.field = field;
        this.methodHandle = methodHandle;
        this.varHandle = varHandle;
        this.uniqueFieldName = uniqueFieldName;
        this.fieldOrMethodName = fieldOrMethodName;
        this.isPublic = isPublic;
        this.isMethod = isMethod;
        this.primitiveKind = primitiveKind(field.getType());
        this.primitiveFunction = primitiveFunction;
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
     * Attempt to create a LambdaMetafactory-generated primitive getter for primitive fields/getters.
     * Returns null if creation fails or the getter does not return a primitive type.
     */
    private static Object createPrimitiveLambdaAccessor(MethodHandles.Lookup lookup, MethodHandle getter) {
        byte primitiveKind = primitiveKind(getter.type().returnType());
        if (primitiveKind == PRIMITIVE_NONE) {
            return null;
        }

        try {
            Class<?> targetClass = getter.type().parameterType(0);
            MethodHandles.Lookup lambdaLookup = tryPrivateLookup(targetClass, lookup);

            // If we couldn't get a private lookup and the target class is not public,
            // the generated lambda would fail at runtime with IllegalAccessError
            if (lambdaLookup == lookup && !Modifier.isPublic(targetClass.getModifiers())) {
                return null;
            }

            switch (primitiveKind) {
                case PRIMITIVE_BOOLEAN:
                    return createPrimitiveLambda(lambdaLookup, getter, BooleanGetter.class,
                            MethodType.methodType(boolean.class, Object.class));
                case PRIMITIVE_BYTE:
                    return createPrimitiveLambda(lambdaLookup, getter, ByteGetter.class,
                            MethodType.methodType(byte.class, Object.class));
                case PRIMITIVE_CHAR:
                    return createPrimitiveLambda(lambdaLookup, getter, CharGetter.class,
                            MethodType.methodType(char.class, Object.class));
                case PRIMITIVE_SHORT:
                    return createPrimitiveLambda(lambdaLookup, getter, ShortGetter.class,
                            MethodType.methodType(short.class, Object.class));
                case PRIMITIVE_INT:
                    return createPrimitiveLambda(lambdaLookup, getter, IntGetter.class,
                            MethodType.methodType(int.class, Object.class));
                case PRIMITIVE_LONG:
                    return createPrimitiveLambda(lambdaLookup, getter, LongGetter.class,
                            MethodType.methodType(long.class, Object.class));
                case PRIMITIVE_FLOAT:
                    return createPrimitiveLambda(lambdaLookup, getter, FloatGetter.class,
                            MethodType.methodType(float.class, Object.class));
                case PRIMITIVE_DOUBLE:
                    return createPrimitiveLambda(lambdaLookup, getter, DoubleGetter.class,
                            MethodType.methodType(double.class, Object.class));
                default:
                    return null;
            }
        } catch (Throwable t) {
            return null;  // Fall back to existing mechanisms
        }
    }

    private static Object createPrimitiveLambda(MethodHandles.Lookup lambdaLookup, MethodHandle getter,
                                                Class<?> getterInterface, MethodType samType) throws Throwable {
        CallSite callSite = LambdaMetafactory.metafactory(
                lambdaLookup,
                "get",
                MethodType.methodType(getterInterface),
                samType,
                getter,
                getter.type()
        );
        return callSite.getTarget().invoke();
    }

    private static byte primitiveKind(Class<?> type) {
        if (type == boolean.class) {
            return PRIMITIVE_BOOLEAN;
        }
        if (type == byte.class) {
            return PRIMITIVE_BYTE;
        }
        if (type == char.class) {
            return PRIMITIVE_CHAR;
        }
        if (type == short.class) {
            return PRIMITIVE_SHORT;
        }
        if (type == int.class) {
            return PRIMITIVE_INT;
        }
        if (type == long.class) {
            return PRIMITIVE_LONG;
        }
        if (type == float.class) {
            return PRIMITIVE_FLOAT;
        }
        if (type == double.class) {
            return PRIMITIVE_DOUBLE;
        }
        return PRIMITIVE_NONE;
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
            Object primitiveLambda = createPrimitiveLambdaAccessor(fieldLookup, handle);
            if (primitiveLambda != null) {
                return new Accessor(field, handle, uniqueFieldName, field.getName(), Modifier.isPublic(field.getModifiers()), false, primitiveLambda);
            }
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
                    Object primitiveLambda = createPrimitiveLambdaAccessor(privateLookup, handle);
                    if (primitiveLambda != null) {
                        return new Accessor(field, handle, uniqueFieldName, field.getName(), Modifier.isPublic(field.getModifiers()), false, primitiveLambda);
                    }
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
            Object primitiveLambda = createPrimitiveLambdaAccessor(methodLookup, handle);
            if (primitiveLambda != null) {
                return new Accessor(field, handle, uniqueFieldName, methodName, true, true, primitiveLambda);
            }
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
            // Primitive LambdaMetafactory path; retrieve() boxes for compatibility.
            if (primitiveFunction != null && !primitiveFunctionFailed) {
                try {
                    return retrievePrimitive(o);
                } catch (Throwable t) {
                    primitiveFunctionFailed = true;
                }
            }

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

    /**
     * Return this accessor's value as a primitive boolean.
     * Intended for callers that already know the underlying field/getter type is boolean.
     */
    public boolean getBoolean(Object o) {
        ensureTarget(o);
        try {
            if (primitiveKind == PRIMITIVE_BOOLEAN && primitiveFunction != null && !primitiveFunctionFailed) {
                try {
                    return ((BooleanGetter) primitiveFunction).get(o);
                } catch (Throwable t) {
                    primitiveFunctionFailed = true;
                }
            }
            return (Boolean) retrieve(o);
        } catch (JsonIoException e) {
            throw e;
        } catch (Throwable t) {
            throw retrievalException(t);
        }
    }

    /**
     * Return this accessor's value as a primitive byte.
     * Intended for callers that already know the underlying field/getter type is byte.
     */
    public byte getByte(Object o) {
        ensureTarget(o);
        try {
            if (primitiveKind == PRIMITIVE_BYTE && primitiveFunction != null && !primitiveFunctionFailed) {
                try {
                    return ((ByteGetter) primitiveFunction).get(o);
                } catch (Throwable t) {
                    primitiveFunctionFailed = true;
                }
            }
            return ((Number) retrieve(o)).byteValue();
        } catch (JsonIoException e) {
            throw e;
        } catch (Throwable t) {
            throw retrievalException(t);
        }
    }

    /**
     * Return this accessor's value as a primitive char.
     * Intended for callers that already know the underlying field/getter type is char.
     */
    public char getChar(Object o) {
        ensureTarget(o);
        try {
            if (primitiveKind == PRIMITIVE_CHAR && primitiveFunction != null && !primitiveFunctionFailed) {
                try {
                    return ((CharGetter) primitiveFunction).get(o);
                } catch (Throwable t) {
                    primitiveFunctionFailed = true;
                }
            }
            return (Character) retrieve(o);
        } catch (JsonIoException e) {
            throw e;
        } catch (Throwable t) {
            throw retrievalException(t);
        }
    }

    /**
     * Return this accessor's value as a primitive short.
     * Intended for callers that already know the underlying field/getter type is short.
     */
    public short getShort(Object o) {
        ensureTarget(o);
        try {
            if (primitiveKind == PRIMITIVE_SHORT && primitiveFunction != null && !primitiveFunctionFailed) {
                try {
                    return ((ShortGetter) primitiveFunction).get(o);
                } catch (Throwable t) {
                    primitiveFunctionFailed = true;
                }
            }
            return ((Number) retrieve(o)).shortValue();
        } catch (JsonIoException e) {
            throw e;
        } catch (Throwable t) {
            throw retrievalException(t);
        }
    }

    /**
     * Return this accessor's value as a primitive int.
     * Intended for callers that already know the underlying field/getter type is int.
     */
    public int getInt(Object o) {
        ensureTarget(o);
        try {
            if (primitiveKind == PRIMITIVE_INT && primitiveFunction != null && !primitiveFunctionFailed) {
                try {
                    return ((IntGetter) primitiveFunction).get(o);
                } catch (Throwable t) {
                    primitiveFunctionFailed = true;
                }
            }
            return ((Number) retrieve(o)).intValue();
        } catch (JsonIoException e) {
            throw e;
        } catch (Throwable t) {
            throw retrievalException(t);
        }
    }

    /**
     * Return this accessor's value as a primitive long.
     * Intended for callers that already know the underlying field/getter type is long.
     */
    public long getLong(Object o) {
        ensureTarget(o);
        try {
            if (primitiveKind == PRIMITIVE_LONG && primitiveFunction != null && !primitiveFunctionFailed) {
                try {
                    return ((LongGetter) primitiveFunction).get(o);
                } catch (Throwable t) {
                    primitiveFunctionFailed = true;
                }
            }
            return ((Number) retrieve(o)).longValue();
        } catch (JsonIoException e) {
            throw e;
        } catch (Throwable t) {
            throw retrievalException(t);
        }
    }

    /**
     * Return this accessor's value as a primitive float.
     * Intended for callers that already know the underlying field/getter type is float.
     */
    public float getFloat(Object o) {
        ensureTarget(o);
        try {
            if (primitiveKind == PRIMITIVE_FLOAT && primitiveFunction != null && !primitiveFunctionFailed) {
                try {
                    return ((FloatGetter) primitiveFunction).get(o);
                } catch (Throwable t) {
                    primitiveFunctionFailed = true;
                }
            }
            return ((Number) retrieve(o)).floatValue();
        } catch (JsonIoException e) {
            throw e;
        } catch (Throwable t) {
            throw retrievalException(t);
        }
    }

    /**
     * Return this accessor's value as a primitive double.
     * Intended for callers that already know the underlying field/getter type is double.
     */
    public double getDouble(Object o) {
        ensureTarget(o);
        try {
            if (primitiveKind == PRIMITIVE_DOUBLE && primitiveFunction != null && !primitiveFunctionFailed) {
                try {
                    return ((DoubleGetter) primitiveFunction).get(o);
                } catch (Throwable t) {
                    primitiveFunctionFailed = true;
                }
            }
            return ((Number) retrieve(o)).doubleValue();
        } catch (JsonIoException e) {
            throw e;
        } catch (Throwable t) {
            throw retrievalException(t);
        }
    }

    private void ensureTarget(Object o) {
        if (o == null) {
            throw new JsonIoException("Cannot retrieve field value from null object for field: " + getActualFieldName());
        }
    }

    private Object retrievePrimitive(Object o) {
        switch (primitiveKind) {
            case PRIMITIVE_BOOLEAN:
                return ((BooleanGetter) primitiveFunction).get(o);
            case PRIMITIVE_BYTE:
                return ((ByteGetter) primitiveFunction).get(o);
            case PRIMITIVE_CHAR:
                return ((CharGetter) primitiveFunction).get(o);
            case PRIMITIVE_SHORT:
                return ((ShortGetter) primitiveFunction).get(o);
            case PRIMITIVE_INT:
                return ((IntGetter) primitiveFunction).get(o);
            case PRIMITIVE_LONG:
                return ((LongGetter) primitiveFunction).get(o);
            case PRIMITIVE_FLOAT:
                return ((FloatGetter) primitiveFunction).get(o);
            case PRIMITIVE_DOUBLE:
                return ((DoubleGetter) primitiveFunction).get(o);
            default:
                throw new IllegalStateException("No primitive getter for field: " + getActualFieldName());
        }
    }

    private JsonIoException retrievalException(Throwable t) {
        return new JsonIoException("Failed to retrieve field value: " + getActualFieldName() + " in class: " + field.getDeclaringClass().getName(), t);
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
