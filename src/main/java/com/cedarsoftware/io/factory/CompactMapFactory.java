package com.cedarsoftware.io.factory;

import java.util.Map;

import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.Resolver;
import com.cedarsoftware.util.CompactMap;

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
public class CompactMapFactory implements JsonReader.ClassFactory {
    @Override
    public Map newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        // Extract config and data sections
        Object configObj = jObj.get("config");
        Object dataObj = jObj.get("data");

        if (!(configObj instanceof Map)) {
            throw new JsonIoException("CompactMap requires a config section");
        }

        if (!(dataObj instanceof Map)) {
            throw new JsonIoException("CompactMap requires a data section");
        }

        // Cast to appropriate types
        Map<String, Object> config = (Map<String, Object>) configObj;
        JsonObject dataJson = (JsonObject) dataObj;

        // Get configuration parameters
        boolean caseSensitive = (Boolean) config.getOrDefault("caseSensitive", true);
        int compactSize = ((Number) config.getOrDefault("compactSize", 64)).intValue();
        String order = (String) config.getOrDefault("order", CompactMap.INSERTION);
        String singleKey = (String) config.getOrDefault("singleKey", null);

        // Build the CompactMap with the right configuration
        CompactMap.Builder<Object, Object> builder = CompactMap.builder()
                .caseSensitive(caseSensitive)
                .compactSize(compactSize);

        if (singleKey != null && !singleKey.isEmpty()) {
            builder.singleValueKey(singleKey);
        }

        if (CompactMap.SORTED.equals(order)) {
            builder.sortedOrder();
        } else if (CompactMap.REVERSE.equals(order)) {
            builder.reverseOrder();
        } else if (CompactMap.INSERTION.equals(order)) {
            builder.insertionOrder();
        } else {
            builder.noOrder();
        }

        CompactMap<Object, Object> cmap = builder.build();

        // Now process the data section, which can be in either format

        // Get the keys and items from the data JsonObject
        Object[] dataKeys = dataJson.getKeys();
        Object[] dataItems = dataJson.getItems();

        JsonReader reader = new JsonReader(resolver);

        if (dataKeys != null && dataItems != null) {
            // Handle data in @keys/@items format
            for (int i = 0; i < dataKeys.length; i++) {
                Object key = dataKeys[i];
                Object value = dataItems[i];

                // Skip metadata keys
                if (key instanceof String && ((String) key).startsWith("@")) {
                    continue;
                }

                // Resolve JsonObjects
                if (key instanceof JsonObject) {
                    key = reader.toJava(((JsonObject) key).getType(), key);
                }
                if (value instanceof JsonObject) {
                    value = reader.toJava(((JsonObject) value).getType(), value);
                }

                // Add to the CompactMap
                cmap.put(key, value);
            }
        } else {
            // Handle data in regular object format with string keys
            for (Map.Entry<Object, Object> entry : dataJson.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();

                // Skip special fields and metadata
                if (key instanceof String && ((String) key).startsWith("@")) {
                    continue;
                }

                // Resolve JsonObjects
                if (value instanceof JsonObject) {
                    value = reader.toJava(((JsonObject) value).getType(), value);
                }

                // Add to the CompactMap
                cmap.put(key, value);
            }
        }

        // Remove config and data from the original JsonObject
        // to prevent them from becoming entries
        jObj.remove("config");
        jObj.remove("data");

        // Set the target of the JsonObject to our CompactMap
        jObj.setTarget(cmap);

        return cmap;
    }
}