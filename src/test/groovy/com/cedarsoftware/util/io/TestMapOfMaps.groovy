package com.cedarsoftware.util.io

import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertSame
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

/**
 * Test cases for JsonReader / JsonWriter
 *
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
class TestMapOfMaps
{
    @Test
    void testMapOfMapsWithUnknownClasses() throws Exception
    {
        String json = '{"@type":"com.foo.bar.baz.Qux","_name":"Hello","_other":null}'

        try
        {
            JsonReader.jsonToJava(json)
            fail()
        }
        catch (IOException e)
        {
            assertTrue(e.message.toLowerCase().contains("could not be created"))
        }
        Map map = JsonReader.jsonToMaps(json)
        assertEquals('Hello', map._name)
        assertNull(map._other)

        // 2nd attempt
        String testObjectClassName = TestObject.class.name
        json = '{"@type":"' + testObjectClassName + '","_name":"alpha","_other":{"@type":"com.baz.Qux","_name":"beta","_other":null}}'

        try
        {
            JsonReader.jsonToJava(json)
            fail()
        }
        catch (IOException e)
        {
            assertTrue(e.message.toLowerCase().contains('ioexception setting field'))
        }

        map = JsonReader.jsonToMaps(json)
        assertEquals('alpha', map._name)
        assertTrue(map._other instanceof JsonObject)
        JsonObject other = (JsonObject) map._other
        assertEquals('beta', other._name)
        assertNull(other._other)
    }

    @Test
    void testForwardRefNegId() throws Exception
    {
        Map doc = JsonReader.jsonToMaps(TestUtil.getResource("forwardRefNegId.json"))
        Object[] items = doc['@items']
        assertEquals(2, items.length)
        Map male = items[0]
        Map female = items[1]
        assertEquals('John', male.name)
        assertEquals('Sonya', female.name)
        assertSame(male.friend, female)
        assertSame(female.friend, male)
    }
}
