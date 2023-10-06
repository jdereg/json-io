package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

import java.sql.Timestamp

import static org.junit.jupiter.api.Assertions.assertFalse

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
class TestMetaUtils
{
    @Test
    void testFillArgs()
    {
        Object[] ret = MetaUtils.fillArgs([Set.class] as Class[], false)
        assert ret[0] instanceof Set
        assertFalse ret[0] instanceof SortedSet

        ret = MetaUtils.fillArgs([SortedSet.class] as Class[], false)
        assert ret[0] instanceof SortedSet

        ret = MetaUtils.fillArgs([SortedMap.class] as Class[], false)
        assert ret[0] instanceof SortedMap

        ret = MetaUtils.fillArgs([Collection.class] as Class[], false)
        assert ret[0] instanceof Collection

        ret = MetaUtils.fillArgs([Calendar.class] as Class[], false)
        assert ret[0] instanceof Calendar

        ret = MetaUtils.fillArgs([TimeZone.class] as Class[], false)
        assert ret[0] instanceof TimeZone

        ret = MetaUtils.fillArgs([BigInteger.class] as Class[], false)
        assert ret[0] instanceof BigInteger

        ret = MetaUtils.fillArgs([BigDecimal.class] as Class[], false)
        assert ret[0] instanceof BigDecimal

        ret = MetaUtils.fillArgs([StringBuilder.class] as Class[], false)
        assert ret[0] instanceof StringBuilder

        ret = MetaUtils.fillArgs([StringBuffer.class] as Class[], false)
        assert ret[0] instanceof StringBuffer

        ret = MetaUtils.fillArgs([Locale.class] as Class[], false)
        assert ret[0] instanceof Locale

        ret = MetaUtils.fillArgs([Timestamp.class] as Class[], false)
        assert ret[0] instanceof Timestamp

        ret = MetaUtils.fillArgs([java.sql.Date.class] as Class[], false)
        assert ret[0] instanceof java.sql.Date

        ret = MetaUtils.fillArgs([Object.class] as Class[], false)
        assert ret[0] instanceof Object

        ret = MetaUtils.fillArgs([Class.class] as Class[], false)
    }

    @Test
    void testNewPrimitiveWrapper()
    {
        try
        {
            MetaUtils.convert(TimeZone.class, "")
        }
        catch (JsonIoException e)
        {
            assert e.message.toLowerCase().contains('not have primitive wrapper')
        }

        try
        {
            MetaUtils.convert(Float.class, "float")
        }
        catch (JsonIoException e)
        {
            assert e.message.toLowerCase().contains('error creating primitive wrapper')
        }

        assert 'G' as char == MetaUtils.convert(Character.class, 'G' as char)
    }

    @Test
    void tryOtherConstructors()
    {
        try
        {
            MetaUtils.tryOtherConstruction(Byte.TYPE)
        }
        catch (JsonIoException e)
        {
            assert e.message.toLowerCase().contains('cannot instantiate')
            assert e.message.toLowerCase().contains('byte')
        }
    }

    @Test
    void testGetDistance()
    {
        int x = MetaUtils.getDistance(Serializable.class, Externalizable.class)
        assert x == 1

        x = MetaUtils.getDistance(Externalizable.class, Serializable.class)
        assert x == Integer.MAX_VALUE
    }

    @Test
    void testLoggingMessage()
    {
        Map data = ['a': 'Alpha', 'b': 'Bravo', 'car': 'McLaren 675LT', 'pi': 3.1415926535897932384]
        String methodName = 'blame'
        Object[] args = [17, 34.5, data]
        String msg = MetaUtils.getLogMessage(methodName, args)
        assert msg == 'blame({"value":17}  "34.5"  {"a":"Alpha","b":"Bravo","car":"McLaren 675LT","pi":"3.141592653...)'

        msg = MetaUtils.getLogMessage(methodName, args, 500)
        assert msg == 'blame({"value":17}  "34.5"  {"a":"Alpha","b":"Bravo","car":"McLaren 675LT","pi":"3.1415926535897932384"})'
    }
}
