package com.cedarsoftware.io.factory;

import java.util.Map;

import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.Resolver;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.CompactMap;

/**
 * Factory for creating CompactMap instances from JSON with shortened configuration format.
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
public class CompactMapFactory implements JsonReader.ClassFactory {
    @Override
    @SuppressWarnings("unchecked")
    public Map newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        // Extract config and data sections
        Object configObj = jObj.get("config");
        Object dataObj = jObj.get("data");

        if (!(configObj instanceof String)) {
            throw new JsonIoException("CompactMap requires a config string");
        }

        if (!(dataObj instanceof Map)) {
            throw new JsonIoException("CompactMap requires a data section");
        }

        // Parse config string: mapClass/CS|CI/S{size}/{singleKey}/{order}
        String configStr = (String) configObj;
        String[] parts = configStr.split("/");

        if (parts.length != 5) {
            throw new JsonIoException("Invalid CompactMap config format: " + configStr);
        }

        // Extract config values
        String mapClassName = parts[0];
        boolean caseSensitive = "CS".equals(parts[1]);
        int compactSize = Integer.parseInt(parts[2].substring(1)); // Skip 'S' prefix
        String singleKey = "-".equals(parts[3]) ? null : parts[3];
        String orderCode = parts[4];

        // Map orderCode back to CompactMap constants
        String order;
        switch (orderCode) {
            case "Sort":
                order = CompactMap.SORTED;
                break;
            case "Rev":
                order = CompactMap.REVERSE;
                break;
            case "Ins":
                order = CompactMap.INSERTION;
                break;
            case "Unord":
            default:
                order = CompactMap.UNORDERED;
                break;
        }

        // Load the map class directly by name (it should be fully qualified)
        Class<? extends Map> mapImplClass = null;
        Class<?> cls = ClassUtilities.forName(mapClassName, ClassUtilities.getClassLoader());
        if (Map.class.isAssignableFrom(cls)) {
            mapImplClass = (Class<? extends Map>) cls;
        }

        // Build CompactMap with the right configuration
        CompactMap.Builder<Object, Object> builder = CompactMap.builder()
                .caseSensitive(caseSensitive)
                .compactSize(compactSize);

        if (singleKey != null && !singleKey.isEmpty()) {
            builder.singleValueKey(singleKey);
        }

        switch (order) {
            case CompactMap.SORTED:
                builder.sortedOrder();
                break;
            case CompactMap.REVERSE:
                builder.reverseOrder();
                break;
            case CompactMap.INSERTION:
                builder.insertionOrder();
                break;
            default:
                builder.noOrder();
                break;
        }

        // Set the map type if we found a valid class
        if (mapImplClass != null) {
            builder.mapType(mapImplClass);
        }

        CompactMap<Object, Object> cmap = builder.build();
        Map<Object, Object> data = (Map<Object, Object>) dataObj;
        JsonReader reader = new JsonReader(resolver);

        // Process all entries in the data section
        for (Map.Entry<Object, Object> entry : data.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            
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

        // Remove config and data from the original JsonObject
        jObj.remove("config");
        jObj.remove("data");

        // Set the target
        jObj.setTarget(cmap);

        return cmap;
    }
}