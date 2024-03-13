package com.cedarsoftware.io;

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
class ByteTest
{
    private static class ManyBytes implements Serializable
    {
        private final Byte _arrayElement;
        private final Byte[] _typeArray;
        private final Object[] _objArray;
        private final Object _polyRefTarget;
        private final Object _polyRef;
        private final Object _polyNotRef;
        private final Byte _min;
        private final Byte _max;
        private final Byte _null;

        private ManyBytes()
        {
            _arrayElement = (byte) -1;
            _polyRefTarget = (byte) 71;
            _polyRef = _polyRefTarget;
            _polyNotRef = (byte) 71;
            Byte local = (byte) 75;
            _null  = null;
            _typeArray = new Byte[] {_arrayElement, (byte) 44, local, _null, null, (byte) 44};
            _objArray = new Object[] {_arrayElement, (byte) 69, local, _null, null, (byte) 69};
            _min = Byte.MIN_VALUE;
            _max = Byte.MAX_VALUE;
        }
    }

    @Test
    void testByte()
    {
        ManyBytes test = new ManyBytes();
        String json = TestUtil.toJson(test);
        TestUtil.printLine("json = " + json);

        ManyBytes that = TestUtil.toObjects(json, null);

        assertEquals((byte) that._arrayElement, (byte) -1);
        assertEquals(that._polyRefTarget, (byte) 71);
        assertEquals(that._polyRef, (byte) 71);
        assertEquals(that._polyNotRef, (byte) 71);
        assertSame(that._polyRef, that._polyRefTarget);
        assertSame(that._polyNotRef, that._polyRef);             // byte cache is working

        assertEquals(6, that._typeArray.length);
        assertSame(that._typeArray[0], that._arrayElement);  // byte cache is working
        assertTrue(that._typeArray[1] instanceof Byte);
        assertTrue(that._typeArray[1] instanceof Byte);
        assertEquals((byte) that._typeArray[1], (byte) 44);
        assertEquals(6, that._objArray.length);
        assertSame(that._objArray[0], that._arrayElement);   // byte cache is working
        assertTrue(that._objArray[1] instanceof Byte);
        assertEquals(that._objArray[1], (byte) 69);
        assertTrue(that._polyRefTarget instanceof Byte);
        assertTrue(that._polyNotRef instanceof Byte);
        assertEquals(that._objArray[2], (byte) 75);

        assertNull(that._null);
        assertNull(that._typeArray[3]);
        assertNull(that._typeArray[4]);
        assertNull(that._objArray[3]);
        assertNull(that._objArray[4]);

        assertEquals(Byte.MIN_VALUE, (byte) that._min);
        assertEquals(Byte.MAX_VALUE, (byte) that._max);
    }
}
