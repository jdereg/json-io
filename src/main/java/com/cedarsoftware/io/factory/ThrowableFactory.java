package com.cedarsoftware.io.factory;

import java.util.LinkedHashMap;
import java.util.Map;

import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.Resolver;

/**
 * Factory class to create Throwable instances.
 *
 * <p>
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
public class ThrowableFactory implements JsonReader.ClassFactory {
    private static final String STACK_TRACE = "stackTrace";
    private static final String CAUSE = "cause";

    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        Map<Object, Object> map = new LinkedHashMap<>(jObj);

        // Pre-process the cause to ensure it's properly typed
        JsonObject jsonCause = (JsonObject) jObj.get(CAUSE);
        if (jsonCause != null) {
            Class<Throwable> causeType = (Class<Throwable>) jsonCause.getType();
            if (causeType != null) {
                // Add type information to the cause map
                Map<Object, Object> causeMap = new LinkedHashMap<>(jsonCause);
                causeMap.put("@type", causeType.getName());
                map.put(CAUSE, causeMap);
            }
        }

        // Let Converter.convert() handle all the complex logic
        Throwable t = (Throwable) resolver.getConverter().convert(map, c);

        // Handle stack trace separately since it needs special processing
        Object[] stackTrace = (Object[]) jObj.get(STACK_TRACE);
        if (stackTrace != null) {
            StackTraceElement[] elements = new StackTraceElement[stackTrace.length];
            for (int i = 0; i < stackTrace.length; i++) {
                JsonObject stackTraceMap = (JsonObject) stackTrace[i];
                elements[i] = stackTraceMap == null ? null :
                        resolver.toJavaObjects(stackTraceMap, StackTraceElement.class);
            }
            t.setStackTrace(elements);
        }

        return t;
    }
}