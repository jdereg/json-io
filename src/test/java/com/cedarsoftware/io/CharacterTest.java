package com.cedarsoftware.io;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
public class CharacterTest
{
    @Test
    public void testCharacter()
    {
        ManyCharacters test = new ManyCharacters();
        String json = TestUtil.toJson(test);
        TestUtil.printLine("json = " + json);
        ManyCharacters that = TestUtil.toObjects(json, null);

        assertEquals((char) that._arrayElement, (char) 1);
        assertEquals(that._polyRefTarget, (char) 71);
        assertEquals(that._polyRef, (char) 71);
        assertEquals(that._polyNotRef, (char) 71);
        assertSame(that._polyRef, that._polyRefTarget);
        assertSame(that._polyNotRef, that._polyRef);// Character cache working

        assertEquals(5, that._typeArray.length);
        assertSame(that._typeArray[0], that._arrayElement);
        assertTrue(that._typeArray[1] instanceof Character);
        assertEquals('a', (char) that._typeArray[1]);
        assertEquals(5, that._objArray.length);
        assertSame(that._objArray[0], that._arrayElement);
        assertTrue(that._objArray[1] instanceof Character);
        assertEquals('b', that._objArray[1]);
        assertTrue(that._polyRefTarget instanceof Character);
        assertTrue(that._polyNotRef instanceof Character);

        assertSame(that._objArray[2], that._typeArray[2]);
        assertEquals(that._objArray[2], (char) 75);

        assertNull(that._null);
        assertNull(that._typeArray[3]);
        assertNull(that._typeArray[4]);
        assertNull(that._objArray[3]);
        assertNull(that._objArray[4]);

        assertEquals(Character.MIN_VALUE, (char) that._min);
        assertEquals(Character.MAX_VALUE, (char) that._max);
    }

    @Test
    void testFunnyChars()
    {
        String json = "{\"@type\":\"[C\",\"@items\":[\"a\\t\\u0004\"]}";
        char[] chars = TestUtil.toObjects(json, null);
        assertEquals(chars.length, 3);
        assertEquals(chars[0], 'a');
        assertEquals(chars[1], '\t');
        assertEquals(chars[2], '\u0004');
    }

    @Test
    void testQuoteAsCharacterValue()
    {   // { "@type": "char", "value": "\\"" }
        String json = "{\"@type\":\"char\",\"value\": \"\\\"\"}";
        Character ch = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), char.class);
        assert ch == '"';

    }

    @Test
    void testUnicodeChars1()
    {
        String monkeys = "\uD83D\uDE4A\uD83D\uDE49\uD83D\uDE48";
        String json = JsonIo.toJson(monkeys, null);
        String rereadJson = JsonIo.toObjects(json, null, String.class);
        assertEquals(rereadJson, monkeys);
    }

    @Test
    void testUnicodeChars2()
    {
        String monkeys = "\uD83D\uDE4A\uD83D\uDE49\uD83D\uDE48";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonIo.toJson(baos, monkeys, null);
        String json = baos.toString();
        assertEquals(json, '"' + monkeys + '"');
    }

    @Test
    void testUnicodeChars3() throws Exception
    {
        String monkeys = "\uD83D\uDE4A\uD83D\uDE49\uD83D\uDE48";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonIo.toJson(baos, monkeys, null);
        String json = baos.toString();
        assertEquals('"' + monkeys + '"', json);
    }

    private static class ManyCharacters implements Serializable
    {
        private ManyCharacters()
        {
            _arrayElement = (char) 1;
            _polyRefTarget = (char) 71;
            _polyRef = _polyRefTarget;
            _polyNotRef = (char) 71;
            Character local = (char) 75;
            _null = null;
            _typeArray = new Character[]{_arrayElement, 'a', local, _null, null};
            _objArray = new Object[]{_arrayElement, 'b', local, _null, null};
            _min = Character.MIN_VALUE;
            _max = Character.MAX_VALUE;
        }

        private final Character _arrayElement;
        private final Character[] _typeArray;
        private final Object[] _objArray;
        private final Object _polyRefTarget;
        private final Object _polyRef;
        private final Object _polyNotRef;
        private final Character _min;
        private final Character _max;
        private final Character _null;
    }
}
