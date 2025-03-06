package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.io.reflect.Injector;
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
        final Iterator<Map.Entry<Object, Object>> i = jsonObj.entrySet().iterator();
        final Class<?> cls = javaMate.getClass();
        ReadOptions readOptions = getReadOptions();
        final Map<String, Injector> injectorMap = readOptions.getDeepInjectorMap(cls);

        while (i.hasNext()) {
            Map.Entry<Object, Object> entry = i.next();
            String key = (String) entry.getKey();
            final Injector injector = injectorMap.get(key);
            Object rhs = entry.getValue();
            if (injector != null) {
                assignField(jsonObj, injector, rhs);
            } else if (readOptions.getMissingFieldHandler() != null) {
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
                    unresolvedRefs.add(new UnresolvedReference(jsonObj, injector.getName(), ref));
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
        missingFields.add(new Missingfields(target, missingField, value));
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
        final Collection col = (Collection) jsonObj.getTarget();
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

        if (items == null) {
            return;
        }

        Class<?> elementClass;
        for (Object element : items) {
            Object special;
            elementClass = element == null ? null : element.getClass();
            if (element == null) {
                col.add(null);
            } else if ((special = readWithFactoryIfExists(element, TypeUtilities.getRawClass(elementType))) != null) {
                // Use custom converter if available.
                col.add(special);
            } else if (converter.isSimpleTypeConversionSupported(elementClass, elementClass)) {
                // Simple types: add as is.
                col.add(element);
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
                // Otherwise, assume the element is a JsonObject.
                JsonObject jObj = (JsonObject) element;
                final Long ref = jObj.getReferenceId();
                if (ref != null) {
                    JsonObject refObject = getReferences().getOrThrow(ref);
                    if (refObject.getTarget() != null) {
                        col.add(refObject.getTarget());
                    } else {
                        unresolvedRefs.add(new UnresolvedReference(jsonObj, idx, ref));
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
        final int len = jsonObj.size();
        if (len == 0) {
            return;
        }

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
        final Object[] jsonItems = jsonObj.getItems();

        for (int i = 0; i < len; i++) {
            final Object element = jsonItems[i];
            Object special;

            if (element == null) {
                setArrayElement(array, i, null);
            } else if ((special = readWithFactoryIfExists(element, effectiveRawComponentType)) != null) {
                if (effectiveRawComponentType.isEnum() && special instanceof String) {
                    special = Enum.valueOf(effectiveRawComponentType, (String) special);
                }
                setArrayElement(array, i, special);
            } else if (element.getClass().isArray()) {   // Array of arrays
                if (char[].class == effectiveRawComponentType) {
                    // Special handling for char[] arrays.
                    Object[] jsonArray = (Object[]) element;
                    if (jsonArray.length == 0) {
                        setArrayElement(array, i, new char[]{});
                    } else {
                        final char[] chars = ((String) jsonArray[0]).toCharArray();
                        setArrayElement(array, i, chars);
                    }
                } else {
                    JsonObject jsonArray = new JsonObject();
                    jsonArray.setItems((Object[])element);
                    // Set the full type using the effective component type.
                    jsonArray.setType(effectiveComponentType);
                    setArrayElement(array, i, createInstance(jsonArray));
                    push(jsonArray);
                }
            } else if (element instanceof JsonObject) {
                JsonObject jsonElement = (JsonObject) element;
                Long ref = jsonElement.getReferenceId();

                if (ref != null) {
                    JsonObject refObject = refTracker.getOrThrow(ref);
                    if (refObject.getTarget() != null) {
                        setArrayElement(array, i, refObject.getTarget());
                    } else {
                        unresolvedRefs.add(new UnresolvedReference(jsonObj, i, ref));
                    }
                } else {
                    // Set the full type on the element.
                    jsonElement.setType(effectiveComponentType);
                    Object arrayElement = createInstance(jsonElement);
                    setArrayElement(array, i, arrayElement);
                    boolean isNonRefClass = readOptions.isNonReferenceableClass(arrayElement.getClass());
                    if (!isNonRefClass && !jsonElement.isFinished) {
                        push(jsonElement);
                    }
                }
            } else {
                if (element instanceof String && ((String) element).trim().isEmpty()
                        && effectiveRawComponentType != String.class
                        && effectiveRawComponentType != Object.class) {
                    setArrayElement(array, i, null);
                } else {
                    setArrayElement(array, i, element);
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

    private void markUntypedObjects(final Type type, final JsonObject rhs) {
        if (rhs.isFinished) {
            return;     // Already marked.
        }
        final Deque<Object[]> stack = new ArrayDeque<>();
        Class<?> fieldClass = TypeUtilities.getRawClass(type);
        Map<String, Injector> classFields = getReadOptions().getDeepInjectorMap(fieldClass);
        stack.addFirst(new Object[]{type, rhs});

        while (!stack.isEmpty()) {
            Object[] item = stack.removeFirst();
            final Type t = (Type) item[0];
            final Object instance = item[1];
            
            if (instance == null) {
                continue;
            }
            
            if (t instanceof ParameterizedType) {
                Class<?> clazz = TypeUtilities.getRawClass(t);
                ParameterizedType pType = (ParameterizedType) t;
                Type[] typeArgs = pType.getActualTypeArguments();

                if (typeArgs.length < 1 || clazz == null) {
                    continue;
                }

                // Assuming 'type' is the parent's type and 't' is the field type that needs resolution:
                Type resolvedType = TypeUtilities.resolveType(type, t);
                stampTypeOnJsonObject(instance, resolvedType);

                if (Map.class.isAssignableFrom(clazz)) {
                    JsonObject jsonObj = (JsonObject) instance; // Maps are brought in as JsonObjects
                    Map.Entry<Object[], Object[]> pair = jsonObj.asTwoArrays();
                    Object[] keys = pair.getKey();
                    Object[] items = pair.getValue();
                    getTemplateTraverseWorkItem(stack, keys, typeArgs[0]);
                    getTemplateTraverseWorkItem(stack, items, typeArgs[1]);
                } else if (Collection.class.isAssignableFrom(clazz)) {
                    if (instance.getClass().isArray()) {
                        int len = Array.getLength(instance);
                        for (int i = 0; i < len; i++) {
                            Object vals = Array.get(instance, i);
                            if (vals == null) {
                                continue;
                            }

                            // Check if 'vals' is an array of any type
                            if (vals.getClass().isArray()) {
                                // Convert the inner array to a List in a type-agnostic way
                                int innerLen = Array.getLength(vals);
                                List<Object> items = new ArrayList<>(innerLen);
                                for (int j = 0; j < innerLen; j++) {
                                    items.add(Array.get(vals, j));
                                }

                                JsonObject coll = new JsonObject();
                                coll.setType(clazz);
                                coll.setItems((Object[])vals);
                                stack.addFirst(new Object[]{t, items});
                                Array.set(instance, i, coll);
                            } else {
                                stack.addFirst(new Object[]{t, vals});
                            }
                        }
                    } else if (instance instanceof Collection) {
                        final Collection col = (Collection) instance;
                        for (Object o : col) {
                            stack.addFirst(new Object[]{typeArgs[0], o});
                        }
                    } else if (instance instanceof JsonObject) {
                        final JsonObject jObj = (JsonObject) instance;
                        final Object[] array = jObj.getItems();
                        if (array != null) {
                            for (Object o : array) {
                                stack.addFirst(new Object[]{typeArgs[0], o});
                            }
                        }
                    }
                } else {
                    if (instance instanceof JsonObject) {
                        final JsonObject jObj = (JsonObject) instance;

                        for (Map.Entry<Object, Object> entry : jObj.entrySet()) {
                            final String fieldName = (String) entry.getKey();
                            if (fieldName.startsWith("this$")) {
                                continue;
                            }
                            Injector injector = classFields.get(fieldName);
                            if (injector != null) {
                                Type genericType = injector.getGenericType();
                                // Resolve the field's type using the parent type 't'
                                Type resolved = TypeUtilities.resolveType(t, genericType);

                                // If resolution didn't fully resolve the type, use a fallback
                                if (TypeUtilities.hasUnresolvedType(resolved)) {
                                    resolved = typeArgs[0];  // fallback: use the first type argument
                                }

                                stack.addFirst(new Object[]{ resolved, entry.getValue() });
                            }
                        }
                    }
                }
            } else {
                stampTypeOnJsonObject(instance, t);
            }
        }
    }

    private static void getTemplateTraverseWorkItem(final Deque<Object[]> stack, final Object[] items, final Type type) {
        if (items == null || items.length < 1) {
            return;
        }
        Class<?> rawType = TypeUtilities.getRawClass(type);
        if (rawType != null && Collection.class.isAssignableFrom(rawType)) {
            stack.add(new Object[]{type, items});
        } else {
            for (Object item : items) {
                stack.add(new Object[]{type, item});
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