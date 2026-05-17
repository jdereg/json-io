package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.Base64;

import com.cedarsoftware.io.JsonClassWriter;
import com.cedarsoftware.io.JsonGenerator;
import com.cedarsoftware.io.WriterContext;

/**
 * Custom writer for {@link ByteBuffer} — emits the buffer's remaining bytes as a single
 * Base64-encoded {@code "value"} field inside json-io's standard {@code @type}-tagged object
 * envelope.
 *
 * <p>Migrated to the new {@link JsonGenerator}-based {@link JsonClassWriter} API in
 * json-io 4.103.0. The deprecated {@link Writer}-based override is retained as a thin
 * delegate so that user subclasses written against the old API can still chain via
 * {@code super.write(o, output, ctx)} without behaviour change.
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
public class ByteBufferWriter implements JsonClassWriter {

    /**
     * New {@link JsonGenerator}-based emission path. The framework dispatches here for
     * writers (or user subclasses) that override this method; the deprecated
     * {@link #write(Object, boolean, Writer, WriterContext)} override is retained
     * only as a delegate for {@code super}-chaining compatibility.
     */
    @Override
    public void write(Object obj, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
        ByteBuffer bytes = (ByteBuffer) obj;
        String encoded;

        if (bytes.hasArray()) {
            // Array-backed buffer: copy exactly the [position, limit) slice without mutating the buffer.
            int offset = bytes.arrayOffset() + bytes.position();
            int length = bytes.remaining();
            byte[] slice = new byte[length];
            System.arraycopy(bytes.array(), offset, slice, 0, length);
            encoded = Base64.getEncoder().encodeToString(slice);
        } else {
            // Direct (non-heap) buffer: save/restore position so serialization has no side effects.
            int originalPosition = bytes.position();
            try {
                byte[] tmp = new byte[bytes.remaining()];
                bytes.get(tmp);
                encoded = Base64.getEncoder().encodeToString(tmp);
            } finally {
                bytes.position(originalPosition);
            }
        }

        gen.writeFieldName("value");
        gen.writeString(encoded);
    }

    /**
     * <b>Deprecated</b> bridge to the {@link JsonGenerator}-based override above.
     * Kept so user subclasses written against the pre-4.103.0 API can still
     * call {@code super.write(o, output, ctx)} and get the built-in behaviour.
     * Scheduled for removal in 5.0.
     */
    @Override
    @Deprecated
    public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
        JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_insideObjectBody(
                output, context.getWriteOptions());
        write(obj, showType, bridge, context);
    }
}
