package com.cedarsoftware.util.io

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap

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
@CompileStatic
class TestInnerCollections
{
    @Test
    void testLinkedHashMap_LinkedKeySet()
    {
        keySetTest(new LinkedHashMap())
    }

    @Test
    void testHashMap_KeySet()
    {
        keySetTest(new HashMap<>())
    }

    @Test
    void testTreeMap_KeySet()
    {
        keySetTest(new TreeMap<>())
    }

    @Test
    void testConcurrentMap_KeySet()
    {
        keySetTest(new ConcurrentHashMap<>())
    }

    @Test
    void testSkipListMap_KeySet()
    {
        keySetTest(new ConcurrentSkipListMap<>())
    }

    @Test
    void testIdentityMap_KeySet()
    {
        keySetTest(new IdentityHashMap<>())
    }

    @Test
    void testLinkedHashMap_LinkedValues()
    {
        valuesTest(new LinkedHashMap<>())
    }

    @Test
    void testHashMap_Values()
    {
        valuesTest(new HashMap<>())
    }

    @Test
    void testTreeMap_Values()
    {
        valuesTest(new TreeMap<>())
    }

    @Test
    void testConcurrentMap_Values()
    {
        valuesTest(new ConcurrentHashMap<>())
    }

    @Test
    void testSkipListMap_Values()
    {
        valuesTest(new ConcurrentSkipListMap<>())
    }

    @Test
    void testIdentityMap_Values()
    {
        valuesTest(new IdentityHashMap<>())
    }

    static void keySetTest(Map map)
    {
        map.a = 'alpha'
        map.b = 'beta'
        map.c = 'charlie'
        String json = TestUtil.getJsonString(map.keySet())

        Set set = TestUtil.readJsonObject(json) as Set
        assert set.size() == 3
        assert set.contains('a')
        assert set.contains('b')
        assert set.contains('c')
    }

    static void valuesTest(Map map)
    {
        map.a = 'alpha'
        map.b = 'beta'
        map.c = 'charlie'
        String json = TestUtil.getJsonString(map.values())

        List list = TestUtil.readJsonObject(json) as List
        assert list.size() == 3
        assert list.contains('alpha')
        assert list.contains('beta')
        assert list.contains('charlie')
    }
}
