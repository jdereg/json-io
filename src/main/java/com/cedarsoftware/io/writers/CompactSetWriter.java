package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import com.cedarsoftware.io.WriterContext;
import com.cedarsoftware.util.CompactMap;
import com.cedarsoftware.util.CompactSet;

import static com.cedarsoftware.io.JsonWriter.JsonClassWriter;

/**
 * Writer for CompactSet instances that produces a shortened configuration format.
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
    public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
        CompactSet set = (CompactSet) obj;

        // Get configuration directly from the set
        Map<String, Object> config = set.getConfig();

        // Extract configuration values
        boolean caseSensitive = (Boolean) config.get(CompactMap.CASE_SENSITIVE);
        int compactSize = (Integer) config.get(CompactMap.COMPACT_SIZE);
        String ordering = (String) config.get(CompactMap.ORDERING);

        // Generate shortened config string: CS|CI/S{size}/{order}
        StringBuilder configStr = new StringBuilder();
        configStr.append(caseSensitive ? "CS" : "CI").append('/');
        configStr.append('S').append(compactSize).append('/');

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

        // Write data array - using same key as CompactMap for consistency
        output.write("\"data\":");

        // Write the elements as an array
        output.write('[');
        boolean first = true;
        for (Object element : set) {
            if (first) {
                first = false;
            } else {
                output.write(',');
            }
            context.writeImpl(element, showType);
        }
        output.write(']');
    }

    @Override
    public String getTypeName(Object o) {
        return CompactSet.class.getName();
    }
}