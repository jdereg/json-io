package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import com.cedarsoftware.io.annotation.IoProperty;
import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which keys are written without quotes (TOON spec 7.3): those that match {@code [A-Za-z_][A-Za-z0-9_.]*}, the dot
 * included. json-io put a key with a dot in quotes unless key folding was on, to keep it from a reader that expands
 * paths; a dotted key is a key like another, and a reader takes it as it stands unless it is told to expand.
 * Under {@code toonLegacyForms} it is written as json-io 4.113.0 wrote it.
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
class ToonDottedKeyTest {

    /** Spec 7.3: keys "MUST be unquoted if they match" this, and quoted otherwise. */
    private static final Pattern UNQUOTED_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");

    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictToon(true).build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().strictToon(false).build();
    private static final ReadOptions[] BOTH_MODES = {STRICT, LENIENT};
    private static final WriteOptions LEGACY = new WriteOptionsBuilder().toonLegacyForms(true).build();
    private static final WriteOptions PLAIN_JSON = new WriteOptionsBuilder().showTypeInfoNever().build();

    /** A field whose name in the text holds a dot: its quoting is decided once, with the class's write plan. */
    public static class Settings {
        @IoProperty("server.port")
        public int port = 8080;
        public String name = "main";
    }

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static void assertReadsBack(Object expected, String toon) {
        for (ReadOptions options : BOTH_MODES) {
            assertEquals(JsonIo.toJson(expected, PLAIN_JSON),
                    JsonIo.toJson(JsonIo.fromToon(toon, options).asClass(Object.class), PLAIN_JSON), toon);
        }
    }

    @Test
    void aDottedKeyIsWrittenWithoutQuotes() {
        Map<String, Object> doc = map("a.b", 1L, "true", 2L, "x.y.z", map("k.l", 3L), "trailing.", 4L, "v1.2", 5L);

        String toon = JsonIo.toToon(doc, null);

        assertEquals("a.b: 1\ntrue: 2\nx.y.z:\n  k.l: 3\ntrailing.: 4\nv1.2: 5", toon);
        assertReadsBack(doc, toon);
    }

    @Test
    void inAFieldListAnEntryKeyAndAListItem() {
        Map<String, Object> doc = map(
                "rows", new ArrayList<>(listOf(map("a.b", 1L, "c", 2L), map("a.b", 3L, "c", 4L))),
                "byHost", map("a.example.com", map("port", 80L), "b.example.com", map("port", 81L)),
                "items", new ArrayList<>(listOf(map("x.y", 1L), "z")),
                "list.of", new ArrayList<>(listOf(1L, 2L)));

        String toon = JsonIo.toToon(doc, null);

        assertEquals("rows[2]{a.b,c}:\n"
                + "  1,2\n"
                + "  3,4\n"
                + "byHost[2:]{port}:\n"
                + "  a.example.com: 80\n"
                + "  b.example.com: 81\n"
                + "items[2]:\n"
                + "  - x.y: 1\n"
                + "  - z\n"
                + "list.of[2]: 1,2", toon);
        assertReadsBack(doc, toon);
    }

    @Test
    void theFieldOfAnObject() {
        String toon = JsonIo.toToon(new Settings(), null);

        assertEquals("server.port: 8080\nname: main", toon);
        Settings back = JsonIo.fromToon("server.port: 9090\nname: other", STRICT).asClass(Settings.class);
        assertEquals(9090, back.port);
        assertEquals("other", back.name);
        assertEquals("\"server.port\": 8080\nname: main", JsonIo.toToon(new Settings(), LEGACY));
        assertEquals("server.port: 8080\nname: main", JsonIo.toToon(new Settings(),
                new WriteOptionsBuilder().toonLegacyForms(true).toonKeyFolding(true).build()));
    }

    /** A dot does not make a key unquoted: the rest of the rule holds. */
    @Test
    void theKeysThatAreStillQuoted() {
        Map<String, Object> doc = map(".a", 1L, "1.a", 2L, "a-b.c", 3L, "a b", 4L, "a.b:c", 5L, "", 6L, "é.x", 7L);

        String toon = JsonIo.toToon(doc, null);

        assertEquals("\".a\": 1\n\"1.a\": 2\n\"a-b.c\": 3\n\"a b\": 4\n\"a.b:c\": 5\n\"\": 6\n\"é.x\": 7", toon);
        assertReadsBack(doc, toon);
    }

    /** Written for json-io 4.113.0 and earlier, a dotted key keeps the quotes they gave it. */
    @Test
    void legacyFormsQuoteADottedKey() {
        Map<String, Object> doc = map("a.b", 1L, "rows", new ArrayList<>(listOf(map("c.d", 1L), map("c.d", 2L))));

        String toon = JsonIo.toToon(doc, LEGACY);

        assertEquals("\"a.b\": 1\nrows[2]{\"c.d\"}:\n  1\n  2", toon);
        assertReadsBack(doc, toon);
        // with key folding on they wrote it bare, and so does the legacy form
        assertEquals("a.b: 1", JsonIo.toToon(map("a.b", 1L),
                new WriteOptionsBuilder().toonLegacyForms(true).toonKeyFolding(true).build()));
    }

    /**
     * A reader takes a dotted key as it stands. Told to expand paths (toonExpandPaths, json-io's option from
     * spec v3), it expands every unquoted one; a key that must stay whole for such a reader is one in quotes.
     */
    @Test
    void aReaderThatExpandsPathsExpandsAnUnquotedDottedKey() {
        ReadOptions expanding = new ReadOptionsBuilder().toonExpandPaths(true).build();

        assertEquals(JsonIo.toJson(map("a", map("b", 1L)), PLAIN_JSON),
                JsonIo.toJson(JsonIo.fromToon("a.b: 1", expanding).asClass(Map.class), PLAIN_JSON));
        assertEquals(JsonIo.toJson(map("a.b", 1L), PLAIN_JSON),
                JsonIo.toJson(JsonIo.fromToon("\"a.b\": 1", expanding).asClass(Map.class), PLAIN_JSON));
        assertReadsBack(map("a.b", 1L), "a.b: 1");
    }

    /** With key folding on, a chain of single keys is folded and a key that holds a dot itself is left whole. */
    @Test
    void keyFoldingStillFolds() {
        Map<String, Object> doc = map("a", map("b", map("c", 1L)), "d.e", 2L);

        assertEquals("a.b.c: 1\nd.e: 2", JsonIo.toToon(doc, new WriteOptionsBuilder().toonKeyFolding(true).build()));
        assertEquals("a:\n  b:\n    c: 1\nd.e: 2", JsonIo.toToon(doc, null));
    }

    /** For 5,000 keys from a fixed seed: quoted exactly when the spec's pattern says so, and read back whole. */
    @Test
    void aKeyIsQuotedExactlyWhenTheSpecsPatternSaysSo() {
        Random random = new Random(20261012L);
        char[] alphabet = "abZ_.19- :".toCharArray();
        int bare = 0;
        int dotted = 0;
        for (int n = 0; n < 5000; n++) {
            StringBuilder key = new StringBuilder();
            for (int length = 1 + random.nextInt(5); length > 0; length--) {
                key.append(alphabet[random.nextInt(alphabet.length)]);
            }
            String name = key.toString();
            boolean unquoted = UNQUOTED_KEY.matcher(name).matches();
            Map<String, Object> doc = map(name, 1L, "list", new ArrayList<>(listOf(map(name, "v"), "x")));

            String toon = JsonIo.toToon(doc, null);

            String written = unquoted ? name : '"' + name + '"';     // (no character of the alphabet is escaped)
            assertTrue(toon.startsWith(written + ": 1\n"), toon);
            assertTrue(toon.contains("\n  - " + written + ": v\n"), toon);
            assertReadsBack(doc, toon);
            if (unquoted) {
                bare++;
                if (name.indexOf('.') >= 0) {
                    dotted++;
                }
            }
        }
        assertTrue(bare > 500 && dotted > 150, bare + " unquoted keys, " + dotted + " of them with a dot");
    }
}
