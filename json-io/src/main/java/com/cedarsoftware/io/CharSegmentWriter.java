package com.cedarsoftware.io;

import java.io.Writer;
import java.util.ArrayList;

/**
 * A non-synchronized {@link Writer} that accumulates output in a chain of {@code char[]}
 * segments and materializes the result with {@link #toString()}.
 * <p>
 * Used by {@link JsonIo#toJson(Object, WriteOptions)} and {@link JsonIo#toToon(Object, WriteOptions)}
 * in place of a {@code StringBuilder}-backed writer. JFR profiling of JsonPerformanceTest showed
 * ~46% of JSON write-phase CPU inside the StringBuilder append machinery:
 * {@code AbstractStringBuilder.ensureCapacityInternal} (capacity check on every append, plus
 * doubling-growth copies past the presize), compact-string coder checks per append, and —
 * whenever the payload contains any non-Latin-1 character — a full-buffer
 * {@code inflateIfNeededFor} copy mid-write followed by wide-char appends for the remainder
 * of the document.
 * <p>
 * Segments make growth copy-free: when the current segment fills, it is archived and a new
 * (larger) segment is started — no accumulated content is ever re-copied during the write.
 * Appends are direct {@code char[]} stores guarded by a single bounds check, with no coder
 * machinery. {@link #toString()} gathers the segments into one exact-size {@code char[]} and
 * builds the final String in a single pass (the same assembly Jackson's
 * {@code SegmentedStringWriter}/{@code TextBuffer} pipeline performs).
 * <p>
 * This class is deliberately not thread-safe: each {@code toJson}/{@code toToon} invocation
 * owns its writer and the JSON/TOON writers are single-threaded by design. {@link #flush()}
 * and {@link #close()} are no-ops.
 */
final class CharSegmentWriter extends Writer {
    private static final int FIRST_SEGMENT_SIZE = 1 << 13;   // 8K chars
    private static final int MAX_SEGMENT_SIZE = 1 << 18;     // 256K chars

    private char[] buf = new char[FIRST_SEGMENT_SIZE];
    private int pos;
    private ArrayList<char[]> segments;   // archived (completely full) segments; lazily created
    private int archivedLen;              // total chars across archived segments

    @Override
    public void write(int c) {
        if (pos == buf.length) {
            rollSegment();
        }
        buf[pos++] = (char) c;
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        char[] b = buf;
        int p = pos;
        if (len <= b.length - p) {
            System.arraycopy(cbuf, off, b, p, len);
            pos = p + len;
            return;
        }
        writeCharsSplit(cbuf, off, len);
    }

    private void writeCharsSplit(char[] cbuf, int off, int len) {
        while (len > 0) {
            int space = buf.length - pos;
            if (space == 0) {
                rollSegment();
                space = buf.length;
            }
            int n = Math.min(space, len);
            System.arraycopy(cbuf, off, buf, pos, n);
            pos += n;
            off += n;
            len -= n;
        }
    }

    @Override
    public void write(String str) {
        write(str, 0, str.length());
    }

    @Override
    public void write(String str, int off, int len) {
        char[] b = buf;
        int p = pos;
        if (len <= b.length - p) {
            str.getChars(off, off + len, b, p);
            pos = p + len;
            return;
        }
        writeStringSplit(str, off, len);
    }

    private void writeStringSplit(String str, int off, int len) {
        while (len > 0) {
            int space = buf.length - pos;
            if (space == 0) {
                rollSegment();
                space = buf.length;
            }
            int n = Math.min(space, len);
            str.getChars(off, off + n, buf, pos);
            pos += n;
            off += n;
            len -= n;
        }
    }

    @Override
    public Writer append(CharSequence csq) {
        write(csq == null ? "null" : csq.toString());
        return this;
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) {
        write(csq == null ? "null" : csq.subSequence(start, end).toString());
        return this;
    }

    @Override
    public Writer append(char c) {
        write(c);
        return this;
    }

    /**
     * Archive the (full) current segment and start a new one, doubling the segment size
     * up to {@link #MAX_SEGMENT_SIZE}. Archived segments are always completely full, so
     * {@code archivedLen} advances by exactly {@code buf.length}.
     */
    private void rollSegment() {
        if (segments == null) {
            segments = new ArrayList<>(8);
        }
        segments.add(buf);
        archivedLen += buf.length;
        buf = new char[Math.min(buf.length << 1, MAX_SEGMENT_SIZE)];
        pos = 0;
    }

    /**
     * Materialize the accumulated content as a String. Single-segment case (output fit in
     * the first segment) builds the String directly; multi-segment gathers into one
     * exact-size array first.
     */
    @Override
    public String toString() {
        if (segments == null) {
            return new String(buf, 0, pos);
        }
        char[] all = new char[archivedLen + pos];
        int dst = 0;
        for (int i = 0, n = segments.size(); i < n; i++) {
            char[] seg = segments.get(i);
            System.arraycopy(seg, 0, all, dst, seg.length);
            dst += seg.length;
        }
        System.arraycopy(buf, 0, all, dst, pos);
        return new String(all);
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
