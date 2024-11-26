package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
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
                        // This will be JsonPrimitive.setValue() in the future (clean)
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
                // The code below is 'upgrading' the RHS values in the passed in JsonObject Map
                // by using the @type class name (when specified and exists), to coerce the vanilla
                // JSON values into the proper types defined by the class listed in @type.  This is
                // a cool feature of json-io, that even when reading a map-of-maps JSON file, it will
                // improve the final types of values in the maps RHS, to be of the field type that
                // was optionally specified in @type.
                final Class<?> fieldType = injector.getType();
                if (Primitives.isPrimitive(fieldType) || BigDecimal.class.equals(fieldType) || BigInteger.class.equals(fieldType) || Date.class.equals(fieldType)) {
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
     * Process java.util.Collection and it's derivatives.  Collections are written specially
     * so that the serialization does not expose the Collection's internal structure, for
     * example a TreeSet.  All entries are processed, except unresolved references, which
     * are filled in later.  For an indexable collection, the unresolved references are set
     * back into the proper element location.  For non-indexable collections (Sets), the
     * unresolved references are added via .add().
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     */
    protected void traverseCollection(final JsonObject jsonObj) {
        Object items = jsonObj.getItems();
        if (items == null || Array.getLength(items) == 0) {
            return;
        }

        Object target = jsonObj.getTarget() != null ? jsonObj.getTarget() : jsonObj.getItems();
        final ReferenceTracker refTracker = getReferences();
        final Converter converter = getConverter();
        final int len = Array.getLength(items);

        // Cache the base component type of the array from the target
        Class<?> componentType = Object.class;
        if (jsonObj.getTarget() != null) {
            componentType = jsonObj.getTarget().getClass();
            while (componentType.isArray()) {
                componentType = componentType.getComponentType();
            }
        }

        for (int i = 0; i < len; i++) {
            Object element = Array.get(items, i);

            if (element == null) {
                Array.set(target, i, null);
            } else if (element.getClass().isArray()) {   // Array element inside Collection
                JsonObject jsonObject = new JsonObject();
                jsonObject.setItems(element);
                push(jsonObject);
            } else if (converter.isConversionSupportedFor(element.getClass(), componentType)) {
                // Convert the element to the base component type
                Object convertedValue = converter.convert(element, componentType);
                Array.set(target, i, convertedValue);
            } else if (element instanceof JsonObject) {
                JsonObject jsonObject = (JsonObject) element;
                Long refId = jsonObject.getReferenceId();

                if (refId == null) {
                    // Convert JsonObject to its destination type if possible
                    Class<?> type = jsonObject.getJavaType();
                    if (type != null && converter.isConversionSupportedFor(Map.class, type)) {
                        Array.set(target, i, converter.convert(jsonObject, type));
                        jsonObject.setFinished();
                    } else {
                        push(jsonObject);
                    }
                } else {    // Connect reference
                    JsonObject refObject = refTracker.getOrThrow(refId);
                    Class<?> type = refObject.getJavaType();

                    if (type != null && converter.isConversionSupportedFor(Map.class, type)) {
                        refObject.setFinishedTarget(converter.convert(refObject, type), true);
                        Array.set(target, i, refObject.getTarget());
                    } else {
                        Array.set(target, i, refObject);
                    }
                }
            } else {
                try {
                    Array.set(target, i, element);
                } catch (Exception e) {
                    String elementType = element.getClass().getName();
                    String valueRepresentation = String.valueOf(element);
                    String arrayType = target.getClass().getSimpleName();

                    throw new JsonIoException("Cannot set '" + elementType + "' (value: " + valueRepresentation + ") into '" +
                            arrayType + "' at index " + i + ". Type mismatch between value and array type.");
                }
            }
        }
        jsonObj.setFinished();
        jsonObj.setTarget(null);  // Don't waste space (used for typed return, not generic Map return)
        jsonObj.setItems(target);
    }

    protected void traverseArray(JsonObject jsonObj) {
        traverseCollection(jsonObj);
    }

    public void assignField(final JsonObject jsonObj, final Injector injector, final Object rhs) {
    }

    Object resolveArray(Class<?> suggestedType, List<Object> list)
    {
        if (suggestedType == null || suggestedType == Object.class) {
            // No suggested type, so use Object[]
            return list.toArray();
        }

        JsonObject jsonArray = new JsonObject();
        jsonArray.setTarget(Array.newInstance(suggestedType, list.size()));
        jsonArray.setItems(list.toArray());
        traverseJsonObject(jsonArray);
//        jsonArray.setFinished();
//        return jsonArray.getTarget();
        return jsonArray;
    }
}