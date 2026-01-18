package com.cedarsoftware.io;

import java.io.Externalizable;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.cedarsoftware.io.reflect.Injector;
import com.cedarsoftware.util.ArrayUtilities;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.ClassValueMap;
import com.cedarsoftware.util.CompactCIHashMap;
import com.cedarsoftware.util.CompactCIHashSet;
import com.cedarsoftware.util.CompactCILinkedMap;
import com.cedarsoftware.util.CompactCILinkedSet;
import com.cedarsoftware.util.CompactLinkedMap;
import com.cedarsoftware.util.CompactLinkedSet;
import com.cedarsoftware.util.CompactMap;
import com.cedarsoftware.util.CompactSet;
import com.cedarsoftware.util.ConcurrentList;
import com.cedarsoftware.util.ConcurrentNavigableSetNullSafe;
import com.cedarsoftware.util.ConcurrentSet;
import com.cedarsoftware.util.IdentitySet;
import com.cedarsoftware.util.StringUtilities;
import com.cedarsoftware.util.TypeUtilities;
import com.cedarsoftware.util.convert.Converter;

/**
 * This class is used to convert a source of Java Maps that were created from
 * the JsonParser.  These are in 'raw' form with no 'pointers'.  This code will
 * reconstruct the 'shape' of the graph by connecting @ref's to @ids.
 * <p>
 * The subclasses that override this class can build an object graph using Java
 * classes or a Map-of-Map representation.  In both cases, the @ref value will
 * be replaced with the Object (or Map) that had the corresponding @id.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
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
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class Resolver {
    private static final String NO_FACTORY = "_︿_ψ_☼";

    // Common ancestor roots to skip when checking type compatibility (using IdentitySet for Class comparison)
    private static final Set<Class<?>> SKIP_COMMON_ROOTS;
    static {
        Set<Class<?>> roots = new IdentitySet<>();
        roots.add(Object.class);
        roots.add(Serializable.class);
        roots.add(Externalizable.class);
        roots.add(Cloneable.class);
        SKIP_COMMON_ROOTS = roots;
    }
    
    // Security limits to prevent DoS attacks via unbounded memory consumption
    // These are now configurable via ReadOptions for backward compatibility
    
    final Collection<UnresolvedReference> unresolvedRefs = new ArrayList<>();
    protected final Deque<JsonObject> stack = new ArrayDeque<>();
    private final Collection<JsonObject> mapsToRehash = new ArrayList<>();
    // store the missing field found during deserialization to notify any client after the complete resolution is done
    private final Collection<MissingField> missingFields = new ArrayList<>();
    protected ReadOptions readOptions;
    protected ReferenceTracker references;
    protected final Converter converter;
    private SealedSupplier sealedSupplier = new SealedSupplier();
    
    // Performance: Hoisted ReadOptions constants to avoid repeated method calls
    private final int maxUnresolvedRefs;
    private final int maxMissingFields;
    private final int maxStackDepth;
    private final int maxMapsToRehash;
    protected final boolean returningJavaObjects;


    /**
     * UnresolvedReference is created to hold a logical pointer to a reference that
     * could not yet be loaded, as the @ref appears ahead of the referenced object's
     * definition.  This can point to a field reference or an array/Collection element reference.
     */
    static final class UnresolvedReference {
        private final JsonObject referencingObj;
        private String field;
        private final long refId;
        private int index = -1;

        UnresolvedReference(JsonObject referrer, String fld, long id) {
            referencingObj = referrer;
            field = fld;
            refId = id;
        }

        UnresolvedReference(JsonObject referrer, int idx, long id) {
            referencingObj = referrer;
            index = idx;
            refId = id;
        }
    }

    /**
     * stores missing fields information to notify client after the complete deserialization resolution
     */
    protected static class MissingField {
        private final Object target;
        private final String fieldName;
        private final Object value;

        public MissingField(Object target, String fieldName, Object value) {
            this.target = target;
            this.fieldName = fieldName;
            this.value = value;
        }
    }

    protected Resolver(ReadOptions readOptions, ReferenceTracker references, Converter converter) {
        this.readOptions = readOptions;
        this.references = references;
        this.converter = converter;

        // Performance: Hoist ReadOptions constants (handle null for test cases)
        if (readOptions != null) {
            this.maxUnresolvedRefs = readOptions.getMaxUnresolvedReferences();
            this.maxMissingFields = readOptions.getMaxMissingFields();
            this.maxStackDepth = readOptions.getMaxStackDepth();
            this.maxMapsToRehash = readOptions.getMaxMapsToRehash();
            this.returningJavaObjects = readOptions.isReturningJavaObjects();
        } else {
            // Default values for test cases
            this.maxUnresolvedRefs = Integer.MAX_VALUE;
            this.maxMissingFields = Integer.MAX_VALUE;
            this.maxStackDepth = Integer.MAX_VALUE;
            this.maxMapsToRehash = Integer.MAX_VALUE;
            this.returningJavaObjects = true;
        }
    }

    public ReadOptions getReadOptions() {
        return readOptions;
    }

    public ReferenceTracker getReferences() {
        return references;
    }

    public Converter getConverter() {
        return converter;
    }

    // ====================================================================================================
    // Convenience methods for ClassFactory implementations
    // ====================================================================================================

    /**
     * Convenience method for reading a String field from a JsonObject in ClassFactory implementations.
     * Handles type conversion automatically.
     *
     * @param jsonObj the JsonObject (typically passed to ClassFactory.newInstance)
     * @param fieldName the field name to read
     * @return the String value, or null if not present or null
     */
    public String readString(JsonObject jsonObj, String fieldName) {
        return converter.convert(jsonObj.get(fieldName), String.class);
    }

    /**
     * Convenience method for reading an int field from a JsonObject in ClassFactory implementations.
     * Handles type conversion automatically.
     *
     * @param jsonObj the JsonObject (typically passed to ClassFactory.newInstance)
     * @param fieldName the field name to read
     * @return the int value, or 0 if not present or null
     */
    public int readInt(JsonObject jsonObj, String fieldName) {
        return converter.convert(jsonObj.get(fieldName), int.class);
    }

    /**
     * Convenience method for reading a long field from a JsonObject in ClassFactory implementations.
     * Handles type conversion automatically.
     *
     * @param jsonObj the JsonObject (typically passed to ClassFactory.newInstance)
     * @param fieldName the field name to read
     * @return the long value, or 0L if not present or null
     */
    public long readLong(JsonObject jsonObj, String fieldName) {
        return converter.convert(jsonObj.get(fieldName), long.class);
    }

    /**
     * Convenience method for reading a float field from a JsonObject in ClassFactory implementations.
     * Handles type conversion automatically.
     *
     * @param jsonObj the JsonObject (typically passed to ClassFactory.newInstance)
     * @param fieldName the field name to read
     * @return the float value, or 0.0f if not present or null
     */
    public float readFloat(JsonObject jsonObj, String fieldName) {
        return converter.convert(jsonObj.get(fieldName), float.class);
    }

    /**
     * Convenience method for reading a double field from a JsonObject in ClassFactory implementations.
     * Handles type conversion automatically.
     *
     * @param jsonObj the JsonObject (typically passed to ClassFactory.newInstance)
     * @param fieldName the field name to read
     * @return the double value, or 0.0 if not present or null
     */
    public double readDouble(JsonObject jsonObj, String fieldName) {
        return converter.convert(jsonObj.get(fieldName), double.class);
    }

    /**
     * Convenience method for reading a boolean field from a JsonObject in ClassFactory implementations.
     * Handles type conversion automatically.
     *
     * @param jsonObj the JsonObject (typically passed to ClassFactory.newInstance)
     * @param fieldName the field name to read
     * @return the boolean value, or false if not present or null
     */
    public boolean readBoolean(JsonObject jsonObj, String fieldName) {
        return converter.convert(jsonObj.get(fieldName), boolean.class);
    }

    /**
     * Convenience method for reading a typed object field from a JsonObject in ClassFactory implementations.
     * Handles full deserialization including complex types, cycles, and references.
     *
     * @param jsonObj the JsonObject (typically passed to ClassFactory.newInstance)
     * @param fieldName the field name to read
     * @param type the target type to convert to
     * @return the fully deserialized object, or null if not present
     */
    @SuppressWarnings("unchecked")
    public <T> T readObject(JsonObject jsonObj, String fieldName, Class<T> type) {
        Object value = jsonObj.get(fieldName);
        if (value == null) {
            return null;
        }
        return (T) toJava(type, value);
    }

    /**
     * Convenience method for reading a typed array field from a JsonObject in ClassFactory implementations.
     * Handles full deserialization including complex types, cycles, and references.
     *
     * @param jsonObj the JsonObject (typically passed to ClassFactory.newInstance)
     * @param fieldName the field name to read
     * @param arrayType the array type (e.g., String[].class, MyObject[].class)
     * @return the fully deserialized array, or null if not present
     */
    @SuppressWarnings("unchecked")
    public <T> T[] readArray(JsonObject jsonObj, String fieldName, Class<T[]> arrayType) {
        Object value = jsonObj.get(fieldName);
        if (value == null) {
            return null;
        }
        return (T[]) toJava(arrayType, value);
    }

    /**
     * Convenience method for reading a List field from a JsonObject in ClassFactory implementations.
     * Handles full deserialization including complex types, cycles, and references.
     *
     * @param jsonObj the JsonObject (typically passed to ClassFactory.newInstance)
     * @param fieldName the field name to read
     * @return the fully deserialized List, or null if not present
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> readList(JsonObject jsonObj, String fieldName) {
        Object value = jsonObj.get(fieldName);
        if (value == null) {
            return null;
        }
        return (List<T>) toJava(List.class, value);
    }

    /**
     * Convenience method for reading a Map field from a JsonObject in ClassFactory implementations.
     * Handles full deserialization including complex types, cycles, and references.
     *
     * @param jsonObj the JsonObject (typically passed to ClassFactory.newInstance)
     * @param fieldName the field name to read
     * @return the fully deserialized Map, or null if not present
     */
    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> readMap(JsonObject jsonObj, String fieldName) {
        Object value = jsonObj.get(fieldName);
        if (value == null) {
            return null;
        }
        return (Map<K, V>) toJava(Map.class, value);
    }

    /**
     * Resolves a parsed JSON value to a Java object.
     * This is the primary resolution entry point used by ClassFactory implementations
     * and JsonIo for converting parsed JSON into Java objects.
     *
     * @param type the target type (may be null to infer from JSON)
     * @param value the parsed JSON value (JsonObject, array, or primitive)
     * @return the resolved Java object
     */
    public Object toJava(Type type, Object value) {
        if (value == null) {
            return null;
        }

        // Primitives (String, Boolean, Number, etc.) - not JsonObjects or arrays
        if (!(value instanceof JsonObject) && !value.getClass().isArray()) {
            return convertToType(value, type);
        }

        // Wrap raw Java arrays in a JsonObject for uniform processing
        JsonObject jsonObj;
        if (value.getClass().isArray()) {
            Class<?> valueClass = value.getClass();
            // Primitive arrays (int[], char[], etc.) that are already fully resolved - return directly
            // They cannot be cast to Object[] and need no further processing
            if (valueClass.getComponentType().isPrimitive()) {
                // Check if conversion is needed to a different type
                if (type != null) {
                    Class<?> targetClass = TypeUtilities.getRawClass(type);
                    if (targetClass != valueClass && converter.isConversionSupportedFor(valueClass, targetClass)) {
                        return converter.convert(value, targetClass);
                    }
                }
                return value;
            }
            jsonObj = new JsonObject();
            jsonObj.setType(type);
            jsonObj.setTarget(value);
            jsonObj.setItems((Object[]) value);
        } else {
            jsonObj = (JsonObject) value;
        }

        // Special case: Map→non-Map conversion BEFORE toJavaObjects modifies structure.
        // If the JsonObject is Map-like and target type is a non-Map type that the Converter
        // can handle, convert directly. This is critical because toJavaObjects() replaces
        // nested JsonObjects in values arrays with their targets (empty HashMaps), losing
        // the data needed for conversion (like _v keys used by MapConversions).
        // SKIP this when:
        // - JsonObject has @type metadata for a non-Map type (json-io should resolve it)
        // - Target is an Enum (json-io has special factory handling for enums)
        if (!jsonObj.isArray() && type != null) {
            Class<?> targetClass = TypeUtilities.getRawClass(type);
            // Check if @type is a Map type - if so, we may need early conversion
            Type jsonType = jsonObj.getType();
            boolean jsonTypeIsMap = jsonType == null ||
                    Map.class.isAssignableFrom(TypeUtilities.getRawClass(jsonType));

            if (jsonTypeIsMap
                    && !Map.class.isAssignableFrom(targetClass)
                    && targetClass != Object.class
                    && !Enum.class.isAssignableFrom(targetClass)
                    && converter.isConversionSupportedFor(Map.class, targetClass)) {
                // Convert directly using the unmodified JsonObject (which has Map data)
                return convertToType(jsonObj, type);
            }
        }

        // Special case: Array→different-Array or Array→Collection conversion.
        // If the JsonObject has an array @type but target is a different array type or Collection,
        // first resolve to the @type array, then convert using Converter.
        // This handles cases like char[] (serialized specially with a single String in @items)
        // being read as byte[], where the factory system can't handle the format mismatch.
        if (jsonObj.isArray() && type != null) {
            Class<?> targetClass = TypeUtilities.getRawClass(type);
            Type jsonType = jsonObj.getType();

            if (jsonType != null) {
                Class<?> sourceClass = TypeUtilities.getRawClass(jsonType);

                // Check if we need array/collection cross-conversion
                // Note: Collection→Array conversion is handled later by convertToType() via Converter,
                // not here, because Collections have isArray()=false and don't enter this block.
                boolean needsConversion = false;
                if (sourceClass.isArray() && targetClass.isArray() && sourceClass != targetClass) {
                    needsConversion = true;  // Different array types (e.g., char[] → byte[])
                } else if (sourceClass.isArray() && Collection.class.isAssignableFrom(targetClass)) {
                    needsConversion = true;  // Array to Collection
                }

                // Note: We intentionally DON'T call isConversionSupportedFor() here because
                // java-util's Converter has placeholder entries (VoidConversions::toNull) for
                // array cross-conversions that get incorrectly cached when isConversionSupportedFor
                // is called. Instead, we let convert() handle the conversion directly.
                // The actual conversion happens in ArrayConversions.arrayToArray via attemptContainerConversion.
                if (needsConversion) {
                    // Resolve to the source type first (using the @type from JSON)
                    Object result = toJavaObjects(jsonObj, jsonType);
                    result = extractTargetIfNeeded(result);

                    // Then convert to target type using Converter
                    if (result != null) {
                        try {
                            return converter.convert(result, targetClass);
                        } catch (IllegalArgumentException e) {
                            // Conversion not supported - fall through to normal resolution
                        }
                    }
                }
            }
        }

        // Resolve the JsonObject (handles both arrays and objects)
        Object result = toJavaObjects(jsonObj, type);

        // If resolution returned null, fall back appropriately
        if (result == null) {
            result = jsonObj.isArray() ? jsonObj.getItems() : jsonObj;
        }

        // Extract target if needed (for Java mode, unwrap JsonObject to its target)
        result = extractTargetIfNeeded(result);

        // Final type conversion
        return convertToType(result, type);
    }

    /**
     * <h2>Convert a Parsed JsonObject to a Fully Resolved Java Object</h2>
     *
     * <p>
     * This method converts a root-level {@code JsonObject}—a Map-of-Maps representation of parsed JSON—into an actual
     * Java object instance. The {@code JsonObject} is typically produced by a prior call to {@code JsonIo.toMaps(String)}
     * or {@code JsonIo.toMaps(InputStream)}.
     * The conversion process uses the provided <code>root</code> parameter, a {@link java.lang.reflect.Type} that represents
     * the expected root type (including any generic type parameters). Although the full type information is preserved for
     * resolution, the {@code JsonObject}'s legacy {@code hintType} field (which remains a {@code Class<?>}) is set using the
     * raw class extracted from the provided type.
     * </p>
     *
     * <p>
     * The resolution process works as follows:
     * </p>
     * <ol>
     *   <li>
     *     <strong>Reference Resolution:</strong> If the {@code JsonObject} is a reference, it is resolved using the internal
     *     reference map. If a referenced object is found, it is returned immediately.
     *   </li>
     *   <li>
     *     <strong>Already Converted Check:</strong> If the {@code JsonObject} has already been fully converted (i.e. its
     *     {@code isFinished} flag is set), then its target (the converted Java object) is returned.
     *   </li>
     *   <li>
     *     <strong>Instance Creation and Traversal:</strong> Otherwise, the method sets the hint type on the {@code JsonObject}
     *     (using the raw class extracted from the provided full {@code Type}), creates a new instance (if necessary),
     *     and then traverses the object graph to resolve nested references and perform conversions.
     *   </li>
     * </ol>
     *
     * <h3>Parameters</h3>
     * <ul>
     *   <li>
     *     <b>rootObj</b> - The root {@code JsonObject} (a Map-of-Maps) representing the parsed JSON data.
     *   </li>
     *   <li>
     *     <b>root</b> - A {@code Type} representing the expected Java type (including full generic details) for the resulting
     *     object. If {@code null}, type inference defaults to a generic {@code Map} representation.
     *   </li>
     * </ul>
     *
     * <h3>Return Value</h3>
     * <p>
     * Returns a Java object that represents the fully resolved version of the JSON data. Depending on the JSON structure
     * and the provided type hint, the result may be a user-defined DTO, a collection, an array, or a primitive value.
     * </p>
     *
     * @param rootObj the root {@code JsonObject} representing parsed JSON data.
     * @param rootType a {@code Type} representing the expected Java type (with full generic details) for the root object; may be {@code null}.
     * @param <T> the type of the resulting Java object.
     * @return a fully resolved Java object representing the JSON data.
     */
    @SuppressWarnings("unchecked")
    <T> T toJavaObjects(JsonObject rootObj, Type rootType) {
        // If the JsonObject is a reference, resolve it.
        if (rootObj.isReference()) {
            rootObj = references.get((long) rootObj.refId);
            if (rootObj != null) {
                return (T) rootObj;
            }
        }

        // If already converted, return its target.
        if (rootObj.isFinished) {
            return (T) rootObj.getTarget();
        }

        // Hook: Allow subclasses to adjust type before resolution (e.g., sorted collection substitution)
        adjustTypeBeforeResolve(rootObj, rootType);

        // Determine effective type for instance creation:
        // 1. If rootObj already has a type (from @type or Parser's suggestedType), use it
        // 2. If rootType is specified by user, use that
        // 3. Fall back to Object.class
        if (rootObj.getType() == null) {
            rootObj.setType(rootType != null ? rootType : Object.class);
        }
        Object instance = rootObj.getTarget() != null ? rootObj.getTarget() : createInstance(rootObj);
        Object result = rootObj.isFinished ? instance : traverseJsonObject(rootObj);

        // Hook: Allow subclasses to reconcile result type (e.g., type conversion, Maps mode handling)
        return (T) reconcileResult(result, rootObj, rootType);
    }

    /**
     * Hook for subclasses to adjust the JsonObject's type before resolution begins.
     * Called before instance creation. Default implementation does nothing.
     *
     * @param rootObj the JsonObject about to be resolved
     * @param rootType the expected root type (may be null)
     */
    protected void adjustTypeBeforeResolve(JsonObject rootObj, Type rootType) {
        // Default: no adjustment
    }

    /**
     * Hook for subclasses to reconcile the resolved result with the expected type.
     * Called after resolution completes. Default implementation returns result as-is.
     *
     * @param result the resolved Java object
     * @param rootObj the original JsonObject
     * @param rootType the expected root type (may be null)
     * @return the reconciled result (may be converted or wrapped)
     */
    protected Object reconcileResult(Object result, JsonObject rootObj, Type rootType) {
        return result;  // Default: return as-is
    }

    // ========== Resolution Helper Methods ==========

    /**
     * In Java mode, if the result is a JsonObject with a target, return the target.
     * In Maps mode, return the value as-is.
     */
    private Object extractTargetIfNeeded(Object value) {
        if (returningJavaObjects
                && value instanceof JsonObject
                && ((JsonObject) value).target != null) {
            return ((JsonObject) value).target;
        }
        return value;
    }

    /**
     * Check if the graph object is compatible with the requested type based on shared
     * collection interfaces. This prevents unnecessary conversion for cases like:
     * - SealableList (graph) when UnmodifiableList (rawType) was requested
     * - Both implement List, so no conversion is needed
     * <p>
     * This is important because:
     * 1. Sealable types are json-io's substitutes for JDK unmodifiable collections
     * 2. Converting would create new instances that don't have forward refs patched
     * 3. The Sealable collections will be sealed in cleanup() anyway
     */
    private boolean isCompatibleCollectionType(Object graph, Class<?> rawType) {
        // Check specific collection interfaces (List, Set, Queue)
        // Note: Map is NOT a Collection, so not checked here - Map compatibility
        // is handled by targetClass.isInstance(value) in convertToType()
        if (graph instanceof List && List.class.isAssignableFrom(rawType)) {
            return true;
        }
        if (graph instanceof Set && Set.class.isAssignableFrom(rawType)) {
            return true;
        }
//        // Future proof - if we add SealableQueue - then make sure this code is added:
//        return graph instanceof Queue && Queue.class.isAssignableFrom(rawType);
        return false;
    }

    /**
     * Unified type conversion for all cases (arrays, objects, primitives).
     * Checks type compatibility and converts if necessary.
     */
    @SuppressWarnings("unchecked")
    private Object convertToType(Object value, Type targetType) {
        if (targetType == null || value == null) {
            return value;
        }

        Class<?> targetClass = TypeUtilities.getRawClass(targetType);

        // 1. Already the right type (exact or subtype match)
        if (targetClass.isInstance(value)) {
            return value;
        }

        // 2. Collection interface compatibility (for Sealable types)
        //    SealableList implements List, so accept it when List is requested
        if (isCompatibleCollectionType(value, targetClass)) {
            return value;
        }

        // 3. Try Converter (handles String→Enum, Number→Enum, and many other conversions)
        if (converter.isConversionSupportedFor(value.getClass(), targetClass)) {
            try {
                return converter.convert(value, targetClass);
            } catch (Exception e) {
                // For String→Class conversion from plain JSON strings, if the class is not found,
                // treat it as a type mismatch rather than exposing "class not found".
                // This distinguishes plain string JSON (type mismatch) from JSON with @type:class
                // (where class-not-found is the expected error from ClassFactory).
                boolean isStringToClassFailure = targetClass == Class.class && value instanceof String;
                if (!isStringToClassFailure) {
                    throw e;
                }
                // Fall through to throw type mismatch error below for String→Class failures
            }
        }

        // 4. Lenient mode for complex objects: accept if they share meaningful common ancestors
        //    This is for POJOs/complex objects, NOT for primitives or simple types.
        //    Only apply this when value is a "complex" object (not a primitive wrapper, String, etc.)
        if (!isSimpleType(value.getClass())) {
            Set<Class<?>> commonAncestors = ClassUtilities.findLowestCommonSupertypesExcluding(value.getClass(), targetClass, SKIP_COMMON_ROOTS);
            if (!commonAncestors.isEmpty()) {
                return value;
            }
        }

        IllegalArgumentException cause = new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to " + targetClass.getName());
        throw new JsonIoException("Return type mismatch. Expecting: " + targetClass.getName() + ", found: " + value.getClass().getName(), cause);
    }

    /**
     * Check if a class is a "simple" type (primitive, wrapper, String, etc.)
     * that should NOT get the lenient common-ancestors check.
     */
    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive() ||
                Number.class.isAssignableFrom(clazz) ||
                Boolean.class == clazz ||
                Character.class == clazz ||
                String.class == clazz ||
                Class.class == clazz ||
                clazz.isEnum();
    }

    /**
     * Walk a JsonObject (Map of String keys to values) and return the
     * Java object equivalent filled in as good as possible (everything
     * except unresolved reference fields or unresolved array/collection elements).
     *
     * @param root JsonObject reference to a Map-of-Maps representation of the JSON
     *             input after it has been completely read.
     * @return Properly constructed, typed, Java object graph built from a Map
     * of Maps representation (JsonObject root).
     */
    public <T> T traverseJsonObject(JsonObject root) {
        push(root);

        while (!stack.isEmpty()) {
            final JsonObject jsonObj = stack.pop();

            if (jsonObj.isFinished) {
                continue;
            }

            // Performance: Use cached type classification instead of repeated isArray/isCollection/isMap checks
            switch (jsonObj.getJsonType()) {
                case ARRAY:
                    traverseArray(jsonObj);
                    break;
                case COLLECTION:
                    traverseCollection(jsonObj);
                    break;
                case MAP:
                    traverseMap(jsonObj);
                    break;
                default:
                    traverseObject(jsonObj);
                    break;
            }
        }
        return (T) root.getTarget();
    }

    protected void traverseObject(JsonObject jsonObj) {
        if (jsonObj.isFinished) {
            return;
        }
        Object special;
        if ((special = readWithFactoryIfExists(jsonObj, null)) != null) {
            jsonObj.setTarget(special);
        } else {
            traverseFields(jsonObj);
        }
    }

    public SealedSupplier getSealedSupplier() {
        return sealedSupplier;
    }

    /**
     * Push a JsonObject on the work stack that has not yet had it's fields move over to it's Java peer (.target)
     * @param jsonObject JsonObject that supplies the source values for the Java peer (target)
     */
    public void push(JsonObject jsonObject) {
        // Performance: Skip objects that have already been fully processed
        if (jsonObject.isFinished) {
            return;
        }
        // Security: Prevent stack overflow via depth limiting (uses hoisted constant)
        if (maxStackDepth != Integer.MAX_VALUE && stack.size() >= maxStackDepth) {
            throw new JsonIoException("Security limit exceeded: Maximum traversal stack depth (" + maxStackDepth + ") reached. Possible deeply nested attack.");
        }
        stack.push(jsonObject);
    }

    public abstract void traverseFields(final JsonObject jsonObj);

    protected abstract Object readWithFactoryIfExists(final Object o, final Type compType);

    protected abstract void traverseCollection(JsonObject jsonObj);

    protected abstract void traverseArray(JsonObject jsonObj);
    
    /**
     * Security-aware method to add unresolved references with size limits
     */
    protected void addUnresolvedReference(UnresolvedReference ref) {
        // Security: Prevent unbounded memory growth via unresolved references
        // Performance: Use hoisted constant
        if (maxUnresolvedRefs != Integer.MAX_VALUE && unresolvedRefs.size() >= maxUnresolvedRefs) {
            throw new JsonIoException("Security limit exceeded: Maximum unresolved references (" + maxUnresolvedRefs + ") reached. Possible DoS attack.");
        }
        unresolvedRefs.add(ref);
    }
    
    /**
     * Security-aware method to add missing fields with size limits
     */
    protected void addMissingField(MissingField field) {
        // Security: Prevent unbounded memory growth via missing fields
        // Performance: Use hoisted constant
        if (maxMissingFields != Integer.MAX_VALUE && missingFields.size() >= maxMissingFields) {
            throw new JsonIoException("Security limit exceeded: Maximum missing fields (" + maxMissingFields + ") reached. Possible DoS attack.");
        }
        missingFields.add(field);
    }

    protected void cleanup() {
        patchUnresolvedReferences();  // Note: clears unresolvedRefs internally
        rehashMaps();
        references.clear();
        mapsToRehash.clear();
        handleMissingFields();
        missingFields.clear();
        stack.clear();
        references = null;
        readOptions = null;
        sealedSupplier.seal();
        sealedSupplier = null;
    }

    // calls the missing field handler if any for each recorded missing field.
    private void handleMissingFields() {
        MissingFieldHandler missingFieldHandler = readOptions.getMissingFieldHandler();
        if (missingFieldHandler != null) {
            for (MissingField mf : missingFields) {
                missingFieldHandler.fieldMissing(mf.target, mf.fieldName, mf.value);
            }
        }//else no handler so ignore.
    }

    /**
     * Process java.util.Map and it's derivatives.  These are written specially
     * so that the serialization does not expose the class internals
     * (internal fields of TreeMap for example).
     *
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     */
    protected void traverseMap(JsonObject jsonObj) {
        if (jsonObj.isFinished) {
            return;
        }
        jsonObj.setFinished();
        Map.Entry<Object[], Object[]> pair = jsonObj.asTwoArrays();
        final Object[] keys = pair.getKey();
        final Object[] items = pair.getValue();

        if (keys == null) {  // If keys is null, items is also null due to JsonObject validation
            return;
        }

        buildCollection(keys);
        buildCollection(items);

        // Save these for later so that unresolved references inside keys or values
        // get patched first, and then build the Maps.
        // Security: Prevent unbounded memory growth via excessive map creation (uses hoisted constant)
        if (maxMapsToRehash != Integer.MAX_VALUE && mapsToRehash.size() >= maxMapsToRehash) {
            throw new JsonIoException("Security limit exceeded: Maximum maps to rehash (" + maxMapsToRehash + ") reached. Possible DoS attack.");
        }
        mapsToRehash.add(jsonObj);
    }

    /**
     * Add a JsonObject to the list of maps that need rehashing after resolution.
     * This is called by subclasses that override traverseMap() to ensure proper
     * map population via rehashMaps().
     */
    protected void addMapToRehash(JsonObject jsonObj) {
        // Security: Uses hoisted constant for performance
        if (maxMapsToRehash != Integer.MAX_VALUE && mapsToRehash.size() >= maxMapsToRehash) {
            throw new JsonIoException("Security limit exceeded: Maximum maps to rehash (" + maxMapsToRehash + ") reached. Possible DoS attack.");
        }
        mapsToRehash.add(jsonObj);
    }

    private void buildCollection(Object[] arrayContent) {
        final JsonObject collection = new JsonObject();
        collection.setItems(arrayContent);
        collection.setTarget(arrayContent);
        push(collection);
    }

    // Create a ClassValueMap for direct instantiation of certain classes
    private static final ClassValueMap<Function<JsonObject, Object>> DEFAULT_INSTANTIATORS = new ClassValueMap<>();

    static {
        // Initialize the map with lambda factory instantiators for each class
        DEFAULT_INSTANTIATORS.put(ArrayList.class, jsonObj -> new ArrayList<>());
        DEFAULT_INSTANTIATORS.put(LinkedList.class, jsonObj -> new LinkedList<>());
        DEFAULT_INSTANTIATORS.put(CopyOnWriteArrayList.class, jsonObj -> new CopyOnWriteArrayList<>());
        DEFAULT_INSTANTIATORS.put(ConcurrentList.class, jsonObj -> new ConcurrentList<>());
        DEFAULT_INSTANTIATORS.put(CompactCIHashMap.class, jsonObj -> new CompactCIHashMap<>());
        DEFAULT_INSTANTIATORS.put(CompactCILinkedMap.class, jsonObj -> new CompactCILinkedMap<>());
        DEFAULT_INSTANTIATORS.put(CompactLinkedMap.class, jsonObj -> new CompactLinkedMap<>());
        DEFAULT_INSTANTIATORS.put(ConcurrentHashMap.class, jsonObj -> new ConcurrentHashMap<>());
        DEFAULT_INSTANTIATORS.put(ConcurrentSkipListMap.class, jsonObj -> new ConcurrentSkipListMap<>());
        DEFAULT_INSTANTIATORS.put(Vector.class, jsonObj -> new Vector<>());
        DEFAULT_INSTANTIATORS.put(HashMap.class, jsonObj -> new HashMap<>());
        DEFAULT_INSTANTIATORS.put(TreeMap.class, jsonObj -> new TreeMap<>());
        DEFAULT_INSTANTIATORS.put(CompactCIHashSet.class, jsonObj -> new CompactCIHashSet<>());
        DEFAULT_INSTANTIATORS.put(CompactCILinkedSet.class, jsonObj -> new CompactCILinkedSet<>());
        DEFAULT_INSTANTIATORS.put(CompactLinkedSet.class, jsonObj -> new CompactLinkedSet<>());
        DEFAULT_INSTANTIATORS.put(ConcurrentSkipListSet.class, jsonObj -> new ConcurrentSkipListSet<>());
        DEFAULT_INSTANTIATORS.put(ConcurrentNavigableSetNullSafe.class, jsonObj -> new ConcurrentNavigableSetNullSafe<>());
        DEFAULT_INSTANTIATORS.put(ConcurrentSet.class, jsonObj -> new ConcurrentSet<>());
        DEFAULT_INSTANTIATORS.put(HashSet.class, jsonObj -> new HashSet<>());
        DEFAULT_INSTANTIATORS.put(LinkedHashSet.class, jsonObj -> new LinkedHashSet<>());
        DEFAULT_INSTANTIATORS.put(TreeSet.class, jsonObj -> new TreeSet<>());
        DEFAULT_INSTANTIATORS.put(LinkedHashMap.class, jsonObj -> new LinkedHashMap<>());
        DEFAULT_INSTANTIATORS.put(CompactMap.class, jsonObj -> {
            // If the map does not have both config and data keys, then it is a regular CompactMap
            // Note: CompactMap in custom form, has two key/values at the Object level (config and data)
            if (!jsonObj.containsKey("config") || !jsonObj.containsKey("data")) {
                return new CompactMap<>();
            }

            // if the map has both config and data keys, then make sure the config value has < 2 slashes to remain a pure CompactMap
            Object configValue = jsonObj.get("config");
            if (!(configValue instanceof String)) {
                return new CompactMap<>();
            }
            
            // Optimize: count slashes without split() to avoid array allocation
            String config = (String) configValue;
            int slashCount = 0;
            for (int i = 0; i < config.length(); i++) {
                if (config.charAt(i) == '/') {
                    slashCount++;
                    if (slashCount >= 2) break; // Early exit when threshold reached
                }
            }
            
            if (slashCount < 2) {
                return new CompactMap<>();
            }

            return null; // Return null to indicate custom factory should be used
        });
        DEFAULT_INSTANTIATORS.put(CompactSet.class, jsonObj -> {
            // CompactSet instantiator with special logic

            // If the set does not have both config and data keys, then it is a regular CompactSet
            // Note: CompactSet in custom form, has two key/values at the Object level (config and data)
            if (!jsonObj.containsKey("config") || !jsonObj.containsKey("data")) {
                return new CompactSet<>();
            }

            // if the map has both config and data keys, then make sure the config value has < 2 slashes to remain a pure CompactSet
            Object configValue = jsonObj.get("config");
            if (!(configValue instanceof String) || StringUtilities.count((String) configValue, '/') < 2) {
                return new CompactSet<>();
            }

            return null; // Return null to indicate custom factory should be used
        });
    }

    /**
     * This method creates a Java Object instance based on the passed in parameters.
     * If the JsonObject contains a key '@type' then that is used, as the type was explicitly
     * set in the JSON stream.  If the key '@type' does not exist, then the passed in Class
     * is used to create the instance, handling creating an Array or regular Object
     * instance.<p></p>
     * The '@type' is seldom specified in the JSON input stream, as in many
     * cases it can be inferred from a field reference or array component type.
     * For Enum handling, the following rules are applied:
     * <p><pre>
     * Detection Rules:
     * 1. Single Enum Instance
     *    Detect:  javaType is an enum class (enumClass != null) AND jsonObj.getItems() == null
     *    Create:  Single enum instance using enum class
     *    Factory: EnumClassFactory
     *
     * 2. EnumSet Instance
     *    Detect:  javaType is an enum class (enumClass != null) AND jsonObj.getItems() != null
     *    Create:  EnumSet using enum class
     *    Factory: EnumSetFactory
     *
     * 3. Regular Class
     *    Detect:  Non-enum javaType (enumClass == null)
     *    Create:  Regular class instance
     *    Factory: Standard Factory Logic
     * </pre>
     *
     * @param jsonObj Map-of-Map representation of object to create.
     * @return a new Java object of the appropriate type (clazz) using the jsonObj to provide
     * enough hints to get the right class instantiated.  It is not populated when returned.
     */
    Object createInstance(JsonObject jsonObj) {
        // If an instance is already set, return it.
        Object target = jsonObj.getTarget();
        if (target != null) {
            return target;
        }

        // Use the refined Type (if available) to determine the target type.
        Class<?> targetType = resolveTargetType(jsonObj);

        // Check if we have a direct instantiator for this class
        Function<JsonObject, Object> instantiator = DEFAULT_INSTANTIATORS.get(targetType);
        if (instantiator != null) {
            Object instance = instantiator.apply(jsonObj);
            if (instance != null) {
                return jsonObj.setTarget(instance);
            }
        }

        // Knock out popular easy classes to instantiate and finish.
        if (converter.isSimpleTypeConversionSupported(targetType)) {
            Object result = converter.convert(jsonObj, targetType);
            return jsonObj.setFinishedTarget(result, true);
        }

        // Determine the factory type, considering enums and collections.
        Class<?> factoryType = determineFactoryType(jsonObj, targetType);

        // Try creating an instance using the class factory.
        Object mate = createInstanceUsingClassFactory(factoryType, jsonObj);
        if (mate != NO_FACTORY) {
            return mate;
        }

        // Handle array creation.
        if (shouldCreateArray(jsonObj, targetType)) {
            mate = createArrayInstance(jsonObj, targetType);
            return jsonObj.setTarget(mate);
        }

        // Fallback: create an instance using the type directly.
        return createInstanceUsingType(jsonObj);
    }

    // Resolve a target type with proper coercion and enum handling
    private Class<?> resolveTargetType(JsonObject jsonObj) {
        Class<?> targetType = coerceClassIfNeeded(jsonObj.getRawType());
        jsonObj.setType(targetType);

        // Handle enum coercion as before.
        Class<?> enumClass = ClassUtilities.getClassIfEnum(targetType);
        if (enumClass != null) {
            Class<?> coercedEnumClass = getCoercedEnumClass(enumClass);
            if (coercedEnumClass != null) {
                targetType = coercedEnumClass;
                jsonObj.setType(coercedEnumClass);
            }
        }
        return targetType;
    }

    private Class<?> coerceClassIfNeeded(Class<?> type) {
        Class<?> coerced = readOptions.getCoercedClass(type);
        return coerced != null ? coerced : type;
    }

    private Class<?> getCoercedEnumClass(Class<?> enumClass) {
        Class<?> coercedClass = readOptions.getCoercedClass(enumClass);
        return coercedClass != null ? ClassUtilities.getClassIfEnum(coercedClass) : null;
    }

    // Determine the factory type, considering enums and collections
    private Class<?> determineFactoryType(JsonObject jsonObj, Class<?> targetType) {
        Class<?> enumClass = ClassUtilities.getClassIfEnum(targetType);
        if (enumClass != null) {
            boolean isEnumSet = jsonObj.getItems() != null;
            return isEnumSet ? EnumSet.class : enumClass;
        }
        return targetType;
    }

    private boolean shouldCreateArray(JsonObject jsonObj, Class<?> targetType) {
        Object[] items = jsonObj.getItems();
        return targetType.isArray() || (items != null && targetType == Object.class && jsonObj.getKeys() == null);
    }

    private Object createArrayInstance(JsonObject jsonObj, Class<?> targetType) {
        Object[] items = jsonObj.getItems();
        int size = (items == null) ? 0 : items.length;
        Class<?> componentType = targetType.isArray() ? targetType.getComponentType() : Object.class;
        return Array.newInstance(componentType, size);
    }

    /**
     * Create an instance of a Java class using the JavaType field on the jsonObj.
     */
    private Object createInstanceUsingType(JsonObject jsonObj) {
        Class<?> c = jsonObj.getRawType();
        boolean isUnknownObject = c == Object.class && returningJavaObjects;

        Object instance;
        if (isUnknownObject && readOptions.getUnknownTypeClass() == null) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.setType(Map.class);
            instance = jsonObject;
        } else {
            Class<?> targetClass = isUnknownObject ? readOptions.getUnknownTypeClass() : c;
            instance = ClassUtilities.newInstance(converter, targetClass, jsonObj);
        }

        return jsonObj.setTarget(instance);
    }

    /**
     * If a ClassFactory is associated to the passed in Class (clazz), then use the ClassFactory
     * to create an instance.  If a ClassFactory creates the instance, it may optionally load
     * the values into the instance, using the values from the passed in JsonObject.  If the
     * ClassFactory instance creates AND loads the object, it is indicated on the ClassFactory
     * by the isObjectFinal() method returning true.  Therefore, the JsonObject instance that is
     * loaded, is marked with 'isFinished=true' so that no more processing is needed for this instance.
     */
    Object createInstanceUsingClassFactory(Class c, JsonObject jsonObj) {
        // If a ClassFactory exists for a class, use it to instantiate the class.  The ClassFactory
        // may optionally load the newly created instance, in which case, the JsonObject is marked finished, and
        // return.

        ClassFactory classFactory = readOptions.getClassFactory(c);

        if (classFactory == null) {
            return NO_FACTORY;
        }

        Object target = classFactory.newInstance(c, jsonObj, this);

        // don't pass in classFactory.isObjectFinal, only set it to true if classFactory says its so.
        // it allows the factory itself to set final on the jsonObj internally where it depends
        // on how the data comes back, but that value can be a hard true if the factory knows
        // it's always true.
        if (classFactory.isObjectFinal()) {
            return jsonObj.setFinishedTarget(target, true);
        }

        return jsonObj.setTarget(target);
    }

    /**
     * For all fields where the value was "@ref":"n" where 'n' was the id of an object
     * that had not yet been encountered in the stream, make the final substitution.
     */
    private void patchUnresolvedReferences() {
        if (unresolvedRefs.isEmpty()) {
            return;
        }
        for (UnresolvedReference ref : unresolvedRefs) {
            Object objToFix = ref.referencingObj.getTarget();
            JsonObject objReferenced = this.references.getOrThrow(ref.refId);
            Object referencedTarget = objReferenced.getTarget();

            if (ref.index >= 0) {    // Fix []'s and Collections containing a forward reference.
                if (objToFix instanceof List) {
                    ((List) objToFix).set(ref.index, referencedTarget);
                } else if (objToFix instanceof Collection) {   // Patch up Indexable Collections
                    ((Collection) objToFix).add(referencedTarget);
                } else {   // Object arrays - primitive arrays cannot have forward references
                    ((Object[]) objToFix)[ref.index] = referencedTarget;
                }
            } else {    // Fix field forward reference
                // ReadOptions.getDeepInjectorMap() already caches via ClassValueMap - no local cache needed
                Map<String, Injector> injectors = readOptions.getDeepInjectorMap(objToFix.getClass());

                Injector injector = injectors.get(ref.field);
                if (injector != null) {
                    try {
                        injector.inject(objToFix, referencedTarget);
                    } catch (Exception e) {
                        throw new JsonIoException("Error setting field while resolving references '" + ref.field + "', @ref = " + ref.refId, e);
                    }
                }
            }
        }
        unresolvedRefs.clear();
    }

    /**
     * Process Maps/Sets (fix up their internal indexing structure)
     * This is required because Maps hash items using hashCode(), which will
     * change between VMs.  Rehashing the map fixes this.
     * <br>
     * If useMaps==true, then move @keys to keys and @items to values
     * and then drop these two entries from the map.
     * <br>
     * This hashes both Sets and Maps because the JDK sets are implemented
     * as Maps.  If you have a custom-built Set, this would not 'treat' it,
     * and you would need to provide a custom reader for that set.
     */
    private void rehashMaps() {
        if (mapsToRehash.isEmpty()) {
            return;
        }
        for (JsonObject jsonObj : mapsToRehash) {
            jsonObj.rehashMaps();
        }
    }

    public boolean valueToTarget(JsonObject jsonObject) {
        if (jsonObject.getType() == null) {
            return false;
        }

        Class<?> javaType = jsonObject.getRawType();
        // For arrays, attempt simple type conversion.
        if (javaType.isArray() &&
                converter.isSimpleTypeConversionSupported(javaType.getComponentType(),
                        javaType.getComponentType())) {
            Object[] jsonItems = jsonObject.getItems();
            Class<?> componentType = javaType.getComponentType();
            if (jsonItems == null) {    // empty array
                jsonObject.setFinishedTarget(null, true);
                return true;
            }
            int len = jsonItems.length;
            Object javaArray = Array.newInstance(componentType, len);
            boolean isPrimitive = componentType.isPrimitive();
            Object[] typedArray = isPrimitive ? null : (Object[]) javaArray;

            for (int i = 0; i < len; i++) {
                try {
                    Object item = jsonItems[i];
                    Class<?> type = getItemType(item, componentType);
                    Object converted = converter.convert(item, type);
                    if (isPrimitive) {
                        ArrayUtilities.setPrimitiveElement(javaArray, i, converted);
                    } else {
                        typedArray[i] = converted;
                    }
                } catch (Exception e) {
                    throw wrapException(e);
                }
            }
            jsonObject.setFinishedTarget(javaArray, true);
            return true;
        }

        if (!converter.isSimpleTypeConversionSupported(javaType)) {
            return false;
        }

        try {
            Object value = converter.convert(jsonObject, javaType);
            jsonObject.setFinishedTarget(value, true);
            return true;
        } catch (Exception e) {
            throw wrapException(e);
        }
    }

    /**
     * Determines the effective type for an array item, checking for JsonObject type hints.
     */
    private Class<?> getItemType(Object item, Class<?> defaultType) {
        if (item instanceof JsonObject) {
            JsonObject jObj = (JsonObject) item;
            if (jObj.getType() != null) {
                return jObj.getRawType();
            }
        }
        return defaultType;
    }

    /**
     * Wraps an exception in a JsonIoException, preserving the original stack trace.
     */
    private JsonIoException wrapException(Exception e) {
        JsonIoException jioe = new JsonIoException(e.getMessage());
        jioe.setStackTrace(e.getStackTrace());
        return jioe;
    }

    protected void setArrayElement(Object array, int index, Object element) {
        // Fast path for Object arrays - direct assignment (5-10x faster than reflection)
        if (array instanceof Object[]) {
            try {
                ((Object[]) array)[index] = element;
            } catch (ArrayStoreException e) {
                // Convert ArrayStoreException to IllegalArgumentException for consistent error handling
                String elementType = element == null ? "null" : element.getClass().getName();
                String arrayType = array.getClass().getComponentType().getName() + "[]";
                throw new IllegalArgumentException("Cannot set '" + elementType + "' (value: " + element +
                        ") into '" + arrayType + "' at index " + index);
            }
        } else {
            // Primitive arrays - use optimized setPrimitiveElement()
            ArrayUtilities.setPrimitiveElement(array, index, element);
        }
    }

    /**
     * Checks if the given element is a native JSON value that can be added directly to a collection
     * without further processing. These are the types that JsonParser produces directly from raw JSON:
     * <ul>
     *   <li>String - from JSON strings</li>
     *   <li>Boolean - from JSON true/false</li>
     *   <li>Long - from JSON integers</li>
     *   <li>Double - from JSON decimals</li>
     *   <li>BigInteger - from JSON integers too large for Long</li>
     *   <li>BigDecimal - from JSON decimals when configured for high precision</li>
     * </ul>
     * Note: Arrays are NOT included because they require traversal of their elements.
     * Note: AtomicLong/AtomicInteger/AtomicBoolean are NOT included because JsonParser
     * never produces them directly - they require @type information.
     *
     * @param element the element to check
     * @return true if the element can be added directly to a collection without processing
     */
    protected static boolean isDirectlyAddableJsonValue(Object element) {
        return element instanceof String || element instanceof Boolean ||
               element instanceof Long || element instanceof Double ||
               element instanceof BigInteger || element instanceof BigDecimal;
    }

    /**
     * Wraps a raw Object[] array element in a JsonObject, creates its instance, adds it to the collection,
     * and pushes it onto the stack for further processing. This pattern is used when encountering
     * array elements within collections that need to be converted to typed arrays.
     *
     * @param arrayElement the raw Object[] to wrap
     * @param componentType the type for the array elements
     * @param col the collection to add the created array instance to
     */
    protected void wrapArrayAndAddToCollection(Object[] arrayElement, Type componentType, Collection<Object> col) {
        JsonObject jObj = new JsonObject();
        jObj.setType(componentType);
        jObj.setItems(arrayElement);
        createInstance(jObj);
        col.add(jObj.getTarget());
        push(jObj);
    }

    /**
     * Sets an array element, handling both primitive and reference arrays.
     * This consolidates the repeated pattern of conditionally using
     * ArrayUtilities.setPrimitiveElement() vs direct array assignment.
     *
     * @param array the primitive array (used when isPrimitive is true)
     * @param refArray the reference array (used when isPrimitive is false)
     * @param index the array index to set
     * @param value the value to assign
     * @param isPrimitive true if dealing with a primitive array
     */
    protected static void setArrayElement(Object array, Object[] refArray, int index, Object value, boolean isPrimitive) {
        if (isPrimitive) {
            ArrayUtilities.setPrimitiveElement(array, index, value);
        } else {
            refArray[index] = value;
        }
    }

    /**
     * Checks if the JsonObject is already finished. If not, marks it as finished.
     * This consolidates the common guard pattern used at the start of traverse methods.
     *
     * @param jsonObj the JsonObject to check and mark
     * @return true if the object was already finished (caller should return early), false otherwise
     */
    protected static boolean markFinishedIfNot(JsonObject jsonObj) {
        if (jsonObj.isFinished) {
            return true;
        }
        jsonObj.setFinished();
        return false;
    }

    /**
     * Ensures the collection has sufficient capacity if it's an ArrayList.
     * This avoids repeated array resizing during bulk additions.
     *
     * @param col the collection to potentially resize
     * @param size the expected number of elements
     */
    protected static void ensureCollectionCapacity(Collection<?> col, int size) {
        if (col instanceof ArrayList) {
            ((ArrayList<?>) col).ensureCapacity(size);
        }
    }

    /**
     * Resolves a reference JsonObject to the actual referenced JsonObject.
     * Returns null if the jsonObj is not a reference.
     *
     * @param jsonObj the JsonObject that may be a reference (@ref)
     * @return the referenced JsonObject, or null if jsonObj is not a reference
     */
    protected JsonObject resolveReference(JsonObject jsonObj) {
        if (!jsonObj.isReference()) {
            return null;
        }
        return references.getOrThrow(jsonObj.getReferenceId());
    }

    /**
     * Resolves a reference within a collection context. If the referenced object's target
     * is already available, adds it to the collection. Otherwise, registers an unresolved
     * reference to be patched later.
     *
     * @param refHolder the JsonObject containing the reference
     * @param parent the parent JsonObject (for unresolved reference tracking)
     * @param col the collection to add the resolved target to
     * @param idx the index in the collection (for unresolved reference tracking)
     * @param isList true if the collection is index-addressable (List)
     */
    protected void resolveReferenceInCollection(JsonObject refHolder, JsonObject parent,
            Collection<Object> col, int idx, boolean isList) {
        JsonObject refObject = resolveReference(refHolder);
        if (refObject.getTarget() != null) {
            col.add(refObject.getTarget());
        } else {
            addUnresolvedReference(new UnresolvedReference(parent, idx, refHolder.getReferenceId()));
            if (isList) {
                col.add(null);
            }
        }
    }

    /**
     * Processes a resolved JsonObject and adds it to a collection. Handles traversal
     * for referenceable types and special EnumSet handling.
     *
     * @param jObj the resolved JsonObject to add
     * @param col the collection to add to
     */
    protected void addResolvedObjectToCollection(JsonObject jObj, Collection<Object> col) {
        boolean isNonRefClass = readOptions.isNonReferenceableClass(jObj.getRawType());
        if (!isNonRefClass) {
            // Performance: Use cached type classification instead of repeated isArray/isCollection/isMap checks
            switch (jObj.getJsonType()) {
                case ARRAY:
                    traverseArray(jObj);
                    break;
                case COLLECTION:
                    traverseCollection(jObj);
                    break;
                case MAP:
                    traverseMap(jObj);
                    break;
                default:
                    traverseObject(jObj);
                    break;
            }
        }
        if (!(col instanceof EnumSet)) {
            col.add(jObj.getTarget());
        }
    }

    /**
     * Converts a JsonObject to its target type if the type is a non-referenceable class
     * and the Converter supports the conversion from Map. This is used in Maps mode to
     * convert JsonObjects with simple types (UUID, ZonedDateTime, etc.) to their Java equivalents.
     *
     * @param jsonObj the JsonObject to potentially convert
     * @param type the target type (typically from jsonObj.getRawType())
     * @return the converted object if conversion was performed, null otherwise
     */
    protected Object convertIfNonRefType(JsonObject jsonObj, Class<?> type) {
        if (type != null && readOptions.isNonReferenceableClass(type) &&
                converter.isConversionSupportedFor(Map.class, type)) {
            return converter.convert(jsonObj, type);
        }
        return null;
    }

    protected abstract Object resolveArray(Type suggestedType, List<Object> list);

    /**
     * Default implementation of ReferenceTracker.
     * Reference tracking is logically part of the resolution process,
     * as it tracks @id/@ref relationships during JSON parsing and resolution.
     */
    public static class DefaultReferenceTracker implements ReferenceTracker {

        final Map<Long, JsonObject> references = new HashMap<>();
        private final ReadOptions readOptions;

        public DefaultReferenceTracker(ReadOptions readOptions) {
            this.readOptions = readOptions;
        }

        public JsonObject put(Long l, JsonObject o) {
            // Security: Prevent unbounded memory growth via reference tracking
            int maxReferences = readOptions.getMaxObjectReferences();
            if (references.size() >= maxReferences) {
                throw new JsonIoException("Security limit exceeded: Maximum number of object references (" + maxReferences + ") reached. Possible DoS attack.");
            }
            return this.references.put(l, o);
        }

        public void clear() {
            this.references.clear();
        }

        public int size() {
            return this.references.size();
        }

        public JsonObject getOrThrow(Long id) {
            JsonObject target = get(id);
            if (target == null) {
                throw new JsonIoException("Forward reference @ref: " + id + ", but no object defined (@id) with that value");
            }
            return target;
        }

        public JsonObject get(Long id) {
            JsonObject target = references.get(id);
            if (target == null) {
                return null;
            }

            // Fast path: most lookups don't involve reference chains
            if (!target.isReference()) {
                return target;
            }

            // Slow path: follow reference chain with security checks
            // Performance: HashSet created lazily only when we have a reference chain
            Set<Long> visited = new HashSet<>();
            int chainDepth = 0;

            do {
                // Security: Prevent infinite loops via chain depth limit
                int maxChainDepth = readOptions.getMaxReferenceChainDepth();
                if (++chainDepth > maxChainDepth) {
                    throw new JsonIoException("Security limit exceeded: Reference chain depth (" + chainDepth + ") exceeds maximum (" + maxChainDepth + "). Possible circular reference attack.");
                }

                // Security: Enhanced circular reference detection
                if (!visited.add(id)) {
                    throw new JsonIoException("Circular reference detected in reference chain starting with id: " + id + " at depth: " + chainDepth);
                }

                id = target.getReferenceId();
                target = references.get(id);
                if (target == null) {
                    return null;
                }
            } while (target.isReference());

            return target;
        }
    }
}