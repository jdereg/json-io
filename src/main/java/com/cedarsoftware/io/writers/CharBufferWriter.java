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

        output.write("\"value\":\"");

        if (chars.hasArray()) {
            // Use the backing array directly
            char[] array = chars.array();
            int offset = chars.arrayOffset() + chars.position();
            int end = offset + chars.remaining();

            // Process each character, escaping when needed
            for (int i = offset; i < end; i++) {
                char c = array[i];
                switch (c) {
                    case '"':  output.write("\\\""); break;
                    case '\\': output.write("\\\\"); break;
                    case '\b': output.write("\\b"); break;
                    case '\f': output.write("\\f"); break;
                    case '\n': output.write("\\n"); break;
                    case '\r': output.write("\\r"); break;
                    case '\t': output.write("\\t"); break;
                    default:
                        // Handle control characters
                        if (c < ' ') {
                            output.write(String.format("\\u%04x", (int) c));
                        } else {
                            output.write(c);
                        }
                }
            }
        } else {
            // Save position
            int originalPosition = chars.position();
            try {
                while (chars.hasRemaining()) {
                    char c = chars.get();
                    switch (c) {
                        case '"':  output.write("\\\""); break;
                        case '\\': output.write("\\\\"); break;
                        case '\b': output.write("\\b"); break;
                        case '\f': output.write("\\f"); break;
                        case '\n': output.write("\\n"); break;
                        case '\r': output.write("\\r"); break;
                        case '\t': output.write("\\t"); break;
                        default:
                            // Handle control characters
                            if (c < ' ') {
                                output.write(String.format("\\u%04x", (int) c));
                            } else {
                                output.write(c);
                            }
                    }
                }
            } finally {
                // Restore position
                chars.position(originalPosition);
            }
        }

        output.write("\"");
    }
}