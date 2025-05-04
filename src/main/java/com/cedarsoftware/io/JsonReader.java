package com.cedarsoftware.io;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.Convention;
import com.cedarsoftware.util.ExceptionUtilities;
import com.cedarsoftware.util.FastByteArrayInputStream;
import com.cedarsoftware.util.FastReader;
import com.cedarsoftware.util.TypeUtilities;
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
    private final Converter localConverter;
    private final boolean isRoot;

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
            return ClassUtilities.newInstance(resolver.getConverter(), c, null);
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
                    Object value = resolver.toJavaObjects(sub, sub.getType());

                    if (value != null && sub.getType() != null) {
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
    protected FastReader getReader(InputStream inputStream) {
        return new FastReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8), 65536, 10);
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
        this.isRoot = true;   // When root is true, the resolver has .cleanup() called on it upon JsonReader finalization
        this.readOptions = readOptions == null ? ReadOptionsBuilder.getDefaultReadOptions() : readOptions;
        Converter converter = new Converter(this.readOptions.getConverterOptions());
        this.input = getReader(inputStream);
        this.resolver = this.readOptions.isReturningJsonObjects() ?
                new MapResolver(this.readOptions, references, converter) :
                new ObjectResolver(this.readOptions, references, converter);
        this.parser = new JsonParser(this.input, this.resolver);
        localConverter = new Converter(this.readOptions.getConverterOptions());
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

    /**
     * Use this constructor to resolve JsonObjects into Java, for example, in a ClassFactory or custom reader.
     * Then use jsonReader.jsonObjectsToJava(rootJsonObject) to turn JsonObject graph (sub-graph) into Java.
     *
     * @param resolver Resolver, obtained from ClassFactory.newInstance() or CustomReader.read().
     */
    public JsonReader(Resolver resolver) {
        this.isRoot = false;    // If not root, .cleanup() is not called when the JsonReader is finalized
        this.resolver = resolver;
        this.readOptions = resolver.getReadOptions();
        this.localConverter = resolver.getConverter();
        this.input = getReader(new ByteArrayInputStream(new byte[0]));  // Graph is already read-in when using this API
        this.parser = new JsonParser(input, resolver);
    }

    /**
     * Reads and parses a JSON structure from the underlying {@code parser}, then returns
     * an object of type {@code T}. The parsing and return type resolution follow these steps:
     *
     * <ul>
     *   <li>Attempts to parse the top-level JSON element (e.g. object, array, or primitive) and
     *   produce an initial {@code returnValue} of type {@code T} (as inferred or declared).</li>
     *   <li>If the parsed value is {@code null}, simply returns {@code null}.</li>
     *   <li>If the value is recognized as an “array-like” structure (either an actual Java array or
     *   a {@link JsonObject} flagged as an array), it delegates to {@code handleArrayRoot()} to build
     *   the final result object.</li>
     *   <li>If the value is a {@link JsonObject} at the root, it delegates to
     *   {@code determineReturnValueWhenJsonObjectRoot()} for further resolution.</li>
     *   <li>Otherwise (if it’s a primitive or other type), it may convert the result
     *   to {@code rootType} if needed/possible.</li>
     * </ul>
     *
     * @param <T>       The expected return type.
     * @param rootType  The class token representing the desired return type. May be {@code null},
     *                  in which case the type is inferred from the JSON content or defaults to
     *                  the parser’s best guess.
     * @return          The fully resolved and (if applicable) type-converted object.
     *                  Could be {@code null} if the JSON explicitly represents a null value.
     * @throws JsonIoException if there is any error parsing or resolving the JSON content,
     *         or if type conversion fails (e.g., when the actual type does not match
     *         the requested {@code rootType} and no valid conversion is available).
     */
    public <T> T readObject(Type rootType) {
        verifyRootType(rootType);

        final T returnValue;
        try {
            // Attempt to parse the JSON into an object
            returnValue = (T) parser.readValue(rootType);
        } catch (JsonIoException e) {
            throw e;
        } catch (Exception e) {
            // Wrap any other exception
            throw new JsonIoException(getErrorMessage("error parsing JSON value"), e);
        }

        return (T) toJava(rootType, returnValue);
    }

    /**
     * Only use from ClassFactory or CustomReader
     */
    public Object toJava(Type rootType, Object root) {
        if (root == null) {
            return null;
        }

        boolean isJava = resolver.getReadOptions().isReturningJavaObjects();

        // 1) Handle a root-level array
        if (isRootArray(root)) {
            Object o = handleArrayRoot(rootType, root);
            // Fetch root (dig for it) if we have JsonObject and return type is Java
            if (isJava && o instanceof JsonObject && ((JsonObject) o).target != null) {
                o = ((JsonObject) o).target;
            }
            return o;
        }

        // 2) Handle a root-level JsonObject
        if (root instanceof JsonObject) {
            Object o = handleObjectRoot(rootType, (JsonObject)root);
            // Fetch root (dig for it) if we have JsonObject and return type is Java
            if (isJava && o instanceof JsonObject && ((JsonObject) o).target != null) {
                o = ((JsonObject) o).target;
            }
            return o;
        }

        // 3) Otherwise, it’s a primitive (String, Boolean, Number, etc.) or convertible
        return convertIfNeeded(rootType, root);
    }

    /**
     * Returns true if the parsed object represents a root-level “array,”
     * meaning either an actual Java array, or a JsonObject marked as an array.
     */
    private boolean isRootArray(Object value) {
        if (value.getClass().isArray()) {
            return true;
        }
        return (value instanceof JsonObject) && ((JsonObject) value).isArray();
    }

    /**
     * Handles the case where the top-level element is an array (either a real Java array,
     * or a JsonObject that’s flagged as an array).
     */
    private Object handleArrayRoot(Type rootType, Object returnValue) {
        JsonObject rootObj;

        // If it’s actually a Java array
        if (returnValue.getClass().isArray()) {
            rootObj = new JsonObject();
            rootObj.setType(rootType);
            rootObj.setTarget(returnValue);
            rootObj.setItems((Object[])returnValue);
        } else {
            // Otherwise, it’s a JsonObject that has isArray() == true
            rootObj = (JsonObject) returnValue;
        }

        // Attempt to build the final graph object
        Object graph = resolveObjects(rootObj, rootType);
        if (graph == null) {
            // If resolveObjects returned null, fall back on the items as the final object
            graph = rootObj.getItems();
        }

        // Perform any needed type conversion before returning
        return convertIfNeeded(rootType, graph);
    }

    /**
     * Converts returnValue to the desired rootType if necessary and possible.
     */
    @SuppressWarnings("unchecked")
    private Object convertIfNeeded(Type rootType, Object returnValue) {
        if (rootType == null) {
            // If no specific type was requested, return as-is
            return returnValue;
        }
        Class<?> rootClass = TypeUtilities.getRawClass(rootType);

        // If the value is already the desired type (or a subtype), just return
        if (rootClass.isAssignableFrom(returnValue.getClass())) {
            return returnValue;
        }

        // If the rootType is a primitive type, try to convert to its wrapper class
        try {
            return localConverter.convert(returnValue, rootClass);
        } catch (Exception e) {
            throw new JsonIoException("Return type mismatch. Expecting: " +
                    rootClass.getName() + ", found: " + returnValue.getClass().getName(), e);
        }
    }

    /**
     * When return JsonObjects, verify return type (bound to mostly built-in convertable types
     * @param rootType Class passed as rootType to return type
     */
    private <T> void verifyRootType(Type rootType) {
        if (rootType == null || readOptions.isReturningJavaObjects()) {
            return;
        }

        // Obtain the raw Class for the rootType.
        Class<?> rawRootType = TypeUtilities.getRawClass(rootType);
        // We'll use typeToCheck to drill down in case of arrays.
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
            if (localConverter.isSimpleTypeConversionSupported(ultimateRawType, ultimateRawType)
                    || (ultimateRawType != null && ultimateRawType.equals(Object.class))) {
                return;
            }
        } else {
            // For non-array types, check if the type is supported by simple conversion.
            if (localConverter.isSimpleTypeConversionSupported(rawRootType, rawRootType)) {
                return;
            }
        }

        // For further checks, extract the raw class from the (possibly drilled-down) type.
        Class<?> rawTypeToCheck = TypeUtilities.getRawClass(typeToCheck);
        if (rawTypeToCheck != null) {
            if (Collection.class.isAssignableFrom(rawTypeToCheck)) {
                return;
            }
            if (Map.class.isAssignableFrom(rawTypeToCheck)) {
                return;
            }
        }

        // Prepare an error message using the raw root type's name (if available).
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
     * Handles the top-level case where the parsed JSON is represented as a non-array
     * {@link JsonObject}. This method applies fallback type logic if needed, resolves
     * internal references to build the final object graph, and then performs any necessary
     * conversions based on the following conditions:
     * <ul>
     *   <li>Whether <code>returnJsonObjects</code> (i.e. "JSON mode") is enabled, or</li>
     *   <li>A specific <code>rootType</code> is requested by the caller.</li>
     * </ul>
     *
     * <p>The high-level steps are:</p>
     * <ol>
     *   <li><strong>Fallback type check:</strong> If a special case applies, update the
     *       <code>@type</code> in the {@link JsonObject} before resolution.</li>
     *   <li><strong>Resolution:</strong> Call <code>resolveObjects()</code> to hydrate the
     *       parsed structure into a Java object graph (which may be a Collection, Map, bean, or even <code>null</code>).</li>
     *   <li><strong>Null handling:</strong> If the resolved <code>graph</code> is <code>null</code>,
     *       this method returns the original {@code JsonObject} (i.e. <code>returnValue</code>).</li>
     *   <li><strong>rootType handling (Java mode or JSON mode):</strong>
     *     <ul>
     *       <li>If a non-null <code>rootType</code> is specified, check whether the resolved <code>graph</code>
     *           is already assignable to it. Otherwise, attempt a conversion (if supported) or return the unconverted
     *           object.</li>
     *       <li>If no <code>rootType</code> is provided, fall back to returning either the fully resolved Java object
     *           (when <code>returnJsonObjects == false</code>) or the raw {@code JsonObject} (when <code>returnJsonObjects == true</code>),
     *           except in cases where a simple type conversion can be applied.</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param rootType the expected return type (as a {@link java.lang.reflect.Type}), including any generic type information;
     *                 may be {@code null} for type inference.
     * @param jsonObj the initial parsed representation
     * @return the resolved and, if necessary, converted object graph, or the raw {@code JsonObject}, depending on the mode
     *         and convertibility.
     */
    private Object handleObjectRoot(Type rootType, JsonObject jsonObj) {
        boolean returnJson = readOptions.isReturningJsonObjects();

        // 1) Fallback type check: If a fallback condition applies, update the @type in the JsonObject.

        // Convert the provided rootType (a full Type) to its raw Class form
        Class<?> rawRootType = (rootType == null ? null : TypeUtilities.getRawClass(rootType));
        Class<?> rawJObjClass = jsonObj.getRawType();

        if (isSubstituteSortedCollectionNeeded(returnJson, rawRootType, rawJObjClass)) {
            Class<?> fallbackType = getSubstituteCollection(rawJObjClass);
            jsonObj.setType(fallbackType);
        }

        // 2) Resolve internal references/build the object graph.
        Object graph = resolveObjects(jsonObj, rootType);

        // If resolution produced null, return the original JsonObject.
        if (graph == null) {
            return jsonObj;
        }

        // 3) If a specific rootType was provided...
        if (rootType != null) {
            // If the resolved graph is already assignable to the requested type, return it.
            if (rawRootType != null && rawRootType.isAssignableFrom(graph.getClass())) {
                return graph;
            }
            // Otherwise, if conversion is supported, perform the conversion.
            if (rawRootType != null && localConverter.isConversionSupportedFor(graph.getClass(), rawRootType)) {
                return localConverter.convert(graph, rawRootType);
            }

            // Otherwise, try to find common ancestors (excluding Object, Serializable, Cloneable).
            Set<Class<?>> skipRoots = new HashSet<>();
            skipRoots.add(Object.class);
            skipRoots.add(Serializable.class);
            skipRoots.add(Cloneable.class);

            Set<Class<?>> commonAncestors = ClassUtilities.findLowestCommonSupertypesExcluding(graph.getClass(), rawRootType, skipRoots);
            if (commonAncestors.isEmpty()) {
                throw new ClassCastException("Return type mismatch, expected: " +
                        (rawRootType != null ? rawRootType.getName() : rootType.toString()) +
                        ", actual: " + graph.getClass().getName());
            }
            return graph;
        }

        // 4) If no rootType was specified, decide based on the "JSON mode" setting.
        if (returnJson) {
            // --- JSON Mode ---
            Type javaType = jsonObj.getType();
            if (javaType != null) {
                // If there's an @type and it's a simple type (or Number), convert to its basic type.
                Class<?> javaClass = TypeUtilities.getRawClass(javaType);
                if (localConverter.isSimpleTypeConversionSupported(javaClass, javaClass) ||
                        Number.class.isAssignableFrom(javaClass)) {
                    Class<?> basicType = getJsonSynonymType(javaClass);
                    return localConverter.convert(jsonObj, basicType);
                }
                // If it's not a built-in primitive or convertible, return the raw JsonObject.
                if (!isBuiltInPrimitive(graph)) {
                    return jsonObj;
                }
            }
            // If no @type or it isn't convertible, check if the resolved graph can remain a "simple" type.
            if (localConverter.isSimpleTypeConversionSupported(graph.getClass(), graph.getClass())) {
                return graph;
            }
            // Otherwise, return the raw JsonObject.
            return jsonObj;
        }

        // 5) In JavaObjects mode with no specified rootType, return the resolved graph.
        return graph;
    }

    /**
     * Determines whether a fallback substitution should be applied for a sorted collection
     * when deserializing in JSON mode.  When in Map of Maps mode, we always want to return
     * without throwing an exception. If root was a TreeSet or TreeMap, we want to return a
     * LinkedHashSet or LinkedHashMap because the Comparator is not available.
     * <p>This method returns {@code true} if all the following conditions hold:
     * <ul>
     * <li>The deserialization is in "return JSON objects" mode (i.e. {@code returnJson} is true).</li>
     * <li>No explicit root type was provided (i.e. {@code rootType} is null).</li>
     * <li>The legacy Java type is not null and represents a sorted collection (either a {@link SortedSet}
     * or a {@link SortedMap}), as determined by {@link #getSubstituteCollection(Class)}.</li>
     * </ul>
     * In such cases, the deserialization process should substitute the sorted collection with a default
     * Collection type (such as {@link LinkedHashSet} or {@link LinkedHashMap}) to avoid issues related
     * to missing comparators.</p>
     *
     * @param returnJson a flag indicating if the deserialization is in native JSON mode
     * @param rootType   the explicit root type provided for deserialization; may be {@code null}
     * @param javaType   the Java type extracted from the JsonObject; must not be {@code null} for a fallback to be applicable
     * @return {@code true} if a fallback substitution for a sorted collection is needed; {@code false} otherwise
     */
    private boolean isSubstituteSortedCollectionNeeded(boolean returnJson, Class<?> rootType, Class<?> javaType) {
        return returnJson && rootType == null && javaType != null && getSubstituteCollection(javaType) != null;
    }

    /**
     * Checks if the given Java type represents a sorted collection.
     * <p>This method determines whether the specified {@code javaType} is a subtype of {@link SortedSet}
     * or {@link SortedMap}. If {@code javaType} is a SortedSet, the method returns {@link Set} (as a marker
     * for set-like behavior). If it is a SortedMap, it returns {@link Map#entrySet()}'s type marker (here simply {@link Map}).
     * Otherwise, if {@code javaType} is neither a SortedSet nor a SortedMap, the method returns {@code null}.</p>
     *
     * @param javaType the Java type to check
     * @return {@code Set.class} if {@code javaType} is a SortedSet, {@code Map.class} if {@code javaType} is a SortedMap,
     * or {@code null} if it is not a recognized sorted collection type
     */
    private Class<?> getSubstituteCollection(Class<?> javaType) {
        if (SortedSet.class.isAssignableFrom(javaType)) {
            return Set.class;
        } else if (SortedMap.class.isAssignableFrom(javaType)) {
            return Map.class;
        }
        return null;
    }

    /**
     * Determines if the provided Java type has a JSON synonym type.
     *
     * <p>This method maps complex or extended types to their simpler, JSON-friendly equivalents.
     * For example, {@code StringBuilder} and {@code StringBuffer} are mapped to {@code String},
     * while atomic types like {@code AtomicInteger} are mapped to {@code Integer}.
     *
     * @param javaType The Java class type to evaluate.
     * @return The corresponding JSON synonym type if a mapping exists; otherwise, returns the original {@code javaType}.
     */
    private Class<?> getJsonSynonymType(Class<?> javaType) {
        // Define mapping from @type to basic types
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
     * Determines whether the provided object is considered a built-in primitive type.
     *
     * <p>This method checks if the object is a primitive, a wrapper of a primitive, or a type
     * that is natively supported and can be directly represented in JSON without requiring complex
     * conversions.
     *
     * @param obj The object to evaluate.
     * @return {@code true} if the object is a built-in primitive type; {@code false} otherwise.
     */
    private boolean isBuiltInPrimitive(Object obj) {
        if (obj == null) {
            return false;
        }
        Class<?> cls = obj.getClass();
        return localConverter.isSimpleTypeConversionSupported(cls, cls);
    }

    /**
     * Deserializes a JSON object graph into a strongly-typed Java object instance.
     *
     * <p>This method processes a root {@link JsonObject} that represents the serialized form of a Java
     * object graph, which may include nested {@link Map} instances and other complex structures. It
     * converts this JSON-based representation back into a typed Java object, optionally guided by the
     * specified {@code rootType}.</p>
     *
     * <p>If the {@code rootType} parameter is {@code null}, the method attempts to infer the appropriate
     * Java class from the {@code @type} annotation within the {@code rootObj}. In the absence of such
     * type information, it defaults to {@link Object}.</p>
     *
     * <p>After the deserialization process, the method ensures that the resolver's state is cleaned up,
     * maintaining the integrity of subsequent operations.</p>
     *
     * @param <T>      the expected type of the Java object to be returned
     * @param rootObj  the root {@link JsonObject} representing the serialized JSON object graph
     *                 to be deserialized
     * @param rootType the desired Java type for the root object. If {@code null}, the method will
     *                 attempt to determine the type from the {@code @type} annotation in {@code rootObj}
     * @return a Java object instance corresponding to the provided JSON graph, of type {@code T}
     * @throws JsonIoException if an error occurs during deserialization, such as type conversion issues
     *                         or I/O errors
     *
     * <p><strong>Implementation Notes:</strong></p>
     * <ul>
     *     <li>When {@code rootType} is {@code null}, the method prioritizes type inference from the
     *         {@code @type} annotation within {@code rootObj}. If no type information is available,
     *         it defaults to {@link Object}.</li>
     *     <li>If {@link ReadOptions#isCloseStream()} is {@code true}, the method ensures that the
     *         associated stream is closed upon encountering an exception to prevent resource leaks.</li>
     *     <li>Post-deserialization, the resolver's state is cleaned up to maintain consistency and prevent
     *         interference with subsequent deserialization operations.</li>
     * </ul>
     *
     * Implementation Note:
     * <p>This method is designed to handle both scenarios where type information is provided and where it
     * is absent. It ensures robust deserialization by falling back to generic types when necessary and by
     * leveraging type conversion mappings defined within the converter.</p>
     */
    @SuppressWarnings("unchecked")
    protected <T> T resolveObjects(JsonObject rootObj, Type rootType) {
        try {
            // Determine the root type if not explicitly provided
            if (rootType == null) {
                rootType = rootObj.getType() == null ? Object.class : rootObj.getType();
            }

            // Delegate the conversion to the resolver using the converter
            T value = resolver.toJavaObjects(rootObj, rootType);
            return value;
        } catch (Exception e) {
            // Safely close the stream if the read options specify to do so
            if (readOptions.isCloseStream()) {
                ExceptionUtilities.safelyIgnoreException(this::close);
            }

            // Rethrow known JsonIoExceptions directly
            if (e instanceof JsonIoException) {
                throw (JsonIoException) e;
            }

            // Wrap other exceptions in a JsonIoException for consistency
            throw new JsonIoException(getErrorMessage(e.getMessage()), e);
        } finally {
            /*
             * Cleanup the resolver's state post-deserialization.
             * This ensures that any internal caches or temporary data structures
             * used during the conversion process are properly cleared.
             */
            if (isRoot) {
                resolver.cleanup();
            }
        }
    }

    /**
     * Returns the current {@link Resolver} instance used for JSON deserialization.
     *
     * <p>The {@code Resolver} serves as the superclass for both {@link ObjectResolver} and {@link MapResolver},
     * each handling specific aspects of converting JSON structures into Java objects.</p>
     *
     * <ul>
     *     <li><strong>ObjectResolver:</strong>
     *         <p>
     *             Responsible for converting JSON maps (represented by {@link JsonObject}) into their corresponding
     *             Java object instances. It handles the instantiation of Java classes and populates their fields based on
     *             the JSON data.
     *         </p>
     *     </li>
     *     <li><strong>MapResolver:</strong>
     *         <p>
     *             Focuses on refining the value side within a map. It utilizes the class information associated with
     *             a {@link JsonObject} to coerce primitive fields in the map's values to their correct data types.
     *             For example, if a {@code Long} is serialized as a {@code String} in the JSON, the {@code MapResolver}
     *             will convert it back to a {@code Long} within the map by matching the JSON key to the corresponding
     *             field in the Java class and transforming the raw JSON primitive value to the field's type (e.g., {@code long}/{@code Long}).
     *         </p>
     *     </li>
     * </ul>
     *
     * <p>
     * This method is essential for scenarios where JSON data needs to be deserialized into Java objects,
     * ensuring that both object instantiation and type coercion are handled appropriately.
     * </p>
     *
     * @return the {@code Resolver} currently in use. This {@code Resolver} is the superclass for
     *         {@code ObjectResolver} and {@code MapResolver}, facilitating the conversion of JSON structures
     *         into Java objects and the coercion of map values to their correct data types.
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
