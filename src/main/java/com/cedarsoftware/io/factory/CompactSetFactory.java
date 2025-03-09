package com.cedarsoftware.io.factory;

import java.util.Collection;

import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.Resolver;
import com.cedarsoftware.util.CompactMap;
import com.cedarsoftware.util.CompactSet;

/**
 * Factory for creating CompactSet instances from JSON with shortened configuration format.
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
public class CompactSetFactory implements JsonReader.ClassFactory {
    @Override
    @SuppressWarnings("unchecked")
    public Collection newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        // Extract config and data sections
        Object configObj = jObj.get("config");
        Object dataObj = jObj.get("data");

        if (!(configObj instanceof String)) {
            throw new JsonIoException("CompactSet requires a config string");
        }
        String configStr = (String) configObj;

        if (!(dataObj instanceof Object[])) {
            throw new JsonIoException("CompactSet requires a data []");
        }
        Object[] data = (Object[]) dataObj;

        // Clean up
        jObj.remove("config");
        jObj.remove("data");

        jObj.setItems(data);
        
        // Parse config string: CS|CI/S{size}/{order}
        String[] parts = configStr.split("/");

        if (parts.length != 3) {
            throw new JsonIoException("Invalid CompactSet config format: " + configStr);
        }

        // Extract config values
        boolean caseSensitive = "CS".equals(parts[0]);
        int compactSize = Integer.parseInt(parts[1].substring(1)); // Skip 'S' prefix
        String orderCode = parts[2];

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

        // Build CompactSet with the right configuration
        CompactSet.Builder<Object> builder = CompactSet.builder()
                .caseSensitive(caseSensitive)
                .compactSize(compactSize);

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

        CompactSet<Object> cset = builder.build();

        // Set the target early to establish identity
        jObj.setTarget(cset);

        // Create a JsonReader with this resolver
        JsonReader reader = new JsonReader(resolver);
        reader.toJava(null, jObj);
        return cset;
    }
}