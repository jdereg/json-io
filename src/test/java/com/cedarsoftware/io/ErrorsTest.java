package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.Converter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertThatThrownBy(() -> TestUtil.toObjects("[\"bad JSON input\"", null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");
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
        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF expected, content found after");
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
            assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                    .isInstanceOf(JsonIoException.class)
                    .hasMessageContaining("EOF reached");
    }

    @Test
    void testParseMissingLastBracket()
    {
        String json = "[true, \"bunch of ints\", 1,2, 3 , 4, 5 , 6,7,8,9,10]";
        TestUtil.toObjects(json, null);

        String json2 = "[true, \"bunch of ints\", 1,2, 3 , 4, 5 , 6,7,8,9,10";
        assertThatThrownBy(() -> TestUtil.toObjects(json2, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");
    }

    @Test
    void testParseBadValueInArray()
    {
        String json = "[true, \"bunch of ints\", 1,2, 3 , 4, 5 , 6,7,8,9,\'a\']";
        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unknown JSON value type");
    }

    @Test
    void testParseObjectWithoutClosingBrace()
    {
        String json = "{\"key\": true{";
        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Object not ended with '}'");
    }

    @Test
    void testParseBadHex()
    {
        String json = "\"\\u5h1t\"";
        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Expected hexadecimal digit");
    }

    @Test
    void testParseBadEscapeChar()
    {
        String json = "\"What if I try to escape incorrectly \\L1CK\"";
        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Invalid character escape sequence");
    }

    @Test
    void testParseUnfinishedString()
    {
        String json = "\"This is an unfinished string...";
        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached while reading JSON string");
    }

    @Test
    void testParseEOFInToken()
    {
        String json = "falsz";
        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Expected token: false");
    }

    @Test
    void testParseEOFReadingToken()
    {
        String json = "tru";
        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached while reading token: true");
    }

    @Test
    void testParseEOFinArray()
    {
        String json = "[true, false,";
        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");
    }

    @Test
    void testEmptyFieldName()
    {
        String json = "{\"\":17}";
        Map<?, ?> doh = TestUtil.toObjects(json, LinkedHashMap.class);
        assert doh.size() == 1;
        assertEquals(doh.get(""), 17L);
    }

    @Test
    void testNullFieldName()
    {
        String json = "{null:17}";
        assertThatThrownBy(() -> TestUtil.toObjects(json, LinkedHashMap.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Expected quote before field name");
    }

    @Test
    void testMalformedJson()
    {
        final String json = "{\"field\"0}";  // colon expected between fields
        assertThatThrownBy(() -> TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Expected ':' between field and value");

        final String json1 = "{field:0}";  // not quoted field name
        assertThatThrownBy(() -> TestUtil.toObjects(json1, new ReadOptionsBuilder().returnAsJsonObjects().build(), null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Expected quote");

        final String json2 = "{\"field\":0";  // object not terminated correctly (ending in number)
        assertThatThrownBy(() -> TestUtil.toObjects(json2, new ReadOptionsBuilder().returnAsJsonObjects().build(), null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");

        final String json3 = "{\"field\":true";  // object not terminated correctly (ending in token)
        assertThatThrownBy(() -> TestUtil.toObjects(json3, new ReadOptionsBuilder().returnAsJsonObjects().build(), null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");

        final String json4 = "{\"field\":\"test\"";  // object not terminated correctly (ending in string)
        assertThatThrownBy(() -> TestUtil.toObjects(json4, new ReadOptionsBuilder().returnAsJsonObjects().build(), null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");

        final String json5 = "{\"field\":{}";  // object not terminated correctly (ending in another object)
        assertThatThrownBy(() -> TestUtil.toObjects(json5, new ReadOptionsBuilder().returnAsJsonObjects().build(), null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");

        final String json6 = "{\"field\":[]";  // object not terminated correctly (ending in an array)
        assertThatThrownBy(() -> TestUtil.toObjects(json6, new ReadOptionsBuilder().returnAsJsonObjects().build(), null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");

        final String json7 = "{\"field\":3.14";  // object not terminated correctly (ending in double precision number)
        assertThatThrownBy(() -> TestUtil.toObjects(json7, new ReadOptionsBuilder().returnAsJsonObjects().build(), null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");

        final String json8 = "[1,2,3";
        assertThatThrownBy(() -> TestUtil.toObjects(json8, new ReadOptionsBuilder().returnAsJsonObjects().build(), null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");

        final String json9 = "[false,true,false";
        assertThatThrownBy(() -> TestUtil.toObjects(json9, new ReadOptionsBuilder().returnAsJsonObjects().build(), null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");

        final String json10 = "[\"unclosed string]";
        assertThatThrownBy(() -> TestUtil.toObjects(json10, new ReadOptionsBuilder().returnAsJsonObjects().build(), null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached while reading JSON string");
    }

    @Test
    void testBadType()
    {
        ReadOptions readOptions = new ReadOptionsBuilder().failOnUnknownType(false).build();
        String json = "{\"@type\":\"non.existent.class.Non\"}";
        Map map = TestUtil.toObjects(json, readOptions, null);
        assert map.size() == 0;

        // Bad class inside a Collection
        json = "{\"@type\":\"java.util.ArrayList\",\"@items\":[null, true, {\"@type\":\"bogus.class.Name\", \"fingers\":5}]}";
        List list = TestUtil.toObjects(json, readOptions, null);
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
        errorContains("{\"name\", 50}", "expected ':' between field and value");
        errorContains("{\"name\":, }", "unknown JSON value type");
        errorContains("[1, 3, 99d ]", "Expected ',' or ']' inside array");
        Object x = TestUtil.toObjects("\"\\/ should be one char\"", null);
        assert x.equals("/ should be one char");
        x = TestUtil.toObjects("\"' should be one char\"", null);
        assert x.equals("' should be one char");
        x = TestUtil.toObjects("\"a \\' char\"", null);
        assert x.equals("a ' char");
    }

    @Test
    public void testAliasedTypes()
    {
        String json = ClassUtilities.loadResourceAsString("errors/aliasedTypes.json");
        Object[] items = TestUtil.toObjects(json, null);

        assert items.length == 12 : "Expected 12 items, but got " + items.length;

        // BigInteger assertions
        assert new BigInteger("123456789012345678901234567890").equals(items[0]) : "Mismatch at index 0";
        assert new BigInteger("123456789012345678901234567891").equals(items[1]) : "Mismatch at index 1";
        assert new BigInteger("123456789012345678901234567892").equals(items[2]) : "Mismatch at index 2";

        // BigDecimal assertions
        assert new BigDecimal("123456789012345678901234567890.0").equals(items[3]) : "Mismatch at index 3";
        assert new BigDecimal("123456789012345678901234567891.1").equals(items[4]) : "Mismatch at index 4";
        assert new BigDecimal("123456789012345678901234567892.2").equals(items[5]) : "Mismatch at index 5";

        // String assertions
        assert "foo".equals(items[6]) : "Mismatch at index 6";
        assert "bar".equals(items[7]) : "Mismatch at index 7";
        assert "baz".equals(items[8]) : "Mismatch at index 8";

        // Date assertions
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/M/d");
        assert Converter.convert("1970/1/1", Date.class).equals(items[9]) : "Mismatch at index 9";
        assert Converter.convert("1980/1/1", Date.class).equals(items[10]) : "Mismatch at index 10";
        assert Converter.convert("1990/1/1", Date.class).equals(items[11]) : "Mismatch at index 11";
    }
}
