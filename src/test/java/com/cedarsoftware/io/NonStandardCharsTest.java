package com.cedarsoftware.io;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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
public class NonStandardCharsTest
{
    /**
     * Use '-Dfile.encoding=ASCII' when running this test to ensure that json-io is correctly
     * handling everything internally as UTF-8.
     */
    @Test
    public void testNonUtf8()
    {
        TestUtil.printLine(Charset.availableCharsets().toString());
        TestUtil.printLine(Charset.defaultCharset().toString());

        String s1 = "\"Die gelbe Hölle\"";
        Object o = TestUtil.toObjects(s1, null);
        String s2 = TestUtil.toJson(o);
        TestUtil.printLine(s1);
        TestUtil.printLine(s2);
        assert s1.equals(s2);
    }

    @Test
    public void testBeerMug()
    {
        byte[] mug = new byte[] {(byte)0xf0, (byte)0x9f, (byte)0x8d, (byte)0xba};
        String beer = new String(mug, StandardCharsets.UTF_8);
        TestUtil.printLine("beer = " + beer);
        String thirsty = "{\"phrase\":\"I'd like a " + beer + "\"}";
        Map map = TestUtil.toObjects(thirsty, null);
        assert map.get("phrase").equals("I'd like a " + beer);
    }
}
