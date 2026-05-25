package com.cedarsoftware.io;

import java.io.IOException;

/**
 * Convenience surface passed to {@link JsonClassWriter} implementations during
 * tree-walking serialization (via {@link JsonWriter}). Provides field-write
 * primitives and access to the active {@link WriteOptions} so a custom writer
 * can emit JSON fragments without re-implementing escape logic, comma placement,
 * or option lookups.
 *
 * <h3>Relationship to {@link JsonGenerator}</h3>
 *
 * <p>{@code WriterContext} is the <b>tree-walker's</b> custom-writer hook —
 * it exists so a {@code JsonClassWriter} can collaborate with the surrounding
 * graph-serializer ({@code JsonWriter}) while writing a custom representation
 * for one type. {@link JsonGenerator} is the <b>streaming-write API</b> for
 * callers who are <i>not</i> walking a Java object graph at all — for example,
 * emitting JSON token-by-token in a transform pipeline or porting Jackson
 * {@code JsonGenerator} code.
 *
 * <p>As of json-io 4.103.0 the two surfaces share one underlying state machine:
 * {@code WriterContext.writeXxxField} / {@code writeFieldName} / etc. all
 * delegate to a {@link JsonGenerator} that {@code JsonWriter} owns, so all
 * field-emission methods on this interface use the same Jackson-style auto-
 * comma context machine. Callers emit a sequence of tokens and the framework
 * inserts (or rejects as misuse) structural separators automatically — there
 * are no longer any methods that <i>unconditionally</i> emit a leading comma.
 * New {@code JsonClassWriter} implementations are encouraged to override the
 * {@code write(T, boolean, JsonGenerator, WriterContext)} overload directly,
 * which gives the writer direct access to the generator and bypasses this
 * convenience interface entirely.
 *
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
     * Returns the per-field format pattern (e.g., date/time pattern from {@code @IoFormat}),
     * or {@code null} if no field-level format is active. Only meaningful during field writing.
     * @return the format pattern string, or null
     */
    default String getFieldFormatPattern() {
        return null;
    }

    /**
     * Allows you to use the current JsonWriter to write an object out.
     */
    void writeObject(final Object obj, boolean showType, boolean bodyOnly) throws IOException;

    /**
     * Write any object fully.
     */
    void writeImpl(Object obj, boolean showType) throws IOException;

    /**
     * Provide access to all objects that are referenced.
     * Uses identity comparison (==) for keys.
     */
    IdentityIntMap getObjsReferenced();

    // ======================== Semantic Write API ========================
    // These methods provide a high-level, type-safe API for writing JSON structures.
    // They handle quote escaping, comma management, and proper JSON syntax automatically.

    /**
     * Writes a JSON field name followed by a colon (and, in pretty-print mode, a
     * trailing space) — Jackson-aligned {@code writeFieldName} semantics. Handles
     * quote escaping (or JSON5 unquoted identifier emission when enabled) automatically.
     * <p>
     * Example: {@code writeFieldName("name")} produces {@code "name":} (or
     * {@code "name": } in pretty-print mode).
     * <p>
     * The leading comma between fields is inserted automatically by the underlying
     * state machine — callers do <i>not</i> emit it. Equivalent in semantics to the
     * Jackson {@code com.fasterxml.jackson.core.JsonGenerator.writeFieldName}.
     *
     * @param name the field name to write (without quotes)
     * @throws IOException if an I/O error occurs
     */
    void writeFieldName(String name) throws IOException;

    /**
     * Writes a complete JSON string field — equivalent to
     * {@link #writeFieldName(String)} followed by {@link #writeValue(String)}.
     * Jackson-aligned {@code writeStringField} semantics.
     * <p>
     * Example: {@code writeStringField("name", "John")} produces {@code "name":"John"}
     * for the first field in an object, or {@code ,"name":"John"} for a subsequent
     * field — the leading comma is auto-emitted by the state machine when required.
     * <p>
     * Escapes special characters in both field name and value. Handles {@code null}
     * values by writing the JSON literal {@code null} for the value.
     * <p>
     * <b>Usage in custom writers:</b>
     * <pre>{@code
     * public void write(Object obj, boolean showType,
     *                  JsonGenerator gen, WriterContext context) {
     *     MyClass instance = (MyClass) obj;
     *     context.writeStringField("firstName", instance.getFirstName());
     *     context.writeStringField("lastName",  instance.getLastName());
     * }
     * }</pre>
     * Note: the surrounding {@code {/}} are emitted by the framework around the
     * custom writer's body — the writer should NOT emit them itself.
     *
     * @param name the field name
     * @param value the string value (may be null)
     * @throws IOException if an I/O error occurs
     */
    void writeStringField(String name, String value) throws IOException;

    /**
     * Writes a complete JSON object field — equivalent to
     * {@link #writeFieldName(String)} followed by full graph serialization of the
     * value (the same code path as {@link JsonIo#toJson(Object, WriteOptions)},
     * including cycle tracking and {@code @type} policy).
     * <p>
     * Example: {@code writeObjectField("address", addressObj)} produces
     * {@code "address":{...}} for the first field, or {@code ,"address":{...}} for
     * a subsequent field — the leading comma is auto-emitted by the state machine
     * when required.
     * <p>
     * This method handles {@code null} (writes the JSON literal {@code null}),
     * circular references (emits {@code @ref}), and {@code @type} emission according
     * to the active {@link WriteOptions}.
     * <p>
     * <b>Usage in custom writers:</b>
     * <pre>{@code
     * public void write(Object obj, boolean showType,
     *                  JsonGenerator gen, WriterContext context) {
     *     MyClass instance = (MyClass) obj;
     *     context.writeObjectField("config", instance.getConfig());
     *     context.writeObjectField("data",   instance.getData());
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
     * Writes a JSON object opening brace "{".
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
     * Writes a JSON object closing brace "}".
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

    /**
     * Writes a JSON array-field opening — equivalent to
     * {@link #writeFieldName(String)} followed by {@link #writeStartArray()}.
     * Jackson-aligned {@code writeArrayFieldStart} semantics. The leading comma
     * (when this is a non-first field) is auto-emitted by the state machine.
     * <p>
     * Example: {@code writeArrayFieldStart("items")} produces {@code "items":[}
     * for the first field, or {@code ,"items":[} for a subsequent field.
     * <p>
     * <b>Usage pattern:</b>
     * <pre>{@code
     * context.writeArrayFieldStart("entries");
     * for (Entry entry : entries) {
     *     context.writeValue(entry);
     * }
     * context.writeEndArray();
     * }</pre>
     *
     * @param name the field name
     * @throws IOException if an I/O error occurs
     */
    void writeArrayFieldStart(String name) throws IOException;

    /**
     * Writes a JSON object-field opening — equivalent to
     * {@link #writeFieldName(String)} followed by {@link #writeStartObject()}.
     * Jackson-aligned {@code writeObjectFieldStart} semantics. The leading comma
     * (when this is a non-first field) is auto-emitted by the state machine.
     * <p>
     * Example: {@code writeObjectFieldStart("config")} produces <code>"config":{</code>
     * for the first field, or <code>,"config":{</code> for a subsequent field.
     * <p>
     * <b>Usage pattern:</b>
     * <pre>{@code
     * context.writeObjectFieldStart("metadata");
     * context.writeStringField("version", "1.0");
     * context.writeNumberField("count", 42);
     * context.writeEndObject();
     * }</pre>
     *
     * @param name the field name
     * @throws IOException if an I/O error occurs
     */
    void writeObjectFieldStart(String name) throws IOException;

    /**
     * Writes a complete JSON number field — equivalent to
     * {@link #writeFieldName(String)} followed by the number value (unquoted, per
     * JSON spec). Jackson-aligned {@code writeNumberField} semantics. The leading
     * comma (when this is a non-first field) is auto-emitted by the state machine.
     * <p>
     * Example: {@code writeNumberField("count", 42)} produces {@code "count":42}
     * for the first field, or {@code ,"count":42} for a subsequent field.
     * <p>
     * Handles {@code null} by writing the JSON literal {@code null} for the value.
     * <p>
     * <b>Usage pattern:</b>
     * <pre>{@code
     * context.writeNumberField("capacity",   16);
     * context.writeNumberField("loadFactor", 0.75f);
     * context.writeNumberField("size",       100L);
     * }</pre>
     *
     * @param name the field name
     * @param value the number value (may be null)
     * @throws IOException if an I/O error occurs
     */
    void writeNumberField(String name, Number value) throws IOException;

    /**
     * Writes a complete JSON boolean field — equivalent to
     * {@link #writeFieldName(String)} followed by the boolean value (unquoted, per
     * JSON spec). Jackson-aligned {@code writeBooleanField} semantics. The leading
     * comma (when this is a non-first field) is auto-emitted by the state machine.
     * <p>
     * Example: {@code writeBooleanField("active", true)} produces {@code "active":true}
     * for the first field, or {@code ,"active":true} for a subsequent field.
     * <p>
     * <b>Usage pattern:</b>
     * <pre>{@code
     * context.writeBooleanField("caseSensitive", true);
     * context.writeBooleanField("enabled",       false);
     * }</pre>
     *
     * @param name the field name
     * @param value the boolean value
     * @throws IOException if an I/O error occurs
     */
    void writeBooleanField(String name, boolean value) throws IOException;
}
