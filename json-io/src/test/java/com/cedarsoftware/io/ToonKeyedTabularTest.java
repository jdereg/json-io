package com.cedarsoftware.io;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static com.cedarsoftware.util.MapUtilities.mapOf;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An object whose values are uniform objects is a table whose rows carry their own keys (TOON spec 9.5, new in spec
 * v4.0): the keyed header {@code servers[2:]{host,port}:} names the fields once, and each entry is one row
 * {@code alpha: a.example.com,8080}.
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
class ToonKeyedTabularTest {

    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictToon(true).build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().strictToon(false).build();
    private static final ReadOptions[] BOTH_MODES = {STRICT, LENIENT};
    private static final WriteOptions PLAIN_JSON = new WriteOptionsBuilder().showTypeInfoNever().build();

    static class Server {
        String host;
        int port;
    }

    static class Cluster {
        String name;
        Map<String, Server> servers;
        String region;
    }

    static class Pair {
        Server primary;
        Server backup;
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

    private static void assertRejectedInBothModes(String toon, String message) {
        for (ReadOptions options : BOTH_MODES) {
            assertThatThrownBy(() -> read(toon, options))
                    .as(toon)
                    .isInstanceOf(JsonIoException.class)
                    .hasMessageContaining(message);
        }
    }

    private static void assertRejectedInStrictMode(String toon, String message) {
        assertThatThrownBy(() -> read(toon, STRICT))
                .as(toon)
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining(message);
    }

    @Test
    void eachEntryRowIsOneEntryOfTheObject() {
        String toon = "servers[2:]{host,port}:\n"
                + "  alpha: a.example.com,8080\n"
                + "  beta: b.example.com,9090";

        assertReads(mapOf("servers", mapOf(
                "alpha", mapOf("host", "a.example.com", "port", 8080L),
                "beta", mapOf("host", "b.example.com", "port", 9090L))), toon);
    }

    @Test
    void aKeyedHeaderWithNoKeyIsTheRootObject() {
        assertReads(mapOf(
                "alice", mapOf("age", 30L, "city", "Berlin"),
                "bob", mapOf("age", 25L, "city", "Oslo")), "[2:]{age,city}:\n  alice: 30,Berlin\n  bob: 25,Oslo");
    }

    @Test
    void theHeaderMayDeclareADelimiterAndNestedFieldGroups() {
        Object expected = mapOf("regions", mapOf(
                "eu", mapOf("name", "Europe, West", "geo", mapOf("lat", 50L, "lon", 10L)),
                "us", mapOf("name", "America", "geo", mapOf("lat", 40L, "lon", -100L))));

        assertReads(expected, "regions[2:|]{name|geo{lat|lon}}:\n  eu: Europe, West|50|10\n  us: America|40|-100");
        assertReads(expected,
                "regions[2:\t]{name\tgeo{lat\tlon}}:\n  eu: Europe, West\t50\t10\n  us: America\t40\t-100");
    }

    /** The row is split at its first unquoted colon before anything else, so the key may hold a delimiter. */
    @Test
    void theEntryKeyIsTheTextBeforeTheFirstUnquotedColon() {
        String toon = "m[8:]{v}:\n"
                + "  \"a:b\": 1\n"
                + "  \"my key\": 2\n"
                + "  \"42\": 3\n"
                + "  a,b: 4\n"
                + "  k[2]: 5\n"
                + "  foo-bar: 6\n"
                + "  __proto__: 7\n"
                + "  : 8";

        assertReads(mapOf("m", mapOf(
                "a:b", mapOf("v", 1L),
                "my key", mapOf("v", 2L),
                "42", mapOf("v", 3L),
                "a,b", mapOf("v", 4L),
                "k[2]", mapOf("v", 5L),
                "foo-bar", mapOf("v", 6L),
                "__proto__", mapOf("v", 7L),
                "", mapOf("v", 8L))), toon);
    }

    /** A cell is one primitive: [] is the text "[]", and a second colon belongs to the cell. */
    @Test
    void theCellsAreReadAsInARowOfATable() {
        String toon = "m[3:]{a,b}:\n"
                + "  x: [],\"p,q\"\n"
                + "  y: 10:30,true\n"
                + "  z: null,\"\"";

        assertReads(mapOf("m", mapOf(
                "x", mapOf("a", "[]", "b", "p,q"),
                "y", mapOf("a", "10:30", "b", true),
                "z", mapOf("a", null, "b", ""))), toon);
    }

    @Test
    void theObjectEndsWhereTheDepthDropsBackToTheHeaders() {
        String toon = "outer:\n"
                + "  data[2:]{x}:\n"
                + "    a: 1\n"
                + "    b: 2\n"
                + "  count: 3\n"
                + "empty[0:]{x}:\n"
                + "last: 4";

        assertReads(mapOf(
                "outer", mapOf("data", mapOf("a", mapOf("x", 1L), "b", mapOf("x", 2L)), "count", 3L),
                "empty", mapOf(),
                "last", 4L), toon);
    }

    /** On a hyphen line the header stands one level below the hyphen, so its rows are two levels below. */
    @Test
    void aKeyedHeaderMayBeTheFirstFieldOfAListItem() {
        String toon = "items[2]:\n"
                + "  - config[2:]{x}:\n"
                + "      a: 1\n"
                + "      b: 2\n"
                + "    status: ok\n"
                + "  - status: down";

        assertReads(mapOf("items", listOf(
                mapOf("config", mapOf("a", mapOf("x", 1L), "b", mapOf("x", 2L)), "status", "ok"),
                mapOf("status", "down"))), toon);
    }

    @Test
    void commentsAndOuterBlankLinesAreNotRows() {
        String toon = "m[2:]{v}:\n"
                + "\n"
                + "  # the first\n"
                + "  a: 1\n"
                + "# outdented, and still not the end\n"
                + "  b: 2\n"
                + "\n"
                + "next: 3";

        assertReads(mapOf("m", mapOf("a", mapOf("v", 1L), "b", mapOf("v", 2L)), "next", 3L), toon);
    }

    @Test
    void theEntriesFillAMapOfJavaObjects() {
        String toon = "name: core\n"
                + "servers[2:]{host,port}:\n"
                + "  alpha: a.example.com,8080\n"
                + "  beta: b.example.com,9090\n"
                + "region: eu";

        for (ReadOptions options : BOTH_MODES) {
            Cluster cluster = JsonIo.fromToon(toon, options).asClass(Cluster.class);

            assertEquals("core", cluster.name);
            assertEquals("eu", cluster.region);
            assertEquals(new ArrayList<>(listOf("alpha", "beta")), new ArrayList<>(cluster.servers.keySet()));
            Server beta = assertInstanceOf(Server.class, cluster.servers.get("beta"));
            assertEquals("b.example.com", beta.host);
            assertEquals(9090, beta.port);
        }
    }

    /** The entry keys are the fields of a Java object just as the keys of any TOON object are. */
    @Test
    void theEntriesFillTheFieldsOfAJavaObject() {
        String toon = "[2:]{host,port}:\n  primary: a.example.com,8080\n  backup: b.example.com,9090";

        for (ReadOptions options : BOTH_MODES) {
            Pair pair = JsonIo.fromToon(toon, options).asClass(Pair.class);

            assertEquals("a.example.com", pair.primary.host);
            assertEquals(9090, pair.backup.port);
        }
    }

    /** As it does for any root object, the reader marks a keyed root object with the type the caller asked for. */
    @Test
    void theRootObjectCarriesTheRequestedType() {
        String plain = "primary:\n  host: a\n  port: 1";
        String keyed = "[2:]{host,port}:\n  primary: a,1\n  backup: b,2";

        for (String toon : new String[]{plain, keyed}) {
            JsonObject root = (JsonObject) new ToonReader(new StringReader(toon), STRICT).readValue(Pair.class);
            assertEquals(Pair.class, root.getType(), toon);
        }
    }

    /** An entry is always an object, so its key is never one of json-io's meta keys. */
    @Test
    void anEntryKeyThatLooksLikeAMetaKeyIsAKey() {
        Map<?, ?> m = (Map<?, ?>) ((Map<?, ?>) read("m[2:]{v}:\n  @type: 1\n  @id: 2", STRICT)).get("m");

        assertEquals(json(mapOf("v", 1L)), json(m.get("@type")));
        assertEquals(json(mapOf("v", 2L)), json(m.get("@id")));
    }

    /** The syntax is new, so there is no lenient reading to keep: these are errors in non-strict mode too. */
    @Test
    void aRowMustHoldItsKeyAndExactlyOneCellPerLeafField() {
        assertRejectedInBothModes("m[1:]{a,b}:\n  k: 1", "width mismatch at line 2, expected 2 values, got 1");
        assertRejectedInBothModes("m[1:]{a}:\n  k: 1,2", "width mismatch at line 2, expected 1 values, got 2");
        assertRejectedInBothModes("m[1:]{a,n{x,y}}:\n  k: 1,2", "width mismatch at line 2, expected 3 values, got 2");
        assertRejectedInBothModes("m[1:]{v}:\n  a:", "width mismatch at line 2, expected 1 values, got 0");
        assertRejectedInBothModes("m[2:]{v}:\n  a: 1\n  boom", "Entry row without a colon at line 3");
        assertRejectedInBothModes("m[1:]{v}:\n  a: 1\n    b: 2", "wrong depth among the entry rows at line 3");
        // Non-strict mode takes the depth of a first row indented too far; a later row above it is still an error.
        assertThatThrownBy(() -> read("m[1:]{v}:\n    a: 1\n  b: 2", LENIENT))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("wrong depth among the entry rows at line 3");
    }

    @Test
    void nothingMayFollowTheColonOfAKeyedHeader() {
        assertRejectedInBothModes("m[1:]{v}: x\n  a: 1", "Content after the colon of a keyed header at line 1");
        assertRejectedInBothModes("m[1:]{v}:\t\n  a: 1", "Content after the colon of a keyed header at line 1");
        assertReads(mapOf("m", mapOf("a", mapOf("v", 1L))), "m[1:]{v}:   \n  a: 1");
    }

    @Test
    void strictModeRejectsAMalformedKeyedHeader() {
        assertRejectedInStrictMode("m[0:]:", "no field list");
        assertRejectedInStrictMode("m[2|:]{v}:\n  a: 1\n  b: 2", "Malformed keyed header (length)");
        assertRejectedInStrictMode("m[2:,]{v}:\n  a: 1\n  b: 2", "Invalid array count");
        assertRejectedInStrictMode("m[02:]{v}:\n  a: 1\n  b: 2", "leading zeros");
        assertRejectedInStrictMode("m[2 :]{v}:\n  a: 1\n  b: 2", "Malformed keyed header (length)");
        assertRejectedInStrictMode("m[:]{v}:\n  a: 1", "Malformed keyed header (length)");
        assertRejectedInStrictMode("m[-1:]{v}:", "Malformed keyed header (length)");
        assertRejectedInStrictMode("m[2:]{v}", "unexpected content after fields segment");
    }

    /** What separates the modes for a keyed object: the count, a repeated key, a blank line, a depth jump. */
    @Test
    void strictModeHoldsTheRowsToTheHeader() {
        assertRejectedInStrictMode("m[2:]{v}:\n  a: 1", "count mismatch at line 2, expected 2 entries, got 1");
        assertRejectedInStrictMode("m[1:]{v}:\n  a: 1\n  b: 2", "count mismatch at line 3, expected 1 entries, got 2");
        assertRejectedInStrictMode("m[2:]{v}:\n  a: 1\n  a: 2", "Duplicate key 'a' at line 3");
        assertRejectedInStrictMode("m[2:]{v}:\n  a: 1\n\n  b: 2",
                "Blank lines are not allowed among entry rows at line 3");
        assertRejectedInStrictMode("m[1:]{v}:\n    a: 1", "indented more than one level below its header at line 2");
    }

    @Test
    void nonStrictModeReadsEveryRowWhateverTheHeaderDeclares() {
        // the count is not checked: every row is read
        assertEquals(json(mapOf("m", mapOf("a", mapOf("v", 1L), "b", mapOf("v", 2L)), "n", mapOf("c", mapOf("v", 3L)))),
                json(read("m[1:]{v}:\n  a: 1\n  b: 2\nn[3:]{v}:\n  c: 3", LENIENT)));
        // a repeated key: the last one wins
        Map<?, ?> m = (Map<?, ?>) ((Map<?, ?>) read("m[2:]{v}:\n  a: 1\n  a: 2", LENIENT)).get("m");
        assertEquals(json(mapOf("v", 2L)), json(m.get("a")));
        // a blank line between rows is ignored
        assertEquals(json(mapOf("m", mapOf("a", mapOf("v", 1L), "b", mapOf("v", 2L)))),
                json(read("m[2:]{v}:\n  a: 1\n\n  b: 2", LENIENT)));
        // a first row indented too far sets the depth of the rows
        assertEquals(json(mapOf("m", mapOf("a", mapOf("v", 1L), "b", mapOf("v", 2L)), "next", 3L)),
                json(read("m[2:]{v}:\n      a: 1\n      b: 2\nnext: 3", LENIENT)));
    }

    @Test
    void aTrailingObjectAtTheRootMustBeTheWholeDocumentInStrictMode() {
        assertRejectedInStrictMode("[1:]{v}:\n  a: 1\njunk: 3", "Unexpected content after root value");
        assertTrue(((Map<?, ?>) read("[1:]{v}:\n  a: 1\n", STRICT)).containsKey("a"));
    }
}
