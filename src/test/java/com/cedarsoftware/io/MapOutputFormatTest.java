package com.cedarsoftware.io;

import java.util.LinkedHashMap;
import java.util.Map;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

/**
 * Test cases for JsonReader / JsonWriter
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class MapOutputFormatTest
{
    @Test
    public void testMapFormat()
    {
        Map<String, String> map1 = new LinkedHashMap<>(4);
        map1.put("a", "foo");
        map1.put("b", "bar");
        map1.put("c", "baz");
        map1.put("d", "qux");
        Map map = map1;
        String json1 = TestUtil.toJson(map, new WriteOptionsBuilder().forceMapOutputAsTwoArrays(true).build());
        String json2 = TestUtil.toJson(map, new WriteOptionsBuilder().forceMapOutputAsTwoArrays(false).build());
        assert !json1.equals(json2);
        assert json1.contains("@keys");
        assert json1.contains("@items");
        assert !json2.contains("@keys");
        assert !json2.contains("@items");
        Map map2 = TestUtil.toObjects(json2, null);
        assert DeepEquals.deepEquals(map, map2);
    }
}
