package com.cedarsoftware.util.io

import org.junit.Test

import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
class TestErrors
{
    @Test
    void testBadJson() throws Exception
    {
        Object o = null;

        try
        {
            o = TestUtil.readJsonObject('["bad JSON input"')
            fail()
        }
        catch(Exception e)
        {
            assertTrue(e.message.contains("inside"))
        }
        assertTrue(o == null)
    }

    @Test
    void testParseMissingQuote() throws Exception
    {
        try
        {
            String json = '''{
  "array": [
    1,
    2,
    3
  ],
  "boolean": true,
  "null": null,
  "number": 123,
  "object": {
    "a": "b",
    "c": "d",
    "e": "f"
  },
  "string: "Hello World"
}'''
            JsonReader.jsonToJava(json)
            fail()
        }
        catch (IOException e)
        {
            assertTrue(e.message.contains("Expected ':' between string field and value"))
        }
    }
    @Test
    void testParseInvalid1stChar() throws IOException
    {
        try
        {
            String json = '''
  "array": [
    1,
    2,
    3
  ],
  "boolean": true,
  "null": null,
  "number": 123,
  "object": {
    "a": "b",
    "c": "d",
    "e": "f"
  },
  "string:" "Hello World"
}'''
            JsonReader.jsonToJava(json)
            fail()
        }
        catch (IOException e)
        {
            assertTrue(e.message.contains("nknown JSON value type"))
        }
    }

    @Test
    void testParseMissingLastBrace() throws IOException
    {
        try
        {
            String json = """{
  "array": [
    1,
    2,
    3
  ],
  "boolean": true,
  "null": null,
  "number": 123,
  "object": {
    "a": "b",
    "c": "d",
    "e": "f"
  },
  "string": "Hello World"
"""
            JsonReader.jsonToJava(json)
            fail()
        }
        catch (IOException e)
        {
            assertTrue(e.message.contains("EOF reached before closing '}'"))
        }
    }

    @Test
    void testParseMissingLastBracket() throws IOException
    {
        String json = '[true, "bunch of ints", 1,2, 3 , 4, 5 , 6,7,8,9,10]'
        JsonReader.jsonToJava(json)

        try
        {
            json = '[true, "bunch of ints", 1,2, 3 , 4, 5 , 6,7,8,9,10'
            JsonReader.jsonToJava(json)
            fail()
        }
        catch (IOException e)
        {
            assertTrue(e.message.contains("xpected ',' or ']' inside array"))
        }
    }

    @Test
    void testParseBadValueInArray() throws IOException
    {
        String json

        try
        {
            json = '[true, "bunch of ints", 1,2, 3 , 4, 5 , 6,7,8,9,\'a\']'
            JsonReader.jsonToJava(json)
            fail()
        }
        catch (IOException e)
        {
            assertTrue(e.message.contains("nknown JSON value type"))
        }
    }

    @Test
    void testParseObjectWithoutClosingBrace() throws IOException
    {
        try
        {
            String json = '{"key": true{'
            JsonReader.jsonToJava(json)
            fail()
        }
        catch (IOException e)
        {
            assertTrue(e.message.contains("Object not ended with '}'"))
        }
    }

    @Test
    void testParseBadHex()
    {
        try
        {
            String json = '"\\u5h1t"'
            JsonReader.jsonToJava(json)
            fail()
        }
        catch (IOException e)
        {
            assertTrue(e.message.contains("Expected hexadecimal digits"))
        }
    }

    @Test
    void testParseBadEscapeChar()
    {
        try
        {
            String json = '"What if I try to escape incorrectly \\L1CK"'
            JsonReader.jsonToJava(json)
            fail()
        }
        catch (IOException e)
        {
            assertTrue(e.message.contains("nvalid character escape sequence specified"))
        }
    }

    @Test
    void testParseUnfinishedString()
    {
        try
        {
            String json = '"This is an unfinished string...'
            JsonReader.jsonToJava(json)
            fail()
        }
        catch (IOException e)
        {
            assertTrue(e.message.contains("EOF reached while reading JSON string"))
        }
    }

    @Test
    void testParseEOFInToken()
    {
        try
        {
            String json = "falsz"
            JsonReader.jsonToJava(json)
            fail()
        }
        catch (IOException e)
        {
            assertTrue(e.message.contains("xpected token: false"))
        }
    }

    @Test
    void testParseEOFReadingToken()
    {
        try
        {
            String json = "tru"
            JsonReader.jsonToJava(json)
            fail()
        }
        catch (IOException e)
        {
            assertTrue(e.message.contains("EOF reached while reading token"))
        }
    }

    @Test
    void testParseEOFinArray()
    {
        try
        {
            String json = "[true, false,"
            JsonReader.jsonToJava(json)
            fail()
        }
        catch (IOException e)
        {
            assertTrue(e.message.contains("EOF reached prematurely"))
        }
    }

    @Test
    void testMalformedJson() throws Exception
    {
        String json;

        try
        {
            json = '{"field"0}'  // colon expected between fields
            JsonReader.jsonToMaps(json)
            fail()
        }
        catch (IOException e) { }

        try
        {
            json = "{field:0}"  // not quoted field name
            JsonReader.jsonToMaps(json)
            fail()
        }
        catch (IOException e) { }

        try
        {
            json = '{"field":0'  // object not terminated correctly (ending in number)
            JsonReader.jsonToMaps(json)
            fail()
        }
        catch (IOException e) { }

        try
        {
            json = '{"field":true'  // object not terminated correctly (ending in token)
            JsonReader.jsonToMaps(json)
            fail()
        }
        catch (IOException e) { }

        try
        {
            json = '{"field":"test"'  // object not terminated correctly (ending in string)
            JsonReader.jsonToMaps(json)
            fail()
        }
        catch (IOException e) { }

        try
        {
            json = '{"field":{}'  // object not terminated correctly (ending in another object)
            JsonReader.jsonToMaps(json)
            fail()
        }
        catch (IOException e) { }

        try
        {
            json = '{"field":[]'  // object not terminated correctly (ending in an array)
            JsonReader.jsonToMaps(json)
            fail()
        }
        catch (IOException e) { }

        try
        {
            json = '{"field":3.14'  // object not terminated correctly (ending in double precision number)
            JsonReader.jsonToMaps(json)
            fail()
        }
        catch (IOException e) { }

        try
        {
            json = '[1,2,3'
            JsonReader.jsonToMaps(json)
            fail()
        }
        catch (IOException e)  { }

        try
        {
            json = "[false,true,false"
            JsonReader.jsonToMaps(json)
            fail()
        }
        catch (IOException e) { }

        try
        {
            json = '["unclosed string]'
            JsonReader.jsonToMaps(json)
            fail()
        }
        catch (IOException e) { }
    }

    @Test
    void testBadType() throws Exception
    {
        try
        {
            String json = '{"@type":"non.existent.class.Non"}'
            TestUtil.readJsonObject(json)
            fail()
        }
        catch (IOException e)
        {
            assertTrue(e.message.toLowerCase().contains("unable"))
            assertTrue(e.message.toLowerCase().contains("create"))
            assertTrue(e.message.toLowerCase().contains("class"))
        }

        // Bad class inside a Collection
        try
        {
            String json = '{"@type":"java.util.ArrayList","@items":[null, true, {"@type":"bogus.class.Name"}]}'
            TestUtil.readJsonObject(json)
            fail()
        }
        catch (IOException e) { }
    }

    @Test
    void testBadHexNumber() throws Exception
    {
        StringBuilder str = new StringBuilder()
        str.append("[\"\\")
        str.append("u000r\"]")
        try
        {
            TestUtil.readJsonObject(str.toString())
            fail()
        }
        catch (IOException e) { }
    }

    @Test
    void testBadValue() throws Exception
    {
        try
        {
            String json = '{"field":19;}'
            TestUtil.readJsonObject(json)
            fail()
        }
        catch (IOException e) { }

        try
        {
            String json = '{"field":'
            TestUtil.readJsonObject(json)
            fail()
        }
        catch (IOException e) { }

        try
        {
            String json = '{"field":joe'
            TestUtil.readJsonObject(json)
            fail()
        }
        catch (IOException e) { }

        try
        {
            String json = '{"field":trux}'
            TestUtil.readJsonObject(json)
            fail()
        }
        catch (IOException e) { }

        try
        {
            String json = '{"field":tru'
            TestUtil.readJsonObject(json)
            fail()
        }
        catch (IOException e) { }
    }

    @Test
    void testStringEscape() throws Exception
    {
        String json = '["escaped slash \\\' should result in a single /"]'
        TestUtil.readJsonObject(json)

        json = '["escaped slash \\/ should result in a single /"]'
        TestUtil.readJsonObject(json)

        try
        {
            json = '["escaped slash \\x should result in a single /"]'
            TestUtil.readJsonObject(json)
            fail()
        }
        catch (IOException e) { }
    }

    @Test
    void testClassMissingValue() throws Exception
    {
        try
        {
            TestUtil.readJsonObject('[{"@type":"class"}]')
            fail()
        }
        catch (IOException e) { }
    }

    @Test
    void testCalendarMissingValue() throws Exception
    {
        try
        {
            TestUtil.readJsonObject('[{"@type":"java.util.Calendar"}]')
            fail()
        }
        catch (IOException e) { }
    }

    @Test
    void testBadFormattedCalendar() throws Exception
    {
        try
        {
            TestUtil.readJsonObject('[{"@type":"java.util.GregorianCalendar","value":"2012-05-03T12:39:45.1X5-0400"}]')
            fail()
        }
        catch (IOException e) { }
    }

    @Test
    void testEmptyClassName() throws Exception
    {
        try
        {
            TestUtil.readJsonObject('{"@type":""}')
            fail()
        }
        catch(IOException e) { }
    }

    @Test
    void testBadBackRef() throws Exception
    {
        try
        {
            TestUtil.readJsonObject('{"@type":"java.util.ArrayList","@items":[{"@ref":1}]}')
            fail()
        }
        catch(IOException e) { }
    }

    @Test
    void testErrorReporting() throws IOException
    {
        String json = '[{"@type":"funky"},\n{"field:"value"]'
        try
        {
            JsonReader.jsonToJava(json)
            fail()
        }
        catch (IOException e) { }
    }
}
