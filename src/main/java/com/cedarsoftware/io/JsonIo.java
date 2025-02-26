package com.cedarsoftware.io;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.cedarsoftware.io.prettyprint.JsonPrettyPrinter;
import com.cedarsoftware.util.Convention;
import com.cedarsoftware.util.FastByteArrayInputStream;
import com.cedarsoftware.util.FastByteArrayOutputStream;
import com.cedarsoftware.util.convert.Converter;
import com.cedarsoftware.util.convert.DefaultConverterOptions;

/**
 * JsonIo is the main entry point for converting between JSON and Java objects.
 * <p>
 * This API uses a fluent builder pattern that splits JSON conversion into two parts:
 * <ol>
 *   <li>The <b>initial conversion</b>:
 *       <ul>
 *         <li>For writing, you convert a Java object (or an intermediate {@code JsonObject}/{@code Map})
 *             to JSON (either as a {@code String} or directly to an {@code OutputStream}).</li>
 *         <li>For reading, you start by parsing JSON from a source ({@code String}, {@code InputStream}, or an in-memory {@code JsonObject}),
 *             and obtain a builder.</li>
 *       </ul>
 *   </li>
 *   <li>The <b>completion</b>:
 *       <ul>
 *         <li>Call either {@code asClass()} (for a concrete target class) or {@code asType()}
 *             (to support generic types via a {@code TypeHolder}) to complete the conversion.</li>
 *       </ul>
 *   </li>
 * </ol>
 * <p>
 * <i>Note: JsonIo includes extensive type conversion capabilities for both primitive and complex types.
 * To view the complete list of supported conversions, examine the output of
 * {@link #main(String[])} or simply run the class directly from the command line.</i>
 * <p>
 * <b>Usage Examples:</b>
 * <ul>
 *   <li>
 *     <b>1. Converting a Java object to JSON:</b>
 *     <pre>
 *     // Convert a Java object to a JSON String:
 *     String json = JsonIo.toJson(myObject, writeOptions);
 *
 *     // Write a Java object as JSON to an OutputStream:
 *     JsonIo.toJson(outputStream, myObject, writeOptions);
 *     </pre>
 *   </li>
 *   <li>
 *     <b>2. Reading JSON into fully resolved Java objects:</b>
 *     <pre>
 *     // From a JSON String into a specific class:
 *     Person person = JsonIo.toJava(jsonString, readOptions).asClass(Person.class);
 *
 *     // From an InputStream into a generic type (using TypeHolder):
 *     List&lt;Person&gt; people = JsonIo.toJava(inputStream, readOptions)
 *                                    .asType(new TypeHolder&lt;List&lt;Person&gt;&gt;(){});
 *     </pre>
 *   </li>
 *   <li>
 *     <b>3. Reading JSON into an intermediate representation (JsonObject/Map):</b>
 *     <pre>
 *     // Configure the ReadOptions to force returning JsonObjects (typically Maps)
 *     ReadOptions read = new ReadOptionsBuilder()
 *                                           .returnAsJsonObjects()
 *                                           .build();
 *
 *     // Parse the JSON into a Map (or nested Map structure)
 *     Map&lt;String, Object&gt; jsonMap = JsonIo.toJava(jsonString, read).asClass(Map.class);
 *     </pre>
 *     In this case, simple Java types may be converted (or left as is for {@code Map} types),
 *     and {@code Collection} types will be converted appropriately.
 *   </li>
 * </ul>
 * <p>
 * <b>Note:</b>
 * <ul>
 *   <li>The builder returned by the initial JSON reading call must be completed with a call
 *       to either {@code asClass()} or {@code asType()} to perform the final conversion.</li>
 *   <li>The nature of the resulting object—whether it is a fully resolved Java object or an intermediate
 *       representation (typically a {@code Map})—is determined by the {@code ReadOptionBuilder}
 *       settings ({@code returnAsJavaObjects()} or {@code returnAsJsonObjects()}) passed to the call.</li>
 * </ul>
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
public class JsonIo {

    private JsonIo() {}

    /**
     * Converts a Java object to a JSON string representation.
     * <p>
     * This method serializes the provided source object into a JSON string according to the
     * specified write options. The source object can be any Java object, including primitives,
     * collections, arrays, custom classes, or even an intermediate JsonObject (Map) representation
     * that was obtained from a previous read operation.
     *
     * <h3>Examples:</h3>
     * <pre>
     * // Basic usage with default options
     * String json = JsonIo.toJson(myObject, null);
     *
     * // With custom write options
     * WriteOptions options = new WriteOptionsBuilder()
     *     .prettyPrint(true)
     *     .showTypeInfoMinimal()
     *     .build();
     * String json = JsonIo.toJson(myObject, options);
     *
     * // Converting a Map/JsonObject to JSON
     * Map&lt;String, Object&gt; map = JsonIo.toJava(jsonString, readOptions).asClass(Map.class);
     * // ... modify the map ...
     * String updatedJson = JsonIo.toJson(map, writeOptions);
     * </pre>
     *
     * @param srcObject the Java object to convert to JSON; can be any object including primitives,
     *                  collections, custom classes, or a JsonObject/Map
     * @param writeOptions configuration options for controlling the JSON output format;
     *                     if null, default options will be used
     * @return a JSON string representation of the source object
     * @throws JsonIoException if an error occurs during the serialization process
     */
    public static String toJson(Object srcObject, WriteOptions writeOptions) {
        FastByteArrayOutputStream out = new FastByteArrayOutputStream();
        try (JsonWriter writer = new JsonWriter(out, writeOptions)) {
            writer.write(srcObject);
            return out.toString();
        } catch (JsonIoException je) {
            throw je;
        } catch (Exception e) {
            throw new JsonIoException("Unable to convert object to JSON", e);
        }
    }

    /**
     * Writes a Java object as JSON directly to an OutputStream.
     * <p>
     * This method serializes the provided source object into JSON and writes the result to the specified
     * output stream. This is useful for scenarios where you want to stream JSON output directly to a file,
     * network connection, or other destination without creating an intermediate string representation.
     * <p>
     * By default, the output stream is closed after writing. To keep it open (e.g., when writing multiple
     * objects), configure the write options with {@code writeOptions.closeStream(false)}.
     *
     * <h3>Examples:</h3>
     * <pre>
     * // Write JSON to a file
     * try (FileOutputStream fos = new FileOutputStream("data.json")) {
     *     JsonIo.toJson(fos, myObject, writeOptions);
     * }
     *
     * // Stream multiple objects without closing between writes
     * WriteOptions options = new WriteOptionsBuilder()
     *     .closeStream(false)
     *     .build();
     *
     * for (MyObject obj : objects) {
     *     JsonIo.toJson(outputStream, obj, options);
     *     // The stream remains open for the next object
     * }
     * outputStream.close(); // Close manually when done
     * </pre>
     *
     * @param out the output stream where the JSON will be written; must not be null
     * @param source the Java object to convert to JSON
     * @param writeOptions configuration options controlling the JSON output format;
     *                     if null, default options will be used
     * @throws JsonIoException if an error occurs during serialization
     * @throws IllegalArgumentException if the output stream is null
     */
    public static void toJson(OutputStream out, Object source, WriteOptions writeOptions) {
        Convention.throwIfNull(out, "OutputStream cannot be null");
        if (writeOptions == null) {
            writeOptions = WriteOptionsBuilder.getDefaultWriteOptions();
        }
        JsonWriter writer = null;
        try {
            writer = new JsonWriter(out, writeOptions);
            writer.write(source);
        } catch (Exception e) {
            throw new JsonIoException("Unable to convert object and send in JSON format to OutputStream.", e);
        }
        finally {
            if (writeOptions.isCloseStream()) {
                if (writer != null) {
                    writer.close();
                }
            }
        }
    }

    /**
     * Begins the process of converting a JSON string to Java objects.
     * <p>
     * This method is the first step in a two-step JSON parsing process. It takes a JSON string and
     * parsing options, and returns a builder that can complete the conversion to the desired Java type.
     * The returned builder provides two completion methods:
     * <ul>
     *   <li>{@code asClass(Class)} - For converting to a specific class</li>
     *   <li>{@code asType(TypeHolder)} - For converting to a generic type like {@code List<Person>}</li>
     * </ul>
     *
     * <h3>Examples:</h3>
     * <pre>
     * // Parse JSON to a specific class
     * Person person = JsonIo.toJava(jsonString, options).asClass(Person.class);
     *
     * // Parse to a generic collection type
     * List&lt;Person&gt; people = JsonIo.toJava(jsonString, options)
     *                               .asType(new TypeHolder&lt;List&lt;Person&gt;&gt;(){});
     *
     * // Parse to an intermediate Map representation
     * ReadOptions mapOptions = new ReadOptionsBuilder()
     *                              .returnAsJsonObjects()
     *                              .build();
     * Map&lt;String, Object&gt; dataMap = JsonIo.toJava(jsonString, mapOptions).asClass(Map.class);
     * </pre>
     *
     * <p>The behavior of this method is controlled by the {@code ReadOptions}. In particular:
     * <ul>
     *   <li>With {@code ReadOptions.returnAsJavaObjects()} (default), the result will be fully
     *       instantiated Java objects of the specified type.</li>
     *   <li>With {@code ReadOptions.returnAsJsonObjects()}, the result will be the intermediate Map
     *       representation (JsonObjects) that can later be manipulated or converted.</li>
     * </ul>
     *
     * @param json the JSON string to parse; if null, an empty string will be used
     * @param readOptions configuration options for controlling how the JSON is parsed;
     *                    if null, default options will be used
     * @return a builder to complete the conversion by specifying the target type
     */
    public static JavaStringBuilder toJava(String json, ReadOptions readOptions) {
        return new JavaStringBuilder(json, readOptions);
    }

    /**
     * Begins the process of converting JSON from an InputStream to Java objects.
     * <p>
     * This method is the first step in a two-step JSON parsing process. It takes a JSON input stream and
     * parsing options, and returns a builder that can complete the conversion to the desired Java type.
     * The returned builder provides two completion methods:
     * <ul>
     *   <li>{@code asClass(Class)} - For converting to a specific class</li>
     *   <li>{@code asType(TypeHolder)} - For converting to a generic type like {@code List<Person>}</li>
     * </ul>
     *
     * <p>By default, the input stream is closed after reading. To keep it open (e.g., when reading multiple
     * JSON objects), configure the read options with {@code readOptions.closeStream(false)}.
     *
     * <h3>Examples:</h3>
     * <pre>
     * // Parse JSON from a file
     * try (FileInputStream fis = new FileInputStream("data.json")) {
     *     Person person = JsonIo.toJava(fis, options).asClass(Person.class);
     * }
     *
     * // Parse JSON from a stream to a generic type
     * List&lt;Person&gt; people = JsonIo.toJava(inputStream, options)
     *                               .asType(new TypeHolder&lt;List&lt;Person&gt;&gt;(){});
     * </pre>
     *
     * @param in the input stream containing JSON; must not be null
     * @param readOptions configuration options for controlling how the JSON is parsed;
     *                    if null, default options will be used
     * @return a builder to complete the conversion by specifying the target type
     * @throws IllegalArgumentException if the input stream is null
     */
    public static JavaStreamBuilder toJava(InputStream in, ReadOptions readOptions) {
        return new JavaStreamBuilder(in, readOptions);
    }

    /**
     * Begins the process of converting a JsonObject (Map representation) to fully resolved Java objects.
     * <p>
     * This method is part of the two-phase parsing capability of JsonIo. It takes a JsonObject (typically a Map
     * structure that was returned from a previous call with {@code ReadOptions.returnAsJsonObjects()}) and
     * converts it to a specific Java type. This allows you to examine and modify the parsed JSON structure
     * before finalizing the conversion to Java objects.
     * <p>
     * The returned builder provides two completion methods:
     * <ul>
     *   <li>{@code asClass(Class)} - For converting to a specific class</li>
     *   <li>{@code asType(TypeHolder)} - For converting to a generic type like {@code List<Person>}</li>
     * </ul>
     *
     * <h3>Examples:</h3>
     * <pre>
     * // First parse JSON to a Map representation
     * ReadOptions mapOptions = new ReadOptionsBuilder()
     *                              .returnAsJsonObjects()
     *                              .build();
     * Map&lt;String, Object&gt; jsonMap = JsonIo.toJava(jsonString, mapOptions).asClass(Map.class);
     *
     * // Modify the map structure if needed
     * jsonMap.put("extraProperty", "new value");
     *
     * // Then convert the modified structure to Java objects
     * Person person = JsonIo.toJava(jsonMap, readOptions).asClass(Person.class);
     * </pre>
     *
     * @param jsonObject the JsonObject (typically a Map) to convert; must not be null
     * @param readOptions configuration options for controlling the conversion;
     *                    if null, default options will be used
     * @return a builder to complete the conversion by specifying the target type
     * @throws IllegalArgumentException if the jsonObject is null
     */
    public static JavaObjectBuilder toJava(JsonObject jsonObject, ReadOptions readOptions) {
        return new JavaObjectBuilder(jsonObject, readOptions);
    }

    /**
     * Formats a JSON string with proper indentation and line breaks for readability.
     * <p>
     * This method takes a potentially minified or poorly formatted JSON string and converts it
     * to a well-formatted, human-readable version with proper indentation and line breaks.
     * This is useful for debugging, logging, or displaying JSON in a user interface.
     * <p>
     * Note that the formatting is purely cosmetic and does not change the semantic meaning
     * of the JSON content.
     *
     * <h3>Example:</h3>
     * <pre>
     * String minifiedJson = "{\"name\":\"John\",\"age\":30,\"address\":{\"city\":\"New York\",\"zip\":\"10001\"}}";
     * String prettyJson = JsonIo.formatJson(minifiedJson);
     * System.out.println(prettyJson);
     * // Output:
     * // {
     * //   "name": "John",
     * //   "age": 30,
     * //   "address": {
     * //     "city": "New York",
     * //     "zip": "10001"
     * //   }
     * // }
     * </pre>
     *
     * @param json the JSON string to format; if invalid JSON is provided, the method may throw an exception
     * @return a formatted, indented JSON string for improved readability
     */
    public static String formatJson(String json) {
        return JsonPrettyPrinter.prettyPrint(json);
    }

    /**
     * Creates a deep copy of an object by serializing it to JSON and then deserializing it back.
     * <p>
     * This method provides a convenient way to create a completely detached copy of an object graph.
     * It works by converting the object to JSON and then back to a new instance, effectively "cloning"
     * the entire object structure including all nested objects.
     * <p>
     * This approach can be particularly useful when you need to:
     * <ul>
     *   <li>Create a true deep copy of a complex object graph</li>
     *   <li>Detach an object from its original references</li>
     *   <li>Create a snapshot of an object's state</li>
     * </ul>
     *
     * <h3>Example:</h3>
     * <pre>
     * // Create a deep copy of an object
     * Person original = new Person("John", 30);
     * Person copy = JsonIo.deepCopy(original, null, null);
     *
     * // Verify the copy is independent
     * original.setName("Jane");
     * System.out.println(copy.getName()); // Still "John"
     * </pre>
     *
     * <p>The method sets special write options to ensure proper copying, but you can provide
     * custom read and write options to further control the process if needed.
     *
     * @param <T> the type of the object being copied
     * @param source the object to copy; if null, null will be returned
     * @param readOptions options for controlling deserialization; if null, default options will be used
     * @param writeOptions options for controlling serialization; if null, default options will be used
     * @return a deep copy of the original object, or null if the source was null
     */
    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(Object source, ReadOptions readOptions, WriteOptions writeOptions) {
        if (source == null) {
            return null;
        }

        writeOptions = new WriteOptionsBuilder(writeOptions).showTypeInfoMinimal().shortMetaKeys(true).build();
        if (readOptions == null) {
            readOptions = ReadOptionsBuilder.getDefaultReadOptions();
        }

        String json = toJson(source, writeOptions);
        return (T) toJava(json, readOptions).asClass(source.getClass());
    }

    /**
     * Converts a JSON string to a Java object of the specified class.
     * <p>
     * <i>Note: This method will be deprecated in a future release. Please use the {@link #toJava(String, ReadOptions)}
     * method with {@code asClass()} or {@code asType()} instead.</i>
     * <p>
     * This method parses the provided JSON string and converts it to an instance of the specified class.
     *
     * <h3>Example:</h3>
     * <pre>
     * // Legacy approach:
     * Person person = JsonIo.toObjects(jsonString, readOptions, Person.class);
     *
     * // Recommended new approach:
     * Person person = JsonIo.toJava(jsonString, readOptions).asClass(Person.class);
     * </pre>
     *
     * @param <T> the type of the resulting Java object
     * @param json the JSON string to parse
     * @param readOptions options for controlling the parsing; if null, default options will be used
     * @param rootType the class to convert the JSON to
     * @return an instance of the specified class populated from the JSON
     */
    public static <T> T toObjects(String json, ReadOptions readOptions, Class<T> rootType) {
        return toJava(json, readOptions).asClass(rootType);
    }

    /**
     * Converts JSON from an InputStream to a Java object of the specified class.
     * <p>
     * <i>Note: This method will be deprecated in a future release. Please use the {@link #toJava(InputStream, ReadOptions)}
     * method with {@code asClass()} or {@code asType()} instead.</i>
     * <p>
     * This method reads JSON from the provided input stream and converts it to an instance of the specified class.
     *
     * <h3>Example:</h3>
     * <pre>
     * // Legacy approach:
     * Person person = JsonIo.toObjects(inputStream, readOptions, Person.class);
     *
     * // Recommended new approach:
     * Person person = JsonIo.toJava(inputStream, readOptions).asClass(Person.class);
     * </pre>
     *
     * @param <T> the type of the resulting Java object
     * @param in the input stream containing JSON
     * @param readOptions options for controlling the parsing; if null, default options will be used
     * @param rootType the class to convert the JSON to
     * @return an instance of the specified class populated from the JSON
     */
    public static <T> T toObjects(InputStream in, ReadOptions readOptions, Class<T> rootType) {
        return toJava(in, readOptions).asClass(rootType);
    }

    /**
     * Converts a JsonObject (Map representation) to a Java object of the specified class.
     * <p>
     * <i>Note: This method will be deprecated in a future release. Please use the {@link #toJava(JsonObject, ReadOptions)}
     * method with {@code asClass()} or {@code asType()} instead.</i>
     * <p>
     * This method converts the provided JsonObject to an instance of the specified class.
     *
     * <h3>Example:</h3>
     * <pre>
     * // Legacy approach:
     * Person person = JsonIo.toObjects(jsonObject, readOptions, Person.class);
     *
     * // Recommended new approach:
     * Person person = JsonIo.toJava(jsonObject, readOptions).asClass(Person.class);
     * </pre>
     *
     * @param <T> the type of the resulting Java object
     * @param jsonObject the JsonObject to convert
     * @param readOptions options for controlling the conversion; if null, default options will be used
     * @param rootType the class to convert the JsonObject to
     * @return an instance of the specified class populated from the JsonObject
     */
    public static <T> T toObjects(JsonObject jsonObject, ReadOptions readOptions, Class<T> rootType) {
        return toJava(jsonObject, readOptions).asClass(rootType);
    }

    /**
     * Builder for converting a JSON string to Java objects.
     * <p>
     * This builder completes the JSON parsing process started by {@link #toJava(String, ReadOptions)}.
     * It provides methods to specify the target type for the deserialization.
     */
    public static final class JavaStringBuilder {
        private final String json;
        private final ReadOptions readOptions;

        JavaStringBuilder(String json, ReadOptions readOptions) {
            this.json = json != null ? json : "";
            this.readOptions = readOptions != null ? readOptions : ReadOptionsBuilder.getDefaultReadOptions();
        }

        /**
         * Completes the JSON parsing by specifying a target class.
         * <p>
         * This method finishes the conversion process by parsing the JSON string into an
         * instance of the specified class. The method is suitable for converting to:
         * <ul>
         *   <li>Concrete Java classes (e.g., {@code Person.class})</li>
         *   <li>Primitive wrapper classes (e.g., {@code Integer.class})</li>
         *   <li>Interface types like {@code Map.class} or {@code List.class} (which will use appropriate implementations)</li>
         * </ul>
         * <p>
         * For generic types like {@code List<Person>}, use the {@link #asType(TypeHolder)} method instead.
         *
         * <h3>Examples:</h3>
         * <pre>
         * // Convert to a specific class
         * Person person = JsonIo.toJava(jsonString, readOptions).asClass(Person.class);
         *
         * // Convert to a Map
         * Map&lt;String, Object&gt; map = JsonIo.toJava(jsonString, readOptions).asClass(Map.class);
         *
         * // Convert to a List (note: without generic type information)
         * List list = JsonIo.toJava(jsonString, readOptions).asClass(List.class);
         * </pre>
         *
         * <p>The behavior is affected by the ReadOptions:
         * <ul>
         *   <li>With {@code returnAsJavaObjects()} (default), this method attempts to create fully
         *       instantiated objects of the specified class.</li>
         *   <li>With {@code returnAsJsonObjects()}, this method returns an intermediate representation,
         *       typically Map objects, that can be manipulated before further processing.</li>
         * </ul>
         *
         * @param <T> the type to convert the JSON to
         * @param clazz the target class; if null, the type will be inferred from the JSON
         * @return an instance of the specified class populated from the JSON
         * @throws JsonIoException if an error occurs during parsing or conversion
         */
        public <T> T asClass(Class<T> clazz) {
            return asType(TypeHolder.forClass(clazz));
        }

        /**
         * Completes the JSON parsing by specifying a generic type.
         * <p>
         * This method is particularly useful for handling generic types like {@code List<Person>}
         * or {@code Map<String, List<Integer>>}, where the full type information cannot be
         * expressed with a simple Class object.
         * <p>
         * The TypeHolder captures the complete generic type information at compile time,
         * allowing JsonIo to properly handle the generics during deserialization.
         *
         * <h3>Examples:</h3>
         * <pre>
         * // Convert to a List of Person objects
         * List&lt;Person&gt; people = JsonIo.toJava(jsonString, readOptions)
         *                              .asType(new TypeHolder&lt;List&lt;Person&gt;&gt;(){});
         *
         * // Convert to a Map with generic parameters
         * Map&lt;String, List&lt;Integer&gt;&gt; map = JsonIo.toJava(jsonString, readOptions)
         *                                          .asType(new TypeHolder&lt;Map&lt;String, List&lt;Integer&gt;&gt;&gt;(){});
         * </pre>
         *
         * @param <T> the type to convert the JSON to
         * @param typeHolder a TypeHolder instance capturing the full generic type
         * @return an object of the specified type populated from the JSON
         * @throws JsonIoException if an error occurs during parsing or conversion
         */
        public <T> T asType(TypeHolder<T> typeHolder) {
            try {
                return parseJson(typeHolder);
            } catch (JsonIoException je) {
                throw je;
            } catch (Exception e) {
                throw new JsonIoException(e);
            }
        }

        private <T> T parseJson(TypeHolder<T> typeHolder) throws Exception {
            FastByteArrayInputStream in = new FastByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            JsonReader jr = new JsonReader(in, readOptions);
            T root = jr.readObject(typeHolder.getType());
            if (readOptions.isCloseStream()) {
                jr.close();
            }
            return root;
        }
    }

    /**
     * Builder for converting JSON from an InputStream to Java objects.
     * <p>
     * This builder completes the JSON parsing process started by {@link #toJava(InputStream, ReadOptions)}.
     * It provides methods to specify the target type for the deserialization.
     */
    public static final class JavaStreamBuilder {
        private final InputStream in;
        private final ReadOptions readOptions;

        JavaStreamBuilder(InputStream in, ReadOptions readOptions) {
            Convention.throwIfNull(in, "InputStream cannot be null");
            this.in = in;
            this.readOptions = readOptions != null ? readOptions : ReadOptionsBuilder.getDefaultReadOptions();
        }

        /**
         * Completes the JSON parsing by specifying a target class.
         * <p>
         * This method finishes the conversion process by parsing the JSON from the input stream into an
         * instance of the specified class. The method is suitable for converting to:
         * <ul>
         *   <li>Concrete Java classes (e.g., {@code Person.class})</li>
         *   <li>Primitive wrapper classes (e.g., {@code Integer.class})</li>
         *   <li>Interface types like {@code Map.class} or {@code List.class} (which will use appropriate implementations)</li>
         * </ul>
         * <p>
         * For generic types like {@code List<Person>}, use the {@link #asType(TypeHolder)} method instead.
         *
         * <h3>Examples:</h3>
         * <pre>
         * // Read from a file
         * try (FileInputStream fis = new FileInputStream("data.json")) {
         *     Person person = JsonIo.toJava(fis, readOptions).asClass(Person.class);
         * }
         * </pre>
         *
         * @param <T> the type to convert the JSON to
         * @param clazz the target class; if null, the type will be inferred from the JSON
         * @return an instance of the specified class populated from the JSON
         * @throws JsonIoException if an error occurs during parsing or conversion
         */
        public <T> T asClass(Class<T> clazz) {
            return asType(TypeHolder.forClass(clazz));
        }

        /**
         * Completes the JSON parsing by specifying a generic type.
         * <p>
         * This method is particularly useful for handling generic types like {@code List<Person>}
         * or {@code Map<String, List<Integer>>}, where the full type information cannot be
         * expressed with a simple Class object.
         * <p>
         * The TypeHolder captures the complete generic type information at compile time,
         * allowing JsonIo to properly handle the generics during deserialization.
         *
         * <h3>Examples:</h3>
         * <pre>
         * // Read from a network stream
         * List&lt;Person&gt; people = JsonIo.toJava(inputStream, readOptions)
         *                              .asType(new TypeHolder&lt;List&lt;Person&gt;&gt;(){});
         * </pre>
         *
         * @param <T> the type to convert the JSON to
         * @param typeHolder a TypeHolder instance capturing the full generic type
         * @return an object of the specified type populated from the JSON
         * @throws JsonIoException if an error occurs during parsing or conversion
         */
        public <T> T asType(TypeHolder<T> typeHolder) {
            JsonReader jr = null;
            try {
                jr = new JsonReader(in, readOptions);
                T root = jr.readObject(typeHolder.getType());
                return root;
            } catch (JsonIoException je) {
                throw je;
            } catch (Exception e) {
                throw new JsonIoException(e);
            } finally {
                if (jr != null && readOptions.isCloseStream()) {
                    jr.close();
                }
            }
        }
    }

    /**
     * Builder for converting a JsonObject (Map representation) to fully resolved Java objects.
     * <p>
     * This builder completes the conversion process started by {@link #toJava(JsonObject, ReadOptions)}.
     * It provides methods to specify the target type for the conversion from the intermediate
     * JsonObject representation to fully resolved Java objects.
     */
    public static final class JavaObjectBuilder {
        private final JsonObject jsonObject;
        private final ReadOptions readOptions;

        JavaObjectBuilder(JsonObject jsonObject, ReadOptions readOptions) {
            Convention.throwIfNull(jsonObject, "JsonObject cannot be null");
            this.jsonObject = jsonObject;
            this.readOptions = readOptions != null ? readOptions : ReadOptionsBuilder.getDefaultReadOptions();
        }

        /**
         * Completes the conversion by specifying a target class.
         * <p>
         * This method converts the JsonObject (Map representation) into an instance of the
         * specified class. This is useful when you have a JsonObject obtained from a previous
         * parsing step (using {@code ReadOptions.returnAsJsonObjects()}) and want to convert
         * it to a concrete Java object.
         * <p>
         * This method is suitable for converting to:
         * <ul>
         *   <li>Concrete Java classes (e.g., {@code Person.class})</li>
         *   <li>Primitive wrapper classes (e.g., {@code Integer.class})</li>
         *   <li>Interface types like {@code Map.class} or {@code List.class} (which will use appropriate implementations)</li>
         * </ul>
         * <p>
         * For generic types like {@code List<Person>}, use the {@link #asType(TypeHolder)} method instead.
         *
         * <h3>Example:</h3>
         * <pre>
         * // First parse JSON to a Map representation
         * ReadOptions mapOptions = new ReadOptionsBuilder()
         *                              .returnAsJsonObjects()
         *                              .build();
         * Map&lt;String, Object&gt; jsonMap = JsonIo.toJava(jsonString, mapOptions).asClass(Map.class);
         *
         * // Modify the map
         * jsonMap.put("age", 31);
         *
         * // Then convert to a Java object
         * Person person = JsonIo.toJava(jsonMap, readOptions).asClass(Person.class);
         * </pre>
         *
         * @param <T> the type to convert the JsonObject to
         * @param clazz the target class; if null, the type will be inferred from the JsonObject
         * @return an instance of the specified class populated from the JsonObject
         */
        public <T> T asClass(Class<T> clazz) {
            return asType(TypeHolder.forClass(clazz));
        }

        /**
         * Completes the conversion by specifying a generic type.
         * <p>
         * This method is particularly useful for handling generic types like {@code List<Person>}
         * or {@code Map<String, List<Integer>>}, where the full type information cannot be
         * expressed with a simple Class object.
         * <p>
         * The TypeHolder captures the complete generic type information at compile time,
         * allowing JsonIo to properly handle the generics during conversion.
         *
         * <h3>Example:</h3>
         * <pre>
         * // First parse JSON to a Map representation
         * ReadOptions mapOptions = new ReadOptionsBuilder()
         *                              .returnAsJsonObjects()
         *                              .build();
         * Map&lt;String, Object&gt; jsonMap = JsonIo.toJava(jsonString, mapOptions).asClass(Map.class);
         *
         * // Then convert to a generic collection
         * List&lt;Person&gt; people = JsonIo.toJava(jsonMap, readOptions)
         *                             .asType(new TypeHolder&lt;List&lt;Person&gt;&gt;(){});
         * </pre>
         *
         * @param <T> the type to convert the JsonObject to
         * @param typeHolder a TypeHolder instance capturing the full generic type. It can be null, and JsonIo will
         *                   do it's best to infer type's/classes, though we recommend passing a Type.
         * @return an object of the specified type populated from the JsonObject
         */
        public <T> T asType(TypeHolder<T> typeHolder) {
            ReadOptions effectiveOptions = readOptions;
            if (!effectiveOptions.isReturningJavaObjects()) {
                effectiveOptions = new ReadOptionsBuilder(effectiveOptions).returnAsJavaObjects().build();
            }

            JsonReader reader = new JsonReader(effectiveOptions);
            return reader.resolveObjects(jsonObject, typeHolder.getType());
        }
    }

    /**
     * Displays a list of all supported type conversions in JsonIo.
     * <p>
     * When executed directly, this method prints out a comprehensive JSON representation of all
     * the type conversions supported by the underlying {@code Converter} used by JsonIo. This includes
     * conversions between primitive types, temporal types, collections, and more specialized
     * Java types.
     * <p>
     * The extensive conversion capabilities are powered by the
     * <a href="https://github.com/jdereg/java-util">java-util</a> library's {@code Converter} framework,
     * which provides a robust system for transforming between nearly any Java types.
     * <p>
     * <b>Pro Tip:</b> Run this method to see the full range of automatic type conversions
     * available in JsonIo. This can be helpful when working with heterogeneous data or when
     * you need to convert between different Java types during deserialization.
     * <pre>
     * java -cp your-classpath com.cedarsoftware.io.JsonIo
     * </pre>
     *
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        String json = toJson(new Converter(new DefaultConverterOptions()).getSupportedConversions(),
                new WriteOptionsBuilder().prettyPrint(true).showTypeInfoNever().build());
        System.out.println("json-io supported conversions (source type to target types):");
        System.out.println(json);
    }
}