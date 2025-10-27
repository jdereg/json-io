package com.cedarsoftware.io.factory;

import java.util.Map;

import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.Resolver;
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
        int capacity = Integer.parseInt(parts[0]);
        float loadFactor = Float.parseFloat(parts[1]);
        String modeCode = parts[2];
        boolean flattenDimensions = "T".equals(parts[3]);
        boolean simpleKeysMode = "T".equals(parts[4]);
        boolean valueBasedEquality = "T".equals(parts[5]);
        boolean caseSensitive = "T".equals(parts[6]);

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

        if (entriesObj == null) {
            throw new JsonIoException("MultiKeyMap requires an entries array. Found keys: " + jObj.keySet());
        }

        // Convert to Object[] if needed
        Object[] entries;
        if (entriesObj instanceof Object[]) {
            entries = (Object[]) entriesObj;
        } else if (entriesObj instanceof JsonObject) {
            // Resolve the JsonObject to an array
            entries = (Object[]) new JsonReader(resolver).toJava(Object[].class, entriesObj);
        } else {
            throw new JsonIoException("MultiKeyMap entries must be an array, got: " + entriesObj.getClass());
        }

        // Create a JsonReader for resolving JsonObjects
        JsonReader reader = new JsonReader(resolver);

        // Populate the MultiKeyMap with entries
        for (Object entryObj : entries) {
            // Each entry should be a Map/JsonObject with "keys" and "value"
            Map<String, Object> entryMap;
            if (entryObj instanceof Map) {
                entryMap = (Map<String, Object>) entryObj;
            } else {
                throw new JsonIoException("MultiKeyMap entry must be an object, got: " + entryObj.getClass());
            }

            // Check if the "keys" field exists (it can be null for null keys)
            if (!entryMap.containsKey("keys")) {
                throw new JsonIoException("MultiKeyMap entry missing 'keys' field");
            }

            Object keysObj = entryMap.get("keys");
            Object valueObj = entryMap.get("value");

            // Convert keys to Object[]
            Object[] keyComponents;
            if (keysObj == null) {
                // Null key - represented as array with single null element
                keyComponents = new Object[]{null};
            } else if (keysObj instanceof Object[]) {
                keyComponents = (Object[]) keysObj;
            } else if (keysObj instanceof JsonObject) {
                keyComponents = (Object[]) reader.toJava(Object[].class, keysObj);
            } else {
                throw new JsonIoException("MultiKeyMap entry keys must be an array, got: " + keysObj.getClass());
            }

            // Resolve value if it's a JsonObject
            Object value = valueObj;
            if (value instanceof JsonObject) {
                value = reader.toJava(((JsonObject) value).getType(), value);
            }

            // Resolve each key component if it's a JsonObject
            for (int j = 0; j < keyComponents.length; j++) {
                if (keyComponents[j] instanceof JsonObject) {
                    keyComponents[j] = reader.toJava(((JsonObject) keyComponents[j]).getType(), keyComponents[j]);
                }
            }

            // Add to the MultiKeyMap using putMultiKey
            if (keyComponents.length == 1) {
                mkmap.put(keyComponents[0], value);
            } else {
                mkmap.putMultiKey(value, keyComponents);
            }
        }

        // Remove config and entries from the original JsonObject
        jObj.remove("config");
        jObj.remove("entries");

        // Set the target
        jObj.setTarget(mkmap);

        return mkmap;
    }
}
