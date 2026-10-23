package com.cedarsoftware.io;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Strict JSON mode and the characters inside a string. RFC 8259 section 7: U+0000 through U+001F appear in a string
 * only escaped, and the escapes are {@code \" \\ \/ \b \f \n \r \t \}{@code uXXXX}. Up to 4.117.0 {@code strictJson()}
 * accepted a raw tab, line feed or other control character inside a string, and the JSON5 escape {@code \'}.
 * Non-strict reading accepts both, as before.
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
class StrictJsonStringsTest {
    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictJson().build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().build();

    /** Longer than the tokenizer's 256-char read buffer, so the string is read in more than one run. */
    private static final String LONG = repeat('x', 600);

    @Test
    void everyControlCharacterIsRefusedUnescaped() {
        for (char c = 0; c < 0x20; c++) {
            String json = "[\"a" + c + "b\"]";
            String code = String.format("U+%04X", (int) c);
            assertThatThrownBy(() -> JsonIo.toJava(json, STRICT).asClass(Object[].class))
                    .as(code)
                    .isInstanceOf(JsonIoException.class)
                    .hasMessageContaining("Unescaped control character " + code + " not allowed in strict JSON mode");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short",            // the string fits the read buffer
            "longFirstRun",     // the control character is in the first 256 chars of a long string
            "longLaterRun",     // ... past the first 256
            "afterEscape",      // after an escape, where the rest of the string is read in runs
            "longAfterEscape",  // after an escape, past the next 256 chars
            "key"})             // in a field name
    void aTabIsRefusedWhereverItIsInTheString(String where) {
        String json = jsonWithTab(where);
        assertThatThrownBy(() -> JsonIo.toJava(json, STRICT).asClass(Object.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unescaped control character U+0009 not allowed in strict JSON mode");
        assertThatThrownBy(() -> JsonIo.toJava(stream(json), STRICT).asClass(Object.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unescaped control character U+0009 not allowed in strict JSON mode");
        assertThatThrownBy(() -> {
            try (JsonTokenizer t = JsonIo.createTokenizer(json, STRICT)) {
                while (t.nextToken() != null) {
                    // read every token
                }
            }
        }).isInstanceOf(JsonParseException.class)
                .hasMessageContaining("Unescaped control character U+0009 not allowed in strict JSON mode");
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "longFirstRun", "longLaterRun", "afterEscape", "longAfterEscape", "key"})
    void nonStrictModeStillKeepsTheTab(String where) {
        Object read = JsonIo.toJava(jsonWithTab(where), LENIENT).asClass(Object.class);
        String text = read instanceof Map ? ((Map<?, ?>) read).keySet().iterator().next().toString()
                : ((Object[]) read)[0].toString();
        assertEquals(1, text.chars().filter(ch -> ch == '\t').count(), where);
    }

    @Test
    void escapedControlCharactersAreKept() {
        Object[] read = JsonIo.toJava("[\"a\\tb\\nc\\u0001d\\u001fe\\b\\f\\r\"]", STRICT).asClass(Object[].class);
        assertEquals("a\tb\nc\u0001d\u001fe\b\f\r", read[0]);
    }

    @Test
    void charactersFromU0020UpAreKept() {
        String text = " ~\u007f\u0080é€😀" + LONG;
        Object[] read = JsonIo.toJava("[\"" + text + "\"]", STRICT).asClass(Object[].class);
        assertEquals(text, read[0]);
    }

    @Test
    void anEscapedSingleQuoteIsRefused() {
        assertThatThrownBy(() -> JsonIo.toJava("[\"it\\'s\"]", STRICT).asClass(Object[].class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Escaped single quote (\\') not allowed in strict JSON mode");
        assertThatThrownBy(() -> JsonIo.toJava("{\"it\\'s\":1}", STRICT).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Escaped single quote (\\') not allowed in strict JSON mode");
    }

    @Test
    void nonStrictModeStillReadsAnEscapedSingleQuote() {
        assertEquals("it's", JsonIo.toJava("[\"it\\'s\"]", LENIENT).asClass(Object[].class)[0]);
        assertEquals("it's", JsonIo.toJava("['it\\'s']", LENIENT).asClass(Object[].class)[0]);
    }

    @Test
    void theOtherEscapesAreKept() {
        Object[] read = JsonIo.toJava("[\"\\\"\\\\\\/\"]", STRICT).asClass(Object[].class);
        assertEquals("\"\\/", read[0]);
    }

    private static String jsonWithTab(String where) {
        switch (where) {
            case "short":
                return "[\"a\tb\"]";
            case "longFirstRun":
                return "[\"a\tb" + LONG + "\"]";
            case "longLaterRun":
                return "[\"" + LONG + "a\tb\"]";
            case "afterEscape":
                return "[\"a\\nb\tc\"]";
            case "longAfterEscape":
                return "[\"a\\nb" + LONG + "c\td\"]";
            case "key":
                return "{\"a\tb\":1}";
            default:
                throw new IllegalArgumentException(where);
        }
    }

    private static ByteArrayInputStream stream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
