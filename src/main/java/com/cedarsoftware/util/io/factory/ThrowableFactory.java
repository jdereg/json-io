package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.MetaUtils;
import com.cedarsoftware.util.io.ReaderContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory class to create Throwable instances.  Needed for JDK17+ as the only way to set the
 * 'detailMessage' field on a Throwable is via its constructor.
 * <p>
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
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

    public Object newInstance(Class<?> c, JsonObject jObj)
    {
        JsonReader reader = ReaderContext.instance().getReader();
        Map<Class<?>, List<Object>> values = new HashMap<>();
        String message = (String) jObj.get(DETAIL_MESSAGE);
        Throwable cause = reader.reentrantConvertParsedMapsToJava((JsonObject) jObj.get(CAUSE), Throwable.class);

        if (message != null) {
            values.computeIfAbsent(String.class, k -> new ArrayList<>()).add(message);
        }

        if (cause != null) {
            values.computeIfAbsent(Throwable.class, k -> new ArrayList<>()).add(cause);
        }

        gatherRemainingValues(jObj, values, MetaUtils.setOf(DETAIL_MESSAGE, CAUSE, STACK_TRACE));

        // Only need the values
        List<Object> argumentValues = new ArrayList<>();
        for (List<Object> hint : values.values()) {
            argumentValues.addAll(hint);
        }
        Throwable t = (Throwable) MetaUtils.newInstance(c, argumentValues);

        if (t.getCause() == null && cause != null) {
            t.initCause(cause);
        }

        Object[] stackTrace = (Object[]) jObj.get(STACK_TRACE);
        if (stackTrace != null) {
            StackTraceElement[] elements = new StackTraceElement[stackTrace.length];

            for (int i = 0; i < stackTrace.length; i++) {
                JsonObject stackTraceMap = (JsonObject) stackTrace[i];
                elements[i] = stackTraceMap == null ? null : reader.reentrantConvertParsedMapsToJava(stackTraceMap, StackTraceElement.class);
            }
            t.setStackTrace(elements);
        }
        return t;
    }

    @Override
    public boolean isObjectFinal()
    {
        return false;
    }
}
