package com.cedarsoftware.io;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
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
import java.util.logging.Logger;

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
    private static final Logger LOG = Logger.getLogger(Resolver.class.getName());
    private static final String NO_FACTORY = "_︿_ψ_☼";
    
    // Security limits to prevent DoS attacks via unbounded memory consumption
    // These are now configurable via ReadOptions for backward compatibility
    
    final Collection<UnresolvedReference> unresolvedRefs = new ArrayList<>();
    protected final Deque<JsonObject> stack = new ArrayDeque<>();
    private final Collection<JsonObject> mapsToRehash = new ArrayList<>();
    // store the missing field found during deserialization to notify any client after the complete resolution is done
    final Collection<Missingfields> missingFields = new ArrayList<>();
    private ReadOptions readOptions;
    private ReferenceTracker references;
    private final Converter converter;
    private SealedSupplier sealedSupplier = new SealedSupplier();
    
    // Performance: Hoisted ReadOptions constants to avoid repeated method calls
    private int maxUnresolvedRefs;
    private int maxMapsToRehash;
    private int maxMissingFields;

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
    protected static class Missingfields {
        private final Object target;
        private final String fieldName;
        private final Object value;

        public Missingfields(Object target, String fieldName, Object value) {
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
            this.maxMapsToRehash = readOptions.getMaxMapsToRehash();
            this.maxMissingFields = readOptions.getMaxMissingFields();
        } else {
            // Default values for test cases
            this.maxUnresolvedRefs = Integer.MAX_VALUE;
            this.maxMapsToRehash = Integer.MAX_VALUE;
            this.maxMissingFields = Integer.MAX_VALUE;
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
     * @param elementType the element type (e.g., String.class, MyObject.class)
     * @return the fully deserialized List, or null if not present
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> readList(JsonObject jsonObj, String fieldName, Class<T> elementType) {
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
     * @param keyType the key type (e.g., String.class)
     * @param valueType the value type (e.g., Object.class, MyObject.class)
     * @return the fully deserialized Map, or null if not present
     */
    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> readMap(JsonObject jsonObj, String fieldName, Class<K> keyType, Class<V> valueType) {
        Object value = jsonObj.get(fieldName);
        if (value == null) {
            return null;
        }
        return (Map<K, V>) toJava(Map.class, value);
    }

    /**
     * Resolves a parsed JSON value to a Java object.
     * Used by ClassFactory implementations for nested resolution.
     *
     * @param type the target type (may be null to infer from JSON)
     * @param value the parsed JSON value (JsonObject, array, or primitive)
     * @return the resolved Java object
     */
    public Object toJava(Type type, Object value) {
        return resolveRoot(value, type);
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
    public <T> T toJavaObjects(JsonObject rootObj, Type rootType) {
        if (rootObj == null) {
            return null;
        }

        // If the JsonObject is a reference, resolve it.
        if (rootObj.isReference()) {
            rootObj = getReferences().get((long) rootObj.refId);
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
            if (rootType != null) {
                rootObj.setType(rootType);
            } else {
                rootObj.setType(Object.class);
            }
        }
        Object instance = (rootObj.getTarget() == null ? createInstance(rootObj) : rootObj.getTarget());

        Object result;
        if (rootObj.isFinished) {
            result = instance;
        } else {
            result = traverseJsonObject(rootObj);
        }

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

    // ========== Root Resolution Methods ==========
    // These methods handle the routing and resolution of parsed values at the root level.

    /**
     * Resolves a parsed value to a Java object.
     * This handles routing based on the type of parsed value (array, object, or primitive)
     * and delegates to the appropriate resolution logic.
     *
     * @param parsed the parsed value (can be null, array, JsonObject, or primitive)
     * @param rootType the expected return type (may be null for type inference)
     * @return the resolved Java object
     */
    public Object resolveRoot(Object parsed, Type rootType) {
        if (parsed == null) {
            return null;
        }

        // Handle arrays (Java arrays or JsonObjects flagged as arrays)
        if (isRootArray(parsed)) {
            return extractTargetIfNeeded(handleArrayRoot(rootType, parsed));
        }

        // Handle JsonObjects (non-array)
        if (parsed instanceof JsonObject) {
            return extractTargetIfNeeded(handleObjectRoot(rootType, (JsonObject) parsed));
        }

        // Primitives (String, Boolean, Number, etc.)
        return convertIfNeeded(rootType, parsed);
    }

    /**
     * Returns true if the parsed object represents a root-level "array,"
     * meaning either an actual Java array, or a JsonObject marked as an array.
     */
    private boolean isRootArray(Object value) {
        if (value == null) {
            return false;
        }
        if (value.getClass().isArray()) {
            return true;
        }
        return (value instanceof JsonObject) && ((JsonObject) value).isArray();
    }

    /**
     * In Java mode, if the result is a JsonObject with a target, return the target.
     * In Maps mode, return the value as-is.
     */
    private Object extractTargetIfNeeded(Object value) {
        if (readOptions.isReturningJavaObjects()
                && value instanceof JsonObject
                && ((JsonObject) value).target != null) {
            return ((JsonObject) value).target;
        }
        return value;
    }

    /**
     * Handles the case where the top-level element is an array (either a real Java array,
     * or a JsonObject that's flagged as an array).
     */
    private Object handleArrayRoot(Type rootType, Object returnValue) {
        JsonObject rootObj;

        // If it's actually a Java array
        if (returnValue.getClass().isArray()) {
            rootObj = new JsonObject();
            rootObj.setType(rootType);
            rootObj.setTarget(returnValue);
            rootObj.setItems((Object[]) returnValue);
        } else {
            // Otherwise, it's a JsonObject that has isArray() == true
            rootObj = (JsonObject) returnValue;
        }

        // Resolve the array through toJavaObjects
        Object graph = toJavaObjects(rootObj, rootType);
        if (graph == null) {
            // If resolution returned null, fall back on the items as the final object
            graph = rootObj.getItems();
        }

        // Patch forward references and rehash maps BEFORE conversion.
        // This is critical because conversion may create immutable collections
        // that can't be modified after creation.
        patchUnresolvedReferences();
        rehashMaps();

        // Perform any needed type conversion before returning
        return convertIfNeeded(rootType, graph);
    }

    /**
     * Handles the top-level case where the parsed JSON is represented as a non-array
     * JsonObject. This method resolves internal references to build the final object graph,
     * and then performs any necessary type checking or conversion.
     */
    private Object handleObjectRoot(Type rootType, JsonObject jsonObj) {
        // Resolve internal references/build the object graph.
        Object graph = toJavaObjects(jsonObj, rootType);

        // Patch forward references and rehash maps BEFORE any conversion.
        // This is critical because conversion may create immutable objects
        // that can't be modified after creation.
        patchUnresolvedReferences();
        rehashMaps();

        Class<?> rawRootType = (rootType == null ? null : TypeUtilities.getRawClass(rootType));

        // If resolution produced null, return the original JsonObject.
        if (graph == null) {
            return jsonObj;
        }

        // If a specific rootType was provided...
        if (rootType != null) {
            // If the resolved graph is already assignable to the requested type, return it.
            if (rawRootType != null && rawRootType.isInstance(graph)) {
                return graph;
            }
            // Otherwise, if conversion is supported, perform the conversion.
            Converter converter = getConverter();
            if (rawRootType != null && converter.isConversionSupportedFor(graph.getClass(), rawRootType)) {
                return converter.convert(graph, rawRootType);
            }

            // Otherwise, try to find common ancestors (excluding Object, Serializable, Cloneable).
            Set<Class<?>> skipRoots = new HashSet<>();
            skipRoots.add(Object.class);
            skipRoots.add(Serializable.class);
            skipRoots.add(Cloneable.class);

            Set<Class<?>> commonAncestors = ClassUtilities.findLowestCommonSupertypesExcluding(graph.getClass(), rawRootType, skipRoots);
            if (commonAncestors.isEmpty()) {
                throw new ClassCastException("Return type mismatch, expected: " +
                        (rawRootType != null ? rawRootType.getName() : rootType.toString()) +
                        ", actual: " + graph.getClass().getName());
            }
            return graph;
        }

        // No specific rootType was requested - return the resolved graph.
        return graph;
    }

    /**
     * Converts returnValue to the desired rootType if necessary and possible.
     */
    @SuppressWarnings("unchecked")
    private Object convertIfNeeded(Type rootType, Object returnValue) {
        if (rootType == null) {
            // If no specific type was requested, return as-is
            return returnValue;
        }
        Class<?> rootClass = TypeUtilities.getRawClass(rootType);

        // If the value is already the desired type (or a subtype), just return
        if (rootClass.isInstance(returnValue)) {
            return returnValue;
        }

        // Allow simple String to Enum conversion when needed with validation
        if (rootClass.isEnum() && returnValue instanceof String) {
            String enumValue = (String) returnValue;
            // Security: Validate enum string to prevent malicious input
            if (enumValue.trim().isEmpty()) {
                throw new JsonIoException("Invalid enum value: null or empty string for enum type " + rootClass.getName());
            }
            int maxEnumLength = readOptions.getMaxEnumNameLength();
            if (enumValue.length() > maxEnumLength) {
                throw new JsonIoException("Security limit exceeded: Enum name too long (" + enumValue.length() + " chars, max " + maxEnumLength + ") for enum type " + rootClass.getName());
            }
            try {
                return Enum.valueOf((Class<Enum>) rootClass, enumValue.trim());
            } catch (IllegalArgumentException e) {
                throw new JsonIoException("Invalid enum value '" + enumValue + "' for enum type " + rootClass.getName(), e);
            }
        }

        Converter converter = getConverter();
        try {
            return converter.convert(returnValue, rootClass);
        } catch (Exception e) {
            throw new JsonIoException("Return type mismatch. Expecting: " +
                    rootClass.getName() + ", found: " + returnValue.getClass().getName(), e);
        }
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

            traverseSpecificType(jsonObj);
        }
        return (T) root.getTarget();
    }

    protected void traverseSpecificType(JsonObject jsonObj) {
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
        // Security: Prevent stack overflow via depth limiting
        int maxStackDepth = readOptions.getMaxStackDepth();
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
    protected void addMissingField(Missingfields field) {
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
            for (Missingfields mf : missingFields) {
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
        // Security: Prevent unbounded memory growth via excessive map creation
        int maxMapsToRehash = readOptions.getMaxMapsToRehash();
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

        // Enhanced Converter Integration - only for specific DTO types
        // This is intentionally restrictive to avoid breaking existing serialization patterns
        // Note: Skip Throwables as they require special factory handling for type preservation
        if (!Throwable.class.isAssignableFrom(targetType) && isConverterSimpleType(targetType)) {
            Object sourceValue = jsonObj.hasValue() ? jsonObj.getValue() : jsonObj;
            Class<?> sourceType = determineSourceType(jsonObj, sourceValue);

            if (sourceType != null && converter.isConversionSupportedFor(sourceType, targetType)) {
                try {
                    Object value = converter.convert(sourceValue, targetType);
                    return jsonObj.setFinishedTarget(value, true);
                } catch (Exception e) {
                    // Log conversion failure and continue to factory system
                    // This allows fallback to reflection-based approach
                }
            }
        }

        // Determine the factory type, considering enums and collections.
        Class<?> factoryType = determineFactoryType(jsonObj, targetType);

        // Try creating an instance using the class factory.
        Object mate = createInstanceUsingClassFactory(factoryType, jsonObj);
        if (mate != NO_FACTORY) {
            return mate;
        }

        // Legacy converter attempt (kept for backward compatibility)
        // Note: This is now redundant with enhanced converter above, but kept for safety
        if (!Throwable.class.isAssignableFrom(targetType)) {
            Object legacySourceValue = jsonObj.hasValue() ? jsonObj.getValue() : null;
            Class<?> legacySourceType = legacySourceValue != null ? legacySourceValue.getClass() : (!jsonObj.isEmpty() ? Map.class : null);
            
            if (legacySourceType != null && converter.isConversionSupportedFor(legacySourceType, targetType)) {
                try {
                    Object value = converter.convert(legacySourceValue != null ? legacySourceValue : jsonObj, targetType);
                    return jsonObj.setFinishedTarget(value, true);
                } catch (Exception e) {
                    // Conversion failed - continue with other resolution strategies
                    // Only log in debug mode to avoid noise in normal operations
                    if (Boolean.parseBoolean(System.getProperty("json-io.debug", "false"))) {
                        LOG.fine("Legacy conversion failed for " + legacySourceType + " to " + targetType + ": " + e.getMessage());
                    }
                }
            }
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
        Class clazz = readOptions.getCoercedClass(type);
        return clazz == null ? type : clazz;
    }

    private Class<?> getCoercedEnumClass(Class<?> enumClass) {
        Class<?> coercedClass = readOptions.getCoercedClass(enumClass);
        if (coercedClass != null) {
            return ClassUtilities.getClassIfEnum(coercedClass);
        }
        return null;
    }

    /**
     * Enhanced source type detection for DTO conversion.
     * This method analyzes the JsonObject structure to determine the most appropriate
     * source type for Converter-based transformation.
     */
    private Class<?> determineSourceType(JsonObject jsonObj, Object sourceValue) {
        if (sourceValue != null && sourceValue != jsonObj) {
            return sourceValue.getClass();
        }
        
        if (!jsonObj.isEmpty()) {
            return Map.class; // Default for non-empty JsonObjects
        }
        
        return null;
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

    /**
     * Check if a type should be handled by Converter as a simple type.
     * This is intentionally restrictive to avoid breaking existing serialization patterns.
     * Currently only includes: Cedar DTO types (Color, Dimension, Point, Rectangle, Insets).
     */
    private boolean isConverterSimpleType(Class<?> clazz) {
        // Cedar DTO types from java-util
        if (clazz == com.cedarsoftware.util.geom.Color.class) {
            return true;
        }
        if (clazz == com.cedarsoftware.util.geom.Dimension.class) {
            return true;
        }
        if (clazz == com.cedarsoftware.util.geom.Point.class) {
            return true;
        }
        if (clazz == com.cedarsoftware.util.geom.Rectangle.class) {
            return true;
        }
        if (clazz == com.cedarsoftware.util.geom.Insets.class) {
            return true;
        }

        return false;
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
        boolean isUnknownObject = c == Object.class && !readOptions.isReturningJsonObjects();

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
        for (UnresolvedReference ref : unresolvedRefs) {
            Object objToFix = ref.referencingObj.getTarget();
            JsonObject objReferenced = this.references.getOrThrow(ref.refId);
            Object referencedTarget = objReferenced.getTarget();

            if (ref.index >= 0) {    // Fix []'s and Collections containing a forward reference.
                if (objToFix instanceof List) {
                    ((List) objToFix).set(ref.index, referencedTarget);
                } else if (objToFix instanceof Collection) {   // Patch up Indexable Collections
                    ((Collection) objToFix).add(referencedTarget);
                } else if (objToFix instanceof Object[]) {   // Fast path for Object arrays
                    ((Object[]) objToFix)[ref.index] = referencedTarget;
                } else {
                    ArrayUtilities.setPrimitiveElement(objToFix, ref.index, referencedTarget);        // patch primitive array element
                }
            } else {    // Fix field forward reference
                // ReadOptions.getDeepInjectorMap() already caches via ClassValueMap - no local cache needed
                Map<String, Injector> injectors = getReadOptions().getDeepInjectorMap(objToFix.getClass());

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

            // Fast path for reference type arrays - avoid Array.set() reflection overhead
            if (!componentType.isPrimitive()) {
                Object[] typedArray = (Object[]) javaArray;
                for (int i = 0; i < len; i++) {
                    try {
                        Class<?> type = componentType;
                        Object item = jsonItems[i];
                        if (item instanceof JsonObject) {
                            JsonObject jObj = (JsonObject) item;
                            if (jObj.getType() != null) {
                                type = jObj.getRawType();
                            }
                        }
                        typedArray[i] = converter.convert(item, type);
                    } catch (Exception e) {
                        JsonIoException jioe = new JsonIoException(e.getMessage());
                        jioe.setStackTrace(e.getStackTrace());
                        throw jioe;
                    }
                }
            } else {
                // Primitive arrays - use optimized ArrayUtilities.setPrimitiveElement()
                for (int i = 0; i < len; i++) {
                    try {
                        Class<?> type = componentType;
                        Object item = jsonItems[i];
                        if (item instanceof JsonObject) {
                            JsonObject jObj = (JsonObject) item;
                            if (jObj.getType() != null) {
                                type = jObj.getRawType();
                            }
                        }
                        ArrayUtilities.setPrimitiveElement(javaArray, i, converter.convert(item, type));
                    } catch (Exception e) {
                        JsonIoException jioe = new JsonIoException(e.getMessage());
                        jioe.setStackTrace(e.getStackTrace());
                        throw jioe;
                    }
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
            JsonIoException jioe = new JsonIoException(e.getMessage());
            jioe.setStackTrace(e.getStackTrace());
            throw jioe;
        }
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

            // Security: Improve circular reference detection with persistent tracking
            Set<Long> visited = new HashSet<>();
            int chainDepth = 0;

            while (target.isReference()) {
                // Security: Prevent infinite loops via chain depth limit
                int maxChainDepth = readOptions.getMaxReferenceChainDepth();
                if (++chainDepth > maxChainDepth) {
                    throw new JsonIoException("Security limit exceeded: Reference chain depth (" + chainDepth + ") exceeds maximum (" + maxChainDepth + "). Possible circular reference attack.");
                }

                // Security: Enhanced circular reference detection
                if (visited.contains(id)) {
                    throw new JsonIoException("Circular reference detected in reference chain starting with id: " + id + " at depth: " + chainDepth);
                }
                visited.add(id);

                long nextId = target.getReferenceId();
                id = nextId;
                target = references.get(id);
                if (target == null) {
                    return null;
                }
            }

            return target;
        }
    }
}