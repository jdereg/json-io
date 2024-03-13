package com.cedarsoftware.io;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
public class GsonNotHandleStaticInnerButJsonIoCanTest
{
    public class A
    {
        public String a;

        class B
        {

            public String b;

            public B()
            {
                // No args constructor for B
            }
        }
    }

    @Test
    public void testInner()
    {
        A a = new A();
        a.a = "Tesla";

        String json = TestUtil.toJson(a);
        TestUtil.printLine("json = " + json);
        A o1 = (A) TestUtil.toObjects(json, null);
        assertTrue(o1.a.equals("Tesla"));

        A.B b = a.new B();
        b.b = "Elon Musk";
        json = TestUtil.toJson(b);
        TestUtil.printLine("json = " + json);
        A.B b1 = (A.B) TestUtil.toObjects(json, null);
        assertTrue(b1.b.equals("Elon Musk"));

        // gson fail
        Gson gson = new Gson();
        String x = gson.toJson(a);
        assert !x.contains("b");    // inner B dropped
    }
}
