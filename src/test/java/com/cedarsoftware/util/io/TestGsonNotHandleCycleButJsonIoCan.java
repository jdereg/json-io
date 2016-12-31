package com.cedarsoftware.util.io;

import com.google.gson.Gson;
import org.junit.Test;

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
public class TestGsonNotHandleCycleButJsonIoCan
{
    @Test
    public void testCycle()
    {
        TestObject alpha = new TestObject("alpha");
        TestObject beta = new TestObject("beta");
        alpha._other = beta;
        beta._other = alpha;

        // Google blows the stack when there is a cycle in the data
        try
        {
            Gson gson = new Gson();
            String json = gson.toJson(alpha);
            assert false;
        }
        catch(StackOverflowError e)
        {
            assert true;
        }

        // json-io handles cycles just fine.
        String json = JsonWriter.objectToJson(alpha);
        TestObject a2 = (TestObject) JsonReader.jsonToJava(json);
        assert "alpha".equals(a2.getName());
        TestObject b2 = a2._other;
        assert "beta".equals(b2.getName());
        assert b2._other == a2;
        assert a2._other == b2;
    }
}
