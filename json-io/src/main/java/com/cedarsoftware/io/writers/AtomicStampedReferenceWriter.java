package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicStampedReference;

import com.cedarsoftware.io.JsonClassWriter;
import com.cedarsoftware.io.JsonGenerator;
import com.cedarsoftware.io.WriterContext;

/**
 * Writes an {@link AtomicStampedReference} through its public methods as
 * {@code "pair":{"reference":...,"stamp":...}}: the shape json-io wrote by reflection where it could read its field
 * (JDK 8 and 11), so JSON written then still reads. On JDK 17+ java.util.concurrent keeps that field to itself, and
 * the reference and stamp were written as null. Both are read in one step, as a consistent pair. The reference trace
 * in {@code JsonWriter} visits the reference, so a shared object or a cycle through it is kept;
 * {@link com.cedarsoftware.io.factory.AtomicStampedReferenceFactory} reads it back.
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
public class AtomicStampedReferenceWriter implements JsonClassWriter {

    @Override
    public void write(Object obj, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
        int[] stamp = new int[1];
        Object reference = ((AtomicStampedReference<?>) obj).get(stamp);
        context.writeObjectFieldStart("pair");
        context.writeObjectField("reference", reference, Object.class);
        context.writeNumberField("stamp", stamp[0]);
        context.writeEndObject();
    }

    @Override
    @Deprecated
    public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
        JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_insideObjectBody(
                output, context.getWriteOptions());
        write(obj, showType, bridge, context);
    }
}
