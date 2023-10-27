package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

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
class ErrorsTest
{
    @Test
    void testBadJson()
    {
        assertThrows(Exception.class, () -> { TestUtil.toJava("[\"bad JSON input\""); });
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
            assertThrows(Exception.class, () -> { TestUtil.toJava(json); });
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
        var x = TestUtil.toJava(json);
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
            assertThrows(Exception.class, () -> { TestUtil.toJava(json); });
    }

    @Test
    void testParseMissingLastBracket()
    {
        String json = "[true, \"bunch of ints\", 1,2, 3 , 4, 5 , 6,7,8,9,10]";
        TestUtil.toJava(json);

        String json2 = "[true, \"bunch of ints\", 1,2, 3 , 4, 5 , 6,7,8,9,10";
        assertThrows(Exception.class, () -> { TestUtil.toJava(json2); });
    }

    @Test
    void testParseBadValueInArray()
    {
        String json = "[true, \"bunch of ints\", 1,2, 3 , 4, 5 , 6,7,8,9,\'a\']";
        assertThrows(Exception.class, () -> { TestUtil.toJava(json); });
    }

    @Test
    void testParseObjectWithoutClosingBrace()
    {
        String json = "{\"key\": true{";
        assertThrows(Exception.class, () -> { TestUtil.toJava(json); });
    }

    @Test
    void testParseBadHex()
    {
        String json = "\"\\u5h1t\"";
        assertThrows(Exception.class, () -> { TestUtil.toJava(json); });
    }

    @Test
    void testParseBadEscapeChar()
    {
        String json = "\"What if I try to escape incorrectly \\L1CK\"";
        assertThrows(Exception.class, () -> { TestUtil.toJava(json); });
    }

    @Test
    void testParseUnfinishedString()
    {
        String json = "\"This is an unfinished string...";
        assertThrows(Exception.class, () -> { TestUtil.toJava(json); });
    }

    @Test
    void testParseEOFInToken()
    {
        String json = "falsz";
        assertThrows(Exception.class, () -> { TestUtil.toJava(json); });
    }

    @Test
    void testParseEOFReadingToken()
    {
        String json = "tru";
        assertThrows(Exception.class, () -> { TestUtil.toJava(json); });
    }

    @Test
    void testParseEOFinArray()
    {
        String json = "[true, false,";
        assertThrows(Exception.class, () -> { TestUtil.toJava(json); });
    }

    @Test
    void testMalformedJson()
    {
        final String json = "{\"field\"0}";  // colon expected between fields
        assertThrows(Exception.class, () -> { TestUtil.toJava(json, new ReadOptionsBuilder().returnAsMaps().build()); });

        final String json1 = "{field:0}";  // not quoted field name
        assertThrows(Exception.class, () -> { TestUtil.toJava(json1, new ReadOptionsBuilder().returnAsMaps().build()); });

        final String json2 = "{\"field\":0";  // object not terminated correctly (ending in number)
        assertThrows(Exception.class, () -> { TestUtil.toJava(json2, new ReadOptionsBuilder().returnAsMaps().build()); });

        final String json3 = "{\"field\":true";  // object not terminated correctly (ending in token)
        assertThrows(Exception.class, () -> { TestUtil.toJava(json3, new ReadOptionsBuilder().returnAsMaps().build()); });

        final String json4 = "{\"field\":\"test\"";  // object not terminated correctly (ending in string)
        assertThrows(Exception.class, () -> { TestUtil.toJava(json4, new ReadOptionsBuilder().returnAsMaps().build()); });

        final String json5 = "{\"field\":{}";  // object not terminated correctly (ending in another object)
        assertThrows(Exception.class, () -> { TestUtil.toJava(json5, new ReadOptionsBuilder().returnAsMaps().build()); });

        final String json6 = "{\"field\":[]";  // object not terminated correctly (ending in an array)
        assertThrows(Exception.class, () -> { TestUtil.toJava(json6, new ReadOptionsBuilder().returnAsMaps().build()); });

        final String json7 = "{\"field\":3.14";  // object not terminated correctly (ending in double precision number)
        assertThrows(Exception.class, () -> { TestUtil.toJava(json7, new ReadOptionsBuilder().returnAsMaps().build()); });

        final String json8 = "[1,2,3";
        assertThrows(Exception.class, () -> { TestUtil.toJava(json8, new ReadOptionsBuilder().returnAsMaps().build()); });

        final String json9 = "[false,true,false";
        assertThrows(Exception.class, () -> { TestUtil.toJava(json9, new ReadOptionsBuilder().returnAsMaps().build()); });

        final String json10 = "[\"unclosed string]";
        assertThrows(Exception.class, () -> { TestUtil.toJava(json10, new ReadOptionsBuilder().returnAsMaps().build()); });
    }

    @Test
    void testBadType()
    {
        String json = "{\"@type\":\"non.existent.class.Non\"}";
        Map map = TestUtil.toJava(json);
        assert map.size() == 0;

        // Bad class inside a Collection
        json = "{\"@type\":\"java.util.ArrayList\",\"@items\":[null, true, {\"@type\":\"bogus.class.Name\", \"fingers\":5}]}";
        List list = TestUtil.toJava(json);
        assert list.size() == 3;
        assert list.get(0) == null;
        assert list.get(1) != null;
        Map map2 = (Map)list.get(2);
        assert map2.get("fingers").equals(5L);
    }
}