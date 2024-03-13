package com.cedarsoftware.io.factory;

import java.util.ArrayList;
import java.util.List;

import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.MetaUtils;
import com.cedarsoftware.io.ReaderContext;

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

    public Object newInstance(Class<?> c, JsonObject jObj, ReaderContext context)
    {
        List<Object> arguments = new ArrayList<>();
        String message = (String) jObj.get(DETAIL_MESSAGE);
        JsonObject jsonCause = (JsonObject) jObj.get(CAUSE);
        Class<Throwable> causeType = jsonCause == null ? Throwable.class : (Class<Throwable>)jsonCause.getJavaType();
        causeType = causeType == null ? Throwable.class : causeType;
        Throwable cause = context.reentrantConvertJsonValueToJava(jsonCause, causeType);

        if (message != null) {
            arguments.add(message);
        }

        if (cause != null) {
            arguments.add(cause);
        }

        gatherRemainingValues(context, jObj, arguments, MetaUtils.setOf(DETAIL_MESSAGE, CAUSE, STACK_TRACE));

        // Only need the values
        Throwable t = (Throwable) MetaUtils.newInstance(context.getConverter(), c, arguments);

        if (t.getCause() == null && cause != null) {
            t.initCause(cause);
        }

        Object[] stackTrace = (Object[]) jObj.get(STACK_TRACE);
        if (stackTrace != null) {
            StackTraceElement[] elements = new StackTraceElement[stackTrace.length];

            for (int i = 0; i < stackTrace.length; i++) {
                JsonObject stackTraceMap = (JsonObject) stackTrace[i];
                elements[i] = stackTraceMap == null ? null : context.reentrantConvertJsonValueToJava(stackTraceMap, StackTraceElement.class);
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
