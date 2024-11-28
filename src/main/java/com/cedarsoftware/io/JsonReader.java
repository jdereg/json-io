package com.cedarsoftware.io;

import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.cedarsoftware.io.util.CollectionArrayConverter;
import com.cedarsoftware.util.CompactLinkedMap;
import com.cedarsoftware.util.CompactLinkedSet;
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
    private final Converter localConverter;
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
        localConverter = new Converter(this.readOptions.getConverterOptions());
        setupLocalConverter();
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
            verifyRootType(rootType);
            returnValue = (T) parser.readValue(rootType);
            if (returnValue == null) {
                return null;    // easy, done.
            }
        } catch (JsonIoException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonIoException(getErrorMessage("error parsing JSON value"), e);
        }
        
        // Handle JSON [] at root
        JsonObject rootObj = null;

        if (returnValue.getClass().isArray()) {
            rootObj = new JsonObject();
            rootObj.setTarget(returnValue);
            rootObj.setItems(returnValue);
        } else if (returnValue instanceof JsonObject && ((JsonObject) returnValue).isArray()) {
            rootObj = (JsonObject) returnValue;
        }

        if (rootObj != null) {
            T graph = resolveObjects(rootObj, rootType);
            if (graph != null) {
                return graph;
            }
            return (T) rootObj.getItems();
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

    /**
     * Initializes the 'convo' Converter with the necessary conversions.
     */
    private void setupLocalConverter() {
        CollectionArrayConverter arrayConverter = new CollectionArrayConverter(localConverter);
        arrayConverter.setupConversions(localConverter);
    }

    /**
     * When return JsonObjects, verify return type
     * @param rootType Class passed as rootType to return type
     */
    private <T> void verifyRootType(Class<T> rootType) {
        if (rootType == null || readOptions.isReturningJavaObjects()) {
            return;
        }

        // If rootType is an array, drill down to the ultimate component type
        Class<?> typeToCheck = rootType;
        if (rootType.isArray()) {
            while (typeToCheck.isArray()) {
                typeToCheck = typeToCheck.getComponentType();
            }
            if (resolver.isConvertable(typeToCheck) || typeToCheck == Object.class) {
                return;
            }
        }

        // Perform the checks on typeToCheck
        if (getResolver().isConvertable(typeToCheck)) {
            return;
        }

        if (Collection.class.isAssignableFrom(typeToCheck)) {
            return;
        }
        
        if (Map.class.isAssignableFrom(typeToCheck)) {
            return;
        }

        throw new JsonIoException("In readOptions.isReturningJsonObjects() mode, the rootType '" + rootType.getName() +
                "' is not supported. Allowed types are:\n" +
                "- null\n" +
                "- primitive types (e.g., int, boolean) and their wrapper classes (e.g., Integer, Boolean)\n" +
                "- types supported by Converter.convert()\n" +
                "- Map or any of its subclasses\n" +
                "- Collection or any of its subclasses\n" +
                "- Arrays (of any depth) of the above types\n" +
                "Please use one of these types as the rootType when readOptions.isReturningJsonObjects() is enabled.");
    }

    /**
     * Determines the appropriate return value based on the provided {@code rootType}, {@code returnJson} option,
     * and the presence of a {@code @type} annotation within the JSON object.
     *
     * <p>The method evaluates several conditions to decide whether to convert the JSON object to a specified
     * type, retain it as a basic JSON structure, or return it as-is. This ensures flexibility and consistency
     * in handling various data types, aligning with developer expectations and standard behaviors of
     * JSON libraries like Jackson and GSON.
     *
     * <p>The following decision table outlines the logic used to determine the return value:
     *
     * <table border="1" cellpadding="5" cellspacing="0">
     *   <thead>
     *     <tr>
     *       <th>Row</th>
     *       <th>C1: {@code rootType} Specified</th>
     *       <th>C2: Conversion Supported</th>
     *       <th>C3: {@code returnJson}</th>
     *       <th>C4: Has {@code @type}</th>
     *       <th>C5: Not a Built-In Primitive</th>
     *       <th>C6: Convertable</th>
     *       <th>C7: {@code @type} Convertible to Basic Type</th>
     *       <th>Action</th>
     *     </tr>
     *   </thead>
     *   <tbody>
     *     <tr>
     *       <td>1</td>
     *       <td><strong>Yes</strong></td>
     *       <td><strong>Yes</strong></td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td><strong>A1:</strong> Convert and return</td>
     *     </tr>
     *     <tr>
     *       <td>2</td>
     *       <td><strong>Yes</strong></td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td><strong>A2:</strong> Return {@code graph}</td>
     *     </tr>
     *     <tr>
     *       <td>3</td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td><strong>Yes</strong></td>
     *       <td><strong>Yes</strong></td>
     *       <td><strong>Yes</strong></td>
     *       <td>-</td>
     *       <td><strong>Yes</strong></td>
     *       <td><strong>A4:</strong> Convert to basic type and return</td>
     *     </tr>
     *     <tr>
     *       <td>4</td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td><strong>Yes</strong></td>
     *       <td><strong>Yes</strong></td>
     *       <td><strong>Yes</strong></td>
     *       <td>-</td>
     *       <td><strong>No</strong></td>
     *       <td><strong>A3:</strong> Return {@code returnValue}</td>
     *     </tr>
     *     <tr>
     *       <td>5</td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td><strong>Yes</strong></td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td><strong>Yes</strong></td>
     *       <td>-</td>
     *       <td><strong>A2:</strong> Return {@code graph}</td>
     *     </tr>
     *     <tr>
     *       <td>6</td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td><strong>Yes</strong></td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td><strong>A3:</strong> Return {@code returnValue}</td>
     *     </tr>
     *     <tr>
     *       <td>7</td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td><strong>A2:</strong> Return {@code graph}</td>
     *     </tr>
     *   </tbody>
     * </table>
     *
     * <h3>Conditions Explained</h3>
     * <ul>
     *   <li><strong>C1:</strong> <em>rootType Specified</em> - Determines if a specific root type was provided.</li>
     *   <li><strong>C2:</strong> <em>Conversion Supported</em> - Checks if conversion from the current type to {@code rootType} is supported.</li>
     *   <li><strong>C3:</strong> <em>returnJson</em> - Indicates if the return should be in JSON object form.</li>
     *   <li><strong>C4:</strong> <em>Has @type</em> - Checks if the JSON object contains a {@code @type} annotation.</li>
     *   <li><strong>C5:</strong> <em>Not a Built-In Primitive</em> - Determines if the resolved graph is a complex type rather than a primitive.</li>
     *   <li><strong>C6:</strong> <em>Convertable</em> - Checks if the graph can be converted to a basic type without {@code @type}.</li>
     *   <li><strong>C7:</strong> <em>{@code @type} Convertible to Basic Type</em> - Determines if the type specified by {@code @type} can be converted to a fundamental JSON type.</li>
     * </ul>
     *
     * <h3>Actions Explained</h3>
     * <ul>
     *   <li><strong>A1:</strong> <em>Convert and return</em> - Converts the graph to the specified {@code rootType} and returns the converted value.</li>
     *   <li><strong>A2:</strong> <em>Return graph</em> - Returns the resolved graph as-is.</li>
     *   <li><strong>A3:</strong> <em>Return {@code returnValue}</em> - Returns the original JSON object without conversion.</li>
     *   <li><strong>A4:</strong> <em>Convert to basic type and return</em> - Converts the graph to a basic type as determined by {@code getJsonSynonymType} and returns the converted value.</li>
     * </ul>
     *
     * <h3>Examples</h3>
     * <ul>
     *   <li>
     *     <strong>Example 1:</strong><br>
     *     <code>JSON Input:</code>
     *     <pre>{@code
     * {"@type":"StringBuilder","value":"json-io"}
     * }</pre>
     *     <code>returnJson:</code> <strong>true</strong><br>
     *     <code>rootType:</code> <strong>null</strong><br>
     *     <strong>Result:</strong> Returns a <code>String</code> with value "json-io".
     *   </li>
     *   <li>
     *     <strong>Example 2:</strong><br>
     *     <code>JSON Input:</code>
     *     <pre>{@code
     * {"@type":"AtomicInteger","value":123}
     * }</pre>
     *     <code>returnJson:</code> <strong>true</strong><br>
     *     <code>rootType:</code> <strong>null</strong><br>
     *     <strong>Result:</strong> Returns an <code>Integer</code> with value 123.
     *   </li>
     *   <li>
     *     <strong>Example 3:</strong><br>
     *     <code>JSON Input:</code>
     *     <pre>{@code
     * {"@type":"UUID","value":"123e4567-e89b-12d3-a456-426614174000"}
     * }</pre>
     *     <code>returnJson:</code> <strong>true</strong><br>
     *     <code>rootType:</code> <strong>null</strong><br>
     *     <strong>Result:</strong> Returns a <code>UUID</code> instance representing the provided UUID string.
     *   </li>
     * </ul>
     *
     * <h3>Decision Table Summary</h3>
     *
     * <table border="1" cellpadding="5" cellspacing="0">
     *   <thead>
     *     <tr>
     *       <th>Row</th>
     *       <th>C1: {@code rootType} Specified</th>
     *       <th>C2: Conversion Supported</th>
     *       <th>C3: {@code returnJson}</th>
     *       <th>C4: Has {@code @type}</th>
     *       <th>C5: Not a Built-In Primitive</th>
     *       <th>C6: Convertable</th>
     *       <th>C7: {@code @type} Convertible to Basic Type</th>
     *       <th><strong>Action</strong></th>
     *     </tr>
     *   </thead>
     *   <tbody>
     *     <tr>
     *       <td>1</td>
     *       <td><strong>Yes</strong></td>
     *       <td><strong>Yes</strong></td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td><strong>A1:</strong> Convert and return</td>
     *     </tr>
     *     <tr>
     *       <td>2</td>
     *       <td><strong>Yes</strong></td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td><strong>A2:</strong> Return {@code graph}</td>
     *     </tr>
     *     <tr>
     *       <td>3</td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td><strong>Yes</strong></td>
     *       <td><strong>Yes</strong></td>
     *       <td><strong>Yes</strong></td>
     *       <td>-</td>
     *       <td><strong>Yes</strong></td>
     *       <td><strong>A4:</strong> Convert to basic type and return</td>
     *     </tr>
     *     <tr>
     *       <td>4</td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td><strong>Yes</strong></td>
     *       <td><strong>Yes</strong></td>
     *       <td><strong>Yes</strong></td>
     *       <td>-</td>
     *       <td><strong>No</strong></td>
     *       <td><strong>A3:</strong> Return {@code returnValue}</td>
     *     </tr>
     *     <tr>
     *       <td>5</td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td><strong>Yes</strong></td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td><strong>Yes</strong></td>
     *       <td>-</td>
     *       <td><strong>A2:</strong> Return {@code graph}</td>
     *     </tr>
     *     <tr>
     *       <td>6</td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td><strong>Yes</strong></td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td><strong>A3:</strong> Return {@code returnValue}</td>
     *     </tr>
     *     <tr>
     *       <td>7</td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td><strong>No</strong></td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td><strong>A2:</strong> Return {@code graph}</td>
     *     </tr>
     *   </tbody>
     * </table>
     *
     * <h3>Decision Paths Explained</h3>
     * <ol>
     *   <li>
     *     <strong>When {@code rootType} is specified:</strong>
     *     <ul>
     *       <li><strong>If</strong> conversion to {@code rootType} is supported, <strong>then</strong> convert and return the converted value (<em>A1</em>).</li>
     *       <li><strong>Else</strong>, return the resolved {@code graph} as-is (<em>A2</em>).</li>
     *     </ul>
     *   </li>
     *   <li>
     *     <strong>When {@code rootType} is not specified and {@code returnJson} is {@code true}:</strong>
     *     <ul>
     *       <li><strong>If</strong> the JSON object has a {@code @type} and the graph is not a built-in primitive:
     *         <ul>
     *           <li><strong>If</strong> the {@code @type} is convertible to a basic type, <strong>then</strong> convert to the basic type and return (<em>A4</em>).</li>
     *           <li><strong>Else</strong>, return the original {@code JsonObject} without conversion (<em>A3</em>).</li>
     *         </ul>
     *       </li>
     *       <li><strong>If</strong> the JSON object does not have a {@code @type}:
     *         <ul>
     *           <li><strong>If</strong> the graph is convertible, <strong>then</strong> return the converted {@code graph} (<em>A2</em>).</li>
     *           <li><strong>Else</strong>, return the original {@code JsonObject} without conversion (<em>A3</em>).</li>
     *         </ul>
     *       </li>
     *     </ul>
     *   </li>
     *   <li>
     *     <strong>When {@code returnJson} is {@code false}:</strong>
     *     <ul>
     *       <li>Always return the resolved {@code graph}, regardless of {@code @type} (<em>A2</em>).</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * <h3>Supported Extended JDK Classes</h3>
     * <ul>
     *   <li><strong>ZonedDateTime:</strong> Converted to {@code ZonedDateTime} instance.</li>
     *   <li><strong>UUID:</strong> Converted to {@code UUID} instance.</li>
     *   <li><strong>Date:</strong> Converted to {@code Date} instance.</li>
     *   <li><strong>BigInteger:</strong> Preserved as {@code BigInteger} to maintain precision and capacity.</li>
     *   <li><strong>BigDecimal:</strong> Preserved as {@code BigDecimal} to maintain precision and capacity.</li>
     *   All conversions supported by java-util Converter are used on simple return types.
     * </ul>
     *
     * <h3>Examples</h3>
     * <ul>
     *   <li>
     *     <strong>Example 1:</strong><br>
     *     <code>JSON Input:</code>
     *     <pre>{@code
     * {"@type":"BigInteger","value":123456789012345678901234567890}
     * }</pre>
     *     <code>returnJson:</code> <strong>true</strong><br>
     *     <code>rootType:</code> <strong>null</strong><br>
     *     <strong>Result:</strong> Returns a <code>BigInteger</code> instance representing the provided value.
     *   </li>
     *   <li>
     *     <strong>Example 2:</strong><br>
     *     <code>JSON Input:</code>
     *     <pre>{@code
     * {"@type":"BigDecimal","value":12345.67890}
     * }</pre>
     *     <code>returnJson:</code> <strong>true</strong><br>
     *     <code>rootType:</code> <strong>null</strong><br>
     *     <strong>Result:</strong> Returns a <code>BigDecimal</code> instance representing the provided value.
     *   </li>
     * </ul>
     *
     * @param <T>         The generic type parameter representing the expected return type.
     * @param rootType    The class of the type that the user expects as the root type. Can be {@code null}.
     * @param returnValue The JSON object to be evaluated and potentially converted.
     * @return The appropriate return value based on the evaluation of the provided parameters and JSON content.
     */
    private <T> T determineReturnValueWhenJsonObjectRoot(Class<T> rootType, T returnValue) {
        boolean returnJson = readOptions.isReturningJsonObjects();
        Converter converter = resolver.getConverter();

        // Special case not mentioned in the Javadocs:
        // 1. rootType argument passed in, is null.
        // 2. returnJsonObjects = true (returnJavaObjects does not use this path)
        // 3. root JSON has {"@type": Collection or Map}
        // 4. item within the Collection or Map would violate the contract of the container.
        // 5. If all that holds true, then add a 'fallback' case for it.  See "fallback" APIs for examples.
        if (returnValue instanceof JsonObject) {
            JsonObject jObj = (JsonObject) returnValue;
            if (shouldFallbackToDefaultType(returnJson, rootType, jObj.getJavaType())) {
                Class<?> fallbackType = getFallbackType(jObj.getJavaType());
                jObj.setJavaType(fallbackType);
            }
        }

        T graph = resolveObjects((JsonObject) returnValue, rootType);

        // Case 1 & 2: rootType is specified
        if (rootType != null) {
            // Attempt conversion with the convo converter
            if (localConverter.isConversionSupportedFor(graph.getClass(), rootType)) {
                return (T) localConverter.convert(graph, rootType);
            }

            // If no conversion is supported, return the graph as-is
            return graph;
        }

        // Cases 3, 4, 5, 6, 7: rootType is not specified
        if (returnJson) {
            JsonObject jsonObject = (JsonObject) returnValue;
            Class<?> javaType = jsonObject.getJavaType();

            if (javaType != null) { // C4: Has @type
                // Check if the javaType is convertable
                if (resolver.isConvertable(javaType)) {
                    // C7: Convert to the basic type if supported
                    Class<?> basicType = getJsonSynonymType(javaType);
                    @SuppressWarnings("unchecked")
                    T converted = (T) converter.convert(returnValue, basicType);
                    return converted;
                }

                // C5: If not built-in primitive and not convertable, return as JsonObject
                if (!isBuiltInPrimitive(graph)) {
                    return returnValue;
                }
            }

            // C6: Convertable without @type or if @type is present but not convertible
            if (resolver.isConvertable(graph.getClass())) {
                return graph;
            } else {
                return returnValue;
            }
        }

        // Case 7: returnJson is false
        return graph; // JavaObjects mode - return resolved graph
    }

    /**
     * Determines whether to fallback to a default type based on the current deserialization mode and type information.
     *
     * @param returnJson the flag indicating if deserialization is in returnJsonObjects mode
     * @param rootType the specified root type, can be {@code null}
     * @param javaType the Java type extracted from the JsonObject, can be {@code null}
     * @return {@code true} if fallback to default type is necessary; {@code false} otherwise
     */
    private boolean shouldFallbackToDefaultType(boolean returnJson, Class<?> rootType, Class<?> javaType) {
        return returnJson && rootType == null && javaType != null && getFallbackType(javaType) != null;
    }

    /**
     * Retrieves the fallback type for a given unsupported Java type.
     *
     * @param javaType the original Java type extracted from the JsonObject
     * @return the fallback Java type to be used instead of the unsupported type
     */
    private Class<?> getFallbackType(Class<?> javaType) {
        if (SortedSet.class.isAssignableFrom(javaType)) {
            return CompactLinkedSet.class;
        } else if (SortedMap.class.isAssignableFrom(javaType)) {
            return CompactLinkedMap.class;
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
        return cls.isPrimitive() ||
                cls == String.class ||
                cls == Boolean.class ||
                cls == Byte.class ||
                cls == Short.class ||
                cls == Integer.class ||
                cls == Long.class ||
                cls == Float.class ||
                cls == Double.class ||
                cls == Character.class ||
                cls == BigDecimal.class ||
                cls == BigInteger.class;
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
     * @implNote
     * <p>This method is designed to handle both scenarios where type information is provided and where it
     * is absent. It ensures robust deserialization by falling back to generic types when necessary and by
     * leveraging type conversion mappings defined within the converter.</p>
     */
    @SuppressWarnings("unchecked")
    protected <T> T resolveObjects(JsonObject rootObj, Class<T> rootType) {
        try {
            // Determine the root type if not explicitly provided
            if (rootType == null) {
                rootType = rootObj.getJavaType() == null ? (Class<T>) Object.class : (Class<T>) rootObj.getJavaType();
            }

            // Delegate the conversion to the resolver using the converter
            T value = resolver.toJavaObjects(rootObj, rootType);
            return value;
        } catch (Exception e) {
            // Safely close the stream if the read options specify to do so
            if (readOptions.isCloseStream()) {
                MetaUtils.safelyIgnoreException(this::close);
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
            resolver.cleanup();
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
