package com.cedarsoftware.io.factory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
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
        if (!isPresentValueForm(jObj)) {
            // The content written in the Optional's place -- {"x":6} for an Optional<P>, as Jackson writes it and as
            // json-io does where it writes no type (showTypeInfoNever(), standardJson()): one of the type it holds.
            // It read as empty.
            return Optional.of(contentOf(jObj, resolver));
        }
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

    /**
     * True for the form json-io writes under {@code writeOptionalAsObject()} (and for an Optional among the items of a
     * list it types): {"present":..}, with at most "value" beside it -- or "value" alone.
     */
    private static boolean isPresentValueForm(JsonObject jObj) {
        int size = jObj.size();
        boolean hasValue = jObj.containsKey("value");
        if (jObj.containsKey("present")) {
            return size == 1 || size == 2 && hasValue;
        }
        return size == 1 && hasValue;
    }

    /** An object written in the Optional's place: of the type the Optional holds, a map where that is not known. */
    private static Object contentOf(JsonObject jObj, Resolver resolver) {
        Type type = jObj.getType();
        Type held = type instanceof ParameterizedType ? ((ParameterizedType) type).getActualTypeArguments()[0]
                : Object.class;
        JsonObject content = new JsonObject();
        content.setType(held);
        for (Map.Entry<Object, Object> entry : jObj.entrySet()) {
            content.put(entry.getKey(), entry.getValue());
        }
        return resolver.toJava(held, content);
    }

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}