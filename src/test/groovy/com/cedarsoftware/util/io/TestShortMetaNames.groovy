package com.cedarsoftware.util.io

import com.cedarsoftware.util.DeepEquals
import org.junit.jupiter.api.Test

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
class TestShortMetaNames
{
    @Test
    void testShortMetaNames()
    {
        TestObject a = new TestObject("A")
        TestObject b = new TestObject("B")
        a._other = b
        b._other = a
        Map map = new LinkedHashMap();
        map.put(a, b);
        List list = [map]

        Map<String, String> shortNames = Map.of("java.util.ArrayList","al", "java.util.LinkedHashMap","lmap", (TestObject.class.getName()),"to");
        String json = TestUtil.toJson(list, new WriteOptionsBuilder().withShortMetaKeys().withCustomTypeNameMap(shortNames).build());
        List clone = (List) TestUtil.toJava(json, new ReadOptionsBuilder().withCustomTypeNameMap(shortNames).build())
        assert DeepEquals.deepEquals(list, clone)
    }
}
