package com.cedarsoftware.util.io

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotEquals

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
@CompileStatic
class TestPrettyPrint
{
    static class Nice
    {
        private String name
        private Collection items
        private Map dictionary
    }

    @Test
    void testPrettyPrint()
    {
        Nice nice = new Nice()
        nice.name = "Louie"
        nice.items = new ArrayList()
        nice.items.add("One")
        nice.items.add(1L)
        nice.items.add(1)
        nice.items.add(true)
        nice.dictionary = new LinkedHashMap()
        nice.dictionary.put("grade", "A")
        nice.dictionary.put("price", 100.0d)
        nice.dictionary.put("bigdec", new BigDecimal("3.141592653589793238462643383"))

        String target = '''{
  "@type":"com.cedarsoftware.util.io.TestPrettyPrint$Nice",
  "name":"Louie",
  "items":{
    "@type":"java.util.ArrayList",
    "@items":[
      "One",
      1,
      {
        "@type":"int",
        "value":1
      },
      true
    ]
  },
  "dictionary":{
    "@type":"java.util.LinkedHashMap",
    "grade":"A",
    "price":100.0,
    "bigdec":{
      "@type":"java.math.BigDecimal",
      "value":"3.141592653589793238462643383"
    }
  }
}'''
        String json = JsonWriter.objectToJson(nice, [(JsonWriter.PRETTY_PRINT):true] as Map)
        json = json.replaceAll("[\\r]","");
        assertEquals(target, json)

        String json1 = JsonWriter.objectToJson(nice)
        json1 = json1.replaceAll("[\\r]","");
        assertNotEquals(json, json1)

        String json2 = JsonWriter.formatJson(json1)
        json2 = json2.replaceAll("[\\r]","");
        assertEquals(json2, json)
    }
}
