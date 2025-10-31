package com.cedarsoftware.io.factory;

import java.util.Map;

import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.Resolver;
import com.cedarsoftware.util.Converter;
import com.cedarsoftware.util.MultiKeyMap;

/**
 * Factory for creating MultiKeyMap instances from JSON with shortened configuration format.
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
public class MultiKeyMapFactory implements JsonReader.ClassFactory {
    @Override
    @SuppressWarnings("unchecked")
    public Map newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        // Extract config section
        Object configObj = jObj.get("config");

        if (!(configObj instanceof String)) {
            throw new JsonIoException("MultiKeyMap requires a config string. Found keys: " + jObj.keySet() +
                ", config=" + (configObj == null ? "null" : configObj.getClass().getName()));
        }

        // Parse config string: capacity/loadFactor/collectionKeyMode/flattenDimensions/simpleKeysMode/valueBasedEquality/caseSensitive
        String configStr = (String) configObj;
        String[] parts = configStr.split("/");

        if (parts.length != 7) {
            throw new JsonIoException("Invalid MultiKeyMap config format: " + configStr);
        }

        // Extract config values
        int capacity = Converter.convert2int(parts[0]);
        float loadFactor = Converter.convert2float(parts[1]);
        String modeCode = parts[2];
        boolean flattenDimensions = Converter.convert2boolean(parts[3]);
        boolean simpleKeysMode = Converter.convert2boolean(parts[4]);
        boolean valueBasedEquality = Converter.convert2boolean(parts[5]);
        boolean caseSensitive = Converter.convert2boolean(parts[6]);

        // Map modeCode back to CollectionKeyMode
        MultiKeyMap.CollectionKeyMode collectionKeyMode;
        switch (modeCode) {
            case "EXP":
                collectionKeyMode = MultiKeyMap.CollectionKeyMode.COLLECTIONS_EXPANDED;
                break;
            case "NOEXP":
                collectionKeyMode = MultiKeyMap.CollectionKeyMode.COLLECTIONS_NOT_EXPANDED;
                break;
            default:
                collectionKeyMode = MultiKeyMap.CollectionKeyMode.COLLECTIONS_EXPANDED;  // Default fallback
                break;
        }

        // Build MultiKeyMap with the right configuration
        MultiKeyMap.Builder<Object> builder = MultiKeyMap.builder()
                .capacity(capacity)
                .loadFactor(loadFactor)
                .collectionKeyMode(collectionKeyMode)
                .flattenDimensions(flattenDimensions)
                .simpleKeysMode(simpleKeysMode)
                .valueBasedEquality(valueBasedEquality)
                .caseSensitive(caseSensitive);

        MultiKeyMap<Object> mkmap = builder.build();

        // Extract entries array
        Object entriesObj = jObj.get("entries");

        if (entriesObj != null) {
            Object[] entries = extractEntriesArray(entriesObj);
            if (entries != null) {
                // Create JsonReader for resolving each entry's key and value
                JsonReader reader = new JsonReader(resolver);

                // Process entries one-by-one, fully resolving each before put()
                for (Object entryObj : entries) {
                    if (!(entryObj instanceof JsonObject)) {
                        continue;
                    }

                    JsonObject entryJsonObj = (JsonObject) entryObj;
                    Object key = entryJsonObj.get("keys");
                    Object value = entryJsonObj.get("value");

                    // Fully resolve the key
                    // NEW APPROACH: Skip Sealable* - create mutable collection, populate, then wrap as unmodifiable
                    // This eliminates hashCode instability and guarantees key immutability
                    if (key instanceof JsonObject) {
                        JsonObject keyJsonObj = (JsonObject) key;
                        // Let json-io resolve the key object using standard machinery
                        key = reader.toJava(keyJsonObj.getType(), keyJsonObj);
                    }

                    // Fully resolve the value
                    if (value instanceof JsonObject) {
                        value = reader.toJava(((JsonObject) value).getType(), value);
                    }

                    // Now key has stable hashCode - safe to put()
                    mkmap.put(key, value);
                }
            }
        }

        // Remove custom fields
        jObj.remove("config");
        jObj.remove("entries");

        // Set the target
        jObj.setTarget(mkmap);

        return mkmap;
    }

    /**
     * Extracts the entries array from the JsonObject.
     * Handles both direct array and JsonObject with @items.
     */
    private Object[] extractEntriesArray(Object entriesObj) {
        if (entriesObj instanceof Object[]) {
            return (Object[]) entriesObj;
        } else if (entriesObj instanceof JsonObject) {
            JsonObject entriesJsonObj = (JsonObject) entriesObj;
            Object itemsObj = entriesJsonObj.get("@items");
            if (itemsObj instanceof Object[]) {
                return (Object[]) itemsObj;
            }
        }
        return null;
    }
}
