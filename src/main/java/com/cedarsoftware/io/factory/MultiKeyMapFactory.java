package com.cedarsoftware.io.factory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.Resolver;
import com.cedarsoftware.io.util.SealableList;
import com.cedarsoftware.io.util.SealableSet;
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
                    if (key instanceof JsonObject) {
                        JsonObject keyJsonObj = (JsonObject) key;
                        key = reader.toJava(keyJsonObj.getType(), key);
                        // Convert SealableList to stable collection for hash stability
                        // Pass the original type info so we can preserve Set vs List distinction
                        key = convertToStableCollection(key, keyJsonObj);
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
     * Converts Sealable collections to stable collections with consistent hashCode.
     * <p>
     * SealableList and SealableSet have unstable hashCodes that change as items are added,
     * which breaks MultiKeyMap's hash-based lookup. We convert them to stable collections
     * (HashSet or UnmodifiableList) based on the original JSON type.
     * <p>
     * Already-stable collections (HashSet, ArrayList) are returned as-is for performance,
     * or wrapped in Unmodifiable for safety.
     * <p>
     * Performance notes:
     * - SealableList/SealableSet: Must convert (O(n)) due to hashCode instability
     * - HashSet: Return as-is (O(1)) - already stable
     * - ArrayList: Return as-is (O(1)) - already stable, MultiKeyMap will handle correctly
     *
     * @param obj the object to convert (may be SealableList, SealableSet, List, Set, or other)
     * @param jsonObj the JsonObject containing type information (@type field)
     * @return a stable collection suitable for use as MultiKeyMap key
     */
    private Object convertToStableCollection(Object obj, JsonObject jsonObj) {
        // Handle SealableList - MUST convert due to unstable hashCode
        if (obj instanceof SealableList) {
            SealableList<?> sealableList = (SealableList<?>) obj;

            // Check if the original type was a Set by looking at the @type field
            Object typeObj = jsonObj.get("@type");
            String typeName = typeObj != null ? typeObj.toString() : "";
            boolean isSet = typeName.contains("Set") || typeName.contains("set");

            if (isSet) {
                // Convert to HashSet - stable hashCode
                Set<Object> copy = new HashSet<>(sealableList.size());
                copy.addAll(sealableList);
                return copy;
            } else {
                // Convert to UnmodifiableList - stable hashCode
                return Collections.unmodifiableList(new ArrayList<>(sealableList));
            }
        }

        // Handle SealableSet - MUST convert due to unstable hashCode
        if (obj instanceof SealableSet) {
            SealableSet<?> sealableSet = (SealableSet<?>) obj;
            // Convert to HashSet - stable hashCode
            return new HashSet<>(sealableSet);
        }

        // Handle already-stable List (ArrayList, etc.)
        // These are stable, so return as-is for optimal performance
        if (obj instanceof List) {
            // Already stable - no conversion needed
            return obj;
        }

        // Handle already-stable Set (HashSet, etc.)
        if (obj instanceof Set) {
            // Already stable - no conversion needed
            return obj;
        }

        // Not a collection - return as-is
        return obj;
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
