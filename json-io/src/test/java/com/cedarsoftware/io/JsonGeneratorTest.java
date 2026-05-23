package com.cedarsoftware.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for {@link JsonGenerator} / {@link CharStreamGenerator} and
 * the {@link JsonIo#createGenerator} factories. Validates the cursor-style
 * streaming-write contract:
 * <ul>
 *   <li>Scalar emission (int/long/float/double/BigInteger/BigDecimal/boolean/null/string).</li>
 *   <li>Auto-comma insertion across array elements and object fields.</li>
 *   <li>Convenience field-name+value helpers and array/object-start helpers.</li>
 *   <li>writeRaw vs writeRawValue (raw doesn't auto-comma; rawValue does).</li>
 *   <li>Structural misuse throws {@link JsonGenerationException}.</li>
 *   <li>Pretty-print, JSON5 unquoted keys, JSON5 single quotes, NaN/Infinity policy.</li>
 *   <li>OutputStream/Writer factory parity; close() flushes and releases resources.</li>
 *   <li>copyCurrentEvent / copyCurrentStructure round-trip through {@link JsonTokenizer}.</li>
 * </ul>
 */
class JsonGeneratorTest {

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    /** Generate JSON to a string using the given write options (null = defaults). */
    private static String emit(WriteOptions opts, Emit body) throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonGenerator g = JsonIo.createGenerator(sw, opts)) {
            body.run(g);
        }
        return sw.toString();
    }

    /** Default-options helper. */
    private static String emit(Emit body) throws IOException {
        return emit(null, body);
    }

    @FunctionalInterface
    interface Emit {
        void run(JsonGenerator g) throws IOException;
    }

    // -------------------------------------------------------------------
    // Scalars
    // -------------------------------------------------------------------

    @Test
    void writeNumber_int() throws IOException {
        assertEquals("42", emit(g -> g.writeNumber(42)));
    }

    @Test
    void writeNumber_long() throws IOException {
        assertEquals("9223372036854775807", emit(g -> g.writeNumber(Long.MAX_VALUE)));
    }

    // -------------------------------------------------------------------
    // Integer / long digit-pair emission edge cases (the writeIntRaw /
    // writeLongRaw fast path used by gen.writeNumber(int/long) and by
    // JsonWriter's internal @id/@ref/numeric-field emission). Exhaustive
    // coverage of the algorithm's tricky points: zero, sign boundaries,
    // MIN_VALUE (requires working in negative space throughout), digit-
    // pair loop entry/exit, single-digit residues.
    // -------------------------------------------------------------------

    @Test
    void writeNumber_int_zero() throws IOException {
        assertEquals("0", emit(g -> g.writeNumber(0)));
    }

    @Test
    void writeNumber_int_positiveOne() throws IOException {
        assertEquals("1", emit(g -> g.writeNumber(1)));
    }

    @Test
    void writeNumber_int_negativeOne() throws IOException {
        assertEquals("-1", emit(g -> g.writeNumber(-1)));
    }

    @Test
    void writeNumber_int_doubleDigit() throws IOException {
        assertEquals("99", emit(g -> g.writeNumber(99)));
    }

    @Test
    void writeNumber_int_tripleDigit() throws IOException {
        // Crosses the digit-pair loop boundary (|value| >= 100 enters the loop).
        assertEquals("100", emit(g -> g.writeNumber(100)));
        assertEquals("101", emit(g -> g.writeNumber(101)));
        assertEquals("-100", emit(g -> g.writeNumber(-100)));
        assertEquals("-101", emit(g -> g.writeNumber(-101)));
    }

    @Test
    void writeNumber_int_maxValue() throws IOException {
        assertEquals("2147483647", emit(g -> g.writeNumber(Integer.MAX_VALUE)));
    }

    @Test
    void writeNumber_int_minValue() throws IOException {
        // MIN_VALUE has no positive counterpart; algorithm must work in negative
        // space throughout to avoid overflow on the `value = -value` step.
        assertEquals("-2147483648", emit(g -> g.writeNumber(Integer.MIN_VALUE)));
    }

    @Test
    void writeNumber_long_zero() throws IOException {
        assertEquals("0", emit(g -> g.writeNumber(0L)));
    }

    @Test
    void writeNumber_long_negativeOne() throws IOException {
        assertEquals("-1", emit(g -> g.writeNumber(-1L)));
    }

    @Test
    void writeNumber_long_minValue() throws IOException {
        // MIN_VALUE is the canonical test for the negative-space algorithm.
        // Bug history: an earlier int-truncation of the q*100-value term produced
        // -206158430208 instead of -9223372036854775808 -- regression-anchor here.
        assertEquals("-9223372036854775808", emit(g -> g.writeNumber(Long.MIN_VALUE)));
    }

    @Test
    void writeNumber_long_aroundDigitPairBoundary() throws IOException {
        assertEquals("99", emit(g -> g.writeNumber(99L)));
        assertEquals("100", emit(g -> g.writeNumber(100L)));
        assertEquals("-99", emit(g -> g.writeNumber(-99L)));
        assertEquals("-100", emit(g -> g.writeNumber(-100L)));
        assertEquals("9999", emit(g -> g.writeNumber(9999L)));
        assertEquals("10000", emit(g -> g.writeNumber(10000L)));
    }

    @Test
    void writeNumber_double() throws IOException {
        assertEquals("1.5", emit(g -> g.writeNumber(1.5)));
    }

    @Test
    void writeNumber_float() throws IOException {
        assertEquals("1.5", emit(g -> g.writeNumber(1.5f)));
    }

    @Test
    void writeNumber_bigInteger_unquoted() throws IOException {
        BigInteger big = new BigInteger("123456789012345678901234567890");
        assertEquals("123456789012345678901234567890",
                emit(g -> g.writeNumber(big)));
    }

    @Test
    void writeNumber_bigDecimal_stripTrailingZerosAndUnquoted() throws IOException {
        // BigDecimal("1.2300") preserves scale=4; we emit canonical form 1.23.
        assertEquals("1.23", emit(g -> g.writeNumber(new BigDecimal("1.2300"))));
        // Plain integer value retains as plain form (no scientific).
        assertEquals("100", emit(g -> g.writeNumber(new BigDecimal("100.00"))));
    }

    @Test
    void writeNumber_string_passthroughVerbatim() throws IOException {
        // Trusted pre-formatted form; library does not validate.
        assertEquals("3.14e10", emit(g -> g.writeNumber("3.14e10")));
    }

    @Test
    void writeBoolean() throws IOException {
        assertEquals("true", emit(g -> g.writeBoolean(true)));
        assertEquals("false", emit(g -> g.writeBoolean(false)));
    }

    @Test
    void writeNull() throws IOException {
        assertEquals("null", emit(JsonGenerator::writeNull));
    }

    @Test
    void writeString_basic() throws IOException {
        assertEquals("\"hello\"", emit(g -> g.writeString("hello")));
    }

    @Test
    void writeString_withEscapes() throws IOException {
        // Tab + quote + backslash should be escaped.
        String out = emit(g -> g.writeString("a\t\"b\\c"));
        assertEquals("\"a\\t\\\"b\\\\c\"", out);
    }

    @Test
    void writeString_nullEmitsJsonNull() throws IOException {
        assertEquals("null", emit(g -> g.writeString((String) null)));
    }

    @Test
    void writeString_charArraySlice() throws IOException {
        char[] buf = "_hello_".toCharArray();
        assertEquals("\"hello\"", emit(g -> g.writeString(buf, 1, 5)));
    }

    @Test
    void writeNumber_bigDecimalNull_emitsJsonNull() throws IOException {
        assertEquals("null", emit(g -> g.writeNumber((BigDecimal) null)));
    }

    @Test
    void writeNumber_bigIntegerNull_emitsJsonNull() throws IOException {
        assertEquals("null", emit(g -> g.writeNumber((BigInteger) null)));
    }

    @Test
    void writeNumber_stringNull_emitsJsonNull() throws IOException {
        assertEquals("null", emit(g -> g.writeNumber((String) null)));
    }

    // -------------------------------------------------------------------
    // Nested structures + auto-comma
    // -------------------------------------------------------------------

    @Test
    void emptyObject() throws IOException {
        assertEquals("{}", emit(g -> g.writeStartObject().writeEndObject()));
    }

    @Test
    void emptyArray() throws IOException {
        assertEquals("[]", emit(g -> g.writeStartArray().writeEndArray()));
    }

    @Test
    void object_withTwoFields_autoCommaBetween() throws IOException {
        String out = emit(g -> g.writeStartObject()
                .writeFieldName("a").writeNumber(1)
                .writeFieldName("b").writeNumber(2)
                .writeEndObject());
        assertEquals("{\"a\":1,\"b\":2}", out);
    }

    @Test
    void array_withThreeScalars_autoCommaBetween() throws IOException {
        String out = emit(g -> g.writeStartArray()
                .writeNumber(1).writeNumber(2).writeNumber(3)
                .writeEndArray());
        assertEquals("[1,2,3]", out);
    }

    @Test
    void nested_objectInsideArray() throws IOException {
        String out = emit(g -> g.writeStartArray()
                .writeStartObject().writeStringField("k", "v").writeEndObject()
                .writeStartObject().writeStringField("k2", "v2").writeEndObject()
                .writeEndArray());
        assertEquals("[{\"k\":\"v\"},{\"k2\":\"v2\"}]", out);
    }

    @Test
    void nested_arrayInsideObject() throws IOException {
        String out = emit(g -> g.writeStartObject()
                .writeArrayFieldStart("xs")
                    .writeNumber(1).writeNumber(2)
                .writeEndArray()
                .writeEndObject());
        assertEquals("{\"xs\":[1,2]}", out);
    }

    @Test
    void deeplyNested_alternatingObjectAndArray() throws IOException {
        String out = emit(g -> g.writeStartObject()
                .writeObjectFieldStart("a")
                    .writeArrayFieldStart("b")
                        .writeStartObject().writeNumberField("c", 7).writeEndObject()
                    .writeEndArray()
                .writeEndObject()
                .writeEndObject());
        assertEquals("{\"a\":{\"b\":[{\"c\":7}]}}", out);
    }

    // -------------------------------------------------------------------
    // Convenience field-combo helpers
    // -------------------------------------------------------------------

    @Test
    void writeStringField() throws IOException {
        String out = emit(g -> g.writeStartObject()
                .writeStringField("name", "Alice")
                .writeEndObject());
        assertEquals("{\"name\":\"Alice\"}", out);
    }

    @Test
    void writeNumberField_allOverloads() throws IOException {
        String out = emit(g -> g.writeStartObject()
                .writeNumberField("i", 1)
                .writeNumberField("l", 2L)
                .writeNumberField("d", 3.5)
                .writeNumberField("bd", new BigDecimal("4.250"))
                .writeEndObject());
        assertEquals("{\"i\":1,\"l\":2,\"d\":3.5,\"bd\":4.25}", out);
    }

    @Test
    void writeBooleanField_andNullField() throws IOException {
        String out = emit(g -> g.writeStartObject()
                .writeBooleanField("ok", true)
                .writeNullField("note")
                .writeEndObject());
        assertEquals("{\"ok\":true,\"note\":null}", out);
    }

    // -------------------------------------------------------------------
    // writeRaw vs writeRawValue
    // -------------------------------------------------------------------

    @Test
    void writeRaw_doesNotAutoComma_doesNotMarkValue() throws IOException {
        // writeRaw injects chars verbatim; the surrounding context is unchanged.
        // Here we manually craft a JSON snippet using writeRaw.
        String out = emit(g -> g.writeStartArray()
                .writeRaw('1')
                .writeRaw(',')
                .writeRaw('2')
                .writeEndArray());
        assertEquals("[1,2]", out);
    }

    @Test
    void writeRawValue_countsAsValue_autoCommas() throws IOException {
        // writeRawValue is treated as a complete value — emits leading comma in array.
        String out = emit(g -> g.writeStartArray()
                .writeRawValue("{\"k\":1}")
                .writeRawValue("[2]")
                .writeEndArray());
        assertEquals("[{\"k\":1},[2]]", out);
    }

    @Test
    void writeRawValue_null_emitsJsonNull() throws IOException {
        assertEquals("null", emit(g -> g.writeRawValue(null)));
    }

    @Test
    void writeRaw_String_charArraySlice() throws IOException {
        // writeRaw(char[], off, len) — drop slice verbatim.
        String out = emit(g -> g.writeRaw("[".toCharArray(), 0, 1)
                .writeRaw('1')
                .writeRaw("]"));
        assertEquals("[1]", out);
    }

    // -------------------------------------------------------------------
    // Structural-misuse: JsonGenerationException
    // -------------------------------------------------------------------

    @Test
    void error_fieldName_outsideObject() {
        assertThatThrownBy(() -> emit(g -> g.writeStartArray().writeFieldName("x")))
                .isInstanceOf(JsonGenerationException.class)
                .hasMessageContaining("Cannot write field name outside an object context");
    }

    @Test
    void error_endArray_overObject() {
        assertThatThrownBy(() -> emit(g -> g.writeStartObject().writeEndArray()))
                .isInstanceOf(JsonGenerationException.class)
                .hasMessageContaining("Cannot end array");
    }

    @Test
    void error_endObject_overArray() {
        assertThatThrownBy(() -> emit(g -> g.writeStartArray().writeEndObject()))
                .isInstanceOf(JsonGenerationException.class)
                .hasMessageContaining("Cannot end object");
    }

    @Test
    void error_doubleFieldName_withoutValue() {
        assertThatThrownBy(() -> emit(g -> g.writeStartObject()
                .writeFieldName("a").writeFieldName("b")))
                .isInstanceOf(JsonGenerationException.class)
                .hasMessageContaining("previous field name is still pending a value");
    }

    @Test
    void error_endObject_withPendingFieldName() {
        assertThatThrownBy(() -> emit(g -> g.writeStartObject()
                .writeFieldName("a").writeEndObject()))
                .isInstanceOf(JsonGenerationException.class)
                .hasMessageContaining("field name written without a matching value");
    }

    @Test
    void error_value_inObject_withoutFieldName() {
        assertThatThrownBy(() -> emit(g -> g.writeStartObject().writeNumber(1)))
                .isInstanceOf(JsonGenerationException.class)
                .hasMessageContaining("without a preceding field name");
    }

    @Test
    void error_twoRootValues() {
        assertThatThrownBy(() -> emit(g -> g.writeNumber(1).writeNumber(2)))
                .isInstanceOf(JsonGenerationException.class)
                .hasMessageContaining("document already complete");
    }

    @Test
    void error_writeFieldName_null() {
        assertThatThrownBy(() -> emit(g -> g.writeStartObject().writeFieldName(null)))
                .isInstanceOf(JsonGenerationException.class)
                .hasMessageContaining("Field name must not be null");
    }

    // -------------------------------------------------------------------
    // Pretty printing
    // -------------------------------------------------------------------

    @Test
    void prettyPrint_objectWithFields() throws IOException {
        WriteOptions opts = new WriteOptionsBuilder().prettyPrint(true).build();
        String out = emit(opts, g -> g.writeStartObject()
                .writeStringField("a", "x")
                .writeNumberField("b", 1)
                .writeEndObject());
        // Standard pretty: newline + indent before each field, newline + indent before close.
        assertThat(out).contains("\n").contains("\"a\"").contains("\"b\":");
        assertThat(out).startsWith("{");
        assertThat(out).endsWith("}");
        // Ensures multi-line layout (more than one newline).
        long newlineCount = out.chars().filter(c -> c == '\n').count();
        assertTrue(newlineCount >= 3, "expected at least 3 newlines, got: " + newlineCount + " in " + out);
    }

    @Test
    void prettyPrint_emptyContainers_noInternalNewline() throws IOException {
        WriteOptions opts = new WriteOptionsBuilder().prettyPrint(true).build();
        assertEquals("{}", emit(opts, g -> g.writeStartObject().writeEndObject()));
        assertEquals("[]", emit(opts, g -> g.writeStartArray().writeEndArray()));
    }

    // -------------------------------------------------------------------
    // JSON5 features
    // -------------------------------------------------------------------

    @Test
    void json5UnquotedKeys_validIdentifier() throws IOException {
        WriteOptions opts = new WriteOptionsBuilder().json5UnquotedKeys(true).build();
        String out = emit(opts, g -> g.writeStartObject()
                .writeStringField("name", "Alice")
                .writeEndObject());
        assertEquals("{name:\"Alice\"}", out);
    }

    @Test
    void json5UnquotedKeys_invalidIdentifier_stillQuoted() throws IOException {
        WriteOptions opts = new WriteOptionsBuilder().json5UnquotedKeys(true).build();
        // A key starting with a digit is NOT a valid JSON5 identifier.
        String out = emit(opts, g -> g.writeStartObject()
                .writeStringField("1bad", "x")
                .writeEndObject());
        assertEquals("{\"1bad\":\"x\"}", out);
    }

    @Test
    void writeStringFieldUnescaped_emitsRawValueSkippingEscapeScan() throws IOException {
        // Package-private fast path used by JsonWriter for @type field emission where
        // the value is a known-safe Java type alias / class name. The helper is on the
        // concrete class — exercise it through JsonIo's createGenerator(...) so the
        // test stays on the public surface, then cast to CharStreamGenerator (legal
        // within the same package).
        StringWriter sw = new StringWriter();
        try (JsonGenerator g = JsonIo.createGenerator(sw)) {
            CharStreamGenerator concrete = (CharStreamGenerator) g;
            concrete.writeStartObject();
            concrete.writeStringFieldUnescaped("@type", "com.example.Foo");
            concrete.writeStringFieldUnescaped("@id", "42");
            concrete.writeEndObject();
        }
        // State machine engaged correctly: auto-comma between fields, structural close.
        assertEquals("{\"@type\":\"com.example.Foo\",\"@id\":\"42\"}", sw.toString());
    }

    @Test
    void writeFieldNameRaw_emitsPreformattedKeyAndTransitionsState() throws IOException {
        // Package-private fast path for caller-precomputed key+colon strings (used by
        // JsonWriter for the @items / @keys meta-keys where the full key+colon is
        // precomputed at construction with the right quoting/prefix variant). Skips
        // gen's quote-decision + escape-scan. Does NOT emit a separator — the caller
        // has emitted any leading comma manually before calling.
        StringWriter sw = new StringWriter();
        try (JsonGenerator g = JsonIo.createGenerator(sw)) {
            CharStreamGenerator concrete = (CharStreamGenerator) g;
            concrete.writeStartObject();
            concrete.writeFieldNameRaw("\"@id\":");
            concrete.writeNumber(42);
            // Subsequent field — caller emits the leading comma manually (matching
            // the precomputed-prefix pattern), then the raw key, then the value.
            concrete.writeRaw(",");
            concrete.writeFieldNameRaw("\"@type\":");
            concrete.writeString("Foo");
            concrete.writeEndObject();
        }
        assertEquals("{\"@id\":42,\"@type\":\"Foo\"}", sw.toString());
    }

    @Test
    void writeStringFieldUnescaped_nullEmitsJsonNull() throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonGenerator g = JsonIo.createGenerator(sw)) {
            CharStreamGenerator concrete = (CharStreamGenerator) g;
            concrete.writeStartObject();
            concrete.writeStringFieldUnescaped("k", null);
            concrete.writeEndObject();
        }
        assertEquals("{\"k\":null}", sw.toString());
    }

    @Test
    void json5SmartQuotes_doesNotApplyToKeys() throws IOException {
        // Keys are double-quoted (or unquoted identifier), NEVER single-quoted, even when
        // json5SmartQuotes is on. Smart quoting applies to string values only — matches
        // Jackson convention and JsonWriter.writeKey behavior. The "@id" key (used by
        // json-io for object identity) is not a valid JSON5 identifier (starts with @),
        // so it always gets double-quoted regardless of json5UnquotedKeys mode.
        WriteOptions opts = new WriteOptionsBuilder()
                .json5SmartQuotes(true)
                .json5UnquotedKeys(true)
                .build();
        String out = emit(opts, g -> g.writeStartObject()
                .writeStringField("@id", "v")
                .writeEndObject());
        assertEquals("{\"@id\":\"v\"}", out);
    }

    @Test
    void json5SmartQuotes_plainString_usesDoubleQuotes() throws IOException {
        // "Smart" quote selection: when the string contains no double-quote characters,
        // double quotes are the default — no benefit from switching to single quotes.
        WriteOptions opts = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        assertEquals("\"hi\"", emit(opts, g -> g.writeString("hi")));
    }

    @Test
    void json5SmartQuotes_stringWithDoubleQuotes_usesSingleQuotes() throws IOException {
        // Single quotes minimize escaping when the string contains " but no '.
        WriteOptions opts = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        assertEquals("'has \"x\" inside'", emit(opts, g -> g.writeString("has \"x\" inside")));
    }

    @Test
    void json5SmartQuotes_stringWithSingleQuotes_usesDoubleQuotes() throws IOException {
        // Double quotes minimize escaping when the string contains ' but no ".
        WriteOptions opts = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        assertEquals("\"don't\"", emit(opts, g -> g.writeString("don't")));
    }

    @Test
    void json5SmartQuotes_stringWithBothQuoteTypes_usesDoubleQuotes() throws IOException {
        // Both styles require escaping — default to double quotes (standard form).
        WriteOptions opts = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        assertEquals("\"can't say \\\"hi\\\"\"", emit(opts, g -> g.writeString("can't say \"hi\"")));
    }

    @Test
    void json5SmartQuotes_emptyString_usesDoubleQuotes() throws IOException {
        // Empty strings have nothing to escape — pick the default double-quote form.
        WriteOptions opts = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        assertEquals("\"\"", emit(opts, g -> g.writeString("")));
    }

    @Test
    void json5SmartQuotes_matchesJsonWriterWriteStringValue() throws IOException {
        // The CharStreamGenerator's writeString smart selection MUST match
        // JsonWriter.writeStringValue's behavior so the two emission paths
        // produce identical json5SmartQuotes output. This is the foundational
        // invariant for dog-fooding JsonWriter onto CharStreamGenerator.
        WriteOptions opts = new WriteOptionsBuilder().json5SmartQuotes(true).build();
        String[] samples = {
                "hello",
                "has \"x\" inside",
                "don't",
                "can't say \"hi\"",
                "",
                "tab\there",
                "newline\nhere"
        };
        for (String s : samples) {
            // gen path
            String viaGen = emit(opts, g -> g.writeString(s));
            // JsonIo.toJson path (uses JsonWriter.writeStringValue under the hood)
            String viaJsonIo = JsonIo.toJson(s, opts);
            assertEquals(viaJsonIo, viaGen,
                    "json5SmartQuotes output diverged for sample: " + s);
        }
    }

    // -------------------------------------------------------------------
    // NaN/Infinity policy
    // -------------------------------------------------------------------

    @Test
    void allowNanAndInfinity_emitsLiterals() throws IOException {
        WriteOptions opts = new WriteOptionsBuilder().allowNanAndInfinity(true).build();
        assertEquals("NaN", emit(opts, g -> g.writeNumber(Double.NaN)));
        assertEquals("Infinity", emit(opts, g -> g.writeNumber(Double.POSITIVE_INFINITY)));
        assertEquals("-Infinity", emit(opts, g -> g.writeNumber(Double.NEGATIVE_INFINITY)));
    }

    @Test
    void allowNanAndInfinity_false_throws() {
        WriteOptions opts = new WriteOptionsBuilder().allowNanAndInfinity(false).build();
        assertThatThrownBy(() -> emit(opts, g -> g.writeNumber(Double.NaN)))
                .isInstanceOf(JsonGenerationException.class)
                .hasMessageContaining("non-finite double");
    }

    @Test
    void allowNanAndInfinity_false_throws_float() {
        WriteOptions opts = new WriteOptionsBuilder().allowNanAndInfinity(false).build();
        assertThatThrownBy(() -> emit(opts, g -> g.writeNumber(Float.POSITIVE_INFINITY)))
                .isInstanceOf(JsonGenerationException.class)
                .hasMessageContaining("non-finite float");
    }

    @Test
    void json5InfinityNaN_alone_emitsLiterals() throws IOException {
        // json5InfinityNaN is the JSON5-spec flag for permitting NaN/Infinity literals.
        // The generator must treat it as equivalent to allowNanAndInfinity so that a
        // caller setting only the JSON5 flag still gets the literals emitted (matches
        // JsonWriter.isNanInfinityAllowed() which is the union of both flags).
        WriteOptions opts = new WriteOptionsBuilder().json5InfinityNaN(true).build();
        assertEquals("NaN", emit(opts, g -> g.writeNumber(Double.NaN)));
        assertEquals("Infinity", emit(opts, g -> g.writeNumber(Double.POSITIVE_INFINITY)));
        assertEquals("-Infinity", emit(opts, g -> g.writeNumber(Double.NEGATIVE_INFINITY)));
    }

    @Test
    void json5InfinityNaN_alone_emitsLiterals_float() throws IOException {
        WriteOptions opts = new WriteOptionsBuilder().json5InfinityNaN(true).build();
        assertEquals("NaN", emit(opts, g -> g.writeNumber(Float.NaN)));
        assertEquals("Infinity", emit(opts, g -> g.writeNumber(Float.POSITIVE_INFINITY)));
        assertEquals("-Infinity", emit(opts, g -> g.writeNumber(Float.NEGATIVE_INFINITY)));
    }

    @Test
    void bothNanInfinityFlags_emitLiterals() throws IOException {
        // Both flags set — emission still produces the literals (no double-negation
        // or short-circuit weirdness).
        WriteOptions opts = new WriteOptionsBuilder()
                .allowNanAndInfinity(true)
                .json5InfinityNaN(true)
                .build();
        assertEquals("NaN", emit(opts, g -> g.writeNumber(Double.NaN)));
        assertEquals("Infinity", emit(opts, g -> g.writeNumber(Double.POSITIVE_INFINITY)));
    }

    // -------------------------------------------------------------------
    // Factory parity: OutputStream vs Writer produce identical output
    // -------------------------------------------------------------------

    @Test
    void factoryParity_outputStreamMatchesWriter() throws IOException {
        StringWriter writerSink = new StringWriter();
        ByteArrayOutputStream osSink = new ByteArrayOutputStream();

        try (JsonGenerator g = JsonIo.createGenerator(writerSink)) {
            g.writeStartObject().writeStringField("k", "v").writeEndObject();
        }
        try (JsonGenerator g = JsonIo.createGenerator(osSink)) {
            g.writeStartObject().writeStringField("k", "v").writeEndObject();
        }
        assertEquals(writerSink.toString(), osSink.toString("UTF-8"));
    }

    @Test
    void factory_nullOutputStream_throws() {
        assertThatThrownBy(() -> JsonIo.createGenerator((java.io.OutputStream) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void factory_nullWriter_throws() {
        assertThatThrownBy(() -> JsonIo.createGenerator((java.io.Writer) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------
    // close() flushes underlying writer
    // -------------------------------------------------------------------

    @Test
    void close_flushesUnderlyingWriter() throws IOException {
        // A custom sink that records flush.
        final boolean[] flushed = {false};
        StringWriter base = new StringWriter() {
            @Override
            public void flush() {
                flushed[0] = true;
                super.flush();
            }
        };
        try (JsonGenerator g = JsonIo.createGenerator(base)) {
            g.writeStartObject().writeStringField("k", "v").writeEndObject();
        }
        assertTrue(flushed[0], "underlying writer should have been flushed on close");
    }

    @Test
    void close_isIdempotent() throws IOException {
        StringWriter sw = new StringWriter();
        JsonGenerator g = JsonIo.createGenerator(sw);
        g.writeStartObject().writeEndObject();
        g.close();
        g.close(); // second close must not throw
    }

    // -------------------------------------------------------------------
    // Bridge: copyCurrentEvent / copyCurrentStructure (tokenizer → generator)
    // -------------------------------------------------------------------

    @Test
    void copyCurrentStructure_object() throws IOException {
        String source = "{\"id\":\"u-1\",\"age\":30,\"flag\":true,\"empty\":null,\"tags\":[\"a\",\"b\"]}";
        StringWriter sink = new StringWriter();
        try (JsonTokenizer t = JsonIo.createTokenizer(source);
             JsonGenerator g = JsonIo.createGenerator(sink)) {
            t.nextToken(); // position on START_OBJECT
            g.copyCurrentStructure(t);
        }
        assertEquals(source, sink.toString());
    }

    @Test
    void copyCurrentStructure_arrayOfScalars() throws IOException {
        String source = "[1,2,3,\"four\",true,null,1.5]";
        StringWriter sink = new StringWriter();
        try (JsonTokenizer t = JsonIo.createTokenizer(source);
             JsonGenerator g = JsonIo.createGenerator(sink)) {
            t.nextToken();
            g.copyCurrentStructure(t);
        }
        assertEquals(source, sink.toString());
    }

    @Test
    void copyCurrentStructure_nestedDeeplyMixed() throws IOException {
        String source = "{\"a\":{\"b\":[{\"c\":7},{\"d\":[1,[2,3]]}]}}";
        StringWriter sink = new StringWriter();
        try (JsonTokenizer t = JsonIo.createTokenizer(source);
             JsonGenerator g = JsonIo.createGenerator(sink)) {
            t.nextToken();
            g.copyCurrentStructure(t);
        }
        assertEquals(source, sink.toString());
    }

    @Test
    void copyCurrentEvent_singleScalar() throws IOException {
        StringWriter sink = new StringWriter();
        try (JsonTokenizer t = JsonIo.createTokenizer("42");
             JsonGenerator g = JsonIo.createGenerator(sink)) {
            t.nextToken();
            g.copyCurrentEvent(t);
        }
        assertEquals("42", sink.toString());
    }

    @Test
    void copyCurrentEvent_nullSourceToken_throws() {
        StringWriter sink = new StringWriter();
        assertThatThrownBy(() -> {
            try (JsonTokenizer t = JsonIo.createTokenizer("1");
                 JsonGenerator g = JsonIo.createGenerator(sink)) {
                // No t.nextToken() — currentToken() is null
                g.copyCurrentEvent(t);
            }
        }).isInstanceOf(JsonGenerationException.class)
          .hasMessageContaining("source has no current token");
    }

    // -------------------------------------------------------------------
    // OutputStream variant uses UTF-8 encoding correctly
    // -------------------------------------------------------------------

    @Test
    void outputStream_utf8RoundTrip_nonAscii() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (JsonGenerator g = JsonIo.createGenerator(bos)) {
            g.writeString("héllo★");
        }
        // Expected output reads back through UTF-8 cleanly.
        String result = new String(bos.toByteArray(), StandardCharsets.UTF_8);
        // Non-ASCII characters in JSON strings may be escaped depending on
        // writer policy; either form is acceptable as long as the JSON
        // round-trips to the same logical value.
        try (JsonTokenizer t = JsonIo.createTokenizer(result)) {
            t.nextToken();
            assertEquals("héllo★", t.getText());
        }
    }

    // -------------------------------------------------------------------
    // writeObject / writeObjectField — databind entry points for embedding
    // arbitrary POJOs in a hand-rolled streaming sequence
    // -------------------------------------------------------------------

    public static class Person {
        public String name;
        public int age;

        public Person() {
        }

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    @Test
    void writeObject_topLevelPojo_roundTripsViaJsonIo() throws IOException {
        Person p = new Person("Alice", 30);
        StringWriter sw = new StringWriter();
        try (JsonGenerator g = JsonIo.createGenerator(sw)) {
            g.writeObject(p);
        }
        String json = sw.toString();
        Person round = JsonIo.toJava(json, null).asClass(Person.class);
        assertEquals("Alice", round.name);
        assertEquals(30, round.age);
    }

    @Test
    void writeObject_null_emitsJsonNull() throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonGenerator g = JsonIo.createGenerator(sw)) {
            g.writeObject(null);
        }
        assertEquals("null", sw.toString());
    }

    @Test
    void writeObject_insideObjectBody_afterFieldName_emitsAsValue() throws IOException {
        Person p = new Person("Bob", 42);
        StringWriter sw = new StringWriter();
        try (JsonGenerator g = JsonIo.createGenerator(sw)) {
            g.writeStartObject()
                .writeStringField("kind", "user")
                .writeFieldName("person");
            g.writeObject(p);
            g.writeEndObject();
        }
        String json = sw.toString();
        // The outer hand-rolled object frames the writeObject output as a field value.
        assertTrue(json.startsWith("{"), "got: " + json);
        assertTrue(json.endsWith("}"), "got: " + json);
        assertTrue(json.contains("\"kind\":\"user\""), "got: " + json);
        assertTrue(json.contains("\"person\":"), "got: " + json);
        assertTrue(json.contains("\"name\":\"Bob\""), "got: " + json);
        assertTrue(json.contains("\"age\":42"), "got: " + json);
    }

    @Test
    void writeObject_insideArray_autocommasAcrossMultiplePojos() throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonGenerator g = JsonIo.createGenerator(sw)) {
            g.writeStartArray();
            g.writeObject(new Person("A", 1));
            g.writeObject(new Person("B", 2));
            g.writeObject(new Person("C", 3));
            g.writeEndArray();
        }
        String json = sw.toString();
        // Auto-comma between writeObject calls inside the array — no malformed JSON
        assertTrue(json.startsWith("["), "got: " + json);
        assertTrue(json.endsWith("]"), "got: " + json);
        // At a minimum, two element-separator commas
        long commaCount = json.chars().filter(c -> c == ',').count();
        assertTrue(commaCount >= 2, "expected at least 2 separator commas, got: " + json);
        // All three round-trip
        Person[] round = JsonIo.toJava(json, null).asClass(Person[].class);
        assertEquals(3, round.length);
        assertEquals("A", round[0].name);
        assertEquals("B", round[1].name);
        assertEquals("C", round[2].name);
    }

    @Test
    void writeObject_underShowTypeInfoNever_omitsTypeTag() throws IOException {
        Person p = new Person("Carol", 50);
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        StringWriter sw = new StringWriter();
        try (JsonGenerator g = JsonIo.createGenerator(sw, opts)) {
            g.writeObject(p);
        }
        String json = sw.toString();
        assertTrue(!json.contains("\"@type\""),
                "showTypeInfoNever() should suppress @type, got: " + json);
        assertTrue(json.contains("\"name\":\"Carol\""), "got: " + json);
    }

    @Test
    void writeObjectField_combinesNameAndObject() throws IOException {
        Person p = new Person("Dave", 25);
        StringWriter sw = new StringWriter();
        try (JsonGenerator g = JsonIo.createGenerator(sw)) {
            g.writeStartObject();
            g.writeObjectField("person", p);
            g.writeEndObject();
        }
        String json = sw.toString();
        assertTrue(json.contains("\"person\":"), "got: " + json);
        assertTrue(json.contains("\"name\":\"Dave\""), "got: " + json);
        assertTrue(json.contains("\"age\":25"), "got: " + json);
    }

    @Test
    void writeObjectField_null_emitsFieldWithJsonNull() throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonGenerator g = JsonIo.createGenerator(sw)) {
            g.writeStartObject();
            g.writeObjectField("missing", null);
            g.writeEndObject();
        }
        assertEquals("{\"missing\":null}", sw.toString());
    }

    @Test
    void writeObject_sideBySide_defaultVsShowTypeInfoNever_producesDistinctJson() throws IOException {
        // Regression-anchor parallel to the writeBinary side-by-side test: same
        // input, same emission code, only WriteOptions differs. Confirms writeObject
        // is actually reading the ShowType policy through getWriteOptions().
        Person p = new Person("Eve", 60);

        StringWriter defaultOut = new StringWriter();
        try (JsonGenerator g = JsonIo.createGenerator(defaultOut)) {
            g.writeObject(p);
        }
        String defaultJson = defaultOut.toString();

        WriteOptions neverOpts = new WriteOptionsBuilder().showTypeInfoNever().build();
        StringWriter neverOut = new StringWriter();
        try (JsonGenerator g = JsonIo.createGenerator(neverOut, neverOpts)) {
            g.writeObject(p);
        }
        String neverJson = neverOut.toString();

        // Both must contain the data
        assertTrue(defaultJson.contains("\"name\":\"Eve\""), "default got: " + defaultJson);
        assertTrue(neverJson.contains("\"name\":\"Eve\""), "never got: " + neverJson);
        // Only the never variant omits @type — proves the policy was honored
        assertTrue(!neverJson.contains("\"@type\""),
                "showTypeInfoNever() should suppress @type, got: " + neverJson);
    }
}
