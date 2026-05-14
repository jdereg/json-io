package com.cedarsoftware.io;

import java.io.IOException;

/**
 * Signals a parse-time error or invalid cursor call on a {@link JsonTokenizer} —
 * e.g., malformed JSON in the input, an unterminated string, a wrong-token-type
 * call on a typed getter such as {@link JsonTokenizer#getBooleanValue()} on a
 * non-boolean token, or numeric overflow on
 * {@link JsonTokenizer#getIntValue()} / {@link JsonTokenizer#getLongValue()}.
 *
 * <p>Checked exception (extends {@link IOException}) so existing
 * {@code catch (IOException)} blocks transparently handle it — this mirrors
 * Jackson's {@code com.fasterxml.jackson.core.JsonParseException} (which sits
 * at the same place in Jackson's exception hierarchy). Callers wanting to
 * distinguish JSON-syntax / cursor-misuse errors from underlying transport
 * failure may catch this class specifically.
 *
 * <p>This is the read-side counterpart of {@link JsonGenerationException}.
 * Both are checked extensions of {@link IOException}, a documented divergence
 * from json-io's house-style runtime {@link JsonIoException} that exists
 * specifically for the streaming-API surfaces ({@link JsonTokenizer},
 * {@link JsonGenerator}) where Jackson-aligned compile-time forcing of error
 * handling helps porting confidence.
 *
 * @see JsonTokenizer
 */
public class JsonParseException extends IOException {
    private static final long serialVersionUID = 1L;

    public JsonParseException(String message) {
        super(message);
    }

    public JsonParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
