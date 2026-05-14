package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;

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
    private final boolean prettyPrint;
    private final int indentSize;
    private final boolean json5UnquotedKeys;
    private final boolean json5SingleQuotes;
    private final boolean allowNanAndInfinity;
    private final int maxStringLength;

    private boolean closed;
    private Runnable closeHook; // optional release-buffers callback wired by JsonIo factory

    CharStreamGenerator(Writer out, WriteOptions writeOptions) {
        this.out = out;
        // Capture all options up-front so the hot loops read finals — JIT-friendly
        // and avoids per-call virtual dispatch through the WriteOptions interface.
        this.prettyPrint = writeOptions.isPrettyPrint();
        this.indentSize = Math.max(0, writeOptions.getIndentationSize());
        this.json5UnquotedKeys = writeOptions.isJson5UnquotedKeys();
        this.json5SingleQuotes = writeOptions.isJson5SmartQuotes();
        this.allowNanAndInfinity = writeOptions.isAllowNanAndInfinity();
        this.maxStringLength = writeOptions.getMaxStringLength();

        this.contextStack = new byte[16];
        this.contextStack[0] = FRAME_ROOT_EMPTY;
        this.depth = 0;
    }

    /** Called by the {@code JsonIo.createGenerator(...)} factory to wire buffer recycling. */
    void setCloseHook(Runnable hook) {
        this.closeHook = hook;
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
            case FRAME_OBJECT_AFTER_FIELD:
            case FRAME_ARRAY_EMPTY:
                emitIndentIfPretty();
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
        if (json5UnquotedKeys && isValidJson5Identifier(name)) {
            out.write(name);
        } else if (json5SingleQuotes) {
            JsonWriter.writeSingleQuotedString(out, name, maxStringLength);
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
        } else if (json5SingleQuotes) {
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
        out.write(Integer.toString(value));
        markValue();
        return this;
    }

    @Override
    public JsonGenerator writeNumber(long value) throws IOException {
        startValueContext();
        out.write(Long.toString(value));
        markValue();
        return this;
    }

    @Override
    public JsonGenerator writeNumber(float value) throws IOException {
        startValueContext();
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            writeNonFiniteFloat(value);
        } else {
            out.write(Float.toString(value));
        }
        markValue();
        return this;
    }

    @Override
    public JsonGenerator writeNumber(double value) throws IOException {
        startValueContext();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            writeNonFiniteDouble(value);
        } else {
            out.write(Double.toString(value));
        }
        markValue();
        return this;
    }

    private void writeNonFiniteFloat(float value) throws IOException {
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
    }

    private void writeNonFiniteDouble(double value) throws IOException {
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
        out.write(value ? "true" : "false");
        markValue();
        return this;
    }

    @Override
    public JsonGenerator writeNull() throws IOException {
        startValueContext();
        out.write("null");
        markValue();
        return this;
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
