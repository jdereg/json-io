package com.cedarsoftware.io.factory;

import java.util.List;
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

        // Extract entries array - keep as JsonObject, don't resolve yet
        Object entriesObj = jObj.get("entries");

        if (entriesObj == null) {
            throw new JsonIoException("MultiKeyMap requires an entries array. Found keys: " + jObj.keySet());
        }

        // Entries should be an array (or JsonObject representing an array)
        if (!(entriesObj instanceof Object[] || entriesObj instanceof JsonObject)) {
            throw new JsonIoException("MultiKeyMap entries must be an array, got: " + entriesObj.getClass());
        }

        // Create a JsonReader for resolving fields individually
        JsonReader reader = new JsonReader(resolver);

        // Get entries as an array - but DON'T fully resolve nested structures yet
        Object[] entries;
        if (entriesObj instanceof Object[]) {
            entries = (Object[]) entriesObj;
        } else {
            // It's a JsonObject - convert to array but keep entries as JsonObjects
            JsonObject entriesJsonObj = (JsonObject) entriesObj;
            Object itemsObj = entriesJsonObj.get("@items");
            if (itemsObj instanceof Object[]) {
                entries = (Object[]) itemsObj;
            } else {
                throw new JsonIoException("MultiKeyMap entries JsonObject missing @items");
            }
        }

        // Populate the MultiKeyMap with entries
        for (Object entryObj : entries) {
            // Each entry should be a JsonObject with "keys" and "value"
            if (!(entryObj instanceof JsonObject)) {
                throw new JsonIoException("MultiKeyMap entry must be a JsonObject, got: " + entryObj.getClass());
            }

            JsonObject entryJsonObj = (JsonObject) entryObj;

            // Check if the "keys" field exists (it can be null for null keys)
            if (!entryJsonObj.containsKey("keys")) {
                throw new JsonIoException("MultiKeyMap entry missing 'keys' field");
            }

            Object keysObj = entryJsonObj.get("keys");
            Object valueObj = entryJsonObj.get("value");

            // Resolve value using reader (handles both JsonObjects and plain values)
            Object value = valueObj;
            if (value instanceof JsonObject) {
                value = reader.toJava(((JsonObject) value).getType(), value);
            }

            // Handle keys - resolve using reader.toJava() which handles all nested structures properly
            Object key;

            if (keysObj == null) {
                // Null key
                key = null;
            } else if (keysObj instanceof JsonObject) {
                // JsonObject that needs resolution - could be List, Set, array, or single object
                JsonObject keyJsonObj = (JsonObject) keysObj;
                java.lang.reflect.Type keyType = keyJsonObj.getType();

                if (keyType instanceof Class && ((Class<?>) keyType).isArray()) {
                    // OLD FORMAT: Array with marker strings (~~OPEN~~, ~~SET_OPEN~~, etc.)
                    // Resolve the array
                    Object[] keyComponents = (Object[]) reader.toJava(Object[].class, keysObj);

                    // Internalize marker strings back to marker objects
                    Object[] internalizedKeys = MultiKeyMap.internalizeMarkers(keyComponents);

                    // Reconstruct the original key structure
                    key = MultiKeyMap.reconstructKey(internalizedKeys);
                } else {
                    // NEW FORMAT: List, Set, or single object
                    // Let reader.toJava() handle full resolution including nested structures
                    key = reader.toJava(keyType, keysObj);
                }
            } else {
                // Simple value (String, Integer, etc.) - use as-is
                key = keysObj;
            }

            // Add to the MultiKeyMap
            // Keys from entrySet() are always Set/List/single (never Object[])
            // Unwrap unmodifiable collections to ensure MultiKeyMap can process them
            Object finalKey = key;
            if (key instanceof List) {
                // Unwrap List and any nested collections within it
                List<?> keyList = (List<?>) key;
                List<Object> unwrapped = new java.util.ArrayList<>(keyList.size());
                for (Object element : keyList) {
                    if (element instanceof java.util.Set) {
                        unwrapped.add(new java.util.LinkedHashSet<>((java.util.Set<?>) element));
                    } else if (element instanceof List) {
                        unwrapped.add(new java.util.ArrayList<>((List<?>) element));
                    } else {
                        unwrapped.add(element);
                    }
                }
                finalKey = unwrapped;
            } else if (key instanceof java.util.Set) {
                finalKey = new java.util.LinkedHashSet<>((java.util.Set<?>) key);
            }
            // With COLLECTIONS_EXPANDED: put(List) will automatically expand the List into components
            // With COLLECTIONS_NOT_EXPANDED: put(List) treats List as a single key
            mkmap.put(finalKey, value);
        }

        // Remove config and entries from the original JsonObject
        jObj.remove("config");
        jObj.remove("entries");

        // Set the target
        jObj.setTarget(mkmap);

        return mkmap;
    }
}
