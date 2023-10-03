package com.cedarsoftware.util.io

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotSame
import static org.junit.jupiter.api.Assertions.assertSame
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
class TestString
{
    private static class ManyStrings implements Serializable
    {
        private static final int MAX_UTF8_CHAR = 100
        // Foreign characters test (UTF8 multi-byte chars)
        private final String _range
        private String _utf8HandBuilt
        private final String[] _strArray
        private final Object[] _objArray
        private final Object[] _objStrArray
        private final Object[] _cache
        private final Object _poly
        private final String _null

        private ManyStrings()
        {
            _null = null;
            StringBuffer s = new StringBuffer()
            for (int i = 0; i < MAX_UTF8_CHAR; i++)
            {
                s.append(i as char)
            }
            _range = s.toString()

            // BYZANTINE MUSICAL SYMBOL PSILI
            try
            {
                byte[] symbol = [(byte) 0xf0, (byte) 0x9d, (byte) 0x80, (byte) 0x80] as byte[]
                _utf8HandBuilt = new String(symbol, "UTF-8")
            }
            catch (UnsupportedEncodingException e)
            {
                TestUtil.printLine("Get a new JVM that supports UTF-8")
            }

            _strArray = ["1st", "2nd", _null, null, new String("3rd")] as String[]
            _objArray = ["1st", "2nd", _null, null, new String("3rd")] as Object[]
            _objStrArray = ["1st", "2nd", _null, null, new String("3rd")] as String[]
            _cache = ["true", "true", "golf", "golf"] as Object[]
            _poly = "Poly"
        }
    }

    @Test
    void testString()
    {
        ManyStrings test = new ManyStrings()
        String jsonOut = TestUtil.getJsonString(test)
        TestUtil.printLine("json=" + jsonOut)
        ManyStrings that = (ManyStrings) TestUtil.readJsonObject(jsonOut)

        for (int i = 0; i < ManyStrings.MAX_UTF8_CHAR; i++)
        {
            assertTrue(that._range.charAt(i) == (i as char))
        }

        // UTF-8 serialization makes it through clean.
        byte[] bytes = that._utf8HandBuilt.getBytes("UTF-8")
        assertTrue(bytes[0] == (byte) 0xf0)
        assertTrue(bytes[1] == (byte) 0x9d)
        assertTrue(bytes[2] == (byte) 0x80)
        assertTrue(bytes[3] == (byte) 0x80)

        assertNotSame(that._strArray[0], that._objArray[0])
        assertEquals(that._strArray[0], that._objArray[0])
        assertNotSame(that._strArray[1], that._objArray[1])
        assertEquals(that._strArray[1], that._objArray[1])

        assertTrue(that._strArray.length == 5)
        assertTrue(that._objArray.length == 5)
        assertTrue(that._objStrArray.length == 5)
        assertTrue(that._strArray[2] == null)
        assertTrue(that._objArray[2] == null)
        assertTrue(that._objStrArray[2] == null)
        assertTrue(that._strArray[3] == null)
        assertTrue(that._objArray[3] == null)
        assertTrue(that._objStrArray[3] == null)
        assertTrue("Poly".equals(that._poly))

        assertSame(that._cache[0], that._cache[1])       // "true' is part of the reusable cache.
        assertNotSame(that._cache[2], that._cache[3])    // "golf' is NOT part of the reusable cache.
    }

    @Test
    void testRootString()
    {
        String s = '"root string"'
        Object o = JsonReader.jsonToJava(s, [(JsonReader.USE_MAPS):true] as Map)
        assertEquals("root string", o)
        o = TestUtil.readJsonObject(s)
        assertEquals("root string", o)
    }

    @Test
    void testStringAsObject()
    {
        String json = '{"@type":"string","value":"Sledge Hammer"}'
        String x = TestUtil.readJsonObject(json)
        assert x == 'Sledge Hammer'
    }

    @Test
    void testFrenchChar()
    {
        String json = '"Réunion"'
        String x = TestUtil.readJsonObject(json)
        assert x == 'Réunion'
    }

    @Test
    void testEmptyString()
    {
        // Ensure no exception is thrown
        JsonReader.jsonToJava("");
    }

    @Test
    void testNullInput()
    {
        // Ensure no exception is thrown
        JsonReader.jsonToJava(null);
    }
}
