package com.cedarsoftware.util.io

import org.junit.Test

import java.text.ParseException
import java.text.SimpleDateFormat

import static org.junit.Assert.assertTrue

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
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
class TestCustomClassHandler
{
    public static class WeirdDate extends Date
    {
        public WeirdDate(Date date) { super(date.getTime()) }

        public WeirdDate(long millis) { super(millis) }
    }

    public class WeirdDateWriter implements JsonWriter.JsonClassWriter
    {
        public void write(Object o, boolean showType, Writer out)
        {
            String value = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format((Date) o)
            out.write("\"stuff\":\"")
            out.write(value)
            out.write('"')
        }

        public boolean hasPrimitiveForm()
        {
            return true;
        }

        public void writePrimitiveForm(Object o, Writer out)
        {
            String value = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format((Date) o)
            out.write('"')
            out.write(value)
            out.write('"')
        }
    }

    public class WeirdDateReader implements JsonReader.JsonClassReader
    {
        public Object read(Object o, Deque<JsonObject<String, Object>> stack)
        {
            if (o instanceof String)
            {
                try
                {
                    return new WeirdDate(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse((String) o))
                }
                catch (ParseException e)
                {
                    throw new JsonIoException("Date format incorrect")
                }
            }

            JsonObject jObj = (JsonObject) o
            if (jObj.containsKey("stuff"))
            {
                try
                {
                    return jObj.target = new WeirdDate(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse((String) jObj.get("stuff")))
                }
                catch (ParseException e)
                {
                    throw new JsonIoException("Date format incorrect")
                }
            }
            throw new JsonIoException("Date missing 'stuff' field")

        }
    }

    @Test
    void testCustomClassReaderWriter() throws Exception
    {
        JsonWriter.addWriter(WeirdDate.class, new WeirdDateWriter())
        JsonReader.addReader(WeirdDate.class, new WeirdDateReader())

        WeirdDate now = new WeirdDate(System.currentTimeMillis())
        String json = TestUtil.getJsonString(now)
        TestUtil.printLine("json=" + json)
        WeirdDate date = (WeirdDate) TestUtil.readJsonObject(json)
        assertTrue(now.equals(date))

        JsonWriter.addNotCustomWriter(WeirdDate.class)
        JsonReader.addNotCustomReader(WeirdDate.class)
        json = TestUtil.getJsonString(now)
        TestUtil.printLine("json=" + json)
        assertTrue(now.equals(date))

        JsonWriter.removeWriter(WeirdDate.class)
        JsonReader.removeReader(WeirdDate.class)
    }
}
