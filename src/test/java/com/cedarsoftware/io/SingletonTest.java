package com.cedarsoftware.io;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

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
public class SingletonTest
{
    @Test
    public void testSingletonListSerialization()
    {
        List<String> strings = Collections.singletonList("Foo");
        // Simple object with a list field, behaves the same with a class

        // Serialize foo with a singletonList, then deserialization throws
        String json = TestUtil.toJson(strings, new WriteOptionsBuilder().build());
        Object o = TestUtil.toObjects(json, null);
    }

    @Test
    public void testSingletonSetSerialization()
    {
        Set<String> strings = Collections.singleton("One time");
        // Simple object with a list field, behaves the same with a class

        // Serialize foo with a singletonList, then deserialization throws
        String json = TestUtil.toJson(strings, new WriteOptionsBuilder().build());
        Object o = TestUtil.toObjects(json, null);
    }

    @Test
    public void testSingletonMapSerialization()
    {
        Map<String, Double> strings = Collections.singletonMap("bitcoin", 35000.0);
        // Simple object with a list field, behaves the same with a class

        // Serialize foo with a singletonList, then deserialization throws
        String json = TestUtil.toJson(strings, new WriteOptionsBuilder().build());
        Object o = TestUtil.toObjects(json, null);
    }
}