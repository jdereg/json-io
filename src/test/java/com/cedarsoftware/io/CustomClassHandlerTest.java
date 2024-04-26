package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import com.cedarsoftware.util.DateUtilities;
import com.cedarsoftware.util.convert.Converter;
import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
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
class CustomClassHandlerTest
{
    @Test
    void testCustomClassReaderWriter()
    {
        WeirdDate now = new WeirdDate(System.currentTimeMillis());
        String json = TestUtil.toJson(now, new WriteOptionsBuilder()
                .addCustomWrittenClass(WeirdDate.class, new WeirdDateWriter())
                .build());

        TestUtil.printLine("json=" + json);

        WeirdDate date = TestUtil.toObjects(json, new ReadOptionsBuilder()
                .addConverterOverride(Map.class, WeirdDate.class, WeirdDate::fromMapToWeirdDate)
                .addConverterOverride(String.class, WeirdDate.class, WeirdDate::fromStringToWeirdDate)
                .build(), null);
        assertEquals(now, date);

        json = TestUtil.toJson(now, new WriteOptionsBuilder()
                .addCustomWrittenClass(WeirdDate.class, new WeirdDateWriter())
                .setNotCustomWrittenClasses(listOf(WeirdDate.class))
                .build());
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

        public static WeirdDate fromMapToWeirdDate(Object from, Converter converter) {
            Map<?, ?> map = (Map<?, ?>) from;

            if (map.containsKey("stuff")) {
                Date date = converter.convert(map.get("stuff"), Date.class);
                return new WeirdDate(date);
            }

            throw new IllegalArgumentException("stuff wasn't defined");
        }

        public static WeirdDate fromStringToWeirdDate(Object from, Converter converter) {
            String s = (String) from;

            return new WeirdDate(DateUtilities.parseDate(s));
        }
    }

    public static class WeirdDateWriter extends Writers.PrimitiveTypeWriter
    {

        public void write(Object o, boolean showType, Writer out, WriterContext writerContext) throws IOException
        {
            if (showType) {
                out.write("\"stuff\":");
            }
            writePrimitiveForm(o, out, null);
        }

        public void writePrimitiveForm(Object o, Writer out, WriterContext writerContext) throws IOException
        {
            String value = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format((Date) o);
            out.write("\"");
            out.write(value);
            out.write("\"");
        }
    }
}
