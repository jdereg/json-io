package com.cedarsoftware.io;

/**
 * Snapshot of a position in the JSON input stream. Mirrors the shape of
 * Jackson's {@code com.fasterxml.jackson.core.JsonLocation} so error messages
 * and diagnostics carry the same information shape callers already expect.
 *
 * <p>Instances are immutable. {@link JsonTokenizer#getCurrentLocation()}
 * returns one describing the position of the most-recently emitted token.
 *
 * <p>Package-private until 4.104.0+.
 */
final class JsonLocation {

    private final long charOffset;
    private final int lineNr;
    private final int columnNr;
    private final Object sourceRef;

    JsonLocation(long charOffset, int lineNr, int columnNr, Object sourceRef) {
        this.charOffset = charOffset;
        this.lineNr = lineNr;
        this.columnNr = columnNr;
        this.sourceRef = sourceRef;
    }

    /** Zero-based character offset from the start of the input. */
    public long getCharOffset() {
        return charOffset;
    }

    /** One-based line number. */
    public int getLineNr() {
        return lineNr;
    }

    /** One-based column number within the current line. */
    public int getColumnNr() {
        return columnNr;
    }

    /**
     * Optional descriptor of the underlying source (for example a file path or
     * URL). May be {@code null} when no source identity is available.
     */
    public Object getSourceRef() {
        return sourceRef;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(64);
        sb.append("[Source: ");
        sb.append(sourceRef == null ? "UNKNOWN" : sourceRef);
        sb.append("; line: ").append(lineNr);
        sb.append(", column: ").append(columnNr);
        sb.append(", offset: ").append(charOffset);
        sb.append(']');
        return sb.toString();
    }
}
