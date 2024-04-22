package com.cedarsoftware.io;

import com.cedarsoftware.util.DeepEquals;
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
public class UntypedArrayTest
{
    @Test
    public void testArray()
    {
        ObjectArray obj = new ObjectArray();
        obj.init();
        String jsonOut = TestUtil.toJson(obj);
        TestUtil.printLine(jsonOut);

        ObjectArray root = TestUtil.toObjects(jsonOut, null);
        assert DeepEquals.deepEquals(root, obj);
    }

    public static class ObjectArray
    {
        public void init()
        {
            _arrayO = new Object[]{"foo", true, null, 16L, 3.14d};
        }

        private Object _arrayO;
    }
}
