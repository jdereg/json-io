package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;

import com.cedarsoftware.io.JsonClassWriter;
import com.cedarsoftware.io.WriterContext;
import com.cedarsoftware.util.MultiKeyMap;

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
        context.writeValue(config.toString());

        // Write entries array field - combines comma + "entries":[ in one call!
        context.writeArrayFieldStart("entries");

        // Extract all entries using public API and write as array of {keys, value} objects
        // Keys are now returned as native List (ordered), Set (unordered), or single items
        for (java.util.Map.Entry<Object, ?> entry : map.entrySet()) {
            // Write each entry as {"keys":...,"value":...}
            // Keys are already in native JSON-friendly format (List/Set/single)
            // - Single keys: written as-is
            // - Multi-keys (ordered): written as ArrayList with @type
            // - Multi-keys (unordered): written as LinkedHashSet with @type
            // The writeStartObject() automatically handles comma insertion based on context
            context.writeStartObject();
            context.writeFieldName("keys");
            context.writeImpl(entry.getKey(), showType);
            context.writeObjectField("value", entry.getValue());
            context.writeEndObject();
        }

        context.writeEndArray();
    }

    @Override
    public String getTypeName(Object o) {
        return "com.cedarsoftware.util.MultiKeyMap";
    }
}
