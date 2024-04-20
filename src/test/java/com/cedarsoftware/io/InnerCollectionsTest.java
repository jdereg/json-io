package com.cedarsoftware.io;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.junit.jupiter.api.Test;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
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
public class InnerCollectionsTest
{
    @Test
    public void testLinkedHashMap_LinkedKeySet()
    {
        keySetTest(new LinkedHashMap<>());
    }

    @Test
    public void testHashMap_KeySet()
    {
        keySetTest(new HashMap<>());
    }

    @Test
    public void testTreeMap_KeySet()
    {
        keySetTest(new TreeMap<>());
    }

    @Test
    public void testConcurrentMap_KeySet()
    {
        keySetTest(new ConcurrentHashMap<>());
    }

    @Test
    public void testSkipListMap_KeySet()
    {
        keySetTest(new ConcurrentSkipListMap<>());
    }

    @Test
    public void testIdentityMap_KeySet()
    {
        keySetTest(new IdentityHashMap<>());
    }

    @Test
    public void testLinkedHashMap_LinkedValues()
    {
        valuesTest(new LinkedHashMap<>());
    }

    @Test
    public void testHashMap_Values()
    {
        valuesTest(new HashMap<>());
    }

    @Test
    public void testTreeMap_Values()
    {
        valuesTest(new TreeMap<>());
    }

    @Test
    public void testConcurrentMap_Values()
    {
        valuesTest(new ConcurrentHashMap<>());
    }

    @Test
    public void testSkipListMap_Values()
    {
        valuesTest(new ConcurrentSkipListMap<>());
    }

    @Test
    public void testIdentityMap_Values()
    {
        valuesTest(new IdentityHashMap<>());
    }

    public static void keySetTest(Map map)
    {
        map.put("a", "alpha");
        map.put("b", "beta");
        map.put("c", "charlie");
        String json = TestUtil.toJson(map.keySet());

        Set set = TestUtil.toObjects(json, null);
        assert set.size() == 3;
        assert set.contains("a");
        assert set.contains("b");
        assert set.contains("c");
    }

    public static void valuesTest(Map map)
    {
        map.put("a", "alpha");
        map.put("b", "beta");
        map.put("c", "charlie");
        String json = TestUtil.toJson(map.values());

        List list = TestUtil.toObjects(json, null);
        assert list.size() == 3;
        assert list.contains("alpha");
        assert list.contains("beta");
        assert list.contains("charlie");
    }
}
