package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.util.Base64;

import com.cedarsoftware.io.JsonClassWriter;
import com.cedarsoftware.io.JsonGenerator;
import com.cedarsoftware.io.WriterContext;

/**
 * Custom writer for {@code byte[]} — Base64-encodes the array and emits it as a quoted
 * JSON string in the writer's primitive-form slot.
 *
 * <p>Migrated to the new {@link JsonGenerator}-based {@link JsonClassWriter} API in
 * json-io 4.103.0. The deprecated {@link Writer}-based override is retained as a thin
 * delegate so that user subclasses written against the old API can still chain via
 * {@code super.writePrimitiveForm(o, output, ctx)} without behaviour change.
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
public class ByteArrayWriter implements JsonClassWriter {

    /**
     * New {@link JsonGenerator}-based emission path. The framework dispatches here for
     * writers (or user subclasses) that override this method; the deprecated
     * {@link #writePrimitiveForm(Object, Writer, WriterContext)} override is retained
     * only as a delegate for {@code super}-chaining compatibility.
     */
    @Override
    public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
        final byte[] bytes = (byte[]) o;
        gen.writeString(Base64.getEncoder().encodeToString(bytes));
    }

    /**
     * <b>Deprecated</b> bridge to the {@link JsonGenerator}-based override above.
     * Kept so user subclasses written against the pre-4.103.0 API can still
     * call {@code super.writePrimitiveForm(o, output, ctx)} and get the built-in
     * behaviour. Scheduled for removal in 5.0.
     */
    @Override
    @Deprecated
    public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
        JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(
                output, context.getWriteOptions());
        writePrimitiveForm(o, bridge, context);
    }

    @Override
    public boolean hasPrimitiveForm(WriterContext context) {
        return true;
    }
}
