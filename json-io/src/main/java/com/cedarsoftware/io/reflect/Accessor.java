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
 * Reads one field of an object for the writer: the field itself, or the getter method that stands in for it.
 *
 * <p>Either way the read goes through one {@link MethodHandle}, adapted once to {@code (Object)Object} and, for a
 * primitive field, to {@code (Object)int} and its like, and called with {@code invokeExact}. The handle comes from
 * a private lookup on JDK 9 and later and from {@code setAccessible} on JDK 8. A field that cannot be reached at all
 * has no handle; it is read with {@code Field.get}, and for a JDK class that refuses, as null.</p>
 *
 * <p><b>Why one mechanism.</b> Through 4.113.0 a getter was called through a {@code LambdaMetafactory} lambda where
 * one could be made, a primitive field was read through a lambda bound to a {@code VarHandle}, and an object field
 * through {@code MethodHandle.invoke}. Measured on JDK 17, 21 and 25 (2,000 objects of 10 fields written to JSON,
 * before and after in interleaved fresh JVMs): through the adapted handle a class of ten primitive fields writes
 * 10-12% faster than through the VarHandle lambdas, a class of ten Strings 2-3% faster than through {@code invoke},
 * and a class written through its getters within 3% of the getter lambdas (4% slower for one class on JDK 25). On
 * JDK 8, which never had the lambdas, everything is 4-12% faster. Do not bring the lambdas back without measuring:
 * {@code LambdaMetafactory} cannot make one for a field at all, and the writer's call site sees a different accessor
 * for every field, so a lambda there is an interface call that is not inlined either.</p>
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
    // JDK 9+: MethodHandles.privateLookupIn(Class, Lookup), reached by reflection because json-io compiles for Java 8.
    // A lookup with private access to a class reads its private fields without setAccessible.
    private static final Object LOOKUP;
    private static final Method PRIVATE_LOOKUP_IN_METHOD;

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
        Object lookup = null;
        Method privateLookupInMethod = null;

        if (SystemUtilities.currentJdkMajorVersion() >= 9) {
            try {
                Class<?> methodHandlesClass = Class.forName("java.lang.invoke.MethodHandles");
                Class<?> lookupClass = Class.forName("java.lang.invoke.MethodHandles$Lookup");
                lookup = ReflectionUtils.getMethod(methodHandlesClass, "lookup").invoke(null);
                privateLookupInMethod = ReflectionUtils.getMethod(methodHandlesClass,
                        "privateLookupIn", Class.class, lookupClass);
            } catch (Exception e) {
                // No private lookup: fields are opened with setAccessible, as on JDK 8
                lookup = null;
                privateLookupInMethod = null;
            }
        }

        LOOKUP = lookup;
        PRIVATE_LOOKUP_IN_METHOD = privateLookupInMethod;
    }

    private final String uniqueFieldName;
    private final Field field;
    private final boolean isMethod;
    private final String fieldOrMethodName;
    private final MethodHandle methodHandle;    // reads the field or calls the getter; null when neither can be reached
    private final MethodHandle boxedGetter;     // methodHandle as (Object)Object, for invokeExact
    private final MethodHandle primitiveGetter; // methodHandle as (Object)int, (Object)long, ... for a primitive field
    private final boolean isPublic;
    private final byte primitiveKind;
    private volatile boolean handleFailed;      // a field's handle threw: Field.get is used from then on

    /** @deprecated Not used by json-io since 4.114.0. Kept so that code naming it still compiles. */
    @Deprecated
    @FunctionalInterface
    public interface BooleanGetter {
        boolean get(Object target);
    }

    /** @deprecated Not used by json-io since 4.114.0. Kept so that code naming it still compiles. */
    @Deprecated
    @FunctionalInterface
    public interface ByteGetter {
        byte get(Object target);
    }

    /** @deprecated Not used by json-io since 4.114.0. Kept so that code naming it still compiles. */
    @Deprecated
    @FunctionalInterface
    public interface CharGetter {
        char get(Object target);
    }

    /** @deprecated Not used by json-io since 4.114.0. Kept so that code naming it still compiles. */
    @Deprecated
    @FunctionalInterface
    public interface ShortGetter {
        short get(Object target);
    }

    /** @deprecated Not used by json-io since 4.114.0. Kept so that code naming it still compiles. */
    @Deprecated
    @FunctionalInterface
    public interface IntGetter {
        int get(Object target);
    }

    /** @deprecated Not used by json-io since 4.114.0. Kept so that code naming it still compiles. */
    @Deprecated
    @FunctionalInterface
    public interface LongGetter {
        long get(Object target);
    }

    /** @deprecated Not used by json-io since 4.114.0. Kept so that code naming it still compiles. */
    @Deprecated
    @FunctionalInterface
    public interface FloatGetter {
        float get(Object target);
    }

    /** @deprecated Not used by json-io since 4.114.0. Kept so that code naming it still compiles. */
    @Deprecated
    @FunctionalInterface
    public interface DoubleGetter {
        double get(Object target);
    }

    private Accessor(Field field, MethodHandle methodHandle, String uniqueFieldName, String fieldOrMethodName,
                     boolean isPublic, boolean isMethod) {
        this.field = field;
        this.methodHandle = methodHandle;
        this.uniqueFieldName = uniqueFieldName;
        this.fieldOrMethodName = fieldOrMethodName;
        this.isPublic = isPublic;
        this.isMethod = isMethod;
        this.primitiveKind = primitiveKind(field.getType());

        // Adapted once, here, so that every read is an invokeExact: the call site's signature is the handle's own
        // and nothing is converted or checked per call. MethodHandle.invoke on the unadapted handle adapts per call.
        MethodHandle boxed = null;
        MethodHandle primitive = null;
        if (methodHandle != null) {
            try {
                boxed = methodHandle.asType(MethodType.methodType(Object.class, Object.class));
                if (primitiveKind != PRIMITIVE_NONE) {
                    primitive = methodHandle.asType(MethodType.methodType(field.getType(), Object.class));
                }
            } catch (RuntimeException e) {
                boxed = null;       // not adaptable: Field.get reads the field
                primitive = null;
            }
        }
        this.boxedGetter = boxed;
        this.primitiveGetter = primitive;
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
     * A Lookup with private access to the target class via privateLookupIn (JDK 9+), or null when there is none:
     * JDK 8, or a class whose module is not open to json-io.
     */
    private static MethodHandles.Lookup privateLookup(Class<?> targetClass) {
        if (PRIVATE_LOOKUP_IN_METHOD != null && LOOKUP != null) {
            try {
                Object result = PRIVATE_LOOKUP_IN_METHOD.invoke(null, targetClass, LOOKUP);
                if (result instanceof MethodHandles.Lookup) {
                    return (MethodHandles.Lookup) result;
                }
            } catch (Exception e) {
                // privateLookupIn failed: the caller opens the field with setAccessible
            }
        }
        return null;
    }

    /**
     * An accessor that reads the field itself. When the field cannot be reached (a field of a JDK class in a
     * module that is closed to json-io) the accessor has no handle, and {@link #retrieve(Object)} returns null.
     */
    public static Accessor createFieldAccessor(Field field, String uniqueFieldName) {
        return new Accessor(field, fieldGetter(field), uniqueFieldName, field.getName(),
                Modifier.isPublic(field.getModifiers()), false);
    }

    private static MethodHandle fieldGetter(Field field) {
        // JDK 9+: a private lookup reads the field with no setAccessible, which newer JDKs restrict step by step.
        MethodHandles.Lookup privateLookup = privateLookup(field.getDeclaringClass());
        if (privateLookup != null) {
            try {
                return privateLookup.unreflectGetter(field);
            } catch (IllegalAccessException e) {
                // fall through to setAccessible
            }
        }

        // JDK 8, or no private lookup for this class
        boolean isPublicField = Modifier.isPublic(field.getModifiers())
                && Modifier.isPublic(field.getDeclaringClass().getModifiers());
        if (!isPublicField) {
            ExceptionUtilities.safelyIgnoreException(() -> field.setAccessible(true));
        }
        try {
            return MethodHandles.lookup().unreflectGetter(field);
        } catch (IllegalAccessException ex) {
            return null;    // retrieve() falls back to Field.get
        }
    }

    /**
     * An accessor that calls the public method {@code methodName}, which takes no argument and returns the field's
     * type. Returns null when the class has no such method.
     */
    public static Accessor createMethodAccessor(Field field, String methodName, String uniqueFieldName) {
        try {
            MethodType type = MethodType.methodType(field.getType());
            MethodHandle handle = MethodHandles.publicLookup()
                    .findVirtual(field.getDeclaringClass(), methodName, type);
            return new Accessor(field, handle, uniqueFieldName, methodName, true, true);
        } catch (Exception ignore) {
            return null;
        }
    }

    public Object retrieve(Object o) {
        if (o == null) {
            throw new JsonIoException("Cannot retrieve field value from null object for field: "
                    + getActualFieldName());
        }

        if (boxedGetter != null && !handleFailed) {
            try {
                return (Object) boxedGetter.invokeExact(o);
            } catch (Throwable t) {
                handleThrew();      // the field is read instead
            }
        }
        return readField(o);
    }

    /**
     * A call through the handle threw, and the field is read in its place. A handle that reads the field cannot be
     * used after all, so Field.get reads that field from now on. A getter is another matter: it threw for this
     * object, in the state this object is in, and says nothing about the next one. It is called again for the next
     * read. (It used to be switched off for good, and every object written afterwards got the field's value where
     * the one before had got the getter's.)
     */
    private void handleThrew() {
        if (!isMethod) {
            handleFailed = true;
        }
    }

    /** The field's own value, read with Field.get. */
    private Object readField(Object o) {
        try {
            return field.get(o);
        } catch (IllegalAccessException e) {
            Class<?> dc = field.getDeclaringClass();
            if (isJdkInternalClass(dc)) {
                return handleInaccessibleJdkField(dc, getActualFieldName());
            }
            throw new JsonIoException("Failed to retrieve field value: " + getActualFieldName() + " in class: "
                    + dc.getName(), e);
        } catch (Throwable t) {
            throw retrievalException(t);
        }
    }

    /**
     * Return this accessor's value as a primitive boolean.
     * Intended for callers that already know the underlying field/getter type is boolean.
     */
    public boolean getBoolean(Object o) {
        ensureTarget(o);
        try {
            boolean threw = false;
            if (primitiveKind == PRIMITIVE_BOOLEAN && primitiveGetter != null && !handleFailed) {
                try {
                    return (boolean) primitiveGetter.invokeExact(o);
                } catch (Throwable t) {
                    handleThrew();
                    threw = true;
                }
            }
            Object v = threw ? readField(o) : retrieve(o);
            return v == null ? false : (Boolean) v;
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
            boolean threw = false;
            if (primitiveKind == PRIMITIVE_BYTE && primitiveGetter != null && !handleFailed) {
                try {
                    return (byte) primitiveGetter.invokeExact(o);
                } catch (Throwable t) {
                    handleThrew();
                    threw = true;
                }
            }
            Object v = threw ? readField(o) : retrieve(o);
            return v == null ? (byte) 0 : ((Number) v).byteValue();
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
            boolean threw = false;
            if (primitiveKind == PRIMITIVE_CHAR && primitiveGetter != null && !handleFailed) {
                try {
                    return (char) primitiveGetter.invokeExact(o);
                } catch (Throwable t) {
                    handleThrew();
                    threw = true;
                }
            }
            Object v = threw ? readField(o) : retrieve(o);
            return v == null ? (char) 0 : (Character) v;
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
            boolean threw = false;
            if (primitiveKind == PRIMITIVE_SHORT && primitiveGetter != null && !handleFailed) {
                try {
                    return (short) primitiveGetter.invokeExact(o);
                } catch (Throwable t) {
                    handleThrew();
                    threw = true;
                }
            }
            Object v = threw ? readField(o) : retrieve(o);
            return v == null ? (short) 0 : ((Number) v).shortValue();
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
            boolean threw = false;
            if (primitiveKind == PRIMITIVE_INT && primitiveGetter != null && !handleFailed) {
                try {
                    return (int) primitiveGetter.invokeExact(o);
                } catch (Throwable t) {
                    handleThrew();
                    threw = true;
                }
            }
            Object v = threw ? readField(o) : retrieve(o);
            return v == null ? 0 : ((Number) v).intValue();
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
            boolean threw = false;
            if (primitiveKind == PRIMITIVE_LONG && primitiveGetter != null && !handleFailed) {
                try {
                    return (long) primitiveGetter.invokeExact(o);
                } catch (Throwable t) {
                    handleThrew();
                    threw = true;
                }
            }
            Object v = threw ? readField(o) : retrieve(o);
            return v == null ? 0L : ((Number) v).longValue();
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
            boolean threw = false;
            if (primitiveKind == PRIMITIVE_FLOAT && primitiveGetter != null && !handleFailed) {
                try {
                    return (float) primitiveGetter.invokeExact(o);
                } catch (Throwable t) {
                    handleThrew();
                    threw = true;
                }
            }
            Object v = threw ? readField(o) : retrieve(o);
            return v == null ? 0f : ((Number) v).floatValue();
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
            boolean threw = false;
            if (primitiveKind == PRIMITIVE_DOUBLE && primitiveGetter != null && !handleFailed) {
                try {
                    return (double) primitiveGetter.invokeExact(o);
                } catch (Throwable t) {
                    handleThrew();
                    threw = true;
                }
            }
            Object v = threw ? readField(o) : retrieve(o);
            return v == null ? 0d : ((Number) v).doubleValue();
        } catch (JsonIoException e) {
            throw e;
        } catch (Throwable t) {
            throw retrievalException(t);
        }
    }

    private void ensureTarget(Object o) {
        if (o == null) {
            throw new JsonIoException("Cannot retrieve field value from null object for field: "
                    + getActualFieldName());
        }
    }

    private JsonIoException retrievalException(Throwable t) {
        return new JsonIoException("Failed to retrieve field value: " + getActualFieldName() + " in class: "
                + field.getDeclaringClass().getName(), t);
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
        // This allows serialization to continue without the restricted field.
        //
        // Every caller must tolerate this null, INCLUDING the primitive getters. A primitive field can never
        // legitimately hold null, so getInt() and friends used to unbox the result unchecked -- and an
        // inaccessible primitive field therefore turned this deliberate "skip" into a NullPointerException.
        // java.sql.SQLException.vendorCode is one: java.sql is exported but not opened, so no access path
        // reaches it, and the whole SQLException family failed to serialize at all.
        return null;
    }
}
