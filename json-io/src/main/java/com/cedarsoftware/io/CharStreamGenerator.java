package com.cedarsoftware.io;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.IdentityHashMap;

import com.cedarsoftware.util.internal.CharBufScratch;

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

    // Identity-sharing state for writeObject(Object) across multiple calls. Each
    // distinct Java instance written via writeObject gets a top-level @id assigned
    // on its first emission; subsequent writeObject calls with the same instance
    // emit {"@ref":N} pointing back at that id. All fields are lazy-allocated on
    // the first writeObject call so generators that never call writeObject pay
    // zero overhead.
    private IdentityHashMap<Object, Integer> sharedTopLevelIds;
    private int sharedTopLevelCounter; // also tracks the highest id used by any per-call JsonWriter
    private JsonWriter sharedGraphWriter;
    private StringWriter sharedGraphBuffer;

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
     * Reset this generator's structural state to "at a value slot at the given indent
     * depth, ready to emit exactly one JSON value." Used by the {@link JsonWriter#writeImpl}
     * wrapper to align gen's internal depth tracker with JsonWriter's outer
     * {@code this.depth} (which is incremented by {@code tabIn}). Pretty-print indent
     * emission inside the body then matches the actual document depth, even though the
     * outer structure was emitted via legacy {@code out.write('{')} + {@code tabIn} that
     * gen was not driving.
     *
     * <p>Frames at depths {@code 0..depth-1} are NOT cleared — they belong to the outer
     * caller's structural context and the snapshot/restore pairing preserves them around
     * this call. The body should never pop below its entry frame at {@code depth}.
     *
     * @param depth indent depth at the entry point (typically {@code JsonWriter.this.depth})
     */
    void resetForBridgeAtValueSlot(int depth) {
        if (contextStack.length < depth + 1) {
            byte[] grown = new byte[Math.max(contextStack.length * 2, depth + 1)];
            System.arraycopy(contextStack, 0, grown, 0, contextStack.length);
            contextStack = grown;
        }
        contextStack[depth] = FRAME_ROOT_EMPTY;
        this.depth = depth;
        // suppressNextIndent stays false. The migrated callers use writeStart{Object,Array}Raw
        // helpers to emit '{' or '[' WITHOUT going through startValueContext, so no leading
        // emitIndentIfPretty fires for the structural-open token — there's no leading
        // newline+indent to suppress. The FIRST emitIndentIfPretty in the migrated body is
        // the indent INSIDE the body (between '{' and the first field key, or between '['
        // and the first array element), which is exactly what we want to emit.
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
    /**
     * Reset the CURRENT top frame to {@code FRAME_OBJECT_EMPTY} (the "no fields emitted
     * yet" state) without changing {@code gen.depth}. Used by
     * {@link JsonWriter#writeCustom} 's legacy-API dispatch path: after gen-driven
     * {@code @id}/{@code @type} emission (which left state at {@code FRAME_OBJECT_AFTER_VALUE})
     * and a manual {@code ",\n"} prelude, a legacy custom writer that calls back via
     * {@code context.writeFieldName} (which delegates to gen) expects gen at
     * {@code FRAME_OBJECT_EMPTY} so its first field doesn't auto-emit an extra leading
     * comma. Sets {@code suppressNextIndent=true} too — JsonWriter has already emitted
     * the {@code newLine} prelude so the writer's first call should not duplicate it.
     */
    void resetCurrentObjectFrame() {
        contextStack[depth] = FRAME_OBJECT_EMPTY;
        suppressNextIndent = true;
    }

    void resetForBridgeInsideObjectBody(int currentJsonWriterDepth) {
        // Ensure the contextStack has enough room for the resulting depth (one frame
        // per indent level plus the top FRAME_OBJECT_EMPTY for the new body).
        final int requiredLength = currentJsonWriterDepth + 1;
        if (contextStack.length < requiredLength) {
            // Preserve existing frame contents on grow -- they belong to the outer
            // caller's structural context and must survive a nested reset.
            byte[] grown = new byte[Math.max(contextStack.length * 2, requiredLength)];
            System.arraycopy(contextStack, 0, grown, 0, contextStack.length);
            contextStack = grown;
        }
        // Lower frames (0 .. currentJsonWriterDepth-1) are PRESERVED -- they belong
        // to the outer caller's structural context. Prior implementation used
        // Arrays.fill to write placeholders here; that corrupted state when a custom
        // writer's recursion through context.writeImpl triggered a NESTED reset,
        // because the inner reset clobbered the outer caller's frames at depths the
        // outer would later pop into. A well-behaved custom writer emits inside its
        // own object body without popping below its entry frame — so FRAME_OBJECT_AFTER_VALUE
        // is those lower frames are read-only from the inner
        // writer's perspective.
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
     * <p>
     * Package-private so {@link JsonWriter#writeValue(Object)} can emit the leading
     * separator + indent for an array element before delegating to {@code writeImpl}
     * (whose wrapper handles the post-emission state transition via markValue).
     */
    void startValueContext() throws IOException {
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

    /**
     * Transition the current frame after a value has been emitted. Package-private so
     * the {@code writeXxxRaw} structural helpers can drive outer-frame state transitions
     * after an inner structure (object/array body) completes.
     */
    void markValue() {
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

    /**
     * Emit a newline followed by indentation for the current depth. No-op when
     * {@code prettyPrint} is disabled. Package-private — internal migration helper
     * for {@code JsonWriter} element loops that emit their own structural separators;
     * NOT part of the public {@link JsonGenerator} API. The indent level comes from
     * gen's own {@code depth} field — callers don't need to track depth separately.
     */
    void writeNewlineIndent() throws IOException {
        if (!prettyPrint) {
            return;
        }
        out.write('\n');
        writeIndent(depth);
    }

    /**
     * Combined separator: emit {@code ','} followed by newline + indent (when
     * prettyPrint is on) at the current depth. Package-private — internal migration
     * helper for {@code JsonWriter} element loops; NOT part of the public
     * {@link JsonGenerator} API. State machine is NOT engaged — caller is responsible
     * for context.
     */
    void writeSeparator() throws IOException {
        out.write(',');
        if (prettyPrint) {
            out.write('\n');
            writeIndent(depth);
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
            writeJsonUtf8String(out, name, maxStringLength);
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
            writeSingleQuotedString(out, value, maxStringLength);
        } else {
            writeJsonUtf8String(out, value, maxStringLength);
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
     * Field-name emission fast path that takes an ALREADY-FORMATTED key-and-colon string
     * (e.g., {@code "\"@id\":"}, {@code "$items:"}). Skips the per-call quoting decision +
     * escape scan that {@link #writeFieldName(String)} would perform. Auto-emits leading
     * separator (comma between fields, indent in pretty-print mode) just like the public
     * {@code writeFieldName} — drop-in fast path for callers that already have the
     * formatted key+colon string in hand. Transitions state from {@code FRAME_OBJECT_EMPTY}
     * or {@code FRAME_OBJECT_AFTER_VALUE} to {@code FRAME_OBJECT_AFTER_FIELD}.
     *
     * @param preformattedKeyAndColon the pre-quoted, colon-suffixed key string
     * @throws IOException If an I/O error occurs (or {@link JsonGenerationException}
     *         if the current state can't accept a field name)
     */
    void writeFieldNameRaw(String preformattedKeyAndColon) throws IOException {
        byte t = top();
        if (t == FRAME_OBJECT_EMPTY) {
            emitIndentIfPretty();
            out.write(preformattedKeyAndColon);
            setTop(FRAME_OBJECT_AFTER_FIELD);
            return;
        }
        if (t == FRAME_OBJECT_AFTER_VALUE) {
            out.write(',');
            emitIndentIfPretty();
            out.write(preformattedKeyAndColon);
            setTop(FRAME_OBJECT_AFTER_FIELD);
            return;
        }
        if (t == FRAME_OBJECT_AFTER_FIELD) {
            throw new JsonGenerationException(
                    "Cannot write field name: previous field name is still pending a value");
        }
        throw new JsonGenerationException("Cannot write field name outside an object context");
    }

    /**
     * Open the body of an array for raw flat-pack emission. Caller has just invoked
     * {@link #writeStartArray()} and wants to emit values directly via the package-private
     * {@code writeXxxRaw} helpers + manual {@code ','} separators, bypassing gen's
     * per-element state machine. This helper:
     * <ul>
     *   <li>emits the leading newline + indent at the array body's depth (the lazy indent
     *       that {@link #emitIndentIfPretty()} would otherwise produce on the first
     *       {@code writeXxx} call), so pretty-print formatting matches gen-driven elements
     *   <li>flips state from {@code FRAME_ARRAY_EMPTY} to {@code FRAME_ARRAY_AFTER_VALUE}
     *       so {@link #writeEndArray()} emits the correct trailing indent + {@code ']'}
     * </ul>
     * Callers MUST NOT use the public {@code writeXxx} API between this and
     * {@code writeEndArray} — that would emit a spurious leading {@code ','} (state is
     * already {@code FRAME_ARRAY_AFTER_VALUE}). Pair with {@code writeXxxRaw} helpers and
     * direct {@code ','} writes to the underlying writer.
     *
     * @throws IOException If an I/O error occurs (or {@link JsonGenerationException}
     *         if the current state is not {@code FRAME_ARRAY_EMPTY})
     */
    void beginInlineArrayBody() throws IOException {
        byte t = top();
        if (t != FRAME_ARRAY_EMPTY) {
            throw new JsonGenerationException("beginInlineArrayBody called outside FRAME_ARRAY_EMPTY");
        }
        emitIndentIfPretty();
        setTop(FRAME_ARRAY_AFTER_VALUE);
    }

    /**
     * Open-object emission fast path that bypasses the {@code startValueContext} switch
     * + the inner {@code markValue} (outer-frame state transition is deferred to the
     * matching {@link #writeEndObjectRaw()}). State tracking inside the body still works
     * — the {@code FRAME_OBJECT_EMPTY} frame is pushed exactly as {@code writeStartObject}
     * would. Caller is responsible for any leading separator + indent emission and must
     * call a matching {@code writeEndObjectRaw} or {@code writeEndObject} to close.
     *
     * <p>Use case: callers that know the gen state allows immediate {@code {} emission
     * (e.g., at {@code FRAME_ROOT_EMPTY} with {@code suppressNextIndent=true} after a
     * bridge reset, or at {@code FRAME_OBJECT_AFTER_FIELD} where no separator is needed)
     * skip the per-call switch overhead. Counterpart to the {@code writeXxxRaw} scalar
     * family for the structural-token side.
     */
    void writeStartObjectRaw() throws IOException {
        out.write('{');
        push(FRAME_OBJECT_EMPTY);
    }

    /**
     * Open-array emission fast path. See {@link #writeStartObjectRaw()} for the design.
     * Pushes {@code FRAME_ARRAY_EMPTY}; matching close via {@link #writeEndArrayRaw()}
     * or {@link #writeEndArray()}.
     */
    void writeStartArrayRaw() throws IOException {
        out.write('[');
        push(FRAME_ARRAY_EMPTY);
    }

    /**
     * Close-object emission fast path that bypasses the {@code top()} validation throw.
     * Pretty-print trailing indent emission preserved. After {@code pop}, applies
     * {@link #markValue()} to the new top frame — restoring the outer-frame state
     * transition that {@code writeStartObjectRaw} skipped on the way in.
     */
    void writeEndObjectRaw() throws IOException {
        if (prettyPrint && contextStack[depth] == FRAME_OBJECT_AFTER_VALUE) {
            out.write('\n');
            writeIndent(depth - 1);
        }
        out.write('}');
        --depth;
        markValue();
    }

    /**
     * Close-array emission fast path. See {@link #writeEndObjectRaw()} for the design.
     * Pretty-print trailing indent preserved; outer-frame {@link #markValue()} applied
     * after {@code pop}.
     */
    void writeEndArrayRaw() throws IOException {
        if (prettyPrint && contextStack[depth] == FRAME_ARRAY_AFTER_VALUE) {
            out.write('\n');
            writeIndent(depth - 1);
        }
        out.write(']');
        --depth;
        markValue();
    }

    /**
     * Current structural depth, i.e., the index of the top frame in {@code contextStack}.
     * Increments on each {@code writeStart*} push, decrements on each {@code writeEnd*} pop.
     * Package-private accessor for {@code JsonWriter} migration steps that pass gen's own
     * depth as the depth-anchor argument to {@link #resetForBridgeAtValueSlot(int)} —
     * replaces the legacy {@code this.depth} argument that anchored body emission at
     * JsonWriter's logical depth (which diverged from gen's actual depth in
     * nested-from-legacy-custom-writer cases). Using gen's own depth makes body emission
     * land at the structurally-correct depth, fixing the pretty-print indent of nested
     * custom-writer payloads.
     */
    int currentDepth() {
        return depth;
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

    // Side-buffer arena for {@link #snapshotForExternalValue()} — captures the full
    // {@code contextStack[0..depth]} per snapshot so a NESTED reset (from a custom
    // writer's recursion through {@code writeCustom} -> {@code resetForBridgeInsideObjectBody})
    // can't corrupt the outer caller's frames. Reused across calls; the snapshot/restore
    // pairing is naturally LIFO (writeImpl recursion is a stack), so the arena pointer
    // just advances on snapshot and rewinds on restore — no per-call allocation after
    // the initial sizing.
    private byte[] snapshotArena = new byte[64];
    private int snapshotArenaPointer = 0;

    /**
     * Snapshot gen's current structural state for an upcoming external (non-gen-driven)
     * value emission. Returns an opaque {@code int} token; the caller MUST pass this
     * token to {@link #restoreAfterExternalValue(int)} after the external emission
     * completes. Does NOT call {@link #startValueContext()} — the caller is responsible
     * for any separator/indent emission via its own logic.
     *
     * <p>Used by {@link JsonWriter#writeImpl(Object, boolean)} to wrap its (potentially
     * deep, recursive) serialization in a state-sync window. writeImpl may emit any
     * shape via {@code out.write(...)} directly and may recurse through {@code writeCustom}
     * which resets gen state at a deeper depth (potentially clobbering outer frames);
     * the snapshot captures the entire current stack {@code contextStack[0..depth]} so
     * {@code restoreAfterExternalValue} can recover the outer state byte-for-byte.
     *
     * @return opaque token to pass to {@link #restoreAfterExternalValue(int)}
     */
    int snapshotForExternalValue() {
        final int currentDepth = depth;
        final int bytesNeeded = 2 + currentDepth + 1; // 2-byte depth header + stack contents
        if (snapshotArena.length < snapshotArenaPointer + bytesNeeded) {
            int newLen = Math.max(snapshotArena.length * 2, snapshotArenaPointer + bytesNeeded);
            byte[] grown = new byte[newLen];
            System.arraycopy(snapshotArena, 0, grown, 0, snapshotArenaPointer);
            snapshotArena = grown;
        }
        final int token = snapshotArenaPointer;
        // Write depth as 2 bytes (big-endian); depth is bounded by contextStack.length
        // which grows on demand but stays well under 65535 in practice.
        snapshotArena[snapshotArenaPointer++] = (byte) (currentDepth >>> 8);
        snapshotArena[snapshotArenaPointer++] = (byte) currentDepth;
        System.arraycopy(contextStack, 0, snapshotArena, snapshotArenaPointer, currentDepth + 1);
        snapshotArenaPointer += currentDepth + 1;
        return token;
    }

    /**
     * Restore gen's structural state from a {@link #snapshotForExternalValue()} token
     * and transition to "value emitted" via {@link #markValue()}. Use after the external
     * (non-gen-driven) value bytes have been emitted into the underlying writer.
     *
     * @param token the snapshot token from {@link #snapshotForExternalValue()}
     */
    void restoreAfterExternalValue(int token) {
        final int savedDepth = ((snapshotArena[token] & 0xFF) << 8) | (snapshotArena[token + 1] & 0xFF);
        if (contextStack.length < savedDepth + 1) {
            // contextStack should never need to grow on restore (outer stack already
            // grew when the snapshot was taken), but guard against a future code path.
            byte[] grown = new byte[Math.max(contextStack.length * 2, savedDepth + 1)];
            contextStack = grown;
        }
        System.arraycopy(snapshotArena, token + 2, contextStack, 0, savedDepth + 1);
        depth = savedDepth;
        // Release this snapshot's arena bytes — paired snapshot/restore is LIFO so the
        // pointer just rewinds to the token's start position.
        snapshotArenaPointer = token;
        markValue();
    }

    // -------------------------------------------------------------------
    // Escape tables + string emission helpers
    //
    // Moved from JsonWriter in 4.103.0 as part of the dog-food migration —
    // gen now owns the escape-scan and quoting logic for its own string
    // emission. JsonWriter retains public static delegates marked
    // {@code @Deprecated} (for {@code Writers.java} / {@code WriteOptionsBuilder.java}
    // and any external callers); intra-package callers route here directly.
    // -------------------------------------------------------------------

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    // Pre-computed escape strings for ASCII characters used by JSON double-quoted strings.
    // null = character doesn't need escaping; non-null = the escape sequence to write.
    private static final String[] ESCAPE_STRINGS = new String[128];

    // Pre-computed unicode-escape strings for control characters (0x00-0x1F).
    // Used by the single-quoted string path to escape control chars that don't have
    // dedicated short forms like \b / \t / \n / \f / \r.
    private static final String[] CONTROL_UNICODE_ESCAPES = new String[32];

    // Lookup table for single-quoted strings: which ASCII chars need to be escaped.
    // Different from double-quoted: ' must be escaped (not "), " is left as-is.
    private static final boolean[] NEEDS_ESCAPE_SINGLE_QUOTE = new boolean[128];

    private static String toUnicodeEscape(int codePoint) {
        char[] chars = new char[6];
        chars[0] = '\\';
        chars[1] = 'u';
        chars[2] = HEX_DIGITS[(codePoint >>> 12) & 0xF];
        chars[3] = HEX_DIGITS[(codePoint >>> 8) & 0xF];
        chars[4] = HEX_DIGITS[(codePoint >>> 4) & 0xF];
        chars[5] = HEX_DIGITS[codePoint & 0xF];
        return new String(chars);
    }

    static {
        // Control characters (0x00-0x1F) need backslash-uXXXX escaping by default
        for (int i = 0; i <= 0x1F; i++) {
            String unicodeEscape = toUnicodeEscape(i);
            ESCAPE_STRINGS[i] = unicodeEscape;
            CONTROL_UNICODE_ESCAPES[i] = unicodeEscape;
        }
        // Override common control chars with short escape forms
        ESCAPE_STRINGS['\b'] = "\\b";
        ESCAPE_STRINGS['\t'] = "\\t";
        ESCAPE_STRINGS['\n'] = "\\n";
        ESCAPE_STRINGS['\f'] = "\\f";
        ESCAPE_STRINGS['\r'] = "\\r";
        // Quote and backslash always need escaping in double-quoted strings
        ESCAPE_STRINGS['"'] = "\\\"";
        ESCAPE_STRINGS['\\'] = "\\\\";
        // 0x20-0x7E are printable ASCII — leave as null (no escape needed)
        // 0x7F (DEL) needs escaping
        ESCAPE_STRINGS[0x7F] = "\\u007f";

        // Single-quoted string escape rules (JSON5):
        // All control chars need escaping; only ' (single quote) and \\ among printables.
        for (int i = 0; i < 0x20; i++) {
            NEEDS_ESCAPE_SINGLE_QUOTE[i] = true;
        }
        NEEDS_ESCAPE_SINGLE_QUOTE['\''] = true;
        NEEDS_ESCAPE_SINGLE_QUOTE['\\'] = true;
        NEEDS_ESCAPE_SINGLE_QUOTE[0x7F] = true;
    }

    /**
     * Writes a string without scanning for special characters. Use for labels and other
     * inputs you have a-priori guaranteed are JSON-safe (no embedded {@code "}, {@code \\},
     * or control chars).
     *
     * @param writer Writer to which the quoted string is written
     * @param s      String to write — must be JSON-safe
     * @throws IOException if an error occurs writing to the output stream
     */
    static void writeBasicString(final Writer writer, String s) throws IOException {
        writer.write('\"');
        writer.write(s);
        writer.write('\"');
    }

    /**
     * Writes a JSON string value to the output, properly escaped per JSON specifications.
     * Handles control characters, quotes, backslashes, and Unicode code points. Uses the
     * default 1MB string-length limit.
     *
     * @param output The Writer to write to
     * @param s      The string to write as a JSON string value
     * @throws IOException If an I/O error occurs
     */
    static void writeJsonUtf8String(final Writer output, String s) throws IOException {
        writeJsonUtf8String(output, s, 1000000);
    }

    /**
     * Writes a JSON string value, properly escaped per JSON specifications, with explicit
     * max-length cap. Uses batch scanning (run-of-safe-chars + escape + repeat) for
     * minimal {@code Writer.write} calls. Per-thread {@code char[]} scratch buffer via
     * {@code CharBufScratch.getChars} avoids per-call {@code StringLatin1}/{@code UTF16}
     * dispatch on each character.
     *
     * @param output          The Writer to write to
     * @param s               The string to write as a JSON string value
     * @param maxStringLength Maximum allowed string length (memory-safety cap)
     * @throws IOException If an I/O error occurs
     */
    static void writeJsonUtf8String(final Writer output, String s, int maxStringLength) throws IOException {
        if (output == null) {
            throw new JsonIoException("Output writer cannot be null");
        }
        if (s == null) {
            output.write("null");
            return;
        }

        final int len = s.length();
        if (len > maxStringLength) {
            throw new JsonIoException("String too large: " + len + " chars (max: " + maxStringLength + ")");
        }

        output.write('"');

        if (len > 0) {
            // Bulk-copy chars into a per-thread char[] via CharBufScratch.getChars — uses
            // String.getChars (HotSpot intrinsic with SIMD on supported HW for compact-string
            // byte[] -> char[]). Walking buf[i] is a raw array load; replaces per-character
            // s.charAt(i) and avoids the StringLatin1/UTF16 dispatch that JFR showed at
            // ~345 leaf samples combined inside this loop. Slice writes via
            // output.write(buf, off, len) route through StringBuilder.append(char[], ...)
            // — the fastest variant on StringBuilderWriter — instead of append(String, off,
            // off+len). Re-entrancy contract: the TL char[] is consumed synchronously by
            // output.write calls (bytes copied immediately into the underlying sink) before
            // this method returns.
            char[] buf = CharBufScratch.getChars(s, len);

            int last = 0;
            for (int i = 0; i < len; i++) {
                char ch = buf[i];
                String escape;

                if (ch < 128) {
                    escape = ESCAPE_STRINGS[ch];
                    if (escape == null) {
                        continue;  // No escape needed — most common path
                    }
                } else if (ch == 0x2028) {
                    escape = "\\u2028";  // Line separator — escape for JavaScript compatibility
                } else if (ch == 0x2029) {
                    escape = "\\u2029";  // Paragraph separator — escape for JavaScript compatibility
                } else {
                    continue;  // Non-ASCII written as-is (UTF-8 handled by Writer)
                }

                if (last < i) {
                    output.write(buf, last, i - last);
                }
                output.write(escape);
                last = i + 1;
            }

            if (last < len) {
                output.write(buf, last, len - last);
            }
        }
        output.write('"');
    }

    /**
     * Writes a JSON5 single-quoted string value, properly escaped. In single-quoted form
     * the single quote is escaped (as {@code \\'}) while the double quote is not — the
     * inverse of JSON's standard double-quoted form. Used by {@code gen.writeString} in
     * JSON5 smart-quote mode when the value contains {@code "} but not {@code '} (the
     * quote style that minimizes escaping).
     *
     * @param output          The Writer to write to
     * @param s               The string to be written
     * @param maxStringLength Maximum allowed string length
     * @throws IOException If an I/O error occurs
     */
    static void writeSingleQuotedString(final Writer output, String s, int maxStringLength) throws IOException {
        if (output == null) {
            throw new JsonIoException("Output writer cannot be null");
        }
        if (s == null) {
            output.write("null");
            return;
        }

        output.write('\'');
        final int len = s.length();
        if (len > maxStringLength) {
            throw new JsonIoException("String too large for JSON serialization: " + len
                    + " characters. Maximum allowed: " + maxStringLength);
        }

        int start = 0;
        for (int i = 0; i < len; ) {
            char ch = s.charAt(i);
            // Fast path: ASCII chars that don't need single-quote escaping
            if (ch < 128 && !NEEDS_ESCAPE_SINGLE_QUOTE[ch]) {
                i++;
                continue;
            }
            if (i > start) {
                output.write(s, start, i - start);
            }
            int codePoint = s.codePointAt(i);
            if (codePoint < 0x20 || codePoint == 0x7F) {
                switch (codePoint) {
                    case '\b': output.write("\\b"); break;
                    case '\f': output.write("\\f"); break;
                    case '\n': output.write("\\n"); break;
                    case '\r': output.write("\\r"); break;
                    case '\t': output.write("\\t"); break;
                    default:   output.write(CONTROL_UNICODE_ESCAPES[codePoint]);
                }
            } else if (codePoint == '\'') {
                output.write("\\'");
            } else if (codePoint == '\\') {
                output.write("\\\\");
            } else {
                output.write(s, i, Character.charCount(codePoint));
            }
            i += Character.charCount(codePoint);
            start = i;
        }
        if (start < len) {
            output.write(s, start, len - start);
        }
        output.write('\'');
    }

    // -------------------------------------------------------------------
    // Raw injection
    // -------------------------------------------------------------------

    /**
     * Identity-sharing override for {@code writeObject(Object)} — multiple writeObject
     * calls with the same Java instance emit a single full serialization (with a
     * top-level {@code @id}) on the first call, and a {@code {"@ref":N}} pointer on
     * each subsequent call.
     *
     * <p>Implementation: a lazy {@link IdentityHashMap} on the generator tracks
     * which instances have already been emitted and the {@code @id} each was assigned.
     * The first emission of an instance forces a top-level {@code @id} by pre-populating
     * a per-call {@link JsonWriter}'s {@code objsReferenced} map; the @id namespace is
     * advanced across writeObject calls (via JsonWriter's identity-counter accessors)
     * so internal sub-object ids assigned by JsonWriter don't collide with the
     * gen-allocated top-level ids of other writeObject calls.
     *
     * <p>Identity sharing is active only when the active {@link WriteOptions} has
     * {@code cycleSupport(true)} (the default). With {@code cycleSupport(false)},
     * JsonWriter doesn't emit @id at all, so this override falls back to the
     * superclass behavior (full re-serialization on each call).
     */
    @Override
    public JsonGenerator writeObject(Object o) throws IOException {
        if (o == null) {
            return writeNull();
        }
        if (!writeOptions.isCycleSupport()) {
            // Without cycleSupport, JsonWriter won't emit @id and we can't @ref. Fall back.
            return super.writeObject(o);
        }
        if (sharedTopLevelIds == null) {
            sharedTopLevelIds = new IdentityHashMap<>();
            sharedGraphBuffer = new StringWriter();
            sharedGraphWriter = new JsonWriter(sharedGraphBuffer, writeOptions);
        }
        Integer existing = sharedTopLevelIds.get(o);
        if (existing != null) {
            // Already emitted in a prior writeObject call — emit @ref pointing at it.
            return writeRawValue("{\"@ref\":" + existing + "}");
        }
        // First time. Assign a top-level @id, pre-populate the per-call JsonWriter's
        // objsReferenced so traceReferences treats this instance as a referenced
        // object (forcing @id emission at top level even when this instance only
        // appears once in this call's graph). Start the JsonWriter's identity counter
        // past the gen-allocated top-level id so any internal sub-ids it assigns
        // can't collide with other writeObject calls' top-level ids.
        int topId = ++sharedTopLevelCounter;
        sharedTopLevelIds.put(o, topId);

        sharedGraphBuffer.getBuffer().setLength(0);
        IdentityIntMap refs = sharedGraphWriter.getObjsReferenced();
        refs.clear();
        refs.put(o, topId);
        sharedGraphWriter.setIdentity(topId + 1);
        sharedGraphWriter.write(o);
        // Capture the highest id this call's JsonWriter advanced to so the NEXT
        // call's top-level id starts past it.
        int post = sharedGraphWriter.currentIdentity();
        if (post > sharedTopLevelCounter) {
            sharedTopLevelCounter = post - 1; // -1 because we ++ on next call
        }
        return writeRawValue(sharedGraphBuffer.toString());
    }

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
