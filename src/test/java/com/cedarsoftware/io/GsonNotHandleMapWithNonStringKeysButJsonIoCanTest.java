package com.cedarsoftware.io;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
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

public class GsonNotHandleMapWithNonStringKeysButJsonIoCanTest
{
    @Test
    public void testMapWithNonStringKeys()
    {
        Map<Point, String> map = new LinkedHashMap<Point, String>();    // Parameterized types of <Point, String> supplied

        Point pt1 = new Point(1, 10);
        map.put(pt1, "one");
        Point pt2 = new Point(2, 20);
        map.put(pt2, "two");
        Point pt3 = new Point(3, 30);
        map.put(pt3, "three");

        // ------------------------ gson ------------------------
        Gson gson = new Gson();
        String json = gson.toJson(map);
        Map newMap = gson.fromJson(json, Map.class);

        assert newMap.size() == 3;
        assert !newMap.containsKey(pt1);                                // fail, pt1 not found
        assert !(newMap.keySet().iterator().next() instanceof Point);   // fail, keys should be Point instances.
        assert newMap.keySet().iterator().next() instanceof String;     // fail, keys turned into Strings.

        // ------------------------ json-io ------------------------
        json = TestUtil.toJson(map);
        newMap = (Map) TestUtil.toObjects(json, null);

        assert newMap.size() == 3;
        assert newMap.containsKey(pt1);                                // success, pt1 not found
        assert newMap.keySet().iterator().next() instanceof Point;     // success, keys are Points
    }
}
