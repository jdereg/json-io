package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import com.cedarsoftware.io.reflect.Injector;
import com.cedarsoftware.util.ArrayUtilities;
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

    // ========================================================================
    // Field Assignment Helpers
    // ========================================================================

    /**
     * Checks if a class is a "simple type" that Converter can handle completely.
     * Simple types include primitives, wrappers, String, and non-referenceable JDK types.
     */
    private boolean isSimpleType(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        return clazz.isPrimitive()
                || clazz == String.class
                || Number.class.isAssignableFrom(clazz)
                || clazz == Boolean.class
                || clazz == Character.class
                || readOptions.isNonReferenceableClass(clazz);
    }

    /**
     * Checks if we can use fast-path Converter for array elements.
     * Returns true only when we're confident Converter can handle the conversion safely.
     * This is conservative to avoid conversion failures for edge cases.
     */
    private boolean canUseFastPathForArray(Object[] elements, Class<?> targetComponentType) {
        if (!isSimpleType(targetComponentType)) {
            return false;
        }

        // Must have at least one element to check source type
        if (elements.length == 0) {
            return true;  // Empty array - Converter can handle this
        }

        // Find the first non-null element to determine source type
        Class<?> sourceElementType = null;
        for (Object element : elements) {
            if (element != null) {
                sourceElementType = element.getClass();
                break;
            }
        }

        if (sourceElementType == null) {
            return true;  // All nulls - Converter can handle this
        }

        // Only use fast-path when source and target are SAME type (no conversion needed)
        // or when both are numeric types (safe numeric conversions)
        if (sourceElementType == targetComponentType) {
            return true;  // Same type, no conversion needed
        }

        // Allow numeric-to-numeric conversions (int[] -> long[], etc.)
        if (Number.class.isAssignableFrom(sourceElementType) && Number.class.isAssignableFrom(targetComponentType)) {
            return true;
        }

        // For other cases, don't use fast-path - let the normal path handle it
        // This avoids issues like String -> char where multi-char strings fail
        return false;
    }

    /**
     * Just-in-time type stamping for array elements.
     * Sets the type on untyped JsonObject elements before they are processed.
     */
    private void stampElementTypes(Object[] elements, Type elementType) {
        for (Object element : elements) {
            if (element instanceof JsonObject) {
                JsonObject jObj = (JsonObject) element;
                // Only stamp if no type is set and it's not a reference
                if (jObj.getType() == null && !jObj.isReference()) {
                    jObj.setType(elementType);
                }
            }
        }
    }

    /**
     * Fast path for common JSON primitive to Java primitive coercions.
     * JSON only produces Long, Double, String, Boolean - handle common cases without Converter lookup.
     * Returns null if no fast conversion is available (fall through to readWithFactoryIfExists).
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
     * Directly assign parsed JSON Long/Double values into primitive arrays, avoiding
     * intermediate wrapper allocation from fastPrimitiveCoercion() in hot array loops.
     */
    private static boolean tryAssignParsedNumberToPrimitiveArray(
            Object array, int index, Class<?> primitiveType, Object element, Class<?> elementClass) {
        if (elementClass == Long.class) {
            long value = (Long) element;
            if (primitiveType == long.class) {
                ((long[]) array)[index] = value;
                return true;
            } else if (primitiveType == int.class) {
                ((int[]) array)[index] = (int) value;
                return true;
            } else if (primitiveType == double.class) {
                ((double[]) array)[index] = (double) value;
                return true;
            } else if (primitiveType == byte.class) {
                ((byte[]) array)[index] = (byte) value;
                return true;
            } else if (primitiveType == float.class) {
                ((float[]) array)[index] = (float) value;
                return true;
            } else if (primitiveType == short.class) {
                ((short[]) array)[index] = (short) value;
                return true;
            }
        } else if (elementClass == Double.class) {
            double value = (Double) element;
            if (primitiveType == double.class) {
                ((double[]) array)[index] = value;
                return true;
            } else if (primitiveType == float.class) {
                ((float[]) array)[index] = (float) value;
                return true;
            } else if (primitiveType == long.class) {
                ((long[]) array)[index] = (long) value;
                return true;
            } else if (primitiveType == int.class) {
                ((int[]) array)[index] = (int) value;
                return true;
            }
        }
        return false;
    }

    /**
     * Handle assignment of an Object[] RHS to a field.
     * Wraps the array in a JsonObject, preserves generic type info, and queues for traversal.
     * This handles both array fields (String[]) and collection fields (List&lt;String&gt;).
     */
    private void assignArrayField(final JsonObject jsonObj, final Injector injector,
                                   final Object[] elements, final Type fieldType, final Object target) {
        // Check if we can use fast-path conversion (simple element types)
        Class<?> rawFieldType = TypeUtilities.getRawClass(fieldType);
        if (rawFieldType != null && rawFieldType.isArray()) {
            Class<?> componentType = rawFieldType.getComponentType();
            if (canUseFastPathForArray(elements, componentType)) {
                // Fast path: use Converter for simple type arrays (e.g., int[] -> long[])
                injector.inject(target, converter.convert(elements, rawFieldType));
                return;
            }
        }

        // Slow path: wrap in JsonObject and queue for traversal
        JsonObject jsonArray = new JsonObject();
        jsonArray.setType(fieldType);
        jsonArray.setItems(elements);

        // Preserve generic type information for collections
        if (fieldType instanceof ParameterizedType) {
            Type[] typeArgs = ((ParameterizedType) fieldType).getActualTypeArguments();
            if (typeArgs.length > 0) {
                Type elementType = typeArgs[0];
                jsonArray.setItemElementType(elementType);
                stampElementTypes(elements, elementType);
            }
        }

        createInstance(jsonArray);
        injector.inject(target, jsonArray.getTarget());

        if (!readOptions.isNonReferenceableClass(jsonArray.getRawType())) {
            push(jsonArray);
        }
    }

    /**
     * Handle assignment of a JsonObject RHS (non-reference) to a field.
     * Handles nested objects, Maps, and custom classes.
     */
    private void assignJsonObjectField(final JsonObject jsonObj, final Injector injector,
                                        final JsonObject jsRhs, final Type fieldType, final Object target) {
        final Type resolvedFieldType = TypeUtilities.resolveTypeUsingInstance(target, fieldType);

        // Preserve explicit type metadata when present and resolved.
        Type explicitType = jsRhs.getType();
        if (explicitType == null || TypeUtilities.hasUnresolvedType(explicitType)) {
            jsRhs.setType(resolvedFieldType);
        }

        seedIncrementalContainerMetadata(resolvedFieldType, jsRhs);

        createInstance(jsRhs);
        injector.inject(target, jsRhs.getTarget());

        if (!readOptions.isNonReferenceableClass(jsRhs.getRawType())) {
            push(jsRhs);
        }
    }

    /**
     * Seed container metadata on the current JsonObject so downstream traversal can stamp types incrementally.
     * This avoids deep pre-pass traversal and only annotates the immediate container when metadata is missing.
     */
    private void seedIncrementalContainerMetadata(final Type fieldType, final JsonObject jsRhs) {
        if (!(fieldType instanceof ParameterizedType)) {
            return;
        }

        ParameterizedType pType = (ParameterizedType) fieldType;
        Class<?> rawClass = TypeUtilities.getRawClass(pType);
        if (rawClass == null || jsRhs.getItemElementType() != null) {
            return;
        }

        Type[] typeArgs = pType.getActualTypeArguments();
        if (Map.class.isAssignableFrom(rawClass)) {
            if (typeArgs.length >= 1 && jsRhs.getMapKeyType() == null) {
                jsRhs.setMapKeyType(typeArgs[0]);
            }
            if (typeArgs.length >= 2) {
                jsRhs.setItemElementType(typeArgs[1]);
            }
            return;
        }

        if (Collection.class.isAssignableFrom(rawClass) && typeArgs.length >= 1) {
            jsRhs.setItemElementType(typeArgs[0]);
            return;
        }

        // For parameterized object types (non-Map, non-Collection), stamp immediate child JsonObjects
        // with resolved field types so traversal can continue incrementally.
        Map<String, Injector> fields = readOptions.getDeepInjectorMap(rawClass);
        if (fields.isEmpty()) {
            return;
        }

        for (Map.Entry<Object, Object> entry : jsRhs.entrySet()) {
            Object key = entry.getKey();
            if (!(key instanceof String)) {
                continue;
            }
            Injector childInjector = fields.get(key);
            if (childInjector == null) {
                continue;
            }

            Type childGenericType = childInjector.getGenericType();
            Type childResolvedType = TypeUtilities.resolveType(fieldType, childGenericType);
            if (TypeUtilities.hasUnresolvedType(childResolvedType)) {
                continue;
            }

            Object childValue = entry.getValue();
            if (childValue instanceof JsonObject) {
                JsonObject childJson = (JsonObject) childValue;
                if (childJson.getType() == null) {
                    childJson.setType(childResolvedType);
                }
                seedIncrementalContainerMetadata(childResolvedType, childJson);
            }
        }
    }

    // ========================================================================
    // End Field Assignment Helpers
    // ========================================================================

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
        final ReadOptionsBuilder.InjectorPlan injectorPlan = ReadOptionsBuilder.getInjectorPlan(readOptions, javaMate.getClass());

        // Enhanced for-loop is more efficient than iterator for EntrySet
        // Uses cached missingFieldHandler from parent Resolver for performance
        for (Map.Entry<Object, Object> entry : jsonObj.entrySet()) {
            String key = (String) entry.getKey();
            final Injector injector = injectorPlan.get(key);
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
     * Handles assignment by checking RHS type in order: null, reference, scalar, array, JsonObject.
     * Performance: Inlined to avoid per-field AssignmentContext allocation and strategy list iteration.
     *
     * @param jsonObj  a Map-of-Map representation of the current object being examined (containing all fields).
     * @param injector an instance of Injector used for setting values on the target object.
     * @param rhs      the JSON value that will be converted and stored in the field on the associated Java target object.
     */
    private void assignField(final JsonObject jsonObj, final Injector injector, final Object rhs) {
        final Object target = jsonObj.getTarget();
        final Type fieldType = injector.getGenericType();

        // 1. NULL - fastest check
        if (rhs == null) {
            Class<?> rawType = TypeUtilities.getRawClass(fieldType);
            injector.inject(target, rawType.isPrimitive() ? converter.convert(null, rawType) : null);
            return;
        }

        // 2. REFERENCE - check for @ref (only JsonObjects can be references)
        if (rhs instanceof JsonObject) {
            JsonObject jsObj = (JsonObject) rhs;
            if (jsObj.isReference()) {
                JsonObject refObject = resolveReference(jsObj);
                if (refObject != null && refObject.getTarget() != null) {
                    injector.inject(target, refObject.getTarget());
                } else {
                    addUnresolvedReference(new UnresolvedReference(
                            jsonObj, injector.getName(), jsObj.getReferenceId()));
                }
                return;
            }
        }

        // Scalar values (not JsonObject, not Object[]) - try direct assign or converter
        if (!(rhs instanceof JsonObject) && !(rhs instanceof Object[])) {
            final Class<?> rawType = TypeUtilities.getRawClass(fieldType);
            final Class<?> rhsClass = rhs.getClass();
            if (rawType != null) {
                // 3. DIRECT ASSIGN - already correct type
                if (rawType.isAssignableFrom(rhsClass)) {
                    injector.inject(target, rhs);
                    return;
                }
                // 3b. FAST NUMERIC INJECTION - Long→int, Double→float, etc.
                if (rhsClass == Long.class) {
                    if (injector.injectLong(target, (Long) rhs)) {
                        return;
                    }
                } else if (rhsClass == Double.class) {
                    if (injector.injectDouble(target, (Double) rhs)) {
                        return;
                    }
                }
                // 4. CONVERTER - scalar-to-scalar conversion for simple types
                if (isSimpleType(rawType) && converter.isSimpleTypeConversionSupported(rhsClass, rawType)) {
                    injector.inject(target, converter.convert(rhs, rawType));
                    return;
                }
            }
            // 7. FALLBACK for scalars - empty string to null, or direct inject
            if (rhs instanceof String && StringUtilities.isEmpty((String) rhs) && rawType != String.class) {
                injector.inject(target, null);
            } else {
                injector.inject(target, rhs);
            }
            return;
        }

        // 5. ARRAY - Object[] RHS
        if (rhs instanceof Object[]) {
            assignArrayField(jsonObj, injector, (Object[]) rhs, fieldType, target);
            return;
        }

        // 6. JSONOBJECT (non-reference, already checked above)
        assignJsonObjectField(jsonObj, injector, (JsonObject) rhs, fieldType, target);
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
        final boolean hasSpecificElementType = rawElementType != null && rawElementType != Object.class;
        final Class<?> rawNestedElementType = TypeUtilities.getRawClass(elementType);
        final boolean nestedElementIsCollectionType = rawNestedElementType != null
                && Collection.class.isAssignableFrom(rawNestedElementType);
        final boolean nestedElementIsArrayType = rawNestedElementType != null && rawNestedElementType.isArray();

        for (Object element : items) {
            // Strategy 1: NULL - fastest check
            if (element == null) {
                col.add(null);
                idx++;
                continue;
            }

            final Class<?> elementClass = element.getClass();
            final JsonObject jsonElement = element instanceof JsonObject ? (JsonObject) element : null;
            final boolean elementIsArray = elementClass.isArray();

            // Strategy 2: DIRECT ASSIGN - skip expensive factory/converter if already correct type
            // This is a major optimization for collections of simple types (List<String>, Set<UUID>, etc.)
            // IMPORTANT: Skip this fast path for:
            //   - JsonObject (needs instantiation/traversal)
            //   - Object[] (nested arrays need processing)
            //   - When element type is Object (could contain anything)
            if (hasSpecificElementType
                    && jsonElement == null
                    && !elementIsArray
                    && rawElementType.isAssignableFrom(elementClass)) {
                col.add(element);
                idx++;
                continue;
            }

            // Strategy 3: FAST PRIMITIVE COERCION - avoid readWithFactoryIfExists overhead
            if (hasSpecificElementType) {
                Object coerced = fastPrimitiveCoercion(element, elementClass, rawElementType);
                if (coerced != null) {
                    col.add(coerced);
                    idx++;
                    continue;
                }
            }

            // Strategy 4: REFERENCE - check before expensive factory lookup
            if (jsonElement != null && jsonElement.isReference()) {
                resolveReferenceInCollection(jsonElement, jsonObj, col, idx, isList);
                idx++;
                continue;
            }

            // Strategy 5: FACTORY/CONVERTER - handles custom readers, converters (String→Enum, etc.)
            Object special = readWithFactoryIfExists(element, rawElementType);
            if (special != null) {
                col.add(special);
                idx++;
                continue;
            }

            // Strategy 6: NESTED ARRAY - array within collection
            if (elementIsArray) {
                // Determine the type for the nested array/collection.
                // If elementType is a Collection type (e.g., List<User>), wrap as collection.
                // If elementType is an array type (e.g., User[]), create an actual array.
                if (nestedElementIsCollectionType) {
                    // elementType is a Collection (e.g., List<User>), use it directly
                    wrapArrayAndAddToCollection((Object[]) element, elementType, col);
                } else if (nestedElementIsArrayType) {
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
                idx++;
                continue;
            }

            // Strategy 7: JSONOBJECT - needs instantiation and traversal
            if (jsonElement != null) {
                // Set the element's full type to the extracted element type, but only if
                // the element doesn't already have a type set (e.g., from @type in the JSON).
                // This enables proper type inference for nested generic types when
                // deserializing JSON without @type markers.
                if (jsonElement.getType() == null) {
                    jsonElement.setType(elementType);
                }
                seedIncrementalContainerMetadata(elementType, jsonElement);
                createInstance(jsonElement);
                addResolvedObjectToCollection(jsonElement, col);
                idx++;
                continue;
            }

            // Strategy 8: FALLBACK - direct add
            col.add(element);
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

        // Extract key/value types - first check for stored value type (preserved before createInstance
        // changed the type to a raw Class), then fall back to extracting from ParameterizedType.
        Type keyType = jsonObj.getMapKeyType();
        if (keyType == null) {
            keyType = Object.class;
        }
        Type valueType = jsonObj.getItemElementType();  // For Maps, this stores the value type
        Type mapType = jsonObj.getType();
        if (mapType instanceof ParameterizedType) {
            Type[] typeArgs = ((ParameterizedType) mapType).getActualTypeArguments();
            if (typeArgs.length >= 1 && keyType == Object.class) {
                keyType = typeArgs[0];
            }
            if (valueType == null && typeArgs.length >= 2) {
                valueType = typeArgs[1];
            }
        }
        if (valueType == null) {
            valueType = Object.class;
        }
        if (TypeUtilities.hasUnresolvedType(keyType)) {
            keyType = Object.class;
        }
        if (TypeUtilities.hasUnresolvedType(valueType)) {
            valueType = Object.class;
        }

        Class<?> rawValueType = TypeUtilities.getRawClass(valueType);
        boolean valueIsCollection = rawValueType != null && Collection.class.isAssignableFrom(rawValueType);

        // Check if this is @keys/@items format (both explicitly set via setKeys/setItems)
        // vs standard entry format (entries added via put())
        boolean isKeysItemsFormat = jsonObj.getKeys() != null;

        if (isKeysItemsFormat) {
            // @keys/@items format - process arrays directly
            processMapKeysValues(existingKeys, existingItems, keyType, valueType, valueIsCollection);
        } else {
            // Standard entry format - process entries and convert values if needed
            processMapEntries(jsonObj, existingKeys, existingItems, keyType, valueType, valueIsCollection);
        }

        addMapToRehash(jsonObj);
    }

    /**
     * Process Map in @keys/@items format.
     */
    private void processMapKeysValues(Object[] keys, Object[] items, Type keyType, Type valueType, boolean valueIsCollection) {
        // Process keys
        JsonObject keysWrapper = new JsonObject();
        keysWrapper.setItems(keys);
        keysWrapper.setTarget(keys);
        keysWrapper.setItemElementType(keyType);
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
            // Set element type for Map values so traverseArray can convert them (e.g., String -> ZonedDateTime)
            itemsWrapper.setItemElementType(valueType);
            push(itemsWrapper);
        }
    }

    /**
     * Process Map in standard entry format, converting values to collections if needed.
     * Uses the pre-validated keys/items arrays from asTwoArrays().
     */
    private void processMapEntries(JsonObject jsonObj, Object[] keys, Object[] items, Type keyType, Type valueType, boolean valueIsCollection) {
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
            keysWrapper.setItemElementType(keyType);
            push(keysWrapper);
        } else {
            // Use default behavior - wrap and push arrays for traversal.
            // IMPORTANT: Must use the original arrays (not copies) because rehashMaps() modifies them in place.
            JsonObject keysWrapper = new JsonObject();
            keysWrapper.setItems(keys);
            keysWrapper.setTarget(keys);
            keysWrapper.setItemElementType(keyType);
            push(keysWrapper);

            JsonObject itemsWrapper = new JsonObject();
            itemsWrapper.setItems(items);
            itemsWrapper.setTarget(items);
            // Set element type for Map values so traverseArray can convert them (e.g., String -> ZonedDateTime)
            itemsWrapper.setItemElementType(valueType);
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
        // Check getItemElementType() first (set for Map values), then try extracting from array type.
        Type effectiveComponentType = jsonObj.getItemElementType();
        if (effectiveComponentType == null) {
            effectiveComponentType = TypeUtilities.extractArrayComponentType(jsonObj.getType());
        }
        if (effectiveComponentType == null) {
            effectiveComponentType = fallbackCompType;
        }
        // For operations that require a Class, extract the raw type.
        final Class<?> effectiveRawComponentType = TypeUtilities.getRawClass(effectiveComponentType);
        final boolean hasSpecificComponentType = effectiveRawComponentType != null
                && effectiveRawComponentType != Object.class;

        // Optimize: check array type ONCE, not on every element assignment
        final boolean isPrimitive = fallbackCompType.isPrimitive();
        final Object[] refArray = isPrimitive ? null : (Object[]) array;

        for (int i = 0; i < len; i++) {
            final Object element = jsonItems[i];

            // Strategy 1: NULL - fastest check
            if (element == null) {
                setArrayElement(array, refArray, i, null, isPrimitive);
                continue;
            }

            final Class<?> elementClass = element.getClass();
            final JsonObject jsonElement = element instanceof JsonObject ? (JsonObject) element : null;
            final boolean elementIsArray = elementClass.isArray();

            // Strategy 2: DIRECT ASSIGN - skip expensive factory/converter if already correct type
            // This is a major optimization for arrays of simple types (String[], Integer[], etc.)
            // IMPORTANT: Skip this fast path for:
            //   - JsonObject (needs instantiation/traversal)
            //   - Object[] (nested arrays need processing)
            //   - When component type is Object (could contain anything)
            if (hasSpecificComponentType
                    && jsonElement == null
                    && !elementIsArray
                    && effectiveRawComponentType.isAssignableFrom(elementClass)) {
                setArrayElement(array, refArray, i, element, isPrimitive);
                continue;
            }

            // Strategy 3: FAST PRIMITIVE COERCION - avoid readWithFactoryIfExists overhead
            // JSON only produces Long for integers and Double for decimals.
            // Convert directly to the target type without going through Converter lookup.
            if (hasSpecificComponentType) {
                if (isPrimitive && tryAssignParsedNumberToPrimitiveArray(array, i, fallbackCompType, element, elementClass)) {
                    continue;
                }
                Object coerced = fastPrimitiveCoercion(element, elementClass, effectiveRawComponentType);
                if (coerced != null) {
                    setArrayElement(array, refArray, i, coerced, isPrimitive);
                    continue;
                }
            }

            // Strategy 4: REFERENCE - check before expensive factory lookup
            if (jsonElement != null && jsonElement.isReference()) {
                Object resolved = resolveReferenceElement(jsonElement, jsonObj, i);
                if (resolved != UNRESOLVED_REFERENCE) {
                    setArrayElement(array, refArray, i, resolved, isPrimitive);
                }
                // If unresolved, the helper already added it to unresolved references
                continue;
            }

            // Strategy 5: FACTORY/CONVERTER - handles custom readers, converters (String→Enum, etc.)
            Object resolved = readWithFactoryIfExists(element, effectiveRawComponentType);
            if (resolved != null) {
                setArrayElement(array, refArray, i, resolved, isPrimitive);
                continue;
            }

            // Strategy 6: NESTED ARRAY - array of arrays
            if (elementIsArray) {
                if (char[].class == effectiveRawComponentType) {
                    setArrayElement(array, refArray, i, handleCharArrayElement((Object[]) element), isPrimitive);
                } else {
                    setArrayElement(array, refArray, i, handleNestedArrayElement((Object[]) element, effectiveComponentType), isPrimitive);
                }
                continue;
            }

            // Strategy 7: JSONOBJECT - needs instantiation and traversal
            if (jsonElement != null) {
                resolved = processJsonObjectElement(jsonElement, effectiveComponentType);
                setArrayElement(array, refArray, i, resolved, isPrimitive);
                continue;
            }

            // Strategy 8: FALLBACK - empty strings and direct assignment
            boolean isEmptyString = element instanceof String
                    && ((String) element).trim().isEmpty()
                    && effectiveRawComponentType != String.class
                    && effectiveRawComponentType != Object.class;
            setArrayElement(array, refArray, i, isEmptyString ? null : element, isPrimitive);
        }
        jsonObj.clear();
    }

    /**
     * {@inheritDoc}
     * <p>
     * ObjectResolver implementation tries strategies in order:
     * <ol>
     *   <li>Simple type conversion via Converter</li>
     *   <li>ClassFactory instantiation (+ population if isObjectFinal)</li>
     *   <li>JsonClassReader custom reading</li>
     * </ol>
     */
    protected Object readWithFactoryIfExists(final Object o, final Type inferredType) {
        Class<?> rawInferred = TypeUtilities.getRawClass(inferredType);

        // FAST PATH: Non-JsonObject values can often use Converter directly,
        // avoiding expensive JsonObject wrapper creation and factory/reader lookups.
        if (!(o instanceof JsonObject)) {
            Class<?> valueClass = o.getClass();

            // Fast path for primitives and Strings
            if (Primitives.isPrimitive(valueClass) || valueClass == String.class) {
                // Same type or assignable - no conversion needed
                if (rawInferred == null || rawInferred == valueClass || rawInferred.isAssignableFrom(valueClass)) {
                    return null;
                }
                // Type mismatch - try converter directly (skip factory/reader overhead)
                if (converter.isSimpleTypeConversionSupported(valueClass, rawInferred)) {
                    return converter.convert(o, rawInferred);
                }
                return null;
            }

            // Fast path: If target is Object or compatible, no conversion needed
            // Note: We do NOT use Converter for Object[] → typed array/collection here because
            // array elements may contain JsonObjects that need factory processing, not simple conversion.
            if (rawInferred == null || rawInferred == Object.class || rawInferred.isAssignableFrom(valueClass)) {
                return null;
            }
        }

        // Early exit: skip if custom reading is disabled for the inferred type
        if (readOptions.isNotCustomReaderClass(rawInferred)) {
            return null;
        }

        // Normalize input to JsonObject + targetClass
        JsonObject jsonObj = normalizeToJsonObject(o, rawInferred);
        if (jsonObj == null) {
            return null;
        }

        // If createInstance already fully handled this object, return the target directly
        if (jsonObj.isFinished()) {
            return jsonObj.getTarget();
        }

        Class<?> targetClass = jsonObj.getRawType();
        if (targetClass == null) {
            return null;
        }

        // Skip if custom reading is disabled for the resolved target type.
        // This check is needed here for cases where target already exists (normalizeToJsonObject
        // returns early). Also checked in normalizeToJsonObject() to avoid wasteful createInstance().
        if (targetClass != rawInferred && readOptions.isNotCustomReaderClass(targetClass)) {
            return null;
        }

        // Try each strategy in order
        Object result;
        if ((result = tryClassFactory(jsonObj, targetClass)) != null) {
            return result;
        }
        if ((result = tryCustomReader(o, jsonObj, targetClass)) != null) {
            return result;
        }

        return null;
    }

    /**
     * Normalize input to a JsonObject, handling both JsonObject and primitive inputs.
     * @return JsonObject wrapper, or null if input cannot be processed (e.g., reference)
     */
    private JsonObject normalizeToJsonObject(final Object o, final Class<?> rawInferred) {
        // Only process JsonObject inputs - non-JsonObject values are handled by the fast path
        if (!(o instanceof JsonObject)) {
            return null;
        }

        JsonObject jsonObj = (JsonObject) o;

        // References are resolved elsewhere
        if (jsonObj.isReference()) {
            return null;
        }

        // If target already exists, no initialization needed
        if (jsonObj.getTarget() != null) {
            return jsonObj;
        }

        // Insufficient type information to create instance
        Class<?> targetClass = jsonObj.getRawType();
        if (targetClass == null || rawInferred == null) {
            return null;
        }

        // Early exit: skip if custom reading is disabled for the resolved target type
        // This check MUST happen BEFORE createInstance() to avoid wasteful instantiation
        if (targetClass != rawInferred && readOptions.isNotCustomReaderClass(targetClass)) {
            return null;
        }

        // Attempt instance creation
        createInstance(jsonObj);
        return jsonObj;
    }

    /**
     * Try ClassFactory instantiation. Factory may also populate if isObjectFinal() returns true.
     */
    private Object tryClassFactory(final JsonObject jsonObj, final Class<?> targetClass) {
        ClassFactory classFactory = readOptions.getClassFactory(targetClass);
        if (classFactory != null && jsonObj.getTarget() == null) {
            Object target = createInstanceUsingClassFactory(targetClass, jsonObj);
            if (jsonObj.isFinished()) {
                return target;
            }
        }
        return null;
    }

    /**
     * Try JsonClassReader custom reading.
     */
    private Object tryCustomReader(final Object originalInput, final JsonObject jsonObj, final Class<?> targetClass) {
        JsonClassReader reader = readOptions.getCustomReader(targetClass);
        if (reader == null) {
            return null;
        }
        Object read = reader.read(originalInput, this);
        return (read != null) ? jsonObj.setFinishedTarget(read, true) : null;
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
        // Preserve explicit @type metadata on array elements (polymorphic arrays).
        if (jObj.getType() == null) {
            jObj.setType(componentType);
        }
        seedIncrementalContainerMetadata(componentType, jObj);
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
