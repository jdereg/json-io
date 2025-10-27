package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import com.cedarsoftware.io.JsonWriter;
import com.cedarsoftware.io.WriterContext;
import com.cedarsoftware.util.MultiKeyMap;

import static com.cedarsoftware.io.JsonWriter.JsonClassWriter;

/**
 * Writer for MultiKeyMap instances that produces a shortened configuration format.
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
public class MultiKeyMapWriter implements JsonClassWriter {
    @Override
    public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
        MultiKeyMap<?> map = (MultiKeyMap<?>) obj;

        // Get configuration values using public getters
        int capacity = map.getCapacity();
        float loadFactor = map.getLoadFactor();
        MultiKeyMap.CollectionKeyMode collectionKeyMode = map.getCollectionKeyMode();
        boolean flattenDimensions = map.getFlattenDimensions();
        boolean simpleKeysMode = map.getSimpleKeysMode();
        boolean valueBasedEquality = map.getValueBasedEquality();
        boolean caseSensitive = map.getCaseSensitive();

        // Generate shortened config string: capacity/loadFactor/collectionKeyMode/flattenDimensions/simpleKeysMode/valueBasedEquality/caseSensitive
        StringBuilder config = new StringBuilder();
        config.append(capacity).append('/');
        config.append(loadFactor).append('/');

        // Map CollectionKeyMode to short form
        String modeCode;
        switch (collectionKeyMode) {
            case COLLECTIONS_EXPANDED:
                modeCode = "EXP";
                break;
            case COLLECTIONS_NOT_EXPANDED:
                modeCode = "NOEXP";
                break;
            default:
                modeCode = "EXP";  // Default fallback
        }
        config.append(modeCode).append('/');
        config.append(flattenDimensions ? "T" : "F").append('/');
        config.append(simpleKeysMode ? "T" : "F").append('/');
        config.append(valueBasedEquality ? "T" : "F").append('/');
        config.append(caseSensitive ? "T" : "F");

        // Write shortened config field (no leading comma for first custom field)
        context.writeFieldName("config");
        output.write('\"');
        output.write(config.toString());
        output.write('\"');

        // Extract all entries and write as array of {keys, value} objects
        Iterable<MultiKeyMap.MultiKeyEntry<?>> entriesIterable =
            (Iterable<MultiKeyMap.MultiKeyEntry<?>>) com.cedarsoftware.util.ReflectionUtils.call(map, "entries");

        // Write entries array field (with leading comma for subsequent field)
        output.write(',');
        context.writeFieldName("entries");
        context.writeStartArray();

        boolean first = true;
        for (MultiKeyMap.MultiKeyEntry<?> entry : entriesIterable) {
            if (!first) {
                output.write(',');
            }
            first = false;

            // Write each entry as {"keys":[...],"value":...}
            // Externalize markers (OPEN/CLOSE/SET_OPEN/SET_CLOSE/NULL_SENTINEL) to serializable strings
            context.writeStartObject();
            context.writeFieldName("keys");
            Object[] externalizedKeys = MultiKeyMap.externalizeMarkers(entry.keys);
            context.writeImpl(externalizedKeys, showType);
            context.writeObjectField("value", entry.value);
            context.writeEndObject();
        }

        context.writeEndArray();
    }

    @Override
    public String getTypeName(Object o) {
        return "com.cedarsoftware.util.MultiKeyMap";
    }
}
