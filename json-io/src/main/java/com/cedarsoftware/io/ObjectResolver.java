package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.io.reflect.Injector;
import com.cedarsoftware.util.ArrayUtilities;
import com.cedarsoftware.util.Convention;
import com.cedarsoftware.util.IdentitySet;
import com.cedarsoftware.util.StringUtilities;
import com.cedarsoftware.util.TypeUtilities;
import com.cedarsoftware.util.convert.Converter;

/**
 * <p>The ObjectResolver converts the raw Maps created from the JsonParser to Java
 * objects (a graph of Java instances).  The Maps have an optional type entry associated
 * to them to indicate what Java peer instance to create.  The reason type is optional
 * is because it can be inferred in a couple instances.  A non-primitive field that
 * points to an object that is of the same type of the field, does not require the
 * '@type' because it can be inferred from the field.  This is not always the case.
 * For example, if a Person field points to an Employee object (where Employee is a
 * subclass of Person), then the resolver cannot create an instance of the field type
 * (Person) because this is not the proper type.  (It had an Employee record with more
 * fields in this example). In this case, the writer recognizes that the instance type
 * and field type are not the same, and therefore it writes the @type.
 * </p><p>
 * A similar case as above occurs with specific array types.  If there is a Person[]
 * containing Person and Employee instances, then the Person instances will not have
 * the '@type' but the employee instances will (because they are more derived than Person).
 * </p><p>
 * The resolver 'wires' the original object graph.  It does this by replacing
 * '@ref' values in the Maps with pointers (on the field of the associated instance of the
 * Map) to the object that has the same ID.  If the object has not yet been read, then
 * an UnresolvedReference is created.  These are back-patched at the end of the resolution
 * process.  UnresolvedReference keeps track of what field or array element the actual value
 * should be stored within, and then locates the object (by id), and updates the appropriate
 * value.
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
@SuppressWarnings({ "rawtypes", "unchecked"})
public class ObjectResolver extends Resolver
{
    /**
     * Constructor
     * @param readOptions Options to use while reading.
     */
    public ObjectResolver(ReadOptions readOptions, ReferenceTracker references, Converter converter) {
        super(readOptions, references, converter);
    }

    /**
     * Walk the Java object fields and copy them from the JSON object to the Java object,
     * performing any necessary conversions on primitives or deep traversals for field assignments
     * to other objects, arrays, Collections, or Maps.
     *
     * @param jsonObj a Map-of-Map representation of the current object being examined.
     */
    public void traverseFields(final JsonObject jsonObj) {
        if (markFinishedIfNot(jsonObj)) {
            return;
        }

        final Object javaMate = jsonObj.getTarget();
        final Map<String, Injector> injectorMap = readOptions.getDeepInjectorMap(javaMate.getClass());

        // Enhanced for-loop is more efficient than iterator for EntrySet
        // Uses cached missingFieldHandler from parent Resolver for performance
        for (Map.Entry<Object, Object> entry : jsonObj.entrySet()) {
            String key = (String) entry.getKey();
            final Injector injector = injectorMap.get(key);
            Object rhs = entry.getValue();
            if (injector != null) {
                assignField(jsonObj, injector, rhs);
            } else if (missingFieldHandler != null) {
                handleMissingField(jsonObj, rhs, key);
            }
        }
    }

    /**
     * Map a JSON object field to a Java object field.
     *
     * @param jsonObj  a Map-of-Map representation of the current object being examined (containing all fields).
     * @param injector an instance of Injector used for setting values on the target object.
     * @param rhs      the JSON value that will be converted and stored in the field on the associated Java target object.
     */
    public void assignField(final JsonObject jsonObj, final Injector injector, final Object rhs) {
        final Object target = jsonObj.getTarget();
        final Type fieldType = injector.getGenericType();
        final Class<?> rawFieldType = TypeUtilities.getRawClass(fieldType);

        if (rhs == null) {
            injector.inject(target, rawFieldType.isPrimitive() ? converter.convert(null, rawFieldType) : null);
            return;
        }

        // If there is a "tree" of objects (e.g., Map<String, List<Person>>), the sub-objects may not have a
        // @type on them if the JSON source is from JSON.stringify(). Deep traverse the values and assign
        // the full generic type based on the parameterized type.
        if (rhs instanceof JsonObject) {
            if (fieldType instanceof ParameterizedType) {
                markUntypedObjects(fieldType, (JsonObject) rhs);
            }

            final JsonObject jObj = (JsonObject) rhs;
            Type explicitType = jObj.getType();
            if (explicitType != null && !TypeUtilities.hasUnresolvedType(explicitType)) {
                // If the field has an explicit type, use it.
                jObj.setType(explicitType);
            } else {
                // Resolve the field type in the context of the target object.
                // TypeUtilities.resolveTypeUsingInstance() internally caches results.
                Type resolvedFieldType = TypeUtilities.resolveTypeUsingInstance(target, fieldType);
                jObj.setType(resolvedFieldType);
            }
        }

        Object special;
        // Use the raw type (extracted from the full generic type) when checking for custom conversion.
        if ((special = readWithFactoryIfExists(rhs, rawFieldType)) != null) {
            injector.inject(target, special);
        } else if (rhs instanceof Object[]) {
            // If the RHS is an Object[], wrap it in a JsonObject for processing.
            // This handles both array fields (String[]) and collection fields (List<String>).
            // Note: Primitive arrays (int[], etc.) are already handled by createAndPopulateArray
            // and returned as the correct primitive array type, so they bypass this branch.
            final Object[] elements = (Object[]) rhs;
            JsonObject jsonArray = new JsonObject();
            jsonArray.setType(fieldType);
            jsonArray.setItems(elements);

            // Mark types on untyped objects within the array for nested generic types.
            // This must be done BEFORE createInstance() which may convert the ParameterizedType
            // to a raw Class (e.g., List<User> becomes ArrayList.class).
            // This enables proper type inference for JSON without @type markers.
            if (fieldType instanceof ParameterizedType) {
                markUntypedObjects(fieldType, jsonArray);
                // Store element type before createInstance changes the type to a raw Class.
                // This preserves generic type information for traverseCollection.
                Type[] typeArgs = ((ParameterizedType) fieldType).getActualTypeArguments();
                if (typeArgs.length > 0) {
                    jsonArray.setItemElementType(typeArgs[0]);
                }
            }

            createInstance(jsonArray);
            injector.inject(target, jsonArray.getTarget());
            push(jsonArray);
        } else if (rhs instanceof JsonObject) {
            final JsonObject jsRhs = (JsonObject) rhs;

            final JsonObject refObject = resolveReference(jsRhs);
            if (refObject != null) {    // Handle field references.
                if (refObject.getTarget() != null) {
                    injector.inject(target, refObject.getTarget());
                } else {
                    addUnresolvedReference(new UnresolvedReference(jsonObj, injector.getName(), jsRhs.getReferenceId()));
                }
            } else {    // Direct assignment for nested objects.
                // For Map fields with ParameterizedType, preserve the value type before createInstance
                // changes the type to a raw Class. This enables proper type inference in traverseMap.
                if (fieldType instanceof ParameterizedType) {
                    Class<?> rawClass = TypeUtilities.getRawClass(fieldType);
                    if (rawClass != null && Map.class.isAssignableFrom(rawClass)) {
                        Type[] typeArgs = ((ParameterizedType) fieldType).getActualTypeArguments();
                        if (typeArgs.length >= 2) {
                            // Store value type (second type arg) for traverseMap to use
                            jsRhs.setItemElementType(typeArgs[1]);
                        }
                    }
                }
                // Create instance first so @ref references to this object can resolve
                createInstance(jsRhs);
                Object fieldObject = jsRhs.getTarget();
                injector.inject(target, fieldObject);
                if (!readOptions.isNonReferenceableClass(jsRhs.getRawType())) {
                    push(jsRhs);
                }
            }
        } else {
            // Allow empty strings to null out non-String fields
            if (rhs instanceof String && StringUtilities.isEmpty((String)rhs) && rawFieldType != String.class) {
                injector.inject(target, null);
            } else {
                injector.inject(target, rhs);
            }
        }
    }

    /**
     * Try to create a java object from the missing field. Mostly primitive types and JsonObjects
     * that contain @type attribute will be candidates for the missing field callback, others will
     * be ignored. All missing fields are stored for later notification.
     *
     * @param jsonObj      a Map-of-Map representation of the current object being examined.
     * @param rhs          the JSON value that will be converted and stored in the 'field' on the associated Java target object.
     * @param missingField name of the missing field in the java object.
     */
    protected void handleMissingField(final JsonObject jsonObj, final Object rhs, final String missingField) {
        final Object target = jsonObj.getTarget();
        try {
            if (rhs == null) {
                storeMissingField(target, missingField, null);
                return;
            }

            if (rhs.getClass().isArray()) {
                // impossible to determine the array type.
                storeMissingField(target, missingField, null);
            } else if (rhs instanceof JsonObject) {
                final JsonObject jObj = (JsonObject) rhs;

                final JsonObject refObject = resolveReference(jObj);
                if (refObject != null) {
                    storeMissingField(target, missingField, refObject.getTarget());
                } else if (jObj.getType() != null) {
                    Object javaInstance = createInstance(jObj);
                    if (!readOptions.isNonReferenceableClass(jObj.getRawType()) && !jObj.isFinished) {
                        push((JsonObject) rhs);
                    }
                    storeMissingField(target, missingField, javaInstance);
                } else {
                    storeMissingField(target, missingField, null);
                }
            } else {
                storeMissingField(target, missingField, rhs);
            }
        } catch (Exception e) {
            if (e instanceof JsonIoException) {
                throw e;
            }
            String message = e.getClass().getSimpleName() + " missing field '" + missingField + "' on target: "
                    + safeToString(target) + " with value: " + rhs;
            throw new JsonIoException(message, e);
        }
    }

    /**
     * Stores missing field info for later handler callback (some references may need resolution first).
     */
    private void storeMissingField(Object target, String missingField, Object value) {
        addMissingField(new MissingField(target, missingField, value));
    }

    /**
     * @param o Object to turn into a String
     * @return .toString() version of o or "null" if o is null.
     */
    private static String safeToString(Object o) {
        if (o == null) {
            return "null";
        }
        try {
            return o.toString();
        } catch (Exception e) {
            return o.getClass().toString();
        }
    }

    /**
     * Process java.util.Collection and its derivatives. Collections are written specially
     * so that the serialization does not expose the Collection's internal structure (e.g., TreeSet).
     * All entries are processed, except unresolved references, which are filled in later.
     * For index-able collections, unresolved references are set back into the proper element location.
     * For non-index-able collections (Sets), the unresolved references are added via .add().
     *
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     */
    protected void traverseCollection(final JsonObject jsonObj) {
        if (markFinishedIfNot(jsonObj)) {
            return;
        }

        Object[] items = jsonObj.getItems();
        if (items == null) {
            return;
        }

        final Collection col = (Collection) jsonObj.getTarget();
        ensureCollectionCapacity(col, items.length);

        final boolean isList = col instanceof List;
        int idx = 0;

        // Extract element type - first check for stored element type (preserved before createInstance
        // changed the type to a raw Class), then fall back to extracting from ParameterizedType.
        // We use Object.class as fallback when element type cannot be determined.
        Type elementType = jsonObj.getItemElementType();
        Class<?> rawElementType = Object.class;
        if (elementType != null) {
            rawElementType = TypeUtilities.getRawClass(elementType);
            if (rawElementType == null) {
                rawElementType = Object.class;
            }
        } else {
            final Type collectionType = jsonObj.getType();
            if (collectionType instanceof ParameterizedType) {
                Type[] typeArgs = ((ParameterizedType) collectionType).getActualTypeArguments();
                if (typeArgs.length > 0) {
                    elementType = typeArgs[0];
                    rawElementType = TypeUtilities.getRawClass(elementType);
                    if (rawElementType == null) {
                        rawElementType = Object.class;
                    }
                }
            } else if (collectionType instanceof Class) {
                // Handle classes that extend generic collections (e.g., UserList extends ArrayList<User>)
                elementType = extractElementTypeFromGenericSuperclass((Class<?>) collectionType);
                if (elementType != null) {
                    rawElementType = TypeUtilities.getRawClass(elementType);
                    if (rawElementType == null) {
                        rawElementType = Object.class;
                    }
                }
            }
            if (elementType == null) {
                elementType = Object.class;
            }
        }

        for (Object element : items) {
            if (element == null) {
                col.add(null);
                idx++;
                continue;
            }

            // Fast path for native JSON types (includes BigInteger/BigDecimal from JsonParser)
            // but convert numbers to target type if needed (e.g., Long -> Integer for List<Integer>)
            if (isDirectlyAddableJsonValue(element)) {
                if (element instanceof Number && rawElementType != Object.class
                        && Number.class.isAssignableFrom(rawElementType)
                        && !rawElementType.isInstance(element)) {
                    // Convert to target numeric type (e.g., Long to Integer)
                    element = converter.convert(element, rawElementType);
                }
                col.add(element);
                idx++;
                continue;
            }

            // Pre-cache element class to avoid repeated getClass() calls
            final Class<?> elementClass = element.getClass();

            if (element instanceof JsonObject) {
                // Most common case after primitives - handle JsonObject
                JsonObject jObj = (JsonObject) element;
                if (jObj.isReference()) {
                    resolveReferenceInCollection(jObj, jsonObj, col, idx, isList);
                } else {
                    // Set the element's full type to the extracted element type, but only if
                    // the element doesn't already have a type set (e.g., from markUntypedObjects()
                    // or from @type in the JSON). This enables proper type inference for nested
                    // generic types when deserializing JSON without @type markers.
                    if (jObj.getType() == null) {
                        jObj.setType(elementType);
                    }
                    createInstance(jObj);
                    addResolvedObjectToCollection(jObj, col);
                }
            } else if (elementClass.isArray()) {
                // Determine the type for the nested array/collection.
                // If elementType is a Collection type (e.g., List<User>), wrap as collection.
                // If elementType is an array type (e.g., User[]), create an actual array.
                Class<?> rawElementType2 = TypeUtilities.getRawClass(elementType);
                if (rawElementType2 != null && Collection.class.isAssignableFrom(rawElementType2)) {
                    // elementType is a Collection (e.g., List<User>), use it directly
                    wrapArrayAndAddToCollection((Object[]) element, elementType, col);
                } else if (rawElementType2 != null && rawElementType2.isArray()) {
                    // elementType is an array type (e.g., User[]), create an actual array
                    Object arrayInstance = handleNestedArrayElement((Object[]) element, elementType);
                    col.add(arrayInstance);
                } else {
                    // Fallback: extract component type for nested collection
                    Type nestedType = TypeUtilities.extractArrayComponentType(elementType);
                    if (nestedType == null) {
                        nestedType = Object.class;
                    }
                    wrapArrayAndAddToCollection((Object[]) element, nestedType, col);
                }
            } else {
                // Check for custom factory or converter support
                Object special = readWithFactoryIfExists(element, rawElementType);
                if (special != null) {
                    col.add(special);
                } else {
                    col.add(element);
                }
            }
            idx++;
        }
    }

    /**
     * Process java.util.Map and its derivatives with support for parameterized value types.
     * When the Map type is parameterized (e.g., Map<String, List<User>>), this method ensures
     * that values are properly converted to their target collection types.
     *
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     */
    @Override
    protected void traverseMap(JsonObject jsonObj) {
        if (jsonObj.isFinished) {
            return;
        }
        jsonObj.setFinished();

        // Get keys/items using asTwoArrays() to get validation (throws if @keys/@items have different lengths)
        Map.Entry<Object[], Object[]> pair = jsonObj.asTwoArrays();
        Object[] existingKeys = pair.getKey();
        Object[] existingItems = pair.getValue();

        if (existingKeys == null) {  // If keys is null, items is also null
            addMapToRehash(jsonObj);
            return;
        }

        // Extract value type - first check for stored value type (preserved before createInstance
        // changed the type to a raw Class), then fall back to extracting from ParameterizedType.
        Type valueType = jsonObj.getItemElementType();  // For Maps, this stores the value type
        if (valueType == null) {
            Type mapType = jsonObj.getType();
            if (mapType instanceof ParameterizedType) {
                Type[] typeArgs = ((ParameterizedType) mapType).getActualTypeArguments();
                if (typeArgs.length >= 2) {
                    valueType = typeArgs[1];
                }
            }
            if (valueType == null) {
                valueType = Object.class;
            }
        }

        Class<?> rawValueType = TypeUtilities.getRawClass(valueType);
        boolean valueIsCollection = rawValueType != null && Collection.class.isAssignableFrom(rawValueType);

        // Check if this is @keys/@items format (both explicitly set via setKeys/setItems)
        // vs standard entry format (entries added via put())
        boolean isKeysItemsFormat = jsonObj.getKeys() != null;

        if (isKeysItemsFormat) {
            // @keys/@items format - process arrays directly
            processMapKeysValues(existingKeys, existingItems, valueType, valueIsCollection);
        } else {
            // Standard entry format - process entries and convert values if needed
            processMapEntries(jsonObj, existingKeys, existingItems, valueType, valueIsCollection);
        }

        addMapToRehash(jsonObj);
    }

    /**
     * Process Map in @keys/@items format.
     */
    private void processMapKeysValues(Object[] keys, Object[] items, Type valueType, boolean valueIsCollection) {
        // Process keys
        JsonObject keysWrapper = new JsonObject();
        keysWrapper.setItems(keys);
        keysWrapper.setTarget(keys);
        push(keysWrapper);

        // Extract element type for collection values
        Type valueElementType = null;
        if (valueIsCollection && valueType instanceof ParameterizedType) {
            Type[] typeArgs = ((ParameterizedType) valueType).getActualTypeArguments();
            if (typeArgs.length > 0) {
                valueElementType = typeArgs[0];
            }
        }

        // Process values - convert to collections if needed
        if (valueIsCollection) {
            for (int i = 0; i < items.length; i++) {
                Object value = items[i];
                if (value instanceof Object[] && !(value instanceof JsonObject)) {
                    JsonObject wrapper = new JsonObject();
                    wrapper.setType(valueType);
                    wrapper.setItems((Object[]) value);
                    // Preserve element type for traverseCollection
                    if (valueElementType != null) {
                        wrapper.setItemElementType(valueElementType);
                    }
                    createInstance(wrapper);
                    // Store actual collection instance, not wrapper
                    items[i] = wrapper.getTarget();
                    push(wrapper);
                } else if (value instanceof JsonObject) {
                    JsonObject jObj = (JsonObject) value;
                    if (jObj.getType() == null) {
                        jObj.setType(valueType);
                    }
                    // Preserve element type for traverseCollection
                    if (valueElementType != null && jObj.getItemElementType() == null) {
                        jObj.setItemElementType(valueElementType);
                    }
                    createInstance(jObj);
                    // Store actual target instance
                    if (jObj.getTarget() != null) {
                        items[i] = jObj.getTarget();
                    }
                    push(jObj);
                }
            }
        } else {
            JsonObject itemsWrapper = new JsonObject();
            itemsWrapper.setItems(items);
            itemsWrapper.setTarget(items);
            push(itemsWrapper);
        }
    }

    /**
     * Process Map in standard entry format, converting values to collections if needed.
     * Uses the pre-validated keys/items arrays from asTwoArrays().
     */
    private void processMapEntries(JsonObject jsonObj, Object[] keys, Object[] items, Type valueType, boolean valueIsCollection) {
        if (valueIsCollection) {
            // Extract element type for collection values
            Type valueElementType = null;
            if (valueType instanceof ParameterizedType) {
                Type[] typeArgs = ((ParameterizedType) valueType).getActualTypeArguments();
                if (typeArgs.length > 0) {
                    valueElementType = typeArgs[0];
                }
            }

            // Iterate over entries and wrap array values as typed collections
            // Note: items array corresponds to the values in the map
            int len = Math.min(keys.length, items.length);
            for (int i = 0; i < len; i++) {
                Object value = items[i];
                if (value instanceof Object[] && !(value instanceof JsonObject)) {
                    // Wrap raw array in JsonObject with collection type
                    JsonObject wrapper = new JsonObject();
                    wrapper.setType(valueType);
                    wrapper.setItems((Object[]) value);
                    // Preserve element type for traverseCollection
                    if (valueElementType != null) {
                        wrapper.setItemElementType(valueElementType);
                    }
                    createInstance(wrapper);
                    // Update items array with the actual collection instance
                    items[i] = wrapper.getTarget();
                    push(wrapper);
                } else if (value instanceof JsonObject) {
                    JsonObject jObj = (JsonObject) value;
                    if (jObj.getType() == null) {
                        jObj.setType(valueType);
                    }
                    // Preserve element type for traverseCollection
                    if (valueElementType != null && jObj.getItemElementType() == null) {
                        jObj.setItemElementType(valueElementType);
                    }
                    createInstance(jObj);
                    // Update items array with the actual target instance
                    if (jObj.getTarget() != null) {
                        items[i] = jObj.getTarget();
                    }
                    push(jObj);
                }
            }

            // Still need to push wrappers for keys for traversal
            JsonObject keysWrapper = new JsonObject();
            keysWrapper.setItems(keys);
            keysWrapper.setTarget(keys);
            push(keysWrapper);
        } else {
            // Use default behavior - wrap and push arrays for traversal.
            // IMPORTANT: Must use the original arrays (not copies) because rehashMaps() modifies them in place.
            JsonObject keysWrapper = new JsonObject();
            keysWrapper.setItems(keys);
            keysWrapper.setTarget(keys);
            push(keysWrapper);

            JsonObject itemsWrapper = new JsonObject();
            itemsWrapper.setItems(items);
            itemsWrapper.setTarget(items);
            push(itemsWrapper);
        }
    }

    /**
     * Traverse the JsonObject associated to an array (of any type). Convert and assign the
     * list of items in the JsonObject (stored in the @items field) to each array element.
     * All array elements are processed excluding elements that reference an unresolved object.
     * These are filled in later.
     *
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     */
    protected void traverseArray(final JsonObject jsonObj) {
        if (markFinishedIfNot(jsonObj)) {
            return;
        }

        final Object[] jsonItems = jsonObj.getItems();
        if (ArrayUtilities.isEmpty(jsonItems)) {
            return;
        }
        final int len = jsonItems.length;
        final Object array = jsonObj.getTarget();
        final Class<?> fallbackCompType = array.getClass().getComponentType();

        // Use the helper to extract the effective component type from the full type.
        Type effectiveComponentType = TypeUtilities.extractArrayComponentType(jsonObj.getType());
        if (effectiveComponentType == null) {
            effectiveComponentType = fallbackCompType;
        }
        // For operations that require a Class, extract the raw type.
        final Class effectiveRawComponentType = TypeUtilities.getRawClass(effectiveComponentType);

        // Optimize: check array type ONCE, not on every element assignment
        final boolean isPrimitive = fallbackCompType.isPrimitive();
        final Object[] refArray = isPrimitive ? null : (Object[]) array;

        for (int i = 0; i < len; i++) {
            final Object element = jsonItems[i];
            Object resolved;

            if (element == null) {
                setArrayElement(array, refArray, i, null, isPrimitive);
            } else if ((resolved = readWithFactoryIfExists(element, effectiveRawComponentType)) != null) {
                // Custom reader/factory or Converter handled it (including String→Enum, Number→Enum)
                setArrayElement(array, refArray, i, resolved, isPrimitive);
            } else if (element.getClass().isArray()) {
                // Array of arrays - use shared helpers
                if (char[].class == effectiveRawComponentType) {
                    setArrayElement(array, refArray, i, handleCharArrayElement((Object[]) element), isPrimitive);
                } else {
                    setArrayElement(array, refArray, i, handleNestedArrayElement((Object[]) element, effectiveComponentType), isPrimitive);
                }
            } else if (element instanceof JsonObject) {
                JsonObject jsonElement = (JsonObject) element;
                if (jsonElement.isReference()) {
                    // Use shared reference resolution helper
                    resolved = resolveReferenceElement(jsonElement, jsonObj, i);
                    if (resolved != UNRESOLVED_REFERENCE) {
                        setArrayElement(array, refArray, i, resolved, isPrimitive);
                    }
                    // If unresolved, the helper already added it to unresolved references
                } else {
                    // Use shared JsonObject processing helper
                    resolved = processJsonObjectElement(jsonElement, effectiveComponentType);
                    setArrayElement(array, refArray, i, resolved, isPrimitive);
                }
            } else {
                // Allow empty strings to null out non-String, non-Object array elements
                boolean isEmptyString = element instanceof String
                        && ((String) element).trim().isEmpty()
                        && effectiveRawComponentType != String.class
                        && effectiveRawComponentType != Object.class;
                setArrayElement(array, refArray, i, isEmptyString ? null : element, isPrimitive);
            }
        }
        jsonObj.clear();
    }

    /**
     * Convert the passed-in object (o) to a proper Java object. If the passed-in object (o) has a custom reader
     * associated to it, then have it convert the object. If there is no custom reader, then return null.
     *
     * @param o            Object to read (convert). This will be either a JsonObject or a JSON primitive
     *                     (String, long, boolean, double, or null).
     * @param inferredType The full target Type (including generics) to which 'o' should be converted.
     * @return The Java object converted from the passed-in object o, or null if there is no custom reader.
     */
    protected Object readWithFactoryIfExists(final Object o, final Type inferredType) {
        Convention.throwIfNull(o, "Bug in json-io, null must be checked before calling this method.");

        // Extract the raw type from the suggested inferred type.
        Class<?> rawInferred = (inferredType != null) ? TypeUtilities.getRawClass(inferredType) : null;

        // Check if we should skip due to not using custom reader for this type
        if (rawInferred != null && readOptions.isNotCustomReaderClass(rawInferred)) {
            return null;
        }

        JsonObject jsonObj;
        Class<?> targetClass;

        if (o instanceof JsonObject) {
            jsonObj = (JsonObject) o;
            if (jsonObj.isReference()) {
                return null; // no factory for references.
            }
            if (jsonObj.getTarget() == null) {
                targetClass = jsonObj.getRawType();
                if (targetClass == null || rawInferred == null) {
                    return null;
                }
                // Attempt early instance creation.
                Object factoryCreated = createInstance(jsonObj);
                if (factoryCreated != null && jsonObj.isFinished()) {
                    return factoryCreated;
                }
            } else {
                targetClass = jsonObj.getRawType();
            }
        } else {
            // o is not a JsonObject; use the inferred type (or o.getClass() if rawInferred is Object or null).
            targetClass = (rawInferred == null || rawInferred == Object.class) ? o.getClass() : rawInferred;
            jsonObj = new JsonObject();
            jsonObj.setValue(o);
            jsonObj.setType(targetClass);
        }

        if (targetClass != rawInferred && readOptions.isNotCustomReaderClass(targetClass)) {
            return null;
        }

        // Simple type conversion if possible
        if (jsonObj.getTarget() == null && jsonObj.hasValue()) {
            Object value = jsonObj.getValue();
            if (converter.isSimpleTypeConversionSupported(value.getClass(), targetClass)) {
                Object converted = converter.convert(value, targetClass);
                return jsonObj.setFinishedTarget(converted, true);
            }
        }

        // Try custom class factory
        ClassFactory classFactory = readOptions.getClassFactory(targetClass);
        if (classFactory != null && jsonObj.getTarget() == null) {
            Object target = createInstanceUsingClassFactory(targetClass, jsonObj);
            if (jsonObj.isFinished()) {
                return target;
            }
        }

        // Try a custom reader
        JsonClassReader reader = readOptions.getCustomReader(targetClass);
        if (reader == null) {
            return null;
        }
        Object read = reader.read(o, this);
        return (read != null) ? jsonObj.setFinishedTarget(read, true) : null;
    }

    /**
     * Traverses untyped JSON objects and stamps them with their inferred Java types.
     * This enables correct instantiation later during deserialization.
     * Uses a stack-based traversal to handle arbitrarily deep object graphs.
     */
    private void markUntypedObjects(final Type type, final JsonObject rhs) {
        if (rhs.isFinished) {
            return;     // Already marked.
        }

        // Use Map.Entry for type-safe pairing of Type and instance
        final Deque<Map.Entry<Type, Object>> stack = new ArrayDeque<>();
        // Track visited JsonObjects to prevent duplicate traversal when reachable via multiple paths
        // Uses lightweight IdentitySet instead of IdentityHashMap for better performance
        final Set<JsonObject> visited = new IdentitySet<>();
        stack.addFirst(new AbstractMap.SimpleEntry<>(type, rhs));

        while (!stack.isEmpty()) {
            Map.Entry<Type, Object> item = stack.removeFirst();
            final Type t = item.getKey();
            final Object instance = item.getValue();

            if (instance == null) {
                continue;
            }

            // Skip already-processed JsonObjects (visited in this call or finished by main traversal)
            if (instance instanceof JsonObject) {
                JsonObject jObj = (JsonObject) instance;
                // add() returns false if already present
                if (jObj.isFinished || !visited.add(jObj)) {
                    continue;
                }
            }

            if (t instanceof ParameterizedType) {
                handleParameterizedTypeMarking((ParameterizedType) t, instance, type, stack);
            } else {
                stampTypeOnJsonObject(instance, t);
            }
        }
    }

    /**
     * Handles type marking for parameterized types {@code List<T>, Map<K,V>}, etc.
     */
    private void handleParameterizedTypeMarking(final ParameterizedType pType,
                                                 final Object instance,
                                                 final Type parentType,
                                                 final Deque<Map.Entry<Type, Object>> stack) {
        Class<?> clazz = TypeUtilities.getRawClass(pType);
        Type[] typeArgs = pType.getActualTypeArguments();

        if (typeArgs.length < 1 || clazz == null) {
            return;
        }

        // Resolve the type in the context of the parent type
        Type resolvedType = TypeUtilities.resolveType(parentType, pType);
        stampTypeOnJsonObject(instance, resolvedType);

        if (Map.class.isAssignableFrom(clazz)) {
            handleMapTypeMarking(instance, typeArgs, stack);
        } else if (Collection.class.isAssignableFrom(clazz)) {
            handleCollectionTypeMarking(instance, pType, typeArgs, clazz, stack);
        } else {
            handleObjectFieldsMarking(instance, pType, typeArgs, stack);
        }
    }

    /**
     * Handles {@code Map<K,V>} type marking by processing keys and values.
     */
    private void handleMapTypeMarking(final Object instance,
                                       final Type[] typeArgs,
                                       final Deque<Map.Entry<Type, Object>> stack) {
        JsonObject jsonObj = (JsonObject) instance;
        Map.Entry<Object[], Object[]> pair = jsonObj.asTwoArrays();
        addItemsToStack(stack, pair.getKey(), typeArgs[0]);
        addItemsToStack(stack, pair.getValue(), typeArgs[1]);
    }

    /**
     * Handles {@code Collection<T>}to type marking for arrays, Collections, and JsonObjects.
     */
    private void handleCollectionTypeMarking(final Object instance,
                                              final Type containerType,
                                              final Type[] typeArgs,
                                              final Class<?> collectionClass,
                                              final Deque<Map.Entry<Type, Object>> stack) {
        if (instance.getClass().isArray()) {
            handleArrayInCollection(instance, containerType, collectionClass, stack);
        } else if (instance instanceof Collection) {
            // OPTIMIZATION: Skip if collection items don't need field traversal
            if (shouldSkipTraversal(typeArgs[0])) {
                return;
            }
            for (Object o : (Collection<?>) instance) {
                stack.addFirst(new AbstractMap.SimpleEntry<>(typeArgs[0], o));
            }
        } else if (instance instanceof JsonObject) {
            // OPTIMIZATION: Skip if array items don't need field traversal
            if (shouldSkipTraversal(typeArgs[0])) {
                return;
            }
            final Object[] array = ((JsonObject) instance).getItems();
            if (array != null) {
                for (Object o : array) {
                    stack.addFirst(new AbstractMap.SimpleEntry<>(typeArgs[0], o));
                }
            }
        }
    }

    /**
     * Handles arrays that represent collection contents during type marking.
     * The arrayInstance contains the elements of a collection (e.g., User objects in a List<User>).
     *
     * @param arrayInstance The array containing collection elements
     * @param containerType The full parameterized type of the collection (e.g., List<User>)
     * @param collectionClass The raw collection class (e.g., List.class) - unused after refactor
     * @param stack The processing stack for type marking
     */
    private void handleArrayInCollection(final Object arrayInstance,
                                          final Type containerType,
                                          final Class<?> collectionClass,
                                          final Deque<Map.Entry<Type, Object>> stack) {
        // Extract the element type from containerType.
        // For List<User>, elementType = User
        // For List<List<User>>, elementType = List<User>
        Type elementType = Object.class;
        if (containerType instanceof ParameterizedType) {
            Type[] typeArgs = ((ParameterizedType) containerType).getActualTypeArguments();
            if (typeArgs.length > 0) {
                elementType = typeArgs[0];
            }
        }

        int len = ArrayUtilities.getLength(arrayInstance);
        for (int i = 0; i < len; i++) {
            Object element = ArrayUtilities.getElement(arrayInstance, i);
            if (element == null) {
                continue;
            }

            // Handle nested array (e.g., element is Object[] representing a List<User> within List<List<User>>)
            if (element.getClass().isArray()) {
                // Convert the inner array to a List for type resolution
                int innerLen = ArrayUtilities.getLength(element);
                List<Object> items = new ArrayList<>(innerLen);
                for (int j = 0; j < innerLen; j++) {
                    items.add(ArrayUtilities.getElement(element, j));
                }

                // Wrap the array in a JsonObject with the element type (e.g., List<User>)
                JsonObject coll = new JsonObject();
                coll.setType(elementType);  // Use full parameterized type, not raw class
                coll.setItems((Object[]) element);
                stack.addFirst(new AbstractMap.SimpleEntry<>(elementType, items));
                ArrayUtilities.setElement(arrayInstance, i, coll);
            } else {
                // Non-array elements (e.g., JsonObjects representing Users) get the element type
                stack.addFirst(new AbstractMap.SimpleEntry<>(elementType, element));
            }
        }
    }

    /**
     * Handles regular object field type marking (non-Map, non-Collection).
     */
    private void handleObjectFieldsMarking(final Object instance,
                                            final Type containerType,
                                            final Type[] typeArgs,
                                            final Deque<Map.Entry<Type, Object>> stack) {
        if (!(instance instanceof JsonObject)) {
            return;
        }

        // Compute field map for THIS type (critical for nested objects of different types)
        Class<?> rawClass = TypeUtilities.getRawClass(containerType);
        if (rawClass == null) {
            return;
        }
        Map<String, Injector> classFields = readOptions.getDeepInjectorMap(rawClass);

        final JsonObject jObj = (JsonObject) instance;
        for (Map.Entry<Object, Object> entry : jObj.entrySet()) {
            final String fieldName = (String) entry.getKey();

            // Skip synthetic outer class references
            if (fieldName.startsWith("this$")) {
                continue;
            }

            Injector injector = classFields.get(fieldName);
            if (injector != null) {
                Type genericType = injector.getGenericType();
                // Resolve the field's type using the parent type
                Type resolved = TypeUtilities.resolveType(containerType, genericType);

                // Fallback if resolution didn't fully resolve the type
                if (TypeUtilities.hasUnresolvedType(resolved)) {
                    resolved = typeArgs[0];  // Use first type argument as fallback
                }

                // OPTIMIZATION: Skip if field type doesn't need traversal
                if (!shouldSkipTraversal(resolved)) {
                    stack.addFirst(new AbstractMap.SimpleEntry<>(resolved, entry.getValue()));
                }
            }
        }
    }

    /**
     * Determines if a type should skip traversal because it doesn't need field-level type inference.
     * Types with isObjectFinal() factories are fully created/loaded by the factory and have no
     * fields needing type marking.
     */
    private boolean shouldSkipTraversal(final Type type) {
        Class<?> rawClass = TypeUtilities.getRawClass(type);
        if (rawClass == null) {
            return false;        // defensive code, can't execute until behavior of getRawClass() changes
        }

        // Skip primitives and their wrappers - they have no fields
        if (rawClass.isPrimitive() ||
            rawClass == String.class ||
            Number.class.isAssignableFrom(rawClass) ||
            rawClass == Boolean.class ||
            rawClass == Character.class) {
            return true;
        }

        // Skip types with final factories - they're fully created with no field traversal needed
        ClassFactory factory = readOptions.getClassFactory(rawClass);
        return factory != null && factory.isObjectFinal();
    }

    /**
     * Helper method to add array items to the stack with their type.
     * Used by handleMapTypeMarking() to process Map keys and values.
     * Each element is added individually since they are separate Map entries,
     * not elements of a single collection.
     */
    private void addItemsToStack(final Deque<Map.Entry<Type, Object>> stack,
                                  final Object[] items,
                                  final Type itemType) {
        if (items == null || items.length < 1) {
            return;
        }

        // OPTIMIZATION: Skip if itemType has a final factory or is a primitive type
        // These types don't need field-level type inference
        if (shouldSkipTraversal(itemType)) {
            return;
        }

        // Add each item individually with its type.
        // For Map<K, V>, each item is a separate key or value, not elements of one collection.
        // Iterate in reverse to preserve order after addFirst.
        for (int i = items.length - 1; i >= 0; i--) {
            stack.addFirst(new AbstractMap.SimpleEntry<>(itemType, items[i]));
        }
    }

    /**
     * Mark 'type' on JsonObject when the type is missing and is a 'leaf' node
     * (no further subtypes in its parameterized type definition).
     */
    private static void stampTypeOnJsonObject(final Object o, final Type t) {
        if (o instanceof JsonObject && t != null) {
            JsonObject jObj = (JsonObject) o;
            if (jObj.getType() == null) {
                // Bypass setter because it could throw an unresolved type exception
                jObj.type = t;
            }
        }
    }

    protected Object resolveArray(Type suggestedType, List<Object> list) {
        // No type info - return Object[]
        if (suggestedType == null || TypeUtilities.getRawClass(suggestedType) == Object.class) {
            return list.toArray();
        }

        Class<?> rawType = TypeUtilities.getRawClass(suggestedType);

        // Special handling for char[] - stored as a single String in JSON
        if (rawType == char.class) {
            if (list.isEmpty()) {
                return new char[0];
            }
            Object first = list.get(0);
            if (first instanceof String) {
                return ((String) first).toCharArray();
            }
        }

        // Create JsonObject wrapper and let traverseArray() handle element resolution.
        // This defers all the complex element processing (nested arrays, Collections,
        // forward references, type conversion) to the standard traversal path.
        JsonObject jsonArray = new JsonObject();
        jsonArray.setType(suggestedType);
        jsonArray.setTarget(Array.newInstance(rawType, list.size()));
        jsonArray.setItems(list.toArray());
        return jsonArray;
    }

    /**
     * Sentinel value indicating an unresolved forward reference.
     */
    private static final Object UNRESOLVED_REFERENCE = new Object();

    // ============================================================================
    // Shared array element processing helpers
    // ============================================================================

    /**
     * Resolve a reference element. Returns the target if resolved, or UNRESOLVED_REFERENCE if forward reference.
     * Optionally adds to unresolved references list if arrayHolder is provided.
     *
     * @param refHolder   The JsonObject containing the reference
     * @param arrayHolder The array's JsonObject wrapper (for tracking unresolved refs), may be null
     * @param index       The index in the array (for unresolved ref tracking)
     * @return The resolved target, or UNRESOLVED_REFERENCE if not yet resolvable
     */
    private Object resolveReferenceElement(JsonObject refHolder, JsonObject arrayHolder, int index) {
        long ref = refHolder.getReferenceId();
        JsonObject refObject = references.get(ref);
        if (refObject != null && refObject.getTarget() != null) {
            return refObject.getTarget();
        }
        // Forward reference - can't resolve yet
        if (arrayHolder != null) {
            addUnresolvedReference(new UnresolvedReference(arrayHolder, index, ref));
        }
        return UNRESOLVED_REFERENCE;
    }

    /**
     * Process a JsonObject element (non-reference) for array population.
     * Sets the type, creates the instance, and pushes for traversal if needed.
     *
     * @param jObj          The JsonObject element to process
     * @param componentType The full generic type for the array component
     * @return The created target object
     */
    private Object processJsonObjectElement(JsonObject jObj, Type componentType) {
        jObj.setType(componentType);
        createInstance(jObj);
        Object target = jObj.getTarget();
        if (target != null && !jObj.isFinished && !readOptions.isNonReferenceableClass(target.getClass())) {
            push(jObj);
        }
        return target;
    }

    /**
     * Handle a nested array element (Object[] that should become a typed array).
     * Creates a JsonObject wrapper, creates instance, and pushes for traversal.
     *
     * @param arrayElement  The Object[] element
     * @param componentType The full generic type for the nested array
     * @return The created array instance
     */
    private Object handleNestedArrayElement(Object[] arrayElement, Type componentType) {
        JsonObject jsonArray = new JsonObject();
        jsonArray.setItems(arrayElement);
        jsonArray.setType(componentType);
        Object instance = createInstance(jsonArray);
        push(jsonArray);
        return instance;
    }

    /**
     * Handle a char[] element stored as String in JSON.
     *
     * @param arrayElement The Object[] containing the String representation
     * @return The char[] array
     */
    private char[] handleCharArrayElement(Object[] arrayElement) {
        return arrayElement.length == 0 ? new char[]{} : ((String) arrayElement[0]).toCharArray();
    }

    /**
     * Override to preserve element type for nested collections.
     * Wraps a raw Object[] array element in a JsonObject with the proper type information,
     * creates its instance, adds it to the collection, and pushes it for traversal.
     *
     * @param arrayElement the raw Object[] to wrap
     * @param componentType the full generic type for the collection/array (e.g., List<User>)
     * @param col the collection to add the created instance to
     */
    @Override
    protected void wrapArrayAndAddToCollection(Object[] arrayElement, Type componentType, Collection<Object> col) {
        JsonObject jObj = new JsonObject();
        jObj.setType(componentType);
        jObj.setItems(arrayElement);

        // Preserve element type for nested collections (e.g., List<List<User>> -> List<User>)
        if (componentType instanceof ParameterizedType) {
            Type[] typeArgs = ((ParameterizedType) componentType).getActualTypeArguments();
            if (typeArgs.length > 0) {
                jObj.setItemElementType(typeArgs[0]);
            }
        }

        createInstance(jObj);
        col.add(jObj.getTarget());
        push(jObj);
    }

    /**
     * Extracts the element type from a class that extends a generic Collection.
     * For example, if UserList extends ArrayList&lt;User&gt;, this method returns User.
     * Returns null if the element type is unresolved (e.g., a TypeVariable).
     *
     * @param clazz the class to examine
     * @return the element type, or null if not found or unresolved
     */
    private Type extractElementTypeFromGenericSuperclass(Class<?> clazz) {
        if (clazz == null || !Collection.class.isAssignableFrom(clazz)) {
            return null;
        }

        // Walk up the class hierarchy looking for a parameterized superclass
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            Type genericSuper = current.getGenericSuperclass();
            if (genericSuper instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericSuper;
                Class<?> rawSuper = TypeUtilities.getRawClass(pt);
                // Check if this is a Collection superclass
                if (rawSuper != null && Collection.class.isAssignableFrom(rawSuper)) {
                    Type[] typeArgs = pt.getActualTypeArguments();
                    if (typeArgs.length > 0) {
                        Type elementType = typeArgs[0];
                        // Only return if the type is resolved (not a TypeVariable)
                        if (!TypeUtilities.hasUnresolvedType(elementType)) {
                            return elementType;
                        }
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

}