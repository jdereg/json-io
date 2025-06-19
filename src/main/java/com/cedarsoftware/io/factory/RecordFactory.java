package com.cedarsoftware.io.factory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;

import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.Resolver;
import com.cedarsoftware.util.ExceptionUtilities;
import com.cedarsoftware.util.ReflectionUtils;
import com.cedarsoftware.util.SystemUtilities;

/**
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
public class RecordFactory implements JsonReader.ClassFactory {
    private static final boolean JAVA_16_OR_ABOVE = SystemUtilities.isJavaVersionAtLeast(16, 0);

    public RecordFactory() {}

    @Override
    public Object newInstance(Class<?> c, JsonObject jsonObj, Resolver resolver) {
        if (!JAVA_16_OR_ABOVE) {
            throw new RuntimeException("Record de-serialization only works with java>=16.");
        }
        try {
            ArrayList<Class<?>> lParameterTypes = new ArrayList<>(jsonObj.size());
            ArrayList<Object> lParameterValues = new ArrayList<>(jsonObj.size());

            Method getRecordComponents = ReflectionUtils.getMethod(Class.class, "getRecordComponents");
            if (getRecordComponents == null) {
                throw new NoSuchMethodException("getRecordComponents");
            }
            Object[] recordComponents = (Object[]) getRecordComponents.invoke(c);
            for (Object recordComponent : recordComponents) {
                Class<?> type = (Class<?>) ReflectionUtils.call(recordComponent, "getType");
                lParameterTypes.add(type);

                String parameterName = (String) ReflectionUtils.call(recordComponent, "getName");
                JsonObject paramValueJsonObj = new JsonObject();

                paramValueJsonObj.setType(type);
                paramValueJsonObj.setValue(jsonObj.get(parameterName));

                if (resolver.valueToTarget(paramValueJsonObj)) {
                    lParameterValues.add(paramValueJsonObj.getTarget());
                } else {
                    lParameterValues.add(paramValueJsonObj.getValue());
                }
            }

            Constructor<?> constructor = ReflectionUtils.getConstructor(c, lParameterTypes.toArray(new Class[0]));
            if (constructor == null) {
                throw new NoSuchMethodException("record constructor not found");
            }
            ExceptionUtilities.safelyIgnoreException(() -> constructor.setAccessible(true));
            return constructor.newInstance(lParameterValues.toArray(new Object[0]));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class RecordReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Resolver resolver)
        {
            if (!JAVA_16_OR_ABOVE) {
                throw new RuntimeException("Record de-serialization only works with java>=16.");
            }
            try {
                JsonObject jsonObj = (JsonObject) o;

                ArrayList<Class<?>> lParameterTypes = new ArrayList<>(jsonObj.size());
                ArrayList<Object> lParameterValues = new ArrayList<>(jsonObj.size());

                Class<?> c = jsonObj.getRawType();
                // the record components are per definition in the constructor parameter order
                // we implement this with reflection due to code compatibility Java<16
                Method getRecordComponents = ReflectionUtils.getMethod(Class.class, "getRecordComponents");
                if (getRecordComponents == null) {
                    throw new NoSuchMethodException("getRecordComponents");
                }
                Object[] recordComponents = (Object[]) getRecordComponents.invoke(c);
                for (Object recordComponent : recordComponents) {
                    Class<?> type = (Class<?>) ReflectionUtils.call(recordComponent, "getType");
                    lParameterTypes.add(type);

                    String parameterName = (String) ReflectionUtils.call(recordComponent, "getName");
                    JsonObject paramValueJsonObj = new JsonObject();

                    paramValueJsonObj.setType(type);
                    paramValueJsonObj.setValue(jsonObj.get(parameterName));

                    if (resolver.valueToTarget(paramValueJsonObj)) {
                        lParameterValues.add(paramValueJsonObj.getTarget());
                    } else {
                        lParameterValues.add(paramValueJsonObj.getValue());
                    }
                }

                Constructor<?> constructor = ReflectionUtils.getConstructor(c, lParameterTypes.toArray(new Class[0]));
                if (constructor == null) {
                    throw new NoSuchMethodException("record constructor not found");
                }
                ExceptionUtilities.safelyIgnoreException(() -> constructor.setAccessible(true));
                return constructor.newInstance(lParameterValues.toArray(new Object[0]));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public boolean isObjectFinal() {
        return true;
    }
}
