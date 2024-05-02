package com.cedarsoftware.io;

import java.math.BigInteger;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * Last test to run, deals with static data and dumps out statistics.
 *
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
class zzLastTest
{
    @Test
    void testRemovingAliases()
    {
        Object[] objects = new Object[] {
                "Hello",
                16,
                16L,
                (byte) 16,
                (short) 16,
                Arrays.asList("foo", true, 'a', BigInteger.ONE)
        };
        String json = TestUtil.toJson(objects);
        WriteOptionsBuilder.removeAliasedClassNamesMatching("j*a?lang.*");
        String json2 = TestUtil.toJson(objects);

        assert json.contains("Integer") && !json.contains("java.lang.Integer");
        assert json.contains("Byte") && !json.contains("java.lang.Byte");
        assert json.contains("BigInteger") && !json.contains("java.math.BigInteger");
        assert json.contains("ArraysAsList") && !json.contains("java.util.Arrays$ArrayList");

        assert json2.contains("java.lang.Integer");
        assert json2.contains("java.lang.Byte");
        assert !json2.contains("java.math.BigInteger");
        assert !json2.contains("java.util.Arrays$ArrayList");

        WriteOptionsBuilder.removeAliasedClassNamesMatching("*");
        json2 = TestUtil.toJson(objects);
        assert json2.contains("java.math.BigInteger");
        assert json2.contains("java.util.Arrays$ArrayList");
    }

    @Test
    void testZTimings()
    {
        TestUtil.writeTimings();
    }
}
