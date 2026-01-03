package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.io.JsonClassWriter;
import com.cedarsoftware.io.JsonWriter;
import com.cedarsoftware.io.WriterContext;
import com.cedarsoftware.util.CompactMap;

/**
 * Writer for CompactMap instances that produces a shortened configuration format.
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
    public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
        CompactMap map = (CompactMap) obj;

        // Get configuration using public API
        Map<String, Object> config = map.getConfig();
        boolean caseSensitive = (Boolean) config.get(CompactMap.CASE_SENSITIVE);
        int compactSize = (Integer) config.get(CompactMap.COMPACT_SIZE);
        String ordering = (String) config.get(CompactMap.ORDERING);
        String singleKey = (String) config.get(CompactMap.SINGLE_KEY);
        Class<?> mapImplClass = (Class<?>) config.get(CompactMap.MAP_TYPE);
        String mapImplClassName = mapImplClass.getName();

        // Generate shortened config string: mapClassFullName/CS|CI/S{size}/{singleKey}/{order}
        StringBuilder configStr = new StringBuilder();
        configStr.append(mapImplClassName).append('/');
        configStr.append(caseSensitive ? "CS" : "CI").append('/');
        configStr.append('S').append(compactSize).append('/');
        configStr.append(singleKey == null ? "-" : singleKey).append('/');

        // Map ordering codes to short form
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

        // Write shortened config
        output.write("\"config\":\"" + configStr + "\",");

        // Write data section
        output.write("\"data\":{");

        // Check if all keys are strings and not forcing two arrays
        boolean allStringKeys = true;
        if (!context.getWriteOptions().isForceMapOutputAsTwoArrays()) {
            Set<Map.Entry<Object, Object>> entries = ((Map<Object, Object>) map).entrySet();
            for (Map.Entry<Object, Object> entry : entries) {
                if (!(entry.getKey() instanceof String)) {
                    allStringKeys = false;
                    break;
                }
            }
        } else {
            allStringKeys = false;  // Force array format if setting is enabled
        }

        // Write entries in appropriate format
        if (allStringKeys) {
            // Standard JSON object format for string keys
            boolean first = true;
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) map).entrySet()) {
                if (first) {
                    first = false;
                } else {
                    output.write(',');
                }
                String key = (String) entry.getKey();
                JsonWriter.writeJsonUtf8String(output, key);
                output.write(':');
                context.writeImpl(entry.getValue(), showType);
            }
        } else {
            // Use json-io's standard @keys/@items format for non-string keys
            Set<Map.Entry<Object, Object>> entries = ((Map<Object, Object>) map).entrySet();
            int size = entries.size();
            Object[] keys = new Object[size];
            Object[] values = new Object[size];

            int i = 0;
            for (Map.Entry<Object, Object> entry : entries) {
                keys[i] = entry.getKey();
                values[i] = entry.getValue();
                i++;
            }

            // Write @keys array
            output.write("\"@keys\":");
            context.writeImpl(keys, showType);

            // Write @items array
            output.write(",\"@items\":");
            context.writeImpl(values, showType);
        }

        output.write("}");
    }

    @Override
    public String getTypeName(Object o) {
        return CompactMap.class.getName();
    }
}