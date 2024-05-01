package com.cedarsoftware.io;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.MapUtilities.mapOf;
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
public class ShortMetaNamesTest
{
    @Test
    public void testShortMetaNames()
    {
        TestObject a = new TestObject("A");
        TestObject b = new TestObject("B");
        a._other = b;
        b._other = a;

        Map<String, String> shortNames = mapOf("java.util.ArrayList", "al", "java.util.LinkedHashMap", "lmap");

        new WriteOptionsBuilder().aliasTypeNames(mapOf(TestObject.class.getName(), "TO"));
        assertThrows(IllegalArgumentException.class, () -> new WriteOptionsBuilder().aliasTypeNames(shortNames));
    }
}
