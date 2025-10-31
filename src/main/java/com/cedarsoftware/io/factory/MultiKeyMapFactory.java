package com.cedarsoftware.io.factory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                        key = createImmutableKey(reader, keyJsonObj);
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
     * Creates an immutable key for MultiKeyMap by directly populating mutable collections
     * and wrapping them as unmodifiable.
     * <p>
     * This approach eliminates Sealable* classes entirely, avoiding their hashCode instability
     * issues and guaranteeing that keys are immutable when stored in MultiKeyMap.
     * <p>
     * Benefits over the old Sealable* approach:
     * - Eliminates hashCode instability (no SealableSet/SealableList)
     * - Guarantees key immutability (prevents users from mutating keys via keySet())
     * - Faster (no conversion overhead)
     * - Simpler code
     * <p>
     * Uses LinkedHashSet for Sets to preserve iteration order:
     * - LinkedHashSet → preserves insertion order
     * - TreeSet → preserves sorted order (as it appears in JSON)
     * - HashSet → preserves iteration order (for consistency/debugging)
     *
     * @param reader the JSON reader for resolving nested objects
     * @param keyJsonObj the JsonObject containing the key data
     * @return an immutable key object (UnmodifiableSet, UnmodifiableList, or resolved single object)
     */
    private Object createImmutableKey(JsonReader reader, JsonObject keyJsonObj) {
        // Use getType() which returns Type, convert to string for checking
        String typeName = keyJsonObj.getType() != null ? keyJsonObj.getType().getTypeName() : "";

        // Check if this is a Collection (Set or List)
        boolean isSet = typeName.contains("Set") || typeName.contains("set");
        boolean isList = typeName.contains("List") || typeName.contains("list");

        // Let json-io resolve the key object using standard machinery
        Object resolved = reader.toJava(keyJsonObj.getType(), keyJsonObj);

        // Now wrap it as unmodifiable if it's a collection
        // This prevents users from mutating keys obtained via keySet()
        if (isSet && resolved instanceof Set) {
            return Collections.unmodifiableSet((Set<?>) resolved);
        } else if (isList && resolved instanceof List) {
            return Collections.unmodifiableList((List<?>) resolved);
        }

        // Not a collection - return as-is
        return resolved;
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
