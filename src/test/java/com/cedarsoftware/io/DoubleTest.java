package com.cedarsoftware.io;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
public class DoubleTest
{
    @Test
    public void testDouble()
    {
        ManyDoubles test = new ManyDoubles();
        String json = TestUtil.toJson(test);
        TestUtil.printLine("json = " + json);
        ManyDoubles that = TestUtil.toObjects(json, null);

        assertEquals(-1.0d, that._arrayElement);
        assertEquals(71.0d, that._polyRefTarget);
        assertEquals(71.0d, that._polyRef);
        assertEquals(71.0d, that._polyNotRef);
        assertSame(that._polyRef, that._polyRefTarget);
        assertSame(that._polyNotRef, that._polyRef);

        assertEquals(6, that._typeArray.length);
        assertNotNull(that._typeArray[1]);
        assertTrue(that._objArray[1] instanceof Double);
        assertEquals(44.0d, that._typeArray[1]);
        assertEquals(6, that._objArray.length);
        assertEquals(69.0d, that._objArray[1]);
        assertTrue(that._polyRefTarget instanceof Double);
        assertTrue(that._polyNotRef instanceof Double);

        assertNull(that._null);
        assertNull(that._typeArray[3]);
        assertNull(that._typeArray[4]);
        assertNull(that._objArray[3]);
        assertNull(that._objArray[4]);

        assertEquals(Double.MIN_VALUE, that._min);
        assertEquals(Double.MAX_VALUE, that._max);
    }

    @Test
    void testNanAsRoot()
    {
        WriteOptions args = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = TestUtil.toJson(Double.NaN, args);
        assert json.contains("null");

        json = TestUtil.toJson(Double.NEGATIVE_INFINITY, args);
        assert json.contains("null");

        json = TestUtil.toJson(Double.POSITIVE_INFINITY, args);
        assert json.contains("null");
    }

    @Test
    void testNanMapKey()
    {
        WriteOptions args = new WriteOptionsBuilder().showTypeInfoNever().build();
        LinkedHashMap<String, Double> map = new LinkedHashMap<String, Double>(1);
        map.put("field", Double.NaN);
        String json = TestUtil.toJson(map, args);
        assert json.contains("null");

        LinkedHashMap<String, Double> map1 = new LinkedHashMap<String, Double>(1);
        map1.put("field", Double.NEGATIVE_INFINITY);
        json = TestUtil.toJson(map1, args);
        assert json.contains("null");

        LinkedHashMap<String, Double> map2 = new LinkedHashMap<String, Double>(1);
        map2.put("field", Double.POSITIVE_INFINITY);
        json = TestUtil.toJson(map2, args);
        assert json.contains("null");
    }

    @Test
    void testNanObjectField()
    {
        DoubleHolder holder = new DoubleHolder();
        holder.number = Double.NaN;

        WriteOptions args = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = TestUtil.toJson(holder, args);
        assert json.contains("null");

        holder.number = Double.NEGATIVE_INFINITY;
        json = TestUtil.toJson(holder, args);
        assert json.contains("null");

        holder.number = Double.POSITIVE_INFINITY;
        json = TestUtil.toJson(holder, args);
        assert json.contains("null");
    }

    @Test
    public void testNanArrayElement()
    {
        WriteOptions args = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = TestUtil.toJson(new ArrayList<>(listOf(Double.NaN)), args);
        assert json.contains("null");

        json = TestUtil.toJson(new ArrayList<>(listOf(Double.NEGATIVE_INFINITY)), args);
        assert json.contains("null");

        LinkedHashMap<String, Double> map2 = new LinkedHashMap<>(1);
        map2.put("field", Double.POSITIVE_INFINITY);
        json = TestUtil.toJson(map2, args);
        assert json.contains("null");
    }

    @Test
    public void testNanAsRoot2()
    {
        String json = TestUtil.toJson(Double.NaN);
        assert json.contains("null");

        json = TestUtil.toJson(Double.NEGATIVE_INFINITY);
        assert json.contains("null");

        json = TestUtil.toJson(Double.POSITIVE_INFINITY);
        assert json.contains("null");
    }

    @Test
    public void testNanMapKey2()
    {
        LinkedHashMap<String, Double> map = new LinkedHashMap<String, Double>(1);
        map.put("field", Double.NaN);
        String json = TestUtil.toJson(map);
        assert json.contains("null");

        LinkedHashMap<String, Double> map1 = new LinkedHashMap<String, Double>(1);
        map1.put("field", Double.NEGATIVE_INFINITY);
        json = TestUtil.toJson(map1);
        assert json.contains("null");

        LinkedHashMap<String, Double> map2 = new LinkedHashMap<String, Double>(1);
        map2.put("field", Double.POSITIVE_INFINITY);
        json = TestUtil.toJson(map2);
        assert json.contains("null");
    }

    @Test
    void testNanObjectField2()
    {
        DoubleHolder holder = new DoubleHolder();
        holder.number = Double.NaN;
        String json = TestUtil.toJson(holder);
        assert json.contains("null");

        holder.number = Double.NEGATIVE_INFINITY;
        json = TestUtil.toJson(holder);
        assert json.contains("null");

        holder.number = Double.POSITIVE_INFINITY;
        json = TestUtil.toJson(holder);
        assert json.contains("null");
    }

    @Test
    void testNanArrayElement2()
    {
        String json = TestUtil.toJson(new ArrayList<>(listOf(Double.NaN)));
        assertThat(json).contains("null");

        json = TestUtil.toJson(new ArrayList<>(listOf(Double.NEGATIVE_INFINITY)));
        assert json.contains("null");

        LinkedHashMap<String, Double> map = new LinkedHashMap<>(1);
        map.put("field", Double.POSITIVE_INFINITY);
        json = TestUtil.toJson(map);
        assert json.contains("null");
    }

    private static class ManyDoubles implements Serializable
    {
        private ManyDoubles()
        {
            _arrayElement = (double) -1;
            _polyRefTarget = 71.0;
            _polyRef = _polyRefTarget;
            _polyNotRef = 71.0;
            double local = 75.0;
            _null = null;
            _typeArray = new Double[]{_arrayElement, 44.0d, local, _null, null, 44.0};
            _objArray = new Object[]{_arrayElement, 69.0d, local, _null, null, 69.0};
            _min = Double.MIN_VALUE;
            _max = Double.MAX_VALUE;
        }

        private final Double _arrayElement;
        private final Double[] _typeArray;
        private final Object[] _objArray;
        private final Object _polyRefTarget;
        private final Object _polyRef;
        private final Object _polyNotRef;
        private final Double _min;
        private final Double _max;
        private final Double _null;
    }

    private static class DoubleHolder
    {
        protected double number;
    }
}
