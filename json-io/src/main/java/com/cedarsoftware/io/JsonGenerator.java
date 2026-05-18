package com.cedarsoftware.io;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Cursor-style JSON generator — json-io's public streaming-write API.
 *
 * <p>Create instances via {@link JsonIo#createGenerator(java.io.OutputStream)},
 * {@link JsonIo#createGenerator(java.io.Writer)}, or their option-aware overloads.
 * Counterpart to the streaming-read API ({@link JsonTokenizer}).
 *
 * <p>The shape of this API mirrors Jackson's
 * {@code com.fasterxml.jackson.core.JsonGenerator} for porting friendliness.
 * Differences from Jackson are deliberate and limited:
 * <ul>
 *   <li>No databind {@code writeObject(Object)} entry point — the streaming-write
 *       API is intentionally low-level. For full graph serialization (cycles,
 *       custom writers, {@code @type} policy) use {@link JsonIo#toJson} which
 *       drives the existing {@link JsonWriter}. To embed a tree-serialized value
 *       inside a hand-written stream, pre-serialize with {@link JsonIo#toJson}
 *       and emit via {@link #writeRawValue(String)}.</li>
 *   <li>{@link #writeBinary(byte[]) writeBinary(byte[])} (and the
 *       {@link #writeBinary(byte[], int, int) writeBinary(byte[], offset, length)}
 *       slice overload) honor the active {@link WriteOptions#isNeverShowingType()}
 *       policy. <b>By default</b> (and under {@code showTypeInfoMinimal/MinimalPlus/Always})
 *       it emits json-io's wrapped form
 *       {@code {"@type":"byte[]","value":"<base64>"}}, which round-trips cleanly
 *       through {@link JsonIo#toJava} back into the original {@code byte[]}. Under
 *       {@code showTypeInfoNever()} — the explicit Jackson-compatible mode — it
 *       emits a <b>bare base64 string</b> ({@code "<base64>"}) instead, matching
 *       Jackson's wire format. Bare-base64 input is still decodable on the read
 *       side via java-util's smart {@code String → byte[]} detection. A
 *       {@code null} input emits the JSON literal {@code null} in either mode.
 *       For {@link java.nio.ByteBuffer} (position/limit-aware) round-trip,
 *       continue to use the tree writer ({@link JsonIo#toJson}) which has a
 *       dedicated {@code ByteBufferWriter} that preserves buffer state.</li>
 *   <li>{@link #writeNumber(BigDecimal)} emits the canonical form via
 *       {@code stripTrailingZeros().toPlainString()} (matches
 *       {@link com.cedarsoftware.util.Converter}'s canonical string form);
 *       {@link #writeNumber(BigInteger)} emits {@code toString()}. Both are
 *       <b>unquoted JSON number literals</b>. To force a quoted-string form
 *       (the tree writer's policy for round-trip precision), call
 *       {@link #writeString(String)} with the same canonical form yourself.</li>
 *   <li>Structural misuse (e.g. {@code writeFieldName} outside an object,
 *       mismatched {@code writeEndArray} over an object context, value without a
 *       preceding field in an object) throws {@link JsonGenerationException},
 *       a checked exception that extends {@link IOException}. Existing
 *       {@code catch (IOException)} blocks around the generator call site
 *       (Jackson convention) handle it transparently; callers wanting to
 *       distinguish programmer mis-sequencing from underlying I/O failure may
 *       catch {@code JsonGenerationException} specifically.</li>
 *   <li>No async surface — sync-only, pure JSON.</li>
 * </ul>
 *
 * <p>Implementations track structural context (open object / array / field-pending
 * state) so commas and colons are inserted automatically. Callers do <i>not</i>
 * manage punctuation; just emit the sequence of tokens that compose the desired
 * JSON document.
 *
 * <p>Concrete subclasses are package-private implementation details; callers
 * always work through this abstract API. Returned values from write methods are
 * {@code this} for fluent chaining, mirroring Jackson.
 *
 * <h3>Example — hand-rolled streaming serializer</h3>
 * <pre>{@code
 * try (JsonGenerator g = JsonIo.createGenerator(out)) {
 *     g.writeStartObject()
 *         .writeStringField("id", "u-1")
 *         .writeStringField("name", "Alice")
 *         .writeArrayFieldStart("tags")
 *             .writeString("admin")
 *             .writeString("active")
 *         .writeEndArray()
 *         .writeNumberField("age", 30)
 *      .writeEndObject();
 * }
 * }</pre>
 *
 * <h3>Example — splice streaming-read into streaming-write</h3>
 * <pre>{@code
 * try (JsonTokenizer t = JsonIo.createTokenizer(input);
 *      JsonGenerator g = JsonIo.createGenerator(out)) {
 *     while (t.nextToken() != null) {
 *         g.copyCurrentEvent(t);
 *     }
 * }
 * }</pre>
 *
 * @see JsonTokenizer
 * @see JsonIo#createGenerator(java.io.OutputStream)
 * @see JsonIo#createGenerator(java.io.Writer)
 */
public abstract class JsonGenerator implements Closeable, Flushable {

    // -------------------------------------------------------------------
    // Structural tokens
    // -------------------------------------------------------------------

    /**
     * Emit {@code &#123;} and push a new object context. A trailing comma is
     * written first if this object is a value in an enclosing array or object.
     *
     * @return this generator for chaining
     * @throws IOException on underlying I/O failure
     * @throws JsonGenerationException if a field name is pending without a value
     *                         (i.e. {@code writeFieldName} was the last call)
     */
    public abstract JsonGenerator writeStartObject() throws IOException;

    /**
     * Emit {@code &#125;} and pop the current object context.
     *
     * @return this generator for chaining
     * @throws IOException on underlying I/O failure
     * @throws JsonGenerationException if the current context is not an object,
     *                         or a field name was written without a matching value
     */
    public abstract JsonGenerator writeEndObject() throws IOException;

    /**
     * Emit {@code [} and push a new array context. A trailing comma is written
     * first if this array is a value in an enclosing array or object.
     *
     * @return this generator for chaining
     * @throws IOException on underlying I/O failure
     */
    public abstract JsonGenerator writeStartArray() throws IOException;

    /**
     * Emit {@code ]} and pop the current array context.
     *
     * @return this generator for chaining
     * @throws IOException on underlying I/O failure
     * @throws JsonGenerationException if the current context is not an array
     */
    public abstract JsonGenerator writeEndArray() throws IOException;

    /**
     * Emit a JSON field name (quoted and colon-terminated) inside an object
     * context. The next write call must produce the field's value.
     *
     * @param name the field name; must not be {@code null}
     * @return this generator for chaining
     * @throws IOException on underlying I/O failure
     * @throws JsonGenerationException if the current context is not an object, or a
     *                         field name is already pending without a value
     */
    public abstract JsonGenerator writeFieldName(String name) throws IOException;

    // -------------------------------------------------------------------
    // Scalar values
    // -------------------------------------------------------------------

    /**
     * Emit a JSON string value (quoted, with standard JSON escape handling).
     * A {@code null} value is emitted as the JSON literal {@code null}.
     *
     * @return this generator for chaining
     */
    public abstract JsonGenerator writeString(String value) throws IOException;

    /**
     * Emit a JSON string value sourced from a char-array slice (zero-copy
     * fast path). Standard JSON escaping is applied.
     *
     * @param text   the backing buffer
     * @param offset starting index, inclusive
     * @param length number of characters to write
     * @return this generator for chaining
     */
    public abstract JsonGenerator writeString(char[] text, int offset, int length) throws IOException;

    /** Emit a JSON number from an {@code int}. */
    public abstract JsonGenerator writeNumber(int value) throws IOException;

    /** Emit a JSON number from a {@code long}. */
    public abstract JsonGenerator writeNumber(long value) throws IOException;

    /**
     * Emit a JSON number from a {@code float}. NaN / Infinity handling depends
     * on the active {@link WriteOptions}: when {@code allowNanAndInfinity} is
     * true these are emitted as bare {@code NaN} / {@code Infinity} (JSON5 /
     * lenient form); when strict, a {@link JsonGenerationException} is thrown.
     */
    public abstract JsonGenerator writeNumber(float value) throws IOException;

    /** Emit a JSON number from a {@code double}. See {@link #writeNumber(float)} for NaN/Infinity policy. */
    public abstract JsonGenerator writeNumber(double value) throws IOException;

    /**
     * Emit a JSON number from a {@link BigInteger}. The value is rendered via
     * {@link BigInteger#toString()} as an <b>unquoted JSON number literal</b>.
     * A {@code null} value is emitted as the JSON literal {@code null}.
     */
    public abstract JsonGenerator writeNumber(BigInteger value) throws IOException;

    /**
     * Emit a JSON number from a {@link BigDecimal}. The value is rendered via
     * {@code stripTrailingZeros().toPlainString()} (matching
     * {@link com.cedarsoftware.util.Converter}'s canonical string form) as an
     * <b>unquoted JSON number literal</b>. A {@code null} value is emitted as
     * the JSON literal {@code null}.
     */
    public abstract JsonGenerator writeNumber(BigDecimal value) throws IOException;

    /**
     * Emit a pre-formatted number literal verbatim. The string is trusted —
     * no validation is performed. Use this for arbitrary-precision or
     * locale-specific formatting outside the standard overloads.
     */
    public abstract JsonGenerator writeNumber(String encodedValue) throws IOException;

    /** Emit the JSON literal {@code true} or {@code false}. */
    public abstract JsonGenerator writeBoolean(boolean value) throws IOException;

    /** Emit the JSON literal {@code null}. */
    public abstract JsonGenerator writeNull() throws IOException;

    // -------------------------------------------------------------------
    // Configuration access (for default methods that need to consult policy)
    // -------------------------------------------------------------------

    /**
     * Returns the {@link WriteOptions} this generator is operating under, or
     * {@code null} if the subclass does not provide one (degenerate or test
     * subclasses). Concrete generators producing real JSON should override to
     * return their effective options so that {@code default} methods such as
     * {@link #writeBinary(byte[])} can consult policy fields like
     * {@link WriteOptions#isNeverShowingType()}.
     *
     * <p>This accessor is intentionally protected and {@code null}-tolerant —
     * existing call sites that construct a generator without {@code WriteOptions}
     * continue to work, with the default methods falling back to their safe
     * wrapped-form behaviour.
     *
     * @return the active {@link WriteOptions}, or {@code null}
     * @since 4.103.0
     */
    protected WriteOptions getWriteOptions() {
        return null;
    }

    // -------------------------------------------------------------------
    // Binary (base64)
    // -------------------------------------------------------------------

    /**
     * Emit a {@code byte[]} as a binary value. The wire format depends on the
     * generator's active {@link WriteOptions#isNeverShowingType()} policy:
     * <ul>
     *   <li><b>Default</b> ({@code MINIMAL} / {@code MINIMAL_PLUS} / {@code ALWAYS}
     *       type-info modes): emits json-io's wrapped form
     *       <code>{"@type":"byte[]","value":"&lt;base64&gt;"}</code>. The {@code @type}
     *       tag tells any reader unambiguously how to decode the value; round-trips
     *       cleanly through {@link JsonIo#toJava} back to the original {@code byte[]}.</li>
     *   <li><b>{@code showTypeInfoNever()}</b>: emits the bare base64 string
     *       <code>"&lt;base64&gt;"</code> (Jackson-compatible). The receiver must
     *       know from external schema or declared Java type that this slot holds a
     *       {@code byte[]}; json-io's reader can also recover the original bytes via
     *       java-util's smart {@code String → byte[]} detection at the
     *       {@link com.cedarsoftware.util.Converter} layer.</li>
     * </ul>
     * A {@code null} input is emitted as the JSON literal {@code null} in either mode.
     *
     * <h3>Streaming-context behaviour</h3>
     * Counts as one value emit — auto-commas with the surrounding array/object context,
     * satisfies a pending field name. Default implementation composes
     * {@link #writeStartObject()} / {@link #writeStringField(String, String)} /
     * {@link #writeEndObject()} (wrapped form) or {@link #writeString(String)} (bare
     * form); subclasses may override for a tighter inline emit.
     *
     * @param data the bytes to encode; may be {@code null}
     * @return this generator for chaining
     * @throws IOException on underlying I/O failure
     * @throws JsonGenerationException on structural misuse
     */
    public JsonGenerator writeBinary(byte[] data) throws IOException {
        if (data == null) {
            return writeNull();
        }
        WriteOptions opts = getWriteOptions();
        if (opts != null && opts.isNeverShowingType()) {
            return writeString(java.util.Base64.getEncoder().encodeToString(data));
        }
        writeStartObject();
        writeStringField("@type", "byte[]");
        writeStringField("value", java.util.Base64.getEncoder().encodeToString(data));
        return writeEndObject();
    }

    /**
     * Slice variant of {@link #writeBinary(byte[])}. Encodes {@code length} bytes starting
     * at {@code offset}.
     *
     * @param data   the source array; may be {@code null}
     * @param offset starting index, inclusive
     * @param length number of bytes to encode
     * @return this generator for chaining
     * @throws IOException on underlying I/O failure
     * @throws IndexOutOfBoundsException if the slice is out of range
     */
    public JsonGenerator writeBinary(byte[] data, int offset, int length) throws IOException {
        if (data == null) {
            return writeNull();
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IndexOutOfBoundsException(
                    "Invalid offset/length: offset=" + offset + ", length=" + length
                            + ", data.length=" + data.length);
        }
        byte[] slice = new byte[length];
        System.arraycopy(data, offset, slice, 0, length);
        return writeBinary(slice);
    }

    // -------------------------------------------------------------------
    // Raw injection
    // -------------------------------------------------------------------

    /**
     * Drop the given characters into the output stream verbatim, with no
     * escaping and no effect on the structural context. Intended for advanced
     * use (custom formatting, embedding pre-encoded fragments). Does <b>not</b>
     * count as a value — the surrounding context's comma / field-pending state
     * is unchanged.
     *
     * @return this generator for chaining
     */
    public abstract JsonGenerator writeRaw(String raw) throws IOException;

    /** Single-char variant of {@link #writeRaw(String)}. */
    public abstract JsonGenerator writeRaw(char raw) throws IOException;

    /** Char-array slice variant of {@link #writeRaw(String)}. */
    public abstract JsonGenerator writeRaw(char[] raw, int offset, int length) throws IOException;

    /**
     * Drop a pre-encoded JSON value into the output. Unlike {@link #writeRaw(String)},
     * this call <b>counts as one value</b> — auto-commas fire, field-pending state
     * resolves, and the value is treated as a structural emission. Useful for
     * embedding output from a tree-serializer ({@code JsonIo.toJson(value)}) inside
     * a hand-written streaming sequence.
     *
     * @param encodedValue a syntactically-valid JSON value (object / array / scalar);
     *                     no validation is performed
     * @return this generator for chaining
     */
    public abstract JsonGenerator writeRawValue(String encodedValue) throws IOException;

    // -------------------------------------------------------------------
    // Convenience: field-name + value in a single call
    // -------------------------------------------------------------------

    /** Equivalent to {@link #writeFieldName(String)} followed by {@link #writeString(String)}. */
    public JsonGenerator writeStringField(String fieldName, String value) throws IOException {
        writeFieldName(fieldName);
        return writeString(value);
    }

    /** Equivalent to {@link #writeFieldName(String)} followed by {@link #writeNumber(int)}. */
    public JsonGenerator writeNumberField(String fieldName, int value) throws IOException {
        writeFieldName(fieldName);
        return writeNumber(value);
    }

    /** Equivalent to {@link #writeFieldName(String)} followed by {@link #writeNumber(long)}. */
    public JsonGenerator writeNumberField(String fieldName, long value) throws IOException {
        writeFieldName(fieldName);
        return writeNumber(value);
    }

    /** Equivalent to {@link #writeFieldName(String)} followed by {@link #writeNumber(double)}. */
    public JsonGenerator writeNumberField(String fieldName, double value) throws IOException {
        writeFieldName(fieldName);
        return writeNumber(value);
    }

    /** Equivalent to {@link #writeFieldName(String)} followed by {@link #writeNumber(BigDecimal)}. */
    public JsonGenerator writeNumberField(String fieldName, BigDecimal value) throws IOException {
        writeFieldName(fieldName);
        return writeNumber(value);
    }

    /** Equivalent to {@link #writeFieldName(String)} followed by {@link #writeBoolean(boolean)}. */
    public JsonGenerator writeBooleanField(String fieldName, boolean value) throws IOException {
        writeFieldName(fieldName);
        return writeBoolean(value);
    }

    /** Equivalent to {@link #writeFieldName(String)} followed by {@link #writeNull()}. */
    public JsonGenerator writeNullField(String fieldName) throws IOException {
        writeFieldName(fieldName);
        return writeNull();
    }

    /** Equivalent to {@link #writeFieldName(String)} followed by {@link #writeStartArray()}. */
    public JsonGenerator writeArrayFieldStart(String fieldName) throws IOException {
        writeFieldName(fieldName);
        return writeStartArray();
    }

    /** Equivalent to {@link #writeFieldName(String)} followed by {@link #writeStartObject()}. */
    public JsonGenerator writeObjectFieldStart(String fieldName) throws IOException {
        writeFieldName(fieldName);
        return writeStartObject();
    }

    // -------------------------------------------------------------------
    // Bridge from streaming-read
    // -------------------------------------------------------------------

    /**
     * Copy the current token from the given {@link JsonTokenizer} into this
     * generator as a single equivalent write call. Useful for token-by-token
     * transformation pipelines (parse → inspect/transform → emit).
     *
     * <p>The tokenizer's cursor is <b>not</b> advanced; the caller controls
     * iteration via {@link JsonTokenizer#nextToken()}.
     *
     * @param source the tokenizer to read from; its {@code currentToken()}
     *               determines what is written
     * @return this generator for chaining
     * @throws IOException on underlying I/O failure
     * @throws JsonGenerationException if {@code source.currentToken()} is {@code null}
     */
    public JsonGenerator copyCurrentEvent(JsonTokenizer source) throws IOException {
        JsonToken token = source.currentToken();
        if (token == null) {
            throw new JsonGenerationException("copyCurrentEvent: source has no current token");
        }
        switch (token) {
            case START_OBJECT:
                return writeStartObject();
            case END_OBJECT:
                return writeEndObject();
            case START_ARRAY:
                return writeStartArray();
            case END_ARRAY:
                return writeEndArray();
            case FIELD_NAME:
                return writeFieldName(source.currentName());
            case VALUE_STRING:
                return writeString(source.getText());
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT: {
                NumberType nt = source.getNumberType();
                switch (nt) {
                    case INT:
                        return writeNumber(source.getIntValue());
                    case LONG:
                        return writeNumber(source.getLongValue());
                    case BIG_INTEGER:
                        return writeNumber(source.getBigIntegerValue());
                    case FLOAT:
                        return writeNumber(source.getFloatValue());
                    case DOUBLE:
                        return writeNumber(source.getDoubleValue());
                    case BIG_DECIMAL:
                        return writeNumber(source.getDecimalValue());
                    default:
                        throw new JsonGenerationException("copyCurrentEvent: unknown NumberType " + nt);
                }
            }
            case VALUE_TRUE:
                return writeBoolean(true);
            case VALUE_FALSE:
                return writeBoolean(false);
            case VALUE_NULL:
                return writeNull();
            default:
                throw new JsonGenerationException("copyCurrentEvent: unsupported token " + token);
        }
    }

    /**
     * Copy the current structural value from {@code source} (and all its
     * children) into this generator. The tokenizer's cursor must be on a
     * value token — a scalar emits a single token; {@link JsonToken#START_OBJECT}
     * or {@link JsonToken#START_ARRAY} emits all tokens up to and including the
     * matching close.
     *
     * <p>On entry, {@code source.currentToken()} is the value-start token.
     * On return, {@code source.currentToken()} is the value-end token (the same
     * scalar, or the matching {@code END_OBJECT}/{@code END_ARRAY}).
     *
     * @param source the tokenizer to read from
     * @return this generator for chaining
     * @throws IOException on underlying I/O failure
     * @throws JsonGenerationException if {@code source.currentToken()} is {@code null}
     */
    public JsonGenerator copyCurrentStructure(JsonTokenizer source) throws IOException {
        JsonToken token = source.currentToken();
        if (token == null) {
            throw new JsonGenerationException("copyCurrentStructure: source has no current token");
        }
        // Scalars: just copy the current event.
        if (token != JsonToken.START_OBJECT && token != JsonToken.START_ARRAY) {
            return copyCurrentEvent(source);
        }
        // Structures: emit the opener, then track unmatched START/END balance until
        // the matching close is emitted. {@code JsonTokenizer.getDepth()} can't be
        // compared directly across the END_* boundary since closes decrement before
        // the token is observed; an explicit balance counter is simpler and correct.
        int balance = 1; // we are inside one open structure
        copyCurrentEvent(source);
        while (balance > 0) {
            JsonToken next = source.nextToken();
            if (next == null) {
                throw new JsonGenerationException("copyCurrentStructure: source exhausted before structure closed");
            }
            if (next == JsonToken.START_OBJECT || next == JsonToken.START_ARRAY) {
                balance++;
            } else if (next == JsonToken.END_OBJECT || next == JsonToken.END_ARRAY) {
                balance--;
            }
            copyCurrentEvent(source);
        }
        return this;
    }

    // -------------------------------------------------------------------
    // Bridge factories for the deprecated Writer-based JsonClassWriter API
    // -------------------------------------------------------------------

    /**
     * Construct a "bridge" {@code JsonGenerator} that wraps an existing
     * {@link Writer} mid-stream, positioned <b>inside an open object body</b>
     * (the first call should be {@link #writeFieldName(String)}).
     *
     * <p><b>Intended only for migration scaffolding.</b> A {@link JsonClassWriter}
     * that has migrated to the new {@code JsonGenerator}-based API needs to keep
     * its deprecated {@code write(o, Writer, ctx)} override functional so that
     * user subclasses written against the old API can still chain via
     * {@code super.write(o, output, ctx)}. The conventional pattern is for the
     * deprecated override to delegate to the new override via a bridge built by
     * this method.
     *
     * <p>Application code outside of {@code JsonClassWriter} migration should
     * use {@link JsonIo#createGenerator(Writer)} instead — that constructs a
     * full-fledged generator with a root-empty context, suitable for hand-rolled
     * streaming output.
     *
     * <p>The returned generator does <b>not</b> own {@code out} and must
     * <b>not</b> be closed; doing so would close the underlying Writer that the
     * caller (typically a {@code JsonWriter}) still owns. Pretty-print indent
     * depth is initialized to zero; this means indents emitted by the bridge
     * may not line up with the surrounding {@code JsonWriter}'s indent in deeply
     * nested pretty-printed output, but the JSON itself remains valid. The
     * common case (calling a migrated writer directly via the framework, no
     * subclass override of the deprecated method) takes the new-method dispatch
     * path and never constructs this bridge — pretty-print alignment is correct
     * there.
     *
     * @param out          the Writer the deprecated override was handed; the
     *                     bridge writes through directly to it
     * @param writeOptions the active {@link WriteOptions} (typically obtained
     *                     via {@code context.getWriteOptions()})
     * @return a generator ready for {@link #writeFieldName(String)} as its
     *         first emission
     * @since 4.103.0
     */
    public static JsonGenerator deprecatedWriterBridge_insideObjectBody(Writer out, WriteOptions writeOptions) {
        return CharStreamGenerator.bridgeInsideObjectBody(out, writeOptions, 0);
    }

    /**
     * Construct a "bridge" {@code JsonGenerator} that wraps an existing
     * {@link Writer} mid-stream, positioned at a <b>single value slot</b>
     * (the first call should emit exactly one JSON value).
     *
     * <p>See {@link #deprecatedWriterBridge_insideObjectBody(Writer, WriteOptions)}
     * for the design intent: this is the {@code writePrimitiveForm} counterpart,
     * used by migrated writers whose deprecated
     * {@code writePrimitiveForm(o, Writer, ctx)} override delegates to its new
     * {@code writePrimitiveForm(o, JsonGenerator, ctx)} sibling.
     *
     * @param out          the Writer the deprecated override was handed
     * @param writeOptions the active {@link WriteOptions}
     * @return a generator ready to emit one value (scalar or one structure)
     * @since 4.103.0
     */
    public static JsonGenerator deprecatedWriterBridge_atValueSlot(Writer out, WriteOptions writeOptions) {
        return CharStreamGenerator.bridgeAtValueSlot(out, writeOptions);
    }

    // -------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------

    /** Flush any buffered output to the underlying sink. */
    @Override
    public abstract void flush() throws IOException;

    /**
     * Flush and close the generator. Subclasses should release any
     * pooled resources (recycler buffers, etc.) here. Idempotent.
     */
    @Override
    public abstract void close() throws IOException;
}
