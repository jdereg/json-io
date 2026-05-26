package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import com.cedarsoftware.io.JsonClassWriter;
import com.cedarsoftware.io.JsonGenerator;
import com.cedarsoftware.io.WriterContext;
import com.cedarsoftware.util.CompactMap;
import com.cedarsoftware.util.CompactSet;

/**
 * Writer for CompactSet instances that produces a shortened configuration format.
 *
 * <p>Migrated to the {@link JsonGenerator}-based {@link JsonClassWriter} API in
 * json-io 4.103.0. Field-level emission of {@code "config"} uses the generator;
 * the {@code "data"} array is emitted through {@link WriterContext}'s semantic
 * API so the tree-walker's {@link com.cedarsoftware.io.JsonWriter} contextStack
 * tracks element commas during the recursive {@code writeValue(element)} loop.
 * The deprecated {@link Writer}-based override is retained as a thin delegate
 * so user subclasses written against the old API can chain via
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
public class CompactSetWriter implements JsonClassWriter {

    @Override
    @SuppressWarnings("unchecked")
    public void write(Object obj, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
        CompactSet set = (CompactSet) obj;
        Map<String, Object> config = set.getConfig();

        boolean caseSensitive = (Boolean) config.get(CompactMap.CASE_SENSITIVE);
        int compactSize = (Integer) config.get(CompactMap.COMPACT_SIZE);
        String ordering = (String) config.get(CompactMap.ORDERING);

        StringBuilder configStr = new StringBuilder();
        configStr.append(caseSensitive ? "CS" : "CI").append('/');
        configStr.append('S').append(compactSize).append('/');

        String orderCode;
        switch (ordering) {
            case CompactMap.SORTED:
                orderCode = "Sort";
                break;
            case CompactMap.REVERSE:
                orderCode = "Rev";
                break;
            case CompactMap.INSERTION:
                orderCode = "Ins";
                break;
            default:
                orderCode = "Unord";
        }
        configStr.append(orderCode);

        // "config" field via the JsonGenerator
        gen.writeFieldName("config");
        gen.writeString(configStr.toString());

        // "data" array via the tree-walker's semantic API — writeValue() handles
        // element commas via JsonWriter's contextStack during recursion.
        context.writeArrayFieldStart("data");
        for (Object element : set) {
            context.writeValue(element);
        }
        context.writeEndArray();
    }

    @Override
    @Deprecated
    public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
        JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_insideObjectBody(
                output, context.getWriteOptions());
        write(obj, showType, bridge, context);
    }

    @Override
    public String getTypeName(Object o) {
        return CompactSet.class.getName();
    }
}