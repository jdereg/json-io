package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.io.reflect.Injector;
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

    protected Object readWithFactoryIfExists(Object o, Class<?> compType) {
        // No custom reader support for maps
        return null;
    }

    /**
     * Walk the JsonObject fields and perform necessary substitutions so that all references matched up.
     * This code patches @ref and @id pairings up, in the 'Map of Map' mode.  Where the JSON may contain
     * an '@id' of an object which can have more than one @ref to it, this code will make sure that each
     * '@ref' (value side of the Map associated to a given field name) will be pointer to the appropriate Map
     * instance.
     * @param jsonObj a Map-of-Map representation of the current object being examined (containing all fields).
     */
    public void traverseFields(final JsonObject jsonObj) {
        final Object target = jsonObj.getTarget();
        final Map<String, Injector> injectorMap = (target == null) ? null : getReadOptions().getDeepInjectorMap(target.getClass());
        final ReferenceTracker refTracker = getReferences();
        final Converter converter = getConverter();
        final ReadOptions readOptions = getReadOptions();

        for (Map.Entry<Object, Object> e : jsonObj.entrySet()) {
            final String fieldName = (String) e.getKey();
            final Injector injector = (injectorMap == null) ? null : injectorMap.get(fieldName);
            final Object rhs = e.getValue();

            if (rhs == null) {
                jsonObj.put(fieldName, null);
            } else if (rhs.getClass().isArray()) {   // RHS is an array
                // Trace the contents of the array (so references inside the array and into the array work)
                JsonObject jsonArray = new JsonObject();
                jsonArray.setItems(rhs);
                push(jsonArray);

                // Assign the array directly to the Map key (field name)
                jsonObj.put(fieldName, rhs);
            } else if (rhs instanceof JsonObject) {
                JsonObject jObj = (JsonObject) rhs;
                if (injector != null) {
                    boolean isNonRefClass = readOptions.isNonReferenceableClass(injector.getType());
                    if (isNonRefClass) {
                        jObj.setValue(converter.convert(jObj.getValue(), injector.getType()));
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
                if (converter.isConversionSupportedFor(rhs.getClass(), fieldType)) {
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
            jsonObject.setItems(element);
        }

        if (jsonObject != null) {
            if (componentType.isArray()) {
                // Set the hintType to guide createInstance to instantiate the correct array type
                jsonObject.setHintType(componentType);

                // Create a new array instance using createInstance, which respects the hintType
                Object arrayElement = createInstance(jsonObject);

                // Assign the newly created array to the parent array's element
                setArrayElement(target, index, arrayElement);

            }
            // Push the JsonObject for further processing
            push(jsonObject);
        }
    }

    protected void traverseArray(JsonObject jsonObj) {
        Object items = jsonObj.getItems();
        if (items == null || Array.getLength(items) == 0) {
            return;
        }

        Object target = jsonObj.getTarget() != null ? jsonObj.getTarget() : jsonObj.getItems();
        final ReferenceTracker refTracker = getReferences();
        final Converter converter = getConverter();
        final int len = Array.getLength(items);

        // Determine the immediate component type of the current array level
        Class<?> componentType = Object.class;
        if (jsonObj.getTarget() != null) {
            componentType = jsonObj.getTarget().getClass();
            if (componentType.isArray()) {
                componentType = componentType.getComponentType();
            }
        }

        for (int i = 0; i < len; i++) {
            Object element = Array.get(items, i);

            if (element == null) {
                Array.set(target, i, null);
            } else if (element.getClass().isArray() || (element instanceof JsonObject && ((JsonObject) element).isArray())) {
                // Handle nested arrays using the unified helper method
                handleNestedArray(element, componentType, target, i);
            }
            else if (converter.isConversionSupportedFor(element.getClass(), componentType)) {
                // Convert the element to the base component type
                Object convertedValue = converter.convert(element, componentType);
                setArrayElement(target, i, convertedValue);
            }
            else if (element instanceof JsonObject) {
                JsonObject jsonObject = (JsonObject) element;
                Long refId = jsonObject.getReferenceId();

                if (refId == null) {
                    // Convert JsonObject to its destination type if possible
                    Class<?> type = jsonObject.getJavaType();
                    if (type != null && converter.isConversionSupportedFor(Map.class, type)) {
                        Object converted = converter.convert(jsonObject, type);
                        setArrayElement(target, i, converted);
                        jsonObject.setFinished();
                    } else {
                        push(jsonObject);
                    }
                } else {    // Connect reference
                    JsonObject refObject = refTracker.getOrThrow(refId);
                    Class<?> type = refObject.getJavaType();

                    if (type != null && converter.isConversionSupportedFor(Map.class, type)) {
                        Object convertedRef = converter.convert(refObject, type);
                        refObject.setFinishedTarget(convertedRef, true);
                        setArrayElement(target, i, refObject.getTarget());
                    } else {
                        setArrayElement(target, i, refObject);
                    }
                }
            } else {
                setArrayElement(target, i, element);
            }
        }
        jsonObj.setItems(target);
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

        Object items = jsonObj.getItems();
        Collection<Object> col = (Collection<Object>) jsonObj.getTarget();
        if (col == null) {
            col = (Collection<Object>)createInstance(jsonObj);
        }
        final boolean isList = col instanceof List;
        int idx = 0;

        if (items != null) {
            int len = Array.getLength(items);
            for (int i=0; i < len; i++) {
                Object element = Array.get(items, i);
                Object special;
                if (element == null) {
                    col.add(null);
                } else if (element instanceof String || element instanceof Boolean || element instanceof Double || element instanceof Long) {
                    // Allow Strings, Booleans, Longs, and Doubles to be "inline" without Java object decoration (@id, @type, etc.)
                    col.add(element);
                } else if (element.getClass().isArray()) {
                    final JsonObject jObj = new JsonObject();
                    jObj.setHintType(Object.class);
                    jObj.setItems(element);
                    createInstance(jObj);
                    col.add(jObj.getTarget());
                    push(jObj);
                } else { // if (element instanceof JsonObject)
                    final JsonObject jObj = (JsonObject) element;
                    final Long ref = jObj.getReferenceId();

                    if (ref != null) {
                        JsonObject refObject = getReferences().getOrThrow(ref);

                        if (refObject.getTarget() != null) {
                            col.add(refObject.getTarget());
                        } else {
                            unresolvedRefs.add(new UnresolvedReference(jsonObj, idx, ref));
                            if (isList) {   // Index-able collection, so set 'null' as element for now - will be patched in later.
                                col.add(null);
                            }
                        }
                    } else {
                        jObj.setHintType(Object.class);
                        createInstance(jObj);
                        boolean isNonRefClass = getReadOptions().isNonReferenceableClass(jObj.getJavaType());
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

    /**
     * Overrides the createJavaFromJson method to handle multi-dimensional arrays when rootType is null.
     *
     * @param root The root object to create from.
     * @return The instantiated Java object.
     */
    @Override
    public Object createJavaFromJson(Object root) {
        if (root instanceof Object[]) {
            int depth = getArrayDepth(root);
            Object multiDimArray = createMultiDimensionalArray(root, depth);
            JsonObject array = new JsonObject();
            array.setTarget(multiDimArray);
            array.setItems(multiDimArray);
            push(array);
            return multiDimArray;
        } else if (root instanceof JsonObject) {
            Object ret = createInstance((JsonObject) root);
            push((JsonObject) root);
            return ret;
        } else {
            return root;
        }
    }

    /**
     * Determines the depth of a multi-dimensional array.
     *
     * @param array The array object.
     * @return The depth of the array.
     */
    private int getArrayDepth(Object array) {
        if (array == null || !array.getClass().isArray()) {
            return 0;
        }
        int depth = 0;
        Class<?> arrayClass = array.getClass();
        while (arrayClass.isArray()) {
            depth++;
            arrayClass = arrayClass.getComponentType();
        }
        return depth;
    }

    /**
     * Recursively creates a multi-dimensional array based on the detected depth.
     *
     * @param array The array object.
     * @param depth The depth of the array.
     * @return The multi-dimensional array.
     */
    private Object createMultiDimensionalArray(Object array, int depth) {
        if (depth == 1) {
            return array;
        }
        if (array == null || !array.getClass().isArray()) {
            throw new IllegalArgumentException("Expected an array, but received: " +
                    (array == null ? "null" : array.getClass().getName()));
        }

        int length = Array.getLength(array);
        Class<?> componentType = array.getClass().getComponentType();

        // Create a new array instance with the same component type
        Object newArray = Array.newInstance(componentType, length);

        for (int i = 0; i < length; i++) {
            Object elem = Array.get(array, i);
            if (elem != null && elem.getClass().isArray()) {
                Object nestedArray = createMultiDimensionalArray(elem, depth - 1);
                Array.set(newArray, i, nestedArray);
            } else {
                Array.set(newArray, i, null);
            }
        }

        return newArray;
    }

    protected Object resolveArray(Class<?> suggestedType, List<Object> list)
    {
        if (suggestedType == null || suggestedType == Object.class) {
            // No suggested type, so use Object[]
            return list.toArray();
        }

        JsonObject jsonArray = new JsonObject();
        if (Collection.class.isAssignableFrom(suggestedType)) {
            jsonArray.setHintType(suggestedType);
            jsonArray.setTarget(createInstance(jsonArray));
        } else {
            jsonArray.setTarget(Array.newInstance(suggestedType, list.size()));
        }
        jsonArray.setItems(list.toArray());
        return jsonArray;
    }
}