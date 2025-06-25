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
public class ThrowableFactory implements JsonReader.ClassFactory
{
    private static final String DETAIL_MESSAGE = "detailMessage";
    private static final String CAUSE = "cause";
    private static final String STACK_TRACE = "stackTrace";

    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver)
    {
        Map<String, Object> map = new LinkedHashMap<>();

        // Alias for constructor parameter name. Ensure it is first in the map so
        // that constructors without parameter names still receive it correctly.
        Object message = jObj.get(DETAIL_MESSAGE);
        if (message == null) {
            message = "";
        }
        map.put("message", message);
        map.put("msg", message);

        Throwable cause = null;

        // Copy remaining properties preserving order, converting the cause as needed
        for (Map.Entry<String, Object> entry : jObj.entrySet()) {
            String key = entry.getKey();
            if (DETAIL_MESSAGE.equals(key)) {
                continue;
            }

            if (CAUSE.equals(key)) {
                JsonObject jsonCause = (JsonObject) entry.getValue();
                Class<Throwable> causeType = jsonCause == null ? Throwable.class : (Class<Throwable>) jsonCause.getType();
                causeType = causeType == null ? Throwable.class : causeType;
                cause = resolver.toJavaObjects(jsonCause, causeType);
                map.put(CAUSE, cause); // Keep 'cause' key present so MapConversions can match constructors
            } else {
                map.put(key, entry.getValue());
            }
        }

        // Instantiate using Converter to leverage MapConversions
        Throwable t = resolver.getConverter().convert(map, c);

        if (t.getCause() == null && cause != null) {
            t.initCause(cause);
        }

        Object[] stackTrace = (Object[]) jObj.get(STACK_TRACE);
        if (stackTrace != null) {
            StackTraceElement[] elements = new StackTraceElement[stackTrace.length];

            for (int i = 0; i < stackTrace.length; i++) {
                JsonObject stackTraceMap = (JsonObject) stackTrace[i];
                elements[i] = stackTraceMap == null ? null : resolver.toJavaObjects(stackTraceMap, StackTraceElement.class);
            }
            t.setStackTrace(elements);
        }
        return t;
    }
}
