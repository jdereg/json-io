package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

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
public class SerializedExceptionTest
{
    static class MyException extends RuntimeException
    {
        private String name;

        MyException(String name, String detail)
        {
            super(detail);
            this.name = name;
        }
    }

    public static class MyExceptionWriter implements JsonWriter.JsonClassWriter
    {
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            MyException e = (MyException) obj;
            output.write("\"name\":\"");
            output.write(e.name);
            output.write("\",\"detailMessage\":\"");
            output.write(e.getMessage());
            output.write('"');
        }
    }

    public static class MyExceptionReader implements JsonReader.ClassFactory
    {
        public Object newInstance(Class<?> c, JsonObject<String, Object> o)
        {
            Map map = (Map) o;
            String name = (String) map.get("name");
            String detailMessage = (String) map.get("detailMessage");
            return new MyException(name, detailMessage);
        }

        public boolean isObjectFinal()
        {
            return true;
        }
    }

    @Test
    /**
     * JDK 17+ compatible test.  This test works under JDK 17 because the private field 'detailMessage' of
     * class Throwable serialized correctly, which will not work with 'field.set()' approach.
     */
    void testAccessToPrivateField()
    {
        JsonWriter.addWriterPermanent(MyException.class, new MyExceptionWriter());
        JsonReader.assignInstantiator(MyException.class, new MyExceptionReader());
        MyException exp = new MyException("foo", "bar");
        String json = JsonWriter.objectToJson(exp);
        MyException exp2 = JsonReader.jsonToJava(json);
        assert "foo".equals(exp2.name);
        assert "bar".equals(exp2.getMessage());
    }
}
