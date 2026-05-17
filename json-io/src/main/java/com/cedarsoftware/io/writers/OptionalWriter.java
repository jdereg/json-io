package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.util.Optional;

import com.cedarsoftware.io.JsonClassWriter;
import com.cedarsoftware.io.JsonGenerator;
import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.WriterContext;

/**
 * Custom writer for {@link Optional}.
 * <p>
 * By default, writes Optional values in Jackson/Gson-compatible primitive form:
 * {@code Optional.empty()} becomes {@code null} and {@code Optional.of(value)} becomes
 * the bare {@code value}. When {@link WriteOptions#isWriteOptionalAsObject()} is true,
 * the legacy json-io object form is used instead ({@code {"present":true,"value":X}} or
 * {@code {"present":false}}). The legacy form is also emitted whenever type info must be
 * shown for polymorphic context or when the framework has attached an {@code @id} marker.
 *
 * <p>Migrated to the {@link JsonGenerator}-based {@link JsonClassWriter} API in
 * json-io 4.103.0. The deprecated {@link Writer}-based overrides are retained as
 * thin delegates so user subclasses written against the old API can chain via
 * {@code super.write*()} unchanged. The contained-value recursion still goes
 * through {@link WriterContext#writeImpl(Object, boolean)} (the tree-walker
 * callback) — that path writes to the underlying Writer directly, bypassing
 * the bridge generator's structural state but producing the same bytes.
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
public class OptionalWriter implements JsonClassWriter {

    @Override
    public void write(Object obj, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
        Optional<?> opt = (Optional<?>) obj;
        gen.writeFieldName("present");
        gen.writeBoolean(opt.isPresent());
        if (opt.isPresent()) {
            gen.writeFieldName("value");
            // Recurse through the tree-walker so custom writers, cycle tracking, and @type
            // policy apply uniformly. The tree-walker writes to the underlying Writer
            // directly (the same stream the bridge generator wraps).
            context.writeImpl(opt.get(), true);
        }
    }

    @Override
    @Deprecated
    public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
        JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_insideObjectBody(
                output, context.getWriteOptions());
        write(obj, showType, bridge, context);
    }

    @Override
    public boolean hasPrimitiveForm(WriterContext context) {
        // Primitive form is used by the framework only when !referenced && !showType.
        // When the user has explicitly opted into the legacy object form, return false
        // so the framework always wraps in { ... } and calls write() for the object body.
        WriteOptions wo = context.getWriteOptions();
        return wo == null || !wo.isWriteOptionalAsObject();
    }

    @Override
    public void writePrimitiveForm(Object obj, JsonGenerator gen, WriterContext context) throws IOException {
        Optional<?> opt = (Optional<?>) obj;
        if (opt.isPresent()) {
            // Recurse to write the contained value via the tree-walker (honors custom
            // writers, cycle tracking, etc.). Writes to the underlying Writer directly.
            context.writeImpl(opt.get(), true);
        } else {
            gen.writeNull();
        }
    }

    @Override
    @Deprecated
    public void writePrimitiveForm(Object obj, Writer output, WriterContext context) throws IOException {
        JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(
                output, context.getWriteOptions());
        writePrimitiveForm(obj, bridge, context);
    }
}
