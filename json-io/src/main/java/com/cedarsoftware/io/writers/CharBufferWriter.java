package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.nio.CharBuffer;

import com.cedarsoftware.io.JsonClassWriter;
import com.cedarsoftware.io.JsonGenerator;
import com.cedarsoftware.io.WriterContext;

/**
 * Custom writer for {@link CharBuffer} — emits the buffer's remaining characters as a single
 * {@code "value":"<text>"} field inside json-io's standard {@code @type}-tagged object envelope.
 *
 * <p>Migrated to the {@link JsonGenerator}-based {@link JsonClassWriter} API in
 * json-io 4.103.0. The deprecated {@link Writer}-based override is retained as a thin
 * delegate so user subclasses written against the old API can chain via
 * {@code super.write(...)} unchanged.
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
public class CharBufferWriter implements JsonClassWriter {

    @Override
    public void write(Object obj, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
        CharBuffer chars = (CharBuffer) obj;
        String value;
        if (chars.hasArray()) {
            // Array-backed: read the [position, limit) slice without mutating the buffer.
            int offset = chars.arrayOffset() + chars.position();
            int length = chars.remaining();
            value = new String(chars.array(), offset, length);
        } else {
            // Direct (non-heap) buffer: save/restore position so serialization is side-effect free.
            int originalPosition = chars.position();
            try {
                char[] tmp = new char[chars.remaining()];
                chars.get(tmp);
                value = new String(tmp);
            } finally {
                chars.position(originalPosition);
            }
        }
        gen.writeFieldName("value");
        gen.writeString(value);
    }

    @Override
    @Deprecated
    public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
        JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_insideObjectBody(
                output, context.getWriteOptions());
        write(obj, showType, bridge, context);
    }
}
