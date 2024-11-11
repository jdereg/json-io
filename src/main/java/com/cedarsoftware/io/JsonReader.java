package com.cedarsoftware.io;

import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.Convention;
import com.cedarsoftware.util.FastByteArrayInputStream;
import com.cedarsoftware.util.FastReader;
import com.cedarsoftware.util.convert.Converter;

/**
 * Read an object graph in JSON format and make it available in Java objects, or
 * in a "Map of Maps." (untyped representation).  This code handles cyclic references
 * and can deserialize any Object graph without requiring a class to be 'Serializable'
 * or have any specific methods on it.  It will handle classes with non-public constructors.
 * <br><br>
 * Usages:
 * <ul><li>
 * Call the static method: {@code JsonReader.jsonToJava(String json)}.  This will
 * return a typed Java object graph.</li>
 * <li>
 * Call the static method: {@code JsonReader.jsonToMaps(String json)}.  This will
 * return an untyped object representation of the JSON String as a Map of Maps, where
 * the fields are the Map keys, and the field values are the associated Map's values.  You can
 * call the JsonWriter.objectToJson() method with the returned Map, and it will serialize
 * the Graph into the equivalent JSON stream from which it was read.
 * <li>
 * Instantiate the JsonReader with an InputStream: {@code JsonReader(InputStream in)} and then call
 * {@code readObject()}.  Cast the return value of readObject() to the Java class that was the root of
 * the graph.
 * </li>
 * <li>
 * Instantiate the JsonReader with an InputStream: {@code JsonReader(InputStream in, true)} and then call
 * {@code readObject()}.  The return value will be a Map of Maps.
 * </li></ul><br>
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
public class JsonReader implements Closeable
{
    private final FastReader input;
    private final Resolver resolver;
    private final ReadOptions readOptions;
    private final JsonParser parser;

    /**
     * Subclass this interface and create a class that will return a new instance of the
     * passed in Class (c).  Your factory subclass will be called when json-io encounters an
     * instance of (c) which you register with JsonReader.assignInstantiator().
     * Use the passed in JsonObject o which is a JsonObject to source values for the construction
     * of your class.
     */
    public interface ClassFactory
    {
        /**
         * Implement this method to return a new instance of the passed in Class.  Use the passed
         * in JsonObject to supply values to the construction of the object.
         *
         * @param c        Class of the object that needs to be created
         * @param jObj     JsonObject (if primitive type do jObj.getPrimitiveValue();
         * @param resolver Resolve instance that has references to ID Map, Converter, ReadOptions
         * @return a new instance of C.  If you completely fill the new instance using
         * the value(s) from object, and no further work is needed for construction, then
         * override the isObjectFinal() method below and return true.
         */
        default Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
            return MetaUtils.newInstance(resolver.getConverter(), c, null);
        }
        
        /**
         * @return true if this object is instantiated and completely filled using the contents
         * from the Object [a JsonObject or value].  In this case, no further processing
         * will be performed on the instance.  If the object has sub-objects (complex fields),
         * then return false so that the JsonReader will continue on filling out the remaining
         * portion of the object.
         */
        default boolean isObjectFinal() {
            return false;
        }

        default void gatherRemainingValues(Resolver resolver, JsonObject jObj, List<Object> arguments, Set<String> excludedFields) {
            Convention.throwIfNull(jObj, "JsonObject cannot be null");

            for (Map.Entry<Object, Object> entry : jObj.entrySet()) {
                if (excludedFields.contains(entry.getKey().toString()) || entry.getValue() == null) {
                    continue;
                }

                if (entry.getValue() instanceof JsonObject) {
                    JsonObject sub = (JsonObject) entry.getValue();
                    Object value = resolver.toJavaObjects(sub, sub.getJavaType());

                    if (value != null && sub.getJavaType() != null) {
                        arguments.add(value);
                    }
                }
            }
        }
    }

    /**
     * Used to react to fields missing when reading an object. This method will be called after all deserialization has
     * occurred to allow all ref to be resolved.
     * <p>
     * Used in conjunction with {@link ReadOptions#getMissingFieldHandler()}.
     */
    public interface MissingFieldHandler
    {
        /**
         * Notify that a field is missing. <br>
         * Warning : not every type can be deserialized upon missing fields. Arrays and Object type that do not have
         * serialized @type definition will be ignored.
         *
         * @param object the object that contains the missing field
         * @param fieldName name of the field to be replaced
         * @param value current value of the field
         */
        void fieldMissing(Object object, String fieldName, Object value);
    }

    /**
     * Implement this interface to add a custom JSON reader.
     */
    public interface JsonClassReader
    {
        /**
         * Read a custom object. Only process the non-structural values for any given reader, and push the structural
         * elements (non-primitive fields) onto the resolver's stack, to be processed.
         * @param jsonObj  Object being read.  Could be a fundamental JSON type (String, long, boolean, double, null, or JsonObject)
         * @param resolver Provides access to push non-primitive items onto the stack for further processing. This will
         *                allow it to be processed by a standard processor (array, Map, Collection) or another custom
         *                factory or reader that handles the "next level."  You can handle sub-objects here if you wanted.
         * @return Java Object that you filled out with values from the passed in jsonObj.
         */
        default Object read(Object jsonObj, Resolver resolver) {
            throw new UnsupportedOperationException("You must implement this method and read the JSON content from jsonObj and copy the values from jsonObj to the target class, jsonObj.getTarget()");
        }
    }

    /**
     * Allow others to try potentially faster Readers.
     * @param inputStream InputStream that will be offering JSON.
     * @return FastReader wrapped around the passed in inputStream, translating from InputStream to InputStreamReader.
     */
    protected FastReader getReader(InputStream inputStream)
    {
        return new FastReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8), 8192, 10);
    }

    /**
     * Creates a json reader using custom read options
     * @param input         InputStream of utf-encoded json
     * @param readOptions Read Options to turn on/off various feature options, or supply additional ClassFactory data,
     *                    etc. If null, readOptions will use all defaults.
     */
    public JsonReader(InputStream input, ReadOptions readOptions) {
        this(input, readOptions, new DefaultReferenceTracker());
    }

    public JsonReader(InputStream inputStream, ReadOptions readOptions, ReferenceTracker references) {
        this.readOptions = readOptions == null ? ReadOptionsBuilder.getDefaultReadOptions() : readOptions;
        Converter converter = new Converter(this.readOptions.getConverterOptions());
        this.input = getReader(inputStream);

        this.resolver = this.readOptions.isReturningJsonObjects() ?
                new MapResolver(this.readOptions, references, converter) :
                new ObjectResolver(this.readOptions, references, converter);

        this.parser = new JsonParser(this.input, this.resolver);
    }

    /**
     * Use this constructor if you already have a JsonObject graph and want to parse it into
     * Java objects by calling jsonReader.jsonObjectsToJava(rootJsonObject) after constructing
     * the JsonReader.
     *
     * @param readOptions Read Options to turn on/off various feature options, or supply additional ClassFactory data,
     *                    etc. If null, readOptions will use all defaults.
     */
    public JsonReader(ReadOptions readOptions) {
        this(new FastByteArrayInputStream(new byte[]{}), readOptions);
    }

    public <T> T readObject(Class<T> rootType) {
        T returnValue;
        try {
            returnValue = (T) parser.readValue(rootType);
            if (returnValue == null) {
                return null;    // easy, done.
            }
            verifyRootType(rootType, returnValue);
        } catch (JsonIoException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonIoException(getErrorMessage("error parsing JSON value"), e);
        }

        // JSON [] at root
        if (returnValue instanceof Object[]) {
            JsonObject rootObj = new JsonObject();
            rootObj.setTarget(returnValue);
            rootObj.setJsonArray((Object[])returnValue);
            T graph = toJavaObjects(rootObj, rootType);
            boolean asMaps = readOptions.isReturningJsonObjects();
            return asMaps ? returnValue : graph;
        }

        // JSON {} at root
        if (returnValue instanceof JsonObject) {
            return determineReturnValueWhenJsonObjectRoot(rootType, returnValue);
        }

        // JSON Primitive (String, Boolean, Double, Long), or convertible types
        if (rootType != null) {
            Converter converter = resolver.getConverter();
            if (converter.isConversionSupportedFor(returnValue.getClass(), rootType)) {
                return converter.convert(returnValue, rootType);
            }

            throw new JsonIoException(getErrorMessage("Return type mismatch, expected: " + rootType.getName() + ", actual: " + returnValue.getClass().getName()));
        }

        return returnValue;
    }

    private <T> void verifyRootType(Class<T> rootType, T returnValue) {
        if (readOptions.isReturningJavaObjects() || rootType == null) {
            return;
        }

        Converter converter = resolver.getConverter();
        if (converter.isConversionSupportedFor(rootType, returnValue.getClass()) || converter.isConversionSupportedFor(returnValue.getClass(), rootType)) {
            return;
        }

        if (returnValue instanceof JsonObject) {
            if (Map.class.isAssignableFrom(rootType)) {
                return;
            }
            throw new JsonIoException("Root type (" + rootType.getName() + ") must be a Map type or null when JSON is an object { }");
        }

        if (returnValue.getClass().isArray()) {
            if (!rootType.isArray() && !(returnValue instanceof JsonObject && ((JsonObject) returnValue).isArray())) {
                throw new JsonIoException("Root type (" + rootType.getName() + ") must be an array type or null when JSON is an array [ ]");
            }
        }

        throw new JsonIoException("Root type (" + rootType.getName() + ") cannot be converted to: " + returnValue.getClass().getName());
    }

    private <T> T determineReturnValueWhenJsonObjectRoot(Class<T> rootType, T returnValue) {
        boolean asMaps = readOptions.isReturningJsonObjects();
        T graph = toJavaObjects((JsonObject) returnValue, rootType);

        // Handle the case where no specific rootType is provided
        if (rootType == null) {
            return (!asMaps || isJsonPrimitive(graph)) ? graph : returnValue;
        }

        if (graph == null) {
            throw new JsonIoException("Cannot use root: {" + rootType.getName() + "} when 'returnAsNativeJsonObjects()' is set. Either use null for root, or set readOptions.returnAsJavaObjects() for this root.");
        }

        // Check for type compatibility or required conversion
        if (!rootType.isAssignableFrom(graph.getClass()) && !ClassUtilities.doesOneWrapTheOther(rootType, graph.getClass())) {
            return attemptTypeConversion(graph, rootType, returnValue);
        }

        // Directly handle Map specific logic or return the processed graph
        return Map.class.isAssignableFrom(rootType) ? (T) pickNotEmpty((Map<?, ?>) graph, (Map<?, ?>) returnValue) : graph;
    }
    
    private <T> T attemptTypeConversion(T graph, Class<T> rootType, T returnValue) {
        Converter converter = getResolver().getConverter();
        if (converter.isConversionSupportedFor(graph.getClass(), rootType)) {
            return (graph instanceof Map) ?
                    converter.convert(pickNotEmpty((Map<?, ?>) graph, (Map<?, ?>) returnValue), rootType)
                    : converter.convert(graph, rootType);
        }
        throw new JsonIoException("Return type mismatch, expected: " + rootType.getName() + ", actual: " + graph.getClass().getName() +
                "\nYou could be encountering an 'unknownType' (class) that cannot be created, in which case a LinkedHashMap is returned." +
                "\nYou may need to add a ClassFactory to create the unknown class.");
    }
    private boolean isJsonPrimitive(Object obj) {
        return obj instanceof Long || obj instanceof BigInteger ||
                obj instanceof Double || obj instanceof BigDecimal ||
                obj instanceof Boolean || obj instanceof String;
    }

    private Map<?,?> pickNotEmpty(Map<?, ?> a, Map<?, ?> b) {
        return a.isEmpty() ? b : a;
    }

    /**
     * This method converts a rootObj Map, (which contains nested Maps
     * and so forth representing a Java Object graph), to a Java
     * object instance.  The rootObj map came from using the JsonReader
     * to parse a JSON graph (using the API that puts the graph
     * into Maps, not the typed representation).
     *
     * @param rootObj JsonObject instance that was the rootObj object from the
     *             JSON input that was parsed in an earlier call to JsonReader.
     * @return a typed Java instance that was serialized into JSON.
     */
    @SuppressWarnings("unchecked")
    protected <T> T toJavaObjects(JsonObject rootObj, Class<T> root) {
        try {
            if (root == null) {
                root = rootObj.getJavaType() == null ? (Class<T>)Object.class : (Class<T>)rootObj.getJavaType();
            }
            return resolver.toJavaObjects(rootObj, root);
        } catch (Exception e) {
            if (readOptions.isCloseStream()) {
                MetaUtils.safelyIgnoreException(this::close);
            }
            if (e instanceof JsonIoException) {
                throw (JsonIoException) e;
            }
            throw new JsonIoException(getErrorMessage(e.getMessage()), e);
        } finally {
            //  In case we decide to only go with Hinted Types (passing class in here),
            //  we'll need to rename and make sure that this cleanup only happens
            //  from the outer (initial) JsonReader and not from class factories.
            resolver.cleanup();
        }
    }

    /**
     * @return a copy of the Resolver being used.  The Resolver is a super class of ObjectResolver or MapResolver.
     * The ObjectResolver is used to convert JSON maps (JsonObject) to Java objects.  A MapResolver is used to convert
     * clean-up the value-side of the map only. The MapResolver uses the Class associated to the JsonObject to coerce
     * primitive fields in the Map's values to their correct data types. For example, if a Long was read in as a String
     * from the JSON, it will be converted back to a Long (inside the Map) because the associated field (matched by
     * String key name in Map) to the Java class 's Field, and converts the raw JSON primitive value to the field type
     * (long/Long).
     */
    public Resolver getResolver() {
        return resolver;
    }
    
    public void close() {
        try {
            if (input != null) {
                input.close();
            }
        }
        catch (Exception e) {
            throw new JsonIoException("Unable to close input", e);
        }
    }

    private String getErrorMessage(String msg)
    {
        if (input != null) {
            return msg + "\nLast read: " + input.getLastSnippet() + "\nline: " + input.getLine() + ", col: " + input.getCol();
        }
        return msg;
    }

    /**
     * Implementation of ReferenceTracker
     */
    static class DefaultReferenceTracker implements ReferenceTracker {

        final Map<Long, JsonObject> references = new HashMap<>();

        public JsonObject put(Long l, JsonObject o) {
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

            while (target.isReference()) {
                id = target.getReferenceId();
                target = references.get(id);
                if (target == null) {
                    return null;
                }
            }

            return target;
        }
    }
}
