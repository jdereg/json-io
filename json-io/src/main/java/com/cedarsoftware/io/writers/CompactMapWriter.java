package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.io.JsonClassWriter;
import com.cedarsoftware.io.JsonGenerator;
import com.cedarsoftware.io.WriterContext;
import com.cedarsoftware.util.CompactMap;

/**
 * Writer for CompactMap instances that produces a shortened configuration format.
 *
 * <p>Migrated to the {@link JsonGenerator}-based {@link JsonClassWriter} API in
 * json-io 4.103.0. The {@code "config"} field is emitted via the generator; the
 * {@code "data"} sub-object (whether emitted as a JSON object of string keys or
 * as {@code @keys}/{@code @items} arrays) is emitted through the
 * {@link WriterContext} semantic API so the tree-walker's
 * {@link com.cedarsoftware.io.JsonWriter} contextStack handles all structural
 * commas during the recursive {@code writeImpl} calls. Replaces the previous
 * hand-rolled {@code output.write(...)} punctuation. The deprecated
 * {@link Writer}-based override is retained as a thin delegate so user
 * subclasses written against the old API can chain via {@code super.write(...)}
 * unchanged.
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
public class CompactMapWriter implements JsonClassWriter {

    @Override
    @SuppressWarnings("unchecked")
    public void write(Object obj, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
        CompactMap map = (CompactMap) obj;
        Map<String, Object> config = map.getConfig();
        boolean caseSensitive = (Boolean) config.get(CompactMap.CASE_SENSITIVE);
        int compactSize = (Integer) config.get(CompactMap.COMPACT_SIZE);
        String ordering = (String) config.get(CompactMap.ORDERING);
        String singleKey = (String) config.get(CompactMap.SINGLE_KEY);
        Class<?> mapImplClass = (Class<?>) config.get(CompactMap.MAP_TYPE);
        String mapImplClassName = mapImplClass.getName();

        StringBuilder configStr = new StringBuilder();
        configStr.append(mapImplClassName).append('/');
        configStr.append(caseSensitive ? "CS" : "CI").append('/');
        configStr.append('S').append(compactSize).append('/');
        configStr.append(singleKey == null ? "-" : singleKey).append('/');

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

        // "data" sub-object via the tree-walker's semantic API — JsonWriter's
        // contextStack will handle structural commas for the inner-object fields.
        Set<Map.Entry<Object, Object>> entries = ((Map<Object, Object>) map).entrySet();
        boolean allStringKeys = !context.getWriteOptions().isForceMapOutputAsTwoArrays();
        if (allStringKeys) {
            for (Map.Entry<Object, Object> entry : entries) {
                if (!(entry.getKey() instanceof String)) {
                    allStringKeys = false;
                    break;
                }
            }
        }

        context.writeObjectFieldStart("data");
        if (allStringKeys) {
            for (Map.Entry<Object, Object> entry : entries) {
                context.writeFieldName((String) entry.getKey());
                context.writeImpl(entry.getValue(), showType);
            }
        } else {
            int size = entries.size();
            Object[] keys = new Object[size];
            Object[] values = new Object[size];
            int i = 0;
            for (Map.Entry<Object, Object> entry : entries) {
                keys[i] = entry.getKey();
                values[i] = entry.getValue();
                i++;
            }
            context.writeFieldName("@keys");
            context.writeImpl(keys, showType);
            context.writeFieldName("@items");
            context.writeImpl(values, showType);
        }
        context.writeEndObject();
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
        return CompactMap.class.getName();
    }
}