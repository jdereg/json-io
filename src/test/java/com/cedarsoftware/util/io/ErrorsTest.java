package com.cedarsoftware.util.io;

import java.util.List;
import java.util.Map;

import com.cedarsoftware.util.ReturnType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

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
class ErrorsTest
{
    @Test
    void testBadJson()
    {
        assertThrows(Exception.class, () -> { TestUtil.toObjects("[\"bad JSON input\"", null); });
    }

    @Test
    void testParseMissingQuote()
    {
            String json = "{\n" +
                    "  \"array\": [\n" +
                    "    1,\n" +
                    "    2,\n" +
                    "    3\n" +
                    "  ],\n" +
                    "  \"boolean\": true,\n" +
                    "  \"null\": null,\n" +
                    "  \"number\": 123,\n" +
                    "  \"object\": {\n" +
                    "    \"a\": \"b\",\n" +
                    "    \"c\": \"d\",\n" +
                    "    \"e\": \"f\"\n" +
                    "  },\n" +
                    "  \"string: \"Hello World\"\n" +
                    "}";
        try
        {
            TestUtil.toObjects(json, null);
        }
        catch (Exception e)
        {
            assert e.getMessage().contains("Expected ':' between string field and value");
        }
    }

    @Test
    void testParseInvalid1stChar()
    {
        String json = "\"array\": [\n" +
                "1,\n" +
                "2,\n" +
                "3\n" +
                "],\n" +
                "\"boolean\": true,\n" +
                "\"null\": null,\n" +
                "\"number\": 123,\n" +
                "\"object\": {\n" +
                "\"a\": \"b\",\n" +
                "\"c\": \"d\",\n" +
                "\"e\": \"f\"\n" +
                "},\n" +
                "\"string:\" \"Hello World\"\n" +
                "}";
        Object x = TestUtil.toObjects(json, null);
        assert "array".equals(x);
    }

    @Test
    void testParseMissingLastBrace()
    {
            String json = "{\n" +
                    "  \"array\": [\n" +
                    "    1,1,1,1,1,1,1,1,1,1,\n" +
                    "    2,0,0,0,0,0,0,0,0,0,0,0,0,\n" +
                    "    3,4,5,6,7,8,9,10\n" +
                    "  ],\n" +
                    "  \"boolean\": true,\n" +
                    "  \"null\": null,\n" +
                    "  \"number\": 123,\n" +
                    "  \"object\": {\n" +
                    "    \"a\": \"b\",\n" +
                    "    \"c\": \"d\",\n" +
                    "    \"e\": \"f\"\n" +
                    "  },\n" +
                    "  \"string\": \"Hello World\"\n";
            assertThrows(Exception.class, () -> { TestUtil.toObjects(json, null); });
    }

    @Test
    void testParseMissingLastBracket()
    {
        String json = "[true, \"bunch of ints\", 1,2, 3 , 4, 5 , 6,7,8,9,10]";
        TestUtil.toObjects(json, null);

        String json2 = "[true, \"bunch of ints\", 1,2, 3 , 4, 5 , 6,7,8,9,10";
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json2, null); });
    }

    @Test
    void testParseBadValueInArray()
    {
        String json = "[true, \"bunch of ints\", 1,2, 3 , 4, 5 , 6,7,8,9,\'a\']";
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json, null); });
    }

    @Test
    void testParseObjectWithoutClosingBrace()
    {
        String json = "{\"key\": true{";
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json, null); });
    }

    @Test
    void testParseBadHex()
    {
        String json = "\"\\u5h1t\"";
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json, null); });
    }

    @Test
    void testParseBadEscapeChar()
    {
        String json = "\"What if I try to escape incorrectly \\L1CK\"";
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json, null); });
    }

    @Test
    void testParseUnfinishedString()
    {
        String json = "\"This is an unfinished string...";
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json, null); });
    }

    @Test
    void testParseEOFInToken()
    {
        String json = "falsz";
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json, null); });
    }

    @Test
    void testParseEOFReadingToken()
    {
        String json = "tru";
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json, null); });
    }

    @Test
    void testParseEOFinArray()
    {
        String json = "[true, false,";
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json, null); });
    }

    @Test
    void testMalformedJson()
    {
        final String json = "{\"field\"0}";  // colon expected between fields
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json, new ReadOptions().returnType(ReturnType.JSON_VALUES), null); });

        final String json1 = "{field:0}";  // not quoted field name
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json1, new ReadOptions().returnType(ReturnType.JSON_VALUES), null); });

        final String json2 = "{\"field\":0";  // object not terminated correctly (ending in number)
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json2, new ReadOptions().returnType(ReturnType.JSON_VALUES), null); });

        final String json3 = "{\"field\":true";  // object not terminated correctly (ending in token)
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json3, new ReadOptions().returnType(ReturnType.JSON_VALUES), null); });

        final String json4 = "{\"field\":\"test\"";  // object not terminated correctly (ending in string)
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json4, new ReadOptions().returnType(ReturnType.JSON_VALUES), null); });

        final String json5 = "{\"field\":{}";  // object not terminated correctly (ending in another object)
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json5, new ReadOptions().returnType(ReturnType.JSON_VALUES), null); });

        final String json6 = "{\"field\":[]";  // object not terminated correctly (ending in an array)
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json6, new ReadOptions().returnType(ReturnType.JSON_VALUES), null); });

        final String json7 = "{\"field\":3.14";  // object not terminated correctly (ending in double precision number)
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json7, new ReadOptions().returnType(ReturnType.JSON_VALUES), null); });

        final String json8 = "[1,2,3";
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json8, new ReadOptions().returnType(ReturnType.JSON_VALUES), null); });

        final String json9 = "[false,true,false";
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json9, new ReadOptions().returnType(ReturnType.JSON_VALUES), null); });

        final String json10 = "[\"unclosed string]";
        assertThrows(Exception.class, () -> { TestUtil.toObjects(json10, new ReadOptions().returnType(ReturnType.JSON_VALUES), null); });
    }

    @Test
    void testBadType()
    {
        String json = "{\"@type\":\"non.existent.class.Non\"}";
        Map map = TestUtil.toObjects(json, null);
        assert map.size() == 0;

        // Bad class inside a Collection
        json = "{\"@type\":\"java.util.ArrayList\",\"@items\":[null, true, {\"@type\":\"bogus.class.Name\", \"fingers\":5}]}";
        List list = TestUtil.toObjects(json, null);
        assert list.size() == 3;
        assert list.get(0) == null;
        assert list.get(1) != null;
        Map map2 = (Map)list.get(2);
        assert map2.get("fingers").equals(5L);
    }

    private boolean errorContains(String json, String errMsg)
    {
        try
        {
            TestUtil.toObjects(json, null);
            fail();
            return false;
        }
        catch (Exception e)
        {
            if (e.getMessage().toLowerCase().contains(errMsg.toLowerCase()))
            {
                return true;
            }
            throw e;
        }
    }

    @Test
    public void testBadJsonFormats()
    {
        errorContains("{\"age\":25)", "not ended with '}");
        errorContains("[25, 50)", "expected ',' or ']' inside array");
        errorContains("{\"name\", 50}", "expected ':' between string field and value");
        errorContains("{\"name\":, }", "unknown JSON value type");
        errorContains("[1, 3, 99d ]", "Expected ',' or ']' inside array");
        Object x = TestUtil.toObjects("\"\\/ should be one char\"", null);
        assert x.equals("/ should be one char");
        x = TestUtil.toObjects("\"' should be one char\"", null);
        assert x.equals("' should be one char");
        x = TestUtil.toObjects("\"a \\' char\"", null);
        assert x.equals("a ' char");
    }
}