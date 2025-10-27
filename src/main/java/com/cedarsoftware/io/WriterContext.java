package com.cedarsoftware.io;

import java.io.IOException;
import java.util.Map;

/**
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
public interface WriterContext {

    /**
     * Gets the write options for the current serialization
     * @return WriteOptions
     */
    WriteOptions getWriteOptions();

    /**
     * Allows you to use the current JsonWriter to write an object out.
     */
    void writeObject(final Object obj, boolean showType, boolean bodyOnly) throws IOException;

    /**
     * Write any object fully.
     */
    void writeImpl(Object obj, boolean showType) throws IOException;

    /**
     * Provide access to all objects that are referenced
     */
    Map<Object, Long> getObjsReferenced();

    // ======================== Semantic Write API ========================
    // These methods provide a high-level, type-safe API for writing JSON structures.
    // They handle quote escaping, comma management, and proper JSON syntax automatically.

    /**
     * Writes a JSON field name followed by a colon. This method handles quote escaping
     * and proper JSON formatting automatically.
     * <p>
     * Example: {@code writeFieldName("name")} produces {@code "name":}
     * </p>
     * <p>
     * <b>Important:</b> This method does NOT write a preceding comma. If you need a comma
     * before the field (for non-first fields in an object), you must write it manually.
     * Consider using the higher-level methods like {@link #writeStringField(String, String)}
     * or {@link #writeObjectField(String, Object)} which handle commas automatically.
     * </p>
     *
     * @param name the field name to write (without quotes)
     * @throws IOException if an I/O error occurs
     */
    void writeFieldName(String name) throws IOException;

    /**
     * Writes a complete JSON string field with automatic comma handling.
     * <p>
     * Example: {@code writeStringField("name", "John")} produces {@code ,"name":"John"}
     * </p>
     * <p>
     * This method automatically:
     * <ul>
     *   <li>Writes a preceding comma (for proper JSON object formatting)</li>
     *   <li>Escapes special characters in both field name and value</li>
     *   <li>Handles null values by writing {@code ,"name":null}</li>
     * </ul>
     * </p>
     * <p>
     * <b>Usage in custom writers:</b>
     * <pre>{@code
     * public void write(Object obj, boolean showType, Writer output, WriterContext context) {
     *     MyClass instance = (MyClass) obj;
     *     output.write('{');
     *     context.writeStringField("firstName", instance.getFirstName());
     *     context.writeStringField("lastName", instance.getLastName());
     *     output.write('}');
     * }
     * }</pre>
     * </p>
     *
     * @param name the field name
     * @param value the string value (may be null)
     * @throws IOException if an I/O error occurs
     */
    void writeStringField(String name, String value) throws IOException;

    /**
     * Writes a complete JSON object field with automatic serialization and comma handling.
     * <p>
     * Example: {@code writeObjectField("address", addressObj)} produces {@code ,"address":{...}}
     * where the address object is fully serialized according to json-io's rules.
     * </p>
     * <p>
     * This method automatically:
     * <ul>
     *   <li>Writes a preceding comma (for proper JSON object formatting)</li>
     *   <li>Serializes the value object with proper type information and reference tracking</li>
     *   <li>Handles null values by writing {@code ,"name":null}</li>
     *   <li>Handles circular references and object deduplication</li>
     * </ul>
     * </p>
     * <p>
     * <b>Usage in custom writers:</b>
     * <pre>{@code
     * public void write(Object obj, boolean showType, Writer output, WriterContext context) {
     *     MyClass instance = (MyClass) obj;
     *     output.write('{');
     *     context.writeObjectField("config", instance.getConfig());
     *     context.writeObjectField("data", instance.getData());
     *     output.write('}');
     * }
     * }</pre>
     * </p>
     *
     * @param name the field name
     * @param value the object to serialize (may be null)
     * @throws IOException if an I/O error occurs
     */
    void writeObjectField(String name, Object value) throws IOException;

    /**
     * Writes a JSON object opening brace {@code {}.
     * <p>
     * This should be paired with {@link #writeEndObject()} to properly close the object.
     * </p>
     * <p>
     * <b>Usage pattern:</b>
     * <pre>{@code
     * context.writeStartObject();
     * context.writeStringField("name", "value");
     * context.writeEndObject();
     * }</pre>
     * </p>
     *
     * @throws IOException if an I/O error occurs
     * @see #writeEndObject()
     */
    void writeStartObject() throws IOException;

    /**
     * Writes a JSON object closing brace {@code }}.
     * <p>
     * This should be paired with a preceding {@link #writeStartObject()} call.
     * </p>
     *
     * @throws IOException if an I/O error occurs
     * @see #writeStartObject()
     */
    void writeEndObject() throws IOException;

    /**
     * Writes a JSON array opening bracket {@code [}.
     * <p>
     * This should be paired with {@link #writeEndArray()} to properly close the array.
     * </p>
     * <p>
     * <b>Usage pattern:</b>
     * <pre>{@code
     * context.writeStartArray();
     * for (Item item : items) {
     *     context.writeStartObject();
     *     context.writeObjectField("data", item);
     *     context.writeEndObject();
     * }
     * context.writeEndArray();
     * }</pre>
     * </p>
     *
     * @throws IOException if an I/O error occurs
     * @see #writeEndArray()
     */
    void writeStartArray() throws IOException;

    /**
     * Writes a JSON array closing bracket {@code ]}.
     * <p>
     * This should be paired with a preceding {@link #writeStartArray()} call.
     * </p>
     *
     * @throws IOException if an I/O error occurs
     * @see #writeStartArray()
     */
    void writeEndArray() throws IOException;

    /**
     * Writes a JSON string value with proper quote escaping.
     * <p>
     * Example: {@code writeValue("Hello")} produces {@code "Hello"}
     * </p>
     * <p>
     * This method:
     * <ul>
     *   <li>Writes opening quote</li>
     *   <li>Escapes special characters (quotes, backslashes, control characters)</li>
     *   <li>Writes closing quote</li>
     *   <li>Handles null by writing {@code null} (without quotes)</li>
     * </ul>
     * </p>
     * <p>
     * <b>Usage pattern:</b>
     * <pre>{@code
     * context.writeFieldName("name");
     * context.writeValue("John Doe");
     * // Produces: "name":"John Doe"
     * }</pre>
     * </p>
     *
     * @param value the string value to write (may be null)
     * @throws IOException if an I/O error occurs
     */
    void writeValue(String value) throws IOException;

    /**
     * Writes a JSON value by serializing the given object.
     * <p>
     * Example: {@code writeValue(myObject)} produces the full JSON representation of myObject
     * </p>
     * <p>
     * This method:
     * <ul>
     *   <li>Serializes the object with proper type information and reference tracking</li>
     *   <li>Handles null by writing {@code null}</li>
     *   <li>Handles primitives, strings, collections, maps, and custom objects</li>
     *   <li>Handles circular references and object deduplication</li>
     * </ul>
     * </p>
     * <p>
     * <b>Usage pattern:</b>
     * <pre>{@code
     * context.writeFieldName("config");
     * context.writeValue(configObject);
     * // Produces: "config":{...serialized config...}
     * }</pre>
     * </p>
     *
     * @param value the object to serialize (may be null)
     * @throws IOException if an I/O error occurs
     */
    void writeValue(Object value) throws IOException;
}
