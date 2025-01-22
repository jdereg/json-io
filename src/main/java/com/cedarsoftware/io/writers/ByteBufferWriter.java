package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.Base64;

import com.cedarsoftware.io.WriterContext;

import static com.cedarsoftware.io.JsonWriter.JsonClassWriter;
import static com.cedarsoftware.io.JsonWriter.writeBasicString;

/**
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
    @Override
    public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
        ByteBuffer bytes = (ByteBuffer) obj;

        // We'll store our final encoded string here
        String encoded;

        if (bytes.hasArray()) {
            // If the buffer is array-backed, we can avoid a copy by using the array offset/length
            int offset = bytes.arrayOffset() + bytes.position();
            int length = bytes.remaining();

            // Java 11+ supports an encodeToString overload with offset/length
            // encoded = Base64.getEncoder().encodeToString(bytes.array(), offset, length);

            // Make a minimal copy of exactly the slice
            byte[] slice = new byte[length];
            System.arraycopy(bytes.array(), offset, slice, 0, length);

            encoded = Base64.getEncoder().encodeToString(slice);
        } else {
            // Otherwise, we have to copy
            // Save the current position so we can restore it later
            int originalPosition = bytes.position();
            try {
                byte[] tmp = new byte[bytes.remaining()];
                bytes.get(tmp);
                encoded = Base64.getEncoder().encodeToString(tmp);
            } finally {
                // Restore the original position to avoid side-effects
                bytes.position(originalPosition);
            }
        }

        // Now write "value":"<encoded>" into the JSON output
        output.write("\"value\":");
        writeBasicString(output, encoded);
    }
}