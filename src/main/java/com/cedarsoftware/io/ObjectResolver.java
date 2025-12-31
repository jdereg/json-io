package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
    protected ObjectResolver(ReadOptions readOptions, ReferenceTracker references, Converter converter) {
        super(readOptions, references, converter);
    }

    /**
     * Walk the Java object fields and copy them from the JSON object to the Java object,
     * performing any necessary conversions on primitives or deep traversals for field assignments
     * to other objects, arrays, Collections, or Maps.
     *
     * @param jsonObj a Map-of-Map representation of the current object being examined (containing all fields).
     */
    public void traverseFields(final JsonObject jsonObj) {
        if (jsonObj.isFinished) {
            return;
        }
        jsonObj.setFinished();

        final Object javaMate = jsonObj.getTarget();
        final Class<?> cls = javaMate.getClass();
        final ReadOptions readOptions = getReadOptions();
        final Map<String, Injector> injectorMap = readOptions.getDeepInjectorMap(cls);
        final JsonReader.MissingFieldHandler missingFieldHandler = readOptions.getMissingFieldHandler();

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
            // Else: no handler so ignore.
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
        // Obtain the full generic type for the field.
        final Type fieldType = injector.getGenericType();
        // Compute the raw type for operations that require a Class (e.g., primitive checks).
        final Class<?> rawFieldType = TypeUtilities.getRawClass(fieldType);

        if (rhs == null) {   // Logically clear field
            if (rawFieldType.isPrimitive()) {
                injector.inject(target, getConverter().convert(null, rawFieldType));
            } else {
                injector.inject(target, null);
            }
            return;
        }

        // If there is a "tree" of objects (e.g., Map<String, List<Person>>), the sub-objects may not have a
        // @type (fullType) on them if the JSON source is from JSON.stringify(). Deep traverse the values and
        // assign the full generic type based on the parameterized type.
        if (rhs instanceof JsonObject) {
            if (fieldType instanceof ParameterizedType) {
                markUntypedObjects(fieldType, (JsonObject)rhs);
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
            final Long ref = jsRhs.getReferenceId();

            if (ref != null) {    // Handle field references.
                final JsonObject refObject = getReferences().getOrThrow(ref);
                if (refObject.getTarget() != null) {
                    injector.inject(target, refObject.getTarget());
                } else {
                    addUnresolvedReference(new UnresolvedReference(jsonObj, injector.getName(), ref));
                }
            } else {    // Direct assignment for nested objects.
                Object fieldObject = jsRhs.getTarget();
                injector.inject(target, fieldObject);
                boolean isNonRefClass = getReadOptions().isNonReferenceableClass(jsRhs.getRawType());
                if (!isNonRefClass) {
                    // If the object is reference-able, process it further.
                    push(jsRhs);
                }
            }
        } else {
            // For primitive conversions, e.g., allowing "" to null out a non-String field.
            if (rhs instanceof String && ((String) rhs).trim().isEmpty()
                    && rawFieldType != String.class) {
                injector.inject(target, null);
            } else {
                injector.inject(target, rhs);
            }
        }
    }

    /**
     * Try to create a java object from the missing field.
	 * Mostly primitive types and jsonObject that contains @type attribute will
	 * be candidate for the missing field callback, others will be ignored. 
	 * All missing field are stored for later notification
     *
     * @param jsonObj a Map-of-Map representation of the current object being examined (containing all fields).
     * @param rhs the JSON value that will be converted and stored in the 'field' on the associated Java target object.
     * @param missingField name of the missing field in the java object.
     */
    protected void handleMissingField(final JsonObject jsonObj, final Object rhs,
                                      final String missingField) {
        final Object target = jsonObj.getTarget();
        try {
            if (rhs == null) { // Logically clear field (allows null to be set against primitive fields, yielding their zero value.
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
                final Long ref = jObj.getReferenceId();

                if (ref != null) { // Correct field references
                    final JsonObject refObject = getReferences().getOrThrow(ref);
                    storeMissingField(target, missingField, refObject.getTarget());
                } else {   // Assign ObjectMap's to Object (or derived) fields
                    // check that jObj as a type
                    if (jObj.getType() != null) {
                        Object javaInstance = createInstance(jObj);
                        boolean isNonRefClass = getReadOptions().isNonReferenceableClass(jObj.getRawType());
                        if (!isNonRefClass && !jObj.isFinished) {
                            push((JsonObject) rhs);
                        }
                        storeMissingField(target, missingField, javaInstance);
                    } else { //no type found, just notify.
                        storeMissingField(target, missingField, null);
                    }
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
     * stores the missing field and their values to call back the handler at the end of the resolution, cause some
     * reference may need to be resolved later.
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
     * Process java.util.Collection and it's derivatives.  Collections are written specially
     * so that the serialization does not expose the Collection's internal structure, for
     * example, a TreeSet.  All entries are processed, except unresolved references, which
     * are filled in later.  For an index-able collection, the unresolved references are set
     * back into the proper element location.  For non-index-able collections (Sets), the
     * unresolved references are added via .add().
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     */
    protected void traverseCollection(final JsonObject jsonObj) {
        if (jsonObj.isFinished) {
            return;
        }
        jsonObj.setFinished();

        final Converter converter = getConverter();
        Object[] items = jsonObj.getItems();
        if (items == null) {
            return;
        }

        final Collection col = (Collection) jsonObj.getTarget();
        
        // Performance: Pre-size ArrayList to avoid repeated resizing
        if (col instanceof ArrayList) {
            ((ArrayList<?>) col).ensureCapacity(items.length);
        }
        
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
        final ReadOptions readOptions = getReadOptions();

        for (Object element : items) {
            if (element == null) {
                col.add(null);
                idx++;
                continue;
            }

            // Performance: Check common primitive/simple types first for fast path
            if (element instanceof String || element instanceof Boolean ||
                element instanceof Long || element instanceof Double) {
                col.add(element);
                idx++;
                continue;
            }
            
            // Pre-cache element class to avoid repeated getClass() calls
            final Class<?> elementClass = element.getClass();
            
            if (element instanceof JsonObject) {
                // Most common case after primitives - handle JsonObject
                JsonObject jObj = (JsonObject) element;
                final Long ref = jObj.getReferenceId();
                if (ref != null) {
                    JsonObject refObject = getReferences().getOrThrow(ref);
                    if (refObject.getTarget() != null) {
                        col.add(refObject.getTarget());
                    } else {
                        addUnresolvedReference(new UnresolvedReference(jsonObj, idx, ref));
                        if (isList) {
                            col.add(null);
                        }
                    }
                } else {
                    // Set the element's full type to the extracted element type.
                    jObj.setType(elementType);
                    createInstance(jObj);

                    boolean isNonRefClass = getReadOptions().isNonReferenceableClass(jObj.getRawType());
                    if (!isNonRefClass) {
                        traverseSpecificType(jObj);
                    }

                    if (!(col instanceof EnumSet)) {
                        col.add(jObj.getTarget());
                    }
                }
            } else if (elementClass.isArray()) {
                // For array elements inside the collection, use the helper to extract the array component type.
                JsonObject jObj = new JsonObject();
                Type arrayComponentType = TypeUtilities.extractArrayComponentType(elementType);
                if (arrayComponentType == null) {
                    arrayComponentType = Object.class;
                }
                jObj.setType(arrayComponentType);
                jObj.setItems((Object[]) element);
                createInstance(jObj);
                col.add(jObj.getTarget());
                push(jObj);
            } else {
                // Check for custom factory or converter support
                Object special = readWithFactoryIfExists(element, rawElementType);
                if (special != null) {
                    col.add(special);
                } else if (converter.isSimpleTypeConversionSupported(elementClass)) {
                    col.add(element);
                } else {
                    // Unexpected type - add as is
                    col.add(element);
                }
            }
            idx++;
        }
    }

    /**
     * Traverse the JsonObject associated to an array (of any type).  Convert and
     * assign the list of items in the JsonObject (stored in the @items field)
     * to each array element.  All array elements are processed excluding elements
     * that reference an unresolved object.  These are filled in later.
     *
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     */
    protected void traverseArray(final JsonObject jsonObj) {
        if (jsonObj.isFinished) {
            return;
        }
        jsonObj.setFinished();
        
        // Performance: Get items array once and use its length directly
        final Object[] jsonItems = jsonObj.getItems();
        if (ArrayUtilities.isEmpty(jsonItems)) {
            return;
        }
        final int len = jsonItems.length;
        final ReadOptions readOptions = getReadOptions();
        final ReferenceTracker refTracker = getReferences();
        final Object array = jsonObj.getTarget();

        // Get the raw component type from the array as a fallback.
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
                if (isPrimitive) {
                    ArrayUtilities.setPrimitiveElement(array, i, null);
                } else {
                    refArray[i] = null;
                }
            } else if ((special = readWithFactoryIfExists(element, effectiveRawComponentType)) != null) {
                if (isEnumComponentType && special instanceof String) {
                    special = Enum.valueOf(effectiveRawComponentType, (String) special);
                }
                if (isPrimitive) {
                    ArrayUtilities.setPrimitiveElement(array, i, special);
                } else {
                    refArray[i] = special;
                }
            } else if (element.getClass().isArray()) {   // Array of arrays
                if (char[].class == effectiveRawComponentType) {
                    // Special handling for char[] arrays.
                    Object[] jsonArray = (Object[]) element;
                    if (jsonArray.length == 0) {
                        if (isPrimitive) {
                            ArrayUtilities.setPrimitiveElement(array, i, new char[]{});
                        } else {
                            refArray[i] = new char[]{};
                        }
                    } else {
                        final char[] chars = ((String) jsonArray[0]).toCharArray();
                        if (isPrimitive) {
                            ArrayUtilities.setPrimitiveElement(array, i, chars);
                        } else {
                            refArray[i] = chars;
                        }
                    }
                } else {
                    JsonObject jsonArray = new JsonObject();
                    jsonArray.setItems((Object[])element);
                    // Set the full type using the effective component type.
                    jsonArray.setType(effectiveComponentType);
                    Object instance = createInstance(jsonArray);
                    if (isPrimitive) {
                        ArrayUtilities.setPrimitiveElement(array, i, instance);
                    } else {
                        refArray[i] = instance;
                    }
                    push(jsonArray);
                }
            } else if (element instanceof JsonObject) {
                JsonObject jsonElement = (JsonObject) element;
                Long ref = jsonElement.getReferenceId();

                if (ref != null) {
                    JsonObject refObject = refTracker.getOrThrow(ref);
                    if (refObject.getTarget() != null) {
                        if (isPrimitive) {
                            ArrayUtilities.setPrimitiveElement(array, i, refObject.getTarget());
                        } else {
                            refArray[i] = refObject.getTarget();
                        }
                    } else {
                        addUnresolvedReference(new UnresolvedReference(jsonObj, i, ref));
                    }
                } else {
                    // Set the full type on the element.
                    jsonElement.setType(effectiveComponentType);
                    Object arrayElement = createInstance(jsonElement);
                    if (isPrimitive) {
                        ArrayUtilities.setPrimitiveElement(array, i, arrayElement);
                    } else {
                        refArray[i] = arrayElement;
                    }
                    // Check for null before calling getClass() - can happen with arrays containing null values
                    if (arrayElement != null) {
                        boolean isNonRefClass = readOptions.isNonReferenceableClass(arrayElement.getClass());
                        if (!isNonRefClass && !jsonElement.isFinished) {
                            push(jsonElement);
                        }
                    }
                }
            } else {
                if (element instanceof String && ((String) element).trim().isEmpty()
                        && effectiveRawComponentType != String.class
                        && effectiveRawComponentType != Object.class) {
                    if (isPrimitive) {
                        ArrayUtilities.setPrimitiveElement(array, i, null);
                    } else {
                        refArray[i] = null;
                    }
                } else {
                    if (isPrimitive) {
                        ArrayUtilities.setPrimitiveElement(array, i, element);
                    } else {
                        refArray[i] = element;
                    }
                }
            }
        }
        jsonObj.clear();
    }

    /**
     * Convert the passed-in object (o) to a proper Java object. If the passed-in object (o) has a custom reader
     * associated to it, then have it convert the object. If there is no custom reader, then return null.
     *
     * @param o            Object to read (convert). This will be either a JsonObject or a JSON primitive (String, long,
     *                     boolean, double, or null).
     * @param inferredType The full target Type (including generics) to which 'o' should be converted.
     * @return The Java object converted from the passed-in object o, or null if there is no custom reader.
     */
    protected Object readWithFactoryIfExists(final Object o, final Type inferredType) {
        Convention.throwIfNull(o, "Bug in json-io, null must be checked before calling this method.");
        final ReadOptions readOptions = getReadOptions();
        final Converter converter = getConverter();

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

        // Simple type conversion if possible.
        if (jsonObj.getTarget() == null && jsonObj.hasValue()) {
            Object value = jsonObj.getValue();
            if (converter.isSimpleTypeConversionSupported(value.getClass(), targetClass)) {
                Object converted = converter.convert(value, targetClass);
                return jsonObj.setFinishedTarget(converted, true);
            }
        }

        // Try custom class factory.
        JsonReader.ClassFactory classFactory = readOptions.getClassFactory(targetClass);
        if (classFactory != null && jsonObj.getTarget() == null) {
            Object target = createInstanceUsingClassFactory(targetClass, jsonObj);
            if (jsonObj.isFinished()) {
                return target;
            }
        }

        // Finally, try a custom reader.
        JsonReader.JsonClassReader reader = readOptions.getCustomReader(targetClass);
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

        // Use Map.Entry for type-safe pairing of Type and instance (cleaner than Object[])
        final Deque<Map.Entry<Type, Object>> stack = new ArrayDeque<>();
        Class<?> fieldClass = TypeUtilities.getRawClass(type);
        Map<String, Injector> classFields = getReadOptions().getDeepInjectorMap(fieldClass);
        stack.addFirst(new AbstractMap.SimpleEntry<>(type, rhs));

        while (!stack.isEmpty()) {
            Map.Entry<Type, Object> item = stack.removeFirst();
            final Type t = item.getKey();
            final Object instance = item.getValue();

            if (instance == null) {
                continue;
            }

            // OPTIMIZATION: Skip already-processed JsonObjects to avoid redundant work
            // This is critical for object graphs with shared references
            if (instance instanceof JsonObject && ((JsonObject) instance).isFinished) {
                continue;  // Already marked - skip to avoid duplicate traversal
            }

            if (t instanceof ParameterizedType) {
                handleParameterizedTypeMarking((ParameterizedType) t, instance, type, classFields, stack);
            } else {
                stampTypeOnJsonObject(instance, t);
            }
        }
    }

    /**
     * Handles type marking for parameterized types (List<T>, Map<K,V>, etc.)
     */
    private void handleParameterizedTypeMarking(
            final ParameterizedType pType,
            final Object instance,
            final Type parentType,
            final Map<String, Injector> classFields,
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
            handleObjectFieldsMarking(instance, pType, typeArgs, classFields, stack);
        }
    }

    /**
     * Handles Map<K,V> type marking by processing keys and values
     */
    private void handleMapTypeMarking(
            final Object instance,
            final Type[] typeArgs,
            final Deque<Map.Entry<Type, Object>> stack) {

        JsonObject jsonObj = (JsonObject) instance; // Maps are brought in as JsonObjects
        Map.Entry<Object[], Object[]> pair = jsonObj.asTwoArrays();
        Object[] keys = pair.getKey();
        Object[] values = pair.getValue();
        addItemsToStack(stack, keys, typeArgs[0]);
        addItemsToStack(stack, values, typeArgs[1]);
    }

    /**
     * Handles Collection<T> type marking for arrays, Collections, and JsonObjects
     */
    private void handleCollectionTypeMarking(
            final Object instance,
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
            final Collection<?> col = (Collection<?>) instance;
            for (Object o : col) {
                stack.addFirst(new AbstractMap.SimpleEntry<>(typeArgs[0], o));
            }
        } else if (instance instanceof JsonObject) {
            // OPTIMIZATION: Skip if array items don't need field traversal
            if (shouldSkipTraversal(typeArgs[0])) {
                return;
            }
            final JsonObject jObj = (JsonObject) instance;
            final Object[] array = jObj.getItems();
            if (array != null) {
                for (Object o : array) {
                    stack.addFirst(new AbstractMap.SimpleEntry<>(typeArgs[0], o));
                }
            }
        }
    }

    /**
     * Handles nested arrays within collections (e.g., int[][], String[][])
     */
    private void handleArrayInCollection(
            final Object arrayInstance,
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
     * Handles regular object field type marking (non-Map, non-Collection)
     */
    private void handleObjectFieldsMarking(
            final Object instance,
            final Type containerType,
            final Type[] typeArgs,
            final Map<String, Injector> classFields,
            final Deque<Map.Entry<Type, Object>> stack) {

        if (!(instance instanceof JsonObject)) {
            return;
        }

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
        JsonReader.ClassFactory factory = getReadOptions().getClassFactory(rawClass);
        return factory != null && factory.isObjectFinal();
    }

    /**
     * Helper method to add array items to the stack with their type
     */
    private void addItemsToStack(
            final Deque<Map.Entry<Type, Object>> stack,
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
            stack.add(new AbstractMap.SimpleEntry<>(itemType, items));
        } else {
            // Add each item individually
            for (Object item : items) {
                stack.add(new AbstractMap.SimpleEntry<>(itemType, item));
            }
        }
    }

    // Mark 'type' on JsonObject when the type is missing and is a 'leaf'
    // node (no further subtypes in it's parameterized type definition)
    private static void stampTypeOnJsonObject(final Object o, final Type t) {
        if (o instanceof JsonObject && t != null) {
            JsonObject jObj = (JsonObject) o;
            if (jObj.getType() == null) {
                jObj.type = t;  // By-pass setter because it could throw an unresolved type exception and we don't have the full type.
            }
        }
    }

    protected Object resolveArray(Type suggestedType, List<Object> list) {
        // If no suggested type is provided or its raw type is Object, simply return an Object[]
        if (suggestedType == null || TypeUtilities.getRawClass(suggestedType) == Object.class) {
            return list.toArray();
        }

        JsonObject jsonArray = new JsonObject();
        // Store the full refined type in the JsonObject.
        jsonArray.setType(suggestedType);

        // Extract the underlying raw class to use for reflection operations.
        Class<?> rawType = TypeUtilities.getRawClass(suggestedType);

        // If the raw type is a Collection, create an instance accordingly.
        if (Collection.class.isAssignableFrom(rawType)) {
            jsonArray.setTarget(createInstance(jsonArray));
        } else {
            // Otherwise assume an array type and create a new array with the appropriate length.
            jsonArray.setTarget(Array.newInstance(rawType, list.size()));
        }
        jsonArray.setItems(list.toArray());
        return jsonArray;
    }
}