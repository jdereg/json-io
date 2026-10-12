package com.cedarsoftware.io;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The byte-order mark (TOON spec 12 and 7.2). A reader removes a single U+FEFF at the very start of the document,
 * before it looks at the first line in any other way; a U+FEFF anywhere else is content. A writer never writes
 * one, and puts quotes around a root string that starts with U+FEFF, which would otherwise be taken for one.
 * json-io kept the mark: a file saved with one by a Windows editor read back with the mark in its first key, and a
 * comment on its first line was no comment.
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
class ToonByteOrderMarkTest {

    private static final String BOM = "\uFEFF";
    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictToon(true).build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().strictToon(false).build();
    private static final ReadOptions[] BOTH_MODES = {STRICT, LENIENT};
    private static final WriteOptions PLAIN_JSON = new WriteOptionsBuilder().showTypeInfoNever().build();
    private static final WriteOptions LEGACY = new WriteOptionsBuilder().toonLegacyForms(true).build();

    public static class Person {
        public String name;
        public int age;
    }

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    /** The value as JSON without type information: what was read, whatever Java classes hold it. */
    private static String json(Object value) {
        return JsonIo.toJson(value, PLAIN_JSON);
    }

    /** Read from a String, from UTF-8 bytes and (an object) into Maps, in both modes: all the same. */
    private static void assertReads(Object expected, String toon) {
        for (ReadOptions options : BOTH_MODES) {
            String mode = options.isStrictToon() ? "strict" : "non-strict";
            assertEquals(json(expected), json(JsonIo.fromToon(toon, options).asClass(Object.class)), mode);
            ByteArrayInputStream bytes = new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8));
            assertEquals(json(expected), json(JsonIo.fromToon(bytes, options).asClass(Object.class)), mode);
            if (expected instanceof Map) {
                assertEquals(json(expected), json(JsonIo.fromToonToMaps(toon, options).asClass(Map.class)), mode);
            }
        }
    }

    // ---------------------------------------------------------------- reading

    @Test
    void aMarkAtTheStartOfTheDocumentIsRemoved() {
        assertReads(map("a", 1L, "b", "x"), BOM + "a: 1\nb: x");
        assertReads(map("a", map("b", 1L)), BOM + "a:\n  b: 1");
        assertReads(listOf(1L, 2L), BOM + "[2]: 1,2");
        assertReads(listOf(map("id", 1L), map("id", 2L)), BOM + "[2]{id}:\n  1\n  2");
        assertReads(map("x", map("v", 1L), "y", map("v", 2L)), BOM + "[2:]{v}:\n  x: 1\n  y: 2");
        assertReads(map("a b", 1L), BOM + "\"a b\": 1");
    }

    /** What follows the mark is read as if the mark had never been there: a number is a number. */
    @Test
    void aRootPrimitiveAfterTheMark() {
        assertReads(8L, BOM + "8");
        assertReads(true, BOM + "true");
        assertReads("hello", BOM + "hello");
        assertReads(listOf(), BOM + "[]");
    }

    /** The mark goes before the lines are sorted into comments, blank lines and content. */
    @Test
    void theMarkIsRemovedBeforeCommentsAndBlankLinesAreFound() {
        assertReads(map("a", 1L), BOM + "# a comment\na: 1");
        assertReads(map("a", 1L), BOM + "\na: 1");
        assertReads(map(), BOM);
        assertReads(map(), BOM + "# only a comment");
    }

    /** One mark is removed, at the start. Any other U+FEFF is a character like another. */
    @Test
    void aMarkAnywhereElseIsContent() {
        assertReads(map(BOM + "a", 1L, BOM + "b", 2L), BOM + BOM + "a: 1\n" + BOM + "b: 2");
        assertReads(map("a", BOM + "x", "b", "y" + BOM, "c", "p" + BOM + "q"),
                "a: " + BOM + "x\nb: y" + BOM + "\nc: p" + BOM + "q");
        assertReads(map("a", listOf(BOM, BOM + "1")), "a[2]: " + BOM + "," + BOM + "1");
        assertReads(map("a", 1L, BOM + "#b", 2L), "a: 1\n" + BOM + "#b: 2");
    }

    @Test
    void aTypedReadSeesNoMark() {
        Person person = JsonIo.fromToon(BOM + "name: Ada\nage: 36", STRICT).asClass(Person.class);

        assertEquals("Ada", person.name);
        assertEquals(36, person.age);
    }

    // ---------------------------------------------------------------- writing

    /** At the root an unquoted U+FEFF would open the document, and be removed by whoever reads it. */
    @Test
    void aRootStringThatStartsWithTheMarkIsQuoted() {
        for (String string : new String[] {BOM + "8", BOM, BOM + "true", BOM + "a b", BOM + BOM}) {
            for (WriteOptions options : new WriteOptions[] {null, LEGACY}) {
                String toon = JsonIo.toToon(string, options);

                assertEquals('"' + string + '"', toon);
                for (ReadOptions mode : BOTH_MODES) {
                    assertEquals(string, JsonIo.fromToon(toon, mode).asClass(Object.class));
                }
            }
        }
        assertEquals("a" + BOM, JsonIo.toToon("a" + BOM, null), "only at the start");
    }

    /** Anywhere but at the root the mark is no start of the document: no quotes for it, and it is read back. */
    @Test
    void elsewhereTheMarkNeedsNoQuotes() {
        Map<String, Object> doc = map("a", BOM + "x", "b", new ArrayList<>(listOf(BOM + "8", "y")), BOM + "k", 1L);

        String toon = JsonIo.toToon(doc, null);

        assertEquals("a: " + BOM + "x\nb[2]: " + BOM + "8,y\n\"" + BOM + "k\": 1", toon);
        assertReads(doc, toon);

        Map<String, Object> first = map(BOM + "k", 1L, "a", 2L);
        String keyFirst = JsonIo.toToon(first, null);
        assertEquals("\"" + BOM + "k\": 1\na: 2", keyFirst, "a key is quoted by the rule for keys");
        assertReads(first, keyFirst);

        List<Object> list = new ArrayList<>(listOf(BOM + "x", "y"));
        String array = JsonIo.toToon(list, null);
        assertEquals("[2]: " + BOM + "x,y", array, "a root array opens with its header");
        assertReads(list, array);
    }

    /** json-io writes no mark of its own, to a String or to a stream. */
    @Test
    void theWriterWritesNoMark() {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        JsonIo.toToon(out, map("a", 1L), null);

        assertArrayEquals("a: 1".getBytes(StandardCharsets.UTF_8), out.toByteArray());
    }
}
