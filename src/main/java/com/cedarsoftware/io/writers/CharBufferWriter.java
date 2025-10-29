package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.nio.CharBuffer;

import com.cedarsoftware.io.WriterContext;

import static com.cedarsoftware.io.JsonWriter.JsonClassWriter;

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
public class CharBufferWriter implements JsonClassWriter {
    @Override
    public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
        CharBuffer chars = (CharBuffer) obj;

        // Extract the string content from CharBuffer
        String value;
        if (chars.hasArray()) {
            // Use the backing array directly
            int offset = chars.arrayOffset() + chars.position();
            int length = chars.remaining();
            value = new String(chars.array(), offset, length);
        } else {
            // Save position and read into string
            int originalPosition = chars.position();
            try {
                char[] tmp = new char[chars.remaining()];
                chars.get(tmp);
                value = new String(tmp);
            } finally {
                // Restore position to avoid side-effects
                chars.position(originalPosition);
            }
        }

        // Write "value":"<string>" using WriterContext API (handles escaping)
        context.writeFieldName("value");
        context.writeValue(value);
    }
}