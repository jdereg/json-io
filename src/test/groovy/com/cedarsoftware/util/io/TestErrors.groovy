package com.cedarsoftware.util.io

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

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
@CompileStatic
class TestErrors
{
    @Test
    void testBadJson()
    {
        assertThrows(Exception.class, { TestUtil.readJsonObject('["bad JSON input"')})
    }

    @Test
    void testParseMissingQuote()
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
            assertThrows(Exception.class, { JsonReader.jsonToJava(json) })
    }

    @Test
    void testParseInvalid1stChar()
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
        def x = JsonReader.jsonToJava(json)
        assert "array" == x
    }

    @Test
    void testParseMissingLastBrace()
    {
            String json = """{
  "array": [
    1,1,1,1,1,1,1,1,1,1,
    2,0,0,0,0,0,0,0,0,0,0,0,0,
    3,4,5,6,7,8,9,10
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
            assertThrows(Exception.class, { JsonReader.jsonToJava(json) })
    }

    @Test
    void testParseMissingLastBracket()
    {
        String json = '[true, "bunch of ints", 1,2, 3 , 4, 5 , 6,7,8,9,10]'
        JsonReader.jsonToJava(json)

        json = '[true, "bunch of ints", 1,2, 3 , 4, 5 , 6,7,8,9,10'
        assertThrows(Exception.class, { JsonReader.jsonToJava(json) })
    }

    @Test
    void testParseBadValueInArray()
    {
        String json = '[true, "bunch of ints", 1,2, 3 , 4, 5 , 6,7,8,9,\'a\']'
        assertThrows(Exception.class, { JsonReader.jsonToJava(json) })
    }

    @Test
    void testParseObjectWithoutClosingBrace()
    {
        String json = '{"key": true{'
        assertThrows(Exception.class, { JsonReader.jsonToJava(json) })
    }

    @Test
    void testParseBadHex()
    {
        String json = '"\\u5h1t"'
        assertThrows(Exception.class, { JsonReader.jsonToJava(json) })
    }

    @Test
    void testParseBadEscapeChar()
    {
        String json = '"What if I try to escape incorrectly \\L1CK"'
        assertThrows(Exception.class, { JsonReader.jsonToJava(json) })
    }

    @Test
    void testParseUnfinishedString()
    {
        String json = '"This is an unfinished string...'
        assertThrows(Exception.class, { JsonReader.jsonToJava(json) })
    }

    @Test
    void testParseEOFInToken()
    {
        String json = "falsz"
        assertThrows(Exception.class, { JsonReader.jsonToJava(json) })
    }

    @Test
    void testParseEOFReadingToken()
    {
        String json = "tru"
        assertThrows(Exception.class, { JsonReader.jsonToJava(json) })
    }

    @Test
    void testParseEOFinArray()
    {
        String json = "[true, false,"
        assertThrows(Exception.class, { JsonReader.jsonToJava(json) })
    }

    @Test
    void testMalformedJson()
    {
        String json = '{"field"0}'  // colon expected between fields
        assertThrows(Exception.class, { JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map) })

        json = "{field:0}"  // not quoted field name
        assertThrows(Exception.class, { JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map) })

        json = '{"field":0'  // object not terminated correctly (ending in number)
        assertThrows(Exception.class, { JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map) })

        json = '{"field":true'  // object not terminated correctly (ending in token)
        assertThrows(Exception.class, { JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map) })

        json = '{"field":"test"'  // object not terminated correctly (ending in string)
        assertThrows(Exception.class, { JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map) })

        json = '{"field":{}'  // object not terminated correctly (ending in another object)
        assertThrows(Exception.class, { JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map) })

        json = '{"field":[]'  // object not terminated correctly (ending in an array)
        assertThrows(Exception.class, { JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map) })

        json = '{"field":3.14'  // object not terminated correctly (ending in double precision number)
        assertThrows(Exception.class, { JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map) })

        json = '[1,2,3'
        assertThrows(Exception.class, { JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map) })

        json = "[false,true,false"
        assertThrows(Exception.class, { JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map) })

        json = '["unclosed string]'
        assertThrows(Exception.class, { JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map) })
    }

    @Test
    void testBadType()
    {
        String json = '{"@type":"non.existent.class.Non"}'
        Map map = (Map) TestUtil.readJsonObject(json)
        assert map.size() == 0

        // Bad class inside a Collection
        json = '{"@type":"java.util.ArrayList","@items":[null, true, {"@type":"bogus.class.Name", "fingers":5}]}'
        List list = (List) TestUtil.readJsonObject(json)
        assert list.size() == 3
        assert list[0] == null
        assert list[1]
        assert list[2]['fingers'] == 5
    }

    private static class Animal
    {

    }

    private static class Dog extends Animal
    {

    }

    private static class AnimalHolder
    {
        Animal dog;
    }

    @Test
    void testBadTypeObject()
    {
        // Unclear and misleading error if the type is not found
        AnimalHolder h = new AnimalHolder()
        h.dog = new Dog()

        String json = TestUtil.getJsonString(h)
        json = json.replace('$Dog', '$BadDog');
       try
        {
            TestUtil.readJsonObject(json)
        }
        catch (Exception e)
        {
            // Should indicate the type that was not found
            assert e.toString().contains("BadDog");
        }
    }

    @Test
    void testBadHexNumber()
    {
        StringBuilder str = new StringBuilder()
        str.append("[\"\\")
        str.append("u000r\"]")

        assertThrows(Exception.class, { TestUtil.readJsonObject(str.toString() )})
    }

    @Test
    void testBadValue()
    {
        String json = '{"field":19;}'
        assertThrows(Exception.class, { TestUtil.readJsonObject(json )})

        json = '{"field":'
        assertThrows(Exception.class, { TestUtil.readJsonObject(json )})

        json = '{"field":joe'
        assertThrows(Exception.class, { TestUtil.readJsonObject(json )})

        json = '{"field":trux}'
        assertThrows(Exception.class, { TestUtil.readJsonObject(json )})

        json = '{"field":tru'
        assertThrows(Exception.class, { TestUtil.readJsonObject(json )})
    }

    @Test
    void testStringEscape()
    {
        String json = '["escaped slash \\\' should result in a single /"]'
        TestUtil.readJsonObject(json)

        json = '["escaped slash \\/ should result in a single /"]'
        TestUtil.readJsonObject(json)

        json = '["escaped slash \\x should result in a single /"]'
        assertThrows(Exception.class, { TestUtil.readJsonObject(json )})
    }

    @Test
    void testClassMissingValue()
    {
        assertThrows(Exception.class, { TestUtil.readJsonObject('[{"@type":"class"}]' )})
    }

    @Test
    void testCalendarMissingValue()
    {
        assertThrows(Exception.class, { TestUtil.readJsonObject('[{"@type":"java.util.Calendar"}]' )})
    }

    @Test
    void testBadFormattedCalendar()
    {
        assertThrows(Exception.class, { TestUtil.readJsonObject('[{"@type":"java.util.GregorianCalendar","value":"2012-05-03T12:39:45.1X5-0400"}]' )})
    }

    @Test
    void testEmptyClassName()
    {
        assertThrows(Exception.class, { TestUtil.readJsonObject('{"@type":""}' )})
    }

    @Test
    void testBadBackRef()
    {
        assertThrows(Exception.class, { TestUtil.readJsonObject('{"@type":"java.util.ArrayList","@items":[{"@ref":1}]}' )})
    }

    @Test
    void testErrorReporting()
    {
        String json = '[{"@type":"funky"},\n{"field:"value"]'
        assertThrows(Exception.class, { JsonReader.jsonToJava(json)})
    }

    @Test
    void testFieldMissingQuotes()
    {
        String json = '[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, {"field":25, field2: "no quotes"}, 11, 12, 13]'
        assertThrows(Exception.class, { JsonReader.jsonToJava(json)})
    }

    @Test
    void testBadFloatingPointNumber()
    {
        String json = '[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, {"field":25, "field2": "no quotes"}, 11, 12.14a, 13]'
        assertThrows(Exception.class, { JsonReader.jsonToJava(json,  [(JsonReader.USE_MAPS):true] as Map)})
    }
}
