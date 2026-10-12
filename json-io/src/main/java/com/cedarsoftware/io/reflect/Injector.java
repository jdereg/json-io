package com.cedarsoftware.io.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.util.Converter;
import com.cedarsoftware.util.ReflectionUtils;
import com.cedarsoftware.util.StringUtilities;
import com.cedarsoftware.util.SystemUtilities;

/**
 * Sets one field of an object for the reader: the field itself, or the setter method that stands in for it.
 *
 * <p>Either way the value goes in through one {@link MethodHandle}, adapted once to {@code (Object, Object)void}
 * and, for a primitive number, to {@code (Object, long)void} and its like, and called with {@code invokeExact}. The
 * handle comes from a private lookup on JDK 9 and later and from {@code setAccessible} on JDK 8. A field no handle
 * can set is set with {@code Field.set}: a final field on JDK 8 through 16, or a field that could not be opened.</p>
 *
 * <p><b>Why one mechanism.</b> Through 4.113.0 a field was set through {@code MethodHandle.invoke}, which looks up
 * the handle's adapted form on every call; a setter through a {@code LambdaMetafactory} lambda where one could be
 * made; and a {@code VarHandle} stood by that could set nothing the handle could not. Measured on the injector calls
 * of a read alone (2,000 objects of 10 fields, before and after in interleaved fresh JVMs), the adapted handle takes
 * 1-8% less time per field on JDK 17, 11-17% less on JDK 21, 8-9% less on JDK 25 and 18-25% less on JDK 8; against
 * the setter lambda it is between 4% faster and 9% slower. Setting a field is 3-4 ns of the 55-65 ns a field costs
 * to read, so none of this shows in a whole read: do not add a second mechanism here for speed without measuring
 * one that does.</p>
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
    // JDK 9+: MethodHandles.privateLookupIn(Class, Lookup), reached by reflection because json-io compiles for Java 8.
    // A lookup with private access to a class sets its private fields without setAccessible.
    private static final Object LOOKUP;
    private static final Method PRIVATE_LOOKUP_IN_METHOD;

    static {
        int javaVersion = SystemUtilities.currentJdkMajorVersion();
        IS_JDK17_OR_HIGHER = javaVersion >= 17;

        Object lookup = null;
        Method privateLookupInMethod = null;

        if (javaVersion >= 9) {
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

    private final Field field;
    private final String displayName;
    private final String uniqueFieldName;
    private final MethodHandle injector;        // sets the field or calls the setter; null when Field.set is used
    private final MethodHandle boxedSetter;     // injector as (Object, Object)void, for invokeExact
    private final MethodHandle primitiveSetter; // injector as (Object, long)void and its like: a primitive number
    private final boolean useFieldSet;          // Field.set() sets the field: there is no handle

    // Cached values for performance - computed once at construction
    private final Class<?> fieldType;
    private final Type genericType;
    private final Class<?> assignmentType;
    private final String fieldName;
    private final int fieldNumericKind;

    private static final int NUM_NONE = 0;
    private static final int NUM_INT = 1;
    private static final int NUM_DOUBLE = 2;
    private static final int NUM_BYTE = 3;
    private static final int NUM_FLOAT = 4;
    private static final int NUM_SHORT = 5;
    private static final int NUM_LONG = 6;

    // Pre-computed fast-path tag for ObjectResolver.assignField dispatch.
    // Eliminates per-field instanceof / class checks by knowing the field's shape at plan-build time.
    public static final byte FAST_PATH_GENERIC = 0;           // Fall through to full dispatch
    public static final byte FAST_PATH_PRIMITIVE_NUMERIC = 1; // int/long/short/byte/float/double
    public static final byte FAST_PATH_BOOLEAN = 2;           // boolean / Boolean
    public static final byte FAST_PATH_STRING = 3;            // String
    private final byte fastPath;

    // Type categorization (replaces sequential class == chains): filled once below, then read through ClassValues,
    // which keep each class's answer on the class
    private static final Map<Class<?>, Integer> NUMERIC_KIND_TABLE = new HashMap<>();
    private static final Map<Class<?>, Class<?>> PRIM_TO_WRAPPER_TABLE = new HashMap<>();
    private static final ClassValue<Integer> NUMERIC_KINDS = new ClassValue<Integer>() {
        @Override
        protected Integer computeValue(Class<?> c) {
            return NUMERIC_KIND_TABLE.get(c);
        }
    };
    private static final ClassValue<Class<?>> PRIM_TO_WRAPPER = new ClassValue<Class<?>>() {
        @Override
        protected Class<?> computeValue(Class<?> c) {
            return PRIM_TO_WRAPPER_TABLE.get(c);
        }
    };
    static {
        NUMERIC_KIND_TABLE.put(int.class, NUM_INT);
        NUMERIC_KIND_TABLE.put(Integer.class, NUM_INT);
        NUMERIC_KIND_TABLE.put(double.class, NUM_DOUBLE);
        NUMERIC_KIND_TABLE.put(Double.class, NUM_DOUBLE);
        NUMERIC_KIND_TABLE.put(byte.class, NUM_BYTE);
        NUMERIC_KIND_TABLE.put(Byte.class, NUM_BYTE);
        NUMERIC_KIND_TABLE.put(float.class, NUM_FLOAT);
        NUMERIC_KIND_TABLE.put(Float.class, NUM_FLOAT);
        NUMERIC_KIND_TABLE.put(short.class, NUM_SHORT);
        NUMERIC_KIND_TABLE.put(Short.class, NUM_SHORT);
        NUMERIC_KIND_TABLE.put(long.class, NUM_LONG);
        NUMERIC_KIND_TABLE.put(Long.class, NUM_LONG);

        PRIM_TO_WRAPPER_TABLE.put(int.class, Integer.class);
        PRIM_TO_WRAPPER_TABLE.put(long.class, Long.class);
        PRIM_TO_WRAPPER_TABLE.put(short.class, Short.class);
        PRIM_TO_WRAPPER_TABLE.put(byte.class, Byte.class);
        PRIM_TO_WRAPPER_TABLE.put(boolean.class, Boolean.class);
        PRIM_TO_WRAPPER_TABLE.put(char.class, Character.class);
        PRIM_TO_WRAPPER_TABLE.put(float.class, Float.class);
        PRIM_TO_WRAPPER_TABLE.put(double.class, Double.class);
    }

    /**
     * Compute a fast-path dispatch tag at plan-build time, so ObjectResolver.assignField
     * can skip the 5-case dispatch chain for common field shapes.
     */
    private static byte computeFastPath(Class<?> fieldType) {
        if (fieldType.isPrimitive()) {
            if (fieldType == boolean.class) {
                return FAST_PATH_BOOLEAN;
            }
            if (fieldType == char.class) {
                return FAST_PATH_GENERIC; // char has special handling, skip fast path
            }
            return FAST_PATH_PRIMITIVE_NUMERIC; // int, long, short, byte, float, double
        }
        if (fieldType == String.class) {
            return FAST_PATH_STRING;
        }
        return FAST_PATH_GENERIC;
    }

    // A handle sets the field or calls the setter
    private Injector(Field field, MethodHandle handle, String uniqueFieldName, String displayName) {
        this(field, handle, uniqueFieldName, displayName, false);
    }

    // Field.set() sets the field
    private Injector(Field field, String uniqueFieldName, String displayName, boolean useFieldSet) {
        this(field, null, uniqueFieldName, displayName, useFieldSet);
    }

    private Injector(Field field, MethodHandle handle, String uniqueFieldName, String displayName,
                     boolean useFieldSet) {
        this.field = field;
        this.displayName = displayName;
        this.uniqueFieldName = uniqueFieldName;
        this.injector = handle;
        this.useFieldSet = useFieldSet;

        // Cache values for performance
        this.fieldType = field.getType();
        this.genericType = field.getGenericType();
        this.assignmentType = this.fieldType.isPrimitive() ? primitiveWrapper(this.fieldType) : this.fieldType;
        this.fieldNumericKind = numericKind(this.fieldType);
        this.fieldName = field.getName();
        this.fastPath = computeFastPath(this.fieldType);

        // Adapted once, here, so that every injection is an invokeExact: the call site's signature is the handle's
        // own and nothing is converted or looked up per call. MethodHandle.invoke on the unadapted handle asks for
        // the adapted one on every call. A handle that takes no object (a static field) cannot be adapted; it is
        // left to invoke, which refuses it as it always has.
        MethodHandle boxed = null;
        MethodHandle primitive = null;
        if (handle != null && handle.type().parameterCount() == 2) {
            boxed = handle.asType(MethodType.methodType(void.class, Object.class, Object.class));
            if (this.fieldType.isPrimitive() && this.fieldNumericKind != NUM_NONE) {
                primitive = handle.asType(MethodType.methodType(void.class, Object.class, this.fieldType));
            }
        }
        this.boxedSetter = boxed;
        this.primitiveSetter = primitive;
    }

    /**
     * Returns the pre-computed fast-path dispatch tag.
     * See FAST_PATH_* constants.
     */
    public byte getFastPath() {
        return fastPath;
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

    public static Injector create(Field field, String uniqueFieldName) {
        // Security: Validate input parameters
        if (field == null) {
            throw new JsonIoException("Field cannot be null");
        }

        if (StringUtilities.isEmpty(uniqueFieldName)) {
            throw new JsonIoException("Unique field name cannot be null or empty");
        }

        // JDK 9+: a private lookup sets the field with no setAccessible, which newer JDKs restrict step by step.
        MethodHandles.Lookup privateLookup = privateLookup(field.getDeclaringClass());
        if (privateLookup != null) {
            try {
                return new Injector(field, privateLookup.unreflectSetter(field), uniqueFieldName, field.getName());
            } catch (IllegalAccessException e) {
                // A final field that has not been made accessible, or one nothing may set (a record's): below
            }
        }

        // JDK 8, no private lookup for this class, or the private lookup refused the field
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

        try {
            return new Injector(field, MethodHandles.lookup().unreflectSetter(field), uniqueFieldName,
                    field.getName());
        } catch (IllegalAccessException e) {
            // Fallback to Field.set() injection
            return new Injector(field, uniqueFieldName, field.getName(), true);
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
            MethodType methodType = MethodType.methodType(void.class, field.getType());
            MethodHandle handle = MethodHandles.lookup()
                    .findVirtual(field.getDeclaringClass(), methodName, methodType);
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

        Object valueToInject = value;
        if (requiresPreConversion(value)) {
            valueToInject = Converter.convert(value, fieldType);
        }

        try {
            injectDirect(object, valueToInject);
        } catch (ClassCastException e) {
            String msg = e.getMessage();
            if (StringUtilities.hasContent(msg) && msg.contains("LinkedHashMap")) {
                throw new JsonIoException("Unable to set field: " + fieldName + " using " + displayName + ".", e);
            }
            try {
                Object convertedValue = Converter.convert(valueToInject, fieldType);
                injectDirect(object, convertedValue);
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

    public boolean injectLong(Object object, long value) {
        if (object == null) {
            throw new JsonIoException("Attempting to set field: " + fieldName + " on null object.");
        }

        try {
            switch (fieldNumericKind) {
                case NUM_LONG:
                    injectAsLong(object, value);
                    return true;
                case NUM_INT:
                    injectAsInt(object, (int) value);
                    return true;
                case NUM_SHORT:
                    injectAsShort(object, (short) value);
                    return true;
                case NUM_BYTE:
                    injectAsByte(object, (byte) value);
                    return true;
                case NUM_FLOAT:
                    injectAsFloat(object, (float) value);
                    return true;
                case NUM_DOUBLE:
                    injectAsDouble(object, (double) value);
                    return true;
                default:
                    return false;
            }
        } catch (Throwable t) {
            if (t instanceof JsonIoException) {
                throw (JsonIoException) t;
            }
            throw new JsonIoException("Unable to set field: " + fieldName + " using " + displayName, t);
        }
    }

    public boolean injectDouble(Object object, double value) {
        if (object == null) {
            throw new JsonIoException("Attempting to set field: " + fieldName + " on null object.");
        }

        try {
            switch (fieldNumericKind) {
                case NUM_DOUBLE:
                    injectAsDouble(object, value);
                    return true;
                case NUM_FLOAT:
                    injectAsFloat(object, (float) value);
                    return true;
                case NUM_LONG:
                    injectAsLong(object, (long) value);
                    return true;
                case NUM_INT:
                    injectAsInt(object, (int) value);
                    return true;
                default:
                    return false;
            }
        } catch (Throwable t) {
            if (t instanceof JsonIoException) {
                throw (JsonIoException) t;
            }
            throw new JsonIoException("Unable to set field: " + fieldName + " using " + displayName, t);
        }
    }

    private boolean requiresPreConversion(Object value) {
        return value != null && !assignmentType.isInstance(value);
    }

    private void injectDirect(Object object, Object value) throws Throwable {
        if (boxedSetter != null) {
            boxedSetter.invokeExact(object, value);
        } else if (useFieldSet) {
            field.set(object, value);
        } else {
            injector.invoke(object, value);
        }
    }

    private void injectAsLong(Object object, long value) throws Throwable {
        if (fieldType == long.class) {
            if (primitiveSetter != null) {
                primitiveSetter.invokeExact(object, value);
            } else if (useFieldSet) {
                field.setLong(object, value);
            } else {
                injector.invoke(object, value);
            }
        } else {
            injectDirect(object, value);
        }
    }

    private void injectAsInt(Object object, int value) throws Throwable {
        if (fieldType == int.class) {
            if (primitiveSetter != null) {
                primitiveSetter.invokeExact(object, value);
            } else if (useFieldSet) {
                field.setInt(object, value);
            } else {
                injector.invoke(object, value);
            }
        } else {
            injectDirect(object, value);
        }
    }

    private void injectAsShort(Object object, short value) throws Throwable {
        if (fieldType == short.class) {
            if (primitiveSetter != null) {
                primitiveSetter.invokeExact(object, value);
            } else if (useFieldSet) {
                field.setShort(object, value);
            } else {
                injector.invoke(object, value);
            }
        } else {
            injectDirect(object, value);
        }
    }

    private void injectAsByte(Object object, byte value) throws Throwable {
        if (fieldType == byte.class) {
            if (primitiveSetter != null) {
                primitiveSetter.invokeExact(object, value);
            } else if (useFieldSet) {
                field.setByte(object, value);
            } else {
                injector.invoke(object, value);
            }
        } else {
            injectDirect(object, value);
        }
    }

    private void injectAsFloat(Object object, float value) throws Throwable {
        if (fieldType == float.class) {
            if (primitiveSetter != null) {
                primitiveSetter.invokeExact(object, value);
            } else if (useFieldSet) {
                field.setFloat(object, value);
            } else {
                injector.invoke(object, value);
            }
        } else {
            injectDirect(object, value);
        }
    }

    private void injectAsDouble(Object object, double value) throws Throwable {
        if (fieldType == double.class) {
            if (primitiveSetter != null) {
                primitiveSetter.invokeExact(object, value);
            } else if (useFieldSet) {
                field.setDouble(object, value);
            } else {
                injector.invoke(object, value);
            }
        } else {
            injectDirect(object, value);
        }
    }

    private static int numericKind(Class<?> type) {
        Integer kind = type == null ? null : NUMERIC_KINDS.get(type);
        return kind != null ? kind : NUM_NONE;
    }

    private static Class<?> primitiveWrapper(Class<?> primitiveType) {
        Class<?> w = primitiveType == null ? null : PRIM_TO_WRAPPER.get(primitiveType);
        return w != null ? w : Void.class;
    }

    public Class<?> getType() {
        return fieldType;
    }

    public String getName() {
        return fieldName;
    }

    public Type getGenericType() {
        return genericType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUniqueFieldName() {
        return uniqueFieldName;
    }
}
