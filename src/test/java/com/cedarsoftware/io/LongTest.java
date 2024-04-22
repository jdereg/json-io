package com.cedarsoftware.io;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
public class LongTest
{
    @Test
    public void testLong()
    {
        ManyLongs test = new ManyLongs();
        String json = TestUtil.toJson(test);
        TestUtil.printLine("json = " + json);
        ManyLongs that = TestUtil.toObjects(json, null);

        Assertions.assertEquals(-1L, (long) that._arrayElement);
        Assertions.assertEquals(710L, that._polyRefTarget);
        Assertions.assertEquals(710L, that._polyRef);
        Assertions.assertEquals(710L, that._polyNotRef);
        Assertions.assertSame(that._polyRef, that._polyRefTarget);
        Assertions.assertSame(that._polyNotRef, that._polyRef);

        Assertions.assertEquals(6, that._typeArray.length);
        Assertions.assertSame(that._typeArray[0], that._arrayElement);
        Assertions.assertTrue(that._typeArray[1] instanceof Long);
        Assertions.assertTrue(that._typeArray[1] instanceof Long);
        Assertions.assertEquals(44L, (long) that._typeArray[1]);
        Assertions.assertEquals(6, that._objArray.length);
        Assertions.assertSame(that._objArray[0], that._arrayElement);
        Assertions.assertTrue(that._objArray[1] instanceof Long);
        Assertions.assertEquals(69L, that._objArray[1]);
        Assertions.assertTrue(that._polyRefTarget instanceof Long);
        Assertions.assertTrue(that._polyNotRef instanceof Long);

        Assertions.assertSame(that._objArray[2], that._typeArray[2]);
        Assertions.assertSame(that._typeArray[1], that._typeArray[5]);
        Assertions.assertSame(that._objArray[1], that._objArray[5]);

        Assertions.assertNull(that._null);
        Assertions.assertNull(that._typeArray[3]);
        Assertions.assertNull(that._typeArray[4]);
        Assertions.assertNull(that._objArray[3]);
        Assertions.assertNull(that._objArray[4]);

        Assertions.assertEquals(Long.MIN_VALUE, (long) that._min);
        Assertions.assertEquals(Long.MAX_VALUE, (long) that._max);
    }

    @Test
    public void testLongAsString()
    {
        long x = 19;
        WriteOptions options = new WriteOptionsBuilder().writeLongsAsStrings(true).build();

        String json = TestUtil.toJson(x, options);
        assert json.contains("\"19\"");

        Object y = TestUtil.toObjects(json, null);
        assert y instanceof Long;
        Assertions.assertEquals(19L, (Long) y);
    }

    @Test
    public void testLongArrayAsString()
    {
        Long[] x = new Long[]{1L, 2L, 3L};
        WriteOptions options = new WriteOptionsBuilder().writeLongsAsStrings(true).build();

        String json = TestUtil.toJson(x, options);
        assert json.contains("\"1\"");
        assert json.contains("\"2\"");
        assert json.contains("\"3\"");

        Object y = TestUtil.toObjects(json, null);
        assert y instanceof Long[];
        Long[] longArray = (Long[]) y;
        Assertions.assertEquals(1L, longArray[0]);
        Assertions.assertEquals(2L, longArray[1]);
        Assertions.assertEquals(3L, longArray[2]);
    }

    @Test
    public void testObjectArrayOfLongsAsString()
    {
        Object[] x = new Object[]{1L, 2L, 3L};
        WriteOptions options = new WriteOptionsBuilder().writeLongsAsStrings(true).build();
        String json = TestUtil.toJson(x, options);
        assert json.contains("\"1\"");
        assert json.contains("\"2\"");
        assert json.contains("\"3\"");

        Object y = TestUtil.toObjects(json, null);
        assert y instanceof Object[];
        Object[] objArray = (Object[])y;
        Assertions.assertEquals(1L, objArray[0]);
        Assertions.assertEquals(2L, objArray[1]);
        Assertions.assertEquals(3L, objArray[2]);
    }

    @Test
    public void testLongCollectionAsString()
    {
        Collection<Long> x = new ArrayList<>();
        x.add(1L);
        x.add(2L);
        x.add(3L);
        WriteOptions options = new WriteOptionsBuilder().writeLongsAsStrings(true).build();
        String json = TestUtil.toJson(x, options);

        assert json.contains("\"1\"");
        assert json.contains("\"2\"");
        assert json.contains("\"3\"");

        Object y = TestUtil.toObjects(json, null);
        assert y instanceof Collection;
        assert y instanceof List;
        List<Long> col = (List<Long>)y;
        Assertions.assertEquals(1L, col.get(0));
        Assertions.assertEquals(2L, col.get(1));
        Assertions.assertEquals(3L, col.get(2));
    }

    @Test
    public void testLongObjectFieldAsString()
    {
        PhysicalAttributes x = new PhysicalAttributes();
        x.setAge(49L);
        x.setWeight(205L);

        WriteOptions options = new WriteOptionsBuilder().writeLongsAsStrings(true).build();
        String json = TestUtil.toJson(x, options);

        assertThat(json)
                .contains("\"49\"")
                .contains("\"205\"");

        Object y = TestUtil.toObjects(json, null);
        assert y instanceof PhysicalAttributes;
        Assertions.assertEquals(49L, ((PhysicalAttributes) y).getAge());
        Assertions.assertEquals(205L, ((PhysicalAttributes) y).getWeight());
    }

    private static class ManyLongs implements Serializable
    {
        private ManyLongs()
        {
            _arrayElement = -1L;
            _polyRefTarget = 710L;
            _polyRef = _polyRefTarget;
            _polyNotRef = 710L;
            Long local = 75L;
            _null = null;
            // 44 below is between -128 and 127, values cached by     Long Long.valueOf(long l)
            _typeArray = new Long[]{_arrayElement, 44L, local, _null, null, 44L};
            _objArray = new Object[]{_arrayElement, 69L, local, _null, null, 69L};
            _min = Long.MIN_VALUE;
            _max = Long.MAX_VALUE;
        }

        private final Long _arrayElement;
        private final Long[] _typeArray;
        private final Object[] _objArray;
        private final Object _polyRefTarget;
        private final Object _polyRef;
        private final Object _polyNotRef;
        private final Long _min;
        private final Long _max;
        private final Long _null;
    }

    private static class PhysicalAttributes
    {
        public Long getAge()
        {
            return age;
        }

        public void setAge(Long age)
        {
            this.age = age;
        }

        public Object getWeight()
        {
            return weight;
        }

        public void setWeight(Object weight)
        {
            this.weight = weight;
        }

        private Long age;
        private Object weight;
    }
}
