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
        assertThatThrownBy(() -> TestUtil.toJava("[\"bad JSON input\"", null).asClass(null))
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
        assertThatThrownBy(() -> TestUtil.toJava(json, null).asClass(null))
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
            assertThatThrownBy(() -> TestUtil.toJava(json, null).asClass(null))
                    .isInstanceOf(JsonIoException.class)
                    .hasMessageContaining("EOF reached");
    }

    @Test
    void testParseMissingLastBracket()
    {
        String json = "[true, \"bunch of ints\", 1,2, 3 , 4, 5 , 6,7,8,9,10]";
        TestUtil.toJava(json, null).asClass(null);

        String json2 = "[true, \"bunch of ints\", 1,2, 3 , 4, 5 , 6,7,8,9,10";
        assertThatThrownBy(() -> TestUtil.toJava(json2, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");
    }

    @Test
    void testParseBadValueInArray()
    {
        String json = "[true, \"bunch of ints\", 1,2, 3 , 4, 5 , 6,7,8,9,\'a\']";
        assertThatThrownBy(() -> TestUtil.toJava(json, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unknown JSON value type");
    }

    @Test
    void testParseObjectWithoutClosingBrace()
    {
        String json = "{\"key\": true{";
        assertThatThrownBy(() -> TestUtil.toJava(json, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Object not ended with '}'");
    }

    @Test
    void testParseBadHex()
    {
        String json = "\"\\u5h1t\"";
        assertThatThrownBy(() -> TestUtil.toJava(json, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Expected hexadecimal digit");
    }

    @Test
    void testParseBadEscapeChar()
    {
        String json = "\"What if I try to escape incorrectly \\L1CK\"";
        assertThatThrownBy(() -> TestUtil.toJava(json, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Invalid character escape sequence");
    }

    @Test
    void testParseUnfinishedString()
    {
        String json = "\"This is an unfinished string...";
        assertThatThrownBy(() -> TestUtil.toJava(json, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached while reading JSON string");
    }

    @Test
    void testParseEOFInToken()
    {
        String json = "falsz";
        assertThatThrownBy(() -> TestUtil.toJava(json, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Expected token: false");
    }

    @Test
    void testParseEOFReadingToken()
    {
        String json = "tru";
        assertThatThrownBy(() -> TestUtil.toJava(json, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached while reading token: true");
    }

    @Test
    void testParseEOFinArray()
    {
        String json = "[true, false,";
        assertThatThrownBy(() -> TestUtil.toJava(json, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");
    }

    @Test
    void testEmptyFieldName()
    {
        String json = "{\"\":17}";
        Map<?, ?> doh = TestUtil.toJava(json, null).asClass(LinkedHashMap.class);
        assert doh.size() == 1;
        assertEquals(doh.get(""), 17L);
    }

    @Test
    void testNullFieldName()
    {
        // In strict JSON mode, unquoted "null" as field name should throw error
        String json = "{null:17}";
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toJava(json, strictOptions).asClass(LinkedHashMap.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unquoted field names not allowed in strict JSON mode");
    }

    @Test
    void testNullFieldNameJson5Permissive()
    {
        // In permissive JSON5 mode, unquoted "null" is a valid identifier for field name
        String json = "{null:17}";
        Map<?, ?> result = TestUtil.toJava(json, null).asClass(LinkedHashMap.class);
        assert result.size() == 1;
        assertEquals(17L, result.get("null"));
    }

    @Test
    void testMalformedJson()
    {
        final String json = "{\"field\"0}";  // colon expected between fields
        assertThatThrownBy(() -> TestUtil.toMaps(json, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Expected ':' between field and value");

        // In strict JSON mode, unquoted field names should throw error
        final String json1 = "{field:0}";  // not quoted field name
        ReadOptions strictOptions = new ReadOptionsBuilder().strictJson().build();
        assertThatThrownBy(() -> JsonIo.toMaps(json1, strictOptions).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unquoted field names not allowed in strict JSON mode");

        // In permissive JSON5 mode, unquoted field names should work
        Map<?, ?> result = TestUtil.toMaps(json1, null).asClass(null);
        assertEquals(0L, result.get("field"));

        final String json2 = "{\"field\":0";  // object not terminated correctly (ending in number)
        assertThatThrownBy(() -> TestUtil.toMaps(json2, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");

        final String json3 = "{\"field\":true";  // object not terminated correctly (ending in token)
        assertThatThrownBy(() -> TestUtil.toMaps(json3, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");

        final String json4 = "{\"field\":\"test\"";  // object not terminated correctly (ending in string)
        assertThatThrownBy(() -> TestUtil.toMaps(json4, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");

        final String json5 = "{\"field\":{}";  // object not terminated correctly (ending in another object)
        assertThatThrownBy(() -> TestUtil.toMaps(json5, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");

        final String json6 = "{\"field\":[]";  // object not terminated correctly (ending in an array)
        assertThatThrownBy(() -> TestUtil.toMaps(json6, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");

        final String json7 = "{\"field\":3.14";  // object not terminated correctly (ending in double precision number)
        assertThatThrownBy(() -> TestUtil.toMaps(json7, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");

        final String json8 = "[1,2,3";
        assertThatThrownBy(() -> TestUtil.toMaps(json8, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");

        final String json9 = "[false,true,false";
        assertThatThrownBy(() -> TestUtil.toMaps(json9, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");

        final String json10 = "[\"unclosed string]";
        assertThatThrownBy(() -> TestUtil.toMaps(json10, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached while reading JSON string");
    }

    @Test
    void testBadType()
    {
        ReadOptions readOptions = new ReadOptionsBuilder().failOnUnknownType(false).build();
        String json = "{\"@type\":\"non.existent.class.Non\"}";
        Map map = TestUtil.toJava(json, readOptions).asClass(null);
        assert map.size() == 0;

        // Bad class inside a Collection
        json = "{\"@type\":\"java.util.ArrayList\",\"@items\":[null, true, {\"@type\":\"bogus.class.Name\", \"fingers\":5}]}";
        List list = TestUtil.toJava(json, readOptions).asClass(null);
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
            TestUtil.toJava(json, null).asClass(null);
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
        Object x = TestUtil.toJava("\"\\/ should be one char\"", null).asClass(null);
        assert x.equals("/ should be one char");
        x = TestUtil.toJava("\"' should be one char\"", null).asClass(null);
        assert x.equals("' should be one char");
        x = TestUtil.toJava("\"a \\' char\"", null).asClass(null);
        assert x.equals("a ' char");
    }

    @Test
    public void testAliasedTypes()
    {
        String json = ClassUtilities.loadResourceAsString("errors/aliasedTypes.json");
        Object[] items = TestUtil.toJava(json, null).asClass(null);

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
