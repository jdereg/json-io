package com.cedarsoftware.io;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.cedarsoftware.util.FastByteArrayInputStream;
import com.cedarsoftware.util.FastByteArrayOutputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
public class NDJSONTest {
    @Test
    void testNDJSONwrite() throws Exception
    {
        TestObject to1 = new TestObject("one");
        TestObject to2 = new TestObject("two");
        to1._other = to2;
        to2._other = to1;

        FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
        WriteOptions writeOptions = new WriteOptionsBuilder().closeStream(false).build();
        JsonIo.toJson(fbaos, to1, writeOptions);
        fbaos.write("\n".getBytes(StandardCharsets.UTF_8));
        JsonIo.toJson(fbaos, to2, writeOptions);
        fbaos.close();
        String json = new String(fbaos.toByteArray(), StandardCharsets.UTF_8);
        TestUtil.assertContainsIgnoreCase(json, "TestObject", "one", "two", "TestObject", "two", "one");
        // Illustrating the capability of assertContainsIgnoreCase below
        TestUtil.assertContainsIgnoreCase(json, "TestObject", "one", "TestObject", "two");

        // Read NDJSON
        ReadOptions readOptions = new ReadOptionsBuilder().closeStream(false).build();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FastByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
        String json1 = bufferedReader.readLine();
        TestObject ta = JsonIo.toObjects(json1, readOptions, TestObject.class);
        
        assertThat(ta.getName()).isEqualTo("one");
        assertThat(ta._other.getName()).isEqualTo("two");
        assertNotEquals(ta._other, ta);
        assertSame(ta._other._other, ta);  // @id/@ref worked

        String json2 = bufferedReader.readLine();
        TestObject tb = JsonIo.toObjects(json2, readOptions, TestObject.class);

        assertThat(tb.getName()).isEqualTo("two");
        assertThat(tb._other.getName()).isEqualTo("one");
        assertNotEquals(tb._other, tb);
        assertSame(tb._other._other, tb);   // @id/@ref worked

        bufferedReader.close();
    }
}
