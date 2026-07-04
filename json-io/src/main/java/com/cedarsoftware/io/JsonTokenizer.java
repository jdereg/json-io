package com.cedarsoftware.io;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.cedarsoftware.util.Converter;
import com.cedarsoftware.util.FastReader;

/**
 * Cursor-style JSON tokenizer — json-io's public streaming-parse API.
 *
 * <p>Create instances via {@link JsonIo#createTokenizer(String)} or
 * {@link JsonIo#createTokenizer(java.io.InputStream)}; the same lexical core
 * also drives {@link JsonParser} (tree-builder mode) internally.
 *
 * <p>The shape of this API mirrors Jackson's
 * {@code com.fasterxml.jackson.core.JsonParser} for porting friendliness.
 * Differences from Jackson are deliberate and limited:
 * <ul>
 *   <li>{@link #nextToken()} returns {@code null} at EOF (no
 *       {@code END_DOCUMENT} token), matching Jackson's convention.</li>
 *   <li>Zero-copy text access uses the single-call
 *       {@link #getTextSlice(FastReader.BufferSlice)} extension instead of
 *       Jackson's three-method {@code getTextCharacters / getTextOffset /
 *       getTextLength} pattern.</li>
 *   <li>{@link #getNumberValue()} matches Jackson and returns the boxed
 *       {@code Number} in the smallest applicable wrapper, so tree-building
 *       callers can fetch the materialized value in a single virtual
 *       dispatch instead of {@code getNumberType()} + per-type
 *       {@code getXxxValue()}. We do not (yet) ship the lossless
 *       {@code getNumberValueExact()} variant.</li>
 *   <li>{@link #nextFieldName()} and {@link #nextTextValue()} match Jackson
 *       and combine cursor advance + string fetch into one virtual dispatch,
 *       skipping the {@code nextToken() + currentName() / getText()}
 *       round-trip on the hot per-field path.</li>
 *   <li>Lenient number coercion is available via the {@code getValueAsXxx()}
 *       family ({@link #getValueAsInt()}, {@link #getValueAsLong()},
 *       {@link #getValueAsDouble()}) — matching Jackson's coercing getters,
 *       including numeric strings — while the strict {@code getXxxValue()}
 *       getters still require a number token.</li>
 *   <li>No async/binary surface — sync-only, pure JSON.</li>
 * </ul>
 *
 * <p>Implementations are responsible for tokenization state ({@link FastReader}
 * input, scratch buffers, the string-interning cache, JSON5 / NaN-Infinity
 * policy, current-token state). Tree-building, metadata semantics
 * ({@code @id}, {@code @ref}, {@code @type}, ...), and number-representation
 * policy ({@code integerTypeBigInteger}, {@code floatingPointBoth}, ...) live
 * outside this class — the tokenizer exposes typed accessors and the caller
 * picks which one to use.
 *
 * <p>Concrete subclasses are package-private implementation details; callers
 * always work through this abstract API.
 */
public abstract class JsonTokenizer implements Closeable {

    // -------------------------------------------------------------------
    // Cursor advancement
    // -------------------------------------------------------------------

    /**
     * Advance to the next token in the input.
     *
     * @return the token type, or {@code null} on EOF
     * @throws IOException on malformed JSON or underlying I/O failure
     */
    public abstract JsonToken nextToken() throws IOException;

    /**
     * The token returned by the most recent {@link #nextToken()} call, or
     * {@code null} if the cursor has not advanced yet or is at EOF.
     */
    public abstract JsonToken currentToken();

    // -------------------------------------------------------------------
    // Field/text access
    // -------------------------------------------------------------------

    /**
     * Name of the most-recently emitted {@link JsonToken#FIELD_NAME} token.
     * Remains valid for the duration of the current property's value, so it
     * can also be queried while positioned on the value token.
     *
     * @return the field name, or {@code null} when not inside an object
     *         property
     */
    public abstract String currentName();

    /**
     * Textual representation of the current token. Defined for any token:
     * <ul>
     *   <li>{@link JsonToken#FIELD_NAME} — the field name</li>
     *   <li>{@link JsonToken#VALUE_STRING} — the string content (escapes
     *       processed)</li>
     *   <li>{@link JsonToken#VALUE_NUMBER_INT} /
     *       {@link JsonToken#VALUE_NUMBER_FLOAT} — the <b>canonical</b> form
     *       of the parsed value, not the raw source lexeme. Trailing zeros,
     *       leading {@code +} signs, leading decimal points, hex prefixes,
     *       and scientific notation are normalized via
     *       {@link Long#toString(long)} / {@link Double#toString(double)} /
     *       {@link java.math.BigInteger#toString()} /
     *       {@link java.math.BigDecimal#toString()}. E.g. {@code 1e2} reads
     *       back as {@code "100.0"}, {@code 1.2300} as {@code "1.23"},
     *       {@code 0xFF} as {@code "255"}, {@code +7} as {@code "7"}.
     *       Preserving the original lexeme would cost an extra String
     *       allocation per non-trivial number; callers who need the raw
     *       source should capture it externally.</li>
     *   <li>{@link JsonToken#VALUE_TRUE} / {@link JsonToken#VALUE_FALSE} /
     *       {@link JsonToken#VALUE_NULL} — the literal name</li>
     *   <li>Structural tokens — the punctuation character</li>
     * </ul>
     */
    public abstract String getText() throws IOException;

    /** Length of the text returned by {@link #getText()}. */
    public abstract int getTextLength();

    /**
     * Zero-copy view of the current token's text. Populates {@code slice} with
     * a borrowed reference into an internal buffer; the contents are valid
     * only until the next {@link #nextToken()} call.
     *
     * @param slice the slice to populate
     * @return {@code true} if the slice was populated, {@code false} if a
     *         zero-copy view is unavailable for the current token (the caller
     *         should fall back to {@link #getText()})
     */
    public abstract boolean getTextSlice(FastReader.BufferSlice slice);

    // -------------------------------------------------------------------
    // Typed value accessors
    // -------------------------------------------------------------------

    /**
     * @return the current number token's value as an {@code int}
     * @throws IOException                      on I/O error
     * @throws JsonParseException               if the current token is not a
     *                                          number, or the value does not
     *                                          fit in an {@code int}
     */
    public abstract int getIntValue() throws IOException;

    /**
     * @return the current number token's value as a {@code long}
     * @throws IOException                      on I/O error
     * @throws JsonParseException               if the current token is not a
     *                                          number, or the value is NaN/
     *                                          Infinity, or its magnitude
     *                                          does not fit in a {@code long}.
     *                                          Fractional parts of in-range
     *                                          doubles are truncated silently
     *                                          (Jackson-parity behavior).
     */
    public abstract long getLongValue() throws IOException;

    /**
     * @return the current number token's value as a {@code float}
     */
    public abstract float getFloatValue() throws IOException;

    /**
     * @return the current number token's value as a {@code double}
     */
    public abstract double getDoubleValue() throws IOException;

    /**
     * @return the current number token's value as a {@link BigInteger}
     */
    public abstract BigInteger getBigIntegerValue() throws IOException;

    /**
     * @return the current number token's value as a {@link BigDecimal}
     */
    public abstract BigDecimal getDecimalValue() throws IOException;

    /**
     * @return {@code true} if the current token is {@link JsonToken#VALUE_TRUE},
     *         {@code false} if it is {@link JsonToken#VALUE_FALSE}
     * @throws JsonParseException if the current token is neither
     */
    public abstract boolean getBooleanValue() throws JsonParseException;

    /**
     * Precise numeric type of the current token.
     *
     * @throws JsonParseException if the current token is not
     *                            {@link JsonToken#VALUE_NUMBER_INT} or
     *                            {@link JsonToken#VALUE_NUMBER_FLOAT}
     */
    public abstract NumberType getNumberType() throws JsonParseException;

    /**
     * Materialize the current numeric token as a boxed {@link Number} in the
     * smallest applicable wrapper ({@code Long} for INT/LONG, {@code Double}
     * for DOUBLE, {@code Float} for FLOAT, {@code BigInteger} for BIG_INTEGER,
     * {@code BigDecimal} for BIG_DECIMAL). Matches Jackson's
     * {@code JsonParser.getNumberValue()} contract and lets tree-building
     * callers avoid the {@code getNumberType()} + {@code getXxxValue()}
     * two-dispatch sequence.
     *
     * @throws JsonParseException if the current token is not
     *                         {@link JsonToken#VALUE_NUMBER_INT} or
     *                         {@link JsonToken#VALUE_NUMBER_FLOAT}
     */
    public abstract Number getNumberValue() throws IOException;

    /**
     * Advance to the next token and, if it is {@link JsonToken#FIELD_NAME},
     * return the field-name string; otherwise return {@code null}. The cursor
     * is advanced in both cases — callers can inspect {@link #currentToken()}
     * to disambiguate (e.g. {@link JsonToken#END_OBJECT}). Matches Jackson's
     * {@code JsonParser.nextFieldName()}.
     *
     * <p>Default implementation delegates to {@link #nextToken()} and
     * {@link #currentName()}; concrete tokenizers should override to fold the
     * two calls into a single virtual dispatch.
     */
    public String nextFieldName() throws IOException {
        return nextToken() == JsonToken.FIELD_NAME ? currentName() : null;
    }

    /**
     * Advance to the next token and, if it is {@link JsonToken#VALUE_STRING},
     * return the string value; otherwise return {@code null} (the cursor still
     * advances — callers can inspect {@link #currentToken()} to handle the
     * non-string case). Matches Jackson's {@code JsonParser.nextTextValue()}.
     *
     * <p>Default implementation delegates to {@link #nextToken()} and
     * {@link #getText()}; concrete tokenizers should override to fold the two
     * calls into a single virtual dispatch.
     */
    public String nextTextValue() throws IOException {
        return nextToken() == JsonToken.VALUE_STRING ? getText() : null;
    }

    // -------------------------------------------------------------------
    // Lenient value accessors (Jackson getValueAsXxx parity)
    // -------------------------------------------------------------------

    /**
     * Coercing counterpart to {@link #getIntValue()}, mirroring Jackson's
     * {@code JsonParser.getValueAsInt()}. A number token returns its value (a
     * fractional {@link JsonToken#VALUE_NUMBER_FLOAT} is truncated toward zero);
     * a {@link JsonToken#VALUE_STRING} holding a number is coerced (e.g.
     * {@code "123"} → {@code 123}, {@code "12.7"} → {@code 12}). Coercion is
     * delegated to java-util's {@code Converter}, so numeric-string results match
     * Jackson's.
     *
     * @throws IOException on I/O error
     * @throws IllegalArgumentException if the current token's text cannot be
     *                                  coerced to an {@code int}
     */
    public int getValueAsInt() throws IOException {
        JsonToken t = currentToken();
        if (t == JsonToken.VALUE_NUMBER_INT || t == JsonToken.VALUE_NUMBER_FLOAT) {
            return getIntValue();
        }
        return Converter.convert(getText(), int.class);
    }

    /**
     * Coercing counterpart to {@link #getLongValue()}; see {@link #getValueAsInt()}.
     *
     * @throws IOException on I/O error
     * @throws IllegalArgumentException if the current token's text cannot be
     *                                  coerced to a {@code long}
     */
    public long getValueAsLong() throws IOException {
        JsonToken t = currentToken();
        if (t == JsonToken.VALUE_NUMBER_INT || t == JsonToken.VALUE_NUMBER_FLOAT) {
            return getLongValue();
        }
        return Converter.convert(getText(), long.class);
    }

    /**
     * Coercing counterpart to {@link #getDoubleValue()}; see {@link #getValueAsInt()}.
     *
     * @throws IOException on I/O error
     * @throws IllegalArgumentException if the current token's text cannot be
     *                                  coerced to a {@code double}
     */
    public double getValueAsDouble() throws IOException {
        JsonToken t = currentToken();
        if (t == JsonToken.VALUE_NUMBER_INT || t == JsonToken.VALUE_NUMBER_FLOAT) {
            return getDoubleValue();
        }
        return Converter.convert(getText(), double.class);
    }

    // -------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------

    /**
     * Skip past the contents of the current structured token. The cursor must
     * be on {@link JsonToken#START_OBJECT} or {@link JsonToken#START_ARRAY};
     * after this call, it is positioned on the matching {@code END_*} token.
     */
    public abstract void skipChildren() throws IOException;

    // -------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------

    /** Position of the most-recently emitted token. */
    public abstract JsonLocation getCurrentLocation();

    /**
     * Scan past any remaining whitespace and JSON5 comments without
     * tokenizing further. Returns {@code true} if a non-trivia character
     * follows (and has been pushed back so the next {@link #nextToken()}
     * call still sees it), or {@code false} if the input is exhausted
     * (only trivia remains).
     *
     * <p>Used by {@link JsonParser} to implement today's depth-0
     * trailing-content check on root-level string values without
     * tokenizing the trailing content (which can be non-JSON, e.g. a stray
     * {@code :}). Not part of the Jackson cursor API; specific to json-io.
     */
    abstract boolean hasNonWhitespaceContent() throws IOException;

    /**
     * Structural nesting depth, where the document root is depth {@code 0}.
     * Each open object or array increments the depth; each close decrements.
     */
    public abstract int getDepth();

    // -------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------

    /**
     * @return {@code true} once {@link #close()} has been called on this
     *         tokenizer. Mirrors Jackson's {@code JsonParser.isClosed()} so a
     *         {@code while (!tokenizer.isClosed())} drain loop ports directly.
     *         Reaching end-of-input alone does not close the tokenizer —
     *         {@link #nextToken()} returns {@code null} at EOF.
     *
     * <p>The default returns {@code false}; concrete tokenizers override to
     * report real close state.
     */
    public boolean isClosed() {
        return false;
    }

    @Override
    public abstract void close() throws IOException;
}
