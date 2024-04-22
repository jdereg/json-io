package com.cedarsoftware.io;

import java.io.Serializable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
public class ShortTest
{
    @Test
    public void testShort()
    {
        ManyShorts test = new ManyShorts();
        String json = TestUtil.toJson(test);
        TestUtil.printLine("json = " + json);
        ManyShorts that = TestUtil.toObjects(json, null);

        assertEquals((short) that._arrayElement, (short) -1);
        assertEquals(that._polyRefTarget, (short) 710);
        assertEquals(that._polyRef, (short) 710);
        assertEquals(that._polyNotRef, (short) 710);
        assertNotSame(that._polyRef, that._polyRefTarget);// Primitive wrappers are treated like primitives (no ref)
        assertNotSame(that._polyNotRef, that._polyRef);

        assertEquals(6, that._typeArray.length);
        assertSame(that._typeArray[0], that._arrayElement);
        assertTrue(that._typeArray[1] instanceof Short);
        assertTrue(that._typeArray[1] instanceof Short);
        assertEquals((short) that._typeArray[1], (short) 44);
        assertEquals(6, that._objArray.length);
        assertSame(that._objArray[0], that._arrayElement);
        assertTrue(that._objArray[1] instanceof Short);
        assertEquals(that._objArray[1], (short) 69);
        assertTrue(that._polyRefTarget instanceof Short);
        assertTrue(that._polyNotRef instanceof Short);

        assertSame(that._objArray[2], that._typeArray[2]);
        assertEquals(that._objArray[2], (short) 75);

        // Because of cache in Short.valueOf(), these values between -128 and 127 will have same address.
        assertSame(that._typeArray[1], that._typeArray[5]);
        assertSame(that._objArray[1], that._objArray[5]);

        assertNull(that._null);
        assertNull(that._typeArray[3]);
        assertNull(that._typeArray[4]);
        assertNull(that._objArray[3]);
        assertNull(that._objArray[4]);

        assertEquals(Short.MIN_VALUE, (short) that._min);
        assertEquals(Short.MAX_VALUE, (short) that._max);
    }

    private static class ManyShorts implements Serializable
    {
        private ManyShorts()
        {
            _arrayElement = (short) -1;
            _polyRefTarget = (short) 710;
            _polyRef = _polyRefTarget;
            _polyNotRef = (short) 710;
            Short local = (short) 75;
            _null = null;
            _typeArray = new Short[]{_arrayElement, (short) 44, local, _null, null, (short) 44};
            _objArray = new Object[]{_arrayElement, (short) 69, local, _null, null, (short) 69};
            _min = Short.MIN_VALUE;
            _max = Short.MAX_VALUE;
        }

        private final Short _arrayElement;
        private final Short[] _typeArray;
        private final Object[] _objArray;
        private final Object _polyRefTarget;
        private final Object _polyRef;
        private final Object _polyNotRef;
        private final Short _min;
        private final Short _max;
        private final Short _null;
    }
}
