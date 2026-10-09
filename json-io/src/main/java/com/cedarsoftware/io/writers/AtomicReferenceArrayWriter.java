package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;

import com.cedarsoftware.io.JsonClassWriter;
import com.cedarsoftware.io.JsonGenerator;
import com.cedarsoftware.io.WriterContext;

/**
 * Writes an {@link AtomicReferenceArray} through its public methods as {@code "array":[...]}: the shape json-io wrote
 * by reflection where it could read the array's field (JDK 8 and 11), so JSON written then still reads. On JDK 17+
 * java.util.concurrent keeps that field to itself, and the array was written as null. The elements are written as
 * that field was, an {@code Object[]}: {@code @type} where the JSON alone would not say it (an {@code Integer}, not a
 * {@code Long}), {@code @ref} for an object written before. The reference trace in {@code JsonWriter} visits the elements, so shared objects and cycles are kept;
 * {@link com.cedarsoftware.io.factory.AtomicReferenceArrayFactory} reads it back.
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
public class AtomicReferenceArrayWriter implements JsonClassWriter {

    @Override
    public void write(Object obj, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
        AtomicReferenceArray<?> array = (AtomicReferenceArray<?>) obj;
        Object[] elements = new Object[array.length()];
        for (int i = 0; i < elements.length; i++) {
            elements[i] = array.get(i);
        }
        context.writeObjectField("array", elements, Object[].class);
    }

    @Override
    @Deprecated
    public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
        JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_insideObjectBody(
                output, context.getWriteOptions());
        write(obj, showType, bridge, context);
    }

    /** {@code array}: its elements, as {@code write} writes them (TOON, and the reference pass). */
    @Override
    public Object toData(Object obj) {
        AtomicReferenceArray<?> array = (AtomicReferenceArray<?>) obj;
        Object[] elements = new Object[array.length()];
        for (int i = 0; i < elements.length; i++) {
            elements[i] = array.get(i);
        }
        Map<String, Object> data = new LinkedHashMap<>(2);
        data.put("array", elements);
        return data;
    }
}
