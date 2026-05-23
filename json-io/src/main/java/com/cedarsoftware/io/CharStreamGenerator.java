package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Concrete {@link JsonGenerator} implementation that writes JSON tokens to an
 * underlying {@link Writer}. Tracks structural context for auto-comma insertion
 * and Jackson-compatible error reporting; respects pretty-printing, JSON5
 * unquoted keys / single quotes, and {@code allowNanAndInfinity} policy.
 *
 * <p>Package-private — callers obtain instances through
 * {@link JsonIo#createGenerator(java.io.OutputStream)} /
 * {@link JsonIo#createGenerator(java.io.Writer)}.
 */
final class CharStreamGenerator extends JsonGenerator {

    // -------------------------------------------------------------------
    // Frame state encoding
    // -------------------------------------------------------------------
    //
    // The structural-context stack is a per-frame state machine. Each frame is a
    // single byte from the FRAME_* set below; the bottom-of-stack frame is always
    // ROOT_*. Transitions are driven by writeFieldName / writeStart* / writeEnd*
    // / scalar emits — see the dispatch tables in startValueContext()/markValue()
    // and the write*() entry points.

    private static final byte FRAME_ROOT_EMPTY = 0;        // no document value emitted yet
    private static final byte FRAME_ROOT_DONE = 1;         // exactly one root value emitted
    private static final byte FRAME_OBJECT_EMPTY = 2;      // "{" written, awaiting field name or "}"
    private static final byte FRAME_OBJECT_AFTER_FIELD = 3;// field name written, awaiting value
    private static final byte FRAME_OBJECT_AFTER_VALUE = 4;// value written, awaiting "," + field or "}"
    private static final byte FRAME_ARRAY_EMPTY = 5;       // "[" written, awaiting first element or "]"
    private static final byte FRAME_ARRAY_AFTER_VALUE = 6; // element written, awaiting "," + element or "]"

    private byte[] contextStack;
    private int depth; // index of current top frame

    // -------------------------------------------------------------------
    // Output + configuration (captured from WriteOptions once at construction)
    // -------------------------------------------------------------------

    private final Writer out;
    private final WriteOptions writeOptions; // retained for cold-path policy lookups (e.g. writeBinary's ShowType check)
    private final boolean prettyPrint;
    private final int indentSize;
    private final boolean json5UnquotedKeys;
    private final boolean json5SingleQuotes;
    private final boolean allowNanAndInfinity;
    private final int maxStringLength;

    private boolean closed;
    private Runnable closeHook; // optional release-buffers callback wired by JsonIo factory

    // Bridge-generator support: set to true to suppress the very next leading
    // newline+indent emission. Used when the bridge is constructed inside an
    // open object body where JsonWriter has already emitted the leading
    // newline+indent for the writer's first emit. Auto-clears on use.
    private boolean suppressNextIndent;

    CharStreamGenerator(Writer out, WriteOptions writeOptions) {
        this.out = out;
        this.writeOptions = writeOptions;
        // Capture hot-path options up-front so the hot loops read finals — JIT-friendly
        // and avoids per-call virtual dispatch through the WriteOptions interface.
        this.prettyPrint = writeOptions.isPrettyPrint();
        this.indentSize = Math.max(0, writeOptions.getIndentationSize());
        this.json5UnquotedKeys = writeOptions.isJson5UnquotedKeys();
        this.json5SingleQuotes = writeOptions.isJson5SmartQuotes();
        // Union of both NaN/Infinity-permitting flags matches JsonWriter.isNanInfinityAllowed():
        // - isAllowNanAndInfinity: non-standard JSON extension flag
        // - isJson5InfinityNaN:    JSON5 spec natively allows the NaN/Infinity literals
        // Treating them as equivalent for emission keeps gen consistent with the writer
        // path so a caller setting either flag gets the literal emitted, not a throw.
        this.allowNanAndInfinity = writeOptions.isAllowNanAndInfinity() || writeOptions.isJson5InfinityNaN();
        this.maxStringLength = writeOptions.getMaxStringLength();

        this.contextStack = new byte[16];
        this.contextStack[0] = FRAME_ROOT_EMPTY;
        this.depth = 0;
    }

    @Override
    protected WriteOptions getWriteOptions() {
        return writeOptions;
    }

    /** Called by the {@code JsonIo.createGenerator(...)} factory to wire buffer recycling. */
    void setCloseHook(Runnable hook) {
        this.closeHook = hook;
    }

    // -------------------------------------------------------------------
    // State-reset entry points — allow JsonWriter to reuse one long-lived
    // CharStreamGenerator instance across many custom-writer dispatches
    // without allocating a fresh bridge generator on each call. The state
    // produced matches what bridgeAtValueSlot / bridgeInsideObjectBody
    // would build for a freshly-allocated instance.
    //
    // Used by JsonWriter.writeCustom (4.104.0+ dog-food migration step).
    // External callers / user custom writers should continue to use the
    // bridgeAtValueSlot / bridgeInsideObjectBody factory methods on
    // {@link JsonGenerator} — those still allocate fresh instances.
    // -------------------------------------------------------------------

    /**
     * Reset this generator's structural state to "at a value slot, ready to emit one
     * JSON value." Equivalent to a freshly constructed {@link CharStreamGenerator}
     * with no emission history. After this call the next emit may be a scalar
     * (writeString / writeNumber / writeBoolean / writeNull) or one matched
     * writeStart* / writeEnd* pair.
     *
     * @since 4.104.0
     */
    void resetForBridgeAtValueSlot() {
        contextStack[0] = FRAME_ROOT_EMPTY;
        depth = 0;
        suppressNextIndent = false;
    }

    /**
     * Reset this generator's structural state to "inside an open object body at the
     * given JsonWriter indent depth, ready for {@code writeFieldName}." Matches the
     * state {@link #bridgeInsideObjectBody(Writer, WriteOptions, int)} produces for
     * a freshly-allocated instance — same stack contents, same
     * {@code suppressNextIndent} flag, same depth.
     *
     * @param currentJsonWriterDepth JsonWriter's current indent depth (post-tabIn)
     * @since 4.104.0
     */
    void resetForBridgeInsideObjectBody(int currentJsonWriterDepth) {
        // Ensure the contextStack has enough room for the resulting depth (one
        // FRAME_ROOT_DONE + (currentJsonWriterDepth-1) placeholders + one
        // FRAME_OBJECT_EMPTY at top). Single-allocation grow path replaces the
        // amortized per-push grow.
        final int requiredLength = currentJsonWriterDepth + 1;
        if (contextStack.length < requiredLength) {
            contextStack = new byte[Math.max(contextStack.length * 2, requiredLength)];
        }
        contextStack[0] = FRAME_ROOT_DONE;
        // Below-top frames are only consulted on writeEnd*, and a well-behaved
        // custom writer never pops below its entry frame — so FRAME_OBJECT_AFTER_VALUE
        // is a benign neutral placeholder. Arrays.fill replaces the push-loop:
        // single intrinsic-backed write vs. N method calls + N array stores.
        if (currentJsonWriterDepth > 1) {
            Arrays.fill(contextStack, 1, currentJsonWriterDepth, FRAME_OBJECT_AFTER_VALUE);
        }
        contextStack[currentJsonWriterDepth] = FRAME_OBJECT_EMPTY;
        depth = currentJsonWriterDepth;
        // Suppress the very first leading newline+indent (JsonWriter has already
        // emitted it as part of the wrapper prelude).
        suppressNextIndent = true;
    }

    // -------------------------------------------------------------------
    // Bridge factory methods — for JsonWriter dispatching to custom writers
    // that override the new JsonGenerator-based JsonClassWriter API.
    //
    // The bridge generator wraps JsonWriter's Writer mid-stream, seeded
    // with the structural state that matches where JsonWriter has paused.
    // The custom writer emits via this generator; JsonWriter resumes raw
    // output afterward. The bridge MUST NOT be closed (would close
    // JsonWriter's underlying Writer); the dispatch code lets it become
    // garbage after the writer returns.
    // -------------------------------------------------------------------

    /**
     * Build a bridge generator positioned inside an open object body, ready for the
     * custom writer's first {@code writeFieldName(...)}. JsonWriter has already
     * emitted the opening {@code &#123;}, any {@code @id}/{@code @type} prelude,
     * and the leading newline+indent for the writer's first field; this generator
     * suppresses its own leading indent on the first emit so output is not duplicated.
     *
     * @param out                    JsonWriter's underlying Writer
     * @param writeOptions           same WriteOptions JsonWriter is using
     * @param currentJsonWriterDepth JsonWriter's current indent depth (post-tabIn);
     *                               used to align this generator's indent emissions
     * @return a generator with stack-depth = currentJsonWriterDepth and top frame
     *         FRAME_OBJECT_EMPTY, ready for {@code writeFieldName}
     */
    static CharStreamGenerator bridgeInsideObjectBody(Writer out, WriteOptions writeOptions,
                                                      int currentJsonWriterDepth) {
        CharStreamGenerator g = new CharStreamGenerator(out, writeOptions);
        // Stack[0] is FRAME_ROOT_EMPTY from the constructor; flip to FRAME_ROOT_DONE
        // because we're "inside" a root value already from JsonWriter's perspective.
        g.contextStack[0] = FRAME_ROOT_DONE;
        // Push placeholder frames so stack depth reaches the JsonWriter indent depth.
        // Below-top frames are only consulted on writeEnd*; a well-behaved custom
        // writer never pops below the entry frame, so the placeholder value doesn't
        // matter functionally. Using FRAME_OBJECT_AFTER_VALUE is the safest neutral
        // choice (any pop into it leaves the generator in a benign auto-comma state).
        for (int i = 1; i < currentJsonWriterDepth; i++) {
            g.push(FRAME_OBJECT_AFTER_VALUE);
        }
        g.push(FRAME_OBJECT_EMPTY);
        // Suppress the very first leading newline+indent (JsonWriter already emitted it).
        g.suppressNextIndent = true;
        return g;
    }

    /**
     * Build a bridge generator positioned at a single value slot, ready for the
     * custom writer's {@code writePrimitiveForm} emission. The writer should call
     * exactly one of {@code writeString/writeNumber/writeBoolean/writeNull} or one
     * matched pair of {@code writeStart* / writeEnd*}.
     *
     * @param out          JsonWriter's underlying Writer
     * @param writeOptions same WriteOptions JsonWriter is using
     * @return a generator at depth 0 with top frame FRAME_ROOT_EMPTY
     */
    static CharStreamGenerator bridgeAtValueSlot(Writer out, WriteOptions writeOptions) {
        return new CharStreamGenerator(out, writeOptions);
    }

    // -------------------------------------------------------------------
    // Stack helpers
    // -------------------------------------------------------------------

    private void push(byte frame) {
        if (depth + 1 >= contextStack.length) {
            byte[] grown = new byte[contextStack.length * 2];
            System.arraycopy(contextStack, 0, grown, 0, contextStack.length);
            contextStack = grown;
        }
        contextStack[++depth] = frame;
    }

    private void pop() {
        // We never pop the root frame; callers must check.
        depth--;
    }

    private byte top() {
        return contextStack[depth];
    }

    private void setTop(byte frame) {
        contextStack[depth] = frame;
    }

    /**
     * Validate that the current context can accept a new value (scalar, object,
     * array). Emits a leading comma if needed (array-after-value). Returns
     * cleanly when the value emit is allowed; throws otherwise.
     * <p>
     * Caller is responsible for invoking {@link #markValue()} <i>after</i>
     * emitting the actual value characters.
     */
    private void startValueContext() throws IOException {
        switch (top()) {
            case FRAME_ROOT_EMPTY:
            case FRAME_ARRAY_EMPTY:
                emitIndentIfPretty();
                return;
            case FRAME_OBJECT_AFTER_FIELD:
                // After a field name in an object, the value goes on the SAME LINE
                // as the key (Jackson convention, matches standard JSON pretty-print).
                // writeFieldName already emitted "key": (with trailing space in pretty
                // mode); we don't add a newline before the value here.
                return;
            case FRAME_ARRAY_AFTER_VALUE:
                out.write(',');
                emitIndentIfPretty();
                return;
            case FRAME_ROOT_DONE:
                throw new JsonGenerationException(
                        "Cannot emit another root-level value; document already complete");
            case FRAME_OBJECT_EMPTY:
            case FRAME_OBJECT_AFTER_VALUE:
                throw new JsonGenerationException(
                        "Cannot emit a value inside an object without a preceding field name");
            default:
                throw new JsonGenerationException("Generator in unexpected state: " + top());
        }
    }

    /** Transition the current frame after a value has been emitted. */
    private void markValue() {
        switch (top()) {
            case FRAME_ROOT_EMPTY:
                setTop(FRAME_ROOT_DONE);
                return;
            case FRAME_OBJECT_AFTER_FIELD:
                setTop(FRAME_OBJECT_AFTER_VALUE);
                return;
            case FRAME_ARRAY_EMPTY:
                setTop(FRAME_ARRAY_AFTER_VALUE);
                return;
            case FRAME_ARRAY_AFTER_VALUE:
                // stays — additional comma will fire on next value
                return;
            default:
                // Unreachable — startValueContext() already rejected these states
        }
    }

    private void emitIndentIfPretty() throws IOException {
        if (!prettyPrint) {
            return;
        }
        // Bridge-generator entry: JsonWriter already emitted leading newline+indent
        // before handing off to the custom writer. Suppress exactly one indent so
        // the first writeFieldName/writeString call doesn't emit a duplicate.
        if (suppressNextIndent) {
            suppressNextIndent = false;
            return;
        }
        // At root depth and the first emission, no leading newline.
        if (depth == 0 && top() == FRAME_ROOT_EMPTY) {
            return;
        }
        out.write('\n');
        writeIndent(depth);
    }

    private void writeIndent(int level) throws IOException {
        int spaces = level * indentSize;
        for (int i = 0; i < spaces; i++) {
            out.write(' ');
        }
    }

    // -------------------------------------------------------------------
    // Structural tokens
    // -------------------------------------------------------------------

    @Override
    public JsonGenerator writeStartObject() throws IOException {
        startValueContext();
        out.write('{');
        markValue();
        push(FRAME_OBJECT_EMPTY);
        return this;
    }

    @Override
    public JsonGenerator writeEndObject() throws IOException {
        byte t = top();
        if (t != FRAME_OBJECT_EMPTY && t != FRAME_OBJECT_AFTER_VALUE) {
            if (t == FRAME_OBJECT_AFTER_FIELD) {
                throw new JsonGenerationException(
                        "Cannot end object: field name written without a matching value");
            }
            throw new JsonGenerationException("Cannot end object: current context is not an object");
        }
        if (prettyPrint && t == FRAME_OBJECT_AFTER_VALUE) {
            out.write('\n');
            writeIndent(depth - 1);
        }
        out.write('}');
        pop();
        return this;
    }

    @Override
    public JsonGenerator writeStartArray() throws IOException {
        startValueContext();
        out.write('[');
        markValue();
        push(FRAME_ARRAY_EMPTY);
        return this;
    }

    @Override
    public JsonGenerator writeEndArray() throws IOException {
        byte t = top();
        if (t != FRAME_ARRAY_EMPTY && t != FRAME_ARRAY_AFTER_VALUE) {
            throw new JsonGenerationException("Cannot end array: current context is not an array");
        }
        if (prettyPrint && t == FRAME_ARRAY_AFTER_VALUE) {
            out.write('\n');
            writeIndent(depth - 1);
        }
        out.write(']');
        pop();
        return this;
    }

    @Override
    public JsonGenerator writeFieldName(String name) throws IOException {
        if (name == null) {
            throw new JsonGenerationException("Field name must not be null");
        }
        byte t = top();
        if (t == FRAME_OBJECT_EMPTY) {
            emitIndentIfPretty();
            writeKey(name);
            setTop(FRAME_OBJECT_AFTER_FIELD);
            return this;
        }
        if (t == FRAME_OBJECT_AFTER_VALUE) {
            out.write(',');
            emitIndentIfPretty();
            writeKey(name);
            setTop(FRAME_OBJECT_AFTER_FIELD);
            return this;
        }
        if (t == FRAME_OBJECT_AFTER_FIELD) {
            throw new JsonGenerationException(
                    "Cannot write field name: previous field name is still pending a value");
        }
        throw new JsonGenerationException("Cannot write field name outside an object context");
    }

    private void writeKey(String name) throws IOException {
        // Keys are always either an unquoted JSON5 identifier (when both eligible by the
        // ECMAScript identifier rules AND the writer is in json5UnquotedKeys mode) or
        // a double-quoted JSON UTF-8 string. Keys are NOT subject to json5SmartQuotes
        // single-quoting: Jackson and json-io's own JsonWriter always double-quote keys,
        // smart-quote logic applies to string values only.
        if (json5UnquotedKeys && isValidJson5Identifier(name)) {
            out.write(name);
        } else {
            JsonWriter.writeJsonUtf8String(out, name, maxStringLength);
        }
        out.write(':');
        if (prettyPrint) {
            out.write(' ');
        }
    }

    // -------------------------------------------------------------------
    // Scalar values
    // -------------------------------------------------------------------

    @Override
    public JsonGenerator writeString(String value) throws IOException {
        startValueContext();
        if (value == null) {
            out.write("null");
        } else if (json5SingleQuotes && JsonWriter.shouldUseSingleQuotedString(value)) {
            // json5SmartQuotes is on AND single quotes minimize escaping for this string —
            // matches JsonWriter.writeStringValue's smart selection. Strings without
            // double-quote characters use the default double-quoted form.
            JsonWriter.writeSingleQuotedString(out, value, maxStringLength);
        } else {
            JsonWriter.writeJsonUtf8String(out, value, maxStringLength);
        }
        markValue();
        return this;
    }

    @Override
    public JsonGenerator writeString(char[] text, int offset, int length) throws IOException {
        if (text == null) {
            return writeString((String) null);
        }
        return writeString(new String(text, offset, length));
    }

    @Override
    public JsonGenerator writeNumber(int value) throws IOException {
        startValueContext();
        writeIntRaw(value);
        markValue();
        return this;
    }

    @Override
    public JsonGenerator writeNumber(long value) throws IOException {
        startValueContext();
        writeLongRaw(value);
        markValue();
        return this;
    }

    // -------------------------------------------------------------------
    // Raw integer digit emission (no state-machine interaction)
    //
    // Package-private fast-path helpers for JsonWriter and any other
    // intra-package caller that has already arranged its surrounding
    // structural context (key+colon, array separator, precomputed prefix,
    // etc.) and just needs the decimal digits of an integer emitted to
    // the underlying Writer. Avoids the Integer.toString / Long.toString
    // allocation that {@link #writeNumber(int)} / {@link #writeNumber(long)}
    // would otherwise pay -- shared lookup tables + per-instance scratch
    // buffer + a tight digit-pair loop give us allocation-free integer
    // emission for hot serialization paths (e.g. @id / @ref values,
    // numeric field values, int[]/short[] elements).
    // -------------------------------------------------------------------

    // Pre-computed digit pairs for fast long-to-chars conversion (00-99)
    private static final char[] DIGIT_TENS = {
        '0', '0', '0', '0', '0', '0', '0', '0', '0', '0',
        '1', '1', '1', '1', '1', '1', '1', '1', '1', '1',
        '2', '2', '2', '2', '2', '2', '2', '2', '2', '2',
        '3', '3', '3', '3', '3', '3', '3', '3', '3', '3',
        '4', '4', '4', '4', '4', '4', '4', '4', '4', '4',
        '5', '5', '5', '5', '5', '5', '5', '5', '5', '5',
        '6', '6', '6', '6', '6', '6', '6', '6', '6', '6',
        '7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
        '8', '8', '8', '8', '8', '8', '8', '8', '8', '8',
        '9', '9', '9', '9', '9', '9', '9', '9', '9', '9'
    };
    private static final char[] DIGIT_ONES = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
    };

    // Scratch buffer for digit emission (max 20 chars for Long.MIN_VALUE with sign).
    private final char[] longBuffer = new char[20];

    /**
     * Emit decimal digits of {@code value} directly to the underlying writer using
     * a digit-pair lookup table. No state-machine interaction; the caller is
     * responsible for surrounding structural context. Handles {@code Long.MIN_VALUE}
     * correctly by working in negative space throughout the conversion.
     *
     * @param value the long value to emit
     * @throws IOException If an I/O error occurs
     */
    void writeLongRaw(long value) throws IOException {
        final Writer output = this.out;
        if (value == 0) {
            output.write('0');
            return;
        }

        int idx = longBuffer.length;
        boolean negative = value < 0;
        if (!negative) {
            value = -value;  // Work with negative to handle Long.MIN_VALUE
        }

        // Extract digits two at a time using lookup tables. The quotient must remain `long`
        // throughout -- casting to int here silently truncates for values outside Integer range
        // (e.g., writing Long.MIN_VALUE produced -206158430208 before the cast was fixed).
        while (value <= -100) {
            long q = value / 100;
            int r = (int) ((q * 100) - value);  // remainder 0-99 always fits in int
            value = q;
            longBuffer[--idx] = DIGIT_ONES[r];
            longBuffer[--idx] = DIGIT_TENS[r];
        }

        // Handle remaining 1-2 digits
        int r = (int) -value;
        longBuffer[--idx] = DIGIT_ONES[r];
        if (r >= 10) {
            longBuffer[--idx] = DIGIT_TENS[r];
        }

        if (negative) {
            longBuffer[--idx] = '-';
        }

        output.write(longBuffer, idx, longBuffer.length - idx);
    }

    /**
     * Emit decimal digits of {@code value} directly to the underlying writer using
     * the digit-pair lookup tables. Int-typed variant of {@link #writeLongRaw(long)}
     * for the small register-allocation win on the common int path. Handles
     * {@code Integer.MIN_VALUE} correctly by working in negative space throughout.
     *
     * @param value the int value to emit
     * @throws IOException If an I/O error occurs
     */
    void writeIntRaw(int value) throws IOException {
        final Writer output = this.out;
        if (value == 0) {
            output.write('0');
            return;
        }

        int idx = longBuffer.length;
        boolean negative = value < 0;
        if (!negative) {
            value = -value;  // Work with negative to handle Integer.MIN_VALUE
        }

        // Extract digits two at a time using lookup tables
        while (value <= -100) {
            int q = value / 100;
            int r = (q * 100) - value;  // remainder 0-99
            value = q;
            longBuffer[--idx] = DIGIT_ONES[r];
            longBuffer[--idx] = DIGIT_TENS[r];
        }

        // Handle remaining 1-2 digits
        int r = -value;
        longBuffer[--idx] = DIGIT_ONES[r];
        if (r >= 10) {
            longBuffer[--idx] = DIGIT_TENS[r];
        }

        if (negative) {
            longBuffer[--idx] = '-';
        }

        output.write(longBuffer, idx, longBuffer.length - idx);
    }

    @Override
    public JsonGenerator writeNumber(float value) throws IOException {
        startValueContext();
        writeFloatRaw(value);
        markValue();
        return this;
    }

    @Override
    public JsonGenerator writeNumber(double value) throws IOException {
        startValueContext();
        writeDoubleRaw(value);
        markValue();
        return this;
    }

    /**
     * Emit a float value's canonical string form directly to the underlying writer.
     * No state-machine interaction; the caller is responsible for surrounding
     * structural context. Honors the {@code allowNanAndInfinity} policy: throws
     * {@link JsonGenerationException} for NaN/Infinity when the flag is off,
     * emits the literal otherwise. Counterpart to {@link #writeIntRaw(int)} /
     * {@link #writeLongRaw(long)} for the float type.
     *
     * @param value the float value to emit
     * @throws IOException If an I/O error occurs (or NaN/Inf when policy disallows)
     */
    void writeFloatRaw(float value) throws IOException {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            if (!allowNanAndInfinity) {
                throw new JsonGenerationException(
                        "Cannot serialize non-finite float (NaN/Infinity) when allowNanAndInfinity=false");
            }
            if (Float.isNaN(value)) {
                out.write("NaN");
            } else if (value == Float.POSITIVE_INFINITY) {
                out.write("Infinity");
            } else {
                out.write("-Infinity");
            }
        } else {
            out.write(Float.toString(value));
        }
    }

    /**
     * Emit a double value's canonical string form directly to the underlying writer.
     * No state-machine interaction; the caller is responsible for surrounding
     * structural context. Honors the {@code allowNanAndInfinity} policy: throws
     * {@link JsonGenerationException} for NaN/Infinity when the flag is off,
     * emits the literal otherwise. Counterpart to {@link #writeIntRaw(int)} /
     * {@link #writeLongRaw(long)} for the double type.
     *
     * @param value the double value to emit
     * @throws IOException If an I/O error occurs (or NaN/Inf when policy disallows)
     */
    void writeDoubleRaw(double value) throws IOException {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            if (!allowNanAndInfinity) {
                throw new JsonGenerationException(
                        "Cannot serialize non-finite double (NaN/Infinity) when allowNanAndInfinity=false");
            }
            if (Double.isNaN(value)) {
                out.write("NaN");
            } else if (value == Double.POSITIVE_INFINITY) {
                out.write("Infinity");
            } else {
                out.write("-Infinity");
            }
        } else {
            out.write(Double.toString(value));
        }
    }

    @Override
    public JsonGenerator writeNumber(BigInteger value) throws IOException {
        startValueContext();
        if (value == null) {
            out.write("null");
        } else {
            out.write(value.toString());
        }
        markValue();
        return this;
    }

    @Override
    public JsonGenerator writeNumber(BigDecimal value) throws IOException {
        startValueContext();
        if (value == null) {
            out.write("null");
        } else {
            out.write(value.stripTrailingZeros().toPlainString());
        }
        markValue();
        return this;
    }

    @Override
    public JsonGenerator writeNumber(String encodedValue) throws IOException {
        if (encodedValue == null) {
            return writeNull();
        }
        startValueContext();
        out.write(encodedValue);
        markValue();
        return this;
    }

    @Override
    public JsonGenerator writeBoolean(boolean value) throws IOException {
        startValueContext();
        writeBooleanRaw(value);
        markValue();
        return this;
    }

    @Override
    public JsonGenerator writeNull() throws IOException {
        startValueContext();
        writeNullRaw();
        markValue();
        return this;
    }

    /**
     * Emit the JSON literal {@code true} or {@code false} directly to the underlying
     * writer. No state-machine interaction; the caller is responsible for surrounding
     * structural context.
     *
     * @param value the boolean value to emit
     * @throws IOException If an I/O error occurs
     */
    void writeBooleanRaw(boolean value) throws IOException {
        out.write(value ? "true" : "false");
    }

    /**
     * Emit the JSON literal {@code null} directly to the underlying writer.
     * No state-machine interaction; the caller is responsible for surrounding
     * structural context.
     *
     * @throws IOException If an I/O error occurs
     */
    void writeNullRaw() throws IOException {
        out.write("null");
    }

    /**
     * Field-level fast path that mirrors {@link #writeStringField(String, String)} but skips
     * the per-call escape scan on the value. The caller MUST guarantee the value contains
     * no JSON-special characters (no embedded {@code "}, {@code \}, or control chars below
     * {@code 0x20}); the value is emitted as {@code "value"} verbatim with double-quote
     * delimiters. State machine stays fully engaged via {@link #writeFieldName(String)} for
     * the key emission. Intended for intra-package callers emitting trusted strings like
     * Java type aliases (e.g., {@code "java.lang.String"}, {@code "long"}) at
     * {@code @type}-style field positions — counterpart to the {@code writeXxxRaw} family
     * for scalars: public Jackson API stays safe and comprehensive, package-private fast
     * path lets callers that can prove safety skip the unnecessary work. A {@code null}
     * value emits the JSON {@code null} literal (no surrounding quotes), matching the
     * public {@code writeStringField} contract.
     *
     * @param name  the field name (subject to gen's key-quoting / unquoted-identifier rules)
     * @param value the value; MUST contain no JSON-special characters when non-null
     * @throws IOException If an I/O error occurs
     */
    void writeStringFieldUnescaped(String name, String value) throws IOException {
        writeFieldName(name);
        // After writeFieldName, state is FRAME_OBJECT_AFTER_FIELD — startValueContext
        // would be a no-op here (no separator needed after a field name), so skip it
        // directly and emit the raw quoted value.
        if (value == null) {
            out.write("null");
        } else {
            out.write('"');
            out.write(value);
            out.write('"');
        }
        markValue();
    }

    // -------------------------------------------------------------------
    // Raw injection
    // -------------------------------------------------------------------

    @Override
    public JsonGenerator writeRaw(String raw) throws IOException {
        if (raw != null) {
            out.write(raw);
        }
        return this;
    }

    @Override
    public JsonGenerator writeRaw(char raw) throws IOException {
        out.write(raw);
        return this;
    }

    @Override
    public JsonGenerator writeRaw(char[] raw, int offset, int length) throws IOException {
        out.write(raw, offset, length);
        return this;
    }

    @Override
    public JsonGenerator writeRawValue(String encodedValue) throws IOException {
        startValueContext();
        if (encodedValue != null) {
            out.write(encodedValue);
        } else {
            out.write("null");
        }
        markValue();
        return this;
    }

    // -------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            out.flush();
        } finally {
            try {
                if (closeHook != null) {
                    closeHook.run();
                }
            } finally {
                out.close();
            }
        }
    }

    // -------------------------------------------------------------------
    // JSON5 identifier check (duplicated from JsonWriter — package-private dup
    // is the lesser evil vs widening JsonWriter's visibility).
    // -------------------------------------------------------------------

    private static boolean isValidJson5Identifier(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        char first = name.charAt(0);
        if (!isIdentifierStart(first)) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!isIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIdentifierStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$';
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || (c >= '0' && c <= '9');
    }
}
