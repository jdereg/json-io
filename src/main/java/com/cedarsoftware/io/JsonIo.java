package com.cedarsoftware.io;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.io.prettyprint.JsonPrettyPrinter;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.Convention;
import com.cedarsoftware.util.FastByteArrayInputStream;
import com.cedarsoftware.util.FastByteArrayOutputStream;
import com.cedarsoftware.util.TypeHolder;
import com.cedarsoftware.util.convert.Converter;
import com.cedarsoftware.util.convert.DefaultConverterOptions;

/**
 * <h1>JsonIo Main API</h1>
 *
 * <p>
 * JsonIo is the primary API for converting between JSON and Java object graphs. It supports both
 * serialization (Java &rarr; JSON) and deserialization (JSON &rarr; Java) using a variety of input
 * and output forms.
 * </p>
 *
 * <h2>Conversion Options</h2>
 * <table style="border-collapse: collapse; border: 1px solid gray;" cellpadding="4" cellspacing="0">
 *   <thead>
 *     <tr style="background-color: #ddd;">
 *       <th style="border: 1px solid gray;">Conversion Type</th>
 *       <th style="border: 1px solid gray;">Input</th>
 *       <th style="border: 1px solid gray;">Output</th>
 *       <th style="border: 1px solid gray;">Example API</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td style="border: 1px solid gray;" rowspan="2"><b>Serialization</b></td>
 *       <td style="border: 1px solid gray;">Java Object or JsonObject (Map-of-Maps)</td>
 *       <td style="border: 1px solid gray;">JSON String</td>
 *       <td style="border: 1px solid gray;">
 *         <pre>
 * String json = JsonIo.toJson(javaObject, writeOptions);
 *         </pre>
 *       </td>
 *     </tr>
 *     <tr>
 *       <td style="border: 1px solid gray;">Java Object or JsonObject</td>
 *       <td style="border: 1px solid gray;">JSON to OutputStream</td>
 *       <td style="border: 1px solid gray;">
 *         <pre>
 * JsonIo.toJson(outputStream, javaObject, writeOptions);
 *         </pre>
 *       </td>
 *     </tr>
 *     <tr>
 *       <td style="border: 1px solid gray;" rowspan="3"><b>Deserialization</b></td>
 *       <td style="border: 1px solid gray;">JSON (String or InputStream)</td>
 *       <td style="border: 1px solid gray;">Java Object or JsonObject (Map-of-Maps)</td>
 *       <td style="border: 1px solid gray;">
 *         <pre>
 * MyClass obj = JsonIo.toObjects(json, readOptions, MyClass.class);
 *         </pre>
 *       </td>
 *     </tr>
 *     <tr>
 *       <td style="border: 1px solid gray;">JSON (String or InputStream)</td>
 *       <td style="border: 1px solid gray;">Java Objects (DTOs) via resolution</td>
 *       <td style="border: 1px solid gray;">
 *         <pre>
 * MyClass obj = JsonIo.toObjects(json, readOptions, MyClass.class);
 *         </pre>
 *       </td>
 *     </tr>
 *     <tr>
 *       <td style="border: 1px solid gray;">JsonObject (Map-of-Maps) root</td>
 *       <td style="border: 1px solid gray;">Java Objects (DTOs)</td>
 *       <td style="border: 1px solid gray;">
 *         <pre>
 * MyClass obj = JsonIo.toObjects(jsonObject, readOptions, MyClass.class);
 *         </pre>
 *       </td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * <h2>Generic Type Support</h2>
 * <p>
 * For cases where you need to preserve full generic type information, use the <b>TypeHolder</b> overloads.
 * This allows you to capture complete type details (including parameterized types) and ensures that the full
 * generic details are preserved. For example:
 * </p>
 *
 * <pre>
 * // For simple, non-generic types:
 * Point p = JsonIo.toObjects(json, readOptions, Point.class);
 *
 * // For generic types:
 * TypeHolder&lt;List&lt;Point&gt;&gt; holder = new TypeHolder&lt;List&lt;Point&gt;&gt;() {};
 * List&lt;Point&gt; points = JsonIo.toObjects(json, readOptions, holder);
 * </pre>
 *
 * <h2>Usage Scenarios</h2>
 * <ul>
 *   <li>
 *     <b>One-step Conversion:</b> Directly convert between Java objects and JSON (options #1 and #3 above).
 *     Use these when you have a known DTO.
 *   </li>
 *   <li>
 *     <b>Two-step Conversion:</b> First parse the JSON into a JsonObject (Map-of-Maps), then resolve it
 *     into Java objects. This is useful if you need to inspect or modify the JSON structure before
 *     object resolution.
 *   </li>
 *   <li>
 *     <b>Raw JSON Processing:</b> If you require high performance or need to work with extremely large data sets,
 *     you can parse JSON into native JsonObjects. This approach avoids binding to specific DTOs, letting you
 *     walk the map structure and process the data in a streaming or iterative fashion.
 *   </li>
 * </ul>
 *
 * <h2>Input Variants</h2>
 * <ul>
 *   <li>
 *     <b>JSON Source:</b> The JSON content can be provided either as a <code>String</code> or an
 *     <code>InputStream</code>.
 *   </li>
 *   <li>
 *     <b>Java Object vs. JsonObject:</b> For serialization, you may supply a standard Java object or a
 *     <code>JsonObject</code> (a Map-of-Maps representing parsed JSON).
 *   </li>
 * </ul>
 *
 * <p>
 * In summary, JsonIo is designed to offer flexible and powerful conversion capabilities:
 * <ul>
 *   <li>Convert between Java objects and JSON with extensive configuration via <code>WriteOptions</code>
 *       and <code>ReadOptions</code>.</li>
 *   <li>Support both simple type hints (using <code>Class&lt;T&gt;</code>) and full generic type details
 *       (using <code>TypeHolder&lt;T&gt;</code>).</li>
 *   <li>Provide options for both direct conversion and multi-step processing (parse then resolve).</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Note:</b> For advanced use cases that require full generic type fidelity (e.g., deserializing a
 * <code>List&lt;Point&gt;</code>), the <code>TypeHolder</code>-based overloads are recommended. For simpler cases,
 * passing <code>Point.class</code> is perfectly acceptable.
 * </p>
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * @author Kenny Partlow (kpartlow@gmail.com)
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
     * <h2>Convert a Java Object to JSON</h2>
     *
     * <p>
     * This method serializes the given Java source object into its JSON representation. It supports both standard Java
     * objects and instances of {@code JsonObject} (e.g. a parsed Map-of-Maps obtained via a previous call to
     * {@code toObjects(...)} when using {@code readOptions.returnAsJsonObjects()}). The conversion respects the
     * configuration specified in the provided {@code WriteOptions}. If no write options are provided, the default settings
     * will be applied.
     * </p>
     *
     * <h3>Key Features</h3>
     * <ul>
     *   <li>Handles complex object graphs, including nested objects and collections.</li>
     *   <li>Respects custom serialization options (pretty printing, type hints, circular references, etc.) via {@code WriteOptions}.</li>
     *   <li>Supports both direct Java objects and pre-parsed {@code JsonObject} representations.</li>
     *   <li>Automatically closes the underlying streams when finished (using try-with-resources).</li>
     * </ul>
     *
     * <h3>Usage Examples</h3>
     * <pre>
     * // Simple serialization of a Java object:
     * MyBean bean = new MyBean(...);
     * String json = JsonIo.toJson(bean, null);  // Uses default write options
     *
     * // Serialization with custom options:
     * WriteOptions options = WriteOptionsBuilder.getDefaultWriteOptions();
     * options.setPrettyPrint(true);
     * String jsonPretty = JsonIo.toJson(bean, options);
     *
     * // Serialization of a JsonObject (Map-of-Maps):
     * JsonObject jsonMap = JsonIo.toObjects(jsonInput, readOptions, MyBean.class);
     * String jsonString = JsonIo.toJson(jsonMap, writeOptions);
     * </pre>
     *
     * <h3>Parameters</h3>
     * <ul>
     *   <li><b>srcObject</b> - The source object to serialize. This can be any Java instance, including a {@code JsonObject}
     *       that was produced by a prior call to {@code toObjects(...)} with the appropriate read options.</li>
     *   <li><b>writeOptions</b> - A {@code WriteOptions} instance that configures the serialization process (e.g.,
     *       pretty printing, custom type handling, output formatting, etc.). If {@code null} is provided, default options
     *       will be used.</li>
     * </ul>
     *
     * <h3>Returns</h3>
     * <p>
     * A {@code String} containing the JSON representation of the source object.
     * </p>
     *
     * <h3>Exceptions</h3>
     * <ul>
     *   <li>{@code JsonIoException} - Thrown if an error occurs during the serialization process.</li>
     * </ul>
     *
     * <p>
     * <b>Note:</b> This method leverages an internal {@code JsonWriter} to perform the serialization, ensuring that
     * the output is correctly formatted and that resources are properly managed.
     * </p>
     *
     * @param srcObject the Java object (or {@code JsonObject}) to be serialized into JSON format.
     * @param writeOptions the configuration options controlling the JSON output; may be {@code null} to use defaults.
     * @return a {@code String} representing the JSON serialization of the provided source object.
     * @throws JsonIoException if an error occurs during serialization.
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
     * <h2>Serialize a Java Object to JSON and Write to an OutputStream</h2>
     *
     * <p>
     * This method serializes the given Java source object into JSON and writes the output to the provided
     * {@code OutputStream}. It supports both standard Java objects and {@code JsonObject} instances (which may have been
     * produced earlier via {@code toObjects(...)} when using {@code readOptions.returnAsJsonObjects()}). The
     * serialization process is governed by the supplied {@code WriteOptions}. If no options are provided, the default
     * settings are used.
     * </p>
     *
     * <h3>Key Features</h3>
     * <ul>
     *   <li>
     *     <b>Stream-based Serialization:</b> Directly writes JSON to an {@code OutputStream} which is useful for streaming
     *     output, NDJSON, or writing to network sockets.
     *   </li>
     *   <li>
     *     <b>Automatic Resource Management:</b> The method uses a try-with-resources block to ensure that the
     *     underlying {@code JsonWriter} is closed automatically. By default, the {@code OutputStream} is also closed
     *     after writing. To prevent closing the stream (e.g., when writing multiple JSON objects), set
     *     {@code writeOptions.closeStream(false)}.
     *   </li>
     *   <li>
     *     <b>Flexible Input:</b> The source object can be a Java object or a pre-parsed {@code JsonObject} (Map-of-Maps).
     *   </li>
     * </ul>
     *
     * <h3>Usage Example</h3>
     * <p>
     * If you want to capture the JSON output as a String, you can wrap your target stream (such as a
     * {@code ByteArrayOutputStream}) around your original {@code OutputStream}. For example:
     * </p>
     * <pre>
     * // Wrap the original output stream
     * ByteArrayOutputStream baos = new ByteArrayOutputStream(originalOutputStream);
     *
     * // Write JSON to the wrapped stream
     * JsonIo.toJson(baos, sourceObject, writeOptions);
     *
     * // Flush and convert the written bytes to a String
     * baos.flush();
     * String json = new String(baos.toByteArray(), StandardCharsets.UTF_8);
     * </pre>
     *
     * <h3>Parameters</h3>
     * <ul>
     *   <li>
     *     <b>out</b> - The {@code OutputStream} where the JSON output will be written. This parameter must not be
     *     {@code null}.
     *   </li>
     *   <li>
     *     <b>source</b> - The Java object (or {@code JsonObject}) to be serialized into JSON.
     *   </li>
     *   <li>
     *     <b>writeOptions</b> - An instance of {@code WriteOptions} that configures the serialization process
     *     (e.g., pretty printing, custom type handling, output formatting). If {@code null} is provided, default options
     *     are used.
     *   </li>
     * </ul>
     *
     * <h3>Return Value</h3>
     * <p>
     * This method does not return a value. Instead, it writes the JSON representation of the source object directly to
     * the provided {@code OutputStream}.
     * </p>
     *
     * <h3>Exceptions</h3>
     * <ul>
     *   <li>
     *     {@code JsonIoException} - Thrown if an error occurs during the serialization process.
     *   </li>
     * </ul>
     *
     * <p>
     * <b>Note:</b> By default, the {@code OutputStream} will be closed after writing. If you need to keep the stream
     * open (for example, when writing multiple JSON objects in NDJSON format), set
     * {@code writeOptions.closeStream(false)}.
     * </p>
     *
     * @param out the {@code OutputStream} destination for the JSON output; must not be {@code null}.
     * @param source the Java object (or {@code JsonObject}) to convert to JSON format.
     * @param writeOptions the configuration options controlling JSON output; may be {@code null} to use default settings.
     * @throws JsonIoException if an error occurs during serialization.
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
     * <h2>Deserialize JSON to Java Objects</h2>
     *
     * <p>
     * This method converts the provided JSON (as a {@code String}) into a corresponding Java object graph.
     * It supports conversion into fully resolved Java DTOs as well as native {@code JsonObject} representations (Map-of-Maps),
     * depending on the settings in {@code ReadOptions}. The optional {@code rootType} parameter hints at the target type
     * for the root object. If {@code rootType} is {@code null}, JsonIo attempts to determine the appropriate type based on
     * the JSON structure and any embedded type metadata (e.g. via an "@type" property).
     * </p>
     *
     * <h3>Conversion Overview</h3>
     * <table style="border-collapse: collapse; border: 1px solid gray;" cellpadding="4" cellspacing="0">
     *   <thead>
     *     <tr style="background-color: #ddd;">
     *       <th style="border: 1px solid gray;">Aspect</th>
     *       <th style="border: 1px solid gray;">Details</th>
     *     </tr>
     *   </thead>
     *   <tbody>
     *     <tr>
     *       <td style="border: 1px solid gray;"><b>Input</b></td>
     *       <td style="border: 1px solid gray;">JSON content as a {@code String}</td>
     *     </tr>
     *     <tr>
     *       <td style="border: 1px solid gray;"><b>Deserialization Mode</b></td>
     *       <td style="border: 1px solid gray;">Either into Java DTOs or into native {@code JsonObject} maps,
     *       depending on {@code ReadOptions} (e.g., {@code returnAsJsonObjects()}).</td>
     *     </tr>
     *     <tr>
     *       <td style="border: 1px solid gray;"><b>Type Hint</b></td>
     *       <td style="border: 1px solid gray;">Provided via the {@code rootType} parameter. If not supplied,
     *       a best-guess is made:
     *         <ul>
     *           <li>JSON objects (<code>{ ... }</code>) become {@code Map} or user-specified DTOs.</li>
     *           <li>JSON arrays (<code>[ ... ]</code>) become arrays or {@code Collection}s.</li>
     *           <li>Primitives (e.g. <code>"text"</code>, numbers, booleans) are returned as the corresponding Java types.</li>
     *         </ul>
     *       </td>
     *     </tr>
     *   </tbody>
     * </table>
     *
     * <h3>Usage Examples</h3>
     * <pre>
     * // 1. Deserialization into a specific DTO:
     * MyClass obj = JsonIo.toObjects(jsonString, readOptions, MyClass.class);
     *
     * // 2. Deserialization when JSON contains embedded type metadata:
     * //    If {@code jsonString} contains an "@type" property, that type will be used instead.
     * MyClass obj = JsonIo.toObjects(jsonString, readOptions, MyClass.class);
     *
     * // 3. Deserialization into native JsonObjects (Map-of-Maps):
     * JsonObject jsonObj = JsonIo.toObjects(jsonString, readOptions, null);
     *
     * // 4. For generic type support (advanced usage), use the TypeHolder overload:
     * //    TypeHolder&lt;List&lt;Point&gt;&gt; holder = new TypeHolder&lt;List&lt;Point&gt;&gt;() {};
     * //    List&lt;Point&gt; points = JsonIo.toObjects(jsonString, readOptions, holder);
     * </pre>
     *
     * <h3>Parameters</h3>
     * <ul>
     *   <li>
     *     <b>json</b> - A {@code String} containing the JSON content. If {@code null}, an empty string is used.
     *   </li>
     *   <li>
     *     <b>readOptions</b> - A {@code ReadOptions} instance that configures how the JSON is deserialized (e.g.,
     *     whether to return native {@code JsonObject} maps, how to handle unknown types, etc.). If {@code null}, the
     *     default options are used.
     *   </li>
     *   <li>
     *     <b>rootType</b> - A {@code Class&lt;T&gt;} that represents the desired root type for the returned object.
     *     If {@code null}, or if the JSON includes an "@type" meta-property, JsonIo will attempt to infer the correct type.
     *   </li>
     * </ul>
     *
     * <h3>Return Value</h3>
     * <p>
     * Returns an instance of type {@code T} representing the deserialized JSON. Depending on the input and read options,
     * this might be a user-defined DTO, a collection, an array, or a native {@code JsonObject} (Map-of-Maps).
     * </p>
     *
     * <h3>Exceptions</h3>
     * <ul>
     *   <li>
     *     {@code JsonIoException} - Thrown if an error occurs during deserialization.
     *   </li>
     * </ul>
     *
     * <p>
     * <b>Note:</b> For cases requiring full generic type support (e.g., deserializing a <code>List&lt;Point&gt;</code>),
     * consider using the {@code TypeHolder}-based overloads. For simple, non-generic types, passing a raw class (e.g.,
     * <code>MyClass.class</code>) is sufficient.
     * </p>
     *
     * @param json the JSON content as a {@code String}; if {@code null}, an empty string is assumed.
     * @param readOptions the configuration options controlling the deserialization process; may be {@code null} to use defaults.
     * @param rootType a {@code Class&lt;T&gt;} representing the expected root type of the returned object; if {@code null},
     *                 a best-guess is made based on the JSON content and type metadata.
     * @param <T> the type of the resulting Java object.
     * @return an instance of type {@code T} representing the deserialized JSON.
     * @throws JsonIoException if an error occurs during deserialization.
     */
    public static <T> T toObjects(String json, ReadOptions readOptions, Class<T> rootType) {
        return toObjectsGeneric(json, readOptions, TypeHolder.of(rootType));
    }

    /**
     * <h2>Deserialize JSON to Java Objects with Full Generic Type Information</h2>
     *
     * <p>
     * This method converts the provided JSON content (as a {@code String}) into the corresponding Java object graph,
     * leveraging a {@code TypeHolder} to capture full generic type details. This overload is particularly useful for
     * cases where the target type includes generic parameters (for example, {@code List<Point>}) that would be lost
     * if a simple {@code Class<T>} were used.
     * </p>
     *
     * <h3>Conversion Overview</h3>
     * <table style="border-collapse: collapse; border: 1px solid gray;" cellpadding="4" cellspacing="0">
     *   <thead>
     *     <tr style="background-color: #ddd;">
     *       <th style="border: 1px solid gray;">Aspect</th>
     *       <th style="border: 1px solid gray;">Details</th>
     *     </tr>
     *   </thead>
     *   <tbody>
     *     <tr>
     *       <td style="border: 1px solid gray;"><b>Input</b></td>
     *       <td style="border: 1px solid gray;">JSON content as a {@code String}</td>
     *     </tr>
     *     <tr>
     *       <td style="border: 1px solid gray;"><b>Type Hint</b></td>
     *       <td style="border: 1px solid gray;">
     *         Provided via a {@code TypeHolder<T>} that preserves the full generic type information (e.g.,
     *         {@code List<Point>} or {@code Map<String, List<Point>>}).
     *       </td>
     *     </tr>
     *     <tr>
     *       <td style="border: 1px solid gray;"><b>Output</b></td>
     *       <td style="border: 1px solid gray;">
     *         A Java object graph of type {@code T} representing the deserialized JSON. This may be a DTO, collection, array,
     *         or a native {@code JsonObject} (Map-of-Maps), depending on the JSON structure and the provided {@code ReadOptions}.
     *       </td>
     *     </tr>
     *   </tbody>
     * </table>
     *
     * <h3>Usage Examples</h3>
     * <pre>
     * // Example 1: Deserializing into a raw type (Point)
     * TypeHolder&lt;Point&gt; holder1 = TypeHolder.of(Point.class);
     * Point point = JsonIo.toObjects(jsonString, readOptions, holder1);
     *
     * // Example 2: Deserializing into a generic List of Points:
     * TypeHolder holder2 = new TypeHolder&lt;List&lt;Point&gt;&gt;();
     * List&lt;Point&gt; points = JsonIo.toObjects(jsonString, readOptions, holder2);
     *
     * // Advanced Example: Deserializing into a Map of String to List&lt;Point&gt;
     * TypeHolder mapType = new TypeHolder&lt;Map&lt;String, List&lt;Point&gt;&gt;&gt;();
     * Map&lt;String, List&lt;Point&gt;&gt; map = JsonIo.toObjects(jsonString, readOptions, holder3);
     * </pre>
     *
     * <h3>Parameters</h3>
     * <ul>
     *   <li>
     *     <b>json</b> - A {@code String} containing the JSON content to be deserialized. If {@code null}, an empty string is assumed.
     *   </li>
     *   <li>
     *     <b>readOptions</b> - A {@code ReadOptions} instance that configures the deserialization process. For example, it
     *     may control the handling of unknown types, type coercion, and the format of the returned objects. If {@code null},
     *     default settings are applied.
     *   </li>
     *   <li>
     *     <b>rootTypeHolder</b> - A {@code TypeHolder<T>} encapsulating the full target type (including generics) for deserialization.
     *     Use this overload to preserve complete type details (e.g., pass {@code TypeHolder.of(new TypeReference<List<Point>>() {}.getType())}).
     *   </li>
     * </ul>
     *
     * <h3>Return Value</h3>
     * <p>
     * Returns an instance of type {@code T} representing the deserialized JSON object graph. Depending on the JSON structure
     * and the read options, this may be a user-defined DTO, a collection, an array, or a native {@code JsonObject} (Map-of-Maps).
     * </p>
     *
     * <h3>Exceptions</h3>
     * <ul>
     *   <li>
     *     {@code JsonIoException} - Thrown if an error occurs during deserialization.
     *   </li>
     * </ul>
     *
     * <p>
     * <b>Note:</b> This overload wraps the JSON {@code String} into an {@code InputStream} and delegates to the corresponding
     * {@code toObjects(InputStream, ReadOptions, TypeHolder<T>)} method. It is especially useful for preserving generic type
     * information that would otherwise be lost when using a simple {@code Class<T>} argument.
     * </p>
     *
     * @param json the JSON content as a {@code String}; if {@code null}, an empty string is assumed.
     * @param readOptions the options that control the deserialization process; may be {@code null} to use default settings.
     * @param rootTypeHolder a {@code TypeHolder<T>} encapsulating the full target type (including generics) for deserialization.
     * @param <T> the type of the resulting Java object.
     * @return an instance of type {@code T} representing the deserialized JSON object graph.
     * @throws JsonIoException if an error occurs during deserialization.
     */
    public static <T> T toObjectsGeneric(String json, ReadOptions readOptions, TypeHolder<T> rootTypeHolder) {
        if (json == null) {
            json = "";
        }
        // Wrap the string in an InputStream and delegate
        return toObjectsGeneric(new FastByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), readOptions, rootTypeHolder);
    }

    /**
     * <h2>Deserialize JSON from an InputStream to Java Objects</h2>
     *
     * <p>
     * This method reads JSON content from the supplied {@code InputStream} and converts it into a corresponding Java object
     * graph. The deserialization process is governed by the provided {@code ReadOptions}. By default, the InputStream is closed
     * after processing; to keep it open (e.g., when processing NDJSON or streaming multiple JSON objects), set
     * {@code readOptions.closeStream(false)}.
     * </p>
     *
     * <h3>Conversion Overview</h3>
     * <table style="border-collapse: collapse; border: 1px solid gray;" cellpadding="4" cellspacing="0">
     *   <thead>
     *     <tr style="background-color: #ddd;">
     *       <th style="border: 1px solid gray;">Aspect</th>
     *       <th style="border: 1px solid gray;">Details</th>
     *     </tr>
     *   </thead>
     *   <tbody>
     *     <tr>
     *       <td style="border: 1px solid gray;"><b>Input</b></td>
     *       <td style="border: 1px solid gray;">JSON content via an {@code InputStream}</td>
     *     </tr>
     *     <tr>
     *       <td style="border: 1px solid gray;"><b>Type Hint</b></td>
     *       <td style="border: 1px solid gray;">
     *         The optional {@code rootType} parameter provides a hint for the expected Java type of the root object.
     *         If {@code null} or if the JSON includes an "@type" meta-property, JsonIo attempts to determine the appropriate type.
     *       </td>
     *     </tr>
     *     <tr>
     *       <td style="border: 1px solid gray;"><b>Output</b></td>
     *       <td style="border: 1px solid gray;">
     *         A Java object graph that may be a user-defined DTO, a native {@code JsonObject} (Map-of-Maps), an array,
     *         a collection, or a primitive (String, Number, Boolean, or null) depending on the JSON structure and
     *         {@code ReadOptions}.
     *       </td>
     *     </tr>
     *   </tbody>
     * </table>
     *
     * <h3>Usage Examples</h3>
     * <pre>
     * // 1. Deserialize JSON from an InputStream into a specific DTO:
     * MyClass obj = JsonIo.toObjects(inputStream, readOptions, MyClass.class);
     *
     * // 2. Deserialize JSON into native JsonObjects when type inference is desired:
     * JsonObject jsonObj = JsonIo.toObjects(inputStream, readOptions, null);
     * </pre>
     *
     * <h3>Parameters</h3>
     * <ul>
     *   <li>
     *     <b>in</b> - The {@code InputStream} from which JSON content is read. This parameter must not be {@code null}.
     *     By default, the stream will be closed after processing.
     *   </li>
     *   <li>
     *     <b>readOptions</b> - A {@code ReadOptions} instance that configures how the JSON is processed. This includes
     *     options such as whether to return native {@code JsonObject} maps, how to handle unknown types, and type coercion.
     *     If {@code null}, the default settings are used.
     *   </li>
     *   <li>
     *     <b>rootType</b> - A {@code Class<T>} that represents the expected root type of the resulting Java object.
     *     If {@code null} or if the JSON includes a type hint (e.g., an "@type" property), JsonIo will attempt to infer
     *     the correct type based on the JSON content.
     *   </li>
     * </ul>
     *
     * <h3>Return Value</h3>
     * <p>
     * Returns an instance of type {@code T} representing the deserialized JSON object graph. Depending on the input and the
     * configuration in {@code ReadOptions}, the result may be:
     * <ul>
     *   <li>A user-defined DTO (if a concrete {@code rootType} is provided).</li>
     *   <li>A native {@code JsonObject} (Map-of-Maps) when {@code ReadOptions.returnAsJsonObjects()} is enabled.</li>
     *   <li>An array or {@code Collection} if the JSON represents an array.</li>
     *   <li>A primitive (String, Number, Boolean, or null) if the JSON represents a JSON primitive.</li>
     * </ul>
     * </p>
     *
     * <h3>Exceptions</h3>
     * <ul>
     *   <li>
     *     {@code JsonIoException} - Thrown if an error occurs during deserialization.
     *   </li>
     * </ul>
     *
     * <p>
     * <b>Note:</b> The provided {@code rootType} is used as a type hint. However, if the JSON content contains an
     * "@type" property, that metadata takes precedence. If no type is specified (i.e., {@code rootType} is {@code null}),
     * JsonIo will attempt to deduce the appropriate type based on the structure of the JSON.
     * </p>
     *
     * @param in the {@code InputStream} from which JSON content is read; must not be {@code null}.
     * @param readOptions the configuration options controlling the deserialization process; may be {@code null} to use default settings.
     * @param rootType a {@code Class<T>} representing the expected root type of the resulting Java object; may be {@code null}
     *                 to allow for type inference.
     * @param <T> the type of the resulting Java object.
     * @return an instance of type {@code T} representing the deserialized JSON object graph.
     * @throws JsonIoException if an error occurs during deserialization.
     */
    public static <T> T toObjects(InputStream in, ReadOptions readOptions, Class<T> rootType) {
        return toObjectsGeneric(in, readOptions, TypeHolder.of(rootType));
    }

    /**
     * <h2>Deserialize JSON from an InputStream to Java Objects with Full Generic Type Information</h2>
     *
     * <p>
     * This method reads JSON content from the supplied {@code InputStream} and converts it into a corresponding Java object
     * graph. It leverages a {@code TypeHolder<T>} to capture and preserve full generic type details, ensuring that complex
     * parameterized types (e.g. {@code List<Point>}) are handled correctly. This overload is especially useful when the target
     * type includes generic parameters that would be lost if only a raw {@code Class<T>} were provided.
     * </p>
     *
     * <h3>Conversion Overview</h3>
     * <table style="border-collapse: collapse; border: 1px solid gray;" cellpadding="4" cellspacing="0">
     *   <thead>
     *     <tr style="background-color: #ddd;">
     *       <th style="border: 1px solid gray;">Aspect</th>
     *       <th style="border: 1px solid gray;">Details</th>
     *     </tr>
     *   </thead>
     *   <tbody>
     *     <tr>
     *       <td style="border: 1px solid gray;"><b>Input</b></td>
     *       <td style="border: 1px solid gray;">JSON content via an {@code InputStream}</td>
     *     </tr>
     *     <tr>
     *       <td style="border: 1px solid gray;"><b>Type Hint</b></td>
     *       <td style="border: 1px solid gray;">
     *         Provided via a {@code TypeHolder<T>} which preserves the full generic type (e.g., {@code List<Point>}).
     *         This ensures that all type parameters are retained during deserialization.
     *       </td>
     *     </tr>
     *     <tr>
     *       <td style="border: 1px solid gray;"><b>Output</b></td>
     *       <td style="border: 1px solid gray;">
     *         A Java object graph of type {@code T} that may be a DTO, collection, array, or native {@code JsonObject}
     *         (Map-of-Maps), depending on the JSON structure and the configuration in {@code ReadOptions}.
     *       </td>
     *     </tr>
     *   </tbody>
     * </table>
     *
     * <h3>Usage Example</h3>
     * <pre>
     * // Example: Deserializing a JSON InputStream into a generic List of Points:
     * TypeHolder&lt;List&lt;Point&gt;&gt; holder = new TypeHolder&lt;List&lt;Point&gt;&gt;() {};
     * List&lt;Point&gt; points = JsonIo.toObjects(inputStream, readOptions, holder);
     * </pre>
     *
     * <h3>Parameters</h3>
     * <ul>
     *   <li>
     *     <b>in</b> - The {@code InputStream} from which JSON content is read. This parameter must not be {@code null}.
     *     By default, the stream will be closed after processing unless {@code readOptions.closeStream(false)} is set.
     *   </li>
     *   <li>
     *     <b>readOptions</b> - A {@code ReadOptions} instance that configures how the JSON is processed (e.g., type coercion,
     *     handling of unknown types, whether to return native {@code JsonObject} maps, etc.). If {@code null}, default settings are used.
     *   </li>
     *   <li>
     *     <b>rootTypeHolder</b> - A {@code TypeHolder<T>} that encapsulates the complete target type, including generic parameters.
     *     This is used to ensure that the full type information is preserved during deserialization.
     *   </li>
     * </ul>
     *
     * <h3>Return Value</h3>
     * <p>
     * Returns an instance of type {@code T} representing the deserialized JSON object graph. Depending on the JSON content and
     * the provided read options, the result may be a user-defined DTO, a collection, an array, or a native {@code JsonObject}.
     * </p>
     *
     * <h3>Exceptions</h3>
     * <ul>
     *   <li>
     *     {@code JsonIoException} - Thrown if an error occurs during the deserialization process.
     *   </li>
     * </ul>
     *
     * <p>
     * <b>Note:</b> This overload wraps the JSON content from the {@code InputStream} and delegates to the internal deserialization
     * logic using the full type information extracted from the provided {@code TypeHolder}. This is essential for preserving
     * generic type details that would be lost if only a raw {@code Class<T>} were used.
     * </p>
     *
     * @param in the {@code InputStream} from which JSON content is read; must not be {@code null}.
     * @param readOptions the configuration options controlling the deserialization process; may be {@code null} to use default settings.
     * @param rootTypeHolder a {@code TypeHolder<T>} that encapsulates the full target type (including generics) for deserialization.
     * @param <T> the type of the resulting Java object.
     * @return an instance of type {@code T} representing the deserialized JSON object graph.
     * @throws JsonIoException if an error occurs during deserialization.
     */
    public static <T> T toObjectsGeneric(InputStream in, ReadOptions readOptions, TypeHolder<T> rootTypeHolder) {
        Convention.throwIfNull(in, "InputStream cannot be null");
        if (readOptions == null) {
            readOptions = ReadOptionsBuilder.getDefaultReadOptions();
        }

        JsonReader jr = null;
        try {
            jr = new JsonReader(in, readOptions);
            // Instead of passing a Class, pass the full Type from the TypeHolder.
            T root = jr.readObject(rootTypeHolder.getType());
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

    /**
     * <h2>Resolve a Native JsonObject to a Fully Resolved Java Object Graph</h2>
     *
     * <p>
     * This method converts a root {@code JsonObject} (a Map-of-Maps representation of parsed JSON) into an actual Java object
     * graph. The parsed {@code JsonObject} is typically produced by calling {@code JsonIo.toObjects(String)} or
     * {@code JsonIo.toObjects(InputStream)} with {@code ReadOptions.returnAsJsonObjects()} enabled. This intermediate
     * representation contains metadata (such as an {@code @type} field) that allows the correct resolution of Java objects.
     * </p>
     *
     * <h3>Conversion Overview</h3>
     * <table style="border-collapse: collapse; border: 1px solid gray;" cellpadding="4" cellspacing="0">
     *   <thead>
     *     <tr style="background-color: #ddd;">
     *       <th style="border: 1px solid gray;">Aspect</th>
     *       <th style="border: 1px solid gray;">Details</th>
     *     </tr>
     *   </thead>
     *   <tbody>
     *     <tr>
     *       <td style="border: 1px solid gray;"><b>Input</b></td>
     *       <td style="border: 1px solid gray;">A {@code JsonObject} (Map-of-Maps) representing parsed JSON data.</td>
     *     </tr>
     *     <tr>
     *       <td style="border: 1px solid gray;"><b>Type Hint</b></td>
     *       <td style="border: 1px solid gray;">
     *           A {@code Class<T>} that indicates the expected Java type of the root object. If the {@code JsonObject}
     *           contains an embedded type hint (e.g., via an {@code @type} property), that metadata will be used.
     *       </td>
     *     </tr>
     *     <tr>
     *       <td style="border: 1px solid gray;"><b>Output</b></td>
     *       <td style="border: 1px solid gray;">
     *           A fully resolved Java object graph of type {@code T} which may be a user-defined DTO, a collection, an array,
     *           or a primitive type, depending on the JSON structure and configuration.
     *       </td>
     *     </tr>
     *   </tbody>
     * </table>
     *
     * <h3>Usage Examples</h3>
     * <pre>
     * // Example 1: Convert a native JsonObject (Map-of-Maps) into a specific DTO:
     * JsonObject jsonObj = JsonIo.toObjects(jsonString, readOptions, null);
     * MyClass obj = JsonIo.toObjects(jsonObj, readOptions, MyClass.class);
     *
     * // Example 2: If no type hint is provided, the resolver attempts to infer the type:
     * JsonObject jsonObj = JsonIo.toObjects(jsonString, readOptions, null);
     * Object result = JsonIo.toObjects(jsonObj, readOptions, null);
     * </pre>
     *
     * <h3>Parameters</h3>
     * <ul>
     *   <li>
     *     <b>jsonObject</b> - The {@code JsonObject} (Map-of-Maps) that represents the parsed JSON data. This is generally
     *     obtained via a prior call to {@code toObjects(String)} or {@code toObjects(InputStream)} when using
     *     {@code ReadOptions.returnAsJsonObjects()}.
     *   </li>
     *   <li>
     *     <b>readOptions</b> - A {@code ReadOptions} instance that controls the resolution process. If {@code null}, default
     *     settings are applied. If the options do not already specify that Java objects should be returned, they are updated
     *     to do so.
     *   </li>
     *   <li>
     *     <b>rootType</b> - A {@code Class<T>} that represents the expected Java type for the root object in the resolved
     *     object graph. If {@code null}, or if the {@code JsonObject} contains an embedded type hint (e.g., an {@code @type}
     *     property), JsonIo will attempt to deduce the appropriate type.
     *   </li>
     * </ul>
     *
     * <h3>Return Value</h3>
     * <p>
     * Returns a Java object graph of type {@code T} representing the resolved JSON. Depending on the input JSON structure
     * and provided type hint, the result might be a user-defined DTO, an array, a collection, or even a primitive value
     * (e.g., String, Number, Boolean, or null).
     * </p>
     *
     * <h3>Notes</h3>
     * <p>
     * This method performs the "resolution" phase of deserialization. It converts the intermediate {@code JsonObject}
     * representation—containing all the JSON data and meta-information—into a fully resolved Java object graph.
     * </p>
     *
     * <p>
     * If you have already converted JSON into a native {@code JsonObject} and simply wish to re-resolve it into Java objects,
     * this method is appropriate. Otherwise, use the other {@code toObjects(...)} overloads to parse and resolve in a single step.
     * </p>
     *
     * @param jsonObject a {@code JsonObject} (Map-of-Maps) representing parsed JSON data.
     * @param readOptions the configuration options for JSON processing; if {@code null}, default settings are used.
     * @param rootType a {@code Class<T>} representing the expected root type of the resulting Java object; if {@code null},
     *                 JsonIo will attempt to infer the type from the {@code JsonObject} metadata.
     * @param <T> the type of the resulting Java object.
     * @return a Java object graph of type {@code T} representing the resolved JSON.
     */
    public static <T> T toObjects(JsonObject jsonObject, ReadOptions readOptions, Class<T> rootType) {
        return toObjectsGeneric(jsonObject, readOptions, TypeHolder.of(rootType));
    }

    /**
     * <h2>Resolve a Native JsonObject to a Fully Resolved Java Object Graph with Full Generic Type Information</h2>
     *
     * <p>
     * This method converts a native {@code JsonObject} (a Map-of-Maps representation of parsed JSON) into a fully resolved
     * Java object graph. It leverages a {@code TypeHolder<T>} to capture and preserve the complete generic type details of the
     * target object. This is particularly useful when the root type includes generic parameters (e.g., {@code List<Point>})
     * that cannot be fully expressed with a raw {@code Class<T>} alone.
     * </p>
     *
     * <h3>Conversion Overview</h3>
     * <table style="border-collapse: collapse; border: 1px solid gray;" cellpadding="4" cellspacing="0">
     *   <thead>
     *     <tr style="background-color: #ddd;">
     *       <th style="border: 1px solid gray;">Aspect</th>
     *       <th style="border: 1px solid gray;">Details</th>
     *     </tr>
     *   </thead>
     *   <tbody>
     *     <tr>
     *       <td style="border: 1px solid gray;"><b>Input</b></td>
     *       <td style="border: 1px solid gray;">A native {@code JsonObject} (Map-of-Maps) that represents parsed JSON data.</td>
     *     </tr>
     *     <tr>
     *       <td style="border: 1px solid gray;"><b>Type Hint</b></td>
     *       <td style="border: 1px solid gray;">
     *         Provided via a {@code TypeHolder<T>}, which encapsulates the full target type, including any generic parameters.
     *         This ensures that complete type information (e.g. {@code List<Point>}) is preserved during resolution.
     *       </td>
     *     </tr>
     *     <tr>
     *       <td style="border: 1px solid gray;"><b>Output</b></td>
     *       <td style="border: 1px solid gray;">
     *         A fully resolved Java object graph of type {@code T}. Depending on the JSON structure and the settings in
     *         {@code ReadOptions}, the result may be a user-defined DTO, a collection, an array, or a primitive value.
     *       </td>
     *     </tr>
     *   </tbody>
     * </table>
     *
     * <h3>Usage Example</h3>
     * <pre>
     * // Example: Resolve a native JsonObject into a generic List of Points
     * JsonObject jsonObj = ...; // Obtained via a prior call to JsonIo.toObjects(String) or toObjects(InputStream)
     * TypeHolder&lt;List&lt;Point&gt;&gt; holder = new TypeHolder&lt;List&lt;Point&gt;&gt;() {};
     * List&lt;Point&gt; points = JsonIo.toObjects(jsonObj, readOptions, holder);
     * </pre>
     *
     * <h3>Parameters</h3>
     * <ul>
     *   <li>
     *     <b>jsonObject</b> - The native {@code JsonObject} (Map-of-Maps) representing parsed JSON data. This object is typically
     *     produced by a previous call to {@code toObjects(String)} or {@code toObjects(InputStream)} when using
     *     {@code ReadOptions.returnAsJsonObjects()}.
     *   </li>
     *   <li>
     *     <b>readOptions</b> - A {@code ReadOptions} instance that configures the resolution process (e.g., whether to return
     *     fully resolved Java objects or native {@code JsonObject} maps). If {@code null}, default settings are applied.
     *   </li>
     *   <li>
     *     <b>rootTypeHolder</b> - A {@code TypeHolder<T>} that encapsulates the complete target type (including generics) for
     *     the resulting Java object graph.
     *   </li>
     * </ul>
     *
     * <h3>Return Value</h3>
     * <p>
     * Returns an instance of type {@code T} representing the fully resolved Java object graph. Depending on the JSON content
     * and type hint, this might be a user-defined DTO, an array, a collection, or a primitive value (e.g., String, Number,
     * Boolean, or null).
     * </p>
     *
     * <h3>Notes</h3>
     * <p>
     * This method performs the resolution phase of deserialization. It converts the intermediate native {@code JsonObject}
     * (which includes JSON data and meta-information such as an "@type" property) into a fully resolved Java object graph,
     * using the full type information provided by the {@code TypeHolder}. This is critical for accurately reconstructing
     * parameterized types.
     * </p>
     *
     * @param jsonObject a {@code JsonObject} (Map-of-Maps) representing the parsed JSON data.
     * @param readOptions the configuration options for JSON processing; if {@code null}, default settings are used.
     * @param rootTypeHolder a {@code TypeHolder<T>} encapsulating the full target type (including generics) for resolution.
     * @param <T> the type of the resulting Java object.
     * @return an instance of type {@code T} representing the fully resolved Java object graph.
     */
    public static <T> T toObjectsGeneric(JsonObject jsonObject, ReadOptions readOptions, TypeHolder<T> rootTypeHolder) {
        if (readOptions == null) {
            readOptions = ReadOptionsBuilder.getDefaultReadOptions();
        } else if (!readOptions.isReturningJavaObjects()) {
            readOptions = new ReadOptionsBuilder(readOptions).returnAsJavaObjects().build();
        }
        JsonReader reader = new JsonReader(readOptions);
        // Pass the full Type to the resolver.
        return reader.resolveObjects(jsonObject, rootTypeHolder.getType());
    }

    /**
     * Format the passed in JSON into multi-line, indented format, commonly used in JSON online editors.
     * @param json String JSON content.
     * @return String JSON formatted in human-readable, standard multi-line, indented format.
     */
    public static String formatJson(String json) {
        return JsonPrettyPrinter.prettyPrint(json);
    }

    /**
     * Copy an object graph using JSON.
     * @param source Object root object to copy
     * @param readOptions ReadOptions feature settings. Can be null for default ReadOptions.
     * @param writeOptions WriteOptions feature settings. Can be null for default WriteOptions.
     * @return A new, duplicate instance of the original.
     */
    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(Object source, ReadOptions readOptions, WriteOptions writeOptions) {
        if (source == null) {
            // They asked to copy null.  The copy of null is null.
            return null;
        }

        writeOptions = new WriteOptionsBuilder(writeOptions).showTypeInfoMinimal().shortMetaKeys(true).build();
        if (readOptions == null) {
            readOptions = ReadOptionsBuilder.getDefaultReadOptions();
        }

        String json = toJson(source, writeOptions);
        return (T) toObjects(json, readOptions, source.getClass());
    }

    /**
     * Call this method to see all the conversions offered.
     * @param args String[] of command line arguments
     */
    public static void main(String[] args) {
        String json = toJson(new Converter(new DefaultConverterOptions()).getSupportedConversions(), new WriteOptionsBuilder().prettyPrint(true).showTypeInfoNever().build());
        System.out.println("json-io supported conversions (source type to target types):");
        System.out.println(json);
    }

    /**
     * Convert an old-style Map of options to a ReadOptionsBuilder. It is not recommended to use this API long term,
     * however, this API will be the fastest route to bridge an old installation using json-io to the new API.
     * @param optionalArgs Map of old json-io options
     * @return ReadOptionsBuilder
     * @deprecated - This exists to show how the old {@code Map<String, Object>} options are crreated using ReadOptionsBuilder.
     */
    @Deprecated
    public static ReadOptionsBuilder getReadOptionsBuilder(Map<String, Object> optionalArgs) {
        if (optionalArgs == null) {
            optionalArgs = new HashMap<>();
        }
        ReadOptionsBuilder builder = new ReadOptionsBuilder();

        int maxParseDepth = 1000;
        if (optionalArgs.containsKey(MAX_PARSE_DEPTH)) {
            maxParseDepth = com.cedarsoftware.util.Converter.convert(optionalArgs.get(MAX_PARSE_DEPTH), int.class);
        }
        builder.maxDepth(maxParseDepth);
        boolean useMaps = com.cedarsoftware.util.Converter.convert(optionalArgs.get(USE_MAPS), boolean.class);

        if (useMaps) {
            builder.returnAsJsonObjects();
        } else {
            builder.returnAsJavaObjects();
        }

        boolean failOnUnknownType = com.cedarsoftware.util.Converter.convert(optionalArgs.get(FAIL_ON_UNKNOWN_TYPE), boolean.class);
        builder.failOnUnknownType(failOnUnknownType);

        Object loader = optionalArgs.get(CLASSLOADER);
        ClassLoader classLoader;
        if (loader instanceof ClassLoader) {
            classLoader = (ClassLoader) loader;
        } else {
            classLoader = ClassUtilities.getClassLoader(JsonIo.class);
        }
        builder.classLoader(classLoader);

        Object type = optionalArgs.get("UNKNOWN_TYPE");
        if (type == null) {
            type = optionalArgs.get(UNKNOWN_OBJECT);
        }
        if (type instanceof Boolean) {
            builder.failOnUnknownType(true);
        } else if (type instanceof String) {
            Class<?> unknownType = ClassUtilities.forName((String) type, classLoader);
            builder.unknownTypeClass(unknownType);
            builder.failOnUnknownType(false);
        }

        Object aliasMap = optionalArgs.get(TYPE_NAME_MAP);
        if (aliasMap instanceof Map) {
            Map<String, String> aliases = (Map<String, String>) aliasMap;
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                builder.aliasTypeName(entry.getKey(), entry.getValue());
            }
        }

        Object missingFieldHandler = optionalArgs.get(MISSING_FIELD_HANDLER);
        if (missingFieldHandler instanceof com.cedarsoftware.io.JsonReader.MissingFieldHandler) {
            builder.missingFieldHandler((com.cedarsoftware.io.JsonReader.MissingFieldHandler) missingFieldHandler);
        }

        Object customReaderMap = optionalArgs.get(CUSTOM_READER_MAP);
        if (customReaderMap instanceof Map) {
            Map<String, Object> customReaders = (Map<String, Object>) customReaderMap;
            for (Map.Entry<String, Object> entry : customReaders.entrySet()) {
                try {
                    Class<?> clazz = Class.forName(entry.getKey());
                    builder.addCustomReaderClass(clazz, (com.cedarsoftware.io.JsonReader.JsonClassReader) entry.getValue());
                } catch (ClassNotFoundException e) {
                    String message = "Custom json-io reader class: " + entry.getKey() + " not found.";
                    throw new com.cedarsoftware.io.JsonIoException(message, e);
                } catch (ClassCastException e) {
                    String message = "Custom json-io reader for: " + entry.getKey() + " must be an instance of com.cedarsoftware.io.JsonReader.JsonClassReader.";
                    throw new com.cedarsoftware.io.JsonIoException(message, e);
                }
            }
        }

        Object notCustomReadersObject = optionalArgs.get(NOT_CUSTOM_READER_MAP);
        if (notCustomReadersObject instanceof Iterable) {
            Iterable<Class<?>> notCustomReaders = (Iterable<Class<?>>) notCustomReadersObject;
            for (Class<?> notCustomReader : notCustomReaders)
            {
                builder.addNotCustomReaderClass(notCustomReader);
            }
        }

        for (Map.Entry<String, Object> entry : optionalArgs.entrySet()) {
            if (OPTIONAL_READ_KEYS.contains(entry.getKey())) {
                continue;
            }
            builder.addCustomOption(entry.getKey(), entry.getValue());
        }

        return builder;
    }

    /**
     * Convert an old-style Map of options to a WriteOptionsBuilder. It is not recommended to use this API long term,
     * however, this API will be the fastest route to bridge an old installation using json-io to the new API.
     * @param optionalArgs Map of old json-io options
     * @return WriteOptionsBuilder
     * @deprecated - This exists to show how the old {@code Map<String, Object>} options are crreated using WriteptionsBuilder.
     */
    @Deprecated
    public static WriteOptionsBuilder getWriteOptionsBuilder(Map<String, Object> optionalArgs) {
        if (optionalArgs == null) {
            optionalArgs = new HashMap<>();
        }

        WriteOptionsBuilder builder = new WriteOptionsBuilder();

        Object dateFormat = optionalArgs.get(DATE_FORMAT);
        if (dateFormat != null) {
            builder.isoDateFormat();
        }

        Boolean showType = com.cedarsoftware.util.Converter.convert(optionalArgs.get(TYPE), Boolean.class);
        if (showType == null) {
            builder.showTypeInfoMinimal();
        } else if (showType) {
            builder.showTypeInfoAlways();
        } else {
            builder.showTypeInfoNever();
        }

        boolean prettyPrint = com.cedarsoftware.util.Converter.convert(optionalArgs.get(PRETTY_PRINT), boolean.class);
        builder.prettyPrint(prettyPrint);

        boolean writeLongsAsStrings = com.cedarsoftware.util.Converter.convert(optionalArgs.get(WRITE_LONGS_AS_STRINGS), boolean.class);
        builder.writeLongsAsStrings(writeLongsAsStrings);

        boolean shortMetaKeys = com.cedarsoftware.util.Converter.convert(optionalArgs.get(SHORT_META_KEYS), boolean.class);
        builder.shortMetaKeys(shortMetaKeys);

        boolean skipNullFields = com.cedarsoftware.util.Converter.convert(optionalArgs.get(SKIP_NULL_FIELDS), boolean.class);
        builder.skipNullFields(skipNullFields);

        boolean forceMapOutputAsTwoArrays = com.cedarsoftware.util.Converter.convert(optionalArgs.get(FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS), boolean.class);
        builder.forceMapOutputAsTwoArrays(forceMapOutputAsTwoArrays);

        boolean writeEnumsAsJsonObject = com.cedarsoftware.util.Converter.convert(optionalArgs.get(ENUM_PUBLIC_ONLY), boolean.class);
        builder.writeEnumAsJsonObject(writeEnumsAsJsonObject);

        Object loader = optionalArgs.get(CLASSLOADER);
        ClassLoader classLoader;
        if (loader instanceof ClassLoader) {
            classLoader = (ClassLoader) loader;
        } else {
            classLoader = ClassUtilities.getClassLoader(JsonIo.class);
        }
        builder.classLoader(classLoader);

        Object aliasMap = optionalArgs.get(TYPE_NAME_MAP);
        if (aliasMap instanceof Map) {
            Map<String, String> aliases = (Map<String, String>) aliasMap;
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                builder.aliasTypeName(entry.getKey(), entry.getValue());
            }
        }

        Object customWriterMap = optionalArgs.get(CUSTOM_WRITER_MAP);
        if (customWriterMap instanceof Map) {
            Map<String, Object> customWriters = (Map<String, Object>) customWriterMap;
            for (Map.Entry<String, Object> entry : customWriters.entrySet()) {
                try {
                    Class<?> clazz = Class.forName(entry.getKey());
                    builder.addCustomWrittenClass(clazz, (com.cedarsoftware.io.JsonWriter.JsonClassWriter) entry.getValue());
                } catch (ClassNotFoundException e) {
                    String message = "Custom json-io writer class: " + entry.getKey() + " not found.";
                    throw new com.cedarsoftware.io.JsonIoException(message, e);
                } catch (ClassCastException e) {
                    String message = "Custom json-io writer for: " + entry.getKey() + " must be an instance of com.cedarsoftware.io.JsonWriter.JsonClassWriter.";
                    throw new com.cedarsoftware.io.JsonIoException(message, e);
                }
            }
        }

        Object notCustomWritersObject = optionalArgs.get(NOT_CUSTOM_WRITER_MAP);
        if (notCustomWritersObject instanceof Iterable) {
            Iterable<Class<?>> notCustomWriters = (Iterable<Class<?>>) notCustomWritersObject;
            for (Class<?> notCustomWriter : notCustomWriters)
            {
                builder.addNotCustomWrittenClass(notCustomWriter);
            }
        }

        Object fieldSpecifiers = optionalArgs.get(FIELD_SPECIFIERS);
        if (fieldSpecifiers instanceof Map) {
            Map<Class<?>, Collection<String>> includedFields = (Map<Class<?>, Collection<String>>) fieldSpecifiers;
            for (Map.Entry<Class<?>, Collection<String>> entry : includedFields.entrySet()) {
                for (String fieldName : entry.getValue()) {
                    builder.addIncludedField(entry.getKey(), fieldName);
                }
            }
        }

        Object fieldBlackList = optionalArgs.get(FIELD_NAME_BLACK_LIST);
        if (fieldBlackList instanceof Map) {
            Map<Class<?>, Collection<String>> excludedFields = (Map<Class<?>, Collection<String>>) fieldBlackList;
            for (Map.Entry<Class<?>, Collection<String>> entry : excludedFields.entrySet()) {
                for (String fieldName : entry.getValue()) {
                    builder.addExcludedField(entry.getKey(), fieldName);
                }
            }
        }

        for (Map.Entry<String, Object> entry : optionalArgs.entrySet())
        {
            if (OPTIONAL_WRITE_KEYS.contains(entry.getKey())) {
                continue;
            }
            builder.addCustomOption(entry.getKey(), entry.getValue());
        }

        return builder;
    }

    public static String PREFIX = "-~";
    public static String SUFFIX = "~-";

    //
    // READ Option Keys (older method of specifying options) -----------------------------------------------------------
    //
    /** If set, this maps class ==> CustomReader */
    public static final String CUSTOM_READER_MAP = "CUSTOM_READERS";
    /** If set, this indicates that no custom reader should be used for the specified class ==> CustomReader */
    public static final String NOT_CUSTOM_READER_MAP = "NOT_CUSTOM_READERS";
    /** If set, the read-in JSON will be turned into a Map of Maps (JsonObject) representation */
    public static final String USE_MAPS = "USE_MAPS";
    /** What to do when an object is found and 'type' cannot be determined. */
    public static final String UNKNOWN_OBJECT = "UNKNOWN_OBJECT";
    /** Will fail JSON parsing if 'type' class defined but is not on classpath. */
    public static final String FAIL_ON_UNKNOWN_TYPE = "FAIL_ON_UNKNOWN_TYPE";
    /** If set, this map will be used when writing @type values - allows short-hand abbreviations type names */
    public static final String TYPE_NAME_MAP = "TYPE_NAME_MAP";
    /** If set, this object will be called when a field is present in the JSON but missing from the corresponding class */
    public static final String MISSING_FIELD_HANDLER = "MISSING_FIELD_HANDLER";
    /** If set, use the specified ClassLoader */
    public static final String CLASSLOADER = "CLASSLOADER";
    /** Default maximum parsing depth */
    public static final String MAX_PARSE_DEPTH = "MAX_PARSE_DEPTH";
    private static final Set<String> OPTIONAL_READ_KEYS = new HashSet<>(Arrays.asList(
            CUSTOM_READER_MAP, NOT_CUSTOM_READER_MAP, USE_MAPS, UNKNOWN_OBJECT, "UNKNOWN_TYPE", FAIL_ON_UNKNOWN_TYPE,
            TYPE_NAME_MAP, MISSING_FIELD_HANDLER, CLASSLOADER));
    
    //
    // WRITE Option Keys (older method of specifying options) -----------------------------------------------------------
    //
    /** If set, this maps class ==> CustomWriter */
    public static final String CUSTOM_WRITER_MAP = "CUSTOM_WRITERS";
    /** If set, this maps class ==> CustomWriter */
    public static final String NOT_CUSTOM_WRITER_MAP = "NOT_CUSTOM_WRITERS";
    /** Set the date format to use within the JSON output */
    public static final String DATE_FORMAT = "DATE_FORMAT";
    /** Constant for use as DATE_FORMAT value */
    public static final String ISO_DATE_FORMAT = "yyyy-MM-dd";
    /** Constant for use as DATE_FORMAT value */
    public static final String ISO_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    /** Force @type always */
    public static final String TYPE = "TYPE";
    /** Force nicely formatted JSON output */
    public static final String PRETTY_PRINT = "PRETTY_PRINT";
    /** Set value to a {@code Map<Class, List<String>>} which will be used to control which fields on a class are output */
    public static final String FIELD_SPECIFIERS = "FIELD_SPECIFIERS";
    /** Set value to a {@code Map<Class, List<String>>} which will be used to control which fields on a class are not output. Black list has always priority to FIELD_SPECIFIERS */
    public static final String FIELD_NAME_BLACK_LIST = "FIELD_NAME_BLACK_LIST";
    /** If set, indicates that private variables of ENUMs are not to be serialized */
    public static final String ENUM_PUBLIC_ONLY = "ENUM_PUBLIC_ONLY";
    /** If set, longs are written in quotes (Javascript safe) */
    public static final String WRITE_LONGS_AS_STRINGS = "WLAS";
    /** If set, then @type -> @t, @keys -> @k, @items -> @i */
    public static final String SHORT_META_KEYS = "SHORT_META_KEYS";
    /** If set, null fields are not written */
    public static final String SKIP_NULL_FIELDS = "SKIP_NULL";
    /** If set to true all maps are transferred to the format @keys[],@items[] regardless of the key_type */
    public static final String FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS = "FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS";
    private static final Set<String> OPTIONAL_WRITE_KEYS = new HashSet<>(Arrays.asList(
            CUSTOM_WRITER_MAP, NOT_CUSTOM_WRITER_MAP, DATE_FORMAT, TYPE, PRETTY_PRINT, ENUM_PUBLIC_ONLY, WRITE_LONGS_AS_STRINGS,
            TYPE_NAME_MAP, SHORT_META_KEYS, SKIP_NULL_FIELDS, CLASSLOADER, FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS));
}