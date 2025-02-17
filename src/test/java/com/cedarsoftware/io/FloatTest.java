package com.cedarsoftware.io;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
public class FloatTest
{
    @Test
    public void testFloat()
    {
        ManyFloats test = new ManyFloats();
        String json = TestUtil.toJson(test);
        TestUtil.printLine("json = " + json);
        ManyFloats that = TestUtil.toObjects(json, null);

        assertEquals(-1.0f, that._arrayElement);
        assertEquals(71.0f, that._polyRefTarget);
        assertEquals(71.0f, that._polyRef);
        assertEquals(71.0f, that._polyNotRef);
        assertNotSame(that._polyRef, that._polyRefTarget);// Primitive wrappers are treated like primitives (no ref)
        assertNotSame(that._polyNotRef, that._polyRef);

        assertEquals(6, that._typeArray.length);
        assertNotNull(that._typeArray[1]);
        assertTrue(that._objArray[1] instanceof Float);
        assertEquals(44.0f, that._typeArray[1]);
        assertEquals(6, that._objArray.length);
        assertEquals(69.0f, that._objArray[1]);
        assertTrue(that._polyRefTarget instanceof Float);
        assertTrue(that._polyNotRef instanceof Float);

        assertEquals(that._objArray[2], that._typeArray[2]);
        assertNotSame(that._typeArray[1], that._typeArray[5]);
        assertEquals(that._typeArray[1], that._typeArray[5], 0.00001f);

        assertNotSame(that._objArray[1], that._objArray[5]);
        assertEquals((float) that._objArray[1], (float) that._objArray[5], 0.00001f);

        assertNotSame(that._typeArray[1], that._objArray[1]);
        assertNotEquals((float) that._typeArray[1], (float) that._objArray[1], 0.00001f);

        assertNotSame(that._typeArray[5], that._objArray[5]);
        assertNotEquals((float) that._typeArray[5], (float) that._objArray[5], 0.00001f);

        assertNull(that._null);
        assertNull(that._typeArray[3]);
        assertNull(that._typeArray[4]);
        assertNull(that._objArray[3]);
        assertNull(that._objArray[4]);

        assertEquals(Float.MIN_VALUE, that._min);
        assertEquals(Float.MAX_VALUE, that._max);
    }

    @Test
    public void parseBadFloat()
    {
        String json = "[123.45.67]";

        try
        {
            TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
            fail();
        }
        catch (JsonIoException ignore)
        { }
    }

    @Test
    public void testNanAsRoot()
    {
        WriteOptions args = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = TestUtil.toJson(Float.NaN, args);
        assert json.contains("null");

        json = TestUtil.toJson(Float.NEGATIVE_INFINITY, args);
        assert json.contains("null");

        json = TestUtil.toJson(Float.POSITIVE_INFINITY, args);
        assert json.contains("null");
    }

    @Test
    public void testNanMapKey()
    {
        WriteOptions args = new WriteOptionsBuilder().showTypeInfoNever().build();
        LinkedHashMap<String, Float> map = new LinkedHashMap<>(1);
        map.put("field", Float.NaN);
        String json = TestUtil.toJson(map, args);
        assert json.contains("null");

        LinkedHashMap<String, Float> map1 = new LinkedHashMap<>(1);
        map1.put("field", Float.NEGATIVE_INFINITY);
        json = TestUtil.toJson(map1, args);
        assert json.contains("null");

        LinkedHashMap<String, Float> map2 = new LinkedHashMap<>(1);
        map2.put("field", Float.POSITIVE_INFINITY);
        json = TestUtil.toJson(map2, args);
        assert json.contains("null");
    }

    @Test
    public void testNanObjectField()
    {
        Simple holder = new Simple();
        holder.setX(Float.NaN);

        WriteOptions args = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = TestUtil.toJson(holder, args);
        assert json.contains("null");

        holder.setX(Float.NEGATIVE_INFINITY);
        json = TestUtil.toJson(holder, args);
        assert json.contains("null");

        holder.setX(Float.POSITIVE_INFINITY);
        json = TestUtil.toJson(holder, args);
        assert json.contains("null");
    }

    @Test
    public void testNanArrayElement()
    {
        WriteOptions args = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = TestUtil.toJson(new ArrayList<>(listOf(Float.NaN)), args);
        assert json.contains("null");

        json = TestUtil.toJson(new ArrayList<>(listOf(Float.NEGATIVE_INFINITY)), args);
        assert json.contains("null");

        LinkedHashMap<String, Float> map = new LinkedHashMap<>(1);
        map.put("field", Float.POSITIVE_INFINITY);
        json = TestUtil.toJson(map, args);
        assert json.contains("null");
    }

    @Test
    public void testNanAsRoot2()
    {
        String json = TestUtil.toJson(Float.NaN);
        assert json.contains("null");

        json = TestUtil.toJson(Float.NEGATIVE_INFINITY);
        assert json.contains("null");

        json = TestUtil.toJson(Float.POSITIVE_INFINITY);
        assert json.contains("null");
    }

    @Test
    public void testNanMapKey2()
    {
        LinkedHashMap<String, Float> map = new LinkedHashMap<>(1);
        map.put("field", Float.NaN);
        String json = TestUtil.toJson(map);
        assert json.contains("null");

        LinkedHashMap<String, Float> map1 = new LinkedHashMap<>(1);
        map1.put("field", Float.NEGATIVE_INFINITY);
        json = TestUtil.toJson(map1);
        assert json.contains("null");

        LinkedHashMap<String, Float> map2 = new LinkedHashMap<>(1);
        map2.put("field", Float.POSITIVE_INFINITY);
        json = TestUtil.toJson(map2);
        assert json.contains("null");
    }

    @Test
    public void testNanObjectField2()
    {
        Simple holder = new Simple();
        holder.setX(Float.NaN);
        String json = TestUtil.toJson(holder);
        assert json.contains("null");

        holder.setX(Float.NEGATIVE_INFINITY);
        json = TestUtil.toJson(holder);
        assert json.contains("null");

        holder.setX(Float.POSITIVE_INFINITY);
        json = TestUtil.toJson(holder);
        assert json.contains("null");
    }

    @Test
    public void testNanArrayElement2()
    {
        WriteOptions options = new WriteOptionsBuilder().allowNanAndInfinity(false).build();
        String json = TestUtil.toJson(listOf(Float.NaN), options);
        assert json.contains("null");

        json = TestUtil.toJson(listOf(Float.NEGATIVE_INFINITY), options);
        assert json.contains("null");

        LinkedHashMap<String, Float> map = new LinkedHashMap<>(1);
        map.put("field", Float.POSITIVE_INFINITY);
        json = TestUtil.toJson(map, options);
        assert json.contains("null");
    }

    private static class Simple
    {
        public float getX()
        {
            return x;
        }

        public void setX(float x)
        {
            this.x = x;
        }

        private float x;
    }

    private static class ManyFloats implements Serializable
    {
        private ManyFloats()
        {
            _arrayElement = (float) -1;
            _polyRefTarget = 71F;
            _polyRef = _polyRefTarget;
            _polyNotRef = 71F;
            Float local = 75F;
            _null = null;
            _typeArray = new Float[]{_arrayElement, 44f, local, _null, null, 44f};
            _objArray = new Object[]{_arrayElement, 69f, local, _null, null, 69f};
            _min = Float.MIN_VALUE;
            _max = Float.MAX_VALUE;
        }

        private final Float _arrayElement;
        private final Float[] _typeArray;
        private final Object[] _objArray;
        private final Object _polyRefTarget;
        private final Object _polyRef;
        private final Object _polyNotRef;
        private final Float _min;
        private final Float _max;
        private final Float _null;
    }
}
