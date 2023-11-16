package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Writer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class CustomClassHandlerTest
{
    @Test
    public void testCustomClassReaderWriter()
    {
        WeirdDate now = new WeirdDate(System.currentTimeMillis());
        Map<Class<WeirdDate>, WeirdDateWriter> map1 = new LinkedHashMap<>(1);
        map1.put(WeirdDate.class, new WeirdDateWriter());
        String json = TestUtil.toJson(now, new WriteOptionsBuilder().withCustomWriterMap(map1).build());
        TestUtil.printLine("json=" + json);

        Map<Class<WeirdDate>, WeirdDateReader> map3 = new LinkedHashMap<>(1);
        map3.put(WeirdDate.class, new WeirdDateReader());
        WeirdDate date = TestUtil.toObjects(json, new ReadOptionsBuilder().withCustomReaders(map3).build(), null);
        assertEquals(now, date);

        Map<Class<WeirdDate>, WeirdDateWriter> map5 = new LinkedHashMap<>(1);
        map5.put(WeirdDate.class, new WeirdDateWriter());
        json = TestUtil.toJson(now, new WriteOptionsBuilder().withCustomWriterMap(map5).withNoCustomizationsFor(MetaUtils.listOf(WeirdDate.class)).build());
        TestUtil.printLine("json=" + json);
        assertEquals(now, date);
    }

    public static class WeirdDate extends Date
    {
        public WeirdDate(Date date)
        {
            super(date.getTime());
        }

        public WeirdDate(long millis)
        {
            super(millis);
        }
    }

    public static class WeirdDateWriter implements JsonWriter.JsonClassWriter
    {
        public void write(Object o, boolean showType, Writer out) throws IOException
        {
            String value = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format((Date) o);
            out.write("\"stuff\":\"");
            out.write(value);
            out.write("\"");
        }

        public boolean hasPrimitiveForm()
        {
            return true;
        }

        public void writePrimitiveForm(Object o, Writer out) throws IOException
        {
            String value = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format((Date) o);
            out.write("\"");
            out.write(value);
            out.write("\"");
        }
    }

    public static class WeirdDateReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject> stack)
        {
            if (o instanceof String)
            {
                try
                {
                    return new WeirdDate(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse((String) o));
                }
                catch (ParseException e)
                {
                    throw new JsonIoException("Date format incorrect");
                }
            }


            JsonObject jObj = (JsonObject) o;
            if (jObj.containsKey("stuff"))
            {
                try
                {
                    return jObj.target = new WeirdDate(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse((String) jObj.get("stuff")));
                }
                catch (ParseException e)
                {
                    throw new JsonIoException("Date format incorrect");
                }
            }

            throw new JsonIoException("Date missing 'stuff' field");
        }
    }
}
