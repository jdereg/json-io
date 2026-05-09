package com.cedarsoftware.io;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.cedarsoftware.util.FastReader;

/**
 * Cursor-style JSON tokenizer. Splits the lexical layer of json-io's parser
 * out of {@link JsonParser} so the same scanner can drive multiple consumers:
 * the existing tree-builder, an eager POJO constructor (planned for 4.104.0+),
 * and an eventual public streaming API.
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
 *   <li>No boxed {@code getNumberValue() / getNumberValueExact()} —
 *       typed accessors cover every case without forcing a {@code Number}
 *       allocation.</li>
 *   <li>No lenient cross-token coercion ({@code getValueAsString()} etc.) in
 *       v1; typed getters require the matching token type.</li>
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
 * <p>Package-private until 4.104.0+.
 */
abstract class JsonTokenizer implements Closeable {

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
     *       {@link JsonToken#VALUE_NUMBER_FLOAT} — the original numeric
     *       text</li>
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
     * @throws JsonIoException                  if the current token is not a
     *                                          number, or the value does not
     *                                          fit in an {@code int}
     */
    public abstract int getIntValue() throws IOException;

    /**
     * @return the current number token's value as a {@code long}
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
     */
    public abstract boolean getBooleanValue();

    /**
     * Precise numeric type of the current token. Defined when
     * {@link #currentToken()} is {@link JsonToken#VALUE_NUMBER_INT} or
     * {@link JsonToken#VALUE_NUMBER_FLOAT}.
     */
    public abstract NumberType getNumberType();

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
     * Structural nesting depth, where the document root is depth {@code 0}.
     * Each open object or array increments the depth; each close decrements.
     */
    public abstract int getDepth();

    // -------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------

    @Override
    public abstract void close() throws IOException;
}
