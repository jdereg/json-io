package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.cedarsoftware.io.reflect.Injector;
import com.cedarsoftware.util.ArrayUtilities;
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
    protected MapResolver(ReadOptions readOptions, ReferenceTracker references, Converter converter) {
        super(readOptions, references, converter);
    }

    /**
     * Override toJavaObjects to validate rootType before resolution.
     * In Maps mode, only certain types are supported as rootType.
     */
    @Override
    public <T> T toJavaObjects(JsonObject rootObj, Type rootType) {
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

        Converter converter = getConverter();
        Class<?> rawRootType = TypeUtilities.getRawClass(rootType);
        Type typeToCheck = rootType;

        // If the raw type represents an array, drill down to the ultimate component type.
        if (rawRootType != null && rawRootType.isArray()) {
            while (true) {
                if (typeToCheck instanceof Class<?>) {
                    Class<?> cls = (Class<?>) typeToCheck;
                    if (cls.isArray()) {
                        typeToCheck = cls.getComponentType();
                        continue;
                    }
                } else if (typeToCheck instanceof GenericArrayType) {
                    typeToCheck = ((GenericArrayType) typeToCheck).getGenericComponentType();
                    continue;
                }
                break;
            }
            // After drilling down, get the raw class of the ultimate component.
            Class<?> ultimateRawType = TypeUtilities.getRawClass(typeToCheck);
            if (converter.isSimpleTypeConversionSupported(ultimateRawType)
                    || (ultimateRawType != null && ultimateRawType.equals(Object.class))) {
                return;
            }
        } else {
            // For non-array types, check if the type is supported by simple conversion.
            if (converter.isSimpleTypeConversionSupported(rawRootType)) {
                return;
            }
        }

        // Check for Collection or Map types
        Class<?> rawTypeToCheck = TypeUtilities.getRawClass(typeToCheck);
        if (rawTypeToCheck != null) {
            if (Collection.class.isAssignableFrom(rawTypeToCheck)) {
                return;
            }
            if (Map.class.isAssignableFrom(rawTypeToCheck)) {
                return;
            }
        }

        // Type not supported in Maps mode
        String typeName = (rawRootType != null ? rawRootType.getName() : rootType.toString());
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
     * In Maps mode, handle type reconciliation for the root result when no explicit
     * rootType was specified by the user.
     *
     * This logic determines the appropriate return type:
     * - Arrays are always returned as actual arrays (not JsonObject)
     * - If @type is a simple type (String, Number, etc.), convert to that type
     * - If the result is a complex type, return the raw JsonObject
     * - If the result is already a simple type, return it as-is
     */
    @Override
    protected Object reconcileResult(Object result, JsonObject rootObj, Type rootType) {
        // If user specified a rootType, don't apply Maps mode handling here
        // The type compatibility checking happens in JsonReader.handleObjectRoot
        if (rootType != null) {
            return result;
        }

        // User did not specify rootType - apply Maps mode logic

        // Arrays should always be returned as actual arrays, not JsonObject
        if (result != null && result.getClass().isArray()) {
            return result;
        }

        Converter converter = getConverter();
        Type javaType = rootObj.getType();

        if (javaType != null) {
            Class<?> javaClass = TypeUtilities.getRawClass(javaType);
            // If @type is a simple type or Number, convert jsonObj to its basic type
            if (converter.isSimpleTypeConversionSupported(javaClass) ||
                    Number.class.isAssignableFrom(javaClass)) {
                Class<?> basicType = getJsonSynonymType(javaClass);
                return converter.convert(rootObj, basicType);
            }
            // If it's not a built-in primitive, return the raw JsonObject
            if (!isBuiltInPrimitive(result, converter)) {
                return rootObj;
            }
        }

        // If the resolved result is a simple type, return it
        if (result != null && converter.isSimpleTypeConversionSupported(result.getClass())) {
            return result;
        }

        // Otherwise, return the raw JsonObject
        return rootObj;
    }

    /**
     * Maps complex types to their simpler JSON-friendly equivalents.
     */
    private Class<?> getJsonSynonymType(Class<?> javaType) {
        if (javaType == StringBuilder.class || javaType == StringBuffer.class) {
            return String.class;
        }
        if (javaType == AtomicInteger.class) {
            return Integer.class;
        }
        if (javaType == AtomicLong.class) {
            return Long.class;
        }
        if (javaType == AtomicBoolean.class) {
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
        // No custom reader support for maps
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

        // Get injector map directly - ReadOptions.getDeepInjectorMap() already caches via ClassValueMap
        Map<String, Injector> injectorMap = null;
        if (target != null) {
            injectorMap = getReadOptions().getDeepInjectorMap(target.getClass());
        }

        final ReferenceTracker refTracker = getReferences();
        final Converter converter = getConverter();
        final ReadOptions readOptions = getReadOptions();

        for (Map.Entry<Object, Object> e : jsonObj.entrySet()) {
            final String fieldName = (String) e.getKey();
            final Object rhs = e.getValue();
            
            if (rhs == null) {
                jsonObj.put(fieldName, null);
                continue;
            }
            
            // Pre-cache class and injector to avoid repeated lookups
            final Class<?> rhsClass = rhs.getClass();
            final Injector injector = (injectorMap == null) ? null : injectorMap.get(fieldName);
            
            if (rhsClass.isArray()) {   // RHS is an array
                // Trace the contents of the array (so references inside the array and into the array work)
                JsonObject jsonArray = new JsonObject();
                jsonArray.setItems((Object[])rhs);
                push(jsonArray);

                // Assign the array directly to the Map key (field name)
                jsonObj.put(fieldName, rhs);
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
                Long refId = jObj.getReferenceId();

                if (refId != null) {    // Correct field references
                    JsonObject refObject = refTracker.getOrThrow(refId);
                    jsonObj.put(fieldName, refObject);    // Update Map-of-Maps reference
                } else {
                    push(jObj);
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
                    jsonObj.put(fieldName, fastValue);
                } else if (converter.isConversionSupportedFor(rhsClass, fieldType)) {
                    Object fieldValue = converter.convert(rhs, fieldType);
                    jsonObj.put(fieldName, fieldValue);
                } else if (rhs instanceof String) {
                    if (fieldType != String.class && fieldType != StringBuilder.class && fieldType != StringBuffer.class) {
                        if ("".equals(((String) rhs).trim())) {   // Allow "" to null out a non-String field on the inbound JSON
                            jsonObj.put(fieldName, null);
                        }
                    }
                }
            }
        }
        jsonObj.setTarget(null);  // don't waste space (used for typed return, not for Map return)
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
        } else if (element != null && element.getClass().isArray()) {
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
        }
    }

    protected void traverseArray(JsonObject jsonObj) {
        Object[] items = jsonObj.getItems();
        if (ArrayUtilities.isEmpty(items)) {
            return;
        }

        Object target = jsonObj.getTarget() != null ? jsonObj.getTarget() : items;
        final ReferenceTracker refTracker = getReferences();
        final Converter converter = getConverter();
        final ReadOptions readOptions = getReadOptions();

        // Determine the immediate component type of the current array level
        Class<?> componentType = Object.class;
        if (jsonObj.getTarget() != null) {
            final Class<?> targetClass = jsonObj.getTarget().getClass();
            if (targetClass.isArray()) {
                componentType = targetClass.getComponentType();
            }
        }

        // Optimize: check array type ONCE, not on every element assignment
        final boolean isPrimitive = componentType.isPrimitive();
        final Object[] refArray = isPrimitive ? null : (Object[]) target;

        final int len = items.length;
        for (int i = 0; i < len; i++) {
            Object element = items[i];

            if (element == null) {
                if (isPrimitive) {
                    ArrayUtilities.setPrimitiveElement(target, i, null);
                } else {
                    refArray[i] = null;
                }
                continue;
            }

            // Each element can be of different type - cannot cache class outside loop
            final Class<?> elementClass = element.getClass();
            if (elementClass.isArray() || (element instanceof JsonObject && ((JsonObject) element).isArray())) {
                // Handle nested arrays using the unified helper method
                handleNestedArray(element, componentType, target, i);
            } else {
                // Fast path for common JSON primitive coercions (Long->int, Double->float, etc.)
                Object fastValue = fastPrimitiveCoercion(element, elementClass, componentType);
                if (fastValue != null) {
                    if (isPrimitive) {
                        ArrayUtilities.setPrimitiveElement(target, i, fastValue);
                    } else {
                        refArray[i] = fastValue;
                    }
                } else if (converter.isConversionSupportedFor(elementClass, componentType)) {
                    // Convert the element to the base component type
                    Object convertedValue = converter.convert(element, componentType);
                    if (isPrimitive) {
                        ArrayUtilities.setPrimitiveElement(target, i, convertedValue);
                    } else {
                        refArray[i] = convertedValue;
                    }
                } else if (element instanceof JsonObject) {
                    JsonObject jsonObject = (JsonObject) element;
                    Long refId = jsonObject.getReferenceId();

                    if (refId == null) {
                        // Convert JsonObject to its destination type if possible
                        // Performance: Only check Converter for nonRef types (UUID, ZonedDateTime, etc.)
                        // User types (Dog, Cat, House) are NOT nonRef and Converter can't convert them anyway
                        Class<?> type = jsonObject.getRawType();
                        boolean isNonRef = type != null && readOptions.isNonReferenceableClass(type);
                        if (isNonRef && converter.isConversionSupportedFor(Map.class, type)) {
                            Object converted = converter.convert(jsonObject, type);
                            if (isPrimitive) {
                                ArrayUtilities.setPrimitiveElement(target, i, converted);
                            } else {
                                refArray[i] = converted;
                            }
                            jsonObject.setFinished();
                        } else {
                            push(jsonObject);
                        }
                    } else {    // Connect reference
                        JsonObject refObject = refTracker.getOrThrow(refId);
                        Class<?> type = refObject.getRawType();

                        // Performance: Only check Converter for nonRef types (UUID, ZonedDateTime, etc.)
                        boolean isNonRef = type != null && readOptions.isNonReferenceableClass(type);
                        if (isNonRef && converter.isConversionSupportedFor(Map.class, type)) {
                            Object convertedRef = converter.convert(refObject, type);
                            refObject.setFinishedTarget(convertedRef, true);
                            if (isPrimitive) {
                                ArrayUtilities.setPrimitiveElement(target, i, refObject.getTarget());
                            } else {
                                refArray[i] = refObject.getTarget();
                            }
                        } else {
                            if (isPrimitive) {
                                ArrayUtilities.setPrimitiveElement(target, i, refObject);
                            } else {
                                refArray[i] = refObject;
                            }
                        }
                    }
                } else {
                    if (isPrimitive) {
                        ArrayUtilities.setPrimitiveElement(target, i, element);
                    } else {
                        refArray[i] = element;
                    }
                }
            }
        }
        jsonObj.setFinished();
    }

    /**
     * Traverse a JsonObject representing a collection (array) and deserialize its elements.
     *
     * @param jsonObj The JsonObject representing the collection.
     */
    @Override
    protected void traverseCollection(final JsonObject jsonObj) {
        if (jsonObj.isFinished) {
            return;
        }

        // Apply sorted collection substitution before instance creation
        substituteSortedCollectionType(jsonObj);

        Object[] items = jsonObj.getItems();
        Collection<Object> col = (Collection<Object>) jsonObj.getTarget();
        if (col == null) {
            col = (Collection<Object>)createInstance(jsonObj);
        }
        
        // Performance: Pre-size ArrayList to avoid repeated resizing
        if (items != null && col instanceof ArrayList) {
            ((ArrayList<?>) col).ensureCapacity(items.length);
        }
        
        final boolean isList = col instanceof List;
        int idx = 0;

        if (items != null) {
            // Cache to avoid repeated getter calls
            final ReadOptions readOptions = getReadOptions();
            final ReferenceTracker refTracker = getReferences();

            for (Object element : items) {
                if (element == null) {
                    col.add(null);
                    idx++;
                    continue;
                }

                // Each element can be of different type - cannot cache class outside loop
                if (element instanceof String || element instanceof Boolean || element instanceof Double || element instanceof Long) {
                    // Allow Strings, Booleans, Longs, and Doubles to be "inline" without Java object decoration (@id, @type, etc.)
                    col.add(element);
                } else if (element.getClass().isArray()) {
                    final JsonObject jObj = new JsonObject();
                    jObj.setType(Object[].class);
                    jObj.setItems((Object[]) element);
                    createInstance(jObj);
                    col.add(jObj.getTarget());
                    push(jObj);
                } else { // if (element instanceof JsonObject)
                    final JsonObject jObj = (JsonObject) element;
                    final Long ref = jObj.getReferenceId();

                    if (ref != null) {
                        JsonObject refObject = refTracker.getOrThrow(ref);

                        if (refObject.getTarget() != null) {
                            col.add(refObject.getTarget());
                        } else {
                            // Security: Use secure method to add unresolved references
                            addUnresolvedReference(new UnresolvedReference(jsonObj, idx, ref));
                            if (isList) {   // Index-able collection, so set 'null' as element for now - will be patched in later.
                                col.add(null);
                            }
                        }
                    } else {
                        if (col instanceof EnumSet) {
                            Class<?> rawType = jObj.getRawType();
                            boolean noEnumName = !jObj.containsKey("name") && !jObj.containsKey("Enum.name") && !jObj.hasValue();
                            if (rawType != null && rawType.isEnum() && noEnumName) {
                                jObj.setFinished();
                                idx++;
                                continue;
                            }
                        }

                        jObj.setType(Object.class);
                        createInstance(jObj);
                        boolean isNonRefClass = readOptions.isNonReferenceableClass(jObj.getRawType());
                        if (!isNonRefClass) {
                            traverseSpecificType(jObj);
                        }

                        if (!(col instanceof EnumSet)) {   // EnumSet has already had it's items added to it.
                            col.add(jObj.getTarget());
                        }
                    }
                }
                idx++;
            }
        }
        jsonObj.setFinished();
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
        // Long -> integer types (most common case in JSON)
        if (valueClass == Long.class) {
            long longVal = (Long) value;
            if (targetType == int.class || targetType == Integer.class) {
                return (int) longVal;
            }
            if (targetType == short.class || targetType == Short.class) {
                return (short) longVal;
            }
            if (targetType == byte.class || targetType == Byte.class) {
                return (byte) longVal;
            }
            if (targetType == double.class || targetType == Double.class) {
                return (double) longVal;
            }
            if (targetType == float.class || targetType == Float.class) {
                return (float) longVal;
            }
        }
        // Double -> float types
        else if (valueClass == Double.class) {
            double doubleVal = (Double) value;
            if (targetType == float.class || targetType == Float.class) {
                return (float) doubleVal;
            }
            if (targetType == long.class || targetType == Long.class) {
                return (long) doubleVal;
            }
            if (targetType == int.class || targetType == Integer.class) {
                return (int) doubleVal;
            }
        }
        // No fast conversion available
        return null;
    }
}