package com.cedarsoftware.util.io;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
 *         limitations under the License.*
 */
@Deprecated
public class Readers
{
    private Readers () {}
    
    public static class URLReader implements JsonReader.JsonClassReader
    {
        @Override
        public Object read(Object o, Deque<JsonObject> stack, ReaderContext context)
        {
            boolean isString = o instanceof String;

            try
            {
                if (isString)
                {
                    URI uri = URI.create((String)o);
                    return uri.toURL();
                }

                return createURLFromJsonObject((JsonObject)o);
            }
            catch(MalformedURLException e)
            {
                throw new JsonIoException("java.net.URL malformed URL:  " + ((o instanceof String) ? o : e.getMessage()));
            }
        }

        URL createURLFromJsonObject(JsonObject jObj) throws MalformedURLException {
            if (jObj.hasValue()) {
                jObj.setTarget(createUrlNewWay(jObj));
            } else {
                jObj.setTarget(createUrlOldWay(jObj));
            }
            return (URL)jObj.getTarget();
        }

        URL createUrlNewWay(JsonObject jObj) throws MalformedURLException
        {
            return URI.create((String)jObj.getValue()).toURL();
        }

        URL createUrlOldWay(JsonObject jObj) throws MalformedURLException {
            String protocol = (String)jObj.get("protocol");
            String host = (String)jObj.get("host");
            String file = (String)jObj.get("file");
            String authority = (String)jObj.get("authority");
            String ref = (String)jObj.get("ref");
            Long port = (Long)jObj.get("port");

            StringBuilder builder = new StringBuilder(protocol + ":");
            if (!protocol.equalsIgnoreCase("jar")) {
                builder.append("//");
            }
            if (authority != null && !authority.isEmpty()) {
                builder.append(authority);
            } else {
                if (host != null && !host.isEmpty()) {
                    builder.append(host);
                }
                if (!port.equals(-1L)) {
                    builder.append(":" + port);
                }
            }
            if (file != null && !file.isEmpty()) {
                builder.append(file);
            }
            if (ref != null && !ref.isEmpty()) {
                builder.append("#" + ref);
            }
            return new URL(builder.toString());
        }
    }

    public static class RecordReader implements JsonReader.JsonClassReader
    {
        @Override
        public Object read(Object o, Deque<JsonObject> stack, ReaderContext context)
        {
            try
            {
                JsonObject jsonObj = (JsonObject) o;

                ArrayList<Class<?>> lParameterTypes = new ArrayList<>(jsonObj.size());
                ArrayList<Object> lParameterValues = new ArrayList<>(jsonObj.size());

                Class<?> c = jsonObj.getJavaType();
                // the record components are per definition in the constructor parameter order
                // we implement this with reflection due to code compatibility Java<16
                Method getRecordComponents = Class.class.getMethod("getRecordComponents");
                Object[] recordComponents = (Object[]) getRecordComponents.invoke(c);
                for (Object recordComponent : recordComponents)
                {
                    Class<?> type = (Class<?>) recordComponent.getClass().getMethod("getType").invoke(recordComponent);
                    lParameterTypes.add(type);

                    String parameterName = (String) recordComponent.getClass().getMethod("getName").invoke(recordComponent);
                    JsonObject parameterValueJsonObj = new JsonObject();

                    parameterValueJsonObj.setJavaType(type);
                    parameterValueJsonObj.setValue(jsonObj.get(parameterName));

                    if(parameterValueJsonObj.isLogicalPrimitive())
                        lParameterValues.add(parameterValueJsonObj.getPrimitiveValue());
                    else
                        lParameterValues.add(parameterValueJsonObj.getValue());
                }

                Constructor<?> constructor = c.getDeclaredConstructor(lParameterTypes.toArray(new Class[0]));
                MetaUtils.trySetAccessible(constructor);
                return constructor.newInstance(lParameterValues.toArray(new Object[0]));

            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Record de-serialization only works with java>=16.", e);
            } catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}
