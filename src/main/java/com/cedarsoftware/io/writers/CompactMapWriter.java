package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.io.JsonObject;
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
        /*
        "com.cedarsoftware.util.CompactMap$LinkedHashMap_CS_S70_Id_Ins”
        
        {"@type": "com.cedarsoftware.util.CompactMap",
          "~|caseSensitive|~": true,
          "~|compactSize|~": 70,
          "~|singleKey|~": "Id",
          "~|ordering|~": "Insertion",
          "~|entries|~": [
            { "key": "Id",    "value": 42 },
            { "key": "Name",  "value": "Alice" },
            { "key": "Active","value": true }
          ]
        }
        */

        CompactMap map = (CompactMap) obj;
        boolean caseSensitive = !((boolean) ReflectionUtils.call(map, "isCaseInsensitive"));
        int compactSize = (int) ReflectionUtils.call(map, "compactSize");
        String ordering = (String) ReflectionUtils.call(map, "getOrdering");
        String singleKey = (String) ReflectionUtils.call(map, "getSingleValueKey");
        output.write("\"" + JsonObject.FIELD_PREFIX + "caseSensitive" + JsonObject.FIELD_SUFFIX +"\":" + caseSensitive);
        output.write(",\"" + JsonObject.FIELD_PREFIX + "compactSize" + JsonObject.FIELD_SUFFIX + "\":" + compactSize);
        output.write(",\"" + JsonObject.FIELD_PREFIX + "order" + JsonObject.FIELD_SUFFIX + "\":\"" + ordering);
        output.write("\",\"" + JsonObject.FIELD_PREFIX + "singleKey" + JsonObject.FIELD_SUFFIX + "\":\"" + singleKey);
        output.write("\",\"" + JsonObject.FIELD_PREFIX + "entries" + JsonObject.FIELD_SUFFIX + "\":[");
        boolean first = true;
        Set<Map.Entry<Object, Object>> entries = ((Map<Object, Object>) map).entrySet();
        for (Map.Entry<Object, Object> entry : entries) {
            if (first) {
                first = false;
            } else {
                output.write(',');
            }
            output.write("{\"key\":");
            context.writeImpl(entry.getKey(), showType);
            output.write(",\"value\":");
            context.writeImpl(entry.getValue(), showType);
            output.write('}');
        }
        output.write("]");
    }

    public String getTypeName(Object o) {
        return CompactMap.class.getName();
    }
}