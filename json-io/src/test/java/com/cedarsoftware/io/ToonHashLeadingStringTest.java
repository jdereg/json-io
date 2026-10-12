package com.cedarsoftware.io;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static com.cedarsoftware.util.MapUtilities.mapOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A string that starts with {@code #} is written in quotes (TOON spec 7.2).
 *
 * <p>Since spec v4.0 a line whose first character is {@code #} is a comment that a reader drops (spec 5.1).
 * json-io through 4.113.0 wrote such a string bare, so {@code #ff0000} in the first column of a table, or as the
 * whole document, started a line and was lost by a v4 reader.
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
class ToonHashLeadingStringTest {

    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictToon(true).build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().strictToon(false).build();

    static class Swatch {
        String color;
        String name;

        Swatch() {
        }

        Swatch(String color, String name) {
            this.color = color;
            this.name = name;
        }
    }

    static class Palette {
        String title = "#palette";
        char mark = '#';
        Character boxedMark = '#';
        String[] codes = {"#fff", "#000"};
        List<String> tags = new ArrayList<>(listOf("#warm", "plain"));
        Map<String, String> byName = new LinkedHashMap<>(mapOf("red", "#ff0000", "green", "#00ff00"));
        URI link = URI.create("#top");
        List<Swatch> swatches = new ArrayList<>(listOf(new Swatch("#ff0000", "red"), new Swatch("#00ff00", "green")));
    }

    @Test
    void theWholeDocumentIsQuoted() {
        assertEquals("\"#hello\"", JsonIo.toToon("#hello", null));
    }

    @Test
    void aFieldValueIsQuoted() {
        assertEquals("note: \"#x\"", JsonIo.toToon(mapOf("note", "#x"), null));
    }

    @Test
    void anInlineArrayValueIsQuoted() {
        assertEquals("tags[2]: \"#a\",b", JsonIo.toToon(mapOf("tags", listOf("#a", "b")), null));
    }

    @Test
    void aListItemIsQuoted() {
        assertEquals("items[2]:\n  - \"#x\"\n  - a: 1", JsonIo.toToon(mapOf("items", listOf("#x", mapOf("a", 1))), null));
    }

    /** The case that lost data: the cell starts its line. */
    @Test
    void theFirstCellOfATableRowIsQuoted() {
        Object palette = mapOf("palette", listOf(
                mapOf("color", "#ff0000", "name", "red"),
                mapOf("color", "#00ff00", "name", "green")));

        assertEquals("palette[2]{color,name}:\n  \"#ff0000\",red\n  \"#00ff00\",green", JsonIo.toToon(palette, null));
    }

    @Test
    void theQuotingHoldsForTheTabAndPipeDelimiters() {
        Object doc = mapOf("tags", listOf("#a", "b"));

        assertEquals("tags[2|]: \"#a\"|b", JsonIo.toToon(doc, new WriteOptionsBuilder().toonDelimiter('|').build()));
        assertEquals("tags[2\t]: \"#a\"\tb", JsonIo.toToon(doc, new WriteOptionsBuilder().toonDelimiter('\t').build()));
    }

    @Test
    void aHashThatIsNotFirstNeedsNoQuotes() {
        assertEquals("a: a#b\nb: issue #12\nc: C#", JsonIo.toToon(mapOf("a", "a#b", "b", "issue #12", "c", "C#"), null));
    }

    @Test
    void aKeyThatStartsWithHashIsQuoted() {
        assertEquals("\"#k\": 1", JsonIo.toToon(mapOf("#k", 1), null));
    }

    @Test
    void noLineOfAJavaObjectsTextStartsWithHash() {
        String toon = JsonIo.toToon(new Palette(), null);

        for (String line : toon.split("\n")) {
            assertFalse(line.trim().startsWith("#"), "a reader drops this line as a comment: " + line + "\n" + toon);
        }
        assertTrue(toon.contains("\"#ff0000\",red"), toon);
    }

    @Test
    void whatIsWrittenReadsBackInBothModes() {
        Palette original = new Palette();
        String toon = JsonIo.toToon(original, null);

        for (ReadOptions options : new ReadOptions[]{STRICT, LENIENT}) {
            Palette copy = JsonIo.fromToon(toon, options).asClass(Palette.class);

            Map<String, Object> diff = new LinkedHashMap<>();
            assertTrue(DeepEquals.deepEquals(original, copy, diff), diff.get("diff") + "\n" + toon);
            assertEquals("#ff0000", copy.swatches.get(0).color);
            assertEquals('#', copy.mark);
        }
    }
}
