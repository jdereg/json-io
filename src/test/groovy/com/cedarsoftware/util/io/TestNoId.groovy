package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertFalse
import static com.cedarsoftware.util.io.JsonObject.ID

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
class TestNoId
{
    static class NoId
    {
        protected Class<?> cls = LinkedList.class;
    }

    @Test
    void testShouldNotNeedId()
    {
        NoId noId = new NoId()
        String json = JsonWriter.objectToJson(noId)
        assertFalse(json.contains(ID))
    }

    @Test
    void testNoIdNeeded()
    {
        TestObject alpha = new TestObject('alpha')
        TestObject beta = new TestObject('beta')
        alpha._other = beta
        String json = TestUtil.getJsonString(alpha)
        assert !json.contains(ID)

        beta._other = alpha
        json = TestUtil.getJsonString(alpha)
        assert json.count(ID) == 1
    }
}
