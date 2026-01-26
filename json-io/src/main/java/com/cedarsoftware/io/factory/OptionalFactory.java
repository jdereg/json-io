package com.cedarsoftware.io.factory;

import java.util.Optional;

import com.cedarsoftware.io.ClassFactory;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.Resolver;

/**
 * Factory to create Optional instances during deserialization.
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
public class OptionalFactory implements ClassFactory {

    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        Object value = jObj.get("value");

        if (value == null) {
            // Check if explicitly marked as empty or value is actually null
            Boolean present = (Boolean) jObj.get("present");
            if (present != null && !present) {
                return Optional.empty();
            }
            // If no "present" field and value is null, return empty
            if (!jObj.containsKey("value")) {
                return Optional.empty();
            }
            return Optional.empty();
        }

        // Resolve nested JsonObjects
        if (value instanceof JsonObject) {
            JsonObject jsonObj = (JsonObject) value;
            value = resolver.toJava(jsonObj.getType(), jsonObj);
        }

        return Optional.of(value);
    }

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}