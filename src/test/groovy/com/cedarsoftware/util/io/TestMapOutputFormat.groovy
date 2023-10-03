package com.cedarsoftware.util.io

import com.cedarsoftware.util.DeepEquals
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

/**
 * Test cases for JsonReader / JsonWriter
 *
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
@CompileStatic
class TestMapOutputFormat
{
    @Test
    void testMapFormat()
    {
        Map map = ['a': 'foo', 'b': 'bar', 'c':'baz', 'd': 'qux']
        String json1 = JsonWriter.objectToJson(map, [(JsonWriter.FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS): true] as Map)
        String json2 = JsonWriter.objectToJson(map, [(JsonWriter.FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS): false] as Map)
        assert json1 != json2
        assert json1.contains('@keys')
        assert json1.contains('@items')
        assert !json2.contains('@keys')
        assert !json2.contains('@items')
        Map newMap = (Map) JsonReader.jsonToJava(json2)
        assert DeepEquals.deepEquals(map, newMap)
    }
}
