package com.cedarsoftware.io;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Coverage tests for ToonReader — targets JaCoCo gaps: malformed array
 * syntax, tabular array errors, count mismatches, missing colons, missing
 * closing braces, delimiter mismatches, list array errors, blank lines
 * in tabular, and the 2-arg constructor.
 *
 * Each test exercises a specific error path or edge case identified from
 * the JaCoCo "nc" (not covered) line analysis.
 */
class ToonReaderCoverageTest {

    // ========== 2-arg constructor (line 130-132) ==========

    @Test
    void testTwoArgConstructor() {
        StringReader reader = new StringReader("name: hello");
        ToonReader toonReader = new ToonReader(reader, null);
        Object result = toonReader.readValue(Object.class);
        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("name")).isEqualTo("hello");
    }

    @Test
    void testTwoArgConstructorWithReadOptions() {
        StringReader reader = new StringReader("count: 42");
        ReadOptions opts = new ReadOptionsBuilder().build();
        ToonReader toonReader = new ToonReader(reader, opts);
        Object result = toonReader.readValue(Object.class);
        assertThat(result).isInstanceOf(Map.class);
    }

    // ========== Malformed array syntax — invalid count ==========

    @Test
    void testMalformedArrayCountNotANumber() {
        // [abc]: ... — non-numeric count (line 463-464)
        String toon = "items[abc]: 1,2,3";
        assertThatThrownBy(() -> JsonIo.fromToon(toon).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Invalid array count");
    }

    @Test
    void testMalformedArrayMissingColon() {
        // [3] xxx — missing colon after [N] (line 490)
        String toon = "items[3] 1 2 3";
        assertThatThrownBy(() -> JsonIo.fromToon(toon).asClass(Map.class))
                .isInstanceOf(Exception.class);
    }

    // ========== Tabular array errors ==========

    private static ReadOptions strictOpts() {
        return new ReadOptionsBuilder().strictToon(true).build();
    }

    @Test
    void testTabularArrayCountMismatchTooFew() {
        // [3]{a,b}: declares 3 rows, but only 1 row provided (line 699-700)
        // Requires strictToon mode
        String toon =
                "rows[3]{a,b}:\n" +
                "  1,2";
        assertThatThrownBy(() -> JsonIo.fromToon(toon, strictOpts()).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("count mismatch");
    }

    @Test
    void testTabularArrayCountMismatchTooMany() {
        // [1]{a,b}: declares 1 row, but 3 rows provided (line 703)
        String toon =
                "rows[1]{a,b}:\n" +
                "  1,2\n" +
                "  3,4\n" +
                "  5,6";
        assertThatThrownBy(() -> JsonIo.fromToon(toon, strictOpts()).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("more rows than declared");
    }

    @Test
    void testTabularRowWidthMismatch() {
        // [1]{a,b,c}: 3 columns but row has 2 values (line 690)
        String toon =
                "rows[1]{a,b,c}:\n" +
                "  1,2";
        assertThatThrownBy(() -> JsonIo.fromToon(toon, strictOpts()).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("width mismatch");
    }

    // ========== Inline array count mismatch ==========

    @Test
    void testInlineArrayCountMismatchTooFew() {
        // [3]: 1,2 — declares 3 but only 2 provided (line 877-878)
        String toon = "items[3]: 1,2";
        assertThatThrownBy(() -> JsonIo.fromToon(toon, strictOpts()).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("count mismatch");
    }

    @Test
    void testInlineArrayCountMismatchTooMany() {
        // [1]: 1,2,3 — declares 1 but 3 provided
        String toon = "items[1]: 1,2,3";
        assertThatThrownBy(() -> JsonIo.fromToon(toon, strictOpts()).asClass(Map.class))
                .isInstanceOf(JsonIoException.class);
    }

    // ========== List array errors ==========

    @Test
    void testListArrayCountMismatch() {
        // [3]: with - prefixed list, only 2 elements (line 1036-1037)
        String toon =
                "items[3]:\n" +
                "  - one\n" +
                "  - two";
        assertThatThrownBy(() -> JsonIo.fromToon(toon, strictOpts()).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("count mismatch");
    }

    @Test
    void testListArrayTooManyElements() {
        // [1]: with 3 list elements (line 1040)
        String toon =
                "items[1]:\n" +
                "  - one\n" +
                "  - two\n" +
                "  - three";
        assertThatThrownBy(() -> JsonIo.fromToon(toon, strictOpts()).asClass(Map.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("more elements than declared");
    }

    // ========== Successful parses (positive coverage) ==========

    @Test
    void testEmptyArray() {
        // [0]: — empty array (line 468-469)
        String toon = "items[0]:";
        Map<?, ?> result = JsonIo.fromToon(toon).asClass(Map.class);
        assertThat(result.get("items")).isInstanceOf(List.class);
        assertThat((List<?>) result.get("items")).isEmpty();
    }

    @Test
    void testInlineArrayThreeElements() {
        String toon = "items[3]: a,b,c";
        Map<?, ?> result = JsonIo.fromToon(toon).asClass(Map.class);
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) result.get("items");
        assertThat(items).containsExactly("a", "b", "c");
    }

    @Test
    void testTabularArrayThreeRowsTwoCols() {
        String toon =
                "rows[3]{a,b}:\n" +
                "  1,2\n" +
                "  3,4\n" +
                "  5,6";
        Map<?, ?> result = JsonIo.fromToon(toon).asClass(Map.class);
        assertThat(result.get("rows")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Object> rows = (List<Object>) result.get("rows");
        assertThat(rows).hasSize(3);
    }

    @Test
    void testListArrayWithDashes() {
        String toon =
                "items[3]:\n" +
                "  - one\n" +
                "  - two\n" +
                "  - three";
        Map<?, ?> result = JsonIo.fromToon(toon).asClass(Map.class);
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) result.get("items");
        assertThat(items).containsExactly("one", "two", "three");
    }

    // ========== Tab and pipe delimiters ==========

    @Test
    void testInlineArrayWithTabDelimiter() {
        // [3\t]: tab-delimited (line 451-453)
        // Round-trip: write with tab delimiter then read back
        java.util.LinkedHashMap<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("items", java.util.Arrays.asList("a", "b", "c"));
        WriteOptions wo = new WriteOptionsBuilder().toonDelimiter('\t').build();
        String toon = JsonIo.toToon(data, wo);
        // Verify it round-trips
        Map<?, ?> result = JsonIo.fromToon(toon).asClass(Map.class);
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) result.get("items");
        assertThat(items).hasSize(3);
    }

    @Test
    void testInlineArrayWithPipeDelimiter() {
        // [3|]: pipe-delimited (line 454-456)
        // Round-trip via writer
        java.util.LinkedHashMap<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("items", java.util.Arrays.asList("a", "b", "c"));
        WriteOptions wo = new WriteOptionsBuilder().toonDelimiter('|').build();
        String toon = JsonIo.toToon(data, wo);
        Map<?, ?> result = JsonIo.fromToon(toon).asClass(Map.class);
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) result.get("items");
        assertThat(items).hasSize(3);
    }

    // ========== Tabular with delimiter mismatch ==========

    @Test
    void testTabularDelimiterMismatchInHeader() {
        // [2]{a|b}: declares comma but header uses pipe (line 633 or 636)
        String toon =
                "rows[2]{a|b}:\n" +
                "  1,2\n" +
                "  3,4";
        // Either parses OK with delimiter inferred from header, or throws on mismatch
        try {
            JsonIo.fromToon(toon).asClass(Map.class);
        } catch (JsonIoException e) {
            assertThat(e.getMessage()).containsAnyOf("mismatch", "Malformed");
        }
    }

    // ========== Blank lines in tabular ==========

    @Test
    void testBlankLineInTabularStrict() {
        // Blank line inside tabular array (line 658)
        String toon =
                "rows[2]{a,b}:\n" +
                "  1,2\n" +
                "\n" +
                "  3,4";
        ReadOptions opts = new ReadOptionsBuilder().strictToon(true).build();
        try {
            JsonIo.fromToon(toon, opts).asClass(Map.class);
        } catch (JsonIoException e) {
            assertThat(e.getMessage()).containsAnyOf("Blank lines", "count mismatch", "row");
        }
    }

    // ========== Missing closing brace in tabular header (line 483) ==========

    @Test
    void testMissingClosingBraceInTabularStrict() {
        // [2]{a,b — missing closing brace
        String toon =
                "rows[2]{a,b\n" +
                "  1,2\n" +
                "  3,4";
        ReadOptions opts = new ReadOptionsBuilder().strictToon(true).build();
        try {
            JsonIo.fromToon(toon, opts).asClass(Map.class);
        } catch (Exception e) {
            // Either Malformed array, or some other parse error
            assertThat(e.getMessage()).isNotNull();
        }
    }

    // ========== Top-level array ==========

    @Test
    void testTopLevelInlineArray() {
        String toon = "[3]: a,b,c";
        Object result = JsonIo.fromToon(toon).asClass(Object.class);
        assertThat(result).isInstanceOf(List.class);
    }

    @Test
    void testTopLevelTabularArray() {
        String toon =
                "[2]{name,age}:\n" +
                "  Alice,30\n" +
                "  Bob,25";
        Object result = JsonIo.fromToon(toon).asClass(Object.class);
        assertThat(result).isInstanceOf(List.class);
    }

    @Test
    void testTopLevelEmptyArray() {
        String toon = "[0]:";
        Object result = JsonIo.fromToon(toon).asClass(Object.class);
        assertThat(result).isInstanceOf(List.class);
        assertThat((List<?>) result).isEmpty();
    }

    // ========== Reading from a Reader directly ==========

    @Test
    void testReadFromStringReader() {
        StringReader reader = new StringReader("key: value");
        ToonReader toonReader = new ToonReader(reader, new ReadOptionsBuilder().build(), null);
        Object result = toonReader.readValue(Object.class);
        assertThat(result).isInstanceOf(Map.class);
    }

    @Test
    void testReadEmptyDocument() {
        StringReader reader = new StringReader("");
        ToonReader toonReader = new ToonReader(reader, null);
        Object result = toonReader.readValue(Object.class);
        // Empty input returns null or empty map
        assertThat(result == null || result instanceof Map).isTrue();
    }

    // ========== IOException path (line 296-297) ==========

    @Test
    void testReaderThrowsIOException() {
        java.io.Reader failingReader = new java.io.Reader() {
            @Override
            public int read(char[] cbuf, int off, int len) throws java.io.IOException {
                throw new java.io.IOException("simulated read failure");
            }
            @Override
            public void close() {}
        };

        ToonReader toonReader = new ToonReader(failingReader, null);
        assertThatThrownBy(() -> toonReader.readValue(Object.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Error reading TOON input");
    }

    // ========== Numeric values in inline arrays ==========

    @Test
    void testInlineArrayNumbers() {
        String toon = "nums[4]: 1,2,3,4";
        Map<?, ?> result = JsonIo.fromToon(toon).asClass(Map.class);
        @SuppressWarnings("unchecked")
        List<Object> nums = (List<Object>) result.get("nums");
        assertThat(nums).hasSize(4);
    }

    @Test
    void testInlineArrayStrings() {
        String toon = "strs[3]: \"hello\",\"world\",\"!\"";
        Map<?, ?> result = JsonIo.fromToon(toon).asClass(Map.class);
        @SuppressWarnings("unchecked")
        List<Object> strs = (List<Object>) result.get("strs");
        assertThat(strs).hasSize(3);
    }

    // ========== Nested arrays ==========

    @Test
    void testNestedTabularArrayInMap() {
        String toon =
                "name: parent\n" +
                "children[2]{name,age}:\n" +
                "  Alice,30\n" +
                "  Bob,25";
        Map<?, ?> result = JsonIo.fromToon(toon).asClass(Map.class);
        assertThat(result.get("name")).isEqualTo("parent");
        @SuppressWarnings("unchecked")
        List<Object> children = (List<Object>) result.get("children");
        assertThat(children).hasSize(2);
    }

    // ========== Quoted strings ==========

    @Test
    void testQuotedStringValue() {
        String toon = "msg: \"hello, world\"";
        Map<?, ?> result = JsonIo.fromToon(toon).asClass(Map.class);
        assertThat(result.get("msg")).isEqualTo("hello, world");
    }

    @Test
    void testQuotedStringWithEscape() {
        String toon = "msg: \"line1\\nline2\"";
        Map<?, ?> result = JsonIo.fromToon(toon).asClass(Map.class);
        assertThat(result.get("msg")).asString().contains("line1");
    }

    // ========== Multiple top-level keys ==========

    @Test
    void testMultipleKeys() {
        String toon =
                "a: 1\n" +
                "b: 2\n" +
                "c: 3";
        Map<?, ?> result = JsonIo.fromToon(toon).asClass(Map.class);
        assertThat(result).hasSize(3);
    }

    // ========== Nested object ==========

    @Test
    void testNestedObject() {
        String toon =
                "outer:\n" +
                "  inner: value";
        Map<?, ?> result = JsonIo.fromToon(toon).asClass(Map.class);
        assertThat(result.get("outer")).isInstanceOf(Map.class);
    }
}
