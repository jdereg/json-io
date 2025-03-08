package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.io.JsonWriter;
import com.cedarsoftware.io.WriterContext;
import com.cedarsoftware.util.CompactMap;
import com.cedarsoftware.util.ReflectionUtils;

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
public class CompactMapWriter implements JsonClassWriter {
    @Override
    public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
        CompactMap map = (CompactMap) obj;

        // Write config section
        output.write("\"config\":{");
        output.write("\"caseSensitive\":" + !((boolean) ReflectionUtils.call(map, "isCaseInsensitive")));
        output.write(",\"compactSize\":" + (int) ReflectionUtils.call(map, "compactSize"));
        output.write(",\"order\":\"" + (String) ReflectionUtils.call(map, "getOrdering") + "\"");
        String singleKey = (String) ReflectionUtils.call(map, "getSingleValueKey");
        if (singleKey != null && !singleKey.isEmpty()) {
            output.write(",\"singleKey\":\"" + singleKey + "\"");
        }
        output.write("},");

        // Start data section
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

        // Close data section
        output.write("}");
    }

    public String getTypeName(Object o) {
        return CompactMap.class.getName();
    }
}