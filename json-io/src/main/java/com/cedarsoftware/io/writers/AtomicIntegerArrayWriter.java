package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicIntegerArray;

import com.cedarsoftware.io.JsonGenerator;
import com.cedarsoftware.io.Writers;
import com.cedarsoftware.io.WriterContext;

/**
 * Writer for AtomicIntegerArray that serializes as an array of integers.
 * This produces JSON like: {"@type":"AtomicIntegerArray","value":[1,2,3,4,5]}
 *
 * <p>Migrated to the {@link JsonGenerator}-based {@link com.cedarsoftware.io.JsonClassWriter}
 * API in json-io 4.103.0. The deprecated {@link Writer}-based override is retained as a thin
 * delegate so user subclasses written against the old API can chain via
 * {@code super.writePrimitiveForm(...)} unchanged.
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
public class AtomicIntegerArrayWriter extends Writers.PrimitiveTypeWriter {

    @Override
    public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
        AtomicIntegerArray array = (AtomicIntegerArray) o;
        int length = array.length();
        gen.writeStartArray();
        for (int i = 0; i < length; i++) {
            gen.writeNumber(array.get(i));
        }
        gen.writeEndArray();
    }

    @Override
    @Deprecated
    public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
        JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(
                output, context.getWriteOptions());
        writePrimitiveForm(o, bridge, context);
    }
}
