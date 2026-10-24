package com.cedarsoftware.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A String holding an unpaired surrogate (half of a pair, as a cut-off emoji leaves) is written with that char as a
 * {@code \\uXXXX} escape, as Jackson writes it, so it reads back as it was. Written raw it has no UTF-8 form: an
 * encoder wrote {@code ?} for it, and the String read back from json-io's stream output was another String. A
 * surrogate pair, and every other char, is written as before.
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
class LoneSurrogateWriteTest {
    public static class Holder {
        public String text;
        public Map<String, String> map;
    }

    /** Each with its unpaired surrogates, as written. */
    private static final String[][] LONE = {
            {"a\ud800b", "a\\ud800b"},
            {"a\udc00b", "a\\udc00b"},
            {"\udc00\ud800", "\\udc00\\ud800"},              // a low then a high: two unpaired
            {"end\ud83d", "end\\ud83d"},                     // a pair cut off at the end
            {"\udfff", "\\udfff"},
            {"\udbff", "\\udbff"},                         // the last high one
            {"\udbff\ud800", "\\udbff\\ud800"},
            {"\ud83d\ude00\ude00", "\ud83d\ude00\\ude00"}};  // a pair, then an unpaired low

    private static final String[] UNCHANGED = {"x\ud83d\ude00y", "\udbff\udfff", "\ud800\udc00",
            "\u00e9\u20ac\u4e2d", "\ud7ff\ue000", "plain", "\u2028"};

    private static Holder holder(String s) {
        Holder h = new Holder();
        h.text = s;
        h.map = new LinkedHashMap<>();
        h.map.put(s, s);
        return h;
    }

    private static void assertSame(Holder h, Holder back) {
        assertEquals(h.text, back.text);
        assertEquals(h.map, back.map);
    }

    @Test
    void anUnpairedSurrogateIsEscaped() {
        for (String[] c : LONE) {
            Holder h = holder(c[0]);
            String json = JsonIo.toJson(h, null);
            assertTrue(json.contains("\"text\":\"" + c[1] + "\""), json);
            assertTrue(json.contains("{\"" + c[1] + "\":\"" + c[1] + "\"}"), json);      // a key too
            assertSame(h, JsonIo.toJava(json, null).asClass(Holder.class));
            assertSame(h, JsonIo.toJava(json, new ReadOptionsBuilder().strictJson().build()).asClass(Holder.class));
        }
    }

    /** Written to a stream as UTF-8: no '?' in place of the surrogate, and it reads back. */
    @Test
    void throughAStream() {
        for (String[] c : LONE) {
            Holder h = holder(c[0]);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JsonIo.toJson(out, h, null);
            String written = new String(out.toByteArray(), StandardCharsets.UTF_8);
            assertFalse(written.contains("?"), written);
            assertSame(h, JsonIo.toJava(new ByteArrayInputStream(out.toByteArray()), null).asClass(Holder.class));
        }
    }

    /** JSON5's single-quoted strings (a value holding a double quote) escape it too. */
    @Test
    void json5SingleQuoted() {
        WriteOptions json5 = new WriteOptionsBuilder().json5().build();
        Holder h = holder("say \"hi\" \ud800");
        String json = JsonIo.toJson(h, json5);
        assertTrue(json.contains("'say \"hi\" \\ud800'"), json);
        assertSame(h, JsonIo.toJava(json, null).asClass(Holder.class));
        h = holder("\udc00 😀");
        json = JsonIo.toJson(h, json5);
        assertTrue(json.contains("\"\\udc00 😀\""), json);
        assertSame(h, JsonIo.toJava(json, null).asClass(Holder.class));
    }

    /** The streaming generator writes strings the same way. */
    @Test
    void theGenerator() throws Exception {
        StringWriter out = new StringWriter();
        try (JsonGenerator gen = JsonIo.createGenerator(out)) {
            gen.writeString("a\ud800");
        }
        assertEquals("\"a\\ud800\"", out.toString());
    }

    @Test
    void everythingElseIsWrittenAsBefore() {
        for (String s : UNCHANGED) {
            Holder h = holder(s);
            String json = JsonIo.toJson(h, null);
            String expected = s.equals("\u2028") ? "\\u2028" : s;
            assertTrue(json.contains("\"text\":\"" + expected + "\""), json);
            assertSame(h, JsonIo.toJava(json, null).asClass(Holder.class));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JsonIo.toJson(out, h, null);
            assertSame(h, JsonIo.toJava(new ByteArrayInputStream(out.toByteArray()), null).asClass(Holder.class));
        }
    }
}
