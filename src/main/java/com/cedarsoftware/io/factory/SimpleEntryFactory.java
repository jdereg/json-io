package com.cedarsoftware.io.factory;

import java.util.AbstractMap;

import com.cedarsoftware.io.ClassFactory;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.Resolver;

/**
 * Factory class to create AbstractMap.SimpleEntry and AbstractMap.SimpleImmutableEntry instances.
 * <p>
 * These classes have final fields that cannot be set via reflection on Java 9+,
 * so we must use the constructor to create properly initialized instances.
 * <p>
 * Handles both:
 * <ul>
 *     <li>{@link AbstractMap.SimpleEntry} - mutable entry (value can be changed)</li>
 *     <li>{@link AbstractMap.SimpleImmutableEntry} - immutable entry</li>
 * </ul>
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
public class SimpleEntryFactory implements ClassFactory {

    /**
     * Creates a new SimpleEntry or SimpleImmutableEntry instance using values from the JsonObject.
     *
     * @param c The class to instantiate (SimpleEntry or SimpleImmutableEntry)
     * @param jObj The JsonObject containing "key" and "value" fields
     * @param resolver The resolver for converting nested objects
     * @return A new entry instance with the key and value set
     */
    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        // Extract key and value from the JsonObject
        Object key = jObj.get("key");
        Object value = jObj.get("value");

        // Recursively resolve any nested JsonObjects
        key = resolveValue(key, resolver);
        value = resolveValue(value, resolver);

        // Create the appropriate entry type
        if (c == AbstractMap.SimpleImmutableEntry.class) {
            return new AbstractMap.SimpleImmutableEntry<>(key, value);
        }
        // Default to SimpleEntry
        return new AbstractMap.SimpleEntry<>(key, value);
    }

    /**
     * Resolves a value that may be a JsonObject to its Java equivalent.
     */
    private Object resolveValue(Object value, Resolver resolver) {
        if (value instanceof JsonObject) {
            JsonObject jsonObj = (JsonObject) value;
            return resolver.toJava(jsonObj.getType(), jsonObj);
        }
        return value;
    }

    /**
     * Returns true because the entry is fully constructed by this factory.
     * No further field processing is needed.
     */
    @Override
    public boolean isObjectFinal() {
        return true;
    }
}