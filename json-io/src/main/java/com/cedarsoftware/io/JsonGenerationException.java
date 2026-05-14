package com.cedarsoftware.io;

import java.io.IOException;

/**
 * Signals an invalid call sequence on a {@link JsonGenerator} — e.g.,
 * {@code writeFieldName} outside an object context, mismatched
 * {@code writeEndObject} / {@code writeEndArray}, a value emitted without
 * a preceding field name inside an object, or a structural close that does
 * not match the open token at the top of the context stack.
 *
 * <p>Checked exception (extends {@link IOException}) so existing
 * {@code catch (IOException)} blocks transparently handle it — this mirrors
 * Jackson's {@code com.fasterxml.jackson.core.JsonGenerationException}
 * (which sits at the same place in Jackson's exception hierarchy).
 * Callers wanting to distinguish structural mis-sequencing from real I/O
 * failure may catch this class specifically.
 *
 * <p>This exception always represents a <b>programmer error</b> in the
 * sequence of generator calls, not a JSON-syntax issue (the generator only
 * produces well-formed JSON), and not an underlying transport failure (those
 * still surface as {@link IOException} from the underlying {@code Writer} /
 * {@code OutputStream}).
 *
 * @see JsonGenerator
 */
public class JsonGenerationException extends IOException {
    private static final long serialVersionUID = 1L;

    public JsonGenerationException(String message) {
        super(message);
    }

    public JsonGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
