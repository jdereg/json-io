package com.cedarsoftware.io;

import java.io.Writer;

/**
 * A non-synchronized {@link Writer} that appends to a {@link StringBuilder}.
 * <p>
 * Used by {@link JsonIo#toJson(Object, WriteOptions)} to avoid the char-to-byte-to-char round trip
 * that the default {@link java.io.OutputStreamWriter}-based pipeline incurs: JFR profiling showed
 * ~50% of JSON write time spent inside {@code sun.nio.cs.UTF_8$Encoder}, {@code StringLatin1.getChars},
 * bounds checks, and the final {@code new String(bytes, UTF-8)} decode on the returned payload.
 * <p>
 * Because {@link StringBuilder} internally uses compact Latin-1 byte storage for pure-ASCII content
 * (Java 9+ "Compact Strings"), writing JSON field names, numeric literals, ISO-8601 dates, and
 * UUIDs — all plain ASCII — remains memory-efficient (1 byte per character) and {@link #toString()}
 * can re-use the compact byte buffer without a re-encoding pass.
 * <p>
 * This class is deliberately not thread-safe: each {@code toJson} invocation owns its writer and
 * the JSON writers are single-threaded by design. {@link #flush()} and {@link #close()} are no-ops.
 *
 * @author Claude4.6o (claude4.6o@ai.assistant)
 */
final class StringBuilderWriter extends Writer {
    private final StringBuilder sb;

    StringBuilderWriter(StringBuilder sb) {
        this.sb = sb;
    }

    StringBuilder getBuilder() {
        return sb;
    }

    @Override
    public void write(int c) {
        sb.append((char) c);
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        sb.append(cbuf, off, len);
    }

    @Override
    public void write(String str) {
        sb.append(str);
    }

    @Override
    public void write(String str, int off, int len) {
        // Writer contract uses (off, len); StringBuilder uses (start, end).
        sb.append(str, off, off + len);
    }

    @Override
    public Writer append(CharSequence csq) {
        sb.append(csq);
        return this;
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) {
        sb.append(csq, start, end);
        return this;
    }

    @Override
    public Writer append(char c) {
        sb.append(c);
        return this;
    }

    @Override
    public void flush() {
        // No backing stream - nothing to flush.
    }

    @Override
    public void close() {
        // No resources to release.
    }
}
