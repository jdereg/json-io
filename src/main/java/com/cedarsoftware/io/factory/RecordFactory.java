package com.cedarsoftware.io.factory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;

import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.Resolver;
import com.cedarsoftware.util.ExceptionUtilities;

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
    private RecordFactory() {}

    public static class RecordReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Resolver resolver)
        {
            try {
                JsonObject jsonObj = (JsonObject) o;

                ArrayList<Class<?>> lParameterTypes = new ArrayList<>(jsonObj.size());
                ArrayList<Object> lParameterValues = new ArrayList<>(jsonObj.size());

                Class<?> c = jsonObj.getRawType();
                // the record components are per definition in the constructor parameter order
                // we implement this with reflection due to code compatibility Java<16
                Method getRecordComponents = Class.class.getMethod("getRecordComponents");
                Object[] recordComponents = (Object[]) getRecordComponents.invoke(c);
                for (Object recordComponent : recordComponents) {
                    Class<?> type = (Class<?>) recordComponent.getClass().getMethod("getType").invoke(recordComponent);
                    lParameterTypes.add(type);

                    String parameterName = (String) recordComponent.getClass().getMethod("getName").invoke(recordComponent);
                    JsonObject paramValueJsonObj = new JsonObject();

                    paramValueJsonObj.setType(type);
                    paramValueJsonObj.setValue(jsonObj.get(parameterName));

                    if (resolver.valueToTarget(paramValueJsonObj)) {
                        lParameterValues.add(paramValueJsonObj.getTarget());
                    } else {
                        lParameterValues.add(paramValueJsonObj.getValue());
                    }
                }

                Constructor<?> constructor = c.getDeclaredConstructor(lParameterTypes.toArray(new Class[0]));
                ExceptionUtilities.safelyIgnoreException(() -> constructor.setAccessible(true));
                return constructor.newInstance(lParameterValues.toArray(new Object[0]));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Record de-serialization only works with java>=16.", e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public boolean isObjectFinal() {
        return true;
    }
}
