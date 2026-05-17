package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Writer;

/**
 * Implement this interface to customize the JSON output for a given class.
 * <p>
 * Register your custom writer using {@link WriteOptionsBuilder#addCustomWrittenClass(Class, JsonClassWriter)}.
 *
 * <h3>Two emission APIs (transition period — old is deprecated)</h3>
 *
 * <p>Historically, custom writers received a {@link Writer} and a {@link WriterContext} and emitted
 * JSON by composing {@code output.write(...)} calls with {@code context.write*} helpers. As of
 * json-io 4.103.0, custom writers should instead use the {@link JsonGenerator} overloads of
 * {@link #write(Object, boolean, JsonGenerator, WriterContext) write} and
 * {@link #writePrimitiveForm(Object, JsonGenerator, WriterContext) writePrimitiveForm} — the
 * generator handles JSON escape, structural state, auto-commas, pretty-printing, JSON5 / NaN /
 * Infinity policy, and {@code maxStringLength} enforcement automatically. The {@link Writer}-based
 * methods are kept for backwards compatibility and are scheduled for removal in 5.0.
 *
 * <p>The framework selects which overload to invoke based on which one your implementation has
 * overridden. Overriding only the deprecated form is a fully supported migration state; you do
 * not need to migrate immediately. If your class overrides the new form, the deprecated form is
 * bypassed entirely. If a class overrides BOTH forms (e.g. a built-in writer that needs to
 * support {@code super.write*(...)} chaining from user subclasses written against the old API),
 * the new form is preferred.
 *
 * <h3>Migration example</h3>
 * <pre>{@code
 * // OLD (deprecated)
 * public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
 *     output.write('"');
 *     output.write(o.toString());
 *     output.write('"');
 * }
 *
 * // NEW
 * public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
 *     gen.writeString(o.toString());   // escape + quoting handled by gen
 * }
 * }</pre>
 *
 * @param <T> the type of object this writer handles
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
public interface JsonClassWriter<T> {
    /**
     * <b>Deprecated</b> — override
     * {@link #write(Object, boolean, JsonGenerator, WriterContext)} instead.
     *
     * <p>Write the object in JSON format to the output Writer. Invoked by the framework when this
     * custom writer's implementation overrides this method (and does NOT also override the
     * {@link JsonGenerator}-based variant). Scheduled for removal in json-io 5.0.
     *
     * @param o        Object to be written in JSON format
     * @param showType boolean indicating whether to include @type in the output
     * @param output   Writer destination where the JSON is written
     * @param context  WriterContext providing access to WriteOptions and other write utilities
     * @throws IOException if an I/O error occurs during writing
     */
    @Deprecated
    default void write(T o, boolean showType, Writer output, WriterContext context) throws IOException {
    }

    /**
     * Write the object in JSON format using the streaming {@link JsonGenerator} API.
     *
     * <p>The generator handles JSON escape, structural state (auto-commas), pretty-printing, JSON5
     * unquoted keys / single quotes, NaN/Infinity policy, and {@code maxStringLength} enforcement
     * automatically — your implementation should compose
     * {@link JsonGenerator#writeFieldName(String) writeFieldName} +
     * {@link JsonGenerator#writeString(String) writeString} /
     * {@link JsonGenerator#writeNumber(long) writeNumber} / etc. without managing commas or quoting
     * by hand.
     *
     * <p>On entry, the generator is positioned <b>inside an open object body for this value</b> —
     * the surrounding {@code &#123;} has already been emitted by the framework and any
     * {@code @id}/{@code @type} prelude has been written. Your implementation should emit field-name
     * + value pairs for the body of the object. The framework will close the object with the
     * matching {@code &#125;} after your method returns.
     *
     * <p>This method supersedes {@link #write(Object, boolean, Writer, WriterContext)}; both are
     * supported during the 4.x migration period but the Writer-based form will be removed in 5.0.
     * If your class overrides this method, the deprecated Writer-based form is bypassed.
     *
     * @param o        Object to be written in JSON format
     * @param showType boolean indicating whether to include {@code @type} in the output
     * @param gen      JsonGenerator destination — positioned inside this value's object body
     * @param context  WriterContext providing access to WriteOptions, recursion, and identity refs
     * @throws IOException if an I/O error occurs during writing
     * @since 4.103.0
     */
    default void write(T o, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
    }

    /**
     * Indicates whether this class has a primitive (non-object) JSON form.
     * Most custom writers will not have a primitive form, so the default is false.
     *
     * @param context WriterContext providing access to WriteOptions
     * @return true if this class can be written as a JSON primitive value
     */
    default boolean hasPrimitiveForm(WriterContext context) {
        return false;
    }

    /**
     * <b>Deprecated</b> — override
     * {@link #writePrimitiveForm(Object, JsonGenerator, WriterContext)} instead.
     *
     * <p>Write the object in its primitive JSON form (without surrounding braces). Only called if
     * {@link #hasPrimitiveForm(WriterContext)} returns true and this method is overridden in
     * preference to the {@link JsonGenerator}-based variant. Scheduled for removal in json-io 5.0.
     *
     * @param o       Object to be written
     * @param output  Writer destination where the JSON is written
     * @param context WriterContext providing access to WriteOptions and writing tools
     * @throws IOException if an I/O error occurs during writing
     */
    @Deprecated
    default void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
    }

    /**
     * Write the object in its primitive JSON form using the streaming {@link JsonGenerator} API.
     * Only called if {@link #hasPrimitiveForm(WriterContext)} returns true.
     *
     * <p>On entry, the generator is positioned at a <b>value slot</b> — your implementation should
     * emit exactly one JSON value (scalar via
     * {@link JsonGenerator#writeString(String) writeString} /
     * {@link JsonGenerator#writeNumber(long) writeNumber} / etc., or a complete structure via
     * {@link JsonGenerator#writeStartArray() writeStartArray}…{@link JsonGenerator#writeEndArray()
     * writeEndArray}). Auto-comma and quoting are handled by the generator; do not emit
     * punctuation yourself.
     *
     * <p>This method supersedes {@link #writePrimitiveForm(Object, Writer, WriterContext)}; both
     * are supported during the 4.x migration period but the Writer-based form will be removed in
     * 5.0. If your class overrides this method, the deprecated Writer-based form is bypassed.
     *
     * @param o       Object to be written
     * @param gen     JsonGenerator destination, positioned at a value slot
     * @param context WriterContext providing access to WriteOptions and writing tools
     * @throws IOException if an I/O error occurs during writing
     * @since 4.103.0
     */
    default void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
    }

    /**
     * Get the type name to use for @type when writing this object.
     * Override to provide a custom type name.
     *
     * @param o the object being written
     * @return the type name string (defaults to the object's class name)
     */
    default String getTypeName(Object o) {
        return o.getClass().getName();
    }
}
