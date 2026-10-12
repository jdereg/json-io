package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static com.cedarsoftware.util.MapUtilities.mapOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A list item that is an object carries its first field on the hyphen line, and that field's key may need quotes:
 * {@code - "my key": 1} (TOON spec 10). The reader took any hyphen line that starts with a quote for a string, so it
 * could not read back what json-io itself writes for a list of maps whose first key holds a space, a hyphen or a
 * leading digit: strict mode failed, and non-strict mode returned the line as a string and lost the rest of the
 * list.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
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
class ToonListItemQuotedKeyTest {

    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictToon(true).build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().strictToon(false).build();
    private static final ReadOptions[] BOTH_MODES = {STRICT, LENIENT};
    private static final WriteOptions PLAIN_JSON = new WriteOptionsBuilder().showTypeInfoNever().build();

    static class Request {
        String name;
        List<Map<String, Object>> headers;
    }

    private static Object read(String toon, ReadOptions options) {
        return JsonIo.fromToon(toon, options).asClass(Object.class);
    }

    /** The value as JSON without type information: what was read, whatever Java classes hold it. */
    private static String json(Object value) {
        return JsonIo.toJson(value, PLAIN_JSON);
    }

    private static void assertReads(Object expected, String toon) {
        for (ReadOptions options : BOTH_MODES) {
            String mode = options.isStrictToon() ? "strict" : "non-strict";
            assertEquals(json(expected), json(read(toon, options)), mode + "\n" + toon);
        }
    }

    @Test
    void aQuotedFirstKeyStartsAnObject() {
        String toon = "items[2]:\n"
                + "  - \"my key\": 1\n"
                + "    b: 2\n"
                + "  - other: 3";

        assertReads(mapOf("items", listOf(mapOf("my key", 1L, "b", 2L), mapOf("other", 3L))), toon);
    }

    /** What json-io writes for a list of maps that are not uniform, it reads back. */
    @Test
    void whatTheWriterWritesReadsBack() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("content-type", "text/plain");
        first.put("x", 1L);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("2nd", true);
        Map<String, Object> third = new LinkedHashMap<>();
        third.put("a:b", "colon in the key");
        third.put("plain", null);
        Map<String, Object> fourth = new LinkedHashMap<>();
        fourth.put("say \"hi\"", "quote in the key");
        Object doc = mapOf("items", new ArrayList<>(listOf(first, second, third, fourth)));

        String toon = JsonIo.toToon(doc, null);

        assertEquals("items[4]:\n"
                + "  - \"content-type\": text/plain\n"
                + "    x: 1\n"
                + "  - \"2nd\": true\n"
                + "  - \"a:b\": colon in the key\n"
                + "    plain: null\n"
                + "  - \"say \\\"hi\\\"\": quote in the key", toon);
        assertReads(doc, toon);
    }

    @Test
    void theQuotedFirstKeyMayOpenAnArrayOrANestedObject() {
        String toon = "items[3]:\n"
                + "  - \"my tags\"[2]: a,b\n"
                + "    n: 1\n"
                + "  - \"my rows\"[2]{id,v}:\n"
                + "      1,x\n"
                + "      2,y\n"
                + "  - \"my object\":\n"
                + "      inner: 1\n"
                + "    after: 2";

        assertReads(mapOf("items", listOf(
                mapOf("my tags", listOf("a", "b"), "n", 1L),
                mapOf("my rows", listOf(mapOf("id", 1L, "v", "x"), mapOf("id", 2L, "v", "y"))),
                mapOf("my object", mapOf("inner", 1L), "after", 2L))), toon);
    }

    /** A colon inside the quotes is part of the string: the item is still one string. */
    @Test
    void aQuotedStringThatHoldsAColonIsStillAString() {
        String toon = "items[4]:\n"
                + "  - \"a: b\"\n"
                + "  - \"http://example.com/x?y=1\"\n"
                + "  - \"say \\\"hi\\\": now\"\n"
                + "  - \"\"";

        assertReads(mapOf("items", listOf("a: b", "http://example.com/x?y=1", "say \"hi\": now", "")), toon);
    }

    @Test
    void theItemsFillATypedList() {
        String toon = "name: upload\n"
                + "headers[2]:\n"
                + "  - \"Content-Type\": text/plain\n"
                + "    length: 12\n"
                + "  - \"X-Trace\": abc";

        for (ReadOptions options : BOTH_MODES) {
            Request request = JsonIo.fromToon(toon, options).asClass(Request.class);

            assertEquals("upload", request.name);
            assertEquals(2, request.headers.size());
            Map<?, ?> first = assertInstanceOf(Map.class, request.headers.get(0));
            assertEquals("text/plain", first.get("Content-Type"));
            assertEquals(12L, first.get("length"));
            assertEquals("abc", request.headers.get(1).get("X-Trace"));
        }
    }
}
