package com.cedarsoftware.util.io;

import com.cedarsoftware.util.DeepEquals;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
public class RootsTest
{
    @Test
    public void testStringRoot()
    {
        Gson gson = new Gson();
        String g = gson.toJson("root should not be a string");
        String j = TestUtil.toJson("root should not be a string");
        assertEquals(g, j);
    }

    @Test
    public void testRoots()
    {
        // Test Object[] as root element passed in
        Object[] foo = new Object[]{new TestObject("alpha"), new TestObject("beta")};

        String jsonOut = TestUtil.toJson(foo);
        TestUtil.printLine(jsonOut);

        Object[] bar = TestUtil.toJava(jsonOut);
        assertEquals(2, bar.length);
        assertEquals(bar[0], new TestObject("alpha"));
        assertEquals(bar[1], new TestObject("beta"));

        String json = "[\"getStartupInfo\",[\"890.022905.16112006.00024.0067ur\",\"machine info\"]]";
        Object[] baz = TestUtil.toJava(json);
        assertEquals(2, baz.length);
        assertEquals("getStartupInfo", baz[0]);
        Object[] args = (Object[]) baz[1];
        assertEquals(2, args.length);
        assertEquals("890.022905.16112006.00024.0067ur", args[0]);
        assertEquals("machine info", args[1]);

        String hw = "[\"Hello, World\"]";
        Object[] qux = TestUtil.toJava(hw);
        assertNotNull(qux);
        assertEquals("Hello, World", qux[0]);

        // Whitespace
        String pkg = TestObject.class.getName();
        Object[] fred = TestUtil.toJava("[  {  \"@type\"  :  \"" + pkg + "\"  ,  \"_name\"  :  \"alpha\"  ,  \"_other\"  :  null  }  ,  {  \"@type\"  :  \"" + pkg + "\"  ,  \"_name\"  :  \"beta\"  ,  \"_other\" : null  }  ]  ");
        assertNotNull(fred);
        assertEquals(2, fred.length);
        assertEquals(fred[0], (new TestObject("alpha")));
        assertEquals(fred[1], (new TestObject("beta")));

        Object[] wilma = TestUtil.toJava("[{\"@type\":\"" + pkg + "\",\"_name\" : \"alpha\" , \"_other\":null,\"fake\":\"_typeArray\"},{\"@type\": \"" + pkg + "\",\"_name\":\"beta\",\"_other\":null}]");
        assertNotNull(wilma);
        assertEquals(2, wilma.length);
        assertEquals(wilma[0], (new TestObject("alpha")));
        assertEquals(wilma[1], (new TestObject("beta")));
    }

    @Test
    public void testRootTypes()
    {
        assert DeepEquals.deepEquals(25L, TestUtil.toJava("25"));
        assert DeepEquals.deepEquals(25.0d, TestUtil.toJava("25.0"));
        assertEquals(true, TestUtil.toJava("true"));
        assertEquals(false, TestUtil.toJava("false"));
        assertEquals("foo", TestUtil.toJava("\"foo\""));
    }

    @Test
    public void testRoots2()
    {
        // Test root JSON type as [ ]
        Object array = new Object[]{"Hello"};
        String json = TestUtil.toJson(array);
        Object oa = TestUtil.toJava(json);
        assertTrue(oa.getClass().isArray());
        assertEquals("Hello", ((Object[]) oa)[0]);

        // Test root JSON type as { }
        Calendar cal = Calendar.getInstance();
        cal.set(1965, 11, 17);
        json = TestUtil.toJson(cal);
        TestUtil.printLine("json = " + json);
        Object obj = TestUtil.toJava(json);
        assertFalse(obj.getClass().isArray());
        Calendar date = (Calendar) obj;
        assertEquals(1965, date.get(Calendar.YEAR));
        assertEquals(11, date.get(Calendar.MONTH));
        assertEquals(17, date.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void testNull()
    {
        String json = TestUtil.toJson(null);
        TestUtil.printLine("json=" + json);
        assert "null".equals(json);
    }

    @Test
    public void testEmptyObject()
    {
        Object o = TestUtil.toJava("{}");
        assert JsonObject.class.equals(o.getClass());

        Object[] oa = TestUtil.toJava("[{},{}]");
        assert oa.length == 2;
        assert JsonObject.class.equals(oa[0].getClass());
        assert JsonObject.class.equals(oa[1].getClass());
    }
}
