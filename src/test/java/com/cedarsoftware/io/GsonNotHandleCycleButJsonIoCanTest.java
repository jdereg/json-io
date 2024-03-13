package com.cedarsoftware.io;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

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
public class GsonNotHandleCycleButJsonIoCanTest
{
    static class Node
    {
        String name;
        Node next;

        Node(String name)
        {
            this.name = name;
        }
    }

    @Test
    public void testCycle()
    {
        Node alpha = new Node("alpha");
        Node beta = new Node("beta");
        alpha.next = beta;
        beta.next = alpha;

        // Google blows the stack when there is a cycle in the data

        assertThrows(StackOverflowError.class, () -> new Gson().toJson(alpha));

        // json-io handles cycles just fine.
        String json = TestUtil.toJson(alpha);
        Node a2 = (Node) TestUtil.toObjects(json, null);
        assert "alpha".equals(a2.name);
        Node b2 = a2.next;
        assert "beta".equals(b2.name);
        assert b2.next == a2;
        assert a2.next == b2;
    }
}
