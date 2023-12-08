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
 * All custom readers for json-io subclass this class.  Special readers are not needed for handling
 * user-defined classes.  However, special readers are built/supplied by json-io for many of the
 * primitive types and other JDK classes simply to allow for a more concise form.
 *
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
public class Readers
{
    private Readers () {}
    
    public static class URLReader implements JsonReader.JsonClassReader
    {
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
            if (jObj.containsKey("value")) {
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

    public static class LocaleReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject> stack, ReaderContext context)
        {
            JsonObject jObj = (JsonObject) o;
            Object language = jObj.get("language");
            if (language == null)
            {
                throw new JsonIoException("java.util.Locale must specify 'language' field");
            }
            Object country = jObj.get("country");
            Object variant = jObj.get("variant");
            if (country == null)
            {
                jObj.setTarget(new Locale((String) language));
                return jObj.getTarget();
            }
            if (variant == null)
            {
                jObj.setTarget(new Locale((String) language, (String) country));
                return jObj.getTarget();
            }

            jObj.setTarget(new Locale((String) language, (String) country, (String) variant));
            return jObj.getTarget();
        }
    }

    public static class CalendarReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject> stack, ReaderContext context)
        {
            String time = null;
            try
            {
                JsonObject jObj = (JsonObject) o;
                time = (String) jObj.get("time");
                if (time == null)
                {
                    throw new JsonIoException("Calendar missing 'time' field");
                }
                Date date = MetaUtils.dateFormat.get().parse(time);
                Class<?> c = jObj.getJavaType();

                // If a Calendar reader needs a ClassFactory.newInstance() call, then write a ClassFactory for
                // the special Calendar class, don't try to do that via a custom reader.  That is why only
                // MetaUtils.newInstance() is used below.
                Calendar calendar = (Calendar) MetaUtils.newInstance(c, null); // can add constructor arg values
                calendar.setTime(date);
                jObj.setFinishedTarget(calendar, true);
                String zone = (String) jObj.get("zone");
                if (zone != null)
                {
                    calendar.setTimeZone(TimeZone.getTimeZone(zone));
                }
                return calendar;
            }
            catch(Exception e)
            {
                throw new JsonIoException("Failed to parse calendar, time: " + time);
            }
        }
    }


    public static class StringReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject> stack, ReaderContext context)
        {
            if (o instanceof String)
            {
                return o;
            }

            if (Primitives.isPrimitive(o.getClass()))
            {
                return o.toString();
            }

            JsonObject jObj = (JsonObject) o;
            if (jObj.containsKey("value"))
            {
                jObj.setTarget(jObj.getValue());
                return jObj.getTarget();
            }
            throw new JsonIoException("String missing 'value' field");
        }
    }

    public static class ClassReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject> stack, ReaderContext context)
        {
            if (o instanceof String)
            {
                String cname = (String) o;
                Class c = MetaUtils.classForName(cname, context.getReadOptions().getClassLoader());
                if (c != null)
                {   // The user is attempting to load a java.lang.Class
                    return c;
                }
                throw new JsonIoException("Unable to load class: " + o);
            }

            JsonObject jObj = (JsonObject) o;
            if (jObj.containsKey("value"))
            {
                String value = (String) jObj.getValue();
                jObj.setTarget(MetaUtils.classForName(value, context.getReadOptions().getClassLoader()));
                if (jObj.getTarget() == null)
                {
                    throw new JsonIoException("Unable to load Class: " + value + ", class not found in JVM.");
                }
                return jObj.getTarget();
            }
            throw new JsonIoException("Class missing 'value' field");
        }
    }

    public static class AtomicBooleanReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject> stack, ReaderContext context)
        {
            Object value = o;
            value = getValueFromJsonObject(o, value, "AtomicBoolean");

            if (value instanceof String)
            {
                String state = (String) value;
                if ("".equals(state.trim()))
                {   // special case
                    return null;
                }
                return new AtomicBoolean("true".equalsIgnoreCase(state));
            }
            else if (value instanceof Boolean)
            {
                return new AtomicBoolean((Boolean) value);
            }
            else if (value instanceof Number && !(value instanceof Double) && !(value instanceof Float))
            {
                return new AtomicBoolean(((Number)value).longValue() != 0);
            }
            throw new JsonIoException("Unknown value in JSON assigned to AtomicBoolean, value type = " + value.getClass().getName());
        }
    }

    public static class AtomicIntegerReader implements JsonReader.JsonClassReader
    {
        @Override
        public Object read(Object o, Deque<JsonObject> stack, ReaderContext context)
        {
            Object value = o;
            value = getValueFromJsonObject(o, value, "AtomicInteger");

            if (value instanceof String)
            {
                String num = (String) value;
                if ("".equals(num.trim()))
                {   // special case
                    return null;
                }
                return new AtomicInteger(Integer.parseInt(MetaUtils.removeLeadingAndTrailingQuotes(num)));
            }
            else if (value instanceof Number && !(value instanceof Double) && !(value instanceof Float))
            {
                return new AtomicInteger(((Number)value).intValue());
            }
            throw new JsonIoException("Unknown value in JSON assigned to AtomicInteger, value type = " + value.getClass().getName());
        }
    }

    public static class AtomicLongReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject> stack, ReaderContext context)
        {
            Object value = o;
            value = getValueFromJsonObject(o, value, "AtomicLong");

            if (value instanceof String)
            {
                String num = (String) value;
                if ("".equals(num.trim()))
                {   // special case
                    return null;
                }
                return new AtomicLong(Long.parseLong(MetaUtils.removeLeadingAndTrailingQuotes(num)));
            }
            else if (value instanceof Number && !(value instanceof Double) && !(value instanceof Float))
            {
                return new AtomicLong(((Number)value).longValue());
            }
            throw new JsonIoException("Unknown value in JSON assigned to AtomicLong, value type = " + value.getClass().getName());
        }
    }

    private static Object getValueFromJsonObject(Object o, Object value, String typeName)
    {
        if (o instanceof JsonObject)
        {
            JsonObject jObj = (JsonObject) o;
            if (jObj.containsKey("value"))
            {
                value = jObj.getValue();
            }
            else
            {
                throw new JsonIoException(typeName + " defined as JSON {} object, missing 'value' field");
            }
        }
        return value;
    }

    public static class BigIntegerReader implements JsonReader.JsonClassReader
    {
        @Override
        public Object read(Object o, Deque<JsonObject> stack, ReaderContext context)
        {
            JsonObject jObj = null;
            Object value = o;
            if (o instanceof JsonObject)
            {
                jObj = (JsonObject) o;
                if (jObj.containsKey("value"))
                {
                    value = jObj.getValue();
                }
                else
                {
                    throw new JsonIoException("BigInteger missing 'value' field");
                }
            }

            if (value instanceof JsonObject)
            {
                JsonObject valueObj = (JsonObject)value;
                if (BigDecimal.class.equals(valueObj.getJavaType()))
                {
                    BigDecimalReader reader = new BigDecimalReader();
                    value = reader.read(value, stack, context);
                }
                else if (BigInteger.class.equals(valueObj.getJavaType()))
                {
                    value = read(value, stack, context);
                }
                else
                {
                    value = valueObj.getValue();
                }
            }

            BigInteger x = (BigInteger) MetaUtils.convert(BigInteger.class, value);
            if (jObj != null)
            {
                jObj.setTarget(x);
            }

            return x;
        }
    }

    public static class BigDecimalReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject> stack, ReaderContext context)
        {
            JsonObject jObj = null;
            Object value = o;
            if (o instanceof JsonObject)
            {
                jObj = (JsonObject) o;
                if (jObj.containsKey("value"))
                {
                    value = jObj.getValue();
                }
                else
                {
                    throw new JsonIoException("BigDecimal missing 'value' field");
                }
            }

            if (value instanceof JsonObject)
            {
                JsonObject valueObj = (JsonObject)value;
                if (BigInteger.class.equals(valueObj.getJavaType()))
                {
                    BigIntegerReader reader = new BigIntegerReader();
                    value = reader.read(value, stack, context);
                }
                else if (BigDecimal.class.equals(valueObj.getJavaType()))
                {
                    value = read(value, stack, context);
                }
                else
                {
                    value = valueObj.getValue();
                }
            }

            BigDecimal x = (BigDecimal) MetaUtils.convert(BigDecimal.class, value);
            if (jObj != null)
            {
                jObj.setTarget(x);
            }
            return x;
        }
    }
    
    public static class StringBuilderReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject> stack, ReaderContext context)
        {
            if (o instanceof String)
            {
                return new StringBuilder((String) o);
            }

            JsonObject jObj = (JsonObject) o;
            if (jObj.containsKey("value"))
            {
                jObj.setTarget(new StringBuilder((String) jObj.getValue()));
                return jObj.getTarget();
            }
            throw new JsonIoException("StringBuilder missing 'value' field");
        }
    }

    public static class StringBufferReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject> stack, ReaderContext context)
        {
            if (o instanceof String)
            {
                return new StringBuffer((String) o);
            }

            JsonObject jObj = (JsonObject) o;
            if (jObj.containsKey("value"))
            {
                jObj.setTarget(new StringBuffer((String) jObj.getValue()));
                return jObj.getTarget();
            }
            throw new JsonIoException("StringBuffer missing 'value' field");
        }
    }

    public static class UUIDReader implements JsonReader.JsonClassReader
    {
        @Override
        public Object read(Object o, Deque<JsonObject> stack, ReaderContext context)
        {
            // to use the String representation
            if (o instanceof String)
            {
                try
                {
                    return UUID.fromString((String) o);
                }
                catch (Exception e)
                {
                    throw new JsonIoException("Unable to load UUID from JSON string: " + o, e);
                }
            }

            JsonObject jObj = getJsonObject((JsonObject) o);
            return jObj.getTarget();
        }

        private static JsonObject getJsonObject(JsonObject jObj) {
            Long mostSigBits = (Long) jObj.get("mostSigBits");
            if (mostSigBits == null)
            {
                throw new JsonIoException("java.util.UUID must specify 'mostSigBits' field and it cannot be empty in JSON");
            }
            Long leastSigBits = (Long) jObj.get("leastSigBits");
            if (leastSigBits == null)
            {
                throw new JsonIoException("java.util.UUID must specify 'leastSigBits' field and it cannot be empty in JSON");
            }

            UUID uuid = new UUID(mostSigBits, leastSigBits);

            jObj.setTarget(uuid);
            return jObj;
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
