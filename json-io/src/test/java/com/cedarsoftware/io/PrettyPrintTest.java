package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.cedarsoftware.util.ClassUtilities;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
class PrettyPrintTest
{
    static class Nice
    {
        private String name;
        private Collection items;
        private Map dictionary;
    }

    @Test
    void testPrettyPrint()
    {
        Nice nice = new Nice();
        nice.name = "Louie";
        nice.items = new ArrayList<>();
        nice.items.add("One");
        nice.items.add(1L);
        nice.items.add(1);
        nice.items.add(true);
        nice.dictionary = new LinkedHashMap<>();
        nice.dictionary.put("grade", "A");
        nice.dictionary.put("price", 100.0d);
        nice.dictionary.put("bigdec", new BigDecimal("3.141592653589793238462643383"));

        String target = ClassUtilities.loadResourceAsString("format/prettyPrint.json");
        WriteOptions writeOptions = new WriteOptionsBuilder().prettyPrint(true).lruSize(25).build();
        String json = TestUtil.toJson(nice, writeOptions);

        assert JsonParser.parseString(json).equals(JsonParser.parseString(target));

        String json1 = TestUtil.toJson(nice);
        assertThat(json)
                .isNotEqualTo(json1)
                .isEqualToIgnoringWhitespace(json1);

        String json2 = JsonIo.formatJson(json1);
        assertThat(json2).isEqualToIgnoringNewLines(json);
    }
}
