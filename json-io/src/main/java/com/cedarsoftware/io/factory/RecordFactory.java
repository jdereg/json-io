package com.cedarsoftware.io.factory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;

import com.cedarsoftware.io.ClassFactory;
import com.cedarsoftware.io.JsonClassReader;
import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.Resolver;
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
public class RecordFactory implements ClassFactory {
    private static final boolean JAVA_16_OR_ABOVE = SystemUtilities.isJavaVersionAtLeast(16, 0);

    public RecordFactory() {}

    @Override
    public Object newInstance(Class<?> c, JsonObject jsonObj, Resolver resolver) {
        if (!JAVA_16_OR_ABOVE) {
            throw new JsonIoException("Record de-serialization requires Java 16 or higher [current Java version: " + System.getProperty("java.version") + "]");
        }
        try {
            return newRecord(c, jsonObj, resolver);
        } catch (JsonIoException e) {
            throw e; // Re-throw JsonIoException as-is
        } catch (NoSuchMethodException e) {
            throw new JsonIoException("Failed to create Record instance: " + e.getMessage() + " [class: " + c.getName() + "]", e);
        } catch (ReflectiveOperationException e) {
            throw new JsonIoException("Failed to access Record components via reflection [class: " + c.getName() + "]", e);
        } catch (Exception e) {
            throw new JsonIoException("Unexpected error creating Record instance [class: " + c.getName() + "]", e);
        }
    }

    public static class RecordReader implements JsonClassReader
    {
        public Object read(Object o, Resolver resolver)
        {
            if (!JAVA_16_OR_ABOVE) {
                throw new JsonIoException("Record de-serialization requires Java 16 or higher [current Java version: " + System.getProperty("java.version") + "]");
            }
            try {
                JsonObject jsonObj = (JsonObject) o;
                return newRecord(jsonObj.getRawType(), jsonObj, resolver);
            } catch (JsonIoException e) {
                throw e; // Re-throw JsonIoException as-is
            } catch (ClassCastException e) {
                throw new JsonIoException("Failed to read Record: invalid JSON object structure [expected JsonObject, got: " + o.getClass().getSimpleName() + "]", e);
            } catch (NoSuchMethodException e) {
                throw new JsonIoException("Failed to create Record instance: " + e.getMessage(), e);
            } catch (ReflectiveOperationException e) {
                throw new JsonIoException("Failed to access Record components via reflection [class: " + ((JsonObject)o).getRawType().getName() + "]", e);
            } catch (Exception e) {
                String className = (o instanceof JsonObject) ? ((JsonObject)o).getRawType().getName() : "unknown";
                throw new JsonIoException("Unexpected error reading Record [class: " + className + "]", e);
            }
        }
    }

    public boolean isObjectFinal() {
        return true;
    }

    /**
     * A record of class {@code c} built from {@code jsonObj}: each component read as its declared (generic) type --
     * an object, a list, a map, an array, another record, by reference too -- as an {@code @IoCreator}'s arguments are
     * ({@link Resolver#toJavaArgument}), and given to the canonical constructor. An absent primitive component is its
     * default. Only scalars were converted before: any other component was given as read, a JsonObject or an
     * Object[], which the constructor refused (or kept, for a Map).
     */
    private static Object newRecord(Class<?> c, JsonObject jsonObj, Resolver resolver)
            throws ReflectiveOperationException {
        // the record components are per definition in the constructor parameter order
        // we implement this with reflection due to code compatibility Java<16
        Method getRecordComponents = ReflectionUtils.getMethod(Class.class, "getRecordComponents");
        if (getRecordComponents == null) {
            throw new NoSuchMethodException(
                    "getRecordComponents method not found - Java 16+ required for Record support");
        }
        Object[] recordComponents = (Object[]) getRecordComponents.invoke(c);
        Class<?>[] parameterTypes = new Class<?>[recordComponents.length];
        Object[] parameterValues = new Object[recordComponents.length];
        for (int i = 0; i < recordComponents.length; i++) {
            Object recordComponent = recordComponents[i];
            Class<?> type = (Class<?>) ReflectionUtils.call(recordComponent, "getType");
            Type genericType = (Type) ReflectionUtils.call(recordComponent, "getGenericType");
            String parameterName = (String) ReflectionUtils.call(recordComponent, "getName");
            parameterTypes[i] = type;
            Object value = jsonObj.get(parameterName);
            if (value == null) {
                parameterValues[i] = type.isPrimitive() ? resolver.getConverter().convert(null, type) : null;
            } else {
                parameterValues[i] = resolver.toJavaArgument(jsonObj, genericType == null ? type : genericType, value);
            }
        }
        Constructor<?> constructor = ReflectionUtils.getConstructor(c, parameterTypes);
        if (constructor == null) {
            throw new NoSuchMethodException("Record constructor not found for class: " + c.getName()
                    + " with parameter types: " + Arrays.toString(parameterTypes));
        }
        return constructor.newInstance(parameterValues);
    }
}
