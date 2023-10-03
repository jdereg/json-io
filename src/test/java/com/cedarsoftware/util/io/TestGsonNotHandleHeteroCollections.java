package com.cedarsoftware.util.io;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
public class TestGsonNotHandleHeteroCollections
{
    static class Node
    {
        String name;
        TestGsonNotHandleCycleButJsonIoCan.Node next;

        Node(String name)
        {
            this.name = name;
        }
    }

    @Test
    public void testGsonFailOnHeteroCollection()
    {
        List list = new ArrayList();
        list.add(1);
        list.add(42L);
        list.add(Math.PI);
        list.add(new Node("Bitcoin"));
        Gson gson = new Gson();
        String json = gson.toJson(list);
        List newList = gson.fromJson(json, List.class);

        // ---------------------------- gson fails ----------------------------
        assert !(newList.get(0) instanceof Integer); // Fail - Integer 1 becomes 1.0 (double)
        assert !(newList.get(1) instanceof Long); // FAIL - Long 42 becomes 42.0 (double))
        assert newList.get(2) instanceof Double;
        assert !(newList.get(3) instanceof Node);   // Fail, last element (Node) converted to Map
        Map map = (Map) newList.get(3);
        assert "Bitcoin".equals(map.get("name"));

        // ---------------------------- json-io maintains types ----------------------------
        json = JsonWriter.objectToJson(list);
        newList = (List) JsonReader.jsonToJava(json);

        assert newList.get(0) instanceof Integer;
        assert newList.get(1) instanceof Long;
        assert newList.get(2) instanceof Double;
        assert newList.get(3) instanceof Node;
        Node testObj = (Node) newList.get(3);
        assert "Bitcoin".equals(testObj.name);
    }
}
