package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.cedarsoftware.io.reflect.Injector;
import com.cedarsoftware.util.ArrayUtilities;
import com.cedarsoftware.util.StringUtilities;
import com.cedarsoftware.util.TypeUtilities;
import com.cedarsoftware.util.convert.Converter;

/**
 * <p>The MapResolver converts the raw Maps created from the JsonParser to higher
 * quality Maps representing the implied object graph.  It does this by replacing
 * <code>@ref</code> values with the Map indicated by the @id key with the same value.
 * </p><p>
 * This approach 'wires' the original object graph.  During the resolution process,
 * if 'peer' classes can be found for given Maps (for example, an @type entry is
 * available which indicates the class that would have been associated to the Map,
 * then the associated class is consulted to help 'improve' the quality of the primitive
 * values within the map fields.  For example, if the peer class indicated that a field
 * was of type 'short', and the Map had a long value (JSON only returns long's for integer
 * types), then the long would be converted to a short.
 * </p><p>
 * The final Map representation is a very high-quality graph that represents the original
 * JSON graph.  It can be passed as input to JsonWriter, and the JsonWriter will write
 * out the equivalent JSON to what was originally read.  This technique allows json-io to
 * be used on a machine that does not have any of the Java classes from the original graph,
 * read it in a JSON graph (any JSON graph), return the equivalent maps, allow mutations of
 * those maps, and finally this graph can be written out.
 * </p>
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
public class MapResolver extends Resolver {
    public MapResolver(ReadOptions readOptions, ReferenceTracker references, Converter converter) {
        super(readOptions, references, converter);
    }

    /**
     * Override toJavaObjects to validate rootType before resolution.
     * In Maps mode, only certain types are supported as rootType.
     */
    @Override
    <T> T toJavaObjects(JsonObject rootObj, Type rootType) {
        verifyRootType(rootType);
        return super.toJavaObjects(rootObj, rootType);
    }

    /**
     * Validates that the rootType is supported in Maps mode.
     * Maps mode supports: null, primitives/wrappers, simple types, Map, Collection, and arrays of these.
     *
     * @param rootType the requested return type
     * @throws JsonIoException if rootType is not supported in Maps mode
     */
    private void verifyRootType(Type rootType) {
        if (rootType == null) {
            return;
        }

        Class<?> rawRootType = TypeUtilities.getRawClass(rootType);
        Type typeToCheck = rootType;

        // If the raw type represents an array, drill down to the ultimate component type.
        if (rawRootType != null && rawRootType.isArray()) {
            typeToCheck = getUltimateComponentType(rootType);
            Class<?> ultimateRawType = TypeUtilities.getRawClass(typeToCheck);
            if (converter.isSimpleTypeConversionSupported(ultimateRawType) ||
                    (ultimateRawType != null && ultimateRawType.equals(Object.class))) {
                return;
            }
        } else if (converter.isSimpleTypeConversionSupported(rawRootType)) {
            return;
        }

        // Check for Collection or Map types
        Class<?> rawTypeToCheck = TypeUtilities.getRawClass(typeToCheck);
        if (rawTypeToCheck != null &&
                (Collection.class.isAssignableFrom(rawTypeToCheck) || Map.class.isAssignableFrom(rawTypeToCheck))) {
            return;
        }

        // Type not supported in Maps mode
        String typeName = rawRootType != null ? rawRootType.getName() : rootType.toString();
        throw new JsonIoException("In readOptions.isReturningJsonObjects() mode, the rootType '" + typeName +
                "' is not supported. Allowed types are:\n" +
                "- null\n" +
                "- primitive types (e.g., int, boolean) and their wrapper classes (e.g., Integer, Boolean)\n" +
                "- types supported by Converter.convert()\n" +
                "- Map or any of its subclasses\n" +
                "- Collection or any of its subclasses\n" +
                "- Arrays (of any depth) of the above types\n" +
                "Please use one of these types as the rootType, or enable readOptions.isReturningJavaObjects().");
    }

    /**
     * Drills down through array types to find the ultimate component type.
     */
    private Type getUltimateComponentType(Type type) {
        while (true) {
            if (type instanceof Class<?>) {
                Class<?> cls = (Class<?>) type;
                if (cls.isArray()) {
                    type = cls.getComponentType();
                    continue;
                }
            } else if (type instanceof GenericArrayType) {
                type = ((GenericArrayType) type).getGenericComponentType();
                continue;
            }
            return type;
        }
    }

    /**
     * In Maps mode, substitute SortedSet/SortedMap with LinkedHashSet/LinkedHashMap.
     * This avoids Comparator issues since TreeSet/TreeMap require Comparators that aren't serialized.
     * LinkedHashSet/LinkedHashMap preserve insertion order, which is better than HashSet/HashMap.
     *
     * @param jsonObj the JsonObject whose type may be substituted
     */
    private void substituteSortedCollectionType(JsonObject jsonObj) {
        Class<?> javaType = jsonObj.getRawType();
        if (javaType == null) {
            return;
        }
        // Substitute sorted collections with order-preserving equivalents
        if (SortedSet.class.isAssignableFrom(javaType)) {
            jsonObj.setType(LinkedHashSet.class);
        } else if (SortedMap.class.isAssignableFrom(javaType)) {
            jsonObj.setType(LinkedHashMap.class);
        }
    }

    /**
     * In Maps mode, substitute sorted collections from JSON's @type with LinkedHashSet/LinkedHashMap.
     * This is necessary because TreeSet/TreeMap require Comparators that aren't serialized.
     *
     * Only substitute if the type came from JSON's @type (indicated by typeString being set).
     * If the type was set via asClass(TreeMap.class), the user's request should be honored.
     */
    @Override
    protected void adjustTypeBeforeResolve(JsonObject rootObj, Type rootType) {
        // Only substitute if the type came from JSON's @type (indicated by typeString being set),
        // not from the user's request via asClass(TreeMap.class).
        // When @type is in the JSON, the Parser sets both typeString and type.
        // When only suggestedType is used, typeString remains null.
        if (rootObj.getTypeString() != null) {
            substituteSortedCollectionType(rootObj);
        }
    }

    /**
     * In Maps mode, Maps (like HashMap) need to have their entries traversed to patch @ref references.
     * The parent class's traverseMap() doesn't do this - it just processes keys/values arrays.
     * We override to handle both @keys/@items format and regular String-key Maps.
     */
    @Override
    protected void traverseMap(JsonObject jsonObj) {
        // Guard against reprocessing already-finished objects
        if (jsonObj.isFinished) {
            return;
        }

        // Apply sorted collection substitution ONLY for types from JSON's @type (typeString set),
        // not for types from user's asClass() request
        if (jsonObj.getTypeString() != null) {
            substituteSortedCollectionType(jsonObj);
        }

        // Check if this is @keys/@items format (complex keys) or regular String-key format
        Object[] complexKeys = jsonObj.getKeys();  // Returns null for regular POJOs
        if (complexKeys != null) {
            // @keys/@items format - traverse keys and items arrays for @ref patching
            Object[] items = jsonObj.getItems();
            if (items != null) {
                traverseArrayForRefs(complexKeys);
                traverseArrayForRefs(items);
            }
        } else {
            // Regular String-key Map - use traverseFields to patch @refs in values
            traverseFields(jsonObj);
        }

        // Add to rehash list so entries are copied to target Map (for asClass(TreeMap.class) etc.)
        addMapToRehash(jsonObj);
    }

    /**
     * Traverse an array looking for @ref JsonObjects to patch.
     * Also handles nested arrays recursively to avoid JsonObject wrapper allocations.
     */
    private void traverseArrayForRefs(Object[] array) {
        final int len = array.length;
        for (int i = 0; i < len; i++) {
            Object element = array[i];
            if (element == null) {
                continue;
            }
            if (element instanceof JsonObject) {
                JsonObject jObj = (JsonObject) element;
                JsonObject refObject = resolveReference(jObj);
                if (refObject != null) {
                    array[i] = refObject;  // Patch the @ref
                } else {
                    push(jObj);  // Traverse nested object
                }
            } else if (element instanceof Object[]) {
                // Recursively traverse nested arrays inline to avoid JsonObject allocation
                traverseArrayForRefs((Object[]) element);
            }
        }
    }

    /**
     * In Maps mode, handle type reconciliation for the root result when no explicit
     * rootType was specified by the user.
     * <p>
     * This logic determines the appropriate return type:
     * - Arrays are always returned as actual arrays (not JsonObject)
     * - If @type is a simple type (String, Number, etc.), convert to that type
     * - If the result is a complex type, return the raw JsonObject
     * - If the result is already a simple type, return it as-is
     */
    @Override
    protected Object reconcileResult(Object result, JsonObject rootObj, Type rootType) {
        // If user specified a rootType, don't apply Maps mode handling here
        // The type compatibility checking happens in Resolver.handleObjectRoot
        if (rootType != null) {
            return result;
        }

        // User did not specify rootType - apply Maps mode logic

        // Arrays should always be returned as actual arrays, not JsonObject
        if (result != null && result.getClass().isArray()) {
            return result;
        }

        // javaType is guaranteed non-null: Resolver.toJavaObjects() sets it to at least Object.class
        Type javaType = rootObj.getType();
        Class<?> javaClass = TypeUtilities.getRawClass(javaType);

        // If @type is a simple type or Number, convert jsonObj to its basic type
        if (converter.isSimpleTypeConversionSupported(javaClass) ||
                Number.class.isAssignableFrom(javaClass)) {
            Class<?> basicType = getJsonSynonymType(javaClass);
            return converter.convert(rootObj, basicType);
        }

        // Return primitive results directly, otherwise return the raw JsonObject
        return isBuiltInPrimitive(result, converter) ? result : rootObj;
    }

    /**
     * Maps complex types to their simpler JSON-friendly equivalents.
     */
    private Class<?> getJsonSynonymType(Class<?> javaType) {
        if (javaType == StringBuilder.class || javaType == StringBuffer.class) {
            return String.class;
        } else if (javaType == AtomicInteger.class) {
            return Integer.class;
        } else if (javaType == AtomicLong.class) {
            return Long.class;
        } else if (javaType == AtomicBoolean.class) {
            return Boolean.class;
        }
        return javaType;
    }

    /**
     * Checks if the object is a built-in primitive type supported by the Converter.
     */
    private boolean isBuiltInPrimitive(Object obj, Converter converter) {
        if (obj == null) {
            return false;
        }
        return converter.isSimpleTypeConversionSupported(obj.getClass());
    }

    protected Object readWithFactoryIfExists(Object o, Type compType) {
        // In Maps mode, convert JsonObjects with simple types (Byte, Short, etc.) to their Java equivalents
        if (o instanceof JsonObject) {
            JsonObject jsonObj = (JsonObject) o;
            Object converted = convertIfNonRefType(jsonObj, jsonObj.getRawType());
            if (converted != null) {
                jsonObj.setFinishedTarget(converted, true);
                return converted;
            }
        }
        return null;
    }

    /**
     * Walk the JsonObject fields and perform necessary substitutions so that all references matched up.
     * This code patches @ref and @id pairings up, in the 'Map of Map' mode.  Where the JSON may contain
     * an '@id' of an object which can have more than one @ref to it, this code will make sure that each
     * '@ref' (value side of the Map associated to a given field name) will be pointer to the appropriate Map
     * instance.
     * <p>
     * Note: We intentionally do NOT call setFinished() here because in Maps mode, the JsonObject itself
     * is the final result, and the same JsonObject may later be converted to Java objects via toJava().
     * Marking it finished here would cause the ObjectResolver to skip it.
     * @param jsonObj a Map-of-Map representation of the current object being examined (containing all fields).
     */
    public void traverseFields(final JsonObject jsonObj) {
        final Object target = jsonObj.getTarget();
        final ReadOptions readOptions = getReadOptions();

        // Get injector map directly - ReadOptions.getDeepInjectorMap() already caches via ClassValueMap
        Map<String, Injector> injectorMap = null;
        if (target != null) {
            injectorMap = readOptions.getDeepInjectorMap(target.getClass());
        }

        for (Map.Entry<Object, Object> e : jsonObj.entrySet()) {
            final String fieldName = (String) e.getKey();
            final Object rhs = e.getValue();
            
            if (rhs == null) {
                // No action needed - null is already the value in the map entry
                continue;
            }
            
            // Pre-cache class and injector to avoid repeated lookups
            final Class<?> rhsClass = rhs.getClass();
            final Injector injector = (injectorMap == null) ? null : injectorMap.get(fieldName);
            
            if (rhsClass.isArray()) {   // RHS is an array
                // Traverse array inline to patch @refs - avoids JsonObject wrapper allocation
                // No put needed - rhs is already the value in the map entry
                traverseArrayForRefs((Object[]) rhs);
            } else if (rhs instanceof JsonObject) {
                JsonObject jObj = (JsonObject) rhs;
                if (injector != null) {
                    final Class<?> injectorType = injector.getType();
                    boolean isNonRefClass = readOptions.isNonReferenceableClass(injectorType);
                    if (isNonRefClass) {
                        jObj.setValue(converter.convert(jObj.getValue(), injectorType));
                        continue;
                    }
                }
                JsonObject refObject = resolveReference(jObj);
                if (refObject != null) {    // Correct field references
                    e.setValue(refObject);   // Direct update - avoids indexOf() lookup
                } else {
                    // In Maps mode, convert JsonObjects with simple types (Byte, Short, etc.)
                    // to their Java equivalents and update the Map entry
                    Object converted = convertIfNonRefType(jObj, jObj.getRawType());
                    if (converted != null) {
                        jObj.setFinishedTarget(converted, true);
                        e.setValue(converted);  // Direct update - avoids indexOf() lookup
                    } else {
                        push(jObj);
                    }
                }
            } else if (injector != null) {
                // The code below is 'upgrading' the RHS values in the JsonObject Map
                // by using the @type class name (when specified and exists), to coerce the vanilla
                // JSON values into the proper types defined by the class listed in @type.  This is
                // a cool feature of json-io, that even when reading a map-of-maps JSON file, it will
                // improve the final types of values in the maps RHS, to be of the field type that
                // was optionally specified in @type.
                final Class<?> fieldType = injector.getType();
                // Performance: Skip conversion if field type is Object or already matches
                if (fieldType == Object.class || rhsClass == fieldType) {
                    continue;
                }
                // Fast path for common JSON primitive coercions (Long->int, Double->float, etc.)
                Object fastValue = fastPrimitiveCoercion(rhs, rhsClass, fieldType);
                if (fastValue != null) {
                    e.setValue(fastValue);   // Direct update - avoids indexOf() lookup
                } else if (converter.isConversionSupportedFor(rhsClass, fieldType)) {
                    Object fieldValue = converter.convert(rhs, fieldType);
                    e.setValue(fieldValue);  // Direct update - avoids indexOf() lookup
                } else if (rhs instanceof String && StringUtilities.isEmpty((String)rhs)) {
                    // Fallback: Allow "" to null out a field when converter doesn't support String conversion.
                    // Note: fieldType cannot be String.class here (line 390 checks rhsClass == fieldType).
                    // StringBuilder/StringBuffer have converter support, so they're handled above.
                    e.setValue(null);        // Direct update - avoids indexOf() lookup
                }
            }
        }
        // Clear target for non-Map types to save memory (Maps mode returns JsonObjects for POJOs).
        // For Map types (HashMap, TreeMap, etc.), keep the target - rehashMaps() will populate it.
        Object currentTarget = jsonObj.getTarget();
        if (currentTarget != null && !(currentTarget instanceof Map)) {
            jsonObj.setTarget(null);
        }
    }

    /**
     * Handles nested array elements, whether they come as JsonObject instances or raw arrays.
     *
     * @param element        The array element to handle.
     * @param componentType  The immediate component type of the current array level.
     * @param target         The parent array where the nested array should be assigned.
     * @param index          The index in the parent array.
     */
    private void handleNestedArray(Object element, Class<?> componentType, Object target, int index) {
        JsonObject jsonObject = null;

        if (element instanceof JsonObject && ((JsonObject) element).isArray()) {
            jsonObject = (JsonObject) element;
        } else if (element instanceof Object[]) {
            jsonObject = new JsonObject();
            jsonObject.setItems((Object[])element);
        }

        if (jsonObject != null) {
            if (componentType.isArray()) {
                // Set the hintType to guide createInstance to instantiate the correct array type
                jsonObject.setType(componentType);

                // Create a new array instance using createInstance, which respects the hintType
                Object arrayElement = createInstance(jsonObject);

                // Assign the newly created array to the parent array's element
                // Nested arrays are always reference types (Object[]), never primitive arrays
                ((Object[]) target)[index] = arrayElement;

            }
            // Push the JsonObject for further processing
            push(jsonObject);
        } else if (target instanceof Object[]) {
            // Primitive nested arrays cannot be wrapped as Object[]; keep original value.
            ((Object[]) target)[index] = element;
        }
    }

    protected void traverseArray(JsonObject jsonObj) {
        // Guard against reprocessing already-finished objects
        if (markFinishedIfNot(jsonObj)) {
            return;
        }

        Object[] items = jsonObj.getItems();
        if (ArrayUtilities.isEmpty(items)) {
            return;
        }

        Object target = jsonObj.getTarget() != null ? jsonObj.getTarget() : items;
        Class<?> componentType = getArrayComponentType(jsonObj);

        // Optimize: check array type ONCE, not on every element assignment
        final boolean isPrimitive = componentType.isPrimitive();
        final Object[] refArray = isPrimitive ? null : (Object[]) target;

        final int len = items.length;
        for (int i = 0; i < len; i++) {
            Object element = items[i];

            if (element == null) {
                setArrayElement(target, refArray, i, null, isPrimitive);
                continue;
            }

            final Class<?> elementClass = element.getClass();
            final JsonObject jsonElement = element instanceof JsonObject ? (JsonObject) element : null;
            if (elementClass.isArray() || (jsonElement != null && jsonElement.isArray())) {
                handleNestedArray(element, componentType, target, i);
            } else {
                processArrayElement(element, jsonElement, elementClass, componentType, target, refArray, i, isPrimitive);
            }
        }
        // Note: setFinished() already called by markFinishedIfNot() at method start
    }

    /**
     * Determine the component type of the array target.
     */
    private Class<?> getArrayComponentType(JsonObject jsonObj) {
        if (jsonObj.getTarget() != null) {
            Class<?> targetClass = jsonObj.getTarget().getClass();
            if (targetClass.isArray()) {
                return targetClass.getComponentType();
            }
        }
        return Object.class;
    }

    /**
     * Process a single array element that is not a nested array.
     */
    private void processArrayElement(Object element, JsonObject jsonElement, Class<?> elementClass, Class<?> componentType,
                                     Object target, Object[] refArray, int index, boolean isPrimitive) {
        // Fast path for common JSON primitive coercions
        Object fastValue = fastPrimitiveCoercion(element, elementClass, componentType);
        if (fastValue != null) {
            setArrayElement(target, refArray, index, fastValue, isPrimitive);
            return;
        }

        if (converter.isConversionSupportedFor(elementClass, componentType)) {
            Object convertedValue = converter.convert(element, componentType);
            setArrayElement(target, refArray, index, convertedValue, isPrimitive);
            return;
        }

        if (jsonElement != null) {
            processJsonObjectArrayElement(jsonElement, target, refArray, index, isPrimitive);
            return;
        }

        setArrayElement(target, refArray, index, element, isPrimitive);
    }

    /**
     * Process a JsonObject element within an array.
     */
    private void processJsonObjectArrayElement(JsonObject jsonObject, Object target, Object[] refArray,
                                               int index, boolean isPrimitive) {
        if (jsonObject.isReference()) {
            processArrayReference(jsonObject, target, refArray, index, isPrimitive);
        } else {
            processArrayJsonObject(jsonObject, target, refArray, index, isPrimitive);
        }
    }

    /**
     * Process an @ref reference within an array.
     */
    private void processArrayReference(JsonObject jsonObject, Object target, Object[] refArray,
                                       int index, boolean isPrimitive) {
        long refId = jsonObject.getReferenceId();
        JsonObject refObject = references.getOrThrow(refId);

        Object convertedRef = convertIfNonRefType(refObject, refObject.getRawType());
        if (convertedRef != null) {
            refObject.setFinishedTarget(convertedRef, true);
            setArrayElement(target, refArray, index, refObject.getTarget(), isPrimitive);
        } else {
            setArrayElement(target, refArray, index, refObject, isPrimitive);
        }
    }

    /**
     * Process a non-reference JsonObject within an array.
     */
    private void processArrayJsonObject(JsonObject jsonObject, Object target, Object[] refArray,
                                        int index, boolean isPrimitive) {
        Object converted = convertIfNonRefType(jsonObject, jsonObject.getRawType());
        if (converted != null) {
            setArrayElement(target, refArray, index, converted, isPrimitive);
            jsonObject.setFinished();
        } else {
            push(jsonObject);
        }
    }

    /**
     * Traverse a JsonObject representing a collection (array) and deserialize its elements.
     *
     * @param jsonObj The JsonObject representing the collection.
     */
    @Override
    protected void traverseCollection(final JsonObject jsonObj) {
        // Guard against reprocessing already-finished objects
        if (markFinishedIfNot(jsonObj)) {
            return;
        }

        // Apply sorted collection substitution ONLY for types from JSON's @type (typeString set),
        // not for types from user's asClass() request (consistent with traverseMap behavior)
        if (jsonObj.getTypeString() != null) {
            substituteSortedCollectionType(jsonObj);
        }

        Object[] items = jsonObj.getItems();
        Collection<Object> col = (Collection<Object>) jsonObj.getTarget();
        if (col == null) {
            col = (Collection<Object>) createInstance(jsonObj);
        }

        // Performance: Pre-size ArrayList to avoid repeated resizing
        if (items != null) {
            ensureCollectionCapacity(col, items.length);
        }

        final boolean isList = col instanceof List;
        final boolean isEnumSet = col instanceof EnumSet;
        int idx = 0;

        if (items != null) {
            for (Object element : items) {
                if (element == null) {
                    col.add(null);
                } else if (isDirectlyAddableJsonValue(element)) {
                    col.add(element);
                } else if (element instanceof Object[]) {
                    wrapArrayAndAddToCollection((Object[]) element, Object[].class, col);
                } else {
                    if (element instanceof JsonObject) {
                        processJsonObjectElement((JsonObject) element, jsonObj, col, idx, isList, isEnumSet);
                    } else {
                        // Preserve non-JsonObject values in maps mode instead of hard-casting.
                        col.add(element);
                    }
                }
                idx++;
            }
        }
        // Note: setFinished() already called by markFinishedIfNot() at method start
    }

    /**
     * Process a JsonObject element within a collection traversal.
     */
    private void processJsonObjectElement(JsonObject jObj, JsonObject parent, Collection<Object> col,
                                          int idx, boolean isList, boolean isEnumSet) {
        if (jObj.isReference()) {
            resolveReferenceInCollection(jObj, parent, col, idx, isList);
            return;
        }

        // Handle EnumSet special case: skip enum elements without name
        if (isEnumSet) {
            Class<?> rawType = jObj.getRawType();
            boolean noEnumName = !jObj.containsKey("name") && !jObj.containsKey("Enum.name") && !jObj.hasValue();
            if (rawType != null && rawType.isEnum() && noEnumName) {
                jObj.setFinished();
                return;
            }
        }

        jObj.setType(Object.class);
        createInstance(jObj);
        addResolvedObjectToCollection(jObj, col);
    }

    protected Object resolveArray(Type suggestedType, List<Object> list) {
        // Extract the raw class from the provided Type.
        Class<?> rawType = (suggestedType == null) ? null : TypeUtilities.getRawClass(suggestedType);

        // If there's no suggested type or the raw type is Object, just return an Object[].
        if (suggestedType == null || rawType == Object.class) {
            return list.toArray();
        }

        JsonObject jsonArray = new JsonObject();

        // Store the full, refined type (which may include generics) in the JsonObject.
        jsonArray.setType(suggestedType);

        // Apply sorted collection substitution for Maps mode
        substituteSortedCollectionType(jsonArray);

        // If the Collection is assignable from raw type, create a Collection instance accordingly.
        if (Collection.class.isAssignableFrom(rawType)) {
            jsonArray.setTarget(createInstance(jsonArray));
        } else {
            // Otherwise, assume it's an array type and create a new array instance.
            jsonArray.setTarget(Array.newInstance(rawType, list.size()));
        }

        jsonArray.setItems(list.toArray());
        return jsonArray;
    }

    /**
     * Fast path for common JSON primitive to Java primitive coercions.
     * JSON only produces Long, Double, String, Boolean - handle common cases without Converter lookup.
     * Returns null if no fast conversion is available (fall through to Converter).
     */
    private static Object fastPrimitiveCoercion(Object value, Class<?> valueClass, Class<?> targetType) {
        if (valueClass == Long.class) {
            // Identity: Long→long/Long needs no conversion - return original object (avoids unbox+rebox)
            if (targetType == long.class || targetType == Long.class) return value;
            return coerceLong((Long) value, targetType);
        } else if (valueClass == Double.class) {
            // Identity: Double→double/Double needs no conversion - return original object (avoids unbox+rebox)
            if (targetType == double.class || targetType == Double.class) return value;
            return coerceDouble((Double) value, targetType);
        }
        return null;
    }

    /**
     * Coerce a Long value to the target numeric type.
     */
    private static Object coerceLong(long longVal, Class<?> targetType) {
        if (targetType == int.class || targetType == Integer.class) {
            return (int) longVal;
        } else if (targetType == short.class || targetType == Short.class) {
            return (short) longVal;
        } else if (targetType == byte.class || targetType == Byte.class) {
            return (byte) longVal;
        } else if (targetType == double.class || targetType == Double.class) {
            return (double) longVal;
        } else if (targetType == float.class || targetType == Float.class) {
            return (float) longVal;
        }
        return null;
    }

    /**
     * Coerce a Double value to the target numeric type.
     */
    private static Object coerceDouble(double doubleVal, Class<?> targetType) {
        if (targetType == float.class || targetType == Float.class) {
            return (float) doubleVal;
        } else if (targetType == long.class || targetType == Long.class) {
            return (long) doubleVal;
        } else if (targetType == int.class || targetType == Integer.class) {
            return (int) doubleVal;
        }
        return null;
    }
}
