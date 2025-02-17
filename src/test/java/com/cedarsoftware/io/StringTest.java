package com.cedarsoftware.io;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
public class StringTest
{
    @Test
    public void testString()
    {
        ManyStrings test = new ManyStrings();
        String jsonOut = TestUtil.toJson(test);
        TestUtil.printLine("json=" + jsonOut);
        ManyStrings that = TestUtil.toObjects(jsonOut, null);

        for (int i = 0; i < ManyStrings.MAX_UTF8_CHAR; i++)
        {
            assertEquals(that._range.charAt(i), (char) i);
        }


        // UTF-8 serialization makes it through clean.
        byte[] bytes = that._utf8HandBuilt.getBytes(StandardCharsets.UTF_8);
        assertEquals(bytes[0], (byte) 0xf0);
        assertEquals(bytes[1], (byte) 0x9d);
        assertEquals(bytes[2], (byte) 0x80);
        assertEquals(bytes[3], (byte) 0x80);

        // Cannot assert same-ness depends on LRU cache size and order tests are run.
        assertEquals(that._strArray[0], that._objArray[0]);
        assertEquals(that._strArray[1], that._objArray[1]);

        assertEquals(5, that._strArray.length);
        assertEquals(5, that._objArray.length);
        assertEquals(5, that._objStrArray.length);
        assertNull(that._strArray[2]);
        assertNull(that._objArray[2]);
        assertNull(that._objStrArray[2]);
        assertNull(that._strArray[3]);
        assertNull(that._objArray[3]);
        assertNull(that._objStrArray[3]);
        assertEquals("Poly", that._poly);

        // Cannot assert same-ness because LRU cache size and order tests are run.
        assertEquals(that._cache[0], that._cache[1]);// "true" is part of the reusable cache.
        assertEquals(that._cache[2], that._cache[3]);// Strings are immutable and we are instance folding.
    }

    @Test
    public void testRootString()
    {
        String s = "\"root string\"";
        Object o = TestUtil.toObjects(s, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assertEquals("root string", o);
        o = TestUtil.toObjects(s, null);
        assertEquals("root string", o);
    }

    @Test
    public void testStringAsObject()
    {
        String json = "{\"@type\":\"string\",\"value\":\"Sledge Hammer\"}";
        String x = TestUtil.toObjects(json, null);
        assert x.equals("Sledge Hammer");
    }

    @Test
    public void testFrenchChar()
    {
        String json = "\"Réunion\"";
        String x = TestUtil.toObjects(json, null);
        assert x.equals("Réunion");
    }

    @Test
    public void testEmptyString()
    {
        assertThatThrownBy(() -> TestUtil.toObjects("", null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");
    }

    @Test
    public void testNullInput()
    {
        assertThatThrownBy(() -> TestUtil.toObjects(null, (Class<?>)null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached prematurely");
    }
    
    private static class ManyStrings implements Serializable
    {
        private ManyStrings()
        {
            _null = null;
            StringBuffer s = new StringBuffer();
            for (int i = 0; i < MAX_UTF8_CHAR; i++)
            {
                s.append((char)i);
            }

            _range = s.toString();

            // BYZANTINE MUSICAL SYMBOL PSILI
            byte[] symbol = new byte[] {(byte) 0xf0, (byte) 0x9d, (byte) 0x80, (byte) 0x80};
            _utf8HandBuilt = new String(symbol, StandardCharsets.UTF_8);

            _strArray = new String[]{"1st", "2nd", _null, null, "3rd"};
            _objArray = new Object[]{"1st", "2nd", _null, null, "3rd"};
            _objStrArray = new String[]{"1st", "2nd", _null, null, "3rd"};
            _cache = new Object[]{"true", "true", "golf", "golf"};
            _poly = "Poly";
        }

        private static final int MAX_UTF8_CHAR = 100;
        private final String _range;
        private String _utf8HandBuilt;
        private final String[] _strArray;
        private final Object[] _objArray;
        private final Object[] _objStrArray;
        private final Object[] _cache;
        private final Object _poly;
        private final String _null;
    }
}
