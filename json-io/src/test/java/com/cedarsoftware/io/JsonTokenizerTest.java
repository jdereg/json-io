package com.cedarsoftware.io;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.cedarsoftware.util.FastReader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Isolation tests for {@link CharStreamTokenizer} and the cursor API on
 * {@link JsonTokenizer}. The existing 3,000+ JsonParser tests still validate
 * end-to-end parsing through {@link JsonParser}; these tests prove that the
 * tokenizer can be exercised standalone with the right token sequence,
 * payloads, and error semantics.
 *
 * <p>Phase A commit 2 — JsonParser still owns its own copy of the tokenization
 * helpers. Commit 3 will retire those copies and route JsonParser through this
 * tokenizer.
 */
class JsonTokenizerTest {

    /** Default permissive tokenizer (JSON5 features enabled). */
    private static CharStreamTokenizer tokenizer(String json) {
        return new CharStreamTokenizer(
                new FastReader(new StringReader(json)),
                false,   // strictJson
                true,    // allowNanAndInfinity
                false,   // integerTypeBigInteger
                false,   // integerTypeBoth
                false,   // floatingPointBigDecimal
                false,   // floatingPointBoth
                256,     // stringBufferSize
                "test"); // sourceRef
    }

    private static CharStreamTokenizer strictTokenizer(String json) {
        return new CharStreamTokenizer(
                new FastReader(new StringReader(json)),
                true,    // strictJson
                false,   // allowNanAndInfinity
                false, false, false, false,
                256,
                "test");
    }

    private static CharStreamTokenizer bigIntegerTokenizer(String json) {
        return new CharStreamTokenizer(
                new FastReader(new StringReader(json)),
                false, true,
                true,    // integerTypeBigInteger
                false, false, false,
                256,
                "test");
    }

    private static CharStreamTokenizer bigDecimalTokenizer(String json) {
        return new CharStreamTokenizer(
                new FastReader(new StringReader(json)),
                false, true,
                false, false,
                true,    // floatingPointBigDecimal
                false,
                256,
                "test");
    }

    // ------------------------------------------------------------------
    // EOF / empty input
    // ------------------------------------------------------------------

    @Test
    void emptyInputReturnsNull() throws IOException {
        CharStreamTokenizer t = tokenizer("");
        assertNull(t.nextToken());
        assertNull(t.currentToken());
    }

    @Test
    void whitespaceOnlyInputReturnsNull() throws IOException {
        CharStreamTokenizer t = tokenizer("   \t\n\r  ");
        assertNull(t.nextToken());
    }

    @Test
    void afterRootValueNextTokenIsNull() throws IOException {
        CharStreamTokenizer t = tokenizer("42");
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertNull(t.nextToken());
        assertNull(t.nextToken()); // sticky
    }

    @Test
    void currentToken_isNullAfterEof() throws IOException {
        // Per JsonTokenizer.currentToken() contract: returns null if the cursor
        // has not advanced yet OR is at EOF. The previous implementation left
        // currentToken holding the last-emitted value after EOF was detected,
        // which contradicted the contract.
        CharStreamTokenizer t = tokenizer("42");
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.currentToken());
        assertNull(t.nextToken());      // EOF
        assertNull(t.currentToken());   // <- must be null per docs
        assertNull(t.nextToken());      // sticky
        assertNull(t.currentToken());
    }

    @Test
    void currentToken_isNullBeforeFirstAdvance() throws IOException {
        CharStreamTokenizer t = tokenizer("42");
        assertNull(t.currentToken()); // not yet advanced
    }

    // ------------------------------------------------------------------
    // Primitive root values
    // ------------------------------------------------------------------

    @Test
    void rootStringValue() throws IOException {
        CharStreamTokenizer t = tokenizer("\"hello\"");
        assertEquals(JsonToken.VALUE_STRING, t.nextToken());
        assertEquals("hello", t.getText());
        assertEquals(5, t.getTextLength());
    }

    @Test
    void rootIntValue() throws IOException {
        CharStreamTokenizer t = tokenizer("42");
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(NumberType.INT, t.getNumberType());
        assertEquals(42, t.getIntValue());
        assertEquals(42L, t.getLongValue());
        assertEquals("42", t.getText());
    }

    @Test
    void rootNegativeInt() throws IOException {
        CharStreamTokenizer t = tokenizer("-7");
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(-7, t.getIntValue());
    }

    @Test
    void rootLongOutsideIntRange() throws IOException {
        long bigLong = 9_000_000_000L; // > Integer.MAX_VALUE
        CharStreamTokenizer t = tokenizer(String.valueOf(bigLong));
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(NumberType.LONG, t.getNumberType());
        assertEquals(bigLong, t.getLongValue());
    }

    @Test
    void rootDoubleValue() throws IOException {
        CharStreamTokenizer t = tokenizer("3.14");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, t.nextToken());
        assertEquals(NumberType.DOUBLE, t.getNumberType());
        assertThat(t.getDoubleValue()).isEqualTo(3.14);
    }

    @Test
    void rootDoubleWithExponent() throws IOException {
        CharStreamTokenizer t = tokenizer("6.022e23");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, t.nextToken());
        assertThat(t.getDoubleValue()).isEqualTo(6.022e23);
    }

    // -------------------------------------------------------------------
    // getIntValue / getLongValue range-check behavior (Jackson-parity)
    // -------------------------------------------------------------------
    // Policy: throw on out-of-range / NaN / Infinity; silently truncate
    // fractional parts of in-range doubles (1.5 -> 1) like Java's (int) cast.
    // Mirrors com.fasterxml.jackson.core.base.ParserBase.convertNumberToInt.

    @Test
    void getIntValue_rejectsLongOutOfIntRange() throws IOException {
        CharStreamTokenizer t = tokenizer("3000000000"); // > Integer.MAX_VALUE
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertThatThrownBy(t::getIntValue)
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("out of range of int");
    }

    @Test
    void getIntValue_truncatesFractionalDouble() throws IOException {
        // Jackson-parity: 1.5 -> 1 (silent truncation, NOT an error).
        CharStreamTokenizer t = tokenizer("1.5");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, t.nextToken());
        assertEquals(1, t.getIntValue());
    }

    @Test
    void getIntValue_rejectsDoubleOutOfIntRange() throws IOException {
        CharStreamTokenizer t = tokenizer("1e20");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, t.nextToken());
        assertThatThrownBy(t::getIntValue)
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("out of range of int");
    }

    @Test
    void getIntValue_rejectsNaN() throws IOException {
        CharStreamTokenizer t = tokenizer("NaN");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, t.nextToken());
        assertThatThrownBy(t::getIntValue)
                .isInstanceOf(JsonIoException.class);
    }

    @Test
    void getIntValue_rejectsInfinity() throws IOException {
        CharStreamTokenizer t = tokenizer("Infinity");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, t.nextToken());
        assertThatThrownBy(t::getIntValue)
                .isInstanceOf(JsonIoException.class);
    }

    @Test
    void getIntValue_acceptsExactIntegerValuedDouble() throws IOException {
        CharStreamTokenizer t = tokenizer("42.0");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, t.nextToken());
        assertEquals(42, t.getIntValue());
    }

    @Test
    void getLongValue_rejectsBigIntegerOutOfLongRange() throws IOException {
        String huge = "12345678901234567890123456789012345"; // 35 digits, > Long.MAX_VALUE
        CharStreamTokenizer t = bigIntegerTokenizer(huge);
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertThatThrownBy(t::getLongValue)
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("out of range of long");
    }

    @Test
    void getLongValue_truncatesFractionalDouble() throws IOException {
        // Jackson-parity: 1.7 -> 1 (silent truncation).
        CharStreamTokenizer t = tokenizer("1.7");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, t.nextToken());
        assertEquals(1L, t.getLongValue());
    }

    @Test
    void getLongValue_rejectsDoubleOutOfLongRange() throws IOException {
        CharStreamTokenizer t = tokenizer("1e30");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, t.nextToken());
        assertThatThrownBy(t::getLongValue)
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("out of range of long");
    }

    @Test
    void getLongValue_rejectsNaN() throws IOException {
        CharStreamTokenizer t = tokenizer("NaN");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, t.nextToken());
        assertThatThrownBy(t::getLongValue)
                .isInstanceOf(JsonIoException.class);
    }

    @Test
    void rootBigIntegerOutsideLongRange() throws IOException {
        String huge = "12345678901234567890123456789012345"; // 35 digits
        CharStreamTokenizer t = tokenizer(huge);
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        // Default integerTypeBoth=false → wraps via BigInteger.longValue() per JsonParser.readInteger
        // We exposed that as a Long; NumberType reflects the typed accessor's view
        assertThat(t.getNumberType()).isIn(NumberType.LONG, NumberType.INT, NumberType.BIG_INTEGER);
    }

    @Test
    void rootBigIntegerWhenForced() throws IOException {
        String huge = "12345678901234567890123456789012345";
        CharStreamTokenizer t = bigIntegerTokenizer(huge);
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(NumberType.BIG_INTEGER, t.getNumberType());
        assertEquals(new BigInteger(huge), t.getBigIntegerValue());
    }

    @Test
    void rootBigDecimalWhenForced() throws IOException {
        CharStreamTokenizer t = bigDecimalTokenizer("3.14");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, t.nextToken());
        assertEquals(NumberType.BIG_DECIMAL, t.getNumberType());
        assertThat(t.getDecimalValue()).isEqualByComparingTo(new BigDecimal("3.14"));
    }

    @Test
    void rootTrue() throws IOException {
        CharStreamTokenizer t = tokenizer("true");
        assertEquals(JsonToken.VALUE_TRUE, t.nextToken());
        assertEquals(true, t.getBooleanValue());
        assertEquals("true", t.getText());
    }

    @Test
    void rootFalse() throws IOException {
        CharStreamTokenizer t = tokenizer("false");
        assertEquals(JsonToken.VALUE_FALSE, t.nextToken());
        assertEquals(false, t.getBooleanValue());
    }

    @Test
    void rootNull() throws IOException {
        CharStreamTokenizer t = tokenizer("null");
        assertEquals(JsonToken.VALUE_NULL, t.nextToken());
        assertEquals("null", t.getText());
    }

    // ------------------------------------------------------------------
    // Object structure
    // ------------------------------------------------------------------

    @Test
    void emptyObject() throws IOException {
        CharStreamTokenizer t = tokenizer("{}");
        assertEquals(JsonToken.START_OBJECT, t.nextToken());
        assertEquals(1, t.getDepth());
        assertEquals(JsonToken.END_OBJECT, t.nextToken());
        assertEquals(0, t.getDepth());
        assertNull(t.nextToken());
    }

    @Test
    void singleFieldObject() throws IOException {
        CharStreamTokenizer t = tokenizer("{\"k\":\"v\"}");
        assertEquals(JsonToken.START_OBJECT, t.nextToken());
        assertEquals(JsonToken.FIELD_NAME, t.nextToken());
        assertEquals("k", t.currentName());
        assertEquals("k", t.getText());
        assertEquals(JsonToken.VALUE_STRING, t.nextToken());
        assertEquals("v", t.getText());
        assertEquals(JsonToken.END_OBJECT, t.nextToken());
        assertNull(t.nextToken());
    }

    @Test
    void multiFieldObject() throws IOException {
        CharStreamTokenizer t = tokenizer("{\"a\":1,\"b\":2,\"c\":3}");
        assertEquals(JsonToken.START_OBJECT, t.nextToken());

        assertEquals(JsonToken.FIELD_NAME, t.nextToken());
        assertEquals("a", t.currentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(1, t.getIntValue());

        assertEquals(JsonToken.FIELD_NAME, t.nextToken());
        assertEquals("b", t.currentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(2, t.getIntValue());

        assertEquals(JsonToken.FIELD_NAME, t.nextToken());
        assertEquals("c", t.currentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(3, t.getIntValue());

        assertEquals(JsonToken.END_OBJECT, t.nextToken());
        assertNull(t.nextToken());
    }

    @Test
    void objectWithMixedValues() throws IOException {
        CharStreamTokenizer t = tokenizer("{\"s\":\"x\",\"n\":42,\"b\":true,\"z\":null}");
        assertEquals(JsonToken.START_OBJECT, t.nextToken());
        assertEquals(JsonToken.FIELD_NAME, t.nextToken());
        assertEquals(JsonToken.VALUE_STRING, t.nextToken());
        assertEquals(JsonToken.FIELD_NAME, t.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(JsonToken.FIELD_NAME, t.nextToken());
        assertEquals(JsonToken.VALUE_TRUE, t.nextToken());
        assertEquals(JsonToken.FIELD_NAME, t.nextToken());
        assertEquals(JsonToken.VALUE_NULL, t.nextToken());
        assertEquals(JsonToken.END_OBJECT, t.nextToken());
    }

    // ------------------------------------------------------------------
    // Array structure
    // ------------------------------------------------------------------

    @Test
    void emptyArray() throws IOException {
        CharStreamTokenizer t = tokenizer("[]");
        assertEquals(JsonToken.START_ARRAY, t.nextToken());
        assertEquals(1, t.getDepth());
        assertEquals(JsonToken.END_ARRAY, t.nextToken());
        assertEquals(0, t.getDepth());
    }

    @Test
    void singleElementArray() throws IOException {
        CharStreamTokenizer t = tokenizer("[42]");
        assertEquals(JsonToken.START_ARRAY, t.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(42, t.getIntValue());
        assertEquals(JsonToken.END_ARRAY, t.nextToken());
    }

    @Test
    void multiElementArray() throws IOException {
        CharStreamTokenizer t = tokenizer("[1, 2, 3]");
        assertEquals(JsonToken.START_ARRAY, t.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(1, t.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(2, t.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(3, t.getIntValue());
        assertEquals(JsonToken.END_ARRAY, t.nextToken());
    }

    @Test
    void heterogeneousArray() throws IOException {
        CharStreamTokenizer t = tokenizer("[\"a\", 2, true, null, [1]]");
        assertEquals(JsonToken.START_ARRAY, t.nextToken());
        assertEquals(JsonToken.VALUE_STRING, t.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(JsonToken.VALUE_TRUE, t.nextToken());
        assertEquals(JsonToken.VALUE_NULL, t.nextToken());
        assertEquals(JsonToken.START_ARRAY, t.nextToken());
        assertEquals(2, t.getDepth());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(JsonToken.END_ARRAY, t.nextToken());
        assertEquals(1, t.getDepth());
        assertEquals(JsonToken.END_ARRAY, t.nextToken());
        assertEquals(0, t.getDepth());
    }

    // ------------------------------------------------------------------
    // Nesting + depth
    // ------------------------------------------------------------------

    @Test
    void deeplyNestedObjects() throws IOException {
        CharStreamTokenizer t = tokenizer("{\"a\":{\"b\":{\"c\":1}}}");
        assertEquals(JsonToken.START_OBJECT, t.nextToken());
        assertEquals(1, t.getDepth());
        assertEquals(JsonToken.FIELD_NAME, t.nextToken());
        assertEquals(JsonToken.START_OBJECT, t.nextToken());
        assertEquals(2, t.getDepth());
        assertEquals(JsonToken.FIELD_NAME, t.nextToken());
        assertEquals(JsonToken.START_OBJECT, t.nextToken());
        assertEquals(3, t.getDepth());
        assertEquals(JsonToken.FIELD_NAME, t.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(JsonToken.END_OBJECT, t.nextToken());
        assertEquals(2, t.getDepth());
        assertEquals(JsonToken.END_OBJECT, t.nextToken());
        assertEquals(1, t.getDepth());
        assertEquals(JsonToken.END_OBJECT, t.nextToken());
        assertEquals(0, t.getDepth());
    }

    @Test
    void deeplyNestedArrays() throws IOException {
        CharStreamTokenizer t = tokenizer("[[[42]]]");
        assertEquals(JsonToken.START_ARRAY, t.nextToken());
        assertEquals(JsonToken.START_ARRAY, t.nextToken());
        assertEquals(JsonToken.START_ARRAY, t.nextToken());
        assertEquals(3, t.getDepth());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(JsonToken.END_ARRAY, t.nextToken());
        assertEquals(JsonToken.END_ARRAY, t.nextToken());
        assertEquals(JsonToken.END_ARRAY, t.nextToken());
    }

    // ------------------------------------------------------------------
    // String escapes
    // ------------------------------------------------------------------

    @Test
    void simpleEscapes() throws IOException {
        CharStreamTokenizer t = tokenizer("\"a\\nb\\tc\\\\d\\\"e\"");
        assertEquals(JsonToken.VALUE_STRING, t.nextToken());
        assertEquals("a\nb\tc\\d\"e", t.getText());
    }

    @Test
    void unicodeEscape() throws IOException {
        CharStreamTokenizer t = tokenizer("\"\\u0041\\u00e9\"");
        assertEquals(JsonToken.VALUE_STRING, t.nextToken());
        assertEquals("Aé", t.getText());
    }

    @Test
    void surrogatePairEscape() throws IOException {
        // U+1F600 (😀) = high D83D + low DE00
        CharStreamTokenizer t = tokenizer("\"\\uD83D\\uDE00\"");
        assertEquals(JsonToken.VALUE_STRING, t.nextToken());
        assertEquals("😀", t.getText());
    }

    @Test
    void longStringExceedingReadBuffer() throws IOException {
        StringBuilder sb = new StringBuilder(600);
        for (int i = 0; i < 600; i++) {
            sb.append('x');
        }
        String json = "\"" + sb + "\"";
        CharStreamTokenizer t = tokenizer(json);
        assertEquals(JsonToken.VALUE_STRING, t.nextToken());
        assertEquals(sb.toString(), t.getText());
    }

    // ------------------------------------------------------------------
    // JSON5 features
    // ------------------------------------------------------------------

    @Test
    void singleQuotedString() throws IOException {
        CharStreamTokenizer t = tokenizer("'hi'");
        assertEquals(JsonToken.VALUE_STRING, t.nextToken());
        assertEquals("hi", t.getText());
    }

    @Test
    void unquotedFieldName() throws IOException {
        CharStreamTokenizer t = tokenizer("{key: 1}");
        assertEquals(JsonToken.START_OBJECT, t.nextToken());
        assertEquals(JsonToken.FIELD_NAME, t.nextToken());
        assertEquals("key", t.currentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(JsonToken.END_OBJECT, t.nextToken());
    }

    @Test
    void singleLineComment() throws IOException {
        CharStreamTokenizer t = tokenizer("// leading comment\n{\"a\":1}");
        assertEquals(JsonToken.START_OBJECT, t.nextToken());
        assertEquals(JsonToken.FIELD_NAME, t.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(JsonToken.END_OBJECT, t.nextToken());
    }

    @Test
    void blockComment() throws IOException {
        CharStreamTokenizer t = tokenizer("/* hi */ [1, /* mid */ 2]");
        assertEquals(JsonToken.START_ARRAY, t.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(JsonToken.END_ARRAY, t.nextToken());
    }

    @Test
    void hexNumber() throws IOException {
        CharStreamTokenizer t = tokenizer("0xFF");
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(255L, t.getLongValue());
    }

    @Test
    void trailingCommaInArray() throws IOException {
        CharStreamTokenizer t = tokenizer("[1, 2, 3,]");
        assertEquals(JsonToken.START_ARRAY, t.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(JsonToken.END_ARRAY, t.nextToken());
    }

    @Test
    void trailingCommaInObject() throws IOException {
        CharStreamTokenizer t = tokenizer("{\"a\":1,}");
        assertEquals(JsonToken.START_OBJECT, t.nextToken());
        assertEquals(JsonToken.FIELD_NAME, t.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(JsonToken.END_OBJECT, t.nextToken());
    }

    @Test
    void leadingDecimalPoint() throws IOException {
        CharStreamTokenizer t = tokenizer(".5");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, t.nextToken());
        assertThat(t.getDoubleValue()).isEqualTo(0.5);
    }

    @Test
    void explicitPositiveSign() throws IOException {
        CharStreamTokenizer t = tokenizer("+7");
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(7, t.getIntValue());
    }

    // ------------------------------------------------------------------
    // NaN/Infinity (allowNanAndInfinity = true)
    // ------------------------------------------------------------------

    @Test
    void positiveInfinity() throws IOException {
        CharStreamTokenizer t = tokenizer("Infinity");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, t.nextToken());
        assertThat(t.getDoubleValue()).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @Test
    void negativeInfinity() throws IOException {
        CharStreamTokenizer t = tokenizer("-Infinity");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, t.nextToken());
        assertThat(t.getDoubleValue()).isEqualTo(Double.NEGATIVE_INFINITY);
    }

    @Test
    void nanLiteral() throws IOException {
        CharStreamTokenizer t = tokenizer("NaN");
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, t.nextToken());
        assertThat(t.getDoubleValue()).isNaN();
    }

    // ------------------------------------------------------------------
    // Strict-mode rejections
    // ------------------------------------------------------------------

    @Test
    void strictRejectsSingleQuotes() {
        CharStreamTokenizer t = strictTokenizer("'x'");
        assertThatThrownBy(t::nextToken)
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Single-quoted");
    }

    @Test
    void strictRejectsComments() {
        CharStreamTokenizer t = strictTokenizer("// hi\n1");
        assertThatThrownBy(t::nextToken)
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Comments not allowed");
    }

    @Test
    void strictRejectsUnquotedKey() {
        CharStreamTokenizer t = strictTokenizer("{key:1}");
        assertThatThrownBy(() -> { t.nextToken(); t.nextToken(); })
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unquoted field names");
    }

    @Test
    void strictRejectsHex() {
        CharStreamTokenizer t = strictTokenizer("0xFF");
        assertThatThrownBy(t::nextToken)
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Hexadecimal");
    }

    @Test
    void strictRejectsTrailingComma() {
        CharStreamTokenizer t = strictTokenizer("[1,]");
        assertThatThrownBy(() -> {
            t.nextToken();
            t.nextToken();
            t.nextToken();
        }).isInstanceOf(JsonIoException.class)
          .hasMessageContaining("Trailing commas");
    }

    @Test
    void strictRejectsLeadingDecimal() {
        CharStreamTokenizer t = strictTokenizer(".5");
        assertThatThrownBy(t::nextToken)
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Leading decimal");
    }

    @Test
    void strictRejectsExplicitPositive() {
        CharStreamTokenizer t = strictTokenizer("+5");
        assertThatThrownBy(t::nextToken)
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Explicit positive");
    }

    // ------------------------------------------------------------------
    // Error paths
    // ------------------------------------------------------------------

    @Test
    void unterminatedString() {
        CharStreamTokenizer t = tokenizer("\"oops");
        assertThatThrownBy(t::nextToken)
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("EOF reached");
    }

    @Test
    void invalidEscape() {
        CharStreamTokenizer t = tokenizer("\"a\\zb\"");
        assertThatThrownBy(t::nextToken)
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Invalid character escape");
    }

    @Test
    void missingColon() {
        CharStreamTokenizer t = tokenizer("{\"a\" 1}");
        assertThatThrownBy(() -> {
            t.nextToken();
            t.nextToken();
        }).isInstanceOf(JsonIoException.class)
          .hasMessageContaining("':'");
    }

    @Test
    void missingCommaInObject() {
        CharStreamTokenizer t = tokenizer("{\"a\":1 \"b\":2}");
        assertThatThrownBy(() -> {
            t.nextToken();   // {
            t.nextToken();   // a
            t.nextToken();   // 1
            t.nextToken();   // expect , or } — error
        }).isInstanceOf(JsonIoException.class)
          .hasMessageContaining("'}'");
    }

    @Test
    void missingCommaInArray() {
        CharStreamTokenizer t = tokenizer("[1 2]");
        assertThatThrownBy(() -> {
            t.nextToken();
            t.nextToken();
            t.nextToken();
        }).isInstanceOf(JsonIoException.class)
          .hasMessageContaining("','");
    }

    @Test
    void unknownLiteralAtRoot() {
        CharStreamTokenizer t = tokenizer("foo");
        assertThatThrownBy(t::nextToken).isInstanceOf(JsonIoException.class);
    }

    @Test
    void typedAccessorOnNonNumberThrows() {
        CharStreamTokenizer t = tokenizer("\"x\"");
        assertThatThrownBy(() -> { t.nextToken(); t.getIntValue(); })
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Numeric value requested");
    }

    @Test
    void unterminatedBlockComment() {
        CharStreamTokenizer t = tokenizer("/* never ends");
        assertThatThrownBy(t::nextToken)
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unterminated block comment");
    }

    // ------------------------------------------------------------------
    // skipChildren
    // ------------------------------------------------------------------

    @Test
    void skipChildrenOnObject() throws IOException {
        CharStreamTokenizer t = tokenizer("[{\"a\":1, \"nested\":{\"x\":[1,2,3]}}, 99]");
        assertEquals(JsonToken.START_ARRAY, t.nextToken());
        assertEquals(JsonToken.START_OBJECT, t.nextToken());
        t.skipChildren();
        assertEquals(JsonToken.END_OBJECT, t.currentToken());
        assertEquals(1, t.getDepth());
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        assertEquals(99, t.getIntValue());
        assertEquals(JsonToken.END_ARRAY, t.nextToken());
    }

    @Test
    void skipChildrenOnArray() throws IOException {
        CharStreamTokenizer t = tokenizer("[[1,2,3], \"after\"]");
        assertEquals(JsonToken.START_ARRAY, t.nextToken());
        assertEquals(JsonToken.START_ARRAY, t.nextToken());
        t.skipChildren();
        assertEquals(JsonToken.END_ARRAY, t.currentToken());
        assertEquals(JsonToken.VALUE_STRING, t.nextToken());
        assertEquals("after", t.getText());
        assertEquals(JsonToken.END_ARRAY, t.nextToken());
    }

    @Test
    void skipChildrenIsNoopOnScalar() throws IOException {
        CharStreamTokenizer t = tokenizer("42");
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.nextToken());
        t.skipChildren(); // should not throw or advance
        assertEquals(JsonToken.VALUE_NUMBER_INT, t.currentToken());
        assertNull(t.nextToken());
    }

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    @Test
    void locationCarriesSourceRef() throws IOException {
        CharStreamTokenizer t = tokenizer("42");
        t.nextToken();
        JsonLocation loc = t.getCurrentLocation();
        assertEquals("test", loc.getSourceRef());
    }

    @Test
    void closeIsCallable() throws IOException {
        CharStreamTokenizer t = tokenizer("1");
        t.nextToken();
        t.close(); // FastReader.close is a no-op-ish; just verify no throw
    }

    // ------------------------------------------------------------------
    // currentName persistence across the value token
    // ------------------------------------------------------------------

    @Test
    void currentNameRemainsValidThroughValueToken() throws IOException {
        CharStreamTokenizer t = tokenizer("{\"k\":42}");
        t.nextToken();                           // START_OBJECT
        t.nextToken();                           // FIELD_NAME
        assertEquals("k", t.currentName());
        t.nextToken();                           // VALUE_NUMBER_INT
        assertEquals("k", t.currentName());      // still the field whose value we're on
    }
}
