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
public class IntegerTest
{
    @Test
    public void testInteger()
    {
        ManyIntegers test = new ManyIntegers();
        String json = TestUtil.toJson(test);
        TestUtil.printLine("json = " + json);
        ManyIntegers that = (ManyIntegers) TestUtil.toObjects(json, null);

        assertEquals(-1, (int) that._arrayElement);
        assertEquals(710, that._polyRefTarget);
        assertEquals(710, that._polyRef);
        assertEquals(710, that._polyNotRef);
        assertNotSame(that._polyRef, that._polyRefTarget);// Primitive wrappers are treated like primitives (no ref)
        assertNotSame(that._polyNotRef, that._polyRef);

        assertEquals(8, that._typeArray.length);
        assertSame(that._typeArray[0], that._arrayElement);
        assertTrue(that._typeArray[1] instanceof Integer);
        assertEquals(44, (int) that._typeArray[1]);
        assertEquals(8, that._objArray.length);
        assertSame(that._objArray[0], that._arrayElement);
        assertTrue(that._objArray[1] instanceof Integer);
        assertEquals(69, that._objArray[1]);
        assertTrue(that._polyRefTarget instanceof Integer);
        assertTrue(that._polyNotRef instanceof Integer);
        assertEquals(75, that._objArray[2]);

        assertSame(that._objArray[2], that._typeArray[2]);
        assertSame(that._typeArray[1], that._typeArray[5]);
        assertSame(that._objArray[1], that._objArray[5]);

        // an unreferenced 0 is cached
        assertSame(that._typeArray[6], that._typeArray[7]);
        assertSame(that._objArray[6], that._objArray[7]);

        assertNull(that._null);
        assertNull(that._typeArray[3]);
        assertNull(that._typeArray[4]);
        assertNull(that._objArray[3]);
        assertNull(that._objArray[4]);

        assertEquals(Integer.MIN_VALUE, (int) that._min);
        assertEquals(Integer.MAX_VALUE, (int) that._max);
    }

    private static class ManyIntegers implements Serializable
    {
        private ManyIntegers()
        {
            _arrayElement = -1;
            _polyRefTarget = 710;
            _polyRef = _polyRefTarget;
            _polyNotRef = 710;
            Integer local = 75;
            _null = null;
            _typeArray = new Integer[]{_arrayElement, 44, local, _null, null, 44, 0, 0};
            _objArray = new Object[]{_arrayElement, 69, local, _null, null, 69, 0, 0};
            _min = Integer.MIN_VALUE;
            _max = Integer.MAX_VALUE;
        }

        private final Integer _arrayElement;
        private final Integer[] _typeArray;
        private final Object[] _objArray;
        private final Object _polyRefTarget;
        private final Object _polyRef;
        private final Object _polyNotRef;
        private final Integer _min;
        private final Integer _max;
        private final Integer _null;
    }
}
