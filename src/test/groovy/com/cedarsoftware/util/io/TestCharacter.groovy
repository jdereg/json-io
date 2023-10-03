package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
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
class TestCharacter
{
    private static class ManyCharacters implements Serializable
    {
        private final Character _arrayElement
        private final Character[] _typeArray
        private final Object[] _objArray
        private final Object _polyRefTarget
        private final Object _polyRef
        private final Object _polyNotRef
        private final Character _min
        private final Character _max
        private final Character _null

        private ManyCharacters()
        {
            _arrayElement = new Character((char) 1)
            _polyRefTarget = new Character((char)71)
            _polyRef = _polyRefTarget
            _polyNotRef = new Character((char) 71)
            Character local = new Character((char)75)
            _null  = null
            _typeArray = [_arrayElement, 'a' as char, local, _null, null] as Character[]
            _objArray = [_arrayElement, 'b' as char, local, _null, null] as Object[]
            _min = Character.MIN_VALUE
            _max = Character.MAX_VALUE
        }
    }

    @Test
    void testCharacter()
    {
        ManyCharacters test = new ManyCharacters()
        String json = TestUtil.getJsonString(test)
        TestUtil.printLine("json = " + json)
        ManyCharacters that = (ManyCharacters) TestUtil.readJsonObject(json)

        assertTrue(that._arrayElement.equals((char) 1))
        assertTrue(that._polyRefTarget.equals((char) 71))
        assertTrue(that._polyRef.equals((char) 71))
        assertTrue(that._polyNotRef.equals((char) 71))
        assertSame(that._polyRef, that._polyRefTarget)
        assertSame(that._polyNotRef, that._polyRef)    // Character cache working

        assertTrue(that._typeArray.length == 5)
        assertSame(that._typeArray[0], that._arrayElement)
        assertTrue(that._typeArray[1] instanceof Character)
        assertTrue(that._typeArray[1].equals('a' as char))
        assertTrue(that._objArray.length == 5)
        assertSame(that._objArray[0], that._arrayElement)
        assertTrue(that._objArray[1] instanceof Character)
        assertTrue(that._objArray[1].equals('b' as char))
        assertTrue(that._polyRefTarget instanceof Character)
        assertTrue(that._polyNotRef instanceof Character)

        assertSame(that._objArray[2], that._typeArray[2])
        assertTrue(that._objArray[2].equals((char) 75))

        assertTrue(that._null == null)
        assertTrue(that._typeArray[3] == null)
        assertTrue(that._typeArray[4] == null)
        assertTrue(that._objArray[3] == null)
        assertTrue(that._objArray[4] == null)

        assertTrue(that._min.equals(Character.MIN_VALUE))
        assertTrue(that._max.equals(Character.MAX_VALUE))
    }

    @Test
    void testFunnyChars()
    {
        String json = '{"@type":"[C","@items":["a\\t\\u0004"]}'
        char[] chars = (char[]) TestUtil.readJsonObject(json)
        assertEquals(chars.length, 3)
        assertEquals(chars[0], 'a' as char)
        assertEquals(chars[1], '\t' as char)
        assertEquals(chars[2], '\u0004' as char)
    }

    @Test
    void testQuoteAsCharacterValue()
    {
        String jsonString = """{ "@type": "char", "value": "\\"" }"""
        JsonReader jsonReader = new JsonReader(new ByteArrayInputStream(jsonString.getBytes('UTF-8')))
        Object object = jsonReader.readObject()
        assert (Character) object == '"'
    }
}
