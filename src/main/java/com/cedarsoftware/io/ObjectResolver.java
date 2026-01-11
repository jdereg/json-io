package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.io.reflect.Injector;
import com.cedarsoftware.util.ArrayUtilities;
import com.cedarsoftware.util.Convention;
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
 * and field type are not the same and therefore it writes the @type.
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
        final MissingFieldHandler missingFieldHandler = readOptions.getMissingFieldHandler();

        // Enhanced for-loop is more efficient than iterator for EntrySet
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
        } else if (rhs.getClass().isArray()) {
            // If the RHS is an array, wrap it in a JsonObject and stamp it with the full field type.
            final Object[] elements = (Object[]) rhs;
            JsonObject jsonArray = new JsonObject();
            jsonArray.setType(fieldType);
            jsonArray.setItems(elements);
            createInstance(jsonArray);
            injector.inject(target, jsonArray.getTarget());
            push(jsonArray);
        } else if (rhs instanceof JsonObject) {
            final JsonObject jsRhs = (JsonObject) rhs;

            if (jsRhs.isReference()) {    // Handle field references.
                final long ref = jsRhs.getReferenceId();
                final JsonObject refObject = references.getOrThrow(ref);
                if (refObject.getTarget() != null) {
                    injector.inject(target, refObject.getTarget());
                } else {
                    addUnresolvedReference(new UnresolvedReference(jsonObj, injector.getName(), ref));
                }
            } else {    // Direct assignment for nested objects.
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
            if (rhs instanceof String && ((String) rhs).trim().isEmpty() && rawFieldType != String.class) {
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

            // we have a jsonObject with a type
            Object special;
            if ((special = readWithFactoryIfExists(rhs, null)) != null) {
                storeMissingField(target, missingField, special);
            } else if (rhs.getClass().isArray()) {
                // impossible to determine the array type.
                storeMissingField(target, missingField, null);
            } else if (rhs instanceof JsonObject) {
                final JsonObject jObj = (JsonObject) rhs;

                if (jObj.isReference()) {
                    final long ref = jObj.getReferenceId();
                    final JsonObject refObject = references.getOrThrow(ref);
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
        addMissingField(new Missingfields(target, missingField, value));
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

        // Extract the element type from the full type, defaulting to Object if not set.
        Type fullType = jsonObj.getType();
        Type elementType = Object.class;
        if (fullType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) fullType;
            Type[] typeArgs = pt.getActualTypeArguments();
            if (typeArgs.length > 0) {
                elementType = typeArgs[0];
            }
        }

        // Pre-compute raw element type to avoid repeated calls in loop
        final Class<?> rawElementType = TypeUtilities.getRawClass(elementType);

        for (Object element : items) {
            if (element == null) {
                col.add(null);
                idx++;
                continue;
            }

            // Fast path for native JSON types (includes BigInteger/BigDecimal from JsonParser)
            if (isDirectlyAddableJsonValue(element)) {
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
                    // Set the element's full type to the extracted element type.
                    jObj.setType(elementType);
                    createInstance(jObj);
                    addResolvedObjectToCollection(jObj, col);
                }
            } else if (elementClass.isArray()) {
                // For array elements inside the collection, use the helper to extract the array component type.
                Type arrayComponentType = TypeUtilities.extractArrayComponentType(elementType);
                if (arrayComponentType == null) {
                    arrayComponentType = Object.class;
                }
                wrapArrayAndAddToCollection((Object[]) element, arrayComponentType, col);
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
        final boolean isEnumComponentType = effectiveRawComponentType.isEnum();

        // Optimize: check array type ONCE, not on every element assignment
        final boolean isPrimitive = fallbackCompType.isPrimitive();
        final Object[] refArray = isPrimitive ? null : (Object[]) array;

        for (int i = 0; i < len; i++) {
            final Object element = jsonItems[i];
            Object special;

            if (element == null) {
                setArrayElement(array, refArray, i, null, isPrimitive);
            } else if ((special = readWithFactoryIfExists(element, effectiveRawComponentType)) != null) {
                if (isEnumComponentType && special instanceof String) {
                    special = Enum.valueOf(effectiveRawComponentType, (String) special);
                }
                setArrayElement(array, refArray, i, special, isPrimitive);
            } else if (element.getClass().isArray()) {
                // Array of arrays
                if (char[].class == effectiveRawComponentType) {
                    // Special handling for char[] arrays.
                    Object[] jsonArray = (Object[]) element;
                    char[] chars = jsonArray.length == 0 ? new char[]{} : ((String) jsonArray[0]).toCharArray();
                    setArrayElement(array, refArray, i, chars, isPrimitive);
                } else {
                    JsonObject jsonArray = new JsonObject();
                    jsonArray.setItems((Object[]) element);
                    jsonArray.setType(effectiveComponentType);
                    Object instance = createInstance(jsonArray);
                    setArrayElement(array, refArray, i, instance, isPrimitive);
                    push(jsonArray);
                }
            } else if (element instanceof JsonObject) {
                JsonObject jsonElement = (JsonObject) element;

                if (jsonElement.isReference()) {
                    long ref = jsonElement.getReferenceId();
                    JsonObject refObject = references.getOrThrow(ref);
                    if (refObject.getTarget() != null) {
                        setArrayElement(array, refArray, i, refObject.getTarget(), isPrimitive);
                    } else {
                        addUnresolvedReference(new UnresolvedReference(jsonObj, i, ref));
                    }
                } else {
                    jsonElement.setType(effectiveComponentType);
                    Object arrayElement = createInstance(jsonElement);
                    setArrayElement(array, refArray, i, arrayElement, isPrimitive);
                    if (arrayElement != null
                            && !readOptions.isNonReferenceableClass(arrayElement.getClass())
                            && !jsonElement.isFinished) {
                        push(jsonElement);
                    }
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

        // Early exit if no raw type available
        if (rawInferred == null && !(o instanceof JsonObject)) {
            return null;
        }

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
            // o is not a JsonObject; use the inferred type (or o.getClass() if rawInferred is Object).
            targetClass = rawInferred.equals(Object.class) ? o.getClass() : rawInferred;
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
        final Set<JsonObject> visited = Collections.newSetFromMap(new IdentityHashMap<>());
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
     * Handles nested arrays within collections (e.g., int[][], String[][]).
     */
    private void handleArrayInCollection(final Object arrayInstance,
                                          final Type containerType,
                                          final Class<?> collectionClass,
                                          final Deque<Map.Entry<Type, Object>> stack) {
        int len = ArrayUtilities.getLength(arrayInstance);
        for (int i = 0; i < len; i++) {
            Object element = ArrayUtilities.getElement(arrayInstance, i);
            if (element == null) {
                continue;
            }

            // Handle nested array (e.g., element is int[] within int[][])
            if (element.getClass().isArray()) {
                // Convert the inner array to a List for type resolution
                int innerLen = ArrayUtilities.getLength(element);
                List<Object> items = new ArrayList<>(innerLen);
                for (int j = 0; j < innerLen; j++) {
                    items.add(ArrayUtilities.getElement(element, j));
                }

                JsonObject coll = new JsonObject();
                coll.setType(collectionClass);
                coll.setItems((Object[]) element);
                stack.addFirst(new AbstractMap.SimpleEntry<>(containerType, items));
                ArrayUtilities.setElement(arrayInstance, i, coll);
            } else {
                stack.addFirst(new AbstractMap.SimpleEntry<>(containerType, element));
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
            return false;
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

        Class<?> rawType = TypeUtilities.getRawClass(itemType);
        if (rawType != null && Collection.class.isAssignableFrom(rawType)) {
            // Treat the entire array as a collection
            stack.addFirst(new AbstractMap.SimpleEntry<>(itemType, items));
        } else {
            // Iterate in reverse to preserve order after addFirst
            for (int i = items.length - 1; i >= 0; i--) {
                stack.addFirst(new AbstractMap.SimpleEntry<>(itemType, items[i]));
            }
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

        if (Collection.class.isAssignableFrom(rawType)) {
            return createAndPopulateCollection(suggestedType, list);
        } else if (rawType.isArray()) {
            return createAndPopulateArray(suggestedType, rawType.getComponentType(), list);
        } else {
            // Not a collection or array type - return Object[]
            return list.toArray();
        }
    }

    /**
     * Create a typed array and populate it with elements from the list.
     * This avoids the double allocation of creating both a target array and items array.
     * Returns the finished array directly, or a JsonObject wrapper if forward references exist.
     */
    private Object createAndPopulateArray(Type arrayType, Class<?> componentType, List<Object> list) {
        // Special handling for char[] - stored as a single String in JSON
        if (componentType == char.class) {
            if (list.isEmpty()) {
                return new char[0];
            }
            Object first = list.get(0);
            if (first instanceof String) {
                return ((String) first).toCharArray();
            }
        }

        int size = list.size();
        Object array = Array.newInstance(componentType, size);
        boolean hasUnresolvedRefs = false;
        List<UnresolvedArrayElement> unresolvedElements = null;

        for (int i = 0; i < size; i++) {
            Object element = list.get(i);

            if (element == null) {
                Array.set(array, i, null);
                continue;
            }

            // Handle nested arrays: Object[] -> String[][] etc.
            if (element instanceof Object[] && componentType.isArray()) {
                Type nestedComponentType = TypeUtilities.extractArrayComponentType(arrayType);
                element = createAndPopulateArray(nestedComponentType, componentType.getComponentType(),
                        java.util.Arrays.asList((Object[]) element));
                Array.set(array, i, element);
                continue;
            }

            // Try to extract value from JsonObject
            Object resolved = extractArrayElementValue(element, componentType);

            if (resolved == UNRESOLVED_REFERENCE) {
                // Forward reference - need to defer resolution (element must be JsonObject per extractArrayElementValue logic)
                hasUnresolvedRefs = true;
                if (unresolvedElements == null) {
                    unresolvedElements = new ArrayList<>();
                }
                JsonObject refHolder = (element instanceof JsonObject) ? (JsonObject) element : null;
                if (refHolder != null) {
                    unresolvedElements.add(new UnresolvedArrayElement(i, refHolder));
                }
                continue;
            }

            if (resolved instanceof JsonObject) {
                JsonObject jObj = (JsonObject) resolved;
                // Complex object that needs further resolution
                jObj.setType(TypeUtilities.extractArrayComponentType(arrayType));
                createInstance(jObj);
                Object target = jObj.getTarget();
                Array.set(array, i, target);
                if (!jObj.isFinished) {
                    push(jObj);
                }
                continue;
            }

            // Convert if needed
            if (resolved != null && !componentType.isAssignableFrom(resolved.getClass())) {
                if (componentType.isEnum() && resolved instanceof String) {
                    resolved = Enum.valueOf((Class<Enum>) componentType, (String) resolved);
                } else if (converter.isConversionSupportedFor(resolved.getClass(), componentType)) {
                    resolved = converter.convert(resolved, componentType);
                }
            }

            Array.set(array, i, resolved);
        }

        // If we have forward references, add unresolved references to be patched later
        if (hasUnresolvedRefs && unresolvedElements != null) {
            // Create a JsonObject wrapper just to track the array for reference patching
            JsonObject jsonArray = new JsonObject();
            jsonArray.setType(arrayType);
            jsonArray.setTarget(array);
            jsonArray.isFinished = true;  // Mark as finished since array is populated

            for (UnresolvedArrayElement unresolved : unresolvedElements) {
                addUnresolvedReference(new UnresolvedReference(jsonArray, unresolved.index,
                        unresolved.refHolder.getReferenceId()));
            }
            return jsonArray;
        }

        return array;
    }

    /**
     * Sentinel value indicating an unresolved forward reference.
     */
    private static final Object UNRESOLVED_REFERENCE = new Object();

    /**
     * Helper class to track unresolved array elements.
     */
    private static class UnresolvedArrayElement {
        final int index;
        final JsonObject refHolder;

        UnresolvedArrayElement(int index, JsonObject refHolder) {
            this.index = index;
            this.refHolder = refHolder;
        }
    }

    /**
     * Extract the actual value from an array element.
     * Returns UNRESOLVED_REFERENCE if this is a forward reference that can't be resolved yet.
     * Returns the JsonObject unchanged if it needs further processing.
     * Otherwise returns the resolved value.
     */
    private Object extractArrayElementValue(Object element, Class<?> componentType) {
        if (!(element instanceof JsonObject)) {
            return element;
        }

        JsonObject jObj = (JsonObject) element;

        // Handle references
        if (jObj.isReference()) {
            long refId = jObj.getReferenceId();
            JsonObject refObj = references.get(refId);
            if (refObj != null && refObj.getTarget() != null) {
                return refObj.getTarget();
            }
            // Forward reference - can't resolve yet
            return UNRESOLVED_REFERENCE;
        }

        // If we have a resolved target, use it
        if (jObj.getTarget() != null) {
            if (!jObj.isFinished) {
                push(jObj);
            }
            return jObj.getTarget();
        }

        // Check if this is a simple value that can be extracted directly
        if (jObj.hasValue()) {
            Object value = jObj.getValue();
            if (componentType.isAssignableFrom(value.getClass())) {
                return value;
            }
            if (converter.isConversionSupportedFor(value.getClass(), componentType)) {
                return converter.convert(value, componentType);
            }
        }

        // Return the JsonObject for further processing
        return jObj;
    }

    /**
     * Create a collection and populate it with elements from the list.
     */
    @SuppressWarnings("unchecked")
    private Object createAndPopulateCollection(Type suggestedType, List<Object> list) {
        // Create the collection instance
        JsonObject jsonArray = new JsonObject();
        jsonArray.setType(suggestedType);
        Collection<Object> collection = (Collection<Object>) createInstance(jsonArray);

        // Get the element type for conversion
        Type elementType = Object.class;
        if (suggestedType instanceof java.lang.reflect.ParameterizedType) {
            java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) suggestedType;
            Type[] typeArgs = pt.getActualTypeArguments();
            if (typeArgs.length > 0) {
                elementType = typeArgs[0];
            }
        }
        Class<?> rawElementType = TypeUtilities.getRawClass(elementType);

        // Track if we need to defer to traverseCollection
        boolean needsTraversal = false;

        for (Object item : list) {
            if (item == null) {
                collection.add(null);
                continue;
            }

            if (item instanceof JsonObject) {
                JsonObject jObj = (JsonObject) item;
                if (jObj.isReference()) {
                    // Has references - needs traversal
                    needsTraversal = true;
                    break;
                }
                if (jObj.getTarget() == null && jObj.getType() == null) {
                    jObj.setType(elementType);
                }
                if (jObj.getTarget() == null) {
                    createInstance(jObj);
                }
                if (!jObj.isFinished) {
                    needsTraversal = true;
                    break;
                }
                collection.add(jObj.getTarget());
            } else if (item.getClass().isArray()) {
                needsTraversal = true;
                break;
            } else {
                // Direct value
                if (rawElementType != Object.class && !rawElementType.isAssignableFrom(item.getClass())) {
                    if (converter.isConversionSupportedFor(item.getClass(), rawElementType)) {
                        item = converter.convert(item, rawElementType);
                    }
                }
                collection.add(item);
            }
        }

        if (needsTraversal) {
            // Fall back to JsonObject wrapper for complex cases
            collection.clear();
            jsonArray.setTarget(collection);
            jsonArray.setItems(list.toArray());
            return jsonArray;
        }

        return collection;
    }
}