package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.ReaderContext;

import java.lang.reflect.Constructor;

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

    public Object newInstance(Class<?> c, JsonObject jsonObj)
    {
        String msg = (String) jsonObj.get(DETAIL_MESSAGE);
        JsonObject causeMap = (JsonObject) jsonObj.get(CAUSE);


        JsonReader reader = ReaderContext.instance().getReader();

        Throwable cause = causeMap == null ? null : reader.convertParsedMapsToJava(causeMap, Throwable.class);
        Throwable t = createException(c, msg, cause);

        Object[] stackTraces = (Object[]) jsonObj.get(STACK_TRACE);
        if (stackTraces != null && stackTraces.length > 0) {
            StackTraceElement[] elements = new StackTraceElement[stackTraces.length];

            for (int i = 0; i < stackTraces.length; i++) {
                JsonObject stackTraceMap = (JsonObject) stackTraces[i];
                elements[i] = stackTraceMap == null ? null : reader.convertParsedMapsToJava(stackTraceMap, StackTraceElement.class);
            }
            t.setStackTrace(elements);
        }

        return t;
    }


    protected Throwable createException(Class<?> type, String message, Throwable t) {
        try {
            final Constructor<?>[] constructors = type.getDeclaredConstructors();

            Constructor<?> choice = null;

            Constructor<?> param2 = null, param1Throw = null, param1String = null, param0 = null;

            for (Constructor<?> c : constructors) {
                if (c.getParameterCount() == 2 && c.getParameterTypes()[0] == String.class && c.getParameterTypes()[1] == Throwable.class) {
                    param2 = c;
                } else if (c.getParameterCount() == 1 && c.getParameterTypes()[0] == Throwable.class) {
                    param1Throw = c;
                } else if (c.getParameterCount() == 1 && c.getParameterTypes()[0] == String.class) {
                    param1String = c;
                } else if (c.getParameterCount() == 0) {
                    param0 = c;
                }
            }

            if (param2 != null && message != null) {
                return (Throwable) param2.newInstance(message, t);
            } else if (param1Throw != null && t != null) {
                return (Throwable) param1Throw.newInstance(t);
            } else if (param1String != null && message != null) {
                return (Throwable) param1String.newInstance(message);
            } else if (param0 != null) {
                return (Throwable) param0.newInstance();
            }
        } catch (Exception e) {
            throw new JsonIoException("Error instantiating constructor: " + type.getName(), e);
        }

        throw new JsonIoException("Unable to find one of the four Throwable.class constructors on Class: " + type.getName() + ". Either implement your own writer or add one of the superclass constructors.");
    }

    public boolean isObjectFinal()
    {
        return true;
    }
}
