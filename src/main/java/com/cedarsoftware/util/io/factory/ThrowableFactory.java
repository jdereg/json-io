package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.*;

import java.lang.reflect.Constructor;
import java.util.HashMap;
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

    public Object newInstance(Class<?> c, JsonObject jsonObj)
    {
        String msg = (String) jsonObj.get(DETAIL_MESSAGE);
        JsonObject causeMap = (JsonObject) jsonObj.get(CAUSE);


        JsonReader reader = ReaderContext.instance().getReader();

        Throwable cause = causeMap == null ? null : reader.convertParsedMapsToJava(causeMap, Throwable.class);
        Throwable t = createException(c, msg, cause);
        if (t.getCause() == null && cause != null) {
            t.initCause(cause);
        }

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
            final Object[] parameters = new Object[constructors.length];

            Map<Class<?>, Object> paramValues = new HashMap<>();
            paramValues.put(String.class, message);
            paramValues.put(Throwable.class, t);

            double bestScore = Double.MIN_VALUE;
            int choice = 0;
            int choiceParameterCount = 0;

            for (int i = 0; i < constructors.length; i++) {
                Class<?>[] types = constructors[i].getParameterTypes();
                Object[] values = new Object[types.length];
                parameters[i] = values;

                double score = MetaUtils.fillArgsWithHints(types, values, true, paramValues);

                if (score >= bestScore && types.length >= choiceParameterCount) {
                    bestScore = score;
                    choice = i;
                    choiceParameterCount = types.length;
                }
            }

            Constructor constructor = constructors[choice];
            Object[] objects = (Object[]) parameters[choice];

            try {
                return (Throwable) constructor.newInstance(objects);
            } catch (IllegalAccessException iae) {
                MetaUtils.trySetAccessible(constructor);
                return (Throwable) constructor.newInstance(objects);
            }
        } catch (Exception e) {
            throw new JsonIoException("Error calling constructor for: " + type.getName(), e);
        }
    }

    @Override
    public boolean isObjectFinal()
    {
        return false;
    }
}
